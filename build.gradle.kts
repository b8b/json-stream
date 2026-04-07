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
                    connection = "scm:git:https://github.com/b8b/json-stream.git"
                    developerConnection = "scm:git:ssh://github.com/b8b/json-stream.git"
                    url = "https://github.com/b8b/json-stream.git"
                }
            }
        }
    }
}
