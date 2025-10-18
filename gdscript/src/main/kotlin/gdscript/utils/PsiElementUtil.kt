package gdscript.utils

import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiEditorUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.prevLeaf
import gdscript.psi.GdArgList
import gdscript.psi.GdCallEx
import gdscript.psi.GdTypes
import gdscript.utils.ElementTypeUtil.isSkipable
import project.psi.model.GdAutoload

object PsiElementUtil {

    private val SKIPS_TO_COMMENT = listOf(TokenType.WHITE_SPACE, GdTypes.INDENT, GdTypes.DEDENT)

    /**
     * Counts the number of preceding newlines before the given position in the parent element.
     * 
     * @param position The text offset position to check before
     * @return The count of newline characters found before the position, excluding trailing spaces and tabs
     */
    fun PsiElement.precedingNewLines(position: Int): Int {
        val parent = this.parent ?: return 0

        if (parent.text.isEmpty()) return 0
        val partial = parent.containingFile.text.substring(0, position)

        var c = 0
        for (ch in partial.toCharArray().reversed()) {
            if (ch == '\n') {
                c += 1
            } else if (ch != ' ' && ch != '\t') {
                break
            }
        }

        return c
    }

    /**
     * Gets the caret offset in the editor if there is exactly one caret.
     * 
     * @return The caret offset if a single caret exists, null otherwise
     */
    fun PsiElement.getCaretOffsetIfSingle(): Int? {
        val editor = PsiEditorUtil.findEditor(this) ?: return null
        if (editor.caretModel.caretCount != 1) {
            return null
        }

        return editor.caretModel.currentCaret.offset
    }

    /**
     * Attempts to find a call expression (GdCallEx) immediately following this element.
     * Checks if the next visible leaf is a left round bracket that belongs to a call expression.
     * 
     * @return The GdCallEx element if found, null otherwise
     */
    fun PsiElement.getCallExpr(): GdCallEx? {
        val next = PsiTreeUtil.nextVisibleLeaf(this) ?: return null
        if (next.elementType == GdTypes.LRBR && next.parent?.elementType == GdTypes.CALL_EX) {
            return next.parent as GdCallEx
        }

        return null
    }

    /**
     * Finds the call expression that contains this element as a parameter.
     * Traverses up the tree to find the parent argument list and its containing call expression.
     * 
     * @return The GdCallEx containing this parameter, null if not found
     */
    fun PsiElement.getCallExprOfParam(): GdCallEx? {
        val argList = PsiTreeUtil.getParentOfType(this, GdArgList::class.java) ?: return null
        if (argList.parent is GdCallEx) return argList.parent as GdCallEx

        return null
    }

    /**
     * Finds the next sibling token that is not whitespace or a comment.
     * 
     * @return The next non-whitespace, non-comment token, or null if none exists
     */
    fun PsiElement.nextNonWhiteCommentToken(): PsiElement? {
        var next = this.nextSibling
        while (next != null && next.elementType.isSkipable()) {
            next = next.nextSibling
        }

        return next
    }

    /**
     * Finds the previous sibling token that is not whitespace or a comment, optionally skipping additional token types.
     * 
     * @param skip Additional element types to skip during traversal
     * @return The previous non-whitespace, non-comment token (excluding skipped types), or null if none exists
     */
    fun PsiElement.prevNonWhiteCommentToken(vararg skip: IElementType): PsiElement? {
        var prev = this.prevSibling
        while (prev != null && (prev.elementType.isSkipable() || skip.contains(prev.elementType))) {
            prev = prev.prevSibling
        }

        return prev
    }

    /**
     * Finds the preceding comment block before this element.
     * Traverses backwards through whitespace, indentation, and dedentation tokens to locate a comment.
     * Stops if a newline is encountered without finding a comment immediately before it.
     * 
     * @return The comment element if found, null otherwise
     */
    fun PsiElement.prevCommentBlock(): PsiElement? {
        var prev = this.prevLeaf()
        while (SKIPS_TO_COMMENT.contains(prev?.elementType)) {
            if (prev!!.text == "\n") {
                prev = prev.prevLeaf()
                break
            }
            prev = prev.prevLeaf()
        }

        if (prev?.elementType == GdTypes.COMMENT) return prev
        return null
    }

    /**
     * Converts an object to its underlying PSI element representation.
     * Handles special cases like GdAutoload by extracting their contained element.
     * 
     * @return The PsiElement representation of this object
     */
    fun Any.psi(): PsiElement {
        return when (this) {
            is GdAutoload -> this.element
            else -> this as PsiElement
        }
    }

}
