package io.github.freya022

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.project.MavenProject
import java.io.IOException

// https://github.com/DV8FromTheWorld/JDA/blob/2328104e0117957ab498a0f3fcf2c281269b7bd9/build.gradle.kts#L44-L49
val isCI = (System.getProperty("BUILD_NUMBER") != null // Jenkins
        || System.getenv("BUILD_NUMBER") != null
        || System.getProperty("GIT_COMMIT") != null // Jitpack
        || System.getenv("GIT_COMMIT") != null
        || System.getProperty("GITHUB_ACTIONS") != null // GitHub Actions
        || System.getenv("GITHUB_ACTIONS") != null)

fun AbstractMojo.getCommitHash(project: MavenProject): String? {
    try {
        val jitpackCommit = System.getenv("GIT_COMMIT")
        if (jitpackCommit != null) return jitpackCommit

        return ProcessBuilder()
            .directory(project.basedir) //Working directory differs when maven build server is used
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .command("git", "rev-parse", "--verify", "HEAD")
            .also { log.debug("Running process: ${it.command()}") }
            .start()
            .also {
                if (it.waitFor() != 0) {
                    throw IOException("Unable to get commit hash via Git")
                }
            }
            .inputStream.bufferedReader().use { it.readLine() }
    } catch (e: Exception) {
        log.error("Unable to get commit hash", e)
        return null
    }
}