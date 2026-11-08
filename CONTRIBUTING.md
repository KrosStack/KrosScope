# Contributing to KrosScope

Thank you for helping with KrosScope. This page explains what a good change looks like and how to check it
before you send it. By taking part you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Set up and check your change

[docs/development.md](docs/development.md) covers the prerequisites, the Gradle commands, how to build and run
the `kscope` tool and how to run the benchmarks. The one command to remember is:

```shell
./gradlew check
```

It compiles every module with Kotlin warnings as errors, runs the tests and Detekt, and enforces the module
dependency rules and a limit of 400 lines per Kotlin file. A change is ready when it passes, and CI runs the
same command. For a change in one module, `./gradlew :<module>:check` is faster while you work.

Enable the pre-commit hook once per clone, so that the same gate runs before every commit and CI confirms a
result instead of discovering one:

```shell
git config core.hooksPath .githooks
```

The hook runs `./gradlew check` without your personal Git configuration, as a CI runner would. CI itself runs
once per pull request into `main`, on Linux; a pull request that changes only documentation starts
no run, and the Windows run is started by hand.

Before you start on a large change, or on a change to a public contract (`core/model`, `core/spi`, the store
schema or the command-line contract), please open an issue and describe what you intend.

## Code standards

[docs/code-guidelines.md](docs/code-guidelines.md) is binding; Detekt enforces what it can and review enforces
the rest. In short:

- Names state intent, and a comment is reserved for a non-obvious reason why. No commented-out code, and no
  to-do markers without a linked issue.
- A function has at most 40 lines, 5 parameters, nesting depth 3 and cyclomatic complexity 10.
- Finite states are sealed hierarchies. Expected failures are values, not exceptions. Data is immutable.
- Output is deterministic. Facts are sorted by a stable key, and contain no clock time, random value, absolute
  path or host name.
- Tests are deterministic and offline, and new behaviour comes with a test.
- Production code is Kotlin. Another language needs a decision record.

The module dependency rules in [docs/architecture.md](docs/architecture.md#module-map) are checked by the
build, so a violation fails the build and is not left to review. In short, `core/model` and `core/spi` are the
public contracts, analyzers and intelligence modules depend on them only, and `service` is the one module that
wires everything together.

## Evidence discipline

Every fact that a producer emits names its producer, its evidence class and its resolution state, defined in
[docs/architecture.md](docs/architecture.md#internal-representation). The rule is that a producer never claims
more precision than it established. When it is unsure it emits `heuristic`, or `unresolved` with a reason
code, and it never guesses. This is what lets a reader trust a clean result.

## Adding support for a kind of artifact

New support is added by registering capabilities in the registry that `service` assembles, not by editing
unrelated logic ([ADR 0008](docs/decisions/0008-capability-registry.md)).

1. **Detect.** Add an `ArtifactDetector` that classifies the files into an `ArtifactType`. It is the only place
   that inspects file names and extensions.
2. **Produce or link.** Add a `FactProducer` that emits symbols and relationships from the artifact, or a
   `BoundaryLinker` that joins the two ends of a link across artifacts, and declare its capabilities.
3. **Test.** Test the producer against checked-in inputs with golden outputs.
4. **Claim only what is proven.** To claim a relationship, give it a class in the gold data of the reference
   workspace and a row in the [support matrix](docs/support-matrix.md). A level of support is claimed only
   where a gold-backed test proves it.

## Measurements and claims

The [benchmark methodology](docs/benchmark.md) is the policy. Its core rules:

- Tune on development data, freeze the implementation, then score test data once. A held-out set that has been
  scored is spent and never guides tuning.
- Do not change thresholds or gold data after seeing a result.
- Never report a token reduction without task-success results beside it.
- Publish the method, environment and commit with a result, and publish the losses with the wins.

## Commits and pull requests

- A commit message is imperative and scoped, and it explains why when that is not obvious. The scope is the
  area that changed, for example `cli: show progress and readable output`, `context: pack raw source only
  below 8000 tokens` or `docs: add the roadmap`.
- A pull request says what changed and why, and how you verified it. If it changes a benchmark result, it
  updates the page in [docs/benchmarks](docs/benchmarks/README.md).
- Do not weaken a rule to make a change pass. In particular, do not add a Detekt baseline, and state the
  reason for any suppression.

## Releasing

1. **Prepare the release in a pull request to `main`.** Set `version` in `gradle.properties`. Give the version
   its own section in `CHANGELOG.md`, headed `## <version> - <date>`. Versions follow
   [Semantic Versioning 2.0.0](https://semver.org).
2. **Create the tag.** Run the *Create release tag* workflow on `main` from the Actions tab with that version,
   for example `0.2.0`. It checks the version and the changelog section, then tags the last commit of `main`
   as `v0.2.0`. A version with a hyphen, such as `1.0.0-rc.1`, becomes a prerelease.
3. **Publication follows.** The tag starts *Publish release*. It runs the check, builds the distribution and
   confirms the packaged `kscope` reports the version. It then creates the GitHub release, which carries
   `kscope-<version>.zip`, `kscope-<version>.tar.gz`, their `SHA256SUMS`, and the changelog section as notes.
   Running it again by hand with the same tag replaces the archives and notes.

Run *Publish components* on `main` in `KrosStack/KrosScope` with a `YYYY.MM` or `YYYY.MM.N` version.
It publishes the runtime ZIP and `SHA256SUMS` under `components-<version>` without changing the latest CLI release.
Upload the separate int8 model files (`LICENSE`, `model.json`, `model.onnx`, `tokenizer.json`) to that same release.
Pin their sizes and SHA-256 hashes in the component manifest only after all assets are published; existing assets are immutable.

## Documentation

Public documentation is the [README](README.md), the [changelog](CHANGELOG.md) and the `docs` directory, whose
pages are listed in the README.

- Every command, option, file name and number in a document must be verifiable in the repository. Do not
  document features that do not exist.
- Use relative links, and make sure each one resolves.
- Do not put absolute local paths, user names, e-mail addresses, tokens or machine identifiers in any
  tracked file, and do not add proprietary or third-party company material. The fixtures are original.
- Record a significant technical decision as a decision record, using the template in
  [docs/decisions/README.md](docs/decisions/README.md). An accepted record is superseded by a later one, not
  edited.
- Add a line to [CHANGELOG.md](CHANGELOG.md) for a change that people who use KrosScope would notice.

## Licensing

KrosScope is licensed under the [Apache License 2.0](LICENSE). Unless you state otherwise, a contribution you
submit is licensed under the same terms.

Dependencies are declared through the version catalog and are limited to Apache-2.0, MIT and BSD licences,
plus EPL-2.0 for test scope. Any other licence needs a decision record; see
[ADR 0001](docs/decisions/0001-platform-and-licensing.md). Third-party code is not copied into the
repository; if an exception is ever approved it comes with its licence text and an entry in `NOTICE`.

## Security and questions

Report a vulnerability privately, as described in [SECURITY.md](SECURITY.md); do not open a public issue for
it. For anything else, open an issue.
