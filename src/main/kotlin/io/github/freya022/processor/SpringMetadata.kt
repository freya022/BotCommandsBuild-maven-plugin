package io.github.freya022.processor

class SpringMetadata(
    val groups: MutableList<GroupMetadata>,
    val properties: MutableList<PropertyMetadata>,
    val hints: MutableList<Map<String, *>>,
) {
    constructor() : this(arrayListOf(), arrayListOf(), arrayListOf())
}
class GroupMetadata(
    val name: String,
    val type: String,
    val sourceType: String,
)
class PropertyMetadata(
    val name: String,
    val defaultValue: String?,
    val type: String,
    val sourceType: String,
    val description: String?,
    val deprecation: Deprecation?
) {
    class Deprecation(
        val reason: String,
        val level: String,
        val replacement: String?,
    )
}
class ClassReferenceHint private constructor(val name: String, val providers: List<Provider>) {
    class Provider(val name: String, val parameters: Parameters) {
        class Parameters(val target: String)
    }

    constructor(name: String, targetClass: String) : this(name, listOf(Provider("class-reference", Provider.Parameters(targetClass))))
}