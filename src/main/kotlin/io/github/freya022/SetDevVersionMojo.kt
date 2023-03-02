package io.github.freya022

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject

@Mojo(name = "set-dev-version", defaultPhase = LifecyclePhase.INITIALIZE)
class SetDevVersionMojo : AbstractMojo() {
    @Parameter(defaultValue = "\${project}", required = true, readonly = true)
    lateinit var project: MavenProject

    @Throws(MojoFailureException::class)
    override fun execute() {
        // https://github.com/DV8FromTheWorld/JDA/blob/2328104e0117957ab498a0f3fcf2c281269b7bd9/build.gradle.kts#L44-L49
        val isCI = System.getProperty("BUILD_NUMBER") != null // Jenkins
                || System.getenv("BUILD_NUMBER") != null
                || System.getProperty("GIT_COMMIT") != null // Jitpack
                || System.getenv("GIT_COMMIT") != null
                || System.getProperty("GITHUB_ACTIONS") != null // GitHub Actions
                || System.getenv("GITHUB_ACTIONS") != null
        if (!isCI) {
            project.version = project.version + "_DEV" //Technically not necessary for the JAR to be named correctly
            project.build.finalName = "${project.artifactId}-${project.version}" //Just reapply the default naming scheme
            log.info("Added DEV to project version")
        }
    }
}