package io.github.freya022

import org.apache.maven.model.Resource
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

@Mojo(name = "BotCommandsBuild", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
class EnforcerMojo : AbstractMojo() {
    @Parameter(defaultValue = "\${project}", required = true, readonly = true)
    lateinit var project: MavenProject

    @Throws(MojoFailureException::class)
    override fun execute() {
        try {
            if (ManagementFactory.getRuntimeMXBean().inputArguments.any { it.startsWith("-agentlib:jdwp") }) {
                println("Type for debug")
                readln()
            }

            val propertiesFile = findPropertiesFile()

            val versionSplit = project.version.substringBefore("-SNAPSHOT").split(".")
            val jdaDependency = getJDADependency() ?: throw IllegalStateException("Unable to find JDA dependency")

            val commitHash = getCommitHash()
            val branchName = getCommitBranch()

            val text = propertiesFile.readText()
                .replace("%%version-major%%", versionSplit[0])
                .replace("%%version-minor%%", versionSplit[1])
                .replace("%%version-revision%%", versionSplit[2])
                .replace("%%commit-hash%%", commitHash)
                .replace("%%branch-name%%", branchName)
                .replace("%%build-jda-version%%", jdaDependency.version)
                .replace("%%build-time%%", System.currentTimeMillis().toString())

            val generatedDir = project.basedir.toPath().resolve("src").resolve("generated")
            val newPropertiesFile = generatedDir.resolve("BotCommands.properties")
            project.addResource(generatedDir.toResource())

            generatedDir.createDirectories()
            newPropertiesFile.writeText(text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        } catch (e: Exception) {
            throw MojoFailureException("Failed to preprocess BotCommands sources", e)
        }
    }

    private fun getJDADependency() = project
        .dependencies
        .find { it.artifactId == "JDA" }

    private fun getCommitHash() = let {
        val jitpackCommit = System.getenv("GIT_COMMIT")
        if (jitpackCommit != null) return@let jitpackCommit

        return@let ProcessBuilder()
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .command("git", "rev-parse", "--verify", "HEAD")
            .start()
            .also {
                if (it.waitFor() != 0) {
                    throw IOException("Unable to get commit hash via Git")
                }
            }
            .inputStream.bufferedReader().use { it.readLine() }
    }

    private fun getCommitBranch() = let {
        val jitpackBranch = System.getenv("GIT_BRANCH")
        if (jitpackBranch != null) return@let jitpackBranch

        return@let ProcessBuilder()
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .command("git", "rev-parse", "--abbrev-ref", "HEAD")
            .start()
            .also {
                if (it.waitFor() != 0) {
                    throw IOException("Unable to get branch name via Git")
                }
            }
            .inputStream.bufferedReader().use { it.readLine() }
    }

    private fun findPropertiesFile() = project
        .resources
        .find {
            Path(it.directory).resolve("BotCommands.properties").exists().also { exists ->
                if (exists) {
                    it.addExclude("BotCommands.properties") //Prevent compiler from adding the template resource
                }
            }
        }
        ?.let {
            Path(it.directory).resolve("BotCommands.properties")
        } ?: throw IOException("Cannot find BotCommands.properties in ${project.resources.joinToString { it.directory }}")

    private fun Path.toResource() = Resource().also { it.directory = toString() }
}