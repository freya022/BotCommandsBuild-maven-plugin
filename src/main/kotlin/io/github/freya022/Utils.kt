package io.github.freya022

// https://github.com/DV8FromTheWorld/JDA/blob/2328104e0117957ab498a0f3fcf2c281269b7bd9/build.gradle.kts#L44-L49
val isCI = (System.getProperty("BUILD_NUMBER") != null // Jenkins
        || System.getenv("BUILD_NUMBER") != null
        || System.getProperty("GIT_COMMIT") != null // Jitpack
        || System.getenv("GIT_COMMIT") != null
        || System.getProperty("GITHUB_ACTIONS") != null // GitHub Actions
        || System.getenv("GITHUB_ACTIONS") != null)