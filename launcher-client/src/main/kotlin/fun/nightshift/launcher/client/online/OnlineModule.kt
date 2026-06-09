package `fun`.nightshift.launcher.client.online

import `fun`.nightshift.launcher.client.api.BackendApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

/**
 * Periodic online counter + heartbeat agent.
 *
 * The Main screen drives this via [start] when it appears and [stop] when
 * the user logs out / closes the launcher. Both polls run on `Dispatchers.IO`
 * with a 30-second cadence (Requirements 13.2 & 13.5). Failures don't
 * propagate — they degrade the counter to "—" without breaking the UI.
 */
class OnlineModule(
    private val api: BackendApiClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lastCount = AtomicReference<Int?>(null)
    private var pollJob: Job? = null
    private var heartbeatJob: Job? = null

    /** Last successfully fetched online count, or `null` when not yet known. */
    val cachedCount: Int? get() = lastCount.get()

    /**
     * Starts both poll & heartbeat loops. Idempotent: a second call replaces
     * the existing jobs cleanly so we don't accumulate timers.
     */
    fun start(onCountChanged: (Int?) -> Unit) {
        stop()
        pollJob = scope.launch {
            while (isActive) {
                val res = api.onlineCount()
                res.onSuccess {
                    lastCount.set(it)
                    onCountChanged(it)
                }.onFailure {
                    log.debug("online/count failed: {}", it.message)
                    onCountChanged(lastCount.get())
                }
                delay(30_000)
            }
        }
        heartbeatJob = scope.launch {
            while (isActive) {
                api.onlineHeartbeat().onFailure {
                    log.debug("online/heartbeat failed: {}", it.message)
                }
                delay(30_000)
            }
        }
    }

    fun stop() {
        pollJob?.cancel(); pollJob = null
        heartbeatJob?.cancel(); heartbeatJob = null
    }

    /**
     * Sends a final heartbeat-style request before the user closes the
     * launcher; failures are swallowed because we're shutting down anyway.
     */
    suspend fun sendFinalLogoutHeartbeat() = withContext(Dispatchers.IO) {
        runCatching { api.onlineHeartbeat() }
    }

    fun shutdown() {
        stop()
        scope.cancel()
    }

    companion object {
        private val log = LoggerFactory.getLogger(OnlineModule::class.java)
    }
}
