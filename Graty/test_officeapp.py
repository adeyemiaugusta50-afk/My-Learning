import unittest
from datetime import datetime, timezone

from officeapp import GoogleSheetsAttendanceStore, create_app
from generate_qr import validate_checkin_url


class FakeRequest:
    def __init__(self, result):
        self.result = result

    def execute(self):
        if isinstance(self.result, Exception):
            raise self.result
        return self.result


class FakeValues:
    def __init__(self):
        self.rows = []
        self.header = []
        self.append_options = []
        self.fail_get = False

    def get(self, spreadsheetId, range):
        if self.fail_get:
            return FakeRequest(ConnectionError("test connection failure"))
        if range.endswith("!A1:E1"):
            values = [self.header] if self.header else []
            return FakeRequest({"values": values})
        if range.endswith("!E2:E"):
            return FakeRequest({"values": [[row[4]] for row in self.rows]})
        row_range = range.split("!", 1)[1]
        row_number = int(row_range.split("A", 1)[1].split(":", 1)[0])
        index = row_number - 2
        values = [self.rows[index]] if index < len(self.rows) else []
        return FakeRequest({"values": values})

    def update(self, spreadsheetId, range, valueInputOption, body):
        self.header = body["values"][0]
        return FakeRequest({"updatedRows": 1})

    def append(
        self,
        spreadsheetId,
        range,
        valueInputOption,
        insertDataOption,
        body,
    ):
        self.append_options.append(valueInputOption)
        self.rows.extend(body["values"])
        return FakeRequest({"updates": {"updatedRows": len(body["values"])}})


class FakeSpreadsheets:
    def __init__(self):
        self.fake_values = FakeValues()

    def values(self):
        return self.fake_values


class FakeService:
    def __init__(self):
        self.fake_spreadsheets = FakeSpreadsheets()

    def spreadsheets(self):
        return self.fake_spreadsheets


class OfficeAppTests(unittest.TestCase):
    def setUp(self):
        self.service = FakeService()
        fixed_utc = datetime(2025, 1, 1, 23, 30, 0, tzinfo=timezone.utc)
        self.store = GoogleSheetsAttendanceStore(
            self.service,
            "test-sheet-id",
            "Attendance",
            "Africa/Lagos",
            clock=lambda tz: fixed_utc.astimezone(tz),
        )
        self.app = create_app({"TESTING": True, "ATTENDANCE_STORE": self.store})
        self.app.extensions["attendance_store"] = self.store
        self.client = self.app.test_client()

    def form_data(self, name="A Visitor", submission_id="5d354e7d-2252-4424-a941-138337d63128"):
        return {"full_name": name, "submission_id": submission_id}

    def test_get_shows_mobile_form_and_privacy_notice(self):
        response = self.client.get("/")
        self.assertEqual(response.status_code, 200)
        self.assertIn(b'name="full_name"', response.data)
        self.assertIn(b"does not prove that you are physically", response.data)

    def test_blank_name_is_rejected_without_saving(self):
        response = self.client.post("/", data=self.form_data("   "))
        self.assertEqual(response.status_code, 400)
        self.assertIn(b"Please enter your full name", response.data)
        self.assertEqual(self.service.fake_spreadsheets.fake_values.rows, [])

    def test_success_uses_server_time_and_raw_text(self):
        name = "=2+2"
        response = self.client.post("/", data=self.form_data(name))
        rows = self.service.fake_spreadsheets.fake_values.rows
        self.assertEqual(response.status_code, 200)
        self.assertIn(b"Check-in confirmed", response.data)
        self.assertIn(b"Arrival time: 00:30:00", response.data)
        self.assertEqual(
            rows[0],
            [
                name,
                "2025-01-02",
                "00:30:00",
                "Present",
                self.form_data(name)["submission_id"],
            ],
        )
        self.assertEqual(
            self.service.fake_spreadsheets.fake_values.append_options, ["RAW"]
        )

    def test_repeated_submission_token_creates_one_row(self):
        data = self.form_data()
        first = self.client.post("/", data=data)
        retry = self.client.post("/", data=data)
        self.assertEqual(first.status_code, 200)
        self.assertEqual(retry.status_code, 200)
        self.assertEqual(len(self.service.fake_spreadsheets.fake_values.rows), 1)

    def test_same_name_with_distinct_tokens_is_allowed(self):
        self.client.post("/", data=self.form_data(submission_id="5d354e7d-2252-4424-a941-138337d63128"))
        self.client.post("/", data=self.form_data(submission_id="73e59356-a988-4d4d-a5f6-d8a6e0ae24f7"))
        self.assertEqual(len(self.service.fake_spreadsheets.fake_values.rows), 2)

    def test_connection_failure_does_not_show_success(self):
        self.service.fake_spreadsheets.fake_values.fail_get = True
        response = self.client.post("/", data=self.form_data())
        self.assertEqual(response.status_code, 503)
        self.assertIn(b"We couldn&#39;t confirm your check-in", response.data)
        self.assertNotIn(b"Check-in confirmed", response.data)

    def test_qr_accepts_private_lan_url(self):
        validate_checkin_url("http://192.168.1.54:5000/")

    def test_qr_rejects_loopback_and_public_http_urls(self):
        with self.assertRaises(ValueError):
            validate_checkin_url("http://127.0.0.1:5000/")
        with self.assertRaises(ValueError):
            validate_checkin_url("http://example.com/")


if __name__ == "__main__":
    unittest.main()
