package dev.bgcheck.app.data

data class BatteryFlow(val currentMa: Double?, val powerMw: Double?) {
    companion object {
        /** ACTION_BATTERY_CHANGED status and plugged extras; unknown data stays unknown. */
        fun powerState(status: Int?, plugged: Int?): BatteryPowerState = when (status) {
            2 -> BatteryPowerState.CHARGING
            3 -> BatteryPowerState.DISCHARGING
            5 -> BatteryPowerState.FULL
            4 -> if (plugged != null && plugged > 0) BatteryPowerState.PLUGGED_IDLE else BatteryPowerState.UNKNOWN
            else -> BatteryPowerState.UNKNOWN
        }

        fun from(currentMicroamps: Int, voltageMv: Int?): BatteryFlow {
            if (currentMicroamps == Int.MIN_VALUE) return BatteryFlow(null, null)
            val current = currentMicroamps / 1000.0
            return BatteryFlow(current, voltageMv?.takeIf { it > 0 }?.let { current * it / 1000.0 })
        }
    }
}
