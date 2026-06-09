package `fun`.nightshift.launcher.client.i18n

/**
 * Default in-jar string bundles for `ru` and `en`. Used as the source of
 * truth when no `lang/{ru,en}.json` override is present (Requirement 20.4).
 */
internal object DefaultBundles {
    val RU: Map<String, String> = mapOf(
        // Common
        "common.appName" to "NightShift Client Recode",
        "common.subtitle" to "игровой клиент",
        "common.cancel" to "Отмена",
        "common.confirm" to "Подтвердить",
        "common.retry" to "Повторить",
        "common.loading" to "Загрузка...",
        "common.error.title" to "Ошибка",

        // Login
        "login.title" to "Вход",
        "login.field.login" to "Логин",
        "login.field.password" to "Пароль",
        "login.field.rememberMe" to "Запомнить меня",
        "login.button.login" to "Войти",
        "login.link.register" to "Создать аккаунт",
        "login.link.forgotPassword" to "Забыл пароль",

        // Register
        "register.title" to "Регистрация",
        "register.field.email" to "E-mail",
        "register.field.passwordConfirm" to "Повторите пароль",
        "register.button.register" to "Зарегистрироваться",
        "register.link.haveAccount" to "Уже есть аккаунт",

        // Key
        "key.title" to "Активация ключа",
        "key.field.key" to "Ключ",
        "key.button.activate" to "Активировать",
        "key.message.expired" to "Срок действия ключа истёк",
        "key.message.noKey" to "У этого аккаунта пока нет активного ключа",

        // Reset
        "reset.title" to "Восстановление пароля",
        "reset.field.loginOrEmail" to "Логин или e-mail",
        "reset.field.comment" to "Комментарий",
        "reset.button.requestReset" to "Отправить запрос",
        "reset.field.code" to "Код восстановления",
        "reset.field.newPassword" to "Новый пароль",
        "reset.button.confirm" to "Сменить пароль",
        "reset.message.requested" to "Запрос отправлен. Ожидайте код от администратора.",

        // Main
        "main.button.play" to "Играть",
        "main.button.settings" to "Настройки",
        "main.button.folder" to "Папка",
        "main.button.minimize" to "Свернуть",
        "main.button.close" to "Закрыть",
        "main.button.logout" to "Выйти",
        "main.online" to "Онлайн: {0}",
        "main.online.unknown" to "Онлайн: —",
        "main.key.lifetime" to "Lifetime",
        "main.key.timeLeft" to "{0} дн. {1} ч.",
        "main.key.expired" to "Ключ истёк",
        "main.key.none" to "Нет активного ключа",

        // Settings
        "settings.title" to "Настройки",
        "settings.section.memory" to "Память JVM",
        "settings.section.language" to "Язык интерфейса",
        "settings.section.sound" to "Звуки",
        "settings.section.account" to "Аккаунт",
        "settings.lang.ru" to "Русский",
        "settings.lang.en" to "English",
        "settings.sound.toggle" to "Включить звуки",
        "settings.telemetry" to "Отправлять телеметрию",

        // Game launch
        "game.preparing" to "Подготовка Minecraft...",
        "game.downloading" to "Загрузка файлов: {0}%",
        "game.launching" to "Запуск игры...",
        "game.error.title" to "Игра завершилась с ошибкой",

        // Errors
        "error.network" to "Нет подключения к серверу",
        "error.account_exists" to "Аккаунт с таким логином или e-mail уже существует",
        "error.invalid_credentials" to "Неверный логин или пароль",
        "error.hwid_mismatch" to "Аккаунт привязан к другому компьютеру",
        "error.rate_limited" to "Слишком много попыток. Попробуйте через 10 минут.",
        "error.client_outdated" to "Лаунчер устарел, требуется обновление",
        "error.key_not_found" to "Ключ не найден",
        "error.key_already_used" to "Ключ уже использован",
        "error.key_expired" to "Срок действия ключа истёк",
        "error.required" to "Поле обязательно",
        "error.login_format" to "Логин: 3–20 символов, буквы, цифры или _",
        "error.email_format" to "Введите корректный e-mail",
        "error.password_short" to "Пароль не короче 8 символов",
        "error.password_weak" to "Пароль должен содержать буквы и цифры",
        "error.passwords_mismatch" to "Пароли не совпадают",
        "error.code_length" to "Код состоит из 8 символов",
        "error.unknown" to "Неизвестная ошибка",
    )

    val EN: Map<String, String> = mapOf(
        "common.appName" to "NightShift Client Recode",
        "common.subtitle" to "game client",
        "common.cancel" to "Cancel",
        "common.confirm" to "Confirm",
        "common.retry" to "Retry",
        "common.loading" to "Loading...",
        "common.error.title" to "Error",

        "login.title" to "Sign in",
        "login.field.login" to "Login",
        "login.field.password" to "Password",
        "login.field.rememberMe" to "Remember me",
        "login.button.login" to "Sign in",
        "login.link.register" to "Create account",
        "login.link.forgotPassword" to "Forgot password",

        "register.title" to "Sign up",
        "register.field.email" to "Email",
        "register.field.passwordConfirm" to "Repeat password",
        "register.button.register" to "Register",
        "register.link.haveAccount" to "Already have an account",

        "key.title" to "Activate key",
        "key.field.key" to "Key",
        "key.button.activate" to "Activate",
        "key.message.expired" to "Your key has expired",
        "key.message.noKey" to "This account has no active key yet",

        "reset.title" to "Password reset",
        "reset.field.loginOrEmail" to "Login or email",
        "reset.field.comment" to "Comment",
        "reset.button.requestReset" to "Send request",
        "reset.field.code" to "Reset code",
        "reset.field.newPassword" to "New password",
        "reset.button.confirm" to "Change password",
        "reset.message.requested" to "Request submitted. Wait for the admin to share the code.",

        "main.button.play" to "Play",
        "main.button.settings" to "Settings",
        "main.button.folder" to "Folder",
        "main.button.minimize" to "Minimize",
        "main.button.close" to "Close",
        "main.button.logout" to "Logout",
        "main.online" to "Online: {0}",
        "main.online.unknown" to "Online: —",
        "main.key.lifetime" to "Lifetime",
        "main.key.timeLeft" to "{0}d {1}h",
        "main.key.expired" to "Key expired",
        "main.key.none" to "No active key",

        "settings.title" to "Settings",
        "settings.section.memory" to "JVM memory",
        "settings.section.language" to "Language",
        "settings.section.sound" to "Sound",
        "settings.section.account" to "Account",
        "settings.lang.ru" to "Russian",
        "settings.lang.en" to "English",
        "settings.sound.toggle" to "Enable sounds",
        "settings.telemetry" to "Send telemetry",

        "game.preparing" to "Preparing Minecraft...",
        "game.downloading" to "Downloading files: {0}%",
        "game.launching" to "Launching game...",
        "game.error.title" to "Minecraft exited with an error",

        "error.network" to "Cannot reach the server",
        "error.account_exists" to "Login or email already in use",
        "error.invalid_credentials" to "Wrong login or password",
        "error.hwid_mismatch" to "Account is bound to a different machine",
        "error.rate_limited" to "Too many attempts. Try again in 10 minutes.",
        "error.client_outdated" to "Launcher is outdated, update required",
        "error.key_not_found" to "Key not found",
        "error.key_already_used" to "Key has already been used",
        "error.key_expired" to "Key has expired",
        "error.required" to "Required",
        "error.login_format" to "Login: 3–20 chars, letters, digits or _",
        "error.email_format" to "Enter a valid email",
        "error.password_short" to "Password must be at least 8 characters",
        "error.password_weak" to "Password must contain letters and digits",
        "error.passwords_mismatch" to "Passwords don't match",
        "error.code_length" to "Code is 8 characters long",
        "error.unknown" to "Unknown error",
    )
}
