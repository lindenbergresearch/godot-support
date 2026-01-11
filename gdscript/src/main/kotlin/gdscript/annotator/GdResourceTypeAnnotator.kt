package gdscript.annotator

import com.intellij.lang.annotation.*
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import gdscript.GdKeywords
import gdscript.highlighter.GdHighlighterColors
import gdscript.index.impl.GdFileResIndex
import gdscript.psi.GdNodePath
import gdscript.psi.GdTypes
import gdscript.psi.utils.GdNodeUtil
import gdscript.settings.GdProjectSettingsState
import gdscript.settings.GdProjectState

/**
 * A regex pattern used to match alternate resource patterns in strings.
 *
 * The pattern captures two types of placeholders:
 *
 * 1. Format specifiers resembling those used in printf-style formatted strings,
 *    such as `%s`, `%d`, `%f`, etc., with optional width, precision, and flags.
 *    These format specifiers exclude escaped `%` symbols (e.g., `%%`).
 * 2. Placeholder expressions enclosed in curly braces, such as `{variableName}`.
 *    These are often used to denote named placeholders or variables in a template.
 */
private val ALTERNATE_RES_PATTERN: Regex
    get() = "((?<!%)%[scdoxXf0-9+.*\\-]+)|(\\{[a-zA-Z0-9]*})".toRegex()


/**
 * Regex pattern used to match resource paths in the form `res://`.
 *
 * This pattern is designed to validate the structure of resource strings
 * typically found in Godot projects. It ensures the resource path starts
 * with `res://`, followed by a valid combination of characters, and ends
 * with a file extension between 3 and 5 characters in length.
 *
 * The resource path can include alphanumeric characters, underscores,
 * hyphens, dots, and forward slashes for directory structures.
 */
private val RES_PATTERN = """res://[A-Za-z0-9_\-./]+\.[A-Za-z0-9]{3,5}""".toRegex()


/**
 * A custom annotator for handling specific `GdResource`-related elements in the code.
 * Provides functionality for annotating and validating node paths and resource strings.
 */
class GdResourceTypeAnnotator : Annotator {
    /**
     * Annotates the given PSI element based on its type and project settings.
     * Handles annotation for specific types like GdNodePath and string literals,
     * applying validations and highlighting where necessary.
     *
     * @param element the PSI element to annotate
     * @param holder the annotation holder used to create and manage annotations
     */
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Skip annotation if indices are not ready
        if (DumbService.isDumb(element.project)) {
            return
        }

        val state = GdProjectSettingsState.getInstance(element).state.annotators

        if (element is GdNodePath) {
            if (state != GdProjectState.DISABLE) resourceExists(element, holder, state)
        } else if (element.elementType == GdTypes.STRING) {
            if (state != GdProjectState.DISABLE)
                if (!stringResourceExists(element, holder, state)) return
            stringFormats(element, holder)
        }
    }


    /**
     * Checks if a given string resource exists in the project context and provides an annotation if it does not.
     *
     * The method validates whether the text content of a `PsiElement` begins with a specific resource prefix and attempts
     * to locate corresponding files for the provided resource key. If no files are found, it creates an annotation using
     * the specified state severity. If the resource exists, no annotation is created.
     *
     * @param element the `PsiElement` containing the potential resource key to validate.
     * @param holder the `AnnotationHolder` used to create annotations for missing resources.
     * @param state a string representing the severity state, used to determine the level of annotation.
     * @return `true` if the resource exists, otherwise `false`.
     */
    private fun stringResourceExists(element: PsiElement, holder: AnnotationHolder, state: String): Boolean {
        val text = element.text.trim('"', '\'')
        if (text.startsWith(GdKeywords.RESOURCE_PREFIX) && GdFileResIndex.getFiles(text, element.project).isEmpty()) {
            holder.run {
                newAnnotationGd(element.project, GdProjectState.selectedLevel(state), "Resource: '$text' not found")
                    .range(TextRange.create(element.textRange.startOffset + 1, element.textRange.endOffset - 1))
                    .create()
            }
            return false
        }

        return true
    }


    /**
     * Inspects if a resource node exists for the given `GdNodePath` element and creates an annotation if it does not.
     *
     * @param element the `GdNodePath` element representing the node path.
     * @param holder the `AnnotationHolder` used to register annotations.
     * @param state the current state determining the severity level of the annotation.
     */
    private fun resourceExists(element: GdNodePath, holder: AnnotationHolder, state: String) {
        if (element.text == "$\".\"") return
        val node = GdNodeUtil.findNode(element)
        if (node != null) return

        holder
            .newAnnotationGd(element.project, GdProjectState.selectedLevel(state), "Node: '${node.elementType}' not found")
            .range(element.textRange)
            .create()
    }


    /**
     * Highlights specific patterns within a given PsiElement and adds annotations to the AnnotationHolder.
     *
     * @param element the PsiElement in which the patterns will be searched and highlighted
     * @param holder the AnnotationHolder used to store the generated annotations
     */
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
