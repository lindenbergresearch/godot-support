package gdscript.structureView

import GdScriptPluginIcons
import com.intellij.navigation.ItemPresentation
import com.jetbrains.rd.util.firstOrNull
import gdscript.GdKeywords
import gdscript.psi.*
import javax.swing.Icon

/**
 * Utility object for generating item presentations for various GDScript declarations.
 */
object GdPresentationUtil {

    /**
     * Creates an ItemPresentation instance for a given GdClassVarDeclTl representation.
     *
     * @param classVar the GdClassVarDeclTl instance used to create the presentation.
     * @return an ItemPresentation object with customized text, location, and icon based on the input classVar.
     */
    fun presentation(classVar: GdClassVarDeclTl): ItemPresentation {
        return object : ItemPresentation {
            /**
             * Provides a user-readable representation of the variable's name.
             *
             * @return the name of the variable as a string
             */
            override fun getPresentableText(): String = classVar.name


            /**
             * Returns the location string for the current class variable.
             *
             * @return the return type of the class variable as a string.
             */
            override fun getLocationString(): String = classVar.returnType


            /**
             * Retrieves the icon associated with a specific structure element.
             *
             * @param unused A boolean parameter that is not utilized in the implementation.
             * @return The icon representing a variable in GDScript structure view.
             */
            override fun getIcon(unused: Boolean): Icon = GdScriptPluginIcons.GDScriptIcons.VAR_MARKER
        }
    }


    /**
     * Creates an ItemPresentation instance for a constant declaration.
     *
     * @param constVar The constant declaration element for which the presentation is created.
     * @return An ItemPresentation instance providing the display text, location, and icon for the constant.
     */
    fun presentation(constVar: GdConstDeclTl): ItemPresentation {
        return object : ItemPresentation {
            /**
             * Retrieves the presentable text for the corresponding constant declaration.
             *
             * @return The name of the constant as the presentable text.
             */
            override fun getPresentableText(): String = constVar.name


            /**
             * Provides the location string for the given element, typically representing
             * the type information associated with the constant declaration.
             *
             * @return The type information of the corresponding constant declaration as a string.
             */
            override fun getLocationString(): String = constVar.returnType


            /**
             * Returns the icon representing a constant marker in the GDScript structure view.
             *
             * @param unused a boolean parameter that is not utilized in this method.
             * @return the icon representing a constant marker.
             */
            override fun getIcon(unused: Boolean): Icon = GdScriptPluginIcons.GDScriptIcons.CONST_MARKER
        }
    }


    /**
     * Creates an ItemPresentation for the provided GdMethodDeclTl.
     *
     * @param method the GdMethodDeclTl representing a method declaration for which the presentation is to be created.
     * @return an ItemPresentation containing presentable text, location string, and an icon for the method.
     */
    fun presentation(method: GdMethodDeclTl): ItemPresentation {
        return object : ItemPresentation {
            /**
             * Provides a presentable name for the method.
             *
             * @return The name of the method as a string.
             */
            override fun getPresentableText(): String = method.name


            /**
             * Returns the location string that represents the return type of the method.
             *
             * @return A string representing the return type of the method.
             */
            override fun getLocationString(): String = method.returnType


            /**
             * Provides the icon representation for the method in the structure view or in other UI components.
             *
             * @param unused A boolean parameter that is not used in this implementation.
             * @return The icon associated with the method marker in the GDScript plugin.
             */
            override fun getIcon(unused: Boolean): Icon = GdScriptPluginIcons.GDScriptIcons.METHOD_MARKER
        }
    }


    /**
     * Creates an instance of `ItemPresentation` for the given `GdEnumDeclTl` element.
     *
     * @param enum the `GdEnumDeclTl` instance for which the presentation is generated.
     * @return an instance of `ItemPresentation` that provides the presentable text, location string, and icon representation.
     */
    fun presentation(enum: GdEnumDeclTl): ItemPresentation {
        return object : ItemPresentation {
            /**
             * Provides a presentable text representation of an enumeration declaration.
             *
             * The method attempts to retrieve the name of the `enumDeclNmi` property
             * from the associated enumeration. If the `enumDeclNmi` is null, it falls back
             * to returning a string containing the key of the first value in the enumeration's
             * values list, followed by an ellipsis to indicate there are additional elements.
             *
             * @return The presentable text of the enumeration declaration, or a fallback
             *         representation if the declaration does not have a name.
             */
            override fun getPresentableText(): String = enum.enumDeclNmi?.name ?: "(${enum.values.firstOrNull()?.key}, ...)"


            /**
             * Provides the location string for the presented item.
             *
             * @return a string representing the location of the item, typically a keyword or other identifier
             */
            override fun getLocationString(): String = GdKeywords.INT


            /**
             * Returns the icon representation for the given context where this method is utilized.
             *
             * @param unused A boolean parameter that is currently not utilized in the implementation.
             * @return An Icon object representing the ENUM_MARKER from GdScriptPluginIcons.GDScriptIcons.
             */
            override fun getIcon(unused: Boolean): Icon = GdScriptPluginIcons.GDScriptIcons.ENUM_MARKER
        }
    }

}
