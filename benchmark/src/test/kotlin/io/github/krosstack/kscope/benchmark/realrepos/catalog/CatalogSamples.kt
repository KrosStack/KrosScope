package io.github.krosstack.kscope.benchmark.realrepos.catalog

internal object CatalogSamples {
    fun catalog(shas: Map<String, String>): String = """{
        "schemaVersion": 2, "repository": "app", "baseTag": "x",
        "tasks": [
          {"id": "both", "level": 4, "named": false, "prompt": "change both", "kind": "k", "repositories": [
            ${state("api", shas, "api", "\"README.md\"")}, ${state("app", shas, "app", "\"README.md\"")}]},
          {"id": "one", "level": 1, "named": true, "prompt": "change one", "repositories": [
            ${state("api", shas, "api", "")}, ${state("app", shas, "app", "\"README.md\"")}]}
        ]}"""

    fun freeze(shas: Map<String, String>, hash: String): String = """{
        "id": "sample-v1", "catalog": {"file": "catalog.json", "sha256": "$hash"},
        "repositories": [
          {"id": "api", "baseCommit": "${shas.getValue("api1")}"},
          {"id": "app", "baseCommit": "${shas.getValue("app1")}"}]}"""

    private fun state(name: String, shas: Map<String, String>, prefix: String, gold: String): String =
        """{"name": "$name", "baseCommit": "${shas.getValue(prefix + "1")}",
            "scenarioCommit": "${shas.getValue(prefix + "1")}", "solutionCommit": "${shas.getValue(prefix + "2")}",
            "gold": [$gold], "extra": 1}"""
}
