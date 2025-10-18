package gdscript.psi.utils

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import gdscript.GdKeywords
import gdscript.psi.*
import gdscript.utils.GdOperand

/**
 * Shared utils among Named and other elements
 */
object GdCommonUtil {

    fun getName(element: GdNamedElement): String {
        return element.text
    }

    fun getName(element: PsiElement): String {
        return when (element) {
            is GdNamedElement -> element.text
            is GdConstDeclTl -> element.name
            is GdClassVarDeclTl -> element.name
            is GdConstDeclSt -> element.name
            is GdVarDeclSt -> element.name
            else -> ""
        }
    }

    fun getNameIdentifier(element: GdNamedIdElement): PsiElement {
        return element.firstChild
    }

    fun setName(element: PsiNamedElement, newName: String): PsiElement {
        val project = element.project
        val keyNode = element.node.firstChildNode

        if (keyNode != null) {
            val id = when (element) {
                is GdClassNameNmi -> {
                    GdCfgUtil.renameValue(project, element.name, newName)
                    GdElementFactory.classNameNmi(project, newName)
                }

                is GdEnumDeclNmi -> GdElementFactory.enumDeclNmi(project, newName)
                is GdEnumValueNmi -> GdElementFactory.enumValueNmi(project, newName)
                is GdFuncDeclIdNmi -> GdElementFactory.funcDeclIdNmi(project, newName)
                is GdInheritanceIdRef -> GdElementFactory.inheritanceIdNm(project, newName)
                is GdInheritanceSubIdRef -> GdElementFactory.inheritanceSubIdNm(project, newName)
                is GdMethodIdNmi -> GdElementFactory.methodIdNmi(project, newName)
                is GdRefIdRef -> GdElementFactory.refIdNm(project, newName)
                is GdSignalIdNmi -> GdElementFactory.signalIdNmi(project, newName)
                is GdStringValRef -> GdElementFactory.typeStringVal(project, newName)
                is GdVarNmi -> GdElementFactory.varNmi(project, newName)
                else -> return element
            }

            element.node.replaceChild(keyNode, id.node)
        }

        return element
    }

    fun returnType(element: PsiElement?): String {
        return when (element) {
            is GdConstDeclTl -> element.returnType
            is GdClassVarDeclTl -> element.returnType
            is GdMethodDeclTl -> element.returnType
            is GdFuncDeclEx -> element.returnType
            is GdParam -> element.returnType
            is GdArgExpr -> element.returnType
            is GdVarDeclSt -> element.returnType
            is GdConstDeclSt -> element.returnType
            is GdExpr -> element.returnType
            is GdTypedVal -> element.returnType
            is GdClassNaming -> element.classname
            is GdClassDeclTl -> element.classNameNmi?.classId.orEmpty()
            is GdEnumDeclTl -> handleEnumDecl(element)
            is GdEnumValue -> GdKeywords.INT
            is GdSignalDeclTl -> "Signal"
            is GdSetDecl -> handleSetDecl(element)
            is GdForSt -> handleForStmt(element)
            null -> return ""
            else -> throw NotImplementedError(element.toString())
        }
    }


    private fun handleSetDecl(element: GdSetDecl): String {
        // First, try to get the type from the typed annotation in the setter itself
        element.typed?.text?.trim(':', ' ')?.let { type ->
            if (type.isNotEmpty()) return type
        }

        // Otherwise, try to get the type from the parent class variable declaration
        val parentVar = PsiTreeUtil.getParentOfType(element, GdClassVarDeclTl::class.java)
        return parentVar?.returnType ?: throw RuntimeException("Unable to resolve getter/setter type: '${element.text}'")
    }


    private fun handleEnumDecl(element: GdEnumDeclTl): String {
        // Return a qualified enum name instead of generic "EnumDictionary"
        // This allows enum values to be resolved for xxx.enum.VALUE patterns
        val enumName = element.name
        if (enumName.isNotEmpty()) {
            val owningClass = GdClassUtil.getFullClassId(element).trim('"')  // ← Remove quotes!
            return "$owningClass.$enumName"
        } else {
            // For unnamed enums, we can't reference them by name
            return "EnumDictionary"
        }
    }


    private fun handleForStmt(element: GdForSt): String {
        if (element.typed != null) {
            return element.typed?.text?.trim(':', ' ') ?: ""
        }

        val forExpr = element.expr?.returnType ?: ""
        if (forExpr.startsWith("Array")) {
            return GdOperand.getReturnType(forExpr, GdKeywords.INT, "[]", element.project)
        } else {
            return forExpr
        }
    }


    fun typed(element: PsiElement?): GdTyped? {
        return when (element) {
            is GdConstDeclTl -> element.typed
            is GdClassVarDeclTl -> element.typed
            is GdSetDecl -> element.typed
            is GdParam -> element.typed
            is GdVarDeclSt -> element.typed
            is GdConstDeclSt -> element.typed
            else -> null
        }
    }

}
