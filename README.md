# Namakkal Market — native Android source

Kotlin + Jetpack Compose native Android app. Reads customer-facing monthly CSV files directly from public read-only Dropbox links over HTTPS. No Google Sheets API key, no Make, no Make webhook, and no Apps Script.

## Customer data source

The Google Sheets customer-only workbook is mirrored to Dropbox:
- October 2026
- November 2026
- December 2026

Dropbox folder: `/Namakkal Market/`

The original source workbook is not modified. The original workbook's internal tabs are excluded from the public mirror.

## Implemented in source

- Direct HTTPS download of Dropbox shared CSV links; no API key in the APK.
- Monthly CSV selection based on the device's current month and year, with latest configured month fallback.
- Tamil names, units, and Low/High prices from the exported monthly tab.
- Latest available date fallback based on actual numeric price entries, labelled with its actual date rather than presented as today's prices.
- Previous available date comparison using arrow-only indicator (up/down/flat; no numeric delta).
- Weekly (last 7 days) and monthly history cards with simple trend charts (within selected month tab).
- Offline cache of the most recently fetched month data.
- WhatsApp sharing through Android share sheet.
- Read-only customer interface.

## Dropbox mirror links

The app currently uses stable public, viewer-only Dropbox links for the three monthly CSVs. The Dropbox files should be overwritten in place when refreshed so the shared links remain stable.

## Sync limitation

The initial three CSV exports were copied and verified through Composio. The available Google Drive change-feed action provides a checkpoint/polling mechanism, not an instant Google Sheets change webhook. A true event-driven real-time sync has not been established. An hourly scheduled refresh is the fastest supported ChatGPT automation cadence; it is not instantaneous. Do not describe the mirror as real-time until an actual trigger/refresh mechanism is configured and verified.

## Build

- Android Studio, JDK 17, Android SDK 35.
- Build with `:app:assembleDebug`.
- GitHub Actions workflow builds a debug APK and uploads it as an artifact.

## Remaining before public release

- Configure an hourly refresh automation only if the owner accepts that delay; otherwise refresh the Dropbox CSVs manually.
- Verify GitHub Actions build succeeds.
- Complete release signing and device QA.
- Add future month/year CSV links to the app when the mirror is extended; this currently requires a source update and rebuild.

The repository contains source code, not a tested release APK.
