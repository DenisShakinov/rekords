# Rekords

[![Maven Central](https://img.shields.io/maven-central/v/io.github.denisshakinov/rekords-core)](https://central.sonatype.com/namespace/io.github.denisshakinov)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Kotlin Multiplatform storage for annotated classes. Describe what you keep as `@Rekord` classes,
declare a schema with its migrations, and read and write them from common code. Where they end up —
SQLite, the browser's IndexedDB or memory — is decided by the engine each target depends on.

- Plain Kotlin classes, mapped by `kotlinx.serialization` — no code generation step.
- Filters, ordering, paging and counting.
- Nested rekords and lists of them, stored once and shared between the rekords referring to them.
- Transactions: several operations applied together or not at all.
- Versioned schemas with migrations run in a transaction of their own.
- Engines found on their own: common code creates the store, each target's dependencies choose the
  storage.

## Modules

| Module              | What it is                                                     | Targets                                                                                                 |
|---------------------|----------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `rekords-core`      | Annotations, schema, `RekordsStore`, filters and the engine API | Android, JVM, JS, Wasm JS, Wasm WASI, iOS, macOS, tvOS, watchOS, Linux, Windows (MinGW), Android Native |
| `rekords-sqlite`    | Engine keeping rekords in SQLite, through `androidx.sqlite`     | Android, JVM, iOS, macOS, tvOS, watchOS, Linux (no Intel targets on Apple platforms)                 |
| `rekords-indexeddb` | Engine keeping rekords in the browser's IndexedDB               | JS, Wasm JS                                                                                             |
| `rekords-memory`    | Engine keeping rekords in memory, for tests                     | Same as `rekords-core`                                                                                  |
| `rekords-sql`       | The SQL editor the SQL engines are built on                     | Same as `rekords-core`                                                                                  |

## Setup

Rekord classes are serialized with `kotlinx.serialization`, so the module declaring them applies
its compiler plugin:

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.denisshakinov:rekords-core:<version>")
        }
        // One engine per target: SQLite on mobile, desktop and native ...
        androidMain.dependencies {
            implementation("io.github.denisshakinov:rekords-sqlite:<version>")
        }
        iosMain.dependencies {
            implementation("io.github.denisshakinov:rekords-sqlite:<version>")
        }
        // ... IndexedDB in the browser.
        webMain.dependencies {
            implementation("io.github.denisshakinov:rekords-indexeddb:<version>")
        }
        commonTest.dependencies {
            implementation("io.github.denisshakinov:rekords-memory:<version>")
        }
    }
}
```

## Usage

### Rekords

A rekord is a class annotated with `@Rekord`, whose constructor properties are `@Field`s. The fields
marked `id = true` identify a rekord: writing one with the same ids updates it instead of adding
another. A field marked `searchable = true` may be indexed by the engine.

```kotlin
@Rekord(type = "note")
class NoteRekord(
    @Field(name = ID, id = true)
    val id: Long,
    @Field(name = TITLE, searchable = true)
    val title: String,
    @Field(name = CREATED)
    val created: LocalDateTime,
    @Field(name = DUE_DATE)
    val dueDate: LocalDate?,
    @Field(name = FOLDER)
    val folder: FolderRekord,
    @Field(name = TAGS)
    val tags: List<TagRekord>,
) {
    companion object {
        const val ID = "id"
        const val TITLE = "title"
        const val CREATED = "created"
        const val DUE_DATE = "due_date"
        const val FOLDER = "folder"
        const val TAGS = "tags"
    }
}
```

A field holds a `String`, `Int`, `Long`, `Float`, `Double`, `Boolean`, a `kotlinx.datetime`
`LocalDate` or `LocalDateTime`, another rekord or a list of them — nullable or not.

### Schema

The schema lists every rekord type, nested ones included, and upgrades the storage from one
version to the next:

```kotlin
class NotesSchema : RekordsSchema {

    override val version: Int = 2

    override val rekordTypes: List<KClass<*>> = listOf(
        NoteRekord::class,
        FolderRekord::class,
        TagRekord::class,
    )

    override suspend fun RekordsMigrationEditor.onUpgrade(oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            addField<NoteRekord>(NoteRekord.DUE_DATE)
        }
    }
}
```

A new storage is created with every rekord type of the schema, and `onCreate` can fill it. An older
one is handed to `onUpgrade`, which can add, remove and rename rekord types and fields, and read
and write rekords while at it. A migration that fails leaves the storage as it was, and is tried
again by the next operation.

### Store

Common code creates the store with the engine the target depends on:

```kotlin
val store = RekordsStore(NotesSchema()) {
    name = "notes.db"
}
```

Every operation returns a `Result`:

```kotlin
store.putRekord(note)
store.putRekords(notes)

val note: NoteRekord? = store
    .getRekord<NoteRekord>(Filter.Equals(NoteRekord.ID, 42L))
    .getOrThrow()

val dueThisWeek: List<NoteRekord> = store
    .getRekords<NoteRekord>(
        filter = Filter.GreaterThanOrEquals(NoteRekord.DUE_DATE, today) +
            Filter.LessThan(NoteRekord.DUE_DATE, today.plus(1, DateTimeUnit.WEEK)),
        orderBy = listOf(Order.Ascending(NoteRekord.DUE_DATE)),
        limit = 20,
    )
    .getOrThrow()

val count: Int = store.count<NoteRekord>(Filter.Contains(NoteRekord.TITLE, "draft")).getOrThrow()

store.deleteRekord(note)
store.delete<NoteRekord>(Filter.LessThan(NoteRekord.CREATED, cutoff))
```

Filters are combined with `+` (and) and `or`, or with `Filter.And`, `Filter.Or` and `Filter.Not`.
`Filter.Nested` filters by a field of a nested rekord, or of any rekord in a list:

```kotlin
Filter.Nested(NoteRekord.TAGS, Filter.Equals(TagRekord.NAME, "work"))
```

### Transactions

Operations run in `transaction` are applied together once it completes; if it throws, none of them
is:

```kotlin
store.transaction {
    deleteRekord(oldNote)
    putRekord(newNote)
}
```

### Engines

A store created without an engine uses the one the target depends on, and fails with a message
saying what to do when there is none or several of them. An engine can also be named, as tests do
with the in-memory one:

```kotlin
val store = RekordsStore(NotesSchema(), InMemory)
```

What the engine does can be followed with a `RekordsLogger`, such as the statements the SQLite
engine runs:

```kotlin
val store = RekordsStore(NotesSchema()) {
    name = "notes.db"
    logger = RekordsLogger { level, tag, message, throwable ->
        println("[$level] $tag: $message")
        throwable?.printStackTrace()
    }
}
```

## Platform notes

### SQLite

- **Android** keeps the database in the application's database directory, through the framework's
  SQLite. The module's manifest declares a content provider handing the application context over
  to the engine, so nothing has to be passed to create a store.
- **JVM** keeps the database at the path `name` is, relative to the working directory, through
  the SQLite bundled with `androidx.sqlite:sqlite-bundled`.
- **Apple platforms and Linux** keep the database in the platform's application data directory,
  through the operating system's SQLite, which the final binary has to link: a klib carries no
  linker options. Where Kotlin links the binary — a test executable, a dynamic framework — add
  `linkerOpts.add("-lsqlite3")` to it. A static framework is linked by Xcode instead, so add
  `-lsqlite3` to the app target's `OTHER_LDFLAGS`.
- Another `SQLiteDriver`, such as `BundledSQLiteDriver` on Android, can be passed to the engine,
  with its artifact added to the target's dependencies:

  ```kotlin
  val store = RekordsStore(NotesSchema(), SQLite) {
      name = "notes.db"
      driver = BundledSQLiteDriver()
  }
  ```

### IndexedDB

The engine keeps rekords in the IndexedDB database `name` names, in the browser the application
runs in.

## Building

The library builds with JDK 25. `./gradlew jvmTest jsNodeTest wasmJsNodeTest` runs the tests on
the JVM, Node.js and Wasm; the native test tasks, such as `macosArm64Test`, need Xcode.

## Releasing

The modules are published to Maven Central with the
[Gradle Maven Publish Plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/central/).
The credentials and the signing key are read from `~/.gradle/gradle.properties`, never from the
repository:

```properties
mavenCentralUsername=<Central Portal token username>
mavenCentralPassword=<Central Portal token password>
signingInMemoryKey=<ASCII-armored private key>
signingInMemoryKeyId=<last 8 characters of the key id>
signingInMemoryKeyPassword=<key password>
```

Set a release `version` in `gradle.properties`, then run on macOS, which builds every target:

```shell
./gradlew publishToMavenCentral
```

The deployment is then released from the [Central Portal](https://central.sonatype.com/publishing).
A `-SNAPSHOT` version is not signed, so `./gradlew publishToMavenLocal` works without a key.

## License

Rekords is distributed under the [MIT License](LICENSE).
