import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    kotlin("jvm")
    application
}

group = "io.sentry.appium.tests"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.commons:commons-configuration2:_")
    implementation("commons-beanutils:commons-beanutils:_")
    implementation(Ktor.client.core)
    implementation(Ktor.client.cio)
    implementation(Ktor.client.auth)
    implementation("org.kohsuke:github-api:_")

    testImplementation(kotlin("test"))
    testImplementation(platform(Testing.junit.bom))
    testImplementation(Testing.junit.jupiter)
    testImplementation("io.appium:java-client:_")
    testImplementation(Testing.kotest.assertions.core)
    testImplementation("com.google.guava:guava:_")
    testImplementation("com.jayway.jsonpath:json-path:_")
    testImplementation("org.slf4j:slf4j-jdk14:_")
    testImplementation("com.sksamuel.hoplite:hoplite-core:_")
    testImplementation("com.sksamuel.hoplite:hoplite-hocon:_")
    testImplementation("com.sksamuel.hoplite:hoplite-yaml:_")
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

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

application {
    mainClass.set("ResultProcessorKt")
}
