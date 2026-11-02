plugins {
    id("com.android.library")
    id("app.cash.sqldelight")
}

android {
    namespace = "sample.db"
    compileSdk = 36
}

sqldelight {
    databases {
        create("TaskDatabase") {
            packageName.set("sample.db")
        }
    }
}
