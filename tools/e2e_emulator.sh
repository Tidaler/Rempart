#!/usr/bin/env bash
# Tests de bout en bout sur l'émulateur (ou un téléphone de TEST branché).
# Usage : bash tools/e2e_emulator.sh [serial]
# Prépare l'appareil (consentement VPN, accessibilité, administrateur, Chrome sans
# écran d'accueil), lance les tests d'instrumentation, puis vérifie depuis le PC
# que la désinstallation est refusée tant que l'administrateur est actif.
set -euo pipefail
cd "$(dirname "$0")/.."

ADB="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}/platform-tools/adb"
[ -n "${1:-}" ] && ADB="$ADB -s $1"
PKG=org.tidalex.rempart
A11Y="$PKG/org.tidalex.rempart.filter.accessibility.RempartAccessibilityService"
ADMIN="$PKG/org.tidalex.rempart.lock.RempartAdminReceiver"

./gradlew -q :app:installDebug :app:installDebugAndroidTest

# 1. Consentement VPN sans boîte de dialogue (appareil de test uniquement).
$ADB shell appops set $PKG ACTIVATE_VPN allow
# 2. Service d'accessibilité.
$ADB shell settings put secure enabled_accessibility_services "$A11Y"
$ADB shell settings put secure accessibility_enabled 1
# 3. Administrateur de l'appareil.
$ADB shell dpm set-active-admin "$ADMIN" || true
# 4. Notifications.
$ADB shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
# 5. Chrome sans écran de bienvenue.
$ADB shell am set-debug-app --persistent com.android.chrome || true
$ADB shell 'echo "_ --disable-fre --no-default-browser-check --no-first-run" > /data/local/tmp/chrome-command-line' || true

# Tests d'instrumentation.
./gradlew :app:connectedDebugAndroidTest

# Sécurité : la désinstallation doit échouer tant que l'administrateur est actif.
if $ADB uninstall $PKG 2>&1 | grep -q Success; then
  echo "ÉCHEC : Rempart a pu être désinstallé alors que l'administrateur était actif" >&2
  exit 1
fi
echo "OK : désinstallation refusée par Android (administrateur actif)"

# Mesure batterie / CPU indicative après les tests.
$ADB shell dumpsys batterystats --charged $PKG | grep -iE "cpu:|wake" | head -5 || true
