# Office attendance check-in

This Flask app provides a public, mobile-friendly check-in form. Visitors do not
need a Google account. The server writes attendance to a private Google Sheet
and confirms the check-in only after Google Sheets confirms the row was saved.
The sheet contains four attendance fields and a fifth internal submission ID
used to make retries idempotent. Keep the sheet private; visitors cannot view
its rows through this app.

The default office timezone is `Africa/Lagos`. Set `OFFICE_TIMEZONE` to another
IANA timezone if the office location changes.

## 1. Create and configure the private Google Sheet

1. Create a spreadsheet in Google Sheets and add a tab named `Attendance`
   (or choose another tab name for `GOOGLE_SHEET_TAB`).
2. The app creates the header row on its first check-in. If you add it yourself,
   use these exact columns in order: `Full Name`, `Date`, `Arrival Time`,
   `Status`, `Submission ID`. Keep the fifth column for retry protection. You
   may hide that column in the sheet UI.
3. Copy the spreadsheet ID from its URL. It is the text between `/d/` and
   `/edit` in a URL like `https://docs.google.com/spreadsheets/d/SHEET_ID/edit`.
4. In [Google Cloud Console](https://console.cloud.google.com/), create/select a
   project, enable **Google Sheets API**, and create a **service account** under
   **IAM & Admin → Service Accounts**.
5. Create a JSON key for that service account and store it securely. Find the
   service account email (it ends in `iam.gserviceaccount.com`) and share the
   spreadsheet with that email as an **Editor**. Keep general link sharing off:
   visitors do not need access to the sheet.
6. Configure `GOOGLE_SHEET_ID`, `GOOGLE_SHEET_TAB`, `OFFICE_TIMEZONE`, and
   either `GOOGLE_SERVICE_ACCOUNT_FILE` or `GOOGLE_SERVICE_ACCOUNT_JSON`.
   Prefer keeping the service-account key file outside the repository and
   setting the absolute file path. If using the JSON variable, it must contain
   the complete one-line JSON object. Never place the actual key in source code,
   this README, Git, or chat. Rotate/delete the key if it is accidentally exposed.

## 2. Run on the office Wi-Fi (Windows)

Install Python 3.10 or later. In PowerShell, open this folder and run:

```powershell
py -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
Copy-Item .env.example .env
```

Edit `.env` with the spreadsheet ID and credentials. `.env` is excluded by
`.gitignore`; keep it readable only by the Windows account that runs the app and
never commit or share it. Prefer placing the downloaded key JSON file outside
this repository, restricting its Windows file permissions to the account that
runs the app, and setting `GOOGLE_SERVICE_ACCOUNT_FILE` in `.env` to its
absolute path. The app reads the file on the server and never sends the
credential to a visitor.

For a temporary local test only, you can instead load the JSON into the current
PowerShell session:

```powershell
$env:GOOGLE_SERVICE_ACCOUNT_JSON = (Get-Content .\service-account.json -Raw)
```

Set the other values in `.env`, or temporarily in PowerShell:

```powershell
$env:GOOGLE_SHEET_ID = "your-spreadsheet-id"
$env:GOOGLE_SHEET_TAB = "Attendance"
$env:OFFICE_TIMEZONE = "Africa/Lagos"
.\.venv\Scripts\waitress-serve.exe --listen=0.0.0.0:5000 officeapp:app
```

Use `.env` (or persistent Windows environment variables) for a scheduled task;
temporary `$env:` values from a PowerShell window are not inherited by a task
started later.

Waitress is a Windows-compatible production WSGI server. The app listens on all
network interfaces (`0.0.0.0`) with Flask debug mode disabled. On the computer,
test `http://127.0.0.1:5000`. To find its current Wi-Fi IPv4 address, run:

```powershell
Get-NetIPAddress -InterfaceAlias "Wi-Fi" -AddressFamily IPv4 |
  Where-Object { $_.AddressState -eq "Preferred" } |
  Select-Object -ExpandProperty IPAddress
```

For this computer, the current Wi-Fi IPv4 address is **192.168.1.54**, so the
current phone URL is **http://192.168.1.54:5000/**. This address can change
unless you reserve it on the router. Phones must be connected to the same
office Wi-Fi; some guest Wi-Fi networks block communication between devices.

### Windows Firewall (Private network only)

Windows currently reports this Wi-Fi network as **Public**. Only change it to
**Private** if it is your trusted office network: open **Settings → Network &
internet → Wi-Fi → the connected network's properties**, then select **Private
network**. Do not mark a public or untrusted hotspot as Private.

After confirming it is the trusted office Wi-Fi, open **PowerShell as
Administrator** and create a narrowly scoped inbound rule for the app's TCP
port, on Private networks only, from the local subnet:

```powershell
New-NetFirewallRule -DisplayName "Office Attendance (Waitress)" `
  -Direction Inbound -Action Allow -Protocol TCP -LocalPort 5000 `
  -Profile Private -RemoteAddress LocalSubnet
```

To remove this rule later, run PowerShell as Administrator:

```powershell
Remove-NetFirewallRule -DisplayName "Office Attendance (Waitress)"
```

Do not create a Public-profile firewall rule or router port forwarding.

### Reserve the computer's Wi-Fi IP

The address `192.168.1.54` is the current DHCP address, not a guarantee that it
will stay assigned. In the router's **LAN/DHCP reservation** settings, reserve
`192.168.1.54` for this computer's Wi-Fi adapter (identified by its Wi-Fi MAC
address). Save the reservation and reconnect the computer; confirm the same IP
with the command above. Router menus differ, so use the router's documentation.
If the router cannot reserve an address, the QR code will need updating whenever
the computer receives a different IP.

### Start automatically when you sign in

First install dependencies and test the Waitress command above. Then open
**Task Scheduler → Create Task**:

1. On **General**, choose the same Windows account that can read `.env` and
   select **Run whether user is logged on or not**. Windows will ask for that
   account's password so the task can run in the background.
2. On **Triggers**, add **At log on** for that account.
3. On **Actions**, choose **Start a program**:
   - **Program/script:** the full path to
     `Graty\.venv\Scripts\waitress-serve.exe`
   - **Add arguments:** `--listen=0.0.0.0:5000 officeapp:app`
   - **Start in:** the full path to the `Graty` folder.
4. On **Conditions**, clear any setting that requires the computer to be idle
   or on AC power if that is appropriate, and save the task.
5. Sign out and back in (or run the task once), then visit
   `http://127.0.0.1:5000` on the computer and the LAN URL from a phone.

The account running the task must be able to read `.env`, which stays excluded
from Git. For better key isolation, store the file with Windows permissions
limited to that account. No credential is placed in the Task Scheduler command
line.

The computer must remain powered on, connected to office Wi-Fi, and awake.
Sleep, shutdown, Wi-Fi disconnection, or an IP change will make the local URL
unavailable. No router port forwarding is needed or configured.
This LAN URL uses HTTP, not HTTPS: only use it on a trusted office Wi-Fi network.
The check-in form remains public to devices on that network, but the private
Google Sheet and service-account key are not exposed to them.

## 3. Optional hosted deployment

Deploy this folder to a Python-compatible web host, such as a managed Python web
service. GitHub Pages hosts static sites and **cannot run this Python/Flask
backend**. Example host build and start commands:

```text
Build: pip install -r requirements.txt
Start: waitress-serve --listen=0.0.0.0:$PORT officeapp:app
```

Use the host's secret/environment-variable settings for all configuration,
especially `GOOGLE_SERVICE_ACCOUNT_JSON`; do not upload the key file or `.env`.
Set `OFFICE_TIMEZONE=Africa/Lagos`, `GOOGLE_SHEET_ID`, and `GOOGLE_SHEET_TAB`
there as well. Configure a **single running Waitress process**: the app
serializes check-ins inside that process, and the sheet's
internal submission ID handles retries after a restart. Do not horizontally
scale multiple app instances; Google Sheets does not provide a unique-key
transaction for coordinating simultaneous writers across separate instances.

Choose a plan that keeps a web service running if continuous availability is
important. Free or low-cost plans may sleep after inactivity, have cold-start
delays, monthly usage limits, or scale to zero. These limits vary by provider
and plan and can change, so check the host's current plan documentation before
relying on it. A sleeping app is not continuously responsive, even though
your computer and VS Code can be off.

After deployment, confirm that the provider gives the app a public HTTPS URL
and test a check-in. The first check-in also verifies Google API enablement,
the service-account key, sheet ID/tab, and sheet sharing. Keep that URL for the
QR code below.

## 4. Create the office QR code

For the same-office Wi-Fi setup, generate a QR code for the current private LAN
URL:

```powershell
python generate_qr.py http://192.168.1.54:5000/
```

The script creates `office-checkin-qr.png` in the current folder. Print and
scan it while connected to the office Wi-Fi. If you later choose hosted
deployment instead, use its final public HTTPS URL, never `localhost`.

## Privacy and physical presence

The page tells visitors that their name and arrival time are recorded. It does
not expose attendance records or Google credentials to visitors. A QR scan is
not proof that someone is physically at the office: a link or a picture of the
QR code can be shared and scanned elsewhere.

## Tests

Run the app tests with:

```powershell
python -m unittest -v
```

The tests use a fake Sheets API service and cover blank-name validation,
successful saves and server time, formula-like names written as raw text,
retries, same-name visitors, and Sheets connection failures. They do not verify
your Google Cloud/API setup or an actual deployment. Those checks require the
service account, enabled API, sheet sharing, configured secrets, and a
deployed HTTPS URL.
