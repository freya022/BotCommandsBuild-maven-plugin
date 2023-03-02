package io.github.freya022

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject

@Mojo(name = "set-ci-version", requiresDirectInvocation = true)
class SetCIVersionMojo : AbstractMojo() {
    @Parameter(defaultValue = "\${project}", required = true, readonly = true)
    lateinit var project: MavenProject

    @Throws(MojoFailureException::class)
    override fun execute() {
        if (isCI) {
            project.file.readText() //Remove _DEV
                    .replace(Regex("_DEV(?=</version>)"), "")
                    .let { project.file.writeText(it) }

            log.info("Removed DEV from project version")
        } else {
            throw MojoFailureException("Cannot run set-ci-version on non-CI")
        }
    }
}