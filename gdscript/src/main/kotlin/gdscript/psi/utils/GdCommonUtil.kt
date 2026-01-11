package gdscript.psi.utils

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import gdscript.GdKeywords
import gdscript.psi.*
import gdscript.psi.impl.GdEnumValueNmiImpl
import gdscript.utils.GdOperand

/**
 * Utility object providing common operations and helper functions for working with elements in a PSI tree.
 */
object GdCommonUtil {

    /**
     * Retrieves the name of the given GdNamedElement.
     *
     * @param element The GdNamedElement for which the name is retrieved.
     * @return The name of the provided GdNamedElement as a String.
     */
    fun getName(element: GdNamedElement): String {
        return element.text
    }

    /**
     * Retrieves the name of the given PsiElement.
     *
     * @param element The PsiElement whose name is to be retrieved.
     *                The expected element can be of type GdNamedElement, GdConstDeclTl,
     *                GdClassVarDeclTl, GdConstDeclSt, or GdVarDeclSt.
     * @return The name of the element as a String. Returns an empty string if the element
     *         does not match any of the specified types or if the name cannot be retrieved.
     */
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


    /**
     * Processes the text of a given PsiElement by applying several transformations, including trimming,
     * optional wrapping, padding, and length limitation. These transformations are applied based on the provided parameters.
     *
     * @param element The PsiElement whose text will be processed.
     * @param maxLen Optional parameter specifying the maximum allowed length of the processed text.
     * If the length of the text exceeds this value, it will be truncated, and an ellipsis will be appended. Default value is -1, indicating no length restriction.
     * @param wrapWith Optional parameter specifying the string with which to wrap the processed text.
     * If provided, the text will be prefixed and suffixed with this string. Default value is an empty string, indicating no wrapping.
     * @param padRight Optional parameter specifying the minimum length of the processed text.
     * If the processed text is shorter than this value, it will be padded with trailing spaces to meet the length requirement. Default value is -1, indicating no padding.
     * @return The processed text as a String after applying the transformations based on the given parameters.
     */
    fun getFilteredText(element: PsiElement, maxLen: Int = -1, wrapWith: String = "", padRight: Int = -1): String {
        var txt = element.text.replace(Regex("\\s+"), " ").trim()
        if (wrapWith.isNotEmpty()) txt = "$wrapWith$txt$wrapWith"

        if ((maxLen > 0) && (txt.length > maxLen)) {
            txt = txt.take(maxLen - 2) + " …$wrapWith"
        } else {
            if (padRight > 0) txt = txt.padEnd(padRight, ' ')
        }

        return txt
    }


    /**
     * Retrieves the name identifier of the specified GdNamedIdElement.
     *
     * @param element the GdNamedIdElement for which the name identifier is to be retrieved
     * @return the PsiElement representing the name identifier of the given element
     */
    fun getNameIdentifier(element: GdNamedIdElement): PsiElement {
        return element.firstChild
    }

    /**
     * Renames a given PsiNamedElement with a new name while updating the relevant AST nodes.
     *
     * @param element The PsiNamedElement instance whose name needs to be changed.
     * @param newName The new name to assign to the element.
     * @return The updated PsiElement after the renaming operation.
     */
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

    /**
     * Resolves and returns the return type for the given PsiElement.
     *
     * @param element The PsiElement for which to determine the return type.
     * Can be null or one of various GdScript-specific element types.
     * @return A String representing the return type of the given element.
     * If the element is null, an empty string is returned. If the element
     * type is not implemented, a NotImplementedError is thrown.
     */
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
            is GdEnumValueNmiImpl -> handleEnumDecl(element)
            null -> return ""
            else -> throw NotImplementedError(element.toString())
        }
    }


    /**
     * Resolves and returns the type of the given `GdSetDecl` element by inspecting its typed annotation or related parent class variable declaration.
     *
     * @param element the GdSetDecl element whose type needs to be resolved
     * @return a string representation of the resolved type
     * @throws RuntimeException if the type cannot be resolved from the given element or its references
     */
    private fun handleSetDecl(element: GdSetDecl): String {
        // First, try to get the type from the typed annotation in the setter itself
        element.typed?.text?.trim(':', ' ')?.let { type ->
            if (type.isNotEmpty()) return type
        }

        // Otherwise, try to get the type from the parent class variable declaration
        val parentVar = PsiTreeUtil.getParentOfType(element, GdClassVarDeclTl::class.java)
        return parentVar?.returnType ?: throw RuntimeException("Unable to resolve getter/setter type: '${element.text}'")
    }


    /**
     * Handles the declaration of an enum and resolves its qualified name if possible.
     *
     * Returns the fully qualified name of the enum in the form of `OwningClass.EnumName` for named enums.
     * If the enum does not have a name, a generic "EnumDictionary" is returned.
     *
     * @param element The `GdEnumDeclTl` representing the enum being processed.
     * @return A `String` containing the fully qualified name of the enum, or "EnumDictionary" for unnamed enums.
     */
    private fun handleEnumDecl(element: GdEnumDeclTl): String {
        // Return a qualified enum name instead of generic "EnumDictionary"
        // This allows enum values to be resolved for xxx.enum.VALUE patterns
        val enumName = element.name
        return if (enumName.isNotEmpty()) {
            val owningClass = GdClassUtil.getFullClassId(element).trim('"')  // ← Remove quotes!
            "$owningClass.$enumName"
        } else {
            // For unnamed enums, we can't reference them by name
            "EnumDictionary"
        }
    }

    /**
     * Handles an enumeration declaration by constructing a fully qualified name
     * for the enum or returning a generic reference if the enum is unnamed.
     *
     * @param element The enumeration value implementation (`GdEnumValueNmiImpl`) to process.
     * @return A `String` representing the fully qualified name of the enumeration,
     *         or "EnumDictionary" if the enum name is empty.
     */
    private fun handleEnumDecl(element: GdEnumValueNmiImpl): String {
        // Return a qualified enum name instead of generic "EnumDictionary"
        // This allows enum values to be resolved for xxx.enum.VALUE patterns
        val enumName = element.name
        return if (enumName.isNotEmpty()) {
            val owningClass = GdClassUtil.getFullClassId(element).trim('"')  // ← Remove quotes!
            "$owningClass.$enumName"
        } else {
            // For unnamed enums, we can't reference them by name
            "EnumDictionary"
        }
    }


    /**
     * Handles a "for" statement element and determines its return type based on its properties.
     *
     * @param element The `GdForSt` element representing the "for" statement.
     * @return A string representing the type associated with the "for" statement.
     */
    private fun handleForStmt(element: GdForSt): String {
        if (element.typed != null) {
            return element.typed?.text?.trim(':', ' ') ?: ""
        }

        val forExpr = element.expr?.returnType ?: ""
        return if (forExpr.startsWith("Array")) {
            GdOperand.getReturnType(forExpr, GdKeywords.INT, "[]", element.project)
        } else {
            forExpr
        }
    }


    /**
     * Determines the type of the provided PsiElement, if applicable.
     *
     * @param element The PsiElement for which the type is to be determined. Can be null.
     * @return A GdTyped instance representing the type of the element if it matches
     *         known types; null otherwise.
     */
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
