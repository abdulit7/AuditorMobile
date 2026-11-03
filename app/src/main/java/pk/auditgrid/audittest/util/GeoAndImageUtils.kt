package pk.auditgrid.audittest.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.CancellationSignal
import android.os.Looper
import android.util.Base64
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

const val DEFAULT_ALLOWED_RADIUS_M = 150
const val MAX_ACCEPTABLE_ACCURACY_M = 100

data class DevicePosition(
    val lat: Double,
    val lng: Double,
    val accuracy: Double,
    val at: String
)

enum class GpsErrorKind {
    DENIED, UNAVAILABLE, TIMEOUT, UNSUPPORTED
}

class GpsException(val kind: GpsErrorKind) : Exception(gpsErrorMessage(kind))

fun gpsErrorMessage(kind: GpsErrorKind): String = when (kind) {
    GpsErrorKind.DENIED ->
        "Location permission is off. Allow location for this app in Android settings, then tap \"Try again\"."
    GpsErrorKind.TIMEOUT ->
        "Getting your location took too long. Move to an open area and tap \"Try again\"."
    GpsErrorKind.UNSUPPORTED ->
        "This device does not support location services."
    GpsErrorKind.UNAVAILABLE ->
        "Your location is not available right now. Check that device location is on and tap \"Try again\"."
}

fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

@SuppressLint("MissingPermission")
suspend fun getDevicePosition(context: Context, timeoutMs: Long = 15000L): DevicePosition {
    if (!hasLocationPermission(context)) {
        throw GpsException(GpsErrorKind.DENIED)
    }
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: throw GpsException(GpsErrorKind.UNSUPPORTED)

    val gpsEnabled = runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
    val netEnabled = runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)
    if (!gpsEnabled && !netEnabled) {
        throw GpsException(GpsErrorKind.UNAVAILABLE)
    }

    // Check recent last-known location first (within 30 seconds)
    val now = System.currentTimeMillis()
    val candidates = listOfNotNull(
        runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull(),
        runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull(),
        runCatching { lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER) }.getOrNull()
    )
    val freshLast = candidates
        .filter { now - it.time <= 30_000L }
        .minByOrNull { it.accuracy }
    if (freshLast != null) {
        return freshLast.toDevicePosition()
    }

    val provider = if (gpsEnabled) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
    val loc = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine<Location?> { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                try {
                    lm.getCurrentLocation(
                        provider,
                        signal,
                        ContextCompat.getMainExecutor(context)
                    ) { location ->
                        if (cont.isActive) cont.resume(location)
                    }
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        lm.removeUpdates(this)
                        if (cont.isActive) cont.resume(location)
                    }
                }
                try {
                    lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                    cont.invokeOnCancellation { lm.removeUpdates(listener) }
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    } ?: candidates.minByOrNull { it.accuracy }

    if (loc == null) {
        throw GpsException(GpsErrorKind.TIMEOUT)
    }
    return loc.toDevicePosition()
}

private fun Location.toDevicePosition(): DevicePosition {
    val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(if (time > 0) time else System.currentTimeMillis()))
    return DevicePosition(
        lat = latitude,
        lng = longitude,
        accuracy = if (hasAccuracy()) accuracy.toDouble() else 25.0,
        at = iso
    )
}

fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371000.0
    val rad = { d: Double -> d * Math.PI / 180.0 }
    val dLat = rad(lat2 - lat1)
    val dLng = rad(lng2 - lng1)
    val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
        cos(rad(lat1)) * cos(rad(lat2)) * sin(dLng / 2.0) * sin(dLng / 2.0)
    return 2.0 * r * asin(min(1.0, sqrt(a)))
}

fun calculateDistanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Int =
    haversineMeters(lat1, lng1, lat2, lng2).roundToInt()

fun makeReportCode(prefix: String): String {
    val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    val rand = (1..4).map { chars.random() }.joinToString("")
    val stamp = System.currentTimeMillis().toString(36).takeLast(4).uppercase(Locale.US)
    val year = Calendar.getInstance().get(Calendar.YEAR)
    return "$prefix-$year-$stamp$rand"
}

fun newSubmitKey(): String = UUID.randomUUID().toString()

fun pktToday(): String {
    val pktMillis = System.currentTimeMillis() + 5L * 3600_000L
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return sdf.format(Date(pktMillis))
}

fun prettyDate(dateStr: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = parser.parse(dateStr) ?: return dateStr
        val fmt = SimpleDateFormat("EEE, d MMM yyyy", Locale.UK).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        fmt.format(date)
    } catch (_: Exception) {
        dateStr
    }
}

fun currentReportDateDisplay(): String {
    val cal = Calendar.getInstance()
    val months = arrayOf(
        "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
        "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER"
    )
    return "${cal.get(Calendar.DAY_OF_MONTH)} - ${months[cal.get(Calendar.MONTH)]} - ${cal.get(Calendar.YEAR)}."
}

fun currentIsoDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

fun currentTimePkt(): String = "${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())} PKT"

fun currentIsoTimestamp(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}.format(Date())

fun directionsUrl(lat: Double, lng: Double, fromLat: Double? = null, fromLng: Double? = null): String {
    val origin = if (fromLat != null && fromLng != null) "&origin=$fromLat,$fromLng" else ""
    return "https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=driving$origin"
}

fun hasCoords(lat: Double, lng: Double): Boolean =
    !(lat == 0.0 && lng == 0.0) && abs(lat) <= 90.0 && abs(lng) <= 180.0

fun compressBitmapToDataUrl(bitmap: Bitmap, maxDim: Int = 1600, quality: Int = 72): String {
    val maxSide = max(bitmap.width, bitmap.height)
    val scale = min(1.0f, maxDim.toFloat() / maxSide.toFloat())
    val scaled = if (scale < 1.0f) {
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true
        )
    } else {
        bitmap
    }
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
    val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    return "data:image/jpeg;base64,$b64"
}

suspend fun compressUriToDataUrl(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val bmp = BitmapFactory.decodeStream(stream) ?: return@withContext null
            compressBitmapToDataUrl(bmp)
        }
    } catch (_: Exception) {
        null
    }
}

fun dataUrlToBytes(dataUrl: String): Pair<String, ByteArray> {
    val comma = dataUrl.indexOf(',')
    if (comma < 0) return "image/jpeg" to ByteArray(0)
    val head = dataUrl.substring(0, comma)
    val b64 = dataUrl.substring(comma + 1)
    val mime = Regex("data:([^;]+)").find(head)?.groupValues?.getOrNull(1) ?: "image/jpeg"
    val bytes = Base64.decode(b64, Base64.DEFAULT)
    return mime to bytes
}
