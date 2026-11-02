plugins {
    kotlin("multiplatform")
    id("app.cash.sqldelight")
}

kotlin {
    jvm()
}

sqldelight {
    databases {
        create("NotesDatabase") {
            packageName.set("sample.sqld.db")
        }
    }
}
