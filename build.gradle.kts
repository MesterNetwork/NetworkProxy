plugins {
    kotlin("jvm") version "2.0.20-Beta2" apply false
    kotlin("kapt") version "2.0.20-Beta2" apply false
}

allprojects {
    repositories {
        mavenCentral()
    }
}

val targetJavaVersion = 21
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(targetJavaVersion)
    }
}
