cmake_minimum_required(VERSION 3.22)
project(storm_gbca_core LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

if(MSVC)
    add_compile_options(/MP2 /wd4244 /wd4267 /wd4146 /wd4996)
    add_compile_definitions(_CRT_SECURE_NO_WARNINGS NOMINMAX WIN32_LEAN_AND_MEAN)
endif()

# ------------------------------------------------------------------------------
# 1. mGBA static library
# ------------------------------------------------------------------------------
set(BUILD_QT OFF CACHE BOOL "" FORCE)
set(BUILD_SDL OFF CACHE BOOL "" FORCE)
set(BUILD_LIBRETRO OFF CACHE BOOL "" FORCE)
set(BUILD_PERF OFF CACHE BOOL "" FORCE)
set(BUILD_TEST OFF CACHE BOOL "" FORCE)
set(BUILD_SUITE OFF CACHE BOOL "" FORCE)
set(BUILD_CINEMA OFF CACHE BOOL "" FORCE)
set(BUILD_HEADLESS OFF CACHE BOOL "" FORCE)
set(BUILD_EXAMPLE OFF CACHE BOOL "" FORCE)
set(BUILD_PYTHON OFF CACHE BOOL "" FORCE)
set(BUILD_STATIC ON CACHE BOOL "" FORCE)
set(BUILD_SHARED OFF CACHE BOOL "" FORCE)
set(BUILD_GL OFF CACHE BOOL "" FORCE)
set(BUILD_GLES2 OFF CACHE BOOL "" FORCE)
set(BUILD_GLES3 OFF CACHE BOOL "" FORCE)
set(USE_EPOXY OFF CACHE STRING "" FORCE)
set(USE_FFMPEG OFF CACHE BOOL "" FORCE)
set(USE_SQLITE3 OFF CACHE BOOL "" FORCE)
set(USE_ELF OFF CACHE BOOL "" FORCE)
set(USE_LUA OFF CACHE BOOL "" FORCE)
set(USE_DISCORD_RPC OFF CACHE BOOL "" FORCE)
set(USE_PNG OFF CACHE BOOL "" FORCE)
set(USE_LIBZIP OFF CACHE BOOL "" FORCE)
set(USE_MINIZIP OFF CACHE BOOL "" FORCE)
set(USE_EDITLINE OFF CACHE BOOL "" FORCE)
set(ENABLE_DEBUGGERS OFF CACHE BOOL "" FORCE)
set(ENABLE_GDB_STUB OFF CACHE BOOL "" FORCE)
set(ENABLE_SCRIPTING OFF CACHE BOOL "" FORCE)
set(USE_ZLIB ON CACHE BOOL "" FORCE)
set(USE_LZMA ON CACHE BOOL "" FORCE)
set(BUILD_LTO OFF CACHE BOOL "" FORCE)
set(QUIET ON CACHE BOOL "" FORCE)

add_subdirectory(third_party/mgba EXCLUDE_FROM_ALL)

# ------------------------------------------------------------------------------
# 2. SameBoy static library (Windows MSVC + Android Clang)
# ------------------------------------------------------------------------------
set(SAMEBOY_CORE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/sameboy/Core")
set(SAMEBOY_SRC
    "${SAMEBOY_CORE_DIR}/gb.c"
    "${SAMEBOY_CORE_DIR}/sgb.c"
    "${SAMEBOY_CORE_DIR}/apu.c"
    "${SAMEBOY_CORE_DIR}/memory.c"
    "${SAMEBOY_CORE_DIR}/mbc.c"
    "${SAMEBOY_CORE_DIR}/timing.c"
    "${SAMEBOY_CORE_DIR}/display.c"
    "${SAMEBOY_CORE_DIR}/camera.c"
    "${SAMEBOY_CORE_DIR}/sm83_cpu.c"
    "${SAMEBOY_CORE_DIR}/joypad.c"
    "${SAMEBOY_CORE_DIR}/save_state.c"
    "${SAMEBOY_CORE_DIR}/random.c"
    "${SAMEBOY_CORE_DIR}/rumble.c"
    "${CMAKE_CURRENT_SOURCE_DIR}/src/storm_sameboy_bridge.c"
)

add_library(sameboy_core STATIC ${SAMEBOY_SRC})
target_include_directories(sameboy_core
    PUBLIC "${SAMEBOY_CORE_DIR}" "${CMAKE_CURRENT_SOURCE_DIR}/include" "${CMAKE_CURRENT_SOURCE_DIR}/src"
)
target_compile_definitions(sameboy_core PRIVATE
    GB_INTERNAL
    GB_DISABLE_TIMEKEEPING
    GB_DISABLE_REWIND
    GB_DISABLE_DEBUGGER
    GB_DISABLE_CHEATS
    GB_VERSION="1.0.5"
    GB_COPYRIGHT_YEAR="2025"
)
if(MSVC)
    target_compile_options(sameboy_core PRIVATE /std:clatest /wd4200 /wd4018 /wd4244 /wd4267 /wd4146)
else()
    target_compile_options(sameboy_core PRIVATE -std=gnu11 -Wno-unused-parameter -Wno-sign-compare)
endif()

# ------------------------------------------------------------------------------
# 3. rcheevos static library (RetroAchievements hashing + runtime)
# ------------------------------------------------------------------------------
file(GLOB_RECURSE RCHEEVOS_SRC
    "${CMAKE_CURRENT_SOURCE_DIR}/third_party/rcheevos/src/hash/*.c"
    "${CMAKE_CURRENT_SOURCE_DIR}/third_party/rcheevos/src/runtime/*.c"
    "${CMAKE_CURRENT_SOURCE_DIR}/third_party/rcheevos/src/util/*.c"
)
add_library(rcheevos STATIC ${RCHEEVOS_SRC})
target_include_directories(rcheevos
    PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}/third_party/rcheevos/include"
    PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/third_party/rcheevos/src"
)
target_compile_definitions(rcheevos PRIVATE RC_HASH_NO_DISC RC_HASH_NO_ENCRYPTED)
if(MSVC)
    target_compile_options(rcheevos PRIVATE /wd4244 /wd4267 /wd4996)
endif()

# ------------------------------------------------------------------------------
# 4. Oboe (Android only)
# ------------------------------------------------------------------------------
if(ANDROID)
    add_subdirectory(third_party/oboe EXCLUDE_FROM_ALL)
endif()

# ------------------------------------------------------------------------------
# 5. Unified storm_gbca_core shared library
# ------------------------------------------------------------------------------
set(STORM_CORE_SRC
    src/storm_gbca_core.cpp
    src/storm_palettes.cpp
    src/storm_color_lut.cpp
    src/storm_audio_dsp.cpp
    src/storm_rewind.cpp
    src/storm_patcher.cpp
    src/storm_cheats.cpp
    src/storm_upscaler.cpp
    src/storm_mem_search.cpp
)

if(ANDROID)
    list(APPEND STORM_CORE_SRC
        jni/storm_gbca_jni.cpp
        jni/storm_oboe_audio.cpp
        jni/storm_librashader.cpp
        jni/storm_gles3_renderer.cpp
        jni/storm_vulkan_renderer.cpp
    )
endif()

add_library(storm_gbca_core SHARED ${STORM_CORE_SRC})
get_target_property(MGBA_COMPILE_DEFS mgba COMPILE_DEFINITIONS)
target_include_directories(storm_gbca_core
    PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}/include"
    PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}/src"
        "${CMAKE_CURRENT_SOURCE_DIR}/third_party/mgba/include"
        "${CMAKE_CURRENT_SOURCE_DIR}/third_party/mgba/src"
        "${CMAKE_CURRENT_BINARY_DIR}/third_party/mgba/include"
        "${CMAKE_CURRENT_SOURCE_DIR}/third_party/rcheevos/include"
        "${CMAKE_CURRENT_SOURCE_DIR}/third_party/librashader"
)
target_compile_definitions(storm_gbca_core PRIVATE STORM_GBCA_EXPORTS M_CORE_GB M_CORE_GBA ${MGBA_COMPILE_DEFS})
target_link_libraries(storm_gbca_core PRIVATE mgba sameboy_core rcheevos)

if(ANDROID)
    target_link_libraries(storm_gbca_core PRIVATE oboe GLESv3 EGL android log z dl)
    target_link_options(storm_gbca_core PRIVATE "-Wl,-z,max-page-size=16384")
elseif(WIN32)
    target_link_libraries(storm_gbca_core PRIVATE ws2_32 shlwapi)
    set_target_properties(storm_gbca_core PROPERTIES PREFIX "" OUTPUT_NAME "storm_gbca_core")
endif()

# ------------------------------------------------------------------------------
# 6. Native smoke test executable (Desktop)
# ------------------------------------------------------------------------------
if(NOT ANDROID)
    add_executable(core_smoke_test tests/core_smoke_test.cpp)
    target_include_directories(core_smoke_test PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/include")
    target_link_libraries(core_smoke_test PRIVATE storm_gbca_core)
endif()
