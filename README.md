# Fast Gallery 1.4.0

Native Android gallery written in Kotlin and Jetpack Compose (Android 8+, API 26+).

## Features

- Photos and videos with albums, date headers, animated GIF decoding, and device-provided RAW previews.
- In-app video playback, photo zoom, swipe up/down to close the viewer, slideshow, photo details and available EXIF metadata.
- Search, media-type filters (photos, videos, GIF and RAW), sort by date/name/size, grid column pinch zoom, and fast scroll handle.
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
- To limit peak memory, edit copies are decoded at up to 8 MP on regular devices and 4 MP on low-RAM devices; the original remains unchanged.

## Build

Open the `FastGallery` directory in Android Studio or use Gradle 8.9 with JDK 17:

```sh
gradle assembleDebug
```

GitHub Actions builds debug and release APK artifacts when pushed to the configured branches/tags.

## Performance updates in 1.4.0

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

Build and device performance checks have not been run for this source update.