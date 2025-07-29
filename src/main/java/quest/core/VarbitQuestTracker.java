package quest.core;

import org.dreambot.api.methods.settings.PlayerSettings;
import org.dreambot.api.script.AbstractScript;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Advanced quest progress tracker using varbits with event-based monitoring
 * Implements "perfect medium" granularity for quest state capture
 */
public class VarbitQuestTracker {
    
    private final AbstractScript script;
    private Map<Integer, Integer> trackedVarbits;
    private Map<Integer, String> varbitNames;
    private Map<String, Integer> questVarbits;
    private Set<Integer> monitoredVarbitRanges;
    private long lastVarbitCheck;
    private static final long VARBIT_CHECK_INTERVAL = 500; // Check every 500ms for responsiveness
    
    // Enhanced quest varbit ranges for comprehensive monitoring
    private static final int[] QUEST_VARBIT_RANGES = {
        // Common quest varbit ranges
        25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, // Cook's Assistant area
        55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, // Extended Rune Mysteries area
        71, 72, 73, 74, 75, 76, 77, 78, 79, 80,     // Vampire Slayer area
        100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, // Restless Ghost area
        120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130, // Knight's Sword area
        140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 150, // Romeo & Juliet area
        175, 176, 177, 178, 179, 180, 181, 182, 183, 184, 185, // Sheep Shearer / Dragon Slayer area
        220, 221, 222, 223, 224, 225, 226, 227, 228, 229, 230, // Extended Demon Slayer area
        270, 271, 272, 273, 274, 275, 276, 277, 278, 279, 280, // Prince Ali area
        // Add comprehensive discovery ranges for unknown quest varbits
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
        36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54,
        81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99,
        111, 112, 113, 114, 115, 116, 117, 118, 119, 131, 132, 133, 134, 135, 136, 137, 138, 139,
        151, 152, 153, 154, 155, 156, 157, 158, 159, 161, 162, 163, 164, 165, 166, 167, 168, 169, 170,
        171, 172, 173, 174, 186, 187, 188, 189, 190, 191, 192, 193, 194, 195, 196, 197, 198, 199
    };
    
    public VarbitQuestTracker(AbstractScript script) {
        this.script = script;
        this.trackedVarbits = new HashMap<>();
        this.varbitNames = new HashMap<>();
        this.questVarbits = new HashMap<>();
        this.monitoredVarbitRanges = new HashSet<>();
        initializeQuestVarbits();
        initializeVarbitRanges();
    }
    
    private void initializeQuestVarbits() {
        // Verified quest varbits from OSRS Wiki and testing
        questVarbits.put("Cook's Assistant", 29);
        questVarbits.put("Demon Slayer", 222);
        questVarbits.put("Vampire Slayer", 77);
        questVarbits.put("Romeo & Juliet", 144);
        questVarbits.put("Sheep Shearer", 179);
        questVarbits.put("The Restless Ghost", 107);
        questVarbits.put("Ernest the Chicken", 32);
        questVarbits.put("Goblin Diplomacy", 62);
        questVarbits.put("Imp Catcher", 160);
        questVarbits.put("Pirate's Treasure", 71);
        questVarbits.put("Prince Ali Rescue", 273);
        questVarbits.put("Doric's Quest", 31);
        questVarbits.put("Black Knights' Fortress", 130);
        questVarbits.put("Witch's Potion", 67);
        questVarbits.put("Knight's Sword", 122);
        questVarbits.put("Rune Mysteries", 63);
        questVarbits.put("Dragon Slayer", 176);
        
        // Initialize descriptive names for event logging
        varbitNames.put(29, "Cooks_Assistant_Progress");
        varbitNames.put(222, "Demon_Slayer_Progress");
        varbitNames.put(77, "Vampire_Slayer_Progress");
        varbitNames.put(144, "Romeo_Juliet_Progress");
        varbitNames.put(179, "Sheep_Shearer_Progress");
        varbitNames.put(107, "Restless_Ghost_Progress");
        varbitNames.put(32, "Ernest_Chicken_Progress");
        varbitNames.put(62, "Goblin_Diplomacy_Progress");
        varbitNames.put(160, "Imp_Catcher_Progress");
        varbitNames.put(71, "Pirates_Treasure_Progress");
        varbitNames.put(273, "Prince_Ali_Progress");
        varbitNames.put(31, "Dorics_Quest_Progress");
        varbitNames.put(130, "Black_Knights_Progress");
        varbitNames.put(67, "Witchs_Potion_Progress");
        varbitNames.put(122, "Knights_Sword_Progress");
        varbitNames.put(63, "Rune_Mysteries_Progress");
        varbitNames.put(176, "Dragon_Slayer_Progress");
        
        // Add alternative Rune Mysteries varbit candidates for discovery
        varbitNames.put(5, "Rune_Mysteries_Alt_1");
        varbitNames.put(17, "Rune_Mysteries_Alt_2"); 
        varbitNames.put(41, "Rune_Mysteries_Alt_3");
        varbitNames.put(59, "Rune_Mysteries_Alt_4");
        varbitNames.put(281, "Rune_Mysteries_Alt_5");
    }
    
    private void initializeVarbitRanges() {
        // Add all potential quest varbits for comprehensive monitoring
        for (int varbitId : QUEST_VARBIT_RANGES) {
            monitoredVarbitRanges.add(varbitId);
        }
        script.log("VARBIT_MONITOR: Initialized tracking for " + monitoredVarbitRanges.size() + " potential quest varbits");
    }
    
    public void checkForVarbitChanges() {
        if (System.currentTimeMillis() - lastVarbitCheck < VARBIT_CHECK_INTERVAL) {
            return;
        }
        
        lastVarbitCheck = System.currentTimeMillis();
        
        // Primary quest varbit monitoring (high priority)
        for (Map.Entry<Integer, String> entry : varbitNames.entrySet()) {
            int varbitId = entry.getKey();
            String varbitName = entry.getValue();
            
            try {
                int currentValue = PlayerSettings.getConfig(varbitId);
                Integer previousValue = trackedVarbits.get(varbitId);
                
                if (previousValue == null || !previousValue.equals(currentValue)) {
                    trackedVarbits.put(varbitId, currentValue);
                    
                    if (previousValue != null) {
                        // Event-based logging with rich context
                        logVarbitChangeEvent(varbitId, varbitName, previousValue, currentValue);
                        analyzeQuestProgress(varbitId, previousValue, currentValue);
                    } else {
                        script.log("VARBIT_INIT: " + varbitName + " (" + varbitId + ") initialized to " + currentValue);
                    }
                }
            } catch (Exception e) {
                script.log("VARBIT_ERROR: Failed to read varbit " + varbitId + " - " + e.getMessage());
            }
        }
        
        // Discovery mode: Monitor unknown varbits in quest ranges
        monitorUnknownVarbits();
    }
    
    private void logVarbitChangeEvent(int varbitId, String varbitName, int oldValue, int newValue) {
        // Structured event logging with rich context
        String questName = getQuestNameFromVarbit(varbitId);
        String eventType = "VARBIT_CHANGE";
        
        // Create detailed event context
        StringBuilder eventLog = new StringBuilder();
        eventLog.append("{\n");
        eventLog.append("  \"timestamp\": \"").append(java.time.Instant.now().toString()).append("\",\n");
        eventLog.append("  \"eventType\": \"").append(eventType).append("\",\n");
        eventLog.append("  \"data\": {\n");
        eventLog.append("    \"varbitId\": ").append(varbitId).append(",\n");
        eventLog.append("    \"varbitName\": \"").append(varbitName).append("\",\n");
        eventLog.append("    \"oldValue\": ").append(oldValue).append(",\n");
        eventLog.append("    \"newValue\": ").append(newValue).append(",\n");
        eventLog.append("    \"questName\": \"").append(questName != null ? questName : "Unknown").append("\",\n");
        eventLog.append("    \"progressDirection\": \"").append(newValue > oldValue ? "ADVANCE" : "REGRESS").append("\",\n");
        eventLog.append("    \"significantChange\": ").append(Math.abs(newValue - oldValue) > 1).append("\n");
        eventLog.append("  }\n");
        eventLog.append("}");
        
        script.log("QUEST_VARBIT_EVENT: " + eventLog.toString());
    }
    
    private void monitorUnknownVarbits() {
        // Discovery mode: Look for unexpected varbit changes in quest ranges
        for (int varbitId : monitoredVarbitRanges) {
            if (!varbitNames.containsKey(varbitId)) { // Unknown varbit
                try {
                    int currentValue = PlayerSettings.getConfig(varbitId);
                    Integer previousValue = trackedVarbits.get(varbitId);
                    
                    if (previousValue != null && !previousValue.equals(currentValue)) {
                        // Discovered a new quest-related varbit!
                        script.log("VARBIT_DISCOVERY: Unknown varbit " + varbitId + " changed from " + 
                                  previousValue + " to " + currentValue + " - Potential quest progress!");
                        
                        // Auto-add to monitoring
                        varbitNames.put(varbitId, "Discovered_Varbit_" + varbitId);
                        
                        // Log rich discovery event for potential Rune Mysteries tracking
                        logVarbitChangeEvent(varbitId, "Discovered_Varbit_" + varbitId, previousValue, currentValue);
                    }
                    
                    trackedVarbits.put(varbitId, currentValue);
                } catch (Exception e) {
                    // Silently handle errors for discovery mode
                }
            }
        }
        
        // Enhanced Rune Mysteries detection - check if any discovered varbits correlate with quest events
        checkForRuneMysteryCorrelation();
    }
    
    private void checkForRuneMysteryCorrelation() {
        // Check if any recently discovered varbits might be Rune Mysteries progress
        try {
            // Look for varbits that changed during Rune Mysteries events
            for (Map.Entry<Integer, Integer> entry : trackedVarbits.entrySet()) {
                int varbitId = entry.getKey();
                int currentValue = entry.getValue();
                
                // If this varbit has a non-zero value and we haven't identified it yet
                if (currentValue > 0 && !questVarbits.containsValue(varbitId)) {
                    String varbitName = varbitNames.get(varbitId);
                    if (varbitName != null && varbitName.startsWith("Discovered_Varbit_")) {
                        script.log("RUNE_MYSTERIES_CANDIDATE: Varbit " + varbitId + " = " + currentValue + 
                                  " - Potential Rune Mysteries progress varbit!");
                    }
                }
            }
        } catch (Exception e) {
            // Handle any errors silently
        }
    }
    
    private void analyzeQuestProgress(int varbitId, int oldValue, int newValue) {
        String questName = getQuestNameFromVarbit(varbitId);
        if (questName != null) {
            // Enhanced progress analysis with quest-specific context
            String progressType = determineProgressType(questName, oldValue, newValue);
            String stageDescription = getQuestStageDescription(questName, newValue);
            
            if (newValue > oldValue) {
                script.log("QUEST_PROGRESS_ADVANCE: " + questName + " advanced from step " + oldValue + 
                          " to step " + newValue + " (" + progressType + ")");
                if (!stageDescription.isEmpty()) {
                    script.log("QUEST_STAGE_DESCRIPTION: " + stageDescription);
                }
            } else if (newValue < oldValue) {
                script.log("QUEST_PROGRESS_REGRESS: " + questName + " regressed from step " + oldValue + 
                          " to step " + newValue + " (possible reset or error)");
            }
            
            // Check for quest completion with enhanced validation
            if (isQuestComplete(questName)) {
                logQuestCompletionEvent(questName, newValue);
            }
            
            // Check for critical quest milestones
            checkQuestMilestones(questName, oldValue, newValue);
        }
    }
    
    private String determineProgressType(String questName, int oldValue, int newValue) {
        // Determine the type of progress based on quest and value changes
        int change = newValue - oldValue;
        
        if (change == 1) {
            return "Normal Step";
        } else if (change > 1) {
            return "Multiple Steps";
        } else if (change == -1) {
            return "Step Back";
        } else {
            return "Major Change";
        }
    }
    
    private String getQuestStageDescription(String questName, int stageValue) {
        // Provide human-readable descriptions for quest stages
        switch (questName) {
            case "Cook's Assistant":
                return getCooksAssistantStageDescription(stageValue);
            case "Romeo & Juliet":
                return getRomeoJulietStageDescription(stageValue);
            case "Sheep Shearer":
                return getSheepShearerStageDescription(stageValue);
            case "Vampire Slayer":
                return getVampireSlayerStageDescription(stageValue);
            case "Rune Mysteries":
                return getRuneMysteriesStageDescription(stageValue);
            default:
                return "Stage " + stageValue;
        }
    }
    
    private String getCooksAssistantStageDescription(int stage) {
        switch (stage) {
            case 0: return "Quest not started";
            case 1: return "Talked to Cook, quest accepted";
            case 2: return "Need to collect ingredients";
            case 3: return "Collected some ingredients";
            case 4: return "Collected all ingredients";
            case 5: return "Returning to Cook";
            case 10: return "Quest completed";
            default: return "Unknown stage: " + stage;
        }
    }
    
    private String getRomeoJulietStageDescription(int stage) {
        switch (stage) {
            case 0: return "Quest not started";
            case 1: return "Talked to Romeo";
            case 2: return "Need to find Juliet";
            case 3: return "Talked to Juliet";
            case 4: return "Need Cadava berries";
            case 5: return "Have berries, returning to Juliet";
            case 10: return "Quest completed";
            default: return "Unknown stage: " + stage;
        }
    }
    
    private String getSheepShearerStageDescription(int stage) {
        switch (stage) {
            case 0: return "Quest not started";
            case 1: return "Talked to Fred the Farmer";
            case 2: return "Need to shear sheep";
            case 3: return "Collecting wool";
            case 4: return "Have some wool";
            case 5: return "Have all 20 wool";
            case 10: return "Quest completed";
            default: return "Unknown stage: " + stage;
        }
    }
    
    private String getVampireSlayerStageDescription(int stage) {
        switch (stage) {
            case 0: return "Quest not started";
            case 1: return "Talked to Morgan";
            case 2: return "Need garlic and stake";
            case 3: return "Have items, going to vampire";
            case 4: return "Fighting vampire";
            case 10: return "Quest completed";
            default: return "Unknown stage: " + stage;
        }
    }
    
    private String getRuneMysteriesStageDescription(int stage) {
        switch (stage) {
            case 0: return "Quest not started";
            case 1: return "Talked to Duke Horacio, received air talisman";
            case 2: return "Need to deliver talisman to Sedridor";
            case 3: return "Delivered talisman, received research package";
            case 4: return "Need to deliver package to Aubury in Varrock";
            case 5: return "Delivered package to Aubury";
            case 6: return "Need to return to Sedridor";
            case 7: return "Returning to Sedridor with findings";
            case 10: return "Quest completed - can access rune essence mine";
            default: return "Unknown stage: " + stage;
        }
    }
    
    private void logQuestCompletionEvent(String questName, int finalValue) {
        // Rich event logging for quest completion
        StringBuilder completionLog = new StringBuilder();
        completionLog.append("{\n");
        completionLog.append("  \"timestamp\": \"").append(java.time.Instant.now().toString()).append("\",\n");
        completionLog.append("  \"eventType\": \"QUEST_COMPLETION\",\n");
        completionLog.append("  \"data\": {\n");
        completionLog.append("    \"questName\": \"").append(questName).append("\",\n");
        completionLog.append("    \"finalVarbitValue\": ").append(finalValue).append(",\n");
        completionLog.append("    \"completionConfirmed\": true\n");
        completionLog.append("  }\n");
        completionLog.append("}");
        
        script.log("QUEST_COMPLETION_EVENT: " + completionLog.toString());
        script.log("ACHIEVEMENT_UNLOCKED: " + questName + " has been completed!");
    }
    
    private void checkQuestMilestones(String questName, int oldValue, int newValue) {
        // Check for significant quest milestones
        int[] criticalValues = {1, 3, 5, 7, 10}; // Common critical quest stages
        
        for (int critical : criticalValues) {
            if (oldValue < critical && newValue >= critical) {
                script.log("QUEST_MILESTONE_REACHED: " + questName + " reached critical stage " + critical);
            }
        }
    }
    
    private String getQuestNameFromVarbit(int varbitId) {
        for (Map.Entry<String, Integer> entry : questVarbits.entrySet()) {
            if (entry.getValue().equals(varbitId)) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    public boolean isQuestComplete(String questName) {
        // Use varbit checking for quest completion
        Integer varbitId = questVarbits.get(questName);
        if (varbitId != null) {
            int value = PlayerSettings.getConfig(varbitId);
            return value >= 10; // Most quests complete at varbit value 10 or higher
        }
        return false;
    }
    
    public int getQuestProgress(String questName) {
        Integer varbitId = questVarbits.get(questName);
        if (varbitId != null) {
            return PlayerSettings.getConfig(varbitId);
        }
        return -1;
    }
    
    public void startTracking(String questName) {
        Integer varbitId = questVarbits.get(questName);
        if (varbitId != null) {
            int currentValue = PlayerSettings.getConfig(varbitId);
            trackedVarbits.put(varbitId, currentValue);
            script.log("TRACKING_START: Now tracking " + questName + " (varbit " + varbitId + ") starting at value " + currentValue);
        } else {
            script.log("TRACKING_ERROR: Unknown quest varbit for " + questName);
        }
    }
    
    public void addCustomVarbit(int varbitId, String name) {
        varbitNames.put(varbitId, name);
        int currentValue = PlayerSettings.getConfig(varbitId);
        trackedVarbits.put(varbitId, currentValue);
        script.log("CUSTOM_VARBIT_ADDED: " + name + " (" + varbitId + ") with value " + currentValue);
    }
    
    /**
     * Get comprehensive quest state information for logging context
     */
    public String getQuestStateContext(String questName) {
        Integer varbitId = questVarbits.get(questName);
        if (varbitId != null) {
            int progress = PlayerSettings.getConfig(varbitId);
            String stage = getQuestStageDescription(questName, progress);
            boolean complete = isQuestComplete(questName);
            
            return String.format("Quest: %s, Progress: %d, Stage: %s, Complete: %s", 
                                questName, progress, stage, complete);
        }
        return "Quest state unknown for: " + questName;
    }
    
    /**
     * Force a comprehensive varbit scan for debugging
     */
    public void performComprehensiveVarbitScan() {
        script.log("VARBIT_SCAN_START: Performing comprehensive varbit analysis...");
        int changesFound = 0;
        
        for (int varbitId : QUEST_VARBIT_RANGES) {
            try {
                int value = PlayerSettings.getConfig(varbitId);
                if (value > 0) {
                    script.log("VARBIT_SCAN_RESULT: Varbit " + varbitId + " = " + value);
                    changesFound++;
                }
            } catch (Exception e) {
                // Continue scanning even if individual varbit fails
            }
        }
        
        script.log("VARBIT_SCAN_COMPLETE: Found " + changesFound + " active varbits");
    }
}