# Third-Party Licenses & Open-Source Attributions - STORM GBCA

**STORM GBCA (version 1.6.0)** is distributed under the **Mozilla Public License Version 2.0 (MPL-2.0)**. See [LICENSE](LICENSE) for the full project license notice.

---

## Monetization Disclosure

**STORM GBCA is 100% free and open-source software on both Android and Windows.**

- NO paid tiers or pro versions
- NO in-app purchases (IAP)
- NO subscriptions
- NO advertisements or ad SDKs
- NO non-commercial or proprietary emulation cores (every shipped emulation core and library is OSI-approved Free and Open Source Software)

---

## 1. Native Emulation Cores & C/C++ Libraries (`core/third_party/`)

| Component | Version | License | Copyright Holder | Upstream Repository |
| --- | --- | --- | --- | --- |
| **mGBA** | 0.10.5 | **MPL-2.0** | Jeffrey Pfau (endrift) & mGBA contributors | https://github.com/mgba-emu/mgba |
| **SameBoy** | 1.0.5 | **MIT (Expat)** | Lior Halphon (LIJI32) | https://github.com/LIJI32/SameBoy |
| **rcheevos** | 12.5.0 | **MIT** | RetroAchievements.org (Jamiras & contributors) | https://github.com/RetroAchievements/rcheevos |
| **librashader** | 0.8+ | **MPL-2.0 / MIT** | Ronny Chan (SnowflakePowered) | https://github.com/SnowflakePowered/librashader |
| **Google Oboe** | 1.11.2 | **Apache-2.0** | Google LLC | https://github.com/google/oboe |

### MPL-2.0 Source Availability Notice (mGBA & librashader)

In compliance with **MPL-2.0 Section 3.2**:
- The complete source code of **mGBA** and **librashader** integration headers, along with all modifications and CMake build scripts used by STORM GBCA, are included directly in this public repository under [`core/third_party/mgba`](core/third_party/mgba) and [`core/third_party/librashader`](core/third_party/librashader).
- All original STORM GBCA core wrapper code (`core/src/`, `core/include/`) is also licensed under **MPL-2.0**.

---

## 2. Windows Desktop Dependencies (`windows/`)

Referenced in `StormGbca.Desktop.csproj`, `StormGbca.UI.csproj`, `StormGbca.Core.csproj`, and `Storm.AuroraFluid.Kit.csproj`:

| Component | Version | License | Copyright Holder | Upstream Repository |
| --- | --- | --- | --- | --- |
| **Avalonia UI** (`Avalonia`, `Avalonia.Desktop`, `Avalonia.Skia`, `Avalonia.Themes.Fluent`) | 12.2.999-cibuild0100943-alpha | **MIT** | AvaloniaUI OÜ & contributors | https://github.com/AvaloniaUI/Avalonia |
| **SkiaSharp** | 3.119.4 | **MIT** | Mono / Microsoft Corporation | https://github.com/mono/SkiaSharp |
| **Windows Multimedia Audio (`winmm.dll`)** | Windows OS API | System API | Microsoft Corporation | Built-in OS P/Invoke (`WinMmAudioOutput.cs`) |

---

## 3. Android Application Dependencies (`app/build.gradle.kts` & `gradle/libs.versions.toml`)

| Component | Version | License | Copyright Holder | Upstream Repository |
| --- | --- | --- | --- | --- |
| **Jetpack Compose BOM** (`compose-foundation`, `compose-material`, `compose-material-icons`, `compose-ui`, `compose-ui-tooling`) | 2026.05.00 | **Apache-2.0** | Google LLC (AOSP) | https://github.com/androidx/androidx |
| **AndroidX Activity & Activity Compose** | 1.13.0 | **Apache-2.0** | Google LLC (AOSP) | https://github.com/androidx/androidx |
| **AndroidX AppCompat** | 1.7.1 | **Apache-2.0** | Google LLC (AOSP) | https://github.com/androidx/androidx |
| **AndroidX Core KTX** | 1.18.0 | **Apache-2.0** | Google LLC (AOSP) | https://github.com/androidx/androidx |
| **AndroidX DocumentFile** | 1.1.0 | **Apache-2.0** | Google LLC (AOSP) | https://github.com/androidx/androidx |
| **AndroidX Lifecycle ViewModel & Compose** | 2.10.0 | **Apache-2.0** | Google LLC (AOSP) | https://github.com/androidx/androidx |
| **AndroidX Preference KTX** | 1.2.1 | **Apache-2.0** | Google LLC (AOSP) | https://github.com/androidx/androidx |
| **AndroidX Core SplashScreen** | 1.2.0 | **Apache-2.0** | Google LLC (AOSP) | https://github.com/androidx/androidx |
| **Material Components for Android** | 1.13.0 | **Apache-2.0** | Google LLC | https://github.com/material-components/material-components-android |
| **Kotlin Coroutines Core** | 1.10.2 | **Apache-2.0** | JetBrains s.r.o. | https://github.com/Kotlin/kotlinx.coroutines |
| **Kotlinx Serialization JSON** | 1.11.0 | **Apache-2.0** | JetBrains s.r.o. | https://github.com/Kotlin/kotlinx.serialization |
| **Coil Compose** | 2.7.0 | **Apache-2.0** | Coil Contributors | https://github.com/coil-kt/coil |
| **OkHttp** | 5.3.2 | **Apache-2.0** | Square, Inc. | https://github.com/square/okhttp |
| **Apache Commons Compress** | 1.27.1 | **Apache-2.0** | The Apache Software Foundation | https://github.com/apache/commons-compress |
| **XZ for Java** | 1.11 | **0BSD (Public Domain)** | Lasse Collin | https://tukaani.org/xz/java.html |
| **Markwon** | 4.6.2 | **Apache-2.0** | Dimitry Ivanov | https://github.com/noties/Markwon |

---

## 4. Architectural Inspiration & Reference Credits

The following open-source projects served as technical references and inspiration (no non-commercial code is used):

- **SkyEmu** (MIT - https://github.com/skylersaleh/SkyEmu) - LCD subpixel shader concepts and delta-compressed rewind ring ideas.
- **NanoBoyAdvance (`phoenix`)** (BSD-2-Clause - https://codeberg.org/nba-emu/NanoBoyAdvance) - Hermite GBA audio resampling and AGB-001 / AGS-101 color matrices.
- **ares** (ISC - https://github.com/ares-emulator/ares) - Dynamic Rate Control (DRC) audio synchronization and Run-Ahead latency reduction concepts.
- **Gambatte-libretro** (GPL-2.0 reference / public color palettes - https://github.com/libretro/gambatte-libretro) - Classic DMG / GBC / SGB color palette values.
- **VisualBoyAdvance-M, MesenCE, GBE+** - Cheat format specifications and peripheral documentation.
- **Libretro Thumbnails** (https://github.com/libretro-thumbnails) - Optional public cover art repository.
