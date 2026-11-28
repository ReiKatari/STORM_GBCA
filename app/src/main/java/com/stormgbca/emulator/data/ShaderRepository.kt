package com.stormgbca.emulator.data

import android.content.Context
import android.net.Uri
import com.stormgbca.emulator.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class RetroArchShaderPreset(
    val id: String,
    val name: String,
    val description: String,
    val path: String,
    val isBuiltIn: Boolean = true,
)

object ShaderRepository {
    private data class BuiltInDef(val file: String, val name: String, val descRes: Int)

    private val BUILT_INS = listOf(
        BuiltInDef("lcd-gba-grid.slangp", "LCD GBA Grid", R.string.shader_desc_lcd_gba),
        BuiltInDef("lcd-dmg-olive.slangp", "LCD DMG Olive", R.string.shader_desc_lcd_dmg),
        BuiltInDef("lcd3x-sharp.slangp", "LCD 3x Sharp", R.string.shader_desc_lcd3x),
        BuiltInDef("gba-color-ags101.slangp", "GBA Color AGS-101", R.string.shader_desc_ags101),
        BuiltInDef("gbc-color-accurate.slangp", "GBC Color Accurate", R.string.shader_desc_gbc_color),
        BuiltInDef("xbrz-smooth.slangp", "xBRZ Smooth HD", R.string.shader_desc_xbrz),
        BuiltInDef("sharp-bilinear.slangp", "Sharp Bilinear", R.string.shader_desc_sharp_bilinear),
        BuiltInDef("crt-scanlines.slangp", "CRT Scanlines", R.string.shader_desc_crt),
        BuiltInDef("vibrant-hd.slangp", "Vibrant HD", R.string.shader_desc_vibrant),
        BuiltInDef("grayscale-pocket.slangp", "Game Boy Pocket", R.string.shader_desc_pocket),
        BuiltInDef("fsr1-easu-rcas.slangp", "AMD FSR 1 (EASU + RCAS)", R.string.shader_desc_fsr1),
        BuiltInDef("fsr3-temporal-rcas.slangp", "AMD FSR 3 (Temporal + RCAS)", R.string.shader_desc_fsr3),
    )

    fun shadersDir(context: Context): File = File(context.filesDir, "shaders").apply { mkdirs() }
    fun customShadersDir(context: Context): File = File(shadersDir(context), "custom").apply { mkdirs() }

    fun ensureExtracted(context: Context) {
        val dir = shadersDir(context)
        runCatching {
            val verFile = File(dir, ".extracted_ver")
            val curVer = com.stormgbca.emulator.BuildConfig.VERSION_CODE.toString()
            val forceRefresh = !verFile.isFile || verFile.readText().trim() != curVer
            val assetFiles = context.assets.list("shaders") ?: emptyArray()
            for (name in assetFiles) {
                val dst = File(dir, name)
                if (forceRefresh || !dst.exists() || dst.length() == 0L) {
                    context.assets.open("shaders/$name").use { input ->
                        FileOutputStream(dst).use { output -> input.copyTo(output) }
                    }
                }
            }
            verFile.writeText(curVer)
        }
    }

    fun listPresets(context: Context): List<RetroArchShaderPreset> {
        ensureExtracted(context)
        val dir = shadersDir(context)
        val result = ArrayList<RetroArchShaderPreset>()
        result.add(
            RetroArchShaderPreset(
                id = "",
                name = context.getString(R.string.shader_off_name),
                description = context.getString(R.string.shader_off_desc),
                path = "",
                isBuiltIn = true,
            )
        )
        for (def in BUILT_INS) {
            val f = File(dir, def.file)
            if (f.exists()) {
                result.add(RetroArchShaderPreset(def.file, def.name, context.getString(def.descRes), f.absolutePath, true))
            }
        }
        customShadersDir(context).listFiles()?.sortedBy { it.name.lowercase() }?.forEach { f ->
            if (f.isFile && f.name.endsWith(".slangp", ignoreCase = true)) {
                result.add(
                    RetroArchShaderPreset(
                        id = "custom/${f.name}",
                        name = f.nameWithoutExtension,
                        description = context.getString(R.string.shader_custom_desc),
                        path = f.absolutePath,
                        isBuiltIn = false,
                    )
                )
            }
        }
        return result
    }

    suspend fun importCustomShader(context: Context, uri: Uri, displayName: String?): RetroArchShaderPreset? =
        withContext(Dispatchers.IO) {
            val safe = (displayName ?: "custom_${System.currentTimeMillis()}.slangp")
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val fileName = if (safe.endsWith(".slangp", ignoreCase = true) || safe.endsWith(".slang", ignoreCase = true)) {
                safe
            } else {
                "$safe.slangp"
            }
            val dst = File(customShadersDir(context), fileName)
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dst).use { output -> input.copyTo(output) }
                }
                if (dst.name.endsWith(".slang", ignoreCase = true)) {
                    val wrapper = File(customShadersDir(context), "${dst.nameWithoutExtension}.slangp")
                    wrapper.writeText(
                        "shaders = \"1\"\nshader0 = \"${dst.name}\"\nfilter_linear0 = \"false\"\nscale_type0 = \"viewport\"\n"
                    )
                    RetroArchShaderPreset("custom/${wrapper.name}", wrapper.nameWithoutExtension, context.getString(R.string.shader_imported_shader), wrapper.absolutePath, false)
                } else {
                    RetroArchShaderPreset("custom/${dst.name}", dst.nameWithoutExtension, context.getString(R.string.shader_imported_preset), dst.absolutePath, false)
                }
            }.getOrNull()
        }
}
