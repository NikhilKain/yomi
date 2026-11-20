package tachiyomi.domain.library.model

sealed interface LibraryDisplayMode {

    data object CompactGrid : LibraryDisplayMode
    data object ComfortableGrid : LibraryDisplayMode
    data object List : LibraryDisplayMode
    data object CoverOnlyGrid : LibraryDisplayMode

    // Yomi: mixed tile sizes, one wide feature, two halves, three thirds, repeating.
    data object BentoGrid : LibraryDisplayMode

    object Serializer {
        fun deserialize(serialized: String): LibraryDisplayMode {
            return LibraryDisplayMode.deserialize(serialized)
        }

        fun serialize(value: LibraryDisplayMode): String {
            return value.serialize()
        }
    }

    companion object {
        val values by lazy { setOf(BentoGrid, CompactGrid, ComfortableGrid, List, CoverOnlyGrid) }
        // A getter, not a stored value: reading BentoGrid while the interface initialises would store null.
        val default: LibraryDisplayMode get() = BentoGrid

        fun deserialize(serialized: String): LibraryDisplayMode {
            return when (serialized) {
                "COMFORTABLE_GRID" -> ComfortableGrid
                "COMPACT_GRID" -> CompactGrid
                "COVER_ONLY_GRID" -> CoverOnlyGrid
                "BENTO_GRID" -> BentoGrid
                "LIST" -> List
                else -> default
            }
        }
    }

    fun serialize(): String {
        return when (this) {
            ComfortableGrid -> "COMFORTABLE_GRID"
            CompactGrid -> "COMPACT_GRID"
            CoverOnlyGrid -> "COVER_ONLY_GRID"
            BentoGrid -> "BENTO_GRID"
            List -> "LIST"
        }
    }
}
