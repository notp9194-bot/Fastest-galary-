# Fast Gallery 1.4.40

Native Android gallery written in Kotlin and Jetpack Compose (Android 8+, API 26+).

## Features

- Photos and videos with albums, date headers, animated GIF decoding, and device-provided RAW previews.
- In-app video playback, photo zoom, swipe up/down to close the viewer, slideshow, photo details and available EXIF metadata.
- Search, media-type filters (photos, videos, GIF and RAW), sort by date/name/size, live pinch-to-zoom grid columns, and a draggable fast scroller with date bubble.
- Long-press multi-select with drag-to-select (ungli ghumake ek saath kai items) and bulk share, favorites and trash actions.
- Rename, copy to another album/folder, or move (copy followed by Android's delete approval).
- Trash with restore (30-day auto-delete), favorite collection, album hide, and device-authenticated album lock.
- Full-screen edit screen with live preview: rotate 90°, flip horizontal/vertical, straighten (±45°), drag-to-crop (free, 1:1, 4:3, 3:4, 16:9 with movable corners/edges), brightness/contrast/saturation sliders, 12 filters (Original, Mono, Warm, Cool, Vivid, Dramatic, Fade, Vintage, Sepia, Noir, Sunset, Forest), undo/reset and hold-to-compare (before/after). Edits are saved as a new JPEG; the source is preserved.
- Set an image as wallpaper and choose System/Light/Dark appearance.

## Important behavior

- Android asks for confirmation before deleting or editing media when required by its storage permissions.
- Trash: Android 11+ uses the real system trash (`MediaStore.createTrashRequest`); files are removed from other apps too and Android deletes them after 30 days. Android 8-10 has no system trash, so Fast Gallery keeps an app-level flag and permanently deletes items after 30 days (best effort).
- Hide removes an album's items from normal gallery views. Lock gates opening the album with device authentication; it does not encrypt the files or hide them from other apps.
- RAW display depends on Android's media provider having a preview for that camera format. Editing RAW files is not supported; edits create JPEG copies.
- Edit actions are non-destructive and create new files under `Pictures/FastGallery/Edited/`.
- To limit peak memory, edit copies are decoded at up to 8 MP on regular devices and 4 MP on low-RAM devices; the original remains unchanged.

## Build

Open the `FastGallery` directory in Android Studio or use Gradle 8.13 with JDK 17:

```sh
gradle assembleDebug
```

GitHub Actions builds debug and release APK artifacts (plus a release AAB for Play Store) when pushed to the configured branches/tags.

## Release signing (Play Store)

Release build apni keystore se sign hota hai. Keystore na mile to local testing ke liye debug key use hoti hai (Gradle warning deta hai) - aisi APK/AAB Play Store pe upload mat karo.

1. Keystore banao (ek baar; isse safe jagah backup karo - kho gayi to app update nahi kar paoge):
   ```sh
   keytool -genkeypair -v -keystore release.keystore -alias fastgallery \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Local: `keystore.properties.example` ko `keystore.properties` me copy karke values bharo, phir `gradle assembleRelease` ya `gradle bundleRelease` (Play Store ke liye AAB). `keystore.properties` aur `*.keystore` git me ignore hain.
3. GitHub Actions: repo Secrets me `ANDROID_KEYSTORE_BASE64` (`base64 -w0 release.keystore`), `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` daalo. `v*` tag build bina keystore secret ke fail hota hai.
4. Note: debug key se pehle install ki hui APK ke upar apni keystore wali APK install nahi hogi (signature alag) - uninstall karke install karo.

## Play Store release files (1.4.30 ke baad)

- `docs/privacy-policy.html` (+ `play-store/PRIVACY_POLICY.md`): privacy policy. GitHub Pages se `/docs` host karo. `[DEVELOPER NAME]` aur `[CONTACT EMAIL]` bharna baaki hai.
- `play-store/permissions-declaration.md`: photo/video permissions declaration ke draft jawab. `data-safety.md`: Data safety form. `listing.md`: store listing text. `RELEASE_CHECKLIST.md`: poori checklist.
- `scripts/make-keystore.sh`: release keystore + `keystore.properties` banata hai (khud chalao, keystore kisi ko mat bhejo).

## Updates in 1.4.40

- **Compose BOM upgrade (P3 #3)**: `2024.09.03` -> `2026.06.01` (Compose 1.11.x, material3 1.4.0). Latest BOM `2026.09.00` (Compose 1.12) jaan-boojh ke nahi liya: Compose 1.12 ke liye compileSdk 37 + AGP 9 chahiye (abhi AGP 8.11.1 / compileSdk 36). Saath me `material-icons-core` seedha dependency me (BOM se version), taaki `Icons.Default.*` material3 ki transitive dependency par na tike.
  - **Build yahan nahi chali.** Pehli build me dhyan do: agar Gradle "incompatible version of Kotlin / metadata version" error de to root `build.gradle.kts` me `org.jetbrains.kotlin.android` aur `org.jetbrains.kotlin.plugin.compose` dono ko `2.2.20` (same version) kar do. Material3 1.3 -> 1.4 me kuch visual/behavior farak ho sakte hain (dialogs, sheets, chips): ek baar screens haath se dekh lo.
- **Baseline Profile (P3 #4)**: asli profile generate NAHI hua, kyunki ye device/emulator maangta hai jo yahan nahi tha. `baseline-prof.txt` hand-curated hi hai (bas `ScrollPlaceholder` class add ki, jo pehle scroll par chalti hai). Device par ye chalao, phir `app/src/main/generated/baselineProfiles/` commit karke `baseline-prof.txt` delete kar do:
  ```sh
  ./gradlew :app:generateBaselineProfile
  ```
  Device me kam se kam kuch photos aur 1 video hona chahiye (generator inhi par chalta hai).
- **Thumbnail loading tune (P3 #5)**:
  - `ThumbFetcher`: `loadThumbnail` par `Semaphore(MAX_PARALLEL_THUMB_LOADS = 8)`. Dhyan do: code me pehle koi limit nahi thi (Coil ka default IO dispatcher 64 threads tak jaata hai), to ye limit lagana hai, "6 se 8" karna nahi. Maksad: fast scroll me MediaProvider ke Binder pool ko na bharna.
  - `ThumbFetcher`: request cancel hone par (cell scroll se nikal gaya) `CancellationSignal` se chalta hua `loadThumbnail` bhi rukta hai, Binder thread jaldi khali.
  - Slow scroll prefetch: `MediaGrid` scroll ki disha me agli `PREFETCH_ROWS = 2` rows ke thumbnails memory-cache me pehle se enqueue karta hai (API 29+). Fast scroll shuru hote hi ya naya position aate hi pichhle prefetch cancel. `thumbImageRequest()` display aur prefetch dono share karte hain, taaki cache key same rahe.
  - Teeno sirf naap ke rakho: `ScrollBenchmarks` (`:baselineprofile`, `FrameTimingMetric`) se pehle purane build ka number lo, phir naya. Fayda na dikhe to `MAX_PARALLEL_THUMB_LOADS` bada kar do (jaise 64) aur `PREFETCH_ROWS = 0` kar do = pehle jaisa.
- **Grid recompose kam (P3)**: (1) `Thumb(deferLoad)` ab `() -> Boolean` hai; fast-scroll State composition me nahi padha jata, pehle har fling shuru/band par saare visible cells recompose hote the. Ab sirf abhi tak grey cell `snapshotFlow` se intezaar karta hai. Behavior wahi: ek baar load hua cell wapas grey nahi hota. (2) Cell ke `onClick` me `selected` Set ki jagah `currentSelected` (`rememberUpdatedState`): pehle har selection badlav (drag-select ke har step) par saare visible cells ki lambda badalti thi aur wo recompose hote the.
- **`formatDuration` sasta (P3)**: `String.format` (har call par Formatter allocate) hata ke `StringBuilder`; video cell me `remember(item.durationMs)` taaki recompose par dobara na bane. Output wahi (`m:ss`, `h:mm:ss`), test `FormatDurationTest`. Farak: ab hamesha ASCII digits (pehle `String.format` locale ke digits deta tha).
- **Badge allocations (P3)**: video/GIF/RAW badge ka `RoundedCornerShape(6.dp)` aur scrim color top-level constants (`BadgeShape`, `BadgeScrim`): pehle har badge-wale cell ke har recompose par naya shape banta tha. `stringResource(typeBadge)` aur `semantics {}` lambda jaan-boojh ke nahi chheda (resource lookup sasta hai, semantics lambda cell-specific hai).
- **`MediaItem.key` stored (P3)**: getter `uri.toString()` ki jagah ek baar bana `val`. Grid key lambda, `selected`/`favoriteKeys` lookups aur viewer isse bahut baar padhte hain. Equality/hashCode par asar nahi (body property).
- Version: `versionName` 1.4.40 / `versionCode` 46.

Build/tests yahan nahi chale (network aur device nahi tha). Release/profileable build par Macrobenchmark se hi asli numbers milenge.

## Updates in 1.4.39

- **System trash query ka wait hata (load)**: pehle `load()` pehle page ke baad `queryTrashed()` (5000 rows tak) sequentially chalata tha aur dono ke baad hi state publish hoti thi, yaani cache ke baad `fromCache=false` (tap allow) bhi trash query tak rukta tha. Ab:
  - `queryTrashed()` `async` me pehle page ke saath parallel chalti hai (ek `coroutineScope`, cancel dono ko cancel karta hai).
  - Page aate hi state publish (tap/open allow, cache save). Trash baad me `_state.update { trashItems, trashLoaded = true }`.
  - `GalleryState.trashLoaded`: jab tak false, Trash tab skeleton dikhata hai (galat "Trash khali" flash nahi). Refresh me true hi rehta hai. Query fail par bhi true.
- **`buildEntries` main thread se hata (grid data)**: `MediaGrid` composition me `buildEntries` + day groups (har item ka key) + header indices banata tha; 6000+ library par sort/filter/loadAll pe ek frame drop. Ab ye sab `buildGridModel(items)` (`data/Models.kt`, `GridModel`) me `GalleryViewModel.displayItems` ke `Dispatchers.Default` block me banta hai; `GalleryDisplayResult.model` me aata hai, `MediaGrid(model = ...)` bas use karta hai. `contentVersion` param hat gaya (model hi naya object hai). `buildEntries` wahi hai (tests/doosre users).
- Tests: `GridModelTest` (empty, entries == buildEntries, header indices, day groups).
- Version: `versionName` 1.4.39 / `versionCode` 45.

Build/tests yahan nahi chale. Naapo: (1) cold start me cache dikhne ke baad kitni jaldi tap chalta hai (pehle trash query tak rukta tha); (2) bade library (3000+) par sort badalte waqt frame time (Macrobenchmark `FrameTimingMetric`).

## Updates in 1.4.38

- **Tab switch par scroll position (P4, UX fix)**: pehle tab badalte hi `MediaGrid` dispose hota tha aur uska `LazyGridState` kho jaata tha, wapas aane par grid top se shuru hota tha. Ab `GalleryContent` (`MainActivity.kt`) me state hoist hai:
  - Alag state: Photos, Favorites, Trash (`rememberLazyGridState`, rotation/recreate me bachta hai) aur album ka (`rememberSaveable(albumId)`: album badalne par naya, wahi album dobara kholne par top se).
  - `MediaGrid(gridState = ...)` ab state bahar se leta hai (default pehle jaisa). Andar ka `LaunchedEffect(resetKey) { scrollToItem(0) }` hata diya, warna wo har entry par position top pe kar deta.
  - Filter/sort/search badalne par top pe jaana ab central hai: `LaunchedEffect(sort, filter, search)` sab 4 states ko top pe karta hai (sort Settings tab se bhi badalta hai jahan grid composed hi nahi hota). Pehli composition me skip, taaki restore na tute.
  - Tab-swipe code (`TabSwipe.kt`) ko haath nahi lagaya.
  - Limit: Albums list (`AlbumsGrid`) aur album se wapas Albums list par aane par uski position abhi bhi restore nahi hoti (alag grid hai, scope se bahar rakha).
- Version: `versionName` 1.4.38 / `versionCode` 44.

Build/tests yahan nahi chale. Ye speed nahi UX fix hai, to Macrobenchmark se fayda naapne ki zarurat nahi; bas haath se dekho: Photos me neeche scroll -> Favorites -> wapas Photos (position wahin), phir sort/filter badlo (top par jaana chahiye).

## Updates in 1.4.37

- **Thumbnail disk cache (P3)**: verify kiya (code se, device par nahi): 192 MB (low-RAM par 96 MB) Coil disk cache `cache/thumbs` kabhi bharta hi nahi tha. Coil 2.7 me disk cache sirf network fetcher (`HttpUriFetcher`) use karta hai. Grid ke thumbnails `ThumbFetcher` (`ContentResolver.loadThumbnail`, jiska MediaProvider ka apna cache hai) ya local content:// decode se aate hain, jo disk cache ko chhute nahi. `ThumbFetcher` ka `DataSource.DISK` sirf label hai.
  - Fix: `GalleryApp` me `.diskCache(null)` (null na do to Coil default disk cache khud bana leta hai). Purana khali `cache/thumbs` folder warmup thread me delete hota hai. Memory cache jaisa tha waisa.
  - Device par confirm karna ho (1.4.36 install par): `adb shell run-as com.fastgallery.app du -sh cache/thumbs` => ~0.
  - Cold start par visible cells pehle: LazyGrid sirf visible cells (+1 line) compose karta hai aur koi thumbnail prefetch nahi hota, upar se fast-scroll `deferLoad` (1.4.34) pehle se hai. Is liye alag code nahi badla.
  - Baseline profile: hand-curated `baseline-prof.txt` me `ui/Thumb**` (ThumbFetcher/Keyer), `GalleryViewModel**`, `FirstPageCache` pehle se covered. `BaselineProfileGenerator` me dusri cold start (kill + relaunch) joda, taaki cache se grid dikhne ka path bhi record ho. Asli profile device par `./gradlew :app:generateBaselineProfile` se dobara generate karke commit karo.
- Tests: `GalleryAppImageLoaderTest` (disk cache null, memory cache present).
- Version: `versionName` 1.4.37 / `versionCode` 43.

Build/tests yahan nahi chale.

## Updates in 1.4.36

- **Cold start: pehle page ka cache (P2)**: pehle 90 items ki snapshot (`data/FirstPageCache.kt`, `noBackupFilesDir/first_page.bin`, binary + version) disk par save hoti hai. Agli cold start par ye list turant dikhti hai (skeleton skip), aur MediaStore query background me chalti hai.
  - Cache me id, type (image/video), dateAdded, bucketId, duration, name, mime, size, w/h; `uri` id + type se dobara banti hai. `bucketId` zaroori hai: hidden/locked album ki photos cache se bhi nahi dikhti (prefs: hidden, locked, trash, favorites cache ke saath hi load hote hain).
  - `GalleryState.fromCache`: true tab tak jab tak asli query khatam na ho. Is dauran grid me tap/open, long-press selection, drag-select, day-select block hain (stale/delete hui photo na khule). Asli result aate hi cache wali list replace hoti hai, delete hui photos apne aap hat jaati hain.
  - Cache ke dauran `hasMore = true` rehta hai: albums/favorites/search/sort partial 90 items ko poori library samajh ke nahi dikhate (skeleton dikhta hai), `loadNextPage`/`loadAll` asli refresh ke baad hi chalte hain.
  - Race: cache sirf tab lagti hai jab asli load abhi kuch na laya ho (atomic `StateFlow.update`). Query fail ho to stale cache items hata di jaati hain.
  - Cache har successful refresh me update hoti hai (same list ho to disk write skip). Library khali ho to cache clear.
  - Splash: `state.loading` cache hit par kuch ms me false ho jaata hai, to 700 ms wait sirf tab lagta hai jab cache nahi hai (pehli install / cache miss). Cap 700 ms hi rakha; Macrobenchmark se dekh ke kam karna ho to `MainActivity.onCreate` me.
- Tests: `FirstPageCacheTest` (round trip, uri rebuild, limit, corrupt file, clear).
- Version: `versionName` 1.4.36 / `versionCode` 42.

Build/tests yahan nahi chale. Naapne ke liye: cold start (`StartupBenchmarks`) me pehli iteration cache-miss hoti hai (file abhi bani nahi), isliye 1-2 warm-up run ke baad time-to-first-thumbnail / `timeToInitialDisplay` compare karo (pehle 1.4.35, ab 1.4.36).

## Updates in 1.4.35

- **Grid cell ka kaam kam (P1)**: pehle har `Thumb` cell composition me naya `DateFormat` banata tha aur 4 `stringResource` (description, Selected, Favorite, Select) resolve karta tha, scroll me hazaron baar. Ab:
  - Ek shared `CachedDateFormatter` (`ui/ThumbDescription.kt`, `ThumbDateFormatter`): locale badalne tak ek hi `DateFormat` + ek hi `Date`. Main thread only.
  - TalkBack labels (date wala description, Selected/Favorite state, "Select" action) ab `semantics { }` ke andar lazy bante hain, yaani tabhi jab semantics ko zarurat ho (TalkBack on / test). Spoken text pehle jaisa hi hai ("Photo, 12 Mar 2025" + state). Image ka `contentDescription` null, label parent ke merged semantics me.
  - `thumbDateMillis()` pure function: dateTaken, warna dateAdded (sec -> ms).
- Note: `DateFormat` timezone bante waqt hi pakadta hai; app chalte me timezone badle to naya locale/app restart tak purana tz reh sakta hai (sirf TalkBack label me, UI me date header alag se banta hai).
- Tests: `ThumbDescriptionTest` (date fallback, ek hi formatter reuse, locale badalne par rebuild, Java MEDIUM format se match).
- Version: `versionName` 1.4.35 / `versionCode` 41.

Build/tests yahan nahi chale. Fayda naapna ho to Macrobenchmark / Profiler me grid scroll ka frame time pehle (1.4.34) aur ab (1.4.35) compare karo.

## Updates in 1.4.34

- **Fast scroll placeholder** (Google Photos jaisa): tez fling ya fast-scroller jump me jin cells ka thumbnail memory-cache me nahi hota, wo grey placeholder rehte hain aur decode shuru nahi hota. Scroll dheema/ruka hote hi (~80-160 ms) thumbnails load hote hain, halke fade-in (120 ms) ke saath. Memory-cache wale thumbnails fast scroll me bhi turant dikhte hain.
- Detector `ui/ScrollPlaceholder.kt` (`rememberFastScrolling`): scroll chalte waqt hi, har 80 ms par ek beech wale visible item ka pixel-offset badlav naapta hai (screen height / sec). Enter 2.5, exit 1.2 (hysteresis, flicker nahi). Item gayab = bada jump = fast. Constants `FAST_SCROLL_*` file ke top par.
- `Thumb(deferLoad = ...)`: ek baar load hua cell kabhi wapas grey nahi hota. Sirf Photos grid (`MediaGrid`) me laga hai; baaki jagah behavior pehle jaisa.
- Android 10 se neeche memory-cache key pata nahi hota, isliye wahan fast scroll me har naya cell grey rehta hai (decode wahan zyada mehnga hai, to fayda bhi zyada).
- Tests: `ScrollPlaceholderTest` (hysteresis).
- Version: `versionName` 1.4.34 / `versionCode` 40.

Build/tests yahan nahi chale. Device par check karna: thresholds (2.5 / 1.2) fling friction 0.015 ke hisaab se tune karne pad sakte hain.

## Updates in 1.4.33

- **Tab swipe ab bahut sensitive**: bilkul thoda sa left/right swipe karne par tab badal jata hai. Commit distance width ka **28% -> 10%** (max 56dp, yaani phone par ~40dp). Halka flick bhi chalta hai: minimum distance 24dp -> 12dp, minimum speed 700 -> 300 dp/s.
- Horizontal pehchan thodi narm: dx >= dy ka **1.4x** (pehle 1.6x). Vertical scroll / drag-select / pinch / slider / fast scroller ko pehle jaisa priority milti hai (jo child consume kare, swipe uska nahi hota), aur dono kinare (~20dp) back gesture ke liye khali hain.
- Naya feedback: drag karte waqt threshold paar hote hi halka haptic tick (matlab ab chhodne par tab badlega). Wapas kheench lo to cancel ho jata hai.
- Animation thodi tez: bahar slide 150 -> 120 ms, andar aana 260 -> 220 ms.
- Saari values `TabSwipe.kt` ke top par constants hain (`TAB_SWIPE_*`), kam/zyada karna ho to wahin badlo. `TabSwipeTest` naye thresholds ke hisaab se update.
- Version: `versionName` 1.4.33 / `versionCode` 39.

Build/tests yahan nahi chale; real device par check karna ki galti se tab switch to nahi hota (zyada sensitive hone ka trade-off).

## Updates in 1.4.32

- **Build fix (CI)**: `:app:compileDebugKotlin` fail (`Viewer.kt:650` "This foundation API is experimental"). `Modifier.transformable(canPan = ...)` `ExperimentalFoundationApi` hai, par `@OptIn` galti se `cachedAspect` par laga tha. Ab `@OptIn(ExperimentalFoundationApi::class)` `ViewerPage` par hai.
- Version: `versionName` 1.4.32 / `versionCode` 38.

Build yahan nahi chala; fix CI log ke error se pakda gaya hai.

## Updates in 1.4.31

- **Settings icons**: Appearance ab Palette icon (pehle Edit/pencil) aur Haptics ab Vibration icon (pehle CheckCircle) dikhata hai. Dono `ui/Components.kt` me `PaletteIcon` / `VibrationIcon` (Material paths, extended-icons dependency ke bina).
- **Drag-select upar ka auto-scroll**: pehle upar ka edge zone grid ke top se 80dp tha, jo top bar ki padding ke andar aa jata tha, isliye ungli ko status bar tak le jana padta tha. Ab zone `beforeContentPadding` ke BAAD se shuru hota hai (neeche ke liye `afterContentPadding` ke pehle tak), yaani dikhne wale content ke kinare par hi full speed milti hai. Speed ab bhi 0..1 ramp hai.
- **Editor filters**: 4 se **12**. Naye: Vivid, Dramatic, Fade, Vintage, Sepia, Noir, Sunset, Forest. Sab ek hi 4x5 colour matrix hain (`MediaOperations.filterMatrix`), isliye preview aur saved copy same dikhte hain, aur brightness/contrast/saturation sliders ke saath pehle jaisa combine hote hain. Ids ki list `MediaOperations.FILTER_IDS`. Labels `strings.xml` (en + hi) me. Filter chips row pehle se horizontally scroll hoti hai.
- Tests: `EditMathTest` me naye tests (har filter ka matrix valid, alpha neutral, Mono/Noir me rang nahi, Noir Mono se tez, Fade kaale uthata hai, Sepia warm).
- Version: `versionName` 1.4.31 / `versionCode` 37.

Build/tests yahan nahi chale (Gradle/SDK nahi). CI chalake dekhna; drag-select ka upar wala scroll real device par test karna.

## Updates in 1.4.30

- **Play Store target API**: Google Play ab naye apps/updates ke liye Android 16 (API 36) maangta hai. `compileSdk` aur `targetSdk` 35 -> **36** (app + baselineprofile module).
- Build tooling (API 36 ke liye zaroori): Android Gradle Plugin 8.7.0 -> **8.11.1** (API 36 supported, Gradle 8.13 chahiye), Gradle 8.9 -> **8.13** (CI workflow + README), `androidx.baselineprofile` plugin aur `benchmark-macro-junit4` 1.3.3 -> **1.4.1**. Kotlin 2.0.20 aur Compose BOM jaise ke taise (build warning aa sakti hai ki AGP Kotlin plugin se naya hai; error nahi).
- Android 16 behaviour changes check kiye: `onBackPressed` / `KEYCODE_BACK` use nahi hota (Compose `BackHandler` + `enableOnBackInvokedCallback`), screen orientation lock ya `resizeableActivity=false` nahi hai, edge-to-edge pehle se on (`enableEdgeToEdge`). Code change zaroori nahi mila, par real Android 16 device/emulator par test zaroori hai.
- Version: `versionName` 1.4.30 / `versionCode` 36.

Build yahan nahi chala (Gradle/SDK nahi). Tool versions docs se liye gaye hain; CI chalake dekhna.

## Updates in 1.4.29

- **Build fix (CI)**: `:app:compileDebugKotlin` fail ("The API of this layout is experimental"). `WindowInsets.navigationBarsIgnoringVisibility` / `statusBarsIgnoringVisibility` `ExperimentalLayoutApi` hain; `AlbumPicker.kt`, `VideoPlayer.kt`, `Viewer.kt` me file-level `@file:OptIn(ExperimentalLayoutApi::class)` lagaya.
- Version: `versionName` 1.4.29 / `versionCode` 35.

Gradle yahan nahi chala; fix CI log ke error se pakda gaya hai.

## Updates in 1.4.28

- **Build fix (CI)**: `:app:mergeDebugResources` fail ho raha tha ("Invalid unicode escape sequence in string"). Wajah: `values/strings.xml` ki 14 strings me bina escape wali apostrophe thi (`can't`, `Couldn't`, `wasn't`, ...: `err_not_found`, `err_permission`, `err_unsupported`, `msg_*_failed`, `msg_approval_denied`, `msg_album_auth_failed`, `msg_lock_remove_auth_failed`). Sab ko `\'` kar diya. Naye strings me apostrophe hamesha `\'` likho. `values-hi` me aisi koi string nahi thi.
- Version: `versionName` 1.4.28 / `versionCode` 34.

Build/tests yahan nahi chale (Gradle/SDK nahi); fix CI log ke error se pakda gaya hai, isliye CI dobara chalake dekhna.

## Updates in 1.4.27

- Tabs: **left/right swipe se tab switch** (`ui/TabSwipe.kt`, `TabSwipeContainer`). Photos, Albums, Favorites, Trash, Settings ke beech content ungli ke saath chalta hai (halka fade). Chhodne par thoda sa (width ka 10%, max 56dp; 1.4.33 se) ya halka flick ho to purana content bahar slide hota hai, naya opposite side se andar aata hai (halka haptic), warna spring se wapas. Pehle tab par daayein aur aakhri par bayein swipe me rubber-band resistance, tab nahi badalta.
- Gesture conflicts: swipe Main pass me child ke BAAD dekha jata hai, isliye drag-select, pinch zoom, Settings sliders, fast scroller aur vertical scroll pehle apna kaam karte hain. Swipe tabhi lagta hai jab chaal saaf horizontal ho (dx >= dy ka 1.6x) aur kisi ne consume na kiya ho. Screen ke dono kinare (~20dp) system back gesture ke liye chhode gaye hain. RTL me direction ulta.
- Swipe in sab me band hai: selection mode, search khula, album ke andar, Settings sub-page, viewer, picker mode, permission screen, bulk operation chalte waqt. Bottom bar/rail se tap karke tab badalna pehle jaisa (bina animation).
- Tab ka nishaan (nav indicator) slide-out ke baad badalta hai, jab tab asal me switch hota hai.
- Code: faisla `resolveTabSwipe` / `tabSwipeStep` me alag pure functions hain, naya test `TabSwipeTest`. `MainActivity` me `switchTab` ab nav bar/rail wale reset (`albumId`, `settingsPage`, `searchOpen`) ke saath wahi logic use karta hai swipe ke liye. Baseline profile me `TabSwipe` startup path me, generator me swipe journey.
- Version: `versionName` 1.4.27 / `versionCode` 33.

Build, unit tests aur device checks is update ke liye chalaye nahi gaye (is environment me Gradle/Android SDK nahi tha). Swipe ka feel (thresholds: 28%, 700dp/s flick, 150/260 ms) device par try karke tune karna.

## Updates in 1.4.26

- Baseline profile: `app/src/main/baseline-prof.txt` ab 270-byte ka "poora app hot" starter nahi, balki **hand-curated** profile hai. Startup path (GalleryApp, MainActivity, ViewModel, repository, preferences, grid, thumbnails, FastScroller, SelectionBar) `HSP` hai; baad me khulne wale Viewer, VideoPlayer, AlbumPicker, EditDialog, Pip, MediaOperations `HP` hain. Isse install par ART sirf kaam ke code ko compile karta hai.
- **Ye measured profile nahi hai.** Device/emulator (API 28+) par `./gradlew :app:generateBaselineProfile` chalao, `app/src/main/generated/baselineProfiles/` commit karo, phir `baseline-prof.txt` hata do.
- `BaselineProfileGenerator` theek kiya: sort button ab `By.desc("Sort and filter")` se milta hai (pehle `By.text("Sort")` kabhi match nahi hota tha, isliye filter wala path record hi nahi hota tha). Naye journeys: select mode (long-press + Select all), video open, All media wapas, Settings/Albums scroll, viewer pager swipe + double-tap zoom, editor tabs, Copy/move album picker, swipe-down close. Har step null-safe hai.
- Generate karte waqt gallery me kam se kam ek video aur kai photos hone chahiye.
- Version: `versionName` 1.4.26 / `versionCode` 32.

### Pehle ke changes jo README me likhe nahi the (ab code ke hisaab se documented)

- Copy/move: **album picker** (`ui/AlbumPicker.kt`). Folder ka naam type karne ki jagah bottom sheet me existing albums (cover, naam, item count). Sabse upar "New album" (naam type karke naya folder banta hai). "Delete original" switch on ho to copy ki jagah move hota hai; move me current album disabled rehta hai.
- Bulk operations: **progress card** (`ui/BulkProgress.kt`). Delete me "Deleting X of Y" bar, copy/move me bar (size pata na ho to indeterminate) aur Cancel. Chhote kaam me flash na ho isliye card 350 ms baad dikhta hai. Cancel flag background loop padhta hai, aur copy cancel hone par alag message aata hai.
- Grid badges: favorite ke liye chhota **heart** (thumbnail par), **GIF** aur **RAW** ka label video-duration wale corner me (video par dono nahi aate). TalkBack me favorite state bhi bola jata hai.
- Date header: header tap karne par us din ki **sab photos select / deselect** (selection mode me header par check circle dikhta hai).
- Settings: Default sort, slideshow speed, video autoplay, video muted default, haptic feedback on/off, Trash info (kitne din baad auto-delete) aur "Open Trash". Haptics off ho to poori app me vibration band (`HapticsGate`). Naye prefs `GalleryPreferences` me: `videoAutoplay`, `videoMuted`, `hapticsEnabled`, `slideshowDelayMs`.
- Errors: **friendly messages** (`ui/Feedback.kt`, `friendlyError`). Failure par raw `it.message` nahi dikhta; permission, file nahi mili, storage full, file bahut badi, format unsupported ya generic line aati hai (`err_*` strings, en + hi). Asli exception sirf Logcat me jaata hai. Toasts ki jagah ek hi snackbar host (MainActivity aur Viewer dono ke upar).

Build aur device run is update ke liye nahi kiye gaye (is environment me Gradle/Android SDK aur device nahi tha).

## Updates in 1.4.25

- Editor: **tabs**. Neeche Crop / Adjust / Filters tabs. Crop tab me rotate, flip, aspect chips aur straighten slider; Adjust tab me brightness, contrast, saturation sliders; Filters tab me purane 4 filters.
- Editor: **flip**. "Flip horizontal" / "Flip vertical" ek tap me dikhne wali image palat dete hain (toggle nahi, action). Crop box bhi saath me mirror hota hai. Flip rotate ke BAAD lagta hai; rotate karne par H/V flags swap hote hain taaki dikhne wali image na badle.
- Editor: **straighten** (-45° se +45°, 0.1° steps, 0 ke paas magnet). Image frame ke andar ghoomti hai aur itna zoom hoti hai ki kone khali na dikhen (`MediaOperations.straightenCoverScale`, preview aur saved copy dono me wahi). Crop straighten ke BAAD wale frame par hota hai.
- Editor: **brightness / contrast / saturation** sliders (-100..100, 0 ke paas magnet). Saved copy me order: saturation -> contrast -> brightness -> filter (`MediaOperations.colorMatrix`, preview ka ColorFilter bhi yahi matrix use karta hai, isliye jo dikhta hai wahi save hota hai).
- Editor: **undo / reset**. Upar undo icon (50 steps tak), tabs ke saath Reset (reset khud bhi undo ho sakta hai). Slider ya crop-box ka poora drag ek hi step hai.
- Editor: **before/after compare**. Preview ke neeche "Hold to compare" dabaye rakho to original image dikhti hai (crop box ke bina), chhodte hi edit wapas.
- Order (preview aur saved copy dono): EXIF -> rotate -> flip -> straighten -> crop -> colour sliders -> filter.
- Code: `EditDialog.kt` me `EditorState` (undo ka snapshot), `EditSlider`, `CompareButton`; `ImageEdit` me naye fields (`flipHorizontal`, `flipVertical`, `straightenDegrees`, `brightness`, `contrast`, `saturation`, sab ke defaults "koi badlav nahi"). Naye strings (en + hi): `edit_tab_*`, `edit_flip_*`, `edit_straighten`, `edit_brightness`, `edit_contrast`, `edit_saturation`, `edit_undo`, `edit_reset`, `edit_compare*`. Naya test: `EditMathTest`.
- Version: `versionName` 1.4.25 / `versionCode` 31.

Build, unit tests aur device checks is source update ke liye chalaye nahi gaye (is environment me Gradle/Android SDK nahi tha). Sirf colour-matrix aur straighten ka math alag se verify kiya gaya.

## Updates in 1.4.24

- Video: **double-tap seek**. Left third pe double-tap = -10 s, right third = +10 s, beech me = play/pause. Seek ke turant baad (0.7 s ke andar) ke single taps bhi seek karte rehte hain (chrome toggle nahi), aur HUD me jama seconds dikhte hain (+10 s, +20 s ...).
- Video: **long-press = 2x speed**. Chalte video me ungli rakhe rehne tak 2x (haptic + "2x speed" chip), chhodte hi pichhli speed. Ruke hue video me long-press kuch nahi karta. Photo me long-press pehle jaisa.
- Video: **brightness / volume swipe**. Left side me vertical swipe = brightness (sirf is window ki, system setting nahi chhedta, Viewer band hote hi wapas), right side me = volume (STREAM_MUSIC, system volume UI ke bina). Poori range ~70% screen-height ki swipe. HUD me icon + bar + %. Beech ka zone pehle jaisa: neeche = close, upar = details. Zone ki chaudai `SIDE_ZONE_FRACTION` (0.33) se badlo.
- Code: `VideoGestureHandler` (`ui/VideoPlayer.kt`) Viewer ke gestures aur VideoPlayer ke beech pul hai; `VideoBrightness` window brightness yaad/restore karta hai. Naye strings (en + hi): `video_seek_*_hud`, `video_hold_speed_hud`, `video_hud_*`.
- Version: `versionName` 1.4.24 / `versionCode` 30.

Build aur device checks is source update ke liye chalaye nahi gaye.

## Updates in 1.4.23

- Viewer: **photo khulte hi blank/blurry flash nahi**. Full-size decode aane tak neeche ek thumbnail placeholder dikhta hai (jis photo se viewer khula uski grid-size thumbnail memory cache se turant aati hai), phir full photo 160 ms crossfade se uske upar aati hai. Crossfade sirf viewer ki request me hai (`viewerImageRequest`); grid ka `ImageLoader` `crossfade(false)` hi hai. Memory-cache hit pe Coil crossfade khud skip karta hai. Page ka size pata hone se pehle full decode shuru nahi hota (pehle 512 px pe ek bekaar decode hota tha). "Open with" (`ViewActivity`, readOnly) me placeholder nahi (bahar ki URI ka MediaStore thumbnail nahi hota).
- Viewer: **swipe pe agli photo sharp aati hai**. Pager ruk jaane (`settledPage`) ke baad agli aur pichhli photo (video chhodke) full size me `imageLoader.enqueue` se memory cache me preload hoti hai, aur neighbour pages bhi ab current wale size pe decode hote hain (pehle 512 px). Display aur prefetch ki request same helper se banti hai (data/size/scale same) taaki cache key match kare. Low-RAM device pe preload band hai (purana behaviour: neighbours 512 px).
- Code: `viewerDecodeSize`, `viewerImageRequest`, `rememberViewerRequest` (`ui/Components.kt`); `lastGridThumbPx` hint (`ui/Screens.kt`) viewer ko grid ka thumbnail size batata hai.
- Version: `versionName` 1.4.23 / `versionCode` 29.

Build aur device checks is source update ke liye chalaye nahi gaye.

## Updates in 1.4.22

- Feature: **Proper SplashScreen**. `androidx.core:core-splashscreen` (Android 12+ system splash, purane me compat). Brand purple background + app icon, `MainActivity` theme `Theme.Gallery.Starting` (baad me `Theme.Gallery`). Splash pehla page aane tak rukti hai (max 700 ms), taaki cold start me skeleton ki jagah seedha photos dikhen. `ViewActivity` (Open with) me splash nahi.
- Version: `versionName` 1.4.22 / `versionCode` 28.

## Updates in 1.4.21

- Fix: Viewer me aakhri photo/video trash ya delete karne par viewer ab band ho jata hai. Pehle list khali hone par viewer gayab ho jata tha par `viewerIndex` >= 0 rehta tha, jis se bottom nav bar wapas nahi aata tha. Load ke dauran temporary khali list pe viewer band nahi hota.
- Version: `versionName` 1.4.21 / `versionCode` 27.

## Updates in 1.4.20

- Fix: **Viewer pinch zoom** ab ungliyon ke beech wale point se hota hai (pehle center se). Ungliyon ke neeche ka hissa apni jagah rehta hai, zoom-in/out dono me. Double-tap pehle jaisa. Centroid ek alag Initial-pass observer se pakda jata hai (kuch consume nahi karta), isliye pager swipe aur pan pe asar nahi.
- Version: `versionName` 1.4.20 / `versionCode` 26.

## Updates in 1.4.19

- Feature: **Video loop**. Video controls me Repeat button; on karne par video khatam hote hi dobara chalta hai. Setting yaad rehti hai (sab videos ke liye ek).
- Feature: **Last position yaad**. Video band karke ya app chhodke wapas aane par wahin se shuru (pehle 3 sec aur aakhri 3 sec me ho ya poora dekh liya ho to shuru se). Pichhle 100 videos yaad rehte hain.
- Feature: **Picture-in-Picture**. Controls me PiP button, aur chalte video me Home dabane par apne aap PiP window (API 31+ auto-enter, purane me `onUserLeaveHint`). PiP window me play/pause button hai. `MainActivity` aur `ViewActivity` dono me `supportsPictureInPicture` aur `configChanges` (screenSize|smallestScreenSize|screenLayout|orientation) lage hain, taaki PiP ya rotate par activity dobara na bane (Compose khud resize handle karta hai).
- Version: `versionName` 1.4.19 / `versionCode` 25.

## Updates in 1.4.18

- Feature: **Sticky date header**. Photos/Favorites/Trash/album grid me scroll karte waqt upar ek chhota date chip chipka rehta hai, taaki badi library me pata rahe kaunsi date ke photos hain. Asli header jab tak top pe dikh raha ho chip chhupa rehta hai. (`LazyVerticalGrid` me `stickyHeader` nahi hai, isliye overlay.)
- Version: `versionName` 1.4.18 / `versionCode` 24.

## Updates in 1.4.17

- Feature: **Sort/filter yaad rehta hai**. Photos grid ka sort (newest/oldest/name/size) aur filter (All/Photos/Videos/GIFs/RAW) ab `GalleryPreferences` me save hota hai, app band karke kholne par bhi wahi rehta hai. "Reset" dabane par default wapas save hota hai. Picker mode me filter save nahi hota (wo doosre app ki request se aata hai).
- Version: `versionName` 1.4.17 / `versionCode` 23.

## Updates in 1.4.16

- Feature: **Undo snackbar**. Photo/video trash karne ke baad "Undo" milta hai (grid aur viewer dono me). API 30+ pe wapas laane ke liye system approval dobara aata hai; API < 30 pe turant. Permanent delete undo nahi ho sakta (wo pehle se system confirmation maangta hai).
- Feature: **Grid animations**. Trash/favorite/filter se items aur date headers jhatke ki jagah smoothly khisakte hain (`Modifier.animateItem()`). Pinch-zoom ke dauran band rehta hai taaki columns badalte waqt jhatka na aaye.
- Version: `versionName` 1.4.16 / `versionCode` 22.

## Updates in 1.4.15

- Feature: **Open with**. File manager, chat app ya browser se photo/video "Fast Gallery se kholo" ho sakti hai (`ViewActivity`, `ACTION_VIEW` content/file URI). Halka alag viewer hai (library load nahi hoti): zoom, double-tap, video, details aur share chalte hain; bahar ki file par edit/trash/rename/delete/slideshow nahi (Viewer `readOnly`).
- Feature: **Picker**. Doosre apps (`GET_CONTENT` / `PICK`, image/* aur video/*) Fast Gallery ko photo chunne ke liye dikha sakte hain. Single pick me tap karte hi wapas; `EXTRA_ALLOW_MULTIPLE` ho to tap se select aur neeche "Done (n)". Picker me trash/delete actions nahi dikhte. Image-only request pe filter Photos (GIF/RAW ke liye filter menu), video-only pe Videos se shuru hota hai.
- Fix: Details me bahar ki file ki "Date added" 1970 nahi dikhti.
- Version: `versionName` 1.4.15 / `versionCode` 21.

## Updates in 1.4.14

- Feature: **Pull-to-refresh**. Photos / Favorites / Trash / album grid ke top pe neeche kheencho to library dobara load hoti hai (`vm.refresh()`), indicator top bar ke neeche dikhta hai. Auto-refresh (ContentObserver) pehle jaisa chalta rehta hai; ye sirf manual option hai. Material3 `PullToRefreshBox` (BOM 2024.09.03 / material3 1.3.0).
- Version: `versionName` 1.4.14 / `versionCode` 20.

## Updates in 1.4.13

- Viewer: **Double-tap zoom** ab smooth animation ke saath aur tap wali jagah par zoom hota hai (pehle center pe jhatke se). Dobara double-tap se smooth zoom-out.
- Viewer: **Swipe-up for details**. Photo/video ko upar kheencho to Details bottom sheet khulta hai (menu ke Details se bhi wahi). Neeche kheencho to viewer band hota hai. Chhodne par photo spring se wapas aati hai.
- Viewer: **Open/close transition**. Grid me tap ki hui thumbnail se photo expand hoti hai, aur back / close / swipe-down par wapas usi thumbnail me simat jaati hai (agar beech me swipe karke doosri photo par aa gaye to fade). Ye shared-element jaisa effect `ui/Viewer.kt` me custom hai (thumbnail ke window-bounds se), kisi experimental API ke bina.
- Viewer: Details me `"$label: $value"` ki jagah label aur value alag columns me (localization-friendly, item #10 bhi ho gaya).
- Version: `versionName` 1.4.13 / `versionCode` 19.

## Updates in 1.4.12

- Feature: **Hindi (हिन्दी) translation**. `values-hi/strings.xml` me saari 189 strings (placeholders `%1$d`/`%1$s` same). Phone ki language Hindi ho to app apne aap Hindi me khulta hai.
- Feature: **Per-app language** (Android 13+): `res/xml/locales_config.xml` (en, hi) + manifest `android:localeConfig`, to Settings > Apps > Fast Gallery > Language me app ki bhasha alag se chuni ja sakti hai. Nayi bhasha jodne ke liye `values-xx/strings.xml` banao aur `locales_config.xml` me line add karo.
- Note: viewer ke info rows ka `"$label: $value"` formatting abhi code me hai (item #10), baaki saari UI text resources se aati hai.
- Version: `versionName` 1.4.12 / `versionCode` 18.

## Updates in 1.4.11

- Feature: **Tablet / landscape**. Screen width >= 600dp pe neeche ke pill ki jagah side **Navigation Rail** (Photos, Albums, Favorites, Trash, Settings). Selection mode me neeche ka action bar pehle jaisa rehta hai.
- Grid columns ab screen width ke saath badhte hain (400dp reference; phone portrait pe koi badlav nahi). User ka chuna hua count (Settings / pinch) base rehta hai, e.g. 3 columns phone pe 3, 840dp tablet pe ~6. Max 16.
- Albums grid ab adaptive (min 160dp card), aur Settings / hidden-locked screens wide screen pe max 640dp me center hote hain.
- Version: `versionName` 1.4.11 / `versionCode` 17.

## Updates in 1.4.10

- Feature: **Haptics**. Long-press pe selection shuru hote hi vibration (1.4.8 se), drag-select me naye item par halka tick, pinch me columns badalne par tick (1.4.7 se), aur ab: selection mode me tap se select/deselect, Select all / Deselect all, aur viewer me Favorite toggle par bhi halka tick. Fast scroller ke bubble me bhi tick (1.4.7).
- Version: `versionName` 1.4.10 / `versionCode` 16.

## Updates in 1.4.9

- Feature: **Predictive back**. Manifest me `android:enableOnBackInvokedCallback="true"`. Android 13+ (dev option) / 14+ pe back gesture ke dauran system preview animation dikhta hai, aur Android 15+ pe app se home jaane ka animation bhi. Saare in-app back actions (viewer band, selection clear, album/settings se wapas) pehle se `BackHandler` pe hain, isliye unka behavior same rehta hai.
- Version: `versionName` 1.4.9 / `versionCode` 15.

## Updates in 1.4.8

- Feature: **Drag-to-select**. Kisi photo pe long-press karo aur ungli ghumao - beech ke sab items select ho jaate hain (wapas aao to shrink). Pehle se selected photo se shuru karo to deselect mode. Screen ke top/bottom kinare pe ungli le jao to grid khud scroll hota hai. Long-press pe aur naye item pe aane par haptic. TalkBack me "Select" action milta hai.
- Version: `versionName` 1.4.8 / `versionCode` 14.

## Updates in 1.4.7

- Feature: **Fast scroll + date scrubber** (`ui/FastScroller.kt`). Scroll karte hi handle aata hai, ruk ke ~1.5s baad chhup jaata hai. Handle pakad ke drag karo to grid turant jump karta hai aur bubble dikhta hai: Date sort me mahina-saal (e.g. "Sep 2026"), Name sort me pehla akshar, Size sort me file size. Drag shuru hote hi poori library load hoti hai (`vm.loadAll`), taaki badi library me handle poore range me chale. Bubble badalne par halka haptic tick.
- Feature: **Live pinch-to-zoom grid**. Pehle pinch sirf ek step me columns badalta tha; ab ungliyon ke saath grid smoothly scale hota hai, limit paar hone par columns 2-8 ke beech badalte hain (haptic tick ke saath) aur chhodne par spring se settle hota hai. Pinch ke dauran scroll/tap nahi chalte.
- Version: `versionName` 1.4.7 / `versionCode` 13.

## Updates in 1.4.6

- Fix (security): locked album ki photos/videos ab Photos, Favorites, Trash aur search me nahi dikhti. Locked album sirf authentication ke baad khulta hai, app background me jaane par dobara lock hota hai, aur Albums grid me locked album ka cover lock icon se badal gaya.
- Fix: Trash ab asli hai. Android 11+ pe system trash (30 din baad auto-delete); purane Android pe flag + 30 din baad auto-delete. Hidden/locked album ki cheezein Trash me nahi dikhti. Purane app-local trash items ko Trash tab me "system Trash me move karein?" dialog se migrate kar sakte ho.
- Version: `versionName` 1.4.6 / `versionCode` 12.
- UI: top bar me Search, Sort aur Back ab icons hain (accessibility labels ke saath).

- Security: `allowBackup=false` + `dataExtractionRules`/`fullBackupContent` (sab exclude) - hidden/locked albums, favorites aur trash ka prefs ab cloud backup ya device transfer me nahi jaata.
- UI: viewer ka bottom bar ab icons hai (Favorite, Edit, Trash/Restore) aur baaki actions (Rename, Copy/move, Wallpaper, Details, Delete permanently) "More" menu me. Slideshow ka text button play/pause icon bana. Videos me Edit/Wallpaper nahi dikhte.
- UI: multi-select me ab neeche action bar hai (Share, Favorite, Trash/Restore, Delete - icon + label) aur top bar me "Select all" icon (dobara dabane par deselect). Favorite ab smart hai: sab pehle se favorite hon to hata deta hai, warna baaki sab add karta hai (pehle har item ulta ho jaata tha). Count title me string resource se aata hai.
- Release: apni keystore se signing (`keystore.properties` / CI secrets), debug-key sirf fallback. CI ab release AAB bhi banata hai.

- UI: loading ab spinner ki jagah skeleton grid (halka pulse) dikhata hai. Empty screens (Photos, Albums, Favorites, Trash, search/filter no-result) me icon badge + title + hint hai; "Trash is empty" aur "No favorites yet" ab alag-alag guide karte hain.
- i18n: saare hardcoded UI strings (English + Hinglish mix) `res/values/strings.xml` me aa gaye: permission screen, settings, hidden/locked albums, sort/filter, viewer dialogs, edit dialog, details (EXIF) labels, toasts, biometric prompt, nav labels. Ab default sab English hai; Hindi ke liye `res/values-hi/strings.xml` add karna kaafi hai.

- UI: video ka apna player UI. Default ExoPlayer controller hata diya; ab Compose controls hain: center play/pause, seek bar (current / total time ke saath, drag chhodne par seek), mute/unmute icon aur playback speed menu (0.5x - 2x). Controls viewer ke bars ke saath tap se dikhte/chhupte hain aur video chalte waqt 3 sec baad auto-hide. Dusre page pe jaane ya app background me jaane par video pause; chalte waqt screen on rehti hai. Saare labels `strings.xml` me.

- UI: Android 14+ pe jab user "Selected photos and videos" access deta hai, neeche ek dismissible banner dikhta hai ("Manage access" button system ka photo-access dialog dobara kholta hai - aur media chunne ya full access dene ke liye). Full access milne par banner khud gayab; settings se wapas aane par bhi state refresh hoti hai.

Build aur device checks is source update ke liye chalaye nahi gaye.

## Updates in 1.4.5

- Fix: filter / sort / search lagane par grid purana (ya khaali) result dikhata tha jab tak tab switch na karo. Wajah: `MediaGrid` ke entries `remember(itemsVersion)` pe cached the, jo sirf MediaStore load pe badalta tha. Ab `GalleryDisplayResult.version` (har derived result pe naya) key hai, aur query badalne par grid top pe scroll hota hai.
- Fix: `loadAll()` ab `loadingMore`/`hasMore` badalne par bhi dobara try hota hai, isliye scroll-paging ke dauran filter lagane par load atakta nahi.
- Baseline Profile: `:baselineprofile` module (generator + startup benchmark), `profileinstaller`, `<profileable>`, aur starter `app/src/main/baseline-prof.txt`.
  - Real profile generate karo (API 28+ device/emulator, gallery me media ke saath): `./gradlew :app:generateBaselineProfile`, phir `app/src/main/generated/baselineProfiles/` commit karo.
  - Compare: `./gradlew :baselineprofile:connectedBenchmarkAndroidTest` (StartupBenchmarks: profile ke bina vs saath).

- UI: loading ab spinner ki jagah skeleton grid (halka pulse) dikhata hai. Empty screens (Photos, Albums, Favorites, Trash, search/filter no-result) me icon badge + title + hint hai; "Trash is empty" aur "No favorites yet" ab alag-alag guide karte hain.
- i18n: saare hardcoded UI strings (English + Hinglish mix) `res/values/strings.xml` me aa gaye: permission screen, settings, hidden/locked albums, sort/filter, viewer dialogs, edit dialog, details (EXIF) labels, toasts, biometric prompt, nav labels. Ab default sab English hai; Hindi ke liye `res/values-hi/strings.xml` add karna kaafi hai.

Build aur device checks is source update ke liye chalaye nahi gaye.

## Startup / first-open speed updates in 1.4.4

- MediaStore query now starts in `MainActivity.onCreate`, before Compose's first frame, instead of waiting for `ON_RESUME`.
- First page is 90 items (was 250); later scroll pages are 300, and full loads for Albums/search/sort use 2000-item pages.
- Refresh keeps the already-loaded window size, so the grid no longer shrinks and jumps after a media change.
- Removed the 180 ms debounce on every tab/data change; it now applies only while typing in search.
- Grid and album thumbnails use `ContentResolver.loadThumbnail` (API 29+, system-cached thumbnails) through a custom Coil fetcher; API 26-28 keep sampled Coil decoding.
- Per-cell `BoxWithConstraints` was removed from the grid; the cell size is computed once for the whole grid.
- Theme/preferences file and the Coil ImageLoader are warmed up on a background thread in `Application.onCreate`.
- Display filtering skips the list copy when nothing is hidden or trashed.

Build and device performance checks have not been run for this source update.

## Performance updates in 1.4.3

- The Photos grid and viewer fetch MediaStore results in 250-item pages as the user approaches the end of the loaded list.
- Photos and videos are paged together by MediaStore date-added order; provider-side ordering keeps page boundaries consistent.
- Search, filters, custom sorting, Albums, Favorites, and Trash load the remaining pages before showing complete results.
- Search/filter/sort derivation runs on a background dispatcher, with a short debounce and cancellation of outdated work.
- MediaStore change notifications are debounced, and returning to the app no longer triggers a full refresh unless the cache is dirty.
- Grid date headers are rebuilt once per loaded page instead of being constructed independently in the ViewModel and grid.
- Grid and album thumbnails request a decode size based on their measured on-screen pixel dimensions.
- The viewer decodes the current photo at up to 2560 px and adjacent pages at thumbnail size; pager preloading is disabled.
- Coil cache budgets are reduced and adjusted for low-RAM devices.
- Edited images use sampled decoding, apply EXIF orientation before user edits, and recycle intermediate bitmaps on success or failure.
- The Photos tab uses Android's frame-synchronized fling physics with friction set to 0.007; other media grids keep the standard 0.015 setting.
- Bottom navigation uses a rounded, elevated surface, vector icons, themed selection colors, and a subtle selected-icon animation.
- Display results carry their tab/album query context so the previous tab's list is not rendered during a tab change.

Build and device performance checks have not been run for this source update.