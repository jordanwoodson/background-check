package dev.bgcheck.app.ui

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf

/** Injected at the real activity boundary so previews and isolated UI tests remain independent. */
val LocalInspectionPreferences = staticCompositionLocalOf<SharedPreferences?> { null }

@Composable
inline fun <reified T : Enum<T>> rememberEnumPreference(key: String, default: T): MutableState<T> =
    rememberStringPreference(key, default, { raw -> enumValues<T>().firstOrNull { it.name == raw } ?: default }, { it.name })

@Composable
fun rememberIntPreference(key: String, default: Int): MutableState<Int> =
    rememberStringPreference(key, default, { it?.toIntOrNull() ?: default }, { it.toString() })

@Composable
fun <T> rememberStringPreference(key: String, default: T, decode: (String?) -> T, encode: (T) -> String): MutableState<T> {
    val preferences = LocalInspectionPreferences.current
    val state = rememberSaveable(key) { mutableStateOf(runCatching { decode(preferences?.getString(key, null)) }.getOrDefault(default)) }
    return remember(key, preferences, state) {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    state.value = value
                    preferences?.edit()?.putString(key, encode(value))?.apply()
                }
            override fun component1(): T = value
            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}
