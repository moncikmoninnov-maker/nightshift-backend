package `fun`.nightshift.launcher.client.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import `fun`.nightshift.launcher.client.auth.AuthModule
import `fun`.nightshift.launcher.client.config.LauncherConfig
import `fun`.nightshift.launcher.client.config.LauncherConfigStore
import `fun`.nightshift.launcher.client.game.GameLauncher
import `fun`.nightshift.launcher.client.game.GameLauncherException
import `fun`.nightshift.launcher.client.i18n.LocalLocalization
import `fun`.nightshift.launcher.client.i18n.LocalizationManager
import `fun`.nightshift.launcher.client.keys.KeyModule
import `fun`.nightshift.launcher.client.online.OnlineModule
import `fun`.nightshift.launcher.client.paths.LauncherPaths
import `fun`.nightshift.launcher.client.ui.screens.KeyScreen
import `fun`.nightshift.launcher.client.ui.screens.LoginScreen
import `fun`.nightshift.launcher.client.ui.screens.MainScreen
import `fun`.nightshift.launcher.client.ui.screens.PasswordResetScreen
import `fun`.nightshift.launcher.client.ui.screens.RegisterScreen
import `fun`.nightshift.launcher.client.ui.screens.SettingsScreen
import `fun`.nightshift.launcher.client.ui.theme.NightShiftBackdrop
import `fun`.nightshift.launcher.client.ui.theme.NightShiftTheme
import `fun`.nightshift.launcher.client.ui.theme.NsColors
import `fun`.nightshift.launcher.shared.dto.AccountInfo
import `fun`.nightshift.launcher.shared.dto.KeyStatus
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * State machine for the launcher UI.
 *
 * The hierarchy is intentionally flat: an enum dictates which screen is
 * visible and a few `mutableStateOf` slots carry the data each screen
 * consumes. There's no NavController-style stack because the launcher only
 * has linear flows (Login → Key → Main, Settings as a modal-like overlay).
 */
@Composable
fun App(
    auth: AuthModule,
    keys: KeyModule,
    online: OnlineModule,
    gameLauncher: GameLauncher,
    paths: LauncherPaths,
    configStore: LauncherConfigStore,
    initialConfig: LauncherConfig,
    localization: LocalizationManager,
    offlineMode: Boolean = false,
    onMinimizeWindow: () -> Unit,
    onCloseWindow: () -> Unit,
    onGameLaunch: suspend (Int, AccountInfo, GameLauncher, (String) -> Unit) -> Unit,
) {
    val log = remember { LoggerFactory.getLogger("App") }
    val scope = rememberCoroutineScope()

    var route by remember { mutableStateOf<Route>(Route.Bootstrapping) }
    var account by remember { mutableStateOf<AccountInfo?>(null) }
    var config by remember { mutableStateOf(initialConfig) }
    var launching by remember { mutableStateOf(false) }
    var launchStatus by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (offlineMode) {
            // Portable build: synthesise a lifetime account and skip the
            // login/key flow entirely. The user lands on the Play screen.
            val syntheticAccount = AccountInfo(
                id = "offline",
                login = "Player",
                keyStatus = KeyStatus.Active(
                    `fun`.nightshift.launcher.shared.dto.KeyInfo(
                        type = `fun`.nightshift.launcher.shared.dto.KeyType.LIFETIME,
                        activatedAt = "1970-01-01T00:00:00Z",
                        expiresAt = null,
                        remainingTimeMs = null,
                        lifetime = true,
                    )
                )
            )
            account = syntheticAccount
            route = Route.Main
            return@LaunchedEffect
        }
        // Try silent auto-login from the saved session token (Req. 6.5).
        val res = auth.tryAutoLogin()
        res.fold(
            onSuccess = { restored ->
                if (restored != null) {
                    account = restored
                    route = routeForAccount(restored)
                } else {
                    route = Route.Login
                }
            },
            onFailure = {
                log.info("Auto-login failed; showing LoginScreen ({})", it.message)
                route = Route.Login
            }
        )
    }

    NightShiftTheme {
        CompositionLocalProvider(LocalLocalization provides localization) {
            NightShiftBackdrop {
                Crossfade(targetState = route, animationSpec = tween(durationMillis = 280)) { r ->
                    when (r) {
                    Route.Bootstrapping -> Loading()
                    Route.Login -> LoginScreen(
                        auth = auth,
                        config = config,
                        configStore = configStore,
                        onSuccess = { acc ->
                            account = acc
                            route = routeForAccount(acc)
                        },
                        onRegister = { route = Route.Register },
                        onForgotPassword = { route = Route.PasswordReset },
                        onMinimize = onMinimizeWindow,
                        onClose = onCloseWindow,
                    )
                    Route.Register -> RegisterScreen(
                        auth = auth,
                        onSuccess = { acc ->
                            account = acc
                            route = routeForAccount(acc)
                        },
                        onBackToLogin = { route = Route.Login },
                        onMinimize = onMinimizeWindow,
                        onClose = onCloseWindow,
                    )
                    Route.PasswordReset -> PasswordResetScreen(
                        auth = auth,
                        onBackToLogin = { route = Route.Login },
                    )
                    is Route.Key -> KeyScreen(
                        keys = keys,
                        initialMessage = r.message,
                        onActivated = { keyInfo ->
                            // After activation, refresh the account so the Main
                            // screen sees the new key status; if the validate
                            // call fails, fall back to a synthesised status
                            // built from the activate response.
                            scope.launch {
                                auth.refreshAccount().fold(
                                    onSuccess = { acc ->
                                        account = acc
                                        route = Route.Main
                                    },
                                    onFailure = {
                                        val current = account
                                        if (current != null) {
                                            account = current.copy(keyStatus = KeyStatus.Active(keyInfo))
                                            route = Route.Main
                                        }
                                    }
                                )
                            }
                        },
                        onLogout = {
                            scope.launch {
                                auth.logout()
                                online.stop()
                                account = null
                                route = Route.Login
                            }
                        },
                    )
                    Route.Main -> {
                        val acc = account
                        if (acc == null) {
                            route = Route.Login
                        } else {
                            MainScreen(
                                auth = auth,
                                online = online,
                                paths = paths,
                                config = config,
                                configStore = configStore,
                                account = acc,
                                onLogout = {
                                    scope.launch {
                                        auth.logout()
                                        online.stop()
                                        account = null
                                        route = Route.Login
                                    }
                                },
                                onSettings = { route = Route.Settings },
                                onPlay = {
                                    if (launching) return@MainScreen
                                    val memMb = config.jvmMemoryMb
                                    val launchAccount = acc
                                    launching = true
                                    launchStatus = "Подготовка Minecraft..."
                                    scope.launch {
                                        runCatching {
                                            onGameLaunch(memMb, launchAccount, gameLauncher) { msg ->
                                                launchStatus = msg
                                            }
                                        }.onFailure { log.warn("Game launch failed: {}", it.message, it) }
                                        launching = false
                                        launchStatus = null
                                    }
                                },
                                launching = launching,
                                launchStatus = launchStatus,
                                onActivateKey = { route = Route.Key(message = null) },
                                onMinimize = onMinimizeWindow,
                                onClose = onCloseWindow,
                            )
                        }
                    }
                    Route.Settings -> SettingsScreen(
                        initial = config,
                        configStore = configStore,
                        localization = localization,
                        onBack = {
                            config = it
                            route = Route.Main
                        },
                        onLogout = {
                            scope.launch {
                                auth.logout()
                                online.stop()
                                account = null
                                route = Route.Login
                            }
                        },
                    )
                }
                }
            }
        }
    }
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = NsColors.Accent)
    }
}

private sealed class Route {
    data object Bootstrapping : Route()
    data object Login : Route()
    data object Register : Route()
    data object PasswordReset : Route()
    data class Key(val message: String?) : Route()
    data object Main : Route()
    data object Settings : Route()
}

private fun routeForAccount(account: AccountInfo): Route = when (account.keyStatus) {
    is KeyStatus.Active -> Route.Main
    KeyStatus.Expired -> Route.Key(message = "key.message.expired")
    KeyStatus.NoKey -> Route.Key(message = "key.message.noKey")
}
