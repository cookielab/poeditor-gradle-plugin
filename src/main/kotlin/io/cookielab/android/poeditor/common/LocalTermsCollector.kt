package io.cookielab.android.poeditor.common

import io.cookielab.android.poeditor.xml.StringLikeResource
import java.io.File

/**
 * Reads the local terms declared in a single `values` directory.
 *
 * This is the single source of truth for collecting local terms.
 */
internal interface LocalTermsCollector {

    /**
     * Collects the unique string-like resources declared in [valuesDir]'s ready and translated files.
     *
     * The ready file is parsed before the translated file, so on a name collision (resources have name-only
     * equality) the ready entry is the one that survives. Missing files are skipped, so a [valuesDir] holding
     * neither file yields an empty set.
     *
     * @param valuesDir the `values`-like directory to read from.
     * @return the unique resources found across the ready and translated files.
     */
    fun collect(valuesDir: File): Set<StringLikeResource>
}
