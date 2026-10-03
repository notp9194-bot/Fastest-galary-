# Play Store release checklist

**Code / build**
- [ ] CI green (unit tests + release build)
- [ ] Release AAB real phone par test (Android 13 aur Android 16; "Selected photos" access ke saath bhi)
- [ ] `bash scripts/make-keystore.sh` se keystore banao, backup lo, GitHub Secrets daalo
- [ ] `v*` tag push karo, ya local `gradle bundleRelease`; AAB signed (debug key nahi) hona chahiye
- [ ] `versionCode` pichhli upload se zyada

**Play Console**
- [ ] Developer account (aur naye personal accounts ke liye Google ke closed-testing rules dekh lo)
- [ ] App create, Play App Signing on
- [ ] Privacy policy URL (`docs/privacy-policy.html` host karke; `[DEVELOPER NAME]` / `[CONTACT EMAIL]` bharo)
- [ ] Photo and video permissions declaration (`permissions-declaration.md`)
- [ ] Data safety (`data-safety.md`)
- [ ] Content rating, target audience, ads = No
- [ ] Store listing text + icon 512, feature graphic 1024x500, screenshots (`listing.md`)
- [ ] Internal testing track -> fir closed -> production
