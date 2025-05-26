---
layout: default
title: SimpleWear Settings Helper
permalink: /settings-helper
---

# SimpleWear Settings Helper

Companion app for SimpleWear

Latest version: [SimpleWear Settings v1.3.1]({{
site.github.repository_url}}/releases/download/v1.16.1_release/wearsettings-release-1.3.1.apk)

Previous version: [SimpleWear Settings v1.3.0]({{
site.github.repository_url}}/releases/download/v1.16.0_beta/wearsettings-release-1.3.0.apk)

## WiFi and Location Toggle

NOTE: As of Android 10 (or Q), non-system apps are no longer allowed to toggle Wi-Fi on or off. This helper app is needed in order to allow SimpleWear to toggle Wi-Fi. The helper app is built for an older version of Android which allows it to be able to toggle Wi-Fi.

**SimpleWear**, on its own, is unable to change system settings like mobile data and location. 
In order to change these settings, the app requires the **WRITE_SECURE_SETTINGS** permission. This has to be enabled manually by the user and cannot be requested/enabled within the app.

If you are using Android 10 and above, and want to toggle **Wi-Fi** settings or are on any Android version and want to toggle **location and mobile data**, the **SimpleWear Settings** helper app is needed.

## Mobile Data

SimpleWear is unable to toggle mobile data without system permissions. [Root access](./root-access)
or for unrooted devices, [Shizuku](https://github.com/RikkaApps/Shizuku) can be used. Please follow
the instructions in app to start.

## Bluetooth

As of Android 13 (or T), non-system apps are no longer allowed to toggle Bluetooth on or off. This
helper app is needed in order to allow SimpleWear to toggle Bluetooth. The helper app is built for
an older version of Android which allows it to be able to toggle Bluetooth.