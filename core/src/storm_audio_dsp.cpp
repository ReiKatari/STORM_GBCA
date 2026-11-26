#include "storm_internal.hpp"
#include <algorithm>
#include <cmath>
#include <cstring>

namespace storm {

namespace {

inline double hermite4(double y0, double y1, double y2, double y3, double mu) {
    // 4-point Catmull-Rom / cubic Hermite spline (inspired by NBA MusicPlayer2K HQ interpolation)
    const double mu2 = mu * mu;
    const double a0 = -0.5 * y0 + 1.5 * y1 - 1.5 * y2 + 0.5 * y3;
    const double a1 = y0 - 2.5 * y1 + 2.0 * y2 - 0.5 * y3;
    const double a2 = -0.5 * y0 + 0.5 * y2;
    const double a3 = y1;
    return ((a0 * mu + a1) * mu2 + a2 * mu + a3);
}

inline int16_t clamp_s16(double v) {
    if (!std::isfinite(v)) return 0;
    if (v > 32767.0) return 32767;
    if (v < -32768.0) return -32768;
    return static_cast<int16_t>(std::lrint(v));
}

} // namespace

void AudioDsp::reset() {
    hp_prev_in_l = hp_prev_in_r = 0.0;
    hp_prev_out_l = hp_prev_out_r = 0.0;
    dc_acc_l = dc_acc_r = 0.0;
    for (int i = 0; i < 4; ++i) {
        hist_l[i] = 0.0;
        hist_r[i] = 0.0;
    }
    frac_pos = 0.0;
    buffer_fill_ratio = 0.5f;
    ring_read = 0;
    ring_write = 0;
    ring_count = 0;
}

void AudioDsp::ensure_capacity(size_t max_frames) {
    size_t needed = max_frames * 2;
    if (out_ring.size() < needed) {
        out_ring.assign(needed, 0);
        ring_read = 0;
        ring_write = 0;
        ring_count = 0;
    }
}

void AudioDsp::push_source_frames(const int16_t* src_interleaved, int src_frames,
                                  double src_rate, const StormGbcaAudioConfig& cfg) {
    if (!src_interleaved || src_frames <= 0) return;

    const double dst_rate = (cfg.target_sample_rate > 0) ? static_cast<double>(cfg.target_sample_rate) : 48000.0;
    double drc_adj = 1.0;
    if (cfg.dynamic_rate_control) {
        // Ares-style proportional DRC around 0.5 target fill level
        const double max_skew = std::clamp(static_cast<double>(cfg.drc_max_skew), 0.0, 0.05);
        const double err = static_cast<double>(buffer_fill_ratio) - 0.5; // [-0.5 .. +0.5]
        drc_adj = 1.0 + err * 2.0 * max_skew;
    }
    const double step = (src_rate * drc_adj) / dst_rate;
    const double master = std::clamp(static_cast<double>(cfg.master_volume), 0.0, 2.0);
    const double hp_alpha = (cfg.highpass_mode == STORM_AUDIO_HIGHPASS_ACCURATE) ? 0.9958 : 0.9992;

    ensure_capacity(48000); // 1 second ring buffer
    const size_t ring_cap_frames = out_ring.size() / 2;

    for (int i = 0; i < src_frames; ++i) {
        double sl = static_cast<double>(src_interleaved[i * 2 + 0]) * master;
        double sr = static_cast<double>(src_interleaved[i * 2 + 1]) * master;

        // SameBoy 3-mode high-pass / DC removal
        if (cfg.highpass_mode == STORM_AUDIO_HIGHPASS_ACCURATE) {
            double out_l = sl - hp_prev_in_l + hp_alpha * hp_prev_out_l;
            double out_r = sr - hp_prev_in_r + hp_alpha * hp_prev_out_r;
            hp_prev_in_l = sl;
            hp_prev_in_r = sr;
            hp_prev_out_l = out_l;
            hp_prev_out_r = out_r;
            sl = out_l;
            sr = out_r;
        } else if (cfg.highpass_mode == STORM_AUDIO_HIGHPASS_REMOVE_DC) {
            dc_acc_l = dc_acc_l * hp_alpha + sl * (1.0 - hp_alpha);
            dc_acc_r = dc_acc_r * hp_alpha + sr * (1.0 - hp_alpha);
            sl -= dc_acc_l;
            sr -= dc_acc_r;
        }

        // Shift 4-tap history
        hist_l[0] = hist_l[1]; hist_l[1] = hist_l[2]; hist_l[2] = hist_l[3]; hist_l[3] = sl;
        hist_r[0] = hist_r[1]; hist_r[1] = hist_r[2]; hist_r[2] = hist_r[3]; hist_r[3] = sr;

        while (frac_pos < 1.0) {
            double out_l = hist_l[2];
            double out_r = hist_r[2];
            if (cfg.interp_mode == STORM_AUDIO_INTERP_LINEAR) {
                out_l = hist_l[1] + (hist_l[2] - hist_l[1]) * frac_pos;
                out_r = hist_r[1] + (hist_r[2] - hist_r[1]) * frac_pos;
            } else if (cfg.interp_mode == STORM_AUDIO_INTERP_HERMITE) {
                out_l = hermite4(hist_l[0], hist_l[1], hist_l[2], hist_l[3], frac_pos);
                out_r = hermite4(hist_r[0], hist_r[1], hist_r[2], hist_r[3], frac_pos);
            }

            if (ring_count < ring_cap_frames) {
                out_ring[ring_write * 2 + 0] = clamp_s16(out_l);
                out_ring[ring_write * 2 + 1] = clamp_s16(out_r);
                ring_write = (ring_write + 1) % ring_cap_frames;
                ++ring_count;
            } else {
                // Overwrite oldest on overflow
                out_ring[ring_write * 2 + 0] = clamp_s16(out_l);
                out_ring[ring_write * 2 + 1] = clamp_s16(out_r);
                ring_write = (ring_write + 1) % ring_cap_frames;
                ring_read = (ring_read + 1) % ring_cap_frames;
            }
            frac_pos += step;
        }
        frac_pos -= 1.0;
    }
}

int AudioDsp::read_frames(int16_t* dst_interleaved, int max_frames) {
    if (!dst_interleaved || max_frames <= 0 || out_ring.empty()) return 0;
    const size_t ring_cap_frames = out_ring.size() / 2;
    const size_t to_read = std::min(static_cast<size_t>(max_frames), ring_count);
    for (size_t i = 0; i < to_read; ++i) {
        dst_interleaved[i * 2 + 0] = out_ring[ring_read * 2 + 0];
        dst_interleaved[i * 2 + 1] = out_ring[ring_read * 2 + 1];
        ring_read = (ring_read + 1) % ring_cap_frames;
    }
    ring_count -= to_read;
    return static_cast<int>(to_read);
}

} // namespace storm
