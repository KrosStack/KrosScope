# Third-party licences

KrosScope is licensed under the [Apache License 2.0](https://github.com/KrosStack/KrosScope/blob/main/LICENSE). This page lists what its distribution bundles and
what its build uses, with the licence of each component. It follows the licence policy of
[ADR 0001](decisions/0001-platform-and-licensing.md): Apache-2.0, MIT and BSD for what is distributed, EPL-2.0 in test
scope only, and anything else needs an ADR. **Every component below is within that policy.**

The release archive carries this page as `THIRD-PARTY-LICENSES.md`, next to `LICENSE`, `NOTICE` and a `licenses`
directory with the texts that some of the licences require.

## How the licences were determined

Each licence comes from the artifact itself: the `<licenses>` element of its POM in the Gradle cache, the POM of its
parent where the component declares none, or a licence file inside the jar. Where a POM names two licences, the
component is available under either, and KrosScope uses it under the permissive one. Artifacts whose POM names no
licence, or that publish no POM, are marked, and the licence file of the jar is named instead.

## Distribution: the application (`lib`)

| Component | Version | Licence | Where it is used |
|---|---|---|---|
| KrosScope modules (`model`, `spi`, `store`, `engine`, `service`, `cli`, `benchmark`, `retrieval`, `context`, `android`, `compose`, `gradle`, `history`, `jni`, `kotlin`, `schema`; `gradle` carries the Gradle model plugin as a resource) | 0.1.0 | Apache-2.0 | This project. |
| Circle packing of the browser map, adapted from d3-hierarchy | 3 | ISC (`licenses/d3-hierarchy-ISC.txt`), a permissive licence equal in effect to MIT; see [ADR 0021](decisions/0021-browser-map.md) | `kscope serve --map`. |
| Kotlin standard library | 2.4.20 | Apache-2.0 | Everywhere. |
| Kotlin reflection | 2.3.20 | Apache-2.0 | Transitive dependency of the Kotlin libraries. |
| JetBrains annotations | 13.0 | Apache-2.0 | Transitive dependency of the Kotlin and Gradle libraries. |
| kotlinx.serialization (core, JSON) | 1.11.0 | Apache-2.0 | JSON of the model, the results and the worker protocol. |
| Clikt (core, with Mordant terminal support) | 5.1.0 | Apache-2.0 | Command line of `kscope`. |
| Mordant (core, JNA, FFM and Graal bindings, omnibus) | 3.0.2 | Apache-2.0 | Terminal detection and colour output through Clikt. |
| colormath | 3.6.0 | MIT (`licenses/colormath-MIT.txt`) | Colour handling of Mordant. |
| Java Native Access (JNA) | 5.14.0 | Apache-2.0 or LGPL-2.1-or-later; used under Apache-2.0 | Asks the operating system whether standard error is a console; also loaded by Mordant. |
| sqlite-jdbc | 3.53.4.0 | Apache-2.0; bundles code under BSD-2-Clause (`licenses/sqlite-jdbc-zentus-BSD-2-Clause.txt`) | The SQLite index of `core/store`. |
| Gradle Tooling API | 9.8.0 | Apache-2.0 | Reading the exact build model for `scan --trust-build`. |
| Apache Groovy | 5.1.3 | Apache-2.0; bundles ANTLR 4 runtime and ASM under BSD-3-Clause (`licenses/groovy-antlr4-BSD-3-Clause.txt`, `licenses/groovy-asm-BSD-3-Clause.txt`) and picocli under Apache-2.0 | Reading Groovy build scripts. |
| SLF4J API and NOP binding | 2.0.17 | MIT | The Tooling API logs through SLF4J; the NOP binding silences it. |
| TOML parser (tomlj) | 2.2.0 | Apache-2.0 | Reading Gradle version catalogs and Cargo manifests. |
| Wire (schema, runtime) | 7.1.0 | Apache-2.0 | Reading Protocol Buffers definitions. |
| Okio | 3.18.2 | Apache-2.0 | Transitive dependency of Wire. |
| KotlinPoet | 2.3.0 | Apache-2.0 | Transitive dependency of Wire. |
| JavaPoet (Palantir) | 0.19.0 | Apache-2.0 | Transitive dependency of Wire. |
| JSqlParser | 5.4 | Apache-2.0 or LGPL-2.1; used under Apache-2.0 | Reading SQLDelight and Room SQL. |
| JTokkit | 1.1.0 | MIT (`licenses/jtokkit-MIT.txt`) | Token counting (`o200k_base`, `cl100k_base`). |
| Guava | 33.7.1-jre | Apache-2.0 | Transitive dependency. |
| Guava failureaccess | 1.0.3 | Apache-2.0 | Transitive dependency of Guava. |
| Guava listenablefuture | 9999.0-empty-to-avoid-conflict-with-guava | Apache-2.0 | Empty placeholder artifact of Guava. |
| J2ObjC annotations | 3.1 | Apache-2.0 | Transitive dependency of Guava. |
| Error Prone annotations | 2.50.0 | Apache-2.0 | Transitive dependency of Guava. |
| JSpecify | 1.0.1 | Apache-2.0 | Transitive dependency of Guava. |

## Distribution: the Kotlin analysis worker (`workers/kotlin/lib`)

The worker is a second JVM that analyses Kotlin and Java sources. It is built from these artifacts and the KrosScope
modules `kotlin-worker`, `model` and `spi`.

| Component | Version | Licence | Where it is used |
|---|---|---|---|
| Kotlin compiler (`kotlin-compiler`) | 2.4.10 | Apache-2.0 (see below for what it embeds) | Parsing and resolution inside the Analysis API. |
| Kotlin Analysis API (`analysis-api`, `-impl-base`, `-k2`, `-platform-interface`, `-standalone`, `low-level-api-fir`, `symbol-light-classes`, all `-for-ide`) | 2.4.10 | Apache-2.0 | Resolved symbols, references and calls. |
| Kotlin build tools API | 2.4.10 | Apache-2.0 | Transitive dependency of the Analysis API. |
| Kotlin script runtime, stdlib JDK 7 and 8 shims | 2.4.10 | Apache-2.0 | Transitive dependencies. |
| Kotlin standard library | 2.4.20 | Apache-2.0 | Everywhere. |
| Kotlin reflection | 1.6.10 | Apache-2.0 | Transitive dependency of the Analysis API. |
| kotlinx.coroutines (IntelliJ build) | 1.10.2-intellij-2 | Apache-2.0 | Required by the Analysis API. |
| kotlinx.serialization (core, JSON) | 1.11.0 | Apache-2.0 | Worker protocol. |
| Caffeine | 2.9.3 | Apache-2.0 (licence file of the artifact; its POM was not available) | Caches of the Analysis API. |
| Checker Framework qualifiers | 3.19.0 | MIT | Transitive dependency of Caffeine. |
| Error Prone annotations | 2.10.0 | Apache-2.0 | Transitive dependency of Caffeine. |
| JetBrains annotations | 23.0.0 | Apache-2.0 | Transitive dependency. |

### What the Kotlin compiler artifact embeds

`kotlin-compiler-2.4.10.jar` is one jar that embeds code from other projects, and it carries no licence files of its
own. Its POM states Apache-2.0 only. The package names inside it identify these embedded libraries; their licences
are those that the upstream projects publish, and none is outside the policy of ADR 0001:

| Package root | Library | Licence of the upstream project |
|---|---|---|
| `com.intellij` | IntelliJ Platform | Apache-2.0 |
| `com.sun.jna` | JNA | Apache-2.0 or LGPL-2.1-or-later |
| `it.unimi.dsi` | fastutil | Apache-2.0 |
| `com.google.common` | Guava | Apache-2.0 |
| `org.antlr.v4` | ANTLR 4 runtime | BSD-3-Clause |
| `org.jetbrains.org.objectweb.asm` | ASM (repackaged) | BSD-3-Clause |
| `org.jline`, `org.fusesource.jansi` | JLine, JANSI | BSD-3-Clause, Apache-2.0 |
| `io.vavr`, `io.opentelemetry.api`, `one.util.streamex`, `kotlinx.collections.immutable` | Vavr, OpenTelemetry API, StreamEx, kotlinx.collections.immutable | Apache-2.0 |
| `com.fasterxml.aalto`, `org.codehaus.stax2` | Aalto XML, Stax2 API | Apache-2.0, BSD-2-Clause |
| `net.jpountz` | LZ4 Java | Apache-2.0 |
| `org.jdom`, `org.picocontainer`, `javax.inject`, `org.apache.log4j` | JDOM, PicoContainer API, JSR-330, Log4j 1 API | Permissive (JDOM, PicoContainer), Apache-2.0 |

This table is read from the package names of the jar and the upstream projects' published licences, not from files in
the jar. The texts that the BSD licences of JLine, the Stax2 API, the ANTLR 4 runtime and ASM and the JDOM licence require
in a binary distribution are in `licenses/` and named in `NOTICE`. [ADR 0001](decisions/0001-platform-and-licensing.md) left the licence of the Analysis API artifacts open until
published POM metadata could be read; the POMs of all `-for-ide` artifacts and of the compiler now state Apache-2.0.
The Analysis API jars themselves contain only `org.jetbrains.kotlin` code.

## Build plugins and tools (not distributed)

| Component | Version | Licence | Where it is used |
|---|---|---|---|
| Kotlin Gradle plugin | 2.4.20 | Apache-2.0 | Compiles every module. |
| Kotlin serialization compiler plugin | 2.4.20 | Apache-2.0 | Generates serializers. |
| Detekt Gradle plugin | 2.0.0-alpha.6 | Apache-2.0 | Its task type runs the static analysis; the plugin itself is not applied. |
| Detekt CLI | 2.0.0-alpha.6 | Apache-2.0 | Static analysis of the sources. |
| Detekt API | 2.0.0-alpha.6 | Apache-2.0 | The base of the project's own Detekt rule in `tools/detekt-rules`; compile scope of that module only. |
| Detekt test support (with its test utilities) | 2.0.0-alpha.6 | Apache-2.0 | Tests of that rule. |
| Detekt ktlint wrapper | 2.0.0-alpha.6 | Apache-2.0 | Formatting rules; embeds ktlint repackaged as `ktlint-repackage` 2.0.0-alpha.3 (licence file of the jar: Apache-2.0). |
| Gradle plugins `base`, `java-library`, `application`, `kotlin-dsl` | Gradle 9.8.0 | Apache-2.0 | Built into Gradle; they shape the build and the archive. |

## Test scope (not distributed)

| Component | Version | Licence | Where it is used |
|---|---|---|---|
| JUnit Jupiter (API, engine, params, aggregate), JUnit BOM | 5.14.4 | EPL-2.0 | Unit and integration tests. |
| JUnit Platform (launcher, engine, commons) | 1.14.4 | EPL-2.0 | Test execution. |
| kotlin-test, kotlin-test-junit5 | 2.4.20 | Apache-2.0 | Test assertions. |
| opentest4j, apiguardian | 1.3.0, 1.1.2 | Apache-2.0 (licence file of the jar; their POMs carry no licence element) | Transitive dependencies of JUnit. |

JUnit Jupiter's aggregate and params artifacts publish no POM licence element; the licence is that of the JUnit
artifacts that declare it (`junit-jupiter-api`, `junit-jupiter-engine`, `junit-platform-*`: Eclipse Public License
v2.0).
