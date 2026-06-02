# Project Agent Notes

## Active Workspaces

Android APP project:

`D:\codex\巡礼`

Backend/system project:

`D:\codex\面向旅游巡礼场景的 AI 辅助信息管理系统\Tour-AI-Info-Management-System`

Use the APP path for all future APP edits, builds, tests, secret scans, Android Studio project opening, and backend/system integration work.

Use the backend/system path when an APP-side thread needs to inspect or coordinate with the management system backend, API contracts, tests, deployment config, reports, or online deployment settings.

## Legacy APP Copy

Do not use the previous copy as the working APP project:

`C:\Users\s1867\AndroidStudioProjects\livecamera`

That old path is retained only as a historical backup/reference unless the user explicitly asks to inspect it.

## Migration Context

The APP project was moved from `C:\Users\s1867\AndroidStudioProjects\livecamera` to `D:\codex\巡礼`.

Because the new Windows path contains non-ASCII characters, `gradle.properties` includes:

```properties
android.overridePathCheck=true
```

Keep this setting unless the project is moved again to an ASCII-only path.

## Opening And Verification

Open `D:\codex\巡礼` directly in Android Studio.

After changes, prefer verifying from this workspace:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\scan-secrets.ps1 -Mode WorkingTree
.\gradlew.bat :app:assembleDebug --console=plain
```

Expected result: secret scan passes and `:app:assembleDebug` reports `BUILD SUCCESSFUL`.

## Compatibility Notes

Do not move work back to the old Android Studio path.
Do not split the legacy single-activity architecture unless explicitly requested.
Do not change Doubao prompts, backend API contracts, Room schema, or stored API keys/tokens unless explicitly requested.
