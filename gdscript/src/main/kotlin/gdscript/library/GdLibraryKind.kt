package gdscript.library

import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.jetbrains.rider.godot.community.gdscript.GdLanguage

val ID = GdLanguage.id
object GdLibraryKind : PersistentLibraryKind<GdLibraryProperties>(ID) {

    override fun createDefaultProperties(): GdLibraryProperties {
        return GdLibraryProperties()
    }
}
