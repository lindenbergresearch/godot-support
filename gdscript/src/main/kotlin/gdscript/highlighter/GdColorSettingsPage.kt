package gdscript.highlighter

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.*
import com.intellij.rider.plugins.godot.community.icons.RiderPluginsGodotCommunityIcons
import javax.swing.Icon

class GdColorSettingsPage : ColorSettingsPage {
    override fun getIcon(): Icon {
        return RiderPluginsGodotCommunityIcons.GDScript
    }

    override fun getHighlighter(): SyntaxHighlighter {
        return GdSyntaxHighlighter()
    }

    override fun getDemoText(): String {
        return """extends BaseClass
class_name MyClass, "res://path/to/optional/icon.svg"


var a = ${"$"}NodePath
var s = "Hello"
var arr = [1, 2, 3]
var dict = {"key": "value", 2: 3}
@export var typed_var: int
@export var inferred_type := "String"

const ANSWER = 42
const THE_NAME = "Charly"

enum Named {THING_1, THING_2, ANOTHER_THING = -1}

var v2 = Vector2(1, 2)

# DANGER: danger comment
# WARN: warning comment
# NOTE: note comment
## Documentation comment
func _init():
    print("Constructed!")
    var local_var = 5

    if param1 < local_var:
        print(param1)
    elif param2 > 5:
        print(param2)
    else:
        pass

    for i in range(20):
        print(i)

    while param2 != 0:
        param2 -= 1

    var local_var2 = param1 + 3
    return local_var2


class Something:
    var a = 10

"""
    }

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? {
        return null
    }

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> {
        return DESCRIPTORS
    }

    override fun getColorDescriptors(): Array<ColorDescriptor> {
        return ColorDescriptor.EMPTY_ARRAY
    }

    override fun getDisplayName(): String {
        return "GdScript"
    }
}

private val DESCRIPTORS = arrayOf(
    // Keywords and Control Flow
    AttributesDescriptor("Keywords//Keyword", GdHighlighterColors.KEYWORD),
    AttributesDescriptor("Keywords//Flow control", GdHighlighterColors.FLOW_KEYWORDS),

    // Methods
    AttributesDescriptor("Methods//Method declaration", GdHighlighterColors.METHOD_DECLARATION),
    AttributesDescriptor("Methods//Method call", GdHighlighterColors.METHOD_CALL),
    AttributesDescriptor("Methods//Special method", GdHighlighterColors.SPECIAL_METHOD),
    AttributesDescriptor("Methods//Static method call", GdHighlighterColors.STATIC_METHOD_CALL),
    AttributesDescriptor("Methods//Global function", GdHighlighterColors.GLOBAL_FUNCTION),
    AttributesDescriptor("Methods//Method parameter", GdHighlighterColors.PARAMETER),

    // Types
    AttributesDescriptor("Types//BaseType (Vector3, Node3D, int, float ...)", GdHighlighterColors.BASE_TYPE),
    AttributesDescriptor("Types//EngineType (Vector3, Node3D)", GdHighlighterColors.ENGINE_TYPE),
    AttributesDescriptor("Types//User type (class_name)", GdHighlighterColors.CLASS_TYPE),

    // Enums
    AttributesDescriptor("Types//Enum//Enum type", GdHighlighterColors.ENUM_TYPE),
    AttributesDescriptor("Types//Enum//Enum value", GdHighlighterColors.ENUM_VALUE),

    // Signals
    AttributesDescriptor("Types//Signal", GdHighlighterColors.SIGNAL),

    // Variables
    AttributesDescriptor("Variable//Local variable", GdHighlighterColors.LOCAL_VARIABLE),
    AttributesDescriptor("Variable//Variable reference", GdHighlighterColors.MEMBER),
    AttributesDescriptor("Variable//Constant", GdHighlighterColors.CONSTANT),
    AttributesDescriptor("Variable//Math constant (TAU, PI, NAN ...)", GdHighlighterColors.MATH_CONSTANT),

    // Comments
    AttributesDescriptor("Comment//Comment", GdHighlighterColors.COMMENT),
    AttributesDescriptor("Comment//Doc comment", GdHighlighterColors.DOC_COMMENT),
    AttributesDescriptor("Comment//Critical comment", GdHighlighterColors.DANGER),
    AttributesDescriptor("Comment//Warning comment", GdHighlighterColors.WARNING),
    AttributesDescriptor("Comment//Note comment", GdHighlighterColors.NOTE),

    // Strings
    AttributesDescriptor("String//String", GdHighlighterColors.STRING),
    AttributesDescriptor("String//String name (&\"name\")", GdHighlighterColors.STRING_NAME),
    AttributesDescriptor("String//String format mark (\"%s\")", GdHighlighterColors.STRING_FORMAT),

    // Node Paths
    AttributesDescriptor("Node Path//Node path", GdHighlighterColors.NODE_STRING_PATH),
    AttributesDescriptor("Node Path//Node reference", GdHighlighterColors.NODE_PATH),

    // Other
    AttributesDescriptor("Other//Resource notation", GdHighlighterColors.RESOURCE_NOTATION),
    AttributesDescriptor("Other//Operator", GdHighlighterColors.OPERATOR),
    AttributesDescriptor("Other//Annotation", GdHighlighterColors.ANNOTATION),
    AttributesDescriptor("Other//Number", GdHighlighterColors.NUMBER),
    AttributesDescriptor("Other//Error", GdHighlighterColors.BAD_CHARACTER),
)
