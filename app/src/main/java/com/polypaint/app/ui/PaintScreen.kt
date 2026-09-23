package com.polypaint.app.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush as BrushIcon
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.polypaint.app.io.ImportUtils
import com.polypaint.app.io.ModelExporter
import com.polypaint.app.model.ObjLoader
import com.polypaint.app.model.ObjMesh
import com.polypaint.app.model.PaintChannel
import com.polypaint.app.paint.Brush
import com.polypaint.app.paint.TextureLayerManager
import java.io.File

@Composable
fun PaintScreen() {
    val context = LocalContext.current

    var mesh by remember { mutableStateOf<ObjMesh?>(null) }
    var textureManager by remember { mutableStateOf(TextureLayerManager()) }
    var activeChannel by remember { mutableStateOf(PaintChannel.ALBEDO) }
    var mode by remember { mutableStateOf(ViewMode.VIEW) }
    var brush by remember { mutableStateOf(Brush()) }
    var paintVersion by remember { mutableIntStateOf(0) }
    var showWireframe by remember { mutableStateOf(true) }

    val glView = remember { Gl3DView(context) }

    // Keep the GLSurfaceView's render thread paused while the app is backgrounded.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glView.onResume()
                Lifecycle.Event.ON_PAUSE -> glView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun pushChannelToGl(channel: PaintChannel) {
        val bmp = textureManager.bitmapFor(channel)
        glView.queueEvent { glView.modelRenderer.uploadTexture(channel, bmp) }
    }

    fun loadMesh(objFile: File) {
        val result = ObjLoader.load(objFile)
        val newManager = TextureLayerManager()
        if (result.mesh.materials.size > 1) {
            Toast.makeText(
                context,
                "Multiple materials found - PolyPaint paints one unified texture set per model, so only the first material's maps were loaded.",
                Toast.LENGTH_LONG
            ).show()
        }
        result.mesh.materials.values.firstOrNull()?.let {
            newManager.loadFromMaterial(it, objFile.parentFile ?: context.cacheDir)
        }
        mesh = result.mesh
        textureManager = newManager
        glView.currentMesh = result.mesh
        glView.queueEvent {
            glView.modelRenderer.pendingMesh = result.mesh
            glView.modelRenderer.pendingTextures = newManager.allBitmaps()
        }
        if (result.warnings.isNotEmpty()) {
            Toast.makeText(context, result.warnings.first(), Toast.LENGTH_LONG).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val name = queryDisplayName(context, uri)
        val staged = ImportUtils.stageImport(context, uri, name)
        if (staged != null && staged.exists()) {
            loadMesh(staged)
        } else {
            Toast.makeText(context, "Couldn't find a .obj file in what you picked.", Toast.LENGTH_LONG).show()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val currentMesh = mesh
        if (currentMesh == null) {
            Toast.makeText(context, "Import a model first.", Toast.LENGTH_SHORT).show()
        } else {
            ModelExporter.export(context, currentMesh, textureManager, uri)
            Toast.makeText(context, "Exported.", Toast.LENGTH_SHORT).show()
        }
    }

    // Re-bind on every recomposition so the callback always closes over the
    // current activeChannel/brush/textureManager (cheap - just a field write).
    SideEffect {
        glView.onPaintHit = { u, v ->
            textureManager.paintAt(activeChannel, u, v, brush)
            paintVersion++
            pushChannelToGl(activeChannel)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("PolyPaint", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Row {
                IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Import .obj or .zip", tint = MaterialTheme.colorScheme.onBackground)
                }
                IconButton(onClick = { exportLauncher.launch("polypaint_export.zip") }) {
                    Icon(Icons.Filled.FileDownload, contentDescription = "Export", tint = MaterialTheme.colorScheme.onBackground)
                }
            }
        }

        // 3D view (top half).
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(factory = { glView }, modifier = Modifier.fillMaxSize())
        }

        // UV "plain sheet" view (bottom half) - same texture data, so it's
        // always in sync with the 3D view above.
        Box(modifier = Modifier.fillMaxWidth().weight(1f).background(Color(0xFF1B1D22))) {
            UvPaintCanvas(
                bitmap = textureManager.bitmapFor(activeChannel),
                paintVersion = paintVersion,
                mesh = mesh,
                mode = mode,
                showWireframe = showWireframe,
                onPaintPixel = { px, py ->
                    textureManager.paintAtPixel(activeChannel, px, py, brush)
                    paintVersion++
                    pushChannelToGl(activeChannel)
                },
                onStrokeEnd = {},
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == ViewMode.VIEW,
                    onClick = { mode = ViewMode.VIEW; glView.mode = ViewMode.VIEW },
                    label = { Text("View") },
                    leadingIcon = { Icon(Icons.Filled.PanTool, contentDescription = null) }
                )
                FilterChip(
                    selected = mode == ViewMode.PAINT,
                    onClick = { mode = ViewMode.PAINT; glView.mode = ViewMode.PAINT },
                    label = { Text("Paint") },
                    leadingIcon = { Icon(BrushIcon, contentDescription = null) }
                )
                AssistChip(
                    onClick = { showWireframe = !showWireframe },
                    label = { Text(if (showWireframe) "UVs: on" else "UVs: off") },
                    leadingIcon = { Icon(Icons.Filled.GridOn, contentDescription = null) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(PaintChannel.values().toList()) { channel ->
                    FilterChip(
                        selected = activeChannel == channel,
                        onClick = { activeChannel = channel },
                        label = { Text(channel.displayName) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (activeChannel.isScalar) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Value", modifier = Modifier.padding(end = 8.dp), color = MaterialTheme.colorScheme.onBackground)
                    Slider(
                        value = brush.scalarValue,
                        onValueChange = { brush = brush.copy(scalarValue = it) },
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                ColorSwatchRow(
                    selected = Color(brush.colorArgb),
                    onSelect = { c -> brush = brush.copy(colorArgb = c.toArgb()) }
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Size", modifier = Modifier.padding(end = 8.dp), color = MaterialTheme.colorScheme.onBackground)
                Slider(
                    value = brush.radiusPx,
                    valueRange = 4f..160f,
                    onValueChange = { brush = brush.copy(radiusPx = it) },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Opacity", modifier = Modifier.padding(end = 8.dp), color = MaterialTheme.colorScheme.onBackground)
                Slider(
                    value = brush.opacity,
                    onValueChange = { brush = brush.copy(opacity = it) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ColorSwatchRow(selected: Color, onSelect: (Color) -> Unit) {
    val swatches = listOf(
        Color.White, Color.Black, Color(0xFFB0B0B0),
        Color(0xFFD9C2A6), Color(0xFF8B5E3C), Color(0xFFE24B4B),
        Color(0xFF4BA3E2), Color(0xFF57C15E), Color(0xFFE2C23F)
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(swatches) { c ->
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(c)
                    .border(
                        width = if (c == selected) 3.dp else 1.dp,
                        color = if (c == selected) MaterialTheme.colorScheme.primary else Color.Gray,
                        shape = CircleShape
                    )
                    .clickable { onSelect(c) }
            )
        }
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
    }
} catch (e: Exception) {
    null
}
