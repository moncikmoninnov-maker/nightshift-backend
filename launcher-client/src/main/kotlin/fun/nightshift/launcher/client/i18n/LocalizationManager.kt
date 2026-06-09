package `fun`.nightshift.launcher.client.i18n

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/**
 * In-process localization manager for the launcher.
 *
 * Strings come from two places:
 *  1. **Bundled defaults** in [DefaultBundles] — always available, used as
 *     fallback when an external override is missing or malformed.
 *  2. **External overrides** in `%APPDATA%/NightShiftClient/lang/{ru,en}.json`
 *     — power users can drop translations there without rebuilding.
 *
 * The manager exposes a Compose [String]-aware state so any composable that
 * reads [t] re-composes when the locale changes (Requirement 20.3).
 */
class LocalizationManager(
    private val langDir: Path?,
) {
    private val current = mutableStateOf(Bundle("ru", DefaultBundles.RU))

    /** Sets the active locale; falls back to bundled defaults on any error. */
    fun setLocale(locale: String) {
        val normalized = locale.lowercase()
        val baseline = when (normalized) {
            "ru" -> DefaultBundles.RU
            "en" -> DefaultBundles.EN
            else -> {
                log.warn("Unsupported locale '{}'; falling back to ru", locale)
                DefaultBundles.RU
            }
        }
        val merged = baseline + readOverrides(normalized)
        current.value = Bundle(normalized, merged)
    }

    /** Detects the host OS locale and picks `ru` or `en` (Req. 20.2). */
    fun applySystemDefault() {
        val tag = Locale.getDefault().language.lowercase()
        setLocale(if (tag == "ru") "ru" else "en")
    }

    /** Returns the localised string for [key], falling back to the key itself. */
    fun t(key: String): String = current.value.strings[key] ?: key

    /** Lookup with positional arguments, e.g. `"hello {0}"` -> `t("hello.user", "Misha")`. */
    fun t(key: String, vararg args: Any?): String {
        var s = t(key)
        args.forEachIndexed { i, v -> s = s.replace("{$i}", v?.toString() ?: "") }
        return s
    }

    /** Currently active locale tag (e.g. `"ru"`). */
    val locale: String get() = current.value.locale

    private fun readOverrides(locale: String): Map<String, String> {
        val dir = langDir ?: return emptyMap()
        val file = dir.resolve("$locale.json")
        if (!Files.exists(file)) return emptyMap()
        return try {
            val text = Files.readString(file)
            // Hand-rolled flat parser: `{"key": "value", ...}` with no nesting.
            // Avoids a hard kotlinx-serialization dependency in this hot-path
            // call site and tolerates trailing commas / comments cheaply.
            FlatJson.parse(text)
        } catch (t: Throwable) {
            log.warn("Failed to read lang override {}: {}", file, t.message)
            emptyMap()
        }
    }

    private data class Bundle(val locale: String, val strings: Map<String, String>)

    companion object {
        private val log = LoggerFactory.getLogger(LocalizationManager::class.java)
    }
}

/** CompositionLocal for the manager — set by `App.kt` on the root composable. */
val LocalLocalization = compositionLocalOf<LocalizationManager> {
    error("LocalizationManager has not been provided")
}
