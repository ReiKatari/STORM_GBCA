#ifndef STORM_GB_FASTBOOT_H
#define STORM_GB_FASTBOOT_H

#include <stdint.h>
#include <string.h>

/*
 * Minimal SM83 fast-boot ROM stubs for SameBoy when external BootROM files
 * are not supplied by the user. Initializes hardware registers to post-boot
 * state, sets DMG/CGB compatibility mode on CGB, unmaps BootROM via LDH (0x50), A
 * at 0x00FE (rolling PC cleanly into 0x0100).
 */

static inline void storm_build_dmg_fastboot(uint8_t out[0x100], uint8_t post_boot_a) {
    memset(out, 0x00, 0x100);
    /*
     * 0x0000:
     *   31 FE FF    LD SP, $FFFE
     *   3E 91       LD A, $91
     *   E0 40       LDH ($40), A   ; LCDC = $91
     *   3E FC       LD A, $FC
     *   E0 47       LDH ($47), A   ; BGP = $FC
     *   3E FF       LD A, $FF
     *   E0 48       LDH ($48), A   ; OBP0 = $FF
     *   E0 49       LDH ($49), A   ; OBP1 = $FF
     *   3E 80       LD A, $80
     *   E0 26       LDH ($26), A   ; NR52 = $80
     *   3E F3       LD A, $F3
     *   E0 25       LDH ($25), A   ; NR51 = $F3
     *   3E 77       LD A, $77
     *   E0 24       LDH ($24), A   ; NR50 = $77
     *   01 13 00    LD BC, $0013
     *   11 D8 00    LD DE, $00D8
     *   21 4D 01    LD HL, $014D
     *   3E xx       LD A, post_boot_a
     *   C3 FE 00    JP $00FE
     * 0x00FE:
     *   E0 50       LDH ($50), A   ; Unmap boot ROM, PC becomes $0100
     */
    const uint8_t code[] = {
        0x31, 0xFE, 0xFF,
        0x3E, 0x91, 0xE0, 0x40,
        0x3E, 0xFC, 0xE0, 0x47,
        0x3E, 0xFF, 0xE0, 0x48, 0xE0, 0x49,
        0x3E, 0x80, 0xE0, 0x26,
        0x3E, 0xF3, 0xE0, 0x25,
        0x3E, 0x77, 0xE0, 0x24,
        0x01, 0x13, 0x00,
        0x11, 0xD8, 0x00,
        0x21, 0x4D, 0x01,
        0x3E, post_boot_a,
        0xC3, 0xFE, 0x00
    };
    memcpy(out, code, sizeof(code));
    out[0x00FE] = 0xE0;
    out[0x00FF] = 0x50;
}

static inline void storm_build_cgb_fastboot(uint8_t out[0x900], uint8_t post_boot_b) {
    memset(out, 0x00, 0x900);
    /*
     * 0x0000:
     *   C3 00 02    JP $0200
     * 0x00FE:
     *   E0 50       LDH ($50), A
     */
    out[0x0000] = 0xC3;
    out[0x0001] = 0x00;
    out[0x0002] = 0x02;
    out[0x00FE] = 0xE0;
    out[0x00FF] = 0x50;

    /*
     * 0x0200:
     *   31 FE FF       LD SP, $FFFE
     *   3E 91          LD A, $91
     *   E0 40          LDH ($40), A   ; LCDC
     *   3E FC          LD A, $FC
     *   E0 47          LDH ($47), A   ; BGP
     *   3E FF          LD A, $FF
     *   E0 48          LDH ($48), A   ; OBP0
     *   E0 49          LDH ($49), A   ; OBP1
     *   FA 43 01       LD A, ($0143)  ; Check CGB flag
     *   CB 7F          BIT 7, A
     *   20 1B          JR NZ, +27     ; Skip DMG compat if CGB game
     *   3E 04          LD A, $04      ; KEY0 = DMG mode
     *   E0 4C          LDH ($4C), A
     *   3E 80          LD A, $80      ; BGPI auto-inc
     *   E0 68          LDH ($68), A
     *   E0 6A          LDH ($6A), A   ; OBPI auto-inc
     *   21 40 02       LD HL, $0240   ; Default 8-byte palette table
     *   0E 08          LD C, 8
     * loop:
     *   2A             LDI A, (HL)
     *   E0 69          LDH ($69), A
     *   E0 6B          LDH ($6B), A
     *   0D             DEC C
     *   20 FA          JR NZ, -6
     * cgb_mode:
     *   06 post_boot_b LD B, post_boot_b
     *   0E 00          LD C, $00
     *   11 56 FF       LD DE, $FF56
     *   21 0D 00       LD HL, $000D
     *   3E 11          LD A, $11      ; CGB A=$11 (also sets bit 0 for FF50!)
     *   C3 FE 00       JP $00FE
     */
    const uint8_t code200[] = {
        0x31, 0xFE, 0xFF,
        0x3E, 0x91, 0xE0, 0x40,
        0x3E, 0xFC, 0xE0, 0x47,
        0x3E, 0xFF, 0xE0, 0x48, 0xE0, 0x49,
        0xFA, 0x43, 0x01,
        0xCB, 0x7F,
        0x20, 0x16,
        0x3E, 0x04, 0xE0, 0x4C,
        0x3E, 0x80, 0xE0, 0x68, 0xE0, 0x6A,
        0x21, 0x50, 0x02,
        0x0E, 0x08,
        0x2A, 0xE0, 0x69, 0xE0, 0x6B, 0x0D, 0x20, 0xF9,
        /* cgb_mode at 0x022F: */
        0x06, post_boot_b, 0x0E, 0x00,
        0x11, 0x56, 0xFF,
        0x21, 0x0D, 0x00,
        0x3E, 0x11,
        0xC3, 0xFE, 0x00
    };
    memcpy(out + 0x200, code200, sizeof(code200));

    /* Default DMG-on-CGB grayscale palette at 0x0250 (RGB555 LE: white, light gray, dark gray, black) */
    const uint8_t pal[8] = {
        0xFF, 0x7F,
        0xB5, 0x56,
        0x4A, 0x29,
        0x00, 0x00,
    };
    memcpy(out + 0x250, pal, sizeof(pal));
}

#endif /* STORM_GB_FASTBOOT_H */
