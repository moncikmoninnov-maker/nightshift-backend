package `fun`.nightshift.launcher.bootstrapper

import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GradientPaint
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.RenderingHints
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.border.EmptyBorder

class DownloadWindow(downloader: LauncherDownloader) : JFrame("NightShift Launcher") {

    private val accentColor = Color(0xA8, 0x69, 0xFF)
    private val textPrimary = Color(0xEB, 0xEB, 0xF5)
    private val textSecondary = Color(0x9A, 0x93, 0xB8)

    private val progressBar = NsProgressBar()
    private val statusLabel = JLabel("Загрузка...", SwingConstants.CENTER)
    private val titleLabel = JLabel("NightShift Launcher", SwingConstants.CENTER)
    private val subtitleLabel = JLabel("УСТАНОВКА", SwingConstants.CENTER)

    init {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) } catch (_: Exception) {}

        defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) { dispose(); kotlin.system.exitProcess(0) }
        })

        setSize(520, 360)
        setLocationRelativeTo(null)
        isResizable = false

        val bgPanel = BackgroundPanel()
        bgPanel.layout = GridBagLayout()
        bgPanel.border = EmptyBorder(32, 48, 32, 48)
        contentPane = bgPanel

        val c = GridBagConstraints()
        c.gridwidth = GridBagConstraints.REMAINDER
        c.fill = GridBagConstraints.HORIZONTAL

        titleLabel.font = Font("Segoe UI", Font.BOLD, 22)
        titleLabel.foreground = textPrimary
        bgPanel.add(titleLabel, c)

        subtitleLabel.font = Font("Segoe UI", Font.PLAIN, 10)
        subtitleLabel.foreground = accentColor
        subtitleLabel.border = EmptyBorder(0, 0, 16, 0)
        bgPanel.add(subtitleLabel, c)

        progressBar.preferredSize = java.awt.Dimension(400, 8)
        progressBar.maximumSize = java.awt.Dimension(400, 8)
        progressBar.minimumSize = java.awt.Dimension(400, 8)
        progressBar.isIndeterminate = true
        bgPanel.add(progressBar, c)

        statusLabel.font = Font("Segoe UI", Font.PLAIN, 12)
        statusLabel.foreground = textSecondary
        statusLabel.border = EmptyBorder(12, 0, 0, 0)
        bgPanel.add(statusLabel, c)

        downloader.onProgress = { downloaded, total ->
            SwingUtilities.invokeLater {
                progressBar.isIndeterminate = false
                progressBar.maximum = total.toInt()
                progressBar.value = downloaded.toInt()
                statusLabel.text = "Загрузка: %"
            }
        }

        downloader.onStatus = { msg -> SwingUtilities.invokeLater { statusLabel.text = msg } }

        downloader.onError = { msg ->
            SwingUtilities.invokeLater {
                statusLabel.text = msg
                progressBar.isIndeterminate = false
                progressBar.value = 0
            }
        }

        Thread.ofVirtual().start {
            val exe = downloader.run()
            if (exe != null) {
                SwingUtilities.invokeLater { statusLabel.text = "Запуск..."; dispose() }
                launchLauncher(exe)
                kotlin.system.exitProcess(0)
            }
        }
    }

    private fun launchLauncher(exe: java.nio.file.Path) {
        ProcessBuilder(exe.toAbsolutePath().toString())
            .directory(exe.parent.toFile()).inheritIO().start()
    }
}

private class BackgroundPanel : JPanel() {
    private val topColor = Color(0x0E, 0x0A, 0x18)
    private val bottomColor = Color(0x1A, 0x0E, 0x2E)

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.paint = GradientPaint(0f, 0f, topColor, 0f, height.toFloat(), bottomColor)
        g2.fillRect(0, 0, width, height)
        g2.dispose()
    }
}

private class NsProgressBar : JProgressBar() {
    private val accentColor = Color(0xA8, 0x69, 0xFF)
    private val trackColor = Color(0x1B, 0x14, 0x30)

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = trackColor
        g2.fillRoundRect(0, 0, width, height, height, height)

        if (isIndeterminate) {
            val dotWidth = (width * 0.3).toInt()
            val phase = (System.currentTimeMillis() % 2000) / 2000f
            g2.color = accentColor
            g2.fillRoundRect(((width - dotWidth) * phase).toInt(), 0, dotWidth, height, height, height)
        } else if (maximum > 0) {
            val fill = (value.toFloat() / maximum * width).toInt().coerceIn(0, width)
            g2.color = accentColor
            g2.fillRoundRect(0, 0, fill, height, height, height)
        }
        g2.dispose()
    }
}