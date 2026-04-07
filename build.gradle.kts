@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    `maven-publish`
}

group = "org.cikit"
version = "0.0.1"

repositories {
    mavenCentral()
    maven("https://maven.tryformation.com/releases") {
        content {
            includeGroup("com.jillesvangurp")
        }
    }
    mavenLocal {
        content {
            includeGroup("org.cikit")
        }
    }
}

kotlin {
    jvmToolchain(25)

    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
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
        val serializationVersion = "1.10.0"
        val coroutinesVersion = "1.10.2"
        val ktorVersion = "3.4.0"

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
