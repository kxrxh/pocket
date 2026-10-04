# Build

Requires a JDK, Kotlin 2.2.21 compiler, Android SDK platform 36, build-tools 36.1.0, and `zip`.

```sh
export ANDROID_SDK_ROOT=/path/to/Android/sdk
export JAVA_HOME=/path/to/jdk
export KOTLIN_HOME=/path/to/kotlinc
./build.sh
```

APK: `dist/Pocket.apk`. Android 8+.

The script creates a test key in `.keys/`, excluded from Git. Keep the same key for app updates. Use `POCKET_KEYSTORE` to select an existing test key. Release signing needs a separate production setup.
