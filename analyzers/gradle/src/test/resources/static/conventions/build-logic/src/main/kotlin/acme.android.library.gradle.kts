import com.android.build.api.dsl.LibraryExtension

plugins {
    id("com.android.library")
    id("acme.base")
}

extensions.configure<LibraryExtension> {
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }
}
