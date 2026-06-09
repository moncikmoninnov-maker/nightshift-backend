package `fun`.nightshift.launcher.client.hwid

import org.slf4j.LoggerFactory
import oshi.SystemInfo
import oshi.hardware.NetworkIF
import java.security.MessageDigest

/**
 * Default [HwidCollector] backed by [OSHI](https://github.com/oshi/oshi).
 *
 * The HWID is computed as `SHA-256(MB | DISK | CPU | MAC)` (lower-case hex),
 * where the four components are:
 *
 *  * **MB**   — motherboard (baseboard) serial number, falling back to the
 *               computer system serial number if the baseboard reports nothing.
 *  * **DISK** — serial number of the disk that hosts the OS root drive
 *               (matched by partition mount point), falling back to the first
 *               available disk.
 *  * **CPU**  — `processorIdentifier.processorID`.
 *  * **MAC**  — MAC address of the first network interface that is operationally
 *               UP, has a non-blank address and does not start with `00:00:00`.
 *
 * Each component falls back to the literal string [UNKNOWN] when probing fails
 * or the value is null/blank, so the final hash is always well-defined.
 *
 * Result is computed once per process and cached behind double-checked locking,
 * so subsequent calls are O(1).
 *
 * **Threading.** OSHI / WMI calls block briefly. Wall-clock budget is ≤2 seconds
 * on Windows 10/11, which is acceptable for launcher startup but not for the UI
 * thread — invoke [collect] from a coroutine context such as `Dispatchers.IO`.
 */
class OshiHwidCollector : HwidCollector {

    @Volatile
    private var cached: String? = null
    private val lock = Any()

    override fun collect(): String {
        val local = cached
        if (local != null) return local
        return synchronized(lock) {
            val again = cached
            if (again != null) return@synchronized again
            // The whole computation is wrapped in try/catch so a misbehaving
            // OEM driver or stripped-down Windows install can never propagate
            // an exception to the caller — the worst case is a stable but
            // less unique fallback hash derived from the user's profile.
            val computed = try {
                compute()
            } catch (t: Throwable) {
                log.warn("HWID computation failed; using fallback hash", t)
                fallbackHash()
            }
            cached = computed
            computed
        }
    }

    private fun compute(): String {
        val systemInfo = try { SystemInfo() } catch (t: Throwable) {
            log.warn("OSHI SystemInfo init failed: {}", t.message)
            return fallbackHash()
        }
        val hal = try { systemInfo.hardware } catch (t: Throwable) {
            log.warn("OSHI hardware probe failed: {}", t.message)
            return fallbackHash()
        }

        val mb = readMotherboardSerial(hal)
        val disk = readSystemDiskSerial(hal)
        val cpu = readCpuId(hal)
        val mac = readPrimaryMac(hal)

        log.debug("HWID components: MB={} DISK={} CPU={} MAC={}", mb, disk, cpu, mac)

        val joined = "$mb|$disk|$cpu|$mac"
        return sha256Hex(joined)
    }

    /**
     * Last-resort HWID when OSHI / WMI are not usable. Built from stable but
     * less unique values: Windows username, computer name and OS arch. Good
     * enough to keep the account bound to one machine for the lifetime of
     * the OS install; a re-installation will rebind the account on next
     * password reset, exactly like the OSHI path.
     */
    private fun fallbackHash(): String {
        val user = System.getProperty("user.name") ?: UNKNOWN
        val host = System.getenv("COMPUTERNAME") ?: System.getenv("HOSTNAME") ?: UNKNOWN
        val arch = System.getProperty("os.arch") ?: UNKNOWN
        return sha256Hex("FALLBACK|$user|$host|$arch")
    }

    private fun readMotherboardSerial(hal: oshi.hardware.HardwareAbstractionLayer): String =
        runCatchingOrUnknown("motherboard serial") {
            val cs = hal.computerSystem
            val baseboard = cs.baseboard?.serialNumber
            if (!baseboard.isNullOrBlank() && baseboard != "unknown") {
                baseboard
            } else {
                // Fallback to the computer system serial when the baseboard is silent
                // (some OEM laptops only populate one of the two).
                cs.serialNumber.takeUnless { it.isNullOrBlank() || it == "unknown" }
                    ?: UNKNOWN
            }
        }

    private fun readSystemDiskSerial(hal: oshi.hardware.HardwareAbstractionLayer): String =
        runCatchingOrUnknown("disk serial") {
            val systemDrive = (System.getenv("SystemDrive") ?: "")
                .trim()
                .uppercase() // e.g. "C:"

            val disks = hal.diskStores

            val systemDisk = if (systemDrive.isNotEmpty()) {
                disks.firstOrNull { ds ->
                    ds.partitions.any { p ->
                        val mp = p.mountPoint
                        !mp.isNullOrBlank() && mp.uppercase().startsWith(systemDrive)
                    }
                }
            } else {
                null
            }

            val picked = systemDisk ?: disks.firstOrNull()
            picked?.serial?.takeUnless { it.isBlank() } ?: UNKNOWN
        }

    private fun readCpuId(hal: oshi.hardware.HardwareAbstractionLayer): String =
        runCatchingOrUnknown("cpu id") {
            hal.processor.processorIdentifier.processorID
                ?.takeUnless { it.isBlank() }
                ?: UNKNOWN
        }

    private fun readPrimaryMac(hal: oshi.hardware.HardwareAbstractionLayer): String =
        runCatchingOrUnknown("mac address") {
            hal.networkIFs.firstOrNull { nif ->
                nif.ifOperStatus == NetworkIF.IfOperStatus.UP &&
                    nif.macaddr.isNotBlank() &&
                    !nif.macaddr.startsWith("00:00:00")
            }?.macaddr ?: UNKNOWN
        }

    private inline fun runCatchingOrUnknown(component: String, block: () -> String): String =
        try {
            val value = block()
            if (value.isBlank()) UNKNOWN else value
        } catch (t: Throwable) {
            log.debug("Failed to read {}, substituting UNKNOWN", component, t)
            UNKNOWN
        }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(b.toUByte().toString(16).padStart(2, '0'))
        }
        return sb.toString()
    }

    companion object {
        const val UNKNOWN: String = "UNKNOWN"
        private val log = LoggerFactory.getLogger(OshiHwidCollector::class.java)
    }
}
