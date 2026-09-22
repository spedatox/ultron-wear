package com.spedatox.ultroncore.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Text
import com.spedatox.ultroncore.design.GlassRadius
import com.spedatox.ultroncore.design.UltronPalette
import com.spedatox.ultroncore.design.UltronType
import com.spedatox.ultroncore.design.ultronGlass
import com.spedatox.ultroncore.presentation.components.SectionLabel
import java.time.format.TextStyle
import java.util.Locale

/** Details for one scheduled class slot, reached by tapping its schedule card. */
@Composable
fun CourseDetailScreen(courseId: String, vm: UltronViewModel, palette: UltronPalette) {
    val courses by vm.courses.collectAsStateWithLifecycle()
    val course = courses.firstOrNull { it.id == courseId }

    if (course == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Ders bulunamadı", style = UltronType.head, color = palette.textDim)
        }
        return
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        item(key = "course-title") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SectionLabel("DERS DETAYI", palette)
                Text(
                    text = course.name,
                    style = UltronType.head,
                    color = palette.text,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(course.code, style = UltronType.readout, color = palette.accentBright)
            }
        }
        item(key = "course-schedule") {
            DetailCard(
                label = "ZAMAN",
                value = "${course.dayOfWeek.getDisplayName(TextStyle.FULL, TURKISH)} · ${course.timeString}",
                palette = palette,
            )
        }
        item(key = "course-room") {
            DetailCard(label = "DERSLİK", value = course.roomNumber.ifBlank { "Belirtilmemiş" }, palette = palette)
        }
        item(key = "course-instructor") {
            DetailCard(label = "ÖĞRETİM ELEMANI", value = course.instructor.ifBlank { "Belirtilmemiş" }, palette = palette)
        }
    }
}

@Composable
private fun DetailCard(label: String, value: String, palette: UltronPalette) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .ultronGlass(palette, radius = GlassRadius)
            .padding(horizontal = 13.dp, vertical = 10.dp),
    ) {
        SectionLabel(label, palette, Modifier.padding(horizontal = 0.dp, vertical = 0.dp))
        Text(value, style = UltronType.body, color = palette.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

private val TURKISH: Locale = Locale.forLanguageTag("tr")
