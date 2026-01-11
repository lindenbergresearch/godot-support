package gdscript.index.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import gdscript.GdKeywords
import gdscript.index.impl.utils.GdFileResInputFilter


/**
 * Provides utilities for accessing and managing file resources within a project, particularly
 * those adhering to a specific resource path prefix.
 */
class GdFileResIndex {
    /**
     * Companion object containing utility methods for working with resource files
     * in the context of a given project or PSI element.
     */
    companion object {
        /**
         * Retrieves a collection of virtual files based on a given key and project context.
         *
         * The key is processed to compute a relative path, which is then resolved against the
         * content roots of the provided project. The first matching file is returned as a
         * single-element collection or an empty collection if no match is found.
         *
         * @param key the resource key, typically prefixed with the value of `GdKeywords.RESOURCE_PREFIX`.
         * @param project the IntelliJ IDEA project context in which the file search is performed.
         * @return a collection containing the resolved virtual file if found, or an empty collection if no resolution is possible.
         */
        fun getFiles(key: String, project: Project): Collection<VirtualFile> {
            val relPath = key.removePrefix(GdKeywords.RESOURCE_PREFIX)
            val contentRoots = ProjectRootManager.getInstance(project).contentRoots
            for (root in contentRoots) {
                val file = root.findFileByRelativePath(relPath)
                file?.let { return listOf(it) }
            }

            return emptyList()
        }


        /**
         * Retrieves a collection of files matching the specified resource key within the context of
         * the provided PSI element. This function uses the project's content roots and
         * resolves the relative path derived from the key.
         *
         * @param key the resource key, typically prefixed with a specific identifier, used to locate files.
         * @param element the PSI element context to derive the project and perform the file search.
         * @return a collection of `VirtualFile` instances matching the resource key, or an empty collection if none are found.
         */
        fun getFiles(key: String, element: PsiElement): Collection<VirtualFile> {
            return getFiles(key, element.project)
        }


        /**
         * Retrieves a list of non-empty keys associated with a given `PsiElement`.
         *
         * @param element the `PsiElement` for which to retrieve the non-empty keys.
         * @return a list of non-empty keys as strings.
         */
        fun getNonEmptyKeys(element: PsiElement): List<String> {
            return getNonEmptyKeys(element.project)
        }


        /**
         * Retrieves a list of non-empty resource keys for the given project by analyzing
         * the project's file structure and filtering out irrelevant or empty resources.
         *
         * @param project the IntelliJ IDEA project from which resource keys are extracted.
         * @return a list of non-empty string keys representing resources in the project.
         */
        fun getNonEmptyKeys(project: Project): List<String> {
            val fileIndex = ProjectFileIndex.getInstance(project)
            val results = mutableListOf<String>()
            fileIndex.iterateContent(
                { file ->
                    val res = fileIndex.getContentRootForFile(file)?.let { root ->
                        VfsUtilCore.getRelativePath(file, root)?.let { path ->
                            "${GdKeywords.RESOURCE_PREFIX}$path"
                        }
                    }
                    res?.let { results.add(it) }
                    true
                }, fun(it: VirtualFile): Boolean {
                    return GdFileResInputFilter.accept(it)
                }
            )

            return results
        }
    }
}
