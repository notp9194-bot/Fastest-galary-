# Play Console: Photo and video permissions declaration (draft answers)

Play Console > App content > Photo and video permissions. Neeche ke jawab copy karke apne hisaab se adjust karo. Form ka exact wording Play Console me dekh lena, wo badalta rehta hai.

**Permissions jo manifest me hain:** `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` (+ `READ_MEDIA_VISUAL_USER_SELECTED`).

## Core use case

Photo/video **gallery and media manager** app. Photos aur videos ko browse, organise (albums, favorites, trash), dekhna, edit karna app ka main purpose hai.

## Why the Android Photo Picker is not sufficient

Photo Picker ek baar me kuch items chunne ke liye hai (one-time pick). Fast Gallery ko poori device library ka **persistent aur frequent** access chahiye, kyunki:

- Har app launch par poori library date-wise grid me dikhani hoti hai, aur naye photos/videos apne aap dikhne chahiye.
- Albums (folder-wise view), search, sort/filter, favorites, aur trash poori library par kaam karte hain.
- Bulk actions (select, share, copy/move, delete) library ke kisi bhi item par chalte hain.
- Edit karke naya photo usi library me save hota hai.

Picker se ye kuch bhi possible nahi: picker app ko library browse nahi karne deta, sirf user ke chune hue items deta hai.

## Graceful fallback

Android 14+ par agar user sirf "Selected photos" access de, to app chunne hue items dikhata hai aur "Manage access" ka banner deta hai (`READ_MEDIA_VISUAL_USER_SELECTED` handle hota hai). Permission na dene par app permission screen dikhata hai, crash nahi karta.

## Data handling

Photos/videos kisi server ko nahi bheje jaate. App me INTERNET permission hi nahi hai. Access sirf device par dikhane aur user ke kahe actions ke liye use hota hai.

## Store listing me core features likhe hone chahiye

Policy kehti hai ki core features app description me saaf likhe hon. `play-store/listing.md` ka description usi hisaab se likha hai; use waisa hi rakho.

## Review ke liye demo video (agar maange)

30 to 60 sec screen recording: permission prompt aana, grid scroll, album kholna, viewer, favorite/trash, edit karke save.

---

# Baaki permissions

| Permission | Declaration / justification |
|---|---|
| `SET_WALLPAPER` | Normal permission, form nahi. Sirf user ke "Set as wallpaper" action par. |
| `READ_EXTERNAL_STORAGE` (maxSdk 32), `WRITE_EXTERNAL_STORAGE` (maxSdk 28) | Sirf purane Android versions ke liye, Android 13+ par use nahi hote. |
| `USE_BIOMETRIC` / `USE_FINGERPRINT` | `androidx.biometric` library manifest me khud jodti hai. Sirf locked albums kholne ke liye. |
