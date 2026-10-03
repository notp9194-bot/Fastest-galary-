# Play Console: Data safety form (draft answers)

Play Console > App content > Data safety. App me INTERNET permission nahi hai, koi analytics/ads/crash SDK nahi hai, isliye:

| Sawal | Jawab |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Lagu nahi (kuch collect nahi hota) |
| Do you provide a way for users to request that their data is deleted? | Lagu nahi (data sirf device par hai; uninstall / clear storage se hat jata hai) |

Photos/videos ko app sirf device par process karta hai, server par nahi bhejta, isliye Google ki definition ke hisaab se "collected" nahi hai.

**Dobara check karo agar baad me ye jodte ho:** crash reporting (Firebase Crashlytics), analytics, ads, cloud backup/sync, login. Tab form aur privacy policy dono update karni padengi.

## Privacy policy URL

`docs/privacy-policy.html` ko GitHub Pages se host karo (Settings > Pages > Deploy from branch > `/docs`), URL aisa banega:
`https://<username>.github.io/<repo>/privacy-policy.html`
Ye URL Play Console me App content > Privacy policy me daalo. Uske pehle policy me `[DEVELOPER NAME]` aur `[CONTACT EMAIL]` bhar do.

## Baaki App content declarations

- **Ads:** No ads.
- **App access:** Koi login nahi, sab functionality bina special access ke.
- **Content rating:** Questionnaire me utility/gallery app, koi violence/user-generated sharing nahi (app khud kuch publish nahi karta).
- **Target audience:** 18+ ya general (children ke liye designed nahi).
- **Government / financial / health / news app:** No.
