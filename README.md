# Namakkal Market — native Android source

Kotlin + Jetpack Compose native Android app. Reads directly from the customer-only Google Sheets workbook using Google Sheets API v4. No Make, no Make webhook, and no Apps Script.

Customer data source: `Namakkal Market – Customer Prices 2026` (October, November, December 2026 tabs only). The source workbook's Master Items, Instructions & Rules, and Post Log tabs are excluded.

Implemented in source:
- Monthly worksheet discovery and direct read-only sheet fetch.
- Tamil names, units, and Low/High prices from the live sheet layout.
- Latest available date fallback based on actual numeric price entries (not just a date header), labelled with its actual date rather than presented as today's prices.
- Previous available date comparison using arrow-only indicator (up/down/flat; no numeric delta).
- Weekly (last 7 days) and monthly history cards with simple midpoint trend charts (currently based on the selected month tab).
- Offline cache of the most recently fetched month data.
- WhatsApp sharing through Android share sheet.
- Read-only customer interface.

Workbook access:
- Customer-only workbook is `Anyone with the link — Viewer` (link-only, not discoverable).
- Original source workbook is not modified or shared.
- Customer workbook: https://docs.google.com/spreadsheets/d/1yOBoBrT2swBvPnnI5kzJysr71OgCY1vbSiG9qAmWw-8/edit

Required before public release:
1. Enable Google Sheets API v4 in Google Cloud and create an API key restricted to this app's package/signing certificate and Google Sheets API.
2. Add `GOOGLE_SHEETS_API_KEY=...` to local Gradle properties. Never commit the key.
3. Build with Android Studio, JDK 17 and Android SDK 35 using `:app:assembleDebug`.
4. Complete release signing and device QA.

Not yet implemented: release signing, optional customer registration, and cross-month weekly history aggregation. The source has not been compiled or device-tested. This repository contains source code, not an APK.
