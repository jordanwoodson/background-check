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
                put("userId", record.userId); put("userSerial", record.userSerial ?: JSONObject.NULL)
                put("profileName", record.profileName)
            })
        }
    }.toString()

    fun decode(value: String?, legacyUserId: Int = 0): List<ChangeRecord> = runCatching {
        require(legacyUserId >= 0) { "Invalid history owner" }
        val array = JSONArray(value ?: "[]")
        (0 until minOf(array.length(), 50)).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
                val userId = if (item.has("userId")) item.getInt("userId") else legacyUserId
                val before = readPolicy(item.getJSONObject("before"), userId)
                val after = readPolicy(item.getJSONObject("after"), userId)
                val serial = if (item.has("userSerial")) item.nullableLong("userSerial") else before.userSerial
                check(userId >= 0 && before.userId == userId && after.userId == userId)
                check(before.packageName == item.getString("package") && after.packageName == before.packageName)
                check(before.uid == after.uid && before.userSerial == serial && after.userSerial == serial)
                ChangeRecord(item.getString("id"), item.getString("package"), item.getString("label"),
                    item.getString("description"), item.getLong("timestamp"),
                    before, after, item.optBoolean("undone"), item.optBoolean("recovery"),
                    userId = userId, userSerial = serial,
                    profileName = item.optString("profileName", if (userId == 0) "Personal" else "User $userId"))
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    private fun policyJson(p: PolicyState) = JSONObject().apply {
        put("package", p.packageName); put("background", p.backgroundMode ?: JSONObject.NULL)
        put("legacy", p.legacyMode ?: JSONObject.NULL); put("exempt", p.dozeExempt ?: JSONObject.NULL)
        put("systemExempt", p.systemExempt); put("userExempt", p.userExempt ?: JSONObject.NULL)
        put("uidOverride", p.uidOverride); put("bucket", p.standbyBucket ?: JSONObject.NULL)
        put("time", p.capturedAt); put("error", p.error ?: JSONObject.NULL)
        put("userId", p.userId); put("uid", p.uid ?: JSONObject.NULL)
        put("userSerial", p.userSerial ?: JSONObject.NULL)
    }
    private fun readPolicy(j: JSONObject, legacyUserId: Int) = PolicyState(
        packageName = j.getString("package"), backgroundMode = j.nullableString("background"),
        legacyMode = j.nullableString("legacy"), dozeExempt = j.nullableBoolean("exempt"),
        systemExempt = j.optBoolean("systemExempt"), userExempt = j.nullableBoolean("userExempt"),
        uidOverride = j.optBoolean("uidOverride"), standbyBucket = if(j.isNull("bucket")) null else j.getInt("bucket"),
        capturedAt = j.getLong("time"), error = j.nullableString("error"),
        userId = if (j.has("userId")) j.getInt("userId") else legacyUserId,
        uid = if (!j.has("uid") || j.isNull("uid")) null else j.getInt("uid"),
        userSerial = j.nullableLong("userSerial"),
    ).also { policy ->
        check(policy.userId >= 0 && (policy.uid == null || (policy.uid >= 0 && policy.uid / 100000 == policy.userId)))
        check(policy.userSerial == null || policy.userSerial >= 0)
    }
    private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else getString(key)
    private fun JSONObject.nullableBoolean(key: String): Boolean? = if (isNull(key)) null else getBoolean(key)
    private fun JSONObject.nullableLong(key: String): Long? = if (!has(key) || isNull(key)) null else getLong(key)
}
