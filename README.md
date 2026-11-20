# Car Service

An offline Android app (with Android Auto) that tells you when your next car service is due.

- **No account, no internet.** Everything is stored in one file on the phone.
- **Pick your car** (make, model, year, fuel type). The service list matches: diesels get fuel filter and glow plugs instead of spark plugs, DPF only from 2010 and AdBlue only from 2016; electric cars skip oil. Untick anything your car does not have. About 40 makes and 350 models sold in Europe, or type your own.
- **Service items by km and/or time**, e.g. engine oil every 15 000 km or 12 months, whichever comes first. Intervals are typical values; adjust them to your service book.
- **Kilometres from three sources:**
  1. **OBD-II** via a Bluetooth Classic ELM327 adapter. If the car reports its odometer (PID A6, mostly 2019+ cars) the app uses the real value; otherwise it adds up distance from vehicle speed.
  2. **GPS** distance while a trip is running (used automatically when no adapter is connected).
  3. **Manual entry**: tap the odometer and type the number from the dashboard at any time.
- **Reminders**: a daily check notifies you when an item is within 1 000 km or 30 days (configurable) or overdue.
- **Android Auto**: shows the odometer and the most urgent items, with a Start/Stop trip button. Opening the app in the car starts a trip automatically.

## Install

1. On your phone, open **[the latest release](../../releases/latest)** and tap **CarService.apk**. Direct link: `https://github.com/coki83/Car-Service/releases/latest/download/CarService.apk`
2. Open the downloaded file and install it (allow "Install unknown apps" for your browser). Newer releases install over the old one and keep your data.

### Android Auto

Android Auto only shows apps from the Play Store unless you allow unknown sources:

1. In the Android Auto settings on the phone, tap **Version** about 10 times to unlock developer settings.
2. In the three-dot menu, open **Developer settings** and enable **Unknown sources**.
3. Reconnect to the car. Car Service appears in the app launcher.

Android Auto only allows certain app categories. This app registers in the IoT category with a simple list template, which is fine for personal use but would need a different approach to be accepted on the Play Store.

### OBD-II adapter

1. Plug the ELM327 adapter into the OBD port (usually under the steering wheel) and pair it in Android Bluetooth settings (PIN is usually `1234` or `0000`).
2. In the app: **Settings → OBD-II adapter**, pick it. Keep the source on **Auto**.

Bluetooth LE-only adapters (some "iOS compatible" ones) are not supported yet; use a Bluetooth Classic adapter.

## How tracking works

A trip runs as a foreground service with a notification. In **Auto** mode it connects to the OBD adapter if one is chosen and reachable, otherwise it uses GPS and retries the adapter every minute. GPS ignores fixes worse than 25 m and jitter while parked, so expect a small difference from the car's odometer; correct it any time by tapping the odometer.

## Windows / browser version

`web/car-service.html` is the same app for any browser (manual odometer entry only). It is generated from `web/index.template.html` by `python3 tools/catalog.py`, which also generates the Android car list in `CarCatalog.kt`.

## Build locally

Requires JDK 17 and the Android SDK (API 35).

```
./gradlew assembleDebug
```
