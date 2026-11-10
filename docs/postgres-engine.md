# A PostgreSQL engine: notes

Nothing of it is built yet. These notes gather what was found while adding `Filter.Glob`,
`ignoreCase` and full text search (up to `a161ccf`), so that a server engine starts from what the
SQL engine already assumes rather than finding it out test by test.

The Postgres specifics below come from its documentation, not from a server run against them: each
is to be tried before it is relied on.

## The contract

An engine is right when it selects, reads and writes what every other engine does. The shared
suites in `rekords-test` say what that is — `RekordsEditorTest`, `RekordsMigrationTest`,
`RekordsEmbeddedTest`, `RekordsEncryptionTest`, `RekordsFullTextTest` and the rest — and a Postgres
engine runs them the way `JvmSQLiteRekords*Test` do, against a server Testcontainers starts.

What a filter selects is decided in core, not by the engines: `RekordValues.matches` is the
reference, the tokenizer of full text search is `fullTextWords`, and the null semantics are written
down in the KDoc of `Filter`. Where Postgres would answer otherwise by itself, the engine is to
emulate core, not the other way round.

## What carries over

- `SQLDriver` / `SQLConnection` are not tied to SQLite: a JDBC driver adapter opens a connection
  from a URL in place of a file path, and answers `tableSchema` from `information_schema.columns`
  where SQLite has `PRAGMA table_info` (`SQLiteSQLDriver`).
- `SQLConditionBuilder`: nested rekords as correlated `EXISTS` over aliased tables, `IN` lists,
  comparisons, `IS NULL`.
- Tables per rekord type, junction tables per list, id columns named after the fields holding a
  rekord, the `_meta` table holding the schema version.
- `BEGIN` / `COMMIT` / `ROLLBACK` as statements, and nested transactions joined to the outer one.
- Encryption: it happens in core, and the storage is handed ciphertext as text. A deterministic
  field is still selected by equality.

`rekords-sql` is built for the SQLite targets alone (`6c50bf8`); a server engine needs it on the
JVM target with a driver of its own.

## SQLite dialect in rekords-sql

| Where | SQLite | Postgres |
|---|---|---|
| `SQLConnection.upsert` | `UPDATE OR IGNORE`, then `INSERT OR IGNORE` | `INSERT … ON CONFLICT (ids) DO UPDATE`. There is no `UPDATE OR IGNORE`: an update clashing with a key fails the statement. And two statements are not atomic under concurrent writers. |
| `SQLConnection.insertOrIgnore` | `INSERT OR IGNORE` | `INSERT … ON CONFLICT DO NOTHING` |
| `SQLConnection.select` | `LIMIT -1 OFFSET n` for an offset alone | `OFFSET n` |
| `SQLRekordsEditor.INSERTION_ORDER` | lists are read back in `rowid` order of their junction rows | No `rowid`: a junction table needs a position column of its own, written in the order of the list. |
| `SQLRekordsEditor.firstOfEachCondition` (`distinctBy`) | the first row of each set of values by `NOT EXISTS` an earlier one: `IS` compares values null or not, `rowid` breaks ties | `IS NOT DISTINCT FROM` for `IS`; no `rowid`: the ids break ties. `DISTINCT ON` does it in one, ordered by the fields first. |
| `SQLRekordsSchemaEditor.enableFullText` | rows found by `rowid` | rows found by their ids, or one `UPDATE … FROM` |
| `SQLRekordsSchemaEditor.recreateTable` | a table is rebuilt to drop, rename or add a `NOT NULL` column | `ALTER TABLE … DROP / RENAME COLUMN`, `ADD COLUMN … NOT NULL DEFAULT` and dropping the default after. Indexes follow by themselves. |
| `SQLCondition`: `whenNone`, empty `InList`, `NOT COALESCE(…, 0)` | `1` and `0` are booleans | `TRUE` / `FALSE`, `COALESCE(…, FALSE)` |
| `SQLType` | `INTEGER` holds an `Int`, a `Long`, a `Boolean` and a date's days | `BIGINT` for all of them, or `BOOLEAN` for a boolean, with the reading side told which. |
| Names | tables and columns unquoted | Unquoted names are folded to lower case and some are reserved (`user`, `order`): quote every name. |
| Placeholders | `?` | `?` through JDBC; `$1…` natively |

## Semantics built around SQLite

These were made to match what SQLite can do without ICU and without functions of its own. On a
server the engine owns the database and can create functions, so each can also be a function
installed by the first migration instead of SQL built per query.

- **Text of a value.** `Contains`, `Glob` and `WordPrefix` look into a field that holds no text as
  the text of its value — SQLite converts by itself (`instr(3, '3')`). Postgres has to be told:
  `col::text`. The text of a `Double` must be the one Kotlin's `toString` gives, or the engines part
  ways; a `Boolean` stored as an integer reads as `1`, not `true`.
- **`Contains`** — `instr(col, ?) > 0`: case counts, `%` and `_` stand for themselves. Postgres:
  `strpos(col, ?) > 0`.
- **`Glob`** — Postgres has no `GLOB`. The pattern is to be turned into an anchored regular
  expression keeping every rule `Glob.kt` keeps: `]` first in a set, `-` at either end, a range
  after a range, a set left open matching nothing, `?` matching one code point. `GlobTest` holds
  the cases, checked against SQLite itself.
- **`ignoreCase`** — A–Z alone, because SQLite's `lower` without ICU folds nothing else. Postgres
  `lower` folds every letter as the locale says, so it would select more. Fold A–Z alone:
  `translate(col, 'ABC…Z', 'abc…z')` — and an expression index on that if it has to be fast.
- **Null** — `Not` selects the rekords holding null (`NOT COALESCE(cond, FALSE)`), an `InList`
  holding null selects them (`OR col IS NULL`), and nothing is less or more than null. Standard SQL
  already answers like SQLite here, once the booleans are Postgres ones.

## Full text search

The words are told by `fullTextWords` in Kotlin, for every engine, so Postgres is to index the
words it is given and not split text itself. `to_tsvector` / `to_tsquery` would split it their
own way — `spider-man` is not what `fullTextWords` makes of it — and must not be used.

- **Recommended: a `tsvector` of the words, with a GIN index.** Write
  `array_to_tsvector(ARRAY['super', 'mario'])`, which takes the words as they are, and select by
  `col @@ '''sup'':* & ''mar'':*'::tsquery`: a tsquery cast leaves the words as given, and `:*` is
  a prefix. GIN serves prefix queries, so a search no longer reads every row. Whether a word needs
  escaping beyond the quotes is to be tried; a word only ever holds letters, digits and marks.
- **Smallest step: `pg_trgm` over the column as it is now.** Keep `" super mario "` and select by
  `col LIKE '% sup%'` with a trigram GIN index. A word never holds `%`, `_` or `\`. Prefixes shorter
  than three characters gain little from it.
- **A table of words** `(term, ids…)` with a B-tree index. It works anywhere, but has to be kept in
  step by hand on every write and migration.

`enableFullText`, and the column following a field renamed or removed, stay as they are.

## The server model

Bigger than any filter, and to be settled before an engine is:

- **Connections.** `SQLRekordsEditor` keeps one connection and expects one `RekordsStore` running
  one operation at a time. A server needs a pool, a connection per transaction, and an isolation
  level chosen on purpose.
- **Concurrent writes.** Upsert as two statements races under concurrent writers — see the table
  above. Lists, rewritten as delete-then-insert of links, race as well.
- **Migrations.** Several instances starting at once each run `onUpgrade`. The upgrade has to hold
  a lock — `pg_advisory_xact_lock` — and read the version again once it has it.
- **Observing.** A store invalidates the queries it observes on its own writes alone. Writes of
  another instance are not seen; `LISTEN` / `NOTIFY` could carry them, or observing is left to the
  clients.

## An order of work

1. A JVM build of `rekords-sql`, a JDBC `SQLDriver`, and the Testcontainers set-up running one
   shared suite, failing.
2. A dialect behind the places in the table above, SQLite's being what the code does today — so
   that SQLite runs through it unchanged.
3. Postgres's dialect until `RekordsEditorTest` and `RekordsMigrationTest` pass.
4. Text filters, then full text search on a `tsvector`, until their suites pass.
5. The server model: pooling, upsert by `ON CONFLICT`, the migration lock.
