# POEditor sync Gradle plugin

A Gradle plugin that imports Android string resources from the [POEditor](https://poeditor.com) translation
service. It downloads translations for the configured languages, splits them across the Android subprojects of
a multi-module build, and provides a read-only CI check that fails the build when local strings have not yet made
it into POEditor.

The plugin is import-only: it reads from POEditor and writes local resource files. It never uploads strings back.

## Requirements

- The plugin is applied to the **root project** of an Android build. It discovers Android subprojects (those
  applying `com.android.application`, `com.android.library`, or `com.android.dynamic-feature`) automatically and
  ignores the root project itself.
- A POEditor project ID and an API token with read access.

## Installation

Apply the plugin on the root project:

```kotlin
// build.gradle.kts (root)
plugins {
    id("io.cookielab.android.poeditor-gradle-plugin") version "<version>"
}
```

## How the files are organized

For each `values*` directory, the string set is split across three files with distinct ownership. The split keeps
the files humans edit separate from the file the plugin regenerates, so re-syncing from POEditor never clobbers
hand-written strings.

| File               | Owner                           | Touched by the plugin               |
|--------------------|---------------------------------|-------------------------------------|
| `untranslated.xml` | Developer                       | Never                               |
| `ready.xml`        | Developer (plus a cleanup pass) | Read, and rewritten during cleanup  |
| `translated.xml`   | The plugin                      | Overwritten on every import         |

- **`untranslated.xml`**: strings that should not go through translation at all (typically `translatable="false"`
  constants and format-only strings). The plugin never reads or writes this file; it exists so these strings stay
  out of the sync instead of polluting `ready.xml` and `translated.xml`.
- **`ready.xml`**: translatable strings that are not in POEditor yet; new strings are added here by hand. 
  **Should be present only in the default locale** (`values/`). On
  import, the plugin reads it as a local term and, during its cleanup phase, rewrites `ready.xml` (default locale
  only) with exactly the local terms still missing from POEditor. After a sync it therefore holds the strings
  added locally but not yet uploaded to POEditor.
- **`translated.xml`**: generated output. Every import overwrites it with the POEditor translations for the terms
  already present in the default-locale files, so hand-edited translations are lost on the next sync. Deletion is
  the exception: the plugin never prunes terms on its own, so when a screen or feature is removed from the app, its
  now-unused strings must be deleted from the default-locale `translated.xml` by hand. Once they are gone from
  there, the next import stops carrying them into any locale.

Resources are compared **by name only**. When the same string name appears in both `ready.xml` and
`translated.xml`, the `ready.xml` entry wins, because `ready.xml` is read first when the two files are merged.

To guard against a failed or empty export silently wiping committed translations, an import that returns no terms
for the default language aborts the affected subproject instead of overwriting its `translated.xml`.

## Configuration

Configure the plugin through the `poEditorSync` extension. Every option is shown below with its default;
`projectId`, `token`, and `qualifiersToLanguages` are required and have no default.

```kotlin
poEditorSync {
    // Required.
    projectId = "12345" // PoEditor project ID
    token = "super-secret-token" // PoEditor token (read-only is enough)
    qualifiersToLanguages = mapOf(
        "" to "en",   // the default-locale ("") entry is mandatory
        "-cs" to "cs",
    )

    // Optional — listed with their defaults.
    readyFileName = "ready.xml"
    translatedFileName = "translated.xml"
    excludedSuffices = setOf("_ios") // terms whose name ends with any of these are dropped on import
    indent = "    "                  // 4 spaces
    printSyncDate = false            // append an "Imported from POEditor on …" comment to translated files
}
```

### `qualifiersToLanguages`

This map is the bridge between Android's locale model and POEditor's. Each entry maps an **Android resource
qualifier** (the key) to a **POEditor language code** (the value):

- The **key** is the locale qualifier Android appends to the `values` directory, *without* the leading `values`.
  An empty string `""` is the default locale (`values/`), `-cs` is Czech (`values-cs/`), `-de` is German
  (`values-de/`), and so on — exactly the suffixes on the project's `res/values*` folders.
- The **value** is the language code as it exists in the POEditor project (`en`, `cs`, `de`, …). POEditor is the
  source of truth here, so the value must match a language that has been added to the project.

For example:

```kotlin
qualifiersToLanguages = mapOf(
    "" to "en",      // POEditor "en"  → res/values/
    "-cs" to "cs",   // POEditor "cs"  → res/values-cs/
    "-de" to "de",   // POEditor "de"  → res/values-de/
)
```

The plugin imports exactly the languages listed in this map — a language present in POEditor but absent here is
not downloaded, and a qualifier listed here whose POEditor language has no translations yields empty results.

The empty-string default-locale (`""`) entry is **mandatory**; the tasks fail with a configuration error without
it, because the default locale drives `ready.xml` cleanup and the verify check.

## Tasks

Both tasks live in the `PoEditor sync` group.

### `downloadPoEditorStrings`

```bash
./gradlew downloadPoEditorStrings
```

Downloads the configured languages from POEditor and, for every Android subproject, writes `translated.xml` for
each locale and rewrites the default-locale `ready.xml` with the terms still missing from POEditor.

### `verifyPoEditorStrings`

```bash
./gradlew verifyPoEditorStrings
```

A read-only check for CI. It downloads only the default language, compares it against the local default-locale
`ready.xml`/`translated.xml` terms, and **fails the build** if any local term is missing from POEditor. It writes
no files; it flags the strings that a `downloadPoEditorStrings` run would otherwise have moved into `ready.xml`.

## Logging

Both tasks route their output through Gradle's logger, so the level of detail is controlled by Gradle's standard
log-level flags:

- Default output shows the per-subproject sync summary and warnings about strings missing from POEditor.
- `--info` adds per-subproject progress: which subprojects are processed, how many unique terms each holds, and
  how many terms are written to each file.
- `--debug` additionally prints the raw POEditor HTTP requests and responses (the `api_token` form field is
  masked).

```bash
./gradlew downloadPoEditorStrings --info
./gradlew downloadPoEditorStrings --debug
```

## Limitations

- **One resource source directory per subproject.** A subproject whose `sourceSets.main.res.srcDirs` contains more
  than one entry is not supported; the import fails fast rather than guessing which directory to write to.

## License

[MIT](LICENSE)
