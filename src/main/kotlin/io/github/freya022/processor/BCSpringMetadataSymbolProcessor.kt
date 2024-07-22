package io.github.freya022.processor

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.*
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import io.github.freya022.processor.util.*
import io.github.freya022.util.LogSupplier
import io.github.freya022.util.tryAppendDot
import org.apache.maven.plugin.logging.Log
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

private val configurationPropertiesName = AnnotationName("org.springframework.boot.context.properties", "ConfigurationProperties")
private val configurationValueName = AnnotationName("io.github.freya022.botcommands.internal.core.config", "ConfigurationValue")
private val deprecatedValueName = AnnotationName("io.github.freya022.botcommands.internal.core.config", "DeprecatedValue")

private val gson = GsonBuilder()
    .setPrettyPrinting()
    .disableHtmlEscaping()
    .create()

private val classPattern = Regex("java.lang.Class<(.+)>")
private val collectionPattern = Regex("java\\.util\\.(?:Set|List)<(.+)>$")
private val mapPattern = Regex("java\\.util\\.Map<(.+), (.+)>$")

class BCSpringMetadataSymbolProcessor(logSupplier: LogSupplier, private val resourcesPath: Path, private val writePath: Path) : SymbolProcessor {
    private val log: Log by logSupplier

    private val configurableProperties: MutableSet<String> = hashSetOf()
    private val configuredProperties: MutableSet<String> = hashSetOf()
    private val metadata = SpringMetadata().apply {
        val resourceMetadata = resourcesPath.resolve("META-INF").resolve("spring-configuration-metadata.json")
            .readText()
            .let { gson.fromJson(it, SpringMetadata::class.java) }

        groups += resourceMetadata.groups
        properties += resourceMetadata.properties
        hints += resourceMetadata.hints
    }

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
        writePath.writeText(gson.toJson(metadata))
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

        fun KSTypeReference.resolveTypedQualifiedName(from: KSDeclaration): String {
            val type = resolve()
            val qualifiedName = type.declaration.qualifiedName?.asString()?.toJavaType()
                ?: throw IllegalArgumentException("Unknown type for $this in ${from.qualifiedName?.asString()}")

            return when {
                type.arguments.isNotEmpty() -> {
                    val argumentsStr = type.arguments.joinToString {
                        when (it.variance) {
                            Variance.STAR -> "?"
                            else -> it.type!!.resolveTypedQualifiedName(from)
                        }
                    }
                    "$qualifiedName<$argumentsStr>"
                }
                else -> qualifiedName
            }
        }
        val typeStr = configurationPropertiesAnnotation
            .getIfSet("type")
            ?: propertyDeclaration.type.resolveTypedQualifiedName(propertyDeclaration)

        tryPutClassReferenceHint(path, typeStr)

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

    private fun tryPutClassReferenceHint(name: String, typeStr: String) {
        fun ClassReferenceHint.toMap(): Map<String, *> {
            return gson.fromJson(gson.toJson(this), object : TypeToken<Map<String, *>>() {})
        }

        classPattern.matchEntire(typeStr)?.let { matchResult ->
            val classTypeStr = matchResult.groupValues[1]
            if (classTypeStr == "?")
                return

            metadata.hints += ClassReferenceHint(name, classTypeStr).toMap()
            return
        }

        collectionPattern.matchEntire(typeStr)?.let { matchResult ->
            val elementTypeStr = matchResult.groupValues[1]
            // May or may not be a class
            tryPutClassReferenceHint(name, elementTypeStr)
            return
        }

        mapPattern.matchEntire(typeStr)?.let { matchResult ->
            val keyTypeStr = matchResult.groupValues[1]
            val valueTypeStr = matchResult.groupValues[2]
            // May or may not be a class
            tryPutClassReferenceHint("$name.keys", keyTypeStr)
            tryPutClassReferenceHint("$name.values", valueTypeStr)
            return
        }
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
        "kotlin.String" -> "java.lang.String"
        else -> {
            if (this.startsWith("kotlin."))
                log.warn("Unmapped type: $this")
            this
        }
    }
}