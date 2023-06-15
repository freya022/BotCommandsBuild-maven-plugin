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

@Mojo(name = "generate-version-info", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
class GenerateVersionInfoMojo : AbstractMojo() {
    @Parameter(defaultValue = "\${project}", required = true, readonly = true)
    lateinit var project: MavenProject

    private val buildDirectoryPath
        get() = Path(project.build.directory)

    private val generatedSourcesPath: Path
        get() = buildDirectoryPath.resolve("generated-sources")

    @Throws(MojoFailureException::class)
    override fun execute() {
        try {
            if (ManagementFactory.getRuntimeMXBean().inputArguments.any { it.startsWith("-agentlib:jdwp") }) {
                println("Type for debug")
                readln()
            }

            detectJitpack()

            val sourceFile = findSourceFile()
            log.info("Reading BCInfo template from ${sourceFile.absolute()}")

            //Get info to be inserted
            val (versionMajor, versionMinor, versionRevision, versionClassifier) = getVersionValues()
            val jdaDependency = getJDADependency() ?: throw IllegalStateException("Unable to find JDA dependency")
            val commitHash = getCommitHash(project)
            val branchName = getCommitBranch()

            val properties = listOf(
                BCInfoProperty("version-major", "Major version", versionMajor),
                BCInfoProperty("version-minor", "Minor version", versionMinor),
                BCInfoProperty("version-revision", "Version revision", versionRevision),
                BCInfoProperty("version-classifier", "Version classifier", versionClassifier),
                BCInfoProperty("commit-hash", "Commit hash", commitHash?.take(10).toString()),
                BCInfoProperty("branch-name", "Branch name", branchName.toString()),
                BCInfoProperty("build-jda-version", "JDA version used to build", jdaDependency.version),
                BCInfoProperty("build-time", "Build timestamp", System.currentTimeMillis().toString())
            )

            //Replace templates
            val text = properties.fold(sourceFile.readText()) { str, (name, _, value) ->
                when (value) {
                    null -> str.replace(""""%%$name%%"""", "null")
                    else -> str.replace("%%$name%%", value).also { newStr ->
                        if (newStr == str)
                            log.warn("Could not replace any occurrence of property named '$name' with value '$value'")
                    }
                }
            }.replace("\$BCInfo", "BCInfo")

            //Put files in target/generated-sources
            val newSourceFile = generatedSourcesPath.resolveInfoFile()

            //Write to new file, create parent structure
            newSourceFile.parent.createDirectories()
            newSourceFile.writeText(
                text,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            )

            log.info("Wrote BCInfo to ${newSourceFile.absolute()}")
            properties.forEach { (_, propertyHumanName, value) -> log.info("\t$propertyHumanName: $value") }
        } catch (e: Exception) {
            throw MojoFailureException("Failed to preprocess BotCommands sources", e)
        }
    }

    private fun detectJitpack() {
        if (System.getenv("JITPACK") == null) return

        log.info("Detected Jitpack build")
        listOf("JAVA_HOME", "GIT_COMMIT", "GIT_BRANCH", "GIT_DESCRIBE").forEach {
            log.info("$it: ${System.getenv(it)}")
        }
    }

    private fun getVersionValues(): Version {
        return Version.parseOrNull(project.version) ?: throw MojoFailureException(
            this,
            "Failed to parse project version",
            "Failed to parse project version: '${project.version}'"
        )
    }

    private fun getJDADependency() = project
        .dependencies
        .find { it.artifactId == "JDA" }

    private fun getCommitBranch(): String? {
        try {
            //Jitpack builds are detached from a branch, this will return the HEAD hash,
            // which can still be used on GitHub to get the state of the repository at that point
            val jitpackBranch = System.getenv("GIT_BRANCH")
            if (jitpackBranch != null) return jitpackBranch

            return ProcessBuilder()
                .directory(project.basedir) //Working directory differs when maven build server is used
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .command("git", "rev-parse", "--abbrev-ref", "HEAD")
                .also { log.debug("Running process: ${it.command()}") }
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
        .asSequence()
        .map { Path(it).resolveInfoFile().resolveSibling("\$BCInfo.java") }
        .find { it.exists() }
        ?: throw IOException("Cannot find BotCommands.properties in ${project.compileSourceRoots.joinToString()}")

    private fun Path.resolveInfoFile() = resolve(Path("com", "freya02", "botcommands", "api", "BCInfo.java"))

    private data class BCInfoProperty(val propertyName: String, val propertyHumanName: String, val propertyValue: String?)
}