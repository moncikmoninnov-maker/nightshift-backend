package `fun`.nightshift.launcher.backend.telemetry

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Strips known sensitive keys from a telemetry payload before persistence.
 *
 * The launcher promises the user that telemetry payloads do not carry their
 * credentials or hardware fingerprint (Requirement 21.4). Even with a
 * well-behaved client the server applies a defensive filter so a buggy or
 * malicious build cannot accidentally leak secrets into the
 * `telemetry_events.payload` column.
 *
 * The sanitiser walks the JSON tree recursively and removes any key whose
 * name (case-insensitive) appears in [SENSITIVE_KEYS] from every nested
 * [JsonObject]. Arrays are descended into so that `[ { "password": ... } ]`
 * is also cleaned. Primitive leaves are kept as-is.
 */
object JsonSanitizer {

    /**
     * Keys that must never appear in a telemetry payload. Matched
     * case-insensitively against each [JsonObject] entry in the tree.
     */
    private val SENSITIVE_KEYS: Set<String> = setOf(
        "login",
        "password",
        "passwd",
        "email",
        "hwid",
        "token",
        "session",
        "sessionToken",
    ).map { it.lowercase() }.toSet()

    /**
     * Returns a sanitised copy of [payload], or `null` when the input is
     * `null` (so the caller can pass through "no payload" as-is).
     */
    fun sanitize(payload: JsonObject?): JsonObject? {
        if (payload == null) return null
        return sanitizeObject(payload)
    }

    private fun sanitizeObject(obj: JsonObject): JsonObject {
        val cleaned = LinkedHashMap<String, JsonElement>(obj.size)
        for ((key, value) in obj) {
            if (key.lowercase() in SENSITIVE_KEYS) continue
            cleaned[key] = sanitizeElement(value)
        }
        return JsonObject(cleaned)
    }

    private fun sanitizeElement(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> sanitizeObject(element)
        is JsonArray -> JsonArray(element.map { sanitizeElement(it) })
        else -> element
    }
}
