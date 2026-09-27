plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// 依赖锁定：生成并校验 lockfile（供应链安全）
dependencyLocking {
    lockAllConfigurations()
}
