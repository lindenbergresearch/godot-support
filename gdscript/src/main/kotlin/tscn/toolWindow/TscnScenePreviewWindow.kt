package tscn.toolWindow

import GdScriptBundle
import com.intellij.ide.ActivityTracker
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import com.intellij.util.BitUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.TimerUtil
import com.intellij.util.ui.UIUtil
import common.util.GdScriptProjectLifetimeService
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import tscn.toolWindow.model.TscnSceneTreeBuilder
import java.awt.KeyboardFocusManager
import java.awt.event.HierarchyEvent
import java.lang.Runnable
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.*


/**
 * Represents the delay options for the rebuild process.
 *
 * This enum is used to determine when a rebuild action should be performed.
 */
private enum class RebuildDelay {
    /**
     * Enumeration representing the possible states for delayed queue processing.
     *
     * This can be used to designate specific processing times for operations
     * that need to be deferred in a queue system.
     */
    QUEUE,


    /**
     * Represents an enumeration value indicating immediate action or response.
     *
     * The `NOW` value is used within the `RebuildDelay` enum class to specify that an
     * operation or task should be performed immediately without any delay.
     */
    NOW,
}


/**
 * A window within the Godot plugin to provide a preview of TSCN scene files.
 * This class handles interactions with the associated tool window in the IntelliJ IDEA,
 * listens to file activity changes, and manages UI updates for displaying scene structures.
 */
class TscnScenePreviewWindow : Disposable {

    /**
     * Represents the IntelliJ Project within the `TscnScenePreviewWindow` context.
     *
     * This variable is used throughout the class to interact with the project instance,
     * enabling operations like accessing project-level services, managing files, or invoking
     * project-related IntelliJ APIs.
     */
    private val project: Project


    /**
     * Represents an instance of a tool window used within the `TscnScenePreviewWindow` class.
     * This tool window is used to provide a custom UI component for
     * visualizing or interacting with a TSCN (Text-based Scene Definition) file in the IDE.
     */
    private val toolWindow: ToolWindow


    /**
     * Keeps track of the number of ongoing activities or processes related
     * to the `TscnScenePreviewWindow`. This value is used internally to manage
     * the state or operational flow of the class.
     */
    private var activityCount = 0


    /**
     * Represents the currently selected or active virtual file in the TscnScenePreviewWindow.
     * This file is used for previewing or processing tasks within the tool window.
     *
     * The value can be null, indicating no file is currently selected.
     */
    private var myFile: VirtualFile? = null


    /**
     * A [CoroutineScope] used to manage and encapsulate coroutines within the lifecycle of the
     * TscnScenePreviewWindow class. This ensures structured concurrency and proper cancellation
     * of coroutines tied to this scope.
     *
     * This scope is typically used to execute background tasks, such as scheduling rebuilds
     * or running update checks in the context of the preview window.
     *
     * As a private property, it is not accessible outside the TscnScenePreviewWindow class
     * and is intended to coordinate asynchronous tasks specific to this instance.
     */
    private val coroutineScope: CoroutineScope


    /**
     * Represents a flag indicating whether a rebuild operation is pending.
     *
     * This atomic boolean is used to track the need for a rebuild within the `TscnScenePreviewWindow`.
     * It acts as a synchronization point to ensure that rebuild operations are scheduled or executed
     * only when necessary, avoiding redundant or concurrent attempts.
     */
    private val pendingRebuild = AtomicBoolean(false)


    /**
     * A shared flow used to manage and schedule rebuild requests for the scene preview.
     * It allows emitting rebuild delay signals to coordinate updates between components.
     *
     * @see RebuildDelay
     * @see scheduleRebuild
     */
    private val rebuildRequests =
        MutableSharedFlow<RebuildDelay>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)


    /**
     * Indicates whether the structure view for the TSCN scene preview is currently visible.
     *
     * This property checks the visibility state of the specific tool window associated
     * with the TSCN scene preview. It retrieves the tool window manager instance,
     * locates the tool window by its predefined ID, and determines whether the tool window is currently visible.
     *
     * @return `true` if the TSCN scene preview tool window is visible, `false` otherwise.
     */
    private val isStructureViewShowing: Boolean
        get() {
            val windowManager = ToolWindowManager.getInstance(project)
            val toolWindow = windowManager.getToolWindow(TscnScenePreviewWindowFactory.TOOLWINDOW_ID)
            return (toolWindow != null) && toolWindow.isVisible
        }


    /**
     * Initializes an instance of the `TscnScenePreviewWindow` with the provided project and tool window.
     *
     * @param project The instance of the IntelliJ project.
     * @param toolWindow The tool window associated with the TSCN scene preview.
     */
    constructor(project: Project, toolWindow: ToolWindow) {
        this.project = project
        this.toolWindow = toolWindow
        this.coroutineScope = GdScriptProjectLifetimeService.getScope(project)
    }


    /**
     * Schedules and manages tasks for the scene preview window, such as updates, rebuilds, and UI interactions.
     *
     * This method initializes a timer that periodically checks for activity changes and invokes appropriate update tasks.
     * It registers event listeners for hierarchy changes and virtual file events to trigger rebuild actions.
     * Additionally, it manages coroutine-based rebuild scheduling with debounced logic.
     *
     * Key functionalities:
     * - Starts a named timer to monitor activity changes and invokes update tasks.
     * - Listens to hierarchy events, triggering rebuilds or reset actions based on component visibility.
     * - Subscribes to virtual file system changes to handle file-specific rebuilds.
     * - Uses coroutines for debounced rebuild requests to optimize update operations.
     *
     * This function ensures seamless updates to the scene preview window, integrating well with the UI and project lifecycle.
     */
    @OptIn(FlowPreview::class)
    fun runScheduler() {
        val component = toolWindow.component

        val timer = TimerUtil.createNamedTimer("SceneView", 100) { _ ->
            if (!component.isShowing) return@createNamedTimer

            val count = ActivityTracker.getInstance().count
            if (count == activityCount) return@createNamedTimer

            val state = ModalityState.stateForComponent(component)
            if (ModalityState.current().dominates(state)) return@createNamedTimer

            val success = runTask { checkUpdate() }
            if (success) activityCount = count // to check on the next turn
        }

        timer.start()
        Disposer.register(this) {
            timer.stop()
        }

        component.addHierarchyListener { e ->
            if (BitUtil.isSet(e.changeFlags, HierarchyEvent.DISPLAYABILITY_CHANGED.toLong())) {
                val visible = toolWindow.isVisible
                if (visible) {
                    runTask { checkUpdate() }
                    scheduleRebuild()
                } else if (!project.isDisposed) {
                    myFile = null
                    rebuildNow()
                }
            }
        }

        if (component.isShowing) {
            runTask { checkUpdate() }
            scheduleRebuild()
        }

        Disposer.register(toolWindow.contentManager, this)
        coroutineScope.launch {
            rebuildRequests.debounce {
                when (it) {
                    RebuildDelay.NOW -> 0
                    RebuildDelay.QUEUE -> 100
                }
            }
                .collectLatest {
                    rebuildImpl()
                }
        }

        project.messageBus.connect().subscribe<BulkFileListener>(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                /**
                 * Handles actions to be performed after a list of file system events occur.
                 * Checks if any of the provided events is associated with the specified `myFile`,
                 * and schedules a rebuild if the condition is met.
                 *
                 * @param events The list of file system events that occurred.
                 */
                override fun after(events: MutableList<out VFileEvent>) {
                    events.find { it.file == myFile }?.let { scheduleRebuild() }
                }
            },
        )
    }


    /**
     * Releases resources and performs cleanup when the `TscnScenePreviewWindow` is no longer needed.
     *
     * This method is invoked to dispose of any system resources or references held by the instance,
     * ensuring proper resource management and preventing memory leaks.
     *
     * Implementations should release all resources and detach any bindings or listeners
     * associated with the `TscnScenePreviewWindow`. The method is typically called when
     * the object is being removed or the application is being closed.
     */
    override fun dispose() {
    }


    /**
     * Executes a given task of type `Runnable` and returns whether the execution was successful.
     *
     * @param task The `Runnable` task that needs to be executed.
     * @return `true` if the task executes without throwing an exception, `false` otherwise.
     */
    private fun runTask(task: Runnable): Boolean {
        try {
            task.run()
            return true
        } catch (exception: Throwable) {
            return false
        }
    }


    /**
     * Checks and triggers an update process for the current state of the TscnScenePreviewWindow.
     *
     * This method ensures that updates are only performed if the project is not disposed of.
     * It retrieves the current focused component from the `KeyboardFocusManager` to establish the data context.
     * A read action is performed to fetch the currently selected file from the data context.
     * If a valid file is found, the file selection history is used to update the target file.
     *
     * The read operation is executed in a non-blocking manner and finalized on the UI thread.
     * The operation is submitted to the application’s executor service for execution.
     */
    private fun checkUpdate() {
        if (project.isDisposed) return
        val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        val dataContext = DataManager.getInstance().getDataContext(owner)

        ReadAction.nonBlocking(
            Callable {
                val file = getVirtualFile(dataContext)
                if (file != null) {
                    setFileFromSelectionHistory()
                }
            },
        ).finishOnUiThread(ModalityState.defaultModalityState()) {
        }.submit(AppExecutorUtil.getAppExecutorService())
    }


    /**
     * Triggers an immediate rebuild of the scene preview.
     * This method schedules a rebuild operation using the `RebuildDelay.NOW` parameter,
     * ensuring that the rebuild request is processed without delay.
     */
    fun rebuildNow() {
        scheduleRebuild(RebuildDelay.NOW)
    }


    /**
     * Retrieves a single virtual file from the given data context.
     * If the data context contains exactly one virtual file, that file is returned.
     * Otherwise, null is returned.
     *
     * @param dataContext the data context from which to extract the virtual file
     * @return the single virtual file if available, or null if no file or multiple files are present
     */
    private fun getVirtualFile(dataContext: DataContext): VirtualFile? {
        val files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext)
        return if ((files != null) && (files.size == 1)) {
            files[0]
        } else {
            null
        }
    }


    /**
     * Schedules a rebuild operation for the scene preview window.
     *
     * This method checks if the associated tool window is currently visible. If it isn't visible,
     * no action is taken. Otherwise, it delegates the rebuild scheduling to an overloaded version
     * of the method, with a default delay type of `RebuildDelay.QUEUE`.
     *
     * The rebuild operation ensures that the necessary updates are prepared for the scene preview
     * and queued for further processing.
     */
    private fun scheduleRebuild() {
        if (!toolWindow.isVisible) return
        scheduleRebuild(RebuildDelay.QUEUE)
    }


    /**
     * Schedules a rebuild operation with the specified delay.
     *
     * @param delay The type of delay to apply for the rebuild operation. It can be either `QUEUE` or `NOW`.
     */
    private fun scheduleRebuild(delay: RebuildDelay) {
        pendingRebuild.set(true)
        check(rebuildRequests.tryEmit(delay))
    }


    /**
     * Sets the current file based on the selection history in the File Editor Manager.
     * If there is at least one file in the selection history, it updates the file asynchronously
     * using a coroutine.
     *
     * This function retrieves the first file from the selection history and invokes the `setFile`
     * method with the retrieved file, ensuring the application state reflects the most recently selected file
     * in the editor's history.
     */
    private fun setFileFromSelectionHistory() {
        val editorManager = FileEditorManager.getInstance(project) as FileEditorManagerImpl
        val firstInHistory = editorManager.getSelectionHistory().firstOrNull()
        if (firstInHistory != null) {
            coroutineScope.launch {
                setFile(firstInHistory.first)
            }
        }
    }


    /**
     * Sets the given file as the current file and schedules a rebuild if the new file is different
     * from the currently set file.
     *
     * @param file The new file to be set, or null if no file is to be set.
     */
    private fun setFile(file: VirtualFile?) {
        if (file == myFile) return
        myFile = file
        scheduleRebuild()
    }


    /**
     * Rebuilds the contents of the tool window when invoked. This operation involves removing
     * all existing contents from the tool window's content manager and conditionally building
     * a scene tree representation based on the currently selected or tracked file. The method
     * executes asynchronously to prevent blocking the user interface.
     *
     * The following steps are performed within this method:
     * 1. Removes all contents from the tool window's content manager.
     * 2. Checks if the structure view is showing; if not, the rebuild is halted.
     * 3. Retrieves the target file to rebuild, preferring `myFile` if set,
     *    or falls back to the currently selected file in the editor.
     * 4. Validates the file and updates the `myFile` reference if necessary.
     * 5. Executes the reconstruction of the scene tree in a non-blocking background task.
     * 6. Updates the content manager upon the task's completion with the reconstructed
     *    scene tree or a placeholder panel indicating an empty or invalid scene.
     *
     * The operation is marked complete by resetting the rebuild status flag.
     */
    private fun rebuildImpl() {
        ToolWindowManager.getInstance(project).invokeLater {
            if (!isStructureViewShowing) {
                return@invokeLater
            }

            val contentManager = toolWindow.contentManager
            contentManager.removeAllContents(true)

            val file = myFile ?: run {
                val selectedFiles = FileEditorManager.getInstance(project).selectedFiles
                if (selectedFiles.isNotEmpty()) selectedFiles[0] else null
            }

            if ((file != null) && file.isValid) {
                myFile = file
            }

            ReadAction.nonBlocking(Callable { TscnSceneTreeBuilder(project).build(file) })
                .coalesceBy(this)
                .finishOnUiThread(ModalityState.defaultModalityState()) {
                    val content = ContentFactory.getInstance()
                        .createContent(it ?: createContentPanel(JLabel(GdScriptBundle.message("no.scene"))), null, false)
                    contentManager.addContent(content)
                    pendingRebuild.set(false)
                }
                .submit(AppExecutorUtil.getAppExecutorService())
        }
    }


    /**
     * Creates a JPanel and adds the specified JComponent to it.
     *
     * @param component the JComponent to be added to the JPanel
     * @return a JPanel containing the specified JComponent
     */
    private fun createContentPanel(component: JComponent): JPanel {
        val panel = JPanel()
        panel.background = UIUtil.getTreeBackground()
        panel.add(component)

        return panel
    }

}
