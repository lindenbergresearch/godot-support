@file:Suppress("DEPRECATION")

package gdscript.psi.utils

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import gdscript.index.impl.*
import gdscript.psi.*
import gdscript.utils.PsiFileUtil.toAbsoluteResource
import gdscript.utils.VirtualFileUtil.getPsiFile
import gdscript.utils.VirtualFileUtil.resourcePath

/**
 * Utility object for working with Godot class declarations and resolving their corresponding elements
 * within a project. This includes functions to resolve class IDs, determine owning classes, and perform
 * name extraction or transformations.
 */
object GdClassUtil {

    /**
     * Retrieves a `PsiElement` corresponding to a class ID based on the given name, element, and project context.
     *
     * The method attempts the following resolutions in order:
     * 1. Global class ID resolution from the `GdClassIdIndex`.
     * 2. File resolution from the `GdFileResIndex`.
     * 3. Class declarations within the same file from the `GdClassDeclIndex`.
     *
     * @param name The name of the class ID to resolve. May include resource-like paths.
     * @param element The `PsiElement` providing the context for relative path resolution.
     * @param project The project where the resolution is performed.
     * @return The resolved `PsiElement`, or `null` if no matching element is found.
     */
    fun getClassIdElement(name: String, element: PsiElement, project: Project): PsiElement? {
        val path = name.toAbsoluteResource(element, project)

        INSTANCE.getGloballyResolved(path, project).firstOrNull()?.let { return it }
        GdFileResIndex.getFiles(path.trim('"', '\''), project).firstOrNull()?.let { return it.getPsiFile(project) }
        GdClassDeclIndex.INSTANCE.getInFile(name, element, project).firstOrNull()?.let { return it }

        return null
    }


    /**
     * Attempts to retrieve a `PsiElement` corresponding to a class identifier or resource
     * based on a given name and project context. This method is marked as deprecated
     * and is intended for internal usage only, typically invoked after resolving relative paths.
     *
     * @param name the name of the class or resource to resolve. This can be a fully qualified class name
     *             or a resource key that may require conversion and resolution.
     * @param project the IntelliJ project context in which the resolution of the name is performed.
     * @return the resolved `PsiElement`, or `null` if no matching element could be resolved.
     */
    @Deprecated("For internal usage only called after resolving relative paths")
    fun getClassIdElement(name: String, project: Project): PsiElement? {
        return INSTANCE.getGloballyResolved(name, project).firstOrNull()
            ?: GdFileResIndex.getFiles(name.trim('"', '\''), project).firstOrNull()
                ?.let { return it.getPsiFile(project) }
    }


    /**
     * Retrieves a PsiElement corresponding to a given class name. This method is deprecated and should
     * be used with the supplied project instead.
     *
     * @param name The name of the class for which to retrieve the corresponding PsiElement.
     * @param element A PsiElement from which the project will be inferred.
     * @return A PsiElement corresponding to the specified class name, or null if no matching element is found.
     */
    @Deprecated(
        "Use above with supplied project", ReplaceWith(
            "getClassIdElement(name, project)",
            "gdscript.psi.utils.GdClassUtil.getClassIdElement"
        )
    )
    fun getClassIdElement(name: String, element: PsiElement): PsiElement? {
        return getClassIdElement(name, element.project)
    }


    /**
     * Retrieves the name of the class owning the given PsiElement.
     *
     * @param element The PsiElement for which the owning class name is determined.
     * @return The owning class name if it can be resolved; otherwise, the resource path of the file containing the element.
     */
    fun getOwningClassName(element: PsiElement): String {
        return when (val it = getOwningClassElement(element)) {
            is GdClassDeclTl -> it.name

            else -> {
                val cln = PsiTreeUtil.getStubChildOfType(it, GdClassNaming::class.java)
                if (cln != null) return cln.classname

                (element.containingFile.virtualFile ?: element.containingFile.originalFile.virtualFile)
                    .resourcePath()
            }
        }
    }


    /**
     * Retrieves the full class identifier for a given `PsiElement`.
     * This identifier may vary based on the type of `PsiElement`:
     * - If the element is an instance of `GdClassDeclTl`, its `classId` is returned.
     * - If the element is a `GdFile`, the identifier is extracted or derived from file details.
     * - Otherwise, the method recursively attempts to find the owning class element and retrieve its identifier.
     *
     * @param element The `PsiElement` for which the full class identifier is to be determined.
     * @return A `String` representing the full class identifier of the provided element, or an empty string if no identifier is found.
     */
    fun getFullClassId(element: PsiElement): String {
        return when (element) {
            is GdClassDeclTl -> element.classNameNmi?.classId ?: ""

            is GdFile -> {
                val named = PsiTreeUtil.getStubChildOfType(element, GdClassNaming::class.java)
                if (named != null) {
                    named.classNameNmi?.classId ?: ""
                } else {
                    val file = element.virtualFile ?: element.originalFile.virtualFile
                    "\"${file.resourcePath()}\""
                }
            }

            else -> getFullClassId(getOwningClassElement(element))
        }
    }


    /**
     * Determines the owning class element of a given PSI (Program Structure Interface) element.
     *
     * @param element the PSI element for which the owning class element is to be determined
     * @return the owning class element if found, or the containing file of the element if no class element is detected
     */
    fun getOwningClassElement(element: PsiElement): PsiElement {
        when (element) {
            is GdFile -> return element
            is GdClassDeclTl -> return element
        }

        val inner = PsiTreeUtil.getStubOrPsiParentOfType(element, GdClassDeclTl::class.java)
        if (inner != null) return inner

        return element.containingFile
    }


    /**
     * Retrieves the name of the given class declaration element.
     *
     * @param element the `GdClassDeclTl` instance from which the name is retrieved.
     * @return the name associated with the class declaration, or an empty string if the name is not available.
     */
    fun getName(element: GdClassDeclTl): String {
        val stub = element.stub
        if (stub != null) return stub.name()

        return element.classNameNmi?.name.orEmpty()
    }

}
