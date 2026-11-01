# NovelFlow — Android MVP
Target: Android 8.0+; intended for Poco X6 Pro / HyperOS 3.

- Open a chapter URL in the embedded browser.
- Extract likely article text using common novel/WordPress selectors.
- Export chapter plus Russian translation instructions as UTF-8 TXT.
- Import a translated TXT and switch between original and translation.

DeepSeek is not automated. Send/upload the TXT manually through DeepSeek's official interface, then save the answer as TXT and import it.

## Build
The GitHub Actions workflow builds a debug APK. Open **Actions → Android APK**, run the workflow, then download the `NovelFlow-debug-apk` artifact from the completed run.

## Limitations
Generic extraction selectors need testing per site; Foxaholic is not yet verified on-device. Last chapter, translation and URL are restored after restart (single chapter only, no full library or multi-part merge yet). This archive is source code, not an APK.

## Changes in 0.1.1
- Content now respects status/navigation bars and cutouts (Android 15+ edge-to-edge).
- http:// chapter links are allowed.
- Last chapter, translation and URL are saved and restored.
- Upload the *contents* of this folder to the repo root so `.github/workflows` sits at the root.
