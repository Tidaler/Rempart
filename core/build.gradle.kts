import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Module :core : logique métier pure Kotlin, sans dépendance Android.
// Conçu pour devenir plus tard le `commonMain` d'un module Kotlin Multiplatform
// partagé avec l'app iPhone (voir docs/PLAN_IPHONE.md).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

tasks.test {
    maxHeapSize = "1g"
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
