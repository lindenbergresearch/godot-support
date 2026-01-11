package gdscript.psi

import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import gdscript.psi.impl.*
import gdscript.psi.utils.*
import gdscript.structureView.GdPresentationUtil

/**
 * Utility object for working with GDScript language elements, providing a set of helper methods
 * to retrieve and manipulate meta-information about language structures, such as classes, variables,
 * methods, constants, and other declarations.
 */
object GdPsiUtils {

    /**
     * Returns the class name for the given GdClassNaming element.
     *
     * @param element the GdClassNaming element from which the class name will be retrieved
     * @return the class name of the provided element as a string
     */
    @JvmStatic
    fun getClassname(element: GdClassNaming): String = GdClassNamingElementType.getClassname(element)


    /**
     * Retrieves the parent class name of the given GdClassNaming element.
     *
     * @param element the GdClassNaming element whose parent class name is to be retrieved
     * @return the name of the extended class associated with the given element
     */
    @JvmStatic
    fun getParentName(element: GdClassNaming): String = GdClassNamingElementType.getParentName(element)


    /**
     * Retrieves the name of the given GdClassDeclTl element.
     *
     * @param element the GdClassDeclTl instance from which the name will be extracted.
     * @return the name of the specified GdClassDeclTl element as a string. If the name cannot be determined, an empty string is returned.
     */
    @JvmStatic
    fun getName(element: GdClassDeclTl): String = GdClassUtil.getName(element)


    /**
     * Retrieves the name of the parent class that the specified class extends.
     *
     * @param element the class declaration element from which to extract the parent class name
     * @return the name of the parent class, or an empty string if no parent is specified
     */
    @JvmStatic
    fun getParentName(element: GdClassDeclTl): String = GdClassDeclElementType.getParentName(element)


    /**
     * Retrieves the class ID of a given GdClassNameNmi element. The class ID is constructed
     * by traversing the hierarchical structure of the element, considering parent classes
     * or file references, and returning a dot-separated string representation.
     *
     * @param element the GdClassNameNmi element whose class ID needs to be retrieved
     * @return a string representing the class ID, constructed from the hierarchy of the element
     */
    @JvmStatic
    fun getClassId(element: GdClassNameNmi): String = GdClassIdElementType.getClassId(element)


    /**
     * Retrieves the name of the parent class or inherited class from the given GdClassNameNmi element.
     *
     * @param element The GdClassNameNmi element for which the parent class name should be retrieved.
     * @return The name of the parent class if it exists, or null if no parent class is found.
     */
    @JvmStatic
    fun getParentName(element: GdClassNameNmi): String? = GdClassIdElementType.getParentName(element)


    /**
     * Determines whether the specified `GdClassNameNmi` element represents an inner class.
     *
     * @param element the `GdClassNameNmi` element to check
     * @return `true` if the specified element represents an inner class, `false` otherwise
     */
    @JvmStatic
    fun isInner(element: GdClassNameNmi): Boolean = PsiGdClassUtil.isInner(element)


    /**
     * Retrieves the inheritance path of a given GdInheritance element.
     *
     * @param element the GdInheritance element from which to obtain the inheritance path
     * @return the inheritance path as a string, or an empty string if the inheritance path is not available
     */
    @JvmStatic
    fun getInheritancePath(element: GdInheritance): String = GdInheritanceElementType.inheritancePath(element)


    /**
     * Retrieves the PSI (Program Structure Interface) file associated with the given GdInheritanceIdRef element.
     *
     * @param element The GdInheritanceIdRef instance from which the PSI file is to be resolved.
     * @return The corresponding PsiFile instance if found, or null if the PSI file cannot be resolved.
     */
    @JvmStatic
    fun getPsiFile(element: GdInheritanceIdRef): PsiFile? = PsiGdInheritanceUtil.getPsiFile(element)


    /**
     * Checks if a given GdInheritanceIdRef element represents a class name.
     *
     * @param element the GdInheritanceIdRef element to check
     * @return true if the element represents a class name, false otherwise
     */
    @JvmStatic
    fun isClassName(element: GdInheritanceIdRef): Boolean = PsiGdInheritanceUtil.isClassName(element)


    /**
     * Retrieves the name of the specified GdEnumDeclTl element.
     *
     * @param element the GdEnumDeclTl element whose name is to be retrieved
     * @return the name of the specified element, or an empty string if the name cannot be determined
     */
    @JvmStatic
    fun getName(element: GdEnumDeclTl): String = GdEnumUtil.getName(element)


    /**
     * Retrieves the values associated with an enumeration declaration.
     *
     * @param element the enumeration declaration from which values are extracted.
     * @return a LinkedHashMap containing the enumeration values, where the key is the name of the enum value and the value is its associated long value.
     */
    @JvmStatic
    fun getValues(element: GdEnumDeclTl): LinkedHashMap<String, Long> = PsiGdEnumUtil.values(element)


    /**
     * Retrieves an `ItemPresentation` instance for the given `GdEnumDeclTl` element.
     *
     * @param element the `GdEnumDeclTl` element for which the presentation is generated
     * @return an `ItemPresentation` instance containing the presentable text, location string, and icon representation for the element
     */
    @JvmStatic
    fun getPresentation(element: GdEnumDeclTl): ItemPresentation = GdPresentationUtil.presentation(element)


    /**
     * Retrieves an item presentation for the specified constant declaration element.
     *
     * @param element The constant declaration element for which the item presentation is obtained.
     * @return An ItemPresentation instance providing details such as display text, location, and icon.
     */
    @JvmStatic
    fun getPresentation(element: GdConstDeclTl): ItemPresentation = GdPresentationUtil.presentation(element)


    /**
     * Retrieves the return type of a given `GdConstDeclTl` element.
     *
     * @param element the `GdConstDeclTl` element from which to determine the return type
     * @return the return type as a string, or an empty string if no return type is defined
     */
    @JvmStatic
    fun getReturnType(element: GdConstDeclTl): String = PsiGdConstDeclUtil.getReturnType(element)


    /**
     * Retrieves the name of the given GdConstDeclTl element.
     *
     * @param element the GdConstDeclTl element for which the name is to be retrieved
     * @return the name of the given GdConstDeclTl element, or an empty string if the name is not present
     */
    @JvmStatic
    fun getName(element: GdConstDeclTl): String = GdConstDeclUtil.getName(element)


    /**
     * Retrieves the name of the given GdNamedElement.
     *
     * @param element the GdNamedElement whose name is to be retrieved
     * @return the name as a String
     */
    @JvmStatic
    fun getName(element: GdNamedElement): String = GdCommonUtil.getName(element)


    /**
     * Sets a new name for the specified `GdNamedElement`.
     *
     * @param element The element whose name is to be changed. It must implement the `GdNamedElement` interface.
     * @param newName The new name to assign to the specified element.
     * @return The updated `PsiElement` instance with the new name.
     */
    @JvmStatic
    fun setName(element: GdNamedElement, newName: String): PsiElement = GdCommonUtil.setName(element, newName)


    /**
     * Retrieves the name identifier of the specified GdNamedIdElement.
     *
     * @param element the GdNamedIdElement whose name identifier is to be retrieved
     * @return the PsiElement that represents the name identifier of the provided element
     */
    @JvmStatic
    fun getNameIdentifier(element: GdNamedIdElement): PsiElement = GdCommonUtil.getNameIdentifier(element)


    /**
     * Renames the specified GdFile element with the provided new name.
     *
     * @param element The GdFile element whose name is to be changed.
     * @param newName The new name to assign to the element.
     * @return The updated PsiElement with the new name applied.
     */
    @JvmStatic
    fun setName(element: GdFile, newName: String): PsiElement = GdCommonUtil.setName(element, newName)


    /**
     * Retrieves the return type as a string from the specified GdTypedVal element.
     *
     * @param element the GdTypedVal element from which to extract the return type
     * @return a string representing the return type of the given element, or an empty string if not available
     */
    @JvmStatic
    fun getReturnType(element: GdTypedVal): String = GdTypedUtil.getReturnType(element)


    /**
     * Retrieves the name of the provided GdClassVarDeclTl element.
     *
     * @param element the GdClassVarDeclTl element from which to extract the name
     * @return the name of the element as a String, or an empty string if the name is not available
     */
    @JvmStatic
    fun getName(element: GdClassVarDeclTl): String = GdClassVarUtil.getName(element)


    /**
     * Retrieves an ItemPresentation for the given GdClassVarDeclTl instance.
     *
     * @param element the GdClassVarDeclTl instance for which the presentation is created
     * @return an ItemPresentation containing details like text, location, and icon for the element
     */
    @JvmStatic
    fun getPresentation(element: GdClassVarDeclTl): ItemPresentation = GdPresentationUtil.presentation(element)


    /**
     * Retrieves the return type of a given GdClassVarDeclTl element.
     *
     * @param element the GdClassVarDeclTl element whose return type needs to be determined
     * @return the return type of the specified element as a string
     */
    @JvmStatic
    fun getReturnType(element: GdClassVarDeclTl): String = PsiGdClassVarUtil.getReturnType(element)


    /**
     * Checks if a given GdClassVarDeclTl element is annotated with a specified annotation.
     *
     * @param element The GdClassVarDeclTl element to inspect for annotations.
     * @param annotator The name of the annotation to look for.
     * @return True if the element is annotated with the specified annotation, otherwise false.
     */
    @JvmStatic
    fun isAnnotated(element: GdClassVarDeclTl, annotator: String): Boolean = PsiGdClassVarUtil.isAnnotated(element, annotator)


    /**
     * Determines if the given GdClassVarDeclTl element represents a static variable declaration.
     *
     * @param element the GdClassVarDeclTl instance to check.
     * @return true if the provided element is a static variable declaration, false otherwise.
     */
    @JvmStatic
    fun isStatic(element: GdClassVarDeclTl): Boolean = PsiGdClassVarUtil.isStatic(element)


    /**
     * Retrieves the name of the given variable declaration element.
     *
     * @param element the variable declaration element from which the name will be retrieved
     * @return the name of the variable declaration as a string, or an empty string if no name is available
     */
    @JvmStatic
    fun getName(element: GdVarDeclSt): String = GdVarDeclStUtil.getName(element)


    /**
     * Retrieves the name of the given GdConstDeclSt element.
     *
     * @param element the GdConstDeclSt element from which the name is extracted.
     * @return the name of the element, or an empty string if the name is not available.
     */
    @JvmStatic
    fun getName(element: GdConstDeclSt): String = GdConstDeclUtil.getName(element)


    /**
     * Determines the return type of a given constant declaration in a GdScript file.
     *
     * @param element the GdConstDeclSt element representing the constant declaration.
     * @return the return type as a string, or an empty string if the return type cannot be determined.
     */
    @JvmStatic
    fun getReturnType(element: GdConstDeclSt): String = PsiGdLocalConstUtil.getReturnType(element)


    /**
     * Retrieves the return expression of a given constant declaration statement.
     *
     * @param element the constant declaration statement from which the return expression is to be extracted
     * @return the return expression as a PsiElement, or null if no return expression is present
     */
    @JvmStatic
    fun getReturnExpr(element: GdConstDeclSt): PsiElement? = PsiGdLocalConstUtil.getReturnExpr(element)


    /**
     * Checks whether a given method declaration element is marked as static.
     *
     * @param element the method declaration element to be checked.
     * @return `true` if the method is marked as static, otherwise `false`.
     */
    @JvmStatic
    fun isStatic(element: GdMethodDeclTl): Boolean = PsiGdMethodDeclUtil.isStatic(element)


    /**
     * Checks if the given method declaration is variadic (can accept a variable number of arguments).
     *
     * @param element the method declaration element to check.
     * @return true if the method is variadic, false otherwise.
     */
    @JvmStatic
    fun isVariadic(element: GdMethodDeclTl): Boolean = PsiGdMethodDeclUtil.isVariadic(element)


    /**
     * Retrieves the name of the specified method declaration element.
     *
     * @param element the method declaration element for which the name is to be retrieved
     * @return the name of the method as a string, or an empty string if the name is not available
     */
    @JvmStatic
    fun getName(element: GdMethodDeclTl): String = GdMethodUtil.getName(element)


    /**
     * Retrieves the presentation details for a given GdMethodDeclTl element.
     *
     * @param element the GdMethodDeclTl representing a method declaration for which the presentation details are requested.
     * @return an ItemPresentation object containing presentable text, a location string, and an icon associated with the method.
     */
    @JvmStatic
    fun getPresentation(element: GdMethodDeclTl): ItemPresentation = GdPresentationUtil.presentation(element)


    /**
     * Retrieves the return type of a given method declaration element.
     *
     * @param element the method declaration element from which to retrieve the return type.
     * @return the return type as a string. Returns an empty string if no return type is specified.
     */
    @JvmStatic
    fun getReturnType(element: GdMethodDeclTl): String = PsiGdMethodDeclUtil.getReturnType(element)


    /**
     * Retrieves the parameters of a given method declaration as a map.
     *
     * @param element The method declaration element from which to extract the parameters.
     * @return A LinkedHashMap where the keys are parameter names and the values are their corresponding types,
     *         or null if no type is specified.
     */
    @JvmStatic
    fun getParameters(element: GdMethodDeclTl): LinkedHashMap<String, String?> = PsiGdMethodDeclUtil.getParameters(element)


    /**
     * Determines if the given method declaration is a constructor.
     *
     * @param element the method declaration to check
     * @return true if the method is a constructor, false otherwise
     */
    @JvmStatic
    fun isConstructor(element: GdMethodDeclTl): Boolean = PsiGdMethodDeclUtil.isConstructor(element)


    /**
     * Returns the return type of the provided GdParam element.
     *
     * @param element the GdParam element whose return type is to be retrieved
     * @return a string representing the return type of the element, or an empty string if none is found
     */
    @JvmStatic
    fun getReturnType(element: GdParam): String = PsiGdMethodDeclUtil.getReturnType(element)


    /**
     * Retrieves the name of the given GdSignalDeclTl element.
     *
     * @param element The GdSignalDeclTl element for which the name is to be retrieved.
     * @return The name of the provided element as a String, or an empty string if no name is available.
     */
    @JvmStatic
    fun getName(element: GdSignalDeclTl): String = GdSignalUtil.getName(element)


    /**
     * Retrieves the parameters of the given GdSignalDeclTl element.
     *
     * @param element The GdSignalDeclTl element whose parameters need to be retrieved.
     * @return A LinkedHashMap containing parameter names as keys and their default values as nullable strings.
     */
    @JvmStatic
    fun getParameters(element: GdSignalDeclTl): LinkedHashMap<String, String?> = PsiGdSignalUtil.getParameters(element)


    /**
     * Retrieves the type of a given GdFlowSt element.
     *
     * @param element the GdFlowSt element whose type is to be determined
     * @return the type of the provided GdFlowSt element as a string
     */
    @JvmStatic
    fun getType(element: GdFlowSt): String = GdStmtUtil.getType(element)


    /**
     * Retrieves the return-type of a given expression, with an option to allow resource type consideration.
     *
     * @param element The expression for which the return type is to be determined.
     * @param allowResource A boolean flag indicating whether resource types should be considered. Defaults to `false`.
     * @return The return type of the expression as a string.
     */
    @JvmStatic
    fun getReturnTypeOrRes(element: GdExpr, allowResource: Boolean = false): String = PsiGdExprUtil.getReturnType(element, allowResource)


    /**
     * Determines the return-type of the given GdArgExpr element.
     *
     * @param element the GdArgExpr element for which the return type should be retrieved
     * @return the return type of the provided GdArgExpr as a String
     */
    @JvmStatic
    fun getReturnType(element: GdArgExpr): String = PsiGdExprUtil.getReturnType(element.expr)


    /**
     * Determines the return-type of the provided GdExpr element.
     *
     * @param element the GdExpr element whose return type needs to be determined
     * @return the return type of the element as a String, or an empty string if cycle detection fails
     */
    @JvmStatic
    fun getReturnType(element: GdExpr): String {
        return try {
            PsiGdExprUtil.getReturnType(element)
        } catch (e: StackOverflowError) {
            // Fallback in case cycle detection fails
            e.printStackTrace()
            ""
        }
    }


    /**
     * Retrieves the return-type of a given variable declaration.
     *
     * @param element the variable declaration element for which the return type is to be determined
     * @return the return type of the variable if it can be determined, or an empty string if an error occurs
     */
    @JvmStatic
    fun getReturnType(element: GdVarDeclSt): String {
        return try {
            PsiGdLocalVarUtil.getReturnType(element)
        } catch (e: StackOverflowError) {
            e.printStackTrace()
            ""
        }
    }


    /**
     * Retrieves the return-type of a given GDScript function declaration or lambda expression.
     *
     * @param element the GDScript function declaration for which the return type is being determined
     * @return the return type of the function as a string, or an empty string if no return type is specified
     */
    @JvmStatic
    fun getInvokedReturnType(element: GdFuncDeclEx): String = PsiGdLocalFuncUtil.getReturnType(element)


    /**
     * Retrieves the return expression element from the provided function declaration.
     *
     * @param element the function declaration element from which the return expression is extracted
     * @return a PsiElement representing the return expression, or null if not applicable
     */
// TODO remove?
    @JvmStatic
    fun getReturnExpr(element: GdFuncDeclEx): PsiElement? = PsiGdLocalFuncUtil.getReturnExpr(element)


    /**
     * Retrieves the parameters of a given function declaration.
     *
     * @param element the function declaration element from which the parameters are extracted
     * @return a LinkedHashMap where the keys are parameter names and the values are their default values or null if no default is defined
     */
    @JvmStatic
    fun getParameters(element: GdFuncDeclEx): LinkedHashMap<String, String?> = PsiGdLocalFuncUtil.getParameters(element)

}
