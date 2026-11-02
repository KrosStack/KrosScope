# ADR 0010: Owned SQLDelight table definition reader

Status: Accepted · Date: 2026-10-05

## Problem

Architecture principle 6 prefers a mature component to new parsing code. The schema analyzer reads SQLDelight `.sq`
files for two things: the statements of the queries, and the table definitions from which the row classes of
[ADR 0007](0007-generated-surfaces.md) are derived. A table definition needs the table and column names with exact
positions, the SQL type of each column, its nullability and the Kotlin type of SQLDelight's `AS <Kotlin type>`
clause. That clause is an extension of SQLite's `CREATE TABLE` and is the case in which no SQL parser applies.

## Options considered

1. **SQLDelight's own compiler.** It runs without an IDE, but it is configured by a database's options, a
   compilation unit and the dialect artifacts of the build, runs on a mocked IntelliJ environment, and produces
   Kotlin sources. That is a build step coupled to one SQLDelight version, not a reader of a checked-out repository.
2. **JSqlParser for all statements.** It is already in use for queries. It rejects a column with an `AS` clause,
   and it rejects the SQLite table option `WITHOUT ROWID`, so a valid `.sq` table would yield nothing.
3. **Pre-processing for JSqlParser.** Cutting the `AS` clause out of each column before parsing leaves the library
   to read what the owned code already has to find, and it does not locate names and definitions in the file.
4. **An owned reader** for the `CREATE TABLE` subset the graph uses. Chosen.

## Evidence

- JSqlParser 5.4, under its Apache-2.0 option, parses a plain `CREATE TABLE`, also with a table-level primary key,
  and fails on a column that carries an `AS` clause and on the SQLite table option `WITHOUT ROWID`.
- The subset is small: the column list of one statement cut at its top-level commas, and in each column a name, a
  type word, `NOT NULL`, `PRIMARY KEY` and the `AS` clause. Everything else in a definition is skipped.
- The corpus tests of the analyzer exercise quoted names, table-level primary keys, table options, `AS` clauses
  resolved through imports and tables without a column list.

## Decision

- JSqlParser stays the parser for queries: `SELECT`, `INSERT`, `UPDATE` and `DELETE`, after a light normalisation
  of the SQLite constructs it does not know. Splitting a file into labelled statements stays owned and shared.
- `SqlTableReader` in `analyzers/schema` (package `sqldelight`) reads `CREATE [TEMP] TABLE [IF NOT EXISTS]` with a
  column list: name, SQL type, nullability, the `AS` type resolved through the `import` lines of the file, and the
  source range of the table name and of each column. Table constraints are skipped except the primary key.
- A statement without a column list, such as `CREATE TABLE x AS SELECT`, yields no table. A SQL type it does not
  know maps to `Any` and marks the symbol heuristic.

## Consequences

- Dialect drift is ours to follow. A construct the reader does not know loses that detail of the column, never
  the table, and the symbol says when its type was guessed.
- The reader accepts some definitions that SQLDelight rejects, because it does not validate them.
- Views, virtual tables and `.sqm` migrations add no symbols. The reader is a few small functions that the project
  owns and tests like any other extractor, with the checked-in corpus as the specification of the supported subset.

## Reversal cost

Low. The row-class synthesizer, the producer and the linker depend on the table model (`SqlTable`, `SqlColumn`
and their spans), not on the reader. If JSqlParser accepts the `AS` clause with usable ranges, or SQLDelight
publishes a permissively licensed parser that reads a file without its build, the reader can be replaced behind
`SqlTableReader.read` without touching them.
