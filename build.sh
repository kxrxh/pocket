#!/bin/sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
sdk_dir=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
if [ -z "$sdk_dir" ]; then
    if [ -d "$HOME/Library/Android/sdk" ]; then sdk_dir="$HOME/Library/Android/sdk";
    elif [ -d "$HOME/Android/Sdk" ]; then sdk_dir="$HOME/Android/Sdk";
    else echo 'Set ANDROID_SDK_ROOT to your Android SDK directory.' >&2; exit 1; fi
fi
java_dir=${JAVA_HOME:-}
if [ -n "$java_dir" ]; then export PATH="$java_dir/bin:$PATH";
elif [ -d '/Applications/Android Studio.app/Contents/jbr/Contents/Home' ]; then
    export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
    export PATH="$JAVA_HOME/bin:$PATH"
fi
for command_name in jar keytool zip; do
    command -v "$command_name" >/dev/null 2>&1 || { echo "Missing command: $command_name" >&2; exit 1; }
done
kotlin_home=${KOTLIN_HOME:-}
if [ -z "$kotlin_home" ]; then
    if command -v kotlinc >/dev/null 2>&1; then
        kotlin_executable=$(command -v kotlinc)
        kotlin_home=$(CDPATH= cd -- "$(dirname -- "$kotlin_executable")/.." && pwd)
    elif [ -d '/Applications/Android Studio.app/Contents/plugins/Kotlin/kotlinc' ]; then
        kotlin_home='/Applications/Android Studio.app/Contents/plugins/Kotlin/kotlinc'
    else echo 'Install the Kotlin compiler or set KOTLIN_HOME.' >&2; exit 1; fi
fi
[ -x "$kotlin_home/bin/kotlinc" ] && [ -f "$kotlin_home/lib/kotlin-stdlib.jar" ] || { echo 'KOTLIN_HOME must contain bin/kotlinc and lib/kotlin-stdlib.jar.' >&2; exit 1; }
kotlin_version=$("$kotlin_home/bin/kotlinc" -version 2>&1)
case "$kotlin_version" in
    *"kotlinc-jvm 2.2."*) ;;
    *) echo 'Use Kotlin 2.2.x with this build toolchain (tested with 2.2.21).' >&2; exit 1 ;;
esac
tools_dir="$sdk_dir/build-tools/${ANDROID_BUILD_TOOLS_VERSION:-36.1.0}"
android_jar="$sdk_dir/platforms/android-${ANDROID_PLATFORM_VERSION:-36}/android.jar"
[ -f "$android_jar" ] && [ -x "$tools_dir/aapt2" ] || { echo 'Install Android platform 36 and build-tools 36.1.0, or configure their version variables.' >&2; exit 1; }
build_dir="$project_dir/.build"
keystore_path=${POCKET_KEYSTORE:-"$project_dir/.keys/test.keystore"}
mkdir -p "$build_dir/classes" "$build_dir/dex" "$project_dir/dist"
# Clear compiled output to avoid packaging classes removed from the source.
rm -f "$build_dir/classes.jar" "$build_dir/dex"/classes*.dex
find "$build_dir/classes" -type f -name '*.class' -delete
"$tools_dir/aapt2" compile --dir "$project_dir/res" -o "$build_dir/resources.zip"
"$tools_dir/aapt2" link -o "$build_dir/base.apk" -I "$android_jar" --manifest "$project_dir/AndroidManifest.xml" -A "$project_dir/assets" -R "$build_dir/resources.zip"
"$kotlin_home/bin/kotlinc" -jvm-target 1.8 -classpath "$android_jar" -d "$build_dir/classes" "$project_dir/src/dev/fuelwallet/lukoiltest/MainActivity.kt"
jar cf "$build_dir/classes.jar" -C "$build_dir/classes" .
"$tools_dir/d8" --min-api 26 --lib "$android_jar" --output "$build_dir/dex" "$build_dir/classes.jar" "$kotlin_home/lib/kotlin-stdlib.jar"
cp "$build_dir/base.apk" "$build_dir/unsigned.apk"
(cd "$build_dir/dex" && zip -q "$build_dir/unsigned.apk" classes*.dex)
"$tools_dir/zipalign" -f 4 "$build_dir/unsigned.apk" "$build_dir/aligned.apk"
if [ ! -f "$keystore_path" ]; then
    mkdir -p "$(dirname -- "$keystore_path")"
    keytool -genkeypair -keystore "$keystore_path" -alias test -storepass android -keypass android -dname 'CN=Pocket Local Test' -keyalg RSA -keysize 2048 -validity 3650
fi
"$tools_dir/apksigner" sign --ks "$keystore_path" --ks-key-alias test --ks-pass pass:android --key-pass pass:android --out "$project_dir/dist/Pocket.apk" "$build_dir/aligned.apk"
"$tools_dir/apksigner" verify "$project_dir/dist/Pocket.apk"
printf 'Built %s\n' "$project_dir/dist/Pocket.apk"
