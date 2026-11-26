#include "random.h"
#include <time.h>

static uint64_t seed;
static bool enabled = true;

uint8_t GB_random(void)
{
    if (!enabled) return 0;
    
    seed *= 0x27BB2EE687B0B0FDL;
    seed += 0xB504F32D;
    return seed >> 56;
}

uint32_t GB_random32(void)
{
    GB_random();
    return seed >> 32;
}

void GB_random_seed(uint64_t new_seed)
{
    seed = new_seed;
}

void GB_random_set_enabled(bool enable)
{
    enabled = enable;
}

#ifdef _MSC_VER
static void init_seed(void);
#pragma section(".CRT$XCU", read)
__declspec(allocate(".CRT$XCU")) void (*storm_sb_random_init_ptr)(void) = init_seed;
static void init_seed(void)
#else
static void __attribute__((constructor)) init_seed(void)
#endif
{
    seed = time(NULL);
    for (unsigned i = 64; i--;) {
        GB_random();
    }
}
