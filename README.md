# Fast Gallery 1.1.0

Native Android gallery written in Kotlin and Jetpack Compose (Android 8+, API 26+).

## Features

- Photos and videos with albums, date headers, animated GIF decoding, and device-provided RAW previews.
- In-app video playback, photo zoom, swipe up/down to close the viewer, slideshow, photo details and available EXIF metadata.
- Search, media-type filters (photos, videos, GIF and RAW), sort by date/name/size, grid column pinch zoom, fast scroll handle, and grid fling friction set to 0.007.
- Long-press multi-select with bulk share, favorites and trash actions.
- Rename, copy to another album/folder, or move (copy followed by Android's delete approval).
- Local Trash with restore, favorite collection, album hide, and device-authenticated album lock.
- Rotate, center crop (free/square/4:3) and Original/Mono/Warm/Cool filters. Edits are saved as a new JPEG; the source is preserved.
- Set an image as wallpaper and choose System/Light/Dark appearance.

## Important behavior

- Android asks for confirmation before deleting or editing media when required by its storage permissions.
- Trash is an app-local reversible list; the original media remains in shared storage until permanently deleted.
- Hide removes an album's items from normal gallery views. Lock gates opening the album with device authentication; it does not encrypt the files or hide them from other apps.
- RAW display depends on Android's media provider having a preview for that camera format. Editing RAW files is not supported; edits create JPEG copies.
- Edit actions are non-destructive and create new files under `Pictures/FastGallery/Edited/`.

## Build

Open the `FastGallery` directory in Android Studio or use Gradle 8.9 with JDK 17:

```sh
gradle assembleDebug
```

GitHub Actions builds debug and release APK artifacts when pushed to the configured branches/tags.