package app.orchard.tv.ui.home

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orchard.tv.R
import app.orchard.tv.ui.components.Pressable
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type

/** A fresh install gets one guide; an upgrade keeps the user's existing home untouched. */
object FirstRunGuideStore {
    private const val PREFS = "first_run_guide"
    private const val FINISHED = "finished"

    fun shouldShow(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(FINISHED, false)) return false
        val info = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull() ?: return false
        // Android records these at the same instant only for a clean installation. This avoids
        // showing a new guide on an existing TV merely because the guide was added in an update.
        return info.firstInstallTime == info.lastUpdateTime
    }

    fun finish(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(FINISHED, true).commit()
    }
}

private val Ink = Color(0xFF0A1426)
private val Accent = Color(0xFF7FB6FF)

@Composable
fun FirstRunGuide(onFinished: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val nextFocus = remember { FocusRequester() }
    val context = LocalContext.current
    BackHandler(page > 0) { page-- }
    LaunchedEffect(page) { withFrameNanos { }; nextFocus.requestFocus() }

    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Ink, Color(0xFF132C4D), Ink)))) {
        Box(Modifier.align(Alignment.TopEnd).size(360.dp)
            .background(Brush.radialGradient(listOf(Color(0x404B9CFF), Color.Transparent))))

        Column(Modifier.fillMaxSize().padding(horizontal = 62.dp, vertical = 28.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.halo_tv_icon), null,
                    Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)))
                Spacer(Modifier.width(12.dp))
                BasicText("Hi TV", style = Type.Title)
                Spacer(Modifier.weight(1f))
                BasicText(stringResource(R.string.guide_step, page + 1, 7),
                    style = Type.Caption.copy(color = Palette.SecondaryLabel))
            }
            Spacer(Modifier.height(19.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .12f)))
            Spacer(Modifier.height(22.dp))

            if (page == 6) {
                Column(Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Image(painterResource(R.drawable.halo_tv_icon), null,
                        Modifier.size(170.dp).clip(RoundedCornerShape(34.dp)))
                    Spacer(Modifier.height(20.dp))
                    BasicText(stringResource(R.string.guide_final_title),
                        style = Type.LargeTitle.copy(fontSize = 38.sp, fontWeight = FontWeight.SemiBold))
                    Spacer(Modifier.height(10.dp))
                    BasicText(stringResource(R.string.guide_final_body),
                        style = Type.Body.copy(color = Palette.SecondaryLabel))
                }
            } else Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(34.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    BasicText(stringResource(titleFor(page)),
                        style = Type.LargeTitle.copy(fontSize = if (page == 5) 31.sp else 35.sp,
                            fontWeight = FontWeight.SemiBold))
                    Spacer(Modifier.height(14.dp))
                    BasicText(stringResource(bodyFor(page)),
                        style = Type.Body.copy(color = Palette.SecondaryLabel,
                            fontSize = if (page == 5) 14.sp else 17.sp,
                            lineHeight = if (page == 5) 20.sp else 26.sp))
                    if (page == 2) {
                        Spacer(Modifier.height(22.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GuidePhoto(R.drawable.onboarding_null_avatar, Modifier.size(54.dp).clip(CircleShape))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                BasicText("Null_", style = Type.Title)
                                BasicText(stringResource(R.string.guide_original_author),
                                    style = Type.Caption.copy(color = Palette.SecondaryLabel))
                                Spacer(Modifier.height(4.dp))
                                BasicText(stringResource(R.string.guide_maintainer),
                                    style = Type.Caption.copy(color = Palette.SecondaryLabel))
                            }
                        }
                    }
                    if (page == 5) {
                        Spacer(Modifier.height(12.dp))
                        GuideTip("01", R.string.guide_setup_one)
                        GuideTip("02", R.string.guide_setup_two)
                        GuideTip("03", R.string.guide_setup_three)
                    }
                }
                Box(Modifier.weight(1.15f).height(310.dp).then(
                    if (page == 0) Modifier else Modifier.clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = .06f))
                        .border(1.dp, Color.White.copy(alpha = .15f), RoundedCornerShape(24.dp))),
                    contentAlignment = Alignment.Center) {
                    when (page) {
                        0 -> Image(painterResource(R.drawable.halo_tv_icon), null,
                            Modifier.size(210.dp).clip(RoundedCornerShape(42.dp)))
                        1 -> Column(Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            GuideUpdateRow("01", R.string.guide_update_dock)
                            GuideUpdateRow("02", R.string.guide_update_focus)
                            GuideUpdateRow("03", R.string.guide_update_performance)
                            GuideUpdateRow("04", R.string.guide_update_clock)
                            GuideUpdateRow("05", R.string.guide_update_text_color)
                            GuideUpdateRow("06", R.string.guide_update_control)
                        }
                        2 -> Column(horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(26.dp)) {
                            GuidePhoto(R.drawable.onboarding_null_avatar, Modifier.size(96.dp).clip(CircleShape))
                            Spacer(Modifier.height(16.dp))
                            BasicText("BenBen × Hi TV", style = Type.Title)
                            Spacer(Modifier.height(10.dp))
                            BasicText(stringResource(R.string.guide_origin_note),
                                style = Type.Caption.copy(color = Palette.SecondaryLabel))
                            Spacer(Modifier.height(6.dp))
                            BasicText(stringResource(R.string.guide_maintainer),
                                style = Type.Caption.copy(color = Palette.SecondaryLabel))
                        }
                        3 -> GuidePhoto(R.drawable.onboarding_home, Modifier.fillMaxSize().padding(12.dp)
                            .clip(RoundedCornerShape(16.dp)))
                        4 -> GuidePhoto(R.drawable.onboarding_control, Modifier.fillMaxSize().padding(12.dp)
                            .clip(RoundedCornerShape(16.dp)))
                        5 -> GuidePhoto(R.drawable.onboarding_settings, Modifier.fillMaxSize().padding(12.dp)
                            .clip(RoundedCornerShape(16.dp)))
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .12f)))
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    repeat(7) { index ->
                        Box(Modifier.size(if (index == page) 22.dp else 7.dp, 7.dp)
                            .clip(CircleShape)
                            .background(if (index == page) Accent else Color.White.copy(alpha = .25f)))
                    }
                }
                Spacer(Modifier.weight(1f))
                if (page == 6) {
                    GuideButton(stringResource(R.string.guide_done), true, nextFocus) {
                        FirstRunGuideStore.finish(context); onFinished()
                    }
                } else {
                    GuideButton(stringResource(if (page == 0) R.string.guide_begin else R.string.guide_next),
                        true, nextFocus) { page++ }
                }
            }
        }
    }
}

private fun titleFor(page: Int) = when (page) {
    0 -> R.string.guide_welcome_title
    1 -> R.string.guide_update_title
    2 -> R.string.guide_origin_title
    3 -> R.string.guide_home_title
    4 -> R.string.guide_features_title
    5 -> R.string.guide_setup_title
    else -> R.string.guide_setup_title
}

private fun bodyFor(page: Int) = when (page) {
    0 -> R.string.guide_welcome_body
    1 -> R.string.guide_update_body
    2 -> R.string.guide_origin_body
    3 -> R.string.guide_home_body
    4 -> R.string.guide_features_body
    5 -> R.string.guide_setup_body
    else -> R.string.guide_setup_body
}

@Composable
private fun GuideUpdateRow(number: String, label: Int) {
    Row(Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(12.dp))
        .background(Color.White.copy(alpha = .07f)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        BasicText(number, style = Type.Caption.copy(color = Accent))
        Spacer(Modifier.width(13.dp))
        BasicText(stringResource(label), style = Type.Body.copy(fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun GuidePhoto(id: Int, modifier: Modifier) {
    Image(painterResource(id), null, modifier, contentScale = ContentScale.Fit)
}

@Composable
private fun GuideTip(number: String, label: Int) {
    Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(number, style = Type.Caption.copy(color = Accent))
        Spacer(Modifier.width(12.dp))
        BasicText(stringResource(label), style = Type.Body.copy(fontSize = 13.sp, lineHeight = 18.sp))
    }
}

@Composable
private fun GuideButton(label: String, primary: Boolean, focus: FocusRequester?, onClick: () -> Unit) {
    Pressable(onClick = onClick, focusRequester = focus, focusedScale = 1.02f) { state ->
        Box(Modifier.width(if (primary) 192.dp else 142.dp).height(44.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (state.focused || primary) Color.White else Color.White.copy(alpha = .13f)),
            contentAlignment = Alignment.Center) {
            BasicText(label, style = Type.Body.copy(
                color = if (state.focused || primary) Ink else Color.White,
                fontWeight = FontWeight.SemiBold))
        }
    }
}
