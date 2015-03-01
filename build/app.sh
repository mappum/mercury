#!/bin/sh

APP_OUTPUT="target/Mercury Wallet.app"
cp -r build/app.app "$APP_OUTPUT"
cp "target/$1" "$APP_OUTPUT/Contents/Resources/Jars"
