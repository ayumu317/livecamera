# Project Agent Notes

## Current Status

This directory is the primary LiveCamera-LBS Android Gradle workspace.

LiveCamera-LBS Android APP project:

`D:\codex\livecamera-lbs`

Backend/system project:

`D:\codex\面向旅游巡礼场景的 AI 辅助信息管理系统\Tour-AI-Info-Management-System`

Use this APP path for all future APP edits, builds, tests, secret scans, Android Studio project opening, and backend/system integration work.

Use the backend/system path when an APP-side thread needs to inspect or coordinate with the management system backend, API contracts, tests, deployment config, reports, or online deployment settings.

## Legacy APP Copies

Do not use the previous Android Studio copy as the working APP project:

`C:\Users\s1867\AndroidStudioProjects\livecamera`

Do not use the non-ASCII migration copy as the primary Gradle workspace:

`D:\codex\巡礼`

These old paths are retained only as historical backups or references unless the user explicitly asks to inspect them.

## Path And Gradle Test Policy

Use this ASCII-only workspace path for Android Gradle work:

`D:\codex\livecamera-lbs`

Android Gradle Plugin unit tests may fail under non-ASCII Windows paths with `ClassNotFoundException` even when test `.class` files are compiled and `:app:assembleDebug` succeeds. This ASCII workspace is therefore the default APP test/build workspace.

The previous non-ASCII workspace needed:

```properties
android.overridePathCheck=true
```

Do not re-add that override in this ASCII workspace unless a new Gradle error explicitly requires it.

## Verification

Run these from `D:\codex\livecamera-lbs`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\scan-secrets.ps1 -Mode WorkingTree
.\gradlew.bat :app:assembleDebug --console=plain
.\gradlew.bat :app:testDebugUnitTest --console=plain
```

Expected result: secret scan passes, `:app:assembleDebug` reports `BUILD SUCCESSFUL`, and `:app:testDebugUnitTest` does not fail from path-related class loading errors.

## Safety Notes

Do not commit `local.properties`, API keys, tokens, APK/AAB files, or generated build outputs.
Do not move work back to the old Android Studio path.
Do not split the legacy single-activity architecture unless explicitly requested.
Do not change Doubao prompts, backend API contracts, Room schema, or stored API keys/tokens unless explicitly requested.
