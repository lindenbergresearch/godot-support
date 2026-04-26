package gdscript.settings

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.util.xmlb.annotations.Tag

/**
 * Represents the state of the GdScript project configuration.
 * This class is used to store and manage configurations related to code style,
 * annotations, and comment categorizations for GdScript in an IntelliJ-based IDE.
 *
 * The configurations represented in this class include customizations for hiding private members,
 * using short typing syntax, enabling or disabling annotators, and defining various comment categories such as criticals, warnings, and notes.
 *
 * Functionality includes predefined severity levels and methods to determine the selected severity level for annotators.
 *
 * Constants:
 * `DISABLE`: Disables annotations or checks.
 * `WARN`: Sets annotations or checks to a warning severity level.
 * `ERROR`: Sets annotations or checks to an error severity level.
 *
 * @constructor Creates a new instance of GdProjectState with default configuration values.
 *
 * Companion Object:
 * - Provides constants for static configuration options (`DISABLE`, `WARN`, `ERROR`).
 * - Provides the `selectedLevel` method for determining the severity level based on the state.
 *
 * Fields:
 * - `hidePrivate`: Controls whether private members (prefixed with '_') are hidden from code completion.
 * - `shortTyped`: Determines whether a short typing expression should be used for variables.
 * - `annotators`: Sets the state of annotators for code inspection.
 * - `criticals`: Defines keywords for categorizing critical comments.
 * - `warnings`: Defines keywords for categorizing warning comments.
 * - `notes`: Defines keywords for categorizing informational or note comments.
 */
class GdProjectState {
    companion object {
        const val DISABLE = "disable"
        const val WARN = "warn"
        const val ERROR = "error"


        /**
         * Determines the appropriate `HighlightSeverity` level based on the provided state.
         *
         * @param state the state indicating the severity level, typically one of `WARN` or `ERROR`.
         * @return the corresponding `HighlightSeverity` value, either `HighlightSeverity.WEAK_WARNING` for `WARN` or
         * `HighlightSeverity.ERROR` for other cases.
         */
        fun selectedLevel(state: String): HighlightSeverity {
            return when (state) {
                WARN -> HighlightSeverity.WEAK_WARNING
                else -> HighlightSeverity.ERROR
            }
        }
    }


    /**
     * Determines whether private members (typically prefixed with `_`) in GDScript code should be
     * hidden in code completion suggestions.
     *
     * When set to `true`, private members are excluded from code completion, helping to avoid
     * accidental usage of private or internal implementation details. When set to `false`, private
     * members are included in code completion, potentially aiding debugging or advanced usage
     * scenarios.
     *
     * Default value is `true`.
     */
    @Tag("hidePrivate")
    var hidePrivate = true


    /**
     * Determines whether the shorthand typing syntax should be used in the editor.
     *
     * When enabled, it allows using a compact typing format (e.g., `var a := 1`) instead of the
     * standard typing format (`var a: int = 1`). This setting is tied to GdScript editor preferences
     * and provides an option to adjust styling preferences in the code.
     *
     * Stored as part of the project state and configurable through the settings UI.
     */
    @Tag("shortTyped")
    var shortTyped = false


    /**
     * Represents the state of annotators for the GdScript project settings.
     * Determines the level of reference, node, and resource checks.
     * Possible values include `DISABLE`, `WARN`, and `ERROR`.
     */
    @Tag("annotators")
    var annotators: String = DISABLE


    /**
     * Represents a list of critical-level comment tags used for annotating and identifying
     * critical sections or issues in the project's settings.
     *
     * The default value includes a predefined set of critical tags: ALERT, ATTENTION, CAUTION,
     * CRITICAL, DANGER, SECURITY.
     *
     * This property is used for managing and validating critical annotations within the context
     * of the GdProjectState settings and related configurable components.
     */
    @Tag("criticals")
    var criticalTags: String = "ALERT,ATTENTION,CAUTION,CRITICAL,DANGER,SECURITY"


    /**
     * Defines a comma-separated list of tags that are treated as warnings in code comments.
     * These tags are used for identifying potential issues or concerns within the code,
     * such as "BUG", "DEPRECATED", "FIXME", and others.
     *
     * The default value includes common tags typically associated with warnings:
     * "BUG,DEPRECATED,FIXME,HACK,TASK,TBD,TODO,WARNING".
     *
     * This property can be customized to include or exclude specific tags based on
     * project requirements and is accessed and modified within the settings mechanisms.
     */
    @Tag("warnings")
    var warnings: String = "BUG,DEPRECATED,FIXME,HACK,TASK,TBD,TODO,WARNING"


    /**
     * Represents a list of tags that are categorized as "notes" in the context of the project settings.
     * This variable allows customization of which comment tags are treated as notes.
     * The value is a comma-separated string of tags.
     * The default value includes common note-related tags: INFO, NOTE, NOTICE, TEST, TESTING.
     */
    @Tag("notes")
    var notes: String = "INFO,NOTE,NOTICE,TEST,TESTING"

}
