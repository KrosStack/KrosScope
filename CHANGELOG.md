# Changelog

All notable changes to KrosScope are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

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
