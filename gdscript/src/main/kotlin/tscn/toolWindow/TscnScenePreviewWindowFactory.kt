package tscn.toolWindow

import GdScriptBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.*
import common.util.GdScriptProjectLifetimeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A factory responsible for creating and managing the Tscn Scene Preview Tool Window.
 *
 * The `TscnScenePreviewWindowFactory` class implements the `ToolWindowFactory` interface,
 * providing methods to initialize and configure the tool window for previewing `.tscn` scene files.
 */
class TscnScenePreviewWindowFactory() : ToolWindowFactory {
    companion object {
        const val TOOLWINDOW_ID = "TscnScenePreviewWindowId"
    }


    /**
     * Initializes the tool window for the Tscn Scene Preview.
     *
     * This method sets the stripe title of the tool window to a localized string for the
     * scene preview tab and forwards the initialization to the superclass.
     *
     * @param toolWindow the tool window instance being initialized.
     */
    override fun init(toolWindow: ToolWindow) {
        toolWindow.stripeTitle = GdScriptBundle.message("tab.title.scene.preview")
        super.init(toolWindow)
    }


    /**
     * Creates the content for the TSCN Scene Preview tool window.
     *
     * This method initializes the `TscnScenePreviewWindow` for the provided project and tool window,
     * and starts the internal scheduler to manage scene preview updates.
     *
     * @param project the current project instance for which the tool window is being created.
     * @param toolWindow the tool window instance where the content will be set.
     */
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val window = TscnScenePreviewWindow(project, toolWindow)
        window.runScheduler()
    }


    /**
     * Determines whether the tool window should be available for the given project.
     *
     * @param project the current project instance to check availability for.
     * @return true if the tool window should be available for the specified project, false otherwise.
     */
    override fun shouldBeAvailable(project: Project): Boolean {
        return false
    }


    /**
     * Determines asynchronously whether the tool window is applicable for the given project.
     *
     * This method overrides the superclass implementation to provide specific applicability
     * logic for the TSCN Scene Preview tool window.
     *
     * @param project the current project instance to check applicability for.
     * @return true if the tool window is applicable for the specified project, false otherwise.
     */
    override suspend fun isApplicableAsync(project: Project): Boolean {
        // I have tried to await() IsGodotProject here, but it causes all toolwindows to wait, so can't use it
        return super.isApplicableAsync(project)
    }
}


/**
 * Makes the "Tscn Scene Preview" tool window available for the specified project.
 *
 * This method is executed in the Event Dispatch Thread (EDT) and ensures the tool window's
 * availability by retrieving it and marking it as available in the current context.
 *
 * @param project The current project instance for which the tool window is being made available.
 */
fun makeAvailable(project: Project) {
    GdScriptProjectLifetimeService.getScope(project).launch(Dispatchers.EDT) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TscnScenePreviewWindowFactory.TOOLWINDOW_ID) ?: return@launch
        toolWindow.isAvailable = true
    }
}