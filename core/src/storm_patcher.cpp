#include "storm_internal.hpp"
#include <algorithm>
#include <cstring>

namespace storm {

uint32_t calc_crc32(const uint8_t* data, size_t size) {
    uint32_t crc = 0xFFFFFFFFu;
    for (size_t i = 0; i < size; ++i) {
        crc ^= data[i];
        for (int b = 0; b < 8; ++b) {
            uint32_t mask = static_cast<uint32_t>(-static_cast<int32_t>(crc & 1u));
            crc = (crc >> 1) ^ (0xEDB88320u & mask);
        }
    }
    return ~crc;
}

namespace {

constexpr size_t kMaxPatchedRomSize = 64ULL * 1024ULL * 1024ULL;

bool read_vli(const uint8_t* p, size_t len, size_t& pos, uint64_t& out) {
    uint64_t result = 0;
    uint64_t shift = 1;
    for (int iter = 0; iter < 9 && pos < len; ++iter) {
        uint8_t x = p[pos++];
        result += static_cast<uint64_t>(x & 0x7Fu) * shift;
        if (x & 0x80u) {
            out = result;
            return true;
        }
        if (shift > (1ULL << 56)) return false;
        shift <<= 7;
        result += shift;
    }
    return false;
}

inline uint32_t read_le32(const uint8_t* p) {
    return static_cast<uint32_t>(p[0]) |
           (static_cast<uint32_t>(p[1]) << 8) |
           (static_cast<uint32_t>(p[2]) << 16) |
           (static_cast<uint32_t>(p[3]) << 24);
}

bool apply_ips(const uint8_t* rom, size_t rom_size,
               const uint8_t* patch, size_t patch_size,
               std::vector<uint8_t>& out) {
    if (patch_size < 8) return false;
    bool is_ips32 = (std::memcmp(patch, "IPS32", 5) == 0);
    if (!is_ips32 && std::memcmp(patch, "PATCH", 5) != 0) return false;

    out.assign(rom, rom + rom_size);
    size_t pos = 5;
    while (pos < patch_size) {
        if (!is_ips32 && pos + 3 <= patch_size && std::memcmp(patch + pos, "EOF", 3) == 0) {
            pos += 3;
            if (pos + 3 <= patch_size) {
                uint32_t trunc_sz = (static_cast<uint32_t>(patch[pos]) << 16) |
                                    (static_cast<uint32_t>(patch[pos + 1]) << 8) |
                                    static_cast<uint32_t>(patch[pos + 2]);
                if (trunc_sz > kMaxPatchedRomSize) return false;
                out.resize(trunc_sz);
            }
            return true;
        }
        if (is_ips32 && pos + 4 <= patch_size && std::memcmp(patch + pos, "EEOF", 4) == 0) {
            return true;
        }

        size_t off = 0;
        if (is_ips32) {
            if (pos + 4 > patch_size) return false;
            off = (static_cast<size_t>(patch[pos]) << 24) |
                  (static_cast<size_t>(patch[pos + 1]) << 16) |
                  (static_cast<size_t>(patch[pos + 2]) << 8) |
                  static_cast<size_t>(patch[pos + 3]);
            pos += 4;
        } else {
            if (pos + 3 > patch_size) return false;
            off = (static_cast<size_t>(patch[pos]) << 16) |
                  (static_cast<size_t>(patch[pos + 1]) << 8) |
                  static_cast<size_t>(patch[pos + 2]);
            pos += 3;
        }

        if (pos + 2 > patch_size) return false;
        uint16_t sz = static_cast<uint16_t>((patch[pos] << 8) | patch[pos + 1]);
        pos += 2;

        if (sz == 0) {
            if (pos + 3 > patch_size) return false;
            uint16_t rle_len = static_cast<uint16_t>((patch[pos] << 8) | patch[pos + 1]);
            uint8_t val = patch[pos + 2];
            pos += 3;
            if (off > kMaxPatchedRomSize || rle_len > kMaxPatchedRomSize - off) return false;
            if (off + rle_len > out.size()) out.resize(off + rle_len, 0);
            std::memset(out.data() + off, val, rle_len);
        } else {
            if (pos + sz > patch_size) return false;
            if (off > kMaxPatchedRomSize || sz > kMaxPatchedRomSize - off) return false;
            if (off + sz > out.size()) out.resize(off + sz, 0);
            std::memcpy(out.data() + off, patch + pos, sz);
            pos += sz;
        }
    }
    return true;
}

bool apply_ups(const uint8_t* rom, size_t rom_size,
               const uint8_t* patch, size_t patch_size,
               std::vector<uint8_t>& out) {
    if (patch_size < 18 || std::memcmp(patch, "UPS1", 4) != 0) return false;

    uint32_t expected_in_crc = read_le32(patch + patch_size - 12);
    uint32_t expected_out_crc = read_le32(patch + patch_size - 8);
    uint32_t expected_patch_crc = read_le32(patch + patch_size - 4);
    if (calc_crc32(patch, patch_size - 4) != expected_patch_crc) return false;
    if (calc_crc32(rom, rom_size) != expected_in_crc) return false;

    size_t pos = 4;
    uint64_t in_len = 0, out_len = 0;
    if (!read_vli(patch, patch_size - 12, pos, in_len)) return false;
    if (!read_vli(patch, patch_size - 12, pos, out_len)) return false;
    if (out_len == 0 || out_len > kMaxPatchedRomSize) return false;

    out.assign(static_cast<size_t>(out_len), 0);
    std::memcpy(out.data(), rom, std::min(rom_size, static_cast<size_t>(out_len)));

    size_t out_off = 0;
    while (pos < patch_size - 12) {
        uint64_t skip = 0;
        if (!read_vli(patch, patch_size - 12, pos, skip)) return false;
        out_off += static_cast<size_t>(skip);
        while (pos < patch_size - 12) {
            uint8_t x = patch[pos++];
            if (x == 0) {
                ++out_off;
                break;
            }
            if (out_off < out.size()) {
                out[out_off] ^= x;
            }
            ++out_off;
        }
    }

    return calc_crc32(out.data(), out.size()) == expected_out_crc;
}

bool apply_bps(const uint8_t* rom, size_t rom_size,
               const uint8_t* patch, size_t patch_size,
               std::vector<uint8_t>& out) {
    if (patch_size < 16 || std::memcmp(patch, "BPS1", 4) != 0) return false;

    uint32_t expected_in_crc = read_le32(patch + patch_size - 12);
    uint32_t expected_out_crc = read_le32(patch + patch_size - 8);
    uint32_t expected_patch_crc = read_le32(patch + patch_size - 4);
    if (calc_crc32(patch, patch_size - 4) != expected_patch_crc) return false;
    if (calc_crc32(rom, rom_size) != expected_in_crc) return false;

    size_t pos = 4;
    const size_t body_end = patch_size - 12;
    uint64_t src_len = 0, dst_len = 0, meta_len = 0;
    if (!read_vli(patch, body_end, pos, src_len)) return false;
    if (!read_vli(patch, body_end, pos, dst_len)) return false;
    if (!read_vli(patch, body_end, pos, meta_len)) return false;
    if (dst_len == 0 || dst_len > kMaxPatchedRomSize) return false;
    if (meta_len > body_end - pos) return false;
    pos += static_cast<size_t>(meta_len);

    out.assign(static_cast<size_t>(dst_len), 0);
    size_t out_off = 0;
    int64_t src_rel = 0;
    int64_t dst_rel = 0;

    while (pos < body_end) {
        uint64_t data = 0;
        if (!read_vli(patch, body_end, pos, data)) return false;
        uint8_t mode = static_cast<uint8_t>(data & 3u);
        uint64_t length = (data >> 2) + 1;

        if (mode == 0) { // SourceRead
            while (length--) {
                if (out_off >= out.size()) return false;
                out[out_off] = (out_off < rom_size) ? rom[out_off] : 0;
                ++out_off;
            }
        } else if (mode == 1) { // TargetRead
            while (length--) {
                if (out_off >= out.size() || pos >= body_end) return false;
                out[out_off++] = patch[pos++];
            }
        } else if (mode == 2) { // SourceCopy
            uint64_t d = 0;
            if (!read_vli(patch, body_end, pos, d)) return false;
            src_rel += (d & 1u) ? -static_cast<int64_t>(d >> 1) : static_cast<int64_t>(d >> 1);
            while (length--) {
                if (out_off >= out.size() || src_rel < 0 || static_cast<size_t>(src_rel) >= rom_size) return false;
                out[out_off++] = rom[static_cast<size_t>(src_rel++)];
            }
        } else { // TargetCopy
            uint64_t d = 0;
            if (!read_vli(patch, body_end, pos, d)) return false;
            dst_rel += (d & 1u) ? -static_cast<int64_t>(d >> 1) : static_cast<int64_t>(d >> 1);
            while (length--) {
                if (out_off >= out.size() || dst_rel < 0 || static_cast<size_t>(dst_rel) >= out.size()) return false;
                out[out_off++] = out[static_cast<size_t>(dst_rel++)];
            }
        }
    }

    return calc_crc32(out.data(), out.size()) == expected_out_crc;
}

} // namespace

bool apply_soft_patch(const uint8_t* rom, size_t rom_size,
                      const uint8_t* patch, size_t patch_size,
                      std::vector<uint8_t>& out_rom) {
    if (!rom || !patch || patch_size < 4) return false;
    if (patch_size >= 5 && (std::memcmp(patch, "PATCH", 5) == 0 || std::memcmp(patch, "IPS32", 5) == 0)) {
        return apply_ips(rom, rom_size, patch, patch_size, out_rom);
    }
    if (std::memcmp(patch, "UPS1", 4) == 0) {
        return apply_ups(rom, rom_size, patch, patch_size, out_rom);
    }
    if (std::memcmp(patch, "BPS1", 4) == 0) {
        return apply_bps(rom, rom_size, patch, patch_size, out_rom);
    }
    return false;
}

} // namespace storm
