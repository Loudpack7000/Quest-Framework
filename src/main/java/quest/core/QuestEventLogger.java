package quest.core;

import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.container.impl.bank.Bank;
import org.dreambot.api.methods.container.impl.equipment.Equipment;
import org.dreambot.api.methods.container.impl.equipment.EquipmentSlot;
import org.dreambot.api.methods.dialogues.Dialogues;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.settings.PlayerSettings;
import org.dreambot.api.methods.widget.Widgets;
import org.dreambot.api.methods.tabs.Tab;
import org.dreambot.api.methods.tabs.Tabs;

import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.items.Item;
import org.dreambot.api.wrappers.widgets.WidgetChild;

// NEW: Import DreamBot's native quest API for proper quest detection
import org.dreambot.api.methods.quest.Quests;
import org.dreambot.api.methods.quest.book.Quest;
import org.dreambot.api.methods.quest.book.FreeQuest;
import org.dreambot.api.methods.quest.book.PaidQuest;

import org.dreambot.api.methods.quest.book.Quest.State;

// NEW: Import action detection system
import org.dreambot.api.script.listener.ActionListener;
import org.dreambot.api.wrappers.widgets.MenuRow;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Streamlined Quest Logger - Captures 7 Core Requirements Only
 * 1. Movement - Where you walk, with exact coordinates
 * 2. NPC Interactions - Who you talk to, what they say
 * 3. Dialogue Choices - Which options you select
 * 4. Object Interactions - Doors, chests, altars, etc.
 * 5. Inventory Changes - Items gained/lost/used
 * 6. Banking - Bank operations
 * 7. Animations - What actions you perform
 */
public class QuestEventLogger implements ActionListener {
    
    private final AbstractScript script;
    private BufferedWriter logWriter;
    private String currentQuest;
    private int stepCounter = 1;
    private boolean initialized = false;
    
    // State tracking for the 7 core requirements
    private String lastInventoryState = "";
    private boolean lastDialogueState = false;
    private boolean lastBankState = false;
    private int lastAnimation = -1;
    
    // REMOVED: Unused dialogue tracking variables - now handled by onAction() with mouse position
    
    // Spam reduction tracking
    private String lastInventoryChangeType = "";
    private boolean lastMovingState = false;
    
    // NEW: Action spam prevention for consumables
    private String lastActionLogged = "";
    private long lastActionLogTime = 0;
    private static final long ACTION_LOG_COOLDOWN = 3000; // 3 seconds between similar action logs
    
    // Removed unused movement logging variables after refactoring
    
    // Journey tracking for better movement logging
    private Tile journeyStartTile = null;
    private long journeyStartTime = 0;
    // Removed: lastPosition - unused after movement tracking refactor
    
    // NEW: Movement spam reduction
    private long lastMovementStartLog = 0;
    private long lastMovementStopLog = 0;
    private static final long MOVEMENT_LOG_COOLDOWN = 10000; // 10 seconds between movement start logs
    private static final double MIN_JOURNEY_DISTANCE = 25.0; // Increased from 15 to 25 tiles
    private static final double TELEPORT_DISTANCE_THRESHOLD = 500.0; // Reduced from 1000 to 500 for better detection
    
    // Quest varbit tracking for real-time progress monitoring
    private Map<Integer, Integer> questVarbitStates = new HashMap<>();
    private static final Map<String, Integer> QUEST_VARBITS = new HashMap<String, Integer>() {{
        // F2P Quest Varbits (verified IDs from community research)
        put("COOKS_ASSISTANT", 29);
        put("SHEEP_SHEARER", 179); 
        put("ROMEO_AND_JULIET", 144);
        put("THE_RESTLESS_GHOST", 107);
        put("IMP_CATCHER", 11);  // FIXED: Correct varbit for Imp Catcher
        put("DORICS_QUEST", 31);
        put("VAMPIRE_SLAYER", 178);
        put("DEMON_SLAYER", 2561);
        put("ERNEST_THE_CHICKEN", 32);
        put("GOBLIN_DIPLOMACY", 62);
        put("PIRATES_TREASURE", 71);
        put("PRINCE_ALI_RESCUE", 273);
        put("THE_KNIGHTS_SWORD", 122);
        put("BLACK_KNIGHTS_FORTRESS", 130);
        put("WITCHS_POTION", 67);
        put("RUNE_MYSTERIES", 63);
        put("DRAGON_SLAYER", 176);
    }};
    
    // Removed: questVarbits, lastVarbitValues, lastMovementTime, MOVEMENT_COOLDOWN - unused after consolidation
    
    // Removed: object interaction throttling variables - unused after ActionListener integration
    
    // Widget interaction tracking
    private String lastLoggedWidgetText = "";
    
    // Removed: comprehensive interaction tracking variables - unused after ActionListener integration
    private long lastTrackedInteractionTime = 0;
    
    // Quest completion detection cooldown (prevent level-up spam)
    private long lastQuestCompletionTime = 0;
    private static final long QUEST_COMPLETION_COOLDOWN = 10000; // 10 seconds between completion detections
    
    // Equipment change tracking using DreamBot Equipment API
    private Map<EquipmentSlot, String> lastEquipmentState = new HashMap<>();
    private long lastEquipmentChangeTime = 0;
    private static final long EQUIPMENT_CHANGE_COOLDOWN = 2000; // 2 seconds between equipment logs
    
    // NEW: Universal quest detection using DreamBot's native quest API
    private Quest currentActiveQuest = null;
    private Map<Quest, State> lastQuestStates = new HashMap<>();
    private long lastQuestCheckTime = 0;
    private static final long QUEST_CHECK_INTERVAL = 1000; // Check quest states every second
    
    // Magic tracking (consolidated)
    private boolean lastMagicTabState = false;
    private long lastSpellCastTime = 0;
    private static final long SPELL_CAST_COOLDOWN = 1000; // 1 second between spell logs
    
    // Combat tracking (consolidated)
    private boolean lastCombatState = false;
    
    // Prayer tracking (consolidated)
    private long lastPrayerChangeTime = 0;
    private static final long PRAYER_CHANGE_COOLDOWN = 1000; // 1 second between prayer logs
    
    // Grand Exchange tracking
    private boolean lastGEState = false;
    private long lastGEActionTime = 0;
    private static final long GE_ACTION_COOLDOWN = 3000; // 3 seconds between GE logs
    
    // Banking specific cooldowns to prevent spam (consolidated)
    private long lastBankCheckTime = 0;
    private long lastBankActionLogTime = 0;
    private static final long BANK_CHECK_COOLDOWN = 1000; // 1 second cooldown for checkBanking() execution
    private static final long BANK_ACTION_LOG_COOLDOWN = 5000; // Log bank open/close every 5 seconds
    
    // Run/Energy tracking
    private boolean lastRunState = false;
    private long lastRunToggleTime = 0;
    private static final long RUN_TOGGLE_COOLDOWN = 2000; // 2 seconds between run logs
    
    // Removed: unused tracking variables after refactoring
    
    public QuestEventLogger(AbstractScript script, String questName) {
        this.script = script;
        this.currentQuest = questName;
        initializeLogger();
        initializeStateTracking();
    }
    
    private void initializeLogger() {
        try {
            // Create quest_logs directory in the workspace
            String workspacePath = "C:\\Users\\Leone\\Desktop\\Projects in progress\\Dreambot Projects\\AI Quest system";
            java.io.File logDir = new java.io.File(workspacePath + "\\quest_logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
                script.log("Created quest logs directory at: " + logDir.getAbsolutePath());
            }
            
            // Create log file
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "QUEST_" + currentQuest.replaceAll("[^a-zA-Z0-9]", "_") + "_" + timestamp + ".log";
            String fullPath = logDir.getAbsolutePath() + "\\" + filename;
            
            logWriter = new BufferedWriter(new FileWriter(fullPath, true));
            
            // Write header
            logWriter.write("=====================================\n");
            logWriter.write("    QUEST SCRIPT RECREATION LOG     \n");
            logWriter.write("=====================================\n");
            logWriter.write("Quest: " + currentQuest + "\n");
            logWriter.write("Started: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
            logWriter.write("Player: " + (Players.getLocal() != null ? Players.getLocal().getName() : "Unknown") + "\n");
            if (Players.getLocal() != null) {
                logWriter.write("Starting Location: " + formatLocation(Players.getLocal().getTile()) + "\n");
                logWriter.write("Starting Inventory: " + getCurrentInventoryString() + "\n");
            }
            logWriter.write("=====================================\n\n");
            logWriter.flush();
            
            initialized = true;
            script.log("QUEST LOGGER: Initialized for " + currentQuest);
            script.log("LOG FILE: " + fullPath);
            
            // Enable DreamBot console integration
            setupConsoleIntegration();
            
            logAction("Quest recording started", "// Quest: " + currentQuest);
            
            // NEW: Initialize universal quest detection
            detectCurrentActiveQuest();
            
            // Re-enable varbit discovery for Free Discovery mode
            script.log("DEBUG: Current quest = '" + currentQuest + "'");
            if ("Free_Discovery".equals(currentQuest)) {
                script.log("FREE DISCOVERY MODE: Starting varbit discovery system");
                startVarbitDiscovery();
                script.log("FREE DISCOVERY MODE: Starting config discovery system");
                startConfigDiscovery();
            } else {
                script.log("DEBUG: Not Free Discovery mode, skipping varbit discovery");
            }
            
        } catch (Exception e) {
            script.log("QUEST LOGGER ERROR: Failed to initialize - " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void initializeStateTracking() {
        // Initialize tracking variables
        if (Players.getLocal() != null) {
            // Initialize tracking state
            lastAnimation = Players.getLocal().getAnimation();
            lastMovingState = Players.getLocal().isMoving();
        }
        lastInventoryState = getCurrentInventoryString();
        lastDialogueState = Dialogues.inDialogue();
        lastBankState = Bank.isOpen();
        
        script.log("QUEST LOGGER: State tracking initialized");
    }
    
    /**
     * ActionListener implementation - This captures ACTUAL user clicks and interactions
     * This is the KEY method that logs what the user actually selected, not just available actions
     */
    @Override
    public void onAction(MenuRow menuRow, int mouseX, int mouseY) {
        try {
            String action = menuRow.getAction();
            String targetName = menuRow.getObject(); // MenuRow.getObject() returns String, not Entity
            
            // Skip null/empty actions
            if (action == null || action.trim().isEmpty() || targetName == null || targetName.trim().isEmpty()) {
                return;
            }
            
            // Skip common spam actions
            if (action.equals("Walk here") || action.equals("Cancel") || action.equals("Examine")) {
                return;
            }
            
            // NEW: Consolidate consumable actions (Drink, Eat) to prevent spam
            if (action.equals("Drink") || action.equals("Eat")) {
                String actionKey = action + ":" + targetName;
                long currentTime = System.currentTimeMillis();
                
                // Only log if this exact action hasn't been logged recently
                if (!actionKey.equals(lastActionLogged) || (currentTime - lastActionLogTime > ACTION_LOG_COOLDOWN)) {
                    // SINGLE CONSOLIDATED LOG: Only one log entry for consumable usage
                    String actionDescription = "Consumed " + targetName;
                    String scriptCode = "Inventory.interact(\"" + targetName + "\", \"" + action + "\")";
                    
                    logAction(actionDescription, scriptCode);
                    
                    // Silent console log (no logDetail to avoid duplicate entries)
                    script.log("CONSUMABLE: " + actionDescription);
                    
                    // Mark this action as recently logged to prevent ALL duplicate logging
                    lastActionLogged = actionKey;
                    lastActionLogTime = currentTime;
                }
                
                            // IMPORTANT: Skip ALL additional checks for consumables to prevent spam
            // No config checks, no inventory checks, no other smart checks
            return;
        }
        
        // DIALOGUE OPTION DETECTION: Handled by state monitoring in checkDialogue()
        // No longer need to handle "Continue" actions here since we use direct API monitoring
        
        // Dialogue continuation handled by state monitoring in checkDialogue()
        
        // Get tile coordinates for NPCs and GameObjects
        String coordinateInfo = getTargetCoordinates(action, targetName, mouseX, mouseY);
        
        // Log the ACTUAL action that was performed (for non-consumables and non-dialogue-options)
        String actionDescription = "Selected '" + action + "' on " + targetName;
        String scriptCode = generateScriptCode(action, targetName, menuRow);
        
        logAction(actionDescription, scriptCode);
        logDetail("USER_ACTION", action + " | " + coordinateInfo);
        
        script.log("REAL ACTION DETECTED: " + action + " -> " + targetName);
        
        // SMART CONFIG CHECK: Check configs after quest-relevant actions
        if (isQuestRelevantAction(action, targetName)) {
            triggerConfigCheck("User action: " + action + " on " + targetName);
        }
        
        // SMART INVENTORY CHECK: Check inventory after inventory-relevant actions (but not for consumables - already handled above)
        if (isInventoryRelevantAction(action, targetName) && !action.equals("Drink") && !action.equals("Eat")) {
            smartInventoryCheck("User action: " + action + " on " + targetName);
        }
        
        // SMART EQUIPMENT CHECK: Check equipment after equipment-relevant actions
        if (isEquipmentRelevantAction(action, targetName)) {
            smartEquipmentCheck("User action: " + action + " on " + targetName);
        }
        
        // SMART BANKING CHECK: Check banking after banking-relevant actions
        if (isBankingRelevantAction(action, targetName)) {
            smartBankingCheck("User action: " + action + " on " + targetName);
        }
        
        // SMART COMBAT CHECK: Check combat after combat-relevant actions
        if (isCombatRelevantAction(action, targetName)) {
            smartCombatCheck("User action: " + action + " on " + targetName);
        }
        
        // SMART PRAYER CHECK: Check prayers after prayer-relevant actions
        if (isPrayerRelevantAction(action, targetName)) {
            smartPrayerCheck("User action: " + action + " on " + targetName);
        }
        
        // SMART MAGIC CHECK: Check magic after magic-relevant actions
        if (isMagicRelevantAction(action, targetName)) {
            smartMagicCheck("User action: " + action + " on " + targetName);
        }
        
        } catch (Exception e) {
            // Silent catch to prevent spam
        }
    }
    
    // REMOVED: Conflicting dialogue detection method - now handled by onAction() with mouse position
    
    /**
     * Log dialogue option by text (used by the working dialogue detection system)
     */
    public void logSelectedDialogueOption(String optionText) {
        try {
            if (optionText != null && !optionText.trim().isEmpty()) {
                script.log("DIALOGUE OPTION SELECTED: " + optionText);
                logAction("DIALOGUE_SELECTION", optionText);
            }
        } catch (Exception e) {
            script.log("Error logging dialogue option: " + e.getMessage());
        }
    }
    
    // Dialogue option tracking state
    private String[] lastDialogueOptions = null;
    private long lastDialogueOptionsTime = 0;
    private static final long DIALOGUE_OPTION_TIMEOUT = 5000; // 5 seconds to detect option selection
    
    /**
     * Detect dialogue option selection by monitoring dialogue state changes
     */
    private void detectDialogueOptionSelection(String[] currentOptions) {
        long currentTime = System.currentTimeMillis();
        
        // Store the current options for comparison
        if (lastDialogueOptions == null || !Arrays.equals(lastDialogueOptions, currentOptions)) {
            lastDialogueOptions = currentOptions.clone();
            lastDialogueOptionsTime = currentTime;
        }
    }
    
    /**
     * Check for dialogue option selection by monitoring when options disappear
     */
    private void checkDialogueOptionSelection() {
        if (lastDialogueOptions == null) return;
        
        long currentTime = System.currentTimeMillis();
        
        // Check if dialogue options are no longer available (user made a selection)
        if (!Dialogues.areOptionsAvailable() && (currentTime - lastDialogueOptionsTime < DIALOGUE_OPTION_TIMEOUT)) {
            // Try to determine which option was selected by checking the current dialogue text
            String selectedOption = determineSelectedOptionFromDialogue();
            
            if (selectedOption != null) {
                logSelectedDialogueOption(selectedOption);
            } else {
                // Fallback: log all available options
                logAction("DIALOGUE_OPTION_SELECTED", "// User selected one of: " + Arrays.toString(lastDialogueOptions));
            }
            
            // Clear the stored options
            lastDialogueOptions = null;
        }
        
        // Clear old options if timeout exceeded
        if (currentTime - lastDialogueOptionsTime > DIALOGUE_OPTION_TIMEOUT) {
            lastDialogueOptions = null;
        }
    }
    
    /**
     * Determine which dialogue option was selected by checking the current dialogue text
     */
    private String determineSelectedOptionFromDialogue() {
        try {
            // Get the current dialogue text
            String currentDialogue = Dialogues.getNPCDialogue();
            if (currentDialogue == null || currentDialogue.trim().isEmpty()) {
                return null;
            }
            
            // Compare with stored options to see which one matches
            for (String option : lastDialogueOptions) {
                if (option != null && currentDialogue.contains(option)) {
                    return option; // Found the selected option
                }
            }
            
            // If no exact match, try partial matching
            for (String option : lastDialogueOptions) {
                if (option != null) {
                    // Split option into words and check if most words match
                    String[] optionWords = option.split("\\s+");
                    int matchCount = 0;
                    for (String word : optionWords) {
                        if (word.length() > 2 && currentDialogue.toLowerCase().contains(word.toLowerCase())) {
                            matchCount++;
                        }
                    }
                    // If more than 50% of words match, consider it a match
                    if (matchCount > optionWords.length / 2) {
                        return option;
                    }
                }
            }
            
            return null; // Could not determine which option was selected
        } catch (Exception e) {
            script.log("Error determining selected dialogue option: " + e.getMessage());
            return null;
        }
    }
    
    // REMOVED: Unused widget logging method - not needed for dialogue selection
    

    
    /**
     * Determine if an action is quest-relevant and should trigger config checking
     */
    private boolean isQuestRelevantAction(String action, String target) {
        // NPC interactions (dialogue, trading, etc.)
        if (action.equals("Talk-to") || action.equals("Trade") || action.equals("Attack")) {
            return true;
        }
        
        // Object interactions (doors, chests, altars, etc.)
        if (action.equals("Open") || action.equals("Close") || action.equals("Search") || 
            action.equals("Use") || action.equals("Climb-up") || action.equals("Climb-down")) {
            return true;
        }
        
        // Item usage (quest items, tools, etc.) - EXCLUDING routine consumables
        if (action.equals("Use") || action.equals("Drop") || action.equals("Wield") || action.equals("Wear")) {
            return true;
        }
        
        // NOTE: "Eat" and "Drink" are NOT quest-relevant - they're routine consumables
        // These should not trigger config checks to prevent spam
        
        return false;
    }
    
    /**
     * Determine if an action should trigger inventory checking
     */
    private boolean isInventoryRelevantAction(String action, String target) {
        // Actions that definitely change inventory
        return action.equals("Use") || action.equals("Drop") || action.equals("Eat") || 
               action.equals("Drink") || action.equals("Wield") || action.equals("Wear") ||
               action.equals("Bank") || action.equals("Deposit") || action.equals("Withdraw");
    }
    
    /**
     * Determine if an action should trigger equipment checking  
     */
    private boolean isEquipmentRelevantAction(String action, String target) {
        // Actions that change equipment
        return action.equals("Wield") || action.equals("Wear") || action.equals("Remove") ||
               action.equals("Bank") || action.equals("Deposit") || action.equals("Withdraw");
    }
    
    /**
     * Determine if an action should trigger banking checks
     */
    private boolean isBankingRelevantAction(String action, String target) {
        // Banking-related actions
        return action.equals("Bank") || action.equals("Deposit") || action.equals("Withdraw") ||
               target.toLowerCase().contains("banker") || target.toLowerCase().contains("bank");
    }
    
    /**
     * Determine if an action should trigger combat checks
     */
    private boolean isCombatRelevantAction(String action, String target) {
        // Combat-related actions
        return action.equals("Attack") || action.equals("Fight") || action.equals("Cast") ||
               target.toLowerCase().contains("guard") || target.toLowerCase().contains("warrior") ||
               target.toLowerCase().contains("goblin") || target.toLowerCase().contains("rat");
    }
    
    /**
     * Determine if an action should trigger prayer checks
     */
    private boolean isPrayerRelevantAction(String action, String target) {
        // Prayer-related actions
        return action.equals("Pray-at") || action.equals("Pray") || action.equals("Bury") ||
               target.toLowerCase().contains("altar") || target.toLowerCase().contains("bones") ||
               target.toLowerCase().contains("prayer");
    }
    
    /**
     * Determine if an action should trigger magic checks
     */
    private boolean isMagicRelevantAction(String action, String target) {
        // Magic-related actions
        return action.equals("Cast") || action.equals("Teleport") || action.equals("Enchant") ||
               target.toLowerCase().contains("spell") || target.toLowerCase().contains("rune") ||
               target.toLowerCase().contains("staff");
    }
    
    /**
     * Generate appropriate script code based on the actual action performed
     */
    private String generateScriptCode(String action, String target, MenuRow menuRow) {
        // Clean target name for script generation
        String cleanTarget = target.replaceAll("<[^>]*>", "").trim(); // Remove HTML tags
        
        // GameObject interactions
        if (action.equals("Climb-up") || action.equals("Climb-down") || action.equals("Climb")) {
            return "GameObjects.closest(\"" + cleanTarget + "\").interact(\"" + action + "\");";
        }
        
        if (action.equals("Open") || action.equals("Close")) {
            return "GameObjects.closest(\"" + cleanTarget + "\").interact(\"" + action + "\");";
        }
        
        if (action.equals("Search") || action.equals("Use")) {
            return "GameObjects.closest(\"" + cleanTarget + "\").interact(\"" + action + "\");";
        }
        
        // NPC interactions
        if (action.equals("Talk-to") || action.equals("Attack") || action.equals("Trade")) {
            return "NPCs.closest(\"" + cleanTarget + "\").interact(\"" + action + "\");";
        }
        
        // Item interactions
        if (action.equals("Drop") || action.equals("Eat") || action.equals("Drink") || action.equals("Wield") || action.equals("Wear")) {
            return "Inventory.interact(\"" + cleanTarget + "\", \"" + action + "\");";
        }
        
        // Banking actions
        if (action.equals("Deposit") || action.equals("Withdraw")) {
            return "// Bank." + action.toLowerCase() + "(\"" + cleanTarget + "\");";
        }
        
        // Generic fallback
        return "// " + action + " on " + cleanTarget;
    }
    
    /**
     * Get target coordinates - show tile coordinates for NPCs/GameObjects only
     */
    private String getTargetCoordinates(String action, String targetName, int mouseX, int mouseY) {
        try {
            // For NPC interactions, get the NPC's tile coordinates
            if (action.equals("Talk-to") || action.equals("Attack") || action.equals("Trade") || 
                action.equals("Follow") || action.equals("Examine")) {
                
                NPC targetNPC = NPCs.closest(npc -> npc != null && npc.getName() != null && 
                                            npc.getName().equals(targetName));
                if (targetNPC != null) {
                    Tile npcTile = targetNPC.getTile();
                    return "NPC_Tile: (" + npcTile.getX() + ", " + npcTile.getY() + ", " + npcTile.getZ() + ")";
                }
            }
            
            // For GameObject interactions, get the object's tile coordinates
            if (action.equals("Climb-up") || action.equals("Climb-down") || action.equals("Climb") ||
                action.equals("Open") || action.equals("Close") || action.equals("Search") || 
                action.equals("Use") || action.equals("Enter") || action.equals("Exit")) {
                
                GameObject targetObject = GameObjects.closest(obj -> obj != null && obj.getName() != null && 
                                                             obj.getName().equals(targetName));
                if (targetObject != null) {
                    Tile objTile = targetObject.getTile();
                    return "Object_Tile: (" + objTile.getX() + ", " + objTile.getY() + ", " + objTile.getZ() + ")";
                }
            }
            
            // For everything else (inventory items, spells, etc.), no coordinates needed
            return "Target: " + targetName;
            
        } catch (Exception e) {
            // Fallback
            return "Target: " + targetName;
        }
    }
    
    // Debug counter
    private int debugCounter = 0;
    
    /**
     * SMART CONFIG MONITORING - Only check when "trigger events" happen
     * Instead of constant polling, only check configs when something quest-related occurs
     */
    private void smartConfigCheck(String triggerReason) {
        if (!configDiscoveryActive) return;
        
        // Only check configs when triggered by meaningful events
        script.log("SMART CONFIG CHECK: Triggered by " + triggerReason);
        checkConfigChanges();
        checkVarbitChanges();
    }
    
    /**
     * Trigger config checks only when quest-relevant events occur
     */
    private void triggerConfigCheck(String reason) {
        smartConfigCheck(reason);
    }
    
    /**
     * SMART INVENTORY CHECK - Only check when inventory-affecting actions occur
     */
    private void smartInventoryCheck(String triggerReason) {
        script.log("SMART INVENTORY CHECK: Triggered by " + triggerReason);
        checkInventory();
    }
    
    /**
     * SMART EQUIPMENT CHECK - Only check when equipment-affecting actions occur
     */
    private void smartEquipmentCheck(String triggerReason) {
        script.log("SMART EQUIPMENT CHECK: Triggered by " + triggerReason);
        checkEquipmentChanges();
    }
    
    /**
     * SMART BANKING CHECK - Only check when banking-affecting actions occur
     */
    private void smartBankingCheck(String triggerReason) {
        // Silent banking check - the main action log already captures banking interactions
        checkBanking();
    }
    
    /**
     * SMART COMBAT CHECK - Only check when combat-affecting actions occur
     */
    private void smartCombatCheck(String triggerReason) {
        script.log("SMART COMBAT CHECK: Triggered by " + triggerReason);
        checkCombat();
    }
    
    /**
     * SMART PRAYER CHECK - Only check when prayer-affecting actions occur
     */
    private void smartPrayerCheck(String triggerReason) {
        script.log("SMART PRAYER CHECK: Triggered by " + triggerReason);
        checkPrayers();
    }
    
    /**
     * SMART MAGIC CHECK - Only check when magic-affecting actions occur
     */
    private void smartMagicCheck(String triggerReason) {
        script.log("SMART MAGIC CHECK: Triggered by " + triggerReason);
        checkSpellCasting();
    }
    public void checkForEvents() {
        if (!initialized) {
            script.log("QUEST LOGGER: Not initialized yet");
            return;
        }
        
        try {
            // Debug: Show we're checking (reduced frequency for less spam)
            debugCounter++;
            if (debugCounter % 1000 == 0) { // Every ~100 seconds (1000 * 100ms)
                script.log("QUEST LOGGER: SMART EVENT-DRIVEN Active - Smart triggers enabled (check #" + debugCounter + ")");
            }
            
            // PRIORITY 1: IMMEDIATE DETECTION (every loop ~100ms)
            // Only essential real-time monitoring that can't be triggered by events
            
            // Critical: Quest Progress Tracking (progression detection)
            checkQuestProgress();
            
            // Critical: NPC Interactions and Dialogue Choices (user input)
            checkDialogue();
            
            // NEW: Check for dialogue option selection
            checkDialogueOptionSelection();
            
            // NOTE: All other systems now use smart event-driven triggers:
            // - Inventory: Only checked when items are used/moved via ActionListener
            // - Equipment: Only checked when gear is equipped/removed via ActionListener  
            // - Banking: Only checked when banking actions occur via ActionListener
            // - Combat: Only checked when attack actions occur via ActionListener
            // - Prayer: Only checked when prayer actions occur via ActionListener
            // - Magic: Only checked when spell actions occur via ActionListener
            
            // PRIORITY 2: FREQUENT DETECTION (every 3 loops ~300ms)
            if (debugCounter % 3 == 0) {
                // Movement tracking (less critical, can be slightly delayed)
                checkMovement();
                
                // Animations (action confirmation)
                checkAnimations();
            }
            
            // PRIORITY 3: MODERATE DETECTION (every 5 loops ~500ms)
            if (debugCounter % 5 == 0) {
                // COMPREHENSIVE Object Interactions (with proper action detection)
                checkComprehensiveInteractions();
            }
            
            // PRIORITY 4: LOW FREQUENCY (every 10 loops ~1 second)
            if (debugCounter % 10 == 0) {
                // Grand Exchange Trading (interface-based)
                checkGrandExchange();
                
                // Run/Energy Management (state-based)
                checkRunState();
            }
            
        } catch (Exception e) {
            script.log("QUEST LOGGER ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // 0. COMPREHENSIVE INTERACTION DETECTION - DISABLED (ActionListener now handles real clicks)
    private void checkComprehensiveInteractions() {
        // DISABLED: All proximity-based and animation-based guessing removed
        // We now have ActionListener capturing actual user clicks with exact actions
        // No need for proximity detection, animation guessing, or state-based inference
        
        // Only keep essential Grand Exchange detection if needed
        long currentTime = System.currentTimeMillis();
        try {
            // Grand Exchange interface detection (only if truly needed)
            if (Widgets.isVisible(465)) { // Grand Exchange interface
                if (currentTime - lastTrackedInteractionTime > 5000) { // Prevent spam
                    logAction("Grand Exchange accessed", 
                        "// GrandExchange.open() or similar interaction");
                    logDetail("GE_INTERACTION", "Grand Exchange interface opened");
                    lastTrackedInteractionTime = currentTime;
                    
                    script.log("GRAND EXCHANGE INTERACTION DETECTED");
                }
            }
            
                    // Bank interface detection - DISABLED (ActionListener handles bank interactions)
            // The ActionListener already captures "Bank" clicks with proper context
            // No need for duplicate interface-based detection
        } catch (Exception e) {
            // Silent catch to prevent spam
        }
    }
    
    // Enhanced detection methods
    // Removed: detectAnimationBasedInteraction() - unused after ActionListener integration
    
    // Removed: detectMovementBasedInteraction() - unused after ActionListener integration
    
    // Removed: detectStateBasedInteraction() - unused after ActionListener integration
    
    // Helper method to determine the most likely GameObject action - ENHANCED
    private String determineObjectAction(GameObject obj) {
        String[] actions = obj.getActions();
        if (actions != null && actions.length > 0) {
            // Return the first valid action from the object's available actions
            for (String action : actions) {
                if (action != null && !action.trim().isEmpty() && !action.equals("null")) {
                    return action; // This should capture "Climb-up", "Climb-down", "Open", etc.
                }
            }
        }
        
        // Enhanced fallback based on object name patterns for specific quest objects
        String name = obj.getName().toLowerCase();
        
        // Stairs and ladders - direction-specific
        if (name.contains("stair") || name.contains("step")) {
            if (name.contains("up")) return "Climb-up";
            if (name.contains("down")) return "Climb-down";
            return "Climb"; // Generic climb if direction unknown
        }
        if (name.contains("ladder")) {
            if (name.contains("up")) return "Climb-up";
            if (name.contains("down")) return "Climb-down";
            return "Climb"; // Generic climb if direction unknown
        }
        
        // Doors and gates
        if (name.contains("door") || name.contains("gate")) return "Open";
        
        // Containers
        if (name.contains("chest") || name.contains("coffin") || name.contains("crate")) return "Open";
        if (name.contains("barrel") || name.contains("sack")) return "Search";
        
        // Interactive objects
        if (name.contains("bank")) return "Bank";
        if (name.contains("altar")) return "Pray-at";
        if (name.contains("anvil")) return "Smith";
        if (name.contains("furnace")) return "Use";
        if (name.contains("range") || name.contains("fire")) return "Cook";
        if (name.contains("well")) return "Use";
        if (name.contains("lever")) return "Pull";
        if (name.contains("button")) return "Press";
        
        // Resources
        if (name.contains("tree")) return "Chop down";
        if (name.contains("rock")) return "Mine";
        
        return "Interact"; // Generic fallback
    }
    
    // Removed: determineNPCAction() - unused after ActionListener integration
    
    // Helper method to format GameObject interactions for script generation
    private String formatGameObjectInteraction(GameObject obj, String action) {
        return String.format("GameObjects.closest(\"%s\").interact(\"%s\")", 
                            obj.getName(), action);
    }
    
    // Removed: formatNPCInteraction() - unused after ActionListener integration
    
    // 1. Movement Detection - Journey-Based Tracking (REDUCED VERBOSITY)
    private void checkMovement() {
        if (Players.getLocal() == null) return;
        
        Tile currentPos = Players.getLocal().getTile();
        boolean currentlyMoving = Players.getLocal().isMoving();
        long currentTime = System.currentTimeMillis();
        
        // Movement state changes (starting/stopping movement) - WITH COOLDOWNS
        if (lastMovingState != currentlyMoving) {
            if (currentlyMoving) {
                // Started moving - ONLY log if significant time has passed since last movement start
                if (currentTime - lastMovementStartLog > MOVEMENT_LOG_COOLDOWN) {
                    journeyStartTile = currentPos;
                    journeyStartTime = currentTime;
                    logDetail("Movement", "Started significant journey from " + formatLocation(currentPos));
                    lastMovementStartLog = currentTime;
                }
                // Always update journey tracking even if not logged
                if (journeyStartTile == null) {
                    journeyStartTile = currentPos;
                    journeyStartTime = currentTime;
                }
            } else {
                // Stopped moving - CONSOLIDATED logging with distance filtering
                if (journeyStartTile != null && currentPos != null) {
                    double journeyDistance = journeyStartTile.distance(currentPos);
                    long journeyTime = currentTime - journeyStartTime;
                    
                    // ONLY log significant journeys to reduce spam
                    if (journeyDistance >= MIN_JOURNEY_DISTANCE && journeyDistance <= TELEPORT_DISTANCE_THRESHOLD) {
                        // Significant walking journey
                        logAction("Completed journey: " + String.format("%.0f", journeyDistance) + " tiles", 
                            "Walking.walk(new Tile(" + currentPos.getX() + ", " + currentPos.getY() + ", " + currentPos.getZ() + "))");
                        logDetail("Journey", "Walked " + String.format("%.0f", journeyDistance) + " tiles in " + (journeyTime/1000) + "s | " + 
                            formatLocation(journeyStartTile) + " → " + formatLocation(currentPos));
                    }
                    // Log teleports/cutscenes differently (less frequent but important)
                    else if (journeyDistance > TELEPORT_DISTANCE_THRESHOLD) {
                        logAction("Teleport/Cutscene detected: " + String.format("%.0f", journeyDistance) + " tiles", 
                            "// Large distance moved - likely teleport or cutscene");
                        logDetail("Teleport", "Moved " + String.format("%.0f", journeyDistance) + " tiles | " + 
                            formatLocation(journeyStartTile) + " → " + formatLocation(currentPos));
                    }
                    // For distances < MIN_JOURNEY_DISTANCE, don't log journey completion to reduce spam
                }
                
                // ALWAYS log final position (but only when cooldown allows)
                if (currentPos != null && currentTime - lastMovementStopLog > 5000) { // 5 second cooldown for position updates
                    logDetail("Position", "Arrived at " + formatLocation(currentPos));
                    lastMovementStopLog = currentTime;
                }
                
                // Reset for next journey
                journeyStartTile = null;
                
                // Check what they stopped near (reduced frequency)
                if (currentTime - lastMovementStopLog > 5000) {
                    checkNearbyTargets();
                }
            }
            lastMovingState = currentlyMoving;
        }
        
        // Position tracking handled by journey system
    }
    
    // 2 & 3. NPC Interactions and Dialogue - State-based option detection
    private void checkDialogue() {
        boolean currentDialogueState = Dialogues.inDialogue();
        
        // Track dialogue state changes for quest progression
        if (currentDialogueState != lastDialogueState) {
            if (!currentDialogueState) {
                // Dialogue ended - trigger quest progress check
                triggerConfigCheck("Dialogue ended");
            }
            lastDialogueState = currentDialogueState;
        }
        
        // Direct dialogue option detection using DreamBot API
        if (currentDialogueState && Dialogues.areOptionsAvailable()) {
            String[] options = Dialogues.getOptions();
            if (options != null && options.length > 0) {
                // Store options for detection
                detectDialogueOptionSelection(options);
            }
        }
    }
    
    // Removed: checkObjectInteractions() - unused after ActionListener integration
    
    private void checkNearbyTargets() {
        // DISABLED: No more guessing nearby object interactions since ActionListener captures real clicks
        // This method was causing logs like "Likely interacted with object" which are unnecessary
    }
    
    // Removed: isQuestRelevant() - unused after refactoring
    
    // 5. Inventory Changes - Enhanced Item Usage Detection (REDUCED SPAM)
    private void checkInventory() {
        String currentInventoryState = getCurrentInventoryString();
        
        if (!currentInventoryState.equals(lastInventoryState)) {
            String changeType = analyzeInventoryChange(lastInventoryState, currentInventoryState);
            
            // Enhanced item usage detection with spam filtering
            if (changeType.contains("Items lost/used")) {
                // Check if it's item consumption (potion drinking, food eating, etc.)
                String consumedItem = detectItemConsumption(lastInventoryState, currentInventoryState);
                if (consumedItem != null) {
                    // REDUCED LOGGING: Only log if not already logged by onAction
                    long currentTime = System.currentTimeMillis();
                    String actionKey = "Drink:" + consumedItem;
                    boolean alreadyLogged = actionKey.equals(lastActionLogged) && 
                                          (currentTime - lastActionLogTime < ACTION_LOG_COOLDOWN);
                    
                    if (!alreadyLogged) {
                        // Log consumption (but this should be rare now due to onAction handling)
                        logAction("Item consumed: " + consumedItem, 
                            "Inventory.getItem(\"" + consumedItem + "\").interact(\"Drink\") // or click");
                        logDetail("ITEM_CONSUMPTION", "Player consumed: " + consumedItem);
                    }
                    // Skip the detailed inventory change logging for consumables to reduce spam
                } else {
                    // Only log non-repetitive inventory changes
                    if (!changeType.equals(lastInventoryChangeType)) {
                        logAction("Inventory changed: " + changeType, 
                            "// Current inventory: " + currentInventoryState);
                        lastInventoryChangeType = changeType;
                    }
                }
            } else {
                // Only log significant inventory changes
                if (!changeType.equals(lastInventoryChangeType) && 
                    !changeType.contains("No significant changes")) {
                    logAction("Inventory changed: " + changeType, 
                        "// Current inventory: " + currentInventoryState);
                    lastInventoryChangeType = changeType;
                }
            }
            
            // REDUCED DETAIL LOGGING: Only log if not a recent consumable action
            long currentTime = System.currentTimeMillis();
            boolean isRecentConsumableAction = (currentTime - lastActionLogTime < ACTION_LOG_COOLDOWN) && 
                                              (lastActionLogged.startsWith("Drink:") || lastActionLogged.startsWith("Eat:"));
            
            if (!changeType.equals(lastInventoryChangeType) && !isRecentConsumableAction) {
                logDetail("Inventory Change", changeType + " - New state: " + currentInventoryState);
            }
            lastInventoryState = currentInventoryState;
        }
    }
    
    // 6. Banking - ANTI-SPAM with dedicated cooldowns
    private void checkBanking() {
        long currentTime = System.currentTimeMillis();
        
        // Only proceed if enough time has passed since the last execution of this method's logic
        if (currentTime - lastBankCheckTime < BANK_CHECK_COOLDOWN) {
            return; // Too soon, skip this check
        }

        boolean currentBankState = Bank.isOpen();

        if (currentBankState != lastBankState) {
            // Only log if it's been long enough since the last logged state change
            if (currentTime - lastBankActionLogTime > BANK_ACTION_LOG_COOLDOWN) {
                if (!currentBankState) {
                    logAction("Closed bank (state changed)", "Bank.close()");
                    logDetail("Banking", "Bank interface closed");
                }
                lastBankActionLogTime = currentTime; // Update the specific action log cooldown
            }
            lastBankState = currentBankState; // Always update the last state regardless of logging
        }
        lastBankCheckTime = currentTime; // Update method execution cooldown
    }
    
    // 7. Animations - MOSTLY DISABLED (ActionListener now captures real actions)
    private void checkAnimations() {
        // MOSTLY DISABLED: Only log significant animations to reduce spam
        // ActionListener captures the actual user actions, so animation logging is mostly redundant
        
        if (Players.getLocal() == null) return;
        
        int currentAnimation = Players.getLocal().getAnimation();
        
        // SPAM REDUCTION: Only log significant animations, not routine ones
        if (currentAnimation != lastAnimation && currentAnimation > 0) {
            // Only log specific quest-relevant animations (teleports, major actions)
            if (isSignificantAnimation(currentAnimation)) {
                String animationDescription = getAnimationDescription(currentAnimation);
                logDetail("Animation", animationDescription + " (ID: " + currentAnimation + ")");
            }
        }
        
        lastAnimation = currentAnimation;
    }
    
    /**
     * Check if an animation is significant enough to log (reduces spam)
     */
    private boolean isSignificantAnimation(int animationId) {
        // Only log significant animations - teleports, major quest actions, etc.
        switch (animationId) {
            case 708:   // Teleport casting
            case 1816:  // Death animation
            case 2108:  // Quest completion animation
            case 3864:  // Levelup animation
                return true;
            default:
                return false; // Skip routine animations (walking, drinking, eating, etc.)
        }
    }
    
    // Dialogue detection now handled by onAction() with mouse position
    
    private void checkEquipmentChanges() {
        try {
            boolean equipmentChanged = false;
            StringBuilder changes = new StringBuilder();
            
            // Check all equipment slots using DreamBot Equipment API
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                Item currentItem = Equipment.getItemInSlot(slot);
                String currentItemName = (currentItem != null) ? currentItem.getName() : "Empty";
                String lastItemName = lastEquipmentState.get(slot);
                
                // Initialize if this is the first check
                if (lastItemName == null) {
                    lastEquipmentState.put(slot, currentItemName);
                    continue;
                }
                
                // Check for changes
                if (!currentItemName.equals(lastItemName)) {
                    equipmentChanged = true;
                    
                    // Build change description
                    if (changes.length() > 0) {
                        changes.append(", ");
                    }
                    
                    String slotName = slot.name().toLowerCase();
                    if ("Empty".equals(currentItemName)) {
                        changes.append(slotName).append(": removed ").append(lastItemName);
                    } else if ("Empty".equals(lastItemName)) {
                        changes.append(slotName).append(": equipped ").append(currentItemName);
                    } else {
                        changes.append(slotName).append(": ").append(lastItemName).append(" → ").append(currentItemName);
                    }
                    
                    // Update last known state
                    lastEquipmentState.put(slot, currentItemName);
                }
            }
            
            // Log equipment changes with cooldown to prevent spam
            if (equipmentChanged) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastEquipmentChangeTime > EQUIPMENT_CHANGE_COOLDOWN) {
                    logAction("Equipment changed: " + changes.toString(), 
                        "// Equipment slots updated: " + changes.toString());
                    logDetail("EQUIPMENT_CHANGE", "Equipment modified: " + changes.toString());
                    
                    // Console debug output
                    script.log("EQUIPMENT CHANGE DETECTED: " + changes.toString());
                    
                    lastEquipmentChangeTime = currentTime;
                }
            }
            
        } catch (Exception e) {
            // Silent catch to prevent spam - equipment checking is non-critical
            script.log("Equipment check error (non-critical): " + e.getMessage());
        }
    }
    
    // Helper Methods
    private String getCurrentInventoryString() {
        List<Item> items = Inventory.all();
        
        if (items.isEmpty()) {
            return "Empty";
        }
        
        Map<String, Integer> itemCounts = new HashMap<>();
        for (Item item : items) {
            if (item != null) {
                itemCounts.put(item.getName(), itemCounts.getOrDefault(item.getName(), 0) + item.getAmount());
            }
        }
        
        StringBuilder inv = new StringBuilder();
        for (Map.Entry<String, Integer> entry : itemCounts.entrySet()) {
            if (inv.length() > 0) inv.append(", ");
            inv.append(entry.getKey());
            if (entry.getValue() > 1) {
                inv.append(" x").append(entry.getValue());
            }
        }
        
        return inv.toString();
    }
    
    // Enhanced item consumption detection - Fixed to exclude equipped items AND banking
    private String detectItemConsumption(String oldInventory, String newInventory) {
        // NEW: Skip consumption detection if we just logged a consumable action
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastActionLogTime < ACTION_LOG_COOLDOWN && 
            (lastActionLogged.startsWith("Drink:") || lastActionLogged.startsWith("Eat:"))) {
            return null; // Don't log again - onAction already handled it
        }
        
        // Only detect actual consumables - potions and food that leave behind items when used
        
        // Look for items that had quantity reduced (food, consumables, etc.)
        // Removed: Energy potion detection - no longer needed
        
        // Add other consumables that leave behind evidence
        if (oldInventory.contains("Bread") && !newInventory.contains("Bread")) {
            return "Bread"; // Food consumption
        }
        
        // Enhanced logic: For items that completely disappear, be more selective
        String[] oldItems = oldInventory.split(", ");
        
        for (String oldItem : oldItems) {
            String itemName = oldItem.split(" x")[0]; // Get item name without quantity
            
            // Check if item completely disappeared from inventory
            if (!newInventory.contains(itemName)) {
                
                // Skip common items that are typically deposited, not consumed
                if (isLikelyDepositedNotConsumed(itemName)) {
                    continue; // Skip this item - likely deposited to bank
                }
                
                // Check if the item was equipped instead of consumed
                boolean itemIsEquipped = false;
                try {
                    for (EquipmentSlot slot : EquipmentSlot.values()) {
                        Item equippedItem = Equipment.getItemInSlot(slot);
                        if (equippedItem != null && equippedItem.getName().equals(itemName)) {
                            itemIsEquipped = true;
                            break;
                        }
                    }
                } catch (Exception e) {
                    // Silent catch - if equipment check fails, assume it wasn't equipped
                }
                
                // Only log as consumed if it wasn't equipped AND it's likely a consumable AND we haven't just logged it
                if (!itemIsEquipped && isLikelyConsumable(itemName)) {
                    // Double-check we haven't just logged this exact item
                    String actionKey = "Drink:" + itemName;
                    if (!actionKey.equals(lastActionLogged) || (currentTime - lastActionLogTime > ACTION_LOG_COOLDOWN)) {
                        return itemName;
                    }
                }
            }
        }
        
        return null; // No clear consumption detected
    }
    
    /**
     * Unified item classification system - consolidates all item type detection
     */
    private enum ItemType {
        CONSUMABLE, BANKABLE, EQUIPMENT, QUEST_ITEM, CURRENCY, OTHER
    }
    
    private ItemType getItemType(String itemName) {
        if (itemName == null) return ItemType.OTHER;
        
        String lower = itemName.toLowerCase();
        
        // Consumables (food, potions, etc.)
        if (lower.contains("potion") || lower.contains("food") || lower.contains("bread") ||
            lower.contains("fish") || lower.contains("meat") || lower.contains("cake") ||
            lower.contains("pie") || lower.contains("beer") || lower.contains("wine") ||
            lower.contains("tea")) {
            return ItemType.CONSUMABLE;
        }
        
        // Equipment
        if (lower.contains("armor") || lower.contains("weapon") || lower.contains("helm") ||
            lower.contains("shield") || lower.contains("sword") || lower.contains("bow") ||
            lower.contains("staff") || lower.contains("robe") || lower.contains("boots") ||
            lower.contains("gloves")) {
            return ItemType.EQUIPMENT;
        }
        
        // Currency
        if (lower.contains("coin") || lower.contains("gp") || lower.contains("gold")) {
            return ItemType.CURRENCY;
        }
        
        // Bankable items (resources, etc.)
        if (lower.contains("clay") || lower.contains("ore") || lower.contains("bar") ||
            lower.contains("rune") || lower.contains("gem") || lower.contains("log") ||
            lower.contains("raw ") || lower.contains("cooked ")) {
            return ItemType.BANKABLE;
        }
        
        return ItemType.OTHER;
    }
    
    // Helper methods using the unified system
    private boolean isLikelyDepositedNotConsumed(String itemName) {
        ItemType type = getItemType(itemName);
        return type == ItemType.BANKABLE || type == ItemType.CURRENCY || type == ItemType.EQUIPMENT;
    }
    
    private boolean isLikelyConsumable(String itemName) {
        return getItemType(itemName) == ItemType.CONSUMABLE;
    }
    
    private String analyzeInventoryChange(String oldInv, String newInv) {
        if (oldInv.length() < newInv.length()) {
            return "Items gained";
        } else if (oldInv.length() > newInv.length()) {
            return "Items lost/used";
        } else {
            return "Items changed";
        }
    }
    
    private String formatLocation(Tile tile) {
        if (tile == null) return "Unknown";
        return "(" + tile.getX() + ", " + tile.getY() + ", " + tile.getZ() + ")";
    }
    
    private String getAnimationDescription(int animationId) {
        // Common animation descriptions
        switch (animationId) {
            case 827: return "Walking";
            case 832: return "Running";
            case 881: return "Fishing";
            case 896: return "Mining";
            case 886: return "Woodcutting";
            case 1560: return "Combat";
            case 829: return "Consuming item";
            default: return "Action";
        }
    }
    
    private void logStep(String description, String scriptCommand) {
        try {
            if (logWriter != null) {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                String logLine = String.format("[%s] QUEST STEP %d: %s\n", timestamp, stepCounter++, description);
                
                logWriter.write(logLine);
                if (scriptCommand != null && !scriptCommand.isEmpty()) {
                    logWriter.write("    SCRIPT: " + scriptCommand + "\n");
                }
                logWriter.write("\n");
                logWriter.flush();
                
                // Console output
                script.log("QUEST STEP " + (stepCounter - 1) + ": " + description);
                if (scriptCommand != null && !scriptCommand.isEmpty()) {
                    script.log("  -> SCRIPT: " + scriptCommand);
                }
                
                // NEW: Send to GUI if in discovery mode
                sendToGUI("QUEST STEP " + (stepCounter - 1) + ": " + description + (scriptCommand != null && !scriptCommand.isEmpty() ? " | " + scriptCommand : ""));
            }
        } catch (IOException e) {
            script.log("Failed to write step: " + e.getMessage());
        }
    }
    
    private void logAction(String description, String scriptCommand) {
        try {
            if (logWriter != null) {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                String logLine = String.format("[%s] ACTION: %s\n", timestamp, description);
                
                logWriter.write(logLine);
                if (scriptCommand != null && !scriptCommand.isEmpty()) {
                    logWriter.write("    SCRIPT: " + scriptCommand + "\n");
                }
                logWriter.write("\n");
                logWriter.flush();
                
                // Console output
                script.log("ACTION: " + description);
                if (scriptCommand != null && !scriptCommand.isEmpty()) {
                    script.log("  -> SCRIPT: " + scriptCommand);
                }
                
                // NEW: Send to GUI if in discovery mode
                sendToGUI("ACTION: " + description + (scriptCommand != null && !scriptCommand.isEmpty() ? " | " + scriptCommand : ""));
            }
        } catch (IOException e) {
            script.log("Failed to write action: " + e.getMessage());
        }
    }
    
    private void logDetail(String category, String details) {
        try {
            if (logWriter != null) {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                String logLine = String.format("[%s] %s: %s\n", timestamp, category, details);
                
                logWriter.write(logLine);
                logWriter.flush();
                
                // Console output
                script.log(category + ": " + details);
                
                // NEW: Send to GUI if in discovery mode
                sendToGUI(category + ": " + details);
            }
        } catch (IOException e) {
            script.log("Failed to write detail: " + e.getMessage());
        }
    }
    
    /**
     * NEW: Send discovery message to GUI through main script
     */
    private void sendToGUI(String message) {
        try {
            // Call sendDiscoveryToGUI method on the main script using reflection
            java.lang.reflect.Method method = script.getClass().getMethod("sendDiscoveryToGUI", String.class);
            method.invoke(script, message);
        } catch (Exception e) {
            // Silent fail - GUI communication is optional
        }
    }
    
    // Removed: legacyQuestStates - unused after varbit-based tracking
    
    // 8. Quest Progress Tracking - Enhanced with Real-Time Varbit Monitoring
    private void checkQuestProgress() {
        long currentTime = System.currentTimeMillis();
        
        // Only check quest states every second to avoid spam
        if (currentTime - lastQuestCheckTime < QUEST_CHECK_INTERVAL) {
            return;
        }
        lastQuestCheckTime = currentTime;

        try {
            // PRIORITY: Real-time varbit monitoring for quest steps
            checkQuestVarbits();
            
            // NOTE: Config and varbit discovery now handled by smart triggers
            // No more constant polling - only check when quest events occur
            
        } catch (Exception e) {
            // Silent catch to prevent breaking the main loop
        }
    }
    
    /**
     * NEW: Real-time varbit monitoring - tracks individual quest step progress
     * Only logs when quest progress actually changes
     */
    private void checkQuestVarbits() {
        for (Map.Entry<String, Integer> questEntry : QUEST_VARBITS.entrySet()) {
            String questName = questEntry.getKey();
            int varbitId = questEntry.getValue();
            
            try {
                // Get current varbit value using DreamBot API
                int currentValue = PlayerSettings.getBitValue(varbitId);
                Integer lastValue = questVarbitStates.get(varbitId);
                
                if (lastValue == null) {
                    // Initialize tracking
                    questVarbitStates.put(varbitId, currentValue);
                    if (currentValue > 0) {
                        String progressDescription = getQuestStepDescription(questName, currentValue);
                        logStep("QUEST PROGRESS: " + questName + " step " + currentValue + " - " + progressDescription, 
                            "// " + questName + " varbit " + varbitId + " = " + currentValue);
                        script.log("QUEST PROGRESS: " + questName + " step " + currentValue + " (" + progressDescription + ")");
                    }
                } else if (currentValue != lastValue) {
                    // Varbit changed - quest progressed!
                    questVarbitStates.put(varbitId, currentValue);
                    
                    String progressDescription = getQuestStepDescription(questName, currentValue);
                    logStep("QUEST PROGRESS: " + questName + " step " + lastValue + " → " + currentValue + " - " + progressDescription, 
                        "// " + questName + " progressed to step " + currentValue);
                    logDetail("QUEST_VARBIT_CHANGE", 
                        questName + " varbit " + varbitId + " changed from " + lastValue + " to " + currentValue);
                    
                    script.log("QUEST PROGRESS: " + questName + " step " + lastValue + " → " + currentValue + " (" + progressDescription + ")");
                }
            } catch (Exception e) {
                // Skip this varbit if there's an error - don't spam logs
            }
        }
    }
    
    /**
     * NEW: Varbit Discovery System - Scans for changing varbits to discover quest progression
     * This runs when starting a new quest to automatically find the correct varbit
     */
    private Map<Integer, Integer> baselineVarbits = new HashMap<>();
    private boolean discoveryModeActive = false;
    private int checkCount = 0;
    
    // Config tracking for quest progression
    private Map<Integer, Integer> baselineConfigs = new HashMap<>();
    private boolean configDiscoveryActive = false;
    
    public void startVarbitDiscovery() {
        script.log("VARBIT DISCOVERY: Starting quest varbit discovery scan...");
        discoveryModeActive = true;
        baselineVarbits.clear();
        
        // Scan common quest varbit ranges and record baseline values
        int[] commonRanges = {
            // F2P Quest varbits (most common range)
            10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
            29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
            60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72,
            100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110,
            140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 150,
            175, 176, 177, 178, 179, 180, 181, 182, 183, 184, 185
        };
        
        int scannedVarbits = 0;
        for (int varbitId : commonRanges) {
            try {
                int value = PlayerSettings.getBitValue(varbitId);
                // Record ALL varbits (including 0 values) to detect changes from 0->1, 1->2, etc.
                baselineVarbits.put(varbitId, value);
                scannedVarbits++;
            } catch (Exception e) {
                // Skip invalid varbits
            }
        }
        
        logStep("VARBIT_DISCOVERY_STARTED", "// Quest varbit discovery initiated - monitoring " + scannedVarbits + " varbits (including 0-values)");
        script.log("DISCOVERY: Recorded " + scannedVarbits + " baseline varbits. Start quest actions now!");
    }
    
    /**
     * Initialize config discovery system - scans common quest config ranges
     */
    public void startConfigDiscovery() {
        script.log("CONFIG DISCOVERY: Starting quest config discovery scan...");
        configDiscoveryActive = true;
        baselineConfigs.clear();
        
        // Scan common quest config ranges based on OSRS quest system
        int[] commonConfigRanges = {
            // Known quest configs from OSRS research
            101, 102, 103, 104, 105, 106, 107, 108, 109, 110, // Quest completion flags
            144, 145, 146, 147, 148, 149, 150, 151, 152, 153, // Quest states
            175, 176, 177, 178, 179, 180, 181, 182, 183, 184, // Legacy quest configs
            200, 201, 202, 203, 204, 205, 206, 207, 208, 209, // F2P quest configs
            300, 301, 302, 303, 304, 305, 306, 307, 308, 309  // Additional quest tracking
        };
        
        int scannedConfigs = 0;
        for (int configId : commonConfigRanges) {
            try {
                int value = PlayerSettings.getConfig(configId);
                // Record ALL configs (including 0 values) to detect changes
                baselineConfigs.put(configId, value);
                scannedConfigs++;
            } catch (Exception e) {
                // Skip invalid configs
            }
        }
        
        logStep("CONFIG_DISCOVERY_STARTED", "// Quest config discovery initiated - monitoring " + scannedConfigs + " configs (including 0-values)");
        script.log("CONFIG DISCOVERY: Recorded " + scannedConfigs + " baseline configs. Ready for quest progression tracking!");
    }
    
    public void checkVarbitChanges() {
        if (!discoveryModeActive || baselineVarbits.isEmpty()) {
            if (!discoveryModeActive) {
                // Silent return - discovery mode not active
                return;
            }
            if (baselineVarbits.isEmpty()) {
                script.log("DEBUG: No baseline varbits recorded for comparison");
                return;
            }
            return;
        }
        
        // Add periodic diagnostic (every 30 checks = ~30 seconds)
        if (checkCount % 30 == 0) {
            int configCount = configDiscoveryActive ? baselineConfigs.size() : 0;
            script.log("DIAGNOSTIC: Checking " + baselineVarbits.size() + " varbits + " + configCount + " configs for changes (check #" + checkCount + ")");
        }
        checkCount++;
        
        // Check all previously recorded varbits for changes
        for (Map.Entry<Integer, Integer> entry : baselineVarbits.entrySet()) {
            int varbitId = entry.getKey();
            int oldValue = entry.getValue();
            
            try {
                int newValue = PlayerSettings.getBitValue(varbitId);
                if (newValue != oldValue) {
                    // Found a changing varbit - this could be quest progress!
                    String questName = findQuestNameByVarbit(varbitId);
                    
                    logStep("VARBIT DISCOVERED: " + (questName != null ? questName : "Unknown Quest") + 
                           " varbit " + varbitId + " changed from " + oldValue + " to " + newValue, 
                           "// DISCOVERED: Quest uses varbit " + varbitId + " (step " + oldValue + " → " + newValue + ")");
                    
                    script.log("DISCOVERY HIT: Varbit " + varbitId + " = " + oldValue + " → " + newValue + 
                              (questName != null ? " (" + questName + " PROGRESS!)" : " (QUEST PROGRESS DETECTED!)"));
                    
                    // Update baseline for continued tracking
                    baselineVarbits.put(varbitId, newValue);
                }
            } catch (Exception e) {
                // Skip this varbit
            }
        }
    }
    
    /**
     * Helper method to find quest name by varbit ID
     */
    private String findQuestNameByVarbit(int varbitId) {
        for (Map.Entry<String, Integer> entry : QUEST_VARBITS.entrySet()) {
            if (entry.getValue().equals(varbitId)) {
                return entry.getKey();
            }
        }
        return null; // Unknown quest
    }
    
    /**
     * Check all previously recorded configs for changes (config discovery system)
     */
    public void checkConfigChanges() {
        if (!configDiscoveryActive || baselineConfigs.isEmpty()) {
            if (!configDiscoveryActive) {
                // Silent return - config discovery mode not active
                return;
            }
            if (baselineConfigs.isEmpty()) {
                script.log("DEBUG: No baseline configs recorded for comparison");
                return;
            }
            return;
        }
        
        // Check all previously recorded configs for changes
        for (Map.Entry<Integer, Integer> entry : baselineConfigs.entrySet()) {
            int configId = entry.getKey();
            int oldValue = entry.getValue();
            
            try {
                int newValue = PlayerSettings.getConfig(configId);
                if (newValue != oldValue) {
                    // Found a changing config - this could be quest progress!
                    String questName = findQuestNameByConfig(configId);
                    
                    logStep("CONFIG DISCOVERED: " + (questName != null ? questName : "Unknown Quest") + 
                           " config " + configId + " changed from " + oldValue + " to " + newValue, 
                           "// DISCOVERED: Quest uses config " + configId + " (value " + oldValue + " → " + newValue + ")");
                    
                    script.log("CONFIG DISCOVERY HIT: Config " + configId + " = " + oldValue + " → " + newValue + 
                              (questName != null ? " (" + questName + " PROGRESS!)" : " (QUEST PROGRESS DETECTED!)"));
                    
                    // Update baseline for continued tracking
                    baselineConfigs.put(configId, newValue);
                }
            } catch (Exception e) {
                // Skip this config
            }
        }
    }
    
    /**
     * Helper method to find quest name by config ID (will be populated as we discover quest configs)
     */
    private String findQuestNameByConfig(int configId) {
        // This will be enhanced as we discover which configs map to which quests
        // For now, return generic detection
        return null; // Unknown quest
    }
    
    /**
     * Get human-readable description of quest step based on varbit value
     * Now uses the unified QUEST_INFO system
     */
    private String getQuestStepDescription(String questName, int step) {
        QuestInfo info = QUEST_INFO.get(questName);
        if (info != null) {
            return info.getStepDescription(step);
        }
        return "[STEP " + step + "]";
    }
    
    // Removed: checkHighLevelQuestStates() - unused after varbit-based tracking implementation
    
    // Helper method to detect which quest is currently active
    private void detectCurrentActiveQuest() {
        try {
            // Look for any quest that's currently started
            FreeQuest[] freeQuests = FreeQuest.values();
            
            for (FreeQuest quest : freeQuests) {
                if (Quests.isStarted(quest)) {
                    currentActiveQuest = quest;
                    script.log("[QUEST] ACTIVE QUEST DETECTED: " + quest.toString());
                    logDetail("ACTIVE_QUEST_DETECTED", quest.toString() + " is currently in progress");
                    return;
                }
            }
            
            // If no F2P quest is active, check paid quests
            PaidQuest[] paidQuests = PaidQuest.values();
            for (PaidQuest quest : paidQuests) {
                if (Quests.isStarted(quest)) {
                    currentActiveQuest = quest;
                    script.log("[QUEST] ACTIVE QUEST DETECTED: " + quest.toString() + " (Members)");
                    logDetail("ACTIVE_QUEST_DETECTED", quest.toString() + " is currently in progress (Members)");
                    return;
                }
            }
            
            // No active quest found
            currentActiveQuest = null;
            
        } catch (Exception e) {
            script.log("Error detecting active quest: " + e.getMessage());
        }
    }
    
    // Helper method to validate XP rewards and filter out player chat
    private boolean isValidXPReward(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        
        String lowerText = text.toLowerCase();
        
        // Reject obvious player chat patterns
        if (text.contains("I just do") || text.contains("im ") || text.contains("i am ") ||
            text.contains("you ") || lowerText.contains("player says:") ||
            lowerText.contains("runes r expesnive") || lowerText.contains("expensive")) {
            return false;
        }
        
        // Accept only clear XP reward patterns
        return (lowerText.contains("experience") && lowerText.contains("gained")) ||
               (lowerText.contains("xp") && (lowerText.contains("gained") || lowerText.contains("reward"))) ||
               lowerText.contains("quest complete") ||
               text.matches(".*\\d+.*experience.*") || // Contains numbers and experience
               text.matches(".*\\d+.*xp.*");          // Contains numbers and xp
    }
    
    // Removed: isQuestCompletionState() - consolidated into unified QUEST_INFO system
    
    // Removed: checkWidgetInteractions() - unused after simplification
    
    // 10. Spell/Magic Interactions - OPTIMIZED for instant detection
    private void checkSpellCasting() {
        try {
            long currentTime = System.currentTimeMillis();
            
            // INSTANT DETECTION: Magic tab state change
            Tab currentTab = Tabs.getOpen();
            boolean isMagicTabOpen = (currentTab == Tab.MAGIC);
            
            if (isMagicTabOpen != lastMagicTabState) {
                if (isMagicTabOpen) {
                    logAction("Opened magic spellbook", "Tabs.open(Tab.MAGIC)");
                    logDetail("SPELLBOOK_OPENED", "Player opened the magic spellbook");
                    script.log("INSTANT DETECTION: Magic tab opened");
                }
                lastMagicTabState = isMagicTabOpen;
            }
            
            // INSTANT DETECTION: Spell casting through animation
            if (Players.getLocal() != null && Players.getLocal().isAnimating()) {
                int currentAnimation = Players.getLocal().getAnimation();
                
                // Instant spell detection when animation changes
                if (currentAnimation != lastAnimation && isMagicCastingAnimation(currentAnimation) && 
                    currentTime - lastSpellCastTime > SPELL_CAST_COOLDOWN) {
                    
                    String spellName = detectSpellFromAnimation(currentAnimation);
                    logAction("Cast spell: " + spellName, 
                        "Magic.cast(\"" + spellName + "\")");
                    logDetail("SPELL_CAST", "Player cast spell: " + spellName + " (Animation: " + currentAnimation + ")");
                    script.log("INSTANT DETECTION: Spell cast - " + spellName + " (Anim: " + currentAnimation + ")");
                    
                    lastSpellCastTime = currentTime;
                }
            }
            
            // Update tracking variables
            lastMagicTabState = isMagicTabOpen;
            
        } catch (Exception e) {
            // Silent fail to avoid spam in logs
        }
    }
    
    /**
     * Unified animation detection system for all animation types
     */
    private enum AnimationType {
        COMBAT, MAGIC, PRAYER, MOVEMENT, SKILL, CONSUMPTION, OTHER
    }
    
    private AnimationType getAnimationType(int animationId) {
        switch (animationId) {
            // Combat animations
            case 422:   // Punch/Unarmed combat
            case 393:   // Sword slash
            case 395:   // Sword stab
            case 400:   // Pickaxe swing (also combat)
            case 401:   // Axe swing (also combat)
            case 806:   // Bow shooting
            case 1979:  // Whip attack
            case 2080:  // Dagger stab
            case 390:   // Hammer/mace crush
                return AnimationType.COMBAT;
                
            // Magic animations
            case 711:   // Standard spell casting
            case 724:   // High level spell casting  
            case 1162:  // Ancient magic casting
            case 1978:  // Lunar magic casting
            case 8939:  // Bind spell casting
            case 708:   // Teleport casting
            case 9493:  // Enchant spell casting
            case 1818:  // Alchemy casting
                return AnimationType.MAGIC;
                
            // Prayer animations
            case 645:   // Prayer activation
            case 827:   // Bone burying (also walking - context dependent)
                return AnimationType.PRAYER;
                
            // Movement animations
            case 819:   // Walking
            case 824:   // Running
                return AnimationType.MOVEMENT;
                
            // Consumption animations
            case 829:   // Consuming item
                return AnimationType.CONSUMPTION;
                
            // Skill animations
            case 881:   // Fishing
            case 896:   // Mining
            case 886:   // Woodcutting
                return AnimationType.SKILL;
                
            // Significant animations (teleports, level ups, etc.)
            case 1816:  // Death animation
            case 2108:  // Quest completion animation
            case 3864:  // Levelup animation
                return AnimationType.OTHER;
                
            default:
                return AnimationType.OTHER;
        }
    }
    
    private boolean isMagicCastingAnimation(int animationId) {
        return getAnimationType(animationId) == AnimationType.MAGIC;
    }
    
    private String detectSpellFromAnimation(int animationId) {
        // Try to determine which spell was cast based on animation
        switch (animationId) {
            case 711:
                return "Magic Spell"; // Generic casting
            case 724:
                return "High Level Spell";
            case 1162:
                return "Ancient Magic";
            case 1978:
                return "Lunar Magic";
            case 8939:
                return "Bind Spell";
            case 708:
                return "Teleport Spell";
            case 9493:
                return "Enchant Spell";
            case 1818:
                return "Alchemy Spell";
            default:
                return "Unknown Spell (Animation: " + animationId + ")";
        }
    }
    
    // 11. Combat Interactions - NEW
    private void checkCombat() {
        try {
            long currentTime = System.currentTimeMillis();
            
            // Check if player is in combat (using animation and nearby NPCs)
            boolean inCombat = false;
            NPC currentTarget = null;
            
            if (Players.getLocal() != null && Players.getLocal().isAnimating()) {
                int animation = Players.getLocal().getAnimation();
                // Common combat animations
                if (isCombatAnimation(animation)) {
                    inCombat = true;
                    // Find nearest NPC that might be the target
                    currentTarget = NPCs.closest(npc -> npc != null && npc.distance() <= 2.0);
                }
            }
            
            // Detect combat state changes
            if (inCombat != lastCombatState) {
                if (inCombat && currentTarget != null) {
                    logAction("Entered combat with " + currentTarget.getName(), 
                        "NPCs.closest(\"" + currentTarget.getName() + "\").interact(\"Attack\")");
                    logDetail("COMBAT_START", "Combat started with: " + currentTarget.getName() + " (Level " + currentTarget.getLevel() + ")");
                } else if (!inCombat) {
                    logAction("Combat ended", "// Combat finished");
                    logDetail("COMBAT_END", "Combat ended");
                }
                lastCombatState = inCombat;
            }
            
        } catch (Exception e) {
            // Silent fail to avoid spam in logs
        }
    }
    
    private boolean isCombatAnimation(int animationId) {
        return getAnimationType(animationId) == AnimationType.COMBAT;
    }
    
    // 12. Prayer Activation - NEW (Simplified)
    private void checkPrayers() {
        try {
            long currentTime = System.currentTimeMillis();
            
            // Check prayer points and any prayer-related animations
            if (Players.getLocal() != null && Players.getLocal().isAnimating()) {
                int animation = Players.getLocal().getAnimation();
                
                // Prayer activation animation (ID 645 is common for prayer activation)
                if (animation == 645 && currentTime - lastPrayerChangeTime > PRAYER_CHANGE_COOLDOWN) {
                    logAction("Activated prayer", "// Prayer activated (Animation: " + animation + ")");
                    logDetail("PRAYER_ACTIVATED", "Prayer activation animation detected");
                    lastPrayerChangeTime = currentTime;
                }
                
                // Bone burying animation (ID 827) - BUT ONLY if bones are in inventory
                if (animation == 827 && currentTime - lastPrayerChangeTime > PRAYER_CHANGE_COOLDOWN) {
                    // IMPORTANT: Check if player actually has bones to bury
                    Item bones = Inventory.get(item -> item != null && item.getName() != null && 
                                             item.getName().toLowerCase().contains("bone"));
                    
                    if (bones != null) {
                        // Player has bones and is doing the burying animation
                        logAction("Buried bones", "Inventory.getItem(\"" + bones.getName() + "\").interact(\"Bury\")");
                        logDetail("BONES_BURIED", "Bone burying animation detected with bones in inventory: " + bones.getName());
                        lastPrayerChangeTime = currentTime;
                        script.log("INSTANT DETECTION: Buried bones - " + bones.getName());
                    }
                    // If no bones in inventory, this is likely a false positive (walking animation)
                }
            }
            
        } catch (Exception e) {
            // Silent fail to avoid spam in logs
        }
    }
    
    // 13. Grand Exchange Trading - NEW
    private void checkGrandExchange() {
        try {
            long currentTime = System.currentTimeMillis();
            
            // Check if GE is open using widgets
            WidgetChild geWidget = Widgets.getWidgetChild(465, 2); // GE interface widget
            boolean geOpen = (geWidget != null && geWidget.isVisible());
            
            if (geOpen != lastGEState) {
                if (currentTime - lastGEActionTime > GE_ACTION_COOLDOWN) {
                    if (geOpen) {
                        logAction("Opened Grand Exchange", "GrandExchange.open()");
                        logDetail("GE_OPENED", "Grand Exchange interface opened");
                    } else {
                        logAction("Closed Grand Exchange", "GrandExchange.close()");
                        logDetail("GE_CLOSED", "Grand Exchange interface closed");
                    }
                    lastGEState = geOpen;
                    lastGEActionTime = currentTime;
                }
            }
            
            // Could add more detailed GE tracking here (buy/sell orders, etc.)
            // but would need more complex state tracking
            
        } catch (Exception e) {
            // Silent fail to avoid spam in logs
        }
    }
    
    // 14. Run/Energy Management - NEW (Simplified)
    private void checkRunState() {
        try {
            long currentTime = System.currentTimeMillis();
            
            // Check for energy potion usage (already tracked in inventory)
            // and movement speed changes through animations
            if (Players.getLocal() != null && Players.getLocal().isAnimating()) {
                int animation = Players.getLocal().getAnimation();
                
                // Running animation (ID 824) vs walking animation (ID 819)
                if (animation == 824 && !lastRunState && currentTime - lastRunToggleTime > RUN_TOGGLE_COOLDOWN) {
                    logAction("Started running", "// Player began running");
                    logDetail("RUN_ENABLED", "Running animation detected");
                    lastRunState = true;
                    lastRunToggleTime = currentTime;
                } else if (animation == 819 && lastRunState && currentTime - lastRunToggleTime > RUN_TOGGLE_COOLDOWN) {
                    logAction("Stopped running", "// Player stopped running");
                    logDetail("RUN_DISABLED", "Walking animation detected");
                    lastRunState = false;
                    lastRunToggleTime = currentTime;
                }
            }
            
            // Note: Energy potion usage is already tracked in inventory changes
            
        } catch (Exception e) {
            // Silent fail to avoid spam in logs
        }
    }
    
    // Helper method for common quest interface detection (DISABLED to prevent spam)
    // Removed: checkCommonQuestInterfaces() - unused after widget refactoring
    
    // This method was removed as it duplicated the QUEST_VARBITS static map above
    // All quest varbit mappings are now consolidated in the static QUEST_VARBITS map
    
    // Removed: getCurrentVarbitValue() - unused after consolidation
    
    /**
     * Unified quest information system - consolidates completion values and step descriptions
     */
    private static class QuestInfo {
        final int completionValue;
        final String[] stepDescriptions;
        
        QuestInfo(int completionValue, String... stepDescriptions) {
            this.completionValue = completionValue;
            this.stepDescriptions = stepDescriptions;
        }
        
        String getStepDescription(int step) {
            if (step >= stepDescriptions.length) {
                return "[STEP " + step + "]";
            }
            return stepDescriptions[step];
        }
        
        boolean isComplete(int varbitValue) {
            return varbitValue >= completionValue;
        }
    }
    
    private static final Map<String, QuestInfo> QUEST_INFO = new HashMap<String, QuestInfo>() {{
        put("THE_RESTLESS_GHOST", new QuestInfo(4,
            "[NOT STARTED]",
            "[TALKED TO FATHER AERECK]", 
            "[GOT GHOSTSPEAK AMULET]",
            "[FOUND GHOST'S SKULL]",
            "[QUEST COMPLETED!]"
        ));
        put("COOKS_ASSISTANT", new QuestInfo(2,
            "[NOT STARTED]",
            "[TALKED TO COOK]",
            "[GATHERING INGREDIENTS]",
            "[QUEST COMPLETED!]"
        ));
        put("IMP_CATCHER", new QuestInfo(3,
            "[NOT STARTED]",
            "[TALKED TO WIZARD MIZGOG]",
            "[COLLECTING BEADS]",
            "[QUEST COMPLETED!]"
        ));
        put("ROMEO_AND_JULIET", new QuestInfo(60,
            "[NOT STARTED]", "", "", "", "", "", // Steps 0-5
            "[TALKED TO ROMEO]", "", "", "", // Steps 6-9
            "[TALKED TO ROMEO]", "", "", "", "", "", "", "", "", "", // Steps 10-19
            "[DELIVERED MESSAGE TO JULIET]", "", "", "", "", "", "", "", "", "", // Steps 20-29  
            "[TALKED TO FATHER LAWRENCE]", "", "", "", "", "", "", "", "", "", // Steps 30-39
            "[GOT CADAVA POTION]", "", "", "", "", "", "", "", "", "", // Steps 40-49
            "[GAVE POTION TO JULIET]", "", "", "", "", "", "", "", "", "", // Steps 50-59
            "[QUEST COMPLETED!]" // Step 60
        ));
        put("SHEEP_SHEARER", new QuestInfo(21));
        put("RUNE_MYSTERIES", new QuestInfo(6));
        put("DEMON_SLAYER", new QuestInfo(3));
    }};
    
    // Removed: isQuestComplete() - this functionality is now handled by QuestInfo.isComplete()
    
    // This method was removed as it duplicated logDetail() functionality
    // All logging now uses logDetail() directly which already includes console output
    
    // DreamBot Console Integration System
    private void setupConsoleIntegration() {
        try {
            logDetail("CONSOLE_INTEGRATION", "DreamBot console logging enabled - all script output will be mirrored to quest log file");
            logDetail("CONSOLE_INFO", "Quest log location: " + getCurrentLogPath());
            logDetail("CONSOLE_INFO", "DreamBot script: Quest Action Recorder v4.3");
        } catch (Exception e) {
            script.log("Console integration setup failed: " + e.getMessage());
        }
    }
    
    // Method to capture DreamBot script output
    public void logConsoleOutput(String message) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String consoleLog = "[" + timestamp + "] DREAMBOT_CONSOLE: " + message + "\n";
            
            if (logWriter != null) {
                logWriter.write(consoleLog);
                logWriter.flush();
            }
        } catch (Exception e) {
            // Silent fail to prevent recursion
        }
    }
    
    private String getCurrentLogPath() {
        return System.getProperty("user.home") + "\\Desktop\\Projects in progress\\Dreambot Projects\\AI Quest system\\quest_logs\\";
    }
    
    public void close() {
        try {
            if (logWriter != null) {
                logDetail("QUEST_END", "=== QUEST RECORDING ENDED ===");
                logWriter.close();
                script.log("Quest log closed successfully");
            }
        } catch (IOException e) {
            script.log("Error closing quest log: " + e.getMessage());
        }
    }
}
