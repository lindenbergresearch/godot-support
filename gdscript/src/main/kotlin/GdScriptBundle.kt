import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

/**
 * Utility object for handling localized messages in the GDScript plugin.
 *
 * This class uses a dynamic resource bundle to provide localized strings.
 */
object GdScriptBundle {
    private const val BUNDLE = "messages.GdScriptBundle"

    private val INSTANCE: DynamicBundle = DynamicBundle(GdScriptBundle::class.java, BUNDLE)

    /**
     * Retrieves a localized message based on the provided key and parameters.
     *
     * @param key the key to identify the message in the resource bundle.
     * @param params optional parameters to format the message.
     * @return a formatted and localized string corresponding to the given key and parameters.
     */
    @Nls
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
        return INSTANCE.getMessage(key, *params)
    }
}