#!/usr/bin/env bash
# Changes the password of the release keystore WITHOUT changing the signing key, so users can still
# update over their installed copy. Passwords are read silently and never appear in argv or history.
#
#   tools/rotate-keystore-password.sh /path/to/mewze.jks [alias]
#
# Works in Git Bash on Windows too (use "D:/mewzekey/mewze.jks" style paths).
set -euo pipefail

KS="${1:?usage: $0 /path/to/keystore.jks [alias]}"
ALIAS="${2:-mewze}"

command -v keytool >/dev/null 2>&1 || {
  echo "keytool not found. Install JDK 17, or add Android Studio's <jbr>/bin to PATH." >&2; exit 1; }
[ -f "$KS" ] || { echo "Keystore not found: $KS" >&2; exit 1; }

cleanup() { unset OLD_PW OLD_KEY_PW NEW_PW NEW_PW2 2>/dev/null || true; }
trap cleanup EXIT

read -rsp "Current keystore password: " OLD_PW; echo
read -rsp "Current key password (Enter = same as keystore): " OLD_KEY_PW; echo
OLD_KEY_PW="${OLD_KEY_PW:-$OLD_PW}"
export OLD_PW OLD_KEY_PW

fingerprint() { # $1 = password env var name
  keytool -list -v -keystore "$KS" -alias "$ALIAS" -storepass:env "$1" 2>/dev/null \
    | grep -m1 -E '^[[:space:]]*SHA256:' | sed 's/^[[:space:]]*//'
}
store_type() {
  keytool -list -v -keystore "$KS" -storepass:env "$1" 2>/dev/null \
    | grep -m1 -i 'Keystore type' | sed 's/.*: *//'
}

FP_BEFORE="$(fingerprint OLD_PW || true)"
[ -n "$FP_BEFORE" ] || { echo "Could not open the keystore/alias with that password." >&2; exit 1; }
TYPE="$(store_type OLD_PW)"
echo "Keystore type : $TYPE"
echo "Key fingerprint (must stay identical): $FP_BEFORE"

read -rsp "NEW password (min 14 chars, use a password manager): " NEW_PW; echo
read -rsp "Repeat NEW password: " NEW_PW2; echo
[ "$NEW_PW" = "$NEW_PW2" ] || { echo "Passwords do not match." >&2; exit 1; }
[ "${#NEW_PW}" -ge 14 ]    || { echo "Too short (min 14 characters)." >&2; exit 1; }
[ "$NEW_PW" != "$OLD_PW" ] || { echo "New password must differ from the old one." >&2; exit 1; }
export NEW_PW

BACKUP="${KS}.bak-$(date +%Y%m%d-%H%M%S)"
cp -p "$KS" "$BACKUP"
echo "Backup written: $BACKUP"

keytool -storepasswd -keystore "$KS" -storepass:env OLD_PW -new:env NEW_PW
if [ "$(echo "$TYPE" | tr a-z A-Z)" != "PKCS12" ]; then
  # JKS keeps a separate key password; PKCS12 uses the store password for keys too.
  keytool -keypasswd -keystore "$KS" -alias "$ALIAS" -storepass:env NEW_PW \
          -keypass:env OLD_KEY_PW -new:env NEW_PW
fi

FP_AFTER="$(fingerprint NEW_PW || true)"
if [ "$FP_BEFORE" != "$FP_AFTER" ]; then
  echo "FINGERPRINT MISMATCH - restoring backup. Nothing was changed." >&2
  cp -p "$BACKUP" "$KS"; exit 1
fi

cat <<MSG

OK: password changed, signing key unchanged ($FP_AFTER).

Next steps (the keystore FILE changed too, so the GitHub secret must be refreshed):
  1. Update your local keystore.properties (storePassword / keyPassword).
  2. gh auth login   # once
     base64 -w0 "$KS" | gh secret set RELEASE_KEYSTORE_BASE64 -R caohaidang-work/mewze
     gh secret set RELEASE_KEYSTORE_PASSWORD -R caohaidang-work/mewze   # paste the NEW password
     gh secret set RELEASE_KEY_PASSWORD      -R caohaidang-work/mewze   # paste the NEW password
  3. Build a release locally and confirm it still installs over the previous version.
  4. Keep the backup ($BACKUP) in a safe place until step 3 is confirmed, then delete it.
     The backup still has the OLD (leaked) password, so do not leave it lying around.
MSG
