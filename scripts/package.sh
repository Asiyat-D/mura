#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ "${MURA_SKIP_VALIDATED_CHECKS:-0}" != "1" ]; then
  ./gradlew :core:domain:test :app:assembleDebug :app:assembleRelease :app:lintRelease
fi
mkdir -p artifacts
cp app/build/outputs/apk/debug/app-debug.apk artifacts/mura-debug.apk
cp app/build/outputs/apk/release/app-release.apk artifacts/Mura-1.1.0.apk
python3 - <<'PY'
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import hashlib
root = Path.cwd()
artifacts = root / 'artifacts'
with ZipFile(artifacts / 'mura-source.zip', 'w', ZIP_DEFLATED) as archive:
    for path in sorted(root.rglob('*')):
        rel = path.relative_to(root)
        if path.is_file() and not set(rel.parts).intersection({'.gradle', '.kotlin', '.git', '.idea', '.build', '.derivedData', '.swiftpm', 'xcuserdata', 'build', 'artifacts', '.venv', '.runtime', '__pycache__'}) and path.name not in {'local.properties', '.DS_Store', 'release.properties'} and path.suffix not in {'.apk', '.p12', '.jks', '.keystore'} and not any(part.endswith('.xcresult') for part in rel.parts):
            archive.write(path, Path('Mura') / rel)
files = ['Mura-1.1.0.apk', 'mura-debug.apk', 'mura-source.zip']
if (artifacts / 'reports').is_dir():
    with ZipFile(artifacts / 'mura-reports.zip', 'w', ZIP_DEFLATED) as archive:
        for path in sorted((artifacts / 'reports').rglob('*')):
            if path.is_file(): archive.write(path, path.relative_to(artifacts))
    files.append('mura-reports.zip')
(artifacts / 'SHA256SUMS').write_text(''.join(f'{hashlib.sha256((artifacts / name).read_bytes()).hexdigest()}  {name}\n' for name in files))
for name in files: print(name, (artifacts / name).stat().st_size, 'bytes')
PY
