import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.10"
}

group = "io.sentry.appium.tests"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val ktorVersion = "2.0.3"
val hopliteVersion = "2.5.2"
dependencies {
    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:5.9.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.appium:java-client:8.1.1")
    testImplementation("io.kotest:kotest-assertions-core:5.4.1")
    testImplementation("com.google.guava:guava:31.1-jre")
    testImplementation("com.jayway.jsonpath:json-path:2.7.0")
    testImplementation("io.ktor:ktor-client-core:$ktorVersion")
    testImplementation("io.ktor:ktor-client-cio:$ktorVersion")
    testImplementation("io.ktor:ktor-client-auth:$ktorVersion")
    testImplementation("org.slf4j:slf4j-jdk14:1.7.30")
    testImplementation("com.sksamuel.hoplite:hoplite-core:$hopliteVersion")
    testImplementation("com.sksamuel.hoplite:hoplite-hocon:$hopliteVersion")
    testImplementation("com.sksamuel.hoplite:hoplite-yaml:$hopliteVersion")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_11.toString()
}
