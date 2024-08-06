package io.github.freya022

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPJvmConfig.Builder
import com.google.devtools.ksp.processing.KspGradleLogger
import io.github.freya022.processor.BCSpringMetadataSymbolProcessorProvider
import io.github.freya022.util.LogSupplier
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope
import org.apache.maven.project.MavenProject
import java.io.File
import java.nio.file.Path
import kotlin.io.path.*

@Mojo(
    name = "generate-configuration-metadata",
    defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
    // Required to get dependencies for KSP libraries
    requiresDependencyResolution = ResolutionScope.COMPILE
)
class GenerateConfigurationMetadataMojo : AbstractMojo() {
    @Parameter(defaultValue = "\${project}", required = true, readonly = true)
    lateinit var project: MavenProject

    @Parameter(defaultValue = "\${project.basedir}", required = true, readonly = true)
    lateinit var baseDir: File

    @Parameter(defaultValue = "\${project.build.directory}", required = true, readonly = true)
    lateinit var buildDir: File

    @Parameter(defaultValue = "\${kotlin.compiler.apiVersion}", required = true, readonly = true)
    lateinit var apiVersion: String

    @Parameter(defaultValue = "\${kotlin.compiler.languageVersion}", required = true, readonly = true)
    lateinit var languageVersion: String

    @Parameter(defaultValue = "\${kotlin.compiler.jvmTarget}", required = true, readonly = true)
    lateinit var jvmTarget: String

    @Parameter(required = true)
    lateinit var sourceDirs: List<File>

    private val targetPath: Path
        get() = Path(project.build.directory).resolve("classes")

    @Throws(MojoFailureException::class)
    override fun execute() {
        try {
            val ksp = File(buildDir, "ksp")
            val config = Builder().apply {
                apiVersion = this@GenerateConfigurationMetadataMojo.apiVersion
                cachesDir = File(ksp, "cache")
                classOutputDir = File(ksp, "class-output")
                javaOutputDir = File(ksp, "generated")
                jvmTarget = this@GenerateConfigurationMetadataMojo.jvmTarget
                kotlinOutputDir = File(ksp, "kotlin-output")
                languageVersion = this@GenerateConfigurationMetadataMojo.languageVersion
                moduleName = ""
                outputBaseDir = buildDir
                projectBaseDir = baseDir
                resourceOutputDir = File(ksp, "resource-output")
                sourceRoots = sourceDirs
                libraries = project.artifacts.map { it.file }
            }.build()

            KotlinSymbolProcessing(
                config,
                listOf(BCSpringMetadataSymbolProcessorProvider(
                    LogSupplier(this),
                    baseDir.toPath().resolve("src").resolve("main").resolve("resources"),
                    targetPath.resolve("META-INF").resolve("additional-spring-configuration-metadata.json")
                )),
                KspGradleLogger(KspGradleLogger.LOGGING_LEVEL_INFO)
            ).execute()
        } catch (e: Exception) {
            throw MojoFailureException("Failed to preprocess BotCommands sources", e)
        }
    }
}