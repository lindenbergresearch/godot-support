package gdscript.reference

import GdScriptPluginIcons
import com.intellij.codeInsight.highlighting.HighlightedReference
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.childrenOfType
import gdscript.completion.GdLookup
import gdscript.completion.utils.GdCompletionUtil
import gdscript.index.impl.GdClassNamingIndex
import gdscript.psi.*
import gdscript.psi.utils.GdClassMemberUtil
import gdscript.psi.utils.GdClassUtil
import gdscript.utils.PsiElementUtil.psi

/**
 * Represents a reference to a class member in a GdScript-like environment. This class is designed to handle
 * functionalities such as element renaming, resolution of declarations, autocompletion suggestions, and other
 * reference-related operations.
 */
class GdClassMemberReference : PsiReferenceBase<GdRefIdRef>, HighlightedReference {
    /**
     * Companion object containing utility functions for resolving specific elements.
     */
    companion object {

        /**
         * Resolves the identifier of a given PsiElement based on its type.
         *
         * @param element the PsiElement whose identifier is to be resolved; may be null.
         * @return the resolved identifier as a PsiElement if applicable, or null if resolution is not possible.
         */
        fun resolveId(element: PsiElement?): PsiElement? {
            return when (element) {
                is GdClassVarDeclTl -> element.varNmi
                is GdClassDeclTl -> element.classNameNmi
                is GdConstDeclTl -> element.varNmi
                is GdVarDeclSt -> element.varNmi
                is GdConstDeclSt -> element.varNmi
                is GdEnumDeclTl -> element.enumDeclNmi
                is GdEnumValue -> element.enumValueNmi
                is GdMethodDeclTl -> element.methodIdNmi
                is GdSignalDeclTl -> element.signalIdNmi
                is GdForSt -> element.varNmi
                is GdParam -> element.varNmi
                is GdVarNmi -> element
                is GdBindingPattern -> element.varNmi
                is PsiFile -> element
                is GdClassNaming -> element.classNameNmi
                else -> null
            }
        }
    }


    /**
     * Constructs a GdClassMemberReference instance by initializing the reference with the given element.
     *
     * @param element The element on which the reference is based.
     */
    constructor(element: GdRefIdRef) : super(element, TextRange(0, element.textLength))


    /**
     * Handles renaming of the underlying element by substituting it with a new reference
     * created from the provided new element name.
     *
     * @param newElementName The new name to be applied to the element.
     * @return The updated PsiElement after performing the rename operation.
     */
    override fun handleElementRename(newElementName: String): PsiElement {
        return myElement.replace(GdElementFactory.refIdNm(myElement.project, newElementName))
    }


    /**
     * Resolves the declaration associated with the current element.
     *
     * This method attempts to resolve the current element's declaration by analyzing
     * its context, including qualifiers, enum declarations, or class members. It handles
     * namespaced elements, static access, and cases of both named and anonymous enums.
     * In "dumb mode" where indexing is not complete, resolution is skipped.
     *
     * @return The resolved [PsiElement], if found; otherwise, null.
     */
    fun resolveDeclaration(): PsiElement? {
        if (DumbService.isDumb(element.project)) return null

        val cache = ResolveCache.getInstance(element.project)
        val resolved = cache.resolveWithCaching(
            this,
            ResolveCache.Resolver { _, _ ->
                val qualifierExpr = GdClassMemberUtil.calledUpon(element)

                // Anonymous/Named Enum member access
                // e.g., Animation.TYPE_AUDIO or _Anim.FLOOR
                if (qualifierExpr != null) {

                    // Find what the qualifier refers to (could be a class or enum)
                    // Handle both named enums and class with anonymous enums
                    val containerElement: PsiElement? = when (val qualifierDecl = GdClassMemberUtil.findDeclaration(qualifierExpr)) {
                        is GdEnumDeclTl -> qualifierDecl  // Direct enum reference
                        is GdClassDeclTl -> qualifierDecl // Class might have anonymous enums
                        is GdClassNaming -> {
                            // For GdClassNaming, we need to get the actual class declaration
                            val owningClass = GdClassUtil.getOwningClassElement(qualifierDecl)
                            // If getOwningClassElement returns the GdClassNaming itself or a file,
                            // then search enums in it directly
                            (owningClass as? GdClassDeclTl) ?: qualifierDecl  // Use the GdClassNaming directly if no parent class
                        }

                        is PsiFile -> qualifierDecl  // File might have enums
                        else -> null
                    }

                    if (containerElement != null) {
                        val enumValueName = element.text

                        // If it's already an enum, search directly
                        if (containerElement is GdEnumDeclTl) {
                            val enumValue = containerElement.enumValueList.firstOrNull { enumVal ->
                                enumVal.enumValueNmi.text == enumValueName
                            }
                            enumValue?.let { return@Resolver it }
                        }

                        // If it's a class declaration, search ALL enums (anonymous and named)
                        if (containerElement is GdClassDeclTl) {
                            val allEnums = containerElement.childrenOfType<GdEnumDeclTl>()
                            for (enumDecl in allEnums) {
                                val enumValue = enumDecl.enumValueList.firstOrNull { enumVal ->
                                    enumVal.enumValueNmi.text == enumValueName
                                }
                                if (enumValue != null) {
                                    return@Resolver enumValue
                                }
                            }
                        }

                        // If it's a GdClassNaming, we need to search enums there too
                        if (containerElement is GdClassNaming) {
                            val allEnums = GdClassMemberUtil
                                .listClassMemberDeclarations(containerElement, static = true)
                                .filterIsInstance<GdEnumDeclTl>()

                            for (enumDecl in allEnums) {
                                val enumValue = enumDecl.enumValueList.firstOrNull { enumVal ->
                                    enumVal.enumValueNmi.text == enumValueName
                                }

                                enumValue?.let { return@Resolver it }
                            }
                        }

                        // If it's a file, search enums there
                        if (containerElement is PsiFile) {
                            val allEnums = containerElement.childrenOfType<GdEnumDeclTl>()
                            for (enumDecl in allEnums) {
                                val enumValue = enumDecl.enumValueList.firstOrNull { enumVal ->
                                    enumVal.enumValueNmi.text == enumValueName
                                }
                                if (enumValue != null) {
                                    return@Resolver enumValue
                                }
                            }
                        }
                    }
                }

                // Helper to detect if a string like "A.B.C" refers to a (possibly nested) class available in the current file/scope
                val resolvesToClassChain = fun(name: String): Boolean {
                    if (name.isEmpty()) {
                        return false
                    }

                    if (GdClassUtil.getClassIdElement(name, element, element.project) != null) {
                        return true
                    }

                    val parts = name.split('.')
                    if (parts.isEmpty()) {
                        return false
                    }

                    var parent: PsiElement = GdClassUtil.getOwningClassElement(element)

                    // Start from the file scope
                    parent = (parent as? GdFile) ?: element.containingFile
                    var current = PsiTreeUtil
                        .getStubChildrenOfTypeAsList(parent, GdClassDeclTl::class.java)
                        .firstOrNull { it.name == parts[0] }

                    var i = 1
                    while ((current != null) && (i < parts.size)) {
                        current = PsiTreeUtil
                            .getStubChildrenOfTypeAsList(current, GdClassDeclTl::class.java)
                            .firstOrNull { it.name == parts[i] }
                        i++
                    }

                    return (current != null) && (i == parts.size)
                }

                val targetClassDecl = qualifierExpr?.let { expr ->
                    val type = expr.returnType
                    if (type.isNotEmpty()) {
                        val target = GdClassUtil.getClassIdElement(type, element, element.project)
                        target?.let { GdClassUtil.getOwningClassElement(it) as? GdClassDeclTl }
                    } else null
                }

                // Determine if the access is static (on a class) based on the qualifier's declaration
                var isStaticAccess: Boolean? = null
                run {
                    val allRefs = PsiTreeUtil.getChildrenOfType(qualifierExpr, GdRefIdRef::class.java)
                    val leftRef = allRefs?.firstOrNull { it.text != element.text } ?: allRefs?.firstOrNull()
                    val decl = leftRef?.let { GdClassMemberUtil.findDeclaration(it)?.psi() }
                    isStaticAccess = inferStaticAccessFromDecl(decl)
                }

                // If qualifier is a simple identifier bound to a class-typed variable (initializer is a class id without .new/.instance),
                // disallow resolving any non-constructor members on it.
                run {
                    if (qualifierExpr != null) {
                        val refs = PsiTreeUtil.getChildrenOfType(qualifierExpr, GdRefIdRef::class.java)
                        val singleRef = refs?.singleOrNull()
                        if (singleRef != null) {
                            val init = when (val d = GdClassMemberUtil.findDeclaration(singleRef)?.psi()) {
                                is GdClassVarDeclTl -> d.expr
                                is GdVarDeclSt -> d.expr
                                else -> null
                            }

                            if ((init != null) && (init !is GdCallEx)) {
                                val initText = init.text.orEmpty()
                                if (initText.isNotEmpty() && resolvesToClassChain(initText)) {
                                    val name = element.text
                                    if ((name != "new") && (name != "instance")) {
                                        return@Resolver null
                                    }
                                }
                            }
                        }
                    }
                }

                val resolved = GdClassMemberUtil.findDeclaration(element)?.psi()

                // If statically accessed, disallow resolving non-static members even if the target class couldn't be inferred
                if (isStaticAccess == true) {
                    when (resolved) {
                        is GdMethodDeclTl -> if (!resolved.isStatic) return@Resolver null
                        is GdClassVarDeclTl -> if (!resolved.isStatic) return@Resolver null
                    }
                }

                // Additional guard: if a qualifier denotes a class (directly or via class-typed var), block non-static members
                if ((qualifierExpr != null) && qualifierQualifiesAsClass(qualifierExpr)) {
                    when (resolved) {
                        is GdClassDeclTl -> { /* ok */
                        }

                        is GdMethodDeclTl -> if (!resolved.isStatic) return@Resolver null
                        is GdClassVarDeclTl -> if (!resolved.isStatic) return@Resolver null
                    }
                }

                if ((targetClassDecl != null) && (resolved is PsiElement)) {
                    val owner = GdClassUtil.getOwningClassElement(resolved)

                    // Enforce static vs. instance access rules
                    when (resolved) {
                        is GdMethodDeclTl -> {
                            if ((isStaticAccess == true) && !resolved.isStatic) {
                                return@Resolver null
                            }
                        }

                        is GdClassVarDeclTl -> {
                            if ((isStaticAccess == true) && !resolved.isStatic) {
                                return@Resolver null
                            }
                        }

                        is GdClassDeclTl -> {
                            // Accessing a class via instance (obj.ClassName) is invalid
                            if (isStaticAccess == false) return@Resolver null
                        }
                    }

                    // Allow accessing inner classes via their direct parent class (e.g., A1.B1)
                    if (resolved is GdClassDeclTl) {
                        val enclosing = PsiTreeUtil.getStubOrPsiParentOfType(resolved, GdClassDeclTl::class.java)
                        if ((enclosing != null) && (enclosing == targetClassDecl)) {
                            // OK: accessing inner class on its parent
                        } else {
                            if ((owner is GdClassDeclTl) && (owner != targetClassDecl)) {
                                return@Resolver null
                            }
                        }
                    } else {
                        // For non-class members, require exact owning class match
                        if ((owner is GdClassDeclTl) && (owner != targetClassDecl)) {
                            return@Resolver null
                        }
                    }
                }

                resolved
            },
            true,
            false,
        )

        return resolved
    }


    /**
     * Resolves the current reference to a corresponding PsiElement within the project context.
     *
     * The method first attempts direct resolution by invoking `resolveId` with the result of
     * `resolveDeclaration()`. If direct resolution fails, it uses the `GdClassNamingIndex` to
     * search for a match based on the current element's text across all scopes in the project.
     *
     * @return the resolved PsiElement, or null if no resolution is possible
     */
    override fun resolve(): PsiElement? {
        val direct = resolveId(resolveDeclaration())
        direct?.let { return it }

        return GdClassNamingIndex.INSTANCE
            .get(element.text, element.project, GlobalSearchScope.allScope(element.project))
            .firstOrNull()?.containingFile
    }


    /**
     * Provides a list of possible code completion variants for the referenced element.
     *
     * @return An array of LookupElement objects representing the possible completion elements.
     */
    override fun getVariants(): Array<LookupElement> {
        val declarations = GdClassMemberUtil.listDeclarations(element)

        val lookups = declarations.flatMap {
            val result = GdCompletionUtil.lookups(it, completionIntoCallableParam())
            result.toList()
        }

        return lookups.toTypedArray()
    }


    /**
     * Determines whether the context of the current element corresponds to a callable parameter position.
     *
     * This method traverses the PSI tree, analyzing the surrounding elements for a specific context. It identifies
     * if the current argument expression corresponds to a parameter of type "Callable" in a method declaration.
     *
     * @return true if the element is in a context where its parameter is of type "Callable"; false otherwise
     */
//TODO: check why condition is always true
    private fun completionIntoCallableParam(): Boolean {
        return PsiTreeUtil.getParentOfType(element, GdArgExpr::class.java)?.let { arg ->
            PsiTreeUtil.getParentOfType(arg, GdCallEx::class.java)?.let {
                val index = arg.parent.children.indexOf(arg)
                val refId = PsiTreeUtil.getChildrenOfType(it.expr, GdRefIdRef::class.java)?.lastOrNull() ?: return false
                val decl = GdClassMemberReference(refId).resolveDeclaration()
                if (decl is GdMethodDeclTl) {
                    return decl.parameters.values.toTypedArray().getOrNull(index).orEmpty() == "Callable"
                }
                return false
            }
        } ?: false
    }


    /**
     * Creates a lookup element representing a GDScript method with the specified name.
     *
     * @param name The name of the method to be added as a lookup element.
     * @return A `LookupElement` configured with the provided method name and visual attributes.
     */
//TODO: delete unused?
    private fun addMethod(name: String): LookupElement {
        return GdLookup.create(
            name,
            lookup = "()",
            presentable = name,
            priority = GdLookup.BUILT_IN,
            icon = GdScriptPluginIcons.GDScriptIcons.METHOD_MARKER,
        )
    }


    /**
     * Infers whether a given declaration represents static access.
     *
     * The method examines the type of the provided declaration to determine static
     * access. For class declarations, static access is inferred as `true`. For variable
     * declarations with initializers, it attempts to infer based on the initializer's
     * expression. If the declaration does not match any specific pattern, `null` is returned.
     *
     * @param decl The `PsiElement` declaration to evaluate. It may represent a class,
     *             variable declaration, or other construct.
     * @return `true` if the declaration indicates static access, `false` if it indicates
     *         instance access, and `null` if it cannot be determined.
     */
// Helpers to keep resolve/completion logic concise
    private fun inferStaticAccessFromDecl(decl: PsiElement?): Boolean? {
        fun inferFromInitializer(expr: GdExpr?): Boolean? {
            val call = expr as? GdCallEx
            val callee = call?.expr?.text.orEmpty()
            return when {
                callee.endsWith(".new") || callee.endsWith(".instance") -> false

                else -> {
                    val initText = expr?.text.orEmpty()
                    if (initText.isNotEmpty()) {
                        GdClassUtil.getClassIdElement(initText, element, element.project) != null
                    } else null
                }
            }
        }
        return when (decl) {
            is GdClassDeclTl, is GdClassNaming -> true
            is GdClassVarDeclTl -> inferFromInitializer(decl.expr)
            is GdVarDeclSt -> inferFromInitializer(decl.expr)
            else -> null
        }
    }


    /**
     * Determines if a given qualifier expression can logically qualify as a class.
     *
     * The method checks the various possible declarations and resolves whether the
     * expression references a class, a static member, or another type that aligns
     * with the semantics of a class reference.
     *
     * @param qualifierExpr The qualifier expression to evaluate, or null if no expression is provided.
     * @return True if the qualifier expression qualifies as a class, false otherwise.
     */
    private fun qualifierQualifiesAsClass(qualifierExpr: GdExpr?): Boolean {
        if (qualifierExpr == null) return false
        val refs = PsiTreeUtil.getChildrenOfType(qualifierExpr, GdRefIdRef::class.java)
        val leftRef = refs?.firstOrNull()
        val leftDecl = leftRef?.let { GdClassMemberUtil.findDeclaration(it)?.psi() }
        return when (leftDecl) {
            is GdClassDeclTl, is GdClassNaming -> true
            is GdClassVarDeclTl -> inferStaticAccessFromDecl(leftDecl) == true
            is GdVarDeclSt -> inferStaticAccessFromDecl(leftDecl) == true
            null -> GdClassUtil.getClassIdElement(qualifierExpr.text, element, element.project) != null
            else -> false
        }
    }
}
