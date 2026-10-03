package dev.bgcheck.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Only policy metadata stays on this device; no process inventory or telemetry is stored. */
object ChangeHistory {
    fun encode(records: List<ChangeRecord>): String = JSONArray().apply {
        records.take(50).forEach { record ->
            put(JSONObject().apply {
                put("id", record.id); put("package", record.packageName); put("label", record.label)
                put("description", record.description); put("timestamp", record.timestamp)
                put("before", policyJson(record.before)); put("after", policyJson(record.after))
                put("undone", record.undone)
                put("recovery", record.recovery)
            })
        }
    }.toString()

    fun decode(value: String?): List<ChangeRecord> = runCatching {
        val array = JSONArray(value ?: "[]")
        (0 until minOf(array.length(), 50)).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
                ChangeRecord(item.getString("id"), item.getString("package"), item.getString("label"),
                    item.getString("description"), item.getLong("timestamp"),
                    readPolicy(item.getJSONObject("before")), readPolicy(item.getJSONObject("after")),
                    item.optBoolean("undone"), item.optBoolean("recovery"))
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    private fun policyJson(p: PolicyState) = JSONObject().apply {
        put("package", p.packageName); put("background", p.backgroundMode ?: JSONObject.NULL)
        put("legacy", p.legacyMode ?: JSONObject.NULL); put("exempt", p.dozeExempt ?: JSONObject.NULL)
        put("systemExempt", p.systemExempt); put("userExempt", p.userExempt ?: JSONObject.NULL)
        put("uidOverride", p.uidOverride); put("bucket", p.standbyBucket ?: JSONObject.NULL)
        put("time", p.capturedAt); put("error", p.error ?: JSONObject.NULL)
    }
    private fun readPolicy(j: JSONObject) = PolicyState(
        packageName = j.getString("package"), backgroundMode = j.nullableString("background"),
        legacyMode = j.nullableString("legacy"), dozeExempt = j.nullableBoolean("exempt"),
        systemExempt = j.optBoolean("systemExempt"), userExempt = j.nullableBoolean("userExempt"),
        uidOverride = j.optBoolean("uidOverride"), standbyBucket = if(j.isNull("bucket")) null else j.getInt("bucket"),
        capturedAt = j.getLong("time"), error = j.nullableString("error"),
    )
    private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else getString(key)
    private fun JSONObject.nullableBoolean(key: String): Boolean? = if (isNull(key)) null else getBoolean(key)
}
