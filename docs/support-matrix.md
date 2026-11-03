# Support Matrix

KrosScope claims support per relationship, not per language.

## In short

- A level of support is claimed only where a gold-backed test proves it. Nothing that is absent from the table is
  claimed.
- Precision and recall (P / R) come from the conformance run on the reference workspace, at each of the three scan
  levels: `syntax`, `resolved` and `exact`.
- `syntax` reads declarations and resolves no reference. `resolved` (the default) executes no build code and has no
  compile classpaths. `exact` executes the Gradle build, so the build model is exact and the compiler sees the
  classpaths.
- At `exact` every listed relationship scores 1.00 / 1.00. At `resolved` the one exception is Kotlin calls Java,
  whose recall is below 1.00. At `syntax` the precision of every edge that exists is 1.00, and the recall of the
  classes whose code end needs the compiler is low or zero; it is measured and published, not gated.
- Links between repositories are proven by a second gold set, on a small constructed workspace of six repositories, at
  the default level. For each class the table in "Links between repositories" gives the evidence class that gold
  expects and how many gold links the conformance run found. The figures are not a claim about real repositories.
- The section "Not supported or not analysed" lists what is reported as `unresolved` or `not-analysed` or is not
  looked for at all.

## How a claim is made

A level of support is claimed only where a gold-backed test proves it:

- The relationship has a class in the gold data of the reference workspace ([benchmark methodology](benchmark.md)).
- The conformance run scores the class.
- The test suite fails when the score drops.

Nothing that is absent from the tables is claimed, and a relationship without a gold class is not listed.

Precision and recall (P / R) are measured by the conformance run on the reference workspace at the three levels the
engine offers:

- **Syntax** reads the declarations of Kotlin and Java files without an analysis session. No reference, call or
  inheritance is resolved, so nothing has compiler evidence; each source set says so. A class whose code end needs the
  compiler has no edge at this level, which the table shows as `none / 0.00`: the class is not analysed at `syntax`.
- **Resolved** executes no build code and has no compile classpaths.
- **Exact** executes the Gradle build, so that the build model is exact and the compiler sees the classpaths.

The test suite holds the `syntax` column to the values below: the recall of each class cannot drop, and every edge that
exists is precise.

The evidence class of an edge is the one of the mechanism that established it.

- A hop to a surface that a generator makes is derived from the documented naming rules and is stated as
  `convention`, which gold accepts for `generated-artifact` (see Evidence classes in the methodology).
- Every matched edge of every class below carries the evidence class that gold states, or `convention` where gold
  states `generated-artifact`.

Links between repositories have their own gold data and their own run. The fixture
[`fixtures/workspace-of-repositories`](https://github.com/KrosStack/KrosScope/blob/main/fixtures/workspace-of-repositories/README.md) has six repositories side by
side, and `benchmark/workspace/gold/relationships.json` lists the 19 links between them in 11 classes, each with the
evidence class it is expected to carry. `workspace-conformance` scans the fixture at the default level
(`resolved`), and `WorkspaceLinkConformanceTest` runs the same check: for every class, recall 1.00 and the expected
evidence class on every link found. A class that meets this has a row in "Links between repositories". The run is
described in the [methodology](benchmark.md#workspace-of-repositories).

## Relationships

| Relationship | Established by | Reported as unknown | Proven by (gold class, count) | Syntax P / R | Resolved P / R | Exact P / R |
|---|---|---|---|---|---|---|
| Kotlin `expect` to `actual` | `compiler`; `convention`, probable, for pairs the compiler did not bind | A target whose front end could not run has no compiler edge | `expect_actual` 8 | 1.00 / 1.00 | 1.00 / 1.00 | 1.00 / 1.00 |
| Kotlin calls Java | `compiler` | `unresolved` with `kotlin.unresolved-reference.no-classpath` or `kotlin.ambiguous-reference` | `kotlin_calls_java` 16 | none / 0.00 | 1.00 / 0.94 | 1.00 / 1.00 |
| Java calls Kotlin | `compiler`, file facades included | A `@JvmMultifileClass` facade has no target | `java_calls_kotlin` 15 | none / 0.00 | 1.00 / 1.00 | 1.00 / 1.00 |
| Version-catalog alias to module | `build-model` from catalogs and scripts; `convention` through convention plugins | `gradle.static.catalog-alias-unresolved`; computed values are `not-analysed` | `catalog_alias_to_module` 82 | 1.00 / 1.00 | 1.00 / 1.00 | 1.00 / 1.00 |
| Convention plugin to module | `convention`: precompiled script plugins and plugin ids of included builds | A plugin that is not read is `not-analysed` | `convention_plugin_to_module` 12 | 1.00 / 1.00 | 1.00 / 1.00 | 1.00 / 1.00 |
| Manifest component to class | `textual`: the class name written in the manifest, resolved against the module namespace | `jvm.class.not-found`; several candidates give a probable edge that lists them | `manifest_component_to_class` 9 | 1.00 / 1.00 | 1.00 / 1.00 | 1.00 / 1.00 |
| Layout or navigation graph to class | `textual`, resolved as above | `jvm.class.not-found` | `layout_class_reference` 3 | 1.00 / 1.00 | 1.00 / 1.00 | 1.00 / 1.00 |
| Resource to consumer | `convention` for the hop from a resource to its `R` field, then `compiler` for the use; `textual` in XML and the manifest | `android.resource.not-found` for resources of libraries | `resource_to_consumer` 76 | 1.00 / 0.62 | 1.00 / 1.00 | 1.00 / 1.00 |
| Layout to view binding consumer | `convention`: the binding class follows the layout name and its ids | A layout that gets no binding class, with a diagnostic | `viewbinding_layout_to_consumer` 5 | none / 0.00 | 1.00 / 1.00 | 1.00 / 1.00 |
| `BuildConfig` field to consumer | `convention`: standard fields and `buildConfigField` entries | A field that cannot be read; no class unless `buildFeatures.buildConfig` is `true` | `buildconfig_field_to_consumer` 6 | none / 0.00 | 1.00 / 1.00 | 1.00 / 1.00 |
| AIDL interface to implementer | `convention`: the `Stub` and `Default` classes of the interface | Declarations the parser cannot read, with line and column | `aidl_interface_to_implementer` 2 | none / 0.00 | 1.00 / 1.00 | 1.00 / 1.00 |
| AIDL interface to client | `convention`: a class that names the interface or calls `asInterface` | As above | `aidl_interface_to_client` 2 | none / 0.00 | 1.00 / 1.00 | 1.00 / 1.00 |
| AIDL method to override | `convention`; probable where an object expression overrides without naming each method | As above | `aidl_method_to_override` 7 | none / 0.00 | 1.00 / 1.00 | 1.00 / 1.00 |
| AIDL parcelable to class | `textual`: the parcelable name against the module classes | `aidl.parcelable-class-not-found` | `aidl_parcelable_to_class` 1 | 1.00 / 1.00 | 1.00 / 1.00 | 1.00 / 1.00 |
| `external` or `native` declaration to C function | `convention`: the `Java_` naming rule of JNI, joined through a contract symbol | `jni.definition-not-found`; `jni.native-side-not-analysed`; prototype-only or overloaded matches are probable | `jni_external_to_native` 2 | 1.00 / 1.00 | 1.00 / 1.00 | 1.00 / 1.00 |
| Proto message or enum to consumer | `convention` for the generated type, then `compiler`; a file that only reads members typed by the message is a consumer by inference | Calls into types that no stub covers stay `unresolved` | `proto_message_to_consumer` 17 | none / 0.00 | 1.00 / 1.00 | 1.00 / 1.00 |
| SQLDelight query to calling declaration | `convention`: one function per labelled statement | `sqldelight.table-not-found`; `sqldelight.database-unknown` | `sq_query_to_consumer` 14 | none / 0.00 | 1.00 / 1.00 | 1.00 / 1.00 |
| SQLDelight table to consumer | `convention`: the row class of the table; a consumer may be found by inference | As above | `sq_table_to_consumer` 4 | none / 0.00 | 1.00 / 1.00 | 1.00 / 1.00 |
| Room entity, DAO and database | `compiler` for annotations, `generated-artifact` for the exported schema | `room.module-unknown` | `room_entity_dao_database` 4 | 1.00 / 0.25 | 1.00 / 1.00 | 1.00 / 1.00 |
| Room DAO or database to consumer | `compiler` | As above | `room_component_to_consumer` 3 | none / 0.00 | 1.00 / 1.00 | 1.00 / 1.00 |
| Compose resource to consumer | `convention`: the `Res` accessors of the resources plugin; the package is heuristic unless the build states it | Derived package names are marked heuristic | `compose_resource_to_consumer` 7 | none / 0.00 | 1.00 / 1.00 | 1.00 / 1.00 |
| Flavor-specific implementation | `compiler` for the variant the front end analyses, `build-model` for the alternatives between variants | Nothing beyond the declared variant model | `flavor_specific_implementation` 4 | none / 0.00 | 1.00 / 1.00 | 1.00 / 1.00 |
| Test to subject | `compiler` where the test uses the subject, `convention` by test class name, heuristic when only the module is known | The basis of the choice is an attribute of the edge | `test_to_subject` 18 | 1.00 / 0.89 | 1.00 / 1.00 | 1.00 / 1.00 |

## Links between repositories

A class is listed when `WorkspaceLinkConformanceTest` (benchmark module) holds it to recall 1.00 and to the expected
evidence class on the fixture, and a service test exercises it in detail. The figures are links found out of gold
links in the conformance run. They are measured on a small constructed fixture at the default scan level, not on real
repositories, and no precision is claimed. The run also counts links of a class that no gold relationship names; the
only class with any is generated surfaces (7), and that count is reported and not gated.

| Link | Established by | Reported as unknown | Gold class | Evidence | Found | Service test |
|---|---|---|---|---|---|---|
| Maven coordinates: dependency to the module that publishes them | `build-model` | `workspace.coordinate-version-mismatch` (the link is kept and its references are heuristic); `workspace.coordinate-version-unknown`; `workspace.coordinate-ambiguous` (no link) | `coordinate_dependency_to_module` | `build-model` | 1/1 | `WorkspaceOfRepositoriesTest`, `RegisteredRepositoriesTest` |
| Maven coordinates: reference to the declaration across | `compiler`, after the dependency is resolved | Symbols that the module does not declare stay `unresolved` | `coordinate_reference_to_declaration` | `compiler` | 1/1 | `WorkspaceOfRepositoriesTest`, `RegisteredRepositoriesTest` |
| JAR file to the module that builds it | `build-model`: the file name against the archive name and version of a module; `content` when a built JAR matches the sources | `artifact.ambiguous-producer` when several modules build that file name | `jvm_library_file_to_module` | `build-model` | 1/1 | `JvmLibraryLinksTest` |
| Reference into a JAR to the declaration | `compiler`, through the module linked above | As above | `jvm_library_reference_to_declaration` | `compiler` | 1/1 | `JvmLibraryLinksTest` |
| Native library load to the target that builds it | `convention`: the library name against CMake, Cargo and ndk-build targets | `artifact.producer-not-in-workspace`, `artifact.ambiguous-producer` | `native_library_load_to_target` | `convention` | 2/2 | `NativeLibraryLinksTest` |
| `external` declaration to the JNI function in another repository | `convention`: the `Java_` naming rule, in C, C++ and Rust | `jni.definition-not-found`; `jni.native-side-not-analysed` | `jni_external_to_native` | `convention` | 2/2 | `NativeLibraryLinksTest` |
| Generator input to the spec | `build-model`: OpenAPI Generator `inputSpec` and Wire `sourcePath` | `artifact.producer-not-in-workspace` | `spec_input_to_spec` | `build-model` | 3/3 | `GeneratorInputLinksTest` |
| Generated code to the spec | `convention`: the surfaces the generator makes follow its documented naming rules | As above | `generated_surface_to_spec` | `convention` | 4/4 | `GeneratorInputLinksTest` |
| OpenAPI operation to the Ktor route that implements it | `convention`: the method and path | `spec.ambiguous-implementation` | `spec_operation_to_implementation` | `convention` | 1/1 | `SpecLinksTest` |
| Gradle copy of a web bundle to the project that builds it | `build-model`: the `from(...)` path against the output directory of a Vite, webpack or Rollup build, or of a Kotlin/JS or Wasm module | `artifact.ambiguous-producer` | `bundle_consumer_to_project` | `build-model` | 1/1 | `WebBundleLinksTest` |
| Bundle file to the source it came from | `content`: the source map of a built bundle. Needs the build output, found on a second scan | No link before the bundle is built | `bundle_file_to_source` | `content` | 2/2 | `WebBundleLinksTest` |

The JAR file link is heuristic until a built JAR is on disk and exact when its classes match the sources of the
module; the gold link is the one that the sources alone give. `bundle_file_to_source` is scored on a second scan,
after the files of a build are written into a copy of the fixture, as a build would leave them.

## Closed-world checks

Closed-world checks cover every outgoing reference, call and inheritance edge of eight reference files.

- At `syntax` the edges that exist meet precision 1.00 and no edge of the files is found (recall 0.00), because
  nothing is resolved.
- At `resolved` the files that need no Android classpath meet precision 1.00 and recall 0.97.
- At `exact` all files do, with both at 1.00.
- Declarations (81) and the build model (275 facts) match gold at every level.
- No resolved edge to a workspace symbol names a symbol the graph lacks.

## Not supported or not analysed

These are reported as `unresolved` or `not-analysed` where they are met, or are not looked for at all.

- The native side of JNI beyond statically named `Java_` functions: no C or C++ parsing, no `RegisterNatives`, no
  names built by macros.
- Dependency-injection graphs (Hilt, Dagger, Koin and similar).
- Navigation routes. Navigation graphs in XML are read for the classes and resources they name; route strings of
  Compose navigation and generated Safe Args classes are not.
- SQL inside `@Query` beyond the tables it mentions: columns, joins and parameters are not linked.
- Wire compatibility of proto schemas (field numbers, reserved fields, evolution rules).
- Kotlin targets other than the JVM and Android: JS, Wasm and native source sets are analysed best effort, and their
  `expect`/`actual` pairs are linked by convention where the compiler binds none.
- Which service object a Binder client talks to: AIDL clients are linked to the interface, not to an instance.
- Links between repositories that need a build output the repository has not produced: the class list of a JAR, a
  source map of a bundle, a symbol table of a stripped binary, code generated without a spec. They appear after the
  build has run and the repository is scanned again; until then the consumer keeps the link the declarations give, or
  stays `unresolved`.
- Producers that cannot be told apart or are missing: a JAR name that several modules build, a library or bundle
  with several producers, and a library whose producer is not in the workspace link nothing and are reported with
  an `artifact.*` reason.
- Build systems and generators beyond the ones in the table: native libraries are read from CMake, Cargo and
  ndk-build only, bundles from Vite, webpack and Rollup builds and Kotlin/JS and Wasm modules only, and spec
  inputs from OpenAPI Generator and Wire only.
- Fresh generator output. Files below generated source roots are inventoried and marked generated but not analysed;
  generated surfaces come from the documented naming rules of each generator, so a generator that deviates from them
  is not followed.
- Precompiled convention plugins written in Groovy. Precompiled script plugins in the Kotlin DSL are read.
- Gaps inside supported classes: Wire services and boxed oneofs get no stub, SQLDelight views and `.sqm` migrations
  add no symbols, AIDL `enum`, `union` and nested types are skipped, data-binding `<layout>` roots get no binding
  class, and a Java call into a `@JvmMultifileClass` facade has no target.
- Any reference at level `syntax`. No edge has compiler evidence there and each source set carries the note
  `kotlin.references.not-analysed-at-syntax`.
- A compile classpath at level `resolved`. Library symbols stay `unresolved` with `no-classpath`; level `exact` reads the
  classpaths from Gradle.
