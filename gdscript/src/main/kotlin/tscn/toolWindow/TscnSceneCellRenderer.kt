package tscn.toolWindow

import GdScriptPluginIcons.TscnIcons
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import gdscript.GdIcon
import tscn.toolWindow.model.TscnSceneTreeNode
import java.awt.*
import javax.swing.*
import javax.swing.tree.DefaultTreeCellRenderer

class TscnSceneCellRenderer : DefaultTreeCellRenderer {
    companion object {
        const val ICON_WIDTH = 16
        const val ICON_LEFT_MARGIN = 24
        const val ICON_GAP = 4
        
    }

    val project: Project

    constructor(project: Project) {
        this.project = project
    }

    override fun getTreeCellRendererComponent(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean,
    ): Component {
        val baseLabel = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus) as JLabel
        baseLabel.iconTextGap = JBUI.scale(4)

        background = UIUtil.getTreeBackground()
        setBackgroundSelectionColor(UIUtil.getTreeSelectionBackground(hasFocus))
        setBackgroundNonSelectionColor(UIUtil.getTreeBackground(selected, hasFocus))
        setBorderSelectionColor(UIUtil.getTreeSelectionBackground(hasFocus))

        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.emptyRight(JBUI.scale(4))
        panel.add(baseLabel, BorderLayout.CENTER)

        if (value is TscnSceneTreeNode) {
            text = value.myName
            icon = GdIcon.getEditorIcon(value.myType)
            if (value.inherited) foreground = JBColor.YELLOW

            val flowLayout = FlowLayout(FlowLayout.RIGHT, ICON_GAP, ICON_GAP)
            val iconPanel = JPanel(flowLayout, true)
            iconPanel.border = JBUI.Borders.emptyRight(16)
            iconPanel.isOpaque = false

            val labels = listActionIconsLabels(value)
            for (label in labels) {
                iconPanel.add(label, BorderLayout.EAST)
            }

            panel.add(iconPanel, BorderLayout.EAST)
        }

        return panel
    }

    private fun createActionIconLabel(icon: Icon, toolTip: String = ""): JLabel {
        val label = JLabel(icon)
        val dim = Dimension(ICON_WIDTH, 16)
        label.size = dim
        label.preferredSize = dim
        label.toolTipText = toolTip
        label.isOpaque = false
        label.border = JBUI.Borders.empty()
        label.isFocusable = false
        label.iconTextGap = 0

        return label
    }

    private fun listActionIconsLabels(node: TscnSceneTreeNode): List<JLabel> {
        return node.listActions().mapNotNull {
            when (it) {
                "instance" -> createActionIconLabel(TscnIcons.InstanceOptions, "Node has instance resource.")
                "script" -> createActionIconLabel(TscnIcons.Script, "Node has script.")
                "unique" -> createActionIconLabel(TscnIcons.SceneUniqueName, "Node has unique name.")
                "visible" ->
                    if (node.visible) {
                        if (node.parentVisible) createActionIconLabel(TscnIcons.GuiVisibilityVisible, "Node is visible in the scene.")
                        else createActionIconLabel(TscnIcons.GuiVisibilityVisibleDark, "Node is visible dark in the scene.")
                    } else if (node.parentVisible) createActionIconLabel(TscnIcons.GuiVisibilityHidden, "Node is hidden in the scene.")
                    else createActionIconLabel(TscnIcons.GuiVisibilityHiddenDark, "Node is hidden dark in the scene.")

                else -> null
            }
        }
    }

}