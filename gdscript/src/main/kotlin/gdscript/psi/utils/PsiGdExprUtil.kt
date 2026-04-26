package gdscript.psi.utils

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.readText
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.*
import com.intellij.util.containers.tail
import fleet.multiplatform.shims.currentThreadId
import gdscript.GdKeywords
import gdscript.index.impl.GdClassNamingIndex
import gdscript.index.impl.GdFileResIndex
import gdscript.psi.*
import gdscript.psi.impl.*
import gdscript.psi.utils.AnsiHelper.toSubscriptNumbers
import gdscript.psi.utils.StringHelper.objToHexString
import gdscript.psi.utils.StringHelper.toHexString
import gdscript.reference.GdClassMemberReference
import gdscript.utils.GdExprUtil.left
import gdscript.utils.GdExprUtil.right
import gdscript.utils.GdOperand
import gdscript.utils.PsiElementUtil.psi
import gdscript.utils.PsiFileUtil.toAbsoluteResource
import gdscript.utils.VirtualFileUtil.getPsiFile
import kotlinx.html.emptyMap
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


/** String helper methods - TODO: shound be moved to some util class or something! */

fun String?.symbolizeWS(colorize: Boolean = true): String {
    if (this == null) return "null"
    val t = this
        .replace("\r\n", "↵")
        .replace('\r', '·')
        .replace('\n', '↵')
        .replace('\t', '⇥')
        .replace(' ', '·')
//        .replace(Regex("\\s+"), "#")
        .trim()

    if (colorize) {
        return t
            .replace("↵", Ansi.BRIGHT_BLUE + "↵" + Ansi.RESET)
            .replace("·", Ansi.BRIGHT_BLUE + "·" + Ansi.RESET)
            .replace("⇥", Ansi.BRIGHT_BLUE + "⇥" + Ansi.RESET)
    }

    return t
}

fun String?.limit(n: Int): String {
    if (this == null) {
        return "null"
    }
    if (length <= n) {
        return this
    }

    return this.take(n - 1) + "…"

}

fun String?.limitPad(n: Int, removeWs: Boolean = true, doRight: Boolean = false): String {
    if (this == null) return if (doRight) "NULL".padStart(n) else "NULL".padEnd(n)

    var s = this.trim()

    if (removeWs) {
        s = s.replace(Regex("\\s+"), " ")
    }

    if (s.isEmpty()) return if (doRight) s.padStart(n) else s.padEnd(n)

    if ((n > 1) && (this.length > n)) {
        return s.take(n - 1) + "…"
    }

    return if (doRight) s.padStart(n) else s.padEnd(n)
}


fun List<String>.dropPrefix(prefix: String): List<String> {
    if (this.isEmpty()) return emptyList()
    if (!this.contains(prefix)) return emptyList()

    return this.dropWhile { elem -> elem != prefix }.tail()
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
    val lineOffset: Int,
    val length: Int,
    val lineText: String,
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
         * @param element the PsiElement from which the SourceLocation is to be created.
         * @return a SourceLocation object representing the location of the PsiElement, including file information, line number, offset, text length, and file path components.
         */
        fun from(element: PsiElement): SourceLocation {
            val file = element.containingFile
            val offset = element.textRange.startOffset
            val line = file.viewProvider.document?.getLineNumber(offset) ?: -1
            val paths: List<String> = file.virtualFile?.path?.split(FileSystems.getDefault().separator) ?: emptyList()

            val document = element.containingFile.viewProvider.document

            val start = document.getLineStartOffset(line)
            val end = document.getLineEndOffset(line)
            val lineText = document.getText(TextRange(start, end))

            val lineOffs = offset - document.getLineStartOffset(line)

            return SourceLocation(element, file, line + 1, offset, lineOffs, element.textLength, lineText, paths)
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
    fun toInfoString(): String = "${shortPath(1)}:${line}:$lineOffset"

    fun toShortString(): String = "${line}:$lineOffset"
}


/* ----------------------------------------------------------------------- */

/**
 * Utility object containing methods for processing and working with GdExpr expressions.
 */
object PsiGdExprUtil {

    var lock: Boolean = false
    var lock2: Boolean = false


    /**
     * Determines the return type of given GdExpr.
     *
     * @param expr the GdExpr whose return type needs to be determined
     * @return the return type of the provided expression as a string
     */
    fun getReturnType(expr: GdExpr): String {
        return getReturnType(expr, allowResource = false)
    }


    /**
     * Determines the return type of given expression.
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
                    is GdCastEx -> extractSubtype(expr.typedVal)

                    is GdTernaryEx -> {
                        val a = expr.exprList.getOrNull(0)?.returnType ?: ""
                        val b = expr.exprList.getOrNull(2)?.returnType ?: ""

                        return if (a == b) a else GdKeywords.VARIANT
                    }

                    is GdLogicEx, is GdNegateEx, is GdInEx -> GdKeywords.BOOL
                    is GdShiftEx, is GdBitAndEx -> GdKeywords.INT

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
                                return GdKeywords.CALLABLE
                            }

                            val returnType = GdCommonUtil.returnType(declaration)

                            // If declaration was not found (null), first try dictionary-path resolution.
                            // This is needed for expressions like `file.visuals.resource_packs` where
                            // intermediate keys are stored in a predefined dictionary PSI structure.
                            if (returnType.isEmpty() && declaration == null) {
                                val dictElement = resolveDictPathElement(expr)
                                val dictType = inferTypeFromPsi(dictElement)
                                if (dictType.isNotEmpty()) {
                                    return dictType
                                }

                                val qualifierType = expr.expr.returnType
                                if (qualifierType.isNotEmpty()) {
                                    val qualifierClass = GdClassUtil.getClassIdElement(qualifierType, expr, expr.project)
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
                            if (lastId == GdKeywords.NEW) {
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
                            if ((method == GdKeywords.METHOD_GET_NODE) || (method == GdKeywords.METHOD_GET_NODE_OR_NULL) || (method == GdKeywords.METHOD_GET_FIRST_NODE_IN_GROUP)) {
                                //TODO: try to parse Node from .tscn
                                return expr.expr.returnType
                            } else if (method == GdKeywords.METHOD_GET_NODES_IN_GROUP) {
                                return GdKeywords.ARRAY_OF_VARIANT
                            } else if (method == GdKeywords.METHOD_INSTANTIATE) {
                                return GdKeywords.VARIANT
                            } else if (method == GdKeywords.METHOD_GET_CHILD) {
                                return GdKeywords.CLASS_NODE
                            } else if (method == GdKeywords.METHOD_GET_PARENT) {
                                //TODO: try to parse Node from .tscn
                                return GdKeywords.CLASS_NODE
                            } else {
                                if ((method == GdKeywords.LOAD) || (method == GdKeywords.PRELOAD)) {

                                    val res = expr.argList?.argExprList?.firstOrNull()
                                    if (res != null) {
                                        var resource = res.text.trim('"', '\'')
                                        if (!resource.startsWith(GdKeywords.RESOURCE_PREFIX) && (expr.containingFile.originalFile.virtualFile?.parent != null)) {
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
                            return extractSubtype(exprType)
                        return GdOperand.getReturnType(exprType, GdKeywords.INT, "[]", expr.project)
                    }

                    is GdPrimaryEx -> {
                        when (expr.firstChild) {

                            /* --- ---------------------------------------------------------------------------------*/
                            /* --- ---------------------------------------------------------------------------------*/

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

                            /* --- ---------------------------------------------------------------------------------*/
                            /* --- ---------------------------------------------------------------------------------*/

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
                                expr.expr?.returnType ?: GdKeywords.VARIANT
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
                                val resolved = GdClassMemberUtil.findDeclaration(named)

                                if (resolved == null) {
                                    val dictContext = PsiTreeUtil.getParentOfType(named, GdAttributeEx::class.java, false) ?: named
                                    val dictElement = resolveDictPathElement(dictContext)
                                    val dictType = inferTypeFromPsi(dictElement)
                                    if (dictType.isNotEmpty()) {
                                        return dictType
                                    }
                                }

                                return when (val element = resolved) {
                                    is GdClassVarDeclTl -> parseLoadedType(expr, element.returnType)
                                    is GdVarDeclSt -> parseLoadedType(expr, element.returnType)
                                    is GdConstDeclTl -> parseLoadedType(expr, element.returnType)
                                    is GdConstDeclSt -> parseLoadedType(expr, element.returnType)

                                    is GdMethodDeclTl -> {
                                        if (PsiTreeUtil.nextVisibleLeaf(expr)?.elementType == GdTypes.LRBR)
                                            parseLoadedType(expr, element.returnType)
                                        else GdKeywords.CALLABLE
                                    }

                                    is GdParam -> parseLoadedType(expr, element.returnType)
                                    is GdSignalDeclTl -> GdKeywords.SIGNAL

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
                                            return extractSubtype(element.typed)
                                        }

                                        val forExpr = element.expr?.returnType ?: ""
                                        if (forExpr.startsWith(GdKeywords.ARRAY)) {
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

        // for *.tres resource file: do parse Resource-Definition
        if (GdKeywords.isResourceFileName(resourcePath)) {
            val content = file.readText()
            // search for [resource] or [ext_resource] sections
            // and extract the type from the first line
            val typeMatch = """type\s*=\s*"([^"]+)"""".toRegex().find(content)
            return typeMatch?.groupValues?.get(1)
        }

        // handle *.tscn scene file
        if (GdKeywords.isSceneFileName(resourcePath)) {
            return GdKeywords.PACKED_SCENE
        }

        // handle gd script files
        if (GdKeywords.isGDScriptFileName(resourcePath)) {
            // resolve class_name
            val gdFile = file.getPsiFile(project) as? GdFile
            return gdFile?.let { GdClassUtil.getFullClassId(it) } ?: resourcePath
        }

        return null
    }


    /**
     * Determines and returns the return type of given PsiElement or its parent class's return type
     * based on specific conditions involving the PSI tree structure.
     *
     * @param element The PsiElement for which the return type needs to be resolved.
     * Must be an instance of GdRefIdRef and meet specific parent-child relationships
     * in the PSI element tree to return a valid type.
     * @return A String representing the resolved return type of the PsiElement, if applicable.
     * Returns null if the conditions to resolve the return type are not met.
     */
    fun getAttrOrCallParentClass(element: PsiElement): String? {
        if ((element is GdRefIdRef) && (element.parent != null) && (element.parent is GdLiteralEx)) {
            val root = element.parent.parent ?: return null
            if ((root is GdAttributeEx) && (element.parent.prevSibling != null)) {
                return GdCommonUtil.returnType(root.firstChild)
            }

            if ((root is GdCallEx) && (root.prevSibling != null) && (root.parent is GdAttributeEx)) {
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
        if (className.startsWith(GdKeywords.ARRAY)) {
            className = GdKeywords.ARRAY
        } else if (className.startsWith(GdKeywords.DICTIONARY)) {
            className = GdKeywords.DICTIONARY
        }

        return GdClassNamingIndex.INSTANCE.get(className, element.project, GlobalSearchScope.allScope(element.project))
            .firstOrNull()?.containingFile
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
    fun extractSubtype(typed: GdTyped?): String {
//        if (typed == null) return ""
//
//        val returnsType = typed.typedVal.returnType
//        val typeHintList = typed.typedVal.typeHintList.joinToString(", ", "[", "]")
//        val sourceLoc = SourceLocation.from(typed)
//        val prefix =
//            Ansi.BRIGHT_BLACK + "[" +
//                typed.objToHexString() + "/" +
//                currentThreadId().toHexString("<", "h>", uppercase = false) +
//                "]" + Ansi.RESET
//
//        val underWaved: String =
//            " ".repeat(sourceLoc.lineOffset) + Ansi.BRIGHT_RED +
//                "~".repeat(typed.text.length) + Ansi.RESET
//
//        while (lock2) Thread.sleep(1)
//
//        lock2 = true
//        println("$prefix ------------------------------------------------------------------")
//        println("$prefix element type   : ${typed.javaClass.simpleName}")
//        println("$prefix return type    : $returnsType")
//        println("$prefix type-hint list : $typeHintList")
//        println("$prefix element text   : '${typed.text.symbolizeWS()}'")
//        println("$prefix line number    : ${sourceLoc.toInfoString()}")
//        println("$prefix line text      : '${sourceLoc.lineText.symbolizeWS()}'")
//        println("$prefix                   $underWaved \n")
//        lock2 = false

        return typed?.text?.trim(':', ' ') ?: ""
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
    private fun extractSubtype(typed: String): String {
        if (typed.startsWith(GdKeywords.ARRAY)) {
            return typed.substring(GdKeywords.ARRAY.length).trim('[', ']')
        }

        if (typed.startsWith(GdKeywords.DICTIONARY)) {
            val inside = typed.substring(GdKeywords.DICTIONARY.length).trim('[', ']')
            return inside.split(",").asSequence().map { it.trim() }.last()
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
    fun extractSubtype(typed: GdTypedVal?): String {
        if (typed == null) return ""

        val main = typed.typeHintList.first().text
        if (main != GdKeywords.ARRAY) {
            return main
        }

        return typed.typeHintList.last().text
    }


    /**
     * 
     */
    fun listChildren2(
        element: PsiElement,
        markElement: PsiElement = element,
        filter: String = "",
        maxElements: Int = 999,
        maxLevel: Int = 15,
        indent: String = "  ",
        whiteList: List<PsiElement> = emptyList(),
        backList: List<PsiElement> = emptyList()
    ): List<PsiElement> {

        val maxLines: Int = 4096
        var i = 0
        val t0 = System.nanoTime()
        var tRun = System.nanoTime()

        val rules = mapOf(
            //"""Global(.gd)?""".toRegex() to (Ansi.YELLOW + Ansi.BOLD),
            """0+[xX][a-fA-F0-9]+""".toRegex() to Ansi.CYAN,
            "#\\d+".toRegex() to Ansi.BRIGHT_WHITE,
            "[$%][a-zA-Z0-9_./-]+".toRegex() to Ansi.GREEN,
            """[a-zA-Z0-9_]+\.gd""".toRegex() to Ansi.YELLOW + Ansi.ITALIC,

            """([«»|?])""".toRegex() to Ansi.BRIGHT_RED + Ansi.BOLD,
            """([•;])""".toRegex() to Ansi.BRIGHT_BLACK,
            """([()⟨⟩])""".toRegex() to Ansi.CYAN,
            """[⮕⬅]""".toRegex() to Ansi.BRIGHT_RED + Ansi.BOLD,
            """([\[\]])""".toRegex() to Ansi.CYAN,
            """[↵·⇥…]""".toRegex() to Ansi.BRIGHT_BLUE + Ansi.BOLD,

            "==>".toRegex() to Ansi.RED_BOLD,
            "Error".toRegex() to Ansi.RED_BOLD,
            "\\s+⟶".toRegex() to (Ansi.WHITE + Ansi.BOLD),
            Regex("\"([^\"]+)\"(?=\\s*)") to (Ansi.CYAN + Ansi.BOLD),

            """\bString\b""".toRegex() to (Ansi.CYAN + Ansi.BOLD),
            """\bres://.+\b""".toRegex() to (Ansi.CYAN + Ansi.BOLD),
            """\bnull\b""".toRegex() to (Ansi.BRIGHT_RED + Ansi.BOLD + Ansi.ITALIC),
            """\bint\b""".toRegex() to (Ansi.BLUE + Ansi.BOLD),
            """\bvoid\b""".toRegex() to (Ansi.BRIGHT_RED + Ansi.BOLD),
            """\bArray""".toRegex() to (Ansi.CYAN + Ansi.BOLD),
            """\bDictionary\b""".toRegex() to (Ansi.CYAN + Ansi.BOLD),
            """\bVariant\b""".toRegex() to (Ansi.BRIGHT_RED + Ansi.BOLD),
            """\breturn\b""".toRegex() to (Ansi.BRIGHT_RED + Ansi.ITALIC),

            """([└╰├─│])""".toRegex() to (Ansi.BRIGHT_BLACK + Ansi.BOLD),
            """([₀-₉⁰-⁹])""".toRegex() to (Ansi.BRIGHT_WHITE),

            """GdKeyValueImpl""".toRegex() to (Ansi.BRIGHT_BLUE + Ansi.BOLD),
            """GdLiteralExImpl""".toRegex() to (Ansi.BRIGHT_GREEN + Ansi.BOLD),
            """GdStringValRefImpl""".toRegex() to (Ansi.BRIGHT_GREEN + Ansi.BOLD),
            """GdPrimaryExImpl""".toRegex() to (Ansi.BRIGHT_RED + Ansi.BOLD),
            """GdDictDeclImpl""".toRegex() to (Ansi.BRIGHT_YELLOW + Ansi.BOLD),
            """GdArrayDeclImpl""".toRegex() to (Ansi.BRIGHT_MAGENTA + Ansi.BOLD),
            """GdFile""".toRegex() to (Ansi.BRIGHT_RED + Ansi.BOLD + Ansi.ITALIC),

            """resolveClassVarDeclaration""".toRegex() to (Ansi.BRIGHT_CYAN + Ansi.BOLD + Ansi.ITALIC),
            """resolveDictionary""".toRegex() to (Ansi.BRIGHT_WHITE + Ansi.BOLD + Ansi.ITALIC),
            """findDeclaration""".toRegex() to (Ansi.BRIGHT_WHITE + Ansi.BOLD + Ansi.ITALIC),
            """calledUpon""".toRegex() to (Ansi.BRIGHT_WHITE + Ansi.BOLD + Ansi.ITALIC),

            """text=""".toRegex() to (Ansi.BRIGHT_BLUE + Ansi.BOLD + Ansi.ITALIC),
            """expr=""".toRegex() to (Ansi.BRIGHT_BLUE + Ansi.BOLD + Ansi.ITALIC),
            """exprType=""".toRegex() to (Ansi.BRIGHT_BLUE + Ansi.BOLD + Ansi.ITALIC),
            """name=""".toRegex() to (Ansi.BRIGHT_BLUE + Ansi.BOLD + Ansi.ITALIC),
            """file=""".toRegex() to (Ansi.BRIGHT_BLUE + Ansi.BOLD + Ansi.ITALIC),

            """(GdAttributeExImpl|GdRefIdRefImpl|ROM_PACK_NAME)""".toRegex() to (Ansi.BRIGHT_BLUE + Ansi.BOLD),
        )

        val highlightRules = mapOf(
            """-> Dictionary""".toRegex() to (Ansi.BG_CYAN),
        )

        val whiteTypes: Set<Class<out PsiElement>> = whiteList.asSequence().map { it.javaClass }.toSet()
        val blackTypes: Set<Class<out PsiElement>> = backList.asSequence().map { it.javaClass }.toSet()

        fun allowed(e: PsiElement): Boolean {
            val cls = e.javaClass
            if (whiteTypes.isNotEmpty() && whiteTypes.none { it.isAssignableFrom(cls) }) return false
            if (blackTypes.isNotEmpty() && blackTypes.any { it.isAssignableFrom(cls) }) return false
            return !(!filter.isEmpty() && e.text.contains(filter))
        }

        fun typeName(e: PsiElement): String {
            var s = e.javaClass.simpleName.ifBlank { e.javaClass.name.substringAfterLast('.') }

            if (e is GdExpr) s = "!$s"

            if (e.children.size > 0)
                s += "(${e.children.size})"

            if ((e is GdExpr) /*&& !e.children.isEmpty()*/) {
                val types = e.getReturnType().limit(45)

                if (!types.isEmpty())
                    s += " -> $types"
            }


            return s
        }

        fun truncText(s: String): String {
            val t = s
                .replace("\r\n", "↵")
                .replace('\r', '·')
                .replace('\n', '↵')
                .replace('\t', '⇥')
                .replace(' ', '·')
                .replace(Regex("\\s+"), "#")
                .trim()
            return t
        }

        fun truncText(e: PsiElement, max: Int = 150): String {
            val t = truncText((e.text ?: "<EMPTY>")).trim()
            return if (t.length <= max) "«$t»" else "«" + t.take(max - 1) + "…»"
        }


        val out = ArrayList<PsiElement>(1024)

        fun printNode(e: PsiElement, prefix: String, isLast: Boolean, size: Int, index: Int, level: Int) {
            if (level > maxLevel) return

            val t = System.nanoTime() - tRun
            tRun = System.nanoTime()

            val sourceLoc = SourceLocation.from(e)
            val tRunStr = "%6.2fms".format(t.toDouble() / 10e5)
            val isMarked = e == markElement

            val children = e.children
            val s = if (size == 1) "└" else "╰"
            val connector = (if (isLast) "${s}─" else "├─")
            val padSize = if (size > 9) 4 else 2


            val line = buildString {
                append(prefix)
                append(connector)
                append(index.toString().toSubscriptNumbers().padEnd(padSize, '─'))
                var pad = ((((100 - prefix.length) + indent.length) - padSize))
                if (pad < 0) pad = 0

                if (index > maxElements || (i > maxLines)) {
                    append("...".padEnd(pad))
                } else {
                    append((typeName(e)).padEnd(pad))
                    append("⟶ ${truncText(e)}".limitPad(60))
                    append("line:")
                    append(sourceLoc.line.toString().limitPad(4, removeWs = false, doRight = true))

                    val calledUpon = GdClassMemberUtil.calledUpon(e).toString().limitPad(40)
                    val declr = GdClassMemberUtil.findDeclaration(e).toString().limitPad(40)

                    val x = when (element) {
                        is GdExpr -> element.returnType
                        else -> ""
                    }

                    val dic = resolveDictionary(e).toString().limitPad(20)
                    val vvar = resolveVarDeclaration(e)
                    val cvar = resolveClassVarDeclaration(e)

                    append(" calledUpon($calledUpon)")
                    append(" findDeclaration($declr)")

                    if (x.isNotEmpty()) {
                        append(" returnType($x)")
                    }

                    if (dic.isNotEmpty()) {
                        append(" resolveDictionary($dic)")
                    }


                    if (vvar != null) {
                        val ex = vvar.expr?.toString() ?: ""
                        val fname = vvar.containingFile.name
                        append(
                            " resolveVarDeclaration(file=${fname.limitPad(20)} name=«${vvar.name.limitPad(15)}» | text=${truncText(vvar).limitPad(40)} | expr=${
                                truncText(ex).limitPad(45)
                            } | exprType=«${vvar.expr?.returnType?.limitPad(20)}»)"
                        )
                    }


                    if (cvar != null) {
                        val ex = cvar.expr?.toString() ?: ""
                        val fname = cvar.containingFile.name
                        append(
                            " resolveClassVarDeclaration(file=${fname.limitPad(20)} name=«${cvar.name.limitPad(15)}» | text=${truncText(cvar).limitPad(40)} | expr=${truncText(ex).limitPad(45)} | exprType=«${
                                cvar.expr?.returnType?.limitPad(20)
                            }»)"
                        )
                    }
                }
            }

            if (allowed(e)) {
                var bg = if (isMarked) Ansi.BRIGHT_BG_BLACK else ""

                for (entry in highlightRules) {
                    if (line.contains(entry.key)) bg = entry.value
                }

                val tNum = t.toDouble() / 10e5

                var color = Ansi.BRIGHT_BLACK
                if (tNum >= 10) color = Ansi.WHITE
                if (tNum >= 40) color = Ansi.BRIGHT_YELLOW
                if (tNum >= 100) color = Ansi.YELLOW
                if (tNum >= 130) color = Ansi.BRIGHT_RED
                if (tNum >= 180) color = Ansi.RED
                if (tNum >= 299) color = Ansi.BRIGHT_MAGENTA

                val tRunStr = "${color}${tRunStr.limitPad(8, false, true)}${bg}"

                val hexPrefix = AnsiHelper.colorizeByPatterns(e.objToHexString().padEnd(10), rules, bg)
                val lineNr = AnsiHelper.colorizeByPatterns("#${(++i)}".limitPad(4, removeWs = false, doRight = true), rules, bg)
                val lineIndicator = Ansi.BRIGHT_RED + bg + (if (isMarked) "==>" else "   ") + bg


                println("$bg $lineNr $hexPrefix $tRunStr $lineIndicator ${AnsiHelper.colorizeByPatterns(line, rules, bg)} ${Ansi.RESET}")
                out.add(e)
            }

            if (index > maxElements || i > maxLines) return

            val nextPrefix =
                prefix +
                    if (isLast) indent
                    else "│$indent"

            for (j in children.indices) {
                if (j <= maxElements && i < maxLines) printNode(children[j], nextPrefix, j == children.lastIndex, children.size, j + 1, level + 1)
            }
        }

        val rootChildren = element.children
        val rootLine = "${typeName(element).trim()} file=" + element.containingFile.originalFile.virtualFile.name.trim()
        if (allowed(element)) {
            val hexPrefix = AnsiHelper.colorizeByPatterns(element.objToHexString().padEnd(10), rules)
            val lineNr = AnsiHelper.colorizeByPatterns("#${(++i)}".limitPad(4, removeWs = false, doRight = true), rules)
            val lineIndicator = "   "

            println("$lineNr $hexPrefix ${"-".limitPad(8, false, true)} $lineIndicator ${AnsiHelper.colorizeByPatterns(rootLine, rules)}")
            out.add(element)
        }

        for (j in rootChildren.indices) {
            if (j <= maxElements && i < maxLines) printNode(rootChildren[j], indent, j == rootChildren.lastIndex, rootChildren.size, j + 1, 0)
        }

        println(Ansi.BRIGHT_WHITE + "")
        val time = (System.nanoTime() - t0) / 1_000_000_000.0
        if (time <= 60) {
            val timeStr = "%.4f".format(time)
            println("Total duration: ${timeStr}s")
        } else {
            val timeStr = "%.4f".format(time / 60)
            println("Total duration: ${timeStr}m")
        }
        print(Ansi.RESET)

        return out
    }


    /**
     * Returns the current thread's stacktrace as a compact, readable String.
     * Typical use: log(stackTraceHere()) to see who called the current code path.
     */
    @OptIn(ExperimentalStdlibApi::class)
    fun stackTraceHere(
        filter: String = "",
        maxFrames: Int = 64,
        skipFrames: Int = 0,
        includeThreadHeader: Boolean = true
    ): String {
        val st = Thread.currentThread().stackTrace
        // Usually: 0=getStackTrace, 1=stackTraceHere, then callers...
        val baseSkip = 2 + skipFrames
        val from = baseSkip.coerceAtMost(st.size)
        val to = (from + maxFrames).coerceAtMost(st.size)

        val sb = StringBuilder(2048)
        if (includeThreadHeader) {
            val t = Thread.currentThread()
            sb.append("--- <${currentThreadId().toString().padEnd(5)}> 0x${this.hashCode().toHexString()} Thread=\"").append(t.name)
                .append(" state=").append(t.state).append('\n')
        }
        for (i in from until to) {
            val e = st[i]
            val tmp = StringBuffer()

            tmp.append("\t\t <${currentThreadId().toString().padEnd(5)}> 0x${this.hashCode().toHexString()} \t")
                .append("at ")
                .append(e.className).append('.').append(e.methodName)
                .append('(').append(e.fileName ?: "Unknown Source")
                .append(':').append(e.lineNumber).append(')')
                .append('\n')


            if (filter.isEmpty()) sb.append(tmp)

            if (!filter.trim().isEmpty() && tmp.toString().contains(filter)) {
                sb.append(tmp)
            }
        }

        return sb.toString()
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


    /**
     * Looks for an instance of [GdClassVarDeclTlImpl] in parent direction.
     */
    fun resolveVarDeclaration(element: PsiElement): GdClassVarDeclTlImpl? {
        return when {
            element is GdClassVarDeclTlImpl -> element
            element.parent != null -> resolveVarDeclaration(element.parent)
            else -> null
        }
    }

    fun resolveClassVarDeclaration(element: PsiElement): GdClassVarDeclTl? {
        for (e in element.children) {
            if (e is GdRefIdRef) {
                val res = GdClassMemberUtil.findDeclaration(e)?.psi()
                if ((res is GdClassVarDeclTl) && (res.varNmi != null)) {
                    return res
                }
            }

            val res = resolveClassVarDeclaration(e)
            if (res != null) return res
        }

        return null
    }

    fun resolveDictDecl(element: PsiElement): GdDictDeclImpl? {
        for (e in element.children) {
            if (e is GdRefIdRef) {
                val res = GdClassMemberUtil.findDeclaration(e)?.psi()
                if ((res is GdClassVarDeclTl) && (res.varNmi != null)) {
                    val expr = res.expr?.psi()
                    if ((expr != null) && (expr.firstChild is GdDictDeclImpl)) {
                        return expr.firstChild as GdDictDeclImpl
                    }
                }
            }

            val res = resolveDictDecl(e) ?: continue
            return res
        }

        return null
    }


    fun treeContainsDictionary(element: PsiElement): Boolean {
        for (psiElement in element.children) {
            if (psiElement is GdExpr) {
                val ret = psiElement.returnType
                if (ret.startsWith(GdKeywords.DICTIONARY)) return true
            }

            if (psiElement.children.size > 0)
                if (treeContainsDictionary(psiElement)) return true
        }

        return false
    }

    fun treeFindDictionary(element: PsiElement): GdExpr? {
        for (psiElement in element.children) {
            if (psiElement is GdExpr) {
                val ret = psiElement.returnType
                if (ret.startsWith(GdKeywords.DICTIONARY)) return psiElement
            }

            if (psiElement.children.size > 0) {
                val f = treeFindDictionary(psiElement)
                if (f != null) return f
            }
        }

        return null
    }

    fun resolvePath(root: Map<String, *>, path: String): Any? {
        if (path.isEmpty()) return root

        var current: Any? = root
        var start = 0

        while (start < path.length) {
            val end = path.indexOf('.', start).let { if (it == -1) path.length else it }

            if (start == end) {
                // z.B. "aaa..bbb" oder ".aaa"
                return null

            }

            val key = path.substring(start, end)
            val map = current as? Map<*, *> ?: return null
            current = map[key] ?: return null

            start = end + 1

        }

        return current
    }

    fun resolveDictionary(element: PsiElement): Map<String, Any> {
        for (e in element.children) {
            if (e is GdRefIdRef) {
                val res = GdClassMemberUtil.findDeclaration(e)?.psi()
                if ((res is GdClassVarDeclTl) && (res.varNmi != null)) {
                    val expr = res.expr?.psi()
                    val varName = res.varNmi?.name!!

                    if ((expr != null) && (expr.firstChild is GdDictDeclImpl)) {
                        val foo = GdPsiDictParser.parse(expr.firstChild as GdDictDeclImpl)
                        val map1: Map<String, Any> = mapOf(Pair(varName, foo))

                        return map1
                    }
                }
            }

            val res = resolveDictionary(e)
            if (!res.isEmpty()) return res
        }

        return emptyMap
    }

    private fun keyTextOf(entry: GdKeyValueImpl): String {
        return entry.firstChild.text.removeSurrounding("\"")
    }

    private fun nestedDictOf(value: PsiElement): GdDictDeclImpl? {
        return when (value) {
            is GdDictDeclImpl -> value
            is GdPrimaryExImpl -> value.children.firstOrNull { it is GdDictDeclImpl } as? GdDictDeclImpl
            else -> PsiTreeUtil.findChildOfType(value, GdDictDeclImpl::class.java)
        }
    }

    private fun inferTypeFromPsi(element: PsiElement?): String {
        if (element == null) return ""

        return when (element) {
            is GdExpr -> element.returnType
            is GdKeyValueImpl -> inferTypeFromPsi(element.children.getOrNull(1))
            else -> PsiTreeUtil.findChildOfType(element, GdExpr::class.java)?.returnType ?: ""
        }
    }

    private fun dictDeclFromDeclaration(declaration: PsiElement?): GdDictDeclImpl? {
        return when (declaration) {
            is GdClassVarDeclTl -> declaration.expr?.firstChild as? GdDictDeclImpl
            is GdClassVarDeclTlImpl -> declaration.expr?.firstChild as? GdDictDeclImpl
            is GdVarDeclSt -> declaration.expr?.firstChild as? GdDictDeclImpl
            else -> null
        }
    }

    private fun declarationNameOf(declaration: PsiElement?): String? {
        return when (declaration) {
            is GdClassVarDeclTl -> declaration.varNmi?.name
            is GdClassVarDeclTlImpl -> declaration.varNmi?.name
            is GdVarDeclSt -> declaration.varNmi?.name
            else -> null
        }
    }


    /**
     * Resolves a dotted dictionary path against a PSI dictionary declaration.
     *
     * Example:
     *   {
     *     "video": {
     *       "mode": 0
     *     }
     *   }
     *
     *   path = ["video", "mode"]  -> returns the PSI element of the final value (`0`)
     *
     * Rules:
     * - Each intermediate path segment must resolve to a nested dictionary.
     * - The last path segment returns the value PSI element of the matching key.
     * - Invalid or non-dictionary intermediate values return null.
     */
    fun resolveDictValue(path: List<String>, dict: GdDictDeclImpl): PsiElement? {
        if (path.isEmpty()) return null

        var currentDict: GdDictDeclImpl = dict
        var value: PsiElement? = null

        for (i in path.indices) {
            val key = path[i]
            var matchedValue: PsiElement? = null

            for (child in currentDict.children) {
                val keyValue = child as? GdKeyValueImpl ?: continue
                if (keyTextOf(keyValue) != key) continue

                matchedValue = keyValue.children.getOrNull(1) ?: return null
                break
            }

            val resolvedValue = matchedValue ?: return null

            if (i == path.lastIndex) {
                return resolvedValue
            }

            currentDict = nestedDictOf(resolvedValue) ?: return null
            value = resolvedValue
        }

        return value
    }


    fun resolveDictPathElement(element: PsiElement): PsiElement? {
        if (lock) return null

        lock = true
        try {
            val context = when (element) {
                is GdAttributeEx -> element
                is GdExpr -> element
                else -> PsiTreeUtil.getParentOfType(element, GdAttributeEx::class.java, false) ?: element
            }

            val contextText = context.text.trim()
            if (contextText.isEmpty()) return null

            val segments = contextText.split('.').map { it.trim() }.filter { it.isNotEmpty() }
            if (segments.isEmpty()) return null

            val baseName = segments.first()
            if (baseName == GdKeywords.SELF || baseName == GdKeywords.SUPER) return null

            val baseRef = PsiTreeUtil.findChildrenOfType(context, GdRefIdRef::class.java)
                .firstOrNull { it.text == baseName }
                ?: return null

            val declaration = GdClassMemberUtil.findDeclaration(baseRef)?.psi() ?: return null
            val dictDecl = dictDeclFromDeclaration(declaration) ?: return null
            val declarationName = declarationNameOf(declaration) ?: return null

            val relativePath = if (segments.first() == declarationName) segments.drop(1) else segments
            if (relativePath.isEmpty()) {
                return dictDecl
            }

            return resolveDictValue(relativePath, dictDecl)
        } finally {
            lock = false
        }
    }

}
