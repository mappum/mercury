#!/bin/sh

# build .app
APP_OUTPUT="target/Mercury Wallet.app"
cp -r build/app.app "$APP_OUTPUT"
cp "target/$1.jar" "$APP_OUTPUT/Contents/Resources/Jars"

# build .dmg
DMG_TEMP="target/temp.dmg"
DMG_OUTPUT="target/$1.dmg"
cp build/app.dmg "$DMG_TEMP"
hdiutil attach "$DMG_TEMP"
MOUNT_PATH="/Volumes/Mercury Wallet"
cp -r "$APP_OUTPUT" "$MOUNT_PATH"
hdiutil detach "$MOUNT_PATH"
hdiutil convert "$DMG_TEMP" -format UDZO -imagekey zlib-level=9 -o "$DMG_OUTPUT"
rm "$DMG_TEMP"
