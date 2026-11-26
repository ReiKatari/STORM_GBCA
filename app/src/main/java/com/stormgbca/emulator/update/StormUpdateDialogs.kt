package com.stormgbca.emulator.update

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.stormgbca.emulator.BuildConfig
import com.stormgbca.emulator.R
import com.stormgbca.emulator.ui.theme.SpaceGrotesk
import com.stormgbca.emulator.ui.theme.WatermelonMono
import com.stormgbca.emulator.ui.theme.watermelon

@Composable
fun StormUpdateDialog(
    release: UpdateReleaseInfo,
    downloadState: UpdateDownloadState,
    onStartDownload: () -> Unit,
    onInstallApk: (java.io.File) -> Unit,
    onSkipVersion: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = watermelon
    val context = LocalContext.current
    val sizeMb = (release.apkAsset?.sizeBytes ?: 0L) / (1024.0 * 1024.0)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.surface,
            elevation = 20.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.line, RoundedCornerShape(18.dp)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.greenDim)
                            .border(1.dp, colors.green.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = colors.green, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.update_dialog_title), color = colors.text, fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("STORM GBCA", color = colors.text2, fontFamily = WatermelonMono, fontSize = 11.sp)
                    }
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surface2)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close), tint = colors.text, modifier = Modifier.size(16.dp))
                    }
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surface2)
                            .padding(12.dp),
                    ) {
                        Text("v${BuildConfig.VERSION_NAME}", color = colors.text2, fontFamily = WatermelonMono, fontSize = 13.sp)
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = colors.green, modifier = Modifier.size(16.dp))
                        Text("v${release.version}", color = colors.green, fontFamily = WatermelonMono, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        if (sizeMb > 0.0) {
                            Text(stringResource(R.string.update_dialog_size_mb_fmt, sizeMb), color = colors.text2, fontFamily = WatermelonMono, fontSize = 11.sp)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surface2)
                            .border(1.dp, colors.line, RoundedCornerShape(8.dp))
                            .clickable {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.releasePageUrl))) }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Filled.Public, contentDescription = null, tint = colors.green, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(release.sourceLabel, color = colors.text, fontFamily = WatermelonMono, fontSize = 11.sp)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = colors.text2, modifier = Modifier.size(12.dp))
                    }

                    if (release.changelogMd.isNotBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 190.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.bg)
                                .border(1.dp, colors.line, RoundedCornerShape(10.dp))
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            release.changelogMd.lines().filter { it.isNotBlank() }.forEach { line ->
                                val clean = line.trim().removePrefix("# ").removePrefix("## ").removePrefix("### ")
                                Text(clean, color = colors.text2, fontSize = 12.sp, lineHeight = 16.sp)
                            }
                        }
                    }

                    when (downloadState) {
                        is UpdateDownloadState.Downloading -> {
                            val progress = if (downloadState.totalBytes > 0) {
                                (downloadState.bytesRead.toFloat() / downloadState.totalBytes.toFloat()).coerceIn(0f, 1f)
                            } else 0f
                            val mbSpeed = downloadState.speedBytesPerSec / (1024.0 * 1024.0)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                LinearProgressIndicator(
                                    progress = progress,
                                    color = colors.green,
                                    backgroundColor = colors.surface3,
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                )
                                Text(
                                    text = stringResource(R.string.update_downloading_fmt, progress * 100f, mbSpeed),
                                    color = colors.text2,
                                    fontFamily = WatermelonMono,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                        is UpdateDownloadState.Ready -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.green)
                                    .clickable { onInstallApk(downloadState.apkFile) }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(stringResource(R.string.update_install), color = Color(0xFF05060A), fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold)
                            }
                        }
                        is UpdateDownloadState.Error -> {
                            Text(stringResource(R.string.update_error_fmt, downloadState.message), color = colors.red, fontSize = 12.sp)
                        }
                        UpdateDownloadState.Idle -> {}
                    }

                    if (downloadState !is UpdateDownloadState.Ready) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(colors.surface2)
                                    .clickable(onClick = onSkipVersion)
                                    .padding(vertical = 11.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(stringResource(R.string.update_skip), color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1.6f)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(colors.green)
                                    .clickable(enabled = downloadState !is UpdateDownloadState.Downloading, onClick = onStartDownload)
                                    .padding(vertical = 11.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = Color(0xFF05060A), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.update_now), color = Color(0xFF05060A), fontFamily = SpaceGrotesk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
