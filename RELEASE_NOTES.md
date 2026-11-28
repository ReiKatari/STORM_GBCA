# STORM GBCA 1.8.0

## Русский
- **AMD FSR 1 (EASU + RCAS) и AMD FSR 3 (Temporal + RCAS):** добавлены многопроходные GPU-шейдеры сверхвысокой чёткости на Android (OpenGL ES 3.2, Vulkan и RetroArch `.slangp`) и Windows (аппаратный рендерер SkiaSharp GPU SkSL с 12-точечным EASU, 32-фазным субпиксельным сдвигом Halton, отсечением дисперсии YCoCg, репроекцией по оптическому потоку и 5-точечным фильтром RCAS).
- **Настройка резкости FSR RCAS и режимов качества:** в Настройках и игровом меню паузы на обеих платформах доступны ползунок резкости RCAS (`0..100%`) и 4 режима качества (`Качество 67% - 1.5x`, `Баланс 59% - 1.7x`, `Производительность 50% - 2.0x`, `Ультра производительность 33% - 3.0x`).
- **Необязательная генерация кадров (`60 -> 120 FPS`):** добавлена интерполяция промежуточных кадров на основе оптического потока по градиентам яркости с отсечением при смене сцены для экранов 120 Гц (по умолчанию выключена, задержка ~16.7 мс, 100% совместима с RetroAchievements Hardcore).
- **Реальное определение версий OpenGL ES и Vulkan:** в настройках видео статичные подписи `OpenGL 3.2` / `Vulkan 1.1` заменены на фактически поддерживаемые устройством версии графических API и активный GPU-бэкенд с отображением минимальных требований.
- **Менеджер и загрузчик пользовательских драйверов Vulkan GPU на Android (как в STORM DS):** добавлено автоопределение модели GPU, каталог рекомендуемых сборок Turnip / Adreno / Mali, отображение прогресса загрузки, безопасная распаковка ZIP-архивов и локальный импорт драйверов.

## English
- **AMD FSR 1 (EASU + RCAS) & AMD FSR 3 (Temporal + RCAS):** added multi-pass GPU upscaling shaders across Android (OpenGL ES 3.2, Vulkan, and `.slangp`) and Windows (SkiaSharp GPU SkSL renderer with 12-tap EASU, 32-phase Halton jitter, YCoCg variance clipping, optical-flow reprojection, and 5-tap RCAS sharpening).
- **FSR RCAS Sharpness (`0..100%`) & Quality Modes:** added RCAS sharpness tuning and 4 quality presets (`Quality 67% - 1.5x`, `Balanced 59% - 1.7x`, `Performance 50% - 2.0x`, `Ultra Performance 33% - 3.0x`) in both Settings and the in-game Pause Menu on Android and Windows.
- **Optional Frame Generation (`60 -> 120 FPS`):** added optical-flow midpoint interpolation with scene-cut disocclusion fallback for 120 Hz displays (off by default, ~16.7 ms latency note, 100% RetroAchievements Hardcore compatible).
- **Real OpenGL ES & Vulkan Version Detection:** replaced static `OpenGL 3.2` / `Vulkan 1.1` labels in Video Settings with real device-supported API versions and active GPU renderer capability info alongside minimum requirements.
- **Custom Vulkan GPU Driver Manager & Downloader on Android (like STORM DS):** added automatic GPU detection, curated Turnip / Adreno / Mali catalog, live download progress, safe ZIP extraction, and local driver import.

## Deutsch
- **AMD FSR 1 (EASU + RCAS) & AMD FSR 3 (Temporal + RCAS):** mehrstufige GPU-Upscaling-Shader für Android (OpenGL ES 3.2, Vulkan und `.slangp`) sowie Windows (SkiaSharp GPU-SkSL-Renderer mit 12-Tap-EASU, 32-Phasen-Halton-Jitter, YCoCg-Varianz-Clipping, Optical-Flow-Reprojektion und 5-Tap-RCAS).
- **FSR RCAS-Schärfe (`0..100%`) & Qualitätsmodi:** stufenlose RCAS-Schärfe und 4 Qualitätsstufen (`Qualität 67% - 1.5x` bis `Ultra-Leistung 33% - 3.0x`) in den Einstellungen und im Pausenmenü auf beiden Plattformen.
- **Optionale Frame Generation (`60 -> 120 FPS`):** Zwischenbild-Interpolation per optischem Fluss mit Szenenwechsel-Erkennung für 120-Hz-Displays (standardmäßig deaktiviert, ~16.7 ms Eingabeverzögerung, 100% kompatibel mit RetroAchievements Hardcore).
- **Echte OpenGL ES- & Vulkan-Erkennung:** statische Bezeichnungen in den Videoeinstellungen wurden durch die tatsächlich unterstützten API-Versionen, den aktiven GPU-Renderer und Mindestanforderungen ersetzt.
- **Benutzerdefinierter Vulkan-GPU-Treiber-Manager unter Android (wie STORM DS):** GPU-Erkennung, Katalog empfohlener Turnip- / Adreno- / Mali-Treiber, Live-Download-Fortschritt, sicheres ZIP-Entpacken und lokaler Import.

## Français
- **AMD FSR 1 (EASU + RCAS) et AMD FSR 3 (Temporal + RCAS) :** ajout de shaders GPU multi-passes sur Android (OpenGL ES 3.2, Vulkan et `.slangp`) et Windows (rendu GPU SkiaSharp SkSL avec EASU 12 points, jitter Halton 32 phases, écrêtage YCoCg, reprojection par flux optique et netteté RCAS 5 points).
- **Netteté FSR RCAS (`0..100%`) et modes de qualité :** réglage de la netteté RCAS et 4 profils (`Qualité 67% - 1.5x` à `Ultra Performance 33% - 3.0x`) dans les Paramètres et le menu Pause sur les deux plateformes.
- **Génération d'images optionnelle (`60 -> 120 FPS`) :** interpolation intermédiaire par flux optique avec repli sur changement de scène pour écrans 120 Hz (désactivée par défaut, ~16.7 ms de latence, 100% compatible RetroAchievements Hardcore).
- **Détection réelle des versions OpenGL ES et Vulkan :** remplacement des mentions statiques par les versions réellement prises en charge par l'appareil et le moteur de rendu GPU actif avec les prérequis minimaux.
- **Gestionnaire de pilotes GPU Vulkan personnalisés sur Android (comme STORM DS) :** détection du GPU, catalogue Turnip / Adreno / Mali recommandé, progression de téléchargement en direct, extraction ZIP sécurisée et import local.

## Español
- **AMD FSR 1 (EASU + RCAS) y AMD FSR 3 (Temporal + RCAS):** nuevos shaders GPU multipasada en Android (OpenGL ES 3.2, Vulkan y `.slangp`) y Windows (renderizador GPU SkiaSharp SkSL con EASU de 12 muestras, jitter Halton de 32 fases, recorte de varianza YCoCg, reproyección por flujo óptico y nitidez RCAS de 5 muestras).
- **Nitidez FSR RCAS (`0..100%`) y modos de calidad:** ajuste de nitidez RCAS y 4 modos de calidad (`Calidad 67% - 1.5x` hasta `Ultra Rendimiento 33% - 3.0x`) en Ajustes y en el menú de Pausa en ambas plataformas.
- **Generación de fotogramas opcional (`60 -> 120 FPS`):** interpolación intermedia por flujo óptico con detección de cortes de escena para pantallas de 120 Hz (desactivada por defecto, ~16.7 ms de latence, 100% compatible con RetroAchievements Hardcore).
- **Detección real de versiones de OpenGL ES y Vulkan:** reemplaza las etiquetas estáticas en Ajustes de vídeo por las versiones reales compatibles del dispositivo, el renderizador GPU activo y los requisitos mínimos.
- **Gestor y descargador de drivers Vulkan GPU personalizados en Android (como STORM DS):** detección de GPU, catálogo Turnip / Adreno / Mali recomendado, progreso en vivo, extracción ZIP segura e importación local.

## Italiano
- **AMD FSR 1 (EASU + RCAS) e AMD FSR 3 (Temporal + RCAS):** aggiunti shader GPU multi-pass su Android (OpenGL ES 3.2, Vulkan e `.slangp`) e Windows (renderer GPU SkiaSharp SkSL con EASU a 12 tap, jitter Halton a 32 fasi, variance clipping YCoCg, riproiezione con flusso ottico e nitidezza RCAS a 5 tap).
- **Nitidezza FSR RCAS (`0..100%`) e modalità qualità:** controllo nitidezza RCAS e 4 preset (`Qualità 67% - 1.5x` fino a `Prestazioni Ultra 33% - 3.0x`) nelle Impostazioni e nel menu Pausa su entrambe le piattaforme.
- **Generazione fotogrammi opzionale (`60 -> 120 FPS`):** interpolazione intermedia basata su flusso ottico con fallback sui cambi di scena per display a 120 Hz (disattivata di default, ~16.7 ms di latenza, 100% compatibile con RetroAchievements Hardcore).
- **Rilevamento reale versioni OpenGL ES e Vulkan:** sostituite le etichette statiche nelle Impostazioni video con le versioni API effettivamente supportate dal dispositivo e il renderer GPU attivo con i requisiti minimi.
- **Gestore e downloader driver Vulkan GPU personalizzati su Android (come STORM DS):** rilevamento GPU, catalogo Turnip / Adreno / Mali consigliato, avanzamento download in tempo reale, estrazione ZIP sicura e importazione locale.

## Português
- **AMD FSR 1 (EASU + RCAS) e AMD FSR 3 (Temporal + RCAS):** shaders GPU multipassagem no Android (OpenGL ES 3.2, Vulkan e `.slangp`) e Windows (renderizador GPU SkiaSharp SkSL com EASU de 12 amostras, jitter Halton de 32 fases, recorte de variância YCoCg, reprojeção por fluxo óptico e nitidez RCAS de 5 amostras).
- **Nitidez FSR RCAS (`0..100%`) e modos de qualidade:** ajuste de nitidez RCAS e 4 modos (`Qualidade 67% - 1.5x` até `Desempenho Ultra 33% - 3.0x`) nas Configurações e no menu de Pausa em ambas as plataformas.
- **Geração de quadros opcional (`60 -> 120 FPS`):** interpolação intermediária por fluxo óptico com proteção em cortes de cena para telas de 120 Hz (desativada por padrão, ~16.7 ms de latência, 100% compatível com RetroAchievements Hardcore).
- **Detecção real das versões OpenGL ES e Vulkan:** substituiu rótulos estáticos nas Configurações de Vídeo pelas versões reais suportadas pelo dispositivo e pelo renderizador GPU ativo com requisitos mínimos.
- **Gerenciador e baixador de drivers Vulkan GPU personalizados no Android (como no STORM DS):** detecção de GPU, catálogo Turnip / Adreno / Mali recomendado, progresso de download em tempo real, extração ZIP segura e importação local.

## 日本語
- **AMD FSR 1 (EASU + RCAS) および AMD FSR 3 (Temporal + RCAS):** Android (OpenGL ES 3.2、Vulkan、`.slangp`) と Windows (12タップ EASU、32位相 Halton ジッター、YCoCg 分散クリッピング、オプティカルフロー再投影、5タップ RCAS を備えた SkiaSharp GPU SkSL レンダラー) にマルチパス GPU シェーダーを搭載しました。
- **FSR RCAS シャープネス (`0..100%`) と品質モード:** 両プラットフォームの設定画面とポーズメニューに RCAS シャープネス調整と4段階の品質プリセット (`67% - 1.5x` から `33% - 3.0x`) を追加しました。
- **任意フレーム生成 (`60 -> 120 FPS`):** 120 Hz ディスプレイ向けに輝度勾配オプティカルフローによる中間フレーム補間を追加 (既定でオフ、入力遅延約 16.7 ms、RetroAchievements Hardcore 完全対応)。
- **OpenGL ES / Vulkan 実サポートバージョンの検出:** ビデオ設定の固定表記を廃止し、実機が対応する OpenGL ES / Vulkan バージョン、有効な GPU レンダラー情報、最小要件を表示します。
- **Android 向けカスタム Vulkan GPU ドライバー管理・ダウンローダー (STORM DS 準拠):** GPU 自動判定、推奨 Turnip / Adreno / Mali カタログ、リアルタイム進捗表示、安全な ZIP 展開、ローカルインポートに対応しました。

## 简体中文
- **AMD FSR 1 (EASU + RCAS) 与 AMD FSR 3 (Temporal + RCAS)：** 为 Android (OpenGL ES 3.2、Vulkan、`.slangp`) 和 Windows (SkiaSharp GPU SkSL 渲染器，集成 12 采样 EASU、32 相位 Halton 抖动、YCoCg 方差裁剪、光流重投影与 5 采样 RCAS) 新增多通道 GPU 超分着色器。
- **FSR RCAS 锐化 (`0..100%`) 与质量模式：** 在双端设置与游戏内暂停菜单中提供 RCAS 锐化滑块及 4 档质量预设 (`67% - 1.5x` 至 `33% - 3.0x`)。
- **可选帧生成 (`60 -> 120 FPS`)：** 为 120 Hz 显示器新增基于亮度梯度光流的中间帧插值与切屏保护 (默认关闭，约 16.7 ms 输入延迟，100% 兼容 RetroAchievements Hardcore)。
- **真实 OpenGL ES 与 Vulkan 版本检测：** 视频设置中不再显示静态版本号，改为实时显示设备支持的 API 版本、当前活动 GPU 渲染器信息及最低硬件要求。
- **Android 自定义 Vulkan GPU 驱动管理器与下载器 (同步 STORM DS)：** 支持自动识别 GPU、推荐 Turnip / Adreno / Mali 驱动目录、实时下载进度、安全 ZIP 解压与本地导入。

## 한국어
- **AMD FSR 1 (EASU + RCAS) 및 AMD FSR 3 (Temporal + RCAS):** Android(OpenGL ES 3.2, Vulkan, `.slangp`)와 Windows(12탭 EASU, 32위상 Halton 지터, YCoCg 분산 클리핑, 광학 흐름 재투영, 5탭 RCAS를 구현한 SkiaSharp GPU SkSL 렌더러)에 멀티패스 GPU 셰이더를 추가했습니다.
- **FSR RCAS 선명도 (`0..100%`) 및 품질 모드:** 양 플랫폼 설정과 일시정지 메뉴에서 RCAS 선명도 조절과 4단계 품질 프리셋(`67% - 1.5x` ~ `33% - 3.0x`)을 지원합니다.
- **선택형 프레임 생성 (`60 -> 120 FPS`):** 120 Hz 디스플레이를 위한 휘도 기울기 광학 흐름 기반 중간 프레임 보간 및 장면 전환 보호 기능 추가 (기본 꺼짐, 입력 지연 약 16.7 ms, RetroAchievements Hardcore 100% 호환).
- **실제 OpenGL ES 및 Vulkan 버전 감지:** 비디오 설정의 고정 라벨을 기기에서 실제 지원하는 API 버전, 활성 GPU 렌더러 정보 및 최소 사양 표시로 교체했습니다.
- **Android 커스텀 Vulkan GPU 드라이버 관리자 및 다운로더 (STORM DS 방식):** GPU 자동 감지, 추천 Turnip / Adreno / Mali 카탈로그, 실시간 다운로드 진행률, 안전한 ZIP 압축 해제 및 로컬 가져오기를 제공합니다.

## हिन्दी
- **AMD FSR 1 (EASU + RCAS) और AMD FSR 3 (Temporal + RCAS):** Android (OpenGL ES 3.2, Vulkan और `.slangp`) तथा Windows (12-टैप EASU, 32-फ़ेज़ Halton जिटर, YCoCg वैरिएंस क्लिपिंग, ऑप्टिकल-फ़्लो रीप्रोजेक्शन और 5-टैप RCAS के साथ SkiaSharp GPU SkSL रेंडरर) में मल्टी-पास GPU अपस्केलिंग शेडर जोड़े गए हैं।
- **FSR RCAS शार्पनेस (`0..100%`) और गुणवत्ता मोड:** दोनों प्लेटफ़ॉर्म की सेटिंग्स और पॉज़ मेन्यू में RCAS शार्पनेस नियंत्रण तथा 4 गुणवत्ता प्रीसेट (`67% - 1.5x` से `33% - 3.0x`) जोड़े गए हैं।
- **वैकल्पिक फ़्रेम जनरेशन (`60 -> 120 FPS`):** 120 Hz डिस्प्ले के लिए ल्यूमा-ग्रेडिएंट ऑप्टिकल फ़्लो मध्य-फ़्रेम इंटरपोलेशन और सीन-कट फ़ॉलबैक जोड़ा गया (डिफ़ॉल्ट रूप से बंद, ~16.7 ms विलंब, RetroAchievements Hardcore से 100% संगत)।
- **वास्तविक OpenGL ES और Vulkan संस्करण पहचान:** वीडियो सेटिंग्स में स्थिर लेबल हटाकर डिवाइस द्वारा समर्थित वास्तविक API संस्करण, सक्रिय GPU रेंडरर जानकारी और न्यूनतम आवश्यकताएँ दिखाई गई हैं।
- **Android पर कस्टम Vulkan GPU ड्राइवर प्रबंधक और डाउनलोडर (STORM DS जैसा):** स्वचालित GPU पहचान, अनुशंसित Turnip / Adreno / Mali कैटलॉग, लाइव डाउनलोड प्रगति, सुरक्षित ZIP अनपैक और स्थानीय आयात शामिल है।
