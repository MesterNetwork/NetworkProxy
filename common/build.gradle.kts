group = "info.mester.network.common"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // gson
    compileOnly("com.google.code.gson:gson:2.11.0")

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.3")
    testImplementation("com.google.code.gson:gson:2.11.0")
}

tasks.test {
    useJUnitPlatform()
}
