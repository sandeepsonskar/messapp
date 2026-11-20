# Mess Scanner - Android app for mess supervisors

A small app that opens ONLY your mess server, allows the camera for the QR scanner, and refuses any login that is not a
mess-supervisor login (students and admin must use the website). No browser warning: the app remembers the server's
certificate the first time it connects.

## 1. Set your server address
Open  app/src/main/java/com/vsipl/messscanner/MainActivity.java  and change
    private static final String DEFAULT_SERVER = "https://10.2.4.22:5443";
to your server's HTTPS address. (It can also be changed later inside the app - see "Problems" below.)

IMPORTANT: the server must be updated with the newest mess-system.zip (it adds the "supervisors only" check).

## 2. Build the APK - choose ONE way

### Way A: Android Studio (on your Windows PC)
1. Install Android Studio (free, developer.android.com/studio).
2. File -> Open -> choose this folder (mess-supervisor-app). Wait until "Gradle sync" finishes (first time downloads a lot).
3. Menu  Build -> Build Bundle(s) / APK(s) -> Build APK(s).
4. Click "locate" in the pop-up. The file is  app/build/outputs/apk/debug/app-debug.apk

### Way B: GitHub (no software to install)
1. Create a free GitHub account and a new PRIVATE repository.
2. Upload ALL files of this folder to it (drag and drop in the browser; keep the folder structure, including the hidden .github folder).
3. Open the Actions tab -> "Build APK" -> Run workflow. After ~5 minutes open the finished run and download the artifact "mess-scanner-apk".
4. Unzip it: app-debug.apk is inside.

## 3. Install on the supervisors' phones
1. Copy app-debug.apk to the phone (USB, WhatsApp to yourself, Google Drive...).
2. Tap it. If asked, allow "Install unknown apps" for that file manager/browser. Play Protect may say the app is unknown - choose "Install anyway".
3. Open "Mess Scanner", allow the camera, log in with the supervisor ID and password.
4. Tap "Scan student QR". Done.

## Problems
- "Cannot connect": phone is not on the mess network, or the server address is wrong -> tap "Change address".
- "Server certificate changed": happens if the server's IP changed (the certificate is renewed). If the admin did that, tap "Trust new".
- To reset everything: Android Settings -> Apps -> Mess Scanner -> Storage -> Clear data.
- "This app is for mess supervisors only": that login is a student or admin login. Use the website for those.
- If the build fails, copy the red error text and send it to me - the project was checked to compile against the Android 34 API,
  but a real Gradle build could not be run where it was written.

## Later: Play Store / release signing
The debug APK is fine for installing directly on your own phones. A Play Store release needs a signing key - ask if you need it.
