package gdscript.psi.utils

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import gdscript.GdKeywords
import gdscript.index.impl.*
import gdscript.model.BoolVal
import gdscript.psi.*
import gdscript.psi.utils.GdClassUtil.getClassIdElement
import project.psi.util.ProjectAutoloadUtil

/**
 * A thread-local variable used to maintain a stack for tracking declaration resolutions.
 *
 * This stack appears to be specifically designed to hold a mutable set of strings,
 * ensuring thread safety in its operations by isolating the data to individual threads.
 *
 * Usage of `ThreadLocal` with `withInitial` ensures that each thread that accesses the variable
 * will have its own independent mutable set initialized upon first access.
 *
 * This can be particularly useful in scenarios involving recursive resolution of declarations
 * or where tracking resolution contexts across threads is necessary to avoid conflicts.
 */
private val declarationResolutionStack = ThreadLocal.withInitial { mutableSetOf<String>() }

// Neue Utility-Klasse oder Erweiterung in GdClassMemberUtil.kt
//object GdDictionaryStructureUtil {
//
//    fun getDictionaryStructure(dictExpr: GdDictDecl): Map<String, GdExpr> {
//        val result = mutableMapOf<String, GdExpr>()
//        // Dictionary-Einträge parsen und Schlüssel-Wert-Paare extrahieren
//        for (entry in dictExpr.keyValueList) {
//            val key = entry.?.text?.removeSurrounding("\"") ?: continue
//            val value = entry.valueExpr ?: continue
//            result[key] = value
//        }
//        return result
//    }
//
//    /**
//     * Löst verschachtelte Dictionary-Zugriffe auf.
//     */
//    fun resolveNestedDictionaryAccess(
//        baseDeclaration: PsiElement,
//        accessPath: List<String>
//    ): GdExpr? {
//        // Finde das ursprüngliche Dictionary-Literal
//        val dictExpr = when (baseDeclaration) {
//            is GdClassVarDeclTl -> baseDeclaration.expr as? GdDictEx
//            is GdVarDeclSt -> baseDeclaration.expr as? GdDictEx
//            else -> null
//        } ?: return null
//
//        var currentDict: GdDictEx? = dictExpr
//        for (key in accessPath.dropLast(1)) {
//            val structure = getDictionaryStructure(currentDict ?: return null)
//            currentDict = structure[key] as? GdDictEx
//        }
//
//        return currentDict?.let { getDictionaryStructure(it)[accessPath.last()] }
//    }
//}

/**
 * Utility object for handling and resolving class members in GDScript files.
 */
object GdClassMemberUtil {
    val absTime = System.nanoTime()
    var time: Long = 0
    var count: Long = 0
    var count2: Long = 0

    fun findDeclarationByName(name: String, project: Project): PsiElement? {
        // 1. Versuch: über GdClassNamingIndex – sucht nach einer Klasse mit dem Namen
        val classMatch = GdClassNamingIndex.INSTANCE
            .getGlobally(name, project)
            .firstOrNull()
        if (classMatch != null) return classMatch

        // 2. Versuch: nach Datei mit passendem Namen suchen (z. B. Settings.gd)
        val gdFiles = FilenameIndex.getFilesByName(project, "$name.gd", GlobalSearchScope.allScope(project))
        if (gdFiles.isNotEmpty()) {
            return gdFiles.first()
        }

        // Optional: versuche auch ohne ".gd" (falls Godot-Dateien ohne Extension im Index sind)
        val fallback = FilenameIndex.getFilesByName(project, name, GlobalSearchScope.allScope(project))
        return fallback.firstOrNull()
    }


    /**
     * Attempts to find the declaration associated with the given element.
     *
     * @param element The PSI element for which the declaration is being searched.
     * @param onlyLocalScope If true, restricts the search to the local scope of the element.
     * @param ignoreParents If true, skips parental context during the search.
     * @param ignoreGlobalScope If true, excludes the global scope from the search.
     * @return The declaration found for the specified element or null if no declaration is found.
     */
    fun findDeclaration(
        element: PsiElement,
        onlyLocalScope: Boolean = false,
        ignoreParents: Boolean = false,
        ignoreGlobalScope: Boolean = false,
    ): Any? {
        if (DumbService.isDumb(element.project)) return null


        // special handling for enum values 
        if (element is GdEnumValue) {
            // Element is already a GdEnumValue, return it directly
            return element
        }

        if (element is GdEnumDeclTl) {
            // for enum declarations, check if we need to find a parent enum value
            val pt = PsiTreeUtil.getParentOfType(element, GdEnumValue::class.java)
            if (pt != null) {
                return pt
            }
            // otherwise return the enum declaration itself
            return element
        }

        val key = "${element.containingFile.virtualFile?.path}:${element.textOffset}:${element.text}"
        val stack = declarationResolutionStack.get()

        if (key in stack) {
            // cycle detected, return null to break recursion
            return null
        }

        stack.add(key)
        return try {
            listDeclarations(
                element,
                element,
                onlyLocalScope,
                ignoreParents,
                ignoreGlobalScope
            ).firstOrNull()
        } finally {
            stack.remove(key)
        }
    }


    /**
     * List available declarations (const, var, enum, signal, method, ...) from given PsiElement skipping itself
     * @param searchFor stops and returns a matching element
     */
    @SuppressWarnings()
    fun listDeclarations(
        element: PsiElement,
        searchFor: PsiElement,
        onlyLocalScope: Boolean = false,
        ignoreParents: Boolean = false,
        ignoreGlobalScope: Boolean = false,
    ): Array<Any> {
        return listDeclarations(
            element,
            searchFor.text,
            onlyLocalScope,
            ignoreParents,
            ignoreGlobalScope
        )
    }


    /**
     * List available declarations (const, var, enum, signal, method, ...) from given PsiElement skipping itself
     * @param searchFor stops and returns a matching element
     */
    fun listDeclarations(
        element: PsiElement,
        searchFor: String? = null,
        onlyLocalScope: Boolean = false,
        ignoreParents: Boolean = false,
        ignoreGlobalScope: Boolean = false,
        allowResource: Boolean = false,
    ): Array<Any> {
        if (DumbService.isDumb(element.project)) return Array(0) { PsiElement.EMPTY_ARRAY }

        var static: Boolean? = false
        val project = element.project

        val result = mutableListOf<Any>()
        var calledOn: String? = GdKeywords.SELF

        val calledOnPsi: GdExpr? = calledUpon(element)
        val calledOnPsiName = calledOnPsi?.text ?: ""

        if (calledOnPsi != null && calledOnPsiName != GdKeywords.SELF) {
            // Check if there is an assertion check 'if (node is Node3D):'
            var isCheckedSuccess = false
            val isChecked = findIsTypeCheck(element)
            if (isChecked != null) {
                val isExpr = isChecked.expr.firstChild
                if (isExpr is GdRefIdRef && isExpr.text == calledOnPsiName) {
                    calledOn = PsiGdExprUtil.extractSubtype(isChecked.typedVal)
                    isCheckedSuccess = true
                }
            }

            if (!isCheckedSuccess) {
                calledOn = calledOnPsi.getReturnTypeOrRes(allowResource)
            }

            if (calledOn != null) {
                // Only apply this heuristic if we haven't already determined a static context.
                // The idea: if the inferred type name of the qualifier matches the qualifier text itself
                // (e.g., "Outer"), or a fully qualified form like "FileOrOuter.Outer", then we consider
                // the access to be static on a class, unless _Global has a variable with the same name.
                static = isStaticAccessByName(element, calledOnPsi, calledOn)
                // If the qualifier resolves to a script resource path (e.g. "res://Something.gd"),
                // do not assume either static or instance context. Resource-based references can represent
                // unnamed scripts or external files where we cannot reliably infer static-ness from the type
                // string alone. Setting `static = null` prevents premature filtering of members.
                if (calledOn.endsWith(GdKeywords.FILE_SUFFIX_GD_SCRIPT)) {
                    static = null
                }
            }
        }

        if (static == false && (calledOn == null || calledOn == GdKeywords.SELF)) {
            when (val ownerMethod = PsiTreeUtil.getParentOfType(
                calledOnPsi ?: element,
                GdMethodDeclTl::class.java,
            )) {
                is GdMethodDeclTl -> {
                    static = ownerMethod.isStatic
                }
            }
        }

        when (calledOn) {
            GdKeywords.SELF -> calledOn = null
            GdKeywords.SUPER -> calledOn = GdInheritanceUtil.getExtendedClassId(element)
        }

        var parent: PsiElement?

        // If it's stand-alone ref_id, adds also _Global & ClassNames - Classes are added as last due to matching name of some GlobalVars with class_name
        if ((calledOn == null) && !ignoreGlobalScope) {
            arrayOf(GdKeywords.GLOBAL_SCOPE, GdKeywords.GLOBAL_GD_SCRIPT).forEach {
                val globalParent = getClassIdElement(it, project)
                if (globalParent != null) {
                    val local = addsParentDeclarations(
                        GdClassUtil.getOwningClassElement(globalParent),
                        result,
                        null,
                        searchFor,
                        includeUnnamedEnumValues = true
                    )

                    if ((searchFor != null) && (local != null)) {
                        return arrayOf(local)
                    }
                }
            }
        }

        val hitLocal = BoolVal.new()
        // Checks locals only when it's not attribute/call expression moving declaration possibly outside
        if (calledOn == null) {
            val locals = listLocalDeclarationsUpward(element, onlyLocalScope, hitLocal)
            if ((searchFor != null) && locals.containsKey(searchFor)) return arrayOf(locals[searchFor]!!)
            result.addAll(locals.values)

            // This class is already scanned via localDecl - so move to the extended one
            parent = if (onlyLocalScope) {
                GdInheritanceUtil.getExtendedElement(element)
            } else {
                GdClassUtil.getOwningClassElement(element)
            }
        } else {
            // Normalize typed Array[K] to base Array
            if (calledOn.startsWith("${GdKeywords.ARRAY}[")) calledOn = GdKeywords.ARRAY

            // Normalize typed Dictionary[K, V] to base Dictionary
            if (calledOn.startsWith("${GdKeywords.DICTIONARY}[")) {

                val firstChild = PsiTreeUtil.collectElementsOfType(calledOnPsi, GdRefIdRef::class.java).lastOrNull()
                if (firstChild != null) {

                    val dictDecl = findDeclaration(firstChild)
                    if (dictDecl is GdEnumDeclTl) {
                        if (searchFor != null) {

                            val localVal = dictDecl.enumValueList.find { eval -> eval.enumValueNmi.name == searchFor }
                            localVal?.let { return arrayOf(it) }
                        }

                        result.addAll(dictDecl.enumValueList)
                    }
                }

                calledOn = GdKeywords.DICTIONARY
            }

            val enumDecl = if (calledOnPsi is GdAttributeEx) {
                calledOnPsi.refId?.let { findDeclaration(it as PsiElement) }
            } else {
                // For standalone identifiers, find their declaration directly
                calledOnPsi?.let { findDeclaration(it as PsiElement) }
            }

            enumDecl?.let { decl ->
                if (decl is GdEnumDeclTl) {
                    if (searchFor != null) {

                        val localVal = decl.enumValueList.find { eval -> eval.enumValueNmi.name == searchFor }
                        if (localVal != null) {
                            return arrayOf(localVal)
                        }
                    }

                    result.addAll(decl.enumValueList)
                    if (searchFor == null) return result.toTypedArray()
                }
            }

            // Handle "EnumDictionary" or qualified enum names like "Player.TestEnum"
            if (calledOn == "EnumDictionary" || calledOn.contains('.')) {
                // Try to find the enum declaration from calledOnPsi
                val firstChild = PsiTreeUtil.collectElementsOfType(calledOnPsi, GdRefIdRef::class.java).firstOrNull()
                if (firstChild != null) {

                    val enumDecl = findDeclaration(firstChild)
                    if (enumDecl is GdEnumDeclTl) {
                        if (searchFor != null) {

                            val localVal = enumDecl.enumValueList.find { eval -> eval.enumValueNmi.name == searchFor }
                            if (localVal != null) {
                                return arrayOf(localVal)
                            }
                        }

                        result.addAll(enumDecl.enumValueList)
                        return result.toTypedArray()
                    }
                }

                // If calledOn looks like "File.EnumName", try to resolve it
                if ((calledOn != "EnumDictionary") && calledOn.contains('.')) {
                    val enumElement = getClassIdElement(calledOn, element, project)
                    if (enumElement is GdEnumDeclTl) {

                        if (searchFor != null) {
                            val localVal = enumElement.enumValueList.find { eval -> eval.enumValueNmi.name == searchFor }
                            localVal?.let { return arrayOf(it) }
                        }

                        result.addAll(enumElement.enumValueList)
                        return result.toTypedArray()
                    }
                }
            }

            parent = getClassIdElement(calledOn, element, project)
            if (parent == null) {
                val classId = GdClassUtil.getFullClassId(element)
                parent = getClassIdElement(
                    "$classId.${calledOn}",
                    project
                )

                // Try autoload classes
                if (parent == null) {
                    parent = ProjectAutoloadUtil.findFromAlias(calledOn, element)
                    if (parent != null) static = false
                }
            }

            if (parent != null) {
                parent = GdClassUtil.getOwningClassElement(parent)
            }
        }

        // Recursively iterate over all extended classes
        if (!ignoreParents && !hitLocal.value) {
            val includeUnnamedEnumValues = calledOn == null
            val local = collectFromParents(parent, result, project, static, searchFor, includeUnnamedEnumValues)
            local?.let { return arrayOf(it) }
        }

        if (calledOn == null) {
            val autoLoads = ProjectAutoloadUtil.listGlobals(project)
            if (searchFor != null) {
                val localClass = GdClassNamingIndex.INSTANCE.getGlobally(searchFor, element).firstOrNull()
                localClass?.let { return arrayOf(it) }

                val autoLoaded = autoLoads.find { it.key == searchFor }
                autoLoaded?.let { return arrayOf(it) }
            } else {
                result.addAll(GdClassNamingIndex.INSTANCE.getAllValues(project))
                result.addAll(autoLoads)
            }
        }

        if (searchFor != null) return emptyArray()
        return result.toTypedArray()
    }


    /**
     * Recursively iterate over all extended classes
     * Separately used for method-overriding completion
     */
    fun collectFromParents(
        parent: PsiElement?,
        result: MutableList<Any>,
        project: Project,
        static: Boolean? = null,
        search: String? = null,
        includeUnnamedEnumValues: Boolean = true,
    ): PsiElement? {
        if (DumbService.isDumb(project)) return null

        var par = parent
        while (par != null) {
            val local = addsParentDeclarations(par, result, static, search, includeUnnamedEnumValues)
            if ((search != null) && (local != null)) {
                return local
            }
            if (par is GdClassDeclTl) {
                // When within classDecl, check also the root of the current file, not only what the class is extending
                val local = addsParentDeclarations(par.containingFile, result, static, search, includeUnnamedEnumValues)
                if ((search != null) && (local != null)) {
                    return local
                }
            }

            par = GdInheritanceUtil.getExtendedElement(par, project)
        }

        return null
    }


    /**
     * Recursively lists local variable and function declarations starting from a given PSI element,
     * collecting them as it traverses upwards through its parent hierarchy.
     *
     * @param element The starting PSI element from which local declarations should be collected.
     * @param onlyLocalScope If true, stops the collection at the scope of the nearest local function or method.
     * @param hitLocal A mutable boolean wrapper that will be set to true if a local function or method is encountered within the traversal.
     * @return A map of variable and function names to their corresponding PSI elements, representing the local declarations found during the traversal.
     */
    fun listLocalDeclarationsUpward(
        element: PsiElement,
        onlyLocalScope: Boolean = false,
        hitLocal: BoolVal? = null,
    ): HashMap<String, PsiElement> {
        if (DumbService.isDumb(element.project)) return hashMapOf()

        val locals: HashMap<String, PsiElement> = hashMapOf()

        // If inside a match branch GUARD (before ':'), bindings from the pattern list are visible
        run {
            var cur: PsiElement? = element
            while (cur != null) {
                val p = cur.parent
                if (p is GdMatchBlock) {
                    val suiteStart = p.stmtOrSuite?.textRange?.startOffset ?: Int.MAX_VALUE
                    val refStart = element.textRange.startOffset
                    if (refStart < suiteStart) {
                        val vars = PsiTreeUtil.findChildrenOfType(p.patternList, GdVarNmi::class.java)
                        vars.forEach { v ->
                            val name = v.name
                            if (!locals.containsKey(name)) locals[name] = v
                        }
                    }
                    break
                }
                cur = p
            }
        }

        var current: PsiElement = element

        // To avoid matching self
        when (current.parent) {
            is GdClassVarDeclTl,
            is GdVarDeclSt,
            is GdConstDeclTl,
            is GdConstDeclSt,
            is GdEnumDeclTl,
            is GdSetDecl,
            is GdSignalDeclTl,
            is GdMethodDeclTl,
            is GdParam,
            is GdForSt,
            is GdBindingPattern -> {
                current = current.parent
            }
        }

        var isParam = false
        when (current) {
            is GdParam -> {
                isParam = true
                current = current.prevSibling ?: current.parent
            }
        }

        while (true) {
            val movedToParent = current.prevSibling == null
            current = current.prevSibling ?: current.parent ?: break
            if (current is PsiFile) break // avoid directory traversal
            when (current) {
                is GdClassVarDeclTl -> if (!locals.contains(current.name)) locals[current.name] = current
                is GdVarDeclSt -> if (!locals.contains(current.name)) locals[current.name] = current
                is GdConstDeclTl -> if (!locals.contains(current.name)) locals[current.name] = current
                is GdConstDeclSt -> if (!locals.contains(current.name)) locals[current.name] = current

                is GdEnumDeclTl -> {
                    // For named enums, add the enum itself by name
                    if (current.name.isNotBlank()) {
                        if (!locals.contains(current.name)) locals[current.name] = current
                    }
                    // For all enums (named and anonymous), also add individual enum values
                    // This allows direct access to enum values like `AAA` without qualifier
                    current.enumValueList.forEach { enumValue ->
                        val valueName = enumValue.enumValueNmi.name
                        if (!locals.contains(valueName)) locals[valueName] = enumValue
                    }
                }

                is GdSignalDeclTl -> if (!locals.contains(current.name)) locals[current.name] = current

                is GdParam -> {
                    if (!locals.contains(current.varNmi.name)) locals[current.varNmi.name] = current
                }

                is GdForSt -> if (movedToParent && !locals.contains(current.varNmi?.name ?: ""))
                    locals[current.varNmi?.name ?: ""] = current

                is GdPatternList -> {
                    // Pattern binding variables are visible both in the guard (when ...) and in the branch body.
                    // Collect them whenever we encounter the pattern list while walking upwards.
                    PsiTreeUtil.findChildrenOfType(current, GdVarNmi::class.java)
                        .forEach { v -> if (!locals.contains(v.name)) locals[v.name] = v }
                }

                is GdMatchBlock -> {
                    // Be robust: when reaching the match block, also collect bindings from its pattern list
                    PsiTreeUtil.findChildrenOfType(current.patternList, GdVarNmi::class.java)
                        .forEach { v -> if (!locals.contains(v.name)) locals[v.name] = v }
                }

                is GdSetDecl -> {
                    if (movedToParent) {
                        if (!locals.contains(current.varNmi?.name.orEmpty())) locals[current.varNmi?.name.orEmpty()] = current.varNmi!!
                    }
                }

                is GdFuncDeclEx -> {
                    if (movedToParent && !isParam) {
                        current.paramList?.paramList?.forEach { p ->
                            if (!locals.contains(p.varNmi.name)) locals[p.varNmi.name] = p
                        }
                    }
                }

                is GdMethodDeclTl -> {
                    if (onlyLocalScope) {
                        if (!isParam) {
                            current.paramList?.paramList?.forEach { p ->
                                if (!locals.contains(p.varNmi.name)) locals[p.varNmi.name] = p
                            }
                        }
                        if (hitLocal != null) hitLocal.value = true
                        break
                    }
                    if (movedToParent) {
                        current.paramList?.paramList?.forEach { p ->
                            if (!locals.contains(p.varNmi.name)) locals[p.varNmi.name] = p
                        }
                    } else {
                        if (!locals.contains(current.name)) locals[current.name] = current
                    }
                }

                // End of scope
                is GdClassDeclTl -> {
                    if (movedToParent) {
                        break
                    }
                }
            }
        }

        return locals
    }


    @SuppressWarnings()
    fun firstNamedDeclaration(element: PsiElement): PsiElement? {
        return PsiTreeUtil.findFirstParent(element) {
            it is GdClassVarDeclTl
                || it is GdVarDeclSt
                || it is GdConstDeclTl
                || it is GdConstDeclSt
                || it is GdMethodDeclTl
                || it is GdClassDeclTl
                || it is GdParam
        }
    }

    fun firstNamedDeclarationName(element: PsiElement): String? {
        return when (val it = firstNamedDeclaration(element)) {
            is GdClassVarDeclTl -> it.name
            is GdVarDeclSt -> it.name
            is GdConstDeclTl -> it.name
            is GdConstDeclSt -> it.name
            is GdMethodDeclTl -> it.name
            is GdClassDeclTl -> it.name
            is GdParam -> it.varNmi.name
            is GdSignalDeclTl -> it.name
            else -> null
        }
    }


    /**
     * Filters the array to include only elements of type GdMethodDeclTl.
     *
     * @return An array of GdMethodDeclTl elements.
     */
    fun Array<Any>.methods(): Array<GdMethodDeclTl> {
        return this.filterIsInstance<GdMethodDeclTl>().toTypedArray()
    }


    /**
     * Filters out GdMethodsDeclTl
     */
    fun List<Any>.methods(): Array<GdMethodDeclTl> {
        return this.filterIsInstance<GdMethodDeclTl>().toTypedArray()
    }


    /**
     * Filters out GdMethodsDeclTl of constructors
     */
    fun List<PsiElement>.constructors(): Array<GdMethodDeclTl> {
        return this.filterIsInstance<GdMethodDeclTl>().filter { it.isConstructor }.toTypedArray()
    }


    /**
     * Filters out GdClassVarDeclTl
     */
    fun List<PsiElement>.variables(): Array<GdClassVarDeclTl> {
        return this.filterIsInstance<GdClassVarDeclTl>().toTypedArray()
    }


    /**
     * Filters out GdEnumDeclTl
     */
    fun List<PsiElement>.enums(): Array<GdEnumDeclTl> {
        return this.filterIsInstance<GdEnumDeclTl>().toTypedArray()
    }


    /**
     * Filters out GdConstDeclTl
     */
    fun List<PsiElement>.constants(): Array<GdConstDeclTl> {
        return this.filterIsInstance<GdConstDeclTl>().toTypedArray()
    }


    /**
     * Filters out GdSignalDeclTl
     */
    fun List<PsiElement>.signals(): Array<GdSignalDeclTl> {
        return this.filterIsInstance<GdSignalDeclTl>().toTypedArray()
    }


    /**
     * Retrieves the declarations of class members such as methods, variables, signals, enums, constants,
     * or inner classes from a specified class element.
     *
     * @param element The PsiElement representing the class or file whose members are to be listed.
     * @param static Optional parameter that filters the results to include only static members
     * if true, only non-static members if false, or all members if null.
     * @param search Optional filter parameter to search for a specific member by name. If null, all members will be returned.
     * @param constructors If true, includes constructor members in the result; otherwise, filters them out.
     * @param isRecursive If true, includes members from nested classes by traversing recursively.
     * @param includeUnnamedEnumValues If true, includes values from unnamed enums in the result.
     * @return A mutable list of PsiElement representing the class member declarations matching the provided criteria.
     */
    fun listClassMemberDeclarations(
        element: PsiElement,
        static: Boolean? = false,
        search: String? = null,
        constructors: Boolean = false,
        isRecursive: Boolean = false,
        includeUnnamedEnumValues: Boolean = true,
    ): MutableList<PsiElement> {
        if (DumbService.isDumb(element.project)) return mutableListOf()

        val classElement = when (element) {
            is GdFile, is GdClassDeclTl -> element
            else -> GdClassUtil.getOwningClassElement(element)
        }

        val members = mutableListOf<PsiElement>()

        if (search != null) {
            val project = element.project
            val scope = GlobalSearchScope.fileScope(element.containingFile)

            GdMethodDeclIndex.INSTANCE.getScoped(search, project, scope).firstOrNull()?.let {
                if ((((static == null) || (it.isStatic == static)))) {
                    if (constructors || !it.isConstructor) {
                        if (GdClassUtil.getOwningClassElement(it) == classElement) {
                            return mutableListOf(it)
                        }
                    }
                }
            }

            GdConstDeclIndex.INSTANCE.getScoped(search, project, scope).firstOrNull()
                ?.let { if (GdClassUtil.getOwningClassElement(it) == classElement) it else null }?.let { return mutableListOf(it) }

            GdEnumDeclIndex.INSTANCE.getScoped(search, project, scope).firstOrNull()
                ?.let { if (GdClassUtil.getOwningClassElement(it) == classElement) it else null }?.let { return mutableListOf(it) }

            GdSignalDeclIndex.INSTANCE.getScoped(search, project, scope).firstOrNull()
                ?.let { if (GdClassUtil.getOwningClassElement(it) == classElement) it else null }?.let { return mutableListOf(it) }

            GdClassVarDeclIndex.INSTANCE.getScoped(search, project, scope).firstOrNull()
                ?.let { if (GdClassUtil.getOwningClassElement(it) == classElement) it else null }
                ?.let {
                    if (static != true || it.isStatic) {
                        return mutableListOf(it)
                    }
                }

            PsiTreeUtil.getStubChildrenOfTypeAsList(classElement, GdClassDeclTl::class.java).forEach {
                if (it.name == search) return mutableListOf(it)
                if (isRecursive) {
                    members.addAll(listClassMemberDeclarations(it, static, search, constructors = false, isRecursive = true, includeUnnamedEnumValues = includeUnnamedEnumValues))
                    if (members.isNotEmpty()) return members
                }
            }
            if ((classElement is GdClassDeclTl) && !isRecursive) {
                PsiTreeUtil.getStubChildrenOfTypeAsList(classElement.parent, GdClassDeclTl::class.java).forEach {
                    if (it.name == search) return mutableListOf(it)
                    members.addAll(listClassMemberDeclarations(it, static, search, constructors = false, isRecursive = true, includeUnnamedEnumValues = includeUnnamedEnumValues))
                    if (members.isNotEmpty()) return members
                }
            }

            // TODO Create stub for unnamed enums
            // Search in unnamed enums for specific value
            if (includeUnnamedEnumValues) {
                PsiTreeUtil.getStubChildrenOfTypeAsList(classElement, GdEnumDeclTl::class.java).forEach {
                    if (it.name.isBlank()) {
                        it.enumValueList.forEach { value ->
                            if (value.enumValueNmi.name == search) return mutableListOf(value)
                        }
                    }
                }
            }
        } else {
            PsiTreeUtil.getStubChildrenOfTypeAsList(classElement, GdConstDeclTl::class.java).forEach {
                members.add(it)
            }

            PsiTreeUtil.getStubChildrenOfTypeAsList(classElement, GdEnumDeclTl::class.java).forEach {
                if (it.name.isNotBlank()) {
                    members.add(it)
                } else {
                    if (includeUnnamedEnumValues) {
                        it.enumValueList.forEach { value ->
                            members.add(value)
                        }
                    }
                }
            }

            PsiTreeUtil.getStubChildrenOfTypeAsList(classElement, GdSignalDeclTl::class.java).forEach {
                members.add(it)
            }

            PsiTreeUtil.getStubChildrenOfTypeAsList(classElement, GdClassDeclTl::class.java).forEach {
                // For completion and general member listing, include only direct inner classes,
                // not members of their inner trees. Deeper members are reachable after further qualification.
                members.add(it)
            }

            if (classElement is GdClassDeclTl && !isRecursive) {
                PsiTreeUtil.getStubChildrenOfTypeAsList(classElement.parent, GdClassDeclTl::class.java).forEach {
                    members.addAll(listClassMemberDeclarations(it, static, null, false, isRecursive = true, includeUnnamedEnumValues = includeUnnamedEnumValues))
                }
            }

            PsiTreeUtil.getStubChildrenOfTypeAsList(classElement, GdClassVarDeclTl::class.java).forEach {
                if (static != true || it.isStatic) {
                    members.add(it)
                }
            }

            PsiTreeUtil.getStubChildrenOfTypeAsList(classElement, GdMethodDeclTl::class.java).forEach {
                if ((static == null || it.isStatic == static)) {
                    if (constructors || !it.isConstructor) {
                        members.add(it)
                    }
                }
            }
        }

        if (search != null)
            return mutableListOf()

        return members
    }


    /**
     * @param classElement GdClassDecl|GdFile class containing element
     * @param search String|null if looking for a specific declaration
     * @param includeUnnamedEnumValues If true, includes values from unnamed enums
     *
     * @return should search param be not null, returns a matching element?
     */
    private fun addsParentDeclarations(
        classElement: PsiElement,
        result: MutableList<Any>,
        static: Boolean? = false,
        search: String? = null,
        includeUnnamedEnumValues: Boolean = true,
    ): PsiElement? {
        if (DumbService.isDumb(classElement.project)) return null

        val list = listClassMemberDeclarations(
            classElement,
            static,
            search,
            constructors = false,
            isRecursive = false,
            includeUnnamedEnumValues = includeUnnamedEnumValues
        )
        if (search != null)
            return list.firstOrNull()

        result.addAll(list)
        return null
    }


    /**
     * Determines and retrieves the related expression (`GdExpr`) for a given specific `PsiElement` in the context of the
     * PSI tree and its structure.
     *
     * @param element the `PsiElement` for which to evaluate and potentially retrieve the associated expression.
     * @return the corresponding `GdExpr` if the `PsiElement` is part of a recognized structure, or `null` if no valid
     *         expression is found.
     */
    fun calledUpon(element: PsiElement): GdExpr? {
        val getAttrIfAny = fun(el: PsiElement): GdExpr? {
            val previous = PsiTreeUtil.prevVisibleLeaf(el) ?: return null
            val parent = previous.parent ?: return null

            if ((previous.elementType == GdTypes.DOT) && (parent is GdAttributeEx)) {
                // Return the full attribute expression, not just expr
                // This ensures we get xxx.yyy instead of just xxx
                return parent.expr
            }

            return null
        }

        val attr = getAttrIfAny(element)
        if (attr != null) return attr

        val next = PsiTreeUtil.nextVisibleLeaf(element)
        if ((next?.elementType == GdTypes.LRBR) && (next.parent?.elementType == GdTypes.CALL_EX)) {
            return getAttrIfAny(next.parent)
        }

        return null
    }


    /**
     * _GlobalScope has matching variables with classes
     */
    private fun isStaticAccessByName(element: PsiElement, qualifier: GdExpr, typeName: String): Boolean {
        // We consider it static class access when:
        // - The resolved type name equals the qualifier text (e.g., 'Outer'), OR
        // - The resolved type name equals 'FullOwnerId.QualifierText' to handle nested or file-qualified contexts.
        // And we additionally ensure there is no conflicting global variable with the same name in _Global.
        val qualifierText = qualifier.text
        val fullOwnerId = GdClassUtil.getFullClassId(qualifier)
        val looksLikeClassName = (typeName == qualifierText) || (typeName == "$fullOwnerId.$qualifierText")
        return looksLikeClassName && checkGlobalStaticMatch(element, typeName)
    }

    private fun checkGlobalStaticMatch(element: PsiElement, name: String): Boolean {
        val virtualFile = FilenameIndex.getVirtualFilesByName(
            "${GdKeywords.GLOBAL_SCOPE}.gd",
            GlobalSearchScope.allScope(element.project)
        ).firstOrNull() ?: return true
        val psiFile = PsiManager.getInstance(element.project).findFile(virtualFile) ?: return true

        return GdClassVarDeclIndex.INSTANCE.get(
            name,
            element.project,
            GlobalSearchScope.fileScope(psiFile),
        ).isEmpty()
    }


    /**
     * It looks for statements of type checks
     *  if the node is Node3D:
     *  while the next is Node3D:
     * and returns the correct type for hint & validation
     */
    private fun findIsTypeCheck(element: PsiElement): GdIsEx? {
        // TODO It's pretty rough and doesn't check for negatives and such
        return getConditioned(element) { _, stmt ->
            val expr = (stmt as? GdIsEx) ?: PsiTreeUtil.findChildOfType(stmt, GdIsEx::class.java)
            expr?.let { return@getConditioned it }
            null
        }
    }


    /**
     * Looks for statements of has_method
     *  of node[.subnodes].has_method("asd"):
     */
    fun hasMethodCheck(element: PsiElement): Boolean {
        // TODO It's pretty rough and doesn't check for negatives and such
        return getConditioned(element) { el, stmt ->
            val expressions = if (stmt is GdCallEx) listOf(stmt)
            else PsiTreeUtil.findChildrenOfType(stmt, GdCallEx::class.java)

            val hasMethodExpr = expressions.firstOrNull { it.expr.textMatches("has_method") }
            if (hasMethodExpr != null) {
                if (hasMethodExpr.argList?.argExprList?.firstOrNull()?.textMatches("\"${el.text}\"") == true) {
                    return@getConditioned true
                }
            }

            null
        } ?: false
    }


    /**
     * Recursively traverses the parent hierarchy of a given PsiElement and executes a specified action
     * on elements that are instances of GdIfSt, GdWhileSt, or GdElifSt.
     *
     * @param element the initial PsiElement from which the traversal begins
     * @param action a lambda function that takes the current element and an optional PsiElement (typically an expression)
     *               and returns a result of type T, or null if no result is to be returned
     * @return the result of type T from the action if a suitable condition is met, or null if no condition matches
     */
    private fun <T> getConditioned(element: PsiElement, action: (element: PsiElement, stmt: PsiElement?) -> T?): T? {
        val getParent = fun(stmt: PsiElement?): PsiElement? {
            return PsiTreeUtil.getParentOfType(stmt, GdIfSt::class.java, GdWhileSt::class.java, GdElifSt::class.java)
        }

        var parent = getParent(element)
        while (parent != null) {
            when (parent) {
                is GdIfSt -> {
                    val typed = action(element, parent.expr)
                    typed?.let { return it }
                }

                is GdElifSt -> {
                    val typed = action(element, parent.expr)
                    typed?.let { return it }
                    // To avoid matching from base condition that is not part of this suite
                    parent = PsiTreeUtil.getParentOfType(parent, GdIfSt::class.java)
                }

                is GdWhileSt -> {
                    val typed = action(element, parent.expr)
                    typed?.let { return it }
                }
            }

            parent = getParent(parent)
        }

        return null
    }
}
