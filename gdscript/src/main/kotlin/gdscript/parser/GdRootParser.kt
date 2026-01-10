package gdscript.parser

import com.intellij.lang.*
import com.intellij.psi.tree.IElementType
import gdscript.parser.roots.*

/**
 * GdRootParser is responsible for parsing the root structure of a GDScript file.
 * It implements the PsiParser and LightPsiParser interfaces to provide full and lightweight parsing capabilities.
 */
class GdRootParser : PsiParser, LightPsiParser {

    /**
     * Companion object holding utility and configuration elements for the parent class.
     */
    companion object {
        /**
         * A mutable list of parser instances used for parsing various top-level elements in the GDScript PSI tree.
         * Each parser in this list is responsible for handling specific constructs or elements during the parsing process.
         * The parsers are applied in the given order, and the first applicable parser processes the corresponding element.
         */
        val topLevelParsers = mutableListOf(
            GdClassNameParser,
            GdInheritanceParser,
            GdAnnotationTlParser,
            GdClassConstParser,
            GdClassVarParser,
            GdSignalParser,
            GdEnumParser,
            GdMethodParser,
            GdClassParser,
            GdEmptyLineParser,
            GdPassParser
        )
    }

    /**
     * Parses a given root element using the provided PsiBuilder and generates an ASTNode.
     *
     * @param root The root element type to be parsed.
     * @param b The PsiBuilder instance used for the parsing process.
     * @return The generated ASTNode corresponding to the parsed root element.
     */
    override fun parse(root: IElementType, b: PsiBuilder): ASTNode {
        return parseGd(root, GdPsiBuilder(b))
    }

    /**
     * Parses the language structure in a lightweight manner using a specified root element and PsiBuilder.
     * Delegates the parsing process to the `parseLightGd` method after wrapping the PsiBuilder in a GdPsiBuilder.
     *
     * @param root The root element type to define the starting point for the parsing process.
     * @param b The PsiBuilder instance providing tokenized input for the parser.
     */
    override fun parseLight(root: IElementType, b: PsiBuilder) {
        return parseLightGd(root, GdPsiBuilder(b))
    }

    /**
     * Parses the given root element type using the provided GdPsiBuilder and returns the resulting ASTNode.
     *
     * @param root The root element type to be parsed.
     * @param b The GdPsiBuilder instance that provides parsing utilities.
     * @return The constructed ASTNode resulting from the parse operation.
     */
    fun parseGd(root: IElementType, b: GdPsiBuilder): ASTNode {
        parseLightGd(root, b)
        b.setDebugMode(true)
        return b.treeBuilt
    }

    /**
     * Parses a given light-level structure for the provided root element type using the given builder.
     *
     * @param root The root element type to parse.
     * @param b The builder used for parsing and managing token streams.
     */
    fun parseLightGd(root: IElementType, b: GdPsiBuilder) {
        val document = b.mark()
        while (!b.eof) {
            val any = topLevelParsers.any { it.parse(b, 0) }
            if (!any) {
                val m = b.mark()
                val text = b.tokenText
                if (!b.eof) b.advance()
                m.error("Unexpected tokens, $text")
            }
        }
        document.done(root)
    }

}
