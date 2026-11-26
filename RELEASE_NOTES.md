# STORM GBCA 1.1.0

Крупное обновление кроссплатформенного эмулятора **Nintendo Game Boy, Game Boy Color и Game Boy Advance** для **Android** и **Windows x64** с поддержкой **Vulkan 1.1**, полных шейдеров **RetroArch (`.slangp` via `librashader`)**, внутреннего **апскейлинга `1x..4x`**, истинного полноэкранного режима и нового центра **RetroAchievements**.

## Главные исправления и нововведения 1.1.0
- **Истинно полноэкранный режим на Android**: полностью скрываются верхняя и нижняя системные панели во всех экранах приложения (`MainActivity`, `SettingsActivity`, `EmulatorActivity`, `LayoutEditorActivity`) с поддержкой вырезов экрана.
- **Убрана стартовая анимация при запуске**: эмулятор открывается мгновенно сразу в библиотеке ROM (как в **STORM 3DS**).
- **Английские названия всех тем оформления**: `STORM NIGHT`, `STORM DAY`, `STORM MIDNIGHT`, `STORM CYBERPUNK`, `STORM GOTHIC`, `STORM MATRIX`, `STORM SOLAR`, `STORM SAKURA`, `CLASSIC OLED` (Android) и каноничная линейка `STORM ...` на Windows.
- **Полностью переработанный центр RetroAchievements**: удобные поля ввода с показом пароля, аватар профиля, статус подключения, очки Hardcore и Softcore, глобальный рейтинг, переключатели режимов (`Hardcore` / `Encore` / `Unofficial` / `Notifications`) и детальное окно достижений игры с прогресс-баром, очками, фильтрами и настоящими иконками трофеев.
- **Автоматическая загрузка обложек игр (Libretro Thumbnails Index)**: полная индексация каталогов GB/GBC/GBA с 30-дневным локальным кэшем, нечетким сопоставлением названий и 4-поточной фоновой загрузкой на Android и Windows.
- **Полноценная настройка размера, позиции и поворота экрана отдельно для GB, GBC и GBA**: интерактивное перетаскивание и изменение размера игрового окна в редакторе раскладок Android (раздельно для Портрета и Альбома) и точные настройки геометрии экрана на Windows.
- **Полная поддержка шейдеров RetroArch (`.slangp` / `.slang`) через `librashader`**: встроенная библиотека `liblibrashader.so` (`arm64-v8a`, `armeabi-v7a`, `x86_64`), 10 готовых пресетов (`LCD GBA Grid`, `LCD DMG Olive`, `LCD 3x Sharp`, `GBA Color AGS-101`, `GBC Color Accurate`, `xBRZ Smooth HD`, `Sharp Bilinear`, `CRT Scanlines`, `Vibrant HD`, `Game Boy Pocket`) и импорт пользовательских `.slangp`/`.slang` отдельно для GB, GBC и GBA.
- **Графический бэкенд Vulkan 1.1 + кастомные драйверы Turnip (`.so`)**: аппаратный Vulkan 1.1 рендерер на Android с определением GPU и автоматическим резервным откатом на OpenGL ES 3.2.
- **Внутреннее увеличение разрешения (`1x..4x`) и 6 алгоритмов апскейлинга**: `Nearest`, `xBRZ HD`, `Scale2x / EPX`, `MMPX Sharp`, `LCD Subpixel HD` и `CRT Scanlines HD` на Android и Windows.
- **Живой поиск читов в RAM (RAM Cheat Finder), разгон CPU (`50%..400%`) и Frame Skip (`0..4`)**: сканирование памяти `IWRAM`/`EWRAM`/`WRAM`/`HRAM`/`SRAM` (8/16/32 бита) с записью значений на лету и добавлением в чит-коды.

## Файлы релиза
- `STORM_GBCA_1.1.0.apk` - Подписанная сборка для Android 8.0+ (`arm64-v8a`, `armeabi-v7a`, `x86_64`, 16 КБ выравнивание ELF, включает `libstorm_gbca_core.so` и `liblibrashader.so`)
- `STORM_GBCA_1.1.0_Windows.zip` - Портативная автономная сборка для Windows 10/11 (x64)
- `STORM_GBCA_1.1.0_Setup.exe` - Графический установщик для Windows 10/11 (x64) с ярлыками и регистрацией в Windows

## Ссылки
- GitHub: https://github.com/ReiKatari/STORM_GBCA
- STORM GAMES WORLD: https://stormgamesworld.ru/programs?repo=STORM_GBCA
