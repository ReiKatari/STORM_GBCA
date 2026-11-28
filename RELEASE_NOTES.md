# STORM GBCA 1.9.1

## Русский
- **Критическая стабильность Windows UI и модальных окон:** исправлен захват кликов в `AuroraModalHost.Dismiss()` (модальные окна теперь полностью скрываются и освобождают ввод), устранены межпоточные исключения при стартовом сканировании библиотеки и автопроверке обновлений, а также исправлено закрытие выпадающих списков ComboBox и сохранение позиции прокрутки в Настройках.
- **Производительность Skia/SkSL и защита CPU:** добавлена потокобезопасная синхронизация кадров и отложенное освобождение GPU-истории FSR 3 в рендер-потоке, шейдеры Frame Generation и AMD FSR 3 оптимизированы до <= 10 выборок на пиксель (ускорение в ~10 раз), добавлена защита программного CPU-рендера от подвисаний 1440p и предотвращён шторм `RequestAnimationFrame`.
- **Исправление элементов управления и настроек на Windows:** исправлен захват клавиш при переназначении управления (`Focusable = true`), работа слайдеров и чекбоксов с переносом текста, моментальное применение видеофильтров и профилей оптимизации (Lite / Balanced / Performance / High Accuracy), асинхронный запуск Link Cable в меню паузы, немедленное обновление кадра при загрузке слота и перемотке, локализация всех 7 пунктов боковой панели и обновление описания релиза при смене зеркала (`STORM GAMES WORLD` / `GitHub`).
- **Надёжность ядра и Android:** исправлены гонки и блокировки в нативном ядре и Android-клиенте, улучшена стабильность каталога STORM GAMES WORLD, скачивания обложек, RetroAchievements и многоязычной поддержки на всех 11 языках.

## English
- **Critical Windows UI & Modal Stability:** fixed click-blocking in `AuroraModalHost.Dismiss()` so dismissed modals cleanly hide and release hit-testing, eliminated cross-thread `InvalidOperationException` during startup library scans and game update checks, and prevented Settings ComboBox dropdown glitches while preserving scroll offsets.
- **Skia/SkSL Performance & CPU Freeze Guard:** added full thread-safe synchronization and render-thread deferred GPU history disposal for FSR 3, rewrote Frame Generation and AMD FSR 3 SkSL shaders to <= 10 texture samples/pixel (~10x faster), added a software CPU fallback guard to prevent 1440p stalls, and eliminated `RequestAnimationFrame` storms.
- **Windows Controls & Settings Fixes:** fixed keyboard rebinding (`Focusable = true`), pointer capture on sliders/checkboxes/virtual gamepad, text wrapping on checkboxes, immediate `VideoFilter` and `OptimizationProfile` updates, async Link Cable buttons in the pause overlay, instant viewport refresh on Save Slot Load and Rewind, full 11-language navigation rail localization, and live changelog switching between `STORM GAMES WORLD` and `GitHub`.
- **Core & Android Reliability:** resolved stability, deadlock, and lifecycle issues across the native core and Android client, improving STORM GAMES WORLD catalog browsing, cover loading, and RetroAchievements reliability.

## Deutsch
- **Kritische Stabilität der Windows-Oberfläche und Modalfenster:** Click-Blocking in `AuroraModalHost.Dismiss()` behoben, Thread-übergreifende Ausnahmen beim Start-Bibliotheksscan und bei der Update-Prüfung beseitigt sowie ComboBox-Dropdowns und Scroll-Positionen in den Einstellungen stabilisiert.
- **Skia/SkSL-Leistung & CPU-Schutz:** Thread-sichere Frame-Synchronisation und verzögertes GPU-Verlaufs-Disposal für FSR 3 im Render-Thread ergänzt, Frame Generation und AMD FSR 3 auf <= 10 Samples/Pixel beschleunigt (~10x schneller), Software-CPU-Schutz gegen 1440p-Hänger hinzugefügt und `RequestAnimationFrame`-Stürme verhindert.
- **Korrekturen für Windows-Steuerelemente & Einstellungen:** Tastenneubelegung (`Focusable = true`), Pointer-Capture bei Slidern/Checkboxen/Gamepad, Zeilenumbruch bei Checkbox-Labels, sofortige Anwendung von Videofiltern und Optimierungsprofilen, asynchrone Link-Cable-Schaltflächen im Pausenmenü, sofortige Bildaktualisierung beim Laden/Zurückspulen, vollständige Lokalisierung aller 7 Navigationspunkte und dynamischer Changelog-Wechsel (`STORM GAMES WORLD` / `GitHub`).
- **Core- & Android-Zuverlässigkeit:** Abstürze, Deadlocks und UI-Probleme im nativen Core und in der Android-App behoben.

## Français
- **Stabilité critique de l'interface Windows et des modales :** correction du blocage des clics dans `AuroraModalHost.Dismiss()`, suppression des exceptions inter-threads lors du scan initial et de la vérification des mises à jour, et stabilisation des listes ComboBox avec conservation du défilement dans les Paramètres.
- **Performances Skia/SkSL & protection CPU :** synchronisation thread-safe des images et libération différée de l'historique GPU FSR 3 sur le thread de rendu, shaders Frame Generation et AMD FSR 3 optimisés à <= 10 échantillons/pixel (~10x plus rapides), garde CPU logiciel contre les blocages 1440p et suppression de la tempête `RequestAnimationFrame`.
- **Corrections des contrôles et paramètres Windows :** réaffectation clavier (`Focusable = true`), capture du pointeur sur curseurs/cases à cocher/manette virtuelle, retour à la ligne des libellés, application immédiate des filtres vidéo et profils d'optimisation, boutons Link Cable asynchrones en pause, rafraîchissement instantané après chargement/rembobinage, localisation des 7 onglets de navigation et mise à jour des notes de version selon la source (`STORM GAMES WORLD` / `GitHub`).
- **Fiabilité Core & Android :** correction des plantages, blocages et problèmes d'interface sur le cœur natif et le client Android.

## Español
- **Estabilidad crítica de la interfaz de Windows y ventanas modales:** corregido el bloqueo de clics en `AuroraModalHost.Dismiss()`, eliminadas las excepciones entre hilos durante el escaneo inicial y la comprobación de actualizaciones, y estabilizados los menús ComboBox preservando el desplazamiento en Ajustes.
- **Rendimiento Skia/SkSL y protección de CPU:** añadida sincronización segura entre hilos y liberación diferida del historial GPU de FSR 3 en el hilo de renderizado, optimizados los shaders de Frame Generation y AMD FSR 3 a <= 10 muestras/píxel (~10 veces más rápidos), añadida protección CPU por software para evitar bloqueos en 1440p y corregida la tormenta de `RequestAnimationFrame`.
- **Correcciones de controles y ajustes en Windows:** corregida la reasignación de teclas (`Focusable = true`), captura de puntero en deslizadores/casillas/mando virtual, ajuste de línea en casillas, aplicación inmediata de filtros de vídeo y perfiles de optimización, botones Link Cable asíncronos en pausa, actualización inmediata tras cargar/rebobinar, localización completa de los 7 elementos de navegación y actualización del registro de cambios al cambiar entre `STORM GAMES WORLD` y `GitHub`.
- **Fiabilidad del núcleo y Android:** solucionados bloqueos, cuelgues y fallos de interfaz en el núcleo nativo y la versión de Android.

## Italiano
- **Stabilità critica dell'interfaccia Windows e dei modali:** risolto il blocco dei clic in `AuroraModalHost.Dismiss()`, eliminate le eccezioni cross-thread durante la scansione iniziale e il controllo aggiornamenti, e stabilizzati i menu a discesa ComboBox mantenendo lo scorrimento nelle Impostazioni.
- **Prestazioni Skia/SkSL e protezione CPU:** aggiunta sincronizzazione thread-safe e rilascio differito della cronologia GPU FSR 3 sul thread di rendering, shader Frame Generation e AMD FSR 3 ottimizzati a <= 10 campioni/pixel (~10x più veloci), guardia software CPU contro i blocchi 1440p e rimozione della tempesta `RequestAnimationFrame`.
- **Correzioni di controlli e impostazioni Windows:** corretta la rimappatura da tastiera (`Focusable = true`), cattura del puntatore su slider/caselle/gamepad virtuale, testo a capo nelle caselle, applicazione immediata dei filtri video e profili di ottimizzazione, pulsanti Link Cable asincroni nella pausa, aggiornamento immediato al caricamento/riavvolgimento, localizzazione di tutte le 7 voci di navigazione e cambio dinamico del changelog tra `STORM GAMES WORLD` e `GitHub`.
- **Affidabilità Core & Android:** risolti crash, deadlock e bug dell'interfaccia nel core nativo e su Android.

## Português
- **Estabilidade crítica da interface Windows e modais:** corrigido o bloqueio de cliques em `AuroraModalHost.Dismiss()`, eliminadas exceções entre threads durante a verificação inicial da biblioteca e atualizações, e corrigidos os menus ComboBox preservando a posição de rolagem nas Configurações.
- **Desempenho Skia/SkSL e proteção de CPU:** adicionada sincronização thread-safe e descarte adiado de histórico GPU do FSR 3 na thread de renderização, shaders Frame Generation e AMD FSR 3 otimizados para <= 10 amostras/pixel (~10x mais rápidos), proteção para renderização por software evitar travamentos em 1440p e correção da tempestade de `RequestAnimationFrame`.
- **Correções de controles e configurações no Windows:** corrigida a remapeação de teclado (`Focusable = true`), captura de ponteiro em controles deslizantes/caixas de seleção/gamepad virtual, quebra de linha nas caixas de seleção, aplicação imediata de filtros de vídeo e perfis de otimização, botões Link Cable assíncronos no menu de pausa, atualização imediata ao carregar/retroceder, localização dos 7 itens de navegação e alternância dinâmica de notas de versão entre `STORM GAMES WORLD` e `GitHub`.
- **Confiabilidade do Core e Android:** corrigidos travamentos, deadlocks e falhas de interface no núcleo nativo e no Android.

## 日本語
- **Windows UI とモーダルの重大な安定性修正:** `AuroraModalHost.Dismiss()` によるクリック阻害を修正し、起動時スキャンや更新チェック時のクロススレッド例外を解消しました。また、設定画面の ComboBox ドロップダウン不具合を修正し、スクロール位置を保持するようにしました。
- **Skia/SkSL パフォーマンスと CPU 保護:** スレッドセーフな同期処理とレンダー スレッドでの FSR 3 GPU 履歴の遅延破棄を追加し、Frame Generation と AMD FSR 3 シェーダーを 1 ピクセルあたり 10 サンプル以下 (約 10 倍高速) に最適化しました。さらに 1440p でのソフトウェア CPU フリーズ保護と `RequestAnimationFrame` の多重発行防止を実装しました。
- **Windows コントロールと設定の修正:** キー再割り当て (`Focusable = true`)、スライダー/チェックボックス/仮想ゲームパッドのポインターキャプチャ、チェックボックスの折り返し表示、ビデオフィルターと最適化プロファイルの即時反映、ポーズ画面の非同期 Link Cable ボタン、ステート読込・巻き戻し時の即時フレーム更新、ナビゲーション 7 項目の完全多言語化、ミラー切替時 (`STORM GAMES WORLD` / `GitHub`) の変更履歴更新を修正しました。
- **コアと Android の信頼性:** ネイティブコアおよび Android クライアントにおけるクラッシュ、デッドロック、UI 不具合を修正しました。

## 简体中文
- **Windows 界面与模态框关键稳定性修复：** 修复了 `AuroraModalHost.Dismiss()` 导致的点击拦截问题，消除了启动扫描游戏库与检查更新时的跨线程异常，并修复了设置页 ComboBox 下拉框异常且保留滚动条位置。
- **Skia/SkSL 性能与 CPU 防卡死保护：** 新增线程安全同步与渲染线程延迟释放 FSR 3 GPU 历史帧机制，将 Frame Generation 与 AMD FSR 3 着色器优化至每像素 <= 10 次采样 (提速约 10 倍)，加入纯软件 CPU 渲染防 1440p 卡死保护，并消除了 `RequestAnimationFrame` 回调风暴。
- **Windows 控件与设置全面修复：** 修复键盘按键重映射 (`Focusable = true`)、滑块/复选框/虚拟手柄指针捕获、复选框文本换行、视频滤镜与优化配置实时生效、暂停菜单异步 Link Cable 按钮、读档与倒带即时刷新画面、侧边导航 7 项全语言本地化，以及切换 `STORM GAMES WORLD` / `GitHub` 来源时同步更新日志。
- **核心与 Android 可靠性：** 修复原生核心与 Android 端的多项崩溃、死锁和界面交互问题。

## 한국어
- **Windows UI 및 모달 안정성 핵심 수정:** `AuroraModalHost.Dismiss()`의 클릭 차단 버그를 수정하고, 시작 시 라이브러리 스캔 및 업데이트 확인 중 발생하던 크로스 스레드 예외를 제거했으며, 설정 화면의 ComboBox 드롭다운 오류 수정 및 스크롤 위치 유지를 구현했습니다.
- **Skia/SkSL 성능 및 CPU 프리징 방지:** 스레드 안전 동기화와 렌더 스레드에서의 FSR 3 GPU 히스토리 지연 해제를 추가하고, Frame Generation 및 AMD FSR 3 셰이더를 픽셀당 10샘플 이하(약 10배 고속화)로 최적화했으며, 1440p 소프트웨어 CPU 정지 방지 가드와 `RequestAnimationFrame` 폭주를 차단했습니다.
- **Windows 컨트롤 및 설정 수정:** 키보드 재할당(`Focusable = true`), 슬라이더/체크박스/가상 게임패드 포인터 캡처, 체크박스 줄바꿈, 비디오 필터 및 최적화 프로필 즉시 적용, 일시정지 메뉴 비동기 Link Cable 버튼, 슬롯 로드 및 되감기 시 즉시 화면 갱신, 내비게이션 7개 항목 다국어 지원, `STORM GAMES WORLD` / `GitHub` 소스 전환 시 변경 내역 갱신을 수정했습니다.
- **코어 및 Android 신뢰성:** 네이티브 코어와 Android 클라이언트의 충돌, 데드락 및 UI 문제를 해결했습니다.

## हिन्दी
- **Windows UI और मोडल स्थिरता में महत्वपूर्ण सुधार:** `AuroraModalHost.Dismiss()` में क्लिक अवरोध बग ठीक किया गया, स्टार्टअप लाइब्रेरी स्कैन और अपडेट जांच के दौरान क्रॉस-थ्रेड अपवाद समाप्त किए गए, और सेटिंग्स में स्क्रॉल स्थिति सुरक्षित रखते हुए ComboBox ड्रॉपडाउन को स्थिर किया गया।
- **Skia/SkSL प्रदर्शन और CPU फ्रीज सुरक्षा:** थ्रेड-सुरक्षित सिंक्रनाइज़ेशन और रेंडर थ्रेड पर FSR 3 GPU हिस्ट्री का विलंबित निस्तारण जोड़ा गया, Frame Generation और AMD FSR 3 शेडर को <= 10 सैंपल/पिक्सेल (~10x तेज़) तक अनुकूलित किया गया, 1440p सॉफ्टवेयर CPU रुकावट सुरक्षा जोड़ी गई और `RequestAnimationFrame` की अधिकता रोकी गई।
- **Windows नियंत्रण और सेटिंग्स सुधार:** कीबोर्ड रीबाइंडिंग (`Focusable = true`), स्लाइडर/चेकबॉक्स/वर्चुअल गेमपैड पर पॉइंटर कैप्चर, चेकबॉक्स टेक्स्ट रैपिंग, वीडियो फ़िल्टर और ऑप्टिमाइज़ेशन प्रोफ़ाइल का तुरंत लागू होना, पॉज़ मेनू में एसिंक Link Cable बटन, सेव स्लॉट लोड और रिवाइंड पर तुरंत फ्रेम रिफ्रेश, सभी 7 नेविगेशन आइटम का अनुवाद और `STORM GAMES WORLD` / `GitHub` स्विच करने पर रिलीज़ नोट्स अपडेट ठीक किए गए।
- **कोर और Android विश्वसनीयता:** नेटिव कोर और Android क्लाइंट में क्रैश, डेडलॉक और UI समस्याएं हल की गईं।
