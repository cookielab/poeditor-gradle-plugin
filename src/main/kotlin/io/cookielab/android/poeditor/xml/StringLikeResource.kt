package io.cookielab.android.poeditor.xml

/**
 * Describes string resources typically found in res/values directory.
 *
 * Supported types:
 * - Strings
 * - Plurals
 *
 * Unsupported types:
 * - String arrays
 *
 * Note: [StringRes] and [PluralRes] are equal (and hash) by [name] only — value/items are ignored. This drives
 * the `Set`/`distinct` deduplication of terms, where the first occurrence wins (ready.xml is read before
 * translated.xml). [PluralRes.Item] keeps full value equality.
 */
internal sealed interface StringLikeResource {
    /**
     * Each resource has a name.
     */
    val name: String

    /**
     * String resource.
     *
     * @property value the value of this string resource, typically enclosed in quotes.
     */
    data class StringRes(override val name: String, val value: String) : StringLikeResource {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as StringRes

            return name == other.name
        }

        override fun hashCode(): Int {
            return name.hashCode()
        }
    }

    /**
     * Plurals resource.
     *
     * @property items plural items definitions.
     */
    data class PluralRes(override val name: String, val items: List<Item>) : StringLikeResource {

        init {
            require(items.isNotEmpty()) { "PluralRes '$name' must have at least one item." }
            require(items.mapTo(mutableSetOf()) { it.quantity }.size == items.size) {
                "PluralRes '$name' has duplicate quantities."
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as PluralRes

            return name == other.name
        }

        override fun hashCode(): Int {
            return name.hashCode()
        }

        /**
         * A single plural item.
         *
         * @property quantity the quantity this item represents.
         * @property value the string value of this plural form.
         */
        data class Item(val quantity: Quantity, val value: String)

        /**
         * All supported plural quantities.
         *
         * @property xmlName XML representation of this quantity.
         */
        enum class Quantity(val xmlName: String) {
            ZERO("zero"),
            ONE("one"),
            TWO("two"),
            FEW("few"),
            MANY("many"),
            OTHER("other"),
        }
    }
}
