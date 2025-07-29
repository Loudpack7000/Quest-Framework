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
// Testing what other listeners are available
import org.dreambot.api.script.listener.*;
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
    private String[] lastDialogueOptions = null;
    private boolean lastBankState = false;
    private int lastAnimation = -1;
    
    // Spam reduction tracking
    private String lastInventoryChangeType = "";
    private boolean lastMovingState = false;
    
    // Journey tracking for better movement logging
    private Tile journeyStartTile = null;
    private long journeyStartTime = 0;
    private Tile lastPosition = null; // Used by movement tracking
    
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
    
    // Quest progress tracking
    private Map<String, Integer> questVarbits = new HashMap<>();
    private Map<String, Integer> lastVarbitValues = new HashMap<>();
    private long lastMovementTime = 0;
    private static final long MOVEMENT_COOLDOWN = 3000; // 3 seconds between movement logs
    
    // Object interaction throttling
    private String lastInteractedObject = "";
    private long lastObjectInteractionTime = 0;
    private static final long OBJECT_INTERACTION_COOLDOWN = 5000; // 5 seconds between object logs
    private Tile lastObjectInteractionTile = null;
    
    // Widget interaction tracking
    private String lastLoggedWidgetText = "";
    
    // NEW: Comprehensive interaction tracking using DreamBot API
    private GameObject lastTrackedGameObject = null;
    private NPC lastTrackedNPC = null;
    private Tile lastPlayerTile = null;
    private String lastTrackedAction = "";
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
    
    // Spell/Magic tracking
    private Tab lastActiveTab = null;
    private boolean lastMagicTabState = false;
    private String lastCastSpell = "";
    private long lastSpellCastTime = 0;
    private static final long SPELL_CAST_COOLDOWN = 1000; // 1 second between spell logs
    
    // Combat tracking
    private boolean lastCombatState = false;
    private NPC lastCombatTarget = null;
    private boolean lastSpecialAttackState = false;
    private long lastCombatActionTime = 0;
    private static final long COMBAT_ACTION_COOLDOWN = 2000; // 2 seconds between combat logs
    
    // Prayer tracking
    private boolean lastPrayerActiveState = false;
    private String lastActivePrayer = "";
    private long lastPrayerChangeTime = 0;
    private static final long PRAYER_CHANGE_COOLDOWN = 1000; // 1 second between prayer logs
    
    // Grand Exchange tracking
    private boolean lastGEState = false;
    private long lastGEActionTime = 0;
    private static final long GE_ACTION_COOLDOWN = 3000; // 3 seconds between GE logs
    
    // Run/Energy tracking
    private boolean lastRunState = false;
    private long lastRunToggleTime = 0;
    private static final long RUN_TOGGLE_COOLDOWN = 2000; // 2 seconds between run logs
    
    private long lastDialogueChange = 0;
    private long lastAnimationLog = 0;
    private long lastPositionLog = 0;
    private long lastHealthLog = 0;
    private long lastSkillLog = 0;
    
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
            lastPosition = Players.getLocal().getTile();
            lastAnimation = Players.getLocal().getAnimation();
            lastMovingState = Players.getLocal().isMoving();
        }
        lastInventoryState = getCurrentInventoryString();
        lastDialogueState = Dialogues.inDialogue();
        lastDialogueOptions = Dialogues.getOptions();
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
            
            // Get tile coordinates for NPCs and GameObjects
            String coordinateInfo = getTargetCoordinates(action, targetName, mouseX, mouseY);
            
            // Log the ACTUAL action that was performed
            String actionDescription = "Selected '" + action + "' on " + targetName;
            String scriptCode = generateScriptCode(action, targetName, menuRow);
            
            logAction(actionDescription, scriptCode);
            logDetail("USER_ACTION", action + " | " + coordinateInfo);
            
            script.log("REAL ACTION DETECTED: " + action + " -> " + targetName);
            
            // SMART CONFIG CHECK: Check configs after quest-relevant actions
            if (isQuestRelevantAction(action, targetName)) {
                triggerConfigCheck("User action: " + action + " on " + targetName);
            }
            
            // SMART INVENTORY CHECK: Check inventory after inventory-relevant actions
            if (isInventoryRelevantAction(action, targetName)) {
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
        
        // Item usage (quest items, tools, etc.)
        if (action.equals("Use") || action.equals("Drop") || action.equals("Eat") || 
            action.equals("Drink") || action.equals("Wield") || action.equals("Wear")) {
            return true;
        }
        
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
        script.log("SMART BANKING CHECK: Triggered by " + triggerReason);
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
            
            // Bank interface detection (only if truly needed)
            if (Widgets.isVisible(12)) { // Bank interface
                if (currentTime - lastTrackedInteractionTime > 5000) {
                    logAction("Bank accessed", 
                        "// Bank.open() - banking interaction");
                    logDetail("BANK_INTERACTION", "Bank interface opened");
                    lastTrackedInteractionTime = currentTime;
                    
                    script.log("BANK INTERACTION DETECTED");
                }
            }
        } catch (Exception e) {
            // Silent catch to prevent spam
        }
    }
    
    // Enhanced detection methods
    private void detectAnimationBasedInteraction(long currentTime) {
        int currentAnimation = Players.getLocal().getAnimation();
        
        // Look for nearby objects that could be interacted with
        GameObject nearestObject = GameObjects.closest(obj -> 
            obj != null && obj.distance() <= 3.0 && obj.exists());
        
        if (nearestObject != null && 
            (lastTrackedGameObject == null || !nearestObject.equals(lastTrackedGameObject) || 
             currentTime - lastTrackedInteractionTime > 3000)) {
            
            // Log interaction with script code generation
            String actionName = determineObjectAction(nearestObject);
            logAction("Interacting with " + nearestObject.getName(), 
                formatGameObjectInteraction(nearestObject, actionName));
            logDetail("OBJECT_INTERACTION", 
                nearestObject.getName() + " at " + formatLocation(nearestObject.getTile()) + 
                " | Action: " + actionName + " | Distance: " + String.format("%.1f", nearestObject.distance()));
            
            lastTrackedGameObject = nearestObject;
            lastTrackedInteractionTime = currentTime;
            
            script.log("ANIMATION-BASED INTERACTION: " + nearestObject.getName() + 
                       " | Animation: " + currentAnimation +
                       " | Action: " + actionName +
                       " | Distance: " + String.format("%.1f", nearestObject.distance()));
        }
    }
    
    private void detectMovementBasedInteraction(long currentTime) {
        // DISABLED: Annoying guessed action logs removed per user request
        // We only want to log ACTUAL user-selected actions via ActionListener
        // This method previously logged guessed actions like "Climb-down" for nearby objects
        
        // Still track player position for other detection methods
        if (Players.getLocal() != null) {
            Tile currentTile = Players.getLocal().getTile();
            lastPlayerTile = currentTile;
        }
    }
    
    private void detectStateBasedInteraction(long currentTime) {
        // Check for interface changes that might indicate interactions
        if (Players.getLocal() != null) {
            // Check for nearby clickable objects even without animation
            GameObject nearestObject = GameObjects.closest(obj -> 
                obj != null && obj.exists() && obj.distance() <= 1.5 &&
                obj.hasAction("Climb-up", "Climb-down", "Open", "Search"));
            
            if (nearestObject != null && 
                (lastTrackedGameObject == null || !nearestObject.equals(lastTrackedGameObject)) &&
                currentTime - lastTrackedInteractionTime > 2000) {
                
                // Check if we're very close to an interactive object
                if (nearestObject.distance() <= 1.5) {
                    String actionName = determineObjectAction(nearestObject);
                    logAction("Close proximity to " + nearestObject.getName(), 
                        formatGameObjectInteraction(nearestObject, actionName));
                    logDetail("PROXIMITY_INTERACTION", 
                        nearestObject.getName() + " detected in close proximity | Action: " + actionName);
                    
                    lastTrackedGameObject = nearestObject;
                    lastTrackedInteractionTime = currentTime;
                    
                    script.log("PROXIMITY-BASED INTERACTION: " + nearestObject.getName() + 
                               " | Distance: " + String.format("%.1f", nearestObject.distance()) +
                               " | Action: " + actionName);
                }
            }
        }
    }
    
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
    
    // Helper method to determine the most likely NPC action
    private String determineNPCAction(NPC npc) {
        String[] actions = npc.getActions();
        if (actions != null && actions.length > 0) {
            for (String action : actions) {
                if (action != null && !action.trim().isEmpty()) {
                    return action;
                }
            }
        }
        
        // Fallback based on NPC patterns
        String name = npc.getName().toLowerCase();
        if (name.contains("banker")) return "Bank";
        if (name.contains("shop") || name.contains("store")) return "Trade";
        if (name.contains("guard") || name.contains("warrior")) return "Attack";
        
        return "Talk-to"; // Most common fallback
    }
    
    // Helper method to format GameObject interactions for script generation
    private String formatGameObjectInteraction(GameObject obj, String action) {
        return String.format("GameObjects.closest(\"%s\").interact(\"%s\")", 
                            obj.getName(), action);
    }
    
    // Helper method to format NPC interactions for script generation
    private String formatNPCInteraction(NPC npc, String action) {
        return String.format("NPCs.closest(\"%s\").interact(\"%s\")", 
                            npc.getName(), action);
    }
    
    // 1. Movement Detection - Journey-Based Tracking
    private void checkMovement() {
        if (Players.getLocal() == null) return;
        
        Tile currentPos = Players.getLocal().getTile();
        boolean currentlyMoving = Players.getLocal().isMoving();
        long currentTime = System.currentTimeMillis();
        
        // Movement state changes (starting/stopping movement)
        if (lastMovingState != currentlyMoving) {
            if (currentlyMoving) {
                // Started moving - record journey start
                journeyStartTile = currentPos;
                journeyStartTime = currentTime;
                logDetail("Player State", "Started moving");
            } else {
                // Stopped moving - log journey if significant
                logDetail("Player State", "Stopped moving");
                
                if (journeyStartTile != null && currentPos != null) {
                    double journeyDistance = journeyStartTile.distance(currentPos);
                    
                    // Only log journeys > 15 tiles to avoid spam from small movements
                    // Filter out teleports/cutscenes (> 1000 tiles = probably teleport/cutscene)
                    if (journeyDistance >= 15.0 && journeyDistance <= 1000.0) {
                        long journeyTime = currentTime - journeyStartTime;
                        logAction("Journey completed", 
                            "Walking.walk(new Tile(" + currentPos.getX() + ", " + currentPos.getY() + ", " + currentPos.getZ() + "))");
                        logDetail("Journey", "Walked " + String.format("%.1f", journeyDistance) + " tiles from " + 
                            formatLocation(journeyStartTile) + " to " + formatLocation(currentPos) + 
                            " (took " + (journeyTime/1000) + "s)");
                    }
                    // Log teleports/cutscenes differently
                    else if (journeyDistance > 1000.0) {
                        logDetail("Teleport/Cutscene", "Moved " + String.format("%.1f", journeyDistance) + " tiles from " + 
                            formatLocation(journeyStartTile) + " to " + formatLocation(currentPos) + " (likely teleport/cutscene)");
                    }
                }
                
                // Update position when stopped (important for quest steps)
                if (currentPos != null) {
                    logDetail("Position Update", "Stopped at " + formatLocation(currentPos));
                    lastPosition = currentPos;
                }
                
                checkNearbyTargets(); // Check what they stopped near
            }
            lastMovingState = currentlyMoving;
        }
        
        // Always update current position
        if (currentPos != null) {
            lastPosition = currentPos;
        }
    }
    
    // 2 & 3. NPC Interactions and Dialogue - OPTIMIZED for instant detection
    private void checkDialogue() {
        boolean currentDialogueState = Dialogues.inDialogue();
        
        // INSTANT DETECTION: Dialogue state change (highest priority)
        if (currentDialogueState != lastDialogueState) {
            
            if (currentDialogueState) {
                // Dialogue just started - immediate logging
                NPC nearestNPC = NPCs.closest(npc -> npc != null && npc.distance() <= 3);
                if (nearestNPC != null) {
                    logAction("Started dialogue with " + nearestNPC.getName(),
                        "NPCs.closest(\"" + nearestNPC.getName() + "\").interact(\"Talk-to\")");
                    script.log("INSTANT DETECTION: Dialogue started with " + nearestNPC.getName());
                    
                    // SMART CONFIG CHECK: Dialogue started
                    triggerConfigCheck("Dialogue started with " + nearestNPC.getName());
                }
                
                String currentNPCText = Dialogues.getNPCDialogue();
                if (currentNPCText != null && !currentNPCText.isEmpty()) {
                    logDetail("NPC Dialogue", currentNPCText);
                }
            } else {
                // Dialogue ended
                script.log("INSTANT DETECTION: Dialogue ended");
                
                // SMART CONFIG CHECK: Dialogue ended (quest may have progressed)
                triggerConfigCheck("Dialogue ended");
            }
            
            lastDialogueState = currentDialogueState;
        }
        
        // Only check options if we're in dialogue (performance optimization)
        if (currentDialogueState) {
            String[] currentOptions = Dialogues.getOptions();
            
            // INSTANT DETECTION: Dialogue options appeared or changed
            if (!Arrays.equals(lastDialogueOptions, currentOptions)) {
                
                if (currentOptions != null && currentOptions.length > 0) {
                    logDetail("Dialogue Options Available", Arrays.toString(currentOptions));
                    script.log("INSTANT DETECTION: New dialogue options appeared");
                }
                
                // Player selected an option (options disappeared or changed)
                if (lastDialogueOptions != null && lastDialogueOptions.length > 0) {
                    if (currentOptions == null || currentOptions.length == 0 || 
                        (currentOptions.length != lastDialogueOptions.length && currentOptions.length < lastDialogueOptions.length)) {
                        
                        logAction("Player selected dialogue option", 
                            "Dialogues.selectOption(1) // Player chose from: " + Arrays.toString(lastDialogueOptions));
                        script.log("INSTANT DETECTION: Player selected dialogue option");
                        
                        // SMART CONFIG CHECK: Player selected dialogue option (quest step likely completed)
                        triggerConfigCheck("Player selected dialogue option");
                        
                        // SMART INVENTORY CHECK: Dialogue completion might have given items
                        smartInventoryCheck("Player selected dialogue option - checking for quest rewards");
                    }
                    // Options changed to different set (player selected and new options appeared)
                    else if (!Arrays.equals(lastDialogueOptions, currentOptions) && 
                             currentOptions.length > 0) {
                        
                        logAction("Player selected dialogue option and new options appeared", 
                            "Dialogues.selectOption(1) // Previous: " + Arrays.toString(lastDialogueOptions) + 
                            ", New: " + Arrays.toString(currentOptions));
                        script.log("INSTANT DETECTION: Dialogue option selected, new options appeared");
                        
                        // SMART CONFIG CHECK: Dialogue progression
                        triggerConfigCheck("Dialogue option selected, new options appeared");
                    }
                }
                
                lastDialogueOptions = currentOptions;
            }
        }
    }
    
    // 4. Object Interactions
    private void checkObjectInteractions() {
        // Object interactions are now only detected when player stops moving near objects
        // This prevents spam from continuous checking
    }
    
    private void checkNearbyTargets() {
        // DISABLED: No more guessing nearby object interactions since ActionListener captures real clicks
        // This method was causing logs like "Likely interacted with object" which are unnecessary
    }
    
    private boolean isQuestRelevant(String objectName) {
        if (objectName == null) return false;
        String name = objectName.toLowerCase();
        
        // Quest-relevant objects
        return name.contains("door") || name.contains("chest") || name.contains("altar") ||
               name.contains("stairs") || name.contains("ladder") || name.contains("gate") ||
               name.contains("lever") || name.contains("table") || name.contains("bookshelf") ||
               name.contains("coffin") || name.contains("statue") || name.contains("crate") ||
               name.contains("barrel") || name.contains("well") || name.contains("furnace") ||
               name.contains("anvil") || name.contains("bank");
    }
    
    // 5. Inventory Changes - Enhanced Item Usage Detection
    private void checkInventory() {
        String currentInventoryState = getCurrentInventoryString();
        
        if (!currentInventoryState.equals(lastInventoryState)) {
            String changeType = analyzeInventoryChange(lastInventoryState, currentInventoryState);
            
            // Enhanced item usage detection with spam filtering
            if (changeType.contains("Items lost/used")) {
                // Check if it's item consumption (potion drinking, food eating, etc.)
                String consumedItem = detectItemConsumption(lastInventoryState, currentInventoryState);
                if (consumedItem != null) {
                    // Skip detailed logging for energy potions
                    logAction("Item consumed: " + consumedItem, 
                        "Inventory.getItem(\"" + consumedItem + "\").interact(\"Drink\") // or click");
                    logDetail("ITEM_CONSUMPTION", "Player consumed: " + consumedItem);
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
            
            // Reduce detail logging frequency for repetitive changes
            if (!changeType.equals(lastInventoryChangeType)) {
                logDetail("Inventory Change", changeType + " - New state: " + currentInventoryState);
            }
            lastInventoryState = currentInventoryState;
        }
    }
    
    // 6. Banking
    private void checkBanking() {
        boolean currentBankState = Bank.isOpen();
        
        if (currentBankState != lastBankState) {
            if (currentBankState) {
                logAction("Opened bank", "Bank.open()");
                logDetail("Banking", "Bank interface opened - Current inventory: " + getCurrentInventoryString());
            } else {
                logAction("Closed bank", "Bank.close()");
                logDetail("Banking", "Bank interface closed");
            }
            lastBankState = currentBankState;
        }
    }
    
    // 7. Animations - DISABLED (ActionListener now captures real actions)
    private void checkAnimations() {
        // DISABLED: Animation-based guessing removed since ActionListener captures real clicks
        // No need to correlate animations with nearby objects anymore
        
        if (Players.getLocal() == null) return;
        
        int currentAnimation = Players.getLocal().getAnimation();
        
        if (currentAnimation != lastAnimation && currentAnimation > 0) {
            String animationDescription = getAnimationDescription(currentAnimation);
            logDetail("Animation", animationDescription + " (ID: " + currentAnimation + ")");
            
            // REMOVED: No more nearby object correlation since ActionListener handles it
        }
        
        lastAnimation = currentAnimation;
    }
    
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
                
                // Only log as consumed if it wasn't equipped AND it's likely a consumable
                if (!itemIsEquipped && isLikelyConsumable(itemName)) {
                    return itemName;
                }
            }
        }
        
        return null; // No clear consumption detected
    }
    
    // Helper: Check if item is likely deposited to bank rather than consumed
    private boolean isLikelyDepositedNotConsumed(String itemName) {
        String lower = itemName.toLowerCase();
        return lower.contains("clay") || 
               lower.contains("ore") || 
               lower.contains("bar") || 
               lower.contains("coin") ||
               lower.contains("rune") ||
               lower.contains("gem") ||
               lower.contains("log") ||
               lower.contains("wizard hat") ||
               lower.contains("armor") ||
               lower.contains("weapon") ||
               lower.contains("helm") ||
               lower.contains("shield");
    }
    
    // Helper: Check if item is actually consumable
    private boolean isLikelyConsumable(String itemName) {
        String lower = itemName.toLowerCase();
        return lower.contains("potion") || 
               lower.contains("food") || 
               lower.contains("bread") ||
               lower.contains("fish") ||
               lower.contains("meat") ||
               lower.contains("cake") ||
               lower.contains("pie") ||
               lower.contains("beer") ||
               lower.contains("wine") ||
               lower.contains("tea");
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
    
    // Quest state tracking using DreamBot's PlayerSettings API
    private Map<String, Integer> legacyQuestStates = new HashMap<>();
    
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
     */
    private String getQuestStepDescription(String questName, int step) {
        switch (questName) {
            case "THE_RESTLESS_GHOST":
                switch (step) {
                    case 0: return "[NOT STARTED]";
                    case 1: return "[TALKED TO FATHER AERECK]";
                    case 2: return "[GOT GHOSTSPEAK AMULET]";
                    case 3: return "[FOUND GHOST'S SKULL]";
                    case 4: return "[QUEST COMPLETED!]";
                    default: return "[STEP " + step + "]";
                }
            case "COOKS_ASSISTANT":
                switch (step) {
                    case 0: return "[NOT STARTED]";
                    case 1: return "[TALKED TO COOK]";
                    case 2: return "[GATHERING INGREDIENTS]";
                    case 3: return "[QUEST COMPLETED!]";
                    default: return "[STEP " + step + "]";
                }
            case "IMP_CATCHER":
                switch (step) {
                    case 0: return "[NOT STARTED]";
                    case 1: return "[TALKED TO WIZARD MIZGOG]";
                    case 2: return "[COLLECTING BEADS]";
                    case 3: return "[QUEST COMPLETED!]";
                    default: return "[STEP " + step + "]";
                }
            case "ROMEO_AND_JULIET":
                switch (step) {
                    case 0: return "[NOT STARTED]";
                    case 10: return "[TALKED TO ROMEO]";
                    case 20: return "[DELIVERED MESSAGE TO JULIET]";
                    case 30: return "[TALKED TO FATHER LAWRENCE]";
                    case 40: return "[GOT CADAVA POTION]";
                    case 50: return "[GAVE POTION TO JULIET]";
                    case 60: return "[QUEST COMPLETED!]";
                    default: return "[STEP " + step + "]";
                }
            default:
                return "[STEP " + step + "]";
        }
    }
    
    /**
     * Legacy high-level quest state checking (for completion detection)
     */
    private void checkHighLevelQuestStates() {
        try {
            // Get all F2P quests for monitoring
            FreeQuest[] freeQuests = {
                FreeQuest.COOKS_ASSISTANT,
                FreeQuest.DORICS_QUEST,
                FreeQuest.SHEEP_SHEARER,
                FreeQuest.VAMPIRE_SLAYER,
                FreeQuest.THE_RESTLESS_GHOST,
                FreeQuest.ROMEO_AND_JULIET,
                FreeQuest.ERNEST_THE_CHICKEN,
                FreeQuest.IMP_CATCHER,
                FreeQuest.THE_KNIGHTS_SWORD,
                FreeQuest.BLACK_KNIGHTS_FORTRESS,
                FreeQuest.GOBLIN_DIPLOMACY,
                FreeQuest.PIRATES_TREASURE,
                FreeQuest.PRINCE_ALI_RESCUE,
                FreeQuest.DEMON_SLAYER,
                FreeQuest.DRAGON_SLAYER,
                FreeQuest.WITCHS_POTION,
                FreeQuest.RUNE_MYSTERIES
            };
            
            // Check each quest for state changes
            for (FreeQuest quest : freeQuests) {
                State currentState = quest.getState();
                State lastState = lastQuestStates.get(quest);
                
                if (lastState == null) {
                    // Initialize tracking
                    lastQuestStates.put(quest, currentState);
                    
                    // If quest is already started or finished, note it
                    if (currentState == State.STARTED) {
                        logStep("Quest tracking initialized", "// " + quest.toString() + " is currently IN PROGRESS");
                        logDetail("QUEST_TRACK_INIT", quest.toString() + " detected as started");
                        currentActiveQuest = quest;
                        script.log("[QUEST] ACTIVE QUEST DETECTED: " + quest.toString() + " (In Progress)");
                    } else if (currentState == State.FINISHED) {
                        logStep("Quest tracking initialized", "// " + quest.toString() + " is already COMPLETED");
                        logDetail("QUEST_TRACK_INIT", quest.toString() + " detected as finished");
                    }
                } else if (currentState != lastState) {
                    // QUEST STATE CHANGED!
                    lastQuestStates.put(quest, currentState);
                    
                    if (currentState == State.STARTED && lastState == State.NOT_STARTED) {
                        // Quest was just started!
                        logStep("[QUEST STARTED!]", "// " + quest.toString() + " has been started!");
                        logDetail("QUEST_STARTED", quest.toString() + " state changed from NOT_STARTED to STARTED");
                        currentActiveQuest = quest;
                        
                        script.log("=================================");
                        script.log("[QUEST STARTED]: " + quest.toString());
                        script.log("=================================");
                        
                    } else if (currentState == State.FINISHED && lastState == State.STARTED) {
                        // Quest was just completed!
                        logStep("[QUEST COMPLETED!]", "// " + quest.toString() + " has been completed!");
                        logDetail("QUEST_COMPLETED", quest.toString() + " state changed from STARTED to FINISHED");
                        
                        script.log("=================================");
                        script.log("[QUEST COMPLETED]: " + quest.toString());
                        script.log("=================================");
                        
                        // Clear active quest if this was the active one
                        if (currentActiveQuest == quest) {
                            currentActiveQuest = null;
                        }
                        
                        // Check for any other started quests to set as active
                        detectCurrentActiveQuest();
                        
                    } else {
                        // Other state changes
                        logStep("Quest State Change", "// " + quest.toString() + " changed from " + lastState + " to " + currentState);
                        logDetail("QUEST_STATE_CHANGE", quest.toString() + " state: " + lastState + " -> " + currentState);
                    }
                }
            }
            
            // Debug: Show current active quest periodically
            if (debugCounter % 300 == 0 && currentActiveQuest != null) {
                script.log("[QUEST] CURRENTLY TRACKING: " + currentActiveQuest.toString());
            }
            
        } catch (Exception e) {
            script.log("Error in universal quest detection: " + e.getMessage());
        }
    }
    
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
    
    // Helper method to determine if a config value indicates quest completion
    private boolean isQuestCompletionState(String questName, int configValue) {
        switch (questName) {
            case "Cooks_Assistant":
                return configValue >= 2; // Cook's Assistant completion value
            case "Vampire_Slayer":
                return configValue >= 3; // Vampire Slayer completion value
            case "Sheep_Shearer":
                return configValue >= 21; // Sheep Shearer completion value  
            case "Restless_Ghost":
                return configValue >= 5; // Restless Ghost completion value
            case "Romeo_Juliet":
                return configValue >= 100; // Romeo & Juliet completion value
            default:
                return configValue >= 100; // Generic high value for completion
        }
    }
    
    // 9. Widget/Interface Interactions (Fixed to avoid spam and detect quest completion)
    private void checkWidgetInteractions() {
        try {
            // Only check widgets occasionally to avoid spam (every 10 loops = ~3 seconds)
            if (debugCounter % 10 != 0) return;
            
            // Check if any interfaces are open that might be quest-related
            if (Widgets.isOpen()) {
                
                // PRIORITY 1: Quest completion dialog detection
                WidgetChild questComplete = Widgets.get(widget ->
                    widget != null && widget.isVisible() &&
                    widget.getText() != null && 
                    // Make quest completion detection more specific - exclude level up notifications
                    ((widget.getText().toLowerCase().contains("quest complete") ||
                      widget.getText().toLowerCase().contains("you have completed") ||
                      widget.getText().toLowerCase().contains("well done")) &&
                      // Exclude level-up notifications
                      !widget.getText().toLowerCase().contains("advanced your") &&
                      !widget.getText().toLowerCase().contains("level.") &&
                      !widget.getText().toLowerCase().contains("are now level") &&
                      // Only include if it mentions quest-related keywords
                      (widget.getText().toLowerCase().contains("quest") ||
                       widget.getText().toLowerCase().contains("task") ||
                       widget.getText().toLowerCase().contains("adventure")))
                );
                
                if (questComplete != null) {
                    // Add cooldown to prevent spam from level-up notifications
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastQuestCompletionTime > QUEST_COMPLETION_COOLDOWN) {
                        logStep("QUEST COMPLETION INTERFACE DETECTED!", 
                            "// Quest completion dialog found: " + questComplete.getText());
                        logDetail("QUEST_COMPLETION_WIDGET", "Quest completion dialog: " + questComplete.getText());
                        lastQuestCompletionTime = currentTime;
                    }
                    return; // Found completion, no need to check other widgets
                }
                
                // PRIORITY 2: Experience gained widgets (quest rewards) - FIXED: Filter out player chat
                WidgetChild xpWidget = Widgets.get(widget ->
                    widget != null && widget.isVisible() &&
                    widget.getText() != null && 
                    !widget.getText().trim().isEmpty() &&
                    // Filter out player chat messages (they contain color codes but aren't XP)
                    !widget.getText().contains("I just do") && // Player chat filter
                    !widget.getText().contains("im ") && // Common chat patterns
                    !widget.getText().contains("i am ") &&
                    !widget.getText().contains("you ") &&
                    !widget.getText().toLowerCase().contains("player says:") &&
                    // Only match actual XP reward patterns
                    (widget.getText().toLowerCase().contains("experience") ||
                     widget.getText().toLowerCase().contains("xp") ||
                     widget.getText().contains("gained") ||
                     widget.getText().contains("reward"))
                );
                
                if (xpWidget != null && !xpWidget.getText().equals(lastLoggedWidgetText)) {
                    // Additional validation: Only log if it looks like real XP
                    String xpText = xpWidget.getText();
                    if (isValidXPReward(xpText)) {
                        logStep("Experience reward detected", 
                            "// XP Reward: " + xpText);
                        logDetail("XP_REWARD", "Experience gained: " + xpText);
                        lastLoggedWidgetText = xpText;
                    }
                    return;
                }
                
                // PRIORITY 3: Quest journal/interface updates
                WidgetChild questJournal = Widgets.get(widget ->
                    widget != null && widget.isVisible() &&
                    widget.getText() != null && 
                    (widget.getText().toLowerCase().contains("quest") && 
                     !widget.getText().toLowerCase().contains("makeover")) // Filter out makeover mage
                );
                
                if (questJournal != null && !questJournal.getText().equals(lastLoggedWidgetText)) {
                    logStep("Quest interface update", 
                        "// Quest widget: " + questJournal.getText());
                    logDetail("QUEST_INTERFACE", "Quest-related widget: " + questJournal.getText());
                    lastLoggedWidgetText = questJournal.getText();
                }
            }
            
        } catch (Exception e) {
            // Silent fail to avoid spam
        }
    }
    
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
                    
                    lastCastSpell = spellName;
                    lastSpellCastTime = currentTime;
                }
            }
            
            // Update tracking variables
            lastActiveTab = currentTab;
            lastMagicTabState = isMagicTabOpen;
            
        } catch (Exception e) {
            // Silent fail to avoid spam in logs
        }
    }
    
    private boolean isMagicCastingAnimation(int animationId) {
        // Common magic casting animation IDs in OSRS
        switch (animationId) {
            case 711:   // Standard spell casting
            case 724:   // High level spell casting  
            case 1162:  // Ancient magic casting
            case 1978:  // Lunar magic casting
            case 8939:  // Bind spell casting
            case 708:   // Teleport casting
            case 9493:  // Enchant spell casting
            case 1818:  // Alchemy casting
                return true;
            default:
                return false;
        }
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
                lastCombatActionTime = currentTime;
            }
            
            // Update tracking variables
            lastCombatTarget = currentTarget;
            
        } catch (Exception e) {
            // Silent fail to avoid spam in logs
        }
    }
    
    private boolean isCombatAnimation(int animationId) {
        // Common combat animation IDs
        switch (animationId) {
            case 422:   // Punch/Unarmed combat
            case 393:   // Sword slash
            case 395:   // Sword stab
            case 400:   // Pickaxe swing (also combat)
            case 401:   // Axe swing (also combat)
            case 806:   // Bow shooting
            case 1979:  // Whip attack
            case 2080:  // Dagger stab
            case 390:   // Hammer/mace crush
                return true;
            default:
                return false;
        }
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
    private void checkCommonQuestInterfaces() {
        // DISABLED: This method was causing excessive widget spam
        // Only check for critical quest completion dialogs in main widget method
    }
    
    private void initializeQuestVarbits() {
        // Common F2P quest varbits (same as seen in your log)
        questVarbits.put("Cooks_Assistant", 29);
        questVarbits.put("Dorics_Quest", 101); // FIXED: Doric's Quest uses config 101, not 31
        questVarbits.put("Ernest_Chicken", 32);
        questVarbits.put("Goblin_Diplomacy", 62);
        questVarbits.put("Rune_Mysteries", 63);
        questVarbits.put("Witchs_Potion", 67);
        questVarbits.put("Pirates_Treasure", 71);
        questVarbits.put("Vampire_Slayer", 77);
        questVarbits.put("Restless_Ghost", 107);
        questVarbits.put("Knights_Sword", 122);
        questVarbits.put("Black_Knights", 130);
        questVarbits.put("Romeo_Juliet", 144);
        questVarbits.put("Imp_Catcher", 11);  // FIXED: Correct varbit for Imp Catcher
        questVarbits.put("Dragon_Slayer", 176);
        questVarbits.put("Sheep_Shearer", 179); // Keep original mapping for Sheep Shearer
        questVarbits.put("Demon_Slayer", 222);
        questVarbits.put("Prince_Ali", 273);
        
        script.log("QUEST LOGGER: Initialized " + questVarbits.size() + " quest varbits for tracking");
    }
    
    private int getCurrentVarbitValue(int varbitId) {
        try {
            // Use proper DreamBot API for varbit access with debugging
            int value = PlayerSettings.getBitValue(varbitId);
            
            // DEBUG: Extra logging for Cook's Assistant to track what's happening
            if (varbitId == 29 && debugCounter % 50 == 0) { // Cook's Assistant varbit
                logDetail("VARBIT_DEBUG", "Cook's Assistant varbit 29 = " + value);
            }
            
            return value;
        } catch (Exception e) {
            // Log the specific error for debugging
            logDetail("VARBIT_ERROR", "Failed to read varbit " + varbitId + ": " + e.getMessage());
            return 0; // Return 0 if varbit cannot be read
        }
    }
    
    private boolean isQuestComplete(int varbitValue, String questName) {
        // Enhanced quest completion detection with specific quest values
        switch (questName) {
            case "Sheep_Shearer":
                return varbitValue >= 21; // Sheep Shearer completes at 21
            case "Cooks_Assistant":
                return varbitValue >= 2; // Cook's Assistant completes at 2
            case "Rune_Mysteries":
                return varbitValue >= 6; // Rune Mysteries completes at 6
            case "Restless_Ghost":
                return varbitValue >= 5; // Restless Ghost completes at 5
            case "Romeo_Juliet":
                return varbitValue >= 100; // Romeo & Juliet completes at 100
            case "Demon_Slayer":
                return varbitValue >= 3; // Demon Slayer completes at 3
            default:
                return varbitValue >= 10; // Generic completion threshold for unknown quests
        }
    }
    
    // Enhanced log formatting for better DreamBot console integration
    private void logWithConsoleIntegration(String category, String message) {
        // Log to file
        logDetail(category, message);
        
        // Enhanced console output that matches DreamBot's log format
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        script.log("[" + timestamp + "] " + category + ": " + message);
    }
    
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
