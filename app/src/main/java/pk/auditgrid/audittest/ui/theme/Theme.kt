package pk.auditgrid.audittest.ui.theme

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pk.auditgrid.audittest.util.dataUrlToBytes
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

// Exact colors from src/index.css and AuditGrid mobile components
val AgPrimary = Color(0xFF1E3A5F)
val AgRoyalBlue = Color(0xFF0046B8)
val AgActionBlue = Color(0xFF0052CC)
val AgSecondaryBlue = Color(0xFF2563EB)
val AgAccentGreen = Color(0xFF16A34A)
val AgAccentGreenDark = Color(0xFF166534)
val AgAccentGreenBg = Color(0xFFDCFCE7)
val AgBackground = Color(0xFFF8FAFC)
val AgFormBg = Color(0xFFF2F6FC)
val AgSoftBlueBg = Color(0xFFEAF1FF)
val AgSoftBlueBorder = Color(0xFFDBEAFE)
val AgCardWhite = Color(0xFFFFFFFF)
val AgForeground = Color(0xFF0F172A)
val AgMutedBg = Color(0xFFE9EEF5)
val AgMutedText = Color(0xFF475569)
val AgBorder = Color(0xFFCBD5E1)
val AgLightBorder = Color(0xFFE2E8F0)
val AgDestructive = Color(0xFFDC2626)
val AgDestructiveDark = Color(0xFF991B1B)
val AgDestructiveBg = Color(0xFFFEE2E2)
val AgWarning = Color(0xFFB45309)
val AgWarningDark = Color(0xFF92400E)
val AgWarningBg = Color(0xFFFEF3C7)

private val AuditGridColorScheme = lightColorScheme(
    primary = AgPrimary,
    onPrimary = Color.White,
    primaryContainer = AgMutedBg,
    onPrimaryContainer = AgPrimary,
    secondary = AgSecondaryBlue,
    onSecondary = Color.White,
    secondaryContainer = AgSoftBlueBg,
    onSecondaryContainer = AgActionBlue,
    tertiary = AgAccentGreen,
    onTertiary = Color.White,
    background = AgBackground,
    onBackground = AgForeground,
    surface = AgCardWhite,
    onSurface = AgForeground,
    surfaceVariant = AgMutedBg,
    onSurfaceVariant = AgMutedText,
    outline = AgBorder,
    error = AgDestructive,
    onError = Color.White
)

@Composable
fun AuditGridTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AuditGridColorScheme,
        content = content
    )
}

fun formatPKR(amount: Double): String {
    val nf = NumberFormat.getIntegerInstance(Locale.US)
    return "Rs. ${nf.format(amount.roundToLong())}"
}

@Composable
fun GPSStatusChip(status: String) {
    val (color, icon, label) = when (status) {
        "GPS Verified" -> Triple(AgAccentGreen, Icons.Default.LocationOn, "GPS Verified")
        "Outside Allowed Radius" -> Triple(AgDestructive, Icons.Default.LocationOff, "Outside Allowed Radius")
        "Low GPS Accuracy" -> Triple(AgWarning, Icons.Default.SignalCellularAlt, "Low GPS Accuracy")
        "Location Unavailable" -> Triple(AgMutedText, Icons.Default.LocationOff, "Location Unavailable")
        "Awaiting Verification" -> Triple(AgSecondaryBlue, Icons.Default.Schedule, "Awaiting Verification")
        "Approved Exception" -> Triple(Color(0xFF7E22CE), Icons.Default.VerifiedUser, "Approved Exception")
        else -> Triple(AgMutedText, Icons.Default.LocationOn, status)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun EntityStatusIndicator(status: String) {
    val isActive = status.equals("Active", ignoreCase = true)
    val color = if (isActive) AgAccentGreen else AgMutedText
    val icon = if (isActive) Icons.Default.CheckCircle else Icons.Default.Cancel
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = status,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = if (isActive) "Active" else "Inactive",
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ProductGroupTag(groupName: String) {
    val lower = groupName.lowercase(Locale.US)
    val isGroupA = lower.contains("group a") || groupName == "GRP-A"
    val isGroupB = lower.contains("group b") || groupName == "GRP-B"

    val (bg, border, fg, label) = when {
        isGroupA -> Quad(Color(0x0D1E3A5F), AgPrimary.copy(alpha = 0.3f), AgPrimary, "Group A")
        isGroupB -> Quad(Color(0x0D134E4A), Color(0xFF0F766E).copy(alpha = 0.3f), Color(0xFF115E59), "Group B")
        else -> Quad(AgMutedBg.copy(alpha = 0.6f), AgBorder, AgForeground, groupName)
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, border)
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun CodeBadge(code: String, bg: Color = AgMutedBg, fg: Color = AgPrimary) {
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = code,
            color = fg,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DataUrlImage(
    dataUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val bmp = remember(dataUrl) {
        try {
            val (_, bytes) = dataUrlToBytes(dataUrl)
            if (bytes.isNotEmpty()) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } else null
        } catch (_: Exception) {
            null
        }
    }
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(AgMutedBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Photo",
                fontSize = 10.sp,
                color = AgMutedText
            )
        }
    }
}
