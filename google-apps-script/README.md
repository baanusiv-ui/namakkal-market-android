# Google Sheets feed for Namakkal Market Android

This Apps Script endpoint reads the month-named tab (for example, `November 2026`) from the private `blueticks price` spreadsheet and returns JSON containing each item's daily low/high history.

## Deploy once using the spreadsheet owner account

1. Open the spreadsheet while signed in as `psivakumar1974@gmail.com`.
2. Open **Extensions → Apps Script**.
3. Replace the editor contents with the contents of `Code.gs` from this folder.
4. Save the project as **Namakkal Market Android Feed**.
5. Select **Deploy → New deployment → Web app**.
6. Set **Execute as: Me**.
7. Set **Who has access: Anyone**. The endpoint exposes market prices only; do not add personal data to the monthly tabs.
8. Deploy, authorize the requested spreadsheet access, and copy the Web app URL ending in `/exec`.

The Android app needs that deployed `/exec` URL configured before it can fetch the sheet. Do not use the Apps Script editor URL or a `/dev` test URL.

## Refresh behavior

The Android client should refresh on app launch/resume and use WorkManager periodic network refresh (minimum interval 15 minutes). Android background execution and Google endpoint polling do not guarantee instant push. For true push delivery, a separate public webhook receiver and push-notification service are required.

## Month tab format

The script expects:
- A: Tamil name
- B: English name
- C: Unit
- D onward: date-paired columns with headers such as `2026-11-01 (Low)`, `2026-11-01 (High)`

Only populated, valid low/high pairs are returned. It automatically chooses the tab named for the current month in the spreadsheet timezone.
