package gdscript.annotator

import com.intellij.lang.annotation.*
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import gdscript.psi.GdAnnotationTl
import gdscript.psi.utils.GdExprUtil
import gdscript.utils.GdAnnotationUtil

/**
 * The GdAnnotationAnnotator is responsible for validating and annotating custom annotations in
 * a specific domain-specific language (DSL). It ensures correctness of annotations by verifying
 * their arguments, types, and structure based on predefined definitions.
 *
 * This class implements the Annotator interface, enabling integration with a text editor or IDE
 * for real-time validation and feedback.
 *
 * Key Responsibilities:
 * - Skip annotation processing if the indexing is not yet ready in the project.
 * - Identify and validate custom annotation elements of type GdAnnotationTl.
 * - Validate the presence and correctness of annotation definitions using GdAnnotationUtil.
 * - Check the number of arguments in the annotation against the expected parameter definitions,
 *   including handling variadic parameters and required arguments.
 * - Verify type compatibility of actual arguments with the expected parameter types, providing
 *   detailed feedback for mismatched types.
 *
 * Errors or mismatches identified during validation are reported using the AnnotationHolder, marking
 * the relevant code sections and providing descriptive messages to help users correct errors.
 */
class GdAnnotationAnnotator : Annotator {

    /**
     * Annotates the given PSI element with appropriate error or informational messages based on its annotations and arguments.
     *
     * @param element The PSI element to be annotated. Must be of type GdAnnotationTl and represent an annotation in the code.
     * @param holder The annotation holder used to store annotations created during analysis. Responsible for displaying diagnostics in the editor.
     */
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Skip annotation if indices are not ready
        if (DumbService.isDumb(element.project)) {
            return
        }

        if (element !is GdAnnotationTl) return

        val definition = GdAnnotationUtil.get(element)
        if (definition == null) {
            holder
                .newAnnotationGd(element.project, HighlightSeverity.ERROR, "Unknown annotation")
                .range(element.textRange)
                .create()
            return
        }

        val definitionParams = definition.parameters
        val usedParams = element.annotationParams?.exprList ?: emptyList()

        // Check the number of arguments
        if (!definition.variadic && (usedParams.size > definitionParams.size)) {
            holder
                .newAnnotationGd(element.project, HighlightSeverity.ERROR, "Too many arguments")
                .range(element.textRange)
                .create()
            return
        }

        if ((definition.required > 0) && (usedParams.size < definition.required)) {
            holder
                .newAnnotationGd(element.project, HighlightSeverity.ERROR, "Not enough arguments")
                .range(element.textRange)
                .create()
            return
        }

        var expectedType = "Variant"
        var name = ""
        val definedKeys = definitionParams.keys.toTypedArray()

        usedParams.forEachIndexed { index, actualType ->
            if (index < definedKeys.size) {
                expectedType = definitionParams[definedKeys[index]] ?: ""
                name = definedKeys[index]
            }

            val actualReturnType = actualType.returnType
            if (!GdExprUtil.typeAccepts(actualReturnType, expectedType, element)) {
                holder
                    .newAnnotationGd(element.project, HighlightSeverity.ERROR, "")
                    .tooltip("<html><body>Type mismatch for $name<table><tr><td>Required:</td><td>$expectedType</td></tr><tr><td>Found:</td><td>$actualReturnType</td></tr></table></html></body>")
                    .range(actualType.textRange)
                    .create()
            }
        }
    }

}
