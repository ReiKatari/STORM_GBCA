#include "storm_multiplayer.hpp"

#include <algorithm>
#include <chrono>
#include <cstdio>
#include <cstring>

#ifdef _WIN32
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <winsock2.h>
#include <ws2tcpip.h>
using storm_sock_t = SOCKET;
static constexpr storm_sock_t kInvalidSock = INVALID_SOCKET;
#define STORM_CLOSE_SOCK(s) closesocket(static_cast<SOCKET>(s))
#else
#include <arpa/inet.h>
#include <errno.h>
#include <fcntl.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <unistd.h>
using storm_sock_t = int;
static constexpr storm_sock_t kInvalidSock = -1;
#define STORM_CLOSE_SOCK(s) close(static_cast<int>(s))
#endif

namespace storm {

namespace {

static constexpr uint16_t kDiscoveryUdpPort = 54321;
static constexpr uint8_t kPktMagic0 = 0x53; // 'S'
static constexpr uint8_t kPktMagic1 = 0x47; // 'G'

enum PacketType : uint8_t {
    PKT_ASSIGN_ID   = 1,
    PKT_GB_BYTE     = 2,
    PKT_GBA_MULTI   = 3,
    PKT_GBA_NORMAL  = 4,
    PKT_RFU_DATA    = 5,
    PKT_PING        = 6,
    PKT_PONG        = 7,
    PKT_PEER_COUNT  = 8,
};

struct NetPacket {
    uint8_t magic0;
    uint8_t magic1;
    uint8_t type;
    uint8_t player_id;
    uint32_t value0;
    uint32_t value1;
    uint16_t multi[4];
};

static std::mutex g_wsa_mu;
static int g_wsa_ref = 0;

static void net_init() {
#ifdef _WIN32
    std::lock_guard<std::mutex> lk(g_wsa_mu);
    if (g_wsa_ref == 0) {
        WSADATA wsa{};
        WSAStartup(MAKEWORD(2, 2), &wsa);
    }
    g_wsa_ref++;
#endif
}

static void net_deinit() {
#ifdef _WIN32
    std::lock_guard<std::mutex> lk(g_wsa_mu);
    if (g_wsa_ref > 0) {
        g_wsa_ref--;
        if (g_wsa_ref == 0) WSACleanup();
    }
#endif
}

static void set_nonblocking(int64_t s, bool nb) {
    if (s < 0) return;
#ifdef _WIN32
    u_long mode = nb ? 1UL : 0UL;
    ioctlsocket(static_cast<SOCKET>(s), FIONBIO, &mode);
#else
    int flags = fcntl(static_cast<int>(s), F_GETFL, 0);
    if (flags >= 0) {
        fcntl(static_cast<int>(s), F_SETFL, nb ? (flags | O_NONBLOCK) : (flags & ~O_NONBLOCK));
    }
#endif
}

static void set_tcp_nodelay(int64_t s) {
    if (s < 0) return;
    int one = 1;
    setsockopt(static_cast<storm_sock_t>(s), IPPROTO_TCP, TCP_NODELAY,
               reinterpret_cast<const char*>(&one), sizeof(one));
}

static bool send_packet(int64_t s, const NetPacket& pkt) {
    if (s < 0) return false;
    const char* ptr = reinterpret_cast<const char*>(&pkt);
    size_t total = sizeof(NetPacket);
    size_t sent = 0;
    while (sent < total) {
        int n = static_cast<int>(send(static_cast<storm_sock_t>(s), ptr + sent, static_cast<int>(total - sent), 0));
        if (n <= 0) return false;
        sent += static_cast<size_t>(n);
    }
    return true;
}

static int recv_packet_nonblock(int64_t s, NetPacket& out) {
    if (s < 0) return -1;
    int n = static_cast<int>(recv(static_cast<storm_sock_t>(s),
                                  reinterpret_cast<char*>(&out),
                                  static_cast<int>(sizeof(NetPacket)),
                                  MSG_PEEK));
    if (n == 0) return -1; // closed
    if (n < 0) {
#ifdef _WIN32
        int e = WSAGetLastError();
        if (e == WSAEWOULDBLOCK) return 0;
#else
        if (errno == EWOULDBLOCK || errno == EAGAIN) return 0;
#endif
        return -1;
    }
    if (n < static_cast<int>(sizeof(NetPacket))) return 0;
    int rd = static_cast<int>(recv(static_cast<storm_sock_t>(s),
                                   reinterpret_cast<char*>(&out),
                                   static_cast<int>(sizeof(NetPacket)),
                                   0));
    if (rd != static_cast<int>(sizeof(NetPacket))) return -1;
    if (out.magic0 != kPktMagic0 || out.magic1 != kPktMagic1) return 0;
    return 1;
}

// --- GB SIO C callbacks ---
static bool gb_net_init(struct GBSIODriver*) { return true; }
static void gb_net_deinit(struct GBSIODriver*) {}
static void gb_net_write_sb(struct GBSIODriver* driver, uint8_t value) {
    auto* drv = reinterpret_cast<MultiplayerManager::NetGbDriver*>(driver);
    drv->pending_out = value;
}
static uint8_t gb_net_write_sc(struct GBSIODriver* driver, uint8_t value) {
    auto* drv = reinterpret_cast<MultiplayerManager::NetGbDriver*>(driver);
    if ((value & 0x80) != 0 && drv->mgr && driver->p) {
        bool is_master = (value & 0x01) != 0;
        uint8_t in_byte = drv->mgr->exchange_gb_byte(drv->pending_out, is_master);
        driver->p->pendingSB = in_byte;
    }
    return value;
}

// --- GB Printer C callback ---
static void gb_printer_print_cb(struct GBPrinter* printer, int height, const uint8_t* data) {
    auto* wrap = reinterpret_cast<MultiplayerManager::NetPrinterDriver*>(printer);
    if (wrap && wrap->mgr) {
        wrap->mgr->on_gb_printer_print(height, data);
    }
    GBPrinterDonePrinting(printer);
}

// --- GBA SIO C callbacks ---
static bool gba_net_init(struct GBASIODriver*) { return true; }
static void gba_net_deinit(struct GBASIODriver*) {}
static void gba_net_reset(struct GBASIODriver*) {}
static void gba_net_set_mode(struct GBASIODriver* driver, enum GBASIOMode mode) {
    auto* drv = reinterpret_cast<MultiplayerManager::NetGbaDriver*>(driver);
    drv->current_mode = mode;
}
static bool gba_net_handles_mode(struct GBASIODriver*, enum GBASIOMode mode) {
    return mode == GBA_SIO_MULTI || mode == GBA_SIO_NORMAL_8 || mode == GBA_SIO_NORMAL_32;
}
static int gba_net_connected_devices(struct GBASIODriver* driver) {
    auto* drv = reinterpret_cast<MultiplayerManager::NetGbaDriver*>(driver);
    return drv->mgr ? drv->mgr->connected_devices() : 0;
}
static int gba_net_device_id(struct GBASIODriver* driver) {
    auto* drv = reinterpret_cast<MultiplayerManager::NetGbaDriver*>(driver);
    return drv->mgr ? drv->mgr->device_id() : 0;
}
static uint16_t gba_net_write_siocnt(struct GBASIODriver*, uint16_t value) { return value; }
static uint16_t gba_net_write_rcnt(struct GBASIODriver*, uint16_t value) { return value; }
static bool gba_net_start(struct GBASIODriver* driver) {
    auto* drv = reinterpret_cast<MultiplayerManager::NetGbaDriver*>(driver);
    if (!drv->mgr) return false;
    int m = drv->mgr->active_mode();
    if (m == STORM_LINK_WIRELESS_HOST || m == STORM_LINK_WIRELESS_CLIENT || m == STORM_LINK_LOCAL_LOOPBACK) {
        return true;
    }
    return drv->mgr->connected_devices() > 0;
}
static void gba_net_finish_multi(struct GBASIODriver* driver, uint16_t data[4]) {
    auto* drv = reinterpret_cast<MultiplayerManager::NetGbaDriver*>(driver);
    if (!drv->mgr || !driver->p || !driver->p->p) {
        data[0] = data[1] = data[2] = data[3] = 0xFFFF;
        return;
    }
    uint16_t local_send = driver->p->p->memory.io[GBA_REG_SIOMLT_SEND >> 1];
    drv->mgr->exchange_gba_multi(local_send, data);
}
static uint8_t gba_net_finish_normal8(struct GBASIODriver* driver) {
    auto* drv = reinterpret_cast<MultiplayerManager::NetGbaDriver*>(driver);
    if (!drv->mgr || !driver->p || !driver->p->p) return 0xFF;
    uint8_t local_send = static_cast<uint8_t>(driver->p->p->memory.io[GBA_REG_SIODATA8 >> 1] & 0xFF);
    return drv->mgr->exchange_gba_normal8(local_send);
}
static uint32_t gba_net_finish_normal32(struct GBASIODriver* driver) {
    auto* drv = reinterpret_cast<MultiplayerManager::NetGbaDriver*>(driver);
    if (!drv->mgr || !driver->p || !driver->p->p) return 0xFFFFFFFFu;
    uint32_t lo = driver->p->p->memory.io[GBA_REG_SIODATA32_LO >> 1];
    uint32_t hi = driver->p->p->memory.io[GBA_REG_SIODATA32_HI >> 1];
    uint32_t local_send = lo | (hi << 16);
    return drv->mgr->exchange_gba_normal32(local_send);
}

} // namespace

void MultiplayerManager::RfuState::reset() {
    initialized = false;
    state = 0;
    cmd_id = 0;
    param_len = 0;
    cmd_params.clear();
    resp_queue.clear();
    pending_rx_words.clear();
    broadcasting = false;
}

uint32_t MultiplayerManager::RfuState::process_word(uint32_t in_word, MultiplayerManager* mgr) {
    // Nintendo AGB-015 Wireless Adapter RFU 32-bit framing:
    // Host sends 0x9966LLCC (LL = param words count, CC = command byte), then LL param words, then polls 0x80000000 for reply 0x9966LL(CC|0x80).
    if (in_word == 0x80000000u) {
        if (!resp_queue.empty()) {
            uint32_t w = resp_queue.front();
            resp_queue.pop_front();
            return w;
        }
        return 0x99660080u;
    }

    if (state == 1) {
        cmd_params.push_back(in_word);
        if (cmd_params.size() >= param_len) {
            state = 0;
            // Process RFU command with params
            uint8_t ack = static_cast<uint8_t>(cmd_id | 0x80u);
            switch (cmd_id) {
                case 0x24: // Send Data over Wireless Adapter
                    if (mgr && !cmd_params.empty()) mgr->send_rfu_payload(cmd_params);
                    resp_queue.push_back(0x99660000u | ack);
                    break;
                case 0x17: // Setup Broadcast Data
                    broadcasting = true;
                    resp_queue.push_back(0x99660000u | ack);
                    break;
                default:
                    resp_queue.push_back(0x99660000u | ack);
                    break;
            }
            cmd_params.clear();
        }
        return 0x80000000u;
    }

    if ((in_word & 0xFFFF0000u) == 0x99660000u) {
        param_len = static_cast<uint8_t>((in_word >> 8) & 0xFFu);
        cmd_id = static_cast<uint8_t>(in_word & 0xFFu);
        cmd_params.clear();
        resp_queue.clear();

        if (param_len > 0) {
            state = 1;
            return 0x80000000u;
        }

        uint8_t ack = static_cast<uint8_t>(cmd_id | 0x80u);
        switch (cmd_id) {
            case 0x10: // Initialize RFU
                initialized = true;
                resp_queue.push_back(0x99660000u | ack);
                break;
            case 0x11: // Get System Status
                resp_queue.push_back(0x99660100u | ack);
                resp_queue.push_back(static_cast<uint32_t>(local_id) | (broadcasting ? 0x00010000u : 0u));
                break;
            case 0x13: // Get Slot Status
                resp_queue.push_back(0x99660100u | ack);
                resp_queue.push_back((mgr && mgr->connected_devices() > 0) ? 0x00000001u : 0u);
                break;
            case 0x14: // Config Status
                resp_queue.push_back(0x99660000u | ack);
                break;
            case 0x16: // Start Broadcast
                broadcasting = true;
                resp_queue.push_back(0x99660000u | ack);
                break;
            case 0x1B: // Poll Search Parent
                if (mgr && mgr->connected_devices() > 0) {
                    resp_queue.push_back(0x99660200u | ack);
                    resp_queue.push_back(0x00011001u);
                    resp_queue.push_back(0x5354524Du); // "STRM"
                } else {
                    resp_queue.push_back(0x99660000u | ack);
                }
                break;
            case 0x26: // Receive Data
            {
                std::vector<uint32_t> rx;
                if (mgr && mgr->pop_rfu_payload(rx) && !rx.empty()) {
                    uint32_t cnt = static_cast<uint32_t>(std::min<size_t>(rx.size(), 32));
                    resp_queue.push_back(0x99660000u | (cnt << 8) | ack);
                    for (uint32_t i = 0; i < cnt; ++i) resp_queue.push_back(rx[i]);
                } else {
                    resp_queue.push_back(0x99660000u | ack);
                }
                break;
            }
            case 0x3D: // Reset RFU
                reset();
                resp_queue.push_back(0x99660000u | ack);
                break;
            default:
                resp_queue.push_back(0x99660000u | ack);
                break;
        }
        return 0x80000000u;
    }

    return 0xFFFFFFFFu;
}

MultiplayerManager::MultiplayerManager() {
    net_init();
    std::memset(&gb_driver_, 0, sizeof(gb_driver_));
    gb_driver_.d.init = gb_net_init;
    gb_driver_.d.deinit = gb_net_deinit;
    gb_driver_.d.writeSB = gb_net_write_sb;
    gb_driver_.d.writeSC = gb_net_write_sc;
    gb_driver_.mgr = this;

    std::memset(&gba_driver_, 0, sizeof(gba_driver_));
    gba_driver_.d.init = gba_net_init;
    gba_driver_.d.deinit = gba_net_deinit;
    gba_driver_.d.reset = gba_net_reset;
    gba_driver_.d.setMode = gba_net_set_mode;
    gba_driver_.d.handlesMode = gba_net_handles_mode;
    gba_driver_.d.connectedDevices = gba_net_connected_devices;
    gba_driver_.d.deviceId = gba_net_device_id;
    gba_driver_.d.writeSIOCNT = gba_net_write_siocnt;
    gba_driver_.d.writeRCNT = gba_net_write_rcnt;
    gba_driver_.d.start = gba_net_start;
    gba_driver_.d.finishMultiplayer = gba_net_finish_multi;
    gba_driver_.d.finishNormal8 = gba_net_finish_normal8;
    gba_driver_.d.finishNormal32 = gba_net_finish_normal32;
    gba_driver_.mgr = this;

    std::memset(&printer_driver_, 0, sizeof(printer_driver_));
    GBPrinterCreate(&printer_driver_.printer);
    printer_driver_.printer.print = gb_printer_print_cb;
    printer_driver_.mgr = this;

    GBASIODolphinCreate(&dolphin_driver_);
}

MultiplayerManager::~MultiplayerManager() {
    stop(nullptr);
    GBASIODolphinDestroy(&dolphin_driver_);
    net_deinit();
}

void MultiplayerManager::close_sockets() {
    if (listen_sock_ >= 0) { STORM_CLOSE_SOCK(listen_sock_); listen_sock_ = -1; }
    if (client_sock_ >= 0) { STORM_CLOSE_SOCK(client_sock_); client_sock_ = -1; }
    for (int i = 0; i < 3; ++i) {
        if (peer_socks_[i] >= 0) { STORM_CLOSE_SOCK(peer_socks_[i]); peer_socks_[i] = -1; }
    }
    if (beacon_sock_ >= 0) { STORM_CLOSE_SOCK(beacon_sock_); beacon_sock_ = -1; }
}

void MultiplayerManager::attach_to_core(struct mCore* core, int platform) {
    if (!core) return;
    int m = mode_.load(std::memory_order_acquire);
    if (m == STORM_LINK_NONE) return;

    if (platform == STORM_PLATFORM_GBA && core->platform(core) == mPLATFORM_GBA) {
        auto* gba = static_cast<struct GBA*>(core->board);
        if (!gba) return;
        if (m == STORM_LINK_DOLPHIN_JOYBUS) {
            GBASIOSetDriver(&gba->sio, &dolphin_driver_.d);
            dolphin_attached_ = true;
        } else if (m != STORM_LINK_GB_PRINTER) {
            GBASIOSetDriver(&gba->sio, &gba_driver_.d);
        }
    } else if (core->platform(core) == mPLATFORM_GB) {
        auto* gb = static_cast<struct GB*>(core->board);
        if (!gb) return;
        if (m == STORM_LINK_GB_PRINTER) {
            GBSIOSetDriver(&gb->sio, &printer_driver_.printer.d);
        } else if (m != STORM_LINK_DOLPHIN_JOYBUS) {
            GBSIOSetDriver(&gb->sio, &gb_driver_.d);
        }
    }
}

void MultiplayerManager::detach_from_core(struct mCore* core) {
    if (!core) return;
    if (core->platform(core) == mPLATFORM_GBA) {
        auto* gba = static_cast<struct GBA*>(core->board);
        if (gba) GBASIOSetDriver(&gba->sio, nullptr);
    } else if (core->platform(core) == mPLATFORM_GB) {
        auto* gb = static_cast<struct GB*>(core->board);
        if (gb) GBSIOSetDriver(&gb->sio, nullptr);
    }
    dolphin_attached_ = false;
}

int MultiplayerManager::start(struct mCore* core, int platform, const char* game_title,
                              int mode, const char* host_ip, int port, int clock_port, const char* room_name) {
    stop(core);

    if (mode <= STORM_LINK_NONE || mode > STORM_LINK_LOCAL_LOOPBACK) {
        return 0;
    }

    {
        std::lock_guard<std::mutex> lk(mu_);
        platform_ = platform;
        game_title_ = (game_title && game_title[0]) ? game_title : "STORM GBCA";
        host_ip_ = (host_ip && host_ip[0]) ? host_ip : "127.0.0.1";
        port_ = (port > 0 && port < 65535) ? port : (mode == STORM_LINK_DOLPHIN_JOYBUS ? 54970 : 54320);
        clock_port_ = (clock_port > 0 && clock_port < 65535) ? clock_port : 49420;
        room_name_ = (room_name && room_name[0]) ? room_name : "STORM GBCA Room";
        peer_info_.clear();
        status_text_ = "Starting...";
    }

    rfu_.reset();
    {
        std::lock_guard<std::mutex> xlk(xfer_mu_);
        last_gb_peer_byte_ = 0xFF;
        has_gb_peer_byte_ = false;
        multi_words_[0] = multi_words_[1] = multi_words_[2] = multi_words_[3] = 0xFFFF;
        normal_peer_word_ = 0xFFFFFFFFu;
        rfu_inbox_.clear();
    }

    if (mode == STORM_LINK_GB_PRINTER) {
        mode_.store(mode, std::memory_order_release);
        connected_.store(1, std::memory_order_release);
        player_id_.store(0, std::memory_order_release);
        player_count_.store(1, std::memory_order_release);
        {
            std::lock_guard<std::mutex> lk(mu_);
            status_text_ = "Game Boy Printer attached (ready)";
            peer_info_ = "GB Printer";
        }
        attach_to_core(core, platform);
        return 0;
    }

    if (mode == STORM_LINK_LOCAL_LOOPBACK) {
        mode_.store(mode, std::memory_order_release);
        connected_.store(1, std::memory_order_release);
        player_id_.store(0, std::memory_order_release);
        player_count_.store(2, std::memory_order_release);
        ping_ms_.store(0, std::memory_order_release);
        {
            std::lock_guard<std::mutex> lk(mu_);
            status_text_ = "Local Loopback Link active (2P)";
            peer_info_ = "127.0.0.1 (Loopback)";
        }
        attach_to_core(core, platform);
        return 0;
    }

    if (mode == STORM_LINK_DOLPHIN_JOYBUS) {
        struct Address addr{};
        std::memset(&addr, 0, sizeof(addr));
        addr.version = IPV4;
        in_addr parsed{};
        if (inet_pton(AF_INET, host_ip_.c_str(), &parsed) != 1) {
            inet_pton(AF_INET, "127.0.0.1", &parsed);
        }
        addr.ipv4 = ntohl(parsed.s_addr);

        GBASIODolphinDestroy(&dolphin_driver_);
        GBASIODolphinCreate(&dolphin_driver_);
        bool ok = GBASIODolphinConnect(&dolphin_driver_, &addr,
                                       static_cast<short>(port_),
                                       static_cast<short>(clock_port_));
        if (!ok) {
            std::lock_guard<std::mutex> lk(mu_);
            status_text_ = "Dolphin JoyBus connection refused (" + host_ip_ + ":" + std::to_string(port_) + ")";
            return -1;
        }
        mode_.store(mode, std::memory_order_release);
        connected_.store(1, std::memory_order_release);
        player_id_.store(0, std::memory_order_release);
        player_count_.store(2, std::memory_order_release);
        {
            std::lock_guard<std::mutex> lk(mu_);
            status_text_ = "Dolphin JoyBus connected (" + host_ip_ + ":" + std::to_string(port_) + ")";
            peer_info_ = host_ip_ + ":" + std::to_string(port_);
        }
        attach_to_core(core, platform);
        return 0;
    }

    bool is_host = (mode == STORM_LINK_LAN_HOST || mode == STORM_LINK_WIRELESS_HOST || mode == STORM_LINK_IR_HOST);
    if (is_host) {
        storm_sock_t ls = socket(AF_INET, SOCK_STREAM, 0);
        if (ls == kInvalidSock) {
            std::lock_guard<std::mutex> lk(mu_);
            status_text_ = "Failed to open TCP socket";
            return -1;
        }
        int reuse = 1;
        setsockopt(ls, SOL_SOCKET, SO_REUSEADDR, reinterpret_cast<const char*>(&reuse), sizeof(reuse));
        sockaddr_in sin{};
        sin.sin_family = AF_INET;
        sin.sin_addr.s_addr = INADDR_ANY;
        sin.sin_port = htons(static_cast<uint16_t>(port_));
        if (bind(ls, reinterpret_cast<sockaddr*>(&sin), sizeof(sin)) != 0 || listen(ls, 4) != 0) {
            STORM_CLOSE_SOCK(ls);
            std::lock_guard<std::mutex> lk(mu_);
            status_text_ = "Port " + std::to_string(port_) + " busy";
            return -1;
        }
        listen_sock_ = static_cast<int64_t>(ls);
        set_nonblocking(listen_sock_, true);

        // Bind UDP discovery beacon socket synchronously before returning so discovery works immediately
        storm_sock_t us = socket(AF_INET, SOCK_DGRAM, 0);
        if (us != kInvalidSock) {
            int r2 = 1;
            setsockopt(us, SOL_SOCKET, SO_REUSEADDR, reinterpret_cast<const char*>(&r2), sizeof(r2));
            int bc = 1;
            setsockopt(us, SOL_SOCKET, SO_BROADCAST, reinterpret_cast<const char*>(&bc), sizeof(bc));
            sockaddr_in bbind{};
            bbind.sin_family = AF_INET;
            bbind.sin_addr.s_addr = INADDR_ANY;
            bbind.sin_port = htons(kDiscoveryUdpPort);
            if (bind(us, reinterpret_cast<sockaddr*>(&bbind), sizeof(bbind)) == 0) {
                set_nonblocking(static_cast<int64_t>(us), true);
                beacon_sock_ = static_cast<int64_t>(us);
            } else {
                STORM_CLOSE_SOCK(us);
            }
        }

        mode_.store(mode, std::memory_order_release);
        running_.store(true, std::memory_order_release);
        connected_.store(1, std::memory_order_release);
        player_id_.store(0, std::memory_order_release);
        player_count_.store(1, std::memory_order_release);
        {
            std::lock_guard<std::mutex> lk(mu_);
            status_text_ = "Hosting room on port " + std::to_string(port_) + " (waiting for players)";
            peer_info_ = "Host :" + std::to_string(port_);
        }
        attach_to_core(core, platform);
        net_thread_ = std::thread(&MultiplayerManager::host_loop, this);
        beacon_thread_ = std::thread(&MultiplayerManager::beacon_loop, this);
        return 0;
    } else {
        storm_sock_t cs = socket(AF_INET, SOCK_STREAM, 0);
        if (cs == kInvalidSock) {
            std::lock_guard<std::mutex> lk(mu_);
            status_text_ = "Failed to open client TCP socket";
            return -1;
        }
        sockaddr_in sin{};
        sin.sin_family = AF_INET;
        sin.sin_port = htons(static_cast<uint16_t>(port_));
        if (inet_pton(AF_INET, host_ip_.c_str(), &sin.sin_addr) != 1) {
            STORM_CLOSE_SOCK(cs);
            std::lock_guard<std::mutex> lk(mu_);
            status_text_ = "Invalid IP address: " + host_ip_;
            return -1;
        }
        // Non-blocking connect with 2.5s timeout so UI never freezes
        set_nonblocking(static_cast<int64_t>(cs), true);
        int rc = connect(cs, reinterpret_cast<sockaddr*>(&sin), sizeof(sin));
        bool ok = (rc == 0);
        if (!ok) {
            fd_set wfds;
            FD_ZERO(&wfds);
            FD_SET(cs, &wfds);
            timeval tv{2, 500000};
            if (select(static_cast<int>(cs) + 1, nullptr, &wfds, nullptr, &tv) > 0) {
                int so_err = 0;
                socklen_t slen = sizeof(so_err);
                getsockopt(cs, SOL_SOCKET, SO_ERROR, reinterpret_cast<char*>(&so_err), &slen);
                ok = (so_err == 0);
            }
        }
        if (!ok) {
            STORM_CLOSE_SOCK(cs);
            std::lock_guard<std::mutex> lk(mu_);
            status_text_ = "Could not connect to " + host_ip_ + ":" + std::to_string(port_);
            return -1;
        }
        set_tcp_nodelay(static_cast<int64_t>(cs));
        client_sock_ = static_cast<int64_t>(cs);

        mode_.store(mode, std::memory_order_release);
        running_.store(true, std::memory_order_release);
        connected_.store(1, std::memory_order_release);
        player_id_.store(1, std::memory_order_release);
        player_count_.store(2, std::memory_order_release);
        {
            std::lock_guard<std::mutex> lk(mu_);
            status_text_ = "Connected to " + host_ip_ + ":" + std::to_string(port_);
            peer_info_ = host_ip_ + ":" + std::to_string(port_);
        }
        attach_to_core(core, platform);
        net_thread_ = std::thread(&MultiplayerManager::client_loop, this);
        return 0;
    }
}

void MultiplayerManager::stop(struct mCore* core) {
    running_.store(false, std::memory_order_release);
    close_sockets();
    if (net_thread_.joinable()) net_thread_.join();
    if (beacon_thread_.joinable()) beacon_thread_.join();
    detach_from_core(core);
    if (mode_.load(std::memory_order_acquire) == STORM_LINK_DOLPHIN_JOYBUS) {
        GBASIODolphinDestroy(&dolphin_driver_);
        GBASIODolphinCreate(&dolphin_driver_);
    }
    mode_.store(STORM_LINK_NONE, std::memory_order_release);
    connected_.store(0, std::memory_order_release);
    player_id_.store(0, std::memory_order_release);
    player_count_.store(1, std::memory_order_release);
    ping_ms_.store(0, std::memory_order_release);
    std::lock_guard<std::mutex> lk(mu_);
    status_text_ = "Disconnected";
    peer_info_.clear();
}

void MultiplayerManager::on_core_reloaded(struct mCore* core, int platform, const char* game_title) {
    {
        std::lock_guard<std::mutex> lk(mu_);
        platform_ = platform;
        if (game_title && game_title[0]) game_title_ = game_title;
    }
    if (mode_.load(std::memory_order_acquire) != STORM_LINK_NONE) {
        attach_to_core(core, platform);
    }
}

void MultiplayerManager::tick_frame() {
    if (mode_.load(std::memory_order_acquire) == STORM_LINK_DOLPHIN_JOYBUS) {
        connected_.store(GBASIODolphinIsConnected(&dolphin_driver_) ? 1 : 0, std::memory_order_release);
    }
}

void MultiplayerManager::host_loop() {
    while (running_.load(std::memory_order_acquire)) {
        // 1. Accept incoming clients up to 3 (4 players total)
        if (listen_sock_ >= 0) {
            sockaddr_in cli_addr{};
            socklen_t clen = sizeof(cli_addr);
            storm_sock_t acc = accept(static_cast<storm_sock_t>(listen_sock_),
                                      reinterpret_cast<sockaddr*>(&cli_addr), &clen);
            if (acc != kInvalidSock) {
                int slot = -1;
                {
                    std::lock_guard<std::mutex> lk(mu_);
                    for (int i = 0; i < 3; ++i) {
                        if (peer_socks_[i] < 0) { slot = i; break; }
                    }
                    if (slot >= 0) {
                        set_nonblocking(static_cast<int64_t>(acc), true);
                        set_tcp_nodelay(static_cast<int64_t>(acc));
                        peer_socks_[slot] = static_cast<int64_t>(acc);
                    }
                }
                if (slot >= 0) {
                    int assigned_id = slot + 1;
                    NetPacket assign{kPktMagic0, kPktMagic1, PKT_ASSIGN_ID, static_cast<uint8_t>(assigned_id), 0, 0, {0, 0, 0, 0}};
                    send_packet(peer_socks_[slot], assign);

                    int active = 1;
                    char ipbuf[64] = {};
                    inet_ntop(AF_INET, &cli_addr.sin_addr, ipbuf, sizeof(ipbuf));
                    {
                        std::lock_guard<std::mutex> lk(mu_);
                        for (int i = 0; i < 3; ++i) if (peer_socks_[i] >= 0) active++;
                        player_count_.store(active, std::memory_order_release);
                        peer_info_ = std::string(ipbuf) + " (" + std::to_string(active) + "P)";
                        status_text_ = "Host active - " + std::to_string(active) + " player(s) linked";
                    }
                    // Broadcast updated player count
                    NetPacket cnt{kPktMagic0, kPktMagic1, PKT_PEER_COUNT, 0, static_cast<uint32_t>(active), 0, {0, 0, 0, 0}};
                    for (int i = 0; i < 3; ++i) {
                        if (peer_socks_[i] >= 0) send_packet(peer_socks_[i], cnt);
                    }
                } else {
                    STORM_CLOSE_SOCK(acc);
                }
            }
        }

        // 2. Poll connected clients for packets
        bool any_rx = false;
        for (int i = 0; i < 3; ++i) {
            int64_t ps = peer_socks_[i];
            if (ps < 0) continue;
            NetPacket pkt{};
            int r = recv_packet_nonblock(ps, pkt);
            if (r < 0) {
                std::lock_guard<std::mutex> lk(mu_);
                STORM_CLOSE_SOCK(ps);
                peer_socks_[i] = -1;
                int active = 1;
                for (int j = 0; j < 3; ++j) if (peer_socks_[j] >= 0) active++;
                player_count_.store(active, std::memory_order_release);
                status_text_ = (active > 1)
                    ? ("Host active - " + std::to_string(active) + " player(s) linked")
                    : ("Hosting room on port " + std::to_string(port_) + " (waiting for players)");
                continue;
            }
            if (r > 0) {
                any_rx = true;
                std::lock_guard<std::mutex> xlk(xfer_mu_);
                if (pkt.type == PKT_GB_BYTE) {
                    last_gb_peer_byte_ = static_cast<uint8_t>(pkt.value0 & 0xFF);
                    has_gb_peer_byte_ = true;
                } else if (pkt.type == PKT_GBA_MULTI) {
                    int pid = std::clamp<int>(i + 1, 1, 3);
                    multi_words_[pid] = static_cast<uint16_t>(pkt.value0 & 0xFFFF);
                } else if (pkt.type == PKT_GBA_NORMAL) {
                    normal_peer_word_ = pkt.value0;
                } else if (pkt.type == PKT_RFU_DATA) {
                    std::vector<uint32_t> words;
                    words.push_back(pkt.value0);
                    if (pkt.value1 != 0) words.push_back(pkt.value1);
                    if (rfu_inbox_.size() < 64) rfu_inbox_.push_back(std::move(words));
                    // Relay to other clients
                    for (int j = 0; j < 3; ++j) {
                        if (j != i && peer_socks_[j] >= 0) send_packet(peer_socks_[j], pkt);
                    }
                } else if (pkt.type == PKT_PING) {
                    NetPacket pong{kPktMagic0, kPktMagic1, PKT_PONG, 0, pkt.value0, 0, {0, 0, 0, 0}};
                    send_packet(ps, pong);
                }
            }
        }

        if (!any_rx) {
            std::this_thread::sleep_for(std::chrono::milliseconds(1));
        }
    }
}

void MultiplayerManager::client_loop() {
    auto last_ping = std::chrono::steady_clock::now();
    while (running_.load(std::memory_order_acquire) && client_sock_ >= 0) {
        NetPacket pkt{};
        int r = recv_packet_nonblock(client_sock_, pkt);
        if (r < 0) {
            connected_.store(0, std::memory_order_release);
            std::lock_guard<std::mutex> lk(mu_);
            status_text_ = "Host disconnected";
            break;
        }
        if (r > 0) {
            if (pkt.type == PKT_ASSIGN_ID) {
                player_id_.store(std::clamp<int>(pkt.player_id, 1, 3), std::memory_order_release);
            } else if (pkt.type == PKT_PEER_COUNT) {
                player_count_.store(std::clamp<int>(static_cast<int>(pkt.value0), 1, 4), std::memory_order_release);
            } else if (pkt.type == PKT_GB_BYTE) {
                std::lock_guard<std::mutex> xlk(xfer_mu_);
                last_gb_peer_byte_ = static_cast<uint8_t>(pkt.value0 & 0xFF);
                has_gb_peer_byte_ = true;
            } else if (pkt.type == PKT_GBA_MULTI) {
                std::lock_guard<std::mutex> xlk(xfer_mu_);
                for (int i = 0; i < 4; ++i) multi_words_[i] = pkt.multi[i];
            } else if (pkt.type == PKT_GBA_NORMAL) {
                std::lock_guard<std::mutex> xlk(xfer_mu_);
                normal_peer_word_ = pkt.value0;
            } else if (pkt.type == PKT_RFU_DATA) {
                std::lock_guard<std::mutex> xlk(xfer_mu_);
                std::vector<uint32_t> words;
                words.push_back(pkt.value0);
                if (pkt.value1 != 0) words.push_back(pkt.value1);
                if (rfu_inbox_.size() < 64) rfu_inbox_.push_back(std::move(words));
            } else if (pkt.type == PKT_PONG) {
                auto now_ms = static_cast<uint32_t>(
                    std::chrono::duration_cast<std::chrono::milliseconds>(
                        std::chrono::steady_clock::now().time_since_epoch()).count());
                int rtt = static_cast<int>(now_ms - pkt.value0);
                if (rtt >= 0 && rtt < 5000) ping_ms_.store(rtt, std::memory_order_release);
            }
        } else {
            auto now = std::chrono::steady_clock::now();
            if (std::chrono::duration_cast<std::chrono::seconds>(now - last_ping).count() >= 2) {
                last_ping = now;
                uint32_t ts = static_cast<uint32_t>(
                    std::chrono::duration_cast<std::chrono::milliseconds>(now.time_since_epoch()).count());
                NetPacket ping{kPktMagic0, kPktMagic1, PKT_PING, static_cast<uint8_t>(player_id_.load()), ts, 0, {0, 0, 0, 0}};
                send_packet(client_sock_, ping);
            }
            std::this_thread::sleep_for(std::chrono::milliseconds(1));
        }
    }
}

void MultiplayerManager::beacon_loop() {
    if (beacon_sock_ < 0) return;
    storm_sock_t us = static_cast<storm_sock_t>(beacon_sock_);

    auto make_announce = [&]() -> std::string {
        std::lock_guard<std::mutex> lk(mu_);
        int max_p = (platform_ == STORM_PLATFORM_GBA) ? 4 : 2;
        char buf[256];
        std::snprintf(buf, sizeof(buf), "STORM_GBCA_ROOM\t%d\t%d\t%d\t%d\t%d\t%s\t%s",
                      port_, mode_.load(), platform_, player_count_.load(), max_p,
                      room_name_.c_str(), game_title_.c_str());
        return std::string(buf);
    };

    auto last_bcast = std::chrono::steady_clock::now() - std::chrono::seconds(5);
    while (running_.load(std::memory_order_acquire)) {
        // Respond immediately to discovery probes
        char rxbuf[128] = {};
        sockaddr_in from{};
        socklen_t flen = sizeof(from);
        int n = static_cast<int>(recvfrom(us, rxbuf, sizeof(rxbuf) - 1, 0,
                                          reinterpret_cast<sockaddr*>(&from), &flen));
        if (n > 0 && std::strncmp(rxbuf, "STORM_GBCA_DISCOVER", 19) == 0) {
            std::string reply = make_announce();
            sendto(us, reply.c_str(), static_cast<int>(reply.size()), 0,
                   reinterpret_cast<sockaddr*>(&from), flen);
        }

        auto now = std::chrono::steady_clock::now();
        if (std::chrono::duration_cast<std::chrono::milliseconds>(now - last_bcast).count() >= 1200) {
            last_bcast = now;
            std::string ann = make_announce();
            sockaddr_in baddr{};
            baddr.sin_family = AF_INET;
            baddr.sin_addr.s_addr = htonl(INADDR_BROADCAST);
            baddr.sin_port = htons(kDiscoveryUdpPort);
            sendto(us, ann.c_str(), static_cast<int>(ann.size()), 0,
                   reinterpret_cast<sockaddr*>(&baddr), sizeof(baddr));
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(50));
    }
}

void MultiplayerManager::send_rfu_payload(const std::vector<uint32_t>& words) {
    if (words.empty()) return;
    int m = mode_.load(std::memory_order_acquire);
    if (m == STORM_LINK_LOCAL_LOOPBACK) {
        std::lock_guard<std::mutex> xlk(xfer_mu_);
        if (rfu_inbox_.size() < 64) rfu_inbox_.push_back(words);
        return;
    }
    NetPacket pkt{kPktMagic0, kPktMagic1, PKT_RFU_DATA, static_cast<uint8_t>(player_id_.load()),
                  words[0], words.size() > 1 ? words[1] : 0u, {0, 0, 0, 0}};
    if (m == STORM_LINK_WIRELESS_HOST) {
        for (int i = 0; i < 3; ++i) if (peer_socks_[i] >= 0) send_packet(peer_socks_[i], pkt);
    } else if (client_sock_ >= 0) {
        send_packet(client_sock_, pkt);
    }
}

bool MultiplayerManager::pop_rfu_payload(std::vector<uint32_t>& out_words) {
    std::lock_guard<std::mutex> xlk(xfer_mu_);
    if (rfu_inbox_.empty()) return false;
    out_words = std::move(rfu_inbox_.front());
    rfu_inbox_.pop_front();
    return true;
}

uint8_t MultiplayerManager::exchange_gb_byte(uint8_t out_byte, bool) {
    int m = mode_.load(std::memory_order_acquire);
    if (m == STORM_LINK_LOCAL_LOOPBACK) return out_byte;

    NetPacket pkt{kPktMagic0, kPktMagic1, PKT_GB_BYTE, static_cast<uint8_t>(player_id_.load()),
                  out_byte, 0, {0, 0, 0, 0}};
    if (m == STORM_LINK_LAN_HOST || m == STORM_LINK_IR_HOST) {
        if (peer_socks_[0] >= 0) send_packet(peer_socks_[0], pkt);
    } else if (client_sock_ >= 0) {
        send_packet(client_sock_, pkt);
    }

    // Bounded short wait (max 4 ms) so emulation stays smooth even if remote peer lags
    for (int iter = 0; iter < 4; ++iter) {
        {
            std::lock_guard<std::mutex> xlk(xfer_mu_);
            if (has_gb_peer_byte_) {
                has_gb_peer_byte_ = false;
                return last_gb_peer_byte_;
            }
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(1));
    }
    return 0xFF;
}

void MultiplayerManager::exchange_gba_multi(uint16_t local_send, uint16_t out_data[4]) {
    int m = mode_.load(std::memory_order_acquire);
    if (m == STORM_LINK_LOCAL_LOOPBACK) {
        out_data[0] = local_send;
        out_data[1] = local_send;
        out_data[2] = 0xFFFF;
        out_data[3] = 0xFFFF;
        return;
    }

    int pid = std::clamp<int>(player_id_.load(std::memory_order_acquire), 0, 3);
    if (m == STORM_LINK_LAN_HOST || m == STORM_LINK_WIRELESS_HOST) {
        NetPacket bcast{kPktMagic0, kPktMagic1, PKT_GBA_MULTI, 0, local_send, 0, {0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF}};
        {
            std::lock_guard<std::mutex> xlk(xfer_mu_);
            multi_words_[0] = local_send;
            for (int i = 0; i < 4; ++i) {
                bcast.multi[i] = multi_words_[i];
                out_data[i] = multi_words_[i];
            }
        }
        for (int i = 0; i < 3; ++i) {
            if (peer_socks_[i] >= 0) send_packet(peer_socks_[i], bcast);
        }
    } else {
        NetPacket pkt{kPktMagic0, kPktMagic1, PKT_GBA_MULTI, static_cast<uint8_t>(pid),
                      local_send, 0, {0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF}};
        if (client_sock_ >= 0) send_packet(client_sock_, pkt);
        std::lock_guard<std::mutex> xlk(xfer_mu_);
        multi_words_[pid] = local_send;
        for (int i = 0; i < 4; ++i) out_data[i] = multi_words_[i];
    }
}

uint8_t MultiplayerManager::exchange_gba_normal8(uint8_t local_send) {
    int m = mode_.load(std::memory_order_acquire);
    if (m == STORM_LINK_LOCAL_LOOPBACK) return local_send;
    NetPacket pkt{kPktMagic0, kPktMagic1, PKT_GBA_NORMAL, static_cast<uint8_t>(player_id_.load()),
                  local_send, 0, {0, 0, 0, 0}};
    if (m == STORM_LINK_LAN_HOST) {
        if (peer_socks_[0] >= 0) send_packet(peer_socks_[0], pkt);
    } else if (client_sock_ >= 0) {
        send_packet(client_sock_, pkt);
    }
    std::lock_guard<std::mutex> xlk(xfer_mu_);
    return static_cast<uint8_t>(normal_peer_word_ & 0xFF);
}

uint32_t MultiplayerManager::exchange_gba_normal32(uint32_t local_send) {
    int m = mode_.load(std::memory_order_acquire);
    if (m == STORM_LINK_WIRELESS_HOST || m == STORM_LINK_WIRELESS_CLIENT) {
        return rfu_.process_word(local_send, this);
    }
    if (m == STORM_LINK_LOCAL_LOOPBACK) return local_send;

    NetPacket pkt{kPktMagic0, kPktMagic1, PKT_GBA_NORMAL, static_cast<uint8_t>(player_id_.load()),
                  local_send, 0, {0, 0, 0, 0}};
    if (m == STORM_LINK_LAN_HOST) {
        if (peer_socks_[0] >= 0) send_packet(peer_socks_[0], pkt);
    } else if (client_sock_ >= 0) {
        send_packet(client_sock_, pkt);
    }
    std::lock_guard<std::mutex> xlk(xfer_mu_);
    return normal_peer_word_;
}

int MultiplayerManager::connected_devices() const {
    int pc = player_count_.load(std::memory_order_acquire);
    return pc > 1 ? (pc - 1) : 0;
}

int MultiplayerManager::device_id() const {
    return player_id_.load(std::memory_order_acquire);
}

void MultiplayerManager::on_gb_printer_print(int height, const uint8_t* data) {
    if (!data || height <= 0 || height > 256) return;
    static constexpr uint32_t kPaperPalette[4] = {
        0xFFE8F6F4u, // 0: warm paper white (RGBA: R=F4, G=F6, B=E8, A=FF)
        0xFF98B4A8u, // 1: light gray-green
        0xFF506254u, // 2: dark gray-green
        0xFF182018u, // 3: deep ink black
    };
    const int w = GB_VIDEO_HORIZONTAL_PIXELS; // 160
    PrinterSheet sheet;
    sheet.width = w;
    sheet.height = height;
    sheet.rgba.resize(static_cast<size_t>(w) * static_cast<size_t>(height));

    // mGBA printer buffer stores 4 pixels per byte (2 bits per pixel, MSB first)
    for (int y = 0; y < height; ++y) {
        const uint8_t* row = data + (y * (w / 4));
        for (int x = 0; x < w; ++x) {
            uint8_t packed = row[x >> 2];
            int shift = 6 - ((x & 3) * 2);
            uint8_t shade = (packed >> shift) & 0x03u;
            sheet.rgba[static_cast<size_t>(y) * w + x] = kPaperPalette[shade];
        }
    }

    std::lock_guard<std::mutex> lk(printer_mu_);
    if (printer_queue_.size() >= 8) printer_queue_.pop_front();
    printer_queue_.push_back(std::move(sheet));
}

int MultiplayerManager::poll_printer_image(uint32_t* dst_rgba, int max_pixels, int* out_w, int* out_h) {
    std::lock_guard<std::mutex> lk(printer_mu_);
    if (printer_queue_.empty()) return 0;
    const auto& front = printer_queue_.front();
    int total = front.width * front.height;
    if (out_w) *out_w = front.width;
    if (out_h) *out_h = front.height;
    if (!dst_rgba) return 1; // peek mode
    if (max_pixels < total) return -1;
    std::memcpy(dst_rgba, front.rgba.data(), static_cast<size_t>(total) * sizeof(uint32_t));
    printer_queue_.pop_front();
    return 1;
}

void MultiplayerManager::get_status(StormGbcaLinkStatus* out_status) const {
    if (!out_status) return;
    std::memset(out_status, 0, sizeof(*out_status));
    out_status->mode = mode_.load(std::memory_order_acquire);
    out_status->connected = connected_.load(std::memory_order_acquire);
    out_status->player_id = player_id_.load(std::memory_order_acquire);
    out_status->player_count = player_count_.load(std::memory_order_acquire);
    out_status->ping_ms = ping_ms_.load(std::memory_order_acquire);
    std::lock_guard<std::mutex> lk(mu_);
    out_status->port = port_;
    std::snprintf(out_status->room_name, sizeof(out_status->room_name), "%s", room_name_.c_str());
    std::snprintf(out_status->peer_info, sizeof(out_status->peer_info), "%s", peer_info_.c_str());
    std::snprintf(out_status->status_text, sizeof(out_status->status_text), "%s",
                  status_text_.empty() ? "Disconnected" : status_text_.c_str());
}

int MultiplayerManager::discover_lan_rooms(StormGbcaLanRoom* out_rooms, int max_rooms, int timeout_ms) {
    if (!out_rooms || max_rooms <= 0) return 0;
    net_init();
    storm_sock_t us = socket(AF_INET, SOCK_DGRAM, 0);
    if (us == kInvalidSock) {
        net_deinit();
        return 0;
    }
    int bcast = 1;
    setsockopt(us, SOL_SOCKET, SO_BROADCAST, reinterpret_cast<const char*>(&bcast), sizeof(bcast));
    set_nonblocking(static_cast<int64_t>(us), true);

    const char* probe = "STORM_GBCA_DISCOVER";
    sockaddr_in baddr{};
    baddr.sin_family = AF_INET;
    baddr.sin_addr.s_addr = htonl(INADDR_BROADCAST);
    baddr.sin_port = htons(kDiscoveryUdpPort);
    sendto(us, probe, static_cast<int>(std::strlen(probe)), 0,
           reinterpret_cast<sockaddr*>(&baddr), sizeof(baddr));

    // Also probe localhost directly for dual-window local discovery!
    sockaddr_in laddr{};
    laddr.sin_family = AF_INET;
    inet_pton(AF_INET, "127.0.0.1", &laddr.sin_addr);
    laddr.sin_port = htons(kDiscoveryUdpPort);
    sendto(us, probe, static_cast<int>(std::strlen(probe)), 0,
           reinterpret_cast<sockaddr*>(&laddr), sizeof(laddr));

    int found = 0;
    int clamped_ms = std::clamp(timeout_ms, 100, 3000);
    auto deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(clamped_ms);
    auto next_probe = std::chrono::steady_clock::now() + std::chrono::milliseconds(120);

    while (std::chrono::steady_clock::now() < deadline && found < max_rooms) {
        if (std::chrono::steady_clock::now() >= next_probe) {
            next_probe = std::chrono::steady_clock::now() + std::chrono::milliseconds(120);
            sendto(us, probe, static_cast<int>(std::strlen(probe)), 0,
                   reinterpret_cast<sockaddr*>(&baddr), sizeof(baddr));
            sendto(us, probe, static_cast<int>(std::strlen(probe)), 0,
                   reinterpret_cast<sockaddr*>(&laddr), sizeof(laddr));
        }
        char buf[512] = {};
        sockaddr_in from{};
        socklen_t flen = sizeof(from);
        int n = static_cast<int>(recvfrom(us, buf, sizeof(buf) - 1, 0,
                                          reinterpret_cast<sockaddr*>(&from), &flen));
        if (n > 0) {
            buf[n] = '\0';
            if (std::strncmp(buf, "STORM_GBCA_ROOM\t", 16) == 0) {
                char ipbuf[64] = {};
                inet_ntop(AF_INET, &from.sin_addr, ipbuf, sizeof(ipbuf));

                // Parse tab-separated fields
                std::vector<std::string> fields;
                const char* p = buf;
                while (*p) {
                    const char* t = std::strchr(p, '\t');
                    if (!t) { fields.emplace_back(p); break; }
                    fields.emplace_back(p, static_cast<size_t>(t - p));
                    p = t + 1;
                }
                if (fields.size() >= 8) {
                    int rport = std::atoi(fields[1].c_str());
                    bool dup = false;
                    for (int i = 0; i < found; ++i) {
                        if (std::strcmp(out_rooms[i].host_ip, ipbuf) == 0 && out_rooms[i].port == rport) {
                            dup = true;
                            break;
                        }
                    }
                    if (!dup) {
                        StormGbcaLanRoom& r = out_rooms[found++];
                        std::memset(&r, 0, sizeof(r));
                        std::snprintf(r.host_ip, sizeof(r.host_ip), "%s", ipbuf);
                        r.port = rport;
                        r.mode = std::atoi(fields[2].c_str());
                        r.platform = std::atoi(fields[3].c_str());
                        r.player_count = std::atoi(fields[4].c_str());
                        r.max_players = std::atoi(fields[5].c_str());
                        std::snprintf(r.room_name, sizeof(r.room_name), "%s", fields[6].c_str());
                        std::snprintf(r.game_title, sizeof(r.game_title), "%s", fields[7].c_str());
                    }
                }
            }
        } else {
            std::this_thread::sleep_for(std::chrono::milliseconds(15));
        }
    }

    STORM_CLOSE_SOCK(us);
    net_deinit();
    return found;
}

} // namespace storm
