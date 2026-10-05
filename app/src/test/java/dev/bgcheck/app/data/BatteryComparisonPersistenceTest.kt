package dev.bgcheck.app.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BatteryComparisonPersistenceTest {
    private val sample = BatteryComparisonSample(
        EnergySnapshot(uids = listOf(UidEnergy(1_010_101, 12.5, null, 5_000_000_000, 0, 700)),
            components = listOf(PowerComponent("cpu", 4.0)), estimatedTotalMah = 40.0,
            batteryRealtimeMs = 20_000, startClockTimeMs = 5, capturedAt = 50_000,
            warnings = listOf("CPU unavailable")),
        listOf(BatteryUidIdentity(1_010_101, "Work", 42, true,
            listOf(BatteryComparisonPackage("dev.example", "Example", 123)))))

    @Test fun bothSamplesAndUnavailableReadingsSurviveRoundTrip() {
        val session = BatteryComparisonSession(sample, sample.copy(energy = sample.energy.copy(capturedAt = 90_000)))
        val restored = BatteryComparisonJson.decode(BatteryComparisonJson.encode(session))
        assertEquals(session, restored)
        assertNull(restored!!.baseline.energy.uids.single().cpuTimeMs)
        assertEquals(5_000_000_000, restored.later!!.energy.uids.single().networkRxBytes)
    }

    @Test fun baselineWithoutLaterSampleSurvivesRestart() {
        val restored = BatteryComparisonJson.decode(BatteryComparisonJson.encode(BatteryComparisonSession(sample)))
        assertNotNull(restored)
        assertEquals(42L, restored!!.baseline.identities.single().userSerial)
        assertNull(restored.later)
    }

    @Test fun malformedAndFutureVersionDataRestoreEmpty() {
        assertNull(BatteryComparisonJson.decode(null))
        assertNull(BatteryComparisonJson.decode("not json"))
        val encoded = JSONObject(BatteryComparisonJson.encode(BatteryComparisonSession(sample))).put("version", 999)
        assertNull(BatteryComparisonJson.decode(encoded.toString()))
    }

    @Test fun corruptRequiredIdentityDoesNotRestoreAPartialSession() {
        val encoded = JSONObject(BatteryComparisonJson.encode(BatteryComparisonSession(sample)))
        encoded.getJSONObject("baseline").getJSONArray("identities").getJSONObject(0).put("uid", -1)
        assertNull(BatteryComparisonJson.decode(encoded.toString()))
    }

    @Test fun storedSessionIsRestoredThenClearedAndRestarted() {
        var saved: String? = null
        val store = BatteryComparisonStore({ saved }, { saved = it })
        store.save(BatteryComparisonSession(sample))
        val reopened = BatteryComparisonStore({ saved }, { saved = it })
        assertEquals(sample, reopened.load()!!.baseline)
        val later = sample.copy(energy = sample.energy.copy(capturedAt = 90_000))
        reopened.save(reopened.load()!!.copy(later = later))
        assertEquals(90_000L, store.load()!!.later!!.energy.capturedAt)
        store.clear()
        assertNull(reopened.load())
        store.save(BatteryComparisonSession(later))
        assertEquals(90_000L, reopened.load()!!.baseline.energy.capturedAt)
        assertNull(reopened.load()!!.later)
    }

    @Test fun corruptCounterDoesNotRestoreAsZero() {
        val encoded = JSONObject(BatteryComparisonJson.encode(BatteryComparisonSession(sample)))
        encoded.getJSONObject("baseline").getJSONObject("energy").getJSONArray("uids").getJSONObject(0).put("cpuTimeMs", -2)
        assertNull(BatteryComparisonJson.decode(encoded.toString()))
    }

    @Test fun monotonicTimeAndBootCountSurviveColdLaunch() {
        val timed = sample.copy(elapsedRealtimeMs = 123_000, bootCount = 7)
        val restored = BatteryComparisonJson.decode(BatteryComparisonJson.encode(BatteryComparisonSession(timed)))!!
        assertEquals(123_000L, restored.baseline.elapsedRealtimeMs)
        assertEquals(7, restored.baseline.bootCount)
    }

    @Test fun oldSavedSessionWithoutClockMetadataStillRestores() {
        val encoded = JSONObject(BatteryComparisonJson.encode(BatteryComparisonSession(sample)))
        encoded.getJSONObject("baseline").remove("elapsedRealtimeMs")
        encoded.getJSONObject("baseline").remove("bootCount")
        val restored = BatteryComparisonJson.decode(encoded.toString())
        assertNotNull(restored)
        assertNull(restored!!.baseline.elapsedRealtimeMs)
        assertNull(restored.baseline.bootCount)
    }
}
