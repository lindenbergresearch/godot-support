package gdscript.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import gdscript.GdKeywords
import gdscript.GdKeywords.PATH_SEPARATOR
import java.io.File
import kotlin.io.path.pathString
import kotlin.io.path.relativeToOrNull

/**
 * Utility object containing extension functions and helper methods associated with virtual file operations.
 */
object VirtualFileUtil {

    /**
     * Retrieves the local file system path of the virtual file relative to the main project base path.
     * If the virtual file is not associated with any project or the main project base path isn't available,
     * an empty string is returned.
     *
     * @return The relative path of the virtual file as a string, or an empty string if the file cannot be resolved.
     */
    fun VirtualFile.localPath(): String {
        val project = ProjectLocator.getInstance().guessProjectForFile(this) ?: return ""
        val basePath = project.getMainProjectBasePath() ?: return ""
        return fileSystem.getNioPath(this)?.relativeToOrNull(basePath)?.pathString ?: return ""
        // TODO this causes issues - I can't query the Fileindex when indexing... but how to have multiple projects?
        // val projectRoot = ProjectRootFileIndex.getProjectRoot(path, project)
    }


    /**
     * Retrieves the parent path of the virtual file relative to the current project.
     *
     * The returned value is constructed as "project_name/local_path", where "local_path" is
     * derived from the `localPath` method of the virtual file. The method trims the last segment
     * from the constructed path to retrieve its parent path.
     *
     * @return the parent path as a string. Returns an empty string if the file's project cannot be determined.
     */
    fun VirtualFile.localParentPath(): String {
        val project = ProjectLocator.getInstance().guessProjectForFile(this) ?: return ""
        val path = "${project.name}/${localPath()}"

        return path.substringBeforeLast(PATH_SEPARATOR)
    }


    /**
     * Retrieves the resource path of the current `VirtualFile`.
     *
     * @param withPrefix Determines whether the path should include the "res://" prefix. Defaults to `true`.
     * @return The resource path of the `VirtualFile` as a string. If `withPrefix` is `true`, the path will be prefixed with "res://".
     */
    fun VirtualFile.resourcePath(withPrefix: Boolean = true): String {
        return getResourcePath(this, withPrefix)
    }


    /**
     * Retrieves the `PsiFile` associated with the specified `VirtualFile` and the given `PsiElement`.
     *
     * @param element the `PsiElement` used to determine the `Project` context for extracting the `PsiFile`.
     * @return the corresponding `PsiFile` if found, or `null` if no associated file exists.
     */
    fun VirtualFile.getPsiFile(element: PsiElement): PsiFile? {
        return this.getPsiFile(element.project)
    }


    /**
     * Retrieves the `PsiFile` associated with the specified `VirtualFile` in the context of a given `Project`.
     *
     * @param project The current IntelliJ project that contains the file.
     * @return The `PsiFile` corresponding to the specified `VirtualFile`, or `null` if the file cannot be found.
     */
    fun VirtualFile.getPsiFile(project: Project): PsiFile? {
        return PsiManager.getInstance(project).findFile(this)
    }


    /**
     * Constructs a resource path from a given virtual file and an optional prefix.
     *
     * @param file the virtual file for which the resource path is to be constructed
     * @param withPrefix if true, adds a "res://" prefix to the generated resource path
     * @return the constructed resource path as a string
     */
    private fun getResourcePath(file: VirtualFile, withPrefix: Boolean): String {
        return "${if (withPrefix) GdKeywords.RESOURCE_PREFIX else ""}${file.localPath().trimStart(File.separatorChar).split(File.separatorChar).joinToString(PATH_SEPARATOR)}"
    }

}
