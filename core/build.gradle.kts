import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    jvm()
    linuxX64()
    linuxArm64()
    
    android {
       namespace = "dev.zeteny.ticketrail.core"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }
    
    sourceSets {
        commonMain.dependencies {
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serializationJson)
            implementation(libs.okio)
            implementation(libs.kaml)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
            implementation(libs.okio.fakefilesystem)
        }
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
    add("kspLinuxX64", libs.room.compiler)
    add("kspLinuxArm64", libs.room.compiler)
}

// BundledSQLiteDriver only ships native libs for Android ABIs, so database tests can't run on the
// host JVM. They still compile for Android; jvmTest and linuxX64Test cover the behaviour.
tasks.withType<Test>().matching { it.name == "testAndroidHostTest" }.configureEach {
    filter.excludeTestsMatching("dev.zeteny.ticketrail.data.local.database.*")
    // Nothing else lives in androidHostTest yet; drop this once it has its own tests.
    filter.isFailOnNoMatchingTests = false
}
