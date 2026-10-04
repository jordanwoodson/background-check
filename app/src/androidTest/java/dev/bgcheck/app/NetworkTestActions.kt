package dev.bgcheck.app

import android.app.Activity
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.matcher.ViewMatchers.withId
import dev.bgcheck.network.R as NetworkR

/** Wait for the app window to receive input again after the sheet's exit transition. */
internal fun ActivityScenario<Activity>.closeNetworkAppSheet() {
    pressBack()
    val deadline = SystemClock.uptimeMillis() + 5_000
    while (true) {
        var focused = false
        onActivity { focused = it.window.decorView.hasWindowFocus() }
        if (focused) break
        check(SystemClock.uptimeMillis() < deadline) { "Network window did not regain focus after closing its sheet" }
        Thread.sleep(100)
    }
    onView(withId(NetworkR.id.action_sheet_package_name)).check(doesNotExist())
}
