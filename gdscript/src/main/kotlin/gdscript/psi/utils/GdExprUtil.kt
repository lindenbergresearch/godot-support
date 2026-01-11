package gdscript.psi.utils

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.rd.util.firstOrNull
import gdscript.GdKeywords
import gdscript.psi.utils.GdClassMemberUtil.constructors
import gdscript.utils.StringUtil.parseFromSquare

/**
 * Utility object that provides methods for handling and evaluating type compatibility
 * within the context of custom types in the GdScript environment. It primarily works
 * with type strings to determine if one type can be accepted or assigned to another.
 */
object GdExprUtil {

    /**
     * Determines if a type can be assigned or converted from one type to another in the context of a given PSI element.
     *
     * @param from The source type being checked for compatibility.
     * @param into The target type to check compatibility against.
     * @param element The PSI element providing a project and other contextual information.
     * @return `true` if the types are compatible or can be converted; `false` otherwise.
     */
    fun typeAccepts(from: String, into: String, element: PsiElement): Boolean {
        if (DumbService.isDumb(element.project)) return true
        return typeAccepts(from, into, element.project)
    }


    /**
     * Determines if a type `from` can be assigned to a type `into` within the context of a given project.
     * The method accounts for type compatibility, inheritance, constructors, array types, and special exceptions.
     *
     * @param from the source type to check if it can be assigned
     * @param into the target type to check if it can accept the `from` type
     * @param project the IntelliJ project context in which type compatibility is evaluated
     * @return `true` if the `from` type can be assigned to the `into` type, otherwise `false`
     */
    fun typeAccepts(from: String, into: String, project: Project): Boolean {
        // both types are equals
        if (from == into) return true

        // blank
        if (from.isBlank() || into.isBlank()) return true

        // void cannot be assigned to anything except void
        if (into == GdKeywords.VOID) return false

        // left = right
        var left = into
        var right = from

        var arrayCount = 0
        if (from.startsWith(GdKeywords.ARRAY)) arrayCount++
        if (into.startsWith(GdKeywords.ARRAY)) arrayCount++

        // extract type if two arrays are involved
        if (arrayCount > 1) {
            left = left.parseFromSquare()
            right = right.parseFromSquare()
        }

        // test for exceptions
        if (allowedExceptions(left, right, project)) return true

        // check compatible types in both directions
        if (compatibleTypes(left, right) || compatibleTypes(right, left)) return true

        val classId = GdClassUtil.getClassIdElement(left, project)


        if (classId != null) {
            val classElement = GdClassUtil.getOwningClassElement(classId)

            // Constructor
            // todo: here it is too permissive, just checks that there is a ctor, which accepts "right" type as a first arg - doesn't make sense to me
            val clist = GdClassMemberUtil
                .listClassMemberDeclarations(classElement, constructors = true, includeUnnamedEnumValues = true)
                .constructors()

            clist.forEach {
                val v = it.parameters.firstOrNull()?.value
                if (v == left) return true
            }
        }

        // Inheritance
        val currentClassId = GdClassUtil.getClassIdElement(right, project)

        if (currentClassId != null) {
            val currentClassElement = GdClassUtil.getOwningClassElement(currentClassId)
            if (GdInheritanceUtil.isExtending(currentClassElement, left)) return true
        }

        return false
    }


    /**
     * Determines whether the types `left` and `right` are compatible, according to predefined rules.
     *
     * @param left the left type to check for compatibility
     * @param right the right type to check for compatibility
     * @return true if the types are compatible, false otherwise
     */
    private fun compatibleTypes(left: String, right: String): Boolean {
        val allowedStringTypes = arrayOf(GdKeywords.STRING_NAME, GdKeywords.NODE_PATH)
        val allowedNumberTypes = arrayOf(GdKeywords.INT, GdKeywords.FLOAT)

        if ((left == GdKeywords.STRING) && allowedStringTypes.contains(right)) {
            return true
        }
        if ((left == GdKeywords.FLOAT) && allowedNumberTypes.contains(right)) {
            return true
        }
        return !(((left != GdKeywords.INT) || !allowedNumberTypes.contains(right)))
    }


    /**
     * Determines if the given `left` or `right` strings are allowed exceptions based on specific conditions
     * related to project context and class inheritance.
     *
     * @param left the first string to assess, typically representing a class or resource identifier
     * @param right the second string to assess, often representing a class name or resource type
     * @param project the IntelliJ project context used for evaluating class or resource relationships
     * @return true if the given `left` or `right` values satisfy the conditions to be treated as exceptions,
     *         otherwise false
     */
    private fun allowedExceptions(left: String, right: String, project: Project): Boolean {
        if (arrayOf(GdKeywords.VARIANT, "RID").contains(left) ||
            arrayOf(GdKeywords.VARIANT).contains(right)
        ) return true

        if (arrayOf("Node", "Resource").contains(right)) {
            val currentClassId = GdClassUtil.getClassIdElement(left, project)
            if (currentClassId != null) {
                val currentClassElement = GdClassUtil.getOwningClassElement(currentClassId)
                return GdInheritanceUtil.isExtending(currentClassElement, right)
            }
        }

        return false
    }

}
