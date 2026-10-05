package dev.bgcheck.app.data

import org.json.JSONArray
import org.json.JSONObject

/** One local session, including null readings and the identities observed at each endpoint. */
object BatteryComparisonJson {
    private const val VERSION = 1
    private const val MAX_ENTRIES = 20_000
    private const val MAX_JSON_CHARS = 8_000_000

    fun encode(session: BatteryComparisonSession): String = JSONObject().apply {
        put("version", VERSION)
        put("baseline", sampleJson(session.baseline))
        put("later", session.later?.let(::sampleJson) ?: JSONObject.NULL)
    }.toString()

    fun decode(value: String?): BatteryComparisonSession? = runCatching {
        if (value == null || value.length > MAX_JSON_CHARS) return null
        val json = JSONObject(value)
        require(json.getInt("version") == VERSION)
        BatteryComparisonSession(readSample(json.getJSONObject("baseline")),
            if (json.isNull("later")) null else readSample(json.getJSONObject("later")))
    }.getOrNull()

    private fun sampleJson(sample: BatteryComparisonSample) = JSONObject().apply {
        put("elapsedRealtimeMs", sample.elapsedRealtimeMs ?: JSONObject.NULL)
        put("bootCount", sample.bootCount ?: JSONObject.NULL)
        put("energy", JSONObject().apply {
            val energy = sample.energy
            put("capturedAt", energy.capturedAt)
            put("startClockTimeMs", energy.startClockTimeMs ?: JSONObject.NULL)
            put("batteryRealtimeMs", energy.batteryRealtimeMs ?: JSONObject.NULL)
            put("estimatedTotalMah", energy.estimatedTotalMah ?: JSONObject.NULL)
            put("warnings", JSONArray(energy.warnings))
            put("components", JSONArray().apply { energy.components.forEach { component ->
                put(JSONObject().put("name", component.name).put("estimatedMah", component.estimatedMah))
            } })
            put("uids", JSONArray().apply { energy.uids.forEach { reading ->
                put(JSONObject().apply {
                    put("uid", reading.uid)
                    put("estimatedMah", reading.estimatedMah ?: JSONObject.NULL)
                    put("cpuTimeMs", reading.cpuTimeMs ?: JSONObject.NULL)
                    put("networkRxBytes", reading.networkRxBytes ?: JSONObject.NULL)
                    put("networkTxBytes", reading.networkTxBytes ?: JSONObject.NULL)
                    put("wakelockMs", reading.wakelockMs ?: JSONObject.NULL)
                })
            } })
        })
        put("identities", JSONArray().apply { sample.identities.forEach { identity ->
            put(JSONObject().apply {
                put("uid", identity.uid)
                put("profileName", identity.profileName)
                put("userSerial", identity.userSerial ?: JSONObject.NULL)
                put("profileAvailable", identity.profileAvailable)
                put("packages", JSONArray().apply { identity.packages.forEach { app ->
                    put(JSONObject().put("packageName", app.packageName).put("label", app.label)
                        .put("firstInstallTime", app.firstInstallTime ?: JSONObject.NULL))
                } })
            })
        } })
    }

    private fun readSample(json: JSONObject): BatteryComparisonSample {
        val energy = json.getJSONObject("energy")
        val uids = energy.getJSONArray("uids").items { reading ->
            UidEnergy(reading.getInt("uid"), reading.nullableDouble("estimatedMah"), reading.nullableLong("cpuTimeMs"),
                reading.nullableLong("networkRxBytes"), reading.nullableLong("networkTxBytes"), reading.nullableLong("wakelockMs"))
                .also { require(BatteryComparison.validCounters(it)) }
        }
        require(uids.map { it.uid }.distinct().size == uids.size)
        val snapshot = EnergySnapshot(uids = uids,
            components = energy.getJSONArray("components").items {
                PowerComponent(it.getString("name"), it.getDouble("estimatedMah")).also { component ->
                    require(component.estimatedMah.isFinite() && component.estimatedMah >= 0)
                }
            },
            estimatedTotalMah = energy.nullableDouble("estimatedTotalMah"),
            batteryRealtimeMs = energy.nullableLong("batteryRealtimeMs"),
            startClockTimeMs = energy.nullableLong("startClockTimeMs"),
            capturedAt = energy.getLong("capturedAt"),
            warnings = energy.getJSONArray("warnings").let { array ->
                require(array.length() <= MAX_ENTRIES)
                (0 until array.length()).map { array.getString(it) }
            })
        require(snapshot.capturedAt > 0)
        require(snapshot.startClockTimeMs == null || snapshot.startClockTimeMs > 0)
        require(snapshot.batteryRealtimeMs == null || snapshot.batteryRealtimeMs >= 0)
        require(snapshot.estimatedTotalMah == null || snapshot.estimatedTotalMah.isFinite() && snapshot.estimatedTotalMah >= 0)
        val identities = json.getJSONArray("identities").items { identity ->
            BatteryUidIdentity(identity.getInt("uid"), identity.getString("profileName"), identity.nullableLong("userSerial"),
                identity.getBoolean("profileAvailable"), identity.getJSONArray("packages").items {
                    BatteryComparisonPackage(it.getString("packageName"), it.getString("label"), it.nullableLong("firstInstallTime"))
                        .also { app -> require(app.packageName.isNotBlank() && (app.firstInstallTime == null || app.firstInstallTime > 0)) }
                }).also {
                require(it.uid >= 0 && (it.userSerial == null || it.userSerial >= 0))
                require(it.packages.map { app -> app.packageName }.distinct().size == it.packages.size)
            }
        }
        require(identities.map { it.uid }.distinct().size == identities.size)
        val elapsedRealtimeMs = json.nullableLong("elapsedRealtimeMs")
        val bootCount = if (json.isNull("bootCount")) null else json.getInt("bootCount")
        require(elapsedRealtimeMs == null || elapsedRealtimeMs >= 0)
        require(bootCount == null || bootCount >= 0)
        return BatteryComparisonSample(snapshot, identities, elapsedRealtimeMs, bootCount)
    }

    private fun <T> JSONArray.items(read: (JSONObject) -> T): List<T> {
        require(length() <= MAX_ENTRIES)
        return (0 until length()).map { read(getJSONObject(it)) }
    }
    private fun JSONObject.nullableLong(key: String): Long? = if (isNull(key)) null else getLong(key)
    private fun JSONObject.nullableDouble(key: String): Double? = if (isNull(key)) null else getDouble(key)
}
