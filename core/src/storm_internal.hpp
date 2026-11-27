#ifndef STORM_INTERNAL_HPP
#define STORM_INTERNAL_HPP

#include "storm_gbca_core.h"
#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace storm {

struct Rgb24 {
    uint8_t r;
    uint8_t g;
    uint8_t b;
};

struct DmgPaletteEntry {
    const char* name;
    uint32_t bg[4];
    uint32_t obj0[4];
    uint32_t obj1[4];
};

// Palettes (ported from Gambatte gbcpalettes.h + SameBoy)
int get_dmg_palette_count();
const char* get_dmg_palette_name(int index);
const DmgPaletteEntry* get_dmg_palette(int index);
void get_effective_dmg_palette(int index, const uint32_t custom[4], uint32_t out_bg[4], uint32_t out_obj0[4], uint32_t out_obj1[4]);

// Color LUT + software video post-processing (SameBoy 7 modes + NBA AGB/AGS + Ghosting + Filters)
struct VideoPostProcessor {
    std::vector<uint32_t> prev_frame;
    std::vector<uint32_t> work_frame;
    int prev_w = 0;
    int prev_h = 0;

    void reset();
    void process_frame(const uint32_t* src_rgba, int w, int h,
                       int color_correction_mode,
                       int ghosting_pct,
                       int video_filter,
                       bool is_gba,
                       uint32_t* dst, int dst_stride_px, int pixel_format);
};

// Audio DSP (SameBoy 3-mode high-pass + per-channel mixer + NBA Hermite interp + Ares DRC resampler)
struct AudioDsp {
    float hp_prev_in_l = 0.0f;
    float hp_prev_in_r = 0.0f;
    float hp_prev_out_l = 0.0f;
    float hp_prev_out_r = 0.0f;
    float dc_acc_l = 0.0f;
    float dc_acc_r = 0.0f;

    // 4-sample history for cubic/Hermite interpolation
    float hist_l[4] = {0.0f, 0.0f, 0.0f, 0.0f};
    float hist_r[4] = {0.0f, 0.0f, 0.0f, 0.0f};
    float frac_pos = 0.0f;
    float buffer_fill_ratio = 0.5f;

    std::vector<int16_t> out_ring;
    size_t ring_read = 0;
    size_t ring_write = 0;
    size_t ring_count = 0;

    void reset();
    void ensure_capacity(size_t max_frames);
    void push_source_frames(const int16_t* src_interleaved, int src_frames,
                            double src_rate, const StormGbcaAudioConfig& cfg);
    int read_frames(int16_t* dst_interleaved, int max_frames);
};

// Rewind ring buffer (SkyEmu 64-byte segment delta compression)
class RewindRingBuffer {
public:
    static constexpr size_t kSegmentSize = 64;
    static constexpr uint32_t kLastDeltaFlag = 1u << 31;

    struct DeltaSegment {
        uint32_t offset_and_flag = 0;
        uint64_t data[kSegmentSize / 8] = {};
    };

    void configure(bool enabled, int buffer_mb, int interval);
    void reset();
    bool should_capture_frame();
    bool will_capture_next() const { return enabled_ && (frame_divider_ + 1 >= interval_); }
    void push_state(const uint8_t* state, size_t size);
    bool pop_state(std::vector<uint8_t>& out_state);
    int available_frames() const { return frame_count_; }
    bool enabled() const { return enabled_; }
    int interval() const { return interval_; }

private:
    bool more_deltas(uint32_t idx) const;

    bool enabled_ = false;
    int interval_ = 1;
    int frame_divider_ = 0;
    size_t capacity_deltas_ = 0;
    std::vector<DeltaSegment> deltas_;
    std::vector<uint8_t> last_state_;
    std::vector<uint8_t> scratch_aligned_;
    bool has_first_ = false;
    size_t write_idx_ = 0;
    size_t delta_count_ = 0;
    int frame_count_ = 0;
};

// IPS / UPS / BPS soft-patcher + CRC32
uint32_t calc_crc32(const uint8_t* data, size_t size);
bool apply_soft_patch(const uint8_t* rom, size_t rom_size,
                      const uint8_t* patch, size_t patch_size,
                      std::vector<uint8_t>& out_rom);

// High-resolution pixel upscaler (1x..4x: xBRZ, EPX/Scale2x, MMPX, LCD HD, CRT HD)
void upscale_rgba_frame(const uint32_t* src, int sw, int sh,
                        int scale, int mode,
                        std::vector<uint32_t>& out, int& out_w, int& out_h);

// Live RAM Cheat Finder session
class MemSearchSession {
public:
    struct Candidate {
        uint32_t offset = 0;
        uint32_t last_val = 0;
    };

    void reset(int region, int value_size, const uint8_t* mem, size_t mem_len);
    int filter(int op, uint32_t target, const uint8_t* mem, size_t mem_len);
    int get_results(uint32_t* out_addrs, uint32_t* out_vals, int max_count,
                    const uint8_t* mem, size_t mem_len) const;
    int region() const { return region_; }
    int value_size() const { return value_size_; }
    int count() const { return static_cast<int>(candidates_.size()); }
    bool initialized() const { return initialized_; }

private:
    int region_ = STORM_MEM_WRAM;
    int value_size_ = 1;
    bool initialized_ = false;
    std::vector<uint8_t> prev_snapshot_;
    std::vector<Candidate> candidates_;
};

// Cheat normalization (GameShark / ActionReplay / CodeBreaker / GameGenie / Raw)
struct NormalizedCheatLine {
    std::string code;
    int format = 0; // 0=auto, 1=GS/AR, 2=CB, 3=GG, 4=Raw
    uint32_t addr = 0;
    uint32_t val = 0;
    uint8_t cmp = 0;
    bool has_cmp = false;
    bool is_rom_patch = false;
};

std::vector<NormalizedCheatLine> parse_and_normalize_cheat_lines(const char* raw_code, int platform);

} // namespace storm

#endif // STORM_INTERNAL_HPP
