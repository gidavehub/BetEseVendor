# BetEseVendor

Native **Android vendor terminal** (Kotlin + Jetpack Compose) for the Betese PMU horse‑betting
platform. Staff sign in with their betesepmu credentials and can place bets, print tickets, pay
out winners, take customer deposits/withdrawals, view race results, print rapports, and chat with
back office — running on Sunmi‑style POS terminals with a built‑in thermal print engine
(ESC/POS, Sunmi AIDL, Bluetooth, and an on‑screen preview fallback).

| | |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Backend | Firebase Firestore (project `betesepmu-4ffc7`) |
| Min SDK | 24 |

## Features

- **Login** — username/password against the shared Firestore `users` collection (vendors & agents).
- **Place Bet** — all 11 PMU bet types, horse selector, multi‑selection slip, prints the ticket.
- **Scan / Pay** — look up a ticket or booking code → pay out, reprint, cancel, or pay a booking.
- **Finance** — record customer deposits and process withdrawals, each with a printed receipt;
  daily / end‑of‑sale reports.
- **Results & Rapport** — view official race results and print the dividend rapport.
- **Chat** — back‑office / paymaster / customer‑service support messaging.
- **Settings** — printer configuration (paper width, transport, density, Bluetooth) + account.

## Build

Requires the Android SDK and JDK 21 (the JBR bundled with Android Studio works).

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug      # output: app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat :app:testDebugUnitTest  # unit tests
```

> `app/google-services.json` configures the Firebase client. The app connects to the live
> `betesepmu-4ffc7` project, so placing bets / recording deposits writes real data.
