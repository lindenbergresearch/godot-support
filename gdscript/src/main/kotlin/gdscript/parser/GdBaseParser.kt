package gdscript.parser

/**
 * An interface that defines the structure for a base parser.
 * Implementations of this interface are responsible for parsing logic
 * related to GdPsiBuilder at a specified level, with optional configurations.
 */
interface GdBaseParser {

    /**
     * Parses the input using the provided builder and level. The parsing behavior
     * can be adjusted by specifying whether the operation is optional.
     *
     * @param b the builder used to process the parsing logic
     * @param l the current level indicating the scope or depth of parsing
     * @param optional specifies whether the parsing is optional; defaults to false
     * @return true if the parsing is successful, false otherwise
     */
    fun parse(b: GdPsiBuilder, l: Int, optional: Boolean = false): Boolean

}
