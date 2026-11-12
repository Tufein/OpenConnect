package nl.tufein.openconnect.core

import java.time.Instant
import java.time.LocalDate
import kotlin.math.sin

/** Deterministic builders, mirroring the server's test factories. */
object TestData {
    val START: Instant = Instant.parse("2026-05-02T07:00:00Z")

    /** A gently curving track, one point per second, climbing a small hill. */
    fun track(n: Int = 60, start: Instant = START, withHr: Boolean = true): List<TrackPoint> =
        (0 until n).map { i ->
            TrackPoint(
                time = start.plusSeconds(i.toLong()),
                lat = 52.3702 + i * 0.00004,
                lon = 4.8952 + sin(i / 10.0) * 0.00005 + i * 0.00002,
                ele = 5.0 + 0.2 * i,
                hr = if (withHr) 140 + (i % 20) else null,
                cadence = if (withHr) 84 else null,
            )
        }

    fun activity(
        id: String = "garmin:1001",
        sport: Sport = Sport.RUNNING,
        start: Instant = START,
        n: Int = 60,
        name: String = "Morning Run",
        distanceM: Double = 5000.0,
        durationS: Double = (n - 1).toDouble(),
        track: List<TrackPoint> = track(n, start),
    ) = Activity(
        id = id,
        source = "garmin",
        externalId = id.substringAfterLast(':'),
        name = name,
        sport = sport,
        startTime = start,
        durationS = durationS,
        distanceM = distanceM,
        elevationGainM = 12.0,
        avgHr = 150,
        maxHr = 159,
        calories = 420,
        deviceName = "Forerunner 965",
        track = track,
    )

    fun daily(day: LocalDate, steps: Int = 8000) = DailySummary(
        date = day,
        steps = steps,
        stepsGoal = 10_000,
        distanceM = steps * 0.75,
        activeKcal = steps / 25,
        floorsClimbed = 8,
        moderateIntensityMin = 20,
        vigorousIntensityMin = 5,
        restingHr = 52,
        minHr = 48,
        maxHr = 150,
        avgStress = 30,
        maxStress = 80,
        bodyBatteryHigh = 90,
        bodyBatteryLow = 20,
    )

    fun sleep(day: LocalDate, hours: Double = 7.5, score: Int? = 80) = SleepSummary(
        date = day,
        durationS = (hours * 3600).toInt(),
        deepS = 5400,
        lightS = 14400,
        remS = 5400,
        awakeS = 1800,
        score = score,
    )

    fun resource(name: String): ByteArray =
        checkNotNull(TestData::class.java.getResourceAsStream("/fixtures/$name")) { "missing fixture $name" }
            .readBytes()
}
