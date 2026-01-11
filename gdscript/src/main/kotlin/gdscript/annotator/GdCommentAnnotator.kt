package gdscript.annotator

import com.intellij.lang.annotation.*
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.util.text.findTextRange
import gdscript.highlighter.GdHighlighterColors
import gdscript.settings.GdProjectSettingsState

/**
 * A custom annotator class for highlighting and annotating specific elements within comments in Godot projects.
 * This annotator supports dynamic styling of comments based on project-specific settings.
 *
 * This class implements the `Annotator` interface and processes elements of type `PsiComment`.
 * Comments starting with `##` and comments containing critical, warning, or note tags specified in the project settings
 * can be highlighted with appropriate styles.
 */
class GdCommentAnnotator : Annotator {

    /**
     * Annotates the specified PSI element with appropriate styles and highlights based on project settings.
     *
     * @param element The PSI element to annotate. This method only processes elements of type `PsiComment`.
     * @param holder The annotation holder used to create and register annotations for the element.
     */
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Skip annotation if indices are not ready
        if (DumbService.isDumb(element.project)) {
            return
        }

        if (element !is PsiComment) return

        if (element.text.startsWith("##")) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .textAttributes(GdHighlighterColors.DOC_COMMENT)
                .create()
        }

        val state = GdProjectSettingsState.getInstance(element).state
        val criticals = state.criticalTags.split(",")
        val warnings = state.warnings.split(",")
        val notes = state.notes.split(",")

        arrayOf(
            arrayOf(criticals, GdHighlighterColors.DANGER),
            arrayOf(warnings, GdHighlighterColors.WARNING),
            arrayOf(notes, GdHighlighterColors.NOTE),
        ).forEach { its ->
            (its[0] as ArrayList<String>).forEach {
                val range = element.text.findTextRange(it) ?: return@forEach
                holder
                    .newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(range.shiftRight(element.textOffset))
                    .textAttributes(its[1] as TextAttributesKey)
                    .create()
            }
        }
    }
}
