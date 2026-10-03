# Fast Gallery 1.4.14

Native Android gallery written in Kotlin and Jetpack Compose (Android 8+, API 26+).

## Features

- Photos and videos with albums, date headers, animated GIF decoding, and device-provided RAW previews.
- In-app video playback, photo zoom, swipe up/down to close the viewer, slideshow, photo details and available EXIF metadata.
- Search, media-type filters (photos, videos, GIF and RAW), sort by date/name/size, live pinch-to-zoom grid columns, and a draggable fast scroller with date bubble.
- Long-press multi-select with drag-to-select (ungli ghumake ek saath kai items) and bulk share, favorites and trash actions.
- Rename, copy to another album/folder, or move (copy followed by Android's delete approval).
- Trash with restore (30-day auto-delete), favorite collection, album hide, and device-authenticated album lock.
- Full-screen edit screen with live preview: rotate 90°, drag-to-crop (free, 1:1, 4:3, 3:4, 16:9 with movable corners/edges) and Original/Mono/Warm/Cool filters. Edits are saved as a new JPEG; the source is preserved.
- Set an image as wallpaper and choose System/Light/Dark appearance.

## Important behavior

- Android asks for confirmation before deleting or editing media when required by its storage permissions.
- Trash: Android 11+ uses the real system trash (`MediaStore.createTrashRequest`); files are removed from other apps too and Android deletes them after 30 days. Android 8-10 has no system trash, so Fast Gallery keeps an app-level flag and permanently deletes items after 30 days (best effort).
- Hide removes an album's items from normal gallery views. Lock gates opening the album with device authentication; it does not encrypt the files or hide them from other apps.
- RAW display depends on Android's media provider having a preview for that camera format. Editing RAW files is not supported; edits create JPEG copies.
- Edit actions are non-destructive and create new files under `Pictures/FastGallery/Edited/`.
- To limit peak memory, edit copies are decoded at up to 8 MP on regular devices and 4 MP on low-RAM devices; the original remains unchanged.

## Build

Open the `FastGallery` directory in Android Studio or use Gradle 8.9 with JDK 17:

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