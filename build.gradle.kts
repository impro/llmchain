plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.dokka) apply false
}

repositories {
    mavenCentral()
    google()
}
