# Retrieval artifact vocabulary

`ArtifactVocabulary.hints(text)` supplies explicit symbol-category hints for lexical
retrieval. Its authority is the analyzer capability taxonomy and the
`SymbolCategory` enum. The vocabulary was defined from those capabilities,
independently of benchmark task statements, reference answers, expected rankings,
and benchmark results.

The capability taxonomy includes Android manifests and components, Android and
Compose resources, AIDL contracts, JNI contracts, protobuf schemas, SQLDelight
schemas, Room schemas, and Gradle build declarations. A hint expresses the named
artifact kind; it does not infer an artifact from the purpose of a requested
change.

## Terms

The table lists every accepted vocabulary entry. Plural forms are explicit entries.

| Authority | Accepted terms or phrases | Symbol category |
| --- | --- | --- |
| Android and Compose resources; category name | `resource`, `resources` | `RESOURCE` |
| Android string resources | `android string`, `android strings` | `RESOURCE` |
| Android color resources | `android color`, `android colors` | `RESOURCE` |
| Android layout resources | `android layout`, `android layouts` | `RESOURCE` |
| Android menu resources | `android menu`, `android menus` | `RESOURCE` |
| Android navigation resources | `android navigation` | `RESOURCE` |
| Android components; category name | `component`, `components` | `COMPONENT` |
| Android manifests | `manifest`, `manifests` | `COMPONENT` |
| Android manifest permissions | `permission`, `permissions` | `COMPONENT` |
| Android activities | `android activity`, `android activities` | `COMPONENT` |
| Android services | `android service`, `android services` | `COMPONENT` |
| Android receivers | `android receiver`, `android receivers` | `COMPONENT` |
| Android providers | `android provider`, `android providers` | `COMPONENT` |
| AIDL and JNI contracts; category name | `contract`, `contracts` | `CONTRACT` |
| AIDL interfaces and parcelables | `aidl` | `CONTRACT` |
| JNI native contracts | `jni` | `CONTRACT` |
| Schema category name | `schema`, `schemas` | `SCHEMA` |
| Protobuf messages, enums, and services | `protobuf`, `proto`, `protocol buffer`, `protocol buffers` | `SCHEMA` |
| SQLDelight tables, queries, and schemas | `SQLDelight`, `sqldelight` | `SCHEMA` |
| Room entities, DAOs, and databases | `room` | `SCHEMA` |
| Gradle build files and settings; category name | `build`, `builds`, `gradle` | `BUILD` |
| Gradle version catalogs | `catalog`, `catalogs` | `BUILD` |
| Gradle dependencies | `dependency`, `dependencies` | `BUILD` |
| Gradle plugins | `plugin`, `plugins` | `BUILD` |
| Gradle source sets | `source set`, `source sets`, `sourceset`, `sourcesets` | `BUILD` |
| Gradle variants | `variant`, `variants` | `BUILD` |
| Gradle product flavors | `flavor`, `flavors` | `BUILD` |
| Type category name | `type`, `types` | `TYPE` |
| Callable category name | `callable`, `callables` | `CALLABLE` |
| Variable category name | `variable`, `variables` | `VARIABLE` |
| Package category name | `package`, `packages` | `PACKAGE` |

## Matching rules

1. Normalize the input and each vocabulary entry with `CodeTokenizer.parts`.
   This lowercases text and splits code identifier boundaries using the same
   tokenizer as code retrieval. It preserves part order and repeated parts.
2. Match each entry as an exact, contiguous sequence of normalized parts. A
   one-part term must equal a complete part. A phrase must match all its parts
   in order with no intervening part. Punctuation and whitespace use the
   tokenizer's boundaries; camel-case and underscore-separated identifiers can
   produce the same sequence as a phrase.
3. The tokenizer retains parts of at least two characters. All vocabulary terms
   satisfy that rule. `SQLDelight` produces the sequence `sql`, `delight`;
   `sqldelight` produces the single part `sqldelight`. Both spellings are explicit
   entries. `sourceSet` produces the same parts as `source set`.
4. Do not use substring matching, stemming, fuzzy matching, or implicit synonym
   expansion. For example, the part `manifestation` does not equal `manifest`,
   and `pluginless` does not equal `plugin`.
5. Union the categories of all matches, remove duplicates, and sort by the enum
   name. Empty input and input without a matching entry return an empty list.

Bare names shared with general programming or interface vocabulary, such as
`string`, `color`, `layout`, `menu`, `navigation`, `activity`, `service`,
`receiver`, `provider`, `interface`, `parcelable`, `table`, `query`, `entity`,
`dao`, `database`, and `settings`, have no separate entry. Named technologies,
explicit resource/category terms, and the qualified phrases in the table provide
their artifact context. `compose` and `native` alone have no entry.

The vocabulary has no artifact-category prior, task-domain synonyms, workflow
stop words, or benchmark-specific exception list. Additions require a literal
artifact-kind or category-name justification from the capability taxonomy and a
matching update to this table.
