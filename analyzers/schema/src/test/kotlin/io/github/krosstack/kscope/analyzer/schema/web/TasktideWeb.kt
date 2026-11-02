package io.github.krosstack.kscope.analyzer.schema.web

import io.github.krosstack.kscope.analyzer.schema.facts
import io.github.krosstack.kscope.analyzer.schema.symbols
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Symbol

internal const val TASKTIDE_MANIFEST = """{
  "name": "@tasktide/web",
  "version": "0.4.0",
  "private": true,
  "scripts": {
    "build": "vite build"
  },
  "devDependencies": {
    "vite": "5.4.10"
  }
}
"""

internal const val TASKTIDE_VITE_CONFIG = """import { defineConfig } from "vite";

export default defineConfig({
  build: {
    outDir: "dist",
    sourcemap: true,
  },
});
"""

internal const val TASKTIDE_BUNDLE = """async function a(){return(await fetch("/tasks")).json()}
//# sourceMappingURL=index.js.map
"""

internal const val TASKTIDE_MAP =
    """{"version":3,"file":"index.js","sources":["../../src/tasks.ts","../../src/main.ts"],"names":[],"mappings":""}"""

/** The files of `fixtures/workspace-of-repositories/tasktide-web` and of the bundle that a build of it leaves. */
internal val tasktideWeb: Map<String, String> = mapOf(
    "package.json" to TASKTIDE_MANIFEST,
    "vite.config.ts" to TASKTIDE_VITE_CONFIG,
    "src/main.ts" to "import { loadTasks } from \"./tasks\";\nloadTasks();\n",
    "src/tasks.ts" to "export async function loadTasks() {\n  return [];\n}\n",
    "dist/index.html" to "<!doctype html>\n<html></html>\n",
    "dist/assets/index.js" to TASKTIDE_BUNDLE,
    "dist/assets/index.js.map" to TASKTIDE_MAP,
)

internal fun List<FactBatch>.symbolsOfKind(kind: String): List<Symbol> = facts().symbols().filter { it.kind.id == kind }
