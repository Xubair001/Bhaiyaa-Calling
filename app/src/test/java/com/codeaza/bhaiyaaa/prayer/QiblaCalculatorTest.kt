package com.codeaza.bhaiyaaa.prayer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The qibla bearing.
 *
 * Anchored on cities whose qibla is independently published, because a test
 * that only checks the formula against itself proves nothing. London (about
 * 119°) is the useful one: a flat map suggests due east, and getting it wrong
 * would point most of Europe several degrees off - so if this test passes, the
 * great-circle maths is genuinely being done.
 */
class QiblaCalculatorTest {

    private val tolerance = 1.0

    @Test
    fun `London points south-east, not east`() {
        val bearing = QiblaCalculator.bearingFrom(51.5074, -0.1278)

        assertThat(bearing).isWithin(tolerance).of(119.0)
        // The point of the great-circle calculation: a Mercator map says east.
        assertThat(QiblaCalculator.cardinal(bearing)).contains("south-east")
    }

    @Test
    fun `New York points north-east`() {
        assertThat(QiblaCalculator.bearingFrom(40.7128, -74.0060))
            .isWithin(tolerance).of(58.5)
    }

    @Test
    fun `Lahore points roughly west`() {
        val bearing = QiblaCalculator.bearingFrom(31.5204, 74.3587)

        assertThat(bearing).isWithin(2.0).of(260.0)
        assertThat(QiblaCalculator.cardinal(bearing)).contains("west")
    }

    @Test
    fun `due north of the Kaaba points south`() {
        // Same meridian, higher latitude: the answer has to be exactly south,
        // and this needs no external source to know.
        assertThat(QiblaCalculator.bearingFrom(40.0, QiblaCalculator.KAABA_LONGITUDE))
            .isWithin(0.001).of(180.0)
    }

    @Test
    fun `due south of the Kaaba points north`() {
        assertThat(QiblaCalculator.bearingFrom(-10.0, QiblaCalculator.KAABA_LONGITUDE))
            .isWithin(0.001).of(0.0)
    }

    @Test
    fun `two places mirrored about the Kaaba's meridian give mirrored bearings`() {
        val east = QiblaCalculator.bearingFrom(30.0, QiblaCalculator.KAABA_LONGITUDE + 20)
        val west = QiblaCalculator.bearingFrom(30.0, QiblaCalculator.KAABA_LONGITUDE - 20)

        assertThat(QiblaCalculator.normalise(east + west)).isWithin(0.001).of(0.0)
    }

    @Test
    fun `a bearing is always a real compass direction`() {
        var latitude = -80.0
        while (latitude <= 80.0) {
            var longitude = -180.0
            while (longitude < 180.0) {
                val bearing = QiblaCalculator.bearingFrom(latitude, longitude)
                assertThat(bearing).isAtLeast(0.0)
                assertThat(bearing).isLessThan(360.0)
                assertThat(bearing.isNaN()).isFalse()
                longitude += 17.0
            }
            latitude += 13.0
        }
    }

    @Test
    fun `standing at the Kaaba is answered as such rather than with an arrow`() {
        assertThat(
            QiblaCalculator.isAtKaaba(
                QiblaCalculator.KAABA_LATITUDE,
                QiblaCalculator.KAABA_LONGITUDE
            )
        ).isTrue()
        // A GPS fix is never exactly the constant, so the check is a tolerance.
        assertThat(QiblaCalculator.isAtKaaba(21.42250004, 39.82619996)).isTrue()
        assertThat(QiblaCalculator.isAtKaaba(21.45, 39.85)).isFalse()
    }

    @Test
    fun `distance is measured along the surface`() {
        // Published great-circle distances, to the nearest few kilometres.
        assertThat(QiblaCalculator.distanceKmFrom(51.5074, -0.1278))
            .isWithin(20.0).of(4780.0)
        assertThat(QiblaCalculator.distanceKmFrom(31.5204, 74.3587))
            .isWithin(20.0).of(3600.0)
        // Haversine holds up at zero distance, where the law of cosines would
        // lose all its precision.
        assertThat(
            QiblaCalculator.distanceKmFrom(
                QiblaCalculator.KAABA_LATITUDE,
                QiblaCalculator.KAABA_LONGITUDE
            )
        ).isWithin(0.001).of(0.0)
    }

    @Test
    fun `the needle turns the short way round`() {
        // 350 to 10 is a ten-degree nudge clockwise, not 340 the other way.
        assertThat(QiblaCalculator.shortestTurn(350.0, 10.0)).isWithin(0.001).of(20.0)
        assertThat(QiblaCalculator.shortestTurn(10.0, 350.0)).isWithin(0.001).of(-20.0)
        assertThat(QiblaCalculator.shortestTurn(0.0, 180.0)).isWithin(0.001).of(180.0)
    }

    @Test
    fun `cardinal names cover the whole circle without a gap`() {
        assertThat(QiblaCalculator.cardinal(0.0)).isEqualTo("north")
        assertThat(QiblaCalculator.cardinal(359.9)).isEqualTo("north")
        assertThat(QiblaCalculator.cardinal(90.0)).isEqualTo("east")
        assertThat(QiblaCalculator.cardinal(180.0)).isEqualTo("south")
        assertThat(QiblaCalculator.cardinal(270.0)).isEqualTo("west")
    }
}
