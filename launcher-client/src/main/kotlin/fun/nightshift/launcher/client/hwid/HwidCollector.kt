package `fun`.nightshift.launcher.client.hwid

/**
 * Computes a stable hardware fingerprint for the current machine.
 *
 * Implementations must be deterministic for the lifetime of the OS install:
 * the same physical motherboard, system disk, CPU and primary network interface
 * must always produce the same SHA-256 hex string.
 *
 * The returned value is treated as PII-equivalent — it stably identifies a user.
 * Do not log it at INFO or above.
 */
interface HwidCollector {
    /**
     * Returns the cached SHA-256 hex (lower-case) HWID for the current machine.
     *
     * The first call performs the actual hardware probing (which can briefly block
     * via OSHI/WMI calls); subsequent calls return the cached value. Because the
     * first call may take up to ~2 seconds on Windows 10/11, never invoke it on
     * the UI thread without a coroutine context (e.g. `Dispatchers.IO`).
     */
    fun collect(): String
}
