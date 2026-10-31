# Namakkal Market — native Android source

Kotlin + Jetpack Compose native Android app. Reads customer-facing market prices from the new Supabase project. Google Sheets is the source of truth; Supabase stores a read-only customer copy for the app. Dropbox is no longer used.

## Data flow

Google Sheets customer workbook → Supabase Edge Function → Supabase Postgres → Android app

- Source workbook: Namakkal Market – Customer Prices 2026
- Source tabs: October 2026, November 2026, December 2026
- Supabase project: `namakkal-market-sync` (Mumbai region)
- Daily sync: 06:30 AM Asia/Kolkata (01:00 UTC), scheduled with Supabase Cron.
- The Edge Function validates the monthly Low/High date columns, upserts prices, and records sync status.
- Android reads only the customer-safe `market_prices` table using a Supabase publishable key. No service-role key is present in the APK.
- `sync_runs` is internal and not readable by the public app.

## Implemented in source

- Read-only Supabase REST API connection; no Dropbox links or Google credentials in the APK.
- Tamil names, units, and separate Low/High prices.
- Latest available date fallback based on actual numeric price entries, labelled with its actual date rather than presented as today's prices.
- Previous available date comparison using arrow-only indicator (up/down/flat; no numeric delta).
- Weekly (last 7 days) and monthly history cards with simple trend charts.
- Offline cache of the most recently fetched data.
- WhatsApp sharing through Android share sheet.
- Read-only customer interface.

## Source workbook data limitation

The workbook currently has 52 columns per monthly tab. That leaves the final date pair incomplete (25th Low column only) and dates 26–31 are not present in the current tab layout. The sync safely imports only complete Low/High date pairs. Extend the monthly sheet to include all dates through month-end so future daily prices can be imported. The database currently contains 725 price rows, with the latest source date 24 October 2026; it does not invent missing prices.

## Build

- Android Studio, JDK 17, Android SDK 35.
- Build with `:app:assembleDebug`.
- GitHub Actions workflow builds a debug APK and uploads it as an artifact on pushes to `main`.

## Remaining before public release

- Verify the GitHub Actions build succeeds.
- Complete release signing and device QA.
- Confirm the source workbook has complete daily Low/High columns through month-end.
- The repository contains source code, not a tested release APK.
