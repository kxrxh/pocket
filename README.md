# Карман / Pocket

Android app for loyalty cards from Лукойл, Teboil, Газпромнефть and Татнефть.

<img src="docs/home.png" alt="Карман" width="280" />

## Use

Download the APK from [Releases](https://github.com/kxrxh/pocket/releases). Choose a station and sign in on its official page.

Open your QR code and tap **Показать на кассе** to brighten the screen and keep it awake. **Готово** returns to normal view.

Logins are saved on your device. Stations can still expire them. Offline cards are not supported; Tatneft's QR flow needs testing.

## Build

Requires a JDK, Android SDK platform 36, build-tools 36.1.0, and `zip`.

```sh
export ANDROID_SDK_ROOT=/path/to/Android/sdk
export JAVA_HOME=/path/to/jdk
./build.sh
```

APK: `dist/Pocket.apk`. Supports Android 8+.

The script creates a local test signing key. Keep it for future updates; it is ignored by Git.
