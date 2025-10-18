package gdscript.psi

import com.intellij.psi.tree.IElementType
import com.jetbrains.rider.godot.community.gdscript.GdLanguage
import org.jetbrains.annotations.NonNls

class GdTokenType(debugName: @NonNls String) : IElementType(debugName, GdLanguage) {

    override fun toString(): String {
        return "GdTokenType." + super.toString()
    }

    /**
     * Compares two token by name.
     */
    fun equalsTokenType(other: IElementType): Boolean {
        return this.toString() == other.toString()
    }

}
