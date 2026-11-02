#!/usr/bin/env bash
# Release readiness check: lint, unit tests, a release-build compile+R8 check (no APK/AAB kept),
# and a scan of the merged manifest for permissions Play Store policy forbids for this kind of
# app. Prints PASS or FAIL for each step and an overall PASS/FAIL at the end.
#
# Usage: ./scripts/verify_release.sh

set -u
cd "$(dirname "$0")/.."

export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"

overall=0
step() {
    local name="$1"
    shift
    echo "== $name =="
    if "$@"; then
        echo "PASS: $name"
    else
        echo "FAIL: $name"
        overall=1
    fi
    echo
}

run_lint() { ./gradlew lint; }
run_tests() { ./gradlew testDebugUnitTest; }
run_assemble_release() { ./gradlew assembleRelease; }

check_forbidden_permissions() {
    # The exact intermediates path moves between AGP versions, so search for it rather than
    # hardcoding one.
    local manifest
    manifest=$(find app/build/intermediates -path "*release*" -name "AndroidManifest.xml" 2>/dev/null | head -1)
    if [ -z "${manifest:-}" ] || [ ! -f "$manifest" ]; then
        echo "Could not locate the merged release manifest."
        return 1
    fi
    echo "Checking $manifest"
    local forbidden="BIND_NOTIFICATION_LISTENER_SERVICE BIND_ACCESSIBILITY_SERVICE READ_SMS RECEIVE_SMS SYSTEM_ALERT_WINDOW MANAGE_EXTERNAL_STORAGE QUERY_ALL_PACKAGES"
    local found=0
    for perm in $forbidden; do
        if grep -q "$perm" "$manifest"; then
            echo "Forbidden permission present: $perm"
            found=1
        fi
    done
    return $found
}

cleanup_release_outputs() {
    rm -rf app/build/outputs/apk/release app/build/outputs/bundle
}

step "lint" run_lint
step "unit tests" run_tests
step "release build (compile + R8 check)" run_assemble_release
step "forbidden permissions scan" check_forbidden_permissions
cleanup_release_outputs

echo "===================="
if [ "$overall" -eq 0 ]; then
    echo "OVERALL: PASS"
else
    echo "OVERALL: FAIL"
fi
exit $overall
