package quest.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quest Database - Central registry for all quest information
 * Part of the AI Quest Framework v7.0
 */
public class QuestDatabase {
    
    private static final Map<String, QuestInfo> questInfo = new ConcurrentHashMap<>();
    
    static {
        // Cook's Assistant
        questInfo.put("COOKS_ASSISTANT", new QuestInfo(
            "COOKS_ASSISTANT",
            "Cook's Assistant", 
            1, // difficulty
            5, // estimated duration minutes
            new ArrayList<>(), // skill requirements
            Arrays.asList("Egg", "Bucket of milk", "Pot of flour") // required items
        ));
        
        // Vampire Slayer
        questInfo.put("VAMPIRE_SLAYER", new QuestInfo(
            "VAMPIRE_SLAYER",
            "Vampire Slayer",
            2, // difficulty
            15, // estimated duration minutes
            new ArrayList<>(), // skill requirements
            Arrays.asList("Hammer", "Garlic", "Stake") // required items
        ));
        
        // Imp Catcher
        questInfo.put("IMP_CATCHER", new QuestInfo(
            "IMP_CATCHER",
            "Imp Catcher",
            1, // difficulty
            10, // estimated duration minutes
            new ArrayList<>(), // skill requirements
            Arrays.asList("Red bead", "Yellow bead", "Black bead", "White bead") // required items
        ));
    }
    
    public static QuestInfo getQuestInfo(String questId) {
        return questInfo.get(questId);
    }
    
    public static boolean isQuestComplete(String questId) {
        // This would normally check game state via DreamBot API
        // For now, return false to allow testing
        return false;
    }
    
    public static Map<String, QuestInfo> getAllQuests() {
        return new HashMap<>(questInfo);
    }
    
    public static Set<String> getAvailableQuestIds() {
        return questInfo.keySet();
    }
    
    public static Set<String> getAllQuestIds() {
        return new HashSet<>(questInfo.keySet());
    }
    
    public static boolean isQuestRegistered(String questId) {
        return questInfo.containsKey(questId);
    }
    
    public static String getQuestStatus(String questId) {
        // This would check game state via DreamBot API
        // For now return "Available" for testing
        return "Available";
    }
    
    public static Collection<QuestInfo> getAllQuestValues() {
        return questInfo.values();
    }
    
    /**
     * Quest Information Container
     */
    public static class QuestInfo {
        private final String questId;
        private final String displayName;
        private final int difficulty;
        private final int estimatedDurationMinutes;
        private final List<String> skillRequirements;
        private final List<String> requiredItems;
        
        public QuestInfo(String questId, String displayName, int difficulty, 
                        int estimatedDurationMinutes, List<String> skillRequirements, 
                        List<String> requiredItems) {
            this.questId = questId;
            this.displayName = displayName;
            this.difficulty = difficulty;
            this.estimatedDurationMinutes = estimatedDurationMinutes;
            this.skillRequirements = new ArrayList<>(skillRequirements);
            this.requiredItems = new ArrayList<>(requiredItems);
        }
        
        // Getters
        public String getQuestId() { return questId; }
        public String getDisplayName() { return displayName; }
        public int getDifficulty() { return difficulty; }
        public int getEstimatedDurationMinutes() { return estimatedDurationMinutes; }
        public List<String> getSkillRequirements() { return new ArrayList<>(skillRequirements); }
        public List<String> getRequiredItems() { return new ArrayList<>(requiredItems); }
    }
}
