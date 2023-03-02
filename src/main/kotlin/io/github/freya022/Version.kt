package io.github.freya022

data class Version(
        val major: String,
        val minor: String,
        val revision: String,
        val classifier: String?
) {
    companion object {
        private val versionPattern = Regex("""(\d+)\.(\d+)\.(\d+)(?:-(\w+\.\d+))?(?:_DEV)?""")

        fun parseOrNull(version: String): Version? {
            val groups = versionPattern.matchEntire(version)?.groups ?: return null

            val major = groups[1]?.value ?: return null
            val minor = groups[2]?.value ?: return null
            val revision = groups[3]?.value ?: return null
            val classifier = groups[4]?.value

            return Version(major, minor, revision, classifier)
        }
    }
}