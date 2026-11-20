package com.pdfimage.cropper.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PagesScreen(vm: AppViewModel) {
    val doc = vm.doc ?: return
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = doc.name,
                subtitle = "${doc.pageCount} pages · ${vm.crops.size} crops",
                onBack = vm::back,
                actions = {
                    IconButton(onClick = vm::selectAllPages) { Icon(Icons.Default.SelectAll, "Select all pages") }
                    IconButton(onClick = vm::deselectAllPages) { Icon(Icons.Default.Deselect, "Deselect all pages") }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Text(
                    "${vm.exportItems().size} images to convert",
                    modifier = Modifier.weight(1f).padding(start = 16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = { vm.navigate(Screen.REVIEW) }, modifier = Modifier.padding(end = 16.dp)) {
                    Text("Review & convert")
                }
            }
        }
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(112.dp),
            contentPadding = PaddingValues(
                start = 12.dp, end = 12.dp,
                top = pad.calculateTopPadding() + 8.dp,
                bottom = pad.calculateBottomPadding() + 12.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items((0 until doc.pageCount).toList(), key = { it }) { page -> PageCard(vm, page) }
        }
    }
}

@Composable
private fun PageCard(vm: AppViewModel, page: Int) {
    val thumb by produceState<Bitmap?>(null, page) { value = vm.thumbnail(page) }
    val selected = page in vm.selectedPages
    val count = vm.crops.count { it.page == page }
    val ratio = thumb?.let { it.width.toFloat() / it.height } ?: 0.707f

    Card(
        modifier = Modifier.fillMaxWidth().clickable { vm.openEditor(page) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(ratio)) {
            val b = thumb
            if (b != null) {
                Image(b.asImageBitmap(), "Page ${page + 1}", Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
            } else {
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(24.dp), strokeWidth = 2.dp)
            }
            Text(
                "${page + 1}",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
            if (count > 0) {
                Text(
                    "$count crop${if (count == 1) "" else "s"}",
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                        .background(Color(0xFF2F80ED), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            IconButton(
                onClick = { vm.togglePage(page) },
                modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(36.dp)
            ) {
                Box(Modifier.background(Color.White.copy(alpha = 0.85f), CircleShape).padding(2.dp)) {
                    Icon(
                        if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (selected) "Deselect page" else "Select page",
                        tint = Color.Black
                    )
                }
            }
        }
    }
}
