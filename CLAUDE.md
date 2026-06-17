# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

This is a Gradle plugin for Android projects that synchronizes string resources from POEditor translation service. The plugin downloads translations from POEditor API and manages them across multiple Android subprojects, splitting strings into "ready" and "translated" files.

## Build Commands

### Build and Test
```bash
./gradlew build --stacktrace
```
This runs compilation, tests, and all checks (including detekt).

### Run Tests Only
```bash
./gradlew test
```
Tests use JUnit Platform.

### Run Single Test
```bash
./gradlew test --tests "io.cookielab.android.poeditor.extensions.StringExtensionsTest"
```

### Code Quality
```bash
./gradlew detekt
```
Runs static analysis with detekt. Configuration is at `config/detekt/detekt.yml`.

### Clean Build
```bash
./gradlew clean
```

### Publishing
```bash
./gradlew publishPlugins --no-daemon --stacktrace
```
Publishes to the [Gradle Plugin Portal](https://plugins.gradle.org) via the `com.gradle.plugin-publish`
plugin. Requires Plugin Portal credentials, read from the `GRADLE_PUBLISH_KEY` / `GRADLE_PUBLISH_SECRET`
environment variables (or the `gradle.publish.key` / `gradle.publish.secret` Gradle properties).

### Local Testing
```bash
./gradlew publishToMavenLocal
```
Publishes to local Maven repository (~/.m2/repository) for testing in consuming projects.

### Debug Logging
```bash
./gradlew downloadPoEditorStrings --debug
```
Shows detailed HTTP request/response logs via OkHttp's logging interceptor.

### Verify Strings (CI)
```bash
./gradlew verifyPoEditorStrings
```
Read-only check intended for CI: downloads the default language from POEditor, compares it against the local
default-locale `ready.xml`/`translated.xml` terms and **fails the build** if any local term is missing from
POEditor (the "would `ready.xml` be non-empty?" question, answered without writing any files). Requires the
POEditor API token, same as the download task.

## Architecture

### Core Plugin Flow

The plugin operates in three main phases during task execution:

1. **Configuration Phase** (`PoEditorPlugin.kt`)
   - Creates `poEditorSync` extension for user configuration
   - Registers the `downloadPoEditorStrings` and `verifyPoEditorStrings` Gradle tasks
   - Captures subproject metadata inside `gradle.projectsEvaluated { … }`, **not** eagerly during `apply()` — at `apply()` time the subprojects may not have applied their Android plugins yet, so `plugins.hasPlugin(...)` returns false and the captured list comes back empty (a silently passing verify / a no-op download). `projectsEvaluated` guarantees the plugins and their resource source sets are in place. The captured `SubprojectInfo` list is then set on both tasks (avoiding the deprecated `project` accessor during execution)
   - Uses `project.path` (not `project.name`) to get full Gradle project paths like `:feature:parent:leaf`
   - Only processes Android subprojects (application, library, or dynamic-feature plugins)
   - Extracts resource directory paths from subprojects via `BaseExtension.sourceSets`

2. **Download Phase** (`PoEditorTermsDownloader`, behind the `TermsDownloader` interface)
   - Downloads translations from POEditor API for configured languages **sequentially** (no parallelization)
   - Uses OkHttp 5 for blocking HTTP calls (no coroutines to avoid classloader issues in Gradle plugins)
   - HTTP logging interceptor forwards all logs to Gradle's `logger.debug()` - visible with `--debug` flag; the `api_token` form field is masked out of those logs
   - Makes two HTTP requests per language:
     1. POST to `/v2/projects/export` to request export
     2. GET to download the actual XML from the export URL
   - POEditor returns HTTP 200 even on failures (e.g. invalid token), so the export's in-body `response.code` is checked: anything other than `"200"` throws `IOException`
   - Filters out strings with excluded suffixes (default: "_ios")
   - Parses XML responses into `StringLikeResource` objects using `DefaultStringResParser`

3. **Subproject Processing** (`DefaultDownloadStringsProjectProcessor`, behind the `DownloadStringsProjectProcessor` interface)
   - Iterates through all Android subprojects
   - For each subproject:
     - Finds unique terms from the default locale's `ready.xml` and `translated.xml` via the shared `LocalTermsCollector`
     - **Refuses to proceed** (throws `GradleException`) when the default language's downloaded terms are empty while local terms exist — guards against a failed/empty export wiping committed `translated.xml`
     - Processes all resource directories (values, values-cs, etc.) and writes `translated.xml` files
     - **After** all directories are processed, checks for terms missing from POEditor and writes to `ready.xml`
   - Single warning at subproject level (not per-directory) for missing terms
   - Creates resource directories on demand (throws `GradleException` if `mkdirs()` fails)

### Key Components

**Dependency injection via interfaces** — the collaborators (`StringResParser`, `StringResWriter`, `TermsDownloader`, `DownloadStringsProjectProcessor`, `LocalTermsCollector`, `LocaleResolver`, `ReadyTermsVerifier`) are interfaces, each with a single implementation (`DefaultStringResParser`, `FileStringResWriter`, `PoEditorTermsDownloader`, `DefaultDownloadStringsProjectProcessor`, `DefaultLocalTermsCollector`, `DefaultLocaleResolver`, `DefaultReadyTermsVerifier`). The task wires the real implementations; tests substitute fakes/mocks. **When looking for logic, open the implementation class, not the same-named interface file.**

**Local term reading** (`DefaultLocalTermsCollector`, behind the `LocalTermsCollector` interface)
- Single source of truth for reading local terms: parses a `values` directory's `ready.xml` then `translated.xml`, deduplicating by name (resources are equal by name only, so the ready entry wins on a collision); missing files are skipped
- Shared by **both** the download sync (`DefaultDownloadStringsProjectProcessor`) and the verify gate (`DefaultReadyTermsVerifier`), so the two paths cannot drift in how they read local terms

**Task Definition** (`DownloadPoEditorStringsTask.kt`)
- Annotated with `@UntrackedTask` - explicitly marks the task as non-cacheable and non-incremental
- Reason: Downloads from external POEditor API and syncs across multiple dynamic subproject directories
- This prevents Gradle warnings about missing outputs and correctly reflects the task's nature
- The `@TaskAction` is a thin wrapper; the real orchestration lives in `DownloadPoEditorStringsTaskDelegate` (constructed with the concrete collaborators, unit-testable without Gradle). The delegate also validates that the default-locale (`""`) qualifier mapping is configured.

**Task Isolation** (`SubprojectInfo.kt`)
- Simple data class containing subproject `path` (full Gradle path like `:feature:parent:leaf`) and resource directory paths
- Captured at configuration time to avoid accessing `Project` objects during task execution
- Ensures compatibility with Gradle configuration cache and task isolation

**HTTP Client** (`PoEditorTermsDownloader.kt`)
- Uses OkHttp 5 with blocking I/O (no coroutines)
- Sequential downloads to avoid complex coroutines machinery that can cause classloading issues in Gradle plugins
- HttpLoggingInterceptor configured with custom logger that routes to Gradle's `logger.debug()`; masks the `api_token` form field
- Constructor default parameters reference earlier constructor parameters (e.g., `logger` is used to construct `httpClient`); `httpClient`/`json`/`baseUrl` are injectable for tests
- Manual JSON deserialization using kotlinx.serialization (`PoeditorExportResponse`; note `response.code` is a String per POEditor's API)

**XML Parsing** (`DefaultStringResParser.kt`)
- Uses MiniXmlPullParser for pull-based parsing
- Preserves CDATA sections by wrapping them in quotes: `"<![CDATA[...]]>"`
- Handles both `<string>` and `<plurals>` resources
- Escapes raw XML entities (&, <, >) during parsing via `ENTITY_REF` events in its own `sanitizeValue()` (distinct from `String.sanitized()`)
- An unknown plural `quantity` throws `IllegalStateException` with a descriptive message

**XML Writing** (`FileStringResWriter.kt`)
- Generates well-formed Android string resource XML
- Configurable indentation (default: 4 spaces)
- Adds timestamp comment for translated.xml when `printDate = true`; takes an injectable `Clock` so the timestamp is testable

**String Sanitization** (`extensions/String.kt`)
- `sanitized()` removes invalid Unicode characters per XML 1.0 spec and replaces invalid newline characters (U+2028, U+2029) with standard newlines
- This is separate from the parser's entity escaping (`DefaultStringResParser.sanitizeValue()`)

**Resource Types** (`StringLikeResource.kt`)
- Sealed interface with two implementations:
  - `StringRes`: Simple string resources with name and value
  - `PluralRes`: Plural resources with quantity items (zero, one, two, few, many, other); its `init` requires a non-empty list with unique quantities
- `StringRes`/`PluralRes` define `equals`/`hashCode` on **name only** (deliberate — drives the `Set`/`distinct()` dedup across ready/translated). `PluralRes.Item` keeps full value equality.
- String arrays are not supported

### Configuration

Plugin is configured via `poEditorSync` extension block in build.gradle:

```kotlin
poEditorSync {
    projectId = "12345"                    // POEditor project ID (required)
    token = "abc123..."                    // POEditor API token (required)
    qualifiersToLanguages = mapOf(         // Android qualifier -> POEditor language (required)
        "" to "en",
        "-cs" to "cs"
    )
    readyFileName = "ready.xml"            // Default: "ready.xml"
    translatedFileName = "translated.xml"  // Default: "translated.xml"
    excludedSuffices = setOf("_ios")       // Default: setOf("_ios")
    indent = "    "                        // Default: 4 spaces
    printSyncDate = false                  // Append "Imported from POEditor on …" comment to translated files. Default: false
}
```

## Development Setup

- **Kotlin Version**: 2.3.20 (with JVM toolchain 17)
- **Explicit API Mode**: Enabled - all public APIs must have explicit visibility and return types
- **Dependencies** (source of truth: `gradle/libs.versions.toml`):
  - Android Gradle Plugin (AGP) 8.13.0
  - OkHttp 5.4.0 (HTTP client + logging interceptor; `mockwebserver3` in tests)
  - kotlinx-serialization-json 1.11.0 (JSON parsing)
  - ktxml 1.0.0 (XML parsing)
  - Detekt 1.23.6 (static analysis)
  - MockK + kotlin-test (unit tests)

### Gradle Plugin Classloading Considerations

- **No coroutines in task execution**: Coroutines can cause `NoClassDefFoundError` issues due to complex bytecode generation and classloader isolation in Gradle plugins
- **Blocking I/O only**: Use OkHttp's synchronous `.execute()` API instead of suspend functions
- **Configuration-time data capture**: Extract all necessary data from `Project` objects during task configuration, not execution
- **Use project.path not project.name**: For nested subprojects like `:feature:parent:leaf`, use `project.path` to get the full path
- **No `project` accessor in @TaskAction**: Deprecated in Gradle 9+ and breaks configuration cache
- **@UntrackedTask annotation**: Use this for tasks that can't meaningfully participate in incremental builds or caching due to external dependencies or dynamic outputs

### Processing Order

The subproject processing follows this specific order (enforced in `DefaultDownloadStringsProjectProcessor`):

1. Find unique terms from default locale's `ready.xml` and `translated.xml` (via the shared `LocalTermsCollector`)
2. Process all resource directories and write `translated.xml` files
3. Check for missing terms and write to `ready.xml` (cleanup phase)

This order ensures that missing term warnings appear once at the subproject level, not per-directory.

## CI/CD

GitHub Actions workflows defined in `.github/workflows/`:
1. **build.yml**: Runs `./gradlew build --stacktrace` on pull requests and pushes to `main`.
2. **publish.yml**: On a published GitHub release, runs `./gradlew publishPlugins` to the Gradle Plugin Portal. The
   release tag is mapped to the `VERSION` env var (which `build.gradle.kts` reads as the project version), and the
   Plugin Portal credentials come from the `GRADLE_PUBLISH_KEY` / `GRADLE_PUBLISH_SECRET` repository secrets.

## Important Notes

- Plugin only processes Android subprojects (not the root project)
- The processor creates each `values*` directory and throws `GradleException` if that `mkdirs()` fails (the writer additionally best-effort-creates the target file's parent dir)
- String names must be unique across ready.xml and translated.xml within a values directory
- POEditor signals errors with HTTP 200 + an in-body `response.code`; an export `code != "200"` throws `IOException`
- An empty default-language download aborts the subproject with `GradleException` instead of overwriting existing `translated.xml`
- `PluralRes` enforces non-empty, unique-quantity items at construction (rejects empty `<plurals>` and duplicate quantities)
- CDATA sections are preserved but wrapped in quotes during parsing
- Unicode sanitization happens during parsing only (`DefaultStringResParser`); the writer emits values verbatim
- Downloads are sequential (no parallelization) to keep implementation simple and avoid coroutines
- HTTP logging uses Gradle's logger at debug level via custom `HttpLoggingInterceptor.Logger` implementation (with the `api_token` masked)
- Nested subprojects are fully supported via `project.path` usage
- Task is marked as `@UntrackedTask` to explicitly disable caching/incremental builds (correct for sync tasks with external dependencies)
- `verifyPoEditorStrings` is a read-only CI gate: it writes nothing, downloads only the default language, and fails (aggregating all subprojects) when any local default-locale term is missing from POEditor. Logic lives in `VerifyPoEditorStringsTaskDelegate` (orchestration + guards) and `DefaultReadyTermsVerifier` (per-subproject pending-term detection, behind the `ReadyTermsVerifier` interface); both are unit-tested without Gradle. The verifier reads local terms through the same `LocalTermsCollector` the download processor uses, so verify and download agree on what counts as a local term
- Subproject metadata is captured in `gradle.projectsEvaluated` (not eagerly in `apply()`); capturing too early races subproject plugin application and yields an empty list, which makes verify silently pass and download a no-op
