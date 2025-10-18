package gdscript.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.elementType
import com.intellij.psi.util.nextLeaf
import gdscript.GdKeywords
import gdscript.highlighter.GdHighlighterColors
import gdscript.psi.*
import gdscript.psi.impl.*
import gdscript.psi.utils.GdClassMemberUtil
import gdscript.psi.utils.GdClassUtil
import gdscript.reference.GdClassMemberReference
import gdscript.settings.GdProjectSettingsState
import gdscript.settings.GdProjectState
import gdscript.utils.PsiElementUtil.getCallExpr
import gdscript.utils.PsiFileUtil.isInSdk

/**
 * Colors references
 * Checks for existence
 */
class GdRefIdAnnotator : Annotator {
    private val objectContinuation = setOf(GdTypes.LRBR, GdTypes.LSBR, GdTypes.DOT)
    private val unresolvedTolerantTypes =
        setOf(
            GdKeywords.VARIANT,
            "Node",
            "Resource",
            "null"
        )

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Skip annotation if indices are not ready
        if (DumbService.isDumb(element.project)) {
            return
        }

        val state = GdProjectSettingsState.getInstance(element).state.annotators

        if (element !is GdRefIdRef && element !is GdVarNmiImpl && element !is GdNamedIdElement) {
            return
        }

        // Enum type declaration
        if (element is GdSignalIdNmiImpl) {
            holder
                .newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element.textRange)
                .textAttributes(GdHighlighterColors.SIGNAL)
                .create()
            return
        }

        // Enum type declaration
        if (element is GdVarNmiImpl && element.parent is GdParamImpl) {
            holder
                .newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element.textRange)
                .textAttributes(GdHighlighterColors.PARAMETER)
                .create()
            return
        }

        // Enum type declaration
        if (element is GdEnumDeclNmiImpl) {
            holder
                .newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element.textRange)
                .textAttributes(GdHighlighterColors.ENUM_TYPE)
                .create()
            return
        }

        // Enum value declaration
        if (element is GdEnumValueNmiImpl) {
            holder
                .newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element.textRange)
                .textAttributes(GdHighlighterColors.ENUM_VALUE)
                .create()
            return
        }

        val txt = element.text

        // ignore self and super keywords
        if (txt == GdKeywords.SELF || txt == GdKeywords.SUPER) {
            return
        }

        // handle math constants like PI, NAN ...
        if (GdKeywords.MATH_CONSTANTS.contains(txt)) {
            holder
                .newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element.textRange)
                .textAttributes(GdHighlighterColors.MATH_CONSTANT)
                .create()
            return
        }


        var attribute = GdHighlighterColors.METHOD_CALL
        val reference = element.references.firstOrNull()
      //  val calledUponExpr = GdClassMemberUtil.calledUpon(element)

//        if (calledUponExpr != null) {
//            println("Element: '${element.text}' called upon: '${calledUponExpr.text}' type: '${calledUponExpr.returnType}'")
//        }

        // check if there is at least one reference
        if (reference?.isSoft == false && reference is GdClassMemberReference) {
            // println("'${txt}' class=${element.javaClass.typeName} -> resolved='${reference.resolveDeclaration()}' psi_elem='${element}' attribute='${attribute.externalName}'")

            attribute = when (val resolved = reference.resolveDeclaration()) {
                is GdMethodDeclTl -> {
                    if (resolved.containingFile.name.endsWith("GlobalScope.gd")) GdHighlighterColors.GLOBAL_FUNCTION
                    else if (resolved.isStatic) GdHighlighterColors.STATIC_METHOD_CALL
                    else if (txt.startsWith('_')) GdHighlighterColors.SPECIAL_METHOD
                    else GdHighlighterColors.METHOD_CALL
                }

                is GdVarDeclStImpl -> {
                    //       //println("+++ MATCHED LOCAL VAR => ${txt} ${resolved}");
                    GdHighlighterColors.LOCAL_VARIABLE
                }

                is GdConstDeclTlImpl -> {
                    //     //println("+++ MATCHED CONST => ${txt} ${resolved}");
                    GdHighlighterColors.CONSTANT
                }

                is GdParamImpl -> {
//                         //println("+++ MATCHED PARAMETER => ${txt} ${resolved}");
                    GdHighlighterColors.PARAMETER
                }

                is GdEnumDeclTlImpl, is GdEnumDeclTl, is GdEnumDeclNmiImpl -> {
                    //       println("enum: ${element.elementType} text: '${element.text}'")
                    GdHighlighterColors.CLASS_TYPE
                }

                is GdEnumValueImpl -> {
                    //     println("enum VAL: ${element.elementType} member: '${element.text}'")
                    GdHighlighterColors.ENUM_VALUE
                }


                is PsiFile, is GdClassDeclTl, is GdClassNaming -> {
                    var psi = resolved
                    if (resolved is GdClassNaming) {
                        psi = psi.parent!!
                    }

                    if (psi.containingFile.isInSdk()) {
                        val nextLeaf = element.nextLeaf(true)
                        if (!objectContinuation.contains(nextLeaf.elementType) && psi.childrenOfType<GdMethodDeclTl>().any { it.isConstructor }) {
                            holder.newAnnotationGd(
                                element.project,
                                HighlightSeverity.ERROR,
                                "Builtin type $txt cannot be assigned to a variable"
                            ).range(element.textRange).create()

                            return
                        }

                        GdHighlighterColors.ENGINE_TYPE
                    } else {
                        GdHighlighterColors.CLASS_TYPE
                    }
                }

                null -> run {
                    if (element.text == "new" || GdClassMemberUtil.calledUpon(element)?.returnType == "Dictionary") {
                        return@run GdHighlighterColors.MEMBER
                    }

                    val calledUponExpr = GdClassMemberUtil.calledUpon(element)
                    // For undefined types do not mark it as error
                    if (calledUponExpr != null) {
                        // If qualifier is a node path, skip error
                        if (PsiTreeUtil.findChildOfType(calledUponExpr, GdNodePath::class.java) != null)
                            return@run GdHighlighterColors.MEMBER

                        // If qualifier resolves to a named enum, allow enum member access only for existing enum values
                        run {
                            val decl = GdClassMemberUtil.findDeclaration(calledUponExpr)
                            if (decl is GdEnumDeclTl) {
                                val name = element.text
                                val isMember = decl.enumValueList.any { it.enumValueNmi.name == name }
                                if (isMember) return@run GdHighlighterColors.MEMBER
                                // otherwise, fall through to unresolved reference error
                            }
                        }

                        val callType = calledUponExpr.returnType
                        if (callType in unresolvedTolerantTypes)
                            return@run GdHighlighterColors.MEMBER
                    }

                    if (element.getCallExpr() != null && GdClassMemberUtil.hasMethodCheck(element))
                        return@run GdHighlighterColors.METHOD_CALL

                    holder.newAnnotationGd(
                        element.project,
                        GdProjectState.selectedLevel(state),
                        "Reference [${element.text}] not found"
                    ).range(element.textRange).create()

                //    println("Annotating: ${element}(${element.text}) range=${element.textRange} file=${reference.resolveDeclaration()} as ${element.javaClass.typeName} with state '$state'")

                    return
                }

                else -> GdHighlighterColors.MEMBER
            }
        }

        if (attribute == GdHighlighterColors.MEMBER && element.getCallExpr() != null) {
            attribute = GdHighlighterColors.METHOD_CALL
        }

        if (element is GdVarNmiImpl) {
            //println("+++ MATCHED VAR => ${txt} parent=${element.parent} ${element.javaClass.typeName} ${attribute.externalName}")

            if (element.parent is GdConstDeclTlImpl) {
                //println("+++ MATCHED CONST => ${txt} ${element.javaClass.typeName} ${attribute.externalName}")
                attribute = GdHighlighterColors.CONSTANT
            }
            if (element.parent is GdClassVarDeclTlImpl) {
                //println("+++ MATCHED CLASS VAR => ${txt} ${element.javaClass.typeName} ${attribute.externalName}")
                attribute = GdHighlighterColors.MEMBER
            }


            if (element.parent is GdVarDeclStImpl) {
                //println("+++ MATCHED LOCAL VAR => ${txt} ${element.javaClass.typeName} ${attribute.externalName}")
                attribute = GdHighlighterColors.LOCAL_VARIABLE
            }
        }


//        if (reference is GdClassMemberReference) {
//            //println("${txt} -> resolved='${reference.resolveDeclaration()}' psi_elem='${element}' attribute='${attribute.externalName}'")
//        }

        holder
            .newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(element.textRange)
            .textAttributes(attribute)
            .create()
    }

}
