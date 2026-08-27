#!/usr/bin/env bash
#
# Wraps a built APK in a password-protected AES-256 archive for sharing.
#
# What this does and does not protect
# -----------------------------------
# An APK cannot itself be password-locked: Android's package installer has to
# read the archive to install it, so a locked APK would simply be uninstallable.
# What this script protects is the *handover*. The recipient needs the password
# to extract the APK from the archive.
#
# Once they have extracted it, they hold an ordinary APK and can install, copy
# or forward it freely. So this controls who gets the first copy - it is not a
# licence check, and it does not survive the first person you trust.
#
# Usage:
#   tools/package-protected.sh [path/to.apk] [output.7z]
#
# The password is read interactively; it is never passed as an argument, since
# command lines are visible to other processes and land in shell history.

set -euo pipefail

APK="${1:-app/build/outputs/apk/debug/app-universal-debug.apk}"
OUT="${2:-BHAIYAAA-protected.7z}"

if [[ ! -f "$APK" ]]; then
  echo "error: no APK at '$APK'" >&2
  echo "build one first:  ./gradlew assembleDebug" >&2
  exit 1
fi

SEVENZ="$(command -v 7z || command -v 7za || true)"
if [[ -z "$SEVENZ" ]]; then
  echo "error: 7z not found. Install it with:  sudo apt install p7zip-full" >&2
  exit 1
fi

read -r -s -p "Password: " PASSWORD; echo
read -r -s -p "Confirm : " CONFIRM; echo
if [[ "$PASSWORD" != "$CONFIRM" ]]; then
  echo "error: passwords do not match" >&2
  exit 1
fi
if (( ${#PASSWORD} < 8 )); then
  echo "error: use at least 8 characters" >&2
  exit 1
fi

rm -f "$OUT"

# -mhe=on encrypts the archive header too, so the file names inside are not
# readable without the password either. Without it, anyone can see that the
# archive contains an APK and what it is called.
"$SEVENZ" a -t7z -mhe=on -mx=9 -p"$PASSWORD" "$OUT" "$APK" >/dev/null

# Prove the password actually opens it before handing it over.
if ! "$SEVENZ" t -p"$PASSWORD" "$OUT" >/dev/null 2>&1; then
  echo "error: archive failed verification" >&2
  rm -f "$OUT"
  exit 1
fi

# And prove a wrong password does not.
if "$SEVENZ" t -p"definitely-not-the-password-$$" "$OUT" >/dev/null 2>&1; then
  echo "error: archive opened without the password - refusing to ship it" >&2
  rm -f "$OUT"
  exit 1
fi

SIZE="$(du -h "$OUT" | cut -f1)"
echo
echo "Created $OUT ($SIZE), AES-256 with encrypted file names."
echo
echo "The recipient opens it with 7-Zip (Windows), Keka/The Unarchiver (Mac),"
echo "p7zip (Linux) or ZArchiver (Android), using the password you set."
echo
echo "Send the password through a different channel than the file."
