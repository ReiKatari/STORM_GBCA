<p align="center">
  <img src="logo.png" width="220" alt="STORM GBCA Logo" />
</p>

<h1 align="center">STORM GBCA</h1>

<p align="center">
  <strong>Флагманский кроссплатформенный эмулятор Nintendo Game Boy, Game Boy Color и Game Boy Advance для Android и Windows x64 (mGBA + SameBoy + Vulkan 1.1 + RetroArch librashader + интерфейс STORM Watermelon / AuroraFluid)</strong><br />
  Версия <code>1.1.0</code> | Платформы: <code>Android (arm64-v8a, armeabi-v7a, x86_64)</code> и <code>Windows x64</code> | Лицензия: <code>MPL-2.0</code>
</p>

<p align="center">
  <a href="https://github.com/ReiKatari/STORM_GBCA">GitHub Repository</a> -
  <a href="https://github.com/ReiKatari/STORM_GBCA/releases/tag/1.1.0">Релиз STORM GBCA 1.1.0</a> -
  <a href="https://stormgamesworld.ru/programs?repo=STORM_GBCA">Страница на STORM GAMES WORLD</a>
</p>

---

## О проекте

**STORM GBCA** - это современный гибридный эмулятор семейства портативных консолей **Nintendo Game Boy (DMG / Pocket / Light / Super Game Boy)**, **Nintendo Game Boy Color (GBC)** и **Nintendo Game Boy Advance (GBA / GBA SP / Game Boy Micro)** для **Android 8.0+ (API 26+)** и **Windows 10/11 (x64)**.

Проект выполнен в фирменном визуальном стиле экосистемы **STORM** (единая дизайн-система **Watermelon** на Android / **AuroraFluid** на Windows, как в **STORM 3DS** и **STORM DS**), открывается сразу в истинно полноэкранном режиме без системных панелей и без заставки при запуске, полностью очищен от любых элементов Nintendo DS / 3DS и объединяет мощь ядер **mGBA** и **SameBoy** с поддержкой **Vulkan 1.1**, **шейдеров RetroArch (`.slangp` via `librashader`)**, внутреннего **апскейлинга `1x..4x`** и **живого сканера читов в RAM**.

---

## Что нового в версии 1.1.0

- **Истинно полноэкранный режим на Android (Zero System Bars)**: полностью скрываются верхняя строка состояния и нижняя навигационная панель во всех экранах (`MainActivity`, `SettingsActivity`, `EmulatorActivity`, `LayoutEditorActivity`) с поддержкой вырезов экрана (`LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`).
- **Мгновенный запуск без анимации заставки**: убрана стартовая анимация при открытии приложения (как в **STORM 3DS**) - эмулятор сразу открывается на библиотеке ROM.
- **Каноничные английские названия тем**: единые фирменные названия во всех сборках (`STORM NIGHT`, `STORM DAY`, `STORM MIDNIGHT`, `STORM CYBERPUNK`, `STORM GOTHIC`, `STORM MATRIX`, `STORM SOLAR`, `STORM SAKURA`, `CLASSIC OLED` на Android; `STORM MIDNIGHT`, `STORM NIGHT`, `STORM DARK`, `STORM DAY`, `STORM FANTASY`, `STORM CYBERPUNK`, `STORM WARHAMMER 40K`, `STORM ELEMENTAL`, `STORM AURORA` на Windows).
- **Обновленный центр RetroAchievements (RA Hub)**: просторное окно входа с переключателем видимости пароля, аватар профиля, статус подключения, раздельные очки **Hardcore / Softcore**, глобальный рейтинг, переключатели `Hardcore` / `Encore` / `Unofficial` / `Notifications`, а также детальный просмотр достижений для каждой игры (прогресс-бар, заработанные очки, фильтры Все / Полученные / Неполученные и настоящие иконки трофеев).
- **Надежная автоматическая загрузка обложек (Libretro Thumbnails Index)**: полная индексация каталогов `Named_Boxarts` для **GB / GBC / GBA** с 30-дневным локальным кэшем, нормализацией названий, нечетким сопоставлением по токенам (Jaccard + containment) и 4-поточной фоновой загрузкой.
- **Полноценная настройка размера, расположения и поворота экрана отдельно для GB, GBC и GBA**: интерактивный перетаскиваемый и масштабируемый экран в редакторе раскладок на Android (раздельно для Портрета и Альбома) и точные ползунки ширины/высоты/позиции X-Y/поворота (`0`/`90`/`180`/`270`) в студии оформления на Windows.
- **Полная поддержка шейдеров RetroArch (`.slangp` / `.slang`) через `librashader`**: встроенная нативная библиотека `liblibrashader.so` (`arm64-v8a`, `armeabi-v7a`, `x86_64`), 10 предустановленных пресетов (`LCD GBA Grid`, `LCD DMG Olive`, `LCD 3x Sharp`, `GBA Color AGS-101`, `GBC Color Accurate`, `xBRZ Smooth HD`, `Sharp Bilinear`, `CRT Scanlines`, `Vibrant HD`, `Game Boy Pocket`), независимый выбор пресета отдельно для GB, GBC и GBA + импорт любых пользовательских `.slangp`/`.slang`.
- **Графический бэкенд Vulkan 1.1 + кастомные драйверы Turnip (`.so`)**: нативный рендерер Vulkan 1.1 (`ANativeWindow` swapchain) с отображением имени GPU, поддержкой пользовательских драйверов Adreno Turnip и автоматическим откатом на OpenGL ES 3.2 при отсутствии Vulkan.
- **Внутреннее увеличение разрешения (`1x..4x`) и 6 алгоритмов апскейлинга**: `Nearest`, `xBRZ HD`, `Scale2x / EPX`, `MMPX Sharp`, `LCD Subpixel HD` и `CRT Scanlines HD` прямо в нативном C++ ядре на обеих платформах (Android и Windows).
- **Живой поиск читов в памяти (RAM Cheat Finder), разгон CPU (`50%..400%`) и Frame Skip (`0..4`)**: сканирование `IWRAM`/`EWRAM`/`WRAM`/`HRAM`/`SRAM` (8/16/32 бита, `=`, `>`, `<`, `Увеличилось`, `Уменьшилось`, `Изменилось`, `Не менялось`) с прямой записью в память и мгновенным сохранением адреса в список чит-кодов.

---

## Ключевые особенности и архитектура

### 1. Объединенное нативное ядро: лучшее из лучших эмуляторов GB / GBC / GBA
- **Основное ядро - mGBA (`master`, 2026)**: полная совместимость с **GBA, GBC, GB и Super Game Boy**, встроенный HLE BIOS GBA, поддержка архивов `.zip` и `.7z` (включая полную поддержку кириллических/Unicode путей на Windows), автоопределение типов сохранений (`SRAM`, `Flash 64K/128K`, `EEPROM`), `RTC`, `Solar Sensor`, `Gyro`, `Tilt` и `Rumble`.
- **Дополнительное высокоточное ядро - SameBoy (`v1.0.5`)**: независимый выбор ядра отдельно для **GB** и **GBC** (переключение прямо в настройках или меню паузы), циклическая точность SM83, 7 режимов аппаратной цветокоррекции LCD, 3 режима фильтра верхних частот (`Off` / `Accurate` / `Remove DC`), встроенная поддержка быстрой загрузки без внешних BootROM и переносимость сохранений.
- **Vulkan 1.1 + OpenGL ES 3.2 + RetroArch `librashader`**: полноценное аппаратное ускорение и многопроходные шейдеры `.slangp` в дополнение к встроенному постпроцессору `storm_gbca_lcd.glsl` (по мотивам **SkyEmu**).
- **Улучшения звука и цвета из NanoBoyAdvance (`phoenix`, BSD-2-Clause)**: качественная кубическая/Эрмитова (Hermite) интерполяция звука 48 кГц для GBA DirectSound/MP2K и калиброванные цветовые матрицы экранов AGB-001 / AGS-101.
- **Низкая задержка и плавность из ares (`v148`)**: динамический контроль частоты (**Dynamic Rate Control, DRC**) для идеального звука без щелчков при синхронизации `59.7275 Гц -> 60 Гц` и упреждение кадров (**Run-Ahead `0..2` кадра**) с детерминированным снимком буфера кадра.
- **Палитры, микшер, читы и датчики из Gambatte, MesenCE, VBA-M и GBE+**:
  - **28 кураторских палитр DMG/GBC/SGB** + настраиваемая 4-цветная палитра.
  - **6-канальный аудиомикшер** (`PSG 1 Pulse`, `PSG 2 Pulse`, `PSG 3 Wave`, `PSG 4 Noise`, `GBA DMA A`, `GBA DMA B`, общая громкость) с реальным управлением каналами в обоих ядрах.
  - **Универсальный движок чит-кодов + живой RAM Cheat Finder**: GameShark v1/v3, Action Replay v3, CodeBreaker / GameShark SP, Game Genie (GB/GBC), Raw-коды и интерактивный поиск значений в памяти.
  - **Мягкое патчирование на лету (Soft-Patching)**: встроенная поддержка форматов **IPS, IPS32, UPS и BPS**.
  - **Аппаратные датчики и аксессуары**: датчик солнечного света Boktai (`0..10 LUX`), гироскоп и акселерометр, тактильная отдача `Rumble` (вибромотор Android и XInput на Windows), часы реального времени (`RTC`) и рамки **Super Game Boy (`256x224`)**.

### 2. Независимые 3D-скины, экраны, оверлеи и шейдеры отдельно для GB, GBC и GBA
- **26 процедурных 3D-корпусов + режим «Без корпуса (Полный экран)»** (единый каталог на Android и Windows):
  - **Nintendo GB (8 скинов)**: `DMG-01 Классический серый`, `DMG Play It Loud Черный`, `DMG Прозрачный`, `Game Boy Pocket Серебро`, `Game Boy Pocket Ледяной`, `Game Boy Light Золото`, `Super Game Boy Рамка`, `STORM Aurora GB`.
  - **Nintendo GBC (8 скинов)**: `Атомный фиолет (Прозрачный)`, `Ягодный маджента`, `Бирюзовый Teal`, `Киви Лайм`, `Одуванчик Желтый`, `Виноградный фиолет`, `Издание Пикачу`, `STORM Aurora GBC`.
  - **Nintendo GBA (10 скинов)**: `AGB-001 Индиго`, `AGB-001 Ледник (Полупрозрачный)`, `Арктический белый`, `Пряный оранжевый`, `Фуксия Прозрачная`, `GBA SP Кобальтовый синий`, `GBA SP Черный оникс`, `GBA SP NES Классик`, `Game Boy Micro Famicom 20th`, `STORM Aurora GBA`.
- **Независимые профили экрана, оверлея и RetroArch-шейдера для GB, GBC и GBA**: для каждой консоли отдельно сохраняются размеры/позиция/поворот экрана (раздельно в портретном и альбомном режиме), раскладка кнопок, стиль кнопок, шейдер RetroArch `.slangp`, прозрачность, масштаб, виброотклик и триггеры `L`/`R`.

---

## Релиз 1.1.0

| Файл релиза | Назначение | Ключевые возможности |
| --- | --- | --- |
| `STORM_GBCA_1.1.0.apk` | Подписанная универсальная сборка для Android 8.0+ (`arm64-v8a`, `armeabi-v7a`, `x86_64`) | Истинный fullscreen без системных панелей, нативные ядра `libstorm_gbca_core.so` (`mGBA` + `SameBoy` + `Oboe` + **Vulkan 1.1** + **OpenGL ES 3.2**) и `liblibrashader.so` (10 пресетов RetroArch `.slangp` + импорт своих), апскейлинг `1x..4x` (`xBRZ`/`EPX`/`MMPX`/`LCD HD`/`CRT HD`), кастомные драйверы Turnip, редактор экрана и кнопок GB/GBC/GBA, новый центр RetroAchievements, автозагрузка обложек Libretro и живой RAM Cheat Finder |
| `STORM_GBCA_1.1.0_Windows.zip` | Портативная сборка для Windows 10/11 (x64) | Автономный `STORM GBCA.exe` + нативное ядро `storm_gbca_core.dll` (1.1.0), 10 пресетов `.slangp`, апскейлинг `1x..4x` (`xBRZ`/`EPX`/`MMPX`/`LCD HD`/`CRT HD`), разгон CPU `50..400%`, Frame Skip, живой RAM Cheat Finder в меню паузы, настройка геометрии экрана отдельно для GB/GBC/GBA, автозагрузка обложек Libretro, обновленный центр RetroAchievements и 26 3D-скинов на SkiaSharp |
| `STORM_GBCA_1.1.0_Setup.exe` | Установщик для Windows 10/11 (x64) | Единый графический установщик STORM AuroraFluid с распаковкой в `%LOCALAPPDATA%\Programs\STORM GBCA`, созданием ярлыков на рабочем столе и в меню Пуск, регистрацией в «Установке и удалении программ» Windows и скриптом чистого удаления |

---

## Инструкция по использованию и горячие клавиши

### Быстрый старт (Android и Windows)
1. Запустите **STORM GBCA** и нажмите **«Добавить папку ROM»** (на Windows также поддерживается перетаскивание файлов `.gb`, `.gbc`, `.gba`, `.sgb`, `.zip`, `.7z` прямо в окно программы). Обложки игр загрузятся автоматически через кэшируемый индекс **Libretro Thumbnails**.
2. Фильтруйте библиотеку по плашкам **Все / GB / GBC / GBA / Избранное**.
3. В разделе **«Корпуса и оверлеи»** / **«Настройки»** (или прямо во время игры через меню паузы) выберите вкладку **`[GB]`**, **`[GBC]`** или **`[GBA]`**, чтобы независимо настроить **3D-скин**, **размер и положение экрана**, **раскладку кнопок**, **шейдер RetroArch `.slangp`** и **увеличение разрешения `1x..4x`**.
4. Для переключения на **Vulkan 1.1** (или подключения драйвера Turnip `.so` на Android) откройте **«Настройки -> Графика, Vulkan 1.1 и апскейлинг»**.
5. Для входа в **RetroAchievements** нажмите иконку кубка в шапке библиотеки или перейдите в **«Настройки -> RetroAchievements»**.

### Горячие клавиши (Windows)
- **Управление**: Стрелки / `WASD` (D-Pad), `Z` / `J` (B), `X` / `K` (A), `A` / `Q` (L), `S` / `E` (R), `Enter` (Start), `Backspace` (Select) + полная поддержка геймпадов **XInput** с виброотдачей.
- **`Esc`** - Открыть / закрыть меню паузы (Сохранения, Корпус/Оверлей, Графика и мощь, Аудиомикшер, Сенсоры и ядро, Поиск в RAM)
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
- `releases/STORM_GBCA_1.1.0.apk`
- `releases/STORM_GBCA_1.1.0_Windows.zip`
- `releases/STORM_GBCA_1.1.0_Setup.exe`

---

## Благодарности и открытые проекты (Credits)

Проект **STORM GBCA** создан благодаря выдающейся работе авторов и сообществ следующих открытых проектов:

- **[mGBA](https://github.com/mgba-emu/mgba)** (**endrift** и участники проекта) - основное высокоскоростное и точное ядро эмуляции Nintendo Game Boy, Game Boy Color, Super Game Boy и Game Boy Advance (MPL-2.0).
- **[SameBoy](https://github.com/LIJI32/SameBoy)** (**Lior Halphon / LIJI32**) - дополнительное высокоточное ядро для GB/GBC/SGB, эталонные 7 режимов цветокоррекции LCD и 3-режимный фильтр верхних частот аудио (MIT).
- **[librashader](https://github.com/SnowflakePowered/librashader)** (**Ronny Chan / SnowflakePowered**) - высокопроизводительный кросс-API движок шейдеров RetroArch `.slangp` для OpenGL ES 3 и Vulkan (MPL-2.0).
- **[SkyEmu](https://github.com/skylersaleh/SkyEmu)** (**Skyler Saleh**) - архитектура единого LCD/субпиксельного шейдера с гостингом и концепция 64-байтного XOR дельта-сжатия буфера перемотки назад (MIT).
- **[NanoBoyAdvance (`phoenix`)](https://codeberg.org/nba-emu/NanoBoyAdvance)** (**fleroviux**) - референс высококачественной интерполяции GBA-аудио (MP2K / DirectSound) и цветовых матриц LCD AGB-001 / AGS-101 (BSD-2-Clause).
- **[ares](https://github.com/ares-emulator/ares)** (**Near** и команда ares) - идеи динамического контроля частоты аудио (Dynamic Rate Control) и упреждения кадров Run-Ahead (ISC).
- **[Gambatte-libretro](https://github.com/libretro/gambatte-libretro)** (**sinamas** и команда libretro) - вдохновение и коллекция классических цветовых палитр DMG / GBC / SGB / TWB64.
- **[VisualBoyAdvance-M](https://github.com/visualboyadvance-m/visualboyadvance-m)**, **[MesenCE](https://github.com/nesdev-org/MesenCE)** (**Sour**) и **[GBE+](https://github.com/shonumi/gbe-plus)** (**Shonumi**) - вдохновение для универсального парсера чит-кодов, поканального 6-канального аудиомикшера, RAM Cheat Finder и удобства работы с аппаратными датчиками (Solar / Tilt / Gyro / Rumble).
- **[RetroAchievements / rcheevos](https://github.com/RetroAchievements/rcheevos)** (**Jamiras** и сообщество RetroAchievements) - официальная C-библиотека хеширования ROM и достижений (MIT).
- **[Google Oboe](https://github.com/google/oboe)** - библиотека низколатентного вывода аудио AAudio / OpenSL ES для Android (Apache-2.0).
- **[WatermelonDS](https://github.com/SapphireRhodonite/WatermelonDS)** (**SapphireRhodonite**), **[STORM 3DS](https://github.com/ReiKatari/STORM_3DS)** и **[STORM DS](https://github.com/ReiKatari/STORM_DS)** (**STORM TEAM**) - фирменная визуальная дизайн-система STORM Watermelon / AuroraFluid, концепция процедурных 3D-скинов и двойное зеркало обновлений.
- **[Avalonia UI](https://avaloniaui.net/)**, **[SkiaSharp](https://github.com/mono/SkiaSharp)**, **Jetpack Compose**, **Coil** и **[Libretro Thumbnails](https://github.com/libretro-thumbnails)** - UI-фреймворки, процедурный 2D/3D-рендеринг и база обложек игр.

---

## Лицензия

Исходный код **STORM GBCA** распространяется под лицензией **Mozilla Public License 2.0 (MPL-2.0)** ([LICENSE](LICENSE)) с полным соблюдением лицензий всех интегрированных библиотек с открытым исходным кодом (mGBA MPL-2.0, librashader MPL-2.0, SameBoy MIT, SkyEmu MIT, NanoBoyAdvance phoenix BSD-2-Clause, ares ISC, rcheevos MIT, Google Oboe Apache-2.0).
