package io.github.freya022

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.IOException
import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.*

@Mojo(name = "BotCommandsBuild", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
class BCBuildMojo : AbstractMojo() {
    @Parameter(defaultValue = "\${project}", required = true, readonly = true)
    lateinit var project: MavenProject

    @Throws(MojoFailureException::class)
    override fun execute() {
        try {
            if (ManagementFactory.getRuntimeMXBean().inputArguments.any { it.startsWith("-agentlib:jdwp") }) {
                println("Type for debug")
                readln()
            }

            val sourceFile = findSourceFile()
            log.info("Reading BCInfo template from ${sourceFile.absolute()}")

            //Get info to be inserted
            val (versionMajor, versionMinor, versionRevision) = project.version.substringBefore("-SNAPSHOT").split(".")
            val jdaDependency = getJDADependency() ?: throw IllegalStateException("Unable to find JDA dependency")
            val commitHash = getCommitHash()
            val branchName = getCommitBranch()

            //Replace templates
            val text = sourceFile.readText()
                .replace("%%version-major%%", versionMajor)
                .replace("%%version-minor%%", versionMinor)
                .replace("%%version-revision%%", versionRevision)
                .replace("%%commit-hash%%", commitHash?.take(10).toString())
                .replace("%%branch-name%%", branchName.toString())
                .replace("%%build-jda-version%%", jdaDependency.version)
                .replace("%%build-time%%", System.currentTimeMillis().toString())

            //Put files in target/generated-sources
            val generatedDir = Path(project.build.directory).resolve("generated-sources")
            val newSourceFile = generatedDir.resolveInfoFile()

            //Write to new file, create parent structure
            newSourceFile.parent.createDirectories()
            newSourceFile.writeText(text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

            log.info("Wrote BCInfo to ${newSourceFile.absolute()}")
        } catch (e: Exception) {
            throw MojoFailureException("Failed to preprocess BotCommands sources", e)
        }
    }

    private fun getJDADependency() = project
        .dependencies
        .find { it.artifactId == "JDA" }

    private fun getCommitHash(): String? {
        try {
            val jitpackCommit = System.getenv("GIT_COMMIT")
            if (jitpackCommit != null) return jitpackCommit

            return ProcessBuilder()
                .directory(project.basedir) //Working directory differs when maven build server is used
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .command("git", "rev-parse", "--verify", "HEAD")
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

    private fun getCommitBranch(): String? {
        try {
            val jitpackBranch = System.getenv("GIT_BRANCH")
            if (jitpackBranch != null) return jitpackBranch

            return ProcessBuilder()
                .directory(project.basedir) //Working directory differs when maven build server is used
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .command("git", "rev-parse", "--abbrev-ref", "HEAD")
                .start()
                .also {
                    if (it.waitFor() != 0) {
                        throw IOException("Unable to get branch name via Git")
                    }
                }
                .inputStream.bufferedReader().use { it.readLine() }
        } catch (e: Exception) {
            log.error("Unable to get commit branch", e)
            return null
        }
    }

    private fun findSourceFile() = project
        .compileSourceRoots
        .find { Path(it).resolveInfoFile().resolveSibling("\$BCInfo.java").exists() }
        ?.let { Path(it).resolveInfoFile().resolveSibling("\$BCInfo.java") }
        ?: throw IOException("Cannot find BotCommands.properties in ${project.resources.joinToString { it.directory }}")

    private fun Path.resolveInfoFile() = resolve(Path("com", "freya02", "botcommands", "api", "BCInfo.java"))
}