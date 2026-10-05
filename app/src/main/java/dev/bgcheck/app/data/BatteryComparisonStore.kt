package dev.bgcheck.app.data

import android.content.Context

class BatteryComparisonStore(private val read: () -> String?, private val write: (String?) -> Unit) {
    constructor(context: Context) : this(
        { context.getSharedPreferences("battery_comparison", Context.MODE_PRIVATE).getString("session", null) },
        { value -> check(context.getSharedPreferences("battery_comparison", Context.MODE_PRIVATE).edit()
            .putString("session", value).commit()) { "Could not save the battery comparison." } },
    )

    fun load(): BatteryComparisonSession? = BatteryComparisonJson.decode(read())
    fun save(session: BatteryComparisonSession) = write(BatteryComparisonJson.encode(session))
    fun clear() = write(null)
}
