# nso_uploadRemarkable — Android App

Share articles from any Android browser, generate EPUB and PDF with a margin-notes layout optimized for reMarkable.

## Setup on Arch Linux

### Step 1 — Install Android Studio

```bash
# Install from AUR
yay -S android-studio
# Or via flatpak
flatpak install flathub com.google.AndroidStudio
```

### Step 2 — Open the project

1. Launch Android Studio
2. Click **Open** → select the `BinderyApp` folder
3. Wait for Gradle sync to complete (downloads dependencies, takes 2-5 min first time)

### Step 3 — Install Android SDK

If prompted, let Android Studio install the required SDK (API 34).
Or: **Tools → SDK Manager** → install **Android 14 (API 34)**

### Step 4 — Connect your phone

1. On your Android phone: **Settings → About Phone** → tap **Build Number** 7 times
2. Go to **Settings → Developer Options** → enable **USB Debugging**
3. Connect phone via USB → tap **Allow** on the phone when prompted
4. In Android Studio, your phone should appear in the device dropdown

### Step 5 — Build and install

Click the **Run ▶** button (or press `Shift+F10`)

The app installs on your phone automatically.

### Step 6 — Use it

1. Browse to an article in any browser (Chrome, DuckDuckGo, Firefox)
2. Tap **Share → nso_uploadRemarkable**
3. The app opens and fetches the article
4. Repeat for 5-6 articles
5. Tap **Generate EPUB** or **Generate PDF**
6. Share the file to the reMarkable app

## Transferring to reMarkable

After generating, the Android share sheet appears. Choose:
- **reMarkable app** — syncs to cloud automatically
- **Email to yourself** — then open on reMarkable
- **Save to Downloads** — then transfer via USB

## Adding new sites

If a site's article text is truncated, open `ArticleExtractor.kt` and add a selector to `siteParaSelectors`:

```kotlin
"p[class*=your-site-class]",   // Your Site Name
```

Find the class by long-pressing a paragraph in Chrome → **Inspect Element**.

## Building a release APK

```bash
cd BinderyApp
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release-unsigned.apk
```

For sideloading without Android Studio, enable **Install unknown apps** in Android settings.
