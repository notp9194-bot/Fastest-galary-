# Fast Gallery

Native Android (Kotlin + Jetpack Compose) gallery app.

- Photos + videos grid (din ke hisaab se headers), Albums tab
- Full-screen viewer: swipe, pinch-zoom, double-tap zoom, share
- Coil thumbnails (memory + disk cache), video frame thumbnails
- Auto refresh jab naye photos aayein (ContentObserver)
- Android 8+ (minSdk 26), Android 13/14 media permissions supported

## Build
GitHub par push karo -> Actions tab -> "Android Build" -> Artifacts me APK.
Tag `v1.0` push karoge to APK Release me attach ho jayega.

Local: `gradle assembleDebug` (Gradle 8.9, JDK 17).
