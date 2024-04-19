package io.github.freya022

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject

private val tagRegex = Regex("<tag>.*?</tag>")

@Mojo(name = "set-ci-version", requiresDirectInvocation = true)
class SetCIVersionMojo : AbstractMojo() {
    @Parameter(defaultValue = "\${project}", required = true, readonly = true)
    lateinit var project: MavenProject

    @Throws(MojoFailureException::class)
    override fun execute() {
        if (isCI) {
            val commitHash = getCommitHash(project)
            project.file.readText()
                // Remove _DEV
                .replaceFirst("_DEV</version>", "</version>")
                // Put current commit hash in project.scm.tag
                .let {
                    if (commitHash != null) {
                        it.replaceFirst(tagRegex, "<tag>$commitHash</tag>")
                    } else {
                        it
                    }
                }
                .let { project.file.writeText(it) }

            log.info("Removed DEV from project version and added commit hash $commitHash")
        } else {
            throw MojoFailureException("Cannot run set-ci-version on non-CI")
        }
    }
}