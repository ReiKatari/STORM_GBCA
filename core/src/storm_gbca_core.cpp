#include "storm_gbca_core.h"
#include "storm_internal.hpp"
#include "storm_multiplayer.hpp"
#include "storm_sameboy_bridge.h"

extern "C" {
#include <mgba/core/core.h>
#include <mgba/core/config.h>
#include <mgba/core/cheats.h>
#include <mgba/core/interface.h>
#include <mgba/core/log.h>
#include <mgba/core/serialize.h>
#include <mgba/gb/interface.h>
#include <mgba/gba/interface.h>
#include <mgba/internal/gb/gb.h>
#include <mgba/internal/gb/memory.h>
#include <mgba/internal/gba/gba.h>
#include <mgba/internal/gba/audio.h>
#include <mgba/internal/gba/memory.h>
#include <mgba-util/audio-buffer.h>
#include <mgba-util/vfs.h>
#include <mgba-util/hash.h>
#include <mgba-util/md5.h>
#include <mgba-util/crc32.h>
#include "rc_hash.h"
}

#include <algorithm>
#include <atomic>
#include <cctype>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <mutex>
#include <string>
#include <vector>

#ifdef _WIN32
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#endif

namespace {

static FILE* storm_fopen_utf8(const char* path, const char* mode) {
    if (!path || !path[0] || !mode) return nullptr;
#ifdef _WIN32
    int wlen = MultiByteToWideChar(CP_UTF8, 0, path, -1, nullptr, 0);
    int mlen = MultiByteToWideChar(CP_UTF8, 0, mode, -1, nullptr, 0);
    if (wlen > 0 && mlen > 0) {
        std::wstring wpath(static_cast<size_t>(wlen), L'\0');
        std::wstring wmode(static_cast<size_t>(mlen), L'\0');
        MultiByteToWideChar(CP_UTF8, 0, path, -1, &wpath[0], wlen);
        MultiByteToWideChar(CP_UTF8, 0, mode, -1, &wmode[0], mlen);
        FILE* f = _wfopen(wpath.c_str(), wmode.c_str());
        if (f) return f;
    }
#endif
    return std::fopen(path, mode);
}

static std::string to_lower_ext(const char* path) {
    if (!path) return {};
    std::string s(path);
    auto dot = s.rfind('.');
    if (dot == std::string::npos) return {};
    std::string ext = s.substr(dot);
    for (char& c : ext) c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
    return ext;
}

static bool read_file_bytes(const char* path, std::vector<uint8_t>& out) {
    if (!path || !path[0]) return false;
    FILE* f = storm_fopen_utf8(path, "rb");
    if (!f) return false;
    std::fseek(f, 0, SEEK_END);
    long sz = std::ftell(f);
    if (sz <= 0) {
        std::fclose(f);
        return false;
    }
    std::fseek(f, 0, SEEK_SET);
    out.resize(static_cast<size_t>(sz));
    size_t got = std::fread(out.data(), 1, out.size(), f);
    std::fclose(f);
    out.resize(got);
    return got > 0;
}

static bool write_file_bytes(const char* path, const void* data, size_t size) {
    if (!path || !path[0] || !data) return false;
    FILE* f = storm_fopen_utf8(path, "wb");
    if (!f) return false;
    size_t wrote = std::fwrite(data, 1, size, f);
    std::fclose(f);
    return wrote == size;
}

static bool load_rom_from_path_or_archive(const char* path, std::vector<uint8_t>& out, std::string& out_name) {
    std::string ext = to_lower_ext(path);
    if (ext == ".zip" || ext == ".7z") {
#if defined(ENABLE_VFS) && defined(ENABLE_DIRECTORIES)
        auto try_open_archive = [&](const char* archive_path) -> bool {
            struct VDir* dir = VDirOpenArchive(archive_path);
            if (!dir) return false;
            bool ok = false;
            for (struct VDirEntry* ent = dir->listNext(dir); ent; ent = dir->listNext(dir)) {
                const char* ename = ent->name(ent);
                std::string eext = to_lower_ext(ename);
                if (eext == ".gba" || eext == ".gb" || eext == ".gbc" || eext == ".sgb" || eext == ".bin") {
                    struct VFile* vf = dir->openFile(dir, ename, O_RDONLY);
                    if (vf) {
                        ssize_t sz = vf->size(vf);
                        if (sz > 0) {
                            out.resize(static_cast<size_t>(sz));
                            ssize_t rd = vf->read(vf, out.data(), out.size());
                            vf->close(vf);
                            if (rd > 0) {
                                out.resize(static_cast<size_t>(rd));
                                out_name = ename;
                                ok = true;
                                break;
                            }
                        } else {
                            vf->close(vf);
                        }
                    }
                }
            }
            dir->close(dir);
            return ok;
        };

        if (try_open_archive(path)) return true;

#ifdef _WIN32
        // Fallback for Unicode/Cyrillic archive paths on Windows via temporary ASCII path
        std::vector<uint8_t> raw_arch;
        if (read_file_bytes(path, raw_arch)) {
            char tmp_dir[MAX_PATH] = {};
            if (GetTempPathA(MAX_PATH, tmp_dir) > 0) {
                static std::atomic<uint32_t> tmp_seq{0};
                uint32_t seq = tmp_seq.fetch_add(1, std::memory_order_relaxed);
                std::string tmp_path = std::string(tmp_dir) + "storm_gbca_arch_" +
                    std::to_string(GetCurrentProcessId()) + "_" + std::to_string(seq) + ext;
                FILE* tf = std::fopen(tmp_path.c_str(), "wb");
                if (tf) {
                    size_t wr = std::fwrite(raw_arch.data(), 1, raw_arch.size(), tf);
                    std::fclose(tf);
                    bool ok = (wr == raw_arch.size()) && try_open_archive(tmp_path.c_str());
                    std::remove(tmp_path.c_str());
                    if (ok) return true;
                }
            }
        }
#endif
#endif
        return false;
    }
    if (read_file_bytes(path, out)) {
        out_name = path;
        return true;
    }
    return false;
}

static int detect_platform_from_buffer(const uint8_t* data, size_t size, const char* name_hint) {
    std::string ext = to_lower_ext(name_hint);
    if (ext == ".gba") return STORM_PLATFORM_GBA;
    if (ext == ".gbc") return STORM_PLATFORM_GBC;
    if (ext == ".sgb") return STORM_PLATFORM_SGB;

    // Check GBA header: ARM branch at 0x00 (byte 3 == 0xEA) and fixed 0x96 at 0xB2
    if (size >= 0xC0 && data[0xB2] == 0x96 && data[3] == 0xEA) {
        return STORM_PLATFORM_GBA;
    }

    // Check GB/GBC header at 0x104..0x14F
    if (size >= 0x150) {
        uint8_t cgb_flag = data[0x143];
        uint8_t sgb_flag = data[0x146];
        if (cgb_flag & 0x80) return STORM_PLATFORM_GBC;
        if (sgb_flag == 0x03 && data[0x14B] == 0x33) return STORM_PLATFORM_SGB;
        return STORM_PLATFORM_GB;
    }

    if (ext == ".gb") return STORM_PLATFORM_GB;
    return STORM_PLATFORM_NONE;
}

static void fill_rom_info_from_buffer(const uint8_t* data, size_t size, const char* name_hint,
                                        const char* core_name, StormGbcaRomInfo* out) {
    std::memset(out, 0, sizeof(*out));
    if (!data || size == 0) return;

    int plat = detect_platform_from_buffer(data, size, name_hint);
    out->platform = plat;
    out->rom_size = static_cast<uint32_t>(size);
    out->crc32 = storm::calc_crc32(data, size);

    // Compute MD5 via rcheevos or mGBA hash
    uint8_t md5_raw[16] = {};
    md5Buffer(data, size, md5_raw);
    for (int i = 0; i < 16; ++i) {
        snprintf(out->md5 + i * 2, 3, "%02x", md5_raw[i]);
    }
    out->md5[32] = '\0';

    if (core_name) {
        snprintf(out->active_core, sizeof(out->active_core), "%s", core_name);
    }

    if (plat == STORM_PLATFORM_GBA && size >= 0xC0) {
        size_t tlen = 12;
        for (size_t i = 0; i < tlen; ++i) {
            char c = static_cast<char>(data[0xA0 + i]);
            out->title[i] = (c >= 0x20 && c < 0x7F) ? c : '\0';
        }
        out->title[12] = '\0';
        for (size_t i = 0; i < 4; ++i) {
            char c = static_cast<char>(data[0xAC + i]);
            out->game_code[i] = (c >= 0x20 && c < 0x7F) ? c : '\0';
        }
        out->game_code[4] = '\0';
        out->maker[0] = static_cast<char>(data[0xB0]);
        out->maker[1] = static_cast<char>(data[0xB1]);
        out->maker[2] = '\0';

        char rc = out->game_code[3];
        if (rc == 'J') snprintf(out->region, sizeof(out->region), "Japan");
        else if (rc == 'E') snprintf(out->region, sizeof(out->region), "USA");
        else if (rc == 'P' || rc == 'D' || rc == 'F' || rc == 'I' || rc == 'S') snprintf(out->region, sizeof(out->region), "Europe");
        else snprintf(out->region, sizeof(out->region), "World");

        // Detect special hardware from known GBA game codes
        if (std::strncmp(out->game_code, "U3I", 3) == 0 ||
            std::strncmp(out->game_code, "U32", 3) == 0 ||
            std::strncmp(out->game_code, "U33", 3) == 0) {
            out->hw_flags |= STORM_HW_SOLAR | STORM_HW_RTC;
        }
        if (std::strncmp(out->game_code, "RZW", 3) == 0) {
            out->hw_flags |= STORM_HW_GYRO | STORM_HW_RUMBLE;
        }
        if (std::strncmp(out->game_code, "KYG", 3) == 0) {
            out->hw_flags |= STORM_HW_TILT;
        }
        if (std::strncmp(out->game_code, "BPE", 3) == 0 ||
            std::strncmp(out->game_code, "BPR", 3) == 0 ||
            std::strncmp(out->game_code, "BPG", 3) == 0 ||
            std::strncmp(out->game_code, "AXV", 3) == 0 ||
            std::strncmp(out->game_code, "AXP", 3) == 0) {
            out->hw_flags |= STORM_HW_RTC;
        }
        if (std::strncmp(out->game_code, "V49", 3) == 0) {
            out->hw_flags |= STORM_HW_RUMBLE;
        }
    } else if (size >= 0x150) {
        size_t tlen = (data[0x143] & 0x80) ? 11 : 15;
        for (size_t i = 0; i < tlen; ++i) {
            char c = static_cast<char>(data[0x134 + i]);
            out->title[i] = (c >= 0x20 && c < 0x7F) ? c : '\0';
        }
        out->title[tlen] = '\0';
        if (data[0x143] & 0x80) {
            out->hw_flags |= STORM_HW_CGB;
            for (size_t i = 0; i < 4; ++i) {
                char c = static_cast<char>(data[0x13F + i]);
                out->game_code[i] = (c >= 0x20 && c < 0x7F) ? c : '\0';
            }
        }
        if (data[0x146] == 0x03 && data[0x14B] == 0x33) {
            out->hw_flags |= STORM_HW_SGB;
        }
        uint8_t cart_type = data[0x147];
        if (cart_type == 0x0F || cart_type == 0x10) out->hw_flags |= STORM_HW_RTC;
        if (cart_type == 0x1C || cart_type == 0x1D || cart_type == 0x1E) out->hw_flags |= STORM_HW_RUMBLE;
        if (cart_type == 0x22) out->hw_flags |= STORM_HW_TILT | STORM_HW_RUMBLE;
        snprintf(out->region, sizeof(out->region), data[0x14A] == 0x00 ? "Japan" : "World");
    }
}

static uint32_t map_storm_keys_to_mgba(uint32_t mask) {
    // mGBA keys match GBA KEYINPUT bits: A=0, B=1, Select=2, Start=3, Right=4, Left=5, Up=6, Down=7, R=8, L=9
    return mask & 0x3FFu;
}

struct StormRotationPeriph {
    struct mRotationSource d;
    float tilt_x = 0.0f;
    float tilt_y = 0.0f;
    float gyro_z = 0.0f;
};

static void rot_sample(struct mRotationSource* s) { (void)s; }
static int32_t rot_read_tilt_x(struct mRotationSource* s) {
    auto* p = reinterpret_cast<StormRotationPeriph*>(s);
    return static_cast<int32_t>(p->tilt_x * 469762048.0f); // scaled to mGBA fixed point range
}
static int32_t rot_read_tilt_y(struct mRotationSource* s) {
    auto* p = reinterpret_cast<StormRotationPeriph*>(s);
    return static_cast<int32_t>(p->tilt_y * 469762048.0f);
}
static int32_t rot_read_gyro_z(struct mRotationSource* s) {
    auto* p = reinterpret_cast<StormRotationPeriph*>(s);
    return static_cast<int32_t>(p->gyro_z * 536870912.0f);
}

struct StormLuminancePeriph {
    struct GBALuminanceSource d;
    int lux_level = 5;
};

static void lum_sample(struct GBALuminanceSource* s) { (void)s; }
static uint8_t lum_read(struct GBALuminanceSource* s) {
    auto* p = reinterpret_cast<StormLuminancePeriph*>(s);
    int idx = std::clamp(p->lux_level, 0, 10);
    if (idx == 0) return 0xFF; // complete darkness in GBA solar sensor = max charge time
    return static_cast<uint8_t>(GBA_LUX_LEVELS[std::min(idx - 1, 9)]);
}

struct StormRumblePeriph {
    struct mRumbleIntegrator d;
    float current_level = 0.0f;
};

static void rumble_cb(struct mRumbleIntegrator* r, float val) {
    auto* p = reinterpret_cast<StormRumblePeriph*>(r);
    p->current_level = val;
}

struct StormRtcPeriph {
    struct mRTCSource d;
    bool use_host = true;
    int64_t fixed_epoch = 0;
};

static void rtc_sample(struct mRTCSource* s) { (void)s; }
static time_t rtc_unix_time(struct mRTCSource* s) {
    auto* p = reinterpret_cast<StormRtcPeriph*>(s);
    if (p->use_host) return std::time(nullptr);
    return static_cast<time_t>(p->fixed_epoch);
}

struct CheatEntry {
    std::string name;
    std::string raw_code;
    int enabled = 1;
    std::vector<storm::NormalizedCheatLine> lines;
};

} // namespace

struct StormGbcaContext {
    mutable std::recursive_mutex core_mu;
    mutable std::mutex audio_mu;

    StormGbcaConfig cfg{};
    StormGbcaRomInfo rom_info{};

    std::vector<uint8_t> rom_data;
    std::string rom_virtual_name;

    bool rom_loaded = false;
    bool using_sameboy = false;

    // mGBA backend
    struct mCore* mgba = nullptr;
    std::vector<mColor> mgba_fb;
    int mgba_w = 240;
    int mgba_h = 160;
    float mgba_fps = 59.7275f;
    bool mgba_sram_dirty = false;
    std::vector<uint8_t> mgba_last_sram;

    // SameBoy backend
    StormSameboyHandle* sb = nullptr;

    // Peripherals
    StormRotationPeriph rot_periph{};
    StormLuminancePeriph lum_periph{};
    StormRumblePeriph rumble_periph{};
    StormRtcPeriph rtc_periph{};

    // Input & execution
    std::atomic<uint32_t> key_mask{0};
    std::atomic<float> fast_forward{1.0f};

    // Audio / Video / Rewind / Cheats / Upscaling / RAM Search (v1.1.0)
    storm::VideoPostProcessor video_pp;
    storm::AudioDsp audio_dsp;
    storm::RewindRingBuffer rewind;
    storm::MemSearchSession mem_search;
    std::vector<CheatEntry> cheats;
    std::vector<uint8_t> runahead_state;
    std::vector<uint32_t> runahead_fb;
    int runahead_fb_w = 0;
    int runahead_fb_h = 0;
    bool has_runahead_fb = false;
    std::vector<uint8_t> scratch_state;
    std::vector<int16_t> scratch_audio;
    int upscale_scale = 1;
    int upscale_mode = STORM_UPSCALE_NONE;
    std::vector<uint32_t> pp_rgba_buf;
    std::vector<uint32_t> upscaled_rgba_buf;
    int cpu_overclock_pct = 100;
    int frame_skip = 0;
    int frame_skip_counter = 0;
    int optimization_profile = STORM_OPT_BALANCED;
    storm::MultiplayerManager mp;
};

namespace {

static void init_peripherals(StormGbcaContext* ctx) {
    ctx->rot_periph.d.sample = rot_sample;
    ctx->rot_periph.d.readTiltX = rot_read_tilt_x;
    ctx->rot_periph.d.readTiltY = rot_read_tilt_y;
    ctx->rot_periph.d.readGyroZ = rot_read_gyro_z;

    ctx->lum_periph.d.sample = lum_sample;
    ctx->lum_periph.d.readLuminance = lum_read;

    mRumbleIntegratorInit(&ctx->rumble_periph.d);
    ctx->rumble_periph.d.setRumble = rumble_cb;
    ctx->rumble_periph.current_level = 0.0f;

    ctx->rtc_periph.d.sample = rtc_sample;
    ctx->rtc_periph.d.unixTime = rtc_unix_time;
    ctx->rtc_periph.d.serialize = nullptr;
    ctx->rtc_periph.d.deserialize = nullptr;
}

static StormGbcaAudioConfig make_audio_cfg(const StormGbcaConfig& c) {
    StormGbcaAudioConfig ac{};
    ac.highpass_mode = c.audio_highpass_mode;
    ac.interp_mode = c.audio_interp_mode;
    for (int i = 0; i < 6; ++i) ac.channel_volumes[i] = c.channel_volumes[i];
    ac.master_volume = c.master_volume;
    ac.dynamic_rate_control = c.dynamic_rate_control;
    ac.drc_max_skew = c.drc_max_skew;
    ac.target_sample_rate = 48000;
    return ac;
}

static void apply_mgba_config(StormGbcaContext* ctx) {
    if (!ctx->mgba) return;
    struct mCore* core = ctx->mgba;
    core->opts.skipBios = ctx->cfg.skip_bios_intro != 0;
    core->opts.useBios = (ctx->cfg.bios_gba_path[0] || ctx->cfg.bios_gb_path[0] ||
                          ctx->cfg.bios_gbc_path[0] || ctx->cfg.bios_sgb_path[0]);
    core->opts.sampleRate = 48000;
    core->opts.volume = static_cast<int>(std::clamp(ctx->cfg.master_volume, 0.0f, 2.0f) * 256.0f);
    core->opts.mute = false;

    mCoreConfigSetIntValue(&core->config, "sgb.borders", ctx->cfg.sgb_borders ? 1 : 0);
    mCoreConfigSetIntValue(&core->config, "skipBios", ctx->cfg.skip_bios_intro ? 1 : 0);

    if (ctx->cfg.bios_gba_path[0]) mCoreConfigSetValue(&core->config, "gba.bios", ctx->cfg.bios_gba_path);
    if (ctx->cfg.bios_gb_path[0])  mCoreConfigSetValue(&core->config, "gb.bios", ctx->cfg.bios_gb_path);
    if (ctx->cfg.bios_gbc_path[0]) mCoreConfigSetValue(&core->config, "gbc.bios", ctx->cfg.bios_gbc_path);
    if (ctx->cfg.bios_sgb_path[0]) mCoreConfigSetValue(&core->config, "sgb.bios", ctx->cfg.bios_sgb_path);

    if (core->platform(core) == mPLATFORM_GB) {
        uint32_t bg[4], obj0[4], obj1[4];
        storm::get_effective_dmg_palette(ctx->cfg.dmg_palette_index, ctx->cfg.custom_dmg_palette, bg, obj0, obj1);
        for (int i = 0; i < 4; ++i) {
            char key[24];
            snprintf(key, sizeof(key), "gb.pal[%d]", i);
            mCoreConfigSetUIntValue(&core->config, key, bg[i]);
            snprintf(key, sizeof(key), "gb.pal[%d]", i + 4);
            mCoreConfigSetUIntValue(&core->config, key, obj0[i]);
            snprintf(key, sizeof(key), "gb.pal[%d]", i + 8);
            mCoreConfigSetUIntValue(&core->config, key, obj1[i]);
        }
        const char* mname = nullptr;
        switch (ctx->cfg.gb_model) {
            case STORM_GB_MODEL_DMG:  mname = "DMG"; break;
            case STORM_GB_MODEL_MGB:  mname = "MGB"; break;
            case STORM_GB_MODEL_SGB:  mname = "SGB"; break;
            case STORM_GB_MODEL_SGB2: mname = "SGB2"; break;
            case STORM_GB_MODEL_CGB:  mname = "CGB"; break;
            case STORM_GB_MODEL_AGB:  mname = "AGB"; break;
            default: break;
        }
        if (mname) mCoreConfigSetValue(&core->config, "gb.model", mname);
    }

    core->loadConfig(core, &core->config);

    // Channel enable/disable via mGBA core API (volumes < 0.01 mute channel)
    if (core->enableAudioChannel) {
        for (size_t ch = 0; ch < 6; ++ch) {
            core->enableAudioChannel(core, ch, ctx->cfg.channel_volumes[ch] > 0.01f);
        }
    }
}

static void apply_sameboy_config(StormGbcaContext* ctx) {
    if (!ctx->sb) return;
    storm_sb_set_color_correction(ctx->sb, ctx->cfg.color_correction_mode);
    storm_sb_set_highpass(ctx->sb, ctx->cfg.audio_highpass_mode);
    storm_sb_set_border_mode(ctx->sb, ctx->cfg.sgb_borders);
    for (int ch = 0; ch < 4; ++ch) {
        storm_sb_set_channel_muted(ctx->sb, ch, ctx->cfg.channel_volumes[ch] <= 0.01f ? 1 : 0);
    }
    uint32_t bg[4], obj0[4], obj1[4];
    storm::get_effective_dmg_palette(ctx->cfg.dmg_palette_index, ctx->cfg.custom_dmg_palette, bg, obj0, obj1);
    storm_sb_set_dmg_palette(ctx->sb, bg);
}

static void rebuild_cheats_on_core(StormGbcaContext* ctx) {
    if (!ctx->rom_loaded) return;
    if (!ctx->using_sameboy && ctx->mgba && ctx->mgba->cheatDevice) {
        struct mCheatDevice* dev = ctx->mgba->cheatDevice(ctx->mgba);
        if (dev) {
            mCheatDeviceClear(dev);
            for (const auto& c : ctx->cheats) {
                if (!c.enabled) continue;
                struct mCheatSet* set = dev->createSet(dev, c.name.c_str());
                if (!set) continue;
                set->enabled = true;
                for (const auto& ln : c.lines) {
                    mCheatAddLine(set, ln.code.c_str(), 0);
                }
                mCheatAddSet(dev, set);
            }
        }
    }
}

static void apply_sameboy_frame_cheats(StormGbcaContext* ctx) {
    if (!ctx->using_sameboy || !ctx->sb) return;
    for (const auto& c : ctx->cheats) {
        if (!c.enabled) continue;
        for (const auto& ln : c.lines) {
            if (ln.is_rom_patch) {
                storm_sb_patch_rom_byte(ctx->sb, ln.addr, static_cast<uint8_t>(ln.val & 0xFFu),
                                        ln.has_cmp ? 1 : 0, ln.cmp);
            } else if (ln.addr <= 0xFFFFu) {
                storm_sb_write_memory_u8(ctx->sb, static_cast<uint16_t>(ln.addr), static_cast<uint8_t>(ln.val & 0xFFu));
            }
        }
    }
}

static void unload_active_cores(StormGbcaContext* ctx) {
    ctx->mp.stop(ctx->mgba);
    if (ctx->mgba) {
        ctx->mgba->unloadROM(ctx->mgba);
        mCoreConfigDeinit(&ctx->mgba->config);
        ctx->mgba->deinit(ctx->mgba);
        ctx->mgba = nullptr;
    }
    if (ctx->sb) {
        storm_sb_destroy(ctx->sb);
        ctx->sb = nullptr;
    }
    ctx->rom_loaded = false;
    ctx->using_sameboy = false;
    ctx->video_pp.reset();
    ctx->audio_dsp.reset();
    ctx->rewind.reset();
    ctx->mgba_last_sram.clear();
    ctx->mgba_sram_dirty = false;
}

static int init_core_with_loaded_rom(StormGbcaContext* ctx) {
    if (ctx->rom_data.empty()) return -1;

    int plat = detect_platform_from_buffer(ctx->rom_data.data(), ctx->rom_data.size(),
                                           ctx->rom_virtual_name.c_str());
    if (plat == STORM_PLATFORM_NONE) return -1;

    bool want_sameboy = false;
    if (plat == STORM_PLATFORM_GB || plat == STORM_PLATFORM_SGB) {
        want_sameboy = (ctx->cfg.preferred_core_gb == STORM_CORE_SAMEBOY);
    } else if (plat == STORM_PLATFORM_GBC) {
        want_sameboy = (ctx->cfg.preferred_core_gbc == STORM_CORE_SAMEBOY);
    }

    if (want_sameboy) {
        int model = ctx->cfg.gb_model;
        if (model == STORM_GB_MODEL_AUTO) {
            if (plat == STORM_PLATFORM_GBC) model = STORM_GB_MODEL_CGB;
            else if (plat == STORM_PLATFORM_SGB) model = STORM_GB_MODEL_SGB;
            else model = STORM_GB_MODEL_DMG;
        }
        ctx->sb = storm_sb_create(model, ctx->cfg.sgb_borders,
                                  ctx->cfg.bios_gb_path, ctx->cfg.bios_gbc_path,
                                  ctx->cfg.bios_sgb_path, ctx->cfg.skip_bios_intro);
        if (!ctx->sb) return -1;
        if (storm_sb_load_rom_buffer(ctx->sb, ctx->rom_data.data(), ctx->rom_data.size()) != 0) {
            storm_sb_destroy(ctx->sb);
            ctx->sb = nullptr;
            return -1;
        }
        apply_sameboy_config(ctx);
        storm_sb_set_accelerometer(ctx->sb, ctx->rot_periph.tilt_x, ctx->rot_periph.tilt_y);
        ctx->using_sameboy = true;
        ctx->rom_loaded = true;
        fill_rom_info_from_buffer(ctx->rom_data.data(), ctx->rom_data.size(),
                                  ctx->rom_virtual_name.c_str(), "SameBoy", &ctx->rom_info);
    } else {
        enum mPlatform mp = (plat == STORM_PLATFORM_GBA) ? mPLATFORM_GBA : mPLATFORM_GB;
        struct mCore* core = mCoreCreate(mp);
        if (!core) return -1;
        if (!core->init(core)) {
            core->deinit(core);
            return -1;
        }
        mCoreInitConfig(core, nullptr);
        ctx->mgba = core;

        apply_mgba_config(ctx);

        struct VFile* vf = VFileFromConstMemory(ctx->rom_data.data(), ctx->rom_data.size());
        if (!vf || !core->loadROM(core, vf)) {
            if (vf) vf->close(vf);
            mCoreConfigDeinit(&core->config);
            core->deinit(core);
            ctx->mgba = nullptr;
            return -1;
        }

        unsigned w = 240, h = 160;
        core->baseVideoSize(core, &w, &h);
        ctx->mgba_w = static_cast<int>(w);
        ctx->mgba_h = static_cast<int>(h);
        ctx->mgba_fb.assign(static_cast<size_t>(w) * static_cast<size_t>(h), 0);
        core->setVideoBuffer(core, ctx->mgba_fb.data(), w);
        core->setAudioBufferSize(core, 2048);

        core->setPeripheral(core, mPERIPH_ROTATION, &ctx->rot_periph.d);
        core->setPeripheral(core, mPERIPH_RUMBLE, &ctx->rumble_periph.d);
        if (mp == mPLATFORM_GBA) {
            core->setPeripheral(core, mPERIPH_GBA_LUMINANCE, &ctx->lum_periph.d);
        }
        mCoreSetRTC(core, &ctx->rtc_periph.d);

        core->reset(core);

        core->currentVideoSize(core, &w, &h);
        ctx->mgba_w = static_cast<int>(w);
        ctx->mgba_h = static_cast<int>(h);
        int32_t freq = core->frequency(core);
        int32_t fcyc = core->frameCycles(core);
        ctx->mgba_fps = (fcyc > 0) ? (static_cast<float>(freq) / static_cast<float>(fcyc)) : 59.7275f;

        ctx->using_sameboy = false;
        ctx->rom_loaded = true;
        fill_rom_info_from_buffer(ctx->rom_data.data(), ctx->rom_data.size(),
                                  ctx->rom_virtual_name.c_str(), "mGBA", &ctx->rom_info);
    }

    ctx->rewind.configure(ctx->cfg.rewind_enabled != 0,
                          ctx->cfg.rewind_buffer_mb,
                          ctx->cfg.rewind_interval);
    ctx->rewind.reset();
    ctx->audio_dsp.reset();
    ctx->video_pp.reset();
    rebuild_cheats_on_core(ctx);
    if (!ctx->using_sameboy && ctx->mgba) {
        ctx->mp.on_core_reloaded(ctx->mgba, ctx->rom_info.platform, ctx->rom_info.title);
    }
    return 0;
}

static bool save_raw_state(StormGbcaContext* ctx, std::vector<uint8_t>& out) {
    if (!ctx->rom_loaded) return false;
    if (ctx->using_sameboy) {
        size_t sz = storm_sb_state_size(ctx->sb);
        if (sz == 0) return false;
        out.resize(sz);
        return storm_sb_save_state(ctx->sb, out.data(), sz) == 0;
    } else {
        size_t sz = ctx->mgba->stateSize(ctx->mgba);
        if (sz == 0) return false;
        out.resize(sz);
        return ctx->mgba->saveState(ctx->mgba, out.data());
    }
}

static bool load_raw_state(StormGbcaContext* ctx, const uint8_t* data, size_t size) {
    if (!ctx->rom_loaded || !data || size == 0) return false;
    if (ctx->using_sameboy) {
        size_t expected = storm_sb_state_size(ctx->sb);
        if (size < expected) return false;
        return storm_sb_load_state(ctx->sb, data, expected) == 0;
    } else {
        size_t expected = ctx->mgba->stateSize(ctx->mgba);
        if (size < expected) return false;
        return ctx->mgba->loadState(ctx->mgba, data);
    }
}

static void run_single_core_frame(StormGbcaContext* ctx, bool collect_audio) {
    uint32_t keys = ctx->key_mask.load(std::memory_order_acquire);
    if (ctx->using_sameboy) {
        apply_sameboy_frame_cheats(ctx);
        storm_sb_set_keys(ctx->sb, keys);
        storm_sb_run_frame(ctx->sb, collect_audio ? 1 : 0);
        if (collect_audio) {
            ctx->scratch_audio.resize(4096 * 2);
            int got = storm_sb_drain_audio(ctx->sb, ctx->scratch_audio.data(), 4096);
            if (got > 0) {
                std::lock_guard<std::mutex> alock(ctx->audio_mu);
                ctx->audio_dsp.push_source_frames(ctx->scratch_audio.data(), got,
                                                  48000.0, make_audio_cfg(ctx->cfg));
            }
        }
    } else {
        ctx->mgba->setKeys(ctx->mgba, map_storm_keys_to_mgba(keys));
        ctx->mgba->runFrame(ctx->mgba);
        struct mAudioBuffer* ab = ctx->mgba->getAudioBuffer(ctx->mgba);
        if (ab) {
            size_t avail = mAudioBufferAvailable(ab);
            if (avail > 0) {
                ctx->scratch_audio.resize(avail * 2);
                size_t got = mAudioBufferRead(ab, ctx->scratch_audio.data(), avail);
                if (collect_audio && got > 0) {
                    double src_rate = static_cast<double>(ctx->mgba->audioSampleRate(ctx->mgba));
                    if (src_rate <= 0.0) src_rate = 32768.0;
                    std::lock_guard<std::mutex> alock(ctx->audio_mu);
                    ctx->audio_dsp.push_source_frames(ctx->scratch_audio.data(),
                                                      static_cast<int>(got),
                                                      src_rate,
                                                      make_audio_cfg(ctx->cfg));
                }
            }
        }
    }
}

} // namespace

extern "C" {

STORM_GBCA_API const char* storm_gbca_version(void) {
    return STORM_GBCA_VERSION_STRING;
}

STORM_GBCA_API void storm_gbca_get_default_config(StormGbcaConfig* out_cfg) {
    if (!out_cfg) return;
    std::memset(out_cfg, 0, sizeof(*out_cfg));
    out_cfg->preferred_core_gb = STORM_CORE_MGBA;
    out_cfg->preferred_core_gbc = STORM_CORE_MGBA;
    out_cfg->preferred_core_gba = STORM_CORE_MGBA;
    out_cfg->gb_model = STORM_GB_MODEL_AUTO;
    out_cfg->sgb_borders = 1;
    out_cfg->color_correction_mode = STORM_COLOR_CORRECTION_MODERN_BALANCED;
    out_cfg->dmg_palette_index = 0;
    out_cfg->custom_dmg_palette[0] = 0x9BBC0F;
    out_cfg->custom_dmg_palette[1] = 0x8BAC0F;
    out_cfg->custom_dmg_palette[2] = 0x306230;
    out_cfg->custom_dmg_palette[3] = 0x0F380F;
    out_cfg->lcd_ghosting = 0;
    out_cfg->video_filter = STORM_VIDEO_FILTER_NEAREST;
    out_cfg->audio_highpass_mode = STORM_AUDIO_HIGHPASS_ACCURATE;
    out_cfg->audio_interp_mode = STORM_AUDIO_INTERP_HERMITE;
    for (int i = 0; i < 6; ++i) out_cfg->channel_volumes[i] = 1.0f;
    out_cfg->master_volume = 1.0f;
    out_cfg->run_ahead_frames = 0;
    out_cfg->rewind_enabled = 1;
    out_cfg->rewind_buffer_mb = 16;
    out_cfg->rewind_interval = 1;
    out_cfg->skip_bios_intro = 1;
    out_cfg->dynamic_rate_control = 1;
    out_cfg->drc_max_skew = 0.005f;
}

static void silent_mgba_log(struct mLogger*, int, enum mLogLevel, const char*, va_list) {}
static struct mLogger g_silent_logger = { silent_mgba_log, nullptr };

STORM_GBCA_API StormGbcaContext* storm_gbca_create(void) {
    mLogSetDefaultLogger(&g_silent_logger);
    auto* ctx = new StormGbcaContext();
    storm_gbca_get_default_config(&ctx->cfg);
    init_peripherals(ctx);
    return ctx;
}

STORM_GBCA_API void storm_gbca_destroy(StormGbcaContext* ctx) {
    if (!ctx) return;
    {
        std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
        unload_active_cores(ctx);
    }
    delete ctx;
}

STORM_GBCA_API void storm_gbca_reset(StormGbcaContext* ctx) {
    if (!ctx) return;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    if (!ctx->rom_loaded) return;
    ctx->has_runahead_fb = false;
    if (ctx->using_sameboy) {
        storm_sb_reset(ctx->sb);
    } else if (ctx->mgba) {
        ctx->mgba->reset(ctx->mgba);
    }
    ctx->rewind.reset();
    {
        std::lock_guard<std::mutex> alock(ctx->audio_mu);
        ctx->audio_dsp.reset();
    }
    ctx->video_pp.reset();
}

STORM_GBCA_API int storm_gbca_set_config(StormGbcaContext* ctx, const StormGbcaConfig* cfg) {
    if (!ctx || !cfg) return -1;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    bool core_choice_changed =
        (cfg->preferred_core_gb != ctx->cfg.preferred_core_gb) ||
        (cfg->preferred_core_gbc != ctx->cfg.preferred_core_gbc) ||
        (cfg->gb_model != ctx->cfg.gb_model);

    ctx->cfg = *cfg;
    ctx->cfg.run_ahead_frames = std::clamp(ctx->cfg.run_ahead_frames, 0, 2);
    ctx->cfg.lcd_ghosting = std::clamp(ctx->cfg.lcd_ghosting, 0, 100);
    ctx->rewind.configure(ctx->cfg.rewind_enabled != 0, ctx->cfg.rewind_buffer_mb, ctx->cfg.rewind_interval);

    if (ctx->rom_loaded) {
        if (core_choice_changed) {
            std::vector<uint8_t> rom_copy = ctx->rom_data;
            std::string name_copy = ctx->rom_virtual_name;
            unload_active_cores(ctx);
            ctx->rom_data = std::move(rom_copy);
            ctx->rom_virtual_name = std::move(name_copy);
            return init_core_with_loaded_rom(ctx);
        }
        if (ctx->using_sameboy) apply_sameboy_config(ctx);
        else apply_mgba_config(ctx);
    }
    return 0;
}

STORM_GBCA_API int storm_gbca_set_audio_config(StormGbcaContext* ctx, const StormGbcaAudioConfig* cfg) {
    if (!ctx || !cfg) return -1;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    ctx->cfg.audio_highpass_mode = cfg->highpass_mode;
    ctx->cfg.audio_interp_mode = cfg->interp_mode;
    for (int i = 0; i < 6; ++i) ctx->cfg.channel_volumes[i] = cfg->channel_volumes[i];
    ctx->cfg.master_volume = cfg->master_volume;
    ctx->cfg.dynamic_rate_control = cfg->dynamic_rate_control;
    ctx->cfg.drc_max_skew = cfg->drc_max_skew;

    if (ctx->rom_loaded) {
        if (ctx->using_sameboy) apply_sameboy_config(ctx);
        else apply_mgba_config(ctx);
    }
    return 0;
}

STORM_GBCA_API int storm_gbca_load_rom_file(StormGbcaContext* ctx, const char* rom_path) {
    if (!ctx || !rom_path) return -1;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    std::vector<uint8_t> bytes;
    std::string vname;
    if (!load_rom_from_path_or_archive(rom_path, bytes, vname)) return -1;

    ctx->has_runahead_fb = false;
    unload_active_cores(ctx);
    ctx->rom_data = std::move(bytes);
    ctx->rom_virtual_name = std::move(vname);
    return init_core_with_loaded_rom(ctx);
}

STORM_GBCA_API int storm_gbca_load_rom_buffer(StormGbcaContext* ctx, const void* data, size_t size, const char* virtual_name) {
    if (!ctx || !data || size == 0) return -1;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    ctx->has_runahead_fb = false;
    unload_active_cores(ctx);
    const uint8_t* ptr = static_cast<const uint8_t*>(data);
    ctx->rom_data.assign(ptr, ptr + size);
    ctx->rom_virtual_name = virtual_name ? virtual_name : "";
    return init_core_with_loaded_rom(ctx);
}

STORM_GBCA_API void storm_gbca_unload_rom(StormGbcaContext* ctx) {
    if (!ctx) return;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    ctx->has_runahead_fb = false;
    unload_active_cores(ctx);
    ctx->rom_data.clear();
    ctx->rom_virtual_name.clear();
    std::memset(&ctx->rom_info, 0, sizeof(ctx->rom_info));
}

STORM_GBCA_API int storm_gbca_inspect_rom_file(const char* rom_path, StormGbcaRomInfo* out_info) {
    if (!rom_path || !out_info) return -1;
    std::vector<uint8_t> bytes;
    std::string vname;
    if (!load_rom_from_path_or_archive(rom_path, bytes, vname)) return -1;
    fill_rom_info_from_buffer(bytes.data(), bytes.size(), vname.c_str(), "", out_info);
    return out_info->platform == STORM_PLATFORM_NONE ? -1 : 0;
}

STORM_GBCA_API int storm_gbca_inspect_rom_buffer(const void* data, size_t size, const char* virtual_name, StormGbcaRomInfo* out_info) {
    if (!data || size == 0 || !out_info) return -1;
    fill_rom_info_from_buffer(static_cast<const uint8_t*>(data), size, virtual_name, "", out_info);
    return out_info->platform == STORM_PLATFORM_NONE ? -1 : 0;
}

STORM_GBCA_API int storm_gbca_get_rom_info(const StormGbcaContext* ctx, StormGbcaRomInfo* out_info) {
    if (!ctx || !out_info) return -1;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    if (!ctx->rom_loaded) return -1;
    *out_info = ctx->rom_info;
    return 0;
}

STORM_GBCA_API int storm_gbca_apply_patch_file(StormGbcaContext* ctx, const char* patch_path) {
    if (!ctx || !patch_path) return -1;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    if (!ctx->rom_loaded) return -1;
    std::vector<uint8_t> patch;
    if (!read_file_bytes(patch_path, patch)) return -1;
    std::vector<uint8_t> patched;
    if (!storm::apply_soft_patch(ctx->rom_data.data(), ctx->rom_data.size(),
                                 patch.data(), patch.size(), patched)) {
        return -1;
    }
    std::string vname = ctx->rom_virtual_name;
    ctx->has_runahead_fb = false;
    unload_active_cores(ctx);
    ctx->rom_data = std::move(patched);
    ctx->rom_virtual_name = std::move(vname);
    return init_core_with_loaded_rom(ctx);
}

STORM_GBCA_API int storm_gbca_apply_patch_buffer(const void* rom_data, size_t rom_size,
                                                 const void* patch_data, size_t patch_size,
                                                 uint8_t** out_patched, size_t* out_size) {
    if (!rom_data || !patch_data || !out_patched || !out_size) return -1;
    std::vector<uint8_t> patched;
    if (!storm::apply_soft_patch(static_cast<const uint8_t*>(rom_data), rom_size,
                                 static_cast<const uint8_t*>(patch_data), patch_size, patched)) {
        return -1;
    }
    uint8_t* buf = static_cast<uint8_t*>(std::malloc(patched.size()));
    if (!buf) return -1;
    std::memcpy(buf, patched.data(), patched.size());
    *out_patched = buf;
    *out_size = patched.size();
    return 0;
}

STORM_GBCA_API void storm_gbca_free_buffer(void* ptr) {
    std::free(ptr);
}

STORM_GBCA_API void storm_gbca_set_keys(StormGbcaContext* ctx, uint32_t mask) {
    if (!ctx) return;
    ctx->key_mask.store(mask, std::memory_order_release);
}

STORM_GBCA_API int storm_gbca_run_frame(StormGbcaContext* ctx) {
    if (!ctx) return -1;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    if (!ctx->rom_loaded) return -1;

    ctx->has_runahead_fb = false;
    const float ff = ctx->fast_forward.load(std::memory_order_acquire);
    const int ahead = (ff > 1.05f) ? 0 : std::clamp(ctx->cfg.run_ahead_frames, 0, 2);
    if (ahead == 0) {
        run_single_core_frame(ctx, true);
    } else {
        // Ares-style Run-Ahead: run 1 real frame, snapshot, run speculative frames,
        // capture speculative video framebuffer cleanly, then restore CPU/RAM state!
        run_single_core_frame(ctx, true);
        if (save_raw_state(ctx, ctx->runahead_state)) {
            for (int i = 0; i < ahead; ++i) {
                run_single_core_frame(ctx, false);
            }
            const uint32_t* spec_ptr = nullptr;
            int sw = 0, sh = 0;
            if (ctx->using_sameboy && ctx->sb) {
                storm_sb_get_video_size(ctx->sb, &sw, &sh, nullptr);
                spec_ptr = storm_sb_get_pixels(ctx->sb);
            } else if (ctx->mgba) {
                unsigned uw = static_cast<unsigned>(ctx->mgba_w);
                unsigned uh = static_cast<unsigned>(ctx->mgba_h);
                ctx->mgba->currentVideoSize(ctx->mgba, &uw, &uh);
                sw = static_cast<int>(uw);
                sh = static_cast<int>(uh);
                spec_ptr = reinterpret_cast<const uint32_t*>(ctx->mgba_fb.data());
            }
            if (spec_ptr && sw > 0 && sh > 0) {
                size_t count = static_cast<size_t>(sw) * static_cast<size_t>(sh);
                ctx->runahead_fb.assign(spec_ptr, spec_ptr + count);
                ctx->runahead_fb_w = sw;
                ctx->runahead_fb_h = sh;
                ctx->has_runahead_fb = true;
            }
            load_raw_state(ctx, ctx->runahead_state.data(), ctx->runahead_state.size());
        }
    }

    if (ctx->rewind.enabled()) {
        if (save_raw_state(ctx, ctx->scratch_state)) {
            ctx->rewind.push_state(ctx->scratch_state.data(), ctx->scratch_state.size());
        }
    }
    ctx->mp.tick_frame();
    return 0;
}

STORM_GBCA_API void storm_gbca_set_fast_forward(StormGbcaContext* ctx, float mult) {
    if (!ctx) return;
    ctx->fast_forward.store(std::clamp(mult, 0.25f, 16.0f), std::memory_order_release);
}

STORM_GBCA_API int storm_gbca_get_video_info(const StormGbcaContext* ctx, int* out_w, int* out_h, float* out_fps) {
    if (!ctx) return -1;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    if (!ctx->rom_loaded) return -1;
    if (ctx->using_sameboy) {
        storm_sb_get_video_size(ctx->sb, out_w, out_h, out_fps);
        return 0;
    }
    unsigned w = static_cast<unsigned>(ctx->mgba_w);
    unsigned h = static_cast<unsigned>(ctx->mgba_h);
    ctx->mgba->currentVideoSize(ctx->mgba, &w, &h);
    if (out_w) *out_w = static_cast<int>(w);
    if (out_h) *out_h = static_cast<int>(h);
    if (out_fps) *out_fps = ctx->mgba_fps;
    return 0;
}

STORM_GBCA_API int storm_gbca_get_raw_frame_rgba(const StormGbcaContext* ctx, const uint32_t** out_ptr, int* out_w, int* out_h) {
    if (!ctx || !out_ptr) return -1;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    if (!ctx->rom_loaded) return -1;
    if (ctx->has_runahead_fb && !ctx->runahead_fb.empty()) {
        if (out_w) *out_w = ctx->runahead_fb_w;
        if (out_h) *out_h = ctx->runahead_fb_h;
        *out_ptr = ctx->runahead_fb.data();
        return 0;
    }
    if (ctx->using_sameboy) {
        storm_sb_get_video_size(ctx->sb, out_w, out_h, nullptr);
        *out_ptr = storm_sb_get_pixels(ctx->sb);
        return *out_ptr ? 0 : -1;
    }
    unsigned w = static_cast<unsigned>(ctx->mgba_w);
    unsigned h = static_cast<unsigned>(ctx->mgba_h);
    ctx->mgba->currentVideoSize(ctx->mgba, &w, &h);
    if (out_w) *out_w = static_cast<int>(w);
    if (out_h) *out_h = static_cast<int>(h);
    *out_ptr = reinterpret_cast<const uint32_t*>(ctx->mgba_fb.data());
    return 0;
}

STORM_GBCA_API int storm_gbca_copy_frame(StormGbcaContext* ctx, uint32_t* dst, int dst_stride_px, int pixel_format) {
    if (!ctx || !dst) return -1;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    if (!ctx->rom_loaded) return -1;
    const uint32_t* raw = nullptr;
    int w = 0, h = 0;
    if (storm_gbca_get_raw_frame_rgba(ctx, &raw, &w, &h) != 0 || !raw) return -1;
    const bool is_gba = (ctx->rom_info.platform == STORM_PLATFORM_GBA);
    // SameBoy already runs its own 7-mode color correction internally, so only apply LUT for mGBA
    const int cc_mode = ctx->using_sameboy ? STORM_COLOR_CORRECTION_DISABLED : ctx->cfg.color_correction_mode;
    ctx->video_pp.process_frame(raw, w, h, cc_mode, ctx->cfg.lcd_ghosting,
                                ctx->cfg.video_filter, is_gba, dst, dst_stride_px, pixel_format);
    return 0;
}

STORM_GBCA_API int storm_gbca_read_audio(StormGbcaContext* ctx, int16_t* dst_interleaved, int max_frames) {
    if (!ctx || !dst_interleaved || max_frames <= 0) return 0;
    std::unique_lock<std::mutex> alock(ctx->audio_mu, std::try_to_lock);
    if (!alock.owns_lock()) return 0;
    return ctx->audio_dsp.read_frames(dst_interleaved, max_frames);
}

STORM_GBCA_API void storm_gbca_set_audio_buffer_level(StormGbcaContext* ctx, float fill_ratio) {
    if (!ctx) return;
    std::lock_guard<std::mutex> alock(ctx->audio_mu);
    ctx->audio_dsp.buffer_fill_ratio = std::clamp(fill_ratio, 0.0f, 1.0f);
}

STORM_GBCA_API int storm_gbca_save_state_buffer(StormGbcaContext* ctx, void* dst, size_t capacity, size_t* out_size) {
    if (!ctx) return -1;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    if (!ctx->rom_loaded) return -1;
    std::vector<uint8_t> tmp;
    if (!save_raw_state(ctx, tmp)) return -1;
    if (out_size) *out_size = tmp.size();
    if (!dst) return 0; // query size mode
    if (capacity < tmp.size()) return -1;
    std::memcpy(dst, tmp.data(), tmp.size());
    return 0;
}

STORM_GBCA_API int storm_gbca_load_state_buffer(StormGbcaContext* ctx, const void* src, size_t size) {
    if (!ctx || !src || size == 0) return -1;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    if (!ctx->rom_loaded) return -1;
    ctx->has_runahead_fb = false;
    return load_raw_state(ctx, static_cast<const uint8_t*>(src), size) ? 0 : -1;
}

STORM_GBCA_API int storm_gbca_save_state_file(StormGbcaContext* ctx, const char* path) {
    if (!ctx || !path) return -1;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    if (!ctx->rom_loaded) return -1;
    std::vector<uint8_t> tmp;
    if (!save_raw_state(ctx, tmp)) return -1;
    return write_file_bytes(path, tmp.data(), tmp.size()) ? 0 : -1;
}

STORM_GBCA_API int storm_gbca_load_state_file(StormGbcaContext* ctx, const char* path) {
    if (!ctx || !path) return -1;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    if (!ctx->rom_loaded) return -1;
    std::vector<uint8_t> tmp;
    if (!read_file_bytes(path, tmp)) return -1;
    ctx->has_runahead_fb = false;
    return load_raw_state(ctx, tmp.data(), tmp.size()) ? 0 : -1;
}

STORM_GBCA_API int storm_gbca_save_sram_file(StormGbcaContext* ctx, const char* path) {
    if (!ctx || !path) return -1;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    if (!ctx->rom_loaded) return -1;
    if (ctx->using_sameboy) {
        size_t sz = storm_sb_sram_size(ctx->sb);
        if (sz == 0) return 0;
        std::vector<uint8_t> buf(sz);
        if (storm_sb_save_sram_buffer(ctx->sb, buf.data(), buf.size()) != 0) return -1;
        return write_file_bytes(path, buf.data(), buf.size()) ? 0 : -1;
    }
    void* sram = nullptr;
    size_t sz = ctx->mgba->savedataClone(ctx->mgba, &sram);
    if (!sram || sz == 0) return -1;
    bool ok = write_file_bytes(path, sram, sz);
    if (ok) {
        ctx->mgba_last_sram.assign(static_cast<uint8_t*>(sram), static_cast<uint8_t*>(sram) + sz);
        ctx->mgba_sram_dirty = false;
    }
    std::free(sram);
    return ok ? 0 : -1;
}

STORM_GBCA_API int storm_gbca_load_sram_file(StormGbcaContext* ctx, const char* path) {
    if (!ctx || !path) return -1;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    if (!ctx->rom_loaded) return -1;
    if (ctx->using_sameboy) {
        std::vector<uint8_t> buf;
        if (!read_file_bytes(path, buf)) return -1;
        return storm_sb_load_sram_buffer(ctx->sb, buf.data(), buf.size());
    }
    std::vector<uint8_t> sram;
    if (!read_file_bytes(path, sram)) return -1;
    bool ok = ctx->mgba->savedataRestore(ctx->mgba, sram.data(), sram.size(), true);
    if (ok) {
        ctx->mgba_last_sram = std::move(sram);
        ctx->mgba_sram_dirty = false;
    }
    return ok ? 0 : -1;
}

STORM_GBCA_API int storm_gbca_is_sram_dirty(const StormGbcaContext* ctx) {
    if (!ctx) return 0;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    if (!ctx->rom_loaded) return 0;
    if (ctx->using_sameboy) {
        return storm_sb_is_sram_dirty(ctx->sb);
    }
    void* sram = nullptr;
    size_t sz = ctx->mgba->savedataClone(ctx->mgba, &sram);
    if (!sram || sz == 0) {
        if (sram) std::free(sram);
        return 0;
    }
    bool dirty = (ctx->mgba_last_sram.size() != sz) ||
                 (std::memcmp(ctx->mgba_last_sram.data(), sram, sz) != 0);
    std::free(sram);
    return dirty ? 1 : 0;
}

STORM_GBCA_API int storm_gbca_rewind_step(StormGbcaContext* ctx) {
    if (!ctx) return -1;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    if (!ctx->rom_loaded) return -1;
    std::vector<uint8_t> state;
    if (!ctx->rewind.pop_state(state)) return -1;
    ctx->has_runahead_fb = false;
    return load_raw_state(ctx, state.data(), state.size()) ? 0 : -1;
}

STORM_GBCA_API int storm_gbca_rewind_available_frames(const StormGbcaContext* ctx) {
    if (!ctx) return 0;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    return ctx->rewind.available_frames();
}

STORM_GBCA_API void storm_gbca_set_solar_lux(StormGbcaContext* ctx, int lux_0_to_10) {
    if (!ctx) return;
    ctx->lum_periph.lux_level = std::clamp(lux_0_to_10, 0, 10);
}

STORM_GBCA_API void storm_gbca_set_tilt(StormGbcaContext* ctx, float x, float y) {
    if (!ctx) return;
    ctx->rot_periph.tilt_x = std::clamp(x, -4.0f, 4.0f);
    ctx->rot_periph.tilt_y = std::clamp(y, -4.0f, 4.0f);
    if (ctx->using_sameboy && ctx->sb) {
        storm_sb_set_accelerometer(ctx->sb, ctx->rot_periph.tilt_x, ctx->rot_periph.tilt_y);
    }
}

STORM_GBCA_API void storm_gbca_set_gyro(StormGbcaContext* ctx, float z) {
    if (!ctx) return;
    ctx->rot_periph.gyro_z = std::clamp(z, -4.0f, 4.0f);
}

STORM_GBCA_API float storm_gbca_get_rumble(const StormGbcaContext* ctx) {
    if (!ctx || !ctx->rom_loaded) return 0.0f;
    if (ctx->using_sameboy) {
        return storm_sb_get_rumble(ctx->sb);
    }
    return ctx->rumble_periph.current_level;
}

STORM_GBCA_API void storm_gbca_set_rtc(StormGbcaContext* ctx, int use_host, int64_t epoch) {
    if (!ctx) return;
    ctx->rtc_periph.use_host = (use_host != 0);
    ctx->rtc_periph.fixed_epoch = epoch;
}

STORM_GBCA_API void storm_gbca_cheats_clear(StormGbcaContext* ctx) {
    if (!ctx) return;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    ctx->cheats.clear();
    rebuild_cheats_on_core(ctx);
}

STORM_GBCA_API int storm_gbca_cheat_add(StormGbcaContext* ctx, const char* name, const char* code, int enabled) {
    if (!ctx || !code) return -1;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    CheatEntry entry;
    entry.name = name ? name : "Cheat";
    entry.raw_code = code;
    entry.enabled = enabled ? 1 : 0;
    int plat = ctx->rom_loaded ? ctx->rom_info.platform : STORM_PLATFORM_GBA;
    entry.lines = storm::parse_and_normalize_cheat_lines(code, plat);
    if (entry.lines.empty()) return -1;
    ctx->cheats.push_back(std::move(entry));
    rebuild_cheats_on_core(ctx);
    return 0;
}

STORM_GBCA_API size_t storm_gbca_read_memory(const StormGbcaContext* ctx, int region, uint32_t addr, uint8_t* dst, size_t len) {
    if (!ctx || !dst || len == 0) return 0;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    if (!ctx->rom_loaded) return 0;
    if (ctx->using_sameboy) {
        return storm_sb_read_memory(ctx->sb, region, addr, dst, len);
    }

    struct mCore* core = ctx->mgba;
    if (region == STORM_MEM_SYSTEM_BUS) {
        for (size_t i = 0; i < len; ++i) {
            dst[i] = static_cast<uint8_t>(core->busRead8(core, addr + static_cast<uint32_t>(i)));
        }
        return len;
    }

    size_t block_id = 0;
    if (core->platform(core) == mPLATFORM_GBA) {
        switch (region) {
            case STORM_MEM_CART_ROM: block_id = GBA_REGION_ROM0; break;
            case STORM_MEM_CART_RAM: block_id = GBA_REGION_SRAM; break;
            case STORM_MEM_EWRAM:    block_id = GBA_REGION_EWRAM; break;
            case STORM_MEM_IWRAM:
            case STORM_MEM_WRAM:     block_id = GBA_REGION_IWRAM; break;
            case STORM_MEM_VRAM:     block_id = GBA_REGION_VRAM; break;
            case STORM_MEM_OAM:      block_id = GBA_REGION_OAM; break;
            case STORM_MEM_IO:       block_id = GBA_REGION_IO; break;
            default:                 block_id = GBA_REGION_IWRAM; break;
        }
    } else {
        if (region == STORM_MEM_OAM || region == STORM_MEM_IO || region == STORM_MEM_HRAM) {
            uint32_t base = (region == STORM_MEM_OAM) ? GB_BASE_OAM :
                            (region == STORM_MEM_IO)  ? GB_BASE_IO  : GB_BASE_HRAM;
            uint32_t max_sz = (region == STORM_MEM_OAM) ? 0xA0u :
                              (region == STORM_MEM_IO)  ? 0x80u : 0x7Fu;
            if (addr >= max_sz) return 0;
            size_t count = std::min(len, static_cast<size_t>(max_sz - addr));
            for (size_t i = 0; i < count; ++i) {
                dst[i] = static_cast<uint8_t>(core->busRead8(core, base + addr + static_cast<uint32_t>(i)));
            }
            return count;
        }
        switch (region) {
            case STORM_MEM_CART_ROM: block_id = GB_REGION_CART_BANK0; break;
            case STORM_MEM_CART_RAM: block_id = GB_REGION_EXTERNAL_RAM; break;
            case STORM_MEM_WRAM:     block_id = GB_REGION_WORKING_RAM_BANK0; break;
            case STORM_MEM_VRAM:     block_id = GB_REGION_VRAM; break;
            default:                 block_id = GB_REGION_WORKING_RAM_BANK0; break;
        }
    }

    size_t blk_size = 0;
    void* ptr = core->getMemoryBlock(core, block_id, &blk_size);
    if (!ptr || addr >= blk_size) return 0;
    size_t avail = blk_size - addr;
    size_t to_copy = std::min(len, avail);
    std::memcpy(dst, static_cast<const uint8_t*>(ptr) + addr, to_copy);
    return to_copy;
}

static uint32_t map_ra_console(int platform) {
    switch (platform) {
        case STORM_PLATFORM_GB:
        case STORM_PLATFORM_SGB:
            return RC_CONSOLE_GAMEBOY;
        case STORM_PLATFORM_GBC:
            return RC_CONSOLE_GAMEBOY_COLOR;
        case STORM_PLATFORM_GBA:
            return RC_CONSOLE_GAMEBOY_ADVANCE;
        default:
            return RC_CONSOLE_UNKNOWN;
    }
}

STORM_GBCA_API int storm_gbca_compute_ra_hash(const char* rom_path, int platform, char* out_hash_33) {
    if (!rom_path || !out_hash_33) return -1;
    std::vector<uint8_t> bytes;
    std::string vname;
    if (!load_rom_from_path_or_archive(rom_path, bytes, vname)) return -1;
    if (platform == STORM_PLATFORM_NONE) {
        platform = detect_platform_from_buffer(bytes.data(), bytes.size(), vname.c_str());
    }
    return storm_gbca_compute_ra_hash_buffer(bytes.data(), bytes.size(), platform, out_hash_33);
}

STORM_GBCA_API int storm_gbca_compute_ra_hash_buffer(const void* data, size_t size, int platform, char* out_hash_33) {
    if (!data || size == 0 || !out_hash_33) return -1;
    uint32_t console_id = map_ra_console(platform);
    if (console_id == RC_CONSOLE_UNKNOWN) {
        console_id = map_ra_console(detect_platform_from_buffer(static_cast<const uint8_t*>(data), size, ""));
    }
    if (console_id == RC_CONSOLE_UNKNOWN) return -1;
    return rc_hash_generate_from_buffer(out_hash_33, console_id,
                                        static_cast<const uint8_t*>(data), size) ? 0 : -1;
}

STORM_GBCA_API int storm_gbca_write_memory(StormGbcaContext* ctx, int region, uint32_t addr, const uint8_t* src, size_t len) {
    if (!ctx || !src || len == 0) return -1;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    if (!ctx->rom_loaded) return -1;
    if (ctx->using_sameboy && ctx->sb) {
        uint32_t base = 0;
        switch (region) {
            case STORM_MEM_WRAM:     base = 0xC000u; break;
            case STORM_MEM_CART_RAM: base = 0xA000u; break;
            case STORM_MEM_HRAM:     base = 0xFF80u; break;
            case STORM_MEM_OAM:      base = 0xFE00u; break;
            case STORM_MEM_IO:       base = 0xFF00u; break;
            case STORM_MEM_SYSTEM_BUS: base = 0u; break;
            default: base = 0xC000u; break;
        }
        for (size_t i = 0; i < len; ++i) {
            uint32_t a = base + addr + static_cast<uint32_t>(i);
            if (a <= 0xFFFFu) storm_sb_write_memory_u8(ctx->sb, static_cast<uint16_t>(a), src[i]);
        }
        return 0;
    }
    if (!ctx->mgba) return -1;
    struct mCore* core = ctx->mgba;
    if (region == STORM_MEM_SYSTEM_BUS) {
        for (size_t i = 0; i < len; ++i) {
            core->busWrite8(core, addr + static_cast<uint32_t>(i), static_cast<int8_t>(src[i]));
        }
        return 0;
    }
    size_t block_id = 0;
    if (core->platform(core) == mPLATFORM_GBA) {
        switch (region) {
            case STORM_MEM_CART_RAM: block_id = GBA_REGION_SRAM; break;
            case STORM_MEM_EWRAM:    block_id = GBA_REGION_EWRAM; break;
            case STORM_MEM_IWRAM:
            case STORM_MEM_WRAM:     block_id = GBA_REGION_IWRAM; break;
            case STORM_MEM_VRAM:     block_id = GBA_REGION_VRAM; break;
            case STORM_MEM_OAM:      block_id = GBA_REGION_OAM; break;
            default:                 block_id = GBA_REGION_IWRAM; break;
        }
    } else {
        if (region == STORM_MEM_OAM || region == STORM_MEM_IO || region == STORM_MEM_HRAM) {
            uint32_t base = (region == STORM_MEM_OAM) ? GB_BASE_OAM :
                            (region == STORM_MEM_IO)  ? GB_BASE_IO  : GB_BASE_HRAM;
            for (size_t i = 0; i < len; ++i) {
                core->busWrite8(core, base + addr + static_cast<uint32_t>(i), static_cast<int8_t>(src[i]));
            }
            return 0;
        }
        switch (region) {
            case STORM_MEM_CART_RAM: block_id = GB_REGION_EXTERNAL_RAM; break;
            case STORM_MEM_WRAM:     block_id = GB_REGION_WORKING_RAM_BANK0; break;
            case STORM_MEM_VRAM:     block_id = GB_REGION_VRAM; break;
            default:                 block_id = GB_REGION_WORKING_RAM_BANK0; break;
        }
    }
    size_t blk_size = 0;
    void* ptr = core->getMemoryBlock(core, block_id, &blk_size);
    if (!ptr || addr >= blk_size) return -1;
    size_t to_copy = std::min(len, blk_size - addr);
    std::memcpy(static_cast<uint8_t*>(ptr) + addr, src, to_copy);
    return 0;
}

STORM_GBCA_API void storm_gbca_set_upscale(StormGbcaContext* ctx, int scale_1_to_4, int upscale_mode) {
    if (!ctx) return;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    ctx->upscale_scale = std::clamp<int>(scale_1_to_4, 1, 4);
    ctx->upscale_mode = std::clamp<int>(upscale_mode, STORM_UPSCALE_NONE, STORM_UPSCALE_CRT_HD);
}

STORM_GBCA_API int storm_gbca_get_upscaled_size(const StormGbcaContext* ctx, int* out_w, int* out_h) {
    if (!ctx) return -1;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    int w = 0, h = 0;
    if (storm_gbca_get_video_info(ctx, &w, &h, nullptr) != 0) return -1;
    int s = std::clamp(ctx->upscale_scale, 1, 4);
    if (out_w) *out_w = w * s;
    if (out_h) *out_h = h * s;
    return 0;
}

STORM_GBCA_API int storm_gbca_copy_upscaled_frame(StormGbcaContext* ctx, uint32_t* dst, int dst_stride_px, int pixel_format, int* out_w, int* out_h) {
    if (!ctx || !dst) return -1;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    if (!ctx->rom_loaded) return -1;
    const uint32_t* raw = nullptr;
    int w = 0, h = 0;
    if (storm_gbca_get_raw_frame_rgba(ctx, &raw, &w, &h) != 0 || !raw || w <= 0 || h <= 0) return -1;
    const int scale = std::clamp(ctx->upscale_scale, 1, 4);
    const bool is_gba = (ctx->rom_info.platform == STORM_PLATFORM_GBA);
    const int cc_mode = ctx->using_sameboy ? STORM_COLOR_CORRECTION_DISABLED : ctx->cfg.color_correction_mode;

    if (scale <= 1) {
        if (out_w) *out_w = w;
        if (out_h) *out_h = h;
        ctx->video_pp.process_frame(raw, w, h, cc_mode, ctx->cfg.lcd_ghosting,
                                    ctx->cfg.video_filter, is_gba, dst, dst_stride_px, pixel_format);
        return 0;
    }

    ctx->pp_rgba_buf.resize(static_cast<size_t>(w) * static_cast<size_t>(h));
    ctx->video_pp.process_frame(raw, w, h, cc_mode, ctx->cfg.lcd_ghosting,
                                STORM_VIDEO_FILTER_NEAREST, is_gba,
                                ctx->pp_rgba_buf.data(), w, STORM_PIXEL_FORMAT_RGBA8888);

    int uw = 0, uh = 0;
    storm::upscale_rgba_frame(ctx->pp_rgba_buf.data(), w, h, scale, ctx->upscale_mode,
                              ctx->upscaled_rgba_buf, uw, uh);
    if (out_w) *out_w = uw;
    if (out_h) *out_h = uh;

    for (int y = 0; y < uh; ++y) {
        const uint32_t* srow = ctx->upscaled_rgba_buf.data() + static_cast<size_t>(y) * uw;
        uint32_t* drow = dst + static_cast<size_t>(y) * dst_stride_px;
        if (pixel_format == STORM_PIXEL_FORMAT_BGRA8888) {
            for (int x = 0; x < uw; ++x) {
                uint32_t c = srow[x];
                drow[x] = (c & 0xFF00FF00u) | ((c & 0x00FF0000u) >> 16) | ((c & 0x000000FFu) << 16);
            }
        } else {
            std::memcpy(drow, srow, static_cast<size_t>(uw) * sizeof(uint32_t));
        }
    }
    return 0;
}

STORM_GBCA_API void storm_gbca_set_performance(StormGbcaContext* ctx, int cpu_overclock_pct, int frame_skip) {
    if (!ctx) return;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    ctx->cpu_overclock_pct = std::clamp(cpu_overclock_pct, 50, 400);
    ctx->frame_skip = std::clamp(frame_skip, 0, 4);
    if (!ctx->using_sameboy && ctx->mgba) {
        mCoreConfigSetIntValue(&ctx->mgba->config, "frameskip", ctx->frame_skip);
        ctx->mgba->loadConfig(ctx->mgba, &ctx->mgba->config);
    }
}

static size_t read_region_snapshot(const StormGbcaContext* ctx, int region, std::vector<uint8_t>& buf) {
    size_t expected = 0x8000; // 32 KiB default (GBA IWRAM / GB WRAM)
    if (ctx->rom_info.platform == STORM_PLATFORM_GBA) {
        if (region == STORM_MEM_EWRAM) expected = 0x40000;       // 256 KiB
        else if (region == STORM_MEM_IWRAM || region == STORM_MEM_WRAM) expected = 0x8000;
        else if (region == STORM_MEM_CART_RAM) expected = 0x10000;
    } else {
        if (region == STORM_MEM_WRAM) expected = 0x2000;         // 8 KiB (CGB bank0+1 via bus or block)
        else if (region == STORM_MEM_CART_RAM) expected = 0x2000;
        else if (region == STORM_MEM_HRAM) expected = 0x7F;
    }
    buf.assign(expected, 0);
    size_t got = storm_gbca_read_memory(ctx, region, 0, buf.data(), buf.size());
    buf.resize(got);
    return got;
}

STORM_GBCA_API int storm_gbca_mem_search_reset(StormGbcaContext* ctx, int region, int value_size_bytes) {
    if (!ctx) return -1;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    if (!ctx->rom_loaded) return -1;
    std::vector<uint8_t> snap;
    size_t got = read_region_snapshot(ctx, region, snap);
    if (got == 0) return 0;
    ctx->mem_search.reset(region, value_size_bytes, snap.data(), snap.size());
    return ctx->mem_search.count();
}

STORM_GBCA_API int storm_gbca_mem_search_filter(StormGbcaContext* ctx, int cmp_op, uint32_t target_value) {
    if (!ctx) return -1;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    if (!ctx->rom_loaded || !ctx->mem_search.initialized()) return -1;
    std::vector<uint8_t> snap;
    size_t got = read_region_snapshot(ctx, ctx->mem_search.region(), snap);
    if (got == 0) return 0;
    return ctx->mem_search.filter(cmp_op, target_value, snap.data(), snap.size());
}

STORM_GBCA_API int storm_gbca_mem_search_get_results(const StormGbcaContext* ctx, uint32_t* out_addrs, uint32_t* out_vals, int max_count) {
    if (!ctx || !out_addrs || !out_vals || max_count <= 0) return 0;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    if (!ctx->rom_loaded || !ctx->mem_search.initialized()) return 0;
    std::vector<uint8_t> snap;
    read_region_snapshot(ctx, ctx->mem_search.region(), snap);
    return ctx->mem_search.get_results(out_addrs, out_vals, max_count, snap.data(), snap.size());
}

STORM_GBCA_API void storm_gbca_set_optimization_profile(StormGbcaContext* ctx, int profile) {
    if (!ctx) return;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    ctx->optimization_profile = std::clamp<int>(profile, STORM_OPT_LITE, STORM_OPT_HIGH_ACCURACY);
    switch (ctx->optimization_profile) {
        case STORM_OPT_LITE:
            // Old / low-RAM / 32-bit devices: minimal memory footprint & lowest CPU overhead
            ctx->cfg.run_ahead_frames = 0;
            ctx->cfg.audio_interp_mode = STORM_AUDIO_INTERP_NEAREST;
            ctx->cfg.audio_highpass_mode = STORM_AUDIO_HIGHPASS_REMOVE_DC;
            ctx->cfg.rewind_buffer_mb = std::min(ctx->cfg.rewind_buffer_mb, 4);
            ctx->cfg.rewind_interval = std::max(ctx->cfg.rewind_interval, 2);
            ctx->cfg.lcd_ghosting = 0;
            if (ctx->upscale_scale > 1) ctx->upscale_scale = 1;
            break;
        case STORM_OPT_BALANCED:
            ctx->cfg.audio_interp_mode = STORM_AUDIO_INTERP_LINEAR;
            ctx->cfg.audio_highpass_mode = STORM_AUDIO_HIGHPASS_ACCURATE;
            if (ctx->cfg.rewind_buffer_mb < 8) ctx->cfg.rewind_buffer_mb = 12;
            ctx->cfg.rewind_interval = 1;
            break;
        case STORM_OPT_PERFORMANCE:
            ctx->cfg.run_ahead_frames = 0;
            ctx->cfg.audio_interp_mode = STORM_AUDIO_INTERP_LINEAR;
            if (ctx->cfg.rewind_buffer_mb < 16) ctx->cfg.rewind_buffer_mb = 16;
            ctx->cfg.rewind_interval = 1;
            break;
        case STORM_OPT_HIGH_ACCURACY:
            ctx->cfg.audio_interp_mode = STORM_AUDIO_INTERP_HERMITE;
            ctx->cfg.audio_highpass_mode = STORM_AUDIO_HIGHPASS_ACCURATE;
            if (ctx->cfg.rewind_buffer_mb < 24) ctx->cfg.rewind_buffer_mb = 24;
            ctx->cfg.rewind_interval = 1;
            break;
    }
    ctx->rewind.configure(ctx->cfg.rewind_enabled != 0, ctx->cfg.rewind_buffer_mb, ctx->cfg.rewind_interval);
    if (ctx->rom_loaded) {
        if (ctx->using_sameboy) apply_sameboy_config(ctx);
        else apply_mgba_config(ctx);
    }
}

STORM_GBCA_API int storm_gbca_link_start(StormGbcaContext* ctx, int mode, const char* host_ip,
                                         int port, int clock_port, const char* room_name) {
    if (!ctx) return -1;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    // If SameBoy is active for GB/GBC and user attaches Link Cable or GB Printer,
    // transparently switch active session to mGBA so SIO hardware drivers attach seamlessly.
    if (ctx->rom_loaded && ctx->using_sameboy && mode != STORM_LINK_NONE) {
        std::vector<uint8_t> rom_copy = ctx->rom_data;
        std::string name_copy = ctx->rom_virtual_name;
        int prev_gb = ctx->cfg.preferred_core_gb;
        int prev_gbc = ctx->cfg.preferred_core_gbc;
        ctx->cfg.preferred_core_gb = STORM_CORE_MGBA;
        ctx->cfg.preferred_core_gbc = STORM_CORE_MGBA;
        unload_active_cores(ctx);
        ctx->rom_data = std::move(rom_copy);
        ctx->rom_virtual_name = std::move(name_copy);
        init_core_with_loaded_rom(ctx);
        ctx->cfg.preferred_core_gb = prev_gb;
        ctx->cfg.preferred_core_gbc = prev_gbc;
    }
    int plat = ctx->rom_loaded ? ctx->rom_info.platform : STORM_PLATFORM_GBA;
    const char* title = ctx->rom_loaded ? ctx->rom_info.title : "STORM GBCA";
    return ctx->mp.start(ctx->mgba, plat, title, mode, host_ip, port, clock_port, room_name);
}

STORM_GBCA_API void storm_gbca_link_stop(StormGbcaContext* ctx) {
    if (!ctx) return;
    std::lock_guard<std::recursive_mutex> lock(ctx->core_mu);
    ctx->mp.stop(ctx->mgba);
}

STORM_GBCA_API int storm_gbca_link_get_status(const StormGbcaContext* ctx, StormGbcaLinkStatus* out_status) {
    if (!ctx || !out_status) return -1;
    ctx->mp.get_status(out_status);
    return 0;
}

STORM_GBCA_API int storm_gbca_lan_discover(StormGbcaLanRoom* out_rooms, int max_rooms, int timeout_ms) {
    return storm::MultiplayerManager::discover_lan_rooms(out_rooms, max_rooms, timeout_ms);
}

STORM_GBCA_API int storm_gbca_printer_poll_image(StormGbcaContext* ctx, uint32_t* dst_rgba,
                                                 int max_pixels, int* out_w, int* out_h) {
    if (!ctx) return 0;
    return ctx->mp.poll_printer_image(dst_rgba, max_pixels, out_w, out_h);
}

} // extern "C"
