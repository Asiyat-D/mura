#!/usr/bin/env python3
"""Prepare the local Python environment and a checksum-pinned Cloudflare binary."""
import hashlib
import io
import os
from pathlib import Path
import subprocess
import sys
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parent
VERSION = "2026.10.1"
DIGEST = "647615df99d49cd179ea5e13ef760b9bbfb33630f6ae62f87e33dfadda60adcc"
if not (ROOT / ".venv/bin/python").exists():
    subprocess.run([sys.executable, "-m", "venv", str(ROOT / ".venv")], check=True)
subprocess.run([str(ROOT / ".venv/bin/python"), "-m", "pip", "install", "-r", str(ROOT / "requirements.txt")], check=True)
target = ROOT / ".runtime/bin/cloudflared"
if not target.exists():
    url = f"https://github.com/cloudflare/cloudflared/releases/download/{VERSION}/cloudflared-darwin-arm64.tgz"
    data = urllib.request.urlopen(url, timeout=90).read()
    if hashlib.sha256(data).hexdigest() != DIGEST:
        raise SystemExit("Cloudflare release checksum mismatch; nothing installed")
    with tarfile.open(fileobj=io.BytesIO(data), mode="r:gz") as archive:
        entry = next(e for e in archive if e.isfile() and e.name.rstrip("/").split("/")[-1] == "cloudflared")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(archive.extractfile(entry).read())
    os.chmod(target, 0o755)
print("Relay environment ready")
