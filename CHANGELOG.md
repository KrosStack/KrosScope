# Changelog

All notable changes to KrosScope are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Unreleased

### Added

- **Diagrams.** `kscope diagram modules`, `components` and `symbol` draw how modules depend on each other, how
  repositories connect, and the types around a class or the calls around a function. The output is Mermaid, Graphviz
  DOT or JSON with a published [schema](docs/schemas/README.md), kept within `--links` and `--characters`, and a
  running server answers it like the other queries.
- **Diagrams in the map.** The map panel of a function or a type draws its calls or its neighbour types, from the
  links the index holds, with a depth of 1, 2, 3 or all. A click on a node of the diagram selects that symbol.
- **`kscope status`.** One line that says which index answers, its level, how long ago it was scanned, how far behind
  it is (every repository, its commit and its working tree, within 10 seconds, and at most 200,000 files in a folder
  that Git does not track) and whether a server runs. It reads the index file read-only and the files of Git, never
  opens the store, never starts a server and never asks one. An index of another format is named, not replaced. Exit
  code 0 for any index that can be read, also when it is behind; 1 for no index, a damaged one or another format.
  `--format json` has a schema, `docs/schemas/status.schema.json`.
- **`processStart` in `serve.json`.** The server records the start time of its process, which `status` compares with
  the system's to tell a server from another process that has its number.

### Changed

- **`analyze --format json` is capped.** `symbols` was every symbol with a score above 0, which made the JSON of one
  task on a large project 240 MB. It is now the top 50 symbols, the top 10 with `--detail brief` and up to 1,000 with
  `--detail full`, in ranking order. `files` follows the relevance cut-off of the text output (a score of at least 30%
  of the best, and the three best always), except that it is not cut when learned ranking is applied, and
  `--detail full` lists every scored file. `symbolsOmitted` and `filesOmitted` give how many entries were left out;
  each is present only when above 0. `--detail` is now accepted with `--format json` for `analyze`; `--sections` and
  the other commands still refuse it. The `schemaVersion` of the analysis JSON is 3; the other results keep theirs.
  The ranking behind `context`, the benchmark and the MCP tools is unchanged.
- **The skill.** It now starts with `kscope status`, says to run `kscope scan` when the index is behind, asks for
  `analyze` at `--detail standard`, and reads `context` from standard output, without a temporary file.

- **Index of a parent folder.** `info`, `analyze`, `context`, `impact`, `related`, `symbol`, `graph` and `diagram`, run without
  `--workspace` in a folder that has no index, use the index of the nearest parent folder and say so: one line on
  standard error, or the field `indexLocation` in JSON. A file argument can be typed from the current folder. A name
  that several repositories declare means the one declaration of the current repository, and the command says in one
  line that it chose it. When no index is found, the error names folders up to two levels below that have one. `scan`
  says when it makes an index in a folder that the index of a parent folder already covers.
- **Search limited to one repository.** Below a root of several repositories, `analyze` of a task, `context` and
  `symbol` run in a parent folder's index now search only the repository that holds the current folder, and the
  line on standard error says so and how to widen it (`--workspace <root>`). A repository at the workspace root is
  named as `info` lists it. In `symbol`, an id, qualified name or `path:line` outside the repository is shown with a
  line that says where it lies; a simple name that only another repository declares widens the search to the whole
  workspace, with a line that says so. A search that reaches its cap of 50,000 candidates says that the results may
  be incomplete and names `--workspace <root>`; a simple name in `symbol` is not widened when the search of the
  repository stopped at the cap without a hit. JSON adds the optional fields `narrowedTo`, `outsideNarrowing`, `widenedFrom` and `incomplete` to
  `analysis`, `context` and `symbol`. The tests, boundaries and risks of an analysis are not limited. Other commands,
  an index of one repository, a folder that belongs to no repository and `--workspace` answer as before. See ADR
  0026.
- **A shared simple name in `symbol` is narrowed first.** When several declarations in the current repository bear
  the name and others lie elsewhere, `symbol` now reports an ambiguity of the declarations inside that repository
  only, with `narrowedTo` in JSON and the limit line in text. Ids, qualified names and `path:line` are not narrowed.
- **Freshness.** `info` shows when the index was scanned and what a quick check finds behind since then. `analyze`,
  `context` and `impact` end with one line on standard error when a repository is at another commit than the scanned
  one, or when the repository of the current folder has changed files, and their JSON then has the object
  `indexFreshness`. The commit of every repository is read from the files of Git, without a process. Only the
  working tree of the repository of the current folder is compared. A scan writes its times to `.kscope/scan.json`.
  A reftable repository and a reference that the files cannot resolve are asked from Git. `packed-refs` is read up
  to the reference. An index without `scan.json` says `Index may be behind: the time of the scan is not known; the
  next scan records it.` In `analyze`, `context` and `impact` the field `indexFreshness` is absent when nothing is
  behind. A scan also records, for each Git repository, the paths that Git reports as changed or untracked, up to
  10,000. A path that was changed at the scan and is clean now (restored or reverted) counts as a change. A scan
  file without that list (also a missing one) or a list over the bound gives `Index may be behind` with its reason,
  never "not behind", also when the tree is clean. A path that is changed now and newer than the scan still makes
  the index behind; the line then says `at least` that many changed files and gives the reason.

## 0.1.0 - 2026-10-25

First release.

### Added

- **Index.** `kscope scan` builds a graph from Kotlin and Java sources, Gradle files, Android resources and manifests,
  AIDL, proto, SQLDelight, Room and JNI declarations, plus a text search index and signals from Git history. Scans are
  incremental, and `--verify` checks the result against a clean rebuild. Three levels: `syntax`, `resolved` and `exact`.
- **Questions.** `analyze` ranks the files, modules and tests of a task or describes one name, `context` compiles
  source within a token budget, and `info`, `symbol`, `impact`, `related` and `graph` inspect the index.
- **Output.** Text, JSON with published [schemas](docs/schemas/README.md), Markdown and Graphviz DOT, with `--detail`
  and `--sections` to control how much is printed.
- **Several repositories.** Checkouts side by side, or registered with `kscope workspace add`, are indexed together
  and linked through Maven coordinates, JAR and AAR files, native libraries, specs and web bundles.
- **Always ready.** `kscope serve`, with `--watch` and a browser `--map`; `kscope service`, which keeps several
  workspaces ready in the background and can start with your session.
- **Coding agents.** `kscope mcp` offers the commands as tools, and `kscope skill` installs a skill that teaches agents
  to use them.
- **Settings and safety.** `kscope.toml` per workspace; `kscope trust` for what may run a workspace's build.
- **Distribution.** Installers for macOS, Linux and Windows, and release workflows that publish archives with their
  SHA-256 sums.
- **Evidence.** 23 relationship classes scored against gold data, and benchmarks on real repositories with
  [published results](docs/benchmarks/README.md).
