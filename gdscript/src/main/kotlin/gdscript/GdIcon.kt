package gdscript

import GdScriptPluginIcons
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.IconLoader
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * Handles loading and managing editor icons for classes in the context of Godot plugin development.
 * The class caches icons to optimize performance and provides a fallback mechanism using a default backup icon
 * for cases where the desired icon cannot be loaded.
 */
object GdIcon {
    private var editorIcons = HashMap<String, Icon>()


    /**
     * Retrieves the editor icon corresponding to the specified class name. If the icon is not already loaded,
     * it attempts to load the icon from the appropriate resource location and caches it. If the icon cannot be
     * loaded, a default backup icon is used instead.
     *
     * @param className the name of the class for which the editor icon should be retrieved
     * @return the corresponding editor icon, or a default backup icon if the specific icon cannot be loaded
     */
    @ApiStatus.Obsolete // todo: find a way to get rid of this completely and also all unused Godot editor icons
    fun getEditorIcon(className: String): Icon {
        val icon = editorIcons[className]
        if (icon == null) {
            try {
                val loaded = IconLoader.getIcon(
                    String.format("icons/godot_editor/%s.svg", className),
                    GdIcon::class.java
                )
                if (loaded.iconHeight > 1) {
                    //loaded = IconUtil.toSize(loaded, 16, 16)
                    // todo: determine not needed icons and remove them
                    // rework icons to be required size without scaling
                    editorIcons[className] = loaded
                } else {
                    editorIcons[className] = GdScriptPluginIcons.Icons.BackupIcon
                }
            } catch (e: Exception) {
                thisLogger().error("Unable to load editor icon for $className. Using default one.", e)
                editorIcons[className] = GdScriptPluginIcons.Icons.BackupIcon
            }
        }

        return editorIcons[className]!!
    }
}
