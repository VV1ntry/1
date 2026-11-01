# NovelFlow — Android MVP source

Target: Android 8.0+; intended for Poco X6 Pro / HyperOS 3.

## Current MVP
- Enter a chapter URL and open it in an embedded WebView.
- Extract the main article text using common WordPress/novel selectors.
- Preview extracted text and inspect the full original.
- Create a UTF-8 TXT file with a literary-translation prompt and the chapter.
- Share that file through Android's standard `ACTION_SEND` / FileProvider flow.
- Import a returned `.txt` translation and switch between original and translated text.

## DeepSeek integration boundary
This build does **not** automate the DeepSeek app, reverse-engineer its private interface, or intercept its responses. DeepSeek's published user agreement restricts automated crawling/copying and interference with normal service operation. NovelFlow therefore prepares a chapter file and hands it to the Android share sheet. Whether the installed DeepSeek app accepts a shared TXT file depends on its current Android intent filters; otherwise the file can be selected using DeepSeek's official upload control. Importing the generated answer back into NovelFlow is a later feature and will require a supported user-mediated route.

## Build
Requires Android Studio / Android SDK 35 and JDK 17 or newer. Open this folder in Android Studio and run `:app:assembleDebug`.

A GitHub Actions workflow is included at `.github/workflows/android.yml` to build and publish the debug APK as an artifact when the project is pushed to a GitHub repository.

## Known MVP gaps
- Foxaholic-specific extraction selectors still need on-device validation; the public page returned HTTP 403 to the external fetcher, while it may load normally inside the Android WebView.
- No persistent library/database yet.
- Translation import currently supports a single UTF-8 `.txt` file; multi-part merge and progress tracking are not implemented yet.
- This source snapshot does not contain a compiled APK: the current build container has Java but no Android SDK or Gradle installation.
