# Photo Name Search (Android starter project)

An offline-first Android app starter: import a photo, assign a searchable name, and find saved entries by full or partial name. Photo metadata is stored in a local Room database; images remain in their original location on the device.

## Build the APK using only your phone

1. Download this ZIP and extract it, or upload its contents to a new GitHub repository.
2. In GitHub, create a repository and upload the project files (keep the `.github/workflows/build-apk.yml` file).
3. Open the repository's **Actions** tab, select **Build Android APK**, then tap **Run workflow**.
4. When the workflow finishes, open its run, scroll to **Artifacts**, and download `photo-name-search-debug-apk`.
5. Extract the downloaded artifact ZIP on your Android phone and install `app-debug.apk`. Android may ask you to allow installs from your browser or file manager.

The build runs on GitHub's cloud machines; no computer is required. You need a GitHub account and internet access for the build. This is a starter project, not a Play Store release. Review and test it with sample photos before using it for an important collection.
