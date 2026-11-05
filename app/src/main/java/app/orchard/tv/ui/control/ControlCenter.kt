package app.orchard.tv.ui.control

import androidx.compose.ui.focus.onFocusChanged
import app.orchard.tv.ui.components.smoothBackground

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import android.view.KeyEvent as NativeKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.Image
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orchard.tv.CcPage
import app.orchard.tv.BuildConfig
import app.orchard.tv.HomeViewModel
import app.orchard.tv.Overlay
import app.orchard.tv.R
import app.orchard.tv.apps.SourceLooks
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.settings.Effects
import app.orchard.tv.settings.Restricted
import app.orchard.tv.settings.RestrictedKind
import app.orchard.tv.ui.home.kidsTimeLine
import app.orchard.tv.system.HomeStatus
import app.orchard.tv.system.LaunchFallback
import app.orchard.tv.system.NetKind
import app.orchard.tv.system.SourceKind
import app.orchard.tv.system.Sounds
import app.orchard.tv.system.SystemProbe
import app.orchard.tv.ui.components.Pressable
import app.orchard.tv.ui.components.consumeTaps
import app.orchard.tv.ui.components.dismissOnTap
import app.orchard.tv.ui.components.liquidGlass
import app.orchard.tv.ui.wallpaper.DonationDialog
import app.orchard.tv.ui.home.MenuRow
import app.orchard.tv.ui.home.VpnBadge
import app.orchard.tv.ui.home.dateText
import app.orchard.tv.ui.home.timeText
import app.orchard.tv.ui.theme.Glyph
import app.orchard.tv.ui.theme.GlyphIcon
import app.orchard.tv.ui.theme.Motion
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type
import app.orchard.tv.update.ReleaseNotes
import app.orchard.tv.update.Updates
import app.orchard.tv.wallpaper.PhoneServer
import java.time.LocalDateTime

/**
 * tvOS Control Center: a glass panel that drops in from the top-right corner over a blurred Home.
 * Inputs open as a second page inside the same panel, the way tvOS shows audio routes.
 */
@Composable
fun ControlCenter(
    visible: Boolean,
    page: CcPage,
    vm: HomeViewModel,
    now: LocalDateTime,
    onOpen: (android.content.Intent) -> Unit,
    onClose: () -> Unit,
    /** Draw the closed panel once, nearly transparent, so that its first opening is not its first draw (HomeScreen). */
    warm: Boolean = false,
    /** Home is on its top row: no tiles under the panel, only the wallpaper (see liquidGlass). */
    onShelf: Boolean = false,
) {
    BackHandler(enabled = visible) {
        when (page) {
            CcPage.Main -> onClose()
            CcPage.Inputs -> vm.overlay = Overlay.ControlCenter(CcPage.Main)
            CcPage.InputSettings -> vm.overlay = Overlay.ControlCenter(CcPage.Inputs)
            CcPage.Update, CcPage.Setup -> vm.overlay = Overlay.ControlCenter(CcPage.Main)
        }
    }

    LaunchedEffect(visible) { if (visible) Updates.checkIfStale(maxAgeMs = Updates.CONTROL_CENTER_MAX_AGE_MS) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220, easing = Motion.EaseOut)),
        exit = fadeOut(tween(180, easing = Motion.EaseOut)),
        modifier = Modifier.fillMaxSize(),
    ) {
        // A tap outside the panel closes Control Center.
        Box(Modifier.fillMaxSize().dismissOnTap(onClose).background(Color.Black.copy(alpha = 0.28f)))
    }

    Box(Modifier.fillMaxSize().padding(top = 24.dp, end = 32.dp), contentAlignment = Alignment.TopEnd) {
        // The first opening composed the panel and then, frame after frame, gave the GPU its
        // pictures, glyphs and shaders for the first time: 55-95 ms a frame for the whole drop-in,
        // against about 26 ms on the second opening (Xiaomi MiTV-MFTR0, release build). Drawn once
        // at 1% while Home is idle, without focus, the first opening is like the second.
        if (warm && !visible) {
            Column(Modifier.graphicsLayer { alpha = 0.01f }.width(420.dp).liquidGlass(24.dp).padding(16.dp)) {
                MainPage(vm, now, false, onOpen)
            }
        }
        AnimatedVisibility(
            visible = visible,
            // Fade without scaling the large glass layer on every animation frame.
            enter = fadeIn(tween(160, easing = Motion.EaseOut)),
            exit = fadeOut(tween(130, easing = Motion.EaseOut)),
        ) {
            Column(
                Modifier
                    .width(420.dp)
                    .consumeTaps()
                    .liquidGlass(24.dp, seeThroughFilm = onShelf)
                    .padding(16.dp),
            ) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        // Fade only, and snap the panel size. Animating the panel's height re-measures
                        // both pages (and their scrollables) every frame, and sliding the new page on
                        // top of the cross-fade makes its first frame land late; on low-end TV SoCs
                        // that is felt as a stutter when opening and leaving pages.
                        (fadeIn(tween(130, easing = Motion.EaseOut)) togetherWith fadeOut(tween(110, easing = Motion.EaseOut)))
                            .using(SizeTransform { _, _ -> tween(0) })
                    },
                    label = "cc-page",
                ) { p ->
                    // Closing sets the page back to Main while the panel fades out; that page must
                    // not take focus from the tile Home has just put it back on.
                    when (p) {
                        CcPage.Main -> if (Restricted.active) KidsPage(vm, now, visible) else MainPage(vm, now, visible, onOpen)
                        CcPage.Inputs -> InputsPage(vm, visible, onOpen)
                        CcPage.InputSettings -> InputSettingsPage(vm, visible)
                        CcPage.Update -> UpdatePage(vm, visible)
                        CcPage.Setup -> SetupPage(vm, visible, onOpen)
                    }
                }
            }
        }
    }
}

/**
 * Control Center in Kids and Elders mode (Restricted): the time, volume and brightness, the day's
 * time left, and leaving the mode with the PIN. No inputs, network, settings or Personalize.
 */
@Composable
private fun KidsPage(vm: HomeViewModel, now: LocalDateTime, takeFocus: Boolean) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (!takeFocus) return@LaunchedEffect
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    Column {
        BasicText(timeText(now), style = Type.SmallClock.copy(fontSize = Type.LargeTitle.fontSize * 1.05f))
        BasicText(dateText(now), style = Type.Callout)
        kidsTimeLine()?.let {
            Spacer(Modifier.height(6.dp))
            BasicText(it, style = Type.Callout.copy(color = Palette.SecondaryLabel))
        }
        Spacer(Modifier.height(12.dp))
        VolumeRow(vm, first)
        Spacer(Modifier.height(6.dp))
        BrightnessRow(vm, remember { FocusRequester() })
        Spacer(Modifier.height(6.dp))
        MenuRow(
            Glyph.Shield,
            stringResource(if (Restricted.kind == RestrictedKind.Kids) R.string.cc_kids_leave else R.string.cc_elders_leave),
            onClick = vm::leaveKidsMode,
        )
    }
}

@Composable
private fun MainPage(vm: HomeViewModel, now: LocalDateTime, takeFocus: Boolean, onOpen: (android.content.Intent) -> Unit) {
    var donationOpen by remember { mutableStateOf(false) }
    var noticeOpen by remember { mutableStateOf(false) }
    var colorOpen by remember { mutableStateOf(false) }
    var performanceOpen by remember { mutableStateOf(false) }
    if (takeFocus && donationOpen) DonationDialog { donationOpen = false }
    if (takeFocus && noticeOpen) HaloNoticeDialog { noticeOpen = false }
    if (takeFocus && colorOpen) DesktopColorDialog { colorOpen = false }
    if (takeFocus && performanceOpen) PerformanceDialog { performanceOpen = false }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    val volumeFocus = remember { FocusRequester() }
    val brightnessFocus = remember { FocusRequester() }
    // Firmwares that list their inputs late (see HomeViewModel.refreshSources): look again while
    // the page is open and still empty. One provider or TvInputManager query every 3 s.
    LaunchedEffect(takeFocus) {
        if (!takeFocus) return@LaunchedEffect
        while (vm.sources.isEmpty()) {
            vm.refreshSources()
            delay(3000)
        }
    }
    LaunchedEffect(Unit) {
        if (!takeFocus) return@LaunchedEffect
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }

    Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
        // Inputs / network / system settings live on Home; this panel is Control Center.
        Spacer(Modifier.height(4.dp))
        val colorLabel = when (AerialSettings.clockTextColor) {
            1 -> R.string.clock_dark_gray
            2 -> R.string.clock_black
            else -> R.string.clock_white
        }
        val performanceLabel = when (AerialSettings.effects) {
            Effects.Lite -> R.string.st_effects_lite
            Effects.Full -> R.string.st_effects_full
            Effects.Auto -> R.string.st_effects_auto
        }
        // Section entry matching Halo's "…设置" row: opens layout / clock / kids and the rest.
        ControlMenuRow(
            Glyph.Switches,
            stringResource(R.string.cc_desktop_settings),
            focusRequester = first,
            onClick = { vm.overlay = Overlay.Wallpaper },
            trailing = "›",
        )
        Spacer(Modifier.height(5.dp))
        if (true) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    ControlMenuRow(Glyph.TextAppearance, stringResource(R.string.clock_text_color), onClick = { colorOpen = true }, trailing = stringResource(colorLabel))
                }
                Box(Modifier.weight(1f)) {
                    ControlMenuRow(Glyph.Performance, stringResource(R.string.st_effects), onClick = { performanceOpen = true }, trailing = stringResource(performanceLabel))
                }
            }
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    ControlMenuRow(Glyph.Photo, stringResource(R.string.home_wallpaper_screensaver), onClick = { vm.overlay = Overlay.WallpaperPicker })
                }
                Box(Modifier.weight(1f)) {
                    ControlMenuRow(Glyph.Trash, stringResource(R.string.home_clean_background), onClick = {
                        scope.launch {
                            if (Build.VERSION.SDK_INT >= 34) {
                                Toast.makeText(context, R.string.home_clean_unsupported, Toast.LENGTH_LONG).show()
                            } else {
                                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                                fun available(): Long = ActivityManager.MemoryInfo().also(am::getMemoryInfo).availMem
                                val before = available()
                                val requested = withContext(Dispatchers.IO) {
                                    val pm = context.packageManager
                                    // VPNs and network proxies implemented as VPN services can carry the
                                    // TV's connectivity. Never kill them as part of a cleanup request.
                                    val vpnPackages = runCatching {
                                        @Suppress("DEPRECATION")
                                        pm.queryIntentServices(android.content.Intent("android.net.VpnService"), 0)
                                            .mapNotNull { it.serviceInfo?.packageName }.toSet()
                                    }.getOrElse { emptySet() }
                                    @Suppress("DEPRECATION")
                                    pm.getInstalledApplications(0).asSequence()
                                        .filter { it.packageName != context.packageName && it.packageName !in vpnPackages &&
                                            it.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0 }
                                        .count { runCatching { am.killBackgroundProcesses(it.packageName) }.isSuccess }
                                }
                                if (requested == 0) {
                                    Toast.makeText(context, R.string.home_clean_none, Toast.LENGTH_LONG).show()
                                } else {
                                    delay(1200)
                                    val changeMb = (available() - before) / (1024 * 1024)
                                    val delta = if (changeMb >= 0) "+$changeMb" else "$changeMb"
                                    Toast.makeText(context, context.getString(R.string.home_clean_result, requested, delta), Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    })
                }
            }
        } else {
            ControlMenuRow(Glyph.TextAppearance, stringResource(R.string.clock_text_color), onClick = { colorOpen = true },
                trailing = stringResource(colorLabel) + "  ›")
            Spacer(Modifier.height(5.dp))
            ControlMenuRow(Glyph.Performance, stringResource(R.string.st_effects), onClick = { performanceOpen = true },
                trailing = stringResource(performanceLabel) + "  ›")
        }
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                VolumeRow(vm, volumeFocus, Modifier.focusProperties { down = brightnessFocus })
            }
            Box(Modifier.weight(1f)) {
                BrightnessRow(vm, brightnessFocus, Modifier.focusProperties { up = volumeFocus })
            }
        }
        Spacer(Modifier.height(5.dp))
        // What the TV opens when switched on: easy to forget, and puzzling when it happens.
        vm.bootTargetLabel()?.let { name ->
            ControlMenuRow(
                Glyph.Power,
                stringResource(R.string.cc_boot),
                onClick = { vm.overlay = Overlay.ControlCenter(CcPage.InputSettings) },
                trailing = "$name ›",
            )
        }
        Updates.available?.let { latest ->
            ControlMenuRow(
                Glyph.Info,
                stringResource(R.string.cc_update_available, latest),
                onClick = { vm.overlay = Overlay.ControlCenter(CcPage.Update) },
            )
        }

        ControlMenuRow(Glyph.Shield, stringResource(R.string.cc_setup_title),
            onClick = { vm.overlay = Overlay.ControlCenter(CcPage.Setup) }, trailing = "›")
        if (true) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { ControlMenuRow(Glyph.Coffee, stringResource(R.string.donate_title), onClick = { donationOpen = true }) }
                Box(Modifier.weight(1f)) { ControlMenuRow(Glyph.Info, stringResource(R.string.halo_notice_title), onClick = { noticeOpen = true }) }
            }
        } else {
            ControlMenuRow(Glyph.Coffee, stringResource(R.string.donate_title), onClick = { donationOpen = true }, trailing = "›")
            ControlMenuRow(Glyph.Info, stringResource(R.string.halo_notice_title), onClick = { noticeOpen = true }, trailing = "›")
        }

        Spacer(Modifier.height(8.dp))
        val (dot, text) = when (vm.homeStatus) {
            HomeStatus.Default -> Palette.Green to stringResource(R.string.cc_home_default)
            HomeStatus.Guarded -> Palette.Green to stringResource(R.string.cc_home_guarded)
            HomeStatus.NotDefault -> Palette.Orange to stringResource(R.string.cc_home_not_default)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 19.5.dp, bottom = 8.dp)) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(8.dp))
            BasicText(text, style = Type.Caption, modifier = Modifier.weight(1f))
            BasicText("Hi TV ${Updates.current}", style = Type.Caption.copy(color = Palette.TertiaryLabel), modifier = Modifier.padding(end = 14.dp))
        }
    }
}

/** Individual frosted rows echo the wallpaper-tinted glass without another live blur. */
@Composable
private fun ControlMenuRow(
    glyph: Glyph,
    text: String,
    onClick: () -> Unit,
    trailing: String? = null,
    focusRequester: FocusRequester? = null,
) {
    MenuRow(
        glyph, text, onClick,
        modifier = Modifier.padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.075f)),
        trailing = trailing,
        rowHeight = 40.dp,
        focusRequester = focusRequester,
    )
}

@Composable
private fun PerformanceDialog(onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(320.dp).clip(RoundedCornerShape(20.dp))
            .background(Color(0xF2182635)).padding(16.dp)) {
            BasicText(stringResource(R.string.st_effects), style = Type.Headline, modifier = Modifier.padding(14.dp))
            listOf(Effects.Lite to R.string.st_effects_lite, Effects.Auto to R.string.st_effects_auto,
                Effects.Full to R.string.st_effects_full).forEach { (mode, label) ->
                val active = AerialSettings.effects == mode
                MenuRow(if (active) Glyph.Check else Glyph.Info, stringResource(label), onClick = {
                    AerialSettings.chooseEffects(mode)
                    onDismiss()
                }, focusRequester = if (active) focus else null, trailing = if (active) "✓" else null)
            }
            BasicText(stringResource(R.string.st_effects_hint), style = Type.Caption.copy(color = Palette.SecondaryLabel),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
        }
    }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { focus.requestFocus() } }
}

/** The same persisted text-color preference, accessible without leaving Control Center. */
@Composable
private fun DesktopColorDialog(onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(300.dp).clip(RoundedCornerShape(20.dp))
            .background(Color(0xF2182635)).padding(16.dp)) {
            BasicText(stringResource(R.string.clock_text_color), style = Type.Headline, modifier = Modifier.padding(14.dp))
            listOf(R.string.clock_white, R.string.clock_dark_gray, R.string.clock_black).forEachIndexed { index, label ->
                MenuRow(if (index == AerialSettings.clockTextColor) Glyph.Check else Glyph.Info, stringResource(label), onClick = {
                    AerialSettings.chooseClockTextColor(index)
                    onDismiss()
                }, focusRequester = if (index == AerialSettings.clockTextColor) first else null,
                    trailing = if (index == AerialSettings.clockTextColor) "✓" else null)
            }
        }
    }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
}

/** Full-screen notice so the complete attribution stays readable from a TV. */
@Composable
private fun HaloNoticeDialog(onDismiss: () -> Unit) {
    val closeFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF101827)), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 780.dp).fillMaxWidth().padding(horizontal = 42.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                BasicText(stringResource(R.string.halo_notice_title), style = Type.LargeTitle)
                Spacer(Modifier.height(26.dp))
                BasicText(stringResource(R.string.halo_notice_body), style = Type.Body.copy(lineHeight = 29.sp))
                Spacer(Modifier.height(34.dp))
                Pressable(onClick = onDismiss, focusRequester = closeFocus, focusedScale = 1.03f) { state ->
                    Box(
                        Modifier.width(200.dp).height(48.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (state.focused) Palette.Focus else Palette.Fill),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(stringResource(R.string.halo_notice_close), style = Type.Body.copy(
                            color = if (state.focused) Palette.OnFocus else Palette.Label,
                            fontWeight = FontWeight.SemiBold,
                        ))
                    }
                }
                Spacer(Modifier.height(8.dp))
                BasicText(stringResource(R.string.halo_notice_back), style = Type.Caption.copy(color = Palette.SecondaryLabel))
            }
        }
    }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { closeFocus.requestFocus() } }
}

/**
 * How to move to the new version: "Download and Install" fetches the APK and opens the system
 * installer (Updates.install). For TVs whose firmware lets no app install another (see
 * Updates.Failure), a QR code opens the modified build's GitHub releases page.
 */
@Composable
private fun UpdatePage(vm: HomeViewModel, takeFocus: Boolean) {
    val context = LocalContext.current
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (!takeFocus) return@LaunchedEffect
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    DisposableEffect(Unit) { onDispose { Updates.resetInstall() } }
    val canInstall = remember { Updates.canInstall(context) }
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(Unit) {
        val url = Updates.RELEASES_PAGE
        qr = withContext(Dispatchers.Default) { PhoneServer.qr(url).asImageBitmap() }
    }
    Column {
        MenuRow(
            Glyph.ChevronLeft, stringResource(R.string.cc_update_title, Updates.available ?: Updates.current),
            focusRequester = if (canInstall) null else first,
            onClick = { vm.overlay = Overlay.ControlCenter(CcPage.Main) },
        )
        if (canInstall) {
            val state = Updates.install
            MenuRow(
                Glyph.Download,
                when (state) {
                    is Updates.Install.Downloading -> stringResource(R.string.cc_update_downloading, (state.fraction * 100).toInt())
                    Updates.Install.Waiting -> stringResource(R.string.cc_update_waiting)
                    else -> stringResource(R.string.cc_update_install)
                },
                focusRequester = first,
                onClick = { Updates.install(context) },
            )
        }
        Column(Modifier.padding(horizontal = 14.dp)) {
            Spacer(Modifier.height(10.dp))
            val status = when (val state = Updates.install) {
                Updates.Install.Waiting -> stringResource(R.string.cc_update_waiting)
                Updates.Install.Opened -> stringResource(R.string.cc_update_opened)
                Updates.Install.NeedsPermission -> stringResource(R.string.cc_update_permission)
                Updates.Install.NeedsStoragePermission -> stringResource(R.string.cc_update_storage_permission)
                is Updates.Install.Failed -> stringResource(
                    when (state.reason) {
                        Updates.Failure.Download -> R.string.cc_update_failed_download
                        Updates.Failure.Blocked -> R.string.cc_update_failed_blocked
                        Updates.Failure.NotOfficial -> R.string.cc_update_failed_signature
                        Updates.Failure.NotNewer -> R.string.cc_update_failed_older
                    },
                )
                else -> stringResource(R.string.cc_update_current, Updates.current)
            }
            BasicText(status, style = Type.Caption)
            Spacer(Modifier.height(8.dp))
            BasicText(stringResource(R.string.cc_about_attribution), style = Type.Caption.copy(color = Palette.TertiaryLabel))
        }
        // Read when the page opens too: the check that found the version may have missed them.
        LaunchedEffect(Unit) { if (Updates.notes.isEmpty()) Updates.loadNotes() }
        Updates.notes.takeIf { it.isNotEmpty() }?.let { notes ->
            Spacer(Modifier.height(10.dp))
            UpdateNotes(notes)
        }
        Column(Modifier.padding(horizontal = 14.dp)) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(104.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp)) {
                    qr?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize()) }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    BasicText(stringResource(if (canInstall) R.string.cc_update_scan_else else R.string.cc_update_scan), style = Type.Body)
                    Spacer(Modifier.height(6.dp))
                    BasicText(stringResource(R.string.cc_update_keeps), style = Type.Caption)
                }
            }
        }
    }
}

/**
 * What the update brings, release by release. The lines are not controls: focused (Down from the
 * install button), Up and Down scroll it a few lines at a time, and past either end focus moves on
 * as from any row. The panel has room for about ten lines under the install button and above the
 * QR code; a bar on the right shows how much more there is.
 */
@Composable
private fun UpdateNotes(notes: List<ReleaseNotes>) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    val step = with(LocalDensity.current) { 64.dp.toPx() }
    val glow by animateFloatAsState(if (focused) 1f else 0f, tween(140, easing = Motion.EaseOut), label = "notes-focus")
    val scrollable = scroll.maxValue > 0
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.matchParentSize().graphicsLayer { alpha = glow }.smoothBackground(Palette.Fill, 12.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 164.dp)
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { e ->
                    val down = when (e.nativeKeyEvent.keyCode) {
                        NativeKeyEvent.KEYCODE_DPAD_DOWN -> true
                        NativeKeyEvent.KEYCODE_DPAD_UP -> false
                        else -> return@onKeyEvent false
                    }
                    if (if (down) scroll.value >= scroll.maxValue else scroll.value <= 0) return@onKeyEvent false
                    if (e.type == KeyEventType.KeyDown) {
                        Sounds.step()
                        scope.launch { scroll.animateScrollBy(if (down) step else -step, spring(dampingRatio = 1f, stiffness = 500f)) }
                    }
                    true
                }
                .focusProperties { canFocus = scrollable }
                .focusable()
                .drawWithContent {
                    drawContent()
                    if (scroll.maxValue <= 0) return@drawWithContent
                    // The scroll bar, inside the right padding.
                    val track = size.height - 16.dp.toPx()
                    val thumb = (track * size.height / (size.height + scroll.maxValue)).coerceAtLeast(12.dp.toPx())
                    val top = 8.dp.toPx() + (track - thumb) * scroll.value / scroll.maxValue
                    drawRoundRect(
                        if (focused) Palette.SecondaryLabel else Palette.TertiaryLabel,
                        topLeft = Offset(size.width - 6.dp.toPx(), top),
                        size = Size(2.5.dp.toPx(), thumb),
                        cornerRadius = CornerRadius(1.25.dp.toPx()),
                    )
                }
                .verticalScroll(scroll)
                .padding(start = 14.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        ) {
            notes.forEachIndexed { i, release ->
                if (i > 0) Spacer(Modifier.height(10.dp))
                BasicText(stringResource(R.string.cc_update_notes, release.version), style = Type.Callout.copy(color = Palette.Label))
                release.changes.forEach { line ->
                    Row(Modifier.padding(top = 4.dp)) {
                        BasicText("•", style = Type.Caption, modifier = Modifier.width(10.dp))
                        BasicText(line, style = Type.Caption, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * For a TV where Aerial was installed from the APK alone (a USB stick, an app store): it opens, but
 * Home, power-on and wake still go to the stock launcher (issue #213). The settings pages that can
 * fix that on the TV itself, where the TV has them, and a QR code to the website's page with this
 * TV's install command, which does all of it.
 */
@Composable
private fun SetupPage(vm: HomeViewModel, takeFocus: Boolean, onOpen: (android.content.Intent) -> Unit) {
    val context = LocalContext.current
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (!takeFocus) return@LaunchedEffect
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    val guard = remember { SystemProbe.guardSettingsIntent(context) }
    val home = remember { SystemProbe.homeSettingsIntent(context) }
    val revival = remember { SystemProbe.revivalSettingsIntent(context) }
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(Unit) {
        val url = "https://github.com/Null9797/halo-tv/blob/main/INSTALL.md"
        qr = withContext(Dispatchers.Default) { PhoneServer.qr(url).asImageBitmap() }
    }
    Column {
        MenuRow(Glyph.ChevronLeft, stringResource(R.string.cc_setup_title), focusRequester = first, onClick = { vm.overlay = Overlay.ControlCenter(CcPage.Main) })
        BasicText(stringResource(R.string.cc_setup_body), style = Type.Caption, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
        guard?.let { MenuRow(Glyph.Shield, stringResource(R.string.cc_setup_guard), onClick = { vm.whenUnlocked { onOpen(it) } }, trailing = stringResource(R.string.cc_setup_guard_where) + " ›") }
        home?.let { MenuRow(Glyph.Check, stringResource(R.string.cc_setup_home), onClick = { vm.whenUnlocked { onOpen(it) } }, trailing = "›") }
        revival?.let { MenuRow(Glyph.Moon, stringResource(R.string.cc_setup_revival), onClick = { vm.whenUnlocked { onOpen(it) } }, trailing = stringResource(R.string.cc_setup_revival_where) + " ›") }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp)) {
            Box(Modifier.size(104.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp)) {
                qr?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize()) }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                BasicText(stringResource(R.string.cc_setup_scan), style = Type.Body)
                Spacer(Modifier.height(6.dp))
                BasicText(stringResource(R.string.cc_setup_scan_hint), style = Type.Caption)
            }
        }
    }
}

/** A round Control Center button: frosted at rest, white when focused. */
@Composable
private fun RoundButton(glyph: Glyph, label: String, focusRequester: FocusRequester? = null, onClick: () -> Unit) {
    Pressable(onClick = onClick, focusRequester = focusRequester, focusedScale = 1.1f) { state ->
        val bg by animateColorAsState(if (state.focused) Palette.Focus else Palette.Fill, tween(140, easing = Motion.EaseOut), label = "round-bg")
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
            Box(Modifier.size(48.dp).clip(CircleShape).drawBehind { drawRect(bg) }, contentAlignment = Alignment.Center) {
                GlyphIcon(glyph, if (state.focused) Palette.OnFocus else Palette.Label, 20.dp)
            }
            Spacer(Modifier.height(6.dp))
            BasicText(
                label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = Type.Caption.copy(color = if (state.focused) Palette.Label else Palette.SecondaryLabel, textAlign = TextAlign.Center),
            )
        }
    }
}

/**
 * A Control Center slider: focus it, then Left and Right change the level, OK does [onClick].
 * Shared by volume and brightness so both feel identical. On a touch screen a tap does [onClick]
 * and a sideways drag steps the level, [steps] steps across the width of the row.
 */
@Composable
private fun SliderRow(
    glyph: Glyph,
    level: Float,
    value: String,
    steps: Int,
    onStep: (Int) -> Unit,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    // Each step ticks, as focus does; a step past either end bumps instead. Read through state:
    // the drag handler below is made once and outlives this composition's values.
    val atLevel by rememberUpdatedState(level)
    val doStep by rememberUpdatedState(onStep)
    val step = { by: Int ->
        if ((by > 0 && atLevel >= 0.999f) || (by < 0 && atLevel <= 0.001f)) Sounds.limit() else Sounds.step()
        doStep(by)
    }
    val stepCount by rememberUpdatedState(steps.coerceAtLeast(1))
    Pressable(
        onClick = onClick,
        focusedScale = 1.03f,
        focusRequester = focusRequester,
        modifier = modifier.fillMaxWidth(),
        onKey = { e ->
            val code = e.nativeKeyEvent.keyCode
            if (code != NativeKeyEvent.KEYCODE_DPAD_LEFT && code != NativeKeyEvent.KEYCODE_DPAD_RIGHT) return@Pressable false
            if (e.type == KeyEventType.KeyDown) step(if (code == NativeKeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1)
            true
        },
    ) { state ->
        val bg by animateColorAsState(if (state.focused) Palette.Focus else Palette.Fill, tween(140, easing = Motion.EaseOut), label = "slider-bg")
        val fg = if (state.focused) Palette.OnFocus else Palette.Label
        val fraction by animateFloatAsState(level, spring(dampingRatio = 1f, stiffness = 700f), label = "slider-level")
        Row(
            Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    // The level only moves in the steps the system takes, so the drag is counted
                    // out in steps rather than set to an exact point.
                    var travelled = 0f
                    detectHorizontalDragGestures(onDragStart = { travelled = 0f }) { change, dx ->
                        change.consume()
                        travelled += dx
                        val pitch = size.width.toFloat() / stepCount
                        while (travelled >= pitch) { step(1); travelled -= pitch }
                        while (travelled <= -pitch) { step(-1); travelled += pitch }
                    }
                }
                .smoothBackground({ bg }, 14.dp).height(40.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlyphIcon(glyph, fg, 18.dp, level = level)
            Canvas(Modifier.weight(1f).height(6.dp)) {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(fg.copy(alpha = 0.2f), cornerRadius = r)
                drawRoundRect(fg, size = Size(size.width * fraction, size.height), cornerRadius = r)
            }
            BasicText(
                value,
                style = Type.Headline.copy(color = fg, textAlign = TextAlign.End),
                modifier = Modifier.widthIn(min = 28.dp),
            )
        }
    }
}

@Composable
private fun VolumeRow(vm: HomeViewModel, focusRequester: FocusRequester, modifier: Modifier = Modifier) {
    val volume = vm.volume
    SliderRow(
        glyph = if (volume.muted) Glyph.SpeakerMuted else Glyph.Speaker,
        level = if (volume.muted) 0f else volume.fraction,
        value = if (volume.muted) stringResource(R.string.cc_muted) else volume.level.toString(),
        steps = volume.max,
        onStep = vm::adjustVolume,
        onClick = vm::toggleMute,
        focusRequester = focusRequester,
        modifier = modifier,
    )
}

/**
 * Brightness where Aerial may change it; otherwise a row that opens the TV's picture settings, and
 * says so: a bare "Brightness" with no slider read as broken (Vidda 85V1F-S, FFalcon, issue #130).
 */
@Composable
private fun BrightnessRow(vm: HomeViewModel, focusRequester: FocusRequester, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // XGIMI (GMUI): the real brightness is the DLP light-engine level. It reads through the HAL
    // class (SystemProbe.xgimiBrightnessLevel) and, on this firmware, writes through it too, so the
    // row is a slider of the light engine's own levels (1..10) rather than a percentage of the
    // system setting that moves nothing. Poll the HAL while the row is on screen: the level can be
    // changed from the TV's own overlay, which does not pause Aerial, so no onResume or
    // screen_brightness observer ever fires for it.
    val xgimi = remember { SystemProbe.xgimiBrightnessLevel() != null }
    LaunchedEffect(xgimi) {
        if (!xgimi) return@LaunchedEffect
        vm.discoverBrightnessBoost()
        while (isActive) {
            delay(700)
            vm.refreshBrightness()
        }
    }
    val level = vm.brightness
    if (level == null) {
        Pressable(onClick = {
            vm.overlay = Overlay.None
            SystemProbe.openPictureSettings(context)
        }, modifier = modifier.fillMaxWidth(), focusRequester = focusRequester, focusedScale = 1.03f) { state ->
            val fg = if (state.focused) Palette.OnFocus else Palette.Label
            Row(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(14.dp))
                .background(if (state.focused) Palette.Focus else Palette.Fill).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlyphIcon(Glyph.Sun, fg, 18.dp)
                BasicText(stringResource(R.string.cc_brightness_in_settings),
                    style = Type.Caption.copy(color = fg.copy(alpha = 0.8f), fontSize = 10.sp),
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText("›", style = Type.Body.copy(color = fg))
            }
        }
    } else {
        SliderRow(
            glyph = Glyph.Sun,
            level = level,
            value = if (vm.brightnessBoost == true) "10+" else Math.round(level * 100).toString(),
            // Brightness.adjust moves 5% a step, and stops at 1%; XGIMI steps its own levels.
            steps = if (xgimi) SystemProbe.xgimiBrightnessMax else 20,
            onStep = vm::adjustBrightness,
            onClick = {},
            focusRequester = focusRequester,
            modifier = modifier,
        )
    }
}

@Composable
private fun InputsPage(vm: HomeViewModel, takeFocus: Boolean, onOpen: (android.content.Intent) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (!takeFocus) return@LaunchedEffect
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    Column(Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState())) {
        MenuRow(Glyph.ChevronLeft, stringResource(R.string.cc_inputs), onClick = { vm.overlay = Overlay.ControlCenter(CcPage.Main) })
        Spacer(Modifier.height(8.dp))
        if (vm.sources.isEmpty()) {
            BasicText(stringResource(R.string.cc_no_inputs), style = Type.Callout, modifier = Modifier.padding(14.dp))
        }
        val connected = stringResource(R.string.input_connected)
        vm.sources.forEachIndexed { i, source ->
            MenuRow(
                glyph = when (source.kind) {
                    SourceKind.Hdmi, SourceKind.DisplayPort -> Glyph.Hdmi
                    SourceKind.Tuner -> Glyph.Antenna
                    SourceKind.Av -> Glyph.Av
                    SourceKind.Usb -> Glyph.Usb
                    SourceKind.Other -> Glyph.Input
                },
                text = source.label,
                // What the owner named the input on its tile, else what HDMI-CEC reports.
                trailing = SourceLooks.of(source.id)?.let { it.name ?: it.device?.let { d -> stringResource(d.label) } }
                    ?: source.deviceName ?: if (source.connected) connected else null,
                focusRequester = if (i == 0) first else null,
                onClick = { onOpen(source.intent) },
            )
        }
        // Switching is this page's one job; what Aerial does with the inputs is a page further in.
        if (vm.sources.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            MenuRow(Glyph.Gear, stringResource(R.string.cc_inputs_settings), onClick = { vm.overlay = Overlay.ControlCenter(CcPage.InputSettings) })
        }
    }
}

/**
 * What Aerial does with the inputs: which ones are tiles on Home (any number) and which one the
 * TV starts on (one, or none). Kept apart from the list that switches inputs.
 */
@Composable
private fun InputSettingsPage(vm: HomeViewModel, takeFocus: Boolean) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (!takeFocus) return@LaunchedEffect
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    fun name(source: app.orchard.tv.system.TvSource) = source.deviceName?.let { "${source.label} · $it" } ?: source.label
    Column(Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState())) {
        MenuRow(Glyph.ChevronLeft, stringResource(R.string.cc_inputs_settings), onClick = { vm.overlay = Overlay.ControlCenter(CcPage.Inputs) })

        Spacer(Modifier.height(10.dp))
        BasicText(stringResource(R.string.cc_inputs_pin), style = Type.Callout, modifier = Modifier.padding(start = 14.dp, bottom = 6.dp))
        vm.sources.forEachIndexed { i, source ->
            val pinned = source.id in AerialSettings.pinnedSources
            ChoiceRow(
                text = name(source),
                selected = pinned,
                focusRequester = if (i == 0) first else null,
                onClick = { vm.setSourcePinned(source, !pinned) },
            )
        }
        BasicText(
            stringResource(R.string.cc_inputs_pin_hint),
            style = Type.Caption,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp),
        )

        Spacer(Modifier.height(18.dp))
        BasicText(stringResource(R.string.cc_inputs_boot), style = Type.Callout, modifier = Modifier.padding(start = 14.dp, bottom = 6.dp))
        ChoiceRow(
            text = stringResource(R.string.cc_inputs_boot_none),
            selected = !AerialSettings.hasBootTarget,
            focusRequester = null,
            onClick = { vm.chooseBootTarget() },
        )
        // An app is chosen from its tile's menu; here it only shows, and "nothing" clears it.
        AerialSettings.bootApp?.let { pkg ->
            ChoiceRow(
                text = vm.entry(pkg)?.label ?: pkg,
                selected = true,
                focusRequester = null,
                onClick = {},
            )
        }
        vm.sources.forEach { source ->
            ChoiceRow(
                text = name(source),
                selected = source.id == AerialSettings.bootSource,
                focusRequester = null,
                onClick = { vm.chooseBootTarget(sourceId = source.id) },
            )
        }
        BasicText(
            stringResource(R.string.cc_inputs_boot_hint),
            style = Type.Caption,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp),
        )
    }
}

/** A row in a list of choices: the chosen one carries a check, as in tvOS settings. */
@Composable
private fun ChoiceRow(text: String, selected: Boolean, focusRequester: FocusRequester?, onClick: () -> Unit) {
    Pressable(onClick = onClick, focusRequester = focusRequester, focusedScale = 1.03f, modifier = Modifier.fillMaxWidth()) { state ->
        val bg by animateColorAsState(if (state.focused) Palette.Focus else Color.Transparent, tween(140, easing = Motion.EaseOut), label = "choice-bg")
        val fg = if (state.focused) Palette.OnFocus else Palette.Label
        Row(
            Modifier.fillMaxWidth().smoothBackground({ bg }, 12.dp).height(44.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(text, style = Type.Body.copy(color = fg), modifier = Modifier.weight(1f))
            if (selected) GlyphIcon(Glyph.Check, fg, 16.dp)
        }
    }
}
