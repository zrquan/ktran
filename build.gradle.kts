plugins {
    kotlin("multiplatform") version "1.6.10"
}

group = "me.zrquan"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    nativeTarget.apply {
        compilations["main"].cinterops.create("setsockopt") {
            defFile(project.file("src/nativeMain/cinterop/setsockopt.def"))
        }

        binaries {
            executable {
                entryPoint = "main"
                runTask?.args("--help")
            }
        }
    }
    sourceSets {
        val nativeMain by getting
    }
}
