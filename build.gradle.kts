// Root build file. All module configuration lives in the module build files.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlinx.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
