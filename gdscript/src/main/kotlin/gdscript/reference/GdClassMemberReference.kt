package gdscript.reference

import GdScriptPluginIcons
import com.intellij.codeInsight.highlighting.HighlightedReference
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.*
import gdscript.GdKeywords
import gdscript.completion.GdLookup
import gdscript.completion.utils.GdCompletionUtil
import gdscript.index.impl.GdClassNamingIndex
import gdscript.psi.*
import gdscript.psi.impl.GdKeyValueImpl
import gdscript.psi.impl.GdRefIdRefImpl
import gdscript.psi.utils.*
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
        fun resolveId(element: PsiElement?): PsiNamedElement? {
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


    private fun inferTypeFromPsi(element: PsiElement?): String {
        if (element == null) return ""

        return when (element) {
            is GdKeyValueImpl -> inferTypeFromPsi(element.children.getOrNull(1))
            is GdDictDecl -> "Dictionary[Variant, Variant]"
            is GdArrayDecl -> "Array"

            is GdPrimaryEx -> {
                when {
                    PsiTreeUtil.findChildOfType(element, GdDictDecl::class.java) != null -> "Dictionary[Variant, Variant]"
                    PsiTreeUtil.findChildOfType(element, GdArrayDecl::class.java) != null -> "Array"
                    else -> ""
                }
            }

            else -> ""
        }
    }

    private fun qualifierTypeWithDictionaryFallback(qualifierExpr: GdExpr?): String {
        if (qualifierExpr == null) return ""

        val dictElement = PsiGdExprUtil.resolveDictPathElement(qualifierExpr)
        val dictType = inferTypeFromPsi(dictElement)
        if (dictType.isNotEmpty()) {
            return dictType
        }

        val refs = PsiTreeUtil.getChildrenOfType(qualifierExpr, GdRefIdRef::class.java)
        val lastRef = refs?.lastOrNull() ?: return ""
        val resolvedDecl = GdClassMemberUtil.findDeclaration(lastRef)?.psi() ?: return ""

        return when (resolvedDecl) {
            is GdSignalDeclTl -> GdKeywords.SIGNAL
            is GdClassDeclTl -> GdClassUtil.getFullClassId(resolvedDecl)
            is GdClassNaming -> GdClassUtil.getFullClassId(resolvedDecl)
            is GdClassVarDeclTl -> resolvedDecl.returnType
            is GdVarDeclSt -> resolvedDecl.returnType
            is GdConstDeclTl -> resolvedDecl.returnType
            is GdConstDeclSt -> resolvedDecl.returnType
            is GdParam -> resolvedDecl.returnType
            is GdMethodDeclTl -> resolvedDecl.returnType
            else -> ""
        }
    }

    private fun signalTargetRoot(): PsiElement? {
        val byName = GdClassUtil.getClassIdElement("Signal", element, element.project)
        val byKeyword = GdClassUtil.getClassIdElement(GdKeywords.SIGNAL, element, element.project)
        val signalClass = byName ?: byKeyword ?: return null


        when (signalClass) {
            is GdClassDeclTl,
            is GdClassNaming,
            is GdClassNameNmi -> return signalClass
        }

        val classNaming = PsiTreeUtil.getParentOfType(signalClass, GdClassNaming::class.java, false)
        if (classNaming != null) {
            //println("signalTargetRoot: classNaming=${classNaming.javaClass.simpleName}")
            return classNaming
        }

        val containingDecl = PsiTreeUtil.getParentOfType(signalClass, GdClassDeclTl::class.java, false)
        if (containingDecl != null) {
            //println("signalTargetRoot: containingDecl=${containingDecl.javaClass.simpleName}")
            return containingDecl
        }

        val owningDecl = GdClassUtil.getOwningClassElement(signalClass)
        if (owningDecl != null) {
            //println("signalTargetRoot: owningDecl=${owningDecl.javaClass.simpleName}")
            return owningDecl
        }

        val file = signalClass.containingFile
        if (file != null) {
            //println("signalTargetRoot: file=${file.virtualFile?.name}")
            return file
        }

        return signalClass
    }


    private fun resolveSignalMember(memberName: String): PsiElement? {
        val signalRoot = signalTargetRoot() ?: return null

        val fromClassMembers = when (signalRoot) {
            is GdClassDeclTl -> GdClassMemberUtil.listClassMemberDeclarations(
                signalRoot,
                static = false,
                search = memberName,
                constructors = false,
                isRecursive = true,
                includeUnnamedEnumValues = true,
            ).firstOrNull()

            is GdClassNaming -> GdClassMemberUtil.listClassMemberDeclarations(
                signalRoot,
                static = false,
                search = memberName,
                constructors = false,
                isRecursive = true,
                includeUnnamedEnumValues = true,
            ).firstOrNull()

            is GdClassNameNmi -> {
                val classNaming = PsiTreeUtil.getParentOfType(signalRoot, GdClassNaming::class.java, false)
                if (classNaming != null) {
                    GdClassMemberUtil.listClassMemberDeclarations(
                        classNaming,
                        static = false,
                        search = memberName,
                        constructors = false,
                        isRecursive = true,
                        includeUnnamedEnumValues = true,
                    ).firstOrNull()
                } else {
                    null
                }
            }

            else -> null
        }

        if (fromClassMembers != null) {
            //println("resolveSignalMember: class-member hit '$memberName' -> ${fromClassMembers.javaClass.simpleName}")
            return fromClassMembers
        }

        val searchRoot = when (signalRoot) {
            is GdClassNameNmi -> signalRoot.parent ?: signalRoot
            else -> signalRoot
        }

        val named = PsiTreeUtil.findChildrenOfType(searchRoot, PsiNamedElement::class.java)
            .firstOrNull { it.name == memberName }

        if (named != null) {
            //println("resolveSignalMember: named hit '$memberName' -> ${named.javaClass.simpleName}")
            return named as PsiElement
        }

        //println("resolveSignalMember: miss '$memberName' root=${signalRoot.javaClass.simpleName}")
        return null
    }

    private fun signalCompletionDeclarations(): List<PsiElement> {
        val signalRoot = signalTargetRoot() ?: return emptyList()
        // println("signalCompletionDeclarations: root=${signalRoot.javaClass.simpleName} text='${signalRoot.text.take(80)}'")

        val classDecl = when (signalRoot) {
            is GdClassDeclTl -> signalRoot
            is GdClassNaming -> PsiTreeUtil.getParentOfType(signalRoot, GdClassDeclTl::class.java, false)
            is GdClassNameNmi -> PsiTreeUtil.getParentOfType(signalRoot, GdClassDeclTl::class.java, false)
            else -> PsiTreeUtil.getParentOfType(signalRoot, GdClassDeclTl::class.java, false)
        }

        val fromClassMembers: List<PsiElement> = if (classDecl != null) {
            GdClassMemberUtil.listClassMemberDeclarations(
                classDecl,
                static = false,
                search = null,
                constructors = false,
                isRecursive = true,
                includeUnnamedEnumValues = true,
            )
        } else {
            emptyList()
        }

        val searchRoot = signalRoot.containingFile ?: signalRoot
        val named = collectCompletionDeclarations(searchRoot)

        return (fromClassMembers + named)
            .distinctBy { decl ->
                when (decl) {
                    is PsiNamedElement -> decl.name ?: decl.text
                    else -> decl.text
                }
            }
    }

    private fun collectCompletionDeclarations(root: PsiElement): List<PsiElement> {
        val declarations = buildList<PsiElement> {
            addAll(PsiTreeUtil.findChildrenOfType(root, GdClassVarDeclTl::class.java))
            addAll(PsiTreeUtil.findChildrenOfType(root, GdConstDeclTl::class.java))
            addAll(PsiTreeUtil.findChildrenOfType(root, GdMethodDeclTl::class.java))
            addAll(PsiTreeUtil.findChildrenOfType(root, GdSignalDeclTl::class.java))
            addAll(PsiTreeUtil.findChildrenOfType(root, GdEnumDeclTl::class.java))
            addAll(PsiTreeUtil.findChildrenOfType(root, GdClassDeclTl::class.java))
        }

        return declarations.distinctBy { decl ->
            when (decl) {
                is PsiNamedElement -> decl.name ?: decl.text
                else -> decl.text
            }
        }
    }


    /**
     * Completion for signal variables cannot rely solely on the regular reference/type pipeline,
     * because signal qualifiers may resolve to no declaration in completion context even though
     * the signal is declared in the surrounding script. Fall back to a direct name lookup in the
     * owning class and then in the containing file.
     */
    private fun resolveSignalDeclarationByName(expr: GdExpr?): GdSignalDeclTl? {
        if (expr == null) return null

        // This fallback is only valid for plain signal identifiers such as
        // `level_theme_changed` in `level_theme_changed.<caret>`.
        // It must NOT inspect nested ref ids inside call/attribute expressions like
        // `level_theme_changed.emit()` because that would incorrectly treat the whole
        // call expression as the original signal variable again.
        val signalName = when (expr) {
            is GdLiteralEx -> expr.refIdNm?.text
            is GdRefIdRef -> expr.text
            else -> null
        } ?: return null

        val owningClass = PsiTreeUtil.getParentOfType(element, GdClassDeclTl::class.java, false)
        if (owningClass != null) {
            val classSignals = PsiTreeUtil.findChildrenOfType(owningClass, GdSignalDeclTl::class.java)
            val hit = classSignals.firstOrNull { signal ->
                val name = signal.signalIdNmi?.name ?: signal.name
                name == signalName
            }
            if (hit != null) {
                return hit
            }
        }

        val file = element.containingFile
        val fileSignals = PsiTreeUtil.findChildrenOfType(file, GdSignalDeclTl::class.java)
        return fileSignals.firstOrNull { signal ->
            val name = signal.signalIdNmi?.name ?: signal.name
            name == signalName
        }
    }

    private fun qualifierCompletionDeclarations(qualifierExpr: GdExpr?): List<PsiElement> {
        if (qualifierExpr == null) return emptyList()

        if (qualifierExpr.text == GdKeywords.SELF) {
            val nearestClassDecl = PsiTreeUtil.getParentOfType(element, GdClassDeclTl::class.java, false)
            val owningClass = GdClassUtil.getOwningClassElement(element)
            val containingFile = element.containingFile as? GdFile

            val classRoot = when {
                nearestClassDecl != null -> nearestClassDecl as PsiElement
                owningClass is GdClassDeclTl -> owningClass
                owningClass is GdClassNaming -> owningClass
                containingFile != null -> containingFile
                else -> null
            } ?: return emptyList()

            val fromClassMembers: List<PsiElement> = when (classRoot) {
                is GdClassDeclTl -> GdClassMemberUtil.listClassMemberDeclarations(
                    classRoot,
                    static = false,
                    search = null,
                    constructors = false,
                    isRecursive = true,
                    includeUnnamedEnumValues = true,
                )

                is GdClassNaming -> GdClassMemberUtil.listClassMemberDeclarations(
                    classRoot,
                    static = false,
                    search = null,
                    constructors = false,
                    isRecursive = true,
                    includeUnnamedEnumValues = true,
                )

                is GdFile -> buildList {
                    addAll(PsiTreeUtil.getStubChildrenOfTypeAsList(classRoot, GdClassVarDeclTl::class.java))
                    addAll(PsiTreeUtil.getStubChildrenOfTypeAsList(classRoot, GdConstDeclTl::class.java))
                    addAll(PsiTreeUtil.getStubChildrenOfTypeAsList(classRoot, GdMethodDeclTl::class.java))
                    addAll(PsiTreeUtil.getStubChildrenOfTypeAsList(classRoot, GdSignalDeclTl::class.java))
                    addAll(PsiTreeUtil.getStubChildrenOfTypeAsList(classRoot, GdEnumDeclTl::class.java))
                    addAll(PsiTreeUtil.getStubChildrenOfTypeAsList(classRoot, GdClassDeclTl::class.java))
                }

                else -> emptyList()
            }

            val searchRoot: PsiElement = when (classRoot) {
                is GdClassDeclTl -> classRoot
                is GdClassNaming -> classRoot.parent ?: classRoot
                is GdFile -> classRoot
                else -> classRoot
            }

            val named = collectCompletionDeclarations(searchRoot)

            return (fromClassMembers + named)
                .distinctBy { decl ->
                    when (decl) {
                        is PsiNamedElement -> decl.name ?: decl.text
                        else -> decl.text
                    }
                }
        }

        val qualifierRef = when (qualifierExpr) {
            is GdRefIdRef -> qualifierExpr
            is GdLiteralEx -> qualifierExpr.refIdNm
            else -> PsiTreeUtil.findChildOfType(qualifierExpr, GdRefIdRef::class.java)
        }

        val resolvedByReference = when (val ref = qualifierRef?.reference) {
            is GdClassMemberReference -> ref.resolveDeclaration()
            else -> null
        }

        // println("qualifierCompletionDeclarations: qualifierRef=${qualifierRef?.javaClass?.simpleName} text='${qualifierRef?.text}'")

        // println("qualifierCompletionDeclarations: resolvedByReference=${resolvedByReference?.javaClass?.simpleName} text='${resolvedByReference?.text?.take(80)}'")
        if (resolvedByReference is GdSignalDeclTl) {
            // println("qualifierCompletionDeclarations: signal by qualifier reference resolution")
            return signalCompletionDeclarations()
        }

        // Signal qualifiers may fail normal resolve/type inference during completion.
        // In that case, detect locally declared signals by name and reuse the Signal builtin members.
        val directSignalByName = resolveSignalDeclarationByName(qualifierExpr)
        // println("qualifierCompletionDeclarations: directSignalByName=${directSignalByName?.javaClass?.simpleName} text='${directSignalByName?.text?.take(80)}'")
        if (directSignalByName != null) {
            // println("qualifierCompletionDeclarations: signal by direct name lookup")
            return signalCompletionDeclarations()
        }


        val signalDecl = when (val resolved = qualifierRef?.let { GdClassMemberUtil.findDeclaration(it) }) {
            is GdSignalDeclTl -> resolved
            else -> null
        }
        if (signalDecl != null) {
            // println("qualifierCompletionDeclarations: direct signal decl by ref '${signalDecl.name}'")
            return signalCompletionDeclarations()
        }

        val inferredQualifierType = qualifierTypeWithDictionaryFallback(qualifierExpr)
        val literalQualifierType = literalCompletionType(qualifierExpr)
        val qualifierType = inferredQualifierType.ifEmpty { literalQualifierType }

        if (isSignalType(qualifierType)) {
            return signalCompletionDeclarations()
        }

        if (qualifierType.equals(GdKeywords.VOID, ignoreCase = true) || qualifierType.equals("void", ignoreCase = true)) {
            return emptyList()
        }

        val dictElement = PsiGdExprUtil.resolveDictPathElement(qualifierExpr)
        val dictType = inferTypeFromPsi(dictElement)
        // println("qualifierCompletionDeclarations: dictType='$dictType'")
        if (isSignalType(dictType)) {
            // println("qualifierCompletionDeclarations: signal by dictType")
            return signalCompletionDeclarations()
        }

        if (qualifierType.isEmpty()) {
            return emptyList()
        }

        val target = GdClassUtil.getClassIdElement(qualifierType, element, element.project) ?: return emptyList()
        val classRoot = GdClassUtil.getOwningClassElement(target) ?: target

        val fromClassMembers: List<PsiElement> = when (classRoot) {
            is GdClassDeclTl -> GdClassMemberUtil.listClassMemberDeclarations(
                classRoot,
                static = false,
                search = null,
                constructors = false,
                isRecursive = true,
                includeUnnamedEnumValues = true,
            )

            is GdClassNaming -> GdClassMemberUtil.listClassMemberDeclarations(
                classRoot,
                static = false,
                search = null,
                constructors = false,
                isRecursive = true,
                includeUnnamedEnumValues = true,
            )

            is GdClassNameNmi -> {
                val classNaming = PsiTreeUtil.getParentOfType(classRoot, GdClassNaming::class.java, false)
                if (classNaming != null) {
                    GdClassMemberUtil.listClassMemberDeclarations(
                        classNaming,
                        static = false,
                        search = null,
                        constructors = false,
                        isRecursive = true,
                        includeUnnamedEnumValues = true,
                    )
                } else {
                    emptyList()
                }
            }

            else -> emptyList()
        }

        val named = collectCompletionDeclarations(classRoot)

        return (fromClassMembers + named)
            .distinctBy { decl ->
                when (decl) {
                    is PsiNamedElement -> decl.name ?: decl.text
                    else -> decl.text
                }
            }
    }


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


    private fun isSignalType(type: String): Boolean {
        return type == "signal" || type == "Signal" || type.endsWith(".Signal")
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

                if (element is GdRefIdRefImpl && element.text.trim() == GdKeywords.NEW)
                    return@Resolver null

                val qualifierExpr = GdClassMemberUtil.calledUpon(element)

                val dictPath = PsiGdExprUtil.resolveDictPathElement(element)
                if (dictPath != null) return@Resolver dictPath

                val qualifierType = qualifierTypeWithDictionaryFallback(qualifierExpr)
                //println("qualifierType='$qualifierType' keyword='${GdKeywords.SIGNAL}' isSignal=${isSignalType(qualifierType)}")

                if (isSignalType(qualifierType)) {
                    val signalMember = resolveSignalMember(element.text)
                    signalMember?.let { return@Resolver it }
                }

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

//                        // If it's a class declaration, search ALL enums (anonymous and named)
//                        if (containerElement is GdClassDeclTl) {
//                            val allEnums = containerElement.childrenOfType<GdEnumDeclTl>()
//                            for (enumDecl in allEnums) {
//                                val enumValue = enumDecl.enumValueList.firstOrNull { enumVal ->
//                                    enumVal.enumValueNmi.text == enumValueName
//                                }
//                                if (enumValue != null) {
//                                    return@Resolver enumValue
//                                }
//                            }
//                        }

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
                        if (containerElement is PsiFile || containerElement is GdClassDeclTl) {
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

                val targetClassDecl = qualifierExpr?.let {
                    if (qualifierType.isNotEmpty()) {
                        val target = GdClassUtil.getClassIdElement(qualifierType, element, element.project)
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

                                    if ((name != GdKeywords.NEW) && (name != GdKeywords.INSTANCE)) {
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

    private fun immediateCompletionQualifier(): GdExpr? {
        val attribute = PsiTreeUtil.getParentOfType(element, GdAttributeEx::class.java, false)
        val directExpr = attribute?.expr

        // In incomplete PSI after expressions like `signal.emit().<caret>`, the surrounding
        // attribute may still expose the older qualifier instead of the direct call expression.
        // Prefer the nearest preceding call expression if it ends immediately before the dot.
        val prevLeaf = PsiTreeUtil.prevVisibleLeaf(element)
        val prevCall = prevLeaf?.let { PsiTreeUtil.getParentOfType(it, GdCallEx::class.java, false) }
        if (prevCall != null) {
            return prevCall
        }

        if (directExpr != null) {
            return directExpr
        }

        return GdClassMemberUtil.calledUpon(element)
    }

    private fun literalCompletionType(expr: GdExpr?): String {
        if (expr == null) return ""

        val text = expr.text.trim()
        if (text.length >= 2 && text.startsWith('"') && text.endsWith('"')) {
            return GdKeywords.STRING
        }
        if (text == "true" || text == "false") {
            return GdKeywords.BOOL
        }
        if (text.matches(Regex("^-?\\d+$"))) {
            return GdKeywords.INT
        }
        if (text.matches(Regex("^-?\\d+\\.\\d+([eE][+-]?\\d+)?$")) || text.matches(Regex("^-?\\d+[eE][+-]?\\d+$"))) {
            return GdKeywords.FLOAT
        }

        return ""
    }


    /**
     * Provides a list of possible code completion variants for the referenced element.
     *
     * @return An array of LookupElement objects representing the possible completion elements.
     */
    override fun getVariants(): Array<LookupElement> {
        val qualifierExpr = immediateCompletionQualifier()
        val qualifierDeclarations: List<PsiElement> = qualifierCompletionDeclarations(qualifierExpr)
        val directQualifierType = qualifierExpr?.returnType?.trim().orEmpty()
        val fallbackQualifierType = qualifierExpr?.let { qualifierTypeWithDictionaryFallback(it).trim() }.orEmpty()
        val literalQualifierType = literalCompletionType(qualifierExpr).trim()
        val qualifierType = directQualifierType.ifEmpty {
            fallbackQualifierType.ifEmpty { literalQualifierType }
        }

//        println("getVariants: element='${element.text}' class=${element.javaClass.simpleName}")
//        println("getVariants: parent='${element.parent?.text}' parentClass=${element.parent?.javaClass?.simpleName}")
//        println("getVariants: immediateQualifier='${immediateCompletionQualifier()?.text}'")
//        println("getVariants: calledUpon='${GdClassMemberUtil.calledUpon(element)?.text}'")
//        println("getVariants: prevVisibleLeaf='${PsiTreeUtil.prevVisibleLeaf(element)?.text}'")
//        println("getVariants: prevCall='${PsiTreeUtil.prevVisibleLeaf(element)?.let { PsiTreeUtil.getParentOfType(it, GdCallEx::class.java, false) }?.text}'")


        val isQualifiedContext = PsiTreeUtil.getParentOfType(element, GdAttributeEx::class.java, false) != null
            || PsiTreeUtil.prevVisibleLeaf(element)?.elementType == GdTypes.DOT

        val declarations: List<PsiElement> = when {
            !isQualifiedContext -> {
                GdClassMemberUtil.listDeclarations(element).filterIsInstance<PsiElement>()
            }

            (qualifierExpr is GdCallEx) && (
                directQualifierType.equals(GdKeywords.VOID, ignoreCase = true)
                    || directQualifierType.equals("void", ignoreCase = true)
                    || fallbackQualifierType.equals(GdKeywords.VOID, ignoreCase = true)
                    || fallbackQualifierType.equals("void", ignoreCase = true)
                    || qualifierType.equals(GdKeywords.VOID, ignoreCase = true)
                    || qualifierType.equals("void", ignoreCase = true)
                ) -> {
                emptyList()
            }

            qualifierDeclarations.isNotEmpty() -> {
                qualifierDeclarations
            }

            directQualifierType.isNotEmpty()
                || fallbackQualifierType.isNotEmpty()
                || literalQualifierType.isNotEmpty()
                || qualifierType.isNotEmpty() -> {
                emptyList()
            }

            else -> {
                emptyList()
            }
        }

        val lookups = declarations
            .distinctBy { decl ->
                when (decl) {
                    is PsiNamedElement -> decl.name ?: decl.text
                    else -> decl.text
                }
            }
            .flatMap { decl ->
                val built = GdCompletionUtil.lookups(decl, completionIntoCallableParam()).toList()
                built.ifEmpty {
                    val name = (decl as? PsiNamedElement)?.name
                    if (!name.isNullOrBlank()) {
                        listOf(LookupElementBuilder.create(name))
                    } else {
                        emptyList()
                    }
                }
            }

        return lookups.toTypedArray()
    }


    /*
    * Determines whether the context of the current element corresponds to a callable parameter position.
    *
    * This method traverses the PSI tree, analyzing the surrounding elements for a specific context. It identifies
    * if the current argument expression corresponds to a parameter of type "Callable" in a method declaration.
    *
    * @return true if the element is in a context where its parameter is of type "Callable"; false otherwise
    */
    private fun completionIntoCallableParam(): Boolean {
        val arg = PsiTreeUtil.getParentOfType(element, GdArgExpr::class.java) ?: return false
        val call = PsiTreeUtil.getParentOfType(arg, GdCallEx::class.java) ?: return false

        val argContainer = arg.parent ?: return false
        val args = PsiTreeUtil.getChildrenOfTypeAsList(argContainer, GdArgExpr::class.java)
        val index = args.indexOf(arg)
        if (index < 0) return false

        val refId = PsiTreeUtil.getChildrenOfType(call.expr, GdRefIdRef::class.java)?.lastOrNull() ?: return false
        val decl = GdClassMemberReference(refId).resolveDeclaration()

        if (decl is GdMethodDeclTl) {
            val paramType = decl.parameters.values.elementAtOrNull(index).orEmpty().trim()
            return paramType.equals(GdKeywords.CALLABLE, ignoreCase = true)
                || paramType.endsWith(".Callable")
                || paramType.endsWith(".callable")
        }

        return false
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
