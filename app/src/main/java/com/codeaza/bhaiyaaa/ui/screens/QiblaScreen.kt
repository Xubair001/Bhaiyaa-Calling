package com.codeaza.bhaiyaaa.ui.screens

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeaza.bhaiyaaa.prayer.QiblaCalculator
import com.codeaza.bhaiyaaa.ui.components.EmptyState
import com.codeaza.bhaiyaaa.ui.components.InfoBanner
import com.codeaza.bhaiyaaa.ui.components.SectionCard
import com.codeaza.bhaiyaaa.ui.prayer.PrayerViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Which way to face.
 *
 * Entirely offline: the bearing is spherical trigonometry over the coordinates
 * already stored for prayer times, and the heading comes from the device's own
 * sensors. Nothing is fetched and nothing is sent.
 *
 * **True north, not magnetic north.** A compass points at the magnetic pole,
 * which in places is more than fifteen degrees away from true north - enough to
 * miss the Kaaba by a country. `GeomagneticField` corrects for it using a model
 * built into Android, so this stays offline while still being right.
 */
@Composable
fun QiblaScreen(viewModel: PrayerViewModel, onOpenPrayerSettings: () -> Unit) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val latitude = settings.latitude
    val longitude = settings.longitude

    if (latitude == null || longitude == null) {
        EmptyState(
            icon = Icons.Filled.Explore,
            title = "Set your location first",
            body = "The qibla is worked out from where you are. Sukoon uses the same " +
                "coordinates as your prayer times, and they never leave this phone.",
            actionLabel = "Open prayer settings",
            onAction = onOpenPrayerSettings
        )
        return
    }

    val qiblaBearing = remember(latitude, longitude) {
        QiblaCalculator.bearingFrom(latitude, longitude)
    }
    val distanceKm = remember(latitude, longitude) {
        QiblaCalculator.distanceKmFrom(latitude, longitude)
    }
    val atKaaba = remember(latitude, longitude) {
        QiblaCalculator.isAtKaaba(latitude, longitude)
    }

    val heading = rememberDeviceHeading(latitude, longitude)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (atKaaba) {
            item {
                InfoBanner(text = "You are at the Kaaba. There is no direction to point.")
            }
        }

        if (heading == null) {
            item {
                InfoBanner(
                    text = "This phone has no compass, so the dial can't turn. The bearing " +
                        "below is still correct — face " +
                        "${qiblaBearing.roundToInt()}° from true north."
                )
            }
        }

        item {
            SectionCard(title = "Qibla") {
                Box(
                    Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    QiblaDial(
                        qiblaBearing = qiblaBearing,
                        heading = heading,
                        modifier = Modifier.size(DIAL_SIZE_DP.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "${qiblaBearing.roundToInt()}° — ${QiblaCalculator.cardinal(qiblaBearing)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics {
                        // The dial is decorative to a screen reader; this line
                        // carries the whole answer.
                        contentDescription =
                            "Qibla is ${qiblaBearing.roundToInt()} degrees from true north, " +
                                "to the ${QiblaCalculator.cardinal(qiblaBearing)}."
                    }
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = String.format(
                        Locale.getDefault(),
                        "%,.0f km to Makkah · from %s",
                        distanceKm,
                        settings.locationLabel.ifBlank { "your coordinates" }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(title = "Getting a good reading") {
                Text(
                    "A phone compass drifts near metal, speakers and car dashboards. If the " +
                        "dial wanders, move away from them and turn the phone in a figure of " +
                        "eight a few times to recalibrate it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "The bearing itself is arithmetic and is always right. It is the compass " +
                        "underneath it that can be off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * The dial.
 *
 * Drawn with Compose Canvas rather than an image, so it costs nothing in the
 * APK and follows the theme in light and dark. Two marks: a fixed pointer at
 * the top showing where the phone is facing, and the Kaaba's direction rotating
 * relative to it. Line them up and you are facing the qibla.
 */
@Composable
private fun QiblaDial(
    qiblaBearing: Double,
    heading: Float?,
    modifier: Modifier = Modifier
) {
    // Where the Kaaba sits relative to the way the phone is pointing. With no
    // compass the dial is drawn as if facing true north, so the arrow still
    // shows the bearing correctly against the ring's north mark.
    val target = (qiblaBearing - (heading ?: 0f)).toFloat()

    // Animated so the needle glides instead of jittering with every sensor
    // sample. The accumulated angle is deliberately not wrapped to 0..360: the
    // turn is applied as the shortest one each time, so going from 350 to 10
    // degrees nudges forward twenty rather than spinning almost the whole way
    // back through zero.
    //
    // Updated in an effect rather than in the composition body - writing state
    // while composing is what produces a frame that disagrees with the next
    // one, and Compose has no obligation to run a body only once.
    var unwrapped by remember { mutableFloatStateOf(target) }
    LaunchedEffect(target) {
        unwrapped += QiblaCalculator.shortestTurn(unwrapped.toDouble(), target.toDouble())
            .toFloat()
    }
    val angle by animateFloatAsState(
        targetValue = unwrapped,
        animationSpec = tween(NEEDLE_TWEEN_MILLIS),
        label = "qibla"
    )

    val ring = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val centre = Offset(size.width / 2f, size.height / 2f)

            drawCircle(color = ring, radius = radius, style = Stroke(width = RING_STROKE))

            // Tick every thirty degrees, longer at the cardinals, so the ring
            // reads as a compass rather than a plain circle.
            for (degrees in 0 until 360 step 30) {
                val isCardinal = degrees % 90 == 0
                val length = if (isCardinal) radius * 0.16f else radius * 0.08f
                val radians = Math.toRadians(degrees.toDouble() - 90)
                val outer = Offset(
                    centre.x + (radius * kotlin.math.cos(radians)).toFloat(),
                    centre.y + (radius * kotlin.math.sin(radians)).toFloat()
                )
                val inner = Offset(
                    centre.x + ((radius - length) * kotlin.math.cos(radians)).toFloat(),
                    centre.y + ((radius - length) * kotlin.math.sin(radians)).toFloat()
                )
                drawLine(
                    color = if (isCardinal) onSurface else ring,
                    start = inner,
                    end = outer,
                    strokeWidth = if (isCardinal) RING_STROKE else RING_STROKE / 2
                )
            }
        }

        // The needle. Rotated as a whole rather than redrawn, so the animation
        // is a transform rather than a new Path every frame.
        Canvas(
            Modifier
                .fillMaxSize()
                .rotate(angle)
        ) {
            val radius = size.minDimension / 2f
            val centre = Offset(size.width / 2f, size.height / 2f)
            val tip = Offset(centre.x, centre.y - radius * 0.78f)
            val half = radius * 0.11f

            drawPath(
                path = Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo(centre.x - half, centre.y + radius * 0.10f)
                    lineTo(centre.x + half, centre.y + radius * 0.10f)
                    close()
                },
                color = accent
            )
            drawCircle(color = accent, radius = radius * 0.06f, center = centre)
        }

        Text(
            text = "N",
            style = MaterialTheme.typography.labelSmall,
            color = onSurface,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .clearAndSetSemantics {}
        )
    }
}

/**
 * The direction the top of the phone is pointing, in degrees from *true* north.
 *
 * Null when the device has no usable compass, which is a real case on cheap
 * hardware and on most emulators - the screen says so rather than showing a
 * needle that never moves.
 *
 * Registered on the composition and unregistered when it leaves, so the sensor
 * is running only while this screen is open. `SENSOR_DELAY_UI` rather than
 * `FASTEST`: a compass needle does not need two hundred samples a second, and
 * asking for them is a measurable amount of battery for no visible difference.
 */
@Composable
private fun rememberDeviceHeading(latitude: Double, longitude: Double): Float? {
    val context = LocalContext.current
    val sensorManager = remember(context) {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }
    val rotationSensor = remember(sensorManager) {
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    }

    // mutableFloatStateOf is observable, so assigning from the sensor callback
    // recomposes on its own - no separate change counter is needed.
    var heading by remember { mutableFloatStateOf(Float.NaN) }

    // The angle between magnetic and true north where the user is. Computed
    // from a model built into Android, so it needs no network - and without it
    // the arrow is out by up to fifteen degrees in parts of the world.
    val declination = remember(latitude, longitude) {
        GeomagneticField(
            latitude.toFloat(),
            longitude.toFloat(),
            0f,
            System.currentTimeMillis()
        ).declination
    }

    DisposableEffect(rotationSensor, declination) {
        val manager = sensorManager
        val sensor = rotationSensor
        if (manager == null || sensor == null) return@DisposableEffect onDispose { }

        val rotation = FloatArray(ROTATION_MATRIX_SIZE)
        val orientation = FloatArray(ORIENTATION_SIZE)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                SensorManager.getOrientation(rotation, orientation)
                val magnetic = Math.toDegrees(orientation[0].toDouble())
                heading = QiblaCalculator.normalise(magnetic + declination).toFloat()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager.unregisterListener(listener) }
    }

    return heading.takeUnless { it.isNaN() }
}

private const val DIAL_SIZE_DP = 240
private const val RING_STROKE = 4f
private const val NEEDLE_TWEEN_MILLIS = 220
private const val ROTATION_MATRIX_SIZE = 9
private const val ORIENTATION_SIZE = 3
