package com.pdfimage.cropper.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.pdfimage.cropper.crop.CropRegion

@Composable
fun ReviewScreen(vm: AppViewModel) {
    val sorted = vm.crops.sortedWith(compareBy({ it.page }, { it.order }))
    var preview by remember { mutableStateOf<CropRegion?>(null) }
    val total = vm.exportItems().size
    val fullPages = vm.selectedPages.count { p -> vm.crops.none { it.page == p } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = "Review crops",
                subtitle = "$total image${if (total == 1) "" else "s"} will be created",
                onBack = vm::back,
                actions = {
                    IconButton(onClick = { vm.setAllCropsSelected(true) }, enabled = sorted.isNotEmpty()) { Icon(Icons.Default.SelectAll, "Select all crops") }
                    IconButton(onClick = { vm.setAllCropsSelected(false) }, enabled = sorted.isNotEmpty()) { Icon(Icons.Default.Deselect, "Deselect all crops") }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Button(
                    onClick = { vm.navigate(Screen.EXPORT) },
                    enabled = total > 0,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) { Text("Export settings ($total)") }
            }
        }
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = pad.calculateTopPadding() + 8.dp,
                bottom = pad.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (fullPages > 0) {
                item {
                    Text(
                        "$fullPages selected page${if (fullPages == 1) "" else "s"} without crops will be exported as a whole page.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (sorted.isEmpty()) {
                item {
                    Text(
                        "No crops yet. Open a page and drag a rectangle around each photo.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(sorted, key = { it.id }) { crop ->
                val onPage = vm.cropsOnPage(crop.page)
                val index = onPage.indexOfFirst { it.id == crop.id }
                CropRow(
                    vm, crop, index,
                    canUp = index > 0, canDown = index < onPage.size - 1,
                    onPreview = { preview = crop }
                )
            }
        }
    }

    preview?.let { crop ->
        val bmp by produceState<Bitmap?>(null, crop.id) { value = vm.cropPreview(crop, 110) }
        AlertDialog(
            onDismissRequest = { preview = null },
            confirmButton = { TextButton(onClick = { preview = null }) { Text("Close") } },
            title = { Text("Page ${crop.page + 1} crop") },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val b = bmp
                    if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
                    else CircularProgressIndicator()
                }
            }
        )
    }
}

@Composable
private fun CropRow(
    vm: AppViewModel, crop: CropRegion, index: Int,
    canUp: Boolean, canDown: Boolean, onPreview: () -> Unit
) {
    val thumb by produceState<Bitmap?>(null, crop.id, crop.rect) { value = vm.cropPreview(crop, 30) }
    var confirmDelete by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = crop.selected, onCheckedChange = { vm.toggleCrop(crop.id) })
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onPreview),
                contentAlignment = Alignment.Center
            ) {
                val b = thumb
                if (b != null) Image(b.asImageBitmap(), "Crop preview", Modifier.size(72.dp), contentScale = ContentScale.Fit)
                else CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Page ${crop.page + 1} · Crop ${index + 1}", style = MaterialTheme.typography.bodyLarge)
                val w = (crop.rect.width * 100).toInt()
                val h = (crop.rect.height * 100).toInt()
                Text("$w% × $h% of page", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column {
                Row {
                    IconButton(onClick = { vm.moveCrop(crop.id, -1) }, enabled = canUp, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.ArrowUpward, "Move up") }
                    IconButton(onClick = { vm.moveCrop(crop.id, 1) }, enabled = canDown, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.ArrowDownward, "Move down") }
                }
                Row {
                    IconButton(onClick = { vm.openEditor(crop.page, crop.id) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Edit, "Edit crop") }
                    IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Delete, "Delete crop") }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this crop?") },
            confirmButton = { TextButton(onClick = { vm.deleteCrop(crop.id); confirmDelete = false }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}
