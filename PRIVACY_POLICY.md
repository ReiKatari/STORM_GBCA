# Privacy Policy - STORM GBCA

**Effective Date:** 2026-11-28
**Developer:** STORM TEAM
**Application:** STORM GBCA (Android & Windows x64, version 1.9.1)
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
- **Gameplay Data:** Cartridge SRAM saves (`.sav`), save states (`.st0`..`.st9`), save-state screenshots (`.png`), BIOS paths, cheats (`.json`), and user preferences (`settings.json` on Windows / `SharedPreferences` on Android).
- **Cover Art Cache:** Optional locally cached game cover images downloaded only when the user enables Libretro or RetroAchievements cover fetching.
- **Offline RetroAchievements Queue:** Pending achievement or leaderboard submissions stored locally if a network error occurs while offline, automatically cleared once successfully delivered to RetroAchievements:
  - **Android:** `SharedPreferences` (`ra_pending_queue`)
  - **Windows:** `%APPDATA%/STORM_GBCA/ra_offline_queue.json`
- **RetroAchievements Session Credentials:** When signed in to RetroAchievements, ONLY your **username** and **API session token** are stored locally on your device. **Your plaintext password is NEVER stored on disk, in logs, or in URL query parameters** (all RetroAchievements authentication and API calls use HTTPS POST with `application/x-www-form-urlencoded`).

**Retention Period:**
- Local gameplay files, settings, and caches remain on your device only until you delete them, clear application data, or uninstall STORM GBCA.
- RetroAchievements username and API session token are retained locally only while signed in and are **immediately erased** when you click **Sign Out / Logout** or uninstall the application.

---

## 3. External Network Connections & Server Locations

STORM GBCA makes zero telemetry or tracking connections. By default, **automatic update checks on startup** are enabled (sending only anonymous standard HTTPS GET requests to GitHub / STORM GAMES WORLD release feeds with zero personal data or device identifiers) and can be disabled with one switch in Settings (`Check for updates on launch`). All other network requests occur **only** when explicitly initiated or enabled by the user (such as RetroAchievements sign-in or optional cover art downloads).

| Service / Endpoint | Server Location | When Connected | Data Transmitted | Governing Policy |
| --- | --- | --- | --- | --- |
| **RetroAchievements** (`retroachievements.org`, `media.retroachievements.org`) | United States | ONLY when signed in to RetroAchievements or fetching RA game cover art / badges | HTTPS POST login credentials (password sent once during login to obtain an API token, never saved), username, API token, loaded ROM MD5 hash, unlocked achievement IDs, leaderboard scores, Rich Presence status, and cover/badge image requests | https://retroachievements.org/terms |
| **GitHub** (`api.github.com`, `github.com`, `raw.githubusercontent.com`) | United States (GitHub, Inc. / Microsoft) | Automatic update checks on startup (enabled by default, disableable via `Check for updates on launch` in Settings), manual update checks, release package downloads, optional Libretro Thumbnails cover-art index / image lookup, and optional user-initiated custom Vulkan GPU driver catalog lookup and downloads (Turnip / Adreno / Mali releases from GitHub on Android) | Standard HTTPS GET request (`User-Agent`, release metadata, public boxart filename, or public GPU driver release asset); zero personal data or device identifiers | https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement |
| **jsDelivr CDN** (`cdn.jsdelivr.net`, `fastly.jsdelivr.net`) | Global CDN (ProspectOne / Fastly) | ONLY when the user enables Libretro cover downloads or triggers cover fetching | Public game title / platform path to fetch Libretro Thumbnails boxart PNGs; zero personal identifiers | https://www.jsdelivr.com/terms/privacy-policy-jsdelivr-net |
| **STORM GAMES WORLD** (`stormgamesworld.ru`, `storm-games.world`) | Russian Federation / Global mirror | Primary/fallback release update checks (`Check for updates on launch`, disableable in Settings), optional STORM GAMES WORLD game and Russian translation catalog (`/api/platforms`, `/api/games`, `/api/translations`), user-initiated ROM/translation downloads, and optional background game/translation version update checks (disableable in Settings) | Standard HTTPS GET requests for public release metadata, public game/translation catalog entries, and requested ROM/translation files; zero personal identifiers or telemetry | https://stormgamesworld.ru |

---

## 4. GDPR & User Rights

Under the General Data Protection Regulation (GDPR) and equivalent privacy laws, users have rights of access, rectification, portability, and erasure:

- **Local Device Data:** Because STORM TEAM collects and retains zero personal data on its own servers, you exercise 100% direct control over all local data on your device:
  - **Access & Portability:** All saves, states, screenshots, and config files are stored in standard user-accessible directories (`%APPDATA%/STORM_GBCA` or portable `./data` on Windows; app storage / SAF folders on Android).
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
