package quest.core;

import quest.quests.VampireSlayerScript;
import quest.quests.ImpCatcherScript;
import quest.quests.LemonTutQuest;

import quest.trees.RomeoAndJulietTree;
import quest.trees.RuneMysteriesTree;
import quest.trees.CooksAssistantTree;
import quest.trees.WitchsPotionTree;
import quest.trees.SheepShearerTree;
import quest.trees.XMarksTheSpotTree;
import quest.trees.RestlessGhostTree;

import org.dreambot.api.methods.Calculations;
import org.dreambot.api.utilities.Timer;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.methods.container.impl.bank.Bank;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The automation engine that manages and executes quest scripts.
 * This is the brain that runs our automated quest completion system.
 */
public class QuestExecutor {
    
    public enum ExecutorState {
        IDLE,
        PREPARING,
        EXECUTING,
        PAUSED,
        COMPLETED,
        ERROR
    }
    
    private static QuestExecutor instance;
    private ExecutorState currentState;
    private QuestScript activeQuest;
    private String activeQuestId;
    private Timer questTimer;
    private Timer stepTimer;
    private Map<String, Object> questContext;
    private List<String> executionLog;
    private boolean emergencyStop;
    private int maxRetries;
    private int currentRetries;
    
    // Execution statistics
    private long questStartTime;
    private long totalExecutionTime;
    private int stepsCompleted;
    private int questsCompleted;
    
    // Script reference for quest initialization
    private AbstractScript scriptReference;
    
    private QuestExecutor() {
        this.currentState = ExecutorState.IDLE;
        this.questContext = new ConcurrentHashMap<>();
        this.executionLog = new ArrayList<>();
        this.maxRetries = 3;
        this.currentRetries = 0;
        this.emergencyStop = false;
    }
    
    /**
     * Set the script reference for quest initialization
     */
    public void setScriptReference(AbstractScript script) {
        this.scriptReference = script;
    }
    
    /**
     * Get singleton instance
     */
    public static QuestExecutor getInstance() {
        if (instance == null) {
            instance = new QuestExecutor();
        }
        return instance;
    }
    
    /**
     * Start executing a quest by quest ID
     */
    public boolean startQuest(String questId) {
        if (currentState != ExecutorState.IDLE) {
            log("Cannot start quest - executor is not idle. Current state: " + currentState);
            return false;
        }
        
        // Validate quest exists in database
        QuestDatabase.QuestInfo questInfo = QuestDatabase.getQuestInfo(questId);
        if (questInfo == null) {
            log("ERROR: Quest not found in database: " + questId);
            return false;
        }
        
        // Check if quest is already complete
        if (QuestDatabase.isQuestComplete(questId)) {
            log("Quest already completed: " + questInfo.getDisplayName());
            return false;
        }
        
        // Try to load quest script
        QuestScript questScript = loadQuestScript(questId);
        if (questScript == null) {
            log("ERROR: No quest script available for: " + questInfo.getDisplayName());
            return false;
        }
        
        // Initialize the quest script with dependencies
        if (scriptReference != null) {
            questScript.initialize(scriptReference, null); // QuestDatabase is static, no instance needed
        }
        
        // Initialize quest execution
        this.activeQuest = questScript;
        this.activeQuestId = questId;
        this.currentState = ExecutorState.PREPARING;
        this.questTimer = new Timer();
        this.stepTimer = new Timer();
        this.questStartTime = System.currentTimeMillis();
        this.currentRetries = 0;
        this.questContext.clear();
        
        log("Starting quest: " + questInfo.getDisplayName());
        log("Estimated duration: " + questInfo.getEstimatedDurationMinutes() + " minutes");
        
        // Prepare quest
        return prepareQuest();
    }
    
    /**
     * Prepare quest before execution
     */
    private boolean prepareQuest() {
        if (activeQuest == null) return false;
        
        try {
            currentState = ExecutorState.PREPARING;
            log("Preparing quest...");
            
            // Check requirements
            log("DEBUG: Checking if quest has required items...");
            boolean hasItems = activeQuest.hasRequiredItems();
            log("DEBUG: hasRequiredItems() returned: " + hasItems);
            
            if (!hasItems) {
                String[] required = activeQuest.getRequiredItems();
                log("Missing required items - please check: " + String.join(", ", required));
                log("Please gather required items before starting quest");
                log("DEBUG: Quest preparation failed due to missing items");
                currentState = ExecutorState.ERROR;
                return false;
            }
            
            // Skill requirements check removed for now
            // Can be added later if needed
            
            // Initialize quest
            activeQuest.onQuestStart();
            currentState = ExecutorState.EXECUTING;
            log("Quest preparation complete - beginning execution");
            
            return true;
            
        } catch (Exception e) {
            log("ERROR during quest preparation: " + e.getMessage());
            currentState = ExecutorState.ERROR;
            return false;
        }
    }
    
    /**
     * Execute one step of the current quest
     * This should be called repeatedly from the main bot loop
     */
    public void executeStep() {
        if (currentState != ExecutorState.EXECUTING || activeQuest == null || emergencyStop) {
            return;
        }
        
        try {
            // ENHANCED DEBUG: Log quest execution details
            log("[DEBUG] executeStep() called for quest: " + (activeQuestId != null ? activeQuestId : "null"));
            log("[DEBUG] Active quest class: " + (activeQuest != null ? activeQuest.getClass().getSimpleName() : "null"));
            log("[DEBUG] Current step: " + (activeQuest != null ? activeQuest.getCurrentStepDescription() : "null"));
            
            // WALKING STATE CHECK: Wait for any existing walking to complete before starting new actions
            if (isPlayerWalking()) {
                log("[INFO] Waiting for walking to complete before executing next step...");
                return; // Skip execution this loop, wait for walking to finish
            }
            
            // Check if quest is complete
            if (activeQuest.isComplete()) {
                log("[SUCCESS] Quest completed, calling completeQuest()");
                completeQuest();
                return;
            }
            
            // Reset step timer
            stepTimer = new Timer();
            
            // Execute current step with enhanced logging
            String stepDesc = activeQuest.getCurrentStepDescription();
            log("🎯 Executing step: " + stepDesc);
            log("🔄 Calling activeQuest.executeCurrentStep()...");
            
            boolean stepResult = activeQuest.executeCurrentStep();
            
            log("📊 Step execution result: " + stepResult);
            
            // Check if step progressed (simplified success detection)
            if (stepResult) {
                stepsCompleted++;
                currentRetries = 0;
                log("[SUCCESS] Step completed successfully (total steps: " + stepsCompleted + ")");
                
                // Small delay between steps
                try {
                    Thread.sleep(Calculations.random(1000, 2000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
            } else {
                currentRetries++;
                log("[ERROR] Step failed (attempt " + currentRetries + "/" + maxRetries + ")");
                
                if (currentRetries >= maxRetries) {
                    log("🛑 Max retries reached - stopping quest execution");
                    stopQuest();
                    return;
                }
                
                // Wait before retry
                try {
                    Thread.sleep(Calculations.random(3000, 5000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
        } catch (Exception e) {
            log("💥 CRITICAL ERROR during step execution: " + e.getMessage());
            e.printStackTrace();
            stopQuest();
        }
    }
    
    /**
     * Complete the current quest
     */
    private void completeQuest() {
        if (activeQuest == null) return;
        
        try {
            totalExecutionTime = System.currentTimeMillis() - questStartTime;
            questsCompleted++;
            
            log("=== QUEST COMPLETED ===");
            log("Quest: " + QuestDatabase.getQuestInfo(activeQuestId).getDisplayName());
            log("Total time: " + formatTime(totalExecutionTime));
            log("Steps completed: " + stepsCompleted);
            log("=======================");
            
            activeQuest.onQuestComplete();
            currentState = ExecutorState.COMPLETED;
            
            // Reset for next quest
            resetExecutor();
            
        } catch (Exception e) {
            log("ERROR during quest completion: " + e.getMessage());
        }
    }
    
    /**
     * Stop the current quest execution
     */
    public void stopQuest() {
        if (activeQuest != null) {
            try {
                log("Stopping quest execution...");
                activeQuest.cleanup();
            } catch (Exception e) {
                log("Error during quest stop: " + e.getMessage());
            }
        }
        
        resetExecutor();
    }
    
    /**
     * Emergency stop - immediate halt
     */
    public void emergencyStop() {
        log("EMERGENCY STOP ACTIVATED");
        emergencyStop = true;
        stopQuest();
    }
    
    /**
     * Pause quest execution
     */
    public void pauseQuest() {
        if (currentState == ExecutorState.EXECUTING) {
            currentState = ExecutorState.PAUSED;
            log("Quest execution paused");
        }
    }
    
    /**
     * Resume quest execution
     */
    public void resumeQuest() {
        if (currentState == ExecutorState.PAUSED) {
            currentState = ExecutorState.EXECUTING;
            log("Quest execution resumed");
        }
    }
    
    /**
     * Reset executor to idle state
     */
    private void resetExecutor() {
        activeQuest = null;
        activeQuestId = null;
        currentState = ExecutorState.IDLE;
        questContext.clear();
        emergencyStop = false;
        currentRetries = 0;
        stepsCompleted = 0;
    }
    
    /**
     * Load quest script for given quest ID
     * Now includes our discovered quest scripts!
     */
    private QuestScript loadQuestScript(String questId) {
        switch (questId) {
            case "TUTORIAL_ISLAND":
                return new LemonTutQuest();
            case "VAMPIRE_SLAYER":
                return new VampireSlayerScript();
            case "COOKS_ASSISTANT":
                return new TreeQuestWrapper(new CooksAssistantTree(), questId, "Cook's Assistant");
            case "RUNE_MYSTERIES":
                return new TreeQuestWrapper(new RuneMysteriesTree(), questId, "Rune Mysteries");
            case "IMP_CATCHER":
                return new ImpCatcherScript();
            case "WITCHS_POTION":
                return new TreeQuestWrapper(new WitchsPotionTree(), questId, "Witch's Potion");
            case "ROMEO_AND_JULIET":
                return new TreeQuestWrapper(new RomeoAndJulietTree(), questId, "Romeo and Juliet");
            case "SHEEP_SHEARER":
                return new TreeQuestWrapper(new SheepShearerTree(), questId, "Sheep Shearer");
            case "X_MARKS_THE_SPOT":
                return new TreeQuestWrapper(new XMarksTheSpotTree(), questId, "X Marks the Spot");  
            case "THE_RESTLESS_GHOST":
                return new TreeQuestWrapper(new RestlessGhostTree(), questId, "The Restless Ghost");
            case "RESTLESS_GHOST": 
                // TODO: Implement Restless Ghost script
                log("Restless Ghost script not yet implemented");
                return null;
            default:
                log("No quest script available for: " + questId);
                return null;
        }
    }
    
    /**
     * Get current executor state
     */
    public ExecutorState getCurrentState() {
        return currentState;
    }
    
    /**
     * Get active quest ID
     */
    public String getActiveQuestId() {
        return activeQuestId;
    }
    
    /**
     * Get quest execution progress
     */
    public int getCurrentProgress() {
        if (activeQuest == null) return 0;
        return activeQuest.getCurrentProgress();
    }
    
    /**
     * Get execution statistics
     */
    public Map<String, Object> getExecutionStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("currentState", currentState.toString());
        stats.put("activeQuest", activeQuestId);
        stats.put("questsCompleted", questsCompleted);
        stats.put("stepsCompleted", stepsCompleted);
        stats.put("totalExecutionTime", formatTime(totalExecutionTime));
        stats.put("currentRetries", currentRetries);
        stats.put("maxRetries", maxRetries);
        return stats;
    }
    
    /**
     * Get execution log
     */
    public List<String> getExecutionLog() {
        return new ArrayList<>(executionLog);
    }
    
    /**
     * Add message to execution log - ENHANCED with file logging
     */
    private void log(String message) {
        String timestamp = String.format("[%02d:%02d:%02d] ", 
            Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
            Calendar.getInstance().get(Calendar.MINUTE),
            Calendar.getInstance().get(Calendar.SECOND));
        
        String logMessage = timestamp + message;
        executionLog.add(logMessage);
        
        // 1. Standard console output
        System.out.println("[QuestExecutor] " + logMessage);
        
        // 2. DreamBot script logging
        if (scriptReference != null) {
            scriptReference.log("[QuestExecutor] " + message);
        }
        
        // 3. DIRECT file logging for quest automation
        writeQuestExecutorLog(logMessage);
        
        // Keep log size manageable
        if (executionLog.size() > 500) {
            executionLog.remove(0);
        }
    }
    
    /**
     * Write QuestExecutor logs directly to file
     */
    private void writeQuestExecutorLog(String message) {
        try {
            String logDir = "quest_logs";
            java.io.File dir = new java.io.File(logDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = logDir + "/QUEST_EXECUTOR_" + timestamp + ".log";
            
            // Append to file
            try (java.io.FileWriter fw = new java.io.FileWriter(filename, true);
                 java.io.BufferedWriter bw = new java.io.BufferedWriter(fw)) {
                bw.write(message + "\n");
                bw.flush();
            }
            
        } catch (Exception e) {
            // Silent fail to prevent logging loops
        }
    }
    
    /**
     * Format milliseconds to readable time
     */
    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
    
    /**
     * Check if executor is busy
     */
    public boolean isBusy() {
        return currentState != ExecutorState.IDLE && currentState != ExecutorState.COMPLETED;
    }
    
    /**
     * Check if executor is actively running a quest
     */
    public boolean isActive() {
        return currentState == ExecutorState.EXECUTING || currentState == ExecutorState.PREPARING;
    }
    
    /**
     * Get current quest name
     */
    public String getCurrentQuestName() {
        if (activeQuestId != null) {
            QuestDatabase.QuestInfo info = QuestDatabase.getQuestInfo(activeQuestId);
            return info != null ? info.getDisplayName() : activeQuestId;
        }
        return "None";
    }
    
    /**
     * Get current step description
     */
    public String getCurrentStepDescription() {
        if (activeQuest != null) {
            return activeQuest.getCurrentStepDescription();
        }
        return "No active quest";
    }
    
    /**
     * Set quest context data
     */
    public void setContext(String key, Object value) {
        questContext.put(key, value);
    }
    
    /**
     * Get quest context data
     */
    public Object getContext(String key) {
        return questContext.get(key);
    }
    
    /**
     * WALKING STATE DETECTION: Check if player is currently walking or has movement pending
     * This prevents quest steps from interrupting walking actions
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
            return isCurrentlyMoving || hasDestination || walkingAnimation;
            
        } catch (Exception e) {
            // If we can't detect walking state, assume not walking to avoid infinite waits
            System.out.println("[QuestExecutor] Error detecting walking state: " + e.getMessage());
            return false;
        }
    }
}
