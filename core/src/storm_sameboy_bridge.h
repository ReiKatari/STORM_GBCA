#ifndef STORM_SAMEBOY_BRIDGE_H
#define STORM_SAMEBOY_BRIDGE_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct StormSameboyHandle StormSameboyHandle;

StormSameboyHandle* storm_sb_create(int gb_model, int sgb_borders,
                                    const char* bios_gb, const char* bios_gbc, const char* bios_sgb,
                                    int skip_boot);
void storm_sb_destroy(StormSameboyHandle* h);
int storm_sb_load_rom_buffer(StormSameboyHandle* h, const uint8_t* data, size_t size);
void storm_sb_reset(StormSameboyHandle* h);
void storm_sb_set_keys(StormSameboyHandle* h, uint32_t storm_key_mask);
void storm_sb_run_frame(StormSameboyHandle* h, int collect_audio);

void storm_sb_get_video_size(const StormSameboyHandle* h, int* out_w, int* out_h, float* out_fps);
const uint32_t* storm_sb_get_pixels(const StormSameboyHandle* h);

int storm_sb_drain_audio(StormSameboyHandle* h, int16_t* dst, int max_frames);

void storm_sb_set_color_correction(StormSameboyHandle* h, int mode);
void storm_sb_set_highpass(StormSameboyHandle* h, int mode);
void storm_sb_set_channel_muted(StormSameboyHandle* h, int channel, int muted);
void storm_sb_set_dmg_palette(StormSameboyHandle* h, const uint32_t rgb[4]);
void storm_sb_set_border_mode(StormSameboyHandle* h, int sgb_borders);
void storm_sb_set_accelerometer(StormSameboyHandle* h, float x, float y);
float storm_sb_get_rumble(const StormSameboyHandle* h);

size_t storm_sb_state_size(StormSameboyHandle* h);
int storm_sb_save_state(StormSameboyHandle* h, uint8_t* dst, size_t size);
int storm_sb_load_state(StormSameboyHandle* h, const uint8_t* src, size_t size);

size_t storm_sb_sram_size(StormSameboyHandle* h);
int storm_sb_save_sram_buffer(StormSameboyHandle* h, uint8_t* dst, size_t size);
int storm_sb_load_sram_buffer(StormSameboyHandle* h, const uint8_t* src, size_t size);
int storm_sb_save_sram_file(StormSameboyHandle* h, const char* path);
int storm_sb_load_sram_file(StormSameboyHandle* h, const char* path);
int storm_sb_is_sram_dirty(StormSameboyHandle* h);

size_t storm_sb_read_memory(StormSameboyHandle* h, int region, uint32_t addr, uint8_t* dst, size_t len);
void storm_sb_write_memory_u8(StormSameboyHandle* h, uint16_t addr, uint8_t val);
void storm_sb_patch_rom_byte(StormSameboyHandle* h, uint32_t rom_offset, uint8_t val, int has_cmp, uint8_t cmp);

int storm_sb_is_cgb(const StormSameboyHandle* h);
int storm_sb_is_sgb(const StormSameboyHandle* h);
int storm_sb_has_accelerometer(const StormSameboyHandle* h);
int storm_sb_has_rtc(const StormSameboyHandle* h);

#ifdef __cplusplus
}
#endif

#endif /* STORM_SAMEBOY_BRIDGE_H */
