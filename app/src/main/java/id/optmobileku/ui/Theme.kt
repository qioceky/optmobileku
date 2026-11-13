package id.optmobileku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Palet soft neobrutalism Opt Mobileku (sama dengan MikroKu & RuangKlon). */
val Paper = Color(0xFFFFFDF7)
val Ink = Color(0xFF161616)
val OptYellow = Color(0xFFFFD02E)
val OptGreen = Color(0xFF2FBF71)
val OptRed = Color(0xFFFF5A5A)
val OptBlue = Color(0xFFDCEEFF)
val Muted = Color(0xFF666660)

private val OptScheme = lightColorScheme(
    primary = OptYellow,
    onPrimary = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    error = OptRed,
    onError = Color.White
)

@Composable
fun OptMobilekuTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OptScheme, content = content)
}

/** Kartu khas neobrutal: border tebal + bayangan offset keras. */
@Composable
fun NeoCard(
    modifier: Modifier = Modifier,
    fill: Color = Color.White,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = modifier.padding(end = 4.dp, bottom = 4.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Ink)
        )
        val inner = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(fill)
            .border(2.5.dp, Ink, RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(12.dp)
        Column(inner, content = content)
    }
}

@Composable
fun NeoButton(
    text: String,
    modifier: Modifier = Modifier,
    fill: Color = OptYellow,
    textColor: Color = Ink,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(modifier = modifier.padding(end = 4.dp, bottom = 4.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Ink)
        )
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (enabled) fill else Color(0xFFDDDDD5))
                .border(2.5.dp, Ink, RoundedCornerShape(12.dp))
                .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
                .padding(vertical = 11.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text(text, fontWeight = FontWeight.Black, fontSize = 15.sp, color = textColor)
        }
    }
}

@Composable
fun StatusDot(online: Boolean?, modifier: Modifier = Modifier) {
    val color = when (online) {
        true -> OptGreen
        false -> OptRed
        null -> Color(0xFFCCCCCC)
    }
    Box(
        modifier
            .padding(1.dp)
            .clip(RoundedCornerShape(50))
            .background(color)
            .border(2.dp, Ink, RoundedCornerShape(50))
            .padding(4.5.dp)
    )
}

fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "-"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024
        i++
    }
    return if (i == 0) "$bytes B" else String.format("%.1f %s", v, units[i])
}
