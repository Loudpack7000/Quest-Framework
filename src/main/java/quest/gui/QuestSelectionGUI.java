package quest.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import quest.core.QuestDatabase;
import quest.core.QuestExecutor;

/**
 * Modern AI Quest Framework GUI
 * Beautiful tabbed interface for Discovery Mode and Quest Automation
 */
public class QuestSelectionGUI extends JFrame {
    
    private static QuestSelectionGUI instance;
    private QuestStartListener questStartListener;
    private QuestExecutor questExecutor;
    
    // Discovery Mode Components
    private JCheckBox discoveryModeCheckbox;
    private JLabel discoveryStatusLabel;
    
    // Quest Execution Components
    private JComboBox<String> questDropdown;
    private JButton startQuestButton;
    private JButton stopQuestButton;
    private JLabel questStatusLabel;
    private JProgressBar questProgressBar;
    private JTextArea questLogArea;
    
    private boolean questRunning = false;
    private boolean discoveryRunning = false;
    
    // Available quests - populated from QuestDatabase
    private static final List<String> AVAILABLE_QUESTS = new ArrayList<>();
    static {
        AVAILABLE_QUESTS.add("-- Select a Quest --");
        
        // Add quests from QuestDatabase
        for (QuestDatabase.QuestInfo quest : QuestDatabase.getAllQuests().values()) {
            AVAILABLE_QUESTS.add(quest.getDisplayName());
        }
    }
    
    public interface QuestStartListener {
        void onQuestStart(String questName);
        void onQuestStop();
        void onDiscoveryStart();
        void onDiscoveryStop();
    }
    
    public static QuestSelectionGUI getInstance() {
        if (instance == null) {
            instance = new QuestSelectionGUI();
        }
        return instance;
    }
    
    private QuestSelectionGUI() {
        try {
            initializeGUI();
        } catch (Exception e) {
            System.err.println("GUI initialization failed: " + e.getMessage());
            e.printStackTrace();
            // Create a minimal fallback GUI
            createFallbackGUI();
        }
    }
    
    private void createFallbackGUI() {
        setTitle("AI Quest Framework v7.6 (Fallback Mode)");
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(400, 200);
        JPanel panel = new JPanel();
        panel.add(new JLabel("GUI running in fallback mode"));
        add(panel);
    }
    
    private void initializeGUI() {
        setTitle("AI Quest Framework v7.6");
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setResizable(true);
        setAlwaysOnTop(true);
        setSize(500, 400);
        
        // DO NOT change Look and Feel - use DreamBot's default Substance theme
        // Removed UIManager.setLookAndFeel to prevent conflicts with DreamBot's Substance theme
        
        // Main container with compatible background
        JPanel mainContainer = new JPanel(new BorderLayout());
        // Use a more compatible color that works with Substance theme
        mainContainer.setBackground(UIManager.getColor("Panel.background"));
        mainContainer.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Header with title and logo
        JPanel headerPanel = createHeaderPanel();
        mainContainer.add(headerPanel, BorderLayout.NORTH);
        
        // Tabbed pane for Discovery vs Quest Execution
        JTabbedPane tabbedPane = createTabbedPane();
        mainContainer.add(tabbedPane, BorderLayout.CENTER);
        
        // Footer with status
        JPanel footerPanel = createFooterPanel();
        mainContainer.add(footerPanel, BorderLayout.SOUTH);
        
        add(mainContainer);
        setLocationRelativeTo(null);
    }
    
    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        // Use theme-compatible colors
        header.setBackground(UIManager.getColor("Panel.background"));
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createRaisedBevelBorder(),
            new EmptyBorder(10, 15, 10, 15)
        ));
        
        // Title - removed emoji to prevent encoding issues
        JLabel titleLabel = new JLabel("AI Quest Framework");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(UIManager.getColor("Label.foreground"));
        
        // Version
        JLabel versionLabel = new JLabel("v7.6 - Tree-Based Quest System");
        versionLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        versionLabel.setForeground(UIManager.getColor("Label.foreground"));
        
        header.add(titleLabel, BorderLayout.WEST);
        header.add(versionLabel, BorderLayout.EAST);
        
        return header;
    }
    
    private JTabbedPane createTabbedPane() {
        JTabbedPane tabbedPane = new JTabbedPane();
        // Use default theme colors instead of hardcoded colors
        tabbedPane.setBackground(UIManager.getColor("TabbedPane.background"));
        tabbedPane.setForeground(UIManager.getColor("TabbedPane.foreground"));
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 12));
        
        // Discovery Mode Tab
        JPanel discoveryPanel = createDiscoveryPanel();
        tabbedPane.addTab("Discovery Mode", discoveryPanel);
        
        // Quest Automation Tab
        JPanel questPanel = createQuestPanel();
        tabbedPane.addTab("Quest Automation", questPanel);
        
        return tabbedPane;
    }
    
    private JPanel createDiscoveryPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(UIManager.getColor("Panel.background"));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        // Description
        JPanel descPanel = new JPanel(new BorderLayout());
        descPanel.setBackground(UIManager.getColor("Panel.background"));
        
        JLabel descLabel = new JLabel("<html><b>Discovery Mode</b><br>" +
            "Automatically track and record quest actions as you play manually.<br>" +
            "Perfect for learning new quests or analyzing game mechanics.</html>");
        descLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        descLabel.setForeground(UIManager.getColor("Label.foreground"));
        descPanel.add(descLabel, BorderLayout.CENTER);
        
        // Controls
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        controlPanel.setBackground(UIManager.getColor("Panel.background"));
        
        discoveryModeCheckbox = new JCheckBox("START DISCOVERY");
        discoveryModeCheckbox.setFont(new Font("Segoe UI", Font.BOLD, 14));
        discoveryModeCheckbox.setForeground(UIManager.getColor("CheckBox.foreground"));
        discoveryModeCheckbox.setBackground(UIManager.getColor("CheckBox.background"));
        discoveryModeCheckbox.setFocusPainted(false);
        
        discoveryModeCheckbox.addActionListener(e -> {
            if (discoveryModeCheckbox.isSelected()) {
                startDiscoveryMode();
            } else {
                stopDiscoveryMode();
            }
        });
        
        controlPanel.add(discoveryModeCheckbox);
        
        // Status
        discoveryStatusLabel = new JLabel("Ready for Discovery", JLabel.CENTER);
        discoveryStatusLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        discoveryStatusLabel.setForeground(UIManager.getColor("Label.foreground"));
        
        panel.add(descPanel, BorderLayout.NORTH);
        panel.add(controlPanel, BorderLayout.CENTER);
        panel.add(discoveryStatusLabel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createQuestPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(UIManager.getColor("Panel.background"));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        // Quest Selection
        JPanel selectionPanel = new JPanel(new BorderLayout(10, 10));
        selectionPanel.setBackground(UIManager.getColor("Panel.background"));
        selectionPanel.setBorder(new TitledBorder(
            BorderFactory.createLineBorder(UIManager.getColor("TitledBorder.titleColor"), 2),
            "Quest Selection",
            TitledBorder.DEFAULT_JUSTIFICATION,
            TitledBorder.DEFAULT_POSITION,
            new Font("Segoe UI", Font.BOLD, 12),
            UIManager.getColor("TitledBorder.titleColor")
        ));
        
        questDropdown = new JComboBox<>(AVAILABLE_QUESTS.toArray(new String[0]));
        questDropdown.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        questDropdown.setPreferredSize(new Dimension(200, 30));
        
        selectionPanel.add(new JLabel("Select Quest:", JLabel.LEFT) {{
            setForeground(UIManager.getColor("Label.foreground"));
            setFont(new Font("Segoe UI", Font.PLAIN, 12));
        }}, BorderLayout.WEST);
        selectionPanel.add(questDropdown, BorderLayout.CENTER);
        
        // Control Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        buttonPanel.setBackground(UIManager.getColor("Panel.background"));
        
        startQuestButton = new JButton("START QUEST");
        startQuestButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        // Use theme-compatible button colors
        startQuestButton.setForeground(UIManager.getColor("Button.foreground"));
        startQuestButton.setBackground(UIManager.getColor("Button.background"));
        startQuestButton.setFocusPainted(false);
        startQuestButton.setBorder(BorderFactory.createRaisedBevelBorder());
        startQuestButton.setPreferredSize(new Dimension(120, 35));
        
        stopQuestButton = new JButton("STOP QUEST");
        stopQuestButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        stopQuestButton.setForeground(UIManager.getColor("Button.foreground"));
        stopQuestButton.setBackground(UIManager.getColor("Button.background"));
        stopQuestButton.setFocusPainted(false);
        stopQuestButton.setBorder(BorderFactory.createRaisedBevelBorder());
        stopQuestButton.setPreferredSize(new Dimension(120, 35));
        stopQuestButton.setEnabled(false);
        
        startQuestButton.addActionListener(e -> startSelectedQuest());
        stopQuestButton.addActionListener(e -> stopSelectedQuest());
        
        buttonPanel.add(startQuestButton);
        buttonPanel.add(stopQuestButton);
        
        // Progress
        JPanel progressPanel = new JPanel(new BorderLayout(5, 5));
        progressPanel.setBackground(UIManager.getColor("Panel.background"));
        
        questProgressBar = new JProgressBar(0, 100);
        questProgressBar.setStringPainted(true);
        questProgressBar.setString("Select a quest to begin");
        // Use default progress bar colors
        questProgressBar.setForeground(UIManager.getColor("ProgressBar.foreground"));
        questProgressBar.setBackground(UIManager.getColor("ProgressBar.background"));
        
        questStatusLabel = new JLabel("No quest selected", JLabel.CENTER);
        questStatusLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        questStatusLabel.setForeground(UIManager.getColor("Label.foreground"));
        
        progressPanel.add(questProgressBar, BorderLayout.NORTH);
        progressPanel.add(questStatusLabel, BorderLayout.SOUTH);
        
        // Log Area
        questLogArea = new JTextArea(6, 40);
        questLogArea.setFont(new Font("Consolas", Font.PLAIN, 10));
        questLogArea.setBackground(UIManager.getColor("TextArea.background"));
        questLogArea.setForeground(UIManager.getColor("TextArea.foreground"));
        questLogArea.setEditable(false);
        questLogArea.setBorder(new EmptyBorder(5, 5, 5, 5));
        
        JScrollPane scrollPane = new JScrollPane(questLogArea);
        scrollPane.setBorder(new TitledBorder(
            BorderFactory.createLineBorder(new Color(100, 150, 255), 1),
            "Quest Log",
            TitledBorder.DEFAULT_JUSTIFICATION,
            TitledBorder.DEFAULT_POSITION,
            new Font("Segoe UI", Font.PLAIN, 10),
            new Color(100, 150, 255)
        ));
        
        panel.add(selectionPanel, BorderLayout.NORTH);
        panel.add(buttonPanel, BorderLayout.CENTER);
        panel.add(progressPanel, BorderLayout.SOUTH);
        
        // Add log panel to the bottom
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(new Color(35, 35, 35));
        bottomPanel.add(progressPanel, BorderLayout.NORTH);
        bottomPanel.add(scrollPane, BorderLayout.CENTER);
        
        panel.remove(progressPanel);
        panel.add(bottomPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createFooterPanel() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(new Color(20, 20, 20));
        footer.setBorder(new EmptyBorder(5, 10, 5, 10));
        
        JLabel authorLabel = new JLabel("Created by Leone | AI Quest Framework");
        authorLabel.setFont(new Font("Segoe UI", Font.ITALIC, 10));
        authorLabel.setForeground(new Color(120, 120, 120));
        
        footer.add(authorLabel, BorderLayout.WEST);
        
        return footer;
    }
    
    // Discovery Mode Methods
    private void startDiscoveryMode() {
        if (questStartListener != null && !discoveryRunning) {
            discoveryRunning = true;
            discoveryModeCheckbox.setText("STOP DISCOVERY");
            discoveryModeCheckbox.setForeground(new Color(255, 100, 100));
            discoveryStatusLabel.setText("DISCOVERY ACTIVE - Recording all actions");
            discoveryStatusLabel.setForeground(new Color(255, 100, 100));
            questStartListener.onDiscoveryStart();
        }
    }
    
    private void stopDiscoveryMode() {
        if (discoveryRunning) {
            discoveryRunning = false;
            discoveryModeCheckbox.setText("START DISCOVERY");
            discoveryModeCheckbox.setForeground(new Color(100, 255, 100));
            discoveryStatusLabel.setText("Discovery stopped");
            discoveryStatusLabel.setForeground(new Color(180, 180, 180));
            if (questStartListener != null) {
                questStartListener.onDiscoveryStop();
            }
        }
    }
    
    // Quest Automation Methods
    private void startSelectedQuest() {
        String selectedQuest = (String) questDropdown.getSelectedItem();
        if (selectedQuest == null || selectedQuest.equals("-- Select a Quest --")) {
            updateQuestLog("Please select a quest first!");
            return;
        }
        
        if (!questRunning) {
            questRunning = true;
            startQuestButton.setEnabled(false);
            stopQuestButton.setEnabled(true);
            questDropdown.setEnabled(false);
            
            questStatusLabel.setText("Starting quest: " + selectedQuest);
            questProgressBar.setValue(0);
            questProgressBar.setString("Initializing...");
            
            updateQuestLog("Starting quest: " + selectedQuest);
            
            // Use QuestExecutor to start the actual quest script
            if (questExecutor != null) {
                String questId = findQuestIdByDisplayName(selectedQuest);
                if (questId != null) {
                    boolean success = questExecutor.startQuest(questId);
                    if (success) {
                        updateQuestLog("Quest started successfully!");
                        questStatusLabel.setText("RUNNING: " + selectedQuest);
                        questProgressBar.setString("Quest Active");
                        
                        // Also notify the listener if needed for additional logging
                        if (questStartListener != null) {
                            questStartListener.onQuestStart(selectedQuest);
                        }
                    } else {
                        updateQuestLog("Failed to start quest");
                        resetQuestUI();
                    }
                } else {
                    updateQuestLog("Quest not found in database");
                    resetQuestUI();
                }
            } else {
                updateQuestLog("Quest executor not available");
                resetQuestUI();
            }
        }
    }
    
    private void resetQuestUI() {
        questRunning = false;
        startQuestButton.setEnabled(true);
        stopQuestButton.setEnabled(false);
        questDropdown.setEnabled(true);
        questStatusLabel.setText("Ready");
        questProgressBar.setValue(0);
        questProgressBar.setString("Select a quest to begin");
    }
    
    private void stopSelectedQuest() {
        if (questRunning) {
            questRunning = false;
            updateQuestLog("Stopping quest...");
            
            if (questExecutor != null && questExecutor.isActive()) {
                questExecutor.stopQuest();
            }
            
            if (questStartListener != null) {
                questStartListener.onQuestStop();
            }
            
            resetQuestUI();
        }
    }
    
    private void setQuestStopped() {
        SwingUtilities.invokeLater(() -> {
            questRunning = false;
            startQuestButton.setEnabled(true);
            stopQuestButton.setEnabled(false);
            questDropdown.setEnabled(true);
            questStatusLabel.setText("Quest stopped");
            questProgressBar.setValue(0);
            questProgressBar.setString("Ready");
        });
    }
    
    private void updateQuestLog(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
            questLogArea.append("[" + timestamp + "] " + message + "\n");
            questLogArea.setCaretPosition(questLogArea.getDocument().getLength());
        });
    }
    
    private void startQuest() {
        if (questStartListener != null && !questRunning) {
            String selectedQuest = (String) questDropdown.getSelectedItem();
            
            // If we have a quest executor and it's a specific quest (not FREE DISCOVERY MODE)
            if (questExecutor != null && !selectedQuest.equals("FREE DISCOVERY MODE") && !selectedQuest.startsWith("-------")) {
                // Find the quest ID from database
                String questId = findQuestIdByDisplayName(selectedQuest);
                if (questId != null) {
                    addLogMessage("Starting quest execution: " + selectedQuest);
                    boolean success = questExecutor.startQuest(questId);
                    if (success) {
                        setQuestRunning(true);
                        addLogMessage("Quest started successfully: " + selectedQuest);
                    } else {
                        addLogMessage("Failed to start quest: " + selectedQuest);
                        setQuestFailed(selectedQuest, "Initialization failed");
                    }
                    return;
                }
            }
            
            // Fallback to listener-based approach (for FREE DISCOVERY MODE)
            questStartListener.onQuestStart(selectedQuest);
        }
    }
    
    private String findQuestIdByDisplayName(String displayName) {
        for (QuestDatabase.QuestInfo quest : QuestDatabase.getAllQuests().values()) {
            if (quest.getDisplayName().equals(displayName)) {
                return quest.getQuestId();
            }
        }
        return null;
    }
    
    private void stopQuest() {
        if (questExecutor != null && questExecutor.isActive()) {
            addLogMessage("Stopping quest execution...");
            questExecutor.stopQuest();
            setQuestRunning(false);
        }
        
        if (questStartListener != null) {
            questStartListener.onQuestStop();
        }
    }
    
    public void showGUI() {
        SwingUtilities.invokeLater(() -> {
            setVisible(true);
            toFront();
            requestFocus();
        });
    }
    
    public void setQuestStartListener(QuestStartListener listener) {
        this.questStartListener = listener;
    }
    
    public void setQuestExecutor(QuestExecutor executor) {
        this.questExecutor = executor;
    }
    
    public void setQuestRunning(boolean running) {
        SwingUtilities.invokeLater(() -> {
            questRunning = running;
            if (running) {
                startQuestButton.setEnabled(false);
                stopQuestButton.setEnabled(true);
                questDropdown.setEnabled(false);
                String questName = (String) questDropdown.getSelectedItem();
                questStatusLabel.setText("RUNNING: " + questName);
                questProgressBar.setString("Quest in progress...");
            } else {
                startQuestButton.setEnabled(true);
                stopQuestButton.setEnabled(false);
                questDropdown.setEnabled(true);
                questStatusLabel.setText("Ready");
                questProgressBar.setString("Ready");
            }
        });
    }
    
    public void setQuestCompleted(String questName) {
        SwingUtilities.invokeLater(() -> {
            questRunning = false;
            startQuestButton.setEnabled(true);
            stopQuestButton.setEnabled(false);
            questDropdown.setEnabled(true);
            questStatusLabel.setText(questName + " completed!");
            questStatusLabel.setForeground(new Color(100, 255, 100));
            questProgressBar.setValue(100);
            questProgressBar.setString("Quest completed!");
            updateQuestLog("🎉 Quest completed successfully: " + questName);
        });
    }
    
    public void setQuestFailed(String questName, String reason) {
        SwingUtilities.invokeLater(() -> {
            questRunning = false;
            startQuestButton.setEnabled(true);
            stopQuestButton.setEnabled(false);
            questDropdown.setEnabled(true);
            questStatusLabel.setText(questName + " failed");
            questStatusLabel.setForeground(new Color(255, 100, 100));
            questProgressBar.setValue(0);
            questProgressBar.setString("Quest failed");
            updateQuestLog("Quest failed: " + questName + " - " + reason);
        });
    }
    
    public void updateStatus(String status) {
        SwingUtilities.invokeLater(() -> {
            questStatusLabel.setText(status);
            questStatusLabel.setForeground(new Color(180, 180, 180));
        });
    }
    
    public void updateProgress(int percentage) {
        SwingUtilities.invokeLater(() -> {
            questProgressBar.setValue(percentage);
            questProgressBar.setString(percentage + "% complete");
        });
    }
    
    public boolean isRecordingModeEnabled() {
        return discoveryRunning || questRunning;
    }
    
    // Legacy method compatibility
    public void addLogMessage(String message) {
        updateQuestLog(message);
    }
    
    public void logMessage(String message) {
        updateQuestLog(message);
    }
    
    public void appendAutomationLog(String message) {
        updateQuestLog("[AUTO] " + message);
    }
    
    public void appendLog(String message) {
        updateQuestLog(message);
    }
    
    public void appendDiscoveryLog(String message) {
        updateQuestLog("[DISC] " + message);
    }
    
    // Additional methods for main script integration
    public void setMainScript(Object script) {
        updateQuestLog("Main script connected");
    }
}
