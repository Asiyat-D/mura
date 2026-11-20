#!/usr/bin/env python3
"""Install/start/stop the two user-owned Mura development services (no root)."""
import os
import hashlib
import plistlib
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SERVICE = Path.home() / "Library/Application Support/MuraRelay"
AGENTS = Path.home() / "Library/LaunchAgents"
DOMAIN = "gui/" + str(os.getuid())
LABELS = ("kz.mura.relay", "kz.mura.tunnel")


def loaded(label):
    return subprocess.run(["launchctl", "print", DOMAIN + "/" + label], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0


def status():
    for label in LABELS:
        print(label + (": loaded" if loaded(label) else ": stopped"))
    log = SERVICE / "logs/tunnel.log"
    urls = re.findall(r"https://[a-z0-9-]+\.trycloudflare\.com", log.read_text() if log.exists() else "")
    if urls:
        print("Relay HTTPS URL: " + urls[-1])
        (SERVICE / "endpoint.txt").write_text(urls[-1] + "\n")


def install():
    os.umask(0o077)
    SERVICE.mkdir(parents=True, exist_ok=True, mode=0o700)
    (SERVICE / "logs").mkdir(exist_ok=True, mode=0o700)
    AGENTS.mkdir(parents=True, exist_ok=True)
    shutil.copy2(ROOT / "relay.py", SERVICE / "relay.py")
    public = SERVICE / "public"
    public.mkdir(exist_ok=True, mode=0o700)
    for name in ("index.html", "mura.apk", "SHA256SUMS"):
        source = ROOT / "public" / name
        if source.is_file():
            temporary = public / (name + ".new")
            shutil.copy2(source, temporary)
            os.replace(temporary, public / name)
    if not (SERVICE / ".venv").exists():
        shutil.copytree(ROOT / ".venv", SERVICE / ".venv", symlinks=True)
    source, destination = ROOT / ".runtime/bin/cloudflared", SERVICE / "cloudflared"
    if not destination.exists() or hashlib.sha256(source.read_bytes()).digest() != hashlib.sha256(destination.read_bytes()).digest():
        temporary = SERVICE / "cloudflared.new"
        shutil.copy2(source, temporary); os.replace(temporary, destination)
    commands = [
        [str(SERVICE / ".venv/bin/python"), str(SERVICE / "relay.py"), "--port", "8788", "--database", str(SERVICE / "relay.sqlite")],
        [str(SERVICE / "cloudflared"), "tunnel", "--no-autoupdate", "--protocol", "http2", "--url", "http://127.0.0.1:8788"],
    ]
    for label, command in zip(LABELS, commands):
        log = str(SERVICE / ("logs/relay.log" if label == LABELS[0] else "logs/tunnel.log"))
        plist = {"Label": label, "ProgramArguments": command, "WorkingDirectory": str(SERVICE),
                 "RunAtLoad": True, "KeepAlive": True, "ThrottleInterval": 10, "ProcessType": "Background",
                 "StandardOutPath": log, "StandardErrorPath": log, "Umask": 63,
                 "EnvironmentVariables": {"PYTHONUNBUFFERED": "1"}}
        target = AGENTS / (label + ".plist")
        unchanged = False
        if target.exists():
            with target.open("rb") as f:
                unchanged = plistlib.load(f) == plist
        if not unchanged:
            temporary = target.with_suffix(".plist.new")
            with temporary.open("wb") as f:
                plistlib.dump(plist, f)
            os.replace(temporary, target)
        if not loaded(label):
            subprocess.run(["launchctl", "bootstrap", DOMAIN, str(target)], check=True)
        elif label == LABELS[0]:
            subprocess.run(["launchctl", "kickstart", "-k", DOMAIN + "/" + label], check=True)
    status()


if __name__ == "__main__":
    command = sys.argv[1] if len(sys.argv) > 1 else "status"
    if command == "install":
        install()
    elif command == "stop":
        for label in reversed(LABELS):
            if loaded(label):
                subprocess.run(["launchctl", "bootout", DOMAIN + "/" + label], check=True)
        status()
    elif command == "status":
        status()
    else:
        raise SystemExit("Usage: mac-service.py install|status|stop")
