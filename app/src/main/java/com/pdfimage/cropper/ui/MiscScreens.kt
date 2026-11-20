package com.pdfimage.cropper.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pdfimage.cropper.crop.ImageFormat
import com.pdfimage.cropper.crop.Resolution
import com.pdfimage.cropper.data.ThemeMode

@Composable
fun GalleryScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar("Saved images", "${vm.recentOutputs.size} recent", vm::back) }
    ) { pad ->
        if (vm.recentOutputs.isEmpty()) {
            Column(Modifier.padding(pad).padding(24.dp).fillMaxSize()) {
                Text("Nothing saved yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(
                    start = 12.dp, end = 12.dp,
                    top = pad.calculateTopPadding() + 8.dp,
                    bottom = pad.calculateBottomPadding() + 12.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(vm.recentOutputs, key = { it.uri }) { img ->
                    SavedThumb(
                        img.uri,
                        Modifier.fillMaxWidth().aspectRatio(1f).clickable {
                            val view = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(img.uri), "image/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            try { ctx.startActivity(view) }
                            catch (e: ActivityNotFoundException) { vm.message = "No app can open this image." }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val version = androidx.compose.runtime.remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "1.0.0" }
    val o = vm.options

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar("Settings", onBack = vm::back) }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(horizontal = 20.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionTitle("Default image format")
            ChipRow(ImageFormat.entries, o.format, { it.name }) { vm.setDefaults(o.copy(format = it)) }
            SectionTitle("Default JPG quality")
            ChipRow(listOf(50, 70, 80, 90, 100), o.quality, { "$it%" }) { vm.setDefaults(o.copy(quality = it)) }
            SectionTitle("Default output resolution")
            ChipRow(Resolution.entries, o.resolution, { it.label }) { vm.setDefaults(o.copy(resolution = it)) }

            SectionTitle("Save folder (inside Pictures)")
            OutlinedTextField(
                value = vm.folder, onValueChange = vm::updateFolder, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Only letters, numbers, spaces, - and _ are used.") }
            )

            SectionTitle("Theme")
            ChipRow(ThemeMode.entries, vm.theme, { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }) { vm.updateTheme(it) }

            ToggleRow("Haptic feedback", vm.haptics, vm::updateHaptics)
            ToggleRow("Keep crop sessions after converting", vm.keepSessions, vm::updateKeepSessions)

            OutlinedButton(onClick = vm::clearTemporaryFiles, modifier = Modifier.fillMaxWidth()) { Text("Clear temporary files") }

            SectionTitle("About")
            Text("PDF Image Cropper · version $version", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Privacy: every PDF is processed on this device. The app has no internet permission, no analytics and no ads, and never uploads your documents.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
fun HelpScreen(vm: AppViewModel) {
    val steps = listOf(
        "1. Tap IMPORT PDF and choose a document.",
        "2. Tap a page thumbnail to open the crop editor. Use the check mark on a thumbnail to include or exclude a whole page.",
        "3. Drag with one finger to draw a rectangle around a photo. Drag the corner or edge handles to resize, drag inside the active crop to move it.",
        "4. Draw as many rectangles as you need on the same page. Tap a crop (or its corner) to select it; use Delete / Duplicate in the bottom bar, and Undo / Redo at the top.",
        "5. Pinch to zoom and use two fingers to pan. The crop always maps to the original PDF page, whatever the zoom.",
        "6. Open Review & convert to preview, reorder, edit or untick crops.",
        "7. Pick JPG or PNG, quality and resolution, then tap CONVERT ALL. Every crop becomes its own image in Pictures."
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar("Help", onBack = vm::back) }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(horizontal = 20.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            steps.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}
