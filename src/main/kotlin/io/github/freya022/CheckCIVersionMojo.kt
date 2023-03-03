package io.github.freya022

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject

@Mojo(name = "check-ci-version", defaultPhase = LifecyclePhase.VALIDATE)
class CheckCIVersionMojo : AbstractMojo() {
    @Parameter(defaultValue = "\${project}", required = true, readonly = true)
    lateinit var project: MavenProject

    // This gets the tag of the current commit, which should have been created with the GitHub release
    private val currentGitTag by lazy {
        ProcessBuilder()
                .directory(project.basedir) //Working directory differs when maven build server is used
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .command("git", "describe", "--tags", "--abbrev=0", "--exact-match")
                .also { log.debug("Running process: ${it.command()}") }
                .start()
                .also { if (it.waitFor() != 0) return@lazy "" }
                .inputStream.bufferedReader().use { it.readLine() }
    }

    private val isCurrentTagSameAsVersion: Boolean
        get() = currentGitTag == "v${project.version}"

    @Throws(MojoFailureException::class)
    override fun execute() {
        //Check if the release profile is activated
        // We don't want to remove the _DEV suffix if the CI is checking javadocs, for example
        val isRelease = "release" in project.activeProfiles.map { it.id }
        if (isRelease && isCI) {
            if ("_DEV" in project.version)
                throw MojoFailureException("CI builds should not have the _DEV suffix, the CI should run the set-ci-version goal, and should have been ran in a separate maven instance")

            //Safety check to prevent releasing with a different version than the pom.xml one
            if (!isCurrentTagSameAsVersion) {
                when {
                    currentGitTag.isBlank() -> throw MojoFailureException("Tried to release 'v${project.version}' on an untagged commit")
                    else -> throw MojoFailureException("Tried to release 'v${project.version}' on an invalid tag '$currentGitTag'")
                }
            }
        }
    }
}