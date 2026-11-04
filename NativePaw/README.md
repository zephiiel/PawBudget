# PawBudget Native for Android

This is a standalone, fully native Android version of PawBudget. It is built with Kotlin and Jetpack Compose and does not use Capacitor, a WebView, or the web app's source files. The project is isolated in this folder; the existing web and Capacitor projects are not modified by the native app.

## Open and run

Open this `NativePaw` folder in Android Studio, allow Gradle to sync, then run the `app` configuration on an Android device or emulator. The project uses the Gradle wrapper and requires JDK 21 and Android SDK 36.

From PowerShell, a debug APK can be built with:

```powershell
.\gradlew.bat assembleDebug
```

The APK is written to `app\build\outputs\apk\debug\app-debug.apk`.

## Features

- Billing-period overview with one shared excluded-account setting for budget remaining, total used, summary totals, charts, and transaction history, plus account totals for paid and credit spending and yearly period spending.
- Add, edit, and delete transactions from transaction cards and their edit dialog; filter transactions by tapping a category or account chart.
- Pending and paid credit payment lists, bulk paid-status changes, and moving selected unpaid payments to the next statement period without changing the actual spending period, transaction date, budgets, or spending graphs.
- Budget cards open their editor, where budgets can also be deleted.
- Billing-period budgets and to-do items with optional task amounts, compact selectable cards, multi-select totals, projected remaining-budget totals, and bulk completion.
- Configurable billing-cycle start day (1st–31st, with short months ending on their last day).
- On-device automatic persistence, `.pawbudget` backup export and sharing, and import support for both PawBudget backups and existing JSON backups.
- Latest GitHub Release checks, APK download progress, archive validation, and Android's package installer for NativePaw updates.

NativePaw uses the existing Android application ID `com.zephiiel.ledger` so a correctly signed version can replace the Capacitor app. NativePaw supports the existing `ledger-monthly-tracker` version 1 JSON backup format to transfer data from the web or Capacitor app; import a backup after installing because the native app cannot directly read data kept in the old WebView.

NativePaw version `2.3` uses `versionCode` 9, above version 2.2's version code 8. Attach a release-signed APK to a public GitHub Release tagged `v2.3`, preferably named `NativePaw-2.3.apk` so older installed app versions can recognize it. The current updater also recognizes `PawBudget.v2.3.apk`. A Gradle debug APK can be renamed for recognition, but it is signed with the debug key and can update only an installation signed with that same key; Android rejects updates signed with a different key. Keep using the same signing key as the installed app. Release signing is not configured in this project, so provide the existing key securely in Android Studio/Gradle before publishing. The updater checks GitHub only when the user selects **Check for updates**.
