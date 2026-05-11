#!/bin/bash
echo "=== PROJECT STRUCTURE ==="
find . -name "*.kt" -o -name "*.gradle*" -o -name "*.xml" | grep -v ".git" | grep -v "build/" | sort

echo ""
echo "=== ALL KOTLIN SOURCE ==="
find . -path "./build" -prune -o -name "*.kt" -print | while read f; do
    echo ""
    echo "=== FILE: $f ==="
    cat "$f"
done

echo ""
echo "=== BUILD FILES ==="
cat build.gradle.kts
cat app/build.gradle.kts
cat core/camera-engine/build.gradle.kts
cat core/profiles/build.gradle.kts
cat gradle/libs.versions.toml
