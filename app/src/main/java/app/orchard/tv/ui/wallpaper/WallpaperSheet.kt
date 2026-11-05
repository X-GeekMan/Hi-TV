package app.orchard.tv.ui.wallpaper

import androidx.compose.ui.draw.drawBehind

import android.app.Activity
import android.os.Build
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.viewinterop.AndroidView
import app.orchard.tv.wallpaper.ScreensaverVideos
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.orchard.tv.R
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.settings.FocusStyle
import app.orchard.tv.settings.UiSounds
import app.orchard.tv.settings.Effects
import app.orchard.tv.settings.HomeRefreshRate
import app.orchard.tv.system.Sounds
import app.orchard.tv.ui.components.Pressable
import app.orchard.tv.ui.components.consumeTaps
import app.orchard.tv.ui.components.dismissOnTap
import app.orchard.tv.ui.theme.Glyph
import app.orchard.tv.ui.theme.GlyphIcon
import app.orchard.tv.ui.theme.Motion
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type
import app.orchard.tv.wallpaper.PhoneServer
import app.orchard.tv.wallpaper.AerialFormats
import app.orchard.tv.wallpaper.AerialQuality
import app.orchard.tv.wallpaper.ScreensaverMode
import app.orchard.tv.system.SystemScreensaver
import app.orchard.tv.system.DisplayRefreshRate
import app.orchard.tv.wallpaper.Wallpaper
import app.orchard.tv.wallpaper.WallpaperKind
import app.orchard.tv.wallpaper.Wallpapers
import app.orchard.tv.weather.Weather
import app.orchard.tv.weather.WeatherKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Wallpaper selection occupies the screen; the other personalisation settings retain their sheet.
 * The QR code opens the phone page for sending photos and videos.
 */
@Composable
fun WallpaperSheet(
    visible: Boolean,
    wallpaperOnly: Boolean = false,
    onDismiss: () -> Unit,
    hiddenCount: Int,
    onHiddenApps: () -> Unit,
    onStartScreensaver: () -> Unit,
    onRefreshRateChanged: () -> Unit,
    onPin: (on: Boolean) -> Unit,
    /** The sheet is all the way in (true) or not: Home need not draw under it then ([SheetCovers]). */
    onSettled: (Boolean) -> Unit = {},
    /** Draw the closed sheet once, nearly clear, so that its first opening is not its first draw (HomeScreen). */
    warm: Boolean = false,
) {
    val context = LocalContext.current
    var wallpaperCategory by remember { mutableStateOf(0) }
    var settingsCategory by remember { mutableStateOf(0) }
    var phoneUrl by remember { mutableStateOf<String?>(null) }
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }

    BackHandler(enabled = visible, onBack = onDismiss)

    // The phone page listens all along (PhoneServer.listen); the address is read again each time,
    // as the TV's may have changed.
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        while (isActive) {
            val url = withContext(Dispatchers.IO) { runCatching { PhoneServer.start(context) }.getOrNull() }
            if (url != phoneUrl || url != null && qr == null) {
                phoneUrl = url
                qr = url?.let { withContext(Dispatchers.Default) { PhoneServer.qr(it).asImageBitmap() } }
            }
            delay(5_000)
        }
    }

    AnimatedVisibility(visible = visible, enter = fadeIn(tween(220, easing = Motion.EaseOut)), exit = fadeOut(tween(180, easing = Motion.EaseOut)), modifier = Modifier.fillMaxSize()) {
        // A tap above the sheet closes it, back to Control Center as Back does.
        Box(Modifier.fillMaxSize().dismissOnTap(onDismiss).background(Color.Black.copy(alpha = 0.25f)))
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(160, easing = Motion.EaseOut)),
            exit = fadeOut(tween(130, easing = Motion.EaseOut)),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .consumeTaps()
                    .background(Color(0xFF182635))
                    .padding(start = 40.dp, end = 32.dp, top = 24.dp),
            ) {
                val scroll = rememberScrollState()
                val scope = rememberCoroutineScope()
                // How Home looks, in the order you see it: the picture behind everything, how Home
                // is laid out over it, what replaces it when idle, then the weather and the PIN.
                // Settings sit in tvOS-style grouped rows; each group's note follows the focused row.
                if (!wallpaperOnly) {
                    SettingsSidebar(settingsCategory) { index ->
                        if (settingsCategory != index) {
                            settingsCategory = index
                            scope.launch { scroll.scrollTo(0) }
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(scroll).latestBringIntoView(scroll)) {
                    if (wallpaperOnly) {
                        BasicText(stringResource(R.string.home_wallpaper_screensaver), style = Type.LargeTitle)
                        Spacer(Modifier.height(16.dp))
                    }
                    if (wallpaperOnly) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val categories = listOf(R.string.category_static, R.string.category_dynamic, R.string.category_saver) +
                                if (Wallpapers.items.any { it.kind == WallpaperKind.Source }) listOf(R.string.category_sources) else emptyList()
                            categories.forEachIndexed { index, title ->
                                Chip(stringResource(title), on = wallpaperCategory == index) {
                                    wallpaperCategory = index
                                }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        if (wallpaperCategory != 2) {
                            SectionTitle(when (wallpaperCategory) { 0 -> R.string.category_static; 3 -> R.string.category_sources; else -> R.string.category_dynamic })
                            if (wallpaperCategory == 1) BasicText(stringResource(R.string.wp_dynamic_hint), style = Type.Caption.copy(color = Palette.TertiaryLabel))
                            WallpaperRows(videosOnly = wallpaperCategory == 1, sourcesOnly = wallpaperCategory == 3)
                            if (wallpaperCategory == 1 && Wallpapers.moves()) Group { note ->
                                Choices(R.string.st_play, stringResource(R.string.st_play_hint), note,
                                    listOf(false to R.string.st_play_still, true to R.string.st_play_always),
                                    AerialSettings.wallpaperAlwaysMoves, AerialSettings::chooseWallpaperAlwaysMoves, first = true)
                                if (Wallpapers.isVideo()) {
                                    Choices(R.string.video_sound, stringResource(R.string.video_sound_hint), note,
                                        listOf(true to R.string.video_muted, false to R.string.video_unmuted),
                                        AerialSettings.videoWallpaperMuted, AerialSettings::chooseVideoWallpaperMuted)
                                    Choices(R.string.video_fit, stringResource(R.string.video_fit_hint), note,
                                        listOf(false to R.string.video_fill, true to R.string.video_contain),
                                        AerialSettings.videoWallpaperFit, AerialSettings::chooseVideoWallpaperFit)
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    } else {
                    if (settingsCategory == 2) {
                    Group { note ->
                        Choices(R.string.st_effects, stringResource(R.string.st_effects_hint), note,
                            listOf(Effects.Lite to R.string.st_effects_lite, Effects.Auto to R.string.st_effects_auto, Effects.Full to R.string.st_effects_full),
                            AerialSettings.effects, AerialSettings::chooseEffects, first = true)
                    }
                    }
                    if (settingsCategory == 0) {
                    // What is on Home and where; then the clock; then how the remote feels.
                    Group { note ->
                        Choices(
                            R.string.st_layout, stringResource(R.string.st_home_layout_hint), note,
                            listOf(true to R.string.st_home_one_row, false to R.string.st_home_standard),
                            AerialSettings.oneRowHome, AerialSettings::chooseOneRowHome, first = true,
                        )
                        // Two rows show the second row whole; only one row has a peek to hide.
                        if (AerialSettings.oneRowHome) Choices(
                            R.string.st_peek, stringResource(R.string.st_peek_hint), note,
                            listOf(true to R.string.st_peek_show, false to R.string.st_peek_hide),
                            AerialSettings.peekNextRow, AerialSettings::choosePeekNextRow,
                        )
                        Choices(
                            R.string.st_columns, stringResource(R.string.st_columns_hint), note,
                            AerialSettings.HOME_COLUMNS.map { it to it.toString() },
                            AerialSettings.homeColumns, AerialSettings::chooseHomeColumns,
                        )
                        Choices(
                            R.string.st_plate, stringResource(R.string.st_plate_hint), note,
                            listOf(0 to R.string.st_plate_glass, 1 to R.string.plate_liquid, 2 to R.string.st_plate_none),
                            AerialSettings.plateStyle, AerialSettings::choosePlateStyle,
                        )
                        Choices(
                            R.string.st_names, stringResource(R.string.st_names_hint), note,
                            listOf(false to R.string.st_names_focused, true to R.string.st_names_all),
                            AerialSettings.showAllNames, AerialSettings::chooseShowAllNames,
                        )
                        Choices(
                            R.string.settings_icon_position, null, note,
                            listOf(0 to R.string.settings_icon_left, 1 to R.string.settings_icon_right, 2 to R.string.settings_icon_upper_right),
                            AerialSettings.settingsIconPosition, AerialSettings::chooseSettingsIconPosition,
                        )
                        Choices(
                            R.string.settings_icon_visibility, stringResource(R.string.settings_icon_fade_hint), note,
                            listOf(0 to R.string.settings_icon_always, 1 to R.string.settings_icon_fade, 2 to R.string.settings_icon_hidden),
                            AerialSettings.settingsIconVisibility, AerialSettings::chooseSettingsIconVisibility,
                        )
                        // Apps taken off Home are kept with the rest of how Home looks.
                        SettingRow(R.string.st_hidden_apps, stringResource(R.string.st_hidden_apps_hint), note) {
                            ActionChip(
                                if (hiddenCount == 0) stringResource(R.string.st_hidden_none) else stringResource(R.string.st_hidden_count, hiddenCount),
                                Glyph.ChevronRight,
                                onHiddenApps,
                            )
                        }
                    }
                    }
                    if (settingsCategory == 1) {
                    Group { note ->
                        Choices(
                            R.string.clock_bar, null, note,
                            listOf(0 to R.string.st_clock_left, 1 to R.string.st_clock_right, 2 to R.string.clock_center, 3 to R.string.clock_status),
                            AerialSettings.clockPosition, AerialSettings::chooseClockPosition, first = true,
                        )
                        Choices(
                            R.string.clock_bar_size, null, note,
                            listOf(0 to R.string.clock_default, 1 to R.string.st_clock_large, 2 to R.string.clock_bold),
                            AerialSettings.clockSizeMode, AerialSettings::chooseClockSizeMode,
                        )
                        Choices(
                            R.string.show_lunar_date, null, note,
                            listOf(true to R.string.st_on, false to R.string.st_off),
                            AerialSettings.showLunarDate, AerialSettings::chooseShowLunarDate,
                        )
                        Choices(
                            R.string.wp_weather, null, note,
                            listOf(true to R.string.st_on, false to R.string.st_off),
                            AerialSettings.showWeather, AerialSettings::chooseShowWeather,
                        )
                        SettingRow(R.string.wp_weather_city, null, note) { WeatherCityButton() }
                        Choices(
                            R.string.st_device_capacity, stringResource(R.string.st_device_capacity_hint), note,
                            listOf(true to R.string.st_on, false to R.string.st_off),
                            AerialSettings.showDeviceCapacity, AerialSettings::chooseShowDeviceCapacity,
                        )
                    }
                    }
                    if (settingsCategory == 2) {
                    Spacer(Modifier.height(10.dp))
                    Group { note ->
                        Choices(
                            R.string.st_focus_style,
                            stringResource(if (AerialSettings.focusStyle == FocusStyle.HighContrast) R.string.st_focus_high_hint else R.string.st_focus_standard_hint),
                            note,
                            listOf(FocusStyle.HighContrast to R.string.st_focus_high, FocusStyle.Standard to R.string.st_focus_standard, FocusStyle.LiquidSlide to R.string.focus_liquid_slide),
                            AerialSettings.focusStyle, AerialSettings::chooseFocusStyle, first = true,
                        )
                        Choices(
                            R.string.st_sounds,
                            stringResource(if (AerialSettings.uiSounds == UiSounds.Tv) R.string.st_sounds_tv_hint else R.string.st_sounds_hint),
                            note,
                            listOf(UiSounds.Tv to R.string.st_sounds_tv, UiSounds.Aerial to R.string.st_sounds_aerial, UiSounds.Soft to R.string.sound_soft, UiSounds.Crystal to R.string.sound_crystal, UiSounds.Off to R.string.st_off),
                            AerialSettings.uiSounds, { AerialSettings.chooseUiSounds(it); Sounds.prepare() },
                        )

                    }
                    }

                    }
                    if (wallpaperOnly && wallpaperCategory == 2) {
                    Spacer(Modifier.height(22.dp))
                    SectionTitle(R.string.wp_screensaver)
                    Group { note ->
                        SettingRow(
                            R.string.ss_kind,
                            when {
                                Wallpapers.screensaver == ScreensaverMode.Videos -> stringResource(R.string.ss_videos_hint)
                                Wallpapers.screensaver == ScreensaverMode.System -> stringResource(R.string.ss_system_hint)
                                Wallpapers.screensaverFollows -> stringResource(R.string.ss_follow_hint)
                                AerialSettings.backStartsScreensaver -> stringResource(R.string.ss_back_hint)
                                else -> null
                            },
                            note, first = true,
                        ) { ScreensaverChoice(onStartScreensaver) }
                        // Only the qualities this TV can play; a 1080p-only TV has nothing to choose.
                        val qualities = remember { AerialFormats.supported(context) }
                        if (qualities.size > 1) {
                            Choices(
                                R.string.ss_quality, stringResource(R.string.ss_quality_hint), note,
                                listOf(AerialQuality.Auto to R.string.ss_quality_auto, AerialQuality.Hd to R.string.ss_quality_hd) +
                                    listOf(AerialQuality.Uhd to R.string.ss_quality_uhd, AerialQuality.DolbyVision to R.string.ss_quality_dv)
                                        .filter { it.first in qualities },
                                AerialSettings.aerialQuality, AerialSettings::chooseAerialQuality,
                            )
                        }
                        // The clock screensaver is its clock, and the others show none.
                        if (Wallpapers.screensaver == ScreensaverMode.Aerial || Wallpapers.screensaver == ScreensaverMode.Photos) Choices(
                            R.string.ss_show_time, stringResource(R.string.ss_show_time_hint), note,
                            listOf(true to R.string.st_on, false to R.string.st_off),
                            AerialSettings.screensaverClock, AerialSettings::chooseScreensaverClock,
                        )
                        // The firmware's screensaver starts on the firmware's timer: Aerial's delay
                        // did nothing there, and Xiaomi's 1 minute came before the 5 chosen (issue #675).
                        if (Wallpapers.screensaver == ScreensaverMode.System) SettingRow(R.string.ss_delay, null, note) {
                            val delay = remember { SystemScreensaver.firmwareDelayMs(context) }
                            BasicText(
                                when {
                                    delay == null -> stringResource(R.string.ss_delay_system)
                                    delay == 0L -> stringResource(R.string.ss_delay_system_off)
                                    else -> stringResource(R.string.ss_delay_system_minutes, ((delay + 30_000) / 60_000).coerceAtLeast(1).toInt())
                                },
                                style = Type.Body.copy(color = Palette.SecondaryLabel),
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        } else Choices(
                            R.string.ss_delay, null, note,
                            AerialSettings.screensaverChoices.map {
                                it to if (it == AerialSettings.NEVER) stringResource(R.string.ss_never) else stringResource(R.string.ss_minutes, it)
                            },
                            AerialSettings.screensaverMinutes, AerialSettings::chooseScreensaverMinutes,
                        )
                        Choices(
                            R.string.ss_back, stringResource(R.string.ss_back_choice_hint), note,
                            listOf(true to R.string.ss_back_on, false to R.string.ss_back_off),
                            AerialSettings.backStartsScreensaver, AerialSettings::chooseBackStartsScreensaver,
                        )
                    }

                    }
                    if (!wallpaperOnly && settingsCategory == 3) {
                    Spacer(Modifier.height(2.dp))
                    Group(stringResource(R.string.st_pin_hint)) { note ->
                        SettingRow(R.string.st_pin, null, note, first = true) {
                            // The last row that takes focus: bring the note under it into view too.
                            PinChoice(onPin, onFocused = { scope.launch { scroll.animateScrollTo(scroll.maxValue) } })
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    }
                }
                Spacer(Modifier.width(if (wallpaperOnly) 28.dp else 18.dp))
                // Keep phone setup anchored while switching or scrolling categories.
                SettingsPreviewPanel(phoneUrl, qr, if (wallpaperOnly) 60.dp else 0.dp, 24.dp, compact = wallpaperOnly)
            }
        }
    }
}

/** Categories stay visible while the selected settings scroll in the middle column. */
@Composable
private fun SettingsSidebar(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf(R.string.settings_category_home, R.string.settings_category_clock,
        R.string.settings_category_controls, R.string.settings_category_safety)
    val glyphs = listOf(Glyph.Home, Glyph.Calendar, Glyph.Switches, Glyph.Shield)
    Column(Modifier.width(170.dp).fillMaxHeight().padding(bottom = 24.dp)
        .clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.06f)).padding(8.dp)) {
        BasicText(stringResource(R.string.wp_title), style = Type.LargeTitle, maxLines = 1, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 16.dp))
        labels.forEachIndexed { index, title ->
            Pressable(
                onClick = { onSelect(index) }, focusedScale = 1f,
                modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onSelect(index) },
            ) { state ->
                val bg = when { state.focused -> Palette.Focus; selected == index -> Color.White.copy(alpha = 0.18f); else -> Color.Transparent }
                val fg = if (state.focused) Palette.OnFocus else Palette.Label
                Row(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlyphIcon(glyphs[index], fg, 18.dp)
                    BasicText(stringResource(title), style = Type.Caption.copy(color = fg), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(5.dp))
        }
    }
}

/** Phone setup stays available without rendering a second desktop and wallpaper. */
@Composable
private fun SettingsPreviewPanel(url: String?, qr: ImageBitmap?, topInset: androidx.compose.ui.unit.Dp,
    bottomInset: androidx.compose.ui.unit.Dp, compact: Boolean) {
    Box(Modifier.width(196.dp).fillMaxHeight().padding(top = topInset, bottom = bottomInset),
        contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.07f)).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(stringResource(R.string.wp_phone_title), style = Type.Caption)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.size(if (compact) 68.dp else 100.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).padding(7.dp), contentAlignment = Alignment.Center) {
                if (qr != null) Image(qr, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
                else GlyphIcon(Glyph.Offline, Color.DarkGray, 28.dp)
            }
            Spacer(Modifier.height(8.dp))
            BasicText(stringResource(if (url != null) R.string.wp_phone_body else R.string.wp_phone_offline),
                style = Type.Caption.copy(color = Palette.SecondaryLabel, fontSize = 10.sp, lineHeight = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center))
            if (url != null) {
                Spacer(Modifier.height(6.dp))
                BasicText(url.removePrefix("http://"), style = Type.Caption.copy(color = Palette.SecondaryLabel, fontSize = 10.sp),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Every wallpaper in one row, in the order most people want them: the moving aerial, Bing's photo
 * of the day, the owner's own photos, then a few of the built-in pictures and "Follow App". Past
 * daily photos and the rest of the built-ins are left out unless one of them is the current
 * choice. The focused card's note is under the row, as under a group of settings.
 */
@Composable
private fun WallpaperRows(videosOnly: Boolean, sourcesOnly: Boolean = false) {
    val items = Wallpapers.items
    val selected = Wallpapers.selectedId
    var preview by remember { mutableStateOf<Wallpaper?>(null) }
    val first = remember { FocusRequester() }
    val dailyTitle = stringResource(R.string.wp_daily_latest)
    val rotateTitle = stringResource(R.string.wp_photos_rotate)
    val videosTitle = stringResource(R.string.wp_videos_rotate)
    val list = remember(items, selected, dailyTitle, rotateTitle, videosTitle, videosOnly, sourcesOnly) {
        buildList {
if (sourcesOnly) {
                addAll(items.filter { it.kind == WallpaperKind.Source })
            } else if (videosOnly) {
                addAll(items.filter { it.kind == WallpaperKind.Live })
                val videos = items.filter { it.kind == WallpaperKind.Video && it.id != Wallpapers.VIDEOS }
                if (videos.size > 1 || selected == Wallpapers.VIDEOS) add(Wallpaper(Wallpapers.VIDEOS, WallpaperKind.Video, videosTitle))
                addAll(videos)
            } else {
                if (items.any { it.kind == WallpaperKind.Daily }) add(Wallpaper(Wallpapers.LATEST_DAILY, WallpaperKind.Daily, dailyTitle))
                addAll(items.filter { it.kind == WallpaperKind.Daily }.map { wallpaper ->
                    val date = wallpaper.id.removePrefix("daily:")
                    val label = if (date.length == 8 && date.all(Char::isDigit))
                        "${date.substring(0, 4)}-${date.substring(4, 6)}-${date.substring(6, 8)} · ${wallpaper.title}"
                    else wallpaper.title
                    wallpaper.copy(title = label)
                })
                val photos = items.filter { it.kind == WallpaperKind.Photo }
                if (photos.size > 1) add(Wallpaper(Wallpapers.ROTATE_PHOTOS, WallpaperKind.Photo, rotateTitle))
                addAll(photos)
                addAll(items.filter { it.kind == WallpaperKind.Builtin && (it.id in Wallpapers.FEATURED_BUILTINS || it.id == selected) })
                addAll(items.filter { it.kind == WallpaperKind.Auto })
            }
        }
    }
    val takeInitialFocus = if (videosOnly) list.isNotEmpty() else items.none { it.kind == WallpaperKind.Video }
    LaunchedEffect(takeInitialFocus, list.firstOrNull()?.id) {
        if (takeInitialFocus && list.isNotEmpty()) {
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }
    }
    var note by remember { mutableStateOf<String?>(null) }
    // Bound the grid inside the settings scroll container so off-screen thumbnails stay lazy.
    // Padding leaves room for the 8% focus scale on every edge.
    val gridRows = ((list.size + 2) / 3).coerceIn(1, 3)
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxWidth().height((gridRows * 128 + 16).dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
    ) {
        items(list, key = { it.id }) { wp ->
            val hint = wallpaperNote(wp)
            WallpaperCard(wp, if (takeInitialFocus && wp === list.first()) first else null,
                onFocused = { note = hint }, onClick = { Wallpapers.select(wp.id) }, onLongClick = { preview = wp })
        }
    }
    preview?.let { WallpaperPreview(it) { preview = null } }
    Box(Modifier.height(42.dp).padding(start = 4.dp, top = 8.dp)) {
        BasicText(listOfNotNull(note, stringResource(R.string.wallpaper_hold_manage)).joinToString("  ·  "),
            style = Type.Caption.copy(color = Palette.TertiaryLabel), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** What a wallpaper card is, for the note under the row; null where its picture says it all. */
@Composable
private fun wallpaperNote(wp: Wallpaper): String? = when {
    wp.kind == WallpaperKind.Live -> stringResource(R.string.wp_live)
    wp.id == Wallpapers.VIDEOS -> stringResource(R.string.wp_videos_note)
    wp.kind == WallpaperKind.Video -> stringResource(R.string.wp_video_note)
    wp.kind == WallpaperKind.Source -> stringResource(R.string.wp_source_note)
    wp.id == Wallpapers.LATEST_DAILY -> stringResource(R.string.wp_daily_note)
    wp.id == Wallpapers.ROTATE_PHOTOS -> stringResource(R.string.wp_photos_rotate_note)
    wp.kind == WallpaperKind.Photo -> stringResource(R.string.wp_photo_note)
    wp.kind == WallpaperKind.Auto -> stringResource(R.string.wp_auto_note)
    wp.kind == WallpaperKind.Daily -> wp.caption
    else -> null
}

@Composable
private fun WallpaperCard(wp: Wallpaper, focusRequester: FocusRequester?, onFocused: () -> Unit, onClick: () -> Unit, onLongClick: () -> Unit) {
    val selected = Wallpapers.selectedId == wp.id
    val thumb by produceState<ImageBitmap?>(null, wp.id, Wallpapers.items) {
        value = withContext(Dispatchers.Default) { Wallpapers.thumbnail(wp.id)?.asImageBitmap() }
    }
    Pressable(onClick = onClick, onLongClick = onLongClick, focusRequester = focusRequester, focusedScale = 1.08f) { state ->
        LaunchedEffect(state.focused) { if (state.focused) onFocused() }
        Column(Modifier.width(168.dp)) {
            Box(
                Modifier
                    .size(168.dp, 94.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Palette.Fill)
                    .border(
                        if (state.focused || selected) 3.dp else 1.dp,
                        when {
                            state.focused -> Palette.Focus
                            selected -> Palette.Focus.copy(alpha = 0.55f)
                            else -> Palette.Hairline
                        },
                        RoundedCornerShape(12.dp),
                    ),
            ) {
                thumb?.let {
                    Canvas(Modifier.fillMaxSize()) {
                        drawImage(it, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.Medium)
                    }
                }
                if (selected) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(7.dp).size(22.dp).clip(CircleShape).background(Palette.Focus),
                        contentAlignment = Alignment.Center,
                    ) { GlyphIcon(Glyph.Check, Palette.OnFocus, 13.dp) }
                }
                // The aerial wallpaper moves; its card shows the last frame it stopped on.
                if (wp.kind == WallpaperKind.Live || wp.kind == WallpaperKind.Video) {
                    GlyphIcon(Glyph.Play, Color.White, 22.dp, Modifier.align(Alignment.BottomStart).padding(7.dp))
                }
                // An input's picture cannot be copied for a card: its mark over the dark picture.
                if (wp.kind == WallpaperKind.Source) {
                    GlyphIcon(Glyph.Hdmi, Color.White, 34.dp, Modifier.align(Alignment.Center))
                }
            }
            Spacer(Modifier.height(6.dp))
            BasicText(
                wp.title + if (selected) "  ·  " + stringResource(R.string.wallpaper_current) else "", maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = Type.Caption.copy(color = if (state.focused) Palette.Label else Palette.SecondaryLabel),
            )
        }
    }
}

@Composable
private fun WallpaperPreview(wp: Wallpaper, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val selected = Wallpapers.selectedId == wp.id
    val deletable = wp.kind == WallpaperKind.Photo && wp.id.startsWith("photo:") ||
        wp.kind == WallpaperKind.Video && wp.id.startsWith(Wallpapers.VIDEO_PREFIX)
    val picture by produceState<ImageBitmap?>(null, wp.id) {
        value = withContext(Dispatchers.IO) { Wallpapers.previewPicture(wp.id)?.asImageBitmap() }
    }
    val video by produceState<String?>(null, wp.id) {
        if (wp.id.startsWith(Wallpapers.VIDEO_PREFIX))
            value = withContext(Dispatchers.IO) { Wallpapers.videoFilms(wp.id).firstOrNull()?.file?.absolutePath }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(650.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF172637)).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(wp.title + if (selected) "  ·  " + stringResource(R.string.wallpaper_current) else "", style = Type.Headline)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.width(560.dp).height(260.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
                if (video != null) {
                    AndroidView(factory = { ctx -> android.widget.VideoView(ctx).apply {
                        setVideoPath(video)
                        setOnPreparedListener { it.isLooping = true; it.setVolume(0f, 0f); start() }
                    } }, modifier = Modifier.fillMaxSize(), onRelease = { it.stopPlayback() })
                } else picture?.let { Image(it, contentDescription = wp.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip(stringResource(R.string.wallpaper_apply), on = selected) { Wallpapers.select(wp.id); onDismiss() }
                if (deletable) Chip(stringResource(R.string.wallpaper_delete), on = false) {
                    android.app.AlertDialog.Builder(context).setTitle(R.string.wallpaper_delete_confirm)
                        .setMessage(wp.title).setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.wallpaper_delete) { _, _ ->
                            if (wp.kind == WallpaperKind.Photo) Wallpapers.deletePhoto(wp.id)
                            else if (ScreensaverVideos.delete(context, wp.id.removePrefix(Wallpapers.VIDEO_PREFIX))) Wallpapers.videosChanged()
                            onDismiss()
                        }.show()
                }
                Chip(stringResource(R.string.donate_close), on = false, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: Int) {
    BasicText(stringResource(title), style = Type.Headline, modifier = Modifier.padding(start = 4.dp))
    Spacer(Modifier.height(10.dp))
}

/** Optional support for the Hi TV derivative. Payment is entirely outside the app. */
@Composable
internal fun DonationDialog(onDismiss: () -> Unit) {
    val closeFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.width(760.dp).clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF132038)).border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(26.dp))
                .padding(horizontal = 30.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    BasicText(stringResource(R.string.donate_title), style = Type.LargeTitle)
                    Spacer(Modifier.height(12.dp))
                    BasicText(stringResource(R.string.donate_subtitle), style = Type.Body.copy(fontWeight = FontWeight.SemiBold))
                    Spacer(Modifier.height(18.dp))
                    BasicText(stringResource(R.string.donate_description), style = Type.Body)
                    Spacer(Modifier.height(16.dp))
                    BasicText(stringResource(R.string.donate_hint), style = Type.Caption.copy(color = Palette.SecondaryLabel))
                }
                DonationCode(R.drawable.donate_wechat, R.string.donate_wechat)
                DonationCode(R.drawable.donate_alipay, R.string.donate_alipay)
            }
            Spacer(Modifier.height(22.dp))
            Pressable(onClick = onDismiss, focusRequester = closeFocus, focusedScale = 1.03f) { state ->
                Box(Modifier.width(220.dp).height(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (state.focused) Color.White else Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    BasicText(stringResource(R.string.donate_close), style = Type.Body.copy(color = if (state.focused) Color(0xFF101D33) else Color.White, fontWeight = FontWeight.SemiBold))
                }
            }
            Spacer(Modifier.height(8.dp))
            BasicText(stringResource(R.string.donate_back), style = Type.Caption.copy(color = Palette.SecondaryLabel))
        }
    }
    LaunchedEffect(Unit) { withFrameNanos { }; closeFocus.requestFocus() }
}

@Composable
private fun DonationCode(imageId: Int, labelId: Int) {
    val context = LocalContext.current
    val bitmap = remember(imageId) { BitmapFactory.decodeResource(context.resources, imageId).asImageBitmap() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // White card is rounded; the QR itself is not clipped so finder patterns stay whole.
        Box(
            Modifier.size(168.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap,
                contentDescription = stringResource(labelId),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(8.dp))
        BasicText(stringResource(labelId), style = Type.Body)
    }
}

/**
 * A card of setting rows, as in tvOS Settings, with one note under it: the focused row's, or
 * [note] when the row has none or focus is elsewhere. The note keeps two lines of room so the
 * sheet does not shift as focus moves.
 */
@Composable
private fun Group(note: String? = null, modifier: Modifier = Modifier, rows: @Composable ColumnScope.(note: (String?) -> Unit) -> Unit) {
    var focusedNote by remember { mutableStateOf<String?>(null) }
    var inside by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .onFocusChanged { inside = it.hasFocus },
    ) { rows { focusedNote = it } }
    Box(Modifier.height(38.dp).padding(start = 16.dp, top = 7.dp)) {
        (if (inside) focusedNote ?: note else note)?.let {
            BasicText(it, style = Type.Caption.copy(color = Palette.TertiaryLabel), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** One row: the setting's name on the left, its control on the right, a hairline above all but the first. */
@Composable
private fun SettingRow(label: Int, hint: String?, note: (String?) -> Unit, first: Boolean = false, control: @Composable RowScope.() -> Unit) {
    if (!first) Box(Modifier.padding(start = 18.dp).fillMaxWidth().height(1.dp).background(Palette.Hairline))
    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(start = 18.dp, end = 10.dp)
            .onFocusChanged { if (it.hasFocus) note(hint) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(stringResource(label), style = Type.Body, modifier = Modifier.width(112.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        control()
    }
}

/** A row of chips for one of a few values. */
@Composable
private fun <T> Choices(
    label: Int,
    hint: String?,
    note: (String?) -> Unit,
    options: List<Pair<T, Any>>,
    current: T,
    choose: (T) -> Unit,
    first: Boolean = false,
    available: (T) -> Boolean = { true },
) {
    val scope = rememberCoroutineScope()
    SettingRow(label, hint, note, first) {
        // The scroll viewport clips its children. Reserve room on every side for focus scaling,
        // and bring the padded bounds into view when moving through a long row with the remote.
        Row(Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            options.forEach { (value, text) ->
                val bringIntoView = remember(value) { BringIntoViewRequester() }
                Box(Modifier.bringIntoViewRequester(bringIntoView)
                    .onFocusChanged { if (it.hasFocus) scope.launch { bringIntoView.bringIntoView() } }
                    .padding(horizontal = 5.dp, vertical = 4.dp)) {
                    Chip(
                        if (text is Int) stringResource(text) else text.toString(),
                        on = current == value,
                        enabled = available(value),
                    ) { choose(value) }
                }
            }
        }
    }
}

/** Parental PIN on or off; either way goes through the PIN pad (issue #10). */
@Composable
private fun PinChoice(onPin: (on: Boolean) -> Unit, onFocused: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(false to R.string.st_pin_off, true to R.string.st_pin_on).forEach { (on, label) ->
            Chip(stringResource(label), on = AerialSettings.pinSet == on, onFocused = onFocused) {
                if (AerialSettings.pinSet != on) onPin(on)
            }
        }
    }
}

@Composable
private fun WeatherCityButton() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.weather_city_edit)
    val hint = stringResource(R.string.weather_city_hint)
    val failed = stringResource(R.string.weather_city_failed)
    Pressable(onClick = {
        val input = android.widget.EditText(context).apply { setSingleLine(); setText(Weather.place.orEmpty()); setSelectAllOnFocus(true); this.hint = hint }
        android.app.AlertDialog.Builder(context).setTitle(title).setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val city = input.text.toString().trim()
                if (city.isNotEmpty()) scope.launch {
                    val ok = withContext(Dispatchers.IO) { Weather.choose(city) }
                    if (!ok) android.widget.Toast.makeText(context, failed, android.widget.Toast.LENGTH_LONG).show()
                }
            }.show()
    }) { state ->
        BasicText((Weather.place ?: stringResource(R.string.wp_weather_none)) + "  ›", style = Type.Body.copy(color = if (state.focused) Palette.Label else Palette.SecondaryLabel), modifier = Modifier.padding(8.dp))
    }
}

/** Where the weather is for and what it is now. The city is set on the phone page. */
@Composable
private fun WeatherValue() {
    val w = Weather.now
    val place = Weather.place
    val kind = w?.let {
        stringResource(
            when (it.kind) {
                WeatherKind.Clear -> R.string.wx_clear
                WeatherKind.PartlyCloudy -> R.string.wx_partly_cloudy
                WeatherKind.Cloudy -> R.string.wx_cloudy
                WeatherKind.Fog -> R.string.wx_fog
                WeatherKind.Rain -> R.string.wx_rain
                WeatherKind.Snow -> R.string.wx_snow
                WeatherKind.Thunder -> R.string.wx_thunder
            },
        )
    }
    BasicText(
        if (w != null) listOfNotNull(place, "${w.celsius}° $kind").joinToString(" · ") else stringResource(R.string.wp_weather_none),
        style = Type.Body.copy(color = Palette.SecondaryLabel),
        modifier = Modifier.padding(end = 8.dp),
    )
}

/** A pill to pick one of a few options: a check on the chosen one, white when focused. */
@Composable
private fun Chip(label: String, on: Boolean, onFocused: (() -> Unit)? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Pressable(onClick = onClick, enabled = enabled, focusedScale = 1.06f) { state ->
        if (onFocused != null) LaunchedEffect(state.focused) { if (state.focused) onFocused() }
        val bg by animateColorAsState(
            when {
                state.focused -> Palette.Focus
                on -> Palette.FillStrong
                else -> Palette.Fill
            },
            tween(140, easing = Motion.EaseOut), label = "chip-bg",
        )
        val fg = if (state.focused) Palette.OnFocus else Palette.Label
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(bg).height(40.dp).padding(horizontal = 20.dp)
                .then(if (enabled) Modifier else Modifier.graphicsLayer { alpha = 0.38f }),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (on) GlyphIcon(Glyph.Check, fg, 14.dp)
            BasicText(label, style = Type.Body.copy(color = fg))
        }
    }
}

/** A pill that does something rather than choosing a value: white when focused, a glyph after its label. */
@Composable
private fun ActionChip(label: String, glyph: Glyph, onClick: () -> Unit) {
    Pressable(onClick = onClick, focusedScale = 1.06f) { state ->
        val bg by animateColorAsState(if (state.focused) Palette.Focus else Palette.FillStrong, tween(140, easing = Motion.EaseOut), label = "action-bg")
        val fg = if (state.focused) Palette.OnFocus else Palette.Label
        Row(
            Modifier.clip(RoundedCornerShape(50)).drawBehind { drawRect(bg) }.height(40.dp).padding(start = 20.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BasicText(label, style = Type.Body.copy(color = fg))
            GlyphIcon(glyph, fg, 18.dp)
        }
    }
}

@Composable
private fun ScreensaverChoice(onStart: () -> Unit) {
    // The choice first, then the action that tries it.
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            ScreensaverMode.Aerial to R.string.ss_aerial,
            ScreensaverMode.Photos to R.string.ss_photos,
            ScreensaverMode.FlipClock to R.string.ss_flip_clock,
            ScreensaverMode.Videos to R.string.ss_videos,
        ).forEach { (mode, label) ->
            Chip(stringResource(label), on = Wallpapers.screensaver == mode) { Wallpapers.chooseScreensaver(mode) }
        }
        Spacer(Modifier.width(6.dp))
        Pressable(onClick = onStart, focusedScale = 1.06f) { state ->
            val bg by animateColorAsState(if (state.focused) Palette.Focus else Palette.FillStrong, tween(140, easing = Motion.EaseOut), label = "ss-start")
            val fg = if (state.focused) Palette.OnFocus else Palette.Label
            Row(
                Modifier.clip(RoundedCornerShape(50)).drawBehind { drawRect(bg) }.height(40.dp).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlyphIcon(Glyph.Play, fg, 14.dp)
                BasicText(stringResource(R.string.ss_start), style = Type.Body.copy(color = fg))
            }
        }
    }
}

@Composable
private fun PhoneCard(url: String?, qr: ImageBitmap?) {
    Column(Modifier.width(220.dp).padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(176.dp).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (qr != null) {
                Image(qr, contentDescription = null, contentScale = ContentScale.Fit, filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize())
            } else {
                GlyphIcon(Glyph.Offline, Color.Black.copy(alpha = 0.35f), 40.dp)
            }
        }
        Spacer(Modifier.height(14.dp))
        BasicText(stringResource(R.string.wp_phone_title), style = Type.Headline)
        Spacer(Modifier.height(4.dp))
        BasicText(
            stringResource(if (url != null) R.string.wp_phone_body else R.string.wp_phone_offline),
            style = Type.Caption.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
        )
        if (url != null) {
            Spacer(Modifier.height(6.dp))
            // The whole address, token and all, to type into a computer's browser.
            BasicText(url.removePrefix("http://"), style = Type.Caption.copy(color = Palette.SecondaryLabel))
        }
    }
}
