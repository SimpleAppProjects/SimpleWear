---
layout: default
title: Enable WRITE_SECURE_SETTINGS permission
permalink: /secure-settings-access
---

# Enable WRITE_SECURE_SETTINGS permission

In order for SimpleWear to change **location** settings without root access, it needs the WRITE_SECURE_SETTINGS permission. As this is a system permission, it cannot be requested in app and has to be enabled manually. Please use the following tools to enable the permission.

### **Windows**
1. **Download the ZIP file**

    Download the ZIP file [here]({{ site.github.repository_url}}/releases/download/v1.9.0_r0/SettingsEnabler.zip), and unzip it to a folder on your computer.

2. **Enable USB Debugging**

    Enable **Developer Options**. Go to the system **Settings** app. In the **About Phone** section, find the **Build Number** option. Tap **7 times** on the **Build Number** option to enable Developer Options.

    Next, find the new **Developer Options** section. This should be on the previous page or in the **System** section (this may be different on your device).

    In **Developer Options**, enable **USB Debugging**. Once it is enabled, connect your device to your computer via USB. If a popup appears asking to enable **USB Debugging**, click **Allow**.

3. **Run the tool**

    Once your device is connected, find the folder where you extracted the .zip file.

    Double click the _**run.bat**_ file to run the tool.

4. **All done!**

    You should now have the required permissions. Restart the SimpleWear app on your phone to verify if the permission was enabled.

    You may disable **Developer Options** as it is no longer needed.

### **Linux**
1. **Download ADB (SDK Platform Tools)**

    Download [SDK Platform-Tools for Linux](https://developer.android.com/studio/releases/platform-tools.html), and extract the .zip file to a folder on your computer.

2. **Enable USB Debugging**

    Follow the same instructions to enable **USB Debugging** on Windows.

3. **Run the following commands**

    Once your device is connected, find the folder where you extracted the .zip file. 

    Open the Terminal in the unzipped folder and run the following commands:

    `./adb shell pm grant com.thewizrd.simplewear android.permission.WRITE_SECURE_SETTINGS`

    `./adb shell pm grant com.thewizrd.wearsettings android.permission.WRITE_SECURE_SETTINGS`

4. **All done!**

    You should now have the required permissions. Restart the SimpleWear app on your phone to verify if the permission was enabled.

    You may disable **Developer Options** as it is no longer needed.

### ADB Commands
Already have or know how to use ADB? Just run the following commands:

`./adb shell pm grant com.thewizrd.simplewear android.permission.WRITE_SECURE_SETTINGS`

`./adb shell pm grant com.thewizrd.wearsettings android.permission.WRITE_SECURE_SETTINGS`

### Errors
If you see a similar error when running the script:

`Exception occurred while executing 'grant':
java.lang.SecurityException: grantRuntimePermission: Neither user xxxx nor current process has android.permission.GRANT_RUNTIME_PERMISSIONS.`

Please check if you have one of the following settings in Developer options and enable it:

- USB debugging (Security settings)
- Disable Permission Monitoring

Lastly, reboot and try again