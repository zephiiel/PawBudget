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

- Billing-period overview with budget remaining, paid spending, credit spending, category and account charts, and yearly period spending.
- Add, edit, and delete transactions; filter transactions by tapping a category or account chart.
- Pending and paid credit payment lists, individual status changes, and bulk status changes.
- Billing-period budgets and to-do items with optional task amounts, multi-select totals, projected remaining-budget totals, and bulk completion.
- Configurable billing-cycle start day (1st–31st, with short months ending on their last day).
- On-device automatic persistence and Android system file picker for JSON backup import/export.
- Latest GitHub Release checks, APK download progress, archive validation, and Android's package installer for NativePaw updates.

NativePaw uses the existing Android application ID `com.zephiiel.ledger` so a correctly signed version can replace the Capacitor app. NativePaw supports the existing `ledger-monthly-tracker` version 1 JSON backup format to transfer data from the web or Capacitor app; import a backup after installing because the native app cannot directly read data kept in the old WebView.

The initial NativePaw release is version `2.0` (`versionCode` 6), above the previous Capacitor version code 5. To publish it, attach a release-signed APK named `NativePaw-2.0.apk` to a public GitHub Release tagged `v2.0`. Keep using the same signing key as the installed Capacitor APK; Android will reject an update signed with a different key. Release signing is not configured in this project, so provide the existing key securely in Android Studio/Gradle before publishing. The updater checks GitHub only when the user selects **Check for updates** and only offers APK assets named `NativePaw-*.apk`.
