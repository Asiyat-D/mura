import base64
import hashlib
import json
import tempfile
import threading
import time
import unittest
import urllib.request
import urllib.error
import uuid
from pathlib import Path
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.asymmetric.x25519 import X25519PrivateKey
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat
from relay import Relay


class Client:
    def __init__(self, url):
        self.url = url
        self.key = Ed25519PrivateKey.generate()
        signing = self.key.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw)
        agreement = X25519PrivateKey.generate().public_key().public_bytes(Encoding.Raw, PublicFormat.Raw)
        self.id = hashlib.sha256(signing + agreement).hexdigest()
        self.card = dict(version=1, relay=url, signingKey=base64.b64encode(signing).decode(), agreementKey=base64.b64encode(agreement).decode())

    def prepared(self, path, body=None):
        raw = json.dumps(body, separators=(",", ":")).encode() if body is not None else b""
        method = "POST" if body is not None else "GET"
        timestamp, nonce = str(int(time.time())), str(uuid.uuid4())
        signed = f"MURA-RELAY/1\n{method}\n{path}\n{self.id}\n{timestamp}\n{nonce}\n{hashlib.sha256(raw).hexdigest()}".encode()
        return urllib.request.Request(self.url + path, data=raw if body is not None else None, method=method, headers={
            "X-Mura-Device": self.id, "X-Mura-Time": timestamp, "X-Mura-Nonce": nonce,
            "X-Mura-Signature": base64.b64encode(self.key.sign(signed)).decode(), "Content-Type": "application/json"})

    @staticmethod
    def perform(request):
        try:
            with urllib.request.urlopen(request, timeout=5) as response:
                return response.status, json.load(response)
        except urllib.error.HTTPError as error:
            return error.code, json.load(error)

    def request(self, path, body=None):
        return self.perform(self.prepared(path, body))


class RelayTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.server = Relay(("127.0.0.1", 0), Path(self.temp.name) / "relay.sqlite")
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.url = "http://127.0.0.1:" + str(self.server.server_port)
        self.a, self.b, self.m = (Client(self.url) for _ in range(3))
        for client in (self.a, self.b, self.m):
            self.assertEqual(client.request("/v1/register", client.card)[0], 200)

    def tearDown(self):
        self.server.shutdown(); self.server.server_close(); self.thread.join(); self.temp.cleanup()

    def test_public_apk_download_never_exposes_private_files(self):
        public = Path(self.temp.name) / "public"
        public.mkdir()
        self.server.public_directory = public
        apk = b"PK\x03\x04" + bytes(range(256)) * 1024
        (public / "mura.apk").write_bytes(apk)
        (public / "index.html").write_text("Mura download")
        for method in ("GET", "HEAD"):
            request = urllib.request.Request(self.url + "/download/mura.apk", method=method)
            with urllib.request.urlopen(request) as response:
                self.assertEqual(response.status, 200)
                self.assertEqual(int(response.headers["Content-Length"]), len(apk))
                self.assertEqual(response.headers["Content-Type"], "application/vnd.android.package-archive")
                self.assertEqual(response.read(), apk if method == "GET" else b"")
        for path in ("/download/../relay.sqlite", "/relay.sqlite", "/download/relay.py", "/download/%2e%2e/relay.sqlite"):
            with self.assertRaises(urllib.error.HTTPError) as error:
                urllib.request.urlopen(self.url + path)
            self.assertEqual(error.exception.code, 404)

    def envelope(self):
        return dict(version=1, id=str(uuid.uuid4()), sender=self.a.id, recipient=self.b.id, sentAt=int(time.time()),
                    encapsulatedKey=base64.b64encode(bytes(32)).decode(), ciphertext=base64.b64encode(bytes(range(40))).decode())

    def pair(self):
        self.a.request("/v1/contacts", {"peer": self.b.id}); self.b.request("/v1/contacts", {"peer": self.a.id})

    def receipt(self, client, message):
        digest = hashlib.sha256(json.dumps(message, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
        data = f"MURA-RECEIPT/1\n{message['id']}\n{message['sender']}\n{message['recipient']}\n{digest}".encode()
        return dict(id=message['id'], sender=message['sender'], recipient=message['recipient'], messageHash=digest,
                    signature=base64.b64encode(client.key.sign(data)).decode())

    def test_signature_replay_and_identity_spoofing(self):
        request = self.a.prepared("/v1/inbox")
        self.assertEqual(Client.perform(request)[0], 200)
        self.assertEqual(Client.perform(request), (409, {"error": "replay"}))
        spoof = self.m.prepared("/v1/inbox")
        spoof.add_header("X-Mura-Device", self.a.id)
        self.assertEqual(Client.perform(spoof)[0], 401)
        registration = self.m.prepared("/v1/register", self.a.card)
        self.assertEqual(Client.perform(registration)[0], 401)

    def test_mutual_consent_receipts_and_idempotency(self):
        message = self.envelope()
        request = self.a.prepared("/v1/messages", message)
        self.assertEqual(Client.perform(request), (403, {"error": "peer_not_ready"}))
        self.assertEqual(Client.perform(request)[0], 409)  # Failed actions consume the nonce too.
        self.pair()
        for _ in range(2):
            self.assertEqual(self.a.request("/v1/messages", message)[0], 200)
        self.assertEqual(self.b.request("/v1/inbox")[1], [message])
        self.assertEqual(self.m.request("/v1/inbox")[1], [])
        self.assertEqual(self.m.request("/v1/ack", {"receipts": [self.receipt(self.m, message)]})[0], 403)
        self.assertEqual(self.a.request("/v1/status", {"ids": [message["id"]]})[1][message["id"]]["state"], "sent")
        self.assertEqual(self.b.request("/v1/ack", {"receipts": [self.receipt(self.m, message)]})[0], 401)
        self.b.request("/v1/ack", {"receipts": [self.receipt(self.b, message)]})
        self.assertEqual(self.a.request("/v1/status", {"ids": [message["id"]]})[1][message["id"]]["state"], "delivered")
        self.assertEqual(self.b.request("/v1/inbox")[1], [])
        self.assertEqual(self.a.request("/v1/messages", message)[0], 200)
        self.assertEqual(self.b.request("/v1/inbox")[1], [])
        with self.server.connect() as db:
            self.assertIsNone(db.execute("SELECT body FROM envelopes WHERE id=?", (message["id"],)).fetchone()[0])

    def test_erase_revocation_and_expiry(self):
        self.pair(); message = self.envelope()
        self.a.request("/v1/messages", message)
        self.b.request("/v1/revoke", {"peer": self.a.id})
        self.assertEqual(self.b.request("/v1/inbox")[1], [])
        self.assertEqual(self.a.request("/v1/messages", self.envelope())[0], 403)
        self.pair(); message = self.envelope(); self.a.request("/v1/messages", message)
        with self.server.connect() as db:
            db.execute("UPDATE envelopes SET created=?", (int(time.time()) - 8 * 86400,))
        self.server.last_cleanup = float("-inf")
        count = 1
        deadline = time.monotonic() + 2
        while count and time.monotonic() < deadline:
            time.sleep(0.02)
            with self.server.connect() as db:
                count = db.execute("SELECT COUNT(*) FROM envelopes").fetchone()[0]
        self.assertEqual(count, 0)
        self.assertEqual(self.b.request("/v1/inbox")[1], [])
        self.assertEqual(self.a.request("/v1/erase", {})[0], 200)
        self.assertEqual(self.a.request("/v1/inbox")[0], 401)

    def test_malformed_envelopes_and_unauthenticated_requests(self):
        self.pair(); message = self.envelope(); message["sender"] = self.m.id
        self.assertEqual(self.a.request("/v1/messages", message)[0], 400)
        self.assertEqual(Client.perform(urllib.request.Request(self.url + "/v1/inbox"))[0], 400)
        message = self.envelope(); message["ciphertext"] = "not base64"
        self.assertEqual(self.a.request("/v1/messages", message)[0], 400)


if __name__ == "__main__":
    unittest.main()
