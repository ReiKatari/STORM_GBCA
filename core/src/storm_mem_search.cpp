#include "storm_internal.hpp"
#include <algorithm>
#include <cstring>

namespace storm {

namespace {

inline uint32_t read_le_val(const uint8_t* p, int size_bytes) {
    if (size_bytes == 1) return p[0];
    if (size_bytes == 2) return static_cast<uint32_t>(p[0]) | (static_cast<uint32_t>(p[1]) << 8);
    return static_cast<uint32_t>(p[0]) |
           (static_cast<uint32_t>(p[1]) << 8) |
           (static_cast<uint32_t>(p[2]) << 16) |
           (static_cast<uint32_t>(p[3]) << 24);
}

inline bool match_cmp(uint32_t cur, uint32_t prev, int op, uint32_t target) {
    switch (op) {
        case STORM_SEARCH_EQ:        return cur == target;
        case STORM_SEARCH_NEQ:       return cur != target;
        case STORM_SEARCH_GT:        return cur > target;
        case STORM_SEARCH_LT:        return cur < target;
        case STORM_SEARCH_CHANGED:   return cur != prev;
        case STORM_SEARCH_UNCHANGED: return cur == prev;
        case STORM_SEARCH_INCREASED: return cur > prev;
        case STORM_SEARCH_DECREASED: return cur < prev;
        default: return false;
    }
}

} // namespace

void MemSearchSession::reset(int region, int value_size, const uint8_t* mem, size_t mem_len) {
    region_ = region;
    value_size_ = (value_size == 2 || value_size == 4) ? value_size : 1;
    initialized_ = false;
    candidates_.clear();
    if (!mem || mem_len < static_cast<size_t>(value_size_)) {
        prev_snapshot_.clear();
        return;
    }
    prev_snapshot_.assign(mem, mem + mem_len);
    const size_t step = static_cast<size_t>(value_size_);
    candidates_.reserve(mem_len / step);
    for (size_t off = 0; off + step <= mem_len; off += step) {
        uint32_t v = read_le_val(mem + off, value_size_);
        candidates_.push_back({static_cast<uint32_t>(off), v});
    }
    initialized_ = true;
}

int MemSearchSession::filter(int op, uint32_t target, const uint8_t* mem, size_t mem_len) {
    if (!initialized_ || !mem || mem_len < static_cast<size_t>(value_size_)) {
        return 0;
    }
    std::vector<Candidate> next;
    next.reserve(candidates_.size() / 2 + 16);
    for (const auto& c : candidates_) {
        if (static_cast<size_t>(c.offset) + static_cast<size_t>(value_size_) > mem_len) continue;
        uint32_t cur = read_le_val(mem + c.offset, value_size_);
        if (match_cmp(cur, c.last_val, op, target)) {
            next.push_back({c.offset, cur});
        }
    }
    candidates_ = std::move(next);
    prev_snapshot_.assign(mem, mem + mem_len);
    return static_cast<int>(candidates_.size());
}

int MemSearchSession::get_results(uint32_t* out_addrs, uint32_t* out_vals, int max_count,
                                  const uint8_t* mem, size_t mem_len) const {
    if (!initialized_ || !out_addrs || !out_vals || max_count <= 0) return 0;
    int n = std::min(max_count, static_cast<int>(candidates_.size()));
    for (int i = 0; i < n; ++i) {
        out_addrs[i] = candidates_[i].offset;
        if (mem && static_cast<size_t>(candidates_[i].offset) + static_cast<size_t>(value_size_) <= mem_len) {
            out_vals[i] = read_le_val(mem + candidates_[i].offset, value_size_);
        } else {
            out_vals[i] = candidates_[i].last_val;
        }
    }
    return n;
}

} // namespace storm
