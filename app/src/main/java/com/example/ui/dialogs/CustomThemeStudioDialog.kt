package com.example.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*

private enum class ThemeColorChannel(val tagSuffix: String) {
    BACKGROUND("bg"),
    TEXT("text"),
    NUMBERS("numbers")
}

@Composable
fun CustomThemeStudioDialog(
    initialBgArgb: Long,
    initialTextArgb: Long,
    initialNumberArgb: Long,
    activeTheme: ClockVisualTheme,
    onDismiss: () -> Unit,
    onApplyColors: (bgArgb: Long, textArgb: Long, numberArgb: Long) -> Unit
) {
    var bgColor by remember { mutableStateOf(Color((initialBgArgb.toInt()) or -0x1000000)) }
    var textColor by remember { mutableStateOf(Color((initialTextArgb.toInt()) or -0x1000000)) }
    var numberColor by remember { mutableStateOf(Color((initialNumberArgb.toInt()) or -0x1000000)) }

    var selectedChannel by remember { mutableStateOf(ThemeColorChannel.NUMBERS) }

    val currentChannelColor = when (selectedChannel) {
        ThemeColorChannel.BACKGROUND -> bgColor
        ThemeColorChannel.TEXT -> textColor
        ThemeColorChannel.NUMBERS -> numberColor
    }

    val initialHsv = remember(selectedChannel) { colorToHsv(currentChannelColor) }
    var hue by remember(selectedChannel) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(selectedChannel) { mutableFloatStateOf(initialHsv[1]) }
    var value by remember(selectedChannel) { mutableFloatStateOf(initialHsv[2]) }
    var hexInput by remember(selectedChannel) { mutableStateOf(colorToHex(currentChannelColor)) }
    var hexError by remember(selectedChannel) { mutableStateOf(false) }

    fun updateActiveChannelColor(newColor: Color, updateHsvState: Boolean, updateHexField: Boolean) {
        when (selectedChannel) {
            ThemeColorChannel.BACKGROUND -> bgColor = newColor
            ThemeColorChannel.TEXT -> textColor = newColor
            ThemeColorChannel.NUMBERS -> numberColor = newColor
        }
        if (updateHsvState) {
            val hsv = colorToHsv(newColor)
            hue = hsv[0]
            saturation = hsv[1]
            value = hsv[2]
        }
        if (updateHexField) {
            hexInput = colorToHex(newColor)
            hexError = false
        }
    }

    val previewTheme = remember(bgColor, textColor, numberColor) {
        buildCustomVisualTheme(
            bgArgb = colorToOpaqueArgbLong(bgColor),
            textArgb = colorToOpaqueArgbLong(textColor),
            numberArgb = colorToOpaqueArgbLong(numberColor)
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = activeTheme.bgEnd),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, activeTheme.dividerColor),
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth(0.94f)
            .imePadding()
            .padding(vertical = 12.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = activeTheme.accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.custom_theme_dialog_title),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = activeTheme.textPrimary
                        )
                        Text(
                            text = "Style Background, Text & Numbers independently",
                            fontSize = 11.sp,
                            color = activeTheme.textSecondary
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Custom Color Studio",
                        tint = activeTheme.textSecondary
                    )
                }
            }

            // Live Miniature Clock + World Clock Preview
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, previewTheme.dividerColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(previewTheme.bgStart, previewTheme.bgEnd)))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "LIVE PREVIEW",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = previewTheme.accentColor,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "12:45",
                                fontSize = 34.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = previewTheme.numberColor
                            )
                            Text(
                                text = "MONDAY, OCT 26",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = previewTheme.textPrimary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = previewTheme.cardBackground,
                            border = BorderStroke(1.dp, previewTheme.dividerColor.copy(alpha = 0.7f))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = "TOKYO • +9h",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = previewTheme.textPrimary
                                )
                                Text(
                                    text = "01:45 AM",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = previewTheme.numberColor
                                )
                                Text(
                                    text = "Tomorrow",
                                    fontSize = 9.sp,
                                    color = previewTheme.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            // 3 Channel Selector Tabs (Background, Text, Numbers)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple(ThemeColorChannel.BACKGROUND, stringResource(R.string.channel_background), bgColor),
                    Triple(ThemeColorChannel.TEXT, stringResource(R.string.channel_text), textColor),
                    Triple(ThemeColorChannel.NUMBERS, stringResource(R.string.channel_numbers), numberColor)
                ).forEach { (channel, label, swatch) ->
                    val isSelected = selectedChannel == channel
                    Surface(
                        onClick = { selectedChannel = channel },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) activeTheme.accentColor.copy(alpha = 0.22f) else activeTheme.cardBackground,
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) activeTheme.accentColor else activeTheme.dividerColor
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 52.dp)
                            .testTag("color_channel_${channel.tagSuffix}")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(swatch)
                                        .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                                )
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = activeTheme.textPrimary,
                                    maxLines = 1
                                )
                            }
                            Text(
                                text = colorToHex(swatch),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = activeTheme.textSecondary
                            )
                        }
                    }
                }
            }

            // 2D HSV Saturation / Brightness Pad
            HsvSaturationValuePad(
                hue = hue,
                saturation = saturation,
                value = value,
                onSatValChanged = { newS, newV ->
                    saturation = newS
                    value = newV
                    val updated = hsvToColor(hue, newS, newV)
                    updateActiveChannelColor(updated, updateHsvState = false, updateHexField = true)
                }
            )

            // Hue Spectrum Slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "HUE SPECTRUM",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = activeTheme.textSecondary,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "${hue.toInt()}°",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = activeTheme.accentColor
                    )
                }
                HueSpectrumBar(
                    hue = hue,
                    onHueChanged = { newHue ->
                        hue = newHue
                        val updated = hsvToColor(newHue, saturation, value)
                        updateActiveChannelColor(updated, updateHsvState = false, updateHexField = true)
                    }
                )
            }

            // Hex Input + Quick Swatches
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { raw ->
                        val filtered = raw.uppercase().filter { c ->
                            c == '#' || c in '0'..'9' || c in 'A'..'F'
                        }.take(7)
                        hexInput = filtered
                        val parsed = parseHexColorOrNull(filtered)
                        if (parsed != null) {
                            hexError = false
                            updateActiveChannelColor(parsed, updateHsvState = true, updateHexField = false)
                        } else {
                            hexError = filtered.removePrefix("#").length != 6
                        }
                    },
                    label = { Text("Hex (#RRGGBB)", fontSize = 11.sp) },
                    isError = hexError,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = activeTheme.textPrimary
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = activeTheme.accentColor,
                        unfocusedBorderColor = activeTheme.dividerColor,
                        focusedLabelColor = activeTheme.accentColor,
                        unfocusedLabelColor = activeTheme.textSecondary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("hex_color_input")
                )

                val quickSwatches = listOf(
                    Color(0xFF070B14),
                    Color(0xFFFFFFFF),
                    Color(0xFF38BDF8),
                    Color(0xFF00F5D4),
                    Color(0xFFFBBF24),
                    Color(0xFFFB7185)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    quickSwatches.forEach { swatch ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .border(1.dp, activeTheme.dividerColor, CircleShape)
                                .clickable {
                                    updateActiveChannelColor(swatch, updateHsvState = true, updateHexField = true)
                                }
                        )
                    }
                }
            }

            // Seed from any Preset Helper
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "LOAD BASE COLORS FROM PRESET",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = activeTheme.textSecondary,
                    letterSpacing = 0.8.sp
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AllPresetThemes, key = { it.name }) { preset ->
                        Surface(
                            onClick = {
                                bgColor = preset.theme.bgStart
                                textColor = preset.theme.textPrimary
                                numberColor = preset.theme.numberColor
                                val activeAfter = when (selectedChannel) {
                                    ThemeColorChannel.BACKGROUND -> bgColor
                                    ThemeColorChannel.TEXT -> textColor
                                    ThemeColorChannel.NUMBERS -> numberColor
                                }
                                val hsv = colorToHsv(activeAfter)
                                hue = hsv[0]
                                saturation = hsv[1]
                                value = hsv[2]
                                hexInput = colorToHex(activeAfter)
                                hexError = false
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = activeTheme.cardBackground,
                            border = BorderStroke(1.dp, activeTheme.dividerColor)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(preset.theme.numberColor)
                                )
                                Text(
                                    text = preset.name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = activeTheme.textPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Confirm / Cancel Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("CANCEL", color = activeTheme.textSecondary, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onApplyColors(
                            colorToOpaqueArgbLong(bgColor),
                            colorToOpaqueArgbLong(textColor),
                            colorToOpaqueArgbLong(numberColor)
                        )
                    },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("apply_custom_theme_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = activeTheme.accentColor,
                        contentColor = activeTheme.onAccentColor
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("APPLY CUSTOM COLORS", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun HsvSaturationValuePad(
    hue: Float,
    saturation: Float,
    value: Float,
    onSatValChanged: (Float, Float) -> Unit
) {
    val pureHueColor = remember(hue) { hsvToColor(hue, 1f, 1f) }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .testTag("hsv_color_pad")
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val s = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    val v = (1f - (offset.y / size.height.toFloat())).coerceIn(0f, 1f)
                    onSatValChanged(s, v)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val s = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    val v = (1f - (change.position.y / size.height.toFloat())).coerceIn(0f, 1f)
                    onSatValChanged(s, v)
                }
            }
    ) {
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.White, pureHueColor)
            )
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black)
            )
        )

        val thumbX = saturation.coerceIn(0f, 1f) * size.width
        val thumbY = (1f - value.coerceIn(0f, 1f)) * size.height
        val center = Offset(thumbX, thumbY)

        drawCircle(
            color = Color.Black.copy(alpha = 0.65f),
            radius = 11.dp.toPx(),
            center = center,
            style = Stroke(width = 3.dp.toPx())
        )
        drawCircle(
            color = Color.White,
            radius = 9.dp.toPx(),
            center = center,
            style = Stroke(width = 2.5.dp.toPx())
        )
    }
}

@Composable
private fun HueSpectrumBar(
    hue: Float,
    onHueChanged: (Float) -> Unit
) {
    val spectrumColors = remember {
        listOf(
            Color(0xFFFF0000),
            Color(0xFFFFFF00),
            Color(0xFF00FF00),
            Color(0xFF00FFFF),
            Color(0xFF0000FF),
            Color(0xFFFF00FF),
            Color(0xFFFF0000)
        )
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .testTag("hue_slider")
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newH = ((offset.x / size.width.toFloat()) * 360f).coerceIn(0f, 360f)
                    onHueChanged(newH)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val newH = ((change.position.x / size.width.toFloat()) * 360f).coerceIn(0f, 360f)
                    onHueChanged(newH)
                }
            }
    ) {
        drawRect(brush = Brush.horizontalGradient(spectrumColors))
        val markerX = (hue.coerceIn(0f, 360f) / 360f) * size.width
        val center = Offset(markerX, size.height / 2f)
        drawCircle(
            color = Color.Black.copy(alpha = 0.6f),
            radius = 11.dp.toPx(),
            center = center,
            style = Stroke(width = 3.dp.toPx())
        )
        drawCircle(
            color = Color.White,
            radius = 9.dp.toPx(),
            center = center,
            style = Stroke(width = 2.5.dp.toPx())
        )
    }
}
