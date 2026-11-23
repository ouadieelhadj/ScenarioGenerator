#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$'\n\t'
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)"
MODE="${1:-prepare}"
case "$MODE" in prepare|simulator|archive|check) ;; *) printf '%s\n' 'Usage: build-iphone.sh check|prepare|simulator|archive' >&2; exit 2;; esac
if [[ "$(uname -s)" != Darwin ]]; then
  printf '%s\n' 'IOS_BUILD_NOT_RUN|MACOS_AND_XCODE_REQUIRED|No signing or iPhone installation performed.' >&2
  exit 2
fi
for tool in node npm python3 xcodebuild xcrun; do command -v "$tool" >/dev/null || { printf 'MISSING_TOOL|%s\n' "$tool" >&2; exit 2; }; done
node -e 'if (Number(process.versions.node.split(".")[0]) < 22) process.exit(2)'
XCODE_MAJOR="$(xcodebuild -version | awk '/^Xcode /{split($2,v,"."); print v[1]}')"
[[ "$XCODE_MAJOR" =~ ^[0-9]+$ && "$XCODE_MAJOR" -ge 26 ]] || { printf '%s\n' 'XCODE_26_OR_LATER_REQUIRED' >&2; exit 2; }
xcodebuild -checkFirstLaunchStatus >/dev/null
xcrun --sdk iphoneos --show-sdk-version
xcrun --sdk iphonesimulator --show-sdk-version
if [[ "$MODE" == check ]]; then printf '%s\n' 'MAC_PREFLIGHT=OK|NATIVE_BUILD=NOT_RUN'; exit 0; fi
VERSION="${FP_IOS_VERSION:-1.3.10}"
BUILD="${FP_IOS_BUILD:-10310}"
OUT="${FP_IOS_OUTPUT:-$ROOT/ios/App/output/$(date -u +%Y%m%dT%H%M%SZ)}"
mkdir -p "$OUT"
cd "$ROOT"
export npm_config_cache="${npm_config_cache:-$HOME/.npm}"
npm ci --ignore-scripts --prefer-offline --no-audit --no-fund
node --test tools/test-mobile-session-security.mjs tools/test-mobile-session-revocation.mjs tools/test-mobile-native-gateway.mjs tools/test-merchant-native-links.mjs tools/test-incident-file-export.mjs tools/test-incident-client-channel.mjs tools/test-mobile-authorized-commerces.mjs tools/test-mobile-login-diagnostic.mjs
npm run build:merchant-mobile -- --configuration production --source-map=false
node node_modules/@capacitor/cli/bin/capacitor sync ios
python3 tools/ios/configure-ios.py --version "$VERSION" --build "$BUILD"
python3 tools/ios/verify-ios.py --report "$OUT/preparation.json"
# Executes the real shared Swift policy sources; not source-text assertions.
swift test --package-path ios --scratch-path "$OUT/PolicyBuild" 2>&1 | tee "$OUT/swift-policy-tests.log"
if [[ "$MODE" == prepare ]]; then printf 'IOS_PROJECT_READY=%s/ios/App/App.xcodeproj|IPA=NOT_BUILT\n' "$ROOT"; exit 0; fi
COMMON=(-project ios/App/App.xcodeproj -scheme App -configuration Release -derivedDataPath "$OUT/DerivedData")
if [[ "$MODE" == simulator ]]; then
  DESTINATION='generic/platform=iOS Simulator'
  if [[ -f "$OUT/simulator.json" ]]; then
    DESTINATION="$(python3 - "$OUT/simulator.json" <<'PY'
import json,pathlib,sys,uuid
value=json.loads(pathlib.Path(sys.argv[1]).read_text())['udid']
assert str(uuid.UUID(value)).lower()==value.lower()
print('platform=iOS Simulator,id='+value)
PY
)"
  fi
  xcodebuild "${COMMON[@]}" -destination "$DESTINATION" ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- build 2>&1 | tee "$OUT/build-simulator.log"
  printf 'SIMULATOR_BUILD=%s/DerivedData/Build/Products/Release-iphonesimulator/App.app|DEVICE_IPA=NOT_BUILT\n' "$OUT"
  exit 0
fi
TEAM="${FP_IOS_TEAM_ID:-}"
[[ "$TEAM" =~ ^[A-Z0-9]{10}$ ]] || { printf '%s\n' 'APPLE_TEAM_ID_REQUIRED|Set FP_IOS_TEAM_ID to your actual Team ID.' >&2; exit 2; }
PROVISIONING=()
if [[ "${FP_IOS_ALLOW_PROVISIONING:-0}" == 1 ]]; then PROVISIONING=(-allowProvisioningUpdates); fi
ARCHIVE="$OUT/FuturPaymentMerchant.xcarchive"
xcodebuild "${COMMON[@]}" "${PROVISIONING[@]}" -destination 'generic/platform=iOS' -archivePath "$ARCHIVE" DEVELOPMENT_TEAM="$TEAM" archive 2>&1 | tee "$OUT/build-device.log"
python3 - "$OUT/ExportOptions.plist" "$TEAM" <<'PY'
import pathlib,plistlib,sys
pathlib.Path(sys.argv[1]).write_bytes(plistlib.dumps({'method':'app-store-connect','destination':'export','teamID':sys.argv[2],'signingStyle':'automatic','manageAppVersionAndBuildNumber':False}))
PY
xcodebuild -exportArchive "${PROVISIONING[@]}" -archivePath "$ARCHIVE" -exportPath "$OUT/ipa" -exportOptionsPlist "$OUT/ExportOptions.plist" 2>&1 | tee "$OUT/export-ipa.log"
python3 tools/ios/generate-associated-domains.py --team-id "$TEAM" --output "$OUT/apple-app-site-association"
python3 - "$OUT" <<'PY'
import hashlib,json,pathlib,sys
root=pathlib.Path(sys.argv[1]);files=list((root/'ipa').glob('*.ipa'))
assert len(files)==1,'No unique IPA produced'
report={'artifact':files[0].name,'sha256':hashlib.sha256(files[0].read_bytes()).hexdigest(),'signedIpaExported':True,'testFlightUploaded':False,'iPhoneRuntimeTested':False}
(root/'ipa-manifest.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
PY
printf '%s\n' 'IPA_EXPORTED|No TestFlight upload performed. Complete native iPhone acceptance before distribution.'
