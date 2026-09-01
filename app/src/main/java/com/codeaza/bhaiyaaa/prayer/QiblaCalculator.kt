package com.codeaza.bhaiyaaa.prayer

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Which way the Kaaba is, from anywhere on Earth.
 *
 * Spherical trigonometry, not a service. Given a latitude and longitude the
 * answer is computable on the device forever, with no network and no API key -
 * the same reason prayer times belong in an offline-first app. The coordinates
 * are the ones already stored for prayer times, so this costs no new permission
 * and no new data.
 *
 * The bearing is the *initial great-circle* bearing, which is the correct answer
 * and is not the same as the direction a flat map would suggest. From London the
 * great-circle bearing is about 119° - roughly south-east - while a Mercator map
 * makes it look due east. Using the map answer would point people several
 * degrees wrong across most of Europe.
 *
 * Pure and side-effect free, so the maths is testable without a device or a
 * magnetometer.
 */
object QiblaCalculator {

    /** The Kaaba, Masjid al-Haram, Makkah. */
    const val KAABA_LATITUDE = 21.4225
    const val KAABA_LONGITUDE = 39.8262

    /** Mean Earth radius, in kilometres (IUGG). */
    private const val EARTH_RADIUS_KM = 6371.0088

    /**
     * Initial great-circle bearing to the Kaaba, in degrees clockwise from
     * true north, normalised to 0 until (not including) 360.
     *
     * @return 0.0 when standing at the Kaaba itself, where no direction is
     *   meaningful - the caller shows "you are here" rather than an arrow.
     */
    fun bearingFrom(latitude: Double, longitude: Double): Double {
        if (isAtKaaba(latitude, longitude)) return 0.0

        val fromLat = Math.toRadians(latitude)
        val toLat = Math.toRadians(KAABA_LATITUDE)
        val deltaLng = Math.toRadians(KAABA_LONGITUDE - longitude)

        // The standard qibla formula. tan() of the destination latitude rather
        // than the more familiar two-point bearing form, because it stays
        // stable when the two longitudes are close.
        val y = sin(deltaLng)
        val x = cos(fromLat) * tan(toLat) - sin(fromLat) * cos(deltaLng)
        return normalise(Math.toDegrees(atan2(y, x)))
    }

    /** Great-circle distance to the Kaaba, in kilometres. */
    fun distanceKmFrom(latitude: Double, longitude: Double): Double {
        val fromLat = Math.toRadians(latitude)
        val toLat = Math.toRadians(KAABA_LATITUDE)
        val deltaLat = toLat - fromLat
        val deltaLng = Math.toRadians(KAABA_LONGITUDE - longitude)

        // Haversine rather than the spherical law of cosines: the latter loses
        // precision badly over short distances, which is exactly the case
        // someone in Makkah is in.
        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
            cos(fromLat) * cos(toLat) * sin(deltaLng / 2) * sin(deltaLng / 2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a).coerceAtMost(1.0))
    }

    /**
     * The compass point a bearing falls in, for a spoken or written label.
     *
     * Sixteen points rather than eight: "west-south-west" is a materially
     * better description of 254° than "west", and this string is what a screen
     * reader announces instead of an arrow nobody can see.
     */
    fun cardinal(bearing: Double): String {
        val points = listOf(
            "north", "north-north-east", "north-east", "east-north-east",
            "east", "east-south-east", "south-east", "south-south-east",
            "south", "south-south-west", "south-west", "west-south-west",
            "west", "west-north-west", "north-west", "north-north-west"
        )
        val index = ((normalise(bearing) + POINT_HALF_WIDTH) / POINT_WIDTH).toInt() % points.size
        return points[index]
    }

    /**
     * True within roughly a hundred metres of the Kaaba.
     *
     * A tolerance rather than an equality check: floating-point coordinates
     * from a GPS fix are never exactly the constant, and dividing by a
     * near-zero distance would produce a wildly unstable arrow.
     */
    fun isAtKaaba(latitude: Double, longitude: Double): Boolean =
        abs(latitude - KAABA_LATITUDE) < AT_KAABA_TOLERANCE_DEGREES &&
            abs(longitude - KAABA_LONGITUDE) < AT_KAABA_TOLERANCE_DEGREES

    /** Brings any angle into 0 until 360. */
    fun normalise(degrees: Double): Double = ((degrees % 360) + 360) % 360

    /**
     * The smallest turn from [from] to [to], negative for anticlockwise.
     *
     * Used to animate the needle the short way round: without it, moving from
     * 350° to 10° spins the arrow almost all the way back through zero.
     */
    fun shortestTurn(from: Double, to: Double): Double {
        val delta = normalise(to - from)
        return if (delta > 180.0) delta - 360.0 else delta
    }

    private const val POINT_WIDTH = 360.0 / 16
    private const val POINT_HALF_WIDTH = POINT_WIDTH / 2
    private const val AT_KAABA_TOLERANCE_DEGREES = 0.001
}
