#!/usr/bin/env python3
"""Mura ciphertext relay. Bind only to loopback behind an HTTPS reverse proxy."""
import argparse
import base64
import hashlib
import json
import os
import re
import sqlite3
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey
from cryptography.exceptions import InvalidSignature

MAX_BODY = 220_000
TTL = 7 * 86400
ID = re.compile(r"^[0-9a-f]{64}$")


class Rejected(Exception):
    def __init__(self, status, code):
        self.status, self.code = status, code


def b64(value, size=None):
    if not isinstance(value, str):
        raise Rejected(400, "invalid_format")
    try:
        data = base64.b64decode(value, validate=True)
    except (ValueError, TypeError):
        raise Rejected(400, "invalid_format")
    if size is not None and len(data) != size:
        raise Rejected(400, "invalid_format")
    return data


def valid_id(value):
    if not isinstance(value, str) or not ID.fullmatch(value):
        raise Rejected(400, "invalid_id")
    return value


def message_ids(body):
    ids = body.get("ids")
    if not isinstance(ids, list) or not 1 <= len(ids) <= 100:
        raise Rejected(400, "invalid_ids")
    for value in ids:
        try:
            if str(uuid.UUID(value)) != value:
                raise ValueError()
        except (ValueError, TypeError, AttributeError):
            raise Rejected(400, "invalid_ids")
    return ids


class Relay(ThreadingHTTPServer):
    daemon_threads = True
    def __init__(self, address, database):
        self.database = str(database)
        self.public_directory = Path(__file__).parent / "public"
        self.last_cleanup = float("-inf")
        Path(database).parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        with self.connect() as db:
            db.executescript("""
                CREATE TABLE IF NOT EXISTS devices(id TEXT PRIMARY KEY, signing BLOB NOT NULL, agreement BLOB NOT NULL);
                CREATE TABLE IF NOT EXISTS contacts(owner TEXT NOT NULL, peer TEXT NOT NULL, PRIMARY KEY(owner,peer));
                CREATE TABLE IF NOT EXISTS nonces(device TEXT NOT NULL, nonce TEXT NOT NULL, created INTEGER NOT NULL, PRIMARY KEY(device,nonce));
                CREATE INDEX IF NOT EXISTS nonce_expiry ON nonces(created);
                CREATE TABLE IF NOT EXISTS envelopes(id TEXT PRIMARY KEY, sender TEXT NOT NULL, recipient TEXT NOT NULL,
                    body BLOB, digest TEXT NOT NULL, created INTEGER NOT NULL, delivered INTEGER NOT NULL DEFAULT 0);
                CREATE INDEX IF NOT EXISTS inbox ON envelopes(recipient,delivered,created);
            """)
            if "receipt" not in {row[1] for row in db.execute("PRAGMA table_info(envelopes)")}:
                db.execute("ALTER TABLE envelopes ADD COLUMN receipt BLOB")
        os.chmod(database, 0o600)
        super().__init__(address, Handler)

    def connect(self):
        db = sqlite3.connect(self.database, timeout=10)
        db.execute("PRAGMA busy_timeout=10000")
        return db

    def service_actions(self):
        if time.monotonic() - self.last_cleanup < 60:
            return
        self.last_cleanup = time.monotonic()
        db = self.connect()
        try:
            with db:
                db.execute("DELETE FROM envelopes WHERE created < ?", (int(time.time()) - TTL,))
                db.execute("DELETE FROM nonces WHERE created < ?", (int(time.time()) - 600,))
        finally:
            db.close()


class Handler(BaseHTTPRequestHandler):
    server_version = "MuraRelay/1"
    sys_version = ""
    def log_message(self, *_):
        pass  # No contact IDs, headers, URLs or message contents in access logs.

    def setup(self):
        super().setup()
        self.connection.settimeout(15)

    def do_GET(self):
        self.dispatch()

    def do_HEAD(self):
        if not self.public_download():
            self.send_error(404)

    def public_download(self):
        # Fixed public filenames only; never expose the database, keys, logs or other paths.
        routes = {"/": ("index.html", "text/html; charset=utf-8"),
                  "/download": ("index.html", "text/html; charset=utf-8"),
                  "/download/mura.apk": ("mura.apk", "application/vnd.android.package-archive"),
                  "/download/SHA256SUMS": ("SHA256SUMS", "text/plain; charset=utf-8")}
        if self.command not in ("GET", "HEAD") or self.path not in routes:
            return False
        name, content_type = routes[self.path]
        file = self.server.public_directory / name
        if not file.is_file():
            self.send_error(404)
            return True
        with file.open("rb") as source:
            self.send_response(200)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(os.fstat(source.fileno()).st_size))
            self.send_header("Cache-Control", "no-cache")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.send_header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'")
            if name == "mura.apk":
                self.send_header("Content-Disposition", 'attachment; filename="Mura-1.1.0.apk"')
            self.end_headers()
            if self.command == "GET":
                while True:
                    chunk = source.read(65536)
                    if not chunk:
                        break
                    self.wfile.write(chunk)
        return True

    def do_POST(self):
        self.dispatch()

    def reply(self, status, body):
        raw = json.dumps(body, separators=(",", ":")).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(raw)

    def dispatch(self):
        try:
            if self.public_download():
                return
            if self.command == "GET" and self.path == "/health":
                return self.reply(200, {"service": "mura-relay", "version": 1})
            allowed = {"/v1/register", "/v1/contacts", "/v1/revoke", "/v1/messages", "/v1/ack", "/v1/status", "/v1/erase"}
            if not ((self.command == "GET" and self.path == "/v1/inbox") or (self.command == "POST" and self.path in allowed)):
                raise Rejected(404, "not_found")
            if self.headers.get("Transfer-Encoding"):
                raise Rejected(400, "invalid_body")
            try:
                length = int(self.headers.get("Content-Length", "0"))
            except ValueError:
                raise Rejected(400, "invalid_body")
            if not 0 <= length <= MAX_BODY:
                raise Rejected(413, "too_large")
            if self.command == "GET" and length:
                raise Rejected(400, "invalid_body")
            raw = self.rfile.read(length)
            if len(raw) != length:
                raise Rejected(400, "invalid_body")
            try:
                body = json.loads(raw) if raw else {}
            except (ValueError, UnicodeError):
                raise Rejected(400, "invalid_json")
            if not isinstance(body, dict):
                raise Rejected(400, "invalid_json")
            with self.server.connect() as db:
                # Serialize replay protection, quotas and idempotent writes in one transaction.
                db.execute("BEGIN IMMEDIATE")
                device = self.authenticate(db, body, raw)
                db.commit()  # A valid nonce is consumed even when the requested action is rejected.
                db.execute("BEGIN IMMEDIATE")
                result = self.route(db, device, body, raw)
            self.reply(200, result)
        except Rejected as error:
            self.reply(error.status, {"error": error.code})
        except (BrokenPipeError, ConnectionResetError, TimeoutError):
            pass
        except Exception:
            self.reply(503, {"error": "unavailable"})

    def authenticate(self, db, body, raw):
        device = valid_id(self.headers.get("X-Mura-Device"))
        now = int(time.time())
        timestamp = self.headers.get("X-Mura-Time", "")
        nonce = self.headers.get("X-Mura-Nonce", "")
        try:
            if abs(now - int(timestamp)) > 300 or str(uuid.UUID(nonce)) != nonce:
                raise ValueError()
        except (ValueError, TypeError, AttributeError):
            raise Rejected(401, "invalid_auth")
        if self.path == "/v1/register":
            if body.get("version") != 1:
                raise Rejected(400, "invalid_version")
            signing, agreement = b64(body.get("signingKey"), 32), b64(body.get("agreementKey"), 32)
            if hashlib.sha256(signing + agreement).hexdigest() != device:
                raise Rejected(401, "invalid_auth")
        else:
            row = db.execute("SELECT signing FROM devices WHERE id=?", (device,)).fetchone()
            if not row:
                raise Rejected(401, "not_registered")
            signing = row[0]
        signature = b64(self.headers.get("X-Mura-Signature"), 64)
        signed = f"MURA-RELAY/1\n{self.command}\n{self.path}\n{device}\n{timestamp}\n{nonce}\n{hashlib.sha256(raw).hexdigest()}".encode()
        try:
            Ed25519PublicKey.from_public_bytes(signing).verify(signature, signed)
        except (InvalidSignature, ValueError):
            raise Rejected(401, "invalid_auth")
        db.execute("DELETE FROM nonces WHERE created < ?", (now - 600,))
        db.execute("DELETE FROM envelopes WHERE created < ?", (now - TTL,))
        if db.execute("SELECT COUNT(*) FROM nonces WHERE device=? AND created>?", (device, now - 60)).fetchone()[0] >= 300:
            raise Rejected(429, "rate_limited")
        try:
            db.execute("INSERT INTO nonces VALUES(?,?,?)", (device, nonce, now))
        except sqlite3.IntegrityError:
            raise Rejected(409, "replay")
        return device

    def route(self, db, device, body, raw):
        path = self.path
        if path == "/v1/register":
            if db.execute("SELECT COUNT(*) FROM devices").fetchone()[0] >= 2000 and not db.execute("SELECT 1 FROM devices WHERE id=?", (device,)).fetchone():
                raise Rejected(429, "rate_limited")
            db.execute("INSERT OR IGNORE INTO devices VALUES(?,?,?)", (device, b64(body["signingKey"], 32), b64(body["agreementKey"], 32)))
            return {"ok": True}
        if path in ("/v1/contacts", "/v1/revoke"):
            peer = valid_id(body.get("peer"))
            if path == "/v1/revoke":
                db.execute("DELETE FROM contacts WHERE owner=? AND peer=?", (device, peer))
                db.execute("DELETE FROM envelopes WHERE (sender=? AND recipient=?) OR (sender=? AND recipient=?)", (peer, device, device, peer))
            else:
                if db.execute("SELECT COUNT(*) FROM contacts WHERE owner=?", (device,)).fetchone()[0] >= 500:
                    raise Rejected(429, "rate_limited")
                db.execute("INSERT OR IGNORE INTO contacts VALUES(?,?)", (device, peer))
            return {"ok": True}
        if path == "/v1/messages":
            if body.get("version") != 1 or body.get("sender") != device:
                raise Rejected(400, "invalid_message")
            recipient = valid_id(body.get("recipient"))
            try:
                if str(uuid.UUID(body.get("id"))) != body["id"] or type(body.get("sentAt")) is not int or not 0 <= body["sentAt"] < 2**53:
                    raise ValueError()
            except (ValueError, TypeError, AttributeError):
                raise Rejected(400, "invalid_message")
            b64(body.get("encapsulatedKey"), 32)
            if not 17 <= len(b64(body.get("ciphertext"))) <= 131072 + 16:
                raise Rejected(400, "invalid_message")
            if not db.execute("SELECT 1 FROM devices WHERE id=?", (recipient,)).fetchone():
                raise Rejected(404, "recipient_missing")
            if not db.execute("SELECT 1 FROM contacts WHERE owner=? AND peer=?", (recipient, device)).fetchone() or not db.execute("SELECT 1 FROM contacts WHERE owner=? AND peer=?", (device, recipient)).fetchone():
                raise Rejected(403, "peer_not_ready")
            canonical = json.dumps(body, sort_keys=True, separators=(",", ":")).encode()
            digest = hashlib.sha256(canonical).hexdigest()
            previous = db.execute("SELECT digest,sender FROM envelopes WHERE id=?", (body["id"],)).fetchone()
            if previous:
                if previous != (digest, device):
                    raise Rejected(409, "message_conflict")
                return {"ok": True}
            count, size = db.execute("SELECT COUNT(*),COALESCE(SUM(LENGTH(body)),0) FROM envelopes WHERE recipient=? AND delivered=0", (recipient,)).fetchone()
            if count >= 500 or size + len(raw) > 16 * 1024 * 1024:
                raise Rejected(429, "queue_full")
            db.execute("INSERT INTO envelopes(id,sender,recipient,body,digest,created) VALUES(?,?,?,?,?,?)", (body["id"], device, recipient, canonical, digest, int(time.time())))
            return {"ok": True}
        if path == "/v1/inbox":
            return [json.loads(row[0]) for row in db.execute("SELECT body FROM envelopes WHERE recipient=? AND delivered=0 ORDER BY created,id LIMIT 20", (device,))]
        if path == "/v1/ack":
            receipts = body.get("receipts")
            if not isinstance(receipts, list) or not 1 <= len(receipts) <= 100:
                raise Rejected(400, "invalid_receipts")
            signing = db.execute("SELECT signing FROM devices WHERE id=?", (device,)).fetchone()[0]
            for receipt in receipts:
                if not isinstance(receipt, dict):
                    raise Rejected(400, "invalid_receipts")
                mid = message_ids({"ids": [receipt.get("id")]})[0]
                row = db.execute("SELECT sender,recipient,digest FROM envelopes WHERE id=?", (mid,)).fetchone()
                if not row:
                    continue
                if row != (receipt.get("sender"), device, receipt.get("messageHash")) or receipt.get("recipient") != device:
                    raise Rejected(403, "invalid_receipt")
                signed = f"MURA-RECEIPT/1\n{mid}\n{row[0]}\n{device}\n{row[2]}".encode()
                try:
                    Ed25519PublicKey.from_public_bytes(signing).verify(b64(receipt.get("signature"), 64), signed)
                except (InvalidSignature, ValueError):
                    raise Rejected(401, "invalid_receipt")
                encoded = json.dumps(receipt, sort_keys=True, separators=(",", ":")).encode()
                db.execute("UPDATE envelopes SET delivered=1,body=NULL,receipt=? WHERE id=? AND recipient=?", (encoded, mid, device))
            return {"ok": True}
        if path == "/v1/status":
            result = {}
            for mid in message_ids(body):
                row = db.execute("SELECT delivered,receipt FROM envelopes WHERE id=? AND sender=?", (mid, device)).fetchone()
                result[mid] = {"state": ("delivered" if row[0] else "sent") if row else "missing",
                               "receipt": json.loads(row[1]) if row and row[1] else None}
            return result
        if path == "/v1/erase":
            db.execute("DELETE FROM envelopes WHERE sender=? OR recipient=?", (device, device))
            db.execute("DELETE FROM contacts WHERE owner=? OR peer=?", (device, device))
            db.execute("DELETE FROM nonces WHERE device=?", (device,))
            db.execute("DELETE FROM devices WHERE id=?", (device,))
            return {"ok": True}
        raise Rejected(404, "not_found")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8787)
    parser.add_argument("--database", default=str(Path(__file__).parent / ".runtime" / "relay.sqlite"))
    args = parser.parse_args()
    os.umask(0o077)
    server = Relay((args.host, args.port), args.database)
    print(f"Mura relay listening on {args.host}:{args.port}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
