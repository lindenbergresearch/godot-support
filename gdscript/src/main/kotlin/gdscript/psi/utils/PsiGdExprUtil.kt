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

private val resolutionStack = ThreadLocal.withInitial { mutableSetOf<PsiElement>() }

private inline fun <T> withCycleDetection(element: PsiElement, block: () -> T, defaultValue: T): T {
    val stack = resolutionStack.get()

    if (element in stack) {
        return defaultValue
    }

    stack.add(element)
    try {
        return block()
    } finally {
        stack.remove(element)
    }
}

object PsiGdExprUtil {
    fun getReturnType(expr: GdExpr, allowResource: Boolean = false): String {
        return withCycleDetection(expr, {
            return when (expr) {
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

                        //     println("🔍 AttributeEx: '${expr.text}' refId='${expr.refId?.text}' declaration=${declaration?.javaClass?.simpleName}")

                        // In case method is not resolved returnType is method itself
                        if (declaration is GdMethodDeclTl && expr.refId?.nextLeaf()?.elementType == GdTypes.DOT) {
                            return "Callable"
                        }

                        val returnType = GdCommonUtil.returnType(declaration)

                        //   println("   → returnType from GdCommonUtil: '$returnType'")

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
                                            includeUnnamedEnumValues = true
                                        )
                                        val member = members.firstOrNull()
                                        //               println("   → Fallback found member: ${member?.javaClass?.simpleName}")
                                        if (member != null) {
                                            val inferredType = GdCommonUtil.returnType(member)
                                            //                 println("   → Fallback inferredType: '$inferredType'")
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
                            if (parentAttr != null) {
                                return GdCommonUtil.returnType(parentAttr.firstChild)
                            }
                            return ""
                        }
                    }

                    run {
                        val method = expr.expr.text
                        if (method == "get_node" || method == "get_node_or_null" || method == "get_first_node_in_group") {
                            //TODO try to parse Node from .tscn
//                            println("get_node = ${expr.expr.returnType}")
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
                        } else if (method == "load" || method == "preload") {
                            val res = expr.argList?.argExprList?.firstOrNull()
                            if (res != null) {
                                var resource = res.text.trim('"', '\'')
                                if (!resource.startsWith("res://") && expr.containingFile.originalFile.virtualFile?.parent != null) {
                                    resource = resource.toAbsoluteResource(expr, expr.project)
                                }

                                val t = resolveResourceType(resource, expr.project)
                                if (t != null) {
                                    return t
                                }

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

                            node.script?.let {
                                GdFileResIndex.getFiles(it, expr).firstOrNull()
                                    ?.getPsiFile(expr)
                                    ?.let { GdClassUtil.getFullClassId(it) }
                                    ?.let {
                                        if (!it.startsWith("\"res://") || allowResource) return it
                                    }
                            }

                            return node.element.type
                        }

                        is GdDictDecl -> return "Dictionary[Variant, Variant]"
                        is GdArrayDecl -> {
//                        var type = ""
//                        expr.arrayDecl?.exprList?.forEach {
//                            val itType = it.returnType
//                            type = if (type == itType || type == "") {
//                                itType
//                            } else {
//                                "Array"
//                            }
//                        }
//                        type = if (type.isNotEmpty() && type != "Array") {
//                            "Array[$type]"
//                        } else {
//                            "Array[Variant]"
//                        }

                            return "Array[Variant]"
                        }

                        else -> expr.expr?.returnType ?: ""
                    }
                }

                is GdLiteralEx -> {
                    val text = expr.text
                    when (text) {
                        GdKeywords.TRUE -> return GdKeywords.BOOL
                        GdKeywords.FALSE -> return GdKeywords.BOOL
                        GdKeywords.NULL -> return GdKeywords.NULL
                        GdKeywords.NAN -> return "inf"
                        GdKeywords.INF -> return "nan"
                    }

                    val elementType = expr.firstChild?.elementType
                    if (elementType == GdTypes.NUMBER) {
                        if (text.startsWith("0b")) {
                            return GdKeywords.INT
                        } else if (text.startsWith("0x")) {
                            return GdKeywords.INT
                        } else if (text.contains('e') || text.contains('.')) {
                            return GdKeywords.FLOAT
                        }

                        return GdKeywords.INT
                    } else if (elementType == GdTypes.STRING_VAL_NM) {
                        return GdKeywords.STRING
                    } else if (elementType == GdTypes.STRING) {
                        return GdKeywords.STRING
                    } else if (elementType == GdTypes.STRING_NAME) {
                        return GdKeywords.STRING_NAME
                    } else if (elementType == GdTypes.NODE_PATH) {
                        return GdKeywords.STRING
                    } else if (elementType == GdTypes.NODE_PATH_LIT) {
                        return GdKeywords.NODE_PATH
                    } else if (elementType == GdTypes.REF_ID_NM) {
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

                            is GdEnumValue -> GdKeywords.INT
                            is GdClassNaming -> text
                            is GdForSt -> {
                                if (element.typed != null) {
                                    return fromTyped(element.typed)
                                }
                                val forExpr = element.expr?.returnType ?: ""
                                if (forExpr.startsWith("Array")) {
                                    return GdOperand.getReturnType(forExpr, GdKeywords.INT, "[]", expr.project)
                                } else {
                                    return forExpr
                                }
                            }

                            is GdAutoload -> element.key
                            is GdClassDeclTl -> GdClassUtil.getFullClassId(element)
                            else -> ""
                        }
                    }

                    return ""
                }

                else -> ""
            }
        }, "")
    }

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
            val gdFile = file as? GdFile
            return gdFile?.let { GdClassUtil.getFullClassId(it) } ?: resourcePath
        }

        return null
    }

    // TODO unify with doc builder
    fun fromTyped(typed: GdTyped?): String {
        return typed?.text?.trim(':', ' ') ?: ""
    }

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

    fun fromTyped(typed: GdTypedVal?): String {
        if (typed == null) return ""

        val main = typed.typeHintList.first().text
        if (main != "Array") {
            return main
        }

        return typed.typeHintList.last().text
    }

    private fun parseLoadedType(element: PsiElement, type: String): String {
        GdClassMemberUtil.listDeclarations(element, type, false, true, true, true)
            .firstOrNull()
            ?.let { if (it is PsiElement) return GdCommonUtil.returnType(it) }

        return type
    }

}
