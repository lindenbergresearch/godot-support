package gdscript.annotator

import com.intellij.lang.annotation.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbService
import com.intellij.psi.*
import com.intellij.psi.util.*
import fleet.multiplatform.shims.currentThreadId
import gdscript.GdKeywords
import gdscript.highlighter.GdHighlighterColors
import gdscript.psi.*
import gdscript.psi.impl.*
import gdscript.psi.utils.*
import gdscript.reference.GdClassMemberReference
import gdscript.settings.GdProjectSettingsState
import gdscript.settings.GdProjectState
import gdscript.utils.PsiElementUtil.getCallExpr
import gdscript.utils.PsiFileUtil.isInSdk
import java.io.File


/**
 * GdRefIdAnnotator is responsible for annotating PSI elements within Godot script files.
 * It applies various text attribute keys to elements based on their semantic meaning in the code.
 * The annotations provide visual feedback in the IntelliJ IDE to enhance code readability and help the developer understand the context and type of the elements.
 */
class GdRefIdAnnotator : Annotator {
    private val logger = logger<GdRefIdAnnotator>()

    private fun extractSourceInfo(element: PsiElement): String {
        val document = element.containingFile.viewProvider.document
        val lineno = document.getLineNumber(element.textOffset)
        val offs = element.textOffset
        val lineOffs = offs - document.getLineStartOffset(lineno)

        return "offset=" + offs.toString().limitPad(6) + "; line=" + (lineno + 1).toString().limitPad(5) + "; lineOffs=" + lineOffs.toString().limitPad(3)
    }

    private fun extractElementPath(element: PsiElement, depth: Int = 3, noDecl: Boolean = false, noRet: Boolean = false): String {
        var i = 0
        var current = element.parent.parent
        var s = ""

        while (i++ < depth && current.parent != null) {
            s += "[$i]" + current.javaClass.simpleName.limitPad(20) + ";"
            current = current.parent
        }

        var t0 = System.nanoTime()
        val called = GdClassMemberUtil.calledUpon(element)
        val t1 = System.nanoTime() - t0

        t0 = System.nanoTime()
        val calledRet = if (noRet) "---" else called?.returnType
        val t2 = System.nanoTime() - t0

        t0 = System.nanoTime()
        val rsdecl = if (noDecl) "---" else GdClassMemberUtil.findDeclaration(element)
        val t3 = System.nanoTime() - t0


        val t1s = "%.4f".format(t1.toDouble() / 10e5)
        val t2s = if (noRet) "---" else "%.4f".format(t2.toDouble() / 10e5)
        val t3s = if (noDecl) "---" else "%.4f".format(t3.toDouble() / 10e5)

        return s + "; " +
            "calUp=" + called.toString().limitPad(30) + ";" +
            "callT=" + " ${t1s.limitPad(12, doRight = true)} ;" +
            "rettye=" + calledRet.toString().limitPad(16) +
            "; retT=${t2s.limitPad(12, doRight = true)}ms; " +
            "findDecl=" + rsdecl.toString().limitPad(22) + ";" +
            "findDecT=" + t3s.limitPad(12, doRight = true) + "ms; "
    }


    /**
     * Executes [block], returns its duration in nanoseconds, and logs a warning if it exceeds [warnAfterMs].
     */
    @OptIn(ExperimentalStdlibApi::class)
    private inline fun perfNanos(
        name: String,
        element: PsiElement,
        warnAfterMs: Double,
        block: () -> Unit
    ): Long {
        val t0 = System.nanoTime()
        try {
            block()
        } finally {
            val dt = System.nanoTime() - t0
            val dtMs = dt / 1_000_000.0
            val dtStart = (System.nanoTime() - start) / 10e8
            count2++
            val id = "$${currentThreadId()}-0x${this.hashCode().toHexString()} "

            val filePath = element.containingFile?.virtualFile?.path ?: element.containingFile?.name ?: "<no-file>"
            val fileName = filePath.split('/').takeLast(2).joinToString("/")

            if (lastFileName != fileName || lastID != id) {
                logger.info("++++++++++ start examining file: $fileName $id +++++++++++")
                countFile = 0;
                count = 0;
                time = 0.0;
                timeFile = System.nanoTime()
                lastFileName = fileName
                lastID = id
            }

            countFile++

            if (dtMs > warnAfterMs) {
                time += dtMs
                val sourceInfo = extractSourceInfo(element)
                val elementPath = extractElementPath(element, 4)


                logger.warn(
                    " ${fileName.limitPad(23)}" +

                        "${count++.toString().limitPad(6, doRight = true)}/" + "${count2.toString().limitPad(6, doRight = true)} | #" + "${countFile.toString().limitPad(6, doRight = true)} | " +
                        " '${element.text.limitPad(30)}'" +
                        "${"dt=%.2f".format(dtMs)}ms | ".limitPad(20, doRight = true) +
                        "${"dtFile=%.2f".format((System.nanoTime() - timeFile) / 1_000_000.0)}ms | ".limitPad(25, doRight = true) +
                        "${"time=%.2f".format(time / 1000.0)}s | ".limitPad(25, doRight = true) +
                        "${"start=%.2f".format(dtStart)}s |".limitPad(25, doRight = true) +
                        sourceInfo +
                        elementPath +
                        id
                )

                stringCV = fileName + id + ";" +
                    ";" + count.toString() + ";" + count2.toString() + ";" + countFile.toString() + ";" +
                    "%.4f".format(dtMs) + ";" + "${"%.2f".format((System.nanoTime() - timeFile) / 1_000_000.0)};" +
                    "%.4f".format(time / 1000.0) + ";" + "%.4f".format(dtStart) +
                    ";\"" + element.text + "\";" +
                    ";\"" + element.parent.text.limitPad(160) + "\";" +
                    name + ";" + element + ";" +

                    sourceInfo + ";" + elementPath +
                    System.lineSeparator()

                val file = File(fname)

                if (!file.exists()) {
                    file.createNewFile()
                }

                file.appendText(stringCV)

            }
        }
        return System.nanoTime() - t0
    }

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

    private fun signalTargetRoot(element: PsiElement): PsiElement? {
        val byName = GdClassUtil.getClassIdElement("Signal", element, element.project)
        val byKeyword = GdClassUtil.getClassIdElement(GdKeywords.SIGNAL, element, element.project)
        val signalClass = byName ?: byKeyword ?: return null

        when (signalClass) {
            is GdClassDeclTl,
            is GdClassNaming,
            is GdClassNameNmi -> return signalClass
        }

        val classNaming = PsiTreeUtil.getParentOfType(signalClass, GdClassNaming::class.java, false)
        if (classNaming != null) {
            return classNaming
        }

        val containingDecl = PsiTreeUtil.getParentOfType(signalClass, GdClassDeclTl::class.java, false)
        if (containingDecl != null) {
            return containingDecl
        }

        val owningDecl = GdClassUtil.getOwningClassElement(signalClass)
        return owningDecl

        return signalClass.containingFile ?: signalClass
    }

    private fun hasSignalMember(element: PsiElement, memberName: String): Boolean {
        val signalRoot = signalTargetRoot(element) ?: return false

        val fromClassMembers = when (signalRoot) {
            is GdClassDeclTl -> GdClassMemberUtil.listClassMemberDeclarations(
                signalRoot,
                static = false,
                search = memberName,
                constructors = false,
                isRecursive = true,
                includeUnnamedEnumValues = true,
            ).isNotEmpty()

            is GdClassNaming -> GdClassMemberUtil.listClassMemberDeclarations(
                signalRoot,
                static = false,
                search = memberName,
                constructors = false,
                isRecursive = true,
                includeUnnamedEnumValues = true,
            ).isNotEmpty()

            is GdClassNameNmi -> {
                val classNaming = PsiTreeUtil.getParentOfType(signalRoot, GdClassNaming::class.java, false)
                if (classNaming != null) {
                    GdClassMemberUtil.listClassMemberDeclarations(
                        classNaming,
                        static = false,
                        search = memberName,
                        constructors = false,
                        isRecursive = true,
                        includeUnnamedEnumValues = true,
                    ).isNotEmpty()
                } else {
                    false
                }
            }

            else -> false
        }
        if (fromClassMembers) {
            return true
        }

        val searchRoot = when (signalRoot) {
            is GdClassNameNmi -> signalRoot.parent ?: signalRoot
            else -> signalRoot
        }

        return PsiTreeUtil.findChildrenOfType(searchRoot, PsiNamedElement::class.java)
            .any { it.name == memberName }
    }

    private fun isSignalMethodLike(member: PsiElement?): Boolean {
        return member is GdMethodIdNmi || member is GdMethodDeclTl
    }

    private fun resolveSelfMember(element: PsiElement, memberName: String): PsiElement? {
        val nearestClassDecl = PsiTreeUtil.getParentOfType(element, GdClassDeclTl::class.java, false)
        val owningClass = GdClassUtil.getOwningClassElement(element)
        val containingFile = element.containingFile as? GdFile

        val classRoot = when {
            nearestClassDecl != null -> nearestClassDecl as PsiElement
            owningClass is GdClassDeclTl -> owningClass
            owningClass is GdClassNaming -> owningClass
            containingFile != null -> containingFile
            else -> null
        } ?: return null

        val fromClassMembers = when (classRoot) {
            is GdClassDeclTl -> GdClassMemberUtil.listClassMemberDeclarations(
                classRoot,
                static = false,
                search = memberName,
                constructors = false,
                isRecursive = true,
                includeUnnamedEnumValues = true,
            ).firstOrNull()

            is GdClassNaming -> GdClassMemberUtil.listClassMemberDeclarations(
                classRoot,
                static = false,
                search = memberName,
                constructors = false,
                isRecursive = true,
                includeUnnamedEnumValues = true,
            ).firstOrNull()

            is GdFile -> {
                val declarations = buildList<PsiElement> {
                    addAll(PsiTreeUtil.getStubChildrenOfTypeAsList(classRoot, GdClassVarDeclTl::class.java))
                    addAll(PsiTreeUtil.getStubChildrenOfTypeAsList(classRoot, GdConstDeclTl::class.java))
                    addAll(PsiTreeUtil.getStubChildrenOfTypeAsList(classRoot, GdMethodDeclTl::class.java))
                    addAll(PsiTreeUtil.getStubChildrenOfTypeAsList(classRoot, GdSignalDeclTl::class.java))
                    addAll(PsiTreeUtil.getStubChildrenOfTypeAsList(classRoot, GdEnumDeclTl::class.java))
                    addAll(PsiTreeUtil.getStubChildrenOfTypeAsList(classRoot, GdClassDeclTl::class.java))
                }
                declarations.firstOrNull { decl ->
                    when (decl) {
                        is PsiNamedElement -> decl.name == memberName
                        else -> false
                    }
                }
            }

            else -> null
        }

        if (fromClassMembers != null) {
            return fromClassMembers
        }

        return PsiTreeUtil.findChildrenOfType(classRoot, PsiNamedElement::class.java)
            .firstOrNull { it.name == memberName } as? PsiElement
    }

    private fun resolveSignalMember(element: PsiElement, memberName: String): PsiElement? {


        val signalRoot = signalTargetRoot(element) ?: return null

        val fromClassMembers = when (signalRoot) {
            is GdClassDeclTl -> GdClassMemberUtil.listClassMemberDeclarations(
                signalRoot,
                static = false,
                search = memberName,
                constructors = false,
                isRecursive = true,
                includeUnnamedEnumValues = true,
            ).firstOrNull()

            is GdClassNaming -> GdClassMemberUtil.listClassMemberDeclarations(
                signalRoot,
                static = false,
                search = memberName,
                constructors = false,
                isRecursive = true,
                includeUnnamedEnumValues = true,
            ).firstOrNull()

            is GdClassNameNmi -> {
                val classNaming = PsiTreeUtil.getParentOfType(signalRoot, GdClassNaming::class.java, false)
                if (classNaming != null) {
                    GdClassMemberUtil.listClassMemberDeclarations(
                        classNaming,
                        static = false,
                        search = memberName,
                        constructors = false,
                        isRecursive = true,
                        includeUnnamedEnumValues = true,
                    ).firstOrNull()
                } else {
                    null
                }
            }

            else -> null
        }
        if (fromClassMembers != null) {
            return fromClassMembers
        }

        val searchRoot = when (signalRoot) {
            is GdClassNameNmi -> signalRoot.parent ?: signalRoot
            else -> signalRoot
        }

        val named = PsiTreeUtil.findChildrenOfType(searchRoot, PsiNamedElement::class.java)
            .firstOrNull { it.name == memberName }

        return named as? PsiElement
    }

    private fun isSignalLikeExpression(expr: PsiElement?): Boolean {
        if (expr == null || expr !is GdExpr) return false

        val decl = GdClassMemberUtil.findDeclaration(expr)
        if (decl is GdSignalDeclTl) return true

        val returnType = expr.returnType
        if (returnType == GdKeywords.SIGNAL || returnType == "Signal") return true
        if (returnType.endsWith(".${GdKeywords.SIGNAL}") || returnType.endsWith(".Signal")) return true

        val dictElement = PsiGdExprUtil.resolveDictPathElement(expr)
        val dictExpr = dictElement as? GdExpr
        val dictType = dictExpr?.returnType
        if (dictType == GdKeywords.SIGNAL || dictType == "Signal") return true
        if (dictType?.endsWith(".${GdKeywords.SIGNAL}") == true || dictType?.endsWith(".Signal") == true) return true

        return false
    }


    @OptIn(ExperimentalStdlibApi::class)
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Measure only the work we actually do.
        val dt = perfNanos("GdRefIdAnnotator.annotate2", element, warnAfterMs = 400.0) {
            annotate2(element, holder)
        }

    }


    /**
     * Annotates the given PSI element with appropriate syntax highlighting or error annotations based on its type,
     * context, and resolved references within the codebase.
     *
     * @param element The PSI element to be analyzed and annotated.
     * @param holder The annotation holder used to register annotations for the given element.
     */
    fun annotate2(element: PsiElement, holder: AnnotationHolder) {
        // Skip annotation if indices are not ready
        if (DumbService.isDumb(element.project)) {
            return
        }

        if (
            (element !is GdRefIdRef) &&
            (element !is GdVarNmiImpl) &&
            (element !is GdNamedIdElement) &&
            (element !is GdClassNameNmi)
        ) {
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

        val elementText = element.text.trim()

        // self and super keywords
        //TODO: add coloring for them
        if (elementText == GdKeywords.SELF || elementText == GdKeywords.SUPER) {
            holder
                .newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element.textRange)
                .textAttributes(GdHighlighterColors.KEYWORD)
                .create()
            return
        }

        // handle math constants like PI, NAN ...
        if (GdKeywords.MATH_CONSTANTS.contains(elementText)) {
            holder
                .newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element.textRange)
                .textAttributes(GdHighlighterColors.MATH_CONSTANT)
                .create()
            return
        }


        var attribute = GdHighlighterColors.METHOD_CALL
        val reference = element.references.firstOrNull()
        val parRef = element.parent.references.firstOrNull()

        // check if there is at least one reference
        if ((reference?.isSoft == false) && (reference is GdClassMemberReference)) {
            val resolved = reference.resolveDeclaration()

            val calledUponExpr = GdClassMemberUtil.calledUpon(element)
            if (calledUponExpr?.text == GdKeywords.SELF) {
                val selfMember = resolveSelfMember(element, elementText)
                if (selfMember == null) {
                    holder.newAnnotationGd(
                        element.project,
                        HighlightSeverity.ERROR,
                        "Unresolved self member '$elementText'"
                    ).range(element.textRange).create()
                    return
                }
            }

            if (isSignalLikeExpression(calledUponExpr) && isSignalMethodLike(resolved) && element.getCallExpr() == null) {
                holder.newAnnotationGd(
                    element.project,
                    HighlightSeverity.ERROR,
                    "Signal method '$elementText' must be called with parentheses"
                ).range(element.textRange).create()
                return
            }

            attribute = when (resolved) {
                is GdMethodDeclTl -> {
                    //   println("${Ansi.BRIGHT_MAGENTA}methode decl: ${elementText.symbolizeWS().limitPad(30)} resolved=$resolved resFile=${resolved.containingFile.name}")
                    if (resolved.containingFile.name.endsWith("GlobalScope.gd")) GdHighlighterColors.GLOBAL_FUNCTION
                    else if (resolved.isStatic) GdHighlighterColors.STATIC_METHOD_CALL
                    else if (elementText.startsWith(GdKeywords.SPECIAL_NAME_PREFIX)) GdHighlighterColors.SPECIAL_METHOD
                    else if (elementText == GdKeywords.PRELOAD) GdHighlighterColors.KEYWORD
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
                                "Builtin type $elementText cannot be assigned to a variable"
                            ).range(element.textRange).create()

                            return
                        }

                        GdHighlighterColors.ENGINE_TYPE
                    } else {
                        GdHighlighterColors.CLASS_TYPE
                    }
                }

                null -> run {
                    val t0 = System.nanoTime()
                    val calledUponExpr = GdClassMemberUtil.calledUpon(element)

                    if (elementText == GdKeywords.NEW || calledUponExpr?.returnType == GdKeywords.DICTIONARY) {
                        return@run GdHighlighterColors.MEMBER
                    }

                    if (calledUponExpr?.text == GdKeywords.SELF) {
                        val selfMember = resolveSelfMember(element, elementText)
                        if (selfMember != null) {
                            return@run when (selfMember) {
                                is GdMethodDeclTl -> {
                                    if (element.getCallExpr() != null) GdHighlighterColors.METHOD_CALL else GdHighlighterColors.METHOD_DECLARATION
                                }
                                else -> GdHighlighterColors.MEMBER
                            }
                        }

                        holder.newAnnotationGd(
                            element.project,
                            HighlightSeverity.ERROR,
                            "Unresolved self member '$elementText'"
                        ).range(element.textRange).create()
                        return
                    }

                    if (isSignalLikeExpression(calledUponExpr)) {
                        val signalMember = resolveSignalMember(element, elementText)
                        if (signalMember != null) {
                            val isMethodLike = isSignalMethodLike(signalMember)

                            if (isMethodLike && element.getCallExpr() == null) {
                                holder.newAnnotationGd(
                                    element.project,
                                    HighlightSeverity.ERROR,
                                    "Signal method '$elementText' must be called with parentheses"
                                ).range(element.textRange).create()
                                return
                            }

                            return@run if (isMethodLike) {
                                GdHighlighterColors.METHOD_CALL
                            } else {
                                GdHighlighterColors.MEMBER
                            }
                        }
                    }


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
                                val isMember = decl.enumValueList.any { it.enumValueNmi.name == elementText }
                                if (isMember) return@run GdHighlighterColors.ENUM_VALUE
                                // otherwise, fall through to unresolved reference error
                            }

                            // Check if it's a class with anonymous enums
                            if (decl is GdClassDeclTl || decl is GdClassNaming || decl is GdSignalDeclTl) {
                                val classElement = if (decl is GdClassNaming) {
                                    GdClassUtil.getOwningClassElement(decl)
                                } else {
                                    decl
                                }

                                if (classElement is GdClassDeclTl) {
                                    val allEnums = classElement.childrenOfType<GdEnumDeclTl>()
                                    for (enumDecl in allEnums) {
                                        val isMember = enumDecl.enumValueList.any { it.enumValueNmi.name == elementText }
                                        if (isMember) return@run GdHighlighterColors.ENUM_VALUE
                                    }
                                }
                            }
                        }

                        val callType = calledUponExpr.returnType
                        if (callType == GdKeywords.SIGNAL || callType == "Signal") {
                            val signalMember = resolveSignalMember(element, elementText)
                            if (signalMember != null) {
                                val isMethodLike = isSignalMethodLike(signalMember)

                                if (isMethodLike && element.getCallExpr() == null) {
                                    holder.newAnnotationGd(
                                        element.project,
                                        HighlightSeverity.ERROR,
                                        "Signal method '$elementText' must be called with parentheses"
                                    ).range(element.textRange).create()
                                    return
                                }

                                return@run if (isMethodLike) {
                                    GdHighlighterColors.METHOD_CALL
                                } else {
                                    GdHighlighterColors.MEMBER
                                }
                            }
                        }

                        if ((callType in unresolvedTolerantTypes) || callType.startsWith(GdKeywords.RESOURCE_PREFIX)) {
                            return@run GdHighlighterColors.MEMBER
                        } else {
                            //   val decl = GdClassMemberUtil.findDeclaration(calledUponExpr)
//                            val owning = if (decl is PsiElement) GdClassUtil.getOwningClassElement(decl) else null
//                            println(
//                                "${Ansi.YELLOW}owning='${owning?.javaClass?.simpleName.toString().padEnd(30)}' callType='${callType.symbolizeWS().limitPad(30)}' elementText='${
//                                    elementText.symbolizeWS().limitPad(30)
//                                }' decl=${
//                                    GdClassMemberUtil.findDeclaration(calledUponExpr).toString().symbolizeWS().limitPad(30)
//                                } calledUpOn='${
//                                    calledUponExpr.toString().symbolizeWS().limitPad(30)
//                                }' calledUpOnRet='${calledUponExpr.returnType.symbolizeWS().limitPad(30)}' callExpr?=${element.getCallExpr()} hasMethodCheck=${GdClassMemberUtil.hasMethodCheck(element)}"
//                            )
                        }
                    }

                    if (element.getCallExpr() != null && GdClassMemberUtil.hasMethodCheck(element))
                        return@run GdHighlighterColors.METHOD_CALL

                    val state = GdProjectSettingsState.getInstance(element).state.annotators

                    val t = (System.nanoTime() - t0).toDouble() / 1_000_000
                    val ts = "${t}ms"
                    var decl: String? = "none"

                    if (parRef is GdClassMemberReference) {
                        decl = parRef.resolveDeclaration()?.javaClass?.simpleName
                    }

                    val text = element.parent.text

                    // Dictionary member access may already start at the first qualified segment,
                    // e.g. `file.visuals`. Using `> 1` skips exactly that case and causes the
                    // first dictionary key to remain unresolved while deeper keys may still work.
                    if (text.contains('.')) {
                        if ((element.parent is GdExpr) && (element is GdRefIdRef)) {
                            val dic = PsiGdExprUtil.treeFindDictionary(element.parent)
                            if (dic != null) {
                                val parentText = element.parent.text
                                val dict = PsiGdExprUtil.resolveDictDecl(element.parent)

                                if (dict != null) {
                                    val fullPath = parentText
                                        .split('.')
                                        .asSequence()
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() }
                                        .toList()

                                    val basePath = dic.text
                                        .split('.')
                                        .asSequence()
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() }
                                        .toList()

                                    val relativePath = if (
                                        (fullPath.size > basePath.size) &&
                                        (fullPath.subList(0, basePath.size) == basePath)
                                    ) {
                                        fullPath.subList(basePath.size, fullPath.size)
                                    } else {
                                        emptyList()
                                    }

                                    val found = if (relativePath.isNotEmpty()) {
                                        PsiGdExprUtil.resolveDictValue(relativePath, dict)
                                    } else {
                                        null
                                    }

//                                    val exp = found as? GdExpr
//                                    val prefix = if (found != null) Ansi.BRIGHT_GREEN else Ansi.BRIGHT_RED
//
//                                    println(
//                                        prefix +
//                                            "text='${element.text.symbolizeWS().padEnd(20)}' exp='${exp?.javaClass?.simpleName?.padEnd(20)}' ret=${exp?.returnType?.limitPad(30)}  base='${
//                                                dic.text.symbolizeWS().padEnd(30)
//                                            }' relative='${relativePath.joinToString(".").symbolizeWS().limitPad(40)}' - dict=${
//                                                dict.toString().padEnd(35)
//                                            } '$found' t=$t decl=$decl"
//                                    )

                                    if (found != null) {
                                        return@run GdHighlighterColors.STRING
                                    }
                                }
                            }
                        }
                    }

                    val calledUponExpr1 = GdClassMemberUtil.calledUpon(element)
                    val reference = element.references.firstOrNull()
                    val reff = reference?.resolve()


                    // return members.methods().find { it.name == key }?.methodIdNmi

                    val symText = element.parent.text.symbolizeWS()
                    holder.newAnnotationGd(
                        element.project,
                        GdProjectState.selectedLevel(state),
                        "Reference: [${elementText}]/$resolved not found. time=${ts.padEnd(5)} | ref='${
                            reference.toString().padEnd(20)
                        }' refRes='$reff' declaration='${decl?.padEnd(40)}' called=$calledUponExpr1/'${calledUponExpr1?.text.symbolizeWS().limit(50)}' | parent-text='${symText.symbolizeWS().padEnd(50)}'}"
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
            //   println("${Ansi.BLUE}${element.getCallExpr().toString()} ${elementText.symbolizeWS().limitPad(30)}")
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

var count: Long = 0
var count2: Long = 0
var countFile: Long = 0
var time: Double = 0.0
var timeFile: Long = 0
val start = System.nanoTime()
var stringCV: String = ""
var lastFileName: String = ""
var lastID: String = ""
val fname = "/Users/patricklindenberg/Desktop/perf/gdscript_log_" + System.nanoTime() + ".csv"