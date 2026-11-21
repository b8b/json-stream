@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import java.io.File
import java.net.URI
import java.util.zip.ZipInputStream

plugins {
    kotlin("multiplatform")
    `maven-publish`
}

group = "org.cikit"
version = providers.gradleProperty("projectVersion").get()

repositories {
    mavenCentral()
}

val repoRoot = layout.projectDirectory.asFile

val installGitHooks by tasks.registering {
    group = "build setup"
    description = "Points git at the versioned hooks in scripts/git-hooks"
    doLast {
        val rc = ProcessBuilder(
            "git", "config", "core.hooksPath", "scripts/git-hooks"
        ).directory(repoRoot).inheritIO().start().waitFor()
        if (rc != 0) {
            error("git config failed with exit code $rc")
        }
    }
}

val jsonTestSuiteCommit = "1ef36fa01286573e846ac449e8683f8833c5b26a"

val jsonTestSuiteDir = layout.buildDirectory.dir("jsonTestSuite/test_parsing")

val fetchJsonTestSuite = tasks.register("fetchJsonTestSuite") {
    group = "verification"
    description = "Downloads the JSONTestSuite parsing cases into " +
        "build/jsonTestSuite"
    outputs.dir(jsonTestSuiteDir)
    doLast {
        val target = jsonTestSuiteDir.get().asFile
        target.deleteRecursively()
        val archive = File(target.parentFile, "jsonTestSuite.zip")
        val url = "https://github.com/nst/JSONTestSuite/archive/" +
            "$jsonTestSuiteCommit.zip"
        URI(url).toURL().openStream().use { input ->
            archive.outputStream().use { output -> input.copyTo(output) }
        }
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.substringAfter('/', "")
                if (entry.isDirectory || !name.startsWith("test_parsing/")) {
                    continue
                }
                val file = File(target, name.removePrefix("test_parsing/"))
                file.parentFile.mkdirs()
                file.outputStream().use { output -> zip.copyTo(output) }
            }
        }
        archive.delete()
    }
}

kotlin {
    jvmToolchain(11)

    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
            systemProperty(
                "jsonTestSuite.report",
                project.layout.buildDirectory
                    .file("reports/jsonTestSuite.txt")
                    .get().asFile.absolutePath,
            )
            val local = project.layout.projectDirectory
                .dir("JSONTestSuite/test_parsing").asFile
            if (local.isDirectory) {
                systemProperty("jsonTestSuite.dir", local.absolutePath)
            } else {
                dependsOn(fetchJsonTestSuite)
                systemProperty(
                    "jsonTestSuite.dir",
                    jsonTestSuiteDir.get().asFile.absolutePath,
                )
            }
        }
    }

    js {
        browser {
            testTask {
                useKarma {
                    useFirefox()
                }
            }
        }
        nodejs()
        binaries.library()
    }

    wasmJs {
        browser {
            testTask {
                useKarma {
                    useFirefox()
                }
            }
        }
        nodejs()
        binaries.library()
    }

    sourceSets {
        val serializationVersion = "1.13.0"
        val coroutinesVersion = "1.11.0"
        val ktorVersion = "3.6.1"

        commonMain {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:${serializationVersion}")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:${coroutinesVersion}")
                api("io.ktor:ktor-io:${ktorVersion}")
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

publishing {
    publications {
        withType(MavenPublication::class.java).configureEach {
            pom {
                name = "json-stream"
                description = "A streaming JSON parser for Kotlin Multiplatform"
                url = "https://github.com/b8b/json-stream"
                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
                developers {
                    developer {
                        id = "b8b@cikit.org"
                        name = "b8b@cikit.org"
                        email = "b8b@cikit.org"
                    }
                }
                scm {
                    connection =
                        "scm:git:https://github.com/b8b/json-stream.git"
                    developerConnection =
                        "scm:git:ssh://github.com/b8b/json-stream.git"
                    url = "https://github.com/b8b/json-stream.git"
                }
            }
        }
    }
}
