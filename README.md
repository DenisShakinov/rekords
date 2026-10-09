# Rekords

[![Maven Central](https://img.shields.io/maven-central/v/io.github.denisshakinov/rekords-core)](https://central.sonatype.com/namespace/io.github.denisshakinov)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Kotlin Multiplatform storage for annotated classes. Describe what you keep as `@Rekord` classes,
declare a schema with its migrations, and read and write them from common code. Where they end up —
SQLite, the browser's IndexedDB or memory — is decided by the engine each target depends on.

## Why Rekords

Rekords is meant to stay simple and lightweight. It ships no database of its own and uses the one
the platform already has: the operating system's SQLite on Android, Apple platforms and Linux, and
IndexedDB in the browser, on Kotlin/JS and Kotlin/Wasm. The JVM is the exception, where the SQLite
comes bundled with `androidx.sqlite`. How much smaller an application gets has not been measured
yet, but where every megabyte counts, it may be worth a look.

The API is not tied to SQL at all: an engine can keep rekords in whatever storage it likes. Models,
schemas and queries are written once in common code, and every engine supports everything
`rekords-core` offers, so they behave the same on every target.

## What you get

- Plain Kotlin classes, mapped by `kotlinx.serialization` — no code generation step.
- Filters, ordering, paging and counting.
- Nested rekords and lists of them, stored once and shared between the rekords referring to them,
  and filtered by their fields.
- Transactions: several operations applied together or not at all.
- Versioned schemas with migrations run in a transaction of their own.
- Engines found on their own: common code creates the store, each target's dependencies choose the
  storage — SQLite, IndexedDB, or memory for tests and caching. Another engine can be plugged in.
- Encrypted fields: `rekords-crypto` encrypts single fields with AES, deterministically, so that
  rekords are still looked up by them, or randomized.

## When to use something else

Rekords is not a drop-in replacement for Room or SQLDelight. If full SQL on every target suits you
and you need complex queries, those libraries are the better choice. Rekords is for a simple API
that does not depend on the storage, and a smaller footprint.

## Modules

| Module              | What it is                                                     | Targets                                                                                                 |
|---------------------|----------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `rekords-core`      | Annotations, schema, `RekordsStore`, filters and the engine API | Android, JVM, JS, Wasm JS, Wasm WASI, iOS, macOS, tvOS, watchOS, Linux, Windows (MinGW), Android Native |
| `rekords-sqlite`    | Engine keeping rekords in SQLite, through `androidx.sqlite`     | Android, JVM, iOS, macOS, tvOS, watchOS, Linux (no iOS simulator on Intel)                           |
| `rekords-indexeddb` | Engine keeping rekords in the browser's IndexedDB               | JS, Wasm JS                                                                                             |
| `rekords-memory`    | Engine keeping rekords in memory, for tests or as a cache       | Same as `rekords-core`                                                                                  |
| `rekords-crypto`    | AES cipher encrypting the fields of rekords on every engine     | Android, JVM, JS, Wasm JS, iOS, macOS, tvOS, watchOS, Linux, Windows (MinGW)                          |
| `rekords-sql`       | The SQL editor the SQL engines are built on                     | Same as `rekords-sqlite`                                                                                |

Apple platforms are built for Apple silicon, and iOS for the simulator on Intel as well. The targets
Kotlin [deprecates](https://kotl.in/native-targets-tiers) — macOS, tvOS and watchOS on Intel, and
32-bit watchOS — are not.

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

The [`sample`](sample) module runs every scenario below on the JVM, the cache in memory included:
`./gradlew :sample:run`.

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

The application keeps nothing but its latest schema. The classes and schemas of past versions are
not kept, only the steps between them: `onUpgrade` takes a storage of any earlier version through
every `if (oldVersion < n)` after its own, in turn, up to the latest.

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

A rekord is selected once however many of its nested rekords match, and is read back whole, its
lists with every item they hold. Nested filters look as deep as rekords are nested:

```kotlin
Filter.Nested(NoteRekord.FOLDER, Filter.Nested(FolderRekord.OWNER, Filter.Equals(UserRekord.ID, 42L)))
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

### Encryption

A field is encrypted by its declaration, and the store is given the cipher to encrypt it with. The
engine is handed nothing but ciphertext, kept as text, whichever engine it is:

```kotlin
@Rekord(type = "account")
class AccountRekord(
    @Field(name = ID, id = true)
    val id: Long,
    @Field(name = EMAIL, searchable = true, encryption = Encryption.Deterministic)
    val email: String,
    @Field(name = BALANCE, encryption = Encryption.Randomized)
    val balance: Double,
    @Field(name = NOTE, encryption = Encryption.Randomized)
    val note: String?,
)

val store = RekordsStore(AccountsSchema(), cipher = AesRekordsCipher(key)) {
    name = "accounts.db"
}

val account = store
    .getRekord<AccountRekord>(Filter.Equals(AccountRekord.EMAIL, "alice@example.com"))
    .getOrThrow()
```

- `Encryption.Deterministic` encrypts the same value to the same ciphertext, so rekords are
  selected by whether the field equals a value — `Filter.Equals`, `Filter.InList`, `Filter.Not` of
  them — and the field can be an id or searchable. The storage can tell which rekords hold the same
  value, so a field of a few values, such as a flag, is better encrypted randomized.
- `Encryption.Randomized` encrypts the value anew each time. Rekords are selected by nothing but
  whether the field is null, so it can be neither an id nor searchable.
- An encrypted field cannot be compared by a range, `Filter.Contains` or an order: the operation
  fails with an `IllegalArgumentException`. A field holding a rekord is not encrypted itself — its
  rekord's fields are, each as declared.
- Encrypting a field already stored changes how it is kept: a migration removes and adds it again,
  rather than the declaration alone. A store whose schema encrypts a field fails without a cipher.

`AesRekordsCipher` from `rekords-crypto` encrypts randomized fields with AES-256-GCM and
deterministic ones with SIV — AES-256-CTR from an HMAC-SHA256 synthetic IV — under keys it derives
from one 32-byte key. Keeping the key is the application's business, in the Android Keystore or the
iOS Keychain; `AesRekordsCipher.generateKey()` makes a new one. The primitives are the platform's
own through [cryptography-kotlin](https://github.com/whyoleg/cryptography-kotlin), and
[@noble/ciphers](https://github.com/paulmillr/noble-ciphers) in the browser: a cipher is run inside
the editor's transactions, so it is synchronous, which WebCrypto is not. Any other `RekordsCipher`
can be given to a store in its place.

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

### Caching in memory

An in-memory store can serve as the application's cache in front of a persistent one: rekords are
read and written in memory, written to the storage from time to time — on a timer, or as the
application goes to the background — and read back from it at start:

```kotlin
val cache = RekordsStore(NotesSchema(), InMemory)
val storage = RekordsStore(NotesSchema()) {
    name = "notes.db"
}

// At start: fill the cache from the storage.
cache.putRekords(storage.getRekords<NoteRekord>().getOrThrow()).getOrThrow()

// From time to time: replace what the storage holds with what the cache does, in one transaction.
val notes = cache.getRekords<NoteRekord>().getOrThrow()
storage.transaction {
    delete<NoteRekord>()
    putRekords(notes)
}.getOrThrow()
```

- `InMemory` does not register itself, so a target depending on `rekords-memory` still creates the
  storage with the engine it depends on.
- Nested rekords are written along with the rekords holding them. The ones a deleted rekord held
  stay in the storage, though, until their own type is deleted.
- A transaction runs nothing but its own store's operations, so the cache is read before it. What is
  written to the cache meanwhile waits for the next write to the storage, and what the storage has
  not been given yet is lost with the process.
- Writing everything again costs as much as the cache holds. A bigger cache can write only the
  rekords that changed, and delete the ones removed, if the application keeps track of them.

## Platform notes

### SQLite

- **Android** keeps the database in the application's database directory, through the framework's
  SQLite. The module's manifest declares a content provider handing the application context over
  to the engine, so nothing has to be passed to create a store.
- **JVM** keeps the database at the path `name` is, relative to the working directory, through
  the SQLite bundled with `androidx.sqlite:sqlite-bundled`.
- **Windows** is reached through the JVM, whose `sqlite-bundled` carries the SQLite for Windows
  x64 — a desktop application, say. `androidx.sqlite` is not published for Kotlin/Native on
  Windows, so neither is the engine: there `mingwX64` keeps rekords in memory alone.
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

## License

Rekords is distributed under the [MIT License](LICENSE).
