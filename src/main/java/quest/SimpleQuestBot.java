package quest;

import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.listener.ActionListener;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.wrappers.widgets.MenuRow;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.methods.container.impl.bank.Bank;

import quest.gui.QuestSelectionGUI;
import quest.core.QuestEventLogger;
import quest.core.QuestExecutor;

import javax.swing.SwingUtilities;
import java.awt.Graphics;
import java.awt.Color;

/**
 * Comprehensive Quest Recording System
 * Records all player actions during manual quest completion
 * Captures: Movement, NPC Interactions, Dialogue, Objects, Inventory, Banking, Animations
 */
@ScriptManifest(
    category = Category.UTILITY,
    name = "AI Quest Framework v7.0",
    author = "Leone", 
    version = 7.0,
    description = "Complete AI Quest Discovery & Automation Framework with varbit+config monitoring and automated quest execution"
)
public class SimpleQuestBot extends AbstractScript implements ActionListener {
    
    // Core components
    private QuestSelectionGUI gui;
    private QuestEventLogger questLogger;
    
    // Recording state
    private boolean recordingMode = false;
    private String selectedQuest = "";
    private boolean guiInitialized = false;
    private int loopCount = 0;
    private String lastStatus = ""; // Track last status to prevent spam
    private int walkingLogCounter = 0; // Counter to reduce walking detection log spam
    
    @Override
    public void onStart() {
        log("=== Quest Action Recorder v5.0 - Real-Time Quest Step Tracking ===");
        log("NEW: Comprehensive varbit monitoring for individual quest steps");
        log("NEW: Real-time quest progress tracking as steps happen");
        log("Enhanced: Perfect action capture with ActionListener");
        log("Enhanced: Journey-based movement tracking with coordinates");
        log("1. Select 'FREE DISCOVERY MODE' for any actions");
        log("2. Or select specific quest for quest-focused recording");
        log("3. Check 'RECORD ACTIONS' to begin logging");
        log("4. Complete actions manually - everything is logged with proper categorization");
        
        // Initialize quest logger for discovery mode (always available)
        questLogger = new QuestEventLogger(this, "Free_Discovery");
        
        // Initialize quest executor with script reference
        QuestExecutor.getInstance().setScriptReference(this);
        
        // Launch GUI on Swing thread with error handling
        SwingUtilities.invokeLater(() -> {
            try {
                gui = QuestSelectionGUI.getInstance();
                gui.setVisible(true);
                guiInitialized = true;
                
                // Connect this script to the GUI for discovery communication
                gui.setMainScript(this);
                
                // Wire up the QuestExecutor to the GUI
                gui.setQuestExecutor(QuestExecutor.getInstance());
                
                // Set up quest start listener for manual quest selection
                gui.setQuestStartListener(new QuestSelectionGUI.QuestStartListener() {
                    @Override
                    public void onQuestStart(String questName) {
                        startRecording(questName);
                    }
                    
                    @Override
                    public void onQuestStop() {
                        stopRecording();
                    }
                    
                    @Override
                    public void onDiscoveryStart() {
                        startDiscoveryMode();
                    }
                    
                    @Override
                    public void onDiscoveryStop() {
                        stopDiscoveryMode();
                    }
                });
                
                log("GUI launched successfully - Quest logger initialized for discovery mode");
            } catch (Exception e) {
                log("GUI initialization failed: " + e.getMessage());
                log("Script will continue to work without GUI");
                e.printStackTrace();
            }
        });
        
        log("GUI launched - Quest logger initialized for discovery mode");
    }
    
    @Override
    public int onLoop() {
        loopCount++;
        
        if (!guiInitialized) {
            Sleep.sleep(100);
            return 100;
        }
        
        // Check if quest automation is active
        QuestExecutor executor = QuestExecutor.getInstance();
        if (executor.isActive()) {
            // WALKING STATE DETECTION: Check if player is currently walking
            if (isPlayerWalking()) {
                // Extended sleep when walking is in progress - DO NOT interrupt walking
                String walkingStatus = "AUTOMATION: " + executor.getCurrentQuestName() + " - WALKING IN PROGRESS";
                if (!walkingStatus.equals(lastStatus)) {
                    SwingUtilities.invokeLater(() -> {
                        if (gui != null) {
                            gui.appendAutomationLog(walkingStatus);
                        }
                    });
                    lastStatus = walkingStatus;
                }
                return org.dreambot.api.methods.Calculations.random(1500, 2000); // Extended sleep for walking (1.5-2s)
            }
            
            // Only execute new steps when not walking
            executor.executeStep();
            
            // Update GUI with automation status
            String automationStatus = "AUTOMATION: " + executor.getCurrentQuestName() + " - " + executor.getCurrentStepDescription();
            if (!automationStatus.equals(lastStatus)) {
                SwingUtilities.invokeLater(() -> {
                    if (gui != null) {
                        gui.appendAutomationLog(automationStatus);
                    }
                });
                lastStatus = automationStatus;
            }
            
            // Check again if walking started during step execution
            if (isPlayerWalking()) {
                return org.dreambot.api.methods.Calculations.random(1500, 2000); // Extended sleep if walking just started
            }
            
            return org.dreambot.api.methods.Calculations.random(800, 1200); // Varied loop timing for automation
        }
        
        if (recordingMode) {
            // Debug: Confirm we're in recording mode (reduced frequency)
            if (loopCount % 300 == 0) { // Every 90 seconds
                log("DEBUG: Recording mode active, quest logger = " + (questLogger != null ? "exists" : "null"));
            }
            
            // HIGH-FREQUENCY quest event logging for near real-time detection
            if (questLogger != null) {
                questLogger.checkForEvents();
            } else {
                log("WARNING: Quest logger is null but recording mode is active!");
            }
            
            // Update GUI status - avoid spam
            String currentStatus = "RECORDING: " + selectedQuest + " - Complete quest manually";
            if (!currentStatus.equals(lastStatus)) {
                SwingUtilities.invokeLater(() -> {
                    if (gui != null) {
                        gui.appendLog(currentStatus);
                    }
                });
                lastStatus = currentStatus;
            }
        } else {
            // Idle mode - only update status once, not constantly
            String currentStatus = "IDLE: Select quest to start recording";
            if (!currentStatus.equals(lastStatus)) {
                SwingUtilities.invokeLater(() -> {
                    if (gui != null) {
                        gui.appendLog(currentStatus);
                    }
                });
                lastStatus = currentStatus;
            }
        }
        
        return 100; // OPTIMIZED: Check every 100ms for near real-time action detection
    }
    
    /**
     * WALKING STATE DETECTION: Check if player is currently walking or has movement pending
     * This prevents script from interrupting walking actions
     */
    private boolean isPlayerWalking() {
        try {
            // Check if player exists and is moving
            if (Bank.isOpen()) {
                return false; // Not walking if bank is open
            }
            
            if (Players.getLocal() == null) {
                return false; // Can't walk if no player
            }
            
            // Primary check: Is player currently moving?
            boolean isCurrentlyMoving = Players.getLocal().isMoving();
            
            // Secondary check: Does player have a destination set?
            boolean hasDestination = Walking.getDestination() != null;
            
            // Tertiary check: Is walking animation active?
            int currentAnimation = Players.getLocal().getAnimation();
            boolean walkingAnimation = currentAnimation != -1 && (
                currentAnimation == 819 ||  // Walking animation ID
                currentAnimation == 824 ||  // Running animation ID
                currentAnimation == 1205 || // Another walking animation
                currentAnimation == 1206    // Another running animation
            );
            
            // Return true if any walking indicator is active
            boolean isWalking = isCurrentlyMoving || hasDestination || walkingAnimation;
            
            // REDUCED LOGGING: Only log walking detection every 30th check to prevent spam
            if (isWalking) {
                walkingLogCounter++;
                if (walkingLogCounter % 30 == 0) {
                    log("WALKING: moving=" + isCurrentlyMoving + ", hasDestination=" + hasDestination + ", walkAnim=" + walkingAnimation + " (anim=" + currentAnimation + ")");
                }
            } else {
                walkingLogCounter = 0; // Reset counter when not walking
            }
            
            return isWalking;
            
        } catch (Exception e) {
            // If we can't detect walking state, assume not walking to avoid infinite waits
            log("Error detecting walking state: " + e.getMessage());
            return false;
        }
    }

    /**
     * Start recording mode for selected quest or free discovery
     */
    public void startRecording(String questName) {
        if ("FREE DISCOVERY MODE".equals(questName)) {
            log("=== STARTING FREE DISCOVERY RECORDING ===");
            log("Mode: Free Discovery - All actions will be logged");
            selectedQuest = "Free_Discovery";
        } else if ("------- SPECIFIC QUESTS -------".equals(questName)) {
            log("Please select a valid quest or FREE DISCOVERY MODE");
            return;
        } else {
            log("=== STARTING QUEST-SPECIFIC RECORDING ===");
            log("Quest: " + questName);
            selectedQuest = questName;
        }
        
        log("Recording: Movement, NPCs, Dialogue, Objects, Inventory, Banking, Animations");
        recordingMode = true;
        
        // Initialize comprehensive quest logger
        questLogger = new QuestEventLogger(this, selectedQuest);
        
        // ActionListener is now implemented by this script class - no manual registration needed
        log("ActionListener implemented - will now capture actual user clicks!");
        
        // Update GUI to show recording state
        if (gui != null) {
            if ("FREE DISCOVERY MODE".equals(questName)) {
                gui.appendLog("RECORDING: Free Discovery Mode");
                gui.appendLog("Started free discovery recording");
            } else {
                gui.appendLog("RECORDING: " + questName);
                gui.appendLog("Started recording: " + questName);
            }
        }
        
        log("RECORDING NOW ACTIVE - All 7 core action types will be logged!");
        log("Log file location: C:\\Users\\Leone\\Desktop\\Projects in progress\\Dreambot Projects\\AI Quest system\\quest_logs\\");
        log("Console logs will show each action as you perform them");
        log("=======================================");
    }
    
    /**
     * Stop recording mode
     */
    public void stopRecording() {
        if (recordingMode) {
            log("Stopping recording for: " + selectedQuest);
            recordingMode = false;
            
            // Stop quest logger
            if (questLogger != null) {
                questLogger.close();
            }
            
            // Update GUI to show stopped state
            if (gui != null) {
                gui.appendLog("STOPPED: " + selectedQuest);
                gui.appendLog("Stopped recording: " + selectedQuest);
            }
            
            log("Recording stopped. Check quest_logs/ folder for complete action log.");
        }
    }
    
    @Override
    public void onExit() {
        log("Quest Action Recorder shutting down");
        
        if (recordingMode) {
            stopRecording();
        }
        
        // Ensure quest logger is properly stopped
        if (questLogger != null) {
            questLogger.close();
        }
        
        if (gui != null) {
            SwingUtilities.invokeLater(() -> gui.dispose());
        }
    }
    
    @Override
    public void onPaint(Graphics g) {
        // Minimal paint - just show recording status
        if (recordingMode) {
            g.setColor(Color.RED);
            g.drawString("RECORDING: " + selectedQuest, 10, 25);
        }
    }
    
    // Helper methods
    public boolean isRecording() {
        return recordingMode;
    }
    
    public String getCurrentQuest() {
        return selectedQuest;
    }
    
    /**
     * Called by GUI to start discovery mode
     */
    public void startDiscoveryMode() {
        log("=== DISCOVERY MODE ACTIVATED FROM GUI ===");
        startRecording("FREE DISCOVERY MODE");
    }
    
    /**
     * Called by GUI to stop discovery mode
     */
    public void stopDiscoveryMode() {
        log("=== DISCOVERY MODE STOPPED FROM GUI ===");
        stopRecording();
    }
    
    /**
     * Send discovery message to GUI
     */
    public void sendDiscoveryToGUI(String message) {
        if (gui != null) {
            gui.appendDiscoveryLog(message);
        }
    }
    
    // ActionListener implementation - delegate to questLogger
    @Override
    public void onAction(MenuRow menuRow, int mouseX, int mouseY) {
        if (recordingMode && questLogger != null) {
            questLogger.onAction(menuRow, mouseX, mouseY);
        }
    }
}
