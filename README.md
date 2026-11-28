<p align="center">
  <img src="logo.png" width="220" alt="STORM GBCA Logo" />
</p>

<h1 align="center">STORM GBCA</h1>

<p align="center">
  <strong>Флагманский кроссплатформенный эмулятор Nintendo Game Boy, Game Boy Color и Game Boy Advance для Android и Windows x64 (mGBA + SameBoy + Vulkan 1.1 + RetroArch librashader + 40 аутентичных 3D-скинов + Link Cable / Wireless RFU / Dolphin JoyBus / GB Printer + интерфейс STORM DS Watermelon / AuroraFluid)</strong><br />
  Версия <code>1.7.1</code> | Платформы: <code>Android (arm64-v8a, armeabi-v7a, x86_64)</code> и <code>Windows x64</code>
</p>

<p align="center">
  <a href="https://github.com/ReiKatari/STORM_GBCA">GitHub Repository</a> -
  <a href="https://github.com/ReiKatari/STORM_GBCA/releases/tag/1.7.1">Релиз STORM GBCA 1.7.1</a> -
  <a href="https://stormgamesworld.ru/programs?repo=STORM_GBCA">Страница на STORM GAMES WORLD</a> -
  <a href="PRIVACY_POLICY.md">Политика конфиденциальности (Privacy Policy)</a> -
  <a href="THIRD_PARTY_LICENSES.md">Лицензии и компоненты (Third-Party Licenses)</a>
</p>

---

## О проекте

**STORM GBCA** - это современный 100% бесплатный эмулятор с открытым исходным кодом (MPL-2.0, без рекламы, без платных подписок и без телеметрии) для семейства портативных консолей **Nintendo Game Boy (DMG / Pocket / Light / Super Game Boy)**, **Nintendo Game Boy Color (GBC)** и **Nintendo Game Boy Advance (GBA / GBA SP / Game Boy Micro)** для **Android 8.0+ (API 26+)** и **Windows 10/11 (x64)**.

Проект выполнен в фирменном визуальном стиле экосистемы **STORM** (единая дизайн-система **Watermelon** на Android и **AuroraFluid** на Windows, полностью повторяющая эргономику **STORM DS** и **STORM 3DS**), открывается мгновенно в истинно полноэкранном режиме без системных панелей и стартовой заставки, полностью очищен от элементов Nintendo DS / 3DS и объединяет мощь ядер **mGBA** и **SameBoy** с поддержкой **Vulkan 1.1**, **шейдеров RetroArch (`.slangp` via `librashader`)**, внутреннего **апскейлинга `1x..4x`**, **40 аутентичных 3D-корпусов консолей**, **раздельных кнопок A/B и быстрых действий с полной настройкой расположения**, **полного сетевого и локального мультиплеера (Link Cable / Wireless RFU / IR / Dolphin JoyBus / GB Printer)** и **живого сканера читов в RAM**.

---

## Что нового в версии 1.7.1

- **Полное усиление защиты RetroAchievements Hardcore (Red-Team Hardening)**:
  - Предзагрузочный мягкий патч (`.ips` / `.ups` / `.bps`) применяется строго до блокировки Hardcore, а хэш сессии вычисляется непосредственно из загруженного/патченного буфера в памяти (`storm_gbca_get_loaded_ra_hash`) для корректной работы поддерживаемых RA переводов и хаков; патчирование на лету во время сессии в Hardcore по-прежнему заблокировано.
  - Переключение Casual -> Hardcore на лету выполняет полную чистую перезагрузку ядра (очищая даже патчи-читы в ROM), блокирует отключение системного RTC и внешние Link Cable / Wireless / IR / Dolphin JoyBus в Hardcore (разрешён только GB Printer), а неофициальные достижения (`Flags == 5`) никогда не активируются и не отправляются с `h=1`.
- **Защита токена, единый 3-сегментный User-Agent (`mgba/0.11.0`) и точность протокола Connect API**:
  - Поиск обложек RA на Windows переведён на HTTPS POST с единым 3-сегментным User-Agent (`STORM-GBCA/1.7.1 (...) mgba/0.11.0` / `sameboy/1.0.5`), проверка ответов API валидирует JSON `"Success": true`, режим Encore не дублирует уже полученные награды на сервер, а офлайн-очередь и `r=ping` полностью выровнены со спецификацией `rcheevos 12.5.0`.
- **Синхронизация настроек и юридическая прозрачность (MPL-2.0 / GDPR)**:
  - Заблокированы переключатели Rich Presence, Leaderboards и Unofficial в Hardcore на Windows и синхронизирован `SettingsActivity` на Android; все C#-файлы переведены на единые заголовки **MPL-2.0**, а `PRIVACY_POLICY.md` и `THIRD_PARTY_LICENSES.md` приведены к 100% точности по сетевым доменам, `zlib 1.3.2`, `LZMA SDK` и `librashader`.

---

## Ключевые особенности и архитектура

### 1. Объединенное нативное ядро: лучшее из лучших эмуляторов GB / GBC / GBA
- **Основное ядро - mGBA (`master`, 2026)**: полная совместимость с **GBA, GBC, GB и Super Game Boy**, встроенный HLE BIOS GBA, поддержка архивов `.zip` и `.7z` (включая полную поддержку кириллических/Unicode путей на Windows), автоопределение типов сохранений (`SRAM`, `Flash 64K/128K`, `EEPROM`), `RTC`, `Solar Sensor`, `Gyro`, `Tilt`, `Rumble`, **Dolphin JoyBus (`GBASIODolphin`)** и **Game Boy Printer (`GBPrinter`)**.
- **Дополнительное высокоточное ядро - SameBoy (`1.0.5`)**: независимый выбор ядра отдельно для **GB** и **GBC**, циклическая точность SM83, 7 режимов аппаратной цветокоррекции LCD, 3 режима фильтра верхних частот (`Off` / `Accurate` / `Remove DC`) и переносимость сохранений.
- **Vulkan 1.1 + OpenGL ES 3.2 + RetroArch `librashader`**: полноценное аппаратное ускорение и многопроходные шейдеры `.slangp` в дополнение к встроенному постпроцессору `storm_gbca_lcd.glsl` (по мотивам **SkyEmu**).
- **Улучшения звука и цвета из NanoBoyAdvance (`phoenix`)**: качественная кубическая/Эрмитова (Hermite) интерполяция звука 48 кГц для GBA DirectSound/MP2K и калиброванные цветовые матрицы экранов AGB-001 / AGS-101.
- **Низкая задержка и плавность из ares (`148`)**: динамический контроль частоты (**Dynamic Rate Control, DRC**) для идеального звука без щелчков при синхронизации `59.7275 Гц -> 60 Гц` и упреждение кадров (**Run-Ahead `0..2` кадра**).
- **Палитры, микшер, читы и датчики из Gambatte, MesenCE, VBA-M и GBE+**:
  - **28 кураторских палитр DMG/GBC/SGB** + настраиваемая 4-цветная палитра.
  - **6-канальный аудиомикшер** (`PSG 1 Pulse`, `PSG 2 Pulse`, `PSG 3 Wave`, `PSG 4 Noise`, `GBA DMA A`, `GBA DMA B`, общая громкость).
  - **Универсальный движок чит-кодов + живой RAM Cheat Finder**: GameShark 1/3, Action Replay 3, CodeBreaker / GameShark SP, Game Genie (GB/GBC), Raw-коды и интерактивный поиск значений в памяти.
  - **Мягкое патчирование на лету (Soft-Patching)**: встроенная поддержка форматов **IPS, IPS32, UPS и BPS**.
  - **Аппаратные датчики и аксессуары**: датчик солнечного света Boktai (`0..10 LUX`), гироскоп и акселерометр, тактильная отдача `Rumble`, часы реального времени (`RTC`), **Game Boy Printer** и рамки **Super Game Boy (`256x224`)**.

### 2. Независимые 3D-скины, темы, экраны, оверлеи и шейдеры отдельно для GB, GBC и GBA
- **40 фотореалистичных процедурных 3D-корпусов + режим «Без корпуса (Полный экран)»** (единый каталог на Android и Windows: 12 GB + 12 GBC + 16 GBA).
- **Независимые профили темы, экрана, оверлея и RetroArch-шейдера для GB, GBC и GBA**: для каждой консоли отдельно сохраняются тема интерфейса, пресет экрана (`Внутри 3D-корпуса` / `Максимум` / `Во весь экран 100%` / `Пиксель-в-пиксель` / `Свободная`), размеры/позиция/поворот экрана, независимые портретные и альбомные координаты всех кнопок (`D-Pad`, `A/B`, `SELECT`, `START`, `L/R` и 5 отдельных кнопок быстрых действий), стиль кнопок, шейдер RetroArch `.slangp`, прозрачность, масштаб и виброотклик.

---

## Релиз 1.7.1

| Файл релиза | Назначение | Ключевые возможности |
| --- | --- | --- |
| `STORM_GBCA_1.7.1.apk` | Подписанная универсальная сборка для Android 8.0+ (`arm64-v8a`, `armeabi-v7a`, `x86_64`) | Усиленная защита RetroAchievements Hardcore (`rcheevos 12.5.0`), хэш загруженного буфера после предзагрузочного патча, чистая перезагрузка ядра при переключении в Hardcore, блокировка `Flags == 5` и Link/RTC в Hardcore, синхронизация `SettingsActivity` и точные документы MPL-2.0 / GDPR |
| `STORM_GBCA_1.7.1_Windows.zip` | Портативная сборка для Windows 10/11 (x64) | Автономный `STORM GBCA.exe` + нативное ядро `storm_gbca_core.dll` (1.7.1), хэш загруженного буфера, HTTPS POST без токенов в GET, 3-сегментный User-Agent (`mgba/0.11.0`), валидация `"Success": true`, защита Encore и переназначаемые клавиши QuickSave/QuickLoad |
| `STORM_GBCA_1.7.1_Setup.exe` | Установщик для Windows 10/11 (x64) | Эталонный `STORM INSTALLER` (`840x580`, 11 языков, 9 тем, 13 акцентов, Standard/Portable, Drag-and-Drop, подпись `STORM TEAM`), распаковка в `%LOCALAPPDATA%\Programs\STORM GBCA`, создание ярлыков, регистрация в Windows и чистое удаление |

---

## Инструкция по использованию и горячие клавиши

### Быстрый старт (Android и Windows)
1. Запустите **STORM GBCA** и добавьте папку с ROM в **«Настройки -> Библиотека, обложки и бейджи -> Папки с ROM»** (или откройте отдельный файл кнопкой **«Файл»**; на Windows также поддерживается перетаскивание файлов `.gb`, `.gbc`, `.gba`, `.sgb`, `.zip`, `.7z` прямо в окно программы).
2. При желании включите отображение **обложек Libretro / RetroAchievements** и нужных **бейджей** в разделе **«Настройки -> Библиотека, обложки и бейджи»** (по умолчанию все отключено для максимальной скорости и чистоты интерфейса, как в **STORM DS**).
3. Настройте **отдельные 3D-скин, тему, стиль кнопок, шейдер RetroArch и раскладку** для **GB**, **GBC** и **GBA** в **«Настройки -> Управление, экраны и 3D-скины»** или прямо из игрового меню паузы.
4. Для переназначения физического геймпада и клавиатуры откройте **«Настройки -> Управление, экраны и 3D-скины -> Физический геймпад и клавиши»**.
5. Для сетевой игры по Wi-Fi / LAN, подключения **GBA Wireless Adapter (RFU)**, **GBC IR**, **Dolphin GameCube JoyBus** или **Game Boy Printer** откройте **«Настройки -> Сетевая и локальная игра»**.

### Горячие клавиши (Windows)
- **Управление**: Стрелки / `WASD` (D-Pad), `Z` / `J` (B), `X` / `K` (A), `A` / `Q` (L), `S` / `E` (R), `Enter` (Start), `Backspace` (Select) + полная поддержка геймпадов **XInput** с виброотдачей.
- **`Ctrl+K`** - Быстрый поиск и запуск игры
- **`Esc`** - Открыть / закрыть меню паузы
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
- `releases/STORM_GBCA_1.7.1.apk`
- `releases/STORM_GBCA_1.7.1_Windows.zip`
- `releases/STORM_GBCA_1.7.1_Setup.exe`

---

## Благодарности и открытые проекты (Credits)

Проект **STORM GBCA** создан благодаря выдающейся работе авторов и сообществ следующих открытых проектов:

- **[mGBA](https://github.com/mgba-emu/mgba)** (**endrift** и участники проекта) - основное высокоскоростное и точное ядро эмуляции Nintendo Game Boy, Game Boy Color, Super Game Boy и Game Boy Advance, поддержка Dolphin JoyBus и Game Boy Printer.
- **[SameBoy](https://github.com/LIJI32/SameBoy)** (**Lior Halphon / LIJI32**) - дополнительное высокоточное ядро для GB/GBC/SGB, эталонные 7 режимов цветокоррекции LCD и 3-режимный фильтр верхних частот аудио.
- **[librashader](https://github.com/SnowflakePowered/librashader)** (**Ronny Chan / SnowflakePowered**) - высокопроизводительный кросс-API движок шейдеров RetroArch `.slangp` для OpenGL ES 3 и Vulkan.
- **[SkyEmu](https://github.com/skylersaleh/SkyEmu)** (**Skyler Saleh**) - архитектура единого LCD/субпиксельного шейдера с гостингом и концепция 64-байтного XOR дельта-сжатия буфера перемотки назад.
- **[NanoBoyAdvance (`phoenix`)](https://codeberg.org/nba-emu/NanoBoyAdvance)** (**fleroviux**) - референс высококачественной интерполяции GBA-аудио (MP2K / DirectSound) и цветовых матриц LCD AGB-001 / AGS-101.
- **[ares](https://github.com/ares-emulator/ares)** (**Near** и команда ares) - идеи динамического контроля частоты аудио (Dynamic Rate Control) и упреждения кадров Run-Ahead.
- **[Gambatte-libretro](https://github.com/libretro/gambatte-libretro)** (**sinamas** и команда libretro) - вдохновение и коллекция классических цветовых палитр DMG / GBC / SGB / TWB64.
- **[VisualBoyAdvance-M](https://github.com/visualboyadvance-m/visualboyadvance-m)**, **[MesenCE](https://github.com/nesdev-org/MesenCE)** (**Sour**) и **[GBE+](https://github.com/shonumi/gbe-plus)** (**Shonumi**) - вдохновение для универсального парсера чит-кодов, поканального 6-канального аудиомикшера, RAM Cheat Finder и работы с периферией и датчиками.
- **[RetroAchievements / rcheevos](https://github.com/RetroAchievements/rcheevos)** (**Jamiras** и сообщество RetroAchievements) - официальная C-библиотека хеширования ROM, живого исполнения достижений, таблиц лидеров, Rich Presence и 100% соответствия режиму **Hardcore** (проверенные HTTP POST запросы, 3-сегментный User-Agent, блокировка загрузки состояний, перемотки, читов, RAM Finder, разгона, замедления и покадрового шага в Hardcore с сохранением возможности сохранения состояний для отладки, а также офлайн-очередь синхронизации).
- **[Google Oboe](https://github.com/google/oboe)** - библиотека низколатентного вывода аудио AAudio / OpenSL ES для Android.
- **[WatermelonDS](https://github.com/SapphireRhodonite/WatermelonDS)** (**SapphireRhodonite**), **[STORM 3DS](https://github.com/ReiKatari/STORM_3DS)** и **[STORM DS](https://github.com/ReiKatari/STORM_DS)** (**STORM TEAM**) - фирменная визуальная дизайн-система STORM Watermelon / AuroraFluid, 3D-экран настройки клавиш, структура настроек и меню паузы, раздельные кнопки быстрых действий, процедурные 3D-скины и двойное зеркало обновлений.
- **[Avalonia UI](https://avaloniaui.net/)**, **[SkiaSharp](https://github.com/mono/SkiaSharp)**, **Jetpack Compose**, **Coil** и **[Libretro Thumbnails](https://github.com/libretro-thumbnails)** - UI-фреймворки, процедурный 2D/3D-рендеринг и официальная база обложек игр.

---

## Лицензия, конфиденциальность и раскрытие монетизации

- **Монетизация:** STORM GBCA полностью бесплатен и распространяется с открытым исходным кодом (**MPL-2.0**) без платных версий, встроенных покупок, подписок, рекламы и телеметрии.
- **Политика конфиденциальности:** [PRIVACY_POLICY.md](PRIVACY_POLICY.md) (ноль телеметрии, хранение только локальных файлов и токена RA при явном входе, подробная таблица всех необязательных внешних подключений и прав GDPR).
- **Сторонние лицензии:** [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) (полный перечень версий, лицензий и исходных репозиториев mGBA, SameBoy, rcheevos, librashader, Oboe, Avalonia UI, SkiaSharp и библиотек Android).
