#include "storm_internal.hpp"
#include <cctype>
#include <cstdlib>
#include <sstream>

namespace storm {

namespace {

std::string trim_upper(const std::string& s) {
    size_t a = 0;
    while (a < s.size() && std::isspace(static_cast<unsigned char>(s[a]))) ++a;
    size_t b = s.size();
    while (b > a && std::isspace(static_cast<unsigned char>(s[b - 1]))) --b;
    std::string out;
    out.reserve(b - a);
    for (size_t i = a; i < b; ++i) {
        out.push_back(static_cast<char>(std::toupper(static_cast<unsigned char>(s[i]))));
    }
    return out;
}

bool is_hex_str(const std::string& s) {
    if (s.empty()) return false;
    for (char c : s) {
        if (!std::isxdigit(static_cast<unsigned char>(c))) return false;
    }
    return true;
}

} // namespace

std::vector<NormalizedCheatLine> parse_and_normalize_cheat_lines(const char* raw_code, int platform) {
    std::vector<NormalizedCheatLine> out;
    if (!raw_code) return out;

    // Split multiline / delimited codes (\n, \r, +, ;, ,)
    std::string cur;
    auto flush = [&]() {
        std::string line = trim_upper(cur);
        cur.clear();
        if (line.empty()) return;

        // Strip spaces for format detection
        std::string compact;
        compact.reserve(line.size());
        for (char c : line) {
            if (!std::isspace(static_cast<unsigned char>(c))) compact.push_back(c);
        }

        NormalizedCheatLine item;
        item.code = line;

        // Check Raw format: ADDR:VAL or ADDR=VAL
        auto colon = compact.find(':');
        if (colon == std::string::npos) colon = compact.find('=');
        if (colon != std::string::npos) {
            std::string lhs = compact.substr(0, colon);
            std::string rhs = compact.substr(colon + 1);
            if (is_hex_str(lhs) && is_hex_str(rhs)) {
                item.format = 4;
                item.addr = static_cast<uint32_t>(std::strtoul(lhs.c_str(), nullptr, 16));
                item.val = static_cast<uint32_t>(std::strtoul(rhs.c_str(), nullptr, 16));
                out.push_back(std::move(item));
                return;
            }
        }

        // GameGenie GB (XXX-XXX or XXX-XXX-XXX)
        if (platform != STORM_PLATFORM_GBA &&
            ((compact.size() == 7 && compact[3] == '-') ||
             (compact.size() == 11 && compact[3] == '-' && compact[7] == '-'))) {
            item.format = 3;
            std::string hex;
            for (char c : compact) if (c != '-') hex.push_back(c);
            if (is_hex_str(hex) && (hex.size() == 6 || hex.size() == 9)) {
                auto hval = [](char c) -> uint32_t {
                    return static_cast<uint32_t>(std::isdigit(static_cast<unsigned char>(c)) ? (c - '0') : (c - 'A' + 10));
                };
                uint32_t val = (hval(hex[0]) << 4) | hval(hex[1]);
                uint32_t addr = (hval(hex[2]) << 8) | (hval(hex[3]) << 4) | hval(hex[4]) |
                                (((~hval(hex[5])) & 0xFu) << 12);
                item.addr = addr;
                item.val = val;
                item.is_rom_patch = true;
                if (hex.size() == 9) {
                    uint8_t c1 = static_cast<uint8_t>((hval(hex[6]) << 4) | hval(hex[8]));
                    uint8_t cmp = static_cast<uint8_t>(((c1 >> 2) | (c1 << 6)) ^ 0xBAu);
                    item.cmp = cmp;
                    item.has_cmp = true;
                }
                out.push_back(std::move(item));
                return;
            }
        }

        // GameShark GB (8 hex digits: BBVVAAAA -> bank BB, val VV, addr AAAA little-endian)
        if (platform != STORM_PLATFORM_GBA && compact.size() == 8 && is_hex_str(compact)) {
            item.format = 1;
            uint32_t v = static_cast<uint32_t>(std::strtoul(compact.c_str(), nullptr, 16));
            uint8_t val = static_cast<uint8_t>((v >> 16) & 0xFFu);
            uint16_t addr = static_cast<uint16_t>(((v & 0xFFu) << 8) | ((v >> 8) & 0xFFu));
            item.addr = addr;
            item.val = val;
            item.code = compact;
            out.push_back(std::move(item));
            return;
        }

        // GBA CodeBreaker (XXXXXXXX YYYY -> 12 hex digits) or GameShark/AR v1-v3 (XXXXXXXX YYYYYYYY -> 16 hex digits)
        std::string no_dash;
        for (char c : compact) if (c != '-') no_dash.push_back(c);
        if (platform == STORM_PLATFORM_GBA && is_hex_str(no_dash)) {
            if (no_dash.size() == 12) {
                item.format = 2; // CodeBreaker
                item.code = no_dash.substr(0, 8) + " " + no_dash.substr(8, 4);
                out.push_back(std::move(item));
                return;
            }
            if (no_dash.size() == 16) {
                item.format = 1; // GameShark / ActionReplay
                item.code = no_dash.substr(0, 8) + " " + no_dash.substr(8, 8);
                out.push_back(std::move(item));
                return;
            }
        }

        out.push_back(std::move(item));
    };

    for (const char* p = raw_code; *p; ++p) {
        char c = *p;
        if (c == '\n' || c == '\r' || c == '+' || c == ';' || c == ',') {
            flush();
        } else {
            cur.push_back(c);
        }
    }
    flush();

    return out;
}

} // namespace storm
