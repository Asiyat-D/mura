#!/usr/bin/env python3
"""Black-box release APK check on the dedicated Android emulator, with an Apple CryptoKit peer.

Install the signed release APK first. Uses only UI actions and public contact codes.
Requires an already running, unlocked emulator; never selects a physical device.
"""
import base64
import json
from pathlib import Path
import re
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
ADB = str(Path.home() / "Library/Android/sdk/platform-tools/adb")
SERIAL = "emulator-5554"
PACKAGE = "kz.mura.app"


def adb(*args):
    return subprocess.check_output([ADB, "-s", SERIAL, *args], text=True)


def screen():
    adb("shell", "uiautomator", "dump", "/sdcard/mura-release-window.xml")
    return ET.fromstring(adb("exec-out", "cat", "/sdcard/mura-release-window.xml"))


def find(predicate, timeout=20):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        tree = screen()
        for node in tree.iter("node"):
            if predicate(node):
                return node
        time.sleep(0.4)
    raise AssertionError("UI element not found")


def tap(node):
    left, top, right, bottom = map(int, re.findall(r"\d+", node.attrib["bounds"]))
    adb("shell", "input", "tap", str((left+right)//2), str((top+bottom)//2))


def tap_text(text):
    tap(find(lambda n: n.get("text") == text))


def field(hint):
    tree = screen()
    for node in tree.iter("node"):
        if node.get("class") == "android.widget.EditText" and any(hint in c.get("text", "") for c in node.iter("node")):
            return node
    raise AssertionError("Field not found: " + hint)


def type_ascii(value):
    # Only public contact codes and ASCII test text; avoid shell metacharacters.
    assert re.fullmatch(r"[A-Za-z0-9+/=: .-]+", value)
    adb("shell", "input", "text", value.replace(" ", "%s"))


def main():
    with tempfile.TemporaryDirectory(prefix="mura-release-check-") as temporary:
        temporary = Path(temporary)
        adb("shell", "am", "force-stop", PACKAGE)
        adb("shell", "am", "start", "-n", PACKAGE+"/.MainActivity")
        tap_text("Чаты")
        tap_text("Мой код")
        own = find(lambda n: n.get("text", "").startswith("MURA-CONTACT1:"))
        own_file = temporary / "own.txt"
        own_file.write_text(own.get("text"))
        peer_file = temporary / "peer.json"
        executable = ROOT / "ios/Core/.build/out/Products/Debug/MuraChatInterop"
        log = temporary / "peer.log"
        with log.open("w") as output:
            peer = subprocess.Popen([str(executable), "release-relay", str(own_file), str(peer_file)], stdout=output, stderr=output)
            try:
                for _ in range(60):
                    if peer_file.exists():
                        break
                    assert peer.poll() is None, log.read_text()
                    time.sleep(0.5)
                assert peer_file.exists()
                code = "MURA-CONTACT1:" + base64.b64encode(peer_file.read_bytes()).decode()
                tap_text("Закрыть")
                tap(find(lambda n: n.get("content-desc") == "Новый диалог"))
                tap(field("Имя собеседника")); type_ascii("Release peer")
                adb("shell", "input", "keyevent", "4")
                tap(field("Код контакта")); type_ascii(code)
                adb("shell", "input", "keyevent", "4")
                tap_text("Добавить контакт")
                edit = find(lambda n: n.get("class") == "android.widget.EditText")
                tap(edit); type_ascii("Android release 6899")
                adb("shell", "input", "keyevent", "4")
                tap(find(lambda n: n.get("content-desc") == "Отправить"))
                find(lambda n: n.get("text") == "Release reply 6899", timeout=60)
                find(lambda n: "Доставлено" in n.get("text", ""), timeout=30)
                assert peer.wait(timeout=30) == 0, log.read_text()
                adb("shell", "am", "force-stop", PACKAGE)
                adb("shell", "am", "start", "-n", PACKAGE+"/.MainActivity")
                tap_text("Чаты"); tap_text("Release peer")
                find(lambda n: n.get("text") == "Android release 6899")
                find(lambda n: n.get("text") == "Release reply 6899")
                print("PASS: signed release APK launches, adds contact, sends/receives with Apple CryptoKit, verifies delivery and persists after force-stop")
                print(log.read_text().strip())
            finally:
                if peer.poll() is None:
                    peer.terminate(); peer.wait(timeout=10)


if __name__ == "__main__":
    main()
