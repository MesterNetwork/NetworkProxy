plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jetbrains.kotlin.kapt")
    id("xyz.jpenilla.run-velocity") version "2.3.1"
}

group = "info.mester.network.proxy"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://oss.sonatype.org/content/groups/public/") {
        name = "sonatype"
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    // common
    implementation(project(":common"))
    // Velocity
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    kapt("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    // LuckPerms
    compileOnly("net.luckperms:api:5.4")
    // ConfigLib
    implementation("de.exlll:configlib-yaml:4.5.0")
    // HikariCP and MySQL/J connector
    implementation("com.zaxxer:HikariCP:4.0.3")
    implementation("com.mysql:mysql-connector-j:9.1.0")
    // Discord Webhooks
    implementation("club.minnced:discord-webhooks:0.8.4")
    // Redis
    implementation("io.lettuce:lettuce-core:6.5.0.RELEASE")
}

tasks.runVelocity {
    velocityVersion("3.4.0-SNAPSHOT")
    downloadPlugins {
        modrinth("Vebnzrzj", "vtXGoeps")
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.withType(xyz.jpenilla.runtask.task.AbstractRun::class) {
    javaLauncher =
        javaToolchains.launcherFor {
            vendor = JvmVendorSpec.JETBRAINS
            languageVersion = JavaLanguageVersion.of(21)
        }
    jvmArgs("-XX:+AllowEnhancedClassRedefinition")
}
