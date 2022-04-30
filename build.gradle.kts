plugins {
    kotlin("multiplatform") version "1.6.20"
}

group = "me.zrquan"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    macosX64 {
        compilations.getByName("main") {
            cinterops {
                val setsockopt by creating
            }
        }
        binaries {
            executable {
                entryPoint = "main"
            }
        }
    }
    linuxX64 {
        compilations.getByName("main") {
            cinterops {
                val setsockopt by creating
            }
        }
        binaries {
            executable {
                entryPoint = "main"
            }
        }
    }
    mingwX64 {
        compilations.getByName("main") {
            cinterops {
                val setsockopt by creating
            }
        }
        binaries {
            executable {
                entryPoint = "main"
            }
        }
    }
    sourceSets {
        val commonMain by getting
        val posixMain by creating {
            dependsOn(commonMain)
        }
        val macosX64Main by getting {
            dependsOn(posixMain)
        }
        val linuxX64Main by getting {
            dependsOn(posixMain)
        }
        val mingwX64Main by getting {
            dependsOn(posixMain)
        }
    }
}
