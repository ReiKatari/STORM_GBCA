#ifdef ANDROID

#include <jni.h>
#include <android/native_window_jni.h>
#include "storm_gbca_core.h"
#include <algorithm>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

namespace storm {
bool android_audio_start(StormGbcaContext* ctx);
void android_audio_stop();
void android_audio_pause(bool paused);

bool gles_init(const std::string& shader_source);
void gles_resize(int w, int h);
void gles_set_rotation(int deg);
void gles_render(const uint32_t* rgba, int w, int h, int vp_x, int vp_y, int vp_w, int vp_h,
                 int display_mode, float ghosting, float cc_strength, bool is_grayscale, bool integer_scaling);
void gles_destroy();

bool librashader_is_available();
std::string librashader_status();
void librashader_set_preset(const std::string& path);

bool vk_probe_support(std::string& device_name);
bool vk_init(ANativeWindow* window, const std::string& custom_driver_path);
void vk_resize(int w, int h);
void vk_render(const uint32_t* rgba, int w, int h, int vp_x, int vp_y, int vp_w, int vp_h, bool linear_filter, int rotation_deg);
void vk_destroy();
std::string vk_device_name();
}

static std::string sanitize_field(const char* src) {
    if (!src) return {};
    std::string out(src);
    for (char& c : out) {
        if (c == '\t' || c == '\n' || c == '\r') c = ' ';
    }
    return out;
}

static jstring format_rom_info(JNIEnv* env, const StormGbcaRomInfo& info) {
    char buf[512];
    std::string title = sanitize_field(info.title);
    std::string code = sanitize_field(info.game_code);
    std::string maker = sanitize_field(info.maker);
    std::string region = sanitize_field(info.region);
    std::string md5 = sanitize_field(info.md5);
    std::string core = sanitize_field(info.active_core);
    std::snprintf(
        buf, sizeof(buf),
        "%d\t%s\t%s\t%s\t%s\t%u\t%s\t%u\t%u\t%s",
        info.platform,
        title.c_str(),
        code.c_str(),
        maker.c_str(),
        region.c_str(),
        info.crc32,
        md5.c_str(),
        info.rom_size,
        info.hw_flags,
        core.c_str()
    );
    return env->NewStringUTF(buf);
}

extern "C" {

JNIEXPORT jstring JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeVersion(JNIEnv* env, jclass) {
    return env->NewStringUTF(storm_gbca_version());
}

JNIEXPORT jlong JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeCreate(JNIEnv*, jclass) {
    return reinterpret_cast<jlong>(storm_gbca_create());
}

JNIEXPORT void JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeDestroy(JNIEnv*, jclass, jlong handle) {
    storm::android_audio_stop();
    storm_gbca_destroy(reinterpret_cast<StormGbcaContext*>(handle));
}

JNIEXPORT void JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeReset(JNIEnv*, jclass, jlong handle) {
    storm_gbca_reset(reinterpret_cast<StormGbcaContext*>(handle));
}

JNIEXPORT jint JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeLoadRomFile(JNIEnv* env, jclass, jlong handle, jstring path) {
    auto* ctx = reinterpret_cast<StormGbcaContext*>(handle);
    if (!ctx || !path) return -1;
    const char* cpath = env->GetStringUTFChars(path, nullptr);
    int rc = storm_gbca_load_rom_file(ctx, cpath);
    env->ReleaseStringUTFChars(path, cpath);
    return rc;
}

JNIEXPORT jint JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeLoadRomBuffer(JNIEnv* env, jclass, jlong handle, jbyteArray data, jstring virtualName) {
    auto* ctx = reinterpret_cast<StormGbcaContext*>(handle);
    if (!ctx || !data) return -1;
    jsize len = env->GetArrayLength(data);
    if (len <= 0) return -1;
    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    const char* vname = virtualName ? env->GetStringUTFChars(virtualName, nullptr) : "";
    int rc = storm_gbca_load_rom_buffer(ctx, bytes, static_cast<size_t>(len), vname);
    if (virtualName && vname) env->ReleaseStringUTFChars(virtualName, vname);
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    return rc;
}

JNIEXPORT void JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeUnloadRom(JNIEnv*, jclass, jlong handle) {
    storm::android_audio_stop();
    storm_gbca_unload_rom(reinterpret_cast<StormGbcaContext*>(handle));
}

JNIEXPORT jstring JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeInspectRomFile(JNIEnv* env, jclass, jstring path) {
    if (!path) return nullptr;
    const char* cpath = env->GetStringUTFChars(path, nullptr);
    StormGbcaRomInfo info{};
    int rc = storm_gbca_inspect_rom_file(cpath, &info);
    env->ReleaseStringUTFChars(path, cpath);
    if (rc != 0) return nullptr;
    return format_rom_info(env, info);
}

JNIEXPORT jstring JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeInspectRomBuffer(JNIEnv* env, jclass, jbyteArray data, jstring virtualName) {
    if (!data) return nullptr;
    jsize len = env->GetArrayLength(data);
    if (len <= 0) return nullptr;
    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    const char* vname = virtualName ? env->GetStringUTFChars(virtualName, nullptr) : "";
    StormGbcaRomInfo info{};
    int rc = storm_gbca_inspect_rom_buffer(bytes, static_cast<size_t>(len), vname, &info);
    if (virtualName && vname) env->ReleaseStringUTFChars(virtualName, vname);
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    if (rc != 0) return nullptr;
    return format_rom_info(env, info);
}

JNIEXPORT jstring JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeGetRomInfo(JNIEnv* env, jclass, jlong handle) {
    auto* ctx = reinterpret_cast<StormGbcaContext*>(handle);
    if (!ctx) return nullptr;
    StormGbcaRomInfo info{};
    if (storm_gbca_get_rom_info(ctx, &info) != 0) return nullptr;
    return format_rom_info(env, info);
}

JNIEXPORT jint JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeApplyPatchFile(JNIEnv* env, jclass, jlong handle, jstring patchPath) {
    auto* ctx = reinterpret_cast<StormGbcaContext*>(handle);
    if (!ctx || !patchPath) return -1;
    const char* cpath = env->GetStringUTFChars(patchPath, nullptr);
    int rc = storm_gbca_apply_patch_file(ctx, cpath);
    env->ReleaseStringUTFChars(patchPath, cpath);
    return rc;
}

JNIEXPORT jint JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeSetConfig(
    JNIEnv* env, jclass, jlong handle,
    jint preferredCoreGb, jint preferredCoreGbc, jint preferredCoreGba,
    jint gbModel, jboolean sgbBorders, jint colorCorrectionMode,
    jint dmgPaletteIndex, jintArray customDmgPalette, jint lcdGhosting, jint videoFilter,
    jint audioHighpassMode, jint audioInterpMode, jfloatArray channelVolumes, jfloat masterVolume,
    jint runAheadFrames, jboolean rewindEnabled, jint rewindBufferMb, jint rewindInterval,
    jstring biosGbPath, jstring biosGbcPath, jstring biosGbaPath, jstring biosSgbPath,
    jboolean skipBiosIntro, jboolean dynamicRateControl, jfloat drcMaxSkew
) {
    auto* ctx = reinterpret_cast<StormGbcaContext*>(handle);
    if (!ctx) return -1;

    StormGbcaConfig cfg{};
    storm_gbca_get_default_config(&cfg);
    cfg.preferred_core_gb = preferredCoreGb;
    cfg.preferred_core_gbc = preferredCoreGbc;
    cfg.preferred_core_gba = preferredCoreGba;
    cfg.gb_model = gbModel;
    cfg.sgb_borders = sgbBorders ? 1 : 0;
    cfg.color_correction_mode = colorCorrectionMode;
    cfg.dmg_palette_index = dmgPaletteIndex;

    if (customDmgPalette && env->GetArrayLength(customDmgPalette) >= 4) {
        jint* pal = env->GetIntArrayElements(customDmgPalette, nullptr);
        for (int i = 0; i < 4; ++i) cfg.custom_dmg_palette[i] = static_cast<uint32_t>(pal[i]) & 0x00FFFFFFu;
        env->ReleaseIntArrayElements(customDmgPalette, pal, JNI_ABORT);
    }

    cfg.lcd_ghosting = lcdGhosting;
    cfg.video_filter = videoFilter;
    cfg.audio_highpass_mode = audioHighpassMode;
    cfg.audio_interp_mode = audioInterpMode;

    if (channelVolumes && env->GetArrayLength(channelVolumes) >= 6) {
        jfloat* vols = env->GetFloatArrayElements(channelVolumes, nullptr);
        for (int i = 0; i < 6; ++i) cfg.channel_volumes[i] = vols[i];
        env->ReleaseFloatArrayElements(channelVolumes, vols, JNI_ABORT);
    }

    cfg.master_volume = masterVolume;
    cfg.run_ahead_frames = runAheadFrames;
    cfg.rewind_enabled = rewindEnabled ? 1 : 0;
    cfg.rewind_buffer_mb = rewindBufferMb;
    cfg.rewind_interval = rewindInterval;

    auto copyPath = [&](jstring src, char* dst, size_t cap) {
        if (!src) { dst[0] = '\0'; return; }
        const char* c = env->GetStringUTFChars(src, nullptr);
        std::snprintf(dst, cap, "%s", c ? c : "");
        if (c) env->ReleaseStringUTFChars(src, c);
    };
    copyPath(biosGbPath, cfg.bios_gb_path, sizeof(cfg.bios_gb_path));
    copyPath(biosGbcPath, cfg.bios_gbc_path, sizeof(cfg.bios_gbc_path));
    copyPath(biosGbaPath, cfg.bios_gba_path, sizeof(cfg.bios_gba_path));
    copyPath(biosSgbPath, cfg.bios_sgb_path, sizeof(cfg.bios_sgb_path));

    cfg.skip_bios_intro = skipBiosIntro ? 1 : 0;
    cfg.dynamic_rate_control = dynamicRateControl ? 1 : 0;
    cfg.drc_max_skew = drcMaxSkew;

    return storm_gbca_set_config(ctx, &cfg);
}

JNIEXPORT void JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeSetKeys(JNIEnv*, jclass, jlong handle, jint mask) {
    storm_gbca_set_keys(reinterpret_cast<StormGbcaContext*>(handle), static_cast<uint32_t>(mask));
}

JNIEXPORT jint JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeRunFrame(JNIEnv*, jclass, jlong handle) {
    return storm_gbca_run_frame(reinterpret_cast<StormGbcaContext*>(handle));
}

JNIEXPORT void JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeSetFastForward(JNIEnv*, jclass, jlong handle, jfloat mult) {
    storm_gbca_set_fast_forward(reinterpret_cast<StormGbcaContext*>(handle), mult);
}

JNIEXPORT jfloat JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeGetVideoInfo(JNIEnv* env, jclass, jlong handle, jintArray outWH) {
    auto* ctx = reinterpret_cast<StormGbcaContext*>(handle);
    int w = 0, h = 0;
    float fps = 59.7275f;
    if (storm_gbca_get_video_info(ctx, &w, &h, &fps) != 0) return -1.0f;
    if (outWH && env->GetArrayLength(outWH) >= 2) {
        jint buf[2] = {w, h};
        env->SetIntArrayRegion(outWH, 0, 2, buf);
    }
    return fps;
}

JNIEXPORT jint JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeCopyFrame(JNIEnv* env, jclass, jlong handle, jintArray dstPixels, jint dstStridePx, jint pixelFormat) {
    auto* ctx = reinterpret_cast<StormGbcaContext*>(handle);
    if (!ctx || !dstPixels) return -1;
    jint* ptr = env->GetIntArrayElements(dstPixels, nullptr);
    int rc = storm_gbca_copy_frame(ctx, reinterpret_cast<uint32_t*>(ptr), dstStridePx, pixelFormat);
    env->ReleaseIntArrayElements(dstPixels, ptr, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeRewindStep(JNIEnv*, jclass, jlong handle) {
    return storm_gbca_rewind_step(reinterpret_cast<StormGbcaContext*>(handle));
}

JNIEXPORT jint JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeRewindAvailableFrames(JNIEnv*, jclass, jlong handle) {
    return storm_gbca_rewind_available_frames(reinterpret_cast<StormGbcaContext*>(handle));
}

JNIEXPORT jint JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeSaveStateFile(JNIEnv* env, jclass, jlong handle, jstring path) {
    auto* ctx = reinterpret_cast<StormGbcaContext*>(handle);
    if (!ctx || !path) return -1;
    const char* cpath = env->GetStringUTFChars(path, nullptr);
    int rc = storm_gbca_save_state_file(ctx, cpath);
    env->ReleaseStringUTFChars(path, cpath);
    return rc;
}

JNIEXPORT jint JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeLoadStateFile(JNIEnv* env, jclass, jlong handle, jstring path) {
    auto* ctx = reinterpret_cast<StormGbcaContext*>(handle);
    if (!ctx || !path) return -1;
    const char* cpath = env->GetStringUTFChars(path, nullptr);
    int rc = storm_gbca_load_state_file(ctx, cpath);
    env->ReleaseStringUTFChars(path, cpath);
    return rc;
}

JNIEXPORT jint JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeSaveSramFile(JNIEnv* env, jclass, jlong handle, jstring path) {
    auto* ctx = reinterpret_cast<StormGbcaContext*>(handle);
    if (!ctx || !path) return -1;
    const char* cpath = env->GetStringUTFChars(path, nullptr);
    int rc = storm_gbca_save_sram_file(ctx, cpath);
    env->ReleaseStringUTFChars(path, cpath);
    return rc;
}

JNIEXPORT jint JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeLoadSramFile(JNIEnv* env, jclass, jlong handle, jstring path) {
    auto* ctx = reinterpret_cast<StormGbcaContext*>(handle);
    if (!ctx || !path) return -1;
    const char* cpath = env->GetStringUTFChars(path, nullptr);
    int rc = storm_gbca_load_sram_file(ctx, cpath);
    env->ReleaseStringUTFChars(path, cpath);
    return rc;
}

JNIEXPORT jboolean JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeIsSramDirty(JNIEnv*, jclass, jlong handle) {
    return storm_gbca_is_sram_dirty(reinterpret_cast<StormGbcaContext*>(handle)) != 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeSetSolarLux(JNIEnv*, jclass, jlong handle, jint lux) {
    storm_gbca_set_solar_lux(reinterpret_cast<StormGbcaContext*>(handle), lux);
}

JNIEXPORT void JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeSetTilt(JNIEnv*, jclass, jlong handle, jfloat x, jfloat y) {
    storm_gbca_set_tilt(reinterpret_cast<StormGbcaContext*>(handle), x, y);
}

JNIEXPORT void JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeSetGyro(JNIEnv*, jclass, jlong handle, jfloat z) {
    storm_gbca_set_gyro(reinterpret_cast<StormGbcaContext*>(handle), z);
}

JNIEXPORT jfloat JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeGetRumble(JNIEnv*, jclass, jlong handle) {
    return storm_gbca_get_rumble(reinterpret_cast<StormGbcaContext*>(handle));
}

JNIEXPORT void JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeSetRtc(JNIEnv*, jclass, jlong handle, jboolean useHost, jlong epoch) {
    storm_gbca_set_rtc(reinterpret_cast<StormGbcaContext*>(handle), useHost ? 1 : 0, static_cast<int64_t>(epoch));
}

JNIEXPORT void JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeCheatsClear(JNIEnv*, jclass, jlong handle) {
    storm_gbca_cheats_clear(reinterpret_cast<StormGbcaContext*>(handle));
}

JNIEXPORT jint JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeCheatAdd(JNIEnv* env, jclass, jlong handle, jstring name, jstring code, jboolean enabled) {
    auto* ctx = reinterpret_cast<StormGbcaContext*>(handle);
    if (!ctx || !code) return -1;
    const char* cname = name ? env->GetStringUTFChars(name, nullptr) : "Cheat";
    const char* ccode = env->GetStringUTFChars(code, nullptr);
    int rc = storm_gbca_cheat_add(ctx, cname, ccode, enabled ? 1 : 0);
    if (name && cname) env->ReleaseStringUTFChars(name, cname);
    env->ReleaseStringUTFChars(code, ccode);
    return rc;
}

JNIEXPORT jint JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeReadMemory(JNIEnv* env, jclass, jlong handle, jint region, jint addr, jbyteArray dst) {
    auto* ctx = reinterpret_cast<StormGbcaContext*>(handle);
    if (!ctx || !dst) return 0;
    jsize len = env->GetArrayLength(dst);
    if (len <= 0) return 0;
    jbyte* buf = env->GetByteArrayElements(dst, nullptr);
    size_t got = storm_gbca_read_memory(ctx, region, static_cast<uint32_t>(addr),
                                        reinterpret_cast<uint8_t*>(buf), static_cast<size_t>(len));
    env->ReleaseByteArrayElements(dst, buf, 0);
    return static_cast<jint>(got);
}

JNIEXPORT jstring JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeComputeRaHash(JNIEnv* env, jclass, jstring path, jint platform) {
    if (!path) return nullptr;
    const char* cpath = env->GetStringUTFChars(path, nullptr);
    char hash[33] = {};
    int rc = storm_gbca_compute_ra_hash(cpath, platform, hash);
    env->ReleaseStringUTFChars(path, cpath);
    return (rc == 0) ? env->NewStringUTF(hash) : nullptr;
}

JNIEXPORT jstring JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeComputeRaHashBuffer(JNIEnv* env, jclass, jbyteArray data, jint platform) {
    if (!data) return nullptr;
    jsize len = env->GetArrayLength(data);
    if (len <= 0) return nullptr;
    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    char hash[33] = {};
    int rc = storm_gbca_compute_ra_hash_buffer(bytes, static_cast<size_t>(len), platform, hash);
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    return (rc == 0) ? env->NewStringUTF(hash) : nullptr;
}

JNIEXPORT jint JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeGetDmgPaletteCount(JNIEnv*, jclass) {
    return storm_gbca_get_dmg_palette_count();
}

JNIEXPORT jstring JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeGetDmgPaletteName(JNIEnv* env, jclass, jint index) {
    const char* name = storm_gbca_get_dmg_palette_name(index);
    return name ? env->NewStringUTF(name) : nullptr;
}

JNIEXPORT jboolean JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeAudioStart(JNIEnv*, jclass, jlong handle) {
    auto* ctx = reinterpret_cast<StormGbcaContext*>(handle);
    if (!ctx) return JNI_FALSE;
    return storm::android_audio_start(ctx) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeAudioStop(JNIEnv*, jclass) {
    storm::android_audio_stop();
}

JNIEXPORT void JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeAudioPause(JNIEnv*, jclass, jboolean paused) {
    storm::android_audio_pause(paused == JNI_TRUE);
}

JNIEXPORT jboolean JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeGlesInit(JNIEnv* env, jclass, jstring shaderSource) {
    std::string src;
    if (shaderSource) {
        const char* c = env->GetStringUTFChars(shaderSource, nullptr);
        if (c) {
            src = c;
            env->ReleaseStringUTFChars(shaderSource, c);
        }
    }
    return storm::gles_init(src) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeGlesResize(JNIEnv*, jclass, jint width, jint height) {
    storm::gles_resize(width, height);
}

JNIEXPORT void JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeGlesRender(
    JNIEnv*, jclass, jlong handle,
    jint vpX, jint vpY, jint vpW, jint vpH,
    jint displayMode, jfloat ghosting, jfloat ccStrength,
    jboolean isGrayscale, jboolean integerScaling
) {
    auto* ctx = reinterpret_cast<StormGbcaContext*>(handle);
    if (!ctx) {
        storm::gles_render(nullptr, 0, 0, vpX, vpY, vpW, vpH, displayMode, ghosting, ccStrength, isGrayscale == JNI_TRUE, integerScaling == JNI_TRUE);
        return;
    }
    int uw = 0, uh = 0;
    if (storm_gbca_get_upscaled_size(ctx, &uw, &uh) == 0 && uw > 0 && uh > 0) {
        int raw_w = 0, raw_h = 0;
        storm_gbca_get_video_info(ctx, &raw_w, &raw_h, nullptr);
        if (uw > raw_w || uh > raw_h) {
            static thread_local std::vector<uint32_t> up_buf;
            up_buf.resize(static_cast<size_t>(uw) * static_cast<size_t>(uh));
            int out_w = uw, out_h = uh;
            if (storm_gbca_copy_upscaled_frame(ctx, up_buf.data(), uw, STORM_PIXEL_FORMAT_RGBA8888, &out_w, &out_h) == 0) {
                storm::gles_render(up_buf.data(), out_w, out_h, vpX, vpY, vpW, vpH,
                                   displayMode, ghosting, ccStrength, isGrayscale == JNI_TRUE, integerScaling == JNI_TRUE);
                return;
            }
        }
    }
    const uint32_t* raw = nullptr;
    int w = 0, h = 0;
    if (storm_gbca_get_raw_frame_rgba(ctx, &raw, &w, &h) != 0 || !raw) {
        storm::gles_render(nullptr, 0, 0, vpX, vpY, vpW, vpH, displayMode, ghosting, ccStrength, isGrayscale == JNI_TRUE, integerScaling == JNI_TRUE);
        return;
    }
    storm::gles_render(raw, w, h, vpX, vpY, vpW, vpH, displayMode, ghosting, ccStrength, isGrayscale == JNI_TRUE, integerScaling == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeGlesSetRotation(JNIEnv*, jclass, jint rotDeg) {
    storm::gles_set_rotation(rotDeg);
}

JNIEXPORT void JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeGlesDestroy(JNIEnv*, jclass) {
    storm::gles_destroy();
}

// --- v1.1.0 Upscaling, Performance, RAM Cheat Finder, RetroArch librashader, Vulkan ---

JNIEXPORT void JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeSetUpscale(JNIEnv*, jclass, jlong handle, jint scale, jint mode) {
    auto* ctx = reinterpret_cast<StormGbcaContext*>(handle);
    if (ctx) storm_gbca_set_upscale(ctx, scale, mode);
}

JNIEXPORT void JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeSetPerformance(JNIEnv*, jclass, jlong handle, jint cpuOverclockPct, jint frameSkip) {
    auto* ctx = reinterpret_cast<StormGbcaContext*>(handle);
    if (ctx) storm_gbca_set_performance(ctx, cpuOverclockPct, frameSkip);
}

JNIEXPORT jint JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeWriteMemory(JNIEnv*, jclass, jlong handle, jint region, jint addr, jint value, jint sizeBytes) {
    auto* ctx = reinterpret_cast<StormGbcaContext*>(handle);
    if (!ctx) return -1;
    uint8_t bytes[4] = {
        static_cast<uint8_t>(value & 0xFF),
        static_cast<uint8_t>((value >> 8) & 0xFF),
        static_cast<uint8_t>((value >> 16) & 0xFF),
        static_cast<uint8_t>((value >> 24) & 0xFF),
    };
    size_t n = (sizeBytes == 2 || sizeBytes == 4) ? static_cast<size_t>(sizeBytes) : 1;
    return storm_gbca_write_memory(ctx, region, static_cast<uint32_t>(addr), bytes, n);
}

JNIEXPORT jint JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeMemSearchReset(JNIEnv*, jclass, jlong handle, jint region, jint valueSize) {
    auto* ctx = reinterpret_cast<StormGbcaContext*>(handle);
    if (!ctx) return -1;
    return storm_gbca_mem_search_reset(ctx, region, valueSize);
}

JNIEXPORT jint JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeMemSearchFilter(JNIEnv*, jclass, jlong handle, jint cmpOp, jint targetValue) {
    auto* ctx = reinterpret_cast<StormGbcaContext*>(handle);
    if (!ctx) return -1;
    return storm_gbca_mem_search_filter(ctx, cmpOp, static_cast<uint32_t>(targetValue));
}

JNIEXPORT jint JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeMemSearchGetResults(JNIEnv* env, jclass, jlong handle, jintArray outAddrs, jintArray outVals) {
    auto* ctx = reinterpret_cast<StormGbcaContext*>(handle);
    if (!ctx || !outAddrs || !outVals) return 0;
    jsize maxN = std::min(env->GetArrayLength(outAddrs), env->GetArrayLength(outVals));
    if (maxN <= 0) return 0;
    std::vector<uint32_t> addrs(static_cast<size_t>(maxN));
    std::vector<uint32_t> vals(static_cast<size_t>(maxN));
    int got = storm_gbca_mem_search_get_results(ctx, addrs.data(), vals.data(), static_cast<int>(maxN));
    if (got > 0) {
        env->SetIntArrayRegion(outAddrs, 0, got, reinterpret_cast<const jint*>(addrs.data()));
        env->SetIntArrayRegion(outVals, 0, got, reinterpret_cast<const jint*>(vals.data()));
    }
    return got;
}

JNIEXPORT jboolean JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeIsLibrashaderAvailable(JNIEnv*, jclass) {
    return storm::librashader_is_available() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeGetLibrashaderStatus(JNIEnv* env, jclass) {
    return env->NewStringUTF(storm::librashader_status().c_str());
}

JNIEXPORT void JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeSetRetroArchShader(JNIEnv* env, jclass, jstring presetPath) {
    std::string p;
    if (presetPath) {
        const char* c = env->GetStringUTFChars(presetPath, nullptr);
        if (c) {
            p = c;
            env->ReleaseStringUTFChars(presetPath, c);
        }
    }
    storm::librashader_set_preset(p);
}

JNIEXPORT jboolean JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeIsVulkanSupported(JNIEnv*, jclass) {
    std::string dev;
    return storm::vk_probe_support(dev) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeGetVulkanDeviceName(JNIEnv* env, jclass) {
    std::string dev;
    storm::vk_probe_support(dev);
    return env->NewStringUTF(dev.c_str());
}

JNIEXPORT jboolean JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeVkInit(JNIEnv* env, jclass, jobject surface, jstring customDriverPath) {
    if (!surface) return JNI_FALSE;
    ANativeWindow* win = ANativeWindow_fromSurface(env, surface);
    if (!win) return JNI_FALSE;
    std::string drv;
    if (customDriverPath) {
        const char* c = env->GetStringUTFChars(customDriverPath, nullptr);
        if (c) {
            drv = c;
            env->ReleaseStringUTFChars(customDriverPath, c);
        }
    }
    bool ok = storm::vk_init(win, drv);
    ANativeWindow_release(win);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeVkResize(JNIEnv*, jclass, jint width, jint height) {
    storm::vk_resize(width, height);
}

JNIEXPORT void JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeVkRender(
    JNIEnv*, jclass, jlong handle,
    jint vpX, jint vpY, jint vpW, jint vpH,
    jboolean linearFilter, jint rotDeg
) {
    auto* ctx = reinterpret_cast<StormGbcaContext*>(handle);
    if (!ctx) return;
    int uw = 0, uh = 0;
    if (storm_gbca_get_upscaled_size(ctx, &uw, &uh) == 0 && uw > 0 && uh > 0) {
        static thread_local std::vector<uint32_t> vk_buf;
        vk_buf.resize(static_cast<size_t>(uw) * static_cast<size_t>(uh));
        int out_w = uw, out_h = uh;
        if (storm_gbca_copy_upscaled_frame(ctx, vk_buf.data(), uw, STORM_PIXEL_FORMAT_RGBA8888, &out_w, &out_h) == 0) {
            storm::vk_render(vk_buf.data(), out_w, out_h, vpX, vpY, vpW, vpH, linearFilter == JNI_TRUE, rotDeg);
            return;
        }
    }
    const uint32_t* raw = nullptr;
    int w = 0, h = 0;
    if (storm_gbca_get_raw_frame_rgba(ctx, &raw, &w, &h) == 0 && raw) {
        storm::vk_render(raw, w, h, vpX, vpY, vpW, vpH, linearFilter == JNI_TRUE, rotDeg);
    }
}

JNIEXPORT void JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeVkDestroy(JNIEnv*, jclass) {
    storm::vk_destroy();
}

// --- v1.2.0 Device Optimization, Multiplayer / Link Cable / Wireless / JoyBus / GB Printer ---

JNIEXPORT void JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeSetOptimizationProfile(JNIEnv*, jclass, jlong handle, jint profile) {
    auto* ctx = reinterpret_cast<StormGbcaContext*>(handle);
    if (ctx) storm_gbca_set_optimization_profile(ctx, profile);
}

JNIEXPORT jint JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeLinkStart(
    JNIEnv* env, jclass, jlong handle, jint mode, jstring hostIp, jint port, jint clockPort, jstring roomName
) {
    auto* ctx = reinterpret_cast<StormGbcaContext*>(handle);
    if (!ctx) return -1;
    std::string ip, rname;
    if (hostIp) {
        const char* c = env->GetStringUTFChars(hostIp, nullptr);
        if (c) { ip = c; env->ReleaseStringUTFChars(hostIp, c); }
    }
    if (roomName) {
        const char* c = env->GetStringUTFChars(roomName, nullptr);
        if (c) { rname = c; env->ReleaseStringUTFChars(roomName, c); }
    }
    return storm_gbca_link_start(ctx, mode, ip.c_str(), port, clockPort, rname.c_str());
}

JNIEXPORT void JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeLinkStop(JNIEnv*, jclass, jlong handle) {
    auto* ctx = reinterpret_cast<StormGbcaContext*>(handle);
    if (ctx) storm_gbca_link_stop(ctx);
}

JNIEXPORT jstring JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeLinkGetStatus(JNIEnv* env, jclass, jlong handle) {
    auto* ctx = reinterpret_cast<StormGbcaContext*>(handle);
    if (!ctx) return env->NewStringUTF("0\t0\t0\t1\t0\t0\t\t\tDisconnected");
    StormGbcaLinkStatus st{};
    storm_gbca_link_get_status(ctx, &st);
    char buf[512];
    std::snprintf(buf, sizeof(buf), "%d\t%d\t%d\t%d\t%d\t%d\t%s\t%s\t%s",
                  st.mode, st.connected, st.player_id, st.player_count, st.ping_ms, st.port,
                  sanitize_field(st.room_name).c_str(),
                  sanitize_field(st.peer_info).c_str(),
                  sanitize_field(st.status_text).c_str());
    return env->NewStringUTF(buf);
}

JNIEXPORT jstring JNICALL
Java_ru_stormgames_gbca_NativeCore_nativeLanDiscover(JNIEnv* env, jclass, jint timeoutMs) {
    StormGbcaLanRoom rooms[16]{};
    int count = storm_gbca_lan_discover(rooms, 16, timeoutMs);
    std::string out;
    for (int i = 0; i < count; ++i) {
        char line[384];
        std::snprintf(line, sizeof(line), "%s\t%d\t%d\t%d\t%d\t%d\t%s\t%s\n",
                      sanitize_field(rooms[i].host_ip).c_str(),
                      rooms[i].port,
                      rooms[i].mode,
                      rooms[i].platform,
                      rooms[i].player_count,
                      rooms[i].max_players,
                      sanitize_field(rooms[i].room_name).c_str(),
                      sanitize_field(rooms[i].game_title).c_str());
        out += line;
    }
    return env->NewStringUTF(out.c_str());
}

JNIEXPORT jintArray JNICALL
Java_ru_stormgames_gbca_NativeCore_nativePollPrinterImage(JNIEnv* env, jclass, jlong handle, jintArray outWH) {
    auto* ctx = reinterpret_cast<StormGbcaContext*>(handle);
    if (!ctx) return nullptr;
    int w = 0, h = 0;
    if (storm_gbca_printer_poll_image(ctx, nullptr, 0, &w, &h) != 1 || w <= 0 || h <= 0) {
        return nullptr;
    }
    int total = w * h;
    std::vector<uint32_t> rgba(static_cast<size_t>(total));
    if (storm_gbca_printer_poll_image(ctx, rgba.data(), total, &w, &h) != 1) {
        return nullptr;
    }
    if (outWH && env->GetArrayLength(outWH) >= 2) {
        jint wh[2] = {w, h};
        env->SetIntArrayRegion(outWH, 0, 2, wh);
    }
    jintArray arr = env->NewIntArray(total);
    if (!arr) return nullptr;
    env->SetIntArrayRegion(arr, 0, total, reinterpret_cast<const jint*>(rgba.data()));
    return arr;
}

} // extern "C"

#endif // ANDROID
