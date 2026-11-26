# STORM GBCA 1.0.0

Первый официальный кроссплатформенный релиз флагманского эмулятора **Nintendo Game Boy, Game Boy Color и Game Boy Advance** для **Android** и **Windows x64** в фирменном стиле **STORM Watermelon / AuroraFluid**.

## Главные возможности релиза 1.0.0
- **Двойное нативное ядро**: **mGBA (`master`, 2026)** в качестве основного ядра для GB / GBC / SGB / GBA и **SameBoy (`v1.0.5`)** в качестве опционального высокоточного ядра для GB / GBC / SGB.
- **Лучшее из лучших эмуляторов GB/GBC/GBA**:
  - Шейдер LCD + RGB-субпиксели + гостинг и 64-байтная XOR дельта-перемотка назад (по мотивам **SkyEmu**).
  - Эрмитова (Hermite) HQ-интерполяция аудио 48 кГц и цветовые матрицы AGB-001 / AGS-101 (по мотивам **NanoBoyAdvance phoenix**).
  - 7 режимов цветокоррекции LCD и 3-режимный фильтр верхних частот аудио (**SameBoy**).
  - Динамический контроль частоты аудио (DRC) и упреждение кадров **Run-Ahead 0..2** с детерминированным захватом кадра (по мотивам **ares**).
  - 28 кураторских палитр DMG/GBC/SGB, 6-канальный аудиомикшер, универсальный парсер читов (GameShark / Action Replay / CodeBreaker / Game Genie), мягкое патчирование **IPS / IPS32 / UPS / BPS**, датчики **Solar LUX / Gyro / Tilt / Rumble / RTC**, рамки **Super Game Boy** и **RetroAchievements (`rcheevos v12.5.0`)**.
- **Независимые 3D-скины отдельно для GB, GBC и GBA**: 26 процедурных 3D-корпусов (DMG, Pocket, Light, SGB, GBC Atomic Purple/Berry/Teal/Kiwi/Dandelion/Grape/Pikachu, GBA Indigo/Glacier/White/Spice Orange/Fuchsia, GBA SP Cobalt/Onyx/NES Classic, Game Boy Micro Famicom и STORM Aurora) + режим «Без корпуса».
- **Независимые оверлеи, раскладки и стили кнопок отдельно для GB, GBC и GBA**: раздельные настройки раскладки, стиля кнопок, экранного LCD-шейдера, прозрачности, масштаба и шифтов `L`/`R` (скрыты по умолчанию на GB/GBC, активны на GBA).
- **Единый интерфейс STORM**: 9 тем оформления, 12/13 акцентов, 3D-анимация запуска GB/GBC/GBA, плашки платформ (`GB`, `GBC`, `GBA`, `SGB`), регионов и аппаратных функций, автозагрузка обложек Libretro Thumbnails и двойное зеркало автообновлений (**GitHub** + **STORM GAMES WORLD**).

## Файлы релиза
- `STORM_GBCA_1.0.0.apk` - Подписанная сборка для Android 8.0+ (`arm64-v8a`, `armeabi-v7a`, `x86_64`, 16 КБ выравнивание ELF)
- `STORM_GBCA_1.0.0_Windows.zip` - Портативная автономная сборка для Windows 10/11 (x64)
- `STORM_GBCA_1.0.0_Setup.exe` - Графический установщик для Windows 10/11 (x64) с ярлыками и регистрацией в Windows

## Ссылки
- GitHub: https://github.com/ReiKatari/STORM_GBCA
- STORM GAMES WORLD: https://stormgamesworld.ru/programs?repo=STORM_GBCA
