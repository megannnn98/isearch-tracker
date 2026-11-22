# Реверс-инжиниринг iSearching G 1.0.6

Источник: `original/iSearching_G_1.0.6.apks` (SHA-256 в `original/SHA256SUMS`).
Метод: статический анализ `jadx` (декомпиляция `base.apk` в `work/jadx`) и `apktool` (манифест).
Динамический анализ (logcat, HCI snoop) **не выполнялся**. Что не подтверждено, помечено **ГИПОТЕЗА**.
REA MCP в сессии недоступен, использованы `jadx` и `apktool` напрямую.

Пути ниже относятся к `work/jadx/sources/com/lenzetech/isearchingtwo/`. Номера строк взяты из вывода jadx.

## 1. Общие сведения

| Параметр | Значение | Доказательство |
|---|---|---|
| package | `com.mingmao.isearch` | `AndroidManifest.xml`, `package=` |
| compileSdk | 35 | `AndroidManifest.xml` |
| Фоновая служба | `MyService`, `exported=true`, **без** `foregroundServiceType` | `AndroidManifest.xml` |
| Разрешения BLE | `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | `AndroidManifest.xml` |
| Отсутствуют | `FOREGROUND_SERVICE*`, `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `POST_NOTIFICATIONS`, `REORDER_TASKS` | `AndroidManifest.xml` |
| BLE-код | Целиком в `application/MyApplication.java`, не в службе | см. раздел 3 |

## 2. Механизм передачи события: GATT notification

Событие нажатия кнопки приходит как GATT notification (не advertising) на характеристику `0000ffe1-0000-1000-8000-00805f9b34fb`.
Подтверждение: `MyApplication.java` стр. 551–553 вызывает `setCharacteristicNotification(FFE1, true)`, а `onCharacteristicChanged` (стр. 626–640) принимает данные от FFE1.

## 3. UUID и протокол

Все UUID взяты из кода. Ничего не придумано.

| Роль | UUID | Где | Использование |
|---|---|---|---|
| Фильтр сканирования (сервис) | `0000ffe0-0000-1000-8000-00805f9b34fb` | `MyApplication.startDiscovery`, стр. 283 | `ScanFilter.setServiceUuid` |
| Кнопка (notify) | `0000ffe1-…` | стр. 551, 628 | подписка, приём событий |
| Настройка «сигнал при разрыве связи» (write) | `0000ffe2-…` | стр. 554–561 | запись `{1}` или `{0}`, `SetDeviceISAlarm` стр. 403–421 |
| Alert Level (звонок на брелоке, write) | `00002a06-…` | стр. 546–550 | запись `{1}` включает, `{0}` выключает: `AlarmByAddress` стр. 377–388, `CancleAlarmByAddress` стр. 390–401 |
| Battery Level (read) | `00002a19-…` | стр. 562–565 | `readCharacteristic`, значение `value[0]` (`onCharacteristicRead` стр. 608–616) |

Формат сообщения кнопки (`onCharacteristicChanged`, стр. 629–639):
- Событием считается пакет, у которого `value.length > 0` и `value[0] == 1`.
- Остальные байты и пакеты с другим первым байтом игнорируются.
- Это единственный известный формат. Длина пакета и значения кроме `0x01` в коде не разбираются.

**Подача сигнала на брелок возможна**: запись `0x01` в `2A06` (стоп: `0x00`). Работоспособность на железе не проверена.

## 4. Цепочка `BLE event → handler → double click → phone alarm`

1. **Сканирование.** `MyApplication.startDiscovery()` (стр. 257–315) запускает `BluetoothLeScanner.startScan` с фильтром сервиса `FFE0`.
   - Режим `SCAN_MODE_LOW_LATENCY` (значение 2), `CALLBACK_TYPE_ALL_MATCHES`, `setLegacy(true)`.
   - Сканирование останавливается через `FocusMeteringAction.DEFAULT_AUTO_CANCEL_DURATION_MILLIS`. Это константа CameraX, обычно 5000 мс (**ГИПОТЕЗА**: значение не проверено).
2. **Найден брелок.** `scanCallbackH.onScanResult` (стр. 105–129) требует `getName() != null`, затем вызывает `OnMyDeviceFound` (стр. 317). Для сохранённого устройства (`isMine()`) вызывается `ConnectBleByIndexORMac` (стр. 324).
3. **Подключение.** `ConnectBleByIndexORMac` (стр. 456–677): `device.connectGatt(ctx, autoConnect=false, callback)`. По `STATE_CONNECTED` вызывается `discoverServices()` (стр. 494).
4. **Подписка.** `onServicesDiscovered` (стр. 531–605): для `FFE1` вызывается только `setCharacteristicNotification(char, true)`.
5. **Приём события.** `onCharacteristicChanged` (стр. 626–640): если `value[0] == 1`, вызывается `MediaPlayerTools.getInstance().OnFDQClick(mac)`.
6. **Распознавание двойного нажатия.** `Utils/MediaPlayerTools.java`, `OnFDQClick` (стр. 27–75):
   - Режимы `Photo` и `Recording` (`MyClickMode.nowMode`) обрабатываются отдельно.
   - В режиме `Normal`: если `lastClickTime == 0`, это первое нажатие (запоминается время).
   - Следующее нажатие с интервалом `10 мс < dt < 1000 мс` считается двойным нажатием, после чего `lastClickTime = 0`.
   - Иначе время перезаписывается и нажатие считается «первым».
   - Это чистое сравнение по `System.currentTimeMillis()`, без таймера. Одиночное нажатие ничего не делает.
7. **Сигнал на телефоне.** Двойное нажатие вызывает `PlaySound(mac)` (стр. 90–101).
   - `MediaPlayer.create(rawResource)` по `ringIndex` из `getResIdByAddress` (`R.raw.ring0`–`ring8`), громкость `setVolume(1,1)`, `setLooping(true)`.
   - Повторное двойное нажатие вызывает `Pause()` и гасит сигнал, если `isPlaying`.
   - Атрибуты звука (`AudioAttributes`/stream) **не заданы**, `AudioManager` не используется, то есть громкость не поднимается до максимума, а звук идёт по потоку по умолчанию (**ГИПОТЕЗА**: поток не проверен).

## 5. Переподключение

- При `STATE_DISCONNECTED` (`onConnectionStateChange`, стр. 501–527) вызывается `ConnectBleByIndexORMac` снова. Каждый раз создаётся **новый** `connectGatt` с `autoConnect=false`, а старый `BluetoothGatt` не закрывается через `close()` (в этом файле `close()` не вызывается; не подтверждено по всему APK). Возможна утечка клиентов GATT.
- Сигнал при потере связи: если включён флаг `isAlarmOnDisconnect` и пройдены проверки Wi-Fi и времени, вызывается `PlaySound`.
- Повторный запуск сканирования: `BluetoothMonitorReceiver` (регистрируется динамически в `MyApplication.onCreate`, стр. 153–156) и по `STATE_ON` (12) вызывает `startDiscovery()`. Манифест этот ресивер не объявляет.
- Если процесс уничтожен, все эти механизмы исчезают вместе с ним.

## 6. Почему перестаёт работать после удаления из недавних

Подтверждено кодом и манифестом:
- GATT-соединения, состояние, ресивер Bluetooth и логика двойного нажатия живут в `MyApplication` (процесс приложения).
- `MyService` ничего не делает с BLE. Раз в 3 с он вызывает `UPDATERssi()` (`MyService.java`) и не является foreground-службой.
- `MyApplication.onCreate` (стр. 150–152) запускает `MyService` **только при API < 26**. На Android 14 служба не стартует вообще.
- Нет `FOREGROUND_SERVICE`, нет `RECEIVE_BOOT_COMPLETED`, нет запроса на исключение из оптимизации батареи.

Следствие: на Android 14 в HyperOS/MIUI процесс удаляется вместе с карточкой, GATT закрывается, ресивер и таймер исчезают. Это согласуется с описанной проблемой. Это объяснение по коду, но поведение на телефоне пока не воспроизведено.

## 7. Открытые вопросы (требуют динамической проверки)

1. **CCCD не записывается.** Во всём коде нет `writeDescriptor`, UUID `2902` и `ENABLE_NOTIFICATION_VALUE` (проверено `grep`). Приложение вызывает только локальный `setCharacteristicNotification`. Обычно этого мало, чтобы периферия начала присылать уведомления. Значит, либо у FFE1 нет CCCD и брелок шлёт данные всегда, либо он игнорирует CCCD.
   - Проверка: HCI snoop log и лог свойств характеристик. Приложение само пишет в logcat тег `蓝牙`: `ServerUUID:… CharaProperities:… Descriptors UUID:…`.
   - В новом коде CCCD записывать, если дескриптор есть, и логировать свойства.
2. **Фильтр сканирования.** Сервис `FFE0` в рекламном пакете нужно сверить с реальным брелоком. Для этого нужны logcat теги `发现设备`.
3. **Имя устройства.** Приложение игнорирует результаты без имени (`getName() == null`). Какое имя у твоего брелока, неизвестно.
4. **Байт `0x01`.** Не видно, меняется ли первый байт для одиночного, двойного и долгого нажатия на самом брелоке.

## 8. Прочие находки

- **Сетевой обмен.** После `onServicesDiscovered` приложение отправляет POST на `http://47.122.0.200:1234/fdqverify/Index/androidLogin` (стр. 571). Тело содержит имя устройства, рекламные данные, MAC, список характеристик и версию приложения. Ответ с `verify=false` вызывает `MessageEvent DEVICE_VERIFY`.
  Это передача данных по открытому HTTP на сторонний сервер. В замену этот код **не переносить**: по требованиям никакого интернета.
- Двойное нажатие может переключать режим записи звука (`isDoubleClickIsRecording`), в замене не нужно.
- Прочие компоненты: геолокация, камера, PDF, запись звука. В замену не переносятся.

## 9. Подтверждение на железе (2026-11-22)

Брелок «iTAG» D4:80:30:XX:XX:XX, POCO 22071219CG, Android 14. Подробности в `docs/TEST_REPORT.md`.

- Раздел 7, п. 1: у `FFE1` есть CCCD и свойство NOTIFY, запись CCCD проходит, уведомления приходят.
- Раздел 7, п. 3: имя в рекламе `iTAG`.
- Раздел 7, п. 4: двойное нажатие даёт два пакета `FFE1 = 01` с интервалом 90–260 мс. Другие жесты не проверялись.
- Уточнение к разделу 3: `2A06` есть и в Link Loss (`1803`), и в Immediate Alert (`1802`). Оригинал дописывал последнюю найденную, то есть `1802`.
