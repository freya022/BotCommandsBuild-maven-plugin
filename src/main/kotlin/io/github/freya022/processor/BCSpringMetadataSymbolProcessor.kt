package io.github.freya022.processor

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.*
import io.github.freya022.processor.util.*
import io.github.freya022.util.LogSupplier
import io.github.freya022.util.tryAppendDot
import org.apache.maven.plugin.logging.Log
import java.nio.file.Path
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createParentDirectories

private val configurationPropertiesName = AnnotationName("org.springframework.boot.context.properties", "ConfigurationProperties")
private val configurationValueName = AnnotationName("io.github.freya022.botcommands.internal.core.config", "ConfigurationValue")
private val deprecatedValueName = AnnotationName("io.github.freya022.botcommands.internal.core.config", "DeprecatedValue")

class BCSpringMetadataSymbolProcessor(logSupplier: LogSupplier, private val writePath: Path) : SymbolProcessor {
    private val log: Log by logSupplier

    private val configurableProperties: MutableSet<String> = hashSetOf()
    private val configuredProperties: MutableSet<String> = hashSetOf()
    private val metadata = SpringMetadata()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver.getSymbolsWithAnnotation(configurationPropertiesName.name, inDepth = false)
            .filterIsInstance<KSClassDeclaration>()
            .forEach(::processClassDeclaration)

        resolver.getSymbolsWithAnnotation(configurationValueName.name, inDepth = false)
            .filterIsInstance<KSPropertyDeclaration>()
            .forEach(::processPropertyDeclaration)

        return emptyList()
    }

    override fun finish() {
        val propertiesWithoutConfigValue = configurableProperties - configuredProperties
        val propertiesWithoutConstructorParam = configuredProperties - configurableProperties
        if (propertiesWithoutConfigValue.isNotEmpty()) {
            log.warn("Could not find a @ConfigurationValue for $propertiesWithoutConfigValue")
        }
        if (propertiesWithoutConstructorParam.isNotEmpty()) {
            log.warn("Could not find a constructor parameter for $propertiesWithoutConstructorParam")
        }

        writePath.createParentDirectories()
        writePath.bufferedWriter().use {
            ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .writerWithDefaultPrettyPrinter()
                .writeValue(it, metadata)
        }
    }

    /**
     * Find all properties based on the @ConfigurationProperties prefix + constructor parameter name.
     *
     * This is used to check that all properties have a @ConfigurationValue assigned and vice-versa
     */
    private fun processClassDeclaration(classDeclaration: KSClassDeclaration) {
        val prefix: String = classDeclaration.findAnnotation(configurationPropertiesName).getOrDefault("prefix")
        val constructor = classDeclaration.getDeclaredFunctions().single { it.isConstructor() }

        configurableProperties += constructor.parameters
            .mapNotNull { it.name?.asString() }
            .map { "$prefix.$it" }
    }

    private fun processPropertyDeclaration(propertyDeclaration: KSPropertyDeclaration) {
        val configurationPropertiesAnnotation = propertyDeclaration.findAnnotation(configurationValueName)
        val path: String = configurationPropertiesAnnotation.getOrDefault("path")
        val defaultValue: String? = configurationPropertiesAnnotation.getIfSet("defaultValue")

        if (path !in configurableProperties) {
            log.warn("Metadata was added for '$path' but there is no such parameter in a constructor annotated with @ConfigurationProperties")
        } else {
            configuredProperties += path
        }

        fun KSTypeReference.resolveQualifiedName(from: KSDeclaration): String {
            return resolve().declaration.qualifiedName?.asString()
                ?: throw IllegalArgumentException("Unknown type for ${from.qualifiedName?.asString()}: $this")
        }
        val typeStr = configurationPropertiesAnnotation
            .getIfSet("type")
            ?: propertyDeclaration.type.resolveQualifiedName(propertyDeclaration)

        val deprecation = propertyDeclaration.findAnnotationOrNull(deprecatedValueName)?.let { deprecatedValueAnnotation ->
            val reason = deprecatedValueAnnotation.getOrDefault<String>("reason").tryAppendDot()
            val level = deprecatedValueAnnotation.getOrDefault<KSClassDeclaration>("level").simpleName.asString().lowercase()
            val replacement = deprecatedValueAnnotation.getIfSet<String>("replacement")
            PropertyMetadata.Deprecation(reason, level, replacement)
        }

        metadata.properties += PropertyMetadata(
            name = path,
            defaultValue = defaultValue,
            type = typeStr.toJavaType(),
            sourceType = propertyDeclaration.canonicalName,
            deprecation = deprecation
        )
    }

    private fun String.toJavaType() = when (this) {
        "kotlin.collections.List" -> "java.util.List"
        "kotlin.collections.Set" -> "java.util.Set"
        "kotlin.collections.Collection" -> "java.util.Collection"
        "kotlin.collections.Map" -> "java.util.Map"
        "kotlin.Boolean" -> "java.lang.Boolean"
        "kotlin.Int" -> "java.lang.Integer"
        "kotlin.Long" -> "java.lang.Long"
        "kotlin.Double" -> "java.lang.Double"
        else -> {
            if (this.startsWith("kotlin."))
                log.warn("Unmapped type: $this")
            this
        }
    }
}