plugins {
    kotlin("multiplatform")
    id("app.cash.sqldelight")
}

kotlin {
    jvm()
}

sqldelight {
    databases {
        create("MediaDatabase") {
            packageName.set("sample.sqldshared.db")
        }
    }
}
