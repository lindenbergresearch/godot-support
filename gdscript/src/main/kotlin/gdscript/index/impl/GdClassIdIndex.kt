package gdscript.index.impl

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.psi.util.PsiTreeUtil
import common.index.StringStubIndexExtensionExt
import gdscript.index.Indices
import gdscript.psi.GdClassNameNmi
import gdscript.psi.GdClassNaming
import gdscript.utils.VirtualFileUtil.getPsiFile
import gdscript.utils.VirtualFileUtil.resourcePath

/**
 * The GdClassIdIndex class is responsible for providing an extension of the StringStubIndexExtensionExt
 * for managing and querying GdClassNameNmi elements based on their string identifiers. This class provides
 * mechanisms for globally resolving class identifiers into their corresponding elements and includes
 * custom logic for handling class name to resource path conversions and vice versa.
 */
class GdClassIdIndex : StringStubIndexExtensionExt<GdClassNameNmi>() {

    /**
     * Returns the key associated with the StubIndex for identifying and retrieving
     * stub data related to class names within the index.
     *
     * @return the key used to uniquely identify the StubIndex for class names.
     */
    override fun getKey(): StubIndexKey<String, GdClassNameNmi> = Indices.CLASS_NAME_ID

    /**
     * Retrieves the version of the index.
     *
     * @return the version number as an integer
     */
    override fun getVersion(): Int = Indices.VERSION

    /**
     * Resolves a globally qualified name to a collection of relevant class declarations,
     * potentially handling both resource paths and class names.
     *
     * @param name the globally qualified name to resolve; can represent a resource path
     *             (e.g., enclosed in quotes) or a class name.
     * @param project the current IntelliJ IDEA project context within which the resolution is performed.
     * @return a collection of resolved `GdClassNameNmi` instances corresponding to the
     *         specified globally qualified name, or an empty collection if no resolution is possible.
     */
    fun getGloballyResolved(name: String, project: Project): Collection<GdClassNameNmi> {
        if (DumbService.isDumb(project)) return emptyList()
        val fqn = get(name, project, GlobalSearchScope.allScope(project))
        if (fqn.isNotEmpty()) return fqn

        var modified: String
        if (name.startsWith('"')) {
            // Try resource to class_name
            val endIndex = name.indexOf('"', 1)
            val resource = name.substring(1, endIndex)
            val resourceFile = GdFileResIndex.getFiles(resource, project).firstOrNull() ?: return emptyList()
            val psiFile = resourceFile.getPsiFile(project)
            modified = PsiTreeUtil.getStubChildOfType(psiFile, GdClassNaming::class.java)?.classname.orEmpty()

            if (name.length > endIndex + 1) {
                modified = "$modified${name.substring(endIndex + 1)}"
            }
        } else {
            // Try class_name to resource
            val classes = name.split('.').toMutableList()
            val rootClass = classes.firstOrNull() ?: return emptyList()
            val cln = GdClassNamingIndex.INSTANCE.getGlobally(rootClass, project).firstOrNull() ?: return emptyList()

            val resource = cln.containingFile.virtualFile.resourcePath()
            classes[0] = "\"$resource\""

            modified = classes.joinToString(".")
        }

        return get(modified, project, GlobalSearchScope.allScope(project))
    }

}

/**
 * The global instance of the `GdClassIdIndex` to facilitate access and operations
 * related to class identifier indexing in the context of GDScript projects.
 *
 * Provides functionality for looking up class names by identifier, using the stub index extension mechanism
 * to enable efficient indexing and retrieval of class-related data on a global scale.
 */
val INSTANCE = GdClassIdIndex()