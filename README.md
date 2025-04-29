# Unused Resource & Dependency Scanner for Android

An Android app that helps developers identify and clean up unused resources and dependencies in Android Studio projects. This tool improves project hygiene, reduces APK size, and helps you maintain a clean codebase.

---

## Features

- **Detect Unused Resources**
  - Scans `res/` directory for unused items like `drawable/`, `layout/`, `string/`, etc.
  - Supports data binding layout detection
- **Detect Unused Dependencies**
  - Parses `build.gradle` and `build.gradle.kts`
  - Checks for `implementation` dependencies not referenced in any source code
- **Custom Ignore List**
  - Skip specific resource names using `ignore_resources.txt`
- **UI Utilities**
  - Filter results using a Spinner dropdown
  - Export scan results to a `.csv` file
  - View a categorized summary dialog

---

## Supported Resource Types

- Drawable
- Layout
- Font
- Raw
- Anim
- Xml
- Color
- String
- Dimen
- Bool
- Integer
- String Array
- Attr
- Declare-Styleable
- Style
- Other Resource
- **Unused Dependency**

---

## Getting Started

### 1. Build and Run the App

Clone this repository and run the app on an Android device or emulator using Android Studio.

### 2. Select Project Folder

Tap the **“Pick Folder”** button and choose the root of your Android project. The app requires access to the `src/` and `res/` directories.

> Tip: Grant storage permissions if requested.

### 3. Start Scanning

Tap **“Scan”** to begin analyzing the selected folder. Progress will be shown at the bottom.

### 4. View and Filter Results

Use the **Spinner** to filter results by type (e.g. Layouts, Strings, Unused Dependency).

### 5. Export to CSV (Optional)

Tap **“Export”** to save the results to a CSV file in the selected folder.

### 6. View Summary

Tap **“Summary”** to see a quick overview of unused resources and dependencies found.

---

## Dependency Scan Support

The scanner can detect unused dependencies declared like this:

```kotlin
implementation("com.squareup.retrofit2:retrofit:2.9.0")
```

It works by checking if the group ID or artifact ID appears in any source file. If not, it's considered unused.
> [!NOTE]
>
> Note: The scanner ignores dependencies declared using Version Catalog (e.g. libs.androidx.core.ktx) as they are resolved dynamically and require .toml parsing.

Ignore List (Optional)
You can exclude specific resource names from scanning using an ignore file.

ignore_resources.txt
Create a file at:
`/storage/emulated/0/Documents/UnusedFileScanner/ignore_resources.txt`
Each line in the file should be a resource name or part of it. Example:
```
ic_launcher
colorPrimary
themeOverlay
```
