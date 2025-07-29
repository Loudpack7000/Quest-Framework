package quest.utils;

import quest.utils.DialogueUtil.DialogueStep;
import quest.utils.GrandExchangeUtil.ItemRequest;
import quest.utils.GrandExchangeUtil.PriceStrategy;
import java.util.*;

/**
 * Quest Data Storage System
 * Centralized storage for quest-specific dialogue sequences and item requirements
 */
public class QuestData {
    
    // Storage for quest dialogue sequences
    private static final Map<String, Map<String, List<DialogueStep>>> questDialogues = new HashMap<>();
    
    // Storage for quest item requirements
    private static final Map<String, List<ItemRequest>> questItems = new HashMap<>();
    
    // Storage for quest NPCs and their locations
    private static final Map<String, Map<String, NPCInfo>> questNPCs = new HashMap<>();
    
    static {
        initializeQuestData();
    }
    
    /**
     * Initialize all quest data
     */
    private static void initializeQuestData() {
        initializeCooksAssistant();
        initializeImpCatcher();
        initializeVampireSlayer();
        // Add more quests as needed
    }
    
    /**
     * Cook's Assistant quest data
     */
    private static void initializeCooksAssistant() {
        String questName = "Cook's Assistant";
        
        // Dialogue sequences
        Map<String, List<DialogueStep>> dialogues = new HashMap<>();
        
        // Starting dialogue with Cook
        dialogues.put("start_quest", Arrays.asList(
            DialogueStep.continueDialogue("Initial dialogue"),
            DialogueStep.selectOption("Yes.", "Accept quest"),
            DialogueStep.continueDialogue("Quest instructions"),
            DialogueStep.continueDialogue("Final confirmation")
        ));
        
        // Finishing dialogue with Cook
        dialogues.put("complete_quest", Arrays.asList(
            DialogueStep.continueDialogue("Hand in items"),
            DialogueStep.continueDialogue("Quest completion"),
            DialogueStep.continueDialogue("Rewards received")
        ));
        
        questDialogues.put(questName, dialogues);
        
        // Required items
        List<ItemRequest> items = Arrays.asList(
            new ItemRequest("Egg", 1, PriceStrategy.CONSERVATIVE),
            new ItemRequest("Bucket of milk", 1, PriceStrategy.CONSERVATIVE),
            new ItemRequest("Pot of flour", 1, PriceStrategy.CONSERVATIVE)
        );
        questItems.put(questName, items);
        
        // NPCs
        Map<String, NPCInfo> npcs = new HashMap<>();
        npcs.put("Cook", new NPCInfo("Cook", 3207, 3214, 0, "Lumbridge Castle kitchen"));
        questNPCs.put(questName, npcs);
    }
    
    /**
     * Imp Catcher quest data
     */
    private static void initializeImpCatcher() {
        String questName = "Imp Catcher";
        
        // Dialogue sequences
        Map<String, List<DialogueStep>> dialogues = new HashMap<>();
        
        // Starting dialogue with Wizard Mizgog
        dialogues.put("start_quest", Arrays.asList(
            DialogueStep.continueDialogue("Initial greeting"),
            DialogueStep.selectOption("Yes, certainly.", "Accept imp hunting task"),
            DialogueStep.continueDialogue("Task explanation"),
            DialogueStep.continueDialogue("Bead requirements"),
            DialogueStep.continueDialogue("Final instructions")
        ));
        
        // Completing quest with all beads
        dialogues.put("complete_quest", Arrays.asList(
            DialogueStep.continueDialogue("Present beads"),
            DialogueStep.continueDialogue("Wizard's gratitude"),
            DialogueStep.continueDialogue("Reward explanation"),
            DialogueStep.continueDialogue("Quest complete")
        ));
        
        questDialogues.put(questName, dialogues);
        
        // Required items (beads can be hunted or bought)
        List<ItemRequest> items = Arrays.asList(
            new ItemRequest("Red bead", 1, PriceStrategy.MODERATE),
            new ItemRequest("Yellow bead", 1, PriceStrategy.MODERATE),
            new ItemRequest("Black bead", 1, PriceStrategy.MODERATE),
            new ItemRequest("White bead", 1, PriceStrategy.MODERATE)
        );
        questItems.put(questName, items);
        
        // NPCs
        Map<String, NPCInfo> npcs = new HashMap<>();
        npcs.put("Wizard Mizgog", new NPCInfo("Wizard Mizgog", 3104, 3164, 1, "Wizards' Tower"));
        questNPCs.put(questName, npcs);
    }
    
    /**
     * Vampire Slayer quest data
     */
    private static void initializeVampireSlayer() {
        String questName = "Vampire Slayer";
        
        // Dialogue sequences
        Map<String, List<DialogueStep>> dialogues = new HashMap<>();
        
        // Starting dialogue with Morgan
        dialogues.put("start_quest", Arrays.asList(
            DialogueStep.continueDialogue("Morgan's request"),
            DialogueStep.selectOption("Yes, I'm up for an adventure.", "Accept vampire quest"),
            DialogueStep.continueDialogue("Quest details"),
            DialogueStep.continueDialogue("Preparation needed")
        ));
        
        // Dialogue with Dr. Harlow for garlic
        dialogues.put("get_garlic", Arrays.asList(
            DialogueStep.selectOption("I need some garlic.", "Request garlic"),
            DialogueStep.continueDialogue("Dr. Harlow's response"),
            DialogueStep.continueDialogue("Receive garlic")
        ));
        
        questDialogues.put(questName, dialogues);
        
        // Required items
        List<ItemRequest> items = Arrays.asList(
            new ItemRequest("Hammer", 1, PriceStrategy.CONSERVATIVE),
            new ItemRequest("Beer", 1, PriceStrategy.CONSERVATIVE) // For Dr. Harlow
        );
        questItems.put(questName, items);
        
        // NPCs
        Map<String, NPCInfo> npcs = new HashMap<>();
        npcs.put("Morgan", new NPCInfo("Morgan", 3097, 3266, 0, "Draynor Village"));
        npcs.put("Dr. Harlow", new NPCInfo("Dr. Harlow", 3225, 3245, 0, "Blue Moon Inn, Varrock"));
        questNPCs.put(questName, npcs);
    }
    
    /**
     * Get dialogue sequence for a quest and scenario
     */
    public static List<DialogueStep> getDialogueSequence(String questName, String scenario) {
        Map<String, List<DialogueStep>> questDialogue = questDialogues.get(questName);
        if (questDialogue != null) {
            return questDialogue.get(scenario);
        }
        return new ArrayList<>();
    }
    
    /**
     * Get all dialogue scenarios for a quest
     */
    public static Set<String> getDialogueScenarios(String questName) {
        Map<String, List<DialogueStep>> questDialogue = questDialogues.get(questName);
        if (questDialogue != null) {
            return questDialogue.keySet();
        }
        return new HashSet<>();
    }
    
    /**
     * Get required items for a quest
     */
    public static List<ItemRequest> getRequiredItems(String questName) {
        return questItems.getOrDefault(questName, new ArrayList<>());
    }
    
    /**
     * Get NPC information for a quest
     */
    public static NPCInfo getNPCInfo(String questName, String npcName) {
        Map<String, NPCInfo> questNPCMap = questNPCs.get(questName);
        if (questNPCMap != null) {
            return questNPCMap.get(npcName);
        }
        return null;
    }
    
    /**
     * Get all NPCs for a quest
     */
    public static Map<String, NPCInfo> getQuestNPCs(String questName) {
        return questNPCs.getOrDefault(questName, new HashMap<>());
    }
    
    /**
     * Add new dialogue sequence for a quest
     */
    public static void addDialogueSequence(String questName, String scenario, List<DialogueStep> steps) {
        questDialogues.computeIfAbsent(questName, k -> new HashMap<>()).put(scenario, steps);
    }
    
    /**
     * Add required items for a quest
     */
    public static void addRequiredItems(String questName, List<ItemRequest> items) {
        questItems.put(questName, items);
    }
    
    /**
     * Add NPC information for a quest
     */
    public static void addQuestNPC(String questName, String npcName, NPCInfo npcInfo) {
        questNPCs.computeIfAbsent(questName, k -> new HashMap<>()).put(npcName, npcInfo);
    }
    
    /**
     * Get all available quests
     */
    public static Set<String> getAvailableQuests() {
        Set<String> allQuests = new HashSet<>();
        allQuests.addAll(questDialogues.keySet());
        allQuests.addAll(questItems.keySet());
        allQuests.addAll(questNPCs.keySet());
        return allQuests;
    }
    
    /**
     * Calculate total cost for quest items
     */
    public static int calculateQuestCost(String questName) {
        List<ItemRequest> items = getRequiredItems(questName);
        return GrandExchangeUtil.calculateTotalCost(items.toArray(new ItemRequest[0]));
    }
    
    /**
     * Check if player can afford quest items
     */
    public static boolean canAffordQuest(String questName) {
        List<ItemRequest> items = getRequiredItems(questName);
        return GrandExchangeUtil.hasEnoughCoins(items.toArray(new ItemRequest[0]));
    }
    
    /**
     * NPC Information Data Class
     */
    public static class NPCInfo {
        private final String name;
        private final int x;
        private final int y;
        private final int z;
        private final String location;
        
        public NPCInfo(String name, int x, int y, int z, String location) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.location = location;
        }
        
        // Getters
        public String getName() { return name; }
        public int getX() { return x; }
        public int getY() { return y; }
        public int getZ() { return z; }
        public String getLocation() { return location; }
        
        @Override
        public String toString() {
            return name + " at " + location + " (" + x + ", " + y + ", " + z + ")";
        }
    }
}
