package com.stormgbca.emulator.data

import android.content.Context
import android.net.Uri
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
    private data class BuiltInDef(val file: String, val name: String, val desc: String)

    private val BUILT_INS = listOf(
        BuiltInDef("lcd-gba-grid.slangp", "LCD GBA Grid", "Аутентичная матрица GBA с RGB-субпикселями"),
        BuiltInDef("lcd-dmg-olive.slangp", "LCD DMG Olive", "Классический зеленый Dot-Matrix экран Game Boy"),
        BuiltInDef("lcd3x-sharp.slangp", "LCD 3x Sharp", "Четкая субпиксельная сетка LCD 3x"),
        BuiltInDef("gba-color-ags101.slangp", "GBA Color AGS-101", "Калиброванная цветопередача GBA SP AGS-101"),
        BuiltInDef("gbc-color-accurate.slangp", "GBC Color Accurate", "Точная гамма и палитра Game Boy Color"),
        BuiltInDef("xbrz-smooth.slangp", "xBRZ Smooth HD", "Сглаживание диагональных контуров пиксель-арта"),
        BuiltInDef("sharp-bilinear.slangp", "Sharp Bilinear", "Четкое масштабирование без мерцания"),
        BuiltInDef("crt-scanlines.slangp", "CRT Scanlines", "Ретро-эффект ЭЛТ со строчной разверткой"),
        BuiltInDef("vibrant-hd.slangp", "Vibrant HD", "Повышенный контраст и насыщенность цветов"),
        BuiltInDef("grayscale-pocket.slangp", "Game Boy Pocket", "Монохромный серебристый LCD Game Boy Pocket"),
    )

    fun shadersDir(context: Context): File = File(context.filesDir, "shaders").apply { mkdirs() }
    fun customShadersDir(context: Context): File = File(shadersDir(context), "custom").apply { mkdirs() }

    fun ensureExtracted(context: Context) {
        val dir = shadersDir(context)
        runCatching {
            val assetFiles = context.assets.list("shaders") ?: emptyArray()
            for (name in assetFiles) {
                val dst = File(dir, name)
                if (!dst.exists() || dst.length() == 0L) {
                    context.assets.open("shaders/$name").use { input ->
                        FileOutputStream(dst).use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }

    fun listPresets(context: Context): List<RetroArchShaderPreset> {
        ensureExtracted(context)
        val dir = shadersDir(context)
        val result = ArrayList<RetroArchShaderPreset>()
        result.add(
            RetroArchShaderPreset(
                id = "",
                name = "Отключен (Встроенный шейдер)",
                description = "Использовать встроенный шейдер STORM / SkyEmu LCD",
                path = "",
                isBuiltIn = true,
            )
        )
        for (def in BUILT_INS) {
            val f = File(dir, def.file)
            if (f.exists()) {
                result.add(RetroArchShaderPreset(def.file, def.name, def.desc, f.absolutePath, true))
            }
        }
        customShadersDir(context).listFiles()?.sortedBy { it.name.lowercase() }?.forEach { f ->
            if (f.isFile && f.name.endsWith(".slangp", ignoreCase = true)) {
                result.add(
                    RetroArchShaderPreset(
                        id = "custom/${f.name}",
                        name = f.nameWithoutExtension,
                        description = "Пользовательский пресет RetroArch (.slangp)",
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
                    // Auto-wrap single .slang file into a valid .slangp preset for librashader!
                    val wrapper = File(customShadersDir(context), "${dst.nameWithoutExtension}.slangp")
                    wrapper.writeText(
                        "shaders = \"1\"\nshader0 = \"${dst.name}\"\nfilter_linear0 = \"false\"\nscale_type0 = \"viewport\"\n"
                    )
                    RetroArchShaderPreset("custom/${wrapper.name}", wrapper.nameWithoutExtension, "Импортированный шейдер RetroArch", wrapper.absolutePath, false)
                } else {
                    RetroArchShaderPreset("custom/${dst.name}", dst.nameWithoutExtension, "Импортированный пресет RetroArch", dst.absolutePath, false)
                }
            }.getOrNull()
        }
}
