package gdscript.parser

import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiBuilder.Marker
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.tree.IElementType
import gdscript.parser.expr.GdLiteralExParser
import gdscript.psi.GdTypes
import java.util.*

/**
 * A utility class for efficiently parsing grammars using a PSI (Program Structure Interface) builder.
 * Responsible for managing parsing state, providing utility methods for token inspection, and handling
 * grammar-specific parsing needs.
 */
class GdPsiBuilder {

    /**
     * Defines the maximum allowed recursion level for grammar parsing operations.
     *
     * This value is derived from the system property `grammar.kit.gpub.max.level`.
     * If the property is not set or cannot be parsed as an integer, it defaults to 1000.
     *
     * Used primarily in recursion guards to prevent excessively deep recursion,
     * ensuring stable and predictable behavior of the grammar parser.
     */
    val MAX_RECURSION_LEVEL = StringUtil.parseInt(System.getProperty("grammar.kit.gpub.max.level"), 1000)


    /**
     * Represents the underlying [PsiBuilder] instance used for parsing and building PSI trees.
     * This variable provides core functionalities for lexical analysis and token processing
     * within the context of the GDScript parser.
     */
    val b: PsiBuilder


    /**
     * Represents the current state of the `GdPsiBuilder` during the parsing process.
     * This field provides an instance of `GdPsiState` responsible for managing the parsing frames
     * and tracking the parsing context, such as errors and argument sections.
     */
    val state: GdPsiState


    /**
     * Constructs a new instance of the GdPsiBuilder class.
     *
     * Initializes the parser with a given PsiBuilder and sets up an internal state using GdPsiState.
     *
     * @param builder The PsiBuilder instance used for parsing.
     */
    constructor(builder: PsiBuilder) {
        b = builder
        state = GdPsiState(this)
    }


    /**
     * Retrieves the type of the current token being processed by the parser.
     *
     * This property provides a convenient accessor to the `tokenType` field of the `b` object,
     * representing the type of the current token in the parsing context.
     *
     * Used primarily in parsing logic to identify or compare the current token type
     * against expected or specific token types.
     */
    val tokenType get() = b.tokenType


    /**
     * Provides the current token's textual representation within the parsing process.
     * Delegates its value to the `tokenText` property in the `b` instance of `GdPsiBuilder`.
     * Used to access the raw string content of the currently processed token.
     */
    val tokenText get() = b.tokenText


    /**
     * A read-only property that provides the current raw token index within the builder.
     * This is used to track the position of tokens during parsing operations, often for error handling
     * or diagnostics purposes.
     */
    val positionAt get() = b.rawTokenIndex()


    /**
     * Represents a property that evaluates whether the end-of-file (EOF) state
     * has been reached for the underlying PSI (Program Structure Interface) builder.
     *
     * This property is used to determine if there are no more tokens left to parse.
     * It delegates the EOF check to the corresponding method in the `b` builder object.
     */
    val eof get() = b.eof()


    /**
     * Retrieves the parse tree built during the parsing process.
     * This represents the structured result of parsing the input content
     * and may be used for syntax analysis or further processing.
     */
    val treeBuilt get() = b.treeBuilt


    /**
     * Represents a property that indicates whether the current parsing state is within an argument list.
     *
     * The value of this property is determined by the state of the current frame within the PSI (Program Structure Interface) builder.
     * It is true if the current parsing frame is marked as being inside an argument list; otherwise, it is false.
     */
    val isArgs get() = state.isArgs


    /**
     * Indicates whether the current parser state represents an error condition.
     * The value is derived from the `errorAt` property of the current parsing frame, which tracks
     * whether an error has occurred during parsing.
     *
     * This property is commonly used to check for the presence of errors before proceeding with
     * additional parsing steps or actions.
     */
    val isError get() = state.isError


    /**
     * Represents the position at which an error occurred during the parsing process.
     *
     * This property is used to track and manage errors in the parser state. It can be set to indicate the
     * current error position and retrieved to reference the position where an error was recorded. If no
     * error is recorded, it defaults to `0`.
     *
     * The value is managed within the internal parsing state and acts as a mechanism for error handling
     * and resolution.
     */
    var errorAt: Int?
        get() = state.errorAt ?: 0
        set(value) {
            state.errorAt = value
        }


    /**
     * Advances the lexer to the next token.
     *
     * This method is a simple wrapper that triggers the advancement
     * of the underlying lexer. It is typically used during the parsing
     * process to progress through the token stream.
     */
    fun advance() = b.advanceLexer()


    /**
     * Creates and returns a new marker that can be used to define a segment in the PSI tree.
     *
     * A marker allows for defining and marking specific sections during parsing,
     * enabling actions such as completing, rolling back, or dropping sections in the PSI structure.
     *
     * @return a newly created marker from the associated builder.
     */
    fun mark(): Marker = b.mark()


    /**
     * Checks whether the current parsing frame is marked as pinned.
     *
     * A pinned frame indicates that the parsing process has established a significant enough match
     * to continue parsing and prevent rollback, even if subsequent matches fail.
     *
     * @return true if the current frame is pinned, false otherwise
     */
    fun pinned(): Boolean = state.pinned()


    /**
     * Provides access to the most recently completed marker within the parsing process.
     * Represents a parsed structural element in the PSI tree.
     * Delegated to the `latestDoneMarker` property of the underlying `b` instance,
     * cast to the `Marker` type.
     */
    val latestDoneMarker get() = b.latestDoneMarker as Marker


    /**
     * Retrieves the IElementType at the specified distance from the current position in the token stream.
     *
     * @param steps The number of steps to look ahead from the current position. Defaults to 1.
     * @return The IElementType at the specified position, or null if the position is out of bounds.
     */
    fun rawLookup(steps: Int = 1): IElementType? {
        return b.rawLookup(steps)
    }


    /**
     * Remaps the type of the current token to the provided element type.
     *
     * @param type The new IElementType to remap the current token to.
     */
    fun remapCurrentToken(type: IElementType) {
        b.remapCurrentToken(type)
    }


    /**
     * Enables or disables debug mode for the current GdPsiBuilder instance.
     * Debug mode can be used to add additional logging or behavior during parsing.
     *
     * @param boolean If true, debug mode is enabled; if false, debug mode is disabled.
     */
    fun setDebugMode(boolean: Boolean) {
        b.setDebugMode(boolean)
    }


    /**
     * Sets the pinned state of the current parse frame to the specified result or retains the existing pinned state.
     *
     * @param result A Boolean value indicating whether to pin the current frame. Defaults to true.
     * @return A Boolean indicating the pinned state of the current frame after the operation.
     */
    fun pin(result: Boolean = true): Boolean {
        return state.pin(result)
    }


    /**
     * Resets the pinned state of the current frame.
     *
     * This method is used to mark the current parsing state as unpinned,
     * ensuring that the associated frame is no longer considered pinned.
     * A "pinned" frame indicates that its parsing state is locked or has
     * been determined to be final in some contexts. Unpinning allows the
     * parsing process to continue without being constrained by the pinned state.
     */
    fun unpin() {
        state.unpin()
    }


    /**
     * Checks if the current token matches any of the specified element types.
     *
     * @param elementTypes a variable number of IElementType elements to check against the current token
     * @return true if the current token matches any of the provided element types, false otherwise
     */
    fun nextTokenIs(vararg elementTypes: IElementType): Boolean {
        val searchFor = tokenType
        return elementTypes.any { it == searchFor }
    }


    /**
     * Checks if the tokens following the current position in the builder match the specified sequence of element types.
     *
     * @param elementTypes A variable number of element types to check against the tokens following the current position.
     * @return True if the sequence of tokens following the current position matches the provided element types, false otherwise.
     */
    fun followingTokensAre(vararg elementTypes: IElementType): Boolean {
        var step = 0

        return elementTypes.all {
            it == b.lookAhead(step++)
        }
    }


    /**
     * Consumes a token of the specified element type if it is the current token. Optionally, an error
     * can be reported if the token is not as expected, and the token can be pinned to ensure parsing stability.
     *
     * @param elementType The expected type of the token to be consumed.
     * @param optional Indicates whether the token is optional. If true, no error will be reported when the expected token is not present. Default is false.
     * @param pin Controls whether the consumed token should be pinned for parsing stability. Default is false.
     * @return True if the token was successfully consumed, otherwise false.
     */
    fun consumeToken(elementType: IElementType, optional: Boolean = false, pin: Boolean = false): Boolean {
        if (tokenType == elementType) {
            advance()
            pin(pin)
            return true
        } else if (!optional) {
            error(elementType.toString(), false)
            return false
        }

        return false
    }


    /**
     * Parses and consumes a statement-ending token (semicolon or newline).
     * Marks the token as `END_STMT` if found.
     *
     * @param optional If true, the absence of an expected token does not trigger an error; otherwise, it raises an error.
     * @return `true` if an `END_STMT` token is successfully parsed, `false` otherwise.
     */
    fun mceEndStmt(optional: Boolean = false): Boolean {
        if (!nextTokenIs(GdTypes.SEMICON, GdTypes.NEW_LINE)) {
            if (!optional) {
                error("END_STMT", false)
                return false
            }
        }

        val m = mark()
        consumeToken(GdTypes.SEMICON, true)
        consumeToken(GdTypes.NEW_LINE, true)
        m.done(GdTypes.END_STMT)

        return true
    }


    /**
     * Verifies and processes an identifier marker using the specified marker type.
     *
     * This method applies the `parseExtendedRefId` function using the current instance
     * and the provided marker type. If the parsing fails, an error is raised with a default
     * message indicating an identifier was expected but not matched.
     *
     * @param markerType The type of marker to use for parsing and validation.
     * @return `true` if the identifier was successfully parsed and processed; `false` otherwise.
     */
    fun mceIdentifier(markerType: IElementType): Boolean {
        val ok = GdLiteralExParser.parseExtendedRefId(this, markerType)
        if (!ok) error("IDENTIFIER", false)

        return ok
    }


    /**
     * Checks if the next token matches any of the specified token types.
     * If a match is found, it marks the token and advances the builder. If no match is found and the process is non-optional,
     * it consumes the unexpected tokens and marks the operation as unsuccessful.
     *
     * @param markElement the element type used to mark the matched token if found.
     * @param optional whether the matching process should be optional. If true, the method won't consume tokens when no match is found.
     * @param elementTypes a variable number of element types to be matched against the current token.
     * @return true if a matching token is found or the process is optional; false if no match is found and the process is not optional.
     */
    fun mceAnyOf(markElement: IElementType, optional: Boolean, vararg elementTypes: IElementType): Boolean {
        if (!nextTokenIs(*elementTypes)) {
            if (!optional) {
                consumeUnexpected(*elementTypes)
                return false
            }

            return true
        }

        val m = mark()
        advance()
        m.done(markElement)

        return true
    }


    /**
     * Attempts to process the current token against a specified set of element types and marks it
     * using the provided `markToken` type if a match is found.
     *
     * @param markToken The type of token used to mark the processed element.
     * @param elementTypes A variable number of element types to check against the current token.
     * If not provided, the `markToken` type is used as the default.
     * @return `true` if the current token matches one of the given element types and is successfully
     * marked; `false` otherwise.
     */
    fun mcToken(markToken: IElementType, vararg elementTypes: IElementType): Boolean {
        val lookFor = if (elementTypes.isEmpty()) arrayOf(markToken) else elementTypes
        if (nextTokenIs(*lookFor)) {
            val m = mark()
            advance()
            m.done(markToken)
            return true
        }

        return false
    }


    /**
     * Advances the token stream if the current token matches one of the specified element types.
     *
     * @param elementTypes A vararg of potential element types to check against the current token.
     * @return `true` if the token stream was advanced because the current token matched one of the specified element types; `false` otherwise.
     */
    fun passToken(vararg elementTypes: IElementType): Boolean {
        if (nextTokenIs(*elementTypes)) {
            advance()
            return true
        }

        return false
    }


    /**
     * Enters a new parsing section with the specified element type, marking the beginning of the section.
     *
     * @param elementType the type of the element representing the start of the section
     * @return a Marker object used to denote the boundary of the section
     */
    fun enterSection(elementType: IElementType): Marker {
        val m = b.mark()
        state.enterSection(elementType, m)
        return m
    }


    /**
     * Creates a new marker that precedes the latest done marker and enters a new parsing section
     * based on the provided element type.
     *
     * @param elementType the type of element to enter within the new section
     * @return a new marker instance that precedes the latest done marker
     */
    fun precedeEnterSection(elementType: IElementType): Marker {
        val m = (b.latestDoneMarker as Marker).precede()
        state.enterSection(elementType, m)
        return m
    }


    /**
     * Exits a parsing section and optionally removes it based on the given parameters.
     *
     * @param result Indicates whether the parsing section was successful.
     * @param drop Specifies whether to ignore the section on failure and not report parsing errors. Default is false.
     * @return Returns true if the section was successfully exited or pinned; false otherwise.
     */
    fun exitSection(result: Boolean, drop: Boolean = false): Boolean {
        return state.exitSection(result, drop)
    }


    /**
     * Exits the current parser section and updates the state with the specified result and element type.
     *
     * @param result The result of the parsing section, typically indicating success or failure.
     * @param elementType The type of the element associated with the section being exited.
     * @return `true` if the section exit was successful or state is pinned, otherwise `false`.
     */
    fun exitSection(result: Boolean, elementType: IElementType): Boolean {
        state.remapElement(elementType)
        return state.exitSection(result)
    }


    /**
     * Drops the current parsing section and updates the error state if necessary.
     *
     * @param result A boolean indicating whether the section was successfully parsed.
     * @return The result parameter value, indicating the success of parsing this section.
     */
    fun dropSection(result: Boolean): Boolean {
        return state.dropSection(result)
    }


    /**
     * Reports a parsing error and optionally consumes the current token.
     *
     * @param expected The expected element or token as a string. This will be reflected in the error message.
     * @param consume Indicates whether to advance the lexer position after reporting the error. Defaults to true.
     */
    fun error(expected: String, consume: Boolean = true) {
        if (!isError) {
            val m = b.mark()
            if (consume) {
                advance()
            }
            errorAt = positionAt
            m.error("${expected.removePrefix("GdTokenType.")} expected")
        }
    }


    /**
     * Handles and logs an error if the parsing or validation result is not successful and the current state is pinned.
     * It also checks if the state remains pinned after processing the error.
     *
     * @param result The result of a parsing or validation attempt.
     * @param expected A description of the expected value or condition when the error occurs.
     * @return Returns true if the current state remains pinned, otherwise false.
     */
    fun errorPin(result: Boolean, expected: String): Boolean {
        if (!result && pinned()) {
            error(expected.uppercase(Locale.ROOT), false)
        }

        return pinned()
    }


    /**
     * Resets the current state of the builder by clearing error markers and unpinning the current frame.
     *
     * This method is used to ensure a clean state by removing any error markings (if present) and
     * resetting the pinned status within the builder's current frame. It is particularly useful
     * for managing the internal parsing state during complex parsing operations.
     */
    fun clearState() {
        errorAt = null
        unpin()
    }


    /**
     * Consumes unexpected tokens of specified types in the parser stream.
     * Throws an error indicating the type of the first unexpected token
     * or a default placeholder if none are provided.
     *
     * @param elementTypes The types of elements that are unexpected.
     */
    private fun consumeUnexpected(vararg elementTypes: IElementType) {
        error(elementTypes.getOrNull(0)?.toString() ?: "{}")
    }


    /**
     * Triggers an error for an unexpected element type.
     *
     * @param elementType The unexpected element type causing the error.
     */
    private fun consumeUnexpected(elementType: IElementType) {
        error(elementType.toString())
    }


    /**
     * Throws an error based on the provided expected string.
     *
     * @param expected A description of the expected token or input, used to generate the error message.
     */
    private fun consumeUnexpected(expected: String) {
        error(expected)
    }


    /**
     * Prevents excessive recursion during parsing by limiting the recursion level.
     * Logs an error and halts the parsing if the maximum recursion level is exceeded.
     *
     * @param level the current depth of recursion.
     * @param funcName the name of the function where the recursion is occurring, used for logging purposes.
     * @return true if the recursion level is within the acceptable limit; false otherwise.
     */
    fun recursionGuard(level: Int, funcName: String?): Boolean {
        if (level > MAX_RECURSION_LEVEL) {
            b.mark().error("Maximum recursion level ($MAX_RECURSION_LEVEL) reached $funcName")
            return false
        }

        return true
    }

}
