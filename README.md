# Поиск телефона по брелоку (замена iSearching)

[![CI](https://github.com/megannnn98/isearch-tracker/actions/workflows/ci.yml/badge.svg)](https://github.com/megannnn98/isearch-tracker/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Минимальное приложение: брелок с BLE подключается один раз, затем двойное нажатие кнопки включает громкий сигнал на телефоне. Работает в фоне через foreground-службу, без root, Accessibility, интернета и серверов.

Статус: код собран, unit-тесты проходят. **На реальном телефоне и брелоке ничего не проверено**, см. `docs/TEST_REPORT.md`.

## Состав

- `tracker/` проект Android (Kotlin, Gradle).
- `dist/tracker-debug.apk` собранный debug APK.
- `docs/REVERSE_ENGINEERING.md` результаты анализа iSearching.
- `docs/TEST_REPORT.md` тесты, чек-лист проверки на телефоне, ограничения.
- `original/` оригинальные APK iSearching и SHA-256 (не изменялись).

## Сборка

```
cd tracker
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew testDebugUnitTest assembleDebug
```

Нужны JDK 17 и Android SDK (`local.properties`: `sdk.dir=...`). APK: `tracker/app/build/outputs/apk/debug/app-debug.apk`.

## Установка

```
adb install -r dist/tracker-debug.apk
```

1. Откройте приложение, нажмите «Подключить брелок», выдайте разрешения (Bluetooth, уведомления).
2. Нажмите кнопку на брелоке, выберите его в списке. Служба запустится сама, в шторке появится постоянное уведомление.
3. Нажмите «Настройки батареи» и для приложения выберите «Без ограничений». На Xiaomi/POCO также: Настройки, Приложения, Tracker, Автозапуск включить, Экономия энергии «Нет ограничений».
4. Закройте приложение, заблокируйте экран, дважды быстро нажмите кнопку брелока (интервал меньше секунды).
5. Остановить сигнал: ещё раз двойное нажатие, кнопка «Остановить сигнал» в уведомлении или в приложении.

Кнопки в приложении: проверка звука на телефоне, сигнал на самом брелоке (запись в Alert Level `2A06`), отключение брелока.

Перед первым использованием удалите или отключите оригинальный iSearching: два приложения не могут держать один брелок одновременно.

## Архитектура

| Класс | Роль |
|---|---|
| `BleScanner` | поиск по сервису `FFE0` |
| `BleConnectionManager` | GATT, подписка на `FFE1`, запись CCCD |
| `core/ConnectionSupervisor` | автомат переподключения, таймаут обнаружения сервисов (без Android) |
| `core/ReconnectPolicy` | задержка 1, 2, 4 … 30 с |
| `core/BleProtocol` | UUID и разбор пакета (`value[0] == 1`) |
| `core/ButtonEventDetector` | двойное нажатие 10 мс < dt < 1000 мс |
| `PhoneAlarm` | сигнал на потоке будильника с максимальной громкостью |
| `TrackerService` | foreground-служба `connectedDevice`, слушает включение и выключение Bluetooth |
| `BootReceiver` | запуск после перезагрузки и обновления |
| `MainActivity` | выбор брелока и состояние |

Почему не `CompanionDeviceManager`: он нужен для упрощения разрешений и привязки устройства, а не для работы в фоне. Для постоянного GATT всё равно нужна foreground-служба. Можно добавить позже.

## Ограничения

См. `docs/TEST_REPORT.md`, раздел «Ограничения».
