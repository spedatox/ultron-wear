package com.spedatox.ultroncore.presentation

import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material3.Text
import com.spedatox.ultroncore.R
import com.spedatox.ultroncore.UltronWear
import com.spedatox.ultroncore.data.AttendanceStatus
import com.spedatox.ultroncore.design.GlassRadiusSmall
import com.spedatox.ultroncore.design.LocalUltronPalette
import com.spedatox.ultroncore.design.ThemeEngine
import com.spedatox.ultroncore.design.UltronPalette
import com.spedatox.ultroncore.design.UltronTheme
import com.spedatox.ultroncore.design.UltronType
import com.spedatox.ultroncore.design.ultronGlass
import com.spedatox.ultroncore.notification.AttendanceAsk
import com.spedatox.ultroncore.notification.AttendanceNotifier
import com.spedatox.ultroncore.sync.SyncScheduler
import kotlinx.coroutines.launch

/**
 * The full-screen "derse girdin mi?" prompt.
 *
 * This is the fallback path, not the primary one — the notification's three
 * action buttons answer without ever starting an Activity, which is both faster
 * and the right interaction on a wrist. This screen exists for tapping through
 * from the notification body, and for answering a question you dismissed
 * earlier.
 */
class AttendanceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ask = readAsk()
        if (ask == null) {
            finish()
            return
        }

        val palette = ThemeEngine.palette
        setContent {
            UltronTheme {
                CompositionLocalProvider(LocalUltronPalette provides palette) {
                    AttendancePrompt(
                        ask = ask,
                        palette = palette,
                        onAnswer = { status -> answer(ask, status) },
                    )
                }
            }
        }
    }

    private fun readAsk(): AttendanceAsk? {
        val extras = intent?.extras ?: return null
        val payload = listOf(
            AttendanceAsk.KEY_SLOT,
            AttendanceAsk.KEY_CODE,
            AttendanceAsk.KEY_NAME,
            AttendanceAsk.KEY_DATE,
            AttendanceAsk.KEY_TIME,
            AttendanceAsk.KEY_ROOM,
        ).associateWith { extras.getString(it) }
        return AttendanceAsk.from(payload)
    }

    private fun answer(ask: AttendanceAsk, status: AttendanceStatus) {
        getSystemService(Vibrator::class.java)?.takeIf { it.hasVibrator() }?.vibrate(
            VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
        )
        val app = UltronWear.from(this)
        lifecycleScope.launch {
            app.attendance.load()
            app.attendance.record(ask.slotId, ask.courseCode, ask.date, status)
            AttendanceNotifier(this@AttendanceActivity).dismiss(ask.notificationId)
            SyncScheduler.syncNow(this@AttendanceActivity)
            finish()
        }
    }
}

@Composable
private fun AttendancePrompt(
    ask: AttendanceAsk,
    palette: UltronPalette,
    onAnswer: (AttendanceStatus) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = ask.courseName,
            style = UltronType.title,
            color = palette.text,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = buildString {
                append(ask.timeLabel)
                if (ask.room.isNotBlank()) append(" · ").append(ask.room)
            },
            style = UltronType.readout,
            color = palette.textFaint,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = stringResource(R.string.ask_did_you_attend),
            style = UltronType.head,
            color = palette.accentBright,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(12.dp))

        AnswerButton(
            label = stringResource(R.string.answer_yes),
            tint = palette.green,
            palette = palette,
        ) { onAnswer(AttendanceStatus.ATTENDED) }

        Spacer(Modifier.height(6.dp))

        AnswerButton(
            label = stringResource(R.string.answer_no),
            tint = palette.red,
            palette = palette,
        ) { onAnswer(AttendanceStatus.ABSENT) }

        Spacer(Modifier.height(6.dp))

        AnswerButton(
            label = stringResource(R.string.answer_cancelled),
            tint = palette.textDim,
            palette = palette,
        ) { onAnswer(AttendanceStatus.CANCELLED) }
    }
}

@Composable
private fun AnswerButton(
    label: String,
    tint: Color,
    palette: UltronPalette,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .ultronGlass(palette, radius = GlassRadiusSmall, accentRim = tint.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            // 40dp of vertical target: a mis-tap here writes the wrong answer
            // into a ledger that decides whether a course is passed.
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = UltronType.body, color = tint)
    }
}
