package dev.bgcheck.app.data

import org.junit.Assert.*
import org.junit.Test

class BatteryFlowTest {
    @Test fun externalPowerWithoutChargingIsNotReportedAsDischarging() {
        // Android status 4 = NOT_CHARGING; plugged 1 = AC.
        assertEquals(BatteryPowerState.PLUGGED_IDLE, BatteryFlow.powerState(4, 1))
        assertEquals(BatteryFlow(0.0, 0.0), BatteryFlow.from(0, 4200))
    }

    @Test fun usesExplicitChargeStatusAndKeepsMissingReadingsUnknown() {
        assertEquals(BatteryPowerState.DISCHARGING, BatteryFlow.powerState(3, 0))
        assertEquals(BatteryPowerState.CHARGING, BatteryFlow.powerState(2, 2))
        assertEquals(BatteryPowerState.FULL, BatteryFlow.powerState(5, 1))
        assertEquals(BatteryPowerState.UNKNOWN, BatteryFlow.powerState(null, null))
        assertEquals(BatteryPowerState.UNKNOWN, BatteryFlow.powerState(1, 1))
        assertEquals(BatteryPowerState.UNKNOWN, BatteryFlow.powerState(4, null))
        assertEquals(BatteryPowerState.UNKNOWN, BatteryFlow.powerState(4, -1))
    }

    @Test fun convertsMicroampsAndMillivoltsWithoutLosingDirection() {
        assertEquals(BatteryFlow(-250.0, -1000.0), BatteryFlow.from(-250000, 4000))
        assertEquals(BatteryFlow(500.0, 2000.0), BatteryFlow.from(500000, 4000))
        assertEquals(BatteryFlow(0.0, 0.0), BatteryFlow.from(0, 4000))
    }
    @Test fun unsupportedCurrentAndInvalidVoltageStayUnavailable() {
        assertEquals(BatteryFlow(null, null), BatteryFlow.from(Int.MIN_VALUE, 4000))
        assertEquals(BatteryFlow(100.0, null), BatteryFlow.from(100000, null))
        assertEquals(BatteryFlow(100.0, null), BatteryFlow.from(100000, 0))
    }
}
