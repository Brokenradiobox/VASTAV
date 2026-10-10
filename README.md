# VASTAV: local database + email alerts

This starter serves your existing VASTAV website, saves reports to a SQLite database on the computer running the server, and can email new-report notifications to `rastogipuranjay42@gmail.com`.

## What is and isn't automatic
- Reports submitted while connected to this server are stored in `vastav.sqlite` on the server computer.
- Email notifications require valid SMTP settings in `.env`.
- `localStorage` in the browser remains a temporary/offline queue; it is not the central database.
- If you want people outside your home/network to submit reports, the server must be hosted on an internet-accessible computer/server. The database then lives on that server, not magically on your personal PC.
- This starter sends a notification email; it does not attach all report data or photos. Avoid collecting sensitive data you do not need.

## Run on Windows
1. Install Node.js 18 or newer from https://nodejs.org/.
2. Extract this ZIP to a folder.
3. Open Command Prompt/PowerShell in that folder.
4. Run `npm install`.
5. Copy `.env.example` to `.env`.
6. Edit `.env`. To enable email, configure SMTP. For Gmail, enable 2-Step Verification and create an App Password at https://myaccount.google.com/apppasswords. Use that app password in `SMTP_PASS`; do not use your normal Gmail password.
7. Run `npm start`.
8. Open http://localhost:3000 in your browser.

The SQLite database is created automatically as `vastav.sqlite` in this folder (unless `DB_PATH` is changed). Back it up regularly. The server must remain running to accept submissions.

## Important privacy/security notes
- The website collects names, phone numbers, email addresses, descriptions and locations. Tell users what you collect and why, secure access to the server, and limit who can access backups.
- Do not commit `.env` or share SMTP credentials.
- Before public launch, add HTTPS, rate limiting, anti-spam controls, access controls for official/admin views, backups and a privacy notice.
- The website's existing dashboard is not a private admin panel. Do not expose private reports publicly without reviewing the access/privacy design.
- Email can fail independently of database saving; check server logs if notifications do not arrive.

## Convert it into an app
The supplied HTML already includes a web-app manifest link and an install-app button, but this ZIP does not include the referenced manifest, service worker or icon assets. First run it from a web server (not by double-clicking the HTML). For a mobile installable PWA, add valid `manifest.webmanifest`, icons, and a service worker, and serve it over HTTPS. For an Android APK, a common next step is Capacitor: create a web app project, install `@capacitor/core` and `@capacitor/android`, run `npx cap add android`, then `npx cap sync` and open the Android project in Android Studio. The app must call your deployed API URL, not `localhost` on the user's phone. Never embed SMTP passwords or admin secrets in the app.

This project is a starter backend, not a production-hardened public service.
