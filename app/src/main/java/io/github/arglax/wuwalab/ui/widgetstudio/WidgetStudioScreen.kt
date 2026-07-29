package io.github.arglax.wuwalab.ui.widgetstudio

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.arglax.wuwalab.data.WIDGET_CUSTOM_UPLOAD_COST
import io.github.arglax.wuwalab.data.WIDGET_STUDIO_WELCOME_GRANT
import io.github.arglax.wuwalab.data.WidgetApplyResult
import io.github.arglax.wuwalab.data.WidgetImageProcessor
import io.github.arglax.wuwalab.data.WidgetStudioRepository
import io.github.arglax.wuwalab.data.WuwaRepository
import io.github.arglax.wuwalab.ui.components.GlassCard
import io.github.arglax.wuwalab.ui.components.HelpButton
import io.github.arglax.wuwalab.ui.theme.AmberGlow
import io.github.arglax.wuwalab.ui.theme.CoralGlow
import io.github.arglax.wuwalab.ui.theme.EmeraldGlow
import io.github.arglax.wuwalab.ui.theme.GlassBorderSoft
import io.github.arglax.wuwalab.ui.theme.TextMuted
import io.github.arglax.wuwalab.ui.theme.TextPrimary
import io.github.arglax.wuwalab.ui.theme.TextSecondary
import io.github.arglax.wuwalab.ui.theme.VioletGlow
import io.github.arglax.wuwalab.widget.WuwaWidget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

// The exact scrim the real widget paints, mirrored here so the preview is a
// preview and not an approximation: solid to 40%, a hard ramp from 40% to 60%,
// and absolutely nothing from 60% onwards.
private val ScrimDark = Color(0xE610241C)
private val WidgetScrimBrush = Brush.horizontalGradient(
    0.00f to ScrimDark,
    0.40f to ScrimDark,
    0.60f to Color.Transparent,
    1.00f to Color.Transparent
)
private val CompactScrim = Color(0xF010241C)

/**
 * Widget Studio - pick a photo, frame it, see exactly how it will look as both
 * a wide and a square widget, then pay 20 Argstrites to apply it.
 *
 * Nothing is charged until the player confirms, and a failed render refunds
 * itself, so the only way to spend Argstrites here is to actually get a
 * background out of it.
 */
@Composable
fun WidgetStudioScreen(
    studioRepo: WidgetStudioRepository,
    wuwaRepo: WuwaRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val argstrites by wuwaRepo.radiantAstriteFlow.collectAsState(initial = 0)
    val state by studioRepo.stateFlow.collectAsState()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    var showWelcome by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }

    var zoom by remember { mutableFloatStateOf(state.zoom) }
    var offsetX by remember { mutableFloatStateOf(state.offsetX) }
    var offsetY by remember { mutableFloatStateOf(state.offsetY) }

    // The one-time welcome gift. Granted on first arrival, explained straight
    // away so free currency never just appears without a reason.
    LaunchedEffect(Unit) {
        studioRepo.refresh()
        if (studioRepo.claimWelcomeGrantIfNeeded()) showWelcome = true
    }

    // Framing is persisted on a short debounce - dragging shouldn't hammer the disk.
    LaunchedEffect(zoom, offsetX, offsetY) {
        delay(250)
        studioRepo.setFraming(zoom, offsetX, offsetY)
    }

    val sourceBitmap: Bitmap? = remember(state.sourcePath, state.sourcePath?.let { File(it).lastModified() }) {
        WidgetImageProcessor.decodeSource(state)
    }
    val appliedBitmap: Bitmap? = remember(state.appliedPath, state.appliedPath?.let { File(it).lastModified() }) {
        WidgetImageProcessor.loadApplied(context)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val ok = studioRepo.setSource(uri)
            zoom = 1f; offsetX = 0f; offsetY = 0f
            messageIsError = !ok
            message = if (ok) "Photo loaded. Frame it below, then apply." else "That image could not be read. Try another one."
        }
    }

    if (showWelcome) {
        AlertDialog(
            onDismissRequest = { showWelcome = false },
            title = { Text("Here's " + WIDGET_STUDIO_WELCOME_GRANT + " Argstrites", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Welcome to the Widget Studio. Because this is your first visit, " +
                            WIDGET_STUDIO_WELCOME_GRANT + " Argstrites have been added to your balance - " +
                            "exactly enough for one custom widget background.",
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Why the gift? Custom backgrounds cost " + WIDGET_CUSTOM_UPLOAD_COST +
                            " Argstrites each, and earning that from Daily Sign-In alone would take two days " +
                            "before you could even try the feature. This is a one-time starter so you can use " +
                            "it right now. It will not be granted again.",
                        color = TextSecondary,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showWelcome = false }) { Text("Nice, thanks") } }
        )
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Apply this background?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "This will spend " + WIDGET_CUSTOM_UPLOAD_COST + " Argstrites out of your " +
                            argstrites + ".",
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Purchases are final - there are no refunds. You can re-frame and re-apply later, " +
                            "but each new apply costs " + WIDGET_CUSTOM_UPLOAD_COST + " Argstrites again.",
                        color = CoralGlow,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    scope.launch {
                        studioRepo.setFraming(zoom, offsetX, offsetY)
                        when (val result = studioRepo.purchaseAndApply()) {
                            is WidgetApplyResult.Success -> {
                                messageIsError = false
                                message = "Applied. " + result.remainingArgstrites + " Argstrites left."
                                WuwaWidget.updateAll(context)
                            }
                            is WidgetApplyResult.NotEnoughArgstrites -> {
                                messageIsError = true
                                message = "You need " + result.needed + " Argstrites but only have " + result.balance + "."
                            }
                            WidgetApplyResult.NoImageChosen -> {
                                messageIsError = true
                                message = "Choose a photo first."
                            }
                            WidgetApplyResult.RenderFailed -> {
                                messageIsError = true
                                message = "That image could not be processed, so nothing was charged."
                            }
                        }
                    }
                }) { Text("Spend " + WIDGET_CUSTOM_UPLOAD_COST) }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel") } }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Widget Studio",
                    fontWeight = FontWeight.Bold,
                    fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                HelpButton(
                    title = "Widget Studio Help",
                    body = "Pick any photo from your device, pinch or drag to frame it, and see exactly how " +
                        "it will look on your home screen as a wide widget and as a square one.\n\n" +
                        "Applying a custom background costs " + WIDGET_CUSTOM_UPLOAD_COST + " Argstrites. " +
                        "You are asked to confirm first, and nothing is charged if the image cannot be processed.\n\n" +
                        "Your photo never leaves your phone - it is copied into the app's own private storage and " +
                        "flattened into a single image the widget can draw."
                )
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), accent = VioletGlow) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Your Argstrites", color = TextSecondary, fontSize = MaterialTheme.typography.labelSmall.fontSize)
                        Text(
                            argstrites.toString(),
                            color = VioletGlow,
                            fontWeight = FontWeight.Bold,
                            fontSize = MaterialTheme.typography.headlineSmall.fontSize
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Custom background", color = TextSecondary, fontSize = MaterialTheme.typography.labelSmall.fontSize)
                        Text(
                            WIDGET_CUSTOM_UPLOAD_COST.toString() + " each",
                            color = AmberGlow,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        message?.let { text ->
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background((if (messageIsError) CoralGlow else EmeraldGlow).copy(alpha = 0.16f))
                        .border(1.dp, (if (messageIsError) CoralGlow else EmeraldGlow).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Text(
                        text,
                        color = if (messageIsError) CoralGlow else EmeraldGlow,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize
                    )
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), accent = AmberGlow) {
                Text("1. Choose a photo", fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { picker.launch("image/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGlow, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.sourcePath == null) "Upload a picture" else "Choose a different picture")
                }
            }
        }

        if (sourceBitmap != null) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), accent = VioletGlow) {
                    Text("2. Frame it", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(
                        "Pinch to zoom, drag to reposition, or use the sliders.",
                        color = TextMuted,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize
                    )
                    Spacer(Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, GlassBorderSoft, RoundedCornerShape(14.dp))
                            .clipToBounds()
                            .pointerInput(state.sourcePath) {
                                detectTransformGestures { _, pan, gestureZoom, _ ->
                                    zoom = (zoom * gestureZoom).coerceIn(1f, 4f)
                                    offsetX = (offsetX + pan.x / size.width).coerceIn(-1f, 1f)
                                    offsetY = (offsetY + pan.y / size.height).coerceIn(-1f, 1f)
                                }
                            }
                    ) {
                        FramedPhoto(sourceBitmap, zoom, offsetX, offsetY)
                    }

                    Spacer(Modifier.height(12.dp))
                    SliderRow("Zoom", (zoom * 100).roundToInt().toString() + "%", zoom, 1f, 4f) { zoom = it }
                    SliderRow("Horizontal", offsetLabel(offsetX), offsetX, -1f, 1f) { offsetX = it }
                    SliderRow("Vertical", offsetLabel(offsetY), offsetY, -1f, 1f) { offsetY = it }

                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { zoom = 1f; offsetX = 0f; offsetY = 0f },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Reset framing") }
                }
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), accent = EmeraldGlow) {
                    Text("3. How it will look", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(
                        "The dimming shown here is exactly what the real widget draws.",
                        color = TextMuted,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize
                    )
                    Spacer(Modifier.height(12.dp))

                    if (isLandscape) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.weight(2f)) { WidePreview(sourceBitmap, zoom, offsetX, offsetY) }
                            Box(Modifier.weight(1f)) { SquarePreview(sourceBitmap, zoom, offsetX, offsetY) }
                        }
                    } else {
                        WidePreview(sourceBitmap, zoom, offsetX, offsetY)
                        Spacer(Modifier.height(14.dp))
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Box(Modifier.fillMaxWidth(0.55f)) {
                                SquarePreview(sourceBitmap, zoom, offsetX, offsetY)
                            }
                        }
                    }
                }
            }

            item {
                val affordable = argstrites >= WIDGET_CUSTOM_UPLOAD_COST
                GlassCard(modifier = Modifier.fillMaxWidth(), accent = if (affordable) AmberGlow else CoralGlow) {
                    Text("4. Apply", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { showConfirm = true },
                        enabled = affordable,
                        colors = ButtonDefaults.buttonColors(containerColor = AmberGlow, contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (affordable) "Apply to widget - " + WIDGET_CUSTOM_UPLOAD_COST + " Argstrites"
                            else "Need " + (WIDGET_CUSTOM_UPLOAD_COST - argstrites) + " more Argstrites"
                        )
                    }
                    if (!affordable) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Claim your Daily Sign-In on the Dashboard to top up.",
                            color = CoralGlow,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize
                        )
                    }
                }
            }
        }

        if (appliedBitmap != null) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), accent = EmeraldGlow) {
                    Text("Currently on your home screen", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(14.dp))
                            .clipToBounds()
                    ) {
                        Image(
                            bitmap = appliedBitmap.asImageBitmap(),
                            contentDescription = "Applied widget background",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(Modifier.fillMaxSize().background(WidgetScrimBrush))
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            studioRepo.clearCustom()
                            scope.launch { WuwaWidget.updateAll(context) }
                            messageIsError = false
                            message = "Custom background removed. Reverted to the default artwork."
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralGlow),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Remove custom background") }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Removing this background doesn't refund it. If you apply it (or any photo) again " +
                            "afterwards, it's treated as a new upload and $WIDGET_CUSTOM_UPLOAD_COST Argstrites " +
                            "will be charged again to apply it.",
                        color = TextMuted,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize
                    )
                }
            }
        }

        if (state.sourcePath == null) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "No photo chosen yet. Upload one above to start framing it.",
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private fun offsetLabel(value: Float): String = (value * 100).roundToInt().toString() + "%"

@Composable
private fun SliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, color = TextSecondary, fontSize = MaterialTheme.typography.labelSmall.fontSize, modifier = Modifier.weight(1f))
            Text(valueLabel, color = TextMuted, fontSize = MaterialTheme.typography.labelSmall.fontSize)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = min..max,
            colors = SliderDefaults.colors(thumbColor = VioletGlow, activeTrackColor = VioletGlow),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** The framed photo, using the identical cover-then-zoom-then-shift maths as the renderer. */
@Composable
private fun FramedPhoto(bitmap: Bitmap, zoom: Float, offsetX: Float, offsetY: Float) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }
        val hPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx() }
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = zoom,
                    scaleY = zoom,
                    translationX = offsetX * wPx,
                    translationY = offsetY * hPx
                )
        )
    }
}

/** Wide widget preview: 16:9 with the real gradient scrim and sample values. */
@Composable
private fun WidePreview(bitmap: Bitmap, zoom: Float, offsetX: Float, offsetY: Float) {
    Column {
        Text("Rectangular widget (4x2)", color = TextSecondary, fontSize = MaterialTheme.typography.labelSmall.fontSize)
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, GlassBorderSoft, RoundedCornerShape(14.dp))
                .clipToBounds()
        ) {
            FramedPhoto(bitmap, zoom, offsetX, offsetY)
            Box(Modifier.fillMaxSize().background(WidgetScrimBrush))
            SampleWidgetText()
        }
    }
}

/**
 * Square widget preview. The real widget center-crops the same 16:9 image, so
 * this nests the full wide composition inside a clipped square rather than
 * re-framing the photo - what you see is what the home screen gets.
 */
@Composable
private fun SquarePreview(bitmap: Bitmap, zoom: Float, offsetX: Float, offsetY: Float) {
    Column {
        Text("Square widget (2x2)", color = TextSecondary, fontSize = MaterialTheme.typography.labelSmall.fontSize)
        Spacer(Modifier.height(6.dp))
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, GlassBorderSoft, RoundedCornerShape(14.dp))
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            val side = maxWidth
            Box(
                modifier = Modifier
                    .width(side * 16f / 9f)
                    .height(side)
                    .clipToBounds()
            ) {
                FramedPhoto(bitmap, zoom, offsetX, offsetY)
            }
            // Compact widgets use one even scrim so values stay readable
            // wherever they land - that is what is mirrored here.
            Box(Modifier.fillMaxSize().background(CompactScrim))
            SampleWidgetText()
        }
    }
}

@Composable
private fun SampleWidgetText() {
    Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        Text("Rover", color = Color.White, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelLarge.fontSize)
        Text("UL 80", color = Color(0xFFD4AF37), fontSize = MaterialTheme.typography.labelSmall.fontSize)
        Spacer(Modifier.height(6.dp))
        Text("240  Full", color = Color.White, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelMedium.fontSize)
        Text("120 crystals", color = Color(0xFF9FD8B8), fontSize = MaterialTheme.typography.labelSmall.fontSize)
    }
}