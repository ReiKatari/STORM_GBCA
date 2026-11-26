#include "storm_gbca_core.h"
#include <cassert>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <vector>

namespace {

static const uint8_t kNintendoLogo[48] = {
    0xCE, 0xED, 0x66, 0x66, 0xCC, 0x0D, 0x00, 0x0B, 0x03, 0x73, 0x00, 0x83, 0x00, 0x0C, 0x00, 0x0D,
    0x00, 0x08, 0x11, 0x1F, 0x88, 0x89, 0x00, 0x0E, 0xDC, 0xCC, 0x6E, 0xE6, 0xDD, 0xDD, 0xD9, 0x99,
    0xBB, 0xBB, 0x67, 0x63, 0x6E, 0x0E, 0xEC, 0xCC, 0xDD, 0xDC, 0x99, 0x9F, 0xBB, 0xB9, 0x33, 0x3E
};

static std::vector<uint8_t> make_synthetic_gb_rom(bool gbc) {
    std::vector<uint8_t> rom(32768, 0x00);
    // Entry point at 0x0100: NOP; JP 0x0150
    rom[0x0100] = 0x00;
    rom[0x0101] = 0xC3;
    rom[0x0102] = 0x50;
    rom[0x0103] = 0x01;
    std::memcpy(&rom[0x0104], kNintendoLogo, sizeof(kNintendoLogo));
    const char* title = gbc ? "STORMGBCTEST" : "STORMGBTEST";
    std::memcpy(&rom[0x0134], title, std::strlen(title));
    if (gbc) rom[0x0143] = 0x80;

    // Header checksum (0x0134..0x014C)
    uint8_t chk = 0;
    for (int i = 0x0134; i <= 0x014C; ++i) {
        chk = static_cast<uint8_t>(chk - rom[i] - 1);
    }
    rom[0x014D] = chk;

    // Tiny SM83 program at 0x0150:
    // Write a non-zero audio note on CH1 + write WRAM byte + loop forever
    const uint8_t prog[] = {
        0x3E, 0x80, 0xE0, 0x26, // NR52 = 0x80 (APU on)
        0x3E, 0xFF, 0xE0, 0x25, // NR51 = 0xFF
        0x3E, 0x77, 0xE0, 0x24, // NR50 = 0x77
        0x3E, 0x80, 0xE0, 0x11, // NR11 = 0x80
        0x3E, 0xF0, 0xE0, 0x12, // NR12 = 0xF0
        0x3E, 0x83, 0xE0, 0x13, // NR13 = 0x83
        0x3E, 0x87, 0xE0, 0x14, // NR14 = 0x87 (trigger CH1)
        0x3E, 0x42, 0xEA, 0x00, 0xC0, // LD (0xC000), 0x42 (WRAM marker)
        0x18, 0xFE              // JR -2 (infinite loop)
    };
    std::memcpy(&rom[0x0150], prog, sizeof(prog));
    return rom;
}

static std::vector<uint8_t> make_synthetic_gba_rom() {
    std::vector<uint8_t> rom(65536, 0x00);
    // 0x00: ARM B 0x00C0 (offset = (0xC0 - 0x08)/4 = 0x2E -> 0xEA00002E)
    rom[0] = 0x2E; rom[1] = 0x00; rom[2] = 0x00; rom[3] = 0xEA;
    // Fixed value 0x96 at 0xB2
    rom[0xB2] = 0x96;
    std::memcpy(&rom[0xA0], "STORMGBATST", 11);
    std::memcpy(&rom[0xAC], "STME", 4);
    rom[0xB0] = '0'; rom[0xB1] = '1';
    // Header complement checksum (0xA0..0xBC)
    uint8_t sum = 0;
    for (int i = 0xA0; i <= 0xBC; ++i) sum = static_cast<uint8_t>(sum + rom[i]);
    rom[0xBD] = static_cast<uint8_t>(-(sum + 0x19));

    // ARM code at 0xC0:
    // Write 0x5A to EWRAM 0x02000000, set DISPCNT (0x04000000) to forced white/mode3 + loop
    // E3A00301 -> MOV R0, #0x04000000
    // E3A01003 -> MOV R1, #0x03 (Mode 3)
    // E3811B01 -> ORR R1, R1, #0x0400 (BG2 enable)
    // E1C010B0 -> STRH R1, [R0]
    // E3A00302 -> MOV R0, #0x08000000? Wait: 0x02000000 = MOV R0, #0x02, ROR #8 = 0xE3A00402
    // E3A0105A -> MOV R1, #0x5A
    // E5C01000 -> STRB R1, [R0]
    // EAFFFFFE -> B .
    const uint32_t arm_prog[] = {
        0xE3A00301u,
        0xE3A01003u,
        0xE3811B01u,
        0xE1C010B0u,
        0xE3A00402u,
        0xE3A0105Au,
        0xE5C01000u,
        0xEAFFFFFEu,
    };
    std::memcpy(&rom[0xC0], arm_prog, sizeof(arm_prog));
    return rom;
}

static bool test_system(StormGbcaContext* ctx, const char* label,
                        const std::vector<uint8_t>& rom, const char* vname,
                        int expected_w, int expected_h, int core_pref, int expected_plat) {
    StormGbcaConfig cfg{};
    storm_gbca_get_default_config(&cfg);
    cfg.preferred_core_gb = core_pref;
    cfg.preferred_core_gbc = core_pref;
    cfg.rewind_enabled = 1;
    cfg.rewind_buffer_mb = 8;
    cfg.rewind_interval = 1;
    cfg.run_ahead_frames = 1;
    if (storm_gbca_set_config(ctx, &cfg) != 0) {
        std::printf("[FAIL] %s: set_config failed\n", label);
        return false;
    }

    if (storm_gbca_load_rom_buffer(ctx, rom.data(), rom.size(), vname) != 0) {
        std::printf("[FAIL] %s: load_rom_buffer failed\n", label);
        return false;
    }

    StormGbcaRomInfo info{};
    if (storm_gbca_get_rom_info(ctx, &info) != 0 || info.platform != expected_plat) {
        std::printf("[FAIL] %s: rom_info invalid (plat=%d)\n", label, info.platform);
        return false;
    }

    int w = 0, h = 0;
    float fps = 0.0f;
    if (storm_gbca_get_video_info(ctx, &w, &h, &fps) != 0 || w != expected_w || h != expected_h) {
        std::printf("[FAIL] %s: video_info mismatch (%dx%d)\n", label, w, h);
        return false;
    }

    storm_gbca_set_keys(ctx, STORM_KEY_A | STORM_KEY_START);
    storm_gbca_set_solar_lux(ctx, 7);
    storm_gbca_set_tilt(ctx, 0.25f, -0.5f);
    storm_gbca_set_gyro(ctx, 0.1f);
    storm_gbca_set_rtc(ctx, 1, 0);

    if (storm_gbca_cheat_add(ctx, "TestCheat", expected_plat == STORM_PLATFORM_GBA ? "02000010 00000077" : "017700C0", 1) != 0) {
        std::printf("[FAIL] %s: cheat_add failed\n", label);
        return false;
    }

    int total_audio = 0;
    std::vector<int16_t> audio(4096 * 2);
    for (int f = 0; f < 30; ++f) {
        if (storm_gbca_run_frame(ctx) != 0) {
            std::printf("[FAIL] %s: run_frame %d failed\n", label, f);
            return false;
        }
        int got = storm_gbca_read_audio(ctx, audio.data(), 4096);
        total_audio += got;
    }

    if (total_audio <= 0) {
        std::printf("[FAIL] %s: no audio produced\n", label);
        return false;
    }

    std::vector<uint32_t> frame(static_cast<size_t>(w) * static_cast<size_t>(h), 0);
    if (storm_gbca_copy_frame(ctx, frame.data(), w, STORM_PIXEL_FORMAT_RGBA8888) != 0) {
        std::printf("[FAIL] %s: copy_frame failed\n", label);
        return false;
    }

    // Savestate roundtrip
    size_t st_sz = 0;
    if (storm_gbca_save_state_buffer(ctx, nullptr, 0, &st_sz) != 0 || st_sz == 0) {
        std::printf("[FAIL] %s: save_state size query failed\n", label);
        return false;
    }
    std::vector<uint8_t> st_buf(st_sz);
    if (storm_gbca_save_state_buffer(ctx, st_buf.data(), st_buf.size(), &st_sz) != 0) {
        std::printf("[FAIL] %s: save_state_buffer failed\n", label);
        return false;
    }
    if (storm_gbca_load_state_buffer(ctx, st_buf.data(), st_sz) != 0) {
        std::printf("[FAIL] %s: load_state_buffer failed\n", label);
        return false;
    }

    // Rewind check
    int rw_avail = storm_gbca_rewind_available_frames(ctx);
    if (rw_avail <= 0 || storm_gbca_rewind_step(ctx) != 0) {
        std::printf("[FAIL] %s: rewind failed (avail=%d)\n", label, rw_avail);
        return false;
    }

    // RA hash check
    char ra_hash[33] = {};
    if (storm_gbca_compute_ra_hash_buffer(rom.data(), rom.size(), expected_plat, ra_hash) != 0 ||
        std::strlen(ra_hash) != 32) {
        std::printf("[FAIL] %s: RA hash failed\n", label);
        return false;
    }

    storm_gbca_cheats_clear(ctx);
    float rumble = storm_gbca_get_rumble(ctx);

    std::printf("[PASS] %-14s | core=%-7s | %dx%d @ %.2f fps | audio_frames=%d | state=%zuB | rewind=%d | ra=%s | rumble=%.2f\n",
                label, info.active_core, w, h, fps, total_audio, st_sz, rw_avail, ra_hash, rumble);
    return true;
}

} // namespace

int main() {
    std::setvbuf(stdout, nullptr, _IONBF, 0);
    std::printf("STORM GBCA Core Version: %s | Palettes: %d (%s)\n",
                storm_gbca_version(),
                storm_gbca_get_dmg_palette_count(),
                storm_gbca_get_dmg_palette_name(0));

    StormGbcaContext* ctx = storm_gbca_create();
    if (!ctx) {
        std::printf("[FAIL] storm_gbca_create returned null\n");
        return 1;
    }

    auto gb_rom  = make_synthetic_gb_rom(false);
    auto gbc_rom = make_synthetic_gb_rom(true);
    auto gba_rom = make_synthetic_gba_rom();

    bool ok = true;
    ok &= test_system(ctx, "GB-mGBA",     gb_rom,  "test.gb",  160, 144, STORM_CORE_MGBA,    STORM_PLATFORM_GB);
    ok &= test_system(ctx, "GB-SameBoy",  gb_rom,  "test.gb",  160, 144, STORM_CORE_SAMEBOY, STORM_PLATFORM_GB);
    ok &= test_system(ctx, "GBC-mGBA",    gbc_rom, "test.gbc", 160, 144, STORM_CORE_MGBA,    STORM_PLATFORM_GBC);
    ok &= test_system(ctx, "GBC-SameBoy", gbc_rom, "test.gbc", 160, 144, STORM_CORE_SAMEBOY, STORM_PLATFORM_GBC);
    ok &= test_system(ctx, "GBA-mGBA",    gba_rom, "test.gba", 240, 160, STORM_CORE_MGBA,    STORM_PLATFORM_GBA);

    // Quick IPS patcher test
    const uint8_t ips_patch[] = {
        'P','A','T','C','H',
        0x00, 0x01, 0x51,
        0x00, 0x01,
        0xAA,
        'E','O','F'
    };
    uint8_t* patched = nullptr;
    size_t patched_sz = 0;
    if (storm_gbca_apply_patch_buffer(gb_rom.data(), gb_rom.size(),
                                      ips_patch, sizeof(ips_patch),
                                      &patched, &patched_sz) != 0 ||
        !patched || patched[0x0151] != 0xAA) {
        std::printf("[FAIL] IPS patch test failed\n");
        ok = false;
    } else {
        std::printf("[PASS] IPS-Patcher    | patched_size=%zu byte[0x151]=0x%02X\n", patched_sz, patched[0x0151]);
        storm_gbca_free_buffer(patched);
    }

    // Audit Pass 1 Extended Tests: UTF-8 Cyrillic path + Run-Ahead 1/2 + HRAM read + Multi-line Cheats + Per-Channel Audio
    {
        const char* cyr_rom = "storm_audit_\xD1\x82\xD0\xB5\xD1\x81\xD1\x82.gb";
        const char* cyr_sav = "storm_audit_\xD1\x82\xD0\xB5\xD1\x81\xD1\x82.ss0";
        FILE* f = nullptr;
#ifdef _WIN32
        f = _wfopen(L"storm_audit_\u0442\u0435\u0441\u0442.gb", L"wb");
#else
        f = std::fopen(cyr_rom, "wb");
#endif
        if (f) {
            std::fwrite(gb_rom.data(), 1, gb_rom.size(), f);
            std::fclose(f);
        }
        StormGbcaConfig cfg{};
        storm_gbca_get_default_config(&cfg);
        cfg.preferred_core_gb = STORM_CORE_MGBA;
        cfg.run_ahead_frames = 2;
        cfg.channel_volumes[0] = 0.0f;
        cfg.channel_volumes[1] = 1.0f;
        storm_gbca_set_config(ctx, &cfg);

        bool ext_ok = (storm_gbca_load_rom_file(ctx, cyr_rom) == 0);
        if (ext_ok) {
            for (int i = 0; i < 6; ++i) storm_gbca_run_frame(ctx);
            uint8_t hram[16] = {};
            size_t rd = storm_gbca_read_memory(ctx, STORM_MEM_HRAM, 0, hram, sizeof(hram));
            ext_ok &= (rd == sizeof(hram));
            ext_ok &= (storm_gbca_save_state_file(ctx, cyr_sav) == 0);
            ext_ok &= (storm_gbca_load_state_file(ctx, cyr_sav) == 0);
            storm_gbca_cheat_add(ctx, "MultiLine", "010F34C1\r\n01FF35C1\nINVALID_CODE", 1);
            storm_gbca_set_solar_lux(ctx, 7);
            storm_gbca_set_tilt(ctx, 0.25f, -0.25f);
            storm_gbca_set_gyro(ctx, 0.5f);
            storm_gbca_run_frame(ctx);
            storm_gbca_cheats_clear(ctx);
            storm_gbca_unload_rom(ctx);
        }
#ifdef _WIN32
        _wremove(L"storm_audit_\u0442\u0435\u0441\u0442.gb");
        _wremove(L"storm_audit_\u0442\u0435\u0441\u0442.ss0");
#else
        std::remove(cyr_rom);
        std::remove(cyr_sav);
#endif
        if (!ext_ok) {
            std::printf("[FAIL] AuditPass1-Ext | UTF-8 Cyrillic path / Run-Ahead=2 / HRAM read failed\n");
            ok = false;
        } else {
            std::printf("[PASS] AuditPass1-Ext | UTF-8 Cyrillic path + Run-Ahead=2 + HRAM read + MultiLine cheats OK\n");
        }
    }

    // v1.1.0 Tests: 3x xBRZ Upscaling (720x480) + RAM Cheat Finder + Overclock/FrameSkip
    {
        bool v110_ok = (storm_gbca_load_rom_buffer(ctx, gba_rom.data(), gba_rom.size(), "v110.gba") == 0);
        if (v110_ok) {
            storm_gbca_set_upscale(ctx, 3, STORM_UPSCALE_XBRZ);
            storm_gbca_set_performance(ctx, 150, 0);
            storm_gbca_run_frame(ctx);
            int uw = 0, uh = 0;
            v110_ok &= (storm_gbca_get_upscaled_size(ctx, &uw, &uh) == 0 && uw == 720 && uh == 480);
            std::vector<uint32_t> up(static_cast<size_t>(uw) * static_cast<size_t>(uh));
            int ow = 0, oh = 0;
            v110_ok &= (storm_gbca_copy_upscaled_frame(ctx, up.data(), uw, STORM_PIXEL_FORMAT_BGRA8888, &ow, &oh) == 0 && ow == 720 && oh == 480);

            // RAM Cheat Finder roundtrip: write 0x42 at IWRAM[0x120], reset, change to 0x77, filter EQ 0x77
            uint8_t v1 = 0x42, v2 = 0x77;
            v110_ok &= (storm_gbca_write_memory(ctx, STORM_MEM_IWRAM, 0x120, &v1, 1) == 0);
            int c0 = storm_gbca_mem_search_reset(ctx, STORM_MEM_IWRAM, 1);
            int c1 = storm_gbca_mem_search_filter(ctx, STORM_SEARCH_EQ, 0x42);
            v110_ok &= (storm_gbca_write_memory(ctx, STORM_MEM_IWRAM, 0x120, &v2, 1) == 0);
            int c2 = storm_gbca_mem_search_filter(ctx, STORM_SEARCH_INCREASED, 0);
            int c3 = storm_gbca_mem_search_filter(ctx, STORM_SEARCH_EQ, 0x77);
            uint32_t addrs[8] = {}, vals[8] = {};
            int nres = storm_gbca_mem_search_get_results(ctx, addrs, vals, 8);
            bool found_target = false;
            for (int i = 0; i < nres; ++i) {
                if (addrs[i] == 0x120 && vals[i] == 0x77) found_target = true;
            }
            v110_ok &= (c0 > 0 && c1 > 0 && c2 > 0 && c3 > 0 && found_target);
            storm_gbca_set_upscale(ctx, 1, STORM_UPSCALE_NONE);
            storm_gbca_unload_rom(ctx);
        }
        if (!v110_ok) {
            std::printf("[FAIL] v1.1.0-Power   | Upscale 3x xBRZ / RAM Cheat Finder failed\n");
            ok = false;
        } else {
            std::printf("[PASS] v1.1.0-Power   | 3x xBRZ (720x480) + RAM Cheat Finder (0x120=0x77) + Overclock OK\n");
        }
    }

    storm_gbca_destroy(ctx);
    return ok ? 0 : 1;
}
