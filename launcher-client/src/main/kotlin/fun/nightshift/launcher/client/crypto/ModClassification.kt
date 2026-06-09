package `fun`.nightshift.launcher.client.crypto

/**
 * Classification of mod jar files based on protection requirements.
 *
 * - [PREMIUM]: Mods that require encryption protection (e.g., NightShift Client Recode)
 * - [PUBLIC]: Mods that don't require encryption (e.g., Fabric API, Sodium, Lithium)
 */
enum class ModClassification {
    /**
     * Premium mod that requires encryption protection.
     */
    PREMIUM,

    /**
     * Public mod that doesn't require encryption.
     */
    PUBLIC
}

/**
 * Classifies a mod jar file based on its filename.
 *
 * Classification rules:
 * - If filename starts with "NightShift Client Recode " → [ModClassification.PREMIUM]
 * - All other filenames → [ModClassification.PUBLIC]
 *
 * The classification is case-sensitive and based solely on the filename prefix,
 * without inspecting the file content.
 *
 * @param fileName The name of the mod jar file (e.g., "NightShift Client Recode 2.7.jar")
 * @return [ModClassification.PREMIUM] if the file is a premium mod, [ModClassification.PUBLIC] otherwise
 *
 * @see ModClassification
 */
fun classifyMod(fileName: String): ModClassification {
    return if (fileName.startsWith("NightShift Client Recode ")) {
        ModClassification.PREMIUM
    } else {
        ModClassification.PUBLIC
    }
}
