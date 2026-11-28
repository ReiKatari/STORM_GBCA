# Privacy Policy - STORM GBCA

**Effective Date:** 2026-11-28
**Developer:** STORM TEAM
**Application:** STORM GBCA (Android & Windows x64, version 1.7.0)
**Repository:** https://github.com/ReiKatari/STORM_GBCA
**Website:** https://stormgamesworld.ru/programs?repo=STORM_GBCA

---

## 1. Overview & Zero Telemetry Commitment

**STORM GBCA** is 100% free and open-source software (licensed under MPL-2.0) developed and published by **STORM TEAM**.

- **Zero Advertising:** STORM GBCA contains NO advertising SDKs and displays NO ads.
- **Zero Analytics or Tracking:** STORM GBCA contains NO analytics SDKs, NO usage telemetry, NO behavioral profiling, and NO crash-reporting uploads.
- **Zero Paid Tiers:** STORM GBCA has NO in-app purchases, NO subscriptions, and NO paid features on either Android or Windows.

STORM TEAM does not operate any telemetry servers and does not collect, sell, rent, or share personal data.

---

## 2. Local Data Stored on Your Device & Retention Periods

All emulation and configuration data is stored exclusively on your local device:

- **ROM Library & Paths:** References to user-selected ROM folders, ROM metadata cache (`rom_library.json`), and favorites/recent history.
- **Gameplay Data:** Cartridge SRAM saves (`.sav`), save states (`.st0`..`.st9`), save-state screenshots (`.png`), BIOS paths, cheats (`.json`), and user preferences (`settings.json` on Windows / SharedPreferences on Android).
- **Cover Art Cache:** Optional locally cached game cover images downloaded only when the user enables Libretro or RetroAchievements cover fetching.
- **Offline RetroAchievements Queue (`ra_offline_queue.json`):** Pending achievement or leaderboard submissions stored locally if a network error occurs while offline, automatically cleared once successfully delivered to RetroAchievements.
- **RetroAchievements Session Credentials:** When signed in to RetroAchievements, ONLY your **username** and **API session token** are stored locally on your device. **Your plaintext password is NEVER stored on disk, in logs, or in URL query parameters** (login uses HTTPS POST with `application/x-www-form-urlencoded`).

**Retention Period:**
- Local gameplay files, settings, and caches remain on your device only until you delete them, clear application data, or uninstall STORM GBCA.
- RetroAchievements username and API session token are retained locally only while signed in and are **immediately erased** when you click **Sign Out / Logout** or uninstall the application.

---

## 3. Optional External Network Connections & Server Locations

STORM GBCA makes network requests **ONLY** when explicitly initiated or enabled by the user. No background connections are made without user-enabled features.

| Service / Endpoint | Server Location | When Connected | Data Transmitted | Governing Policy |
| --- | --- | --- | --- | --- |
| **RetroAchievements** (`retroachievements.org`, `media.retroachievements.org`) | United States | ONLY when the user explicitly signs in and enables RetroAchievements | HTTPS POST login credentials (password sent once during login to obtain an API token, never saved), username, API token, ROM MD5 hash, unlocked achievement IDs, leaderboard scores, and Rich Presence status | https://retroachievements.org/terms |
| **GitHub** (`api.github.com`, `github.com`) | United States (GitHub, Inc. / Microsoft) | ONLY when checking for app updates or downloading release packages | Standard HTTPS GET request (`User-Agent` and release tag metadata); no personal identifiers | https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement |
| **Libretro Thumbnails** (`thumbnails.libretro.com`) | Global CDN / United States | ONLY when the user enables Libretro cover downloads or triggers cover fetching | Game title / platform path to fetch public boxart PNGs; no personal identifiers | https://www.libretro.com/index.php/privacy-policy/ |
| **STORM GAMES WORLD** (`stormgamesworld.ru`) | Russian Federation | ONLY when checking the fallback update feed or optional Russian game metadata | Standard HTTPS request for public release metadata; no personal identifiers or telemetry | https://stormgamesworld.ru |

---

## 4. GDPR & User Rights

Under the General Data Protection Regulation (GDPR) and equivalent privacy laws, users have rights of access, rectification, portability, and erasure:

- **Local Device Data:** Because STORM TEAM collects and retains zero personal data on its own servers, you exercise 100% direct control over all local data on your device:
  - **Access & Portability:** All saves, states, screenshots, and config files are stored in standard user-accessible directories (`%APPDATA%\STORM_GBCA` or portable `./data` on Windows; app storage / SAF folders on Android).
  - **Instant Erasure:** Click **Sign Out** in RetroAchievements settings to erase stored RA tokens immediately, clear the cover cache in settings, or uninstall STORM GBCA / delete its data folder to remove all local data permanently.
- **RetroAchievements Server Data:** Data submitted to your RetroAchievements account (unlocked trophies, points, leaderboard entries, Rich Presence) is stored on RetroAchievements servers and managed directly through your RetroAchievements account settings at https://retroachievements.org/settings.

---

## 5. Children's Privacy

STORM GBCA does not knowingly collect personal information from children or any other users. RetroAchievements account usage is optional and governed by RetroAchievements' own age and account policies.

---

## 6. Contact & Support

If you have questions about this Privacy Policy or STORM GBCA's data handling, contact STORM TEAM via:

- **GitHub Issues:** https://github.com/ReiKatari/STORM_GBCA/issues
- **Project Portal:** https://stormgamesworld.ru/programs?repo=STORM_GBCA
