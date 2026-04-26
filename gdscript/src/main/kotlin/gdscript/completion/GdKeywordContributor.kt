package gdscript.completion

import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.PlatformPatterns.psiElement
import gdscript.GdKeywords
import gdscript.psi.GdTypes

/**
 * Defines conditions to identify specific patterns in the PSI tree where certain keywords should be skipped.
 *
 * The variable comprises a combination of three patterns:
 * 1. Matches elements located after a dot (`.`) character.
 * 2. Matches elements that are children of `INHERITANCE_ID_NM` type nodes.
 * 3. Matches elements that are children of `CLASS_NAME_NMI` type nodes.
 *
 * This configuration is mainly used in code completion or PSI pattern matching scenarios to filter out
 * keywords that are not applicable or valid within the specified contexts.
 */
val SKIP_KEYWORDS_FOR = PlatformPatterns.or(
    psiElement().afterLeaf(psiElement(GdTypes.DOT)),
    psiElement().withParent(psiElement(GdTypes.INHERITANCE_ID_NM)),
    psiElement().withParent(psiElement(GdTypes.CLASS_NAME_NMI)),
)

/**
 * Represents a predefined array of keywords that are used as hints during GDScript completion.
 * These keywords correspond to common control flow statements in GDScript, such as "pass",
 * "continue", and "break". This variable can be utilized in code completion logic to suggest
 * these keywords based on the current context in the editor.
 */
val TO_HINT_KEYWORDS = arrayOf(
    "pass",
    "continue",
    "break",
)

/**
 * An array containing keywords that should be hinted with a trailing space in
 * the code completion process. These keywords are commonly used in GDScript
 * and include elements such as `func`, `static`, `master`, among others.
 * The hinting mechanism uses this array to provide context-aware suggestions
 * for improved development efficiency.
 */
val TO_HINT_KEYWORDS_WITH_SPACE = arrayOf(
    GdKeywords.FUNC,
    GdKeywords.STATIC,
    GdKeywords.MASTER,
    GdKeywords.PUPPET,
    GdKeywords.REMOTE,
    GdKeywords.REMOTE_SYNC,
    GdKeywords.CONST,
    GdKeywords.VAR,
    GdKeywords.CLASS,
    GdKeywords.SIGNAL,
)

/**
 * Provides completion suggestions for GDScript keywords.
 * Responsible for populating the result set with keyword completions based on the context.
 *
 * This contributor omits certain suggestions based on specific position conditions
 * and handles the inclusion of keyword completions with or without trailing spaces.
 */
class GdKeywordContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (SKIP_KEYWORDS_FOR.accepts(parameters.position)) return

        result.addAllElements(TO_HINT_KEYWORDS.map { GdLookup.create(it, priority = -100.0) })
        result.addAllElements(TO_HINT_KEYWORDS_WITH_SPACE.map { GdLookup.create("$it ", priority = -100.0, presentable = it) })
    }

}

