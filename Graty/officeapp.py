import json
import logging
import os
import threading
import uuid
from datetime import datetime
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from dotenv import load_dotenv
from flask import Flask, current_app, render_template_string, request

load_dotenv()

logging.basicConfig(level=os.environ.get("LOG_LEVEL", "INFO").upper())
logger = logging.getLogger(__name__)

HEADERS = ["Full Name", "Date", "Arrival Time", "Status", "Submission ID"]
SHEETS_SCOPE = "https://www.googleapis.com/auth/spreadsheets"

PAGE = """<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="theme-color" content="#163b35">
  <title>Office check-in</title>
  <style>
    :root { color-scheme: light; font-family: system-ui, sans-serif; }
    * { box-sizing: border-box; }
    body { margin: 0; min-height: 100vh; display: grid; place-items: center;
      padding: 1rem; background: #f3f6f5; color: #172522; }
    main { width: min(100%, 28rem); padding: clamp(1.25rem, 6vw, 2rem);
      background: white; border-radius: 1rem; box-shadow: 0 0.5rem 2rem #163b3518; }
    h1 { margin-top: 0; color: #163b35; }
    label { display: block; margin: 1.5rem 0 0.45rem; font-weight: 650; }
    input, button { width: 100%; min-height: 3rem; border-radius: 0.6rem;
      font: inherit; }
    input { padding: 0.7rem 0.8rem; border: 1px solid #879994; }
    button { margin-top: 1rem; padding: 0.75rem; border: 0; background: #176b55;
      color: white; font-weight: 700; cursor: pointer; }
    button:disabled { opacity: 0.65; cursor: wait; }
    .notice, .message { padding: 0.9rem; border-radius: 0.6rem; line-height: 1.5; }
    .notice { margin-top: 1.5rem; background: #eef4f2; font-size: 0.92rem; }
    .message { margin: 1rem 0; }
    .error { background: #fff0ed; color: #8a2114; }
    .success { background: #eaf7ef; color: #155a31; }
    .time { font-size: 1.25rem; font-weight: 750; }
    [hidden] { display: none !important; }
  </style>
</head>
<body>
  <main>
    <h1>Office check-in</h1>
    <p>Enter your full name to record your arrival.</p>
    {% if message %}
      <div class="message {{ 'success' if success else 'error' }}" role="status" aria-live="polite">
        <strong>{{ message }}</strong>
        {% if success %}<div class="time">Arrival time: {{ arrival_time }}</div>{% endif %}
      </div>
    {% endif %}
    {% if show_form %}
      <form id="check-in-form" method="post" action="/">
        <input type="hidden" name="submission_id" value="{{ submission_id }}">
        <label for="full_name">Full name</label>
        <input id="full_name" name="full_name" type="text" required maxlength="200"
          autocomplete="name" value="{{ full_name }}" placeholder="Your full name">
        <button id="check-in-button" type="submit">Check in</button>
        <p id="loading-message" class="message" role="status" aria-live="polite" hidden>
          Saving your check-in…
        </p>
      </form>
    {% endif %}
    <aside class="notice">
      Your name and arrival time will be recorded in the office attendance sheet.
      Scanning this QR code does not prove that you are physically at the office.
    </aside>
  </main>
  <script>
    const form = document.getElementById("check-in-form");
    if (form) {
      form.addEventListener("submit", () => {
        const button = document.getElementById("check-in-button");
        button.disabled = true;
        button.textContent = "Saving…";
        document.getElementById("loading-message").hidden = false;
      });
    }
  </script>
</body>
</html>
"""


class AttendanceStoreError(Exception):
    """Raised when attendance cannot be safely saved or confirmed."""


class GoogleSheetsAttendanceStore:
    def __init__(
        self,
        service: Any,
        spreadsheet_id: str,
        worksheet: str,
        timezone_name: str,
        clock: Any = None,
    ) -> None:
        try:
            self.timezone = ZoneInfo(timezone_name)
        except ZoneInfoNotFoundError as exc:
            raise AttendanceStoreError(
                f"Unknown office timezone: {timezone_name}"
            ) from exc
        self.service = service
        self.spreadsheet_id = spreadsheet_id
        self.worksheet = worksheet
        self.clock = clock or datetime.now
        self._lock = threading.Lock()

    @classmethod
    def from_environment(cls) -> "GoogleSheetsAttendanceStore":
        raw_credentials = os.environ.get("GOOGLE_SERVICE_ACCOUNT_JSON", "")
        credentials_file = os.environ.get("GOOGLE_SERVICE_ACCOUNT_FILE", "").strip()
        spreadsheet_id = os.environ.get("GOOGLE_SHEET_ID", "").strip()
        worksheet = os.environ.get("GOOGLE_SHEET_TAB", "Attendance").strip()
        timezone_name = os.environ.get("OFFICE_TIMEZONE", "Africa/Lagos").strip()

        if (not raw_credentials and not credentials_file) or not spreadsheet_id:
            raise AttendanceStoreError(
                "Google Sheets credentials or spreadsheet ID are not configured."
            )
        try:
            if credentials_file:
                service_account_info = json.loads(
                    Path(credentials_file).read_text(encoding="utf-8")
                )
            else:
                service_account_info = json.loads(raw_credentials)
            if not isinstance(service_account_info, dict):
                raise ValueError("Service-account JSON must be an object.")
            from google.oauth2.service_account import Credentials
            from googleapiclient.discovery import build

            credentials = Credentials.from_service_account_info(
                service_account_info, scopes=[SHEETS_SCOPE]
            )
            service = build(
                "sheets", "v4", credentials=credentials, cache_discovery=False
            )
        except (OSError, ValueError, KeyError, ImportError) as exc:
            raise AttendanceStoreError(
                "Google Sheets credentials could not be loaded."
            ) from exc

        return cls(service, spreadsheet_id, worksheet, timezone_name)

    def _range(self, cells: str) -> str:
        escaped_title = self.worksheet.replace("'", "''")
        return f"'{escaped_title}'!{cells}"

    def _ensure_headers(self) -> None:
        result = (
            self.service.spreadsheets()
            .values()
            .get(
                spreadsheetId=self.spreadsheet_id,
                range=self._range("A1:E1"),
            )
            .execute()
        )
        values = result.get("values", [])
        if not values or not values[0]:
            (
                self.service.spreadsheets()
                .values()
                .update(
                    spreadsheetId=self.spreadsheet_id,
                    range=self._range("A1:E1"),
                    valueInputOption="RAW",
                    body={"values": [HEADERS]},
                )
                .execute()
            )
            return
        if values[0][: len(HEADERS)] != HEADERS:
            raise AttendanceStoreError(
                "The first row of the attendance tab must contain the required headers."
            )

    def save(self, full_name: str, submission_id: str) -> tuple[str, str]:
        with self._lock:
            self._ensure_headers()
            result = (
                self.service.spreadsheets()
                .values()
                .get(
                    spreadsheetId=self.spreadsheet_id,
                    range=self._range("E2:E"),
                )
                .execute()
            )
            identifiers = result.get("values", [])
            for row_number, row in enumerate(identifiers, start=2):
                if row and row[0] == submission_id:
                    stored = (
                        self.service.spreadsheets()
                        .values()
                        .get(
                            spreadsheetId=self.spreadsheet_id,
                            range=self._range(f"A{row_number}:E{row_number}"),
                        )
                        .execute()
                        .get("values", [])
                    )
                    if (
                        not stored
                        or not stored[0]
                        or len(stored[0]) < 3
                        or stored[0][0] != full_name
                    ):
                        raise AttendanceStoreError(
                            "This check-in token is already associated with a different name."
                        )
                    saved_row = stored[0]
                    return saved_row[1], saved_row[2]

            arrival = self.clock(self.timezone)
            date_text = arrival.strftime("%Y-%m-%d")
            time_text = arrival.strftime("%H:%M:%S")
            append_result = (
                self.service.spreadsheets()
                .values()
                .append(
                    spreadsheetId=self.spreadsheet_id,
                    range=self._range("A:E"),
                    valueInputOption="RAW",
                    insertDataOption="INSERT_ROWS",
                    body={
                        "values": [
                            [
                                full_name,
                                date_text,
                                time_text,
                                "Present",
                                submission_id,
                            ]
                        ]
                    },
                )
                .execute()
            )
            updates = append_result.get("updates", {})
            if updates.get("updatedRows") != 1:
                raise AttendanceStoreError(
                    "Google Sheets did not confirm that the attendance row was saved."
                )
            return date_text, time_text


def create_app(test_config: dict[str, Any] | None = None) -> Flask:
    app = Flask(__name__)
    app.config.from_mapping(OFFICE_TIMEZONE="Africa/Lagos")
    if test_config:
        app.config.update(test_config)

    @app.after_request
    def set_response_headers(response: Any) -> Any:
        response.headers["Cache-Control"] = "no-store"
        response.headers["X-Content-Type-Options"] = "nosniff"
        return response

    @app.route("/", methods=["GET", "POST"])
    def check_in() -> Any:
        if request.method == "GET":
            try:
                ZoneInfo(
                    os.environ.get(
                        "OFFICE_TIMEZONE", app.config["OFFICE_TIMEZONE"]
                    )
                )
            except ZoneInfoNotFoundError:
                logger.exception("The configured office timezone is invalid.")
                return render_page(
                    submission_id=str(uuid.uuid4()),
                    full_name="",
                    message=(
                        "The office check-in service is not configured correctly. "
                        "Please contact the office."
                    ),
                    success=False,
                ), 503
            return render_page(submission_id=str(uuid.uuid4()), full_name="")

        full_name = request.form.get("full_name", "").strip()
        submission_id = request.form.get("submission_id", "").strip()
        if not full_name:
            return render_page(
                submission_id=str(uuid.uuid4()),
                full_name="",
                message="Please enter your full name before checking in.",
                success=False,
            ), 400
        if len(full_name) > 200:
            return render_page(
                submission_id=str(uuid.uuid4()),
                full_name=full_name[:200],
                message="Your name must be 200 characters or fewer.",
                success=False,
            ), 400
        try:
            submission_id = str(uuid.UUID(submission_id))
        except (ValueError, AttributeError):
            return render_page(
                submission_id=str(uuid.uuid4()),
                full_name=full_name,
                message="This check-in form has expired. Please submit the refreshed form.",
                success=False,
            ), 400

        try:
            store = current_app.extensions.get("attendance_store")
            if store is None:
                store = GoogleSheetsAttendanceStore.from_environment()
                current_app.extensions["attendance_store"] = store
            saved_date, arrival_time = store.save(full_name, submission_id)
        except Exception:
            logger.exception("Unable to save or confirm office attendance.")
            return render_page(
                submission_id=submission_id,
                full_name=full_name,
                message=(
                    "We couldn't confirm your check-in. Please try again using this "
                    "form. If a previous attempt reached the sheet, retry protection "
                    "will prevent a duplicate."
                ),
                success=False,
            ), 503

        return render_page(
            submission_id=submission_id,
            full_name=full_name,
            message=f"Check-in confirmed for {saved_date}.",
            arrival_time=arrival_time,
            success=True,
            show_form=False,
        )

    return app


def render_page(
    submission_id: str,
    full_name: str,
    message: str = "",
    arrival_time: str = "",
    success: bool = False,
    show_form: bool = True,
) -> str:
    return render_template_string(
        PAGE,
        submission_id=submission_id,
        full_name=full_name,
        message=message,
        arrival_time=arrival_time,
        success=success,
        show_form=show_form,
    )


app = create_app()

if __name__ == "__main__":
    app.run(
        host="0.0.0.0",
        port=int(os.environ.get("PORT", "5000")),
        debug=False,
    )
