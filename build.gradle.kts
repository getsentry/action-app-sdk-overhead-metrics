import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    kotlin("jvm") version "1.7.10"
    application
}

group = "io.sentry.appium.tests"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val ktorVersion = "2.1.0"
val hopliteVersion = "2.6.2"
dependencies {
    implementation("org.apache.commons:commons-configuration2:2.8.0")
    implementation("commons-beanutils:commons-beanutils:1.9.4")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-auth:$ktorVersion")

    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:5.9.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.appium:java-client:8.1.1")
    testImplementation("io.kotest:kotest-assertions-core:5.4.2")
    testImplementation("com.google.guava:guava:31.1-jre")
    testImplementation("com.jayway.jsonpath:json-path:2.7.0")
    testImplementation("org.slf4j:slf4j-jdk14:2.0.0")
    testImplementation("com.sksamuel.hoplite:hoplite-core:$hopliteVersion")
    testImplementation("com.sksamuel.hoplite:hoplite-hocon:$hopliteVersion")
    testImplementation("com.sksamuel.hoplite:hoplite-yaml:$hopliteVersion")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("started", "passed", "skipped", "failed", "standardOut", "standardError")

        showExceptions = true
        exceptionFormat = TestExceptionFormat.FULL
        showCauses = true
        showStackTraces = true
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_11.toString()
}

application {
    mainClass.set("ResultProcessorKt")
}
