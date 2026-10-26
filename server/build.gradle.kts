import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

group = "dev.zeteny.ticketrail"
version = "1.0.0"

kotlin {
    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        binaries {
            executable {
                mainClass.set("dev.zeteny.ticketrail.ApplicationKt")
            }
        }
    }

    linuxX64 {
        binaries.executable {
            entryPoint = "dev.zeteny.ticketrail.main"
        }
    }

    linuxArm64 {
        binaries.executable {
            entryPoint = "dev.zeteny.ticketrail.main"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(libs.ktor.serverCore)
            implementation(libs.ktor.serverCio)
            implementation(libs.ktor.serverContentNegotiation)
            implementation(libs.ktor.serializationKotlinxJson)
            implementation(libs.ktor.serverStatusPages)
            implementation(libs.ktor.serverAuth)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.ktor)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.serverTestHost)
        }
        // Logback and CallLogging have no native artifacts
        jvmMain.dependencies {
            implementation(libs.logback)
            implementation(libs.ktor.serverCallLogging)
        }
    }
}
