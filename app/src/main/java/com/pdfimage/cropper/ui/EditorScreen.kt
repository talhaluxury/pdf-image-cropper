package com.pdfimage.cropper.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdfimage.cropper.crop.CropGeometry
import com.pdfimage.cropper.crop.Handle
import com.pdfimage.cropper.crop.ViewTransform
import kotlin.math.roundToInt

private val ActiveColor = Color(0xFF2F80ED)
private val InactiveColor = Color(0xFF8E8E93)

@Composable
fun EditorScreen(vm: AppViewModel) {
    val page = vm.editorPage
    val pageCount = vm.doc?.pageCount ?: 0

    var data by remember(page) { mutableStateOf<EditorPage?>(null) }
    LaunchedEffect(page) { data = vm.renderEditorPage(page) }

    var scale by remember(page) { mutableFloatStateOf(1f) }
    var offset by remember(page) { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var confirmReset by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val pageCrops = vm.cropsOnPage(page)
    val active = pageCrops.firstOrNull { it.id == vm.activeId }
    val dpi = vm.options.resolution.dpi

    val badgePaint = remember(density) {
        Paint().apply {
            isAntiAlias = true; color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER; textSize = with(density) { 13.sp.toPx() }
            isFakeBoldText = true
        }
    }
    val dimsPaint = remember(density) {
        Paint().apply {
            isAntiAlias = true; color = android.graphics.Color.WHITE
            textSize = with(density) { 12.sp.toPx() }
        }
    }

    fun transform(): ViewTransform? {
        val d = data ?: return null
        if (canvasSize.width == 0 || canvasSize.height == 0) return null
        return ViewTransform.fit(
            canvasSize.width.toFloat(), canvasSize.height.toFloat(),
            d.bitmap.width.toFloat(), d.bitmap.height.toFloat(),
            scale, offset.x, offset.y
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = "Page ${page + 1} of $pageCount",
                subtitle = "${pageCrops.size} crop${if (pageCrops.size == 1) "" else "s"} · pinch to zoom, two fingers to pan",
                onBack = vm::back,
                actions = {
                    IconButton(onClick = vm::undo, enabled = vm.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
                    IconButton(onClick = vm::redo, enabled = vm.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") }
                    IconButton(onClick = { scale = 1f; offset = Offset.Zero }) { Icon(Icons.Default.FitScreen, "Fit page to screen") }
                    IconButton(onClick = { confirmReset = true }, enabled = pageCrops.isNotEmpty()) {
                        Icon(Icons.Default.DeleteSweep, "Delete all crops on this page")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                actions = {
                    IconButton(onClick = { vm.openEditor(page - 1) }, enabled = page > 0) { Icon(Icons.Default.ChevronLeft, "Previous page") }
                    IconButton(onClick = { vm.openEditor(page + 1) }, enabled = page < pageCount - 1) { Icon(Icons.Default.ChevronRight, "Next page") }
                    IconButton(onClick = { active?.let { vm.deleteCrop(it.id) } }, enabled = active != null) { Icon(Icons.Default.Delete, "Delete crop") }
                    IconButton(onClick = { active?.let { vm.duplicateCrop(it.id) } }, enabled = active != null) { Icon(Icons.Default.ContentCopy, "Duplicate crop") }
                },
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        onClick = {
                            vm.activeId = null
                            vm.message = if (pageCrops.isEmpty()) "Draw a rectangle around a photo first"
                            else "${pageCrops.size} crop${if (pageCrops.size == 1) "" else "s"} saved for this page"
                        },
                        icon = { Icon(Icons.Default.Check, null) },
                        text = { Text("Save crop") }
                    )
                }
            )
        }
    ) { pad ->
        Box(
            Modifier.padding(pad).fillMaxSize().clipToBounds()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .onSizeChanged { canvasSize = it }
        ) {
            val d = data
            if (d == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                val image = remember(d) { d.bitmap.asImageBitmap() }
                Canvas(
                    Modifier.fillMaxSize().pointerInput(d, page) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val t0 = transform() ?: return@awaitEachGesture
                            val slopPx = 28.dp.toPx()

                            vm.beginEdit()

                            // 1) Did the finger land on an existing crop's handle (or the active crop's body)?
                            var handle = Handle.NONE
                            var targetId: String? = null
                            val candidates = vm.cropsOnPage(page)
                                .sortedByDescending { if (it.id == vm.activeId) Long.MAX_VALUE else it.order }
                            for (c in candidates) {
                                val h = CropGeometry.hitTest(
                                    t0.sx(c.rect.left), t0.sy(c.rect.top), t0.sx(c.rect.right), t0.sy(c.rect.bottom),
                                    down.position.x, down.position.y, slopPx
                                )
                                if (h == Handle.NONE) continue
                                if (h == Handle.BODY && c.id != vm.activeId) continue
                                handle = h; targetId = c.id
                                break
                            }

                            // 2) Otherwise start drawing a new crop (only inside the page).
                            var drawingId: String? = null
                            var anchorX = 0f
                            var anchorY = 0f
                            if (targetId == null) {
                                val nx = t0.nx(down.position.x)
                                val ny = t0.ny(down.position.y)
                                if (nx in 0f..1f && ny in 0f..1f) {
                                    anchorX = nx; anchorY = ny
                                    drawingId = vm.newCrop(page, nx, ny)
                                    targetId = drawingId
                                    handle = Handle.DRAW
                                } else {
                                    vm.activeId = null
                                }
                            } else {
                                vm.activeId = targetId
                                if (vm.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }

                            var multi = false
                            do {
                                val event = awaitPointerEvent()
                                val pressedCount = event.changes.count { it.pressed }
                                if (pressedCount >= 2) {
                                    // Pinch zoom + pan (cancels any single-finger crop gesture).
                                    multi = true
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    val c = event.calculateCentroid(useCurrent = true)
                                    val ns = (scale * zoom).coerceIn(1f, 10f)
                                    if (ns <= 1f) {
                                        scale = 1f; offset = Offset.Zero
                                    } else {
                                        val ratio = ns / scale
                                        offset = Offset(
                                            c.x - (c.x - offset.x) * ratio + pan.x,
                                            c.y - (c.y - offset.y) * ratio + pan.y
                                        )
                                        scale = ns
                                    }
                                    event.changes.forEach { it.consume() }
                                } else if (!multi && pressedCount == 1 && targetId != null) {
                                    val ch = event.changes.first { it.pressed }
                                    val t = transform() ?: return@awaitEachGesture
                                    val id = targetId ?: break
                                    val cur = vm.cropById(id)?.rect ?: break
                                    val nx = t.nx(ch.position.x)
                                    val ny = t.ny(ch.position.y)
                                    val newRect = when (handle) {
                                        Handle.DRAW -> CropGeometry.fromPoints(anchorX, anchorY, nx, ny)
                                        Handle.BODY -> {
                                            val delta = ch.position - ch.previousPosition
                                            CropGeometry.move(cur, delta.x / (t.baseW * t.scale), delta.y / (t.baseH * t.scale))
                                        }
                                        else -> CropGeometry.resize(cur, handle, nx, ny)
                                    }
                                    vm.liveUpdate(id, newRect)
                                    ch.consume()
                                }
                            } while (event.changes.any { it.pressed })

                            vm.commitEdit()

                            // A plain tap (no drag) on empty space selects the crop under the finger, if any.
                            val did = drawingId
                            if (did != null && vm.cropById(did) == null) {
                                val nx = t0.nx(down.position.x)
                                val ny = t0.ny(down.position.y)
                                vm.activeId = vm.cropsOnPage(page)
                                    .filter { nx in it.rect.left..it.rect.right && ny in it.rect.top..it.rect.bottom }
                                    .minByOrNull { it.rect.width * it.rect.height }?.id
                            }
                        }
                    }
                ) {
                    val t = transform() ?: return@Canvas
                    drawImage(
                        image,
                        dstOffset = IntOffset(t.sx(0f).roundToInt(), t.sy(0f).roundToInt()),
                        dstSize = IntSize((t.baseW * t.scale).roundToInt(), (t.baseH * t.scale).roundToInt()),
                        filterQuality = FilterQuality.Medium
                    )

                    pageCrops.forEachIndexed { index, c ->
                        val l = t.sx(c.rect.left); val tp = t.sy(c.rect.top)
                        val r = t.sx(c.rect.right); val b = t.sy(c.rect.bottom)
                        val isActive = c.id == vm.activeId
                        val color = if (isActive) ActiveColor else InactiveColor
                        if (isActive) drawRect(ActiveColor.copy(alpha = 0.15f), Offset(l, tp), Size(r - l, b - tp))
                        drawRect(
                            color, Offset(l, tp), Size(r - l, b - tp),
                            style = Stroke(width = (if (isActive) 2.5.dp else 1.5.dp).toPx())
                        )

                        // numbered badge
                        val badge = 22.dp.toPx()
                        drawRoundRect(color, Offset(l, tp), Size(badge, badge), CornerRadius(6.dp.toPx()))
                        drawIntoCanvas {
                            it.nativeCanvas.drawText("${index + 1}", l + badge / 2f, tp + badge / 2f + badgePaint.textSize / 3f, badgePaint)
                        }

                        if (isActive) {
                            val handles = listOf(
                                Offset(l, tp), Offset(r, tp), Offset(l, b), Offset(r, b),
                                Offset((l + r) / 2f, tp), Offset((l + r) / 2f, b),
                                Offset(l, (tp + b) / 2f), Offset(r, (tp + b) / 2f)
                            )
                            handles.forEach {
                                drawCircle(Color.White, 9.dp.toPx(), it)
                                drawCircle(ActiveColor, 9.dp.toPx(), it, style = Stroke(2.dp.toPx()))
                            }
                            // size label: pixel size this crop will have when exported at the chosen resolution
                            val s = CropGeometry.renderScale(c.rect, d.wPts, d.hPts, dpi)
                            val (pw, ph) = CropGeometry.outputSize(c.rect, d.wPts, d.hPts, s)
                            val label = "$pw × $ph px"
                            val tw = dimsPaint.measureText(label)
                            val pad = 6.dp.toPx()
                            val lx = l.coerceIn(0f, (size.width - tw - 2 * pad).coerceAtLeast(0f))
                            val ly = (b + 14.dp.toPx()).coerceAtMost(size.height - 28.dp.toPx())
                            drawRoundRect(Color.Black.copy(alpha = 0.75f), Offset(lx, ly), Size(tw + 2 * pad, 22.dp.toPx()), CornerRadius(6.dp.toPx()))
                            drawIntoCanvas { it.nativeCanvas.drawText(label, lx + pad, ly + 15.dp.toPx(), dimsPaint) }
                        }
                    }
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Delete all crops on this page?") },
            text = { Text("You can bring them back with Undo.") },
            confirmButton = { TextButton(onClick = { vm.resetPage(page); confirmReset = false }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } }
        )
    }
}
