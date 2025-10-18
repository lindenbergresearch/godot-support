package gdscript.annotator

import com.intellij.lang.annotation.*
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import gdscript.psi.GdEnumDeclTl
import gdscript.psi.GdEnumValueNmi

/**
 * Checks for duplicate enum values within the same enum
 */
class GdEnumValueAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Skip annotation if indices are not ready
        if (DumbService.isDumb(element.project)) {
            return
        }

        if (element !is GdEnumValueNmi) return

        val enumDecl = PsiTreeUtil.getParentOfType(element, GdEnumDeclTl::class.java) ?: return
        val name = element.name

        // Find all enum values with the same name in this enum
        val duplicates = enumDecl.enumValueList
            .filter { it.enumValueNmi.name == name }

        // If there are more than one with the same name, it's a duplicate
        if (duplicates.size > 1) {
            holder.newAnnotationGd(
                element.project,
                HighlightSeverity.ERROR,
                "Duplicate enum value '$name' - found ${duplicates.size} times."
            ).range(element.textRange).create()
        }
    }
}
