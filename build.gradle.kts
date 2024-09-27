import org.gradle.kotlin.dsl.testImplementation

plugins {
    kotlin("jvm") version "2.0.0"
}

group = "org.cindustry"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(8)
}

tasks.register<Jar>("cind") {
    archiveClassifier.set("cind")

    manifest {
        attributes(
            "Main-Class" to "org.cindustry.MainKt",
            "Implementation-Title" to "Gradle",
            "Implementation-Version" to archiveVersion
        )
    }

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}