package io.cookielab.android.poeditor

import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

/**
 * An extension for configuration of POEditor plugin.
 */
public abstract class PoEditorPluginExtension {

    /**
     * POEditor project id.
     */
    public abstract val projectId: Property<String>

    /**
     * POEditor access token with read access to the project.
     */
    public abstract val token: Property<String>

    /**
     * Map of android qualifiers to the respective POEditor languages. eg. mapOf("" to "en", "-cs" to "cs").
     */
    public abstract val qualifiersToLanguages: MapProperty<String, String>

    /**
     * Name of the file where to find strings that are ready to be translated. Defaults to "ready.xml".
     */
    public abstract val readyFileName: Property<String>

    /**
     * Name of the file where the translations downloaded from POEditor are written. Defaults to "translated.xml".
     */
    public abstract val translatedFileName: Property<String>

    /**
     * Suffices to exclude. This is used to filter strings downloaded from POEditor. Defaults to setOf("_ios");
     */
    public abstract val excludedSuffices: SetProperty<String>

    /**
     * Configures the size of indentation. Defaults to "    " (4 spaces).
     */
    public abstract val indent: Property<String>

    /**
     * Whether to append a comment with the sync timestamp (eg. "Imported from POEditor on …") to the
     * translated files. Defaults to false.
     */
    public abstract val printSyncDate: Property<Boolean>
}
