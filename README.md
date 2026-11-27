<p align="center">
  <img src="logo.png" width="220" alt="STORM GBCA Logo" />
</p>

<h1 align="center">STORM GBCA</h1>

<p align="center">
  <strong>Флагманский кроссплатформенный эмулятор Nintendo Game Boy, Game Boy Color и Game Boy Advance для Android и Windows x64 (mGBA + SameBoy + Vulkan 1.1 + RetroArch librashader + 40 аутентичных 3D-скинов + Link Cable / Wireless RFU / Dolphin JoyBus / GB Printer + интерфейс STORM DS Watermelon / AuroraFluid)</strong><br />
  Версия <code>1.3.0</code> | Платформы: <code>Android (arm64-v8a, armeabi-v7a, x86_64)</code> и <code>Windows x64</code> | Лицензия: <code>MPL-2.0</code>
</p>

<p align="center">
  <a href="https://github.com/ReiKatari/STORM_GBCA">GitHub Repository</a> -
  <a href="https://github.com/ReiKatari/STORM_GBCA/releases/tag/1.3.0">Релиз STORM GBCA 1.3.0</a> -
  <a href="https://stormgamesworld.ru/programs?repo=STORM_GBCA">Страница на STORM GAMES WORLD</a>
</p>

---

## О проекте

**STORM GBCA** - это современный гибридный эмулятор семейства портативных консолей **Nintendo Game Boy (DMG / Pocket / Light / Super Game Boy)**, **Nintendo Game Boy Color (GBC)** и **Nintendo Game Boy Advance (GBA / GBA SP / Game Boy Micro)** для **Android 8.0+ (API 26+)** и **Windows 10/11 (x64)**.

Проект выполнен в фирменном визуальном стиле экосистемы **STORM** (единая дизайн-система **Watermelon** на Android и **AuroraFluid** на Windows, полностью повторяющая эргономику **STORM DS** и **STORM 3DS**), открывается мгновенно в истинно полноэкранном режиме без системных панелей и стартовой заставки, полностью очищен от элементов Nintendo DS / 3DS и объединяет мощь ядер **mGBA** и **SameBoy** с поддержкой **Vulkan 1.1**, **шейдеров RetroArch (`.slangp` via `librashader`)**, внутреннего **апскейлинга `1x..4x`**, **40 аутентичных 3D-корпусов консолей**, **раздельных кнопок A и B**, **полного сетевого и локального мультиплеера (Link Cable / Wireless RFU / IR / Dolphin JoyBus / GB Printer)** и **живого сканера читов в RAM**.

---

## Что нового в версии 1.3.0

- **Компактный хидер STORM DS и аккуратное двухрядное верхнее меню**:
  - Убран кубок RetroAchievements из верхнего хидера (доступен в Настройках и игровом меню), уменьшена высота шапки до эталонных `48 dp` в одну строку (`STORM GBCA`) точь-в-точь как в **STORM DS** и **STORM 3DS**.
  - Верхнее меню переработано в чистую двухрядную структуру: 1-й ряд - `Все`, `GB`, `GBC`, `GBA`; 2-й ряд - `Избранное`, `Файл`, `Обновить`. Кнопка `Папка` убрана из верхней панели и аккуратно перенесена в **Настройки -> Библиотека, обложки и бейджи**.
- **Исправлена загрузка обложек и бейджей для 100% игр (включая `[MOD - M. RUS]` и `[JP]` ромы)**:
  - Тройной быстрый CDN для обложек Libretro (`cdn.jsdelivr.net` -> `fastly.jsdelivr.net` -> `raw.githubusercontent.com`) с резервом `Named_Titles`, автоматическим fallback на Libretro для русифицированных и модифицированных ROM, отсутствующих в хеш-базе RetroAchievements, удалением года выпуска и нормализацией римских цифр (`II/III/IV/VI`) с приоритетом региона.
  - Бейдж кубка **RetroAchievements** теперь проверяет реальное наличие достижений по MD5-хешу ROM (с локальным кэшем) и отображается только для игр с поддержкой RA (как в **STORM DS**); в списочный режим добавлены недостающие аппаратные бейджи (`RTC`, `SOLAR`, `RUMBLE`, `GYRO`) и аккуратный бейдж региона.
- **40 фотореалистичных 3D-скинов консолей (+ режим без корпуса) на Android и Windows**:
  - **Game Boy (12 моделей)**: `DMG-01 Классический серый`, `DMG Play It Loud` (Черный, Красный, Желтый, Зеленый), `DMG Прозрачный (PCB)`, `Game Boy Pocket` (Серебро, Ледяной, Неоновый зеленый), `Game Boy Light Золото`, `Super Game Boy (SNES)`, `STORM Aurora GB`.
  - **Game Boy Color (12 моделей)**: `Атомный фиолет (PCB)`, `Ягодный маджента`, `Бирюзовый Teal`, `Киви Лайм`, `Одуванчик Желтый`, `Виноградный фиолет`, `Полуночный синий (PCB)`, `Кристальный лед (PCB)`, `Изумрудный прозрачный`, `Pokemon Пикачу и Пичу`, `Pokemon Золото / Серебро`, `STORM Aurora GBC`.
  - **Game Boy Advance (16 моделей)**: `AGB-001` (Индиго, Ледник PCB, Арктический белый, Пряный оранжевый, Фуксия PCB, Полуночный синий), `GBA SP` (Кобальт, Черный оникс, Платина, Огненный красный, Жемчужный AGS-101, NES Классик, Zelda Triforce Gold), `Game Boy Micro` (Famicom 20th, Серебро), `STORM Aurora GBA`.
  - Аутентичная геометрия каждого форм-фактора: скошенный угол, верхние канавки, двойные полосы и 6 прорезей DMG-01; ИК-порт и 5-цветная надпись `C-O-L-O-R` на GBC; плечевые триггеры AGB-001; 3-секционный цилиндрический шарнир, 4 резиновых демпфера, 16-точечный динамик и двойной индикатор питания GBA SP; сменная панель GB Micro; внутренние платы PCB, микросхема CPU и стойки винтов в прозрачных корпусах.
- **Раздельные кнопки A и B (исправлено наложение!)**:
  - Исправлена математическая ошибка перекрытия кнопок `A` и `B` в стандартной раскладке.
  - Добавлен переключатель **«Раздельные кнопки A и B»** (независимое перемещение каждой кнопки в визуальном редакторе раскладки!) и ползунок **«Расстояние между кнопками A и B» (`95%..200%`)**.
- **Расширенная настройка экрана (включая 1-клик «Во весь экран 100%»)**:
  - Быстрые пресеты экрана: **Внутри 3D-корпуса**, **Максимум (пропорции)**, **Во весь экран (100%)**, **Пиксель-в-пиксель**, **Свободная настройка**, а также выравнивание по верху/центру/низу, поворот `0/90/180/270` и автоматическое скрытие 3D-корпуса в полноэкранном режиме.
- **100% локализация**: 10 полных локалей на Android (**367 строк x 10 языков = 3 670 записей**) и 11 полных локалей на Windows.

---

## Ключевые особенности и архитектура

### 1. Объединенное нативное ядро: лучшее из лучших эмуляторов GB / GBC / GBA
- **Основное ядро - mGBA (`master`, 2026)**: полная совместимость с **GBA, GBC, GB и Super Game Boy**, встроенный HLE BIOS GBA, поддержка архивов `.zip` и `.7z` (включая полную поддержку кириллических/Unicode путей на Windows), автоопределение типов сохранений (`SRAM`, `Flash 64K/128K`, `EEPROM`), `RTC`, `Solar Sensor`, `Gyro`, `Tilt`, `Rumble`, **Dolphin JoyBus (`GBASIODolphin`)** и **Game Boy Printer (`GBPrinter`)**.
- **Дополнительное высокоточное ядро - SameBoy (`v1.0.5`)**: независимый выбор ядра отдельно для **GB** и **GBC**, циклическая точность SM83, 7 режимов аппаратной цветокоррекции LCD, 3 режима фильтра верхних частот (`Off` / `Accurate` / `Remove DC`) и переносимость сохранений.
- **Vulkan 1.1 + OpenGL ES 3.2 + RetroArch `librashader`**: полноценное аппаратное ускорение и многопроходные шейдеры `.slangp` в дополнение к встроенному постпроцессору `storm_gbca_lcd.glsl` (по мотивам **SkyEmu**).
- **Улучшения звука и цвета из NanoBoyAdvance (`phoenix`, BSD-2-Clause)**: качественная кубическая/Эрмитова (Hermite) интерполяция звука 48 кГц для GBA DirectSound/MP2K и калиброванные цветовые матрицы экранов AGB-001 / AGS-101.
- **Низкая задержка и плавность из ares (`v148`)**: динамический контроль частоты (**Dynamic Rate Control, DRC**) для идеального звука без щелчков при синхронизации `59.7275 Гц -> 60 Гц` и упреждение кадров (**Run-Ahead `0..2` кадра**).
- **Палитры, микшер, читы и датчики из Gambatte, MesenCE, VBA-M и GBE+**:
  - **28 кураторских палитр DMG/GBC/SGB** + настраиваемая 4-цветная палитра.
  - **6-канальный аудиомикшер** (`PSG 1 Pulse`, `PSG 2 Pulse`, `PSG 3 Wave`, `PSG 4 Noise`, `GBA DMA A`, `GBA DMA B`, общая громкость).
  - **Универсальный движок чит-кодов + живой RAM Cheat Finder**: GameShark v1/v3, Action Replay v3, CodeBreaker / GameShark SP, Game Genie (GB/GBC), Raw-коды и интерактивный поиск значений в памяти.
  - **Мягкое патчирование на лету (Soft-Patching)**: встроенная поддержка форматов **IPS, IPS32, UPS и BPS**.
  - **Аппаратные датчики и аксессуары**: датчик солнечного света Boktai (`0..10 LUX`), гироскоп и акселерометр, тактильная отдача `Rumble`, часы реального времени (`RTC`), **Game Boy Printer** и рамки **Super Game Boy (`256x224`)**.

### 2. Независимые 3D-скины, экраны, оверлеи и шейдеры отдельно для GB, GBC и GBA
- **40 фотореалистичных процедурных 3D-корпусов + режим «Без корпуса (Полный экран)»** (единый каталог на Android и Windows: 12 GB + 12 GBC + 16 GBA).
- **Независимые профили экрана, оверлея и RetroArch-шейдера для GB, GBC и GBA**: для каждой консоли отдельно сохраняются пресет экрана (`Внутри 3D-корпуса` / `Максимум` / `Во весь экран 100%` / `Пиксель-в-пиксель` / `Свободная`), размеры/позиция/поворот экрана (раздельно в портретном и альбомном режиме), раскладка кнопок, раздельные кнопки `A`/`B` и их дистанция, стиль кнопок, шейдер RetroArch `.slangp`, прозрачность, масштаб, виброотклик и триггеры `L`/`R`.

---

## Релиз 1.3.0

| Файл релиза | Назначение | Ключевые возможности |
| --- | --- | --- |
| `STORM_GBCA_1.3.0.apk` | Подписанная универсальная сборка для Android 8.0+ (`arm64-v8a`, `armeabi-v7a`, `x86_64`) | Компактный хидер STORM DS (`48 dp`, без RA-иконки), двухрядное меню (`Все/GB/GBC/GBA` + `Избранное/Файл/Обновить`), надежные обложки (3 быстрых CDN + fallback для `[MOD]` ROM) и проверенные бейджи RA/оборудования, **40 аутентичных 3D-скинов**, раздельные неперекрывающиеся кнопки `A` и `B`, 1-клик пресеты экрана (`Во весь экран 100%` и др.), `mGBA` + `SameBoy` + `Vulkan 1.1` + `librashader` + LAN/RFU/IR/JoyBus/GB Printer и 100% локализация (10 языков, 367 строк) |
| `STORM_GBCA_1.3.0_Windows.zip` | Портативная сборка для Windows 10/11 (x64) | Автономный `STORM GBCA.exe` + нативное ядро `storm_gbca_core.dll` (1.3.0), встроенный индекс 9 132 обложек с 3 быстрыми CDN, **40 фотореалистичных 3D-скинов** на SkiaSharp, 1-клик пресеты полноэкранного режима, раздельные кнопки `A` и `B` с регулировкой дистанции, 10 пресетов `.slangp`, апскейлинг `1x..4x`, полный сетевой/локальный мультиплеер, 0% серого текста во всех темах и 11 языков |
| `STORM_GBCA_1.3.0_Setup.exe` | Установщик для Windows 10/11 (x64) | Единый графический установщик STORM AuroraFluid с распаковкой в `%LOCALAPPDATA%\Programs\STORM GBCA`, созданием ярлыков на рабочем столе и в меню Пуск, регистрацией в «Установке и удалении программ» Windows и скриптом чистого удаления |

---

## Инструкция по использованию и горячие клавиши

### Быстрый старт (Android и Windows)
1. Запустите **STORM GBCA** и добавьте папку с ROM в **«Настройки -> Библиотека, обложки и бейджи -> Папки с ROM»** (или откройте отдельный файл кнопкой **«Файл»**; на Windows также поддерживается перетаскивание файлов `.gb`, `.gbc`, `.gba`, `.sgb`, `.zip`, `.7z` прямо в окно программы).
2. При желании включите отображение **обложек Libretro / RetroAchievements** и нужных **бейджей** в разделе **«Настройки -> Библиотека, обложки и бейджи»** (по умолчанию все отключено для максимальной скорости и чистоты интерфейса, как в **STORM DS**).
3. Фильтруйте библиотеку по плашкам **Все / GB / GBC / GBA** и нижнему ряду **Избранное / Файл / Обновить**.
4. Во время игры откройте меню паузы -> **«3D-корпус консоли»** (40 моделей) или **«Экран, оверлей и раскладка»** для мгновенного переключения на **Во весь экран (100%)**, настройки раздельных кнопок **A** и **B** и их расстояния.
5. Для сетевой игры по Wi-Fi / LAN, подключения **GBA Wireless Adapter (RFU)**, **GBC IR**, **Dolphin GameCube JoyBus** или **Game Boy Printer** откройте **«Настройки -> Сетевая и локальная игра»** или вкладку **«Сеть / Link»** прямо в меню паузы.
6. Для входа в **RetroAchievements**, проверки соединения и просмотра таблиц лидеров откройте **«Настройки -> RetroAchievements»**.

### Горячие клавиши (Windows)
- **Управление**: Стрелки / `WASD` (D-Pad), `Z` / `J` (B), `X` / `K` (A), `A` / `Q` (L), `S` / `E` (R), `Enter` (Start), `Backspace` (Select) + полная поддержка геймпадов **XInput** с виброотдачей.
- **`Esc`** - Открыть / закрыть меню паузы (Сохранения, Корпус/Оверлей, Графика и мощь, Аудиомикшер, Сеть / Link, Сенсоры и ядро, Поиск в RAM)
- **`F5` / `F8`** - Быстрое сохранение / Быстрая загрузка
- **`Space`** - Ускорение эмуляции (Fast-Forward)
- **`R`** - Перемотка времени назад (Rewind)
- **`F11`** - Полноэкранный режим
- **`F12`** - Сохранить скриншот в PNG (`%APPDATA%\STORM_GBCA\screenshots`)

---

## Сборка из исходного кода

### Требования (Android)
- **JDK**: 21
- **Android SDK**: Platform 36 (`compileSdk = 36`, `minSdk = 26`)
- **Android NDK**: `28.0.13004108`
- **CMake**: `3.22.1+` + **Ninja**

### Требования (Windows x64)
- **Visual Studio 2022 / Insiders (MSVC)** + **CMake** + **Ninja** + **Windows SDK 10.0** (для нативной библиотеки `storm_gbca_core.dll`)
- **.NET 10 SDK** (`10.0+`) (для интерфейса Avalonia UI и установщика)

### Команды сборки
```bash
git clone https://github.com/ReiKatari/STORM_GBCA.git
cd STORM_GBCA

# 1. Нативное ядро (Windows x64, низконагруженная сборка)
cmake -S core -B core/build_win -G Ninja -DCMAKE_BUILD_TYPE=Release
cmake --build core/build_win --parallel 4
./core/build_win/core_smoke_test.exe

# 2. Клиент Windows x64 (.NET 10 + Avalonia)
dotnet publish windows/src/StormGbca.Desktop/StormGbca.Desktop.csproj -c Release -r win-x64 --self-contained true -m:4

# 3. Релизная сборка Android APK (низконагруженная сборка)
./gradlew :app:assembleRelease --max-workers=3 -Dorg.gradle.priority=low
```
Готовые артефакты релиза сохраняются в папку `releases/`:
- `releases/STORM_GBCA_1.3.0.apk`
- `releases/STORM_GBCA_1.3.0_Windows.zip`
- `releases/STORM_GBCA_1.3.0_Setup.exe`

---

## Благодарности и открытые проекты (Credits)

Проект **STORM GBCA** создан благодаря выдающейся работе авторов и сообществ следующих открытых проектов:

- **[mGBA](https://github.com/mgba-emu/mgba)** (**endrift** и участники проекта) - основное высокоскоростное и точное ядро эмуляции Nintendo Game Boy, Game Boy Color, Super Game Boy и Game Boy Advance, поддержка Dolphin JoyBus и Game Boy Printer (MPL-2.0).
- **[SameBoy](https://github.com/LIJI32/SameBoy)** (**Lior Halphon / LIJI32**) - дополнительное высокоточное ядро для GB/GBC/SGB, эталонные 7 режимов цветокоррекции LCD и 3-режимный фильтр верхних частот аудио (MIT).
- **[librashader](https://github.com/SnowflakePowered/librashader)** (**Ronny Chan / SnowflakePowered**) - высокопроизводительный кросс-API движок шейдеров RetroArch `.slangp` для OpenGL ES 3 и Vulkan (MPL-2.0).
- **[SkyEmu](https://github.com/skylersaleh/SkyEmu)** (**Skyler Saleh**) - архитектура единого LCD/субпиксельного шейдера с гостингом и концепция 64-байтного XOR дельта-сжатия буфера перемотки назад (MIT).
- **[NanoBoyAdvance (`phoenix`)](https://codeberg.org/nba-emu/NanoBoyAdvance)** (**fleroviux**) - референс высококачественной интерполяции GBA-аудио (MP2K / DirectSound) и цветовых матриц LCD AGB-001 / AGS-101 (BSD-2-Clause).
- **[ares](https://github.com/ares-emulator/ares)** (**Near** и команда ares) - идеи динамического контроля частоты аудио (Dynamic Rate Control) и упреждения кадров Run-Ahead (ISC).
- **[Gambatte-libretro](https://github.com/libretro/gambatte-libretro)** (**sinamas** и команда libretro) - вдохновение и коллекция классических цветовых палитр DMG / GBC / SGB / TWB64.
- **[VisualBoyAdvance-M](https://github.com/visualboyadvance-m/visualboyadvance-m)**, **[MesenCE](https://github.com/nesdev-org/MesenCE)** (**Sour**) и **[GBE+](https://github.com/shonumi/gbe-plus)** (**Shonumi**) - вдохновение для универсального парсера чит-кодов, поканального 6-канального аудиомикшера, RAM Cheat Finder и работы с периферией и датчиками.
- **[RetroAchievements / rcheevos](https://github.com/RetroAchievements/rcheevos)** (**Jamiras** и сообщество RetroAchievements) - официальная C-библиотека хеширования ROM и достижений (MIT).
- **[Google Oboe](https://github.com/google/oboe)** - библиотека низколатентного вывода аудио AAudio / OpenSL ES для Android (Apache-2.0).
- **[WatermelonDS](https://github.com/SapphireRhodonite/WatermelonDS)** (**SapphireRhodonite**), **[STORM 3DS](https://github.com/ReiKatari/STORM_3DS)** и **[STORM DS](https://github.com/ReiKatari/STORM_DS)** (**STORM TEAM**) - фирменная визуальная дизайн-система STORM Watermelon / AuroraFluid, структура настроек и меню паузы, процедурные 3D-скины и двойное зеркало обновлений.
- **[Avalonia UI](https://avaloniaui.net/)**, **[SkiaSharp](https://github.com/mono/SkiaSharp)**, **Jetpack Compose**, **Coil** и **[Libretro Thumbnails](https://github.com/libretro-thumbnails)** - UI-фреймворки, процедурный 2D/3D-рендеринг и официальная база обложек игр.

---

## Лицензия

Исходный код **STORM GBCA** распространяется под лицензией **Mozilla Public License 2.0 (MPL-2.0)** ([LICENSE](LICENSE)) с полным соблюдением лицензий всех интегрированных библиотек с открытым исходным кодом (mGBA MPL-2.0, librashader MPL-2.0, SameBoy MIT, SkyEmu MIT, NanoBoyAdvance phoenix BSD-2-Clause, ares ISC, rcheevos MIT, Google Oboe Apache-2.0).
