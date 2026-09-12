# Build Pocket Pet APK

## GitHub Actions (recommended)
This project includes `.github/workflows/build-apk.yml`.

1. Push the project to a GitHub repository.
2. Open **Actions > Build Pocket Pet APK**.
3. Choose **Run workflow**.
4. When the job finishes, download artifact **PocketPet-debug-apk**.
5. Extract `app-debug.apk` and install it on Android.

The current Android app is offline and does not request Internet permission.

## Android Studio
Open this folder in Android Studio, allow Gradle sync to finish, then choose:
**Build > Build App Bundles or APKs > Build APKs**.
