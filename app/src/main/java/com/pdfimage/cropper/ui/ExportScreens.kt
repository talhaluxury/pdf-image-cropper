package com.pdfimage.cropper.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pdfimage.cropper.crop.ImageFormat
import com.pdfimage.cropper.crop.Resolution

@Composable
fun ExportSettingsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val count = vm.exportItems().size
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startExport()
        else vm.message = "Storage permission is needed on Android 8–9 to save into the gallery."
    }
    val o = vm.options

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar("Export settings", "$count image${if (count == 1) "" else "s"}", vm::back) }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(horizontal = 20.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionTitle("Format")
            ChipRow(ImageFormat.entries, o.format, { it.name }) { vm.options = o.copy(format = it) }

            SectionTitle("JPG quality")
            ChipRow(listOf(50, 70, 80, 90, 100), o.quality, { "$it%" }, enabled = o.format == ImageFormat.JPG) {
                vm.options = o.copy(quality = it)
            }
            if (o.format == ImageFormat.PNG) {
                Text("PNG is lossless, so quality doesn't apply.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionTitle("Output resolution")
            ChipRow(Resolution.entries, o.resolution, { it.label }) { vm.options = o.copy(resolution = it) }
            Text(
                "Each crop is rendered straight from the PDF at this resolution, so aspect ratio is always preserved. Transparent areas are filled white.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val needsPermission = Build.VERSION.SDK_INT < 29 &&
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    if (needsPermission) permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else vm.startExport()
                },
                enabled = count > 0,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("CONVERT ALL ($count)") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun ProgressScreen(vm: AppViewModel) {
    var confirm by remember { mutableStateOf(false) }
    val total = vm.progressTotal.coerceAtLeast(1)
    val current = (vm.progressDone + 1).coerceAtMost(total)

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(
            Modifier.padding(pad).padding(24.dp).fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Converting image $current of $total", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(progress = { vm.progressDone.toFloat() / total }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Text(vm.progressName, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(28.dp))
            OutlinedButton(onClick = { confirm = true }) { Text("Cancel") }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Cancel conversion?") },
            text = { Text("Images already saved will stay in your gallery.") },
            confirmButton = { TextButton(onClick = { confirm = false; vm.cancelExport() }) { Text("Stop") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep going") } }
        )
    }
}

@Composable
fun ResultScreen(vm: AppViewModel, onAnother: () -> Unit) {
    val ctx = LocalContext.current
    val r = vm.result ?: return

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(if (r.cancelled) "Conversion cancelled" else "Conversion complete", onBack = vm::back) }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(horizontal = 20.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatLine("Saved images", "${r.saved.size}")
                    StatLine("Failed", "${r.failed}")
                    StatLine("Total size", formatBytes(r.bytes))
                    StatLine("Duration", formatDuration(r.durationMs))
                    StatLine("Location", "Pictures/${com.pdfimage.cropper.data.AppPrefs.sanitizeFolder(vm.folder)}")
                }
            }
            if (r.errors.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Some images failed", style = MaterialTheme.typography.titleSmall)
                        r.errors.take(8).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                        if (r.errors.size > 8) Text("…and ${r.errors.size - 8} more", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Button(
                onClick = {
                    val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { ctx.startActivity(intent) }
                    catch (e: ActivityNotFoundException) {
                        try {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e2: ActivityNotFoundException) { vm.message = "No gallery app found." }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.padding(4.dp)); Text("Open gallery") }

            OutlinedButton(
                onClick = {
                    val uris = ArrayList(r.saved.map { it.uri })
                    val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "image/*"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    ctx.startActivity(Intent.createChooser(send, "Share images"))
                },
                enabled = r.saved.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Icon(Icons.Default.Share, null); Spacer(Modifier.padding(4.dp)); Text("Share images") }

            OutlinedButton(onClick = { vm.finishAndHome(); onAnother() }, modifier = Modifier.fillMaxWidth()) { Text("Convert another PDF") }
            TextButton(onClick = vm::finishAndHome, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
