#pragma once
/* STORM_SAMEBOY_MSVC_SHIM */
#ifdef _MSC_VER
#include <stdlib.h>
#include <malloc.h>
#include <intrin.h>
#include <stddef.h>
#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif
typedef ptrdiff_t ssize_t;
#define GB_likely(x)   (x)
#define GB_unlikely(x) (x)
#define GB_PACK_PUSH __pragma(pack(push, 1))
#define GB_PACK_POP  __pragma(pack(pop))
#define GB_ATTR_PACKED
#define GB_DEPRECATED(msg)
#define GB_FORMAT_PRINTF(a, b)
#ifdef GB_INTERNAL
#define likely(x)   (x)
#define unlikely(x) (x)
#if !defined(MIN)
#define MIN(A, B) (((A) < (B)) ? (A) : (B))
#endif
#if !defined(MAX)
#define MAX(A, B) (((A) > (B)) ? (A) : (B))
#endif
#define __builtin_bswap16(x) _byteswap_ushort(x)
#define __builtin_bswap32(x) _byteswap_ulong(x)
#define __builtin_bswap64(x) _byteswap_uint64(x)
#define internal
#define GB_NOINLINE __declspec(noinline)
#define unrolled
#define nounroll
#define unreachable() __assume(0)
#define nodefault default: unreachable()
#ifdef GB_BIG_ENDIAN
#define LE16(x) _byteswap_ushort(x)
#define LE32(x) _byteswap_ulong(x)
#define LE64(x) _byteswap_uint64(x)
#define BE16(x) (x)
#define BE32(x) (x)
#define BE64(x) (x)
#else
#define LE16(x) (x)
#define LE32(x) (x)
#define LE64(x) (x)
#define BE16(x) _byteswap_ushort(x)
#define BE32(x) _byteswap_ulong(x)
#define BE64(x) _byteswap_uint64(x)
#define GB_CONST_BE32(x) ((((uint32_t)(x) & 0xFF000000u) >> 24) | (((uint32_t)(x) & 0x00FF0000u) >> 8) | (((uint32_t)(x) & 0x0000FF00u) << 8) | (((uint32_t)(x) & 0x000000FFu) << 24))
#define GB_CONST_LE32(x) ((uint32_t)(x))
#endif
#endif
struct GB_gameboy_s;
typedef struct GB_gameboy_s GB_gameboy_t;
#else

#define GB_likely(x)   __builtin_expect((bool)(x), 1)
#define GB_unlikely(x) __builtin_expect((bool)(x), 0)
#define GB_inline_const(type, ...) (*({static const typeof(type) _= __VA_ARGS__; &_;}))

#if !defined(typeof)
#if defined(__cplusplus) || __STDC_VERSION__ < 202311
#define typeof __typeof__
#endif
#endif

#ifdef GB_INTERNAL

// "Keyword" definitions
#define likely(x)   GB_likely(x)
#define unlikely(x) GB_unlikely(x)
#define inline_const GB_inline_const

#if !defined(MIN)
#define MIN(A, B)    ({ __typeof__(A) __a = (A); __typeof__(B) __b = (B); __a < __b ? __a : __b; })
#endif

#if !defined(MAX)
#define MAX(A, B)    ({ __typeof__(A) __a = (A); __typeof__(B) __b = (B); __a < __b ? __b : __a; })
#endif

#if __GNUC__ < 4 || (__GNUC__ == 4 && __GNUC_MINOR__ < 8)
#define __builtin_bswap16(x) ({ typeof(x) _x = (x); _x >> 8 | _x << 8; })
#endif

#define internal __attribute__((visibility("hidden")))
#define GB_NOINLINE __attribute__((__noinline__))
#define noinline __attribute__((__noinline__))

#if __clang__
#define unrolled _Pragma("unroll")
#define nounroll _Pragma("clang loop unroll(disable)")
#elif __GNUC__ >= 8
#define unrolled _Pragma("GCC unroll 8")
#define nounroll _Pragma("GCC unroll 0")
#else
#define unrolled
#define nounroll
#endif

#define unreachable() __builtin_unreachable();
#define nodefault default: unreachable()

#ifdef GB_BIG_ENDIAN
#define LE16(x) __builtin_bswap16(x)
#define LE32(x) __builtin_bswap32(x)
#define LE64(x) __builtin_bswap64(x)
#define BE16(x) (x)
#define BE32(x) (x)
#define BE64(x) (x)
#else
#define LE16(x) (x)
#define LE32(x) (x)
#define LE64(x) (x)
#define BE16(x) __builtin_bswap16(x)
#define BE32(x) __builtin_bswap32(x)
#define BE64(x) __builtin_bswap64(x)
#endif
#ifdef GB_BIG_ENDIAN
#define GB_CONST_BE32(x) ((uint32_t)(x))
#define GB_CONST_LE32(x) ((((uint32_t)(x) & 0xFF000000u) >> 24) | (((uint32_t)(x) & 0x00FF0000u) >> 8) | (((uint32_t)(x) & 0x0000FF00u) << 8) | (((uint32_t)(x) & 0x000000FFu) << 24))
#else
#define GB_CONST_BE32(x) ((((uint32_t)(x) & 0xFF000000u) >> 24) | (((uint32_t)(x) & 0x00FF0000u) >> 8) | (((uint32_t)(x) & 0x0000FF00u) << 8) | (((uint32_t)(x) & 0x000000FFu) << 24))
#define GB_CONST_LE32(x) ((uint32_t)(x))
#endif
#endif

struct GB_gameboy_s;
typedef struct GB_gameboy_s GB_gameboy_t;

#define GB_PACK_PUSH
#define GB_PACK_POP
#define GB_ATTR_PACKED __attribute__((packed))
#define GB_DEPRECATED(msg) __attribute__((deprecated(msg)))
#define GB_FORMAT_PRINTF(a, b) __attribute__((__format__ (__printf__, a, b)))
#endif
