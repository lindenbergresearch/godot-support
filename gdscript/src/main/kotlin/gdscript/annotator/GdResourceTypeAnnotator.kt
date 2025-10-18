package gdscript.annotator

import com.intellij.lang.annotation.*
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import gdscript.highlighter.GdHighlighterColors
import gdscript.index.impl.GdFileResIndex
import gdscript.psi.GdNodePath
import gdscript.psi.GdTypes
import gdscript.psi.utils.GdNodeUtil
import gdscript.settings.GdProjectSettingsState
import gdscript.settings.GdProjectState

private const val RESOURCE_PREFIX = "res://"
private val ALTERNATE_RES_PATTERN = "((?<!%)%[scdoxXf0-9+.*\\-]+)|(\\{[a-zA-Z0-9]*})".toRegex()
private val RES_PATTERN = """res://(?:[A-Za-z0-9_\-./]+)\.[A-Za-z0-9]{3,5}""".toRegex()

/**
 * Checks for existence of [res://] resource
 * Checks for existence of $NodePath, %Unique
 * Colors string's formatter specifiers
 */
class GdResourceTypeAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Skip annotation if indices are not ready
        if (DumbService.isDumb(element.project)) {
            return
        }

        val state = GdProjectSettingsState.getInstance(element).state.annotators

      //  println("DEBUG: ${element.text} ${element.elementType}")

        if (element is GdNodePath) {
            if (state != GdProjectState.DISABLE) resourceExists(element, holder, state)
        } else if (element.elementType == GdTypes.STRING) {
            if (state != GdProjectState.DISABLE)
                if (!stringResourceExists(element, holder, state)) return
            stringFormats(element, holder)
        }
    }

    private fun stringResourceExists(element: PsiElement, holder: AnnotationHolder, state: String): Boolean {
        val text = element.text.trim('"', '\'')
        if (text.startsWith(RESOURCE_PREFIX) && GdFileResIndex.getFiles(text, element.project).isEmpty()) {
            holder
                .newAnnotationGd(element.project, GdProjectState.selectedLevel(state), "Resource  not found")
                .range(TextRange.create(element.textRange.startOffset + 1, element.textRange.endOffset - 1))
                .create()
            return false
        }

        return true
    }

    private fun resourceExists(element: GdNodePath, holder: AnnotationHolder, state: String) {
        if (element.text == "$\".\"") return
        val node = GdNodeUtil.findNode(element)
        if (node != null) return

        holder
            .newAnnotationGd(element.project, GdProjectState.selectedLevel(state), "Node not found")
            .range(element.textRange)
            .create()
    }

    private fun stringFormats(element: PsiElement, holder: AnnotationHolder) {
        val offset = element.textOffset

        ALTERNATE_RES_PATTERN.findAll(element.text).forEach {
            val range = it.range
            holder
                .newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange(range.first + offset, range.last + offset + 1))
                .textAttributes(GdHighlighterColors.RESOURCE_NOTATION)
                .create()
        }

        RES_PATTERN.findAll(element.text).forEach {
            val range = it.range
            holder
                .newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange(range.first + offset, range.last + offset + 1))
                .textAttributes(GdHighlighterColors.RESOURCE_NOTATION)
                .create()
        }
    }

}
