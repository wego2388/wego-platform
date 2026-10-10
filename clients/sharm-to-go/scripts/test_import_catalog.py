"""Importer failure tests against a local HTTP server, no external writes.

Run: python3 -m unittest discover -s clients/sharm-to-go/scripts -p 'test_*.py'
"""
import copy
import importlib.util
import io
import json
import os
import socket
import tempfile
import threading
import unittest
from contextlib import redirect_stderr, redirect_stdout
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from unittest.mock import patch
from uuid import uuid4

SPEC = importlib.util.spec_from_file_location("catalog_importer", Path(__file__).with_name("import_catalog.py"))
IMPORTER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(IMPORTER)
CATEGORY_ID = "e9d88f7e-4e0e-47b8-a230-9c077d8ba5c0"


class ImportRecoveryTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.root = Path(self.directory.name)
        full = json.loads(IMPORTER.DEFAULT_MANIFEST.read_text())
        service = copy.deepcopy(full["services"][0])
        category = next(category for category in full["categories"] if category["code"] == service["categoryCode"])
        self.manifest = {"categories": [category], "services": [service]}
        self.ref = service["internalRef"]
        self.manifest_path = self.root / "manifest.json"
        self.state_path = self.root / "state.json"
        self.records = {}
        self.calls = []
        self.drop = None
        self.category_status = "ACTIVE"
        owner = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *_args):
                pass

            def do_GET(self):
                self.handle_api()

            def do_POST(self):
                self.handle_api()

            def handle_api(self):
                owner.calls.append((self.command, self.path))
                body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"null")
                status = 200
                drop = owner.drop == self.path
                if drop:
                    owner.drop = None
                if self.path.endswith("/identity/login"):
                    response = {"token": "synthetic-local-token"}
                elif self.path.endswith("/categories"):
                    response = [{"id": CATEGORY_ID, "code": category["code"], "status": owner.category_status}]
                elif self.command == "POST" and self.path.endswith("/services"):
                    if drop and getattr(owner, "drop_before_create", False):
                        self.connection.shutdown(socket.SHUT_RDWR)
                        self.connection.close()
                        return
                    service_id = str(uuid4())
                    response = {**body, "id": service_id, "status": "DRAFT", "version": 0}
                    owner.records[service_id] = response
                    status = 201
                else:
                    service_id = self.path.split("/services/")[-1].split("/")[0]
                    response = owner.records.get(service_id)
                    if response is None:
                        status, response = 404, {}
                    elif self.command == "POST":
                        response["status"] = "REVIEW" if self.path.endswith("submit-for-review") else "APPROVED"
                if drop:
                    self.connection.shutdown(socket.SHUT_RDWR)
                    self.connection.close()
                    return
                encoded = json.dumps(response).encode()
                self.send_response(status)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(encoded)))
                self.end_headers()
                self.wfile.write(encoded)

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.base = f"http://127.0.0.1:{self.server.server_port}"

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()
        self.directory.cleanup()

    def run_import(self, *arguments, base=None):
        self.manifest_path.write_text(json.dumps(self.manifest))
        environment = {"STG_API_BASE": base or self.base, "STG_ADMIN_EMAIL": "fixture@example.com", "STG_ADMIN_PASSWORD": "synthetic-password"}
        with patch.dict(os.environ, environment), redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
            return IMPORTER.main(["--manifest", str(self.manifest_path), "--state-file", str(self.state_path), *arguments])

    def state(self):
        return json.loads(self.state_path.read_text())

    def creates(self):
        return sum(method == "POST" and path.endswith("/services") for method, path in self.calls)

    def catalog_writes(self):
        return [call for call in self.calls if call[0] == "POST" and "/travel-marketplace/" in call[1]]

    def seeded_record(self, status="DRAFT"):
        service_id = str(uuid4())
        self.records[service_id] = {**IMPORTER.payload(self.manifest["services"][0], CATEGORY_ID), "id": service_id, "status": status}
        self.state_path.write_text(json.dumps({self.ref: {"serviceId": service_id, "status": "APPROVED"}}))
        return service_id

    def test_full_catalog_validation_includes_vehicle_prices(self):
        full = json.loads(IMPORTER.DEFAULT_MANIFEST.read_text())
        IMPORTER.validate_manifest(full)
        self.assertEqual(len(full["services"]), 41)
        self.assertEqual(sum(len(service["options"]) for service in full["services"]), 83)

    def test_clean_repeat_checks_actual_approval_without_creating_twice(self):
        self.assertEqual(self.run_import(), 0)
        self.assertEqual(self.run_import(), 0)
        self.assertEqual(self.creates(), 1)
        self.assertEqual(self.state()["apiBase"], self.base)
        self.assertFalse(any(path.endswith("/publish") for _method, path in self.calls))

    def test_resume_review_after_draft_checkpoint_and_lost_transition_reply(self):
        service_id = self.seeded_record()
        self.drop = f"/api/v1/travel-marketplace/services/{service_id}/submit-for-review"
        self.assertEqual(self.run_import(), 1)
        self.assertEqual(self.records[service_id]["status"], "REVIEW")
        self.assertEqual(self.run_import(), 0)
        self.assertEqual(self.records[service_id]["status"], "APPROVED")
        self.assertEqual(self.creates(), 0)

    def test_lost_approval_reply_is_recovered_from_remote_status(self):
        service_id = self.seeded_record("REVIEW")
        self.drop = f"/api/v1/travel-marketplace/services/{service_id}/approve"
        self.assertEqual(self.run_import(), 1)
        self.assertEqual(self.run_import(), 0)
        self.assertEqual(self.records[service_id]["status"], "APPROVED")
        self.assertEqual(self.creates(), 0)

    def test_lost_create_reply_blocks_blind_retry_and_accepts_verified_recovery(self):
        self.drop = "/api/v1/travel-marketplace/services"
        self.assertEqual(self.run_import(), 1)
        self.assertIsNone(self.state()["services"][self.ref]["serviceId"])
        self.assertEqual(self.run_import(), 1)
        self.assertEqual(self.creates(), 1)
        service_id = next(iter(self.records))
        self.assertEqual(self.run_import("--recover-service", f"{self.ref}={service_id}"), 0)
        self.assertEqual(self.creates(), 1)
        self.assertEqual(self.records[service_id]["status"], "APPROVED")

    def test_explicit_uncreated_reconciliation_can_retry_a_request_that_never_arrived(self):
        self.drop = "/api/v1/travel-marketplace/services"
        self.drop_before_create = True
        self.assertEqual(self.run_import(), 1)
        self.assertEqual(len(self.records), 0)
        self.assertEqual(self.run_import(), 1)
        self.assertEqual(self.run_import("--retry-uncreated", self.ref), 0)
        self.assertEqual(len(self.records), 1)

    def test_wrong_target_is_rejected_before_any_http_request(self):
        self.assertEqual(self.run_import(), 0)
        self.calls.clear()
        self.assertEqual(self.run_import(base="http://127.0.0.1:1"), 1)
        self.assertEqual(self.calls, [])

    def test_changed_manifest_cannot_silently_skip_saved_content(self):
        self.assertEqual(self.run_import(), 0)
        self.calls.clear()
        self.manifest["services"][0]["options"][0]["priceAmount"] = "123.00"
        self.assertEqual(self.run_import(), 1)
        self.assertEqual(self.calls, [])

    def test_legacy_wrong_id_does_not_create_or_false_skip(self):
        self.state_path.write_text(json.dumps({self.ref: {"serviceId": str(uuid4()), "status": "APPROVED"}}))
        self.assertEqual(self.run_import(), 1)
        self.assertEqual(self.catalog_writes(), [])

    def test_changed_remote_content_is_not_silently_approved(self):
        service_id = self.seeded_record()
        self.records[service_id]["name"] = {"en": "Different service", "ar": "خدمة أخرى"}
        self.assertEqual(self.run_import(), 1)
        self.assertEqual(self.catalog_writes(), [])

    def test_archived_category_is_rejected_without_catalog_writes(self):
        self.category_status = "ARCHIVED"
        self.assertEqual(self.run_import(), 1)
        self.assertEqual(self.catalog_writes(), [])

    def test_invalid_manifest_and_dry_run_make_no_api_calls(self):
        self.assertEqual(self.run_import("--dry-run"), 0)
        self.assertFalse(self.state_path.exists())
        self.manifest["services"].append(copy.deepcopy(self.manifest["services"][0]))
        self.assertEqual(self.run_import(), 1)
        self.assertEqual(self.calls, [])

    def test_same_state_file_cannot_be_imported_concurrently(self):
        with IMPORTER.locked_state(self.state_path):
            self.assertEqual(self.run_import(), 1)
        self.assertEqual(self.calls, [])

    def test_failed_atomic_replace_preserves_last_checkpoint_and_cleans_temp_file(self):
        previous = {"saved": "old"}
        IMPORTER.save_state(self.state_path, previous)
        with patch.object(IMPORTER.os, "replace", side_effect=OSError("fixture disk failure")):
            with self.assertRaises(OSError):
                IMPORTER.save_state(self.state_path, {"saved": "new"})
        self.assertEqual(self.state(), previous)
        self.assertEqual(list(self.root.glob(".state.json.*")), [])


if __name__ == "__main__":
    unittest.main()
