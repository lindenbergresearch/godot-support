package gdscript.reference

import GdScriptPluginIcons
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.psi.util.PsiTreeUtil
import gdscript.GdKeywords
import gdscript.completion.GdLookup
import gdscript.completion.utils.GdClassCompletionUtil
import gdscript.completion.utils.GdClassCompletionUtil.lookup
import gdscript.completion.utils.GdEnumCompletionUtil.lookup
import gdscript.index.impl.GdClassNamingIndex
import gdscript.psi.*
import gdscript.psi.utils.*
import gdscript.psi.utils.GdClassUtil.getClassIdElement
import project.psi.util.ProjectAutoloadUtil
import kotlin.reflect.KClass

/**
 * A reference implementation that manages the resolution and completion of type hints
 * in a custom scripting language supported by the IntelliJ Platform.
 *
 * This class extends `PsiReferenceBase` and provides custom behavior to handle the resolution
 * and completion of type hint elements, ensuring proper linking and code insights in
 * the editor for the associated type hints.
 */
class GdTypeHintReference : PsiReferenceBase<GdTypeHintRef> {

    /**
     * Holds the key used for resolving or identifying elements within the context
     * of Godot's script resource references or operations. Typically, it represents paths
     * or identifiers related to resources within the project.
     */
    private var key: String = ""


    /**
     * Represents the currently associated IntelliJ project.
     * This variable is used to perform various operations or lookups within the context of the associated project.
     *
     * Belongs to the GdTypeHintReference class and is used in resolving or manipulating resource references
     * specific to the project.
     */
    private var project: Project


    /**
     * Constructor for `GdTypeHintReference`.
     * Initializes the object with the provided `GdTypeHintRef` element, setting up the text range
     * and extracting necessary reference information.
     *
     * @param element The `GdTypeHintRef` element to reference.
     */
    constructor(element: GdTypeHintRef) : super(element, TextRange(0, element.textLength)) {
        key = element.parent.text.substring(0, element.textRangeInParent.endOffset)
        this.project = element.project
    }


    /**
     * Handles renaming of the element by replacing it with a new type hint reference using the provided new element name.
     *
     * @param newElementName The new name for the element being renamed.
     * @return The PsiElement that replaces the original element with the new name.
     */
    override fun handleElementRename(newElementName: String): PsiElement {
        return element.replace(GdElementFactory.typeHintRef(element.project, newElementName))
    }


    /**
     * Resolves the PSI (Program Structure Interface) element associated with this reference.
     *
     * This method uses a caching mechanism to optimize the resolution process, delegating to
     * internal utilities and indexes for precise resolution. The resolution process may involve
     * multiple steps such as accessing class naming indexes, resolving to own class containers,
     * and searching for associated elements through various scopes and contexts.
     *
     * @return the resolved `PsiElement`, or `null` if no element can be resolved
     */
    override fun resolve(): PsiElement? {
        if (DumbService.isDumb(element.project)) return null
        
        val cache = ResolveCache.getInstance(project)
        return cache.resolveWithCaching(
            this,
            ResolveCache.Resolver { _, _ ->
                val classId = GdClassNamingIndex.INSTANCE.getGlobally(key, project).firstOrNull()?.classNameNmi
                classId?.let { return@Resolver it }

                val container = if (key.contains(".")) {
                    val ownerClassId = getOwnerClass() ?: return@Resolver null
                    GdClassUtil.getOwningClassElement(ownerClassId)
                } else {
                    GdClassUtil.getOwningClassElement(element)
                }

                resolveInner(container)?.let { return@Resolver it }
                (container as? GdClassDeclTl)?.let { it1 -> resolveInner(it1.parent)?.let { return@Resolver it } }

                getClassIdElement(GdKeywords.GLOBAL_SCOPE, project)?.let {
                    return@Resolver resolveInner(GdClassUtil.getOwningClassElement(it))
                }
            },
            false,
            false,
        )
    }


    /**
     * Provides completion variants based on the current context and scope.
     *
     * This method constructs an array of `LookupElement` objects representing the
     * possible completions or candidates for the given PSI element reference. It
     * considers global classes, local context such as methods and suites, preloaded
     * or globally accessible objects, and other contextual data.
     *
     * @return An array of `LookupElement` objects representing the completion variants
     *         for the current reference. Returns an empty array if no variants are found.
     */
    override fun getVariants(): Array<LookupElement> {
        val variants = mutableListOf<LookupElement>()
        val container: PsiElement?

        if (key.contains(".")) {
            val classId = getOwnerClass() ?: return emptyArray()
            container = GdClassUtil.getOwningClassElement(classId)
        } else {
            container = GdClassUtil.getOwningClassElement(element)
            variants.addAll(GdClassCompletionUtil.allRootClasses(project))
            PsiTreeUtil.getParentOfType(element, GdMethodDeclTl::class.java)?.let { methodDecl ->
                PsiTreeUtil.findChildOfType(methodDecl, GdSuite::class.java)?.let { suite ->
                    variants.addAll(loadedClasses(suite).map {
                        GdLookup.create(
                            GdCommonUtil.getName(it),
                            priority = GdLookup.LOCAL_USER_DEFINED,
                            icon = GdScriptPluginIcons.GDScriptIcons.OBJECT,
                        )
                    })
                }
            }
            ProjectAutoloadUtil.listGlobals(project).forEach {
                variants.add(
                    GdLookup.create(
                        it.key,
                        priority = GdLookup.USER_DEFINED,
                        icon = GdScriptPluginIcons.GDScriptIcons.OBJECT,
                    )
                )
            }
        }

        variantsInner(container, variants)
        (container as? GdClassDeclTl)?.let { variantsInner(it.parent, variants) }

        return variants.toTypedArray()
    }


    /**
     * Resolves a PsiElement from the specified container by iterating through potential matches
     * such as enums, inner classes, or loaded classes. The method attempts resolution based on
     * the name of the current element.
     *
     * @param container The PsiElement container in which resolution is to be performed.
     *                  This is used to traverse elements like enums, inner classes, and
     *                  other potentially relevant nodes for resolution.
     * @return The resolved PsiElement if a matching element is found; null otherwise.
     */
    private fun resolveInner(container: PsiElement): PsiElement? {
        val myName = element.text
        enums(container).forEach {
            if (it.name == myName) return it.enumDeclNmi
        }
        innerClasses(container).forEach {
            if (it.name == myName) return it.classNameNmi
        }
        loadedClasses(container).forEach {
            if (GdCommonUtil.getName(it) == myName) return GdClassMemberReference.resolveId(it)
        }
        PsiTreeUtil.getParentOfType(element, GdMethodDeclTl::class.java)?.let { methodDecl ->
            PsiTreeUtil.findChildOfType(methodDecl, GdSuite::class.java)?.let { suite ->
                loadedClasses(suite).forEach {
                    if (GdCommonUtil.getName(it) == myName) return GdClassMemberReference.resolveId(it)
                }
            }
        }

        return null
    }


    /**
     * Populates the provided list of lookup elements with variants derived from inner classes,
     * enums, and loaded classes within the specified container.
     *
     * @param container The PsiElement representing the container in which the lookup variants
     *                  are to be resolved. Expected to contain inner classes, enums, or loaded
     *                  classes.
     * @param variants The mutable list of LookupElement objects to which the resolved variants
     *                 will be added. This method appends new entries to the provided list.
     */
    private fun variantsInner(container: PsiElement, variants: MutableList<LookupElement>) {
        variants.addAll(innerClasses(container).map { it.lookup() })
        variants.addAll(enums(container).mapNotNull { it.lookup() })
        variants.addAll(loadedClasses(container).map {
            GdLookup.create(
                GdCommonUtil.getName(it),
                priority = GdLookup.USER_DEFINED,
                icon = GdScriptPluginIcons.GDScriptIcons.OBJECT,
            )
        })
    }


    /**
     * Attempts to determine the owning class for the current element based on its context,
     * resolved identifiers, and project-wide alias definitions.
     *
     * The method uses a series of resolution steps:
     * 1. Extracts a part of the qualified key, attempting to resolve it as a global class identifier.
     * 2. Combines the qualified key with the full class ID of the current element for further resolution.
     * 3. Searches for the class in project-wide alias mappings.
     *
     * If none of the above resolutions succeed, the method returns null.
     *
     * @return The owning class as a `PsiElement`, or `null` if the owning class cannot be resolved.
     */
    private fun getOwnerClass(): PsiElement? {
        val withoutLast = key.substring(0, key.lastIndexOf("."))
        val global = getClassIdElement(withoutLast, project)
        global?.let { return it }

        val myId = GdClassUtil.getFullClassId(element)
        getClassIdElement("$myId.$withoutLast", project)?.let { return it }

        ProjectAutoloadUtil.findFromAlias(withoutLast, element)?.let { return it }

        return null
    }


    /**
     * Retrieves a list of enum declarations within the specified owner class.
     *
     * @param ownerClass the PSI element representing the owner class from which
     *                   to retrieve the enum declarations.
     * @return a list of `GdEnumDeclTl` representing the enum declarations found within the owner class.
     */
    private fun enums(ownerClass: PsiElement): List<GdEnumDeclTl> {
        return findRecursiveOfType(ownerClass, GdEnumDeclTl::class)
    }


    /**
     * Retrieves a list of inner class declarations for the given owner class.
     *
     * @param ownerClass the PSI element representing the owner class for which
     *                   the inner class declarations are to be found.
     * @return a list of inner class declarations of type GdClassDeclTl
     *         within the specified owner class.
     */
    private fun innerClasses(ownerClass: PsiElement): List<GdClassDeclTl> {
        return findRecursiveOfType(ownerClass, GdClassDeclTl::class)
    }


    /**
     * Retrieves a list of PsiElement instances representing class or constant variable declarations
     * within the provided owner element that call specific functions, such as `preload` or `load`.
     *
     * @param ownerElement The PsiElement within which to search for class or constant variable declarations.
     * @return A list of PsiElement instances that meet the search criteria.
     */
    private fun loadedClasses(ownerElement: PsiElement): List<PsiElement> {
        val list = mutableListOf<PsiElement>()

        PsiTreeUtil.getChildrenOfAnyType(
            ownerElement,
            GdClassVarDeclTl::class.java,
            GdConstDeclTl::class.java,
            GdVarDeclSt::class.java,
            GdConstDeclSt::class.java,
        ).forEach {
            val expr = when (it) {
                is GdClassVarDeclTl -> it.expr
                is GdConstDeclTl -> it.expr
                is GdVarDeclSt -> it.expr
                is GdConstDeclSt -> it.expr
                else -> return list
            }
            if ((expr is GdCallEx) && arrayOf("preload", "load").contains(expr.expr.text)) {
                list.add(it)
            }
        }

        return list
    }


    /**
     * Recursively searches for elements of a specific type starting from the given owner class.
     *
     * @param ownerClass the PSI element from which to start the search.
     * @param kClass the KClass instance representing the type of elements to search for.
     * @return a list of elements of the specified type found during the recursive search.
     */
    private fun <T : PsiElement> findRecursiveOfType(ownerClass: PsiElement, kClass: KClass<T>): List<T> {
        val results = mutableListOf<T>()
        var par: PsiElement? = ownerClass
        while (par != null) {
            results.addAll(PsiTreeUtil.getStubChildrenOfTypeAsList(par, kClass.java))
            par = GdInheritanceUtil.getExtendedElement(par, project)
        }
        return results
    }
}
