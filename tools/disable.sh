#!/usr/bin/env bash
# Safe package disabler for Android TV.
# Refuses to disable core system packages that would brick the device.
#
# Usage: disable.sh <TV_HOST:TV_PORT> <package> [package ...]
# Or pipe a list: cat list.txt | disable.sh <TV_HOST:TV_PORT>
set -euo pipefail

ADDR="${1:?Usage: disable.sh HOST:PORT pkg [pkg ...]}"
shift

# Packages that must NEVER be disabled — disabling these can brick the TV
PROTECTED=(
    "android"
    "com.android.systemui"
    "com.android.shell"
    "com.android.phone"
    "com.android.settings"
    "com.android.tv.settings"
    "com.android.permissioncontroller"
    "com.google.android.permissioncontroller"
    "com.android.packageinstaller"
    "com.google.android.packageinstaller"
    "com.android.ext.services"
    "com.google.android.ext.services"
    "com.android.ext.shared"
    "com.google.android.ext.shared"
    "com.android.modulemetadata"
    "com.google.android.modulemetadata"
    "com.android.providers.settings"
    "com.android.providers.media"
    "com.android.providers.downloads"
    "com.android.providers.telephony"
    "com.android.inputdevices"
    "com.android.keychain"
    "com.android.location.fused"
    "com.android.se"
    "com.android.server.telecom"
    "com.android.vpndialogs"
    "com.android.certinstaller"
    "com.google.android.overlay.modules.permissioncontroller"
    "com.google.android.overlay.modules.ext.services"
)

# Pattern-based protection
PROTECTED_PATTERNS=(
    "com.google.android.overlay.modules"
    "com.android.providers."
)

is_protected() {
    local pkg="$1"
    for p in "${PROTECTED[@]}"; do
        [[ "$pkg" == "$p" ]] && return 0
    done
    for pat in "${PROTECTED_PATTERNS[@]}"; do
        [[ "$pkg" == "$pat"* ]] && return 0
    done
    return 1
}

# Check if current Home app
get_home() {
    local home
    home=$(adb -s "$ADDR" shell "dumpsys activity activities 2>/dev/null | grep -o 'mFocusedApp=.*' | head -1" 2>/dev/null || true)
    # Also try cmd package
    adb -s "$ADDR" shell "cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null" 2>/dev/null || true
}

packages=()
if [[ $# -gt 0 ]]; then
    packages=("$@")
else
    while IFS= read -r line; do
        line="${line%%#*}"  # strip comments
        line="${line// /}"  # strip spaces
        [[ -n "$line" ]] && packages+=("$line")
    done
fi

if [[ ${#packages[@]} -eq 0 ]]; then
    echo "No packages specified." >&2
    exit 1
fi

# Pre-check: refuse the whole list if any protected package is in it
blocked=()
for pkg in "${packages[@]}"; do
    if is_protected "$pkg"; then
        blocked+=("$pkg")
    fi
done

if [[ ${#blocked[@]} -gt 0 ]]; then
    echo "REFUSED: list contains protected system packages:" >&2
    for b in "${blocked[@]}"; do
        echo "  - $b" >&2
    done
    echo "These packages are required for the TV to boot. Refusing entire list." >&2
    exit 1
fi

# Disable one at a time
ok=0
fail=0
for pkg in "${packages[@]}"; do
    echo -n "Disabling $pkg ... "
    result=$(adb -s "$ADDR" shell "pm disable-user --user 0 '$pkg' 2>&1")
    if echo "$result" | grep -qi "new state: disabled"; then
        echo "OK"
        ((ok++))
    elif echo "$result" | grep -qi "securityexception\|not found\|error"; then
        echo "FAILED: $result"
        ((fail++))
    else
        # Verify with pm list packages -d
        if adb -s "$ADDR" shell "pm list packages -d" | grep -q "^package:${pkg}$"; then
            echo "OK (verified)"
            ((ok++))
        else
            echo "FAILED (not in disabled list): $result"
            ((fail++))
        fi
    fi
done

echo ""
echo "Done: $ok disabled, $fail failed out of ${#packages[@]}."
