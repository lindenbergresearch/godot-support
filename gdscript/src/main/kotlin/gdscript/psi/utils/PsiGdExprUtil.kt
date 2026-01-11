package gdscript.psi.utils

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.readText
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.*
import gdscript.GdKeywords
import gdscript.index.impl.GdClassNamingIndex
import gdscript.index.impl.GdFileResIndex
import gdscript.psi.*
import gdscript.reference.GdClassMemberReference
import gdscript.utils.GdExprUtil.left
import gdscript.utils.GdExprUtil.right
import gdscript.utils.GdOperand
import gdscript.utils.PsiFileUtil.toAbsoluteResource
import gdscript.utils.VirtualFileUtil.getPsiFile
import project.psi.model.GdAutoload
import java.nio.file.FileSystems

/**
 * A thread-local storage containing a mutable set of `PsiElement` objects.
 * This is utilized to track the resolution context during operations involving
 * PSI (Program Structure Interface) elements in GdScript.
 *
 * Each thread maintains its own stack, ensuring thread safety for resolution-related
 * operations that require isolated contexts specific to the thread's execution.
 *
 * The initial value of the stack is an empty mutable set, created upon first access
 * in a thread.
 */
private val resolutionStack = ThreadLocal.withInitial { mutableSetOf<PsiElement>() }


/**
 * Executes a given block of code while ensuring that the specified element is not processed more than once within the current resolution stack.
 * This is useful for detecting and preventing cyclic references during resolution processes.
 *
 * @param T The return type of the block.
 * @param element The PSI element that is being checked for cycles.
 * @param block The code block to execute if the element is not already in the resolution stack.
 * @param defaultValue The value to return if a cycle is detected (i.e., the element is already in the resolution stack).
 * @return The result of the executed block if no cycle is detected, or the default value if a cycle is detected.
 */
private inline fun <T> withCycleDetection(element: PsiElement, block: () -> T, defaultValue: T): T {
    val stack = resolutionStack.get()
    if (!stack.add(element)) {
        return defaultValue
    }

    return try {
        block()
    } finally {
        stack.remove(element)
    }
}


/**
 * Represents the location of a PSI (Program Structure Interface) element in a source file.
 * This includes details such as the PSI element, file, line number, text offset, text length,
 * and the file path segments as a list of strings.
 *
 * @property element The PSI element associated with this source location.
 * @property file The file containing the PSI element.
 * @property line The 1-based line number of the source location within the file.
 * @property offset The start offset in the file's text range for the PSI element.
 * @property length The length of the text range of the PSI element.
 * @property path A list representing the segments of the file path.
 */
data class SourceLocation(
    val element: PsiElement,
    val file: PsiFile,
    val line: Int,
    val offset: Int,
    val length: Int,
    val path: List<String> = emptyList(),
) {
    /**
     * Companion object for the SourceLocation class.
     * Provides utility functions to create instances of `SourceLocation`.
     */
    companion object {
        /**
         * Creates a SourceLocation instance from the given PsiElement.
         *
         * @param psiElement the PsiElement from which the SourceLocation is to be created.
         * @return a SourceLocation object representing the location of the PsiElement, including file information, line number, offset, text length, and file path components.
         */
        fun from(psiElement: PsiElement): SourceLocation {
            val file = psiElement.containingFile
            val offset = psiElement.textRange.startOffset
            val line = file.viewProvider.document?.getLineNumber(offset) ?: -1
            val paths: List<String> = file.virtualFile?.path?.split(FileSystems.getDefault().separator) ?: emptyList()

            return SourceLocation(psiElement, file, line + 1, offset, psiElement.textLength, paths)
        }
    }


    /**
     * Returns a shortened representation of the [path] by taking the last `i` elements.
     *
     * @param i the number of last elements from the [path] to include in the returned string. Defaults to 3.
     * @return a string representing the last `i` elements of the [path], joined by a "/" delimiter.
     */
    fun shortPath(i: Int = 3): String {
        return path.takeLast(i).joinToString("/")
    }


    /**
     * Converts the source location information into a concise string representation.
     * The output format includes a shortened file path and the line number.
     *
     * @return A string containing the shortened file path and line number, separated by a colon.
     */
    fun toInfoString(): String = "${shortPath()}:${line}"
}


/**
 * Utility object containing methods for processing and working with GdExpr expressions.
 */
object PsiGdExprUtil {
    /**
     * Determines the return type of a given GdExpr.
     *
     * @param expr the GdExpr whose return type needs to be determined
     * @return the return type of the provided expression as a string
     */
    fun getReturnType(expr: GdExpr): String {
        return getReturnType(expr, allowResource = false)
    }


//    /**
//     * Determines the return-type of a given GdExpr expression and optionally considers resource types.
//     *
//     * @param expr The expression whose return type is to be determined.
//     * @param allowResource If true, resource types are considered when computing the return type. Default is false.
//     * @return The return type as a string. Returns an empty string if the type cannot be determined or the project is in dumb mode.
//     */
//    fun getReturnType(expr: GdExpr, allowResource: Boolean = false): String {
//        if (DumbService.isDumb(expr.project)) {
//            return ""
//        }
//
//        val ret = getReturnType2(expr, allowResource).trim()
//
//        val s = ret.ifEmpty { "<empty>" }
//        var exprText = GdCommonUtil.getFilteredText(expr, 40, "'", 45)
//
//        val javaClass = "class=<${expr.javaClass.simpleName}>".padEnd(35, ' ')
//
//        exprText += javaClass
//
//        val location = SourceLocation.from(expr)
//
//        exprText += "[${location.toInfoString()}]".padEnd(45, ' ')
//        val resPrefix = getResolvedCountFmt()
//
//        //! thisLogger().info("$resPrefix 🔍 * getReturnType of $exprText -> '$s'")
//
//        return ret
//    }

    /**
     * Determines the return type of a given expression.
     *
     * @param expr The expression for which the return type is to be determined.
     * @param allowResource Optional parameter indicating whether resource resolution is allowed.
     * @return The determined return type of the expression as a string.
     */
    fun getReturnType(expr: GdExpr, allowResource: Boolean = false): String {
        if (DumbService.isDumb(expr.project)) {
            return ""
        }

        val ret = withCycleDetection(
            expr,
            {
                val r = when (expr) {
                    is GdFuncDeclEx -> GdKeywords.CALLABLE
                    is GdPlusMinusEx -> expr.expr.returnType
                    is GdCastEx -> fromTyped(expr.typedVal)

                    is GdTernaryEx -> {
                        val a = expr.exprList.getOrNull(0)?.returnType ?: ""
                        val b = expr.exprList.getOrNull(2)?.returnType ?: ""

                        return if (a == b) a else ""
                    }

                    is GdLogicEx -> GdKeywords.BOOL
                    is GdNegateEx -> GdKeywords.BOOL
                    is GdInEx -> GdKeywords.BOOL
                    is GdShiftEx -> GdKeywords.INT
                    is GdBitAndEx -> GdKeywords.INT
                    is GdComparisonEx -> GdOperand.getReturnType(
                        expr.exprList.left(), expr.exprList.right(), expr.operator.text, expr.project,
                    )

                    is GdPlusEx -> GdOperand.getReturnType(
                        expr.exprList.left(), expr.exprList.right(), expr.sign.text, expr.project,
                    )

                    is GdFactorEx -> GdOperand.getReturnType(
                        expr.exprList.left(), expr.exprList.right(), expr.factorSign.text, expr.project,
                    )

                    is GdSignEx -> expr.expr?.returnType ?: ""
                    is GdBitNotEx -> GdKeywords.INT
                    is GdPlusMinusPreEx -> expr.expr?.returnType ?: GdKeywords.INT

                    is GdAttributeEx -> {
                        val ref = expr.refId?.references?.firstOrNull() ?: return ""
                        if (ref is GdClassMemberReference) {
                            val declaration = ref.resolveDeclaration()

                            // In case method is not resolved returnType is method itself
                            if (declaration is GdMethodDeclTl && expr.refId?.nextLeaf()?.elementType == GdTypes.DOT) {
                                return "Callable"
                            }

                            val returnType = GdCommonUtil.returnType(declaration)

                            // If declaration was not found (null) but we have a qualifier expression,
                            // try to infer the member type from the qualifier's type
                            if (returnType.isEmpty() && declaration == null) {
                                val qualifierType = expr.expr.returnType
                                if (qualifierType.isNotEmpty()) {
                                    //         println("   → Trying fallback with qualifierType: '$qualifierType'")
                                    // Look up the member in the qualifier's class
                                    val qualifierClass = GdClassUtil.getClassIdElement(qualifierType, expr)
                                    if (qualifierClass != null) {
                                        val memberName = expr.refId?.text
                                        if (memberName != null) {
                                            val members = GdClassMemberUtil.listClassMemberDeclarations(
                                                qualifierClass,
                                                static = false,
                                                search = memberName,
                                                constructors = false,
                                                isRecursive = false,
                                                includeUnnamedEnumValues = true,
                                            )
                                            val member = members.firstOrNull()
                                            if (member != null) {
                                                val inferredType = GdCommonUtil.returnType(member)
                                                return inferredType
                                            }
                                        }
                                    }
                                }
                            }

                            return returnType
                        } else {
                            // If attribute resolves to a class name or class decl, return its full class id
                            when (val resolved = ref.resolve()) {
                                is GdClassDeclTl -> return GdClassUtil.getFullClassId(resolved)
                                is GdClassNaming -> return GdClassUtil.getFullClassId(resolved)
                            }
                        }

                        return ""
                    }

                    is GdIsEx -> GdKeywords.BOOL

                    is GdCallEx -> {
                        // Detect constructor calls like A.B.new() or simply new() by inspecting the callee name
                        run {
                            val callee = expr.expr
                            val lastId = PsiTreeUtil.getChildrenOfType(callee, GdRefIdRef::class.java)?.lastOrNull()?.text
                            if (lastId == "new") {
                                // Qualified constructor call: the class is the qualifier of the attribute
                                if (callee is GdAttributeEx) {
                                    // Try to resolve the attribute's reference to a class and return its full class id
                                    when (val resolved = callee.refId?.references?.firstOrNull()?.resolve()) {
                                        is GdClassDeclTl -> return GdClassUtil.getFullClassId(resolved)
                                        is GdClassNaming -> return GdClassUtil.getFullClassId(resolved)
                                    }
                                    return GdCommonUtil.returnType(callee.firstChild)
                                }
                                // Unqualified new(): take the attribute parent if any
                                val parentAttr = PsiTreeUtil.getParentOfType(expr, GdAttributeEx::class.java)
                                parentAttr?.let { return GdCommonUtil.returnType(it.firstChild) }
                                return ""
                            }
                        }

                        run {
                            val method = expr.expr.text
                            if ((method == "get_node") || (method == "get_node_or_null") || (method == "get_first_node_in_group")) {
                                //TODO try to parse Node from .tscn
                                return expr.expr.returnType
                            } else if (method == "get_nodes_in_group") {
                                return "Array[Variant]"
                            } else if (method == "instantiate") {
                                return GdKeywords.VARIANT
                            } else if (method == "get_child") {
                                return "Node"
                            } else if (method == "get_parent") {
                                //TODO try to parse Node from .tscn
                                return "Node"
                            } else {
                                if ((method == "load") || (method == "preload")) {
                                    val res = expr.argList?.argExprList?.firstOrNull()
                                    if (res != null) {
                                        var resource = res.text.trim('"', '\'')
                                        if (!resource.startsWith("res://") && (expr.containingFile.originalFile.virtualFile?.parent != null)) {
                                            resource = resource.toAbsoluteResource(expr, expr.project)
                                        }

                                        val t = resolveResourceType(resource, expr.project)
                                        t?.let { return it }

                                        GdFileResIndex.getFiles(resource, expr)
                                            .firstOrNull()
                                            ?.getPsiFile(expr)
                                            ?.let { GdClassUtil.getOwningClassName(it) }
                                            ?.let {
                                                return it
                                            }
                                    }

                                    return expr.expr.returnType
                                }
                            }

                            expr.expr.returnType
                        }
                    }

                    is GdArrEx -> {
                        val exprType = expr.exprList.firstOrNull()?.returnType ?: return GdKeywords.VARIANT
                        if (exprType.startsWith("Array[") || exprType.startsWith("Dictionary["))
                            return fromTyped(exprType)
                        return GdOperand.getReturnType(exprType, GdKeywords.INT, "[]", expr.project)
                    }

                    is GdPrimaryEx -> {
                        when (expr.firstChild) {
                            is GdNodePath -> {
                                if (expr.firstChild.text.contains(':')) return GdKeywords.VARIANT
                                val node = GdNodeUtil.findNode(expr.firstChild as GdNodePath) ?: return GdKeywords.VARIANT

                                node.script?.let { str ->
                                    GdFileResIndex.getFiles(str, expr).firstOrNull()
                                        ?.getPsiFile(expr)
                                        ?.let { GdClassUtil.getFullClassId(it) }
                                        ?.let {
                                            if (!it.startsWith("\"res://") || allowResource) return it
                                        }
                                }

                                return node.element.type
                            }

                            is GdDictDecl -> {
                                return "Dictionary[Variant, Variant]"
                            }

                            is GdArrayDecl -> {
                                //TODO: check if it's Array[Variant] or Array[T]' -------------------
                                var type = ""
                                expr.arrayDecl?.exprList?.forEach {
                                    val itType = it.returnType
                                    type = if ((type == itType) || (type == "")) {
                                        itType
                                    } else {
                                        "Array"
                                    }
                                }
                                type = if (type.isNotEmpty() && (type != "Array")) {
                                    "Array[$type]"
                                } else {
                                    "Array[Variant]"
                                }

                                return type
                                //TODO: check if it's Array[Variant] or Array[T]' -------------------
                            }

                            else -> {
                                expr.expr?.returnType ?: ""
                            }
                        }
                    }

                    is GdLiteralEx -> {
                        val text = expr.text
                        when (text) {
                            GdKeywords.TRUE -> return GdKeywords.BOOL
                            GdKeywords.FALSE -> return GdKeywords.BOOL
                            GdKeywords.NULL -> return GdKeywords.NULL
                            GdKeywords.NAN -> return "nan"
                            GdKeywords.INF -> return "inf"
                        }

                        when (expr.firstChild?.elementType) {
                            GdTypes.NUMBER -> {
                                if (text.startsWith("0b")) {
                                    return GdKeywords.INT
                                } else if (text.startsWith("0x")) {
                                    return GdKeywords.INT
                                } else if (text.contains('e') || text.contains('.')) {
                                    return GdKeywords.FLOAT
                                }

                                return GdKeywords.INT
                            }

                            GdTypes.STRING_VAL_NM -> {
                                return GdKeywords.STRING
                            }

                            GdTypes.STRING -> {
                                return GdKeywords.STRING
                            }

                            GdTypes.STRING_NAME -> {
                                return GdKeywords.STRING_NAME
                            }

                            GdTypes.NODE_PATH -> {
                                return GdKeywords.STRING
                            }

                            GdTypes.NODE_PATH_LIT -> {
                                return GdKeywords.NODE_PATH
                            }

                            GdTypes.REF_ID_NM -> {
                                if (text == GdKeywords.SELF) {
                                    return GdClassUtil.getOwningClassName(expr)
                                } else if (text == GdKeywords.SUPER) {
                                    // TODO this can return nesting... :/ Losos.InnerClass -> somehow it must be parsed
                                    return GdInheritanceUtil.getExtendedClassId(expr)
                                }

                                if (DumbService.isDumb(expr.project)) {
                                    return ""
                                }

                                val named: GdRefIdRef = expr.refIdNm ?: return ""
                                return when (val element = GdClassMemberUtil.findDeclaration(named)) {
                                    is GdClassVarDeclTl -> parseLoadedType(expr, element.returnType)
                                    is GdVarDeclSt -> parseLoadedType(expr, element.returnType)
                                    is GdConstDeclTl -> parseLoadedType(expr, element.returnType)
                                    is GdConstDeclSt -> parseLoadedType(expr, element.returnType)

                                    is GdMethodDeclTl -> {
                                        if (PsiTreeUtil.nextVisibleLeaf(expr)?.elementType == GdTypes.LRBR)
                                            parseLoadedType(expr, element.returnType)
                                        else "Callable"
                                    }

                                    is GdParam -> parseLoadedType(expr, element.returnType)
                                    is GdSignalDeclTl -> "Signal"

                                    is GdEnumDeclTl -> {
                                        // Delegate to GdCommonUtil for consistency
                                        GdCommonUtil.returnType(element)
                                    }

                                    is GdEnumValue -> {
                                        GdKeywords.INT
                                    }

                                    is GdClassNaming -> text

                                    is GdForSt -> {
                                        if (element.typed != null) {
                                            return fromTyped(element.typed)
                                        }

                                        val forExpr = element.expr?.returnType ?: ""
                                        return if (forExpr.startsWith("Array")) {
                                            GdOperand.getReturnType(forExpr, GdKeywords.INT, "[]", expr.project)
                                        } else {
                                            forExpr
                                        }
                                    }

                                    is GdAutoload -> element.key
                                    is GdClassDeclTl -> GdClassUtil.getFullClassId(element)
                                    else -> ""
                                }
                            }

                            else -> return ""
                        } // <- expr.firstChild?.elementType
                    } // <- GdLiteralEx

                    else -> ""
                }

                return r
            },
            ""
        )

        return ret
    }


    /**
     * Resolves the resource type of the file specified by the given resource path within the context of the provided project.
     *
     * The method identifies the type of resource based on the file's extension and content:
     * - For `.tres` files, it extracts the type from the resource definition in the content.
     * - For `.tscn` files, it returns "PackedScene".
     * - For `.gd` files, it resolves the class name using GdFile and associated utilities.
     *
     * @param resourcePath The path of the resource to resolve. This can be a `.tres`, `.tscn`, or `.gd` file.
     * @param project The project context in which the resource resides.
     * @return The resolved resource type as a string, or null if it cannot be determined.
     */
    private fun resolveResourceType(resourcePath: String, project: Project): String? {
        val files = GdFileResIndex.getFiles(resourcePath, project)
        val file = files.firstOrNull() ?: return null

        // for .tres file: do parse Resource-Definition
        if (resourcePath.endsWith(".tres")) {
            val content = file.readText()
            // search for [resource] or [ext_resource] sections
            // and extract the type from the first line
            val typeMatch = """type\s*=\s*"([^"]+)"""".toRegex().find(content)
            return typeMatch?.groupValues?.get(1)
        }

        // handle ".tscn" file
        if (resourcePath.endsWith(".tscn")) {
            return "PackedScene"
        }

        // handle gd script files
        if (resourcePath.endsWith(".gd")) {
            // resolve class_name
            val gdFile = file.getPsiFile(project) as? GdFile
            return gdFile?.let { GdClassUtil.getFullClassId(it) } ?: resourcePath
        }

        return null
    }


    /**
     * Converts a `GdTyped` object into a `String` representation by retrieving and formatting its text.
     * Removes leading and trailing colon or space characters from the text. Returns an empty string
     * if the input is null.
     *
     * @param typed The `GdTyped` object to convert, which may be null.
     * @return The formatted text from the `GdTyped` object as a `String`, or an empty string if `typed` is null.
     */
// TODO unify with doc builder
    fun fromTyped(typed: GdTyped?): String {
        return typed?.text?.trim(':', ' ') ?: ""
    }

    /**
     * Determines and returns the return type of a given PsiElement or its parent class's return type
     * based on specific conditions involving the PSI tree structure.
     *
     * @param element The PsiElement for which the return type needs to be resolved.
     * Must be an instance of GdRefIdRef and meet specific parent-child relationships
     * in the PSI element tree to return a valid type.
     * @return A String representing the resolved return type of the PsiElement, if applicable.
     * Returns null if the conditions to resolve the return type are not met.
     */
    fun getAttrOrCallParentClass(element: PsiElement): String? {
        if (element is GdRefIdRef
            && element.parent != null
            && element.parent is GdLiteralEx
        ) {
            val root = element.parent.parent ?: return null
            if (root is GdAttributeEx && element.parent.prevSibling != null) {
                return GdCommonUtil.returnType(root.firstChild)
            }
            if (root is GdCallEx && root.prevSibling != null && root.parent is GdAttributeEx) {
                return GdCommonUtil.returnType(root.parent.firstChild)
            }
        }

        return null
    }

    /**
     * Resolves a PsiFile from a given PsiElement by determining its associated class and
     * searching for the corresponding file in the project scope. If the class name starts
     * with "Array" or "Dictionary", it will normalize the name to "Array" or "Dictionary", respectively.
     *
     * @param element The PsiElement from which the associated PsiFile is to be resolved.
     * @return The resolved PsiFile associated with the given element, or null if no associated file is found.
     */
    fun getAttrOrCallParentFile(element: PsiElement): PsiFile? {
        var className = getAttrOrCallParentClass(element) ?: return null
        if (className.startsWith("Array")) {
            className = "Array"
        } else if (className.startsWith("Dictionary")) {
            className = "Dictionary"
        }

        return GdClassNamingIndex.INSTANCE.get(className, element.project, GlobalSearchScope.allScope(element.project))
            .firstOrNull()?.containingFile
    }

    /**
     * Parses and extracts a type name from a given typed string based on specific rules.
     * It supports derived types such as arrays and dictionaries and provides a default
     * fallback to the `GdKeywords.VARIANT` type if no matching rule applies.
     *
     * @param typed A string representing a typed value, such as "Array[<type>]"
     *              or "Dictionary[<key>,<value>]".
     * @return The extracted inner type as a string if applicable; otherwise,
     *         returns a default value (`GdKeywords.VARIANT`).
     */
    private fun fromTyped(typed: String): String {
        if (typed.startsWith("Array")) {
            return typed.substring(5).trim('[', ']')
        }

        if (typed.startsWith("Dictionary")) {
            val inside = typed.substring(10).trim('[', ']')
            return inside.split(",").map { it.trim() }.last()
        }

        return GdKeywords.VARIANT
    }

    /**
     * Extracts the main type or array element type from the given `GdTypedVal` object.
     * If the `GdTypedVal` is null, or its type is not "Array", the primary type
     * is returned. In the case of "Array", the nested type within the array is returned.
     *
     * @param typed The `GdTypedVal` instance containing type information. Can be nullable.
     * @return A string representing the primary type or the array's element type. Returns an empty string if `typed` is null.
     */
    fun fromTyped(typed: GdTypedVal?): String {
        if (typed == null) return ""

        val main = typed.typeHintList.first().text
        if (main != "Array") {
            return main
        }

        return typed.typeHintList.last().text
    }

    /**
     * Parses the given type from the provided PSI element and attempts to resolve a more specific type
     * if applicable, based on class member declarations.
     *
     * @param element The PSI element to inspect and retrieve type information from.
     * @param type The initial type string to parse and potentially refine.
     * @return A resolved type as a string if a specific declaration is found,
     *         otherwise returns the original type.
     */
    private fun parseLoadedType(element: PsiElement, type: String): String {
        GdClassMemberUtil.listDeclarations(
            element,
            type,
            onlyLocalScope = false,
            ignoreParents = true,
            ignoreGlobalScope = true,
            allowResource = true
        ).firstOrNull()
            ?.let { (it as? PsiElement)?.let { it1 -> return GdCommonUtil.returnType(it1) } }

        return type
    }

}
