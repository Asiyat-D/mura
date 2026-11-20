#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
ios_project_root=$(pwd)
ios_artifacts_root=$(cd .. && pwd)/artifacts
mkdir -p "$ios_artifacts_root/ios-reports"
if [ "${MURA_SKIP_VALIDATED_CHECKS:-0}" != "1" ]; then
swift test --package-path Core > "$ios_artifacts_root/ios-reports/core-tests.txt" 2>&1
xcodebuild -project Mura.xcodeproj -scheme Mura -configuration Release -destination 'generic/platform=iOS Simulator' -derivedDataPath .derivedData CODE_SIGNING_ALLOWED=NO ARCHS=arm64 build > "$ios_artifacts_root/ios-reports/simulator-build.txt" 2>&1
xcodebuild -project Mura.xcodeproj -scheme Mura -configuration Release -destination 'generic/platform=iOS' -derivedDataPath .derivedData CODE_SIGNING_ALLOWED=NO build > "$ios_artifacts_root/ios-reports/iphone-unsigned-build.txt" 2>&1
fi
codesign --force --sign - --timestamp=none --entitlements Simulator.entitlements .derivedData/Build/Products/Release-iphonesimulator/Mura.app
codesign --verify --deep --strict .derivedData/Build/Products/Release-iphonesimulator/Mura.app
python3 - "$ios_project_root" "$ios_artifacts_root" <<'PY'
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import hashlib, sys
root, output = map(Path, sys.argv[1:])
files = []
def archive_folder(source, destination, prefix, excluded=(), append=False):
    with ZipFile(output / destination, 'a' if append else 'w', ZIP_DEFLATED) as archive:
        for path in sorted(source.rglob('*')):
            rel = path.relative_to(source)
            if path.is_file() and not set(rel.parts).intersection(excluded) and path.name not in {'.DS_Store', 'release.properties'} and path.suffix not in {'.apk', '.p12', '.jks', '.keystore'} and not any(part.endswith('.xcresult') for part in rel.parts):
                archive.write(path, Path(prefix) / rel)
    if destination not in files: files.append(destination)
archive_folder(root / '.derivedData/Build/Products/Release-iphonesimulator/Mura.app', 'mura-ios-simulator.zip', 'Mura.app')
archive_folder(root / '.derivedData/Build/Products/Release-iphoneos/Mura.app', 'mura-ios-device-unsigned.zip', 'Mura.app')
archive_folder(root, 'mura-ios-source.zip', 'Mura-iOS/ios', {'.derivedData', '.build', '.swiftpm', 'xcuserdata'})
archive_folder(root.parent / 'server', 'mura-ios-source.zip', 'Mura-iOS/server', {'.venv', '.runtime', '__pycache__'}, append=True)
archive_folder(root.parent / 'docs', 'mura-ios-source.zip', 'Mura-iOS/docs', append=True)
with ZipFile(output / 'mura-ios-source.zip', 'a', ZIP_DEFLATED) as archive:
    archive.writestr('Mura-iOS/README.md', '# Mura iOS 1.1\n\nOpen `ios/Mura.xcodeproj`. See `ios/README.md` for the app and `server/README.md` for the HTTPS relay.\n')
archive_folder(output / 'ios-reports', 'mura-ios-reports.zip', 'ios-reports')
(output / 'SHA256SUMS-iOS').write_text(''.join(f'{hashlib.sha256((output / name).read_bytes()).hexdigest()}  {name}\n' for name in files))
for name in files: print(name, (output / name).stat().st_size, 'bytes')
PY
