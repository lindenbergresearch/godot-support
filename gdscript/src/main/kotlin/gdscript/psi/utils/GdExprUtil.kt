package gdscript.psi.utils

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import gdscript.GdKeywords
import gdscript.utils.StringUtil.parseFromSquare

object GdExprUtil {

    fun typeAccepts(from: String, into: String, element: PsiElement): Boolean {
        return typeAccepts(from, into, element.project)
    }

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

    //    val classId = GdClassUtil.getClassIdElement(left, project)

  //      println("classId: '$classId' ${classId?.text?.toText()}")

//        if (classId != null) {
//            val classElement = GdClassUtil.getOwningClassElement(classId)
//
//            // Constructor
//            // todo: here it is too permissive, just checks that there is a ctor, which accepts "right" type as a first arg - doesn't make sense to me
//            var clist = GdClassMemberUtil
//                .listClassMemberDeclarations(classElement, constructors = true)
//                .constructors()
//
//                println("clist: $clist")
//                clist.forEach {
//                    val v = it.parameters.firstOrNull()?.value
//                    println("   => <$v>")
//                    if (v == right) return true
//                }
//        }

        // Inheritance
        val currentClassId = GdClassUtil.getClassIdElement(right, project)

        if (currentClassId != null) {
            val currentClassElement = GdClassUtil.getOwningClassElement(currentClassId)
            if (GdInheritanceUtil.isExtending(currentClassElement, left)) return true
        }

        return false
    }

    private fun compatibleTypes(left: String, right: String): Boolean {
        val allowedStringTypes = arrayOf(GdKeywords.STRING_NAME, GdKeywords.NODE_PATH)
        val allowedNumberTypes = arrayOf(GdKeywords.INT, GdKeywords.FLOAT)

        if (left == GdKeywords.STRING && allowedStringTypes.contains(right)) return true
        if (left == GdKeywords.FLOAT && allowedNumberTypes.contains(right)) return true
        if (left == GdKeywords.INT && allowedNumberTypes.contains(right)) return true

        return false
    }

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
