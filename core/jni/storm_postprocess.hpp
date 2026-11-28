#pragma once

#include <algorithm>
#include <atomic>
#include <cstdint>

namespace storm {

inline std::atomic<uint64_t> g_real_frame_seq{0};

inline void pp_bump_frame_seq() {
    g_real_frame_seq.fetch_add(1, std::memory_order_release);
}

inline uint64_t pp_get_frame_seq() {
    return g_real_frame_seq.load(std::memory_order_acquire);
}

struct PostProcessParams {
    int   fsr_mode = 0;           // 0=off, 1=FSR1, 2=FSR3
    float rcas_sharpness = 0.50f; // 0.0..1.0
    int   fsr_quality = 0;        // 0=Quality, 1=Balanced, 2=Performance, 3=Ultra Performance
    bool  frame_gen_enabled = false;
    float frame_gen_alpha = 0.0f; // 0.0=real frame, 0.5=midpoint interpolated frame
};

struct PostProcessState {
    std::atomic<int>   fsr_mode{0};
    std::atomic<float> rcas_sharpness{0.50f};
    std::atomic<int>   fsr_quality{0};
    std::atomic<bool>  frame_gen_enabled{false};
    std::atomic<float> frame_gen_alpha{0.0f};
};

inline PostProcessState& post_process_state() {
    static PostProcessState state;
    return state;
}

inline PostProcessParams get_post_process_params() {
    auto& s = post_process_state();
    PostProcessParams p;
    p.fsr_mode = std::clamp(s.fsr_mode.load(std::memory_order_relaxed), 0, 2);
    p.rcas_sharpness = std::clamp(s.rcas_sharpness.load(std::memory_order_relaxed), 0.0f, 1.0f);
    p.fsr_quality = std::clamp(s.fsr_quality.load(std::memory_order_relaxed), 0, 3);
    p.frame_gen_enabled = s.frame_gen_enabled.load(std::memory_order_relaxed);
    p.frame_gen_alpha = std::clamp(s.frame_gen_alpha.load(std::memory_order_relaxed), 0.0f, 1.0f);
    return p;
}

inline int pp_get_fsr_mode() {
    return std::clamp(post_process_state().fsr_mode.load(std::memory_order_relaxed), 0, 2);
}

inline bool pp_get_frame_gen() {
    return post_process_state().frame_gen_enabled.load(std::memory_order_relaxed);
}

inline float fsr_quality_scale(int quality) {
    switch (quality) {
        case 0: return 0.67f; // Quality: 67% - 1.5x
        case 1: return 0.59f; // Balanced: 59% - 1.7x
        case 2: return 0.50f; // Performance: 50% - 2.0x
        case 3: return 0.33f; // Ultra Performance: 33% - 3.0x
        default: return 0.67f;
    }
}

inline float fsr_quality_sharpness_bias(int quality) {
    switch (quality) {
        case 0: return 0.00f;
        case 1: return 0.08f;
        case 2: return 0.16f;
        case 3: return 0.24f;
        default: return 0.00f;
    }
}

} // namespace storm
