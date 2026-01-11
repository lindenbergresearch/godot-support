package gdscript.annotator

import com.intellij.lang.annotation.*
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import gdscript.action.quickFix.GdFileClassNameAction
import gdscript.action.quickFix.GdRemoveElementsAction
import gdscript.highlighter.GdHighlighterColors
import gdscript.index.impl.GdClassNamingIndex
import gdscript.index.impl.GdFileResIndex
import gdscript.psi.*
import gdscript.psi.utils.GdClassUtil
import gdscript.psi.utils.PsiGdFileUtil
import gdscript.utils.PsiFileUtil.isInSdk
import gdscript.utils.StringUtil.snakeToPascalCase

/**
 * Annotator implementation that adds semantic highlighting and validation to PSI elements related to GDScript classes.
 * Handles various annotations, such as errors and warnings, for GDScript-related elements in the source code.
 */
class GdClassNameAnnotator : Annotator {

    /**
     * Annotates specified elements of the PSI tree by applying highlighting and validations.
     *
     * @param element The PSI element to be processed for annotations.
     * @param holder The holder used to collect annotations and apply them to the elements.
     */
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Skip annotation if indices are not ready
        if (DumbService.isDumb(element.project)) {
            return
        }

        when (element) {
            is GdInheritanceId -> existingInheritance(element, holder)
            is GdInheritanceIdRef -> colorInheritance(element, holder)
            is GdInheritanceSubIdRef -> colorClass(element, GdHighlighterColors.CLASS_TYPE, holder)

            is GdClassNameNmi -> {
                alreadyExists(element, holder)
                    || classNameToFilename(element, holder)
                    || colorClass(element, GdHighlighterColors.CLASS_TYPE, holder)
            }

            is GdClassNaming -> isDuplicated(element, holder, "class_name")
            is GdInheritance -> isDuplicated(element, holder, "Inheritance")
        }
    }


    /**
     * Validates whether the given inheritance reference in a `GdInheritanceId` element points to an existing class.
     * If the class is not found, an error annotation is added to the holder.
     *
     * @param element The `GdInheritanceId` element representing the inheritance reference to validate.
     * @param holder The `AnnotationHolder` used to hold and display any validation errors.
     */
    private fun existingInheritance(element: GdInheritanceId, holder: AnnotationHolder) {
        val ref = element.lastChild.references.firstOrNull()
        if (ref?.resolve() != null) return

        val name = element.text
        if ((GdClassUtil.getClassIdElement(name, element, element.project) == null)
            // File index when you are extending a script without class_name
            && GdFileResIndex.getFiles(name.trim('"', '\''), element.project).isEmpty()
        ) {
            // The last case is extending InnerClass within the same file which does not require FQN
            // and can directly use any at the lower level
            val classElement = when (element) {
                is GdFile, is GdClassDeclTl -> element
                else -> GdClassUtil.getOwningClassElement(element)
            }

            PsiTreeUtil.getStubChildrenOfTypeAsList(classElement, GdClassDeclTl::class.java).forEach {
                if (it.name == name) return
            }

            if (classElement is GdClassDeclTl) {
                PsiTreeUtil.getStubChildrenOfTypeAsList(classElement.parent, GdClassDeclTl::class.java).forEach {
                    if (it.name == name) return
                }
            }

            holder
                .newAnnotationGd(element.project, HighlightSeverity.ERROR, "Class not found: '$name'")
                .range(element.textRange)
                .create()
        }
    }


    /**
     * Highlights the inheritance ID reference element with the appropriate color based on its classification.
     *
     * @param element The inheritance ID reference element to be analyzed and highlighted.
     * @param holder The annotation holder used to apply the coloring and annotations.
     */
    private fun colorInheritance(element: GdInheritanceIdRef, holder: AnnotationHolder) {
        if (element.isClassName) {
            if (GdClassUtil.getClassIdElement(element.text, element, element.project)?.containingFile?.isInSdk() == true)
                colorClass(element, GdHighlighterColors.ENGINE_TYPE, holder)
            else colorClass(element, GdHighlighterColors.CLASS_TYPE, holder)
        }
    }


    /**
     * Highlights a specified element in the editor with the given color and severity.
     *
     * @param element the PSI element to be colored
     * @param color the text attributes key representing the color to apply
     * @param holder the annotation holder used to apply highlight to the element
     * @return true if the highlighting is applied successfully
     */
    private fun colorClass(element: PsiElement, color: TextAttributesKey, holder: AnnotationHolder): Boolean {
        holder
            .newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(element.textRange)
            .textAttributes(color)
            .create()
        return true
    }


    /**
     * Checks if the given `GdClassNameNmi` element conflicts with any other class definitions
     * either globally or within its scope, and if so, marks it with an appropriate annotation.
     *
     * @param element The `GdClassNameNmi` element to check for conflicts.
     * @param holder The `AnnotationHolder` used to create annotations for any detected conflicts.
     * @return `true` if a conflict is detected, otherwise `false`.
     */
    private fun alreadyExists(element: GdClassNameNmi, holder: AnnotationHolder): Boolean {
        val name = element.name
        var message = "Class defined in global scope"

        var conflict = GdClassNamingIndex.INSTANCE.getGloballyWithoutSelf(element).isNotEmpty()

        if (element.isInner) {
            // Inner class can conflict with the local main class
            conflict = conflict || GdClassNamingIndex.INSTANCE.getInFile(element).isNotEmpty()

            // Or with previously defined at the same level
            var prev = element.parent.prevSibling
            while ((prev != null) && !conflict) {
                if ((prev is GdClassDeclTl) && (prev.classNameNmi?.name == name)) {
                    conflict = true
                    message = "Class already defined"
                }
                prev = prev.prevSibling
            }
        }

        if (conflict) {
            holder
                .newAnnotationGd(element.project, HighlightSeverity.ERROR, message)
                .range(element.textRange)
                .create()
            return true
        }

        return false
    }


    /**
     * Validates if the class name matches the filename in which it is declared. If the class name does not match,
     * it creates a warning annotation and suggests renaming the class to match the filename.
     *
     * @param element the class name element being validated
     * @param holder the annotation holder used to create warnings
     * @return true if a mismatch is detected and an annotation is created, false otherwise
     */
    private fun classNameToFilename(element: GdClassNameNmi, holder: AnnotationHolder): Boolean {
        if (element.parent !is GdClassNaming) return false

        val name = element.name
        val filename = PsiGdFileUtil.filename(element.containingFile).snakeToPascalCase()
        if (filename.lowercase() != name.lowercase()) {
            holder
                .newAnnotationGd(element.project, HighlightSeverity.WEAK_WARNING, "Class name does not match filename")
                .range(element.textRange)
                .withFix(GdFileClassNameAction(filename, element))
                .create()
            return true
        }
        return false
    }


    /**
     * Checks if a given element is duplicated within its sibling nodes. If a duplicate is found,
     * an error annotation is created for the specified element, and a quick-fix action is provided
     * to remove the duplicate element.
     *
     * @param element The PSI element to check for duplication. This represents a node in the abstract syntax tree.
     * @param holder The annotation holder where error annotations are registered.
     * @param type The type of the element being checked, used to customize the error message.
     */
    private fun isDuplicated(element: PsiElement, holder: AnnotationHolder, type: String) {
        if (PsiTreeUtil.getPrevSiblingOfType(element, element::class.java) !== null) {
            holder
                .newAnnotationGd(element.project, HighlightSeverity.ERROR, "$type already defined")
                .range(element.textRange)
                .withFix(GdRemoveElementsAction(element))
                .create()
        }
    }

}
