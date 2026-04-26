package gdscript.annotator

import com.intellij.lang.annotation.*
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import gdscript.GdKeywords
import gdscript.psi.*
import gdscript.psi.utils.*
import gdscript.utils.GdExprUtil.left
import gdscript.utils.GdExprUtil.right
import gdscript.utils.GdOperand
import gdscript.utils.StringUtil.isDynamicType

class GdExprTypeAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Skip annotation if indices are not ready
        if (DumbService.isDumb(element.project)) {
            return
        }

        when (element) {
            is GdFactorEx -> factorExpr(element, holder)
            is GdPlusEx -> plusExpr(element, holder)
            is GdBitAndEx -> bitAndExpr(element, holder)
            is GdVarDeclSt -> varDeclExpr(element, holder)
            is GdAssignSt -> assignExpr(element, holder)
            is GdShiftEx -> shiftExpr(element, holder)
            is GdComparisonEx -> comparisonExpr(element, holder)
            is GdClassVarDeclTl -> classVarDecl(element, holder)
            is GdArrEx -> arrIndexExpr(element, holder)
        }
    }

    private fun varDeclExpr(element: GdVarDeclSt, holder: AnnotationHolder) {
        // Skip type checking for variant types (declared with '=' and no explicit type)
        val left = element.returnType
        val right = element.expr?.returnType ?: return
        val operator = element.assignTyped?.text ?: return
        validate(left, right, operator, "Cannot assign", element, holder)
    }

    private fun classVarDecl(element: GdClassVarDeclTl, holder: AnnotationHolder) {
        // Skip type checking for variant types (declared with '=' and no explicit type)
        if (isVariantDeclaration(element)) {
            //  println("Skipping classVarDecl type checking for variant declaration: $element / ${element.text.toText()}")
            return
        }

        val left = element.returnType
        val right = element.expr?.returnType ?: return
        val operator = element.assignTyped?.text ?: return
        validate(left, right, operator, "Cannot assign", element, holder)
    }

    private fun assignExpr(element: GdAssignSt, holder: AnnotationHolder) {
        // Check if the left side references a variable
        val leftExpr = element.exprList.firstOrNull()
        if (leftExpr != null) {

            val varDecl = findVariableDeclaration(leftExpr)
            if (varDecl != null) {
                // For typed variables, get the declared type and validate against it
                val declaredType = getDeclaredType(varDecl)
                val rightExpr = element.exprList.right()
                val operator = element.assignSign.text

                if (declaredType.isNotEmpty()) {
                    // Validate: declared type of variable vs. type of assigned value
                    validate(declaredType, rightExpr, operator, "Cannot assign", element, holder)
                    return
                }
            }
        }

        // Fallback to original behavior if variable declaration not found
        val left = element.exprList.left()
        val right = element.exprList.right()
        val operator = element.assignSign.text
        validate(left, right, operator, "Cannot assign", element, holder)
    }

    private fun factorExpr(element: GdFactorEx, holder: AnnotationHolder) {
        val left = element.exprList.left()
        val right = element.exprList.right()
        val operator = element.factorSign.text
        validate(left, right, operator, "Cannot factor", element, holder)
    }

    private fun plusExpr(element: GdPlusEx, holder: AnnotationHolder) {
        val left = element.exprList.left()
        val right = element.exprList.right()
        val operator = element.sign.text
        validate(left, right, operator, "Cannot factor", element, holder)
    }

    private fun shiftExpr(element: GdShiftEx, holder: AnnotationHolder) {
        val left = element.exprList.left()
        val right = element.exprList.right()
        validate(left, right, "<<", "Cannot factor", element, holder)
    }

    private fun comparisonExpr(element: GdComparisonEx, holder: AnnotationHolder) {
        val left = element.exprList.left()
        val right = element.exprList.right()
        validate(left, right, element.operator.text, "Cannot factor", element, holder)
    }

    private fun bitAndExpr(element: GdBitAndEx, holder: AnnotationHolder) {
        val left = element.exprList.left()
        val right = element.exprList.right()
        val operator = element.bitAndSign.text
        validate(left, right, operator, "Incomparable", element, holder)
    }

    private fun validate(
        left: String,
        right: String,
        operator: String,
        message: String,
        element: PsiElement,
        holder: AnnotationHolder,
    ) {
        var l = left
        var r = right

        if (l == r || r == GdKeywords.NULL) return
        if (l.isDynamicType() || r.isDynamicType()) return

        if (l == "PackedScene") return
        if (l == "EnumDictionary") l = "int"
        if (r == "EnumDictionary") r = "int"

        if (GdOperand.isAllowed(l, r, operator, element.project)) return
        if (GdExprUtil.typeAccepts(r, l, element)) return
        if (GdExprUtil.typeAccepts(l, r, element)) return

        holder
            .newAnnotationGd(element.project, HighlightSeverity.ERROR, "$message a value of type '$r' as '$l'")
            .range(element.textRange)
            .create()
    }


    /**
     * Determines if a variable declaration is a Variant type.
     * A variable is considered Variant if:
     * - It has NO explicit type annotation (typed == null)
     * - AND it uses simple '=' assignment (not ':=')
     */
    private fun isVariantDeclaration(declaration: PsiElement): Boolean {
        return when (declaration) {
            is GdVarDeclSt -> {
                // Check if there's an explicit type annotation
                if (declaration.typed != null) {
                    return false
                }

                // Check if the assignment uses simple '='
                val assignTyped = declaration.assignTyped ?: return false
                return isVariantAssignment(assignTyped)
            }

            is GdClassVarDeclTl -> {
                // Check if there's an explicit type annotation
                if (declaration.typed != null) {
                    return false
                }

                // Check if the assignment uses simple '='
                val assignTyped = declaration.assignTyped ?: return false
                return isVariantAssignment(assignTyped)
            }

            else -> false
        }
    }


    /**
     * Checks if the assignment uses '=' (variant) instead of ':=' (typed)
     */
    private fun isVariantAssignment(assignTyped: GdAssignTyped): Boolean {
        val firstChild = assignTyped.firstChild ?: return false
        return firstChild.elementType == GdTypes.EQ
    }


    /**
     * Gets the declared type of variable declaration.
     */
    private fun getDeclaredType(declaration: PsiElement): String {
        return when (declaration) {
            is GdVarDeclSt -> {
                // For explicitly typed variables
                if (declaration.typed != null) {
                    return PsiGdExprUtil.extractSubtype(declaration.typed)
                }

                // For: = assignment (inferred typed), get type from the initial expression
                val assignTyped = declaration.assignTyped
                if ((assignTyped != null) && !isVariantAssignment(assignTyped)) {
                    return declaration.expr?.returnType ?: ""
                }

                ""
            }

            is GdClassVarDeclTl -> {
                // For explicitly typed variables
                if (declaration.typed != null) {
                    return PsiGdExprUtil.extractSubtype(declaration.typed)
                }

                // For: = assignment (inferred typed), get type from the initial expression
                val assignTyped = declaration.assignTyped
                if ((assignTyped != null) && !isVariantAssignment(assignTyped)) {
                    return declaration.expr?.returnType ?: ""
                }

                ""
            }

            else -> {
                ""
            }
        }
    }


    /**
     * Finds the declaration of a variable from an expression reference
     */
    private fun findVariableDeclaration(expr: GdExpr): PsiElement? {
        // Get the identifier from the expression
        val refId = when (expr) {
            is GdLiteralEx -> expr.refIdNm
            is GdAttributeEx -> expr.refId
            else -> null
        } ?: return null

        // Resolve the reference to find the declaration
        val decl = GdClassMemberUtil.findDeclaration(refId)
        (decl as? PsiElement)?.let { return it }

        return null
    }

    private fun arrIndexExpr(element: GdArrEx, holder: AnnotationHolder) {
        val expressionList = element.exprList
        if (expressionList.getOrNull(1) == null) {
            // Highlight both brackets and everything between them
            val lBracket = element.node.findChildByType(GdTypes.LSBR)?.psi
            val rBracket = element.node.findChildByType(GdTypes.RSBR)?.psi
            val range = when {
                (lBracket != null) && (rBracket != null) -> {
                    lBracket.textRange.union(rBracket.textRange)
                }

                else -> {
                    element.textRange
                }
            }
            holder
                .newAnnotationGd(element.project, HighlightSeverity.ERROR, "Indexer has 1 parameter but is invoked with 0 argument")
                .range(range)
                .create()
            return
        }

        val baseType = expressionList.getOrNull(0)?.returnType ?: return
        val indexType = expressionList.getOrNull(1)?.returnType ?: return

        // Skip if dynamic/unresolved
        if (baseType.isDynamicType() || indexType.isDynamicType()) return

        var expectedKey: String? = null
        if (baseType.startsWith("Array[")) {
            expectedKey = GdKeywords.INT
        } else if (baseType == "Array") {
            expectedKey = GdKeywords.INT
        } else if (baseType.startsWith("Dictionary[")) {
            val inside = baseType.substringAfter("[").substringBeforeLast("]")
            val parts = inside.split(",").map { it.trim() }
            if (parts.isNotEmpty()) expectedKey = parts[0]
        }

        val exp = expectedKey ?: return // if null (untyped dict) does not enforce
        // If the expected key type accepts the provided index type, it's fine
        if (GdExprUtil.typeAccepts(exp, indexType, element)) return

        holder
            .newAnnotationGd(element.project, HighlightSeverity.ERROR, "Invalid index type $indexType, expected $exp")
            .range(expressionList.getOrNull(1)?.textRange ?: element.textRange)
            .create()
    }
}
