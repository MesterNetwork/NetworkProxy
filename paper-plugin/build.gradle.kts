plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("io.papermc.paperweight.userdev") version "1.7.4"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "info.mester.network.proxy"
version = "a1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation(kotlin("reflect"))
    // common
    implementation(project(":common"))
    // set up paper
    paperweight.paperDevBundle("1.21.3-R0.1-SNAPSHOT")
    // LuckPerms
    compileOnly("net.luckperms:api:5.4")
    // PlaceholderAPI
    compileOnly("me.clip:placeholderapi:2.11.6")
    // HikariCP and MySQL/J connector
    implementation("com.zaxxer:HikariCP:4.0.3")
    implementation("com.mysql:mysql-connector-j:9.1.0")
}

tasks {
    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }

    build {
        dependsOn("shadowJar")
    }

    test {
        useJUnitPlatform()
    }

    runServer {
        minecraftVersion("1.21.3")
    }

    runDevBundleServer {
        downloadPlugins {
            modrinth("Vebnzrzj", "cfNN7sys")
        }
    }
}

tasks.withType(xyz.jpenilla.runtask.task.AbstractRun::class) {
    javaLauncher =
        javaToolchains.launcherFor {
            vendor = JvmVendorSpec.JETBRAINS
            languageVersion = JavaLanguageVersion.of(21)
        }
    jvmArgs("-XX:+AllowEnhancedClassRedefinition")
}

sourceSets {
    main {
        java {
            srcDir("src/main/kotlin")
        }
    }
    test {
        java {
            srcDir("src/test/kotlin")
        }
    }
}
