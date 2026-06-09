# OTA Mod Distribution — Операционный гайд

Этот документ описывает, как доставлять новые версии mod-jar'ов (`NightShift Client Beta`, `fabric-api`, `baritone-api-fabric`) пользователям без выпуска нового `.exe` лаунчера. Источник правды для требований: `.kiro/specs/ota-mod-and-launcher-updates/`.

## Где живут файлы

| Сторона | Путь | Кто читает |
|---------|------|------------|
| Сервер (Mods_Source_Dir) | `~/.nightshift-backend/mods/` (Linux/macOS) или `C:\Users\<user>\.nightshift-backend\mods\` (Windows) | Бэкенд при `GET /mod/manifest` |
| Сервер (override) | значение env-переменной `MODS_DIR` | Перекрывает дефолт выше |
| Клиент (кеш) | `%APPDATA%\NightShiftClient\cache\mods\` | `RemoteModJarSource` сравнивает SHA-256 перед каждым запуском игры |
| Клиент (рабочий) | `%APPDATA%\NightShiftClient\minecraft\mods\` | Minecraft + Fabric loader при запуске |

## Стартовое наполнение сервера

Бэкенд при первом старте создаёт пустой `Mods_Source_Dir` сам. Стартовый набор jar'ов нужно положить туда вручную:

**Windows PowerShell:**
```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\.nightshift-backend\mods"
Copy-Item nightshift-launcher\launcher-client\src\main\resources\mods\*.jar `
    "$env:USERPROFILE\.nightshift-backend\mods\"
```

**Linux/macOS:**
```bash
mkdir -p ~/.nightshift-backend/mods
cp nightshift-launcher/launcher-client/src/main/resources/mods/*.jar ~/.nightshift-backend/mods/
```

Проверка через curl:
```
curl http://127.0.0.1:8080/mod/manifest
```

Должны вернуться три entry с правильными `sha256`/`sizeBytes`.

## Публикация новой версии мода

### Через CLI (`launcher-mod-publisher`, рекомендуется)

> Будет реализовано в задаче 6 спека `ota-mod-and-launcher-updates`. После сборки:

```powershell
$env:NIGHTSHIFT_BACKEND_URL = "http://127.0.0.1:8080"
$env:NIGHTSHIFT_ADMIN_TOKEN = "<your-secret>"
nightshift-publish --file ".\NightShift Client Beta-2.3.jar" --name "NightShift Client Beta-2.3.jar"
```

Exit codes:
- `0` — успех (HTTP 200, файл записан и manifest пересчитан)
- `2` — HTTP 4xx/5xx, тело в stderr
- `3` — сетевая ошибка
- `4` — локальный файл недоступен
- `64` — неверные аргументы

### Через curl (fallback)

```
curl -X POST `
  -H "X-Admin-Token: $env:ADMIN_TOKEN" `
  -H "Content-Type: application/octet-stream" `
  --data-binary "@./NightShift Client Beta-2.3.jar" `
  "http://127.0.0.1:8080/admin/mod/upload/NightShift%20Client%20Beta-2.3.jar"
```

Бэкенд:
1. Принимает поток в `{name}.tmp`,
2. Атомарным `Files.move(REPLACE_EXISTING)` заменяет старый файл,
3. Логирует `[admin] uploaded mod 'X.jar' (Y KiB)`.

`GET /mod/manifest` сразу же отражает новый SHA-256 без рестарта.

## Защита

| Слой | Реализация |
|------|------------|
| Path traversal | Backend отклоняет имена с `..`, `/`, `\` (HTTP 400 `validation_failed`). Upload дополнительно требует `.jar`. |
| Admin token | `X-Admin-Token` сверяется с env `ADMIN_TOKEN`. При пустой env — dev-режим (защита отключена). Никогда не логируется. |
| Atomic write | `tmp + Files.move(REPLACE_EXISTING)` — manifest не считает SHA-256 поверх полу-перезаписанного jar. |
| SHA-256 dedup | Клиент не качает то, что уже в кеше с правильным хешем. |
| Retry-then-fallback | На SHA-256 mismatch — повтор 1 раз, потом fallback на embedded jar'ы. |

## Production-чеклист

- [ ] Установить `ADMIN_TOKEN` в env бэкенда (любая длинная случайная строка).
- [ ] Настроить HTTPS для всех эндпоинтов бэкенда (Nginx + Let's Encrypt).
- [ ] Положить стартовые три jar'а в `~/.nightshift-backend/mods/` на сервере.
- [ ] Проверить `GET /mod/manifest` через curl с production URL.
- [ ] Опубликовать тестовый jar через `nightshift-publish` и убедиться что SHA-256 в манифесте обновился.
- [ ] Проверить что лаунчер на чистой машине скачивает все три jar'а и стартует Minecraft.

## Troubleshooting

### Лаунчер не подтягивает обновлённый jar

1. Проверить `%APPDATA%\NightShiftClient\logs\launcher-plain.log` — должны быть строки `Backend manifest: 3 mods` и `Downloading mod ... (reason: missing|sha-mismatch)`.
2. Если в логе `Backend unreachable; falling back to N embedded mods` — проверить `NIGHTSHIFT_BACKEND_URL` и сетевой доступ.
3. Удалить кеш `%APPDATA%\NightShiftClient\cache\mods\` и попробовать снова — заставит лаунчер пересчитать SHA-256 и подтянуть всё заново.

### `GET /mod/manifest` возвращает пустой список

`Mods_Source_Dir` пуст или путь не тот. Проверить env `MODS_DIR` и реальный каталог. Бэкенд логирует путь при инициализации `modRoutes(modsDir)`.

### CLI получает HTTP 401 `unauthorized`

Не передан или не совпадает `X-Admin-Token`. Проверить `NIGHTSHIFT_ADMIN_TOKEN` локально и `ADMIN_TOKEN` на бэкенде.
