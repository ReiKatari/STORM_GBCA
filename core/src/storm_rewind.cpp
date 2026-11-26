#include "storm_internal.hpp"
#include <algorithm>
#include <cstring>

namespace storm {

void RewindRingBuffer::configure(bool enabled, int buffer_mb, int interval) {
    enabled_ = enabled;
    interval_ = std::max(1, interval);
    int mb = std::clamp(buffer_mb, 1, 128);
    size_t target_bytes = static_cast<size_t>(mb) * 1024u * 1024u;
    size_t new_cap = std::max<size_t>(4096, target_bytes / sizeof(DeltaSegment));
    if (new_cap != capacity_deltas_) {
        capacity_deltas_ = new_cap;
        deltas_.resize(capacity_deltas_);
        reset();
    }
}

void RewindRingBuffer::reset() {
    has_first_ = false;
    write_idx_ = 0;
    delta_count_ = 0;
    frame_count_ = 0;
    frame_divider_ = 0;
    last_state_.clear();
}

bool RewindRingBuffer::more_deltas(uint32_t idx) const {
    if (capacity_deltas_ == 0) return false;
    return (deltas_[idx % capacity_deltas_].offset_and_flag & kLastDeltaFlag) == 0;
}

void RewindRingBuffer::push_state(const uint8_t* state, size_t size) {
    if (!enabled_ || !state || size == 0) return;
    if (capacity_deltas_ == 0) configure(true, 16, interval_);

    if (++frame_divider_ < interval_) return;
    frame_divider_ = 0;

    // Pad to multiple of 64 bytes for aligned uint64_t segment comparisons (SkyEmu algorithm)
    size_t padded_size = ((size + kSegmentSize - 1) / kSegmentSize) * kSegmentSize;
    std::vector<uint8_t> aligned_state(padded_size, 0);
    std::memcpy(aligned_state.data(), state, size);

    if (!has_first_ || last_state_.size() != padded_size) {
        has_first_ = true;
        last_state_ = std::move(aligned_state);
        write_idx_ = 0;
        delta_count_ = 0;
        frame_count_ = 0;
        return;
    }

    const size_t total_segments = padded_size / kSegmentSize;
    uint64_t* old_data = reinterpret_cast<uint64_t*>(last_state_.data());
    const uint64_t* new_data = reinterpret_cast<const uint64_t*>(aligned_state.data());
    size_t total_deltas = 0;

    for (size_t s = 0; s < total_segments; ++s) {
        const size_t base_off = s * (kSegmentSize / 8);
        bool diff = false;
        for (size_t k = 0; k < kSegmentSize / 8; ++k) {
            if (new_data[base_off + k] != old_data[base_off + k]) {
                diff = true;
                break;
            }
        }
        if (diff) {
            size_t slot = write_idx_ % capacity_deltas_;
            uint32_t off = static_cast<uint32_t>(s);
            if (total_deltas == 0) off |= kLastDeltaFlag;
            for (size_t k = 0; k < kSegmentSize / 8; ++k) {
                deltas_[slot].data[k] = old_data[base_off + k] ^ new_data[base_off + k];
                old_data[base_off + k] = new_data[base_off + k];
            }
            deltas_[slot].offset_and_flag = off;
            write_idx_ = (write_idx_ + 1) % capacity_deltas_;
            if (delta_count_ < capacity_deltas_) {
                ++delta_count_;
            }
            ++total_deltas;
        }
    }

    // If no bytes changed, push one zeroed delta marker so frame_count stays accurate
    if (total_deltas == 0) {
        size_t slot = write_idx_ % capacity_deltas_;
        deltas_[slot].offset_and_flag = kLastDeltaFlag;
        std::memset(deltas_[slot].data, 0, kSegmentSize);
        write_idx_ = (write_idx_ + 1) % capacity_deltas_;
        if (delta_count_ < capacity_deltas_) {
            ++delta_count_;
        }
    }

    ++frame_count_;

    // Trim partial transaction at the tail if ring wrapped
    if (delta_count_ >= capacity_deltas_) {
        delta_count_ = capacity_deltas_ - 1;
        while (delta_count_ > 0) {
            uint32_t tail = static_cast<uint32_t>((write_idx_ + capacity_deltas_ - delta_count_) % capacity_deltas_);
            if (!more_deltas(tail)) break;
            --delta_count_;
        }
        // Recount available frames accurately after wrap trim
        int frames = 0;
        for (size_t i = 0; i < delta_count_; ++i) {
            size_t idx = (write_idx_ + capacity_deltas_ - delta_count_ + i) % capacity_deltas_;
            if (!more_deltas(static_cast<uint32_t>(idx))) ++frames;
        }
        frame_count_ = frames;
    }
}

bool RewindRingBuffer::pop_state(std::vector<uint8_t>& out_state) {
    if (!enabled_ || !has_first_ || delta_count_ == 0 || frame_count_ <= 0) return false;

    uint64_t* cur_data = reinterpret_cast<uint64_t*>(last_state_.data());
    bool more = true;
    while (delta_count_ > 0 && more) {
        --delta_count_;
        write_idx_ = (write_idx_ + capacity_deltas_ - 1) % capacity_deltas_;
        uint32_t off = deltas_[write_idx_].offset_and_flag;
        if (off & kLastDeltaFlag) {
            more = false;
            off &= ~kLastDeltaFlag;
        }
        const size_t base_off = static_cast<size_t>(off) * (kSegmentSize / 8);
        if (base_off + (kSegmentSize / 8) <= last_state_.size() / 8) {
            for (size_t k = 0; k < kSegmentSize / 8; ++k) {
                cur_data[base_off + k] ^= deltas_[write_idx_].data[k];
            }
        }
    }
    --frame_count_;
    out_state = last_state_;
    return true;
}

} // namespace storm
