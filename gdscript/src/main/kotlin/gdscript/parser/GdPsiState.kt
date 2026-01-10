package gdscript.parser

import com.intellij.lang.PsiBuilder.Marker
import com.intellij.psi.tree.IElementType
import com.intellij.util.containers.LimitedPool
import gdscript.psi.GdTypes.ARG_LIST

/**
 * Represents the state of a PSI parsing operation, tracking the current frame of the parsing process
 * and delegating coordination for entering, exiting, and managing parsing sections.
 */
class GdPsiState {

    /**
     * Holds the current parsing state frame for use in managing
     * grammar parsing sections within the `GdPsiState` class.
     *
     * Represents the active `GdPsiFrame`, including its context
     * and scope, which may include details like the associated
     * element type, marker, parent frame, and other parsing metadata.
     *
     * This variable is mutable and can be updated as the state transitions
     * through different stages of parsing.
     *
     * @property currentFrame The currently active `GdPsiFrame` or null if no frame is active.
     */
    private var currentFrame: GdPsiFrame? = null


    /**
     * Represents the current instance of GdPsiBuilder used to manage the parsing process
     * within the GdPsiState. It acts as the core utility for reading and manipulating
     * tokens during the parsing of a grammar or language structure.
     */
    private val b: GdPsiBuilder


    /**
     * Companion object for the GdPsiState class.
     * Provides a pool of reusable GdPsiFrame objects to optimize memory usage.
     */
    companion object {
        /**
         * Defines the maximum size of the reusable frame pool used for parsing operations.
         * This value determines the limit for the number of frames that can be stored in the pool,
         * helping optimize memory allocation and reuse during parsing processes.
         */
        const val FRAME_POOL_SIZE = 500


        /**
         * A constant pool of reusable `GdPsiFrame` objects to optimize memory allocation and reuse.
         *
         * This pool is limited to a fixed size specified by `FRAME_POOL_SIZE`. New frames are created
         * via the `GdPsiFrame.create` method as needed, but existing frames within the pool are recycled
         * whenever possible to improve performance and reduce overhead during parsing processes.
         */
        val FRAMES = LimitedPool<GdPsiFrame>(FRAME_POOL_SIZE, GdPsiFrame::create)
    }


    /**
     * Constructor for the GdPsiState class, initializing the state and associating it with a GdPsiBuilder instance.
     *
     * @param b The GdPsiBuilder instance to be associated with this state.
     */
    constructor(b: GdPsiBuilder) {
        currentFrame = FRAMES.alloc()
        this.b = b
    }


    /**
     * Indicates whether the current parsing state is within an argument section.
     * This is determined based on the current frame's state. If no frame is active,
     * it defaults to `false`.
     */
    val isArgs get() = currentFrame?.withinArg ?: false


    /**
     * Indicates whether the current parsing state contains an error.
     *
     * This property evaluates to `true` if the `currentFrame` has an error at the
     * current parsing position. Otherwise, it evaluates to `false`.
     */
    val isError get() = currentFrame?.errorAt != null


    /**
     * Represents the position in the parser where an error occurred.
     * It provides a way to access or set the error location within the current frame of parsing.
     */
    var errorAt
        get() = currentFrame?.errorAt
        set(value) {
            currentFrame?.errorAt = value
        }


    /**
     * Enters a new parsing section by creating a new parsing frame with the specified element type and marker.
     * The new frame becomes the current frame, inheriting certain properties from the previous frame.
     *
     * @param elementType The type of the element to associate with the new parsing frame.
     * @param mark The marker that defines the boundary of the new section.
     */
    fun enterSection(elementType: IElementType, mark: Marker) {
        val newFrame = FRAMES.alloc().init(elementType, mark)
        newFrame.parent = currentFrame
        newFrame.withinArg = currentFrame?.withinArg ?: false || elementType == ARG_LIST
        currentFrame = newFrame
    }


    /**
     * Exits the current parsing section, handling errors and updating the parsing state.
     *
     * @param result The result of the current section's parsing process.
     *               A value of `true` indicates successful completion, while `false` indicates failure.
     * @param drop Optional parameter that determines whether to ignore any associated errors during the exit.
     *             Defaults to `false`.
     * @return `true` if the section exits successfully or is pinned; `false` otherwise.
     */
    fun exitSection(result: Boolean, drop: Boolean = false): Boolean {
        val res = currentFrame?.exit(result) ?: false

        var errorType: IElementType? = null
        if (!res && currentFrame?.errorAt != null && !drop) {
            errorType = currentFrame!!.elementType
        }

        currentFrame = currentFrame?.parent
        if (errorType != null && !isError) {
            b.error(errorType.toString(), false)
        }

        return res
    }


    /**
     * Drops the current parsing section and optionally handles errors based on the provided result.
     *
     * @param result A boolean value indicating whether the current parsing result is successful.
     * @return A boolean value reflecting the result of dropping the section. Typically, it matches the input result.
     */
    fun dropSection(result: Boolean): Boolean {
        currentFrame?.drop(result)

        var errorAt: Int? = null
        if (result && isError) {
            errorAt = currentFrame!!.errorAt
        }

        currentFrame = currentFrame?.parent
        if (errorAt != null && !isError) {
            b.errorAt = errorAt
        }

        return result
    }


    /**
     * Updates the type of the current frame's element to the provided element type.
     *
     * @param elementType The new IElementType to set as the current frame's element type.
     */
    fun remapElement(elementType: IElementType) {
        currentFrame?.elementType = elementType
    }


    /**
     * Pins the current frame based on the provided result parameter or its existing state.
     *
     * @param result A Boolean value that determines whether to pin the current frame. Defaults to true.
     * @return A Boolean value indicating the new pinned state of the current frame, or false if no frame is present.
     */
    fun pin(result: Boolean = true): Boolean {
        currentFrame?.pinned = result || currentFrame?.pinned ?: false
        return currentFrame?.pinned ?: false
    }


    /**
     * Checks if the current frame is pinned, indicating whether the current parsing
     * state is locked in place.
     *
     * @return true if the current frame is pinned, false otherwise.
     */
    fun pinned(): Boolean {
        return currentFrame?.pinned ?: false
    }


    /**
     * Unpins the current parsing frame, if one is present.
     *
     * This method sets the `pinned` property of the `currentFrame` to `false`.
     * Unpinning is typically used to release the association of the currently
     * pinned frame, allowing it to be processed without being locked to the
     * current state context.
     */
    fun unpin() {
        currentFrame?.pinned = false
    }

}


/**
 * Represents a frame within a PSI (Program Structure Interface) parsing process.
 * Used to manage and track the state of parsing operations.
 */
class GdPsiFrame {

    /**
     * Companion object for the GdPsiFrame class, providing a factory method for creating instances of GdPsiFrame.
     */
    companion object {
        /**
         * Creates and returns a new instance of GdPsiFrame.
         *
         * @return A new instance of GdPsiFrame.
         */
        fun create(): GdPsiFrame {
            return GdPsiFrame()
        }
    }


    /**
     * Represents the type of the element associated with the `GdPsiFrame` instance.
     * This variable can be used to specify or retrieve the kind of syntax element
     * being processed within the parsing flow.
     *
     * It is nullable, indicating that there may be cases where the element type
     * has not been assigned or is not applicable for a specific `GdPsiFrame`.
     */
    var elementType: IElementType? = null


    /**
     * Represents a marker used within the parsing process to manage the state of parsing in a PSI structure.
     * It can either complete the parsing process for a specific element type or revert to a previous state,
     * depending on parsing conditions.
     *
     * The property is nullable, which indicates that the marker may not always be initialized or used.
     */
    var mark: Marker? = null


    /**
     * Represents the parent frame in the PSI (Program Structure Interface) tree structure.
     * This variable holds a reference to an optional `GdPsiFrame` object,
     * which acts as the hierarchical parent of the current frame, or null if there is no parent.
     */
    var parent: GdPsiFrame? = null


    /**
     * Represents the position of an error within the frame, if any.
     * The value is an optional integer, where a `null` value means no error is recorded.
     * If set, it indicates the index at which the error occurred during parsing.
     */
    var errorAt: Int? = null


    /**
     * Indicates whether the current parsing state is "pinned", which affects how the parsing result
     * is handled. When `true`, the associated marker and parsing element are finalized regardless of
     * the parsing result. If `false`, the parsing result determines whether the marker is finalized
     * or rolled back/dropped.
     *
     * Generally used within parsing logic to control the flow and finalization of elements in
     * the parse tree.
     */
    var pinned: Boolean = false


    /**
     * Indicates whether this frame is required during parsing.
     * A required frame enforces the necessity of its parsing logic,
     * potentially impacting the control flow of parse operations.
     */
    var required: Boolean = true


    /**
     * Indicates whether the current context is within an argument list.
     * This variable is primarily used internally in parsing logic to track
     * if the parsing operations are being executed within an argument-related
     * scope in syntax trees.
     */
    var withinArg: Boolean = false


    /**
     * Initializes the current GdPsiFrame instance with the specified element type and marker.
     *
     * @param elementType The type of the element to be associated with this frame.
     * @param mark The marker to be associated with this frame.
     * @return The initialized GdPsiFrame instance.
     */
    fun init(elementType: IElementType, mark: Marker): GdPsiFrame {
        this.elementType = elementType
        this.mark = mark
        return this
    }


    /**
     * Exits the current frame and finalizes or rolls back its marker based on the provided result.
     *
     * @param result A boolean indicating the parsing result or completion status.
     *                If true, finalizes the marker with the associated element type.
     *                If false and not pinned, rolls back the marker to its initial state.
     * @return A boolean value that is true if the provided result is true or the frame is pinned.
     */
    fun exit(result: Boolean): Boolean {
        if (elementType == ARG_LIST) withinArg = false
        if (mark == null || elementType == null) return true
        if (result || pinned) mark!!.done(elementType!!)
        else mark!!.rollbackTo()

        return result || pinned
    }


    /**
     * Drops or rolls back the current parse marker based on the result and pinned state.
     *
     * @param result A boolean indicating the success of the parsing operation.
     * @return A boolean indicating whether the operation succeeded or is pinned.
     */
    fun drop(result: Boolean): Boolean {
        if (elementType == ARG_LIST) withinArg = false
        if (mark == null) return true
        if (result || pinned) mark!!.drop()
        else mark!!.rollbackTo()

        return result || pinned
    }

}
