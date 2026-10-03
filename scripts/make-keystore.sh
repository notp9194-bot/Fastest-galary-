#!/usr/bin/env bash
# Release (upload) keystore banata hai aur keystore.properties likhta hai.
# Chalane ke liye (project root se):  bash scripts/make-keystore.sh
#
# IMPORTANT:
#  - Keystore aur uske passwords kisi ko mat bhejo (chat, email, git). Khud banao, khud rakho.
#  - release.keystore ka backup 2 alag safe jagah rakho (password manager + offline).
#  - Play Console me "Play App Signing" on rakho: tab ye sirf UPLOAD key hai, kho jaye to Google reset kar sakta hai.
set -euo pipefail

command -v keytool >/dev/null || { echo "keytool nahi mila. JDK 17 install karo."; exit 1; }

KEYSTORE="release.keystore"
ALIAS="fastgallery"
[ -e "$KEYSTORE" ] && { echo "$KEYSTORE pehle se hai. Overwrite nahi kar raha."; exit 1; }
[ -e keystore.properties ] && { echo "keystore.properties pehle se hai. Overwrite nahi kar raha."; exit 1; }

read -r -s -p "Keystore password (kam se kam 8 chars): " STORE_PASS; echo
read -r -s -p "Dobara likho: " STORE_PASS2; echo
[ "$STORE_PASS" = "$STORE_PASS2" ] || { echo "Passwords match nahi hue."; exit 1; }
[ "${#STORE_PASS}" -ge 8 ] || { echo "Password bahut chhota."; exit 1; }
read -r -p "Tumhara naam (certificate ke liye): " CN

keytool -genkeypair -v -keystore "$KEYSTORE" -alias "$ALIAS" \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$STORE_PASS" -keypass "$STORE_PASS" \
  -dname "CN=${CN}"

umask 077
cat > keystore.properties <<PROPS
storeFile=${KEYSTORE}
storePassword=${STORE_PASS}
keyAlias=${ALIAS}
keyPassword=${STORE_PASS}
PROPS

echo
echo "Ho gaya: $KEYSTORE aur keystore.properties (dono .gitignore me hain)."
echo
echo "GitHub Actions ke liye repo Secrets me ye 4 daalo:"
echo "  ANDROID_KEYSTORE_BASE64   = $(printf '%s' "$(base64 -w0 "$KEYSTORE" 2>/dev/null || base64 "$KEYSTORE" | tr -d '\n')" | cut -c1-20)...  (poora: base64 -w0 $KEYSTORE)"
echo "  ANDROID_KEYSTORE_PASSWORD = (jo abhi daala)"
echo "  ANDROID_KEY_ALIAS         = $ALIAS"
echo "  ANDROID_KEY_PASSWORD      = (jo abhi daala)"
echo
echo "Local release AAB:  gradle bundleRelease   ->  app/build/outputs/bundle/release/"
