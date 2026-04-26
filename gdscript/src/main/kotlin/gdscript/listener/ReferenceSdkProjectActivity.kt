package gdscript.listener

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import gdscript.library.GdLibraryUpdater
import gdscript.utils.*

/**
 * Represents an activity executed during the project lifecycle, typically triggered upon project initialization.
 * Specifically, this activity interacts with the Godot SDK integration mechanisms in JetBrains Rider IDE,
 * ensuring that SDK checks and updates are scheduled as necessary if the Rider Godot support plugin is not installed.
 */
class ReferenceSdkProjectActivity : ProjectActivity {

    /**
     * Executes an operation related to the project configuration and checks for the Rider Godot support plugin.
     *
     * - If the project is disposed of, the operation is aborted.
     * - If the Rider Godot support plugin is not installed, schedules a check for the Godot SDK using
     *   the project's base path.
     * - If the main project base path property is available and completed, schedules another SDK check
     *   for its resolved path.
     *
     * @param project the current project instance being operated on. It is used to determine the project's
     *        base path, disposition state, and other configurations necessary for performing the operation.
     */
    override suspend fun execute(project: Project) {
        if (project.isDisposed) return

        if (!PluginManagerCore.isRiderGodotSupportPluginInstalled()) {
            val basePath = project.getMainProjectBasePath() ?: return
            GdLibraryUpdater.getInstance(project).scheduleSkdCheck(basePath)
        }

        val prop = RiderGodotSupportPluginUtil.getMainProjectBasePathProperty(project) ?: return
        val path = prop.await()
        GdLibraryUpdater.getInstance(project).scheduleSkdCheck(path)
    }
}
