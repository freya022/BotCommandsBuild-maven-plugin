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
    val deprecation: Deprecation?
) {
    class Deprecation(
        val reason: String,
        val level: String,
        val replacement: String?,
    )
}