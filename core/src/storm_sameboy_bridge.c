#include "storm_sameboy_bridge.h"
#include "storm_gbca_core.h"
#include "storm_gb_fastboot.h"

#include "gb.h"
#include "display.h"
#include "apu.h"
#include "joypad.h"
#include "save_state.h"
#include "memory.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define SB_MAX_W 256
#define SB_MAX_H 224
#define SB_AUDIO_CAP_FRAMES 8192

struct StormSameboyHandle {
    GB_gameboy_t* gb;
    uint32_t pixels[SB_MAX_W * SB_MAX_H];
    int16_t audio_buf[SB_AUDIO_CAP_FRAMES * 2];
    int audio_frames;
    int collect_audio;
    float rumble_amp;
    int skip_boot;
    char bios_gb[512];
    char bios_gbc[512];
    char bios_sgb[512];
    GB_palette_t custom_palette;
};

static uint32_t sb_rgb_encode(GB_gameboy_t* gb, uint8_t r, uint8_t g, uint8_t b) {
    (void)gb;
    /* RGBA8888 in little-endian memory: R, G, B, A -> 0xAABBGGRR */
    return (0xFFu << 24) | ((uint32_t)b << 16) | ((uint32_t)g << 8) | (uint32_t)r;
}

static void sb_sample_cb(GB_gameboy_t* gb, GB_sample_t* sample) {
    struct StormSameboyHandle* h = (struct StormSameboyHandle*)GB_get_user_data(gb);
    if (!h || !h->collect_audio) return;
    if (h->audio_frames < SB_AUDIO_CAP_FRAMES) {
        h->audio_buf[h->audio_frames * 2 + 0] = sample->left;
        h->audio_buf[h->audio_frames * 2 + 1] = sample->right;
        h->audio_frames++;
    }
}

static void sb_rumble_cb(GB_gameboy_t* gb, double rumble_amplitude) {
    struct StormSameboyHandle* h = (struct StormSameboyHandle*)GB_get_user_data(gb);
    if (h) {
        h->rumble_amp = (float)rumble_amplitude;
    }
}

static void sb_log_cb(GB_gameboy_t* gb, const char* string, GB_log_attributes_t attributes) {
    (void)gb;
    (void)string;
    (void)attributes;
}

static void sb_boot_rom_cb(GB_gameboy_t* gb, GB_boot_rom_t type) {
    struct StormSameboyHandle* h = (struct StormSameboyHandle*)GB_get_user_data(gb);
    const char* path = NULL;
    if (h && !h->skip_boot) {
        switch (type) {
            case GB_BOOT_ROM_DMG_0:
            case GB_BOOT_ROM_DMG:
            case GB_BOOT_ROM_MGB:
                path = h->bios_gb[0] ? h->bios_gb : NULL;
                break;
            case GB_BOOT_ROM_SGB:
            case GB_BOOT_ROM_SGB2:
                path = h->bios_sgb[0] ? h->bios_sgb : NULL;
                break;
            default:
                path = h->bios_gbc[0] ? h->bios_gbc : NULL;
                break;
        }
        if (path && GB_load_boot_rom(gb, path) == 0) {
            return;
        }
    }

    if (type == GB_BOOT_ROM_CGB_0 || type == GB_BOOT_ROM_CGB ||
        type == GB_BOOT_ROM_CGB_E || type == GB_BOOT_ROM_AGB_0 || type == GB_BOOT_ROM_AGB) {
        uint8_t cgb_boot[0x900];
        uint8_t post_b = (type == GB_BOOT_ROM_AGB || type == GB_BOOT_ROM_AGB_0) ? 0x01 : 0x00;
        storm_build_cgb_fastboot(cgb_boot, post_b);
        GB_load_boot_rom_from_buffer(gb, cgb_boot, sizeof(cgb_boot));
    } else {
        uint8_t dmg_boot[0x100];
        uint8_t post_a = (type == GB_BOOT_ROM_MGB || type == GB_BOOT_ROM_SGB2) ? 0xFF : 0x01;
        storm_build_dmg_fastboot(dmg_boot, post_a);
        GB_load_boot_rom_from_buffer(gb, dmg_boot, sizeof(dmg_boot));
    }
}

static GB_model_t map_gb_model(int storm_model, int is_cgb_hint, int is_sgb_hint) {
    switch (storm_model) {
        case STORM_GB_MODEL_DMG:  return GB_MODEL_DMG_B;
        case STORM_GB_MODEL_MGB:  return GB_MODEL_MGB;
        case STORM_GB_MODEL_SGB:  return GB_MODEL_SGB_NTSC;
        case STORM_GB_MODEL_SGB2: return GB_MODEL_SGB2;
        case STORM_GB_MODEL_CGB:  return GB_MODEL_CGB_E;
        case STORM_GB_MODEL_AGB:  return GB_MODEL_AGB_A;
        default:
            if (is_cgb_hint) return GB_MODEL_CGB_E;
            if (is_sgb_hint) return GB_MODEL_SGB_NTSC;
            return GB_MODEL_DMG_B;
    }
}

StormSameboyHandle* storm_sb_create(int gb_model, int sgb_borders,
                                    const char* bios_gb, const char* bios_gbc, const char* bios_sgb,
                                    int skip_boot) {
    StormSameboyHandle* h = (StormSameboyHandle*)calloc(1, sizeof(StormSameboyHandle));
    if (!h) return NULL;
    h->skip_boot = skip_boot;
    if (bios_gb)  strncpy(h->bios_gb, bios_gb, sizeof(h->bios_gb) - 1);
    if (bios_gbc) strncpy(h->bios_gbc, bios_gbc, sizeof(h->bios_gbc) - 1);
    if (bios_sgb) strncpy(h->bios_sgb, bios_sgb, sizeof(h->bios_sgb) - 1);

    h->gb = GB_alloc();
    if (!h->gb) {
        free(h);
        return NULL;
    }
    GB_init(h->gb, map_gb_model(gb_model, 1, 0));
    GB_set_user_data(h->gb, h);
    GB_set_log_callback(h->gb, sb_log_cb);
    GB_set_boot_rom_load_callback(h->gb, sb_boot_rom_cb);
    GB_set_rgb_encode_callback(h->gb, sb_rgb_encode);
    GB_set_pixels_output(h->gb, h->pixels);
    GB_set_sample_rate(h->gb, 48000);
    GB_apu_set_sample_callback(h->gb, sb_sample_cb);
    GB_set_rumble_callback(h->gb, sb_rumble_cb);
    GB_set_rumble_mode(h->gb, GB_RUMBLE_CARTRIDGE_ONLY);
    GB_set_border_mode(h->gb, sgb_borders ? GB_BORDER_SGB : GB_BORDER_NEVER);
    GB_set_palette(h->gb, &GB_PALETTE_DMG);
    h->collect_audio = 1;
    return h;
}

void storm_sb_destroy(StormSameboyHandle* h) {
    if (!h) return;
    if (h->gb) {
        GB_free(h->gb);
        GB_dealloc(h->gb);
    }
    free(h);
}

int storm_sb_load_rom_buffer(StormSameboyHandle* h, const uint8_t* data, size_t size) {
    if (!h || !h->gb || !data || size < 0x150) return -1;
    GB_load_rom_from_buffer(h->gb, data, size);
    GB_reset(h->gb);
    return 0;
}

void storm_sb_reset(StormSameboyHandle* h) {
    if (!h || !h->gb) return;
    GB_reset(h->gb);
}

void storm_sb_set_keys(StormSameboyHandle* h, uint32_t storm_key_mask) {
    if (!h || !h->gb) return;
    unsigned sb_mask = 0;
    if (storm_key_mask & STORM_KEY_RIGHT)  sb_mask |= GB_KEY_RIGHT_MASK;
    if (storm_key_mask & STORM_KEY_LEFT)   sb_mask |= GB_KEY_LEFT_MASK;
    if (storm_key_mask & STORM_KEY_UP)     sb_mask |= GB_KEY_UP_MASK;
    if (storm_key_mask & STORM_KEY_DOWN)   sb_mask |= GB_KEY_DOWN_MASK;
    if (storm_key_mask & STORM_KEY_A)      sb_mask |= GB_KEY_A_MASK;
    if (storm_key_mask & STORM_KEY_B)      sb_mask |= GB_KEY_B_MASK;
    if (storm_key_mask & STORM_KEY_SELECT) sb_mask |= GB_KEY_SELECT_MASK;
    if (storm_key_mask & STORM_KEY_START)  sb_mask |= GB_KEY_START_MASK;
    GB_set_key_mask(h->gb, (GB_key_mask_t)sb_mask);
}

void storm_sb_run_frame(StormSameboyHandle* h, int collect_audio) {
    if (!h || !h->gb) return;
    h->collect_audio = collect_audio;
    if (collect_audio) {
        h->audio_frames = 0;
    }
    GB_run_frame(h->gb);
}

void storm_sb_get_video_size(const StormSameboyHandle* h, int* out_w, int* out_h, float* out_fps) {
    if (!h || !h->gb) return;
    if (out_w) *out_w = (int)GB_get_screen_width(h->gb);
    if (out_h) *out_h = (int)GB_get_screen_height(h->gb);
    if (out_fps) *out_fps = (float)GB_get_usual_frame_rate(h->gb);
}

const uint32_t* storm_sb_get_pixels(const StormSameboyHandle* h) {
    return h ? h->pixels : NULL;
}

int storm_sb_drain_audio(StormSameboyHandle* h, int16_t* dst, int max_frames) {
    if (!h || !dst || max_frames <= 0) return 0;
    int n = h->audio_frames < max_frames ? h->audio_frames : max_frames;
    if (n > 0) {
        memcpy(dst, h->audio_buf, (size_t)n * 2 * sizeof(int16_t));
        int rem = h->audio_frames - n;
        if (rem > 0) {
            memmove(h->audio_buf, h->audio_buf + n * 2, (size_t)rem * 2 * sizeof(int16_t));
        }
        h->audio_frames = rem;
    }
    return n;
}

void storm_sb_set_color_correction(StormSameboyHandle* h, int mode) {
    if (!h || !h->gb) return;
    GB_color_correction_mode_t m = GB_COLOR_CORRECTION_DISABLED;
    switch (mode) {
        case STORM_COLOR_CORRECTION_CORRECT_CURVES:  m = GB_COLOR_CORRECTION_CORRECT_CURVES; break;
        case STORM_COLOR_CORRECTION_MODERN_BALANCED: m = GB_COLOR_CORRECTION_MODERN_BALANCED; break;
        case STORM_COLOR_CORRECTION_MODERN_BOOST:    m = GB_COLOR_CORRECTION_MODERN_BOOST_CONTRAST; break;
        case STORM_COLOR_CORRECTION_REDUCE_CONTRAST: m = GB_COLOR_CORRECTION_REDUCE_CONTRAST; break;
        case STORM_COLOR_CORRECTION_LOW_CONTRAST:    m = GB_COLOR_CORRECTION_LOW_CONTRAST; break;
        case STORM_COLOR_CORRECTION_MODERN_ACCURATE: m = GB_COLOR_CORRECTION_MODERN_ACCURATE; break;
        default: m = GB_COLOR_CORRECTION_DISABLED; break;
    }
    GB_set_color_correction_mode(h->gb, m);
}

void storm_sb_set_highpass(StormSameboyHandle* h, int mode) {
    if (!h || !h->gb) return;
    GB_highpass_mode_t m = GB_HIGHPASS_OFF;
    if (mode == STORM_AUDIO_HIGHPASS_ACCURATE) m = GB_HIGHPASS_ACCURATE;
    else if (mode == STORM_AUDIO_HIGHPASS_REMOVE_DC) m = GB_HIGHPASS_REMOVE_DC_OFFSET;
    GB_set_highpass_filter_mode(h->gb, m);
}

void storm_sb_set_channel_muted(StormSameboyHandle* h, int channel, int muted) {
    if (!h || !h->gb || channel < 0 || channel > 3) return;
    GB_set_channel_muted(h->gb, (GB_channel_t)channel, muted != 0);
}

void storm_sb_set_dmg_palette(StormSameboyHandle* h, const uint32_t rgb[4]) {
    if (!h || !h->gb || !rgb) return;
    for (int i = 0; i < 4; ++i) {
        h->custom_palette.colors[i].r = (uint8_t)((rgb[i] >> 16) & 0xFF);
        h->custom_palette.colors[i].g = (uint8_t)((rgb[i] >> 8) & 0xFF);
        h->custom_palette.colors[i].b = (uint8_t)(rgb[i] & 0xFF);
    }
    h->custom_palette.colors[4] = h->custom_palette.colors[0];
    GB_set_palette(h->gb, &h->custom_palette);
}

void storm_sb_set_border_mode(StormSameboyHandle* h, int sgb_borders) {
    if (!h || !h->gb) return;
    GB_set_border_mode(h->gb, sgb_borders ? GB_BORDER_SGB : GB_BORDER_NEVER);
}

void storm_sb_set_accelerometer(StormSameboyHandle* h, float x, float y) {
    if (!h || !h->gb) return;
    GB_set_accelerometer_values(h->gb, (double)x, (double)y);
}

float storm_sb_get_rumble(const StormSameboyHandle* h) {
    return h ? h->rumble_amp : 0.0f;
}

size_t storm_sb_state_size(StormSameboyHandle* h) {
    if (!h || !h->gb) return 0;
    return GB_get_save_state_size(h->gb);
}

int storm_sb_save_state(StormSameboyHandle* h, uint8_t* dst, size_t size) {
    if (!h || !h->gb || !dst) return -1;
    size_t needed = GB_get_save_state_size(h->gb);
    if (size < needed) return -1;
    GB_save_state_to_buffer(h->gb, dst);
    return 0;
}

int storm_sb_load_state(StormSameboyHandle* h, const uint8_t* src, size_t size) {
    if (!h || !h->gb || !src || size == 0) return -1;
    return GB_load_state_from_buffer(h->gb, src, size) == 0 ? 0 : -1;
}

size_t storm_sb_sram_size(StormSameboyHandle* h) {
    if (!h || !h->gb) return 0;
    int sz = GB_save_battery_size(h->gb);
    return sz > 0 ? (size_t)sz : 0;
}

int storm_sb_save_sram_buffer(StormSameboyHandle* h, uint8_t* dst, size_t size) {
    if (!h || !h->gb || !dst || size == 0) return -1;
    int rc = GB_save_battery_to_buffer(h->gb, dst, size);
    if (rc == 0) GB_clear_battery_dirty(h->gb);
    return rc == 0 ? 0 : -1;
}

int storm_sb_load_sram_buffer(StormSameboyHandle* h, const uint8_t* src, size_t size) {
    if (!h || !h->gb || !src || size == 0) return -1;
    GB_load_battery_from_buffer(h->gb, src, size);
    GB_clear_battery_dirty(h->gb);
    return 0;
}

int storm_sb_save_sram_file(StormSameboyHandle* h, const char* path) {
    if (!h || !h->gb || !path) return -1;
    int rc = GB_save_battery(h->gb, path);
    if (rc == 0) GB_clear_battery_dirty(h->gb);
    return rc == 0 ? 0 : -1;
}

int storm_sb_load_sram_file(StormSameboyHandle* h, const char* path) {
    if (!h || !h->gb || !path) return -1;
    return GB_load_battery(h->gb, path) == 0 ? 0 : -1;
}

int storm_sb_is_sram_dirty(StormSameboyHandle* h) {
    if (!h || !h->gb) return 0;
    return GB_get_battery_dirty(h->gb) ? 1 : 0;
}

size_t storm_sb_read_memory(StormSameboyHandle* h, int region, uint32_t addr, uint8_t* dst, size_t len) {
    if (!h || !h->gb || !dst || len == 0) return 0;
    if (region == STORM_MEM_SYSTEM_BUS) {
        for (size_t i = 0; i < len; ++i) {
            uint32_t a = addr + (uint32_t)i;
            if (a > 0xFFFFu) return i;
            dst[i] = GB_safe_read_memory(h->gb, (uint16_t)a);
        }
        return len;
    }

    GB_direct_access_t acc = GB_DIRECT_ACCESS_RAM;
    switch (region) {
        case STORM_MEM_CART_ROM: acc = GB_DIRECT_ACCESS_ROM; break;
        case STORM_MEM_CART_RAM: acc = GB_DIRECT_ACCESS_CART_RAM; break;
        case STORM_MEM_WRAM:     acc = GB_DIRECT_ACCESS_RAM; break;
        case STORM_MEM_VRAM:     acc = GB_DIRECT_ACCESS_VRAM; break;
        case STORM_MEM_OAM:      acc = GB_DIRECT_ACCESS_OAM; break;
        case STORM_MEM_HRAM:     acc = GB_DIRECT_ACCESS_HRAM; break;
        case STORM_MEM_IO:       acc = GB_DIRECT_ACCESS_IO; break;
        default: break;
    }
    size_t blk_size = 0;
    uint16_t bank = 0;
    uint8_t* ptr = (uint8_t*)GB_get_direct_access(h->gb, acc, &blk_size, &bank);
    if (!ptr || addr >= blk_size) return 0;
    size_t avail = blk_size - addr;
    size_t to_copy = len < avail ? len : avail;
    memcpy(dst, ptr + addr, to_copy);
    return to_copy;
}

void storm_sb_write_memory_u8(StormSameboyHandle* h, uint16_t addr, uint8_t val) {
    if (!h || !h->gb) return;
    GB_write_memory(h->gb, addr, val);
}

void storm_sb_patch_rom_byte(StormSameboyHandle* h, uint32_t rom_offset, uint8_t val, int has_cmp, uint8_t cmp) {
    if (!h || !h->gb) return;
    size_t rom_size = 0;
    uint16_t bank = 0;
    uint8_t* rom = (uint8_t*)GB_get_direct_access(h->gb, GB_DIRECT_ACCESS_ROM, &rom_size, &bank);
    if (!rom || rom_size == 0) return;
    if (rom_offset < 0x4000u && rom_offset < rom_size) {
        if (!has_cmp || rom[rom_offset] == cmp) {
            rom[rom_offset] = val;
        }
    } else if (rom_offset >= 0x4000u && rom_offset < 0x8000u) {
        /* Patch across all switchable banks if compare byte matches */
        for (size_t off = rom_offset; off < rom_size; off += 0x4000u) {
            if (!has_cmp || rom[off] == cmp) {
                rom[off] = val;
            }
        }
    }
}

int storm_sb_is_cgb(const StormSameboyHandle* h) {
    return (h && h->gb) ? (GB_is_cgb(h->gb) ? 1 : 0) : 0;
}

int storm_sb_is_sgb(const StormSameboyHandle* h) {
    return (h && h->gb) ? (GB_is_sgb(h->gb) ? 1 : 0) : 0;
}

int storm_sb_has_accelerometer(const StormSameboyHandle* h) {
    return (h && h->gb) ? (GB_has_accelerometer(h->gb) ? 1 : 0) : 0;
}

int storm_sb_has_rtc(const StormSameboyHandle* h) {
    return (h && h->gb) ? (GB_rom_supports_alarms(h->gb) ? 1 : 0) : 0;
}
