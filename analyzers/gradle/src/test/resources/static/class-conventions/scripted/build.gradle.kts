plugins {
    id("com.android.library")
}

android {
    namespace = "acme.scripted"

    buildTypes {
        create("staging")
    }

    productFlavors {
        listOf("free", "paid").forEach { create(it) }
    }
}
