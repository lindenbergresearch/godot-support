package gdscript.annotator

import com.intellij.lang.annotation.*
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import gdscript.psi.*

/**
 * Checks for duplicate enum values within the same enum
 * and across all anonymous enums in the same file
 */
class GdEnumValueAnnotator : Annotator {

    /**
     * Checks for duplicate enum values within the same enum or across all anonymous enums in a file.
     * Generates error annotations if duplicates are found.
     *
     * @param element The PSI element to be annotated, expected to be a `GdEnumValueNmi`.
     * @param holder The annotation holder used to apply annotations to the element.
     */
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Skip annotation if indices are not ready
        if (DumbService.isDumb(element.project)) {
            return
        }

        if (element !is GdEnumValueNmi) return

        val enumDecl = PsiTreeUtil.getParentOfType(element, GdEnumDeclTl::class.java) ?: return
        val name = element.name
        val isAnonymousEnum = enumDecl.enumDeclNmi == null

        if (isAnonymousEnum) {
            // For anonymous enums, check across ALL anonymous enums in the file
            val file = (element.containingFile as? GdFile) ?: return
            val allAnonymousEnums = PsiTreeUtil.findChildrenOfType(file, GdEnumDeclTl::class.java)
                .filter { it.enumDeclNmi == null }

            val duplicates = allAnonymousEnums
                .flatMap { it.enumValueList }
                .filter { it.enumValueNmi.name == name }

            if (duplicates.size > 1) {
                holder.newAnnotationGd(
                    element.project,
                    HighlightSeverity.ERROR,
                    "Duplicate enum value in anonymous enums - found: '$name' at ${duplicates.size} locations.",
                ).range(element.textRange).create()
            }
        } else {
            // For named enums, check only within the same enum
            val duplicates = enumDecl.enumValueList
                .filter { it.enumValueNmi.name == name }

            if (duplicates.size > 1) {
                holder.newAnnotationGd(
                    element.project,
                    HighlightSeverity.ERROR,
                    "Duplicate enum value - found: <b>'$name'</b> at ${duplicates.size} locations.",
                ).range(element.textRange).create()
            }
        }
    }
}
