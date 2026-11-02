# TaskTide, across repositories

Six small repositories of the TaskTide product that build things for each other. A test turns each directory into a
Git repository of its own, then scans them as one workspace
([ADR 0020](../../docs/decisions/0020-links-through-built-artifacts.md)).

| Repository | What it is |
|---|---|
| `tasktide-app` | The application: `:client` uses everything below, `:server` serves the API and the web bundle |
| `tasktide-kit` | A Kotlin library build: `:format` is published by Maven coordinates, `:text` is used as a JAR file |
| `tasktide-native-cpp` | A C++ JNI library built with CMake |
| `tasktide-native-rust` | A Rust JNI library built with Cargo |
| `tasktide-web` | A web project built with Vite |
| `tasktide-spec` | The OpenAPI and proto specs of the API |

What crosses a repository boundary:

| From | To | How |
|---|---|---|
| `tasktide-app/client` | `tasktide-kit/format` | the Maven coordinates `sample.tasktide:format:1.2.0` |
| `tasktide-app/client` | `tasktide-kit/text` | the file `libs/tasktide-text-1.0.0.jar`, which `:text` builds |
| `tasktide-app/client` | `tasktide-native-cpp`, `tasktide-native-rust` | `System.loadLibrary` and JNI functions |
| `tasktide-app/server` | `tasktide-web` | a copy of the Vite output `dist` into the server's resources |
| `tasktide-app/client`, `server` | `tasktide-spec` | OpenAPI Generator and Wire read the specs; `:server` implements `GET /tasks` |

The repositories hold sources only. Build outputs, such as the JAR in `tasktide-app/client/libs` or the web bundle in
`tasktide-web/dist`, are ignored by each repository and listed in
[`benchmark/workspace/outputs.json`](../../benchmark/workspace/outputs.json); a test writes them to stand in for a
build. The links that should be found are in
[`benchmark/workspace/gold/relationships.json`](../../benchmark/workspace/gold/relationships.json).
