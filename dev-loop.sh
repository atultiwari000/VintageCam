#!/bin/bash
set -e

echo "🚀 Building..."
./gradlew assembleDebug --quiet

echo "📲 Installing..."
adb install -r app/build/outputs/apk/debug/app-debug.apk

echo "🧹 Clearing logs..."
adb logcat -c

echo "📋 Watching logs (Ctrl+C to stop)..."
adb logcat -v threadtime | grep --color=always -i "vintagecam\|FATAL\|AndroidRuntime\|Exception\|NullPointer\|Process: com.vintagecam"
