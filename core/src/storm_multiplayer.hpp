#ifndef STORM_MULTIPLAYER_HPP
#define STORM_MULTIPLAYER_HPP

#include "storm_gbca_core.h"

extern "C" {
#include <mgba/core/core.h>
#include <mgba/internal/gb/gb.h>
#include <mgba/internal/gb/sio.h>
#include <mgba/internal/gb/sio/printer.h>
#include <mgba/internal/gba/gba.h>
#include <mgba/internal/gba/io.h>
#include <mgba/internal/gba/sio.h>
#include <mgba/internal/gba/sio/dolphin.h>
}

#include <atomic>
#include <cstdint>
#include <deque>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

namespace storm {

struct PrinterSheet {
    int width = 0;
    int height = 0;
    std::vector<uint32_t> rgba;
};

/**
 * Full-featured GB / GBC / GBA Link Cable, GBA Wireless Adapter (AGB-015 RFU),
 * GBC Infrared Link, Dolphin GameCube JoyBus Link, Game Boy Printer,
 * and UDP LAN Auto-Discovery engine.
 */
class MultiplayerManager {
public:
    MultiplayerManager();
    ~MultiplayerManager();

    int start(struct mCore* core, int platform, const char* game_title,
              int mode, const char* host_ip, int port, int clock_port, const char* room_name);
    void stop(struct mCore* core);
    void on_core_reloaded(struct mCore* core, int platform, const char* game_title);
    void tick_frame();

    void get_status(StormGbcaLinkStatus* out_status) const;
    int poll_printer_image(uint32_t* dst_rgba, int max_pixels, int* out_w, int* out_h);

    static int discover_lan_rooms(StormGbcaLanRoom* out_rooms, int max_rooms, int timeout_ms);

    // Internal driver hooks called from C SIO callbacks
    void on_gb_printer_print(int height, const uint8_t* data);
    uint8_t exchange_gb_byte(uint8_t out_byte, bool is_master);
    void exchange_gba_multi(uint16_t local_send, uint16_t out_data[4]);
    uint8_t exchange_gba_normal8(uint8_t local_send);
    uint32_t exchange_gba_normal32(uint32_t local_send);
    int connected_devices() const;
    int device_id() const;

    int active_mode() const { return mode_.load(std::memory_order_acquire); }

    struct NetGbDriver {
        struct GBSIODriver d;
        MultiplayerManager* mgr;
        uint8_t pending_out;
    };

    struct NetGbaDriver {
        struct GBASIODriver d;
        MultiplayerManager* mgr;
        enum GBASIOMode current_mode;
        uint32_t last_normal_out;
    };

    struct NetPrinterDriver {
        struct GBPrinter printer;
        MultiplayerManager* mgr;
    };

private:

    // AGB-015 Wireless Adapter RFU command state machine
    struct RfuState {
        bool initialized = false;
        int state = 0; // 0=idle, 1=cmd_rx, 2=resp_tx
        uint8_t cmd_id = 0;
        uint8_t param_len = 0;
        std::vector<uint32_t> cmd_params;
        std::deque<uint32_t> resp_queue;
        std::vector<uint32_t> pending_rx_words;
        bool broadcasting = false;
        uint16_t local_id = 0x1001;
        void reset();
        uint32_t process_word(uint32_t in_word, MultiplayerManager* mgr);
    };

    void attach_to_core(struct mCore* core, int platform);
    void detach_from_core(struct mCore* core);
    void close_sockets();
    void host_loop();
    void client_loop();
    void beacon_loop();
    void send_rfu_payload(const std::vector<uint32_t>& words);
    bool pop_rfu_payload(std::vector<uint32_t>& out_words);

    mutable std::mutex mu_;
    std::atomic<int> mode_{STORM_LINK_NONE};
    std::atomic<bool> running_{false};
    std::atomic<int> connected_{0};
    std::atomic<int> player_id_{0};
    std::atomic<int> player_count_{1};
    std::atomic<int> ping_ms_{0};

    int platform_ = STORM_PLATFORM_NONE;
    int port_ = 54320;
    int clock_port_ = 49420;
    std::string host_ip_;
    std::string room_name_;
    std::string game_title_;
    std::string peer_info_;
    std::string status_text_;

    // Platform socket handles stored as int64_t (-1 = invalid)
    int64_t listen_sock_ = -1;
    int64_t client_sock_ = -1;
    int64_t peer_socks_[3] = {-1, -1, -1};
    int64_t beacon_sock_ = -1;

    std::thread net_thread_;
    std::thread beacon_thread_;

    // Shared transfer exchange buffers
    std::mutex xfer_mu_;
    uint8_t last_gb_peer_byte_ = 0xFF;
    bool has_gb_peer_byte_ = false;
    uint16_t multi_words_[4] = {0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF};
    uint32_t normal_peer_word_ = 0xFFFFFFFFu;
    std::deque<std::vector<uint32_t>> rfu_inbox_;

    // SIO Driver instances
    NetGbDriver gb_driver_{};
    NetGbaDriver gba_driver_{};
    NetPrinterDriver printer_driver_{};
    struct GBASIODolphin dolphin_driver_{};
    bool dolphin_attached_ = false;
    RfuState rfu_;

    // Game Boy Printer output queue
    std::mutex printer_mu_;
    std::deque<PrinterSheet> printer_queue_;
};

} // namespace storm

#endif // STORM_MULTIPLAYER_HPP
