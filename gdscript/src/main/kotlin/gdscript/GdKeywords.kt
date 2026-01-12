package gdscript

/**
 * The `GdKeywords` object serves as a centralized repository for defining constants and keywords
 * commonly used in the context of the GDScript language. It provides easy access to predefined
 * strings for various GDScript keywords, constants, math constants, return types, prefixes,
 * flow types, and annotations.
 *
 * This class can be useful for maintaining consistency across a codebase or for use in tools
 * or utilities that interact with GDScript.
 */
object GdKeywords {
    /** Keywords */
    const val EXTENDS = "extends"
    const val CLASS = "class"
    const val EMIT = "emit"
    const val SIGNAL = "signal"

    const val LOAD = "load"
    const val PRELOAD = "preload"

    const val CLASS_NAME = "class_name"
    const val ONREADY = "onready"
    const val FUNC = "func"
    const val STATIC = "static"
    const val VARARG = "vararg"
    const val MASTER = "master"
    const val PUPPET = "puppet"
    const val REMOTE = "remote"
    const val REMOTE_SYNC = "remotesync"
    const val MASTER_SYNC = "mastersync"
    const val PUPPET_SYNC = "puppetsync"
    const val CONST = "const"
    const val VAR = "var"
    const val SELF = "self"
    const val SUPER = "super"
    const val VARIANT = "Variant"


    /** Constants */
    const val TRUE = "true"
    const val FALSE = "false"
    const val NULL = "null"


    /** Math Constants */
    const val NAN = "NAN"
    const val INF = "INF"
    const val TAU = "TAU"
    const val PI = "PI"
    val MATH_CONSTANTS = setOf(
        PI,
        TAU,
        INF,
        NAN
    )


    /** Return types */
    const val INT = "int"
    const val FLOAT = "float"
    const val BOOL = "bool"
    const val STRING = "String"
    const val ARRAY = "Array"
    const val DICTIONARY = "Dictionary"
    const val STRING_NAME = "StringName"
    const val NODE_PATH = "NodePath"
    const val VOID = "void"
    const val CALLABLE = "Callable"

    val BUILT_TYPES = setOf(
        INT,
        STRING,
        FLOAT,
        BOOL,
        ARRAY,
        DICTIONARY
    )


    /** Prefixes */
    const val RESOURCE_PREFIX = "res://"
    const val SPECIAL_NAME_PREFIX = "_"
    const val PATH_SEPARATOR = "/"


    const val GLOBAL_SCOPE = "${SPECIAL_NAME_PREFIX}GlobalScope"
    const val GLOBAL_GD_SCRIPT = "${SPECIAL_NAME_PREFIX}GDScript"
    const val INIT_METHOD = "${SPECIAL_NAME_PREFIX}init"


    /** Flow Types */
    const val FLOW_RETURN = "return"

    val LITERALS = arrayOf(TRUE, FALSE, NULL)
    val LITERAL_TYPES = arrayOf(BOOL, BOOL, NULL)


    /** Annotations */
    const val ANNOTATION_TOOL = "tool"
    const val ANNOTATION_EXPORT = "export"
    const val ANNOTATION_ONREADY = "onready"
    const val ANNOTATION_ICON = "icon"


    // TODO delete - use?
    val ANNOTATIONS_ROOT_ONLY = arrayOf(
        ANNOTATION_TOOL,
        ANNOTATION_ICON,
    )

}
