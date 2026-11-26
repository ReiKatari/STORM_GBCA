#ifndef STORM_GBCA_CORE_H
#define STORM_GBCA_CORE_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#if defined(_WIN32)
  #if defined(STORM_GBCA_STATIC)
    #define STORM_GBCA_API
  #elif defined(STORM_GBCA_EXPORTS)
    #define STORM_GBCA_API __declspec(dllexport)
  #else
    #define STORM_GBCA_API __declspec(dllimport)
  #endif
#else
  #define STORM_GBCA_API __attribute__((visibility("default")))
#endif

#define STORM_GBCA_VERSION_STRING "1.1.0"

/* Key bitmask */
enum {
    STORM_KEY_A      = 1u << 0,
    STORM_KEY_B      = 1u << 1,
    STORM_KEY_SELECT = 1u << 2,
    STORM_KEY_START  = 1u << 3,
    STORM_KEY_RIGHT  = 1u << 4,
    STORM_KEY_LEFT   = 1u << 5,
    STORM_KEY_UP     = 1u << 6,
    STORM_KEY_DOWN   = 1u << 7,
    STORM_KEY_R      = 1u << 8,
    STORM_KEY_L      = 1u << 9,
};

/* Platform identifiers */
enum {
    STORM_PLATFORM_NONE = 0,
    STORM_PLATFORM_GB   = 1,
    STORM_PLATFORM_GBC  = 2,
    STORM_PLATFORM_GBA  = 3,
    STORM_PLATFORM_SGB  = 4,
};

/* Core backend selection */
enum {
    STORM_CORE_MGBA    = 0,
    STORM_CORE_SAMEBOY = 1,
};

/* Game Boy hardware model override */
enum {
    STORM_GB_MODEL_AUTO = 0,
    STORM_GB_MODEL_DMG  = 1,
    STORM_GB_MODEL_MGB  = 2,
    STORM_GB_MODEL_SGB  = 3,
    STORM_GB_MODEL_SGB2 = 4,
    STORM_GB_MODEL_CGB  = 5,
    STORM_GB_MODEL_AGB  = 6,
};

/* Pixel output formats for storm_gbca_copy_frame */
enum {
    STORM_PIXEL_FORMAT_RGBA8888 = 0,
    STORM_PIXEL_FORMAT_BGRA8888 = 1,
};

/* Color correction modes (SameBoy 7 modes + NBA GBA modes) */
enum {
    STORM_COLOR_CORRECTION_DISABLED        = 0,
    STORM_COLOR_CORRECTION_CORRECT_CURVES  = 1,
    STORM_COLOR_CORRECTION_MODERN_BALANCED = 2,
    STORM_COLOR_CORRECTION_MODERN_BOOST    = 3,
    STORM_COLOR_CORRECTION_REDUCE_CONTRAST = 4,
    STORM_COLOR_CORRECTION_LOW_CONTRAST    = 5,
    STORM_COLOR_CORRECTION_MODERN_ACCURATE = 6,
    STORM_COLOR_CORRECTION_GBA_AGB_AGS     = 7,
};

/* Video filter modes */
enum {
    STORM_VIDEO_FILTER_NEAREST        = 0,
    STORM_VIDEO_FILTER_BILINEAR       = 1,
    STORM_VIDEO_FILTER_LCD_GRID       = 2,
    STORM_VIDEO_FILTER_RGB_SUBPIXEL   = 3,
    STORM_VIDEO_FILTER_XBRZ_LITE      = 4,
    STORM_VIDEO_FILTER_CRT_SCANLINES  = 5,
    STORM_VIDEO_FILTER_SHARP_BILINEAR = 6,
};

/* Audio high-pass filter modes */
enum {
    STORM_AUDIO_HIGHPASS_OFF       = 0,
    STORM_AUDIO_HIGHPASS_ACCURATE  = 1,
    STORM_AUDIO_HIGHPASS_REMOVE_DC = 2,
};

/* Audio interpolation modes */
enum {
    STORM_AUDIO_INTERP_NEAREST = 0,
    STORM_AUDIO_INTERP_LINEAR  = 1,
    STORM_AUDIO_INTERP_HERMITE = 2,
};

/* Cartridge hardware capability flags */
enum {
    STORM_HW_RTC    = 1u << 0,
    STORM_HW_RUMBLE = 1u << 1,
    STORM_HW_SOLAR  = 1u << 2,
    STORM_HW_GYRO   = 1u << 3,
    STORM_HW_TILT   = 1u << 4,
    STORM_HW_SGB    = 1u << 5,
    STORM_HW_CGB    = 1u << 6,
};

/* Memory regions for storm_gbca_read_memory */
enum {
    STORM_MEM_CART_ROM = 0,
    STORM_MEM_CART_RAM = 1,
    STORM_MEM_WRAM     = 2,
    STORM_MEM_IWRAM    = 3,
    STORM_MEM_EWRAM    = 4,
    STORM_MEM_VRAM     = 5,
    STORM_MEM_OAM      = 6,
    STORM_MEM_HRAM     = 7,
    STORM_MEM_IO       = 8,
    STORM_MEM_SYSTEM_BUS = 9,
};

typedef struct StormGbcaContext StormGbcaContext;

typedef struct StormGbcaRomInfo {
    int      platform;
    char     title[64];
    char     game_code[16];
    char     maker[8];
    char     region[16];
    uint32_t crc32;
    char     md5[33];
    uint32_t rom_size;
    uint32_t hw_flags;
    char     active_core[16];
} StormGbcaRomInfo;

typedef struct StormGbcaAudioConfig {
    int   highpass_mode;
    int   interp_mode;
    float channel_volumes[6]; /* PSG1, PSG2, PSG3, PSG4, DMA_A, DMA_B */
    float master_volume;
    int   dynamic_rate_control;
    float drc_max_skew;
    int   target_sample_rate; /* default 48000 */
} StormGbcaAudioConfig;

typedef struct StormGbcaConfig {
    int      preferred_core_gb;
    int      preferred_core_gbc;
    int      preferred_core_gba;
    int      gb_model;
    int      sgb_borders;
    int      color_correction_mode;
    int      dmg_palette_index;       /* 0..27, or -1 for custom_dmg_palette */
    uint32_t custom_dmg_palette[4];   /* 0x00RRGGBB */
    int      lcd_ghosting;            /* 0..100 */
    int      video_filter;            /* 0..6 */
    int      audio_highpass_mode;     /* 0..2 */
    int      audio_interp_mode;       /* 0..2 */
    float    channel_volumes[6];      /* PSG1-4, DMA A, DMA B */
    float    master_volume;           /* 0.0..2.0 */
    int      run_ahead_frames;        /* 0..2 */
    int      rewind_enabled;          /* 0 or 1 */
    int      rewind_buffer_mb;        /* e.g. 16 */
    int      rewind_interval;         /* frames between snapshots (default 1) */
    char     bios_gb_path[512];
    char     bios_gbc_path[512];
    char     bios_gba_path[512];
    char     bios_sgb_path[512];
    int      skip_bios_intro;
    int      dynamic_rate_control;
    float    drc_max_skew;
} StormGbcaConfig;

/* Lifecycle */
STORM_GBCA_API const char* storm_gbca_version(void);
STORM_GBCA_API StormGbcaContext* storm_gbca_create(void);
STORM_GBCA_API void storm_gbca_destroy(StormGbcaContext* ctx);
STORM_GBCA_API void storm_gbca_reset(StormGbcaContext* ctx);

/* Configuration */
STORM_GBCA_API void storm_gbca_get_default_config(StormGbcaConfig* out_cfg);
STORM_GBCA_API int storm_gbca_set_config(StormGbcaContext* ctx, const StormGbcaConfig* cfg);
STORM_GBCA_API int storm_gbca_set_audio_config(StormGbcaContext* ctx, const StormGbcaAudioConfig* cfg);

/* ROM loading & inspection */
STORM_GBCA_API int storm_gbca_load_rom_file(StormGbcaContext* ctx, const char* rom_path);
STORM_GBCA_API int storm_gbca_load_rom_buffer(StormGbcaContext* ctx, const void* data, size_t size, const char* virtual_name);
STORM_GBCA_API void storm_gbca_unload_rom(StormGbcaContext* ctx);
STORM_GBCA_API int storm_gbca_inspect_rom_file(const char* rom_path, StormGbcaRomInfo* out_info);
STORM_GBCA_API int storm_gbca_inspect_rom_buffer(const void* data, size_t size, const char* virtual_name, StormGbcaRomInfo* out_info);
STORM_GBCA_API int storm_gbca_get_rom_info(const StormGbcaContext* ctx, StormGbcaRomInfo* out_info);

/* IPS/UPS/BPS soft patching */
STORM_GBCA_API int storm_gbca_apply_patch_file(StormGbcaContext* ctx, const char* patch_path);
STORM_GBCA_API int storm_gbca_apply_patch_buffer(const void* rom_data, size_t rom_size,
                                                 const void* patch_data, size_t patch_size,
                                                 uint8_t** out_patched, size_t* out_size);
STORM_GBCA_API void storm_gbca_free_buffer(void* ptr);

/* Execution */
STORM_GBCA_API void storm_gbca_set_keys(StormGbcaContext* ctx, uint32_t mask);
STORM_GBCA_API int storm_gbca_run_frame(StormGbcaContext* ctx);
STORM_GBCA_API void storm_gbca_set_fast_forward(StormGbcaContext* ctx, float mult);

/* Video */
STORM_GBCA_API int storm_gbca_get_video_info(const StormGbcaContext* ctx, int* out_w, int* out_h, float* out_fps);
STORM_GBCA_API int storm_gbca_copy_frame(StormGbcaContext* ctx, uint32_t* dst, int dst_stride_px, int pixel_format);
STORM_GBCA_API int storm_gbca_get_raw_frame_rgba(const StormGbcaContext* ctx, const uint32_t** out_ptr, int* out_w, int* out_h);

/* Audio */
STORM_GBCA_API int storm_gbca_read_audio(StormGbcaContext* ctx, int16_t* dst_interleaved, int max_frames);
STORM_GBCA_API void storm_gbca_set_audio_buffer_level(StormGbcaContext* ctx, float fill_ratio);

/* Savestates & SRAM */
STORM_GBCA_API int storm_gbca_save_state_file(StormGbcaContext* ctx, const char* path);
STORM_GBCA_API int storm_gbca_load_state_file(StormGbcaContext* ctx, const char* path);
STORM_GBCA_API int storm_gbca_save_state_buffer(StormGbcaContext* ctx, void* dst, size_t capacity, size_t* out_size);
STORM_GBCA_API int storm_gbca_load_state_buffer(StormGbcaContext* ctx, const void* src, size_t size);
STORM_GBCA_API int storm_gbca_save_sram_file(StormGbcaContext* ctx, const char* path);
STORM_GBCA_API int storm_gbca_load_sram_file(StormGbcaContext* ctx, const char* path);
STORM_GBCA_API int storm_gbca_is_sram_dirty(const StormGbcaContext* ctx);

/* Rewind */
STORM_GBCA_API int storm_gbca_rewind_step(StormGbcaContext* ctx);
STORM_GBCA_API int storm_gbca_rewind_available_frames(const StormGbcaContext* ctx);

/* Sensors & Peripherals */
STORM_GBCA_API void storm_gbca_set_solar_lux(StormGbcaContext* ctx, int lux_0_to_10);
STORM_GBCA_API void storm_gbca_set_tilt(StormGbcaContext* ctx, float x, float y);
STORM_GBCA_API void storm_gbca_set_gyro(StormGbcaContext* ctx, float z);
STORM_GBCA_API float storm_gbca_get_rumble(const StormGbcaContext* ctx);
STORM_GBCA_API void storm_gbca_set_rtc(StormGbcaContext* ctx, int use_host, int64_t epoch);

/* Cheats */
STORM_GBCA_API void storm_gbca_cheats_clear(StormGbcaContext* ctx);
STORM_GBCA_API int storm_gbca_cheat_add(StormGbcaContext* ctx, const char* name, const char* code, int enabled);

/* Resolution upscale algorithms (1x..4x internal upscaling) */
enum {
    STORM_UPSCALE_NONE      = 0,
    STORM_UPSCALE_XBRZ      = 1,
    STORM_UPSCALE_SCALE_EPX = 2,
    STORM_UPSCALE_MMPX      = 3,
    STORM_UPSCALE_LCD_HD    = 4,
    STORM_UPSCALE_CRT_HD    = 5,
};

/* RAM Cheat Finder comparison operators */
enum {
    STORM_SEARCH_EQ        = 0,
    STORM_SEARCH_NEQ       = 1,
    STORM_SEARCH_GT        = 2,
    STORM_SEARCH_LT        = 3,
    STORM_SEARCH_CHANGED   = 4,
    STORM_SEARCH_UNCHANGED = 5,
    STORM_SEARCH_INCREASED = 6,
    STORM_SEARCH_DECREASED = 7,
};

/* Memory & RetroAchievements */
STORM_GBCA_API size_t storm_gbca_read_memory(const StormGbcaContext* ctx, int region, uint32_t addr, uint8_t* dst, size_t len);
STORM_GBCA_API int storm_gbca_write_memory(StormGbcaContext* ctx, int region, uint32_t addr, const uint8_t* src, size_t len);
STORM_GBCA_API int storm_gbca_compute_ra_hash(const char* rom_path, int platform, char* out_hash_33);
STORM_GBCA_API int storm_gbca_compute_ra_hash_buffer(const void* data, size_t size, int platform, char* out_hash_33);

/* Resolution Upscaling & Performance (v1.1.0) */
STORM_GBCA_API void storm_gbca_set_upscale(StormGbcaContext* ctx, int scale_1_to_4, int upscale_mode);
STORM_GBCA_API int storm_gbca_get_upscaled_size(const StormGbcaContext* ctx, int* out_w, int* out_h);
STORM_GBCA_API int storm_gbca_copy_upscaled_frame(StormGbcaContext* ctx, uint32_t* dst, int dst_stride_px, int pixel_format, int* out_w, int* out_h);
STORM_GBCA_API void storm_gbca_set_performance(StormGbcaContext* ctx, int cpu_overclock_pct, int frame_skip);

/* RAM Cheat Finder (v1.1.0) */
STORM_GBCA_API int storm_gbca_mem_search_reset(StormGbcaContext* ctx, int region, int value_size_bytes);
STORM_GBCA_API int storm_gbca_mem_search_filter(StormGbcaContext* ctx, int cmp_op, uint32_t target_value);
STORM_GBCA_API int storm_gbca_mem_search_get_results(const StormGbcaContext* ctx, uint32_t* out_addrs, uint32_t* out_vals, int max_count);

/* Palette Info */
STORM_GBCA_API int storm_gbca_get_dmg_palette_count(void);
STORM_GBCA_API const char* storm_gbca_get_dmg_palette_name(int index);

#ifdef __cplusplus
}
#endif

#endif /* STORM_GBCA_CORE_H */
