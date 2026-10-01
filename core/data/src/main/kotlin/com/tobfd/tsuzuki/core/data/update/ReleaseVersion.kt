package com.tobfd.tsuzuki.core.data.update

/**
 * A release version `major.minor.patch`, as in the tags `v1.2.3` (docs/RELEASING.md) and the app's
 * `versionName`. Anything else (pre-release suffixes, two parts, words) is not a version.
 */
internal data class ReleaseVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<ReleaseVersion> {

    override fun compareTo(other: ReleaseVersion): Int =
        compareValuesBy(this, other, ReleaseVersion::major, ReleaseVersion::minor, ReleaseVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val PATTERN = Regex("""v?(\d{1,6})\.(\d{1,6})\.(\d{1,6})""")

        /** "v1.2.3" or "1.2.3"; null for anything else. */
        fun parse(text: String): ReleaseVersion? {
            val match = PATTERN.matchEntire(text.trim()) ?: return null
            val (major, minor, patch) = match.destructured
            return ReleaseVersion(major.toInt(), minor.toInt(), patch.toInt())
        }
    }
}
