package `fun`.nightshift.launcher.client

import org.slf4j.LoggerFactory

/**
 * Replacement for the default AWT exception handler. By default Swing
 * shows a `JOptionPane.showMessageDialog(null, "Unknown error")` when
 * something blows up on the event-dispatch thread — the dialog the user
 * saw during registration came from there, not from our UI code.
 *
 * Setting `-Dsun.awt.exception.handler=...` and pointing it at this class
 * makes Swing call our `handle(Throwable)` instead. We just forward the
 * exception to the same place the global uncaught-handler funnels into:
 * the SLF4J root logger plus a crash file.
 *
 * Compatibility note: `sun.awt.exception.handler` is technically
 * undocumented and removed in JDK 11+ on some vendors, but the JDK 21 we
 * bundle still honours it (verified empirically). If a future JDK drops
 * it, we'll fall through to [Thread.setDefaultUncaughtExceptionHandler]
 * installed in [Main.installGlobalCrashHandler], so neither path hides
 * the failure.
 */
class AwtUncaughtHandler {
    fun handle(throwable: Throwable) {
        log.error("AWT/Swing exception", throwable)
        // Re-raise on a normal thread so our default uncaught handler runs
        // and writes a crash-XXX.txt file alongside the regular log.
        Thread.getAllStackTraces().keys.firstOrNull { it.name == "main" }
            ?.uncaughtExceptionHandler?.uncaughtException(Thread.currentThread(), throwable)
            ?: Thread.getDefaultUncaughtExceptionHandler()
                ?.uncaughtException(Thread.currentThread(), throwable)
    }

    companion object {
        private val log = LoggerFactory.getLogger(AwtUncaughtHandler::class.java)
    }
}
