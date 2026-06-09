package `fun`.nightshift.launcher.client.keys

import `fun`.nightshift.launcher.client.api.BackendApiClient
import `fun`.nightshift.launcher.client.hwid.HwidCollector
import `fun`.nightshift.launcher.shared.dto.KeyActivateRequest
import `fun`.nightshift.launcher.shared.dto.KeyInfo
import `fun`.nightshift.launcher.shared.dto.KeyValidateRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Activation and validation flows for the user's access key.
 *
 * Both calls run on `Dispatchers.IO` because the HWID probe is blocking.
 * The bearer session token is automatically attached to each request via
 * [BackendApiClient.tokenProvider], so callers don't need to pass it.
 */
class KeyModule(
    private val api: BackendApiClient,
    private val hwid: HwidCollector,
) {
    suspend fun activate(keyValue: String): Result<KeyInfo> = withContext(Dispatchers.IO) {
        val hwidValue = hwid.collect()
        api.keyActivate(KeyActivateRequest(keyValue.trim(), hwidValue))
    }

    suspend fun validate(): Result<KeyInfo> = withContext(Dispatchers.IO) {
        val hwidValue = hwid.collect()
        api.keyValidate(KeyValidateRequest(hwidValue))
    }
}
