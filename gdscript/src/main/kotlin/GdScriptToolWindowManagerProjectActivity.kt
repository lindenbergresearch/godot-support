import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.jetbrains.rd.util.threading.coroutines.launch
import common.util.GdScriptProjectLifetimeService
import gdscript.utils.RiderGodotSupportPluginUtil
import tscn.toolWindow.makeAvailable

/**
 * Handles the initialization of the GDScript tool window feature when a project is opened.
 *
 * This class is responsible for enabling the Tscn Scene Preview tool window for projects
 * recognized as Godot projects. It uses the project lifecycle management to ensure
 * the tool window is activated correctly and efficiently when applicable.
 */
class GdScriptToolWindowManagerProjectActivity : ProjectActivity {
    /**
     * Executes the necessary setup for the GDScript project activity when a project is opened.
     *
     * @param project The current project instance for which the activity is executed.
     */
    override suspend fun execute(project: Project) {
        val lifetime = GdScriptProjectLifetimeService.getLifetime(project)
        lifetime.launch {
            tryEnableTscnScenePreview(project)
        }
    }


    /**
     * Attempts to enable the TSCN Scene Preview tool window for the specified project.
     *
     * This method verifies if the provided project is a Godot project. If the Godot environment is
     * confirmed or the Godot plugin is not detected, the TSCN Scene Preview tool window is made available.
     *
     * @param project The current project instance to check for Godot compatibility and enable the tool window.
     */
    private suspend fun tryEnableTscnScenePreview(project: Project) {
        val isGodot = RiderGodotSupportPluginUtil.isGodotProject(project)
        if (isGodot == null) {
            // Godot plugin is disabled or not installed
            makeAvailable(project)
            return
        }
        if (isGodot.await())
            makeAvailable(project)
    }
}