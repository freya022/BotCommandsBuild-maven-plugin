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

    @Throws(MojoFailureException::class)
    override fun execute() {
        if (isCI && "_DEV" in project.version) {
            throw MojoFailureException("CI builds should not have the _DEV suffix, the CI should run the set-ci-version goal, and should have been ran in a separate maven instance")
        }
    }
}