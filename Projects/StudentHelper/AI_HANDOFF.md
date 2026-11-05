# Student Helper — AI Handoff Notes

## Product purpose
Student Helper is an Android student planner for homework and exams. A user adds a task title, chooses Homework or Exam, and selects its deadline. Tasks are stored locally and remain after ordinary app restarts.

## Main behavior
- The top card counts **only** tasks whose deadline is tomorrow (the device's local date plus one day).
- The numerator is the number of those tomorrow tasks marked complete; the denominator is all tasks due tomorrow.
- Tasks due today, in the past, or on later dates must not affect the tomorrow progress card.
- Each task can be marked complete/incomplete.
- Long-pressing a task opens task actions; the three-dot menu also provides Rename and Delete.
- The list can be filtered to All, Homework, or Exams.
- New tasks default to tomorrow, but the user can choose another date.

## Design requirements
- Jetpack Compose + Google Material 3.
- Keep the existing visual design, spacing, typography, colors, component shapes, and animation behavior unless the user explicitly asks to redesign them.
- Keep the progress animation, task completion color/icon animation, and lazy-list add/remove/reorder animations.
- Android 12 minimum (`minSdk = 31`).
- Dynamic Material You system colors are used on Android 12+; retain the fallback palette for completeness.

## Architecture / source map
- `app/src/main/java/com/example/studenthelper/MainActivity.kt`: Activity entry point and theme setup.
- `.../data/TaskStorage.kt`: `StudyTask`, `TaskType`, Preferences DataStore persistence, JSON encoding/decoding, add/update/delete operations.
- `.../ui/StudentHelperApp.kt`: screen state, DataStore collection, tomorrow statistics, filtering/sorting, LazyColumn, and event wiring.
- `.../ui/TaskComponents.kt`: header, progress card, filters, task cards, task-type tags, empty state, rename and delete dialogs.
- `.../ui/AddTaskBottomSheet.kt`: task creation sheet and Material 3 date picker.
- `.../ui/DateFormatting.kt`: due-date labels and date-picker epoch conversion helpers.
- `.../ui/theme/Theme.kt`: Material 3 color schemes and system-bar styling.
- `app/src/main/AndroidManifest.xml`: Android manifest and launcher Activity.
- `app/src/main/res/values/styles.xml`: Android window theme referenced by the manifest.
- `app/build.gradle.kts`: app SDK settings and dependencies.
- `gradle/libs.versions.toml`: dependency and plugin versions.

## Data model
`StudyTask` contains:
- `id: String` — stable UUID used as the LazyColumn key.
- `title: String` — task name.
- `type: TaskType` — `HOMEWORK` or `EXAM`.
- `dueDateEpochDay: Long` — local calendar date represented as epoch day.
- `isCompleted: Boolean` — completion state.

Do not replace the date representation with UTC timestamps without carefully preserving local calendar-date behavior. The selected date is a day, not a deadline time.

## Persistence
- Preferences DataStore named `student_helper_tasks`.
- One JSON string preference stores the complete task list.
- Storage operations are suspend functions and use DataStore's transactional `edit` API.
- Do not move storage writes onto the main thread or replace permanent storage with in-memory state.
- This is local-only storage, not cloud sync. Clearing app data or uninstalling can remove it.

## Pinned versions — do not change unless explicitly requested
- Android Gradle Plugin: `8.12.0`
- Kotlin: `2.0.21`
- Gradle wrapper: `8.13`
- Core KTX: `1.10.1`
- Lifecycle Runtime KTX: `2.6.1`
- Activity Compose: `1.8.0`
- Compose BOM: `2024.09.00`
- JUnit: `4.13.2`
- AndroidX Test JUnit: `1.1.5`
- Espresso: `3.5.1`
- DataStore Preferences: `1.1.1` (additional dependency)
- Lifecycle Runtime Compose: `2.6.1` (additional dependency for lifecycle-aware collection)
- `compileSdk = 36`, `targetSdk = 36`, `minSdk = 31`, Java/JVM target 17.

## Current performance work
- Collect stored tasks with lifecycle awareness so the UI stops observing while the Activity is stopped.
- Decode stored JSON on `Dispatchers.Default`, not during a UI frame.
- Memoize tomorrow-only statistics and filtered/sorted task lists.
- Use LazyColumn stable keys and content types.
- Use transient `remember` state rather than Bundle-saveable state for each task's open popup.
- Cache due-date labels and reuse immutable date formatters, avoiding repeated formatting during task color animations/recomposition.
- Preserve all existing animation durations and component visuals.

## Guidance for future AI changes
1. Read this file and the relevant source files before proposing edits.
2. Do not silently change the pinned Android/Gradle/Kotlin/Compose versions.
3. Preserve behavior and visual design unless the user requests a change.
4. For performance work, identify the actual UI/main-thread or rendering hotspot before removing animations or changing appearance.
5. Keep the tomorrow progress denominator restricted to tasks due tomorrow.
6. When adding fields to `StudyTask`, update both JSON encode and decode logic and consider backward compatibility for tasks already saved on the device.
7. Return complete replacement files or a clear patch with exact paths; don't give disconnected snippets when a full-file update is requested.
