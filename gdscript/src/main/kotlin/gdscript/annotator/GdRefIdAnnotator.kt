package gdscript.annotator

import com.intellij.lang.annotation.*
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.*
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
 * GdRefIdAnnotator is responsible for annotating PSI elements within Godot script files.
 * It applies various text attribute keys to elements based on their semantic meaning in the code.
 * The annotations provide visual feedback in the IntelliJ IDE to enhance code readability and help the developer understand the context and type of the elements.
 */
class GdRefIdAnnotator : Annotator {
    /**
     * A private set representing the continuation symbols used for object references.
     *
     * This set includes specific symbols (e.g., `LRBR`, `LSBR`, and `DOT` from `GdTypes`)
     * that denote the continuation points in object-related expressions. Used internally
     * to aid in annotation purposes within the context of the `GdRefIdAnnotator` class.
     */
    private val objectContinuation = setOf(GdTypes.LRBR, GdTypes.LSBR, GdTypes.DOT)
    /**
     * A set containing types that are considered tolerant for unresolved references.
     * These types do not trigger specific unresolved reference annotations
     * and are treated as exceptions during the annotation process.
     */
    private val unresolvedTolerantTypes =
        setOf(
            GdKeywords.VARIANT,
            "Node",
            "Resource",
            GdKeywords.NULL
        )

    /**
     * Annotates the given PSI element with appropriate syntax highlighting or error annotations based on its type,
     * context, and resolved references within the codebase.
     *
     * @param element The PSI element to be analyzed and annotated.
     * @param holder The annotation holder used to register annotations for the given element.
     */
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Skip annotation if indices are not ready
        if (DumbService.isDumb(element.project)) {
            return
        }


        if (element !is GdRefIdRef && element !is GdVarNmiImpl && element !is GdNamedIdElement && element !is GdClassNameNmi) {
            return
        }

        // Signal type declaration
        if (element is GdSignalIdNmiImpl) {
            holder
                .newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element.textRange)
                .textAttributes(GdHighlighterColors.SIGNAL)
                .create()
            return
        }

        // Var type declaration
        if (element is GdVarNmiImpl && element.parent is GdParamImpl) {
            holder
                .newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element.textRange)
                .textAttributes(GdHighlighterColors.PARAMETER)
                .create()
            return
        }

        // Enum Decl type declaration
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

        // Setter parameter value declaration
        if (element is GdVarNmiImpl && element.parent is GdSetDeclImpl) {
            holder
                .newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element.textRange)
                .textAttributes(GdHighlighterColors.PARAMETER)
                .create()
            return
        }

        val txt = element.text.trim()

        // ignore self and super keywords
        //TODO: add coloring for them
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

        // check if there is at least one reference
        if (reference?.isSoft == false && reference is GdClassMemberReference) {
            val resolved = reference.resolveDeclaration()

            attribute = when (resolved) {
                is GdMethodDeclTl -> {
                    if (resolved.containingFile.name.endsWith("GlobalScope.gd")) GdHighlighterColors.GLOBAL_FUNCTION
                    else if (resolved.isStatic) GdHighlighterColors.STATIC_METHOD_CALL
                    else if (txt.startsWith(GdKeywords.SPECIAL_NAME_PREFIX)) GdHighlighterColors.SPECIAL_METHOD
                    else if (txt == GdKeywords.PRELOAD) GdHighlighterColors.KEYWORD
                    else GdHighlighterColors.METHOD_CALL
                }

                is GdVarDeclStImpl -> {
                    GdHighlighterColors.LOCAL_VARIABLE
                }

                is GdConstDeclTlImpl -> {
                    GdHighlighterColors.CONSTANT
                }

                is GdParamImpl -> {
                    GdHighlighterColors.PARAMETER
                }

                is GdEnumDeclTlImpl, is GdEnumDeclTl, is GdEnumDeclNmiImpl -> {
                    GdHighlighterColors.ENUM_TYPE
                }

                is GdEnumValueImpl, is GdEnumValueNmiImpl -> {
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
                    // For undefined types do not mark it as an error
                    if (calledUponExpr != null) {
                        // If qualifier is a node path, skip the error
                        if (PsiTreeUtil.findChildOfType(calledUponExpr, GdNodePath::class.java) != null)
                            return@run GdHighlighterColors.MEMBER

                        // If a qualifier resolves to a named enum, allow enum member access only for existing enum values
                        run {
                            val decl = GdClassMemberUtil.findDeclaration(calledUponExpr)

                            // Check if it's a direct enum declaration
                            if (decl is GdEnumDeclTl) {
                                val name = element.text.trim()
                                val isMember = decl.enumValueList.any { it.enumValueNmi.name == name }
                                if (isMember) return@run GdHighlighterColors.ENUM_VALUE
                                // otherwise, fall through to unresolved reference error
                            }

                            // Check if it's a class with anonymous enums
                            if (decl is GdClassDeclTl || decl is GdClassNaming) {
                                val classElement = if (decl is GdClassNaming) {
                                    GdClassUtil.getOwningClassElement(decl)
                                } else {
                                    decl
                                }

                                if (classElement is GdClassDeclTl) {
                                    val name = element.text
                                    val allEnums = classElement.childrenOfType<GdEnumDeclTl>()
                                    for (enumDecl in allEnums) {
                                        val isMember = enumDecl.enumValueList.any { it.enumValueNmi.name == name }

                                        if (isMember) return@run GdHighlighterColors.ENUM_VALUE
                                    }
                                }
                            }
                        }

                        val callType = calledUponExpr.returnType
                        if (callType in unresolvedTolerantTypes)
                            return@run GdHighlighterColors.MEMBER
                    }

                    if (element.getCallExpr() != null && GdClassMemberUtil.hasMethodCheck(element))
                        return@run GdHighlighterColors.METHOD_CALL

                    val state = GdProjectSettingsState.getInstance(element).state.annotators

                    holder.newAnnotationGd(
                        element.project,
                        GdProjectState.selectedLevel(state),
                        "Reference: [${element.text}] not found. $element ${element.elementType} ${element.javaClass.typeName}"
                    ).range(element.textRange).create()

                    return
                }

                else ->
                    if (resolved.containingFile.name.endsWith("GlobalScope.gd")) {
                        GdHighlighterColors.GLOBAL_FUNCTION
                    } else {
                        GdHighlighterColors.MEMBER
                    }
            }
        }

        if (attribute == GdHighlighterColors.MEMBER && element.getCallExpr() != null) {
            attribute = GdHighlighterColors.METHOD_CALL
        }

        if (element is GdVarNmiImpl) {
            if (element.parent is GdConstDeclTlImpl) {
                attribute = GdHighlighterColors.CONSTANT
            }

            if (element.parent is GdClassVarDeclTlImpl) {
                attribute = GdHighlighterColors.MEMBER
            }

            if (element.parent is GdVarDeclStImpl) {
                attribute = GdHighlighterColors.LOCAL_VARIABLE
            }
        }

        holder
            .newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(element.textRange)
            .textAttributes(attribute)
            .create()
    }

}
