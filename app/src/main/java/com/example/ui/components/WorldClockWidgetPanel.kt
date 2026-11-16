package com.example.ui.components

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ClockSettings
import com.example.data.FriendClock
import com.example.ui.ClockViewModel
import com.example.ui.theme.ChronometerDisplayFont
import com.example.ui.theme.ChronometerMonoFont
import com.example.ui.theme.ClockVisualTheme
import com.example.util.TimeAndAlertUtils
import com.example.widget.WorldClockWidgetConfigureActivity
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WorldClockWidgetPanel(
    friendClocks: List<FriendClock>,
    currentLocalTime: ZonedDateTime,
    settings: ClockSettings,
    activeTheme: ClockVisualTheme,
    viewModel: ClockViewModel,
    onAddCityClicked: () -> Unit,
    modifier: Modifier = Modifier,
    columnsInGrid: Int = 2,
    isHeroAdjacent: Boolean = true,
    fillContainer: Boolean = false,
    dynamicRowSpacing: androidx.compose.ui.unit.Dp = 10.dp,
    dynamicColSpacing: androidx.compose.ui.unit.Dp = 10.dp
) {
    val context = LocalContext.current
    var showConfigDrawer by rememberSaveable { mutableStateOf(false) }

    val sortedClocks = remember(friendClocks, settings.clockSortOrder, currentLocalTime.minute) {
        TimeAndAlertUtils.getSortedFriendClocks(friendClocks, settings.clockSortOrder, currentLocalTime)
    }

    val displayedClocks = remember(sortedClocks, isHeroAdjacent) {
        if (isHeroAdjacent) {
            val pinned = sortedClocks.filter { it.showInWidget }
            if (pinned.isNotEmpty()) pinned else sortedClocks
        } else {
            sortedClocks
        }
    }

    Card(
        modifier = if (fillContainer) modifier.fillMaxWidth().fillMaxHeight() else modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = activeTheme.cardBackground),
        border = hdBorderStroke(
            accentColor = activeTheme.accentColor,
            dividerColor = activeTheme.dividerColor,
            width = 1.1.dp
        )
    ) {
        Column(
            modifier = if (fillContainer) Modifier.fillMaxSize().padding(14.dp) else Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Title, Count, Configure Gear & + Add City
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = activeTheme.accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.world_clock_section_title),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                            color = activeTheme.accentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${displayedClocks.size} of ${friendClocks.size} cities active",
                            fontSize = 10.sp,
                            fontFamily = ChronometerMonoFont,
                            color = activeTheme.textSecondary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { showConfigDrawer = !showConfigDrawer },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("configure_world_widget_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = stringResource(R.string.cd_configure_world_widget),
                            tint = if (showConfigDrawer) activeTheme.accentColor else activeTheme.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Button(
                        onClick = onAddCityClicked,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = activeTheme.accentColor,
                            contentColor = activeTheme.onAccentColor
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag(if (isHeroAdjacent) "add_friend_floating_key" else "add_timezone_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.cd_add_city),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.world_clock_add_city),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // Expandable Inline Widget Configuration Panel
            AnimatedVisibility(visible = showConfigDrawer) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = activeTheme.bgEnd,
                    border = BorderStroke(1.dp, activeTheme.dividerColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "WIDGET LAYOUT & DISPLAY OPTIONS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = activeTheme.accentColor,
                            letterSpacing = 0.8.sp
                        )

                        // Layout Mode Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "SIDE_BY_SIDE" to "Beside Main",
                                "COMPACT_STRIP" to "Strip",
                                "GRID" to "Grid"
                            ).forEach { (layoutKey, label) ->
                                val selected = settings.worldClockWidgetLayout == layoutKey
                                Surface(
                                    onClick = { viewModel.updateWorldClockWidgetLayout(layoutKey) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) activeTheme.accentColor else activeTheme.cardBackground,
                                    border = BorderStroke(1.dp, if (selected) activeTheme.accentColor else activeTheme.dividerColor),
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 40.dp)
                                        .testTag("widget_layout_$layoutKey")
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) activeTheme.onAccentColor else activeTheme.textPrimary
                                        )
                                    }
                                }
                            }
                        }

                        // Metadata Chips (UTC Offset, Date, Seconds, Show Alongside Main)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = settings.showWorldClockBesideMain,
                                    onClick = { viewModel.toggleWorldClockBesideMain(!settings.showWorldClockBesideMain) },
                                    label = { Text("Show Next to Main Clock", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = activeTheme.accentColor,
                                        selectedLabelColor = activeTheme.onAccentColor,
                                        containerColor = activeTheme.cardBackground,
                                        labelColor = activeTheme.textPrimary
                                    )
                                )
                            }
                            item {
                                FilterChip(
                                    selected = settings.worldClockShowOffset,
                                    onClick = { viewModel.toggleWorldClockShowOffset(!settings.worldClockShowOffset) },
                                    label = { Text("UTC Offset", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = activeTheme.accentColor,
                                        selectedLabelColor = activeTheme.onAccentColor,
                                        containerColor = activeTheme.cardBackground,
                                        labelColor = activeTheme.textPrimary
                                    )
                                )
                            }
                            item {
                                FilterChip(
                                    selected = settings.worldClockShowDate,
                                    onClick = { viewModel.toggleWorldClockShowDate(!settings.worldClockShowDate) },
                                    label = { Text("Date", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = activeTheme.accentColor,
                                        selectedLabelColor = activeTheme.onAccentColor,
                                        containerColor = activeTheme.cardBackground,
                                        labelColor = activeTheme.textPrimary
                                    )
                                )
                            }
                            item {
                                FilterChip(
                                    selected = settings.worldClockShowSeconds,
                                    onClick = { viewModel.toggleWorldClockShowSeconds(!settings.worldClockShowSeconds) },
                                    label = { Text("Seconds", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = activeTheme.accentColor,
                                        selectedLabelColor = activeTheme.onAccentColor,
                                        containerColor = activeTheme.cardBackground,
                                        labelColor = activeTheme.textPrimary
                                    )
                                )
                            }
                        }

                        // Sort Order + Home Screen Widget Launcher
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ClockSortSelector(
                                currentSortOrder = settings.clockSortOrder,
                                activeTheme = activeTheme,
                                onSortSelected = { viewModel.updateClockSortOrder(it) }
                            )

                            OutlinedButton(
                                onClick = {
                                    context.startActivity(
                                        Intent(context, WorldClockWidgetConfigureActivity::class.java)
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, activeTheme.accentColor),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 40.dp)
                                    .testTag("pin_home_widget_config_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Widgets,
                                    contentDescription = null,
                                    tint = activeTheme.accentColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Home Widget",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = activeTheme.accentColor
                                )
                            }
                        }
                    }
                }
            }

            // World Cities Content
            if (displayedClocks.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = activeTheme.bgEnd,
                    border = BorderStroke(1.dp, activeTheme.dividerColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = activeTheme.textSecondary,
                            modifier = Modifier.size(26.dp)
                        )
                        Text(
                            text = "No world cities added yet. Tap '+ Add City' to track multiple global timezones alongside your main clock.",
                            fontSize = 12.sp,
                            color = activeTheme.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (settings.worldClockWidgetLayout == "COMPACT_STRIP" && isHeroAdjacent) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(displayedClocks, key = { it.id }) { city ->
                        CompactCityRibbonCard(
                            friendClock = city,
                            currentTime = currentLocalTime,
                            settings = settings,
                            activeTheme = activeTheme,
                            onTogglePin = { viewModel.toggleFriendShowInWidget(city) },
                            onDelete = { viewModel.removeFriend(city) }
                        )
                    }
                }
            } else {
                val effectiveCols = columnsInGrid.coerceAtLeast(1)
                val chunkedRows = remember(displayedClocks, effectiveCols) {
                    displayedClocks.chunked(effectiveCols)
                }

                Box(
                    modifier = if (fillContainer) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth()
                ) {
                    val useElasticHeight = fillContainer && chunkedRows.size <= 3

                    Column(
                        modifier = if (fillContainer) {
                            if (useElasticHeight) Modifier.fillMaxSize() else Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        } else {
                            Modifier.fillMaxWidth()
                        },
                        verticalArrangement = if (useElasticHeight) {
                            Arrangement.SpaceBetween
                        } else {
                            Arrangement.spacedBy(dynamicRowSpacing)
                        }
                    ) {
                        chunkedRows.forEach { rowCities ->
                            Row(
                                modifier = if (useElasticHeight) {
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                } else {
                                    Modifier.fillMaxWidth()
                                },
                                horizontalArrangement = Arrangement.spacedBy(dynamicColSpacing)
                            ) {
                                rowCities.forEach { city ->
                                    Box(
                                        modifier = if (useElasticHeight) {
                                            Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                        } else {
                                            Modifier.weight(1f)
                                        }
                                    ) {
                                        FriendLocalClockItem(
                                            friendClock = city,
                                            currentTime = currentLocalTime,
                                            use24Hour = settings.digital24HourFormat,
                                            activeTheme = activeTheme,
                                            showOffset = settings.worldClockShowOffset,
                                            showDate = settings.worldClockShowDate,
                                            showSeconds = settings.worldClockShowSeconds,
                                            onTogglePin = { viewModel.toggleFriendShowInWidget(city) },
                                            onDelete = { viewModel.removeFriend(city) },
                                            isExpandedLayout = fillContainer
                                        )
                                    }
                                }
                                repeat(effectiveCols - rowCities.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactCityRibbonCard(
    friendClock: FriendClock,
    currentTime: ZonedDateTime,
    settings: ClockSettings,
    activeTheme: ClockVisualTheme,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val zone = remember(friendClock.timezoneId) { TimeAndAlertUtils.safeZoneId(friendClock.timezoneId) }
    val cityTime = currentTime.withZoneSameInstant(zone)
    val pattern = when {
        settings.digital24HourFormat && settings.worldClockShowSeconds -> "HH:mm:ss"
        settings.digital24HourFormat -> "HH:mm"
        settings.worldClockShowSeconds -> "hh:mm:ss a"
        else -> "hh:mm a"
    }
    val formatter = remember(pattern) { DateTimeFormatter.ofPattern(pattern, Locale.US) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US) }
    val compactOffset = TimeAndAlertUtils.formatCompactRelativeOffset(currentTime, friendClock.timezoneId)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = activeTheme.bgEnd,
        border = hdBorderStroke(
            accentColor = activeTheme.accentColor,
            dividerColor = activeTheme.dividerColor,
            width = 1.dp
        ),
        modifier = Modifier
            .widthIn(min = 148.dp, max = 196.dp)
            .testTag("friend_clock_widget_${friendClock.id}")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = cityTitleWithWeather(friendClock),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = activeTheme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (settings.worldClockShowOffset) {
                    Text(
                        text = compactOffset,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ChronometerMonoFont,
                        color = activeTheme.accentColor
                    )
                }
            }

            Text(
                text = cityTime.format(formatter),
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = ChronometerDisplayFont,
                style = TextStyle(fontFeatureSettings = "tnum"),
                color = activeTheme.numberColor,
                maxLines = 1
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (settings.worldClockShowDate) cityTime.format(dateFormatter) else friendClock.friendName,
                    fontSize = 10.sp,
                    color = activeTheme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("delete_friend_${friendClock.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_remove_city),
                        tint = activeTheme.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FriendLocalClockItem(
    friendClock: FriendClock,
    currentTime: ZonedDateTime,
    use24Hour: Boolean,
    activeTheme: ClockVisualTheme,
    showOffset: Boolean = true,
    showDate: Boolean = true,
    showSeconds: Boolean = false,
    onTogglePin: (() -> Unit)? = null,
    onDelete: () -> Unit,
    isExpandedLayout: Boolean = false
) {
    val friendZone = remember(friendClock.timezoneId) {
        TimeAndAlertUtils.safeZoneId(friendClock.timezoneId)
    }

    val friendTime = currentTime.withZoneSameInstant(friendZone)
    val hhPattern = if (use24Hour) "HH" else "hh"
    val hhFormatter = remember(use24Hour) { DateTimeFormatter.ofPattern(hhPattern, Locale.US) }
    val mmFormatter = remember { DateTimeFormatter.ofPattern("mm", Locale.US) }
    val ssFormatter = remember { DateTimeFormatter.ofPattern("ss", Locale.US) }
    val amPmFormatter = remember { DateTimeFormatter.ofPattern("a", Locale.US) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US) }

    val amPmString = friendTime.format(amPmFormatter)
    val offsetLabel = TimeAndAlertUtils.formatRelativeOffset(currentTime, friendClock.timezoneId)

    Card(
        modifier = (if (isExpandedLayout) Modifier.fillMaxWidth().fillMaxHeight() else Modifier.fillMaxWidth())
            .clip(RoundedCornerShape(16.dp))
            .testTag("friend_clock_widget_${friendClock.id}"),
        colors = CardDefaults.cardColors(
            containerColor = activeTheme.bgEnd
        ),
        border = hdBorderStroke(
            accentColor = activeTheme.accentColor,
            dividerColor = activeTheme.dividerColor,
            width = 1.dp,
            highlighted = friendClock.showInWidget
        )
    ) {
        Column(
            modifier = if (isExpandedLayout) {
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            } else {
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            },
            verticalArrangement = if (isExpandedLayout) Arrangement.SpaceBetween else Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(26.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (friendClock.showInWidget) activeTheme.accentColor
                                else activeTheme.dividerColor
                            )
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = cityTitleWithWeather(friendClock),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = activeTheme.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (friendClock.friendName.isNotBlank() && friendClock.friendName != friendClock.cityLabel) {
                                "${friendClock.friendName} • ${friendClock.timezoneId}"
                            } else {
                                friendClock.timezoneId
                            },
                            fontSize = 10.sp,
                            fontFamily = ChronometerMonoFont,
                            color = activeTheme.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (onTogglePin != null) {
                        IconButton(
                            onClick = onTogglePin,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (friendClock.showInWidget) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                contentDescription = stringResource(R.string.cd_pin_city_widget),
                                tint = if (friendClock.showInWidget) activeTheme.accentColor else activeTheme.textSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("delete_friend_${friendClock.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.cd_remove_city),
                            tint = activeTheme.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Digital Time Numerals colored via dedicated activeTheme.numberColor
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    val numeralFontSize = if (isExpandedLayout) 33.sp else 27.sp
                    val secondsFontSize = if (isExpandedLayout) 15.sp else 13.sp
                    val amPmFontSize = if (isExpandedLayout) 13.sp else 11.sp
                    val tabularStyle = TextStyle(fontFeatureSettings = "tnum", letterSpacing = (-0.5).sp)

                    Text(
                        text = friendTime.format(hhFormatter),
                        fontSize = numeralFontSize,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = ChronometerDisplayFont,
                        color = activeTheme.numberColor,
                        style = tabularStyle
                    )

                    val colonAlpha by animateFloatAsState(
                        targetValue = if (friendTime.second % 2 == 0) 1f else 0.30f,
                        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                        label = "WorldColonAlpha"
                    )

                    Text(
                        text = ":",
                        fontSize = numeralFontSize,
                        fontWeight = FontWeight.Medium,
                        fontFamily = ChronometerDisplayFont,
                        color = activeTheme.numberColor.copy(alpha = colonAlpha),
                        modifier = Modifier.padding(horizontal = 1.5.dp)
                    )

                    Text(
                        text = friendTime.format(mmFormatter),
                        fontSize = numeralFontSize,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = ChronometerDisplayFont,
                        color = activeTheme.numberColor,
                        style = tabularStyle
                    )

                    if (showSeconds) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = friendTime.format(ssFormatter),
                            fontSize = secondsFontSize,
                            fontFamily = ChronometerMonoFont,
                            fontWeight = FontWeight.Bold,
                            color = activeTheme.numberColor.copy(alpha = 0.88f),
                            style = TextStyle(fontFeatureSettings = "tnum"),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    if (!use24Hour) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = amPmString,
                            fontSize = amPmFontSize,
                            fontWeight = FontWeight.Bold,
                            fontFamily = ChronometerDisplayFont,
                            color = activeTheme.textSecondary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (showOffset) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = activeTheme.cardBackground,
                            border = BorderStroke(0.85.dp, activeTheme.dividerColor)
                        ) {
                            Text(
                                text = offsetLabel,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = ChronometerMonoFont,
                                color = activeTheme.accentColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                maxLines = 1
                            )
                        }
                    }
                    if (showDate) {
                        Text(
                            text = friendTime.format(dateFormatter),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = activeTheme.textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ClockSortSelector(
    currentSortOrder: String,
    activeTheme: ClockVisualTheme,
    onSortSelected: (String) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val options = listOf(
            "DEFAULT" to "Added",
            "NAME" to "A-Z",
            "OFFSET" to "UTC Offset"
        )
        options.forEach { (key, label) ->
            val isSelected = currentSortOrder == key
            Surface(
                onClick = { onSortSelected(key) },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) activeTheme.accentColor else activeTheme.cardBackground,
                border = if (isSelected) null else BorderStroke(1.dp, activeTheme.dividerColor),
                modifier = Modifier
                    .defaultMinSize(minHeight = 36.dp)
                    .testTag("sort_option_$key")
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) activeTheme.onAccentColor else activeTheme.textSecondary
                    )
                }
            }
        }
    }
}
