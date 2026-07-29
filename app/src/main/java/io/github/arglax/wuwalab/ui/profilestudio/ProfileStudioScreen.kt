package io.github.arglax.wuwalab.ui.profilestudio

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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.arglax.wuwalab.data.PROFILE_CUSTOM_UPLOAD_COST
import io.github.arglax.wuwalab.data.ProfileApplyResult
import io.github.arglax.wuwalab.data.ProfileImageProcessor
import io.github.arglax.wuwalab.data.ProfileStudioRepository
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

/**
 * Profile Studio - the square sibling of the Widget Studio. Pick a photo,
 * frame it into a circle preview matching the profile header, and apply it
 * for [PROFILE_CUSTOM_UPLOAD_COST] Argstrites.
 *
 * Re-framing (zoom/position only) the SAME photo and re-applying is FREE -
 * only a genuinely new upload costs Argstrites again. See
 * [ProfileStudioRepository.applyCurrentFraming] for the bookkeeping.
 */
@Composable
fun ProfileStudioScreen(
    studioRepo: ProfileStudioRepository,
    wuwaRepo: WuwaRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val argstrites by wuwaRepo.radiantAstriteFlow.collectAsState(initial = 0)
    val state by studioRepo.stateFlow.collectAsState()

    var showConfirm by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }

    var zoom by remember { mutableFloatStateOf(state.zoom) }
    var offsetX by remember { mutableFloatStateOf(state.offsetX) }
    var offsetY by remember { mutableFloatStateOf(state.offsetY) }

    LaunchedEffect(Unit) { studioRepo.refresh() }

    LaunchedEffect(zoom, offsetX, offsetY) {
        delay(250)
        studioRepo.setFraming(zoom, offsetX, offsetY)
    }

    val sourceBitmap: Bitmap? = remember(state.sourcePath, state.sourcePath?.let { File(it).lastModified() }) {
        ProfileImageProcessor.decodeSource(state)
    }
    val appliedBitmap: Bitmap? = remember(state.appliedPath, state.appliedPath?.let { File(it).lastModified() }) {
        ProfileImageProcessor.loadApplied(context)
    }
    val isFreeReapply = remember(state.sourcePath, state.purchasedSourcePath) { studioRepo.isCurrentSourceAlreadyPurchased() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val ok = studioRepo.setSource(uri)
            zoom = 1f; offsetX = 0f; offsetY = 0f
            messageIsError = !ok
            message = if (ok) "Photo loaded. Frame it below, then apply." else "That image could not be read. Try another one (PNG, JPG, or JPEG)."
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(if (isFreeReapply) "Re-apply this framing?" else "Apply this profile picture?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    if (isFreeReapply) {
                        Text(
                            "This is the same photo you already applied - just repositioned. " +
                                "Re-framing and re-applying the SAME photo is free.",
                            color = TextPrimary
                        )
                    } else {
                        Text(
                            "This will spend $PROFILE_CUSTOM_UPLOAD_COST Argstrites out of your $argstrites.",
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Purchases are final. But once you've applied a photo, you can re-frame and " +
                                "re-apply that SAME photo as many times as you like for free - only a new upload costs again.",
                            color = CoralGlow,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    scope.launch {
                        studioRepo.setFraming(zoom, offsetX, offsetY)
                        when (val result = studioRepo.applyCurrentFraming()) {
                            is ProfileApplyResult.Success -> {
                                messageIsError = false
                                message = if (result.wasFree) {
                                    "Re-applied for free."
                                } else {
                                    "Applied. ${result.remainingArgstrites} Argstrites left."
                                }
                            }
                            is ProfileApplyResult.NotEnoughArgstrites -> {
                                messageIsError = true
                                message = "You need ${result.needed} Argstrites but only have ${result.balance}."
                            }
                            ProfileApplyResult.NoImageChosen -> {
                                messageIsError = true
                                message = "Choose a photo first."
                            }
                            ProfileApplyResult.RenderFailed -> {
                                messageIsError = true
                                message = "That image could not be processed, so nothing was charged."
                            }
                        }
                    }
                }) { Text(if (isFreeReapply) "Re-apply free" else "Spend $PROFILE_CUSTOM_UPLOAD_COST") }
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
                    "Profile Studio",
                    fontWeight = FontWeight.Bold,
                    fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                HelpButton(
                    title = "Profile Studio Help",
                    body = "Pick any photo (PNG, JPG, or JPEG) from your device, pinch or drag to frame it into a " +
                        "circle, and see exactly how it will look in your profile header.\n\n" +
                        "Applying a NEW photo costs $PROFILE_CUSTOM_UPLOAD_COST Argstrites. But once it's applied, " +
                        "re-framing and re-applying that SAME photo again is free - you're only ever charged for " +
                        "a genuinely different upload.\n\n" +
                        "Your photo never leaves your phone."
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
                        Text("New photo", color = TextSecondary, fontSize = MaterialTheme.typography.labelSmall.fontSize)
                        Text("$PROFILE_CUSTOM_UPLOAD_COST each", color = AmberGlow, fontWeight = FontWeight.Bold)
                        Text("Re-frame same photo: free", color = EmeraldGlow, fontSize = MaterialTheme.typography.labelSmall.fontSize)
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
                    Text(text, color = if (messageIsError) CoralGlow else EmeraldGlow, fontSize = MaterialTheme.typography.labelSmall.fontSize)
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
                            .fillMaxWidth(0.6f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .border(1.dp, GlassBorderSoft, CircleShape)
                            .clipToBounds()
                            .pointerInput(state.sourcePath) {
                                detectTransformGestures { _, pan, gestureZoom, _ ->
                                    zoom = (zoom * gestureZoom).coerceIn(1f, 4f)
                                    offsetX = (offsetX + pan.x / size.width).coerceIn(-1f, 1f)
                                    offsetY = (offsetY + pan.y / size.height).coerceIn(-1f, 1f)
                                }
                            }
                    ) {
                        FramedSquarePhoto(sourceBitmap, zoom, offsetX, offsetY)
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
                val affordable = isFreeReapply || argstrites >= PROFILE_CUSTOM_UPLOAD_COST
                GlassCard(modifier = Modifier.fillMaxWidth(), accent = if (affordable) AmberGlow else CoralGlow) {
                    Text("3. Apply", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { showConfirm = true },
                        enabled = affordable,
                        colors = ButtonDefaults.buttonColors(containerColor = AmberGlow, contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (isFreeReapply) "Re-apply framing - free"
                            else if (affordable) "Apply to profile - $PROFILE_CUSTOM_UPLOAD_COST Argstrites"
                            else "Need ${PROFILE_CUSTOM_UPLOAD_COST - argstrites} more Argstrites"
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
                    Text("Currently on your profile", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(10.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Image(
                            bitmap = appliedBitmap.asImageBitmap(),
                            contentDescription = "Applied profile picture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .border(1.dp, GlassBorderSoft, CircleShape)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                studioRepo.clearCustom()
                                messageIsError = false
                                message = "Custom picture removed. Reverted to a bundled/shop avatar."
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralGlow),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Remove custom picture") }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Removing clears this photo's paid-for status. If you add it (or any photo) again " +
                            "afterwards, it counts as a new upload and $PROFILE_CUSTOM_UPLOAD_COST Argstrites " +
                            "may be charged again to apply it.",
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

@Composable
private fun FramedSquarePhoto(bitmap: Bitmap, zoom: Float, offsetX: Float, offsetY: Float) {
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