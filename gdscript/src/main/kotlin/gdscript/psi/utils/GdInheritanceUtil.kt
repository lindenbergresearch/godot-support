package gdscript.psi.utils

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import gdscript.index.impl.GdFileResIndex
import gdscript.index.impl.INSTANCE
import gdscript.psi.*
import gdscript.utils.VirtualFileUtil.getPsiFile

/**
 * Utility object for working with GDScript class inheritance in a PSI (Program Structure Interface) context.
 * It provides methods to analyze, resolve, and interact with inheritance-related features of GDScript classes.
 */
object GdInheritanceUtil {

    /**
     * Retrieves the extended class ID for a given PSI element.
     *
     * @param element The PSI element for which the extended class ID is to be determined.
     * This can be an instance of GdClassNaming, GdClassDeclTl, GdFile, PsiFile, or any other related PSI element.
     * @return A string representing the extended class ID. Returns the parent name for `GdClassNaming`
     * and `GdClassDeclTl`, the inheritance path for `GdFile`, an empty string for `PsiFile`,
     * or recursively resolves the parent class element in other cases.
     */
    fun getExtendedClassId(element: PsiElement): String {
        return when (element) {
            is GdClassNaming -> element.parentName
            is GdClassDeclTl -> element.parentName
            is GdFile -> PsiTreeUtil.getStubChildOfType(element, GdInheritance::class.java)?.inheritancePath.orEmpty()
            is PsiFile -> ""
            else -> getExtendedClassId(PsiGdClassUtil.getParentClassElement(element))
        }
    }


    /**
     * Retrieves the extended element based on the provided `element`.
     * This is a deprecated method, and it's recommended to use `getExtendedElement(element, project)`
     * for improved performance and better handling of project references.
     *
     * @param element The PSI element for which the extended element is to be determined.
     * @return The extended PSI element, or `null` if no extended element is found.
     */
    @Deprecated(
        "Switch to getExtendedElement(element, project) to promote efficient project reference usage",
        ReplaceWith("getExtendedElement(element, project)", "gdscript.psi.utils.GdInheritanceUtil.getExtendedElement")
    )
    fun getExtendedElement(element: PsiElement): PsiElement? {
        return getExtendedElement(
            getExtendedClassId(element),
            element,
            element.project,
        )
    }


    /**
     * Retrieves the extended element for a given PSI element within the context of a specified project.
     *
     * @param element The PSI element for which the extended element needs to be determined.
     * @param project The project within which the PSI element resides.
     * @return The extended PSI element if found, otherwise null.
     */
    fun getExtendedElement(element: PsiElement, project: Project): PsiElement? {
        return getExtendedElement(getExtendedClassId(element), element, project)
    }


    /**
     * Checks if the given PsiElement extends or matches a class with the specified name.
     *
     * @param element The PsiElement to check for inheritance or matching class.
     * @param className The name of the class to check against.
     * @return True if the element extends or matches the specified class; false otherwise.
     */
    fun isExtending(element: PsiElement, className: String): Boolean {
        if (GdClassUtil.getOwningClassName(element) == className) return true

        var parentId = getExtendedClassId(element)
        while (parentId.isNotBlank()) {
            if (parentId == className) return true
            val parent = INSTANCE.getGlobally(parentId, element).firstOrNull() ?: return false
            parentId = getExtendedClassId(parent)
        }

        return false
    }


    /**
     * Resolves and retrieves an extended `PsiElement` for the specified class ID.
     *
     * This method attempts to locate a `PsiElement` corresponding to the given class ID in the specified
     * project and context. It performs the following steps in order:
     * 1. Checks for a class ID element using `GdClassUtil.getClassIdElement`. If found, it retrieves its parent
     *    `GdClassDeclTl` if applicable or falls back to its containing file.
     * 2. If no class ID element is found, it attempts to locate the element by resolving the resource path
     *    using `GdFileResIndex`.
     *
     * @param classId The unique identifier of the class to be extended. May include resource paths.
     * @param element The `PsiElement` providing the context for resolving the class ID.
     * @param project The IntelliJ IDEA project context in which the resolution is performed.
     * @return The resolved `PsiElement`, which could be a class declaration or the containing file,
     *         or `null` if the resolution fails.
     */
    fun getExtendedElement(classId: String, element: PsiElement, project: Project): PsiElement? {
        val classEl = GdClassUtil.getClassIdElement(classId, element, project)
        // Extending directly named class (includes "res://Item.gd".InnerClass)
        classEl?.let { return (it.parent as? GdClassDeclTl) ?: it.containingFile }

        // In the case of unnamed "res://Item.gd" check for the resource itself
        val file = GdFileResIndex.getFiles(classId.trim('"', '\''), project).firstOrNull() ?: return null

        return file.getPsiFile(project)
    }

}
