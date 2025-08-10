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
        // Tutorial Island (Special tutorial)
        questInfo.put("TUTORIAL_ISLAND", new QuestInfo(
            "TUTORIAL_ISLAND",
            "Tutorial Island",
            1, // difficulty
            30, // estimated duration minutes
            new ArrayList<>(), // skill requirements
            new ArrayList<>() // no items required - tutorial provides everything
        ));
        
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

        // Demon Slayer
        questInfo.put("DEMON_SLAYER", new QuestInfo(
            "DEMON_SLAYER",
            "Demon Slayer",
            2, // difficulty
            20, // estimated duration minutes
            new ArrayList<>(),
            Arrays.asList("Bucket of water", "Bones x25")
        ));
        
        // Doric's Quest
        questInfo.put("DORICS_QUEST", new QuestInfo(
            "DORICS_QUEST",
            "Doric's Quest",
            1, // difficulty (very easy)
            5, // estimated duration minutes
            new ArrayList<>(), // skill requirements
            Arrays.asList("Copper ore", "Clay", "Iron ore") // required items
        ));
        
        // Pirate's Treasure
        questInfo.put("PIRATES_TREASURE", new QuestInfo(
            "PIRATES_TREASURE",
            "Pirate's Treasure",
            3, // difficulty (intermediate)
            20, // estimated duration minutes
            new ArrayList<>(), // skill requirements
            Arrays.asList("Karamjan rum", "White apron", "Spade", "30 gp") // required items
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

        // The Knight's Sword
        questInfo.put("THE_KNIGHTS_SWORD", new QuestInfo(
            "THE_KNIGHTS_SWORD",
            "The Knight's Sword",
            2, // difficulty
            20, // estimated duration minutes
            new ArrayList<>(),
            Arrays.asList("Redberry pie", "Iron bar x2", "Any pickaxe")
        ));
        
        // Rune Mysteries
        questInfo.put("RUNE_MYSTERIES", new QuestInfo(
            "RUNE_MYSTERIES",
            "Rune Mysteries",
            1, // difficulty
            8, // estimated duration minutes
            new ArrayList<>(), // skill requirements
            new ArrayList<>() // no items required to start (Air talisman given during quest)
        ));
        
        // Witch's Potion
        questInfo.put("WITCHS_POTION", new QuestInfo(
            "WITCHS_POTION",
            "Witch's Potion",
            1, // difficulty
            15, // estimated duration minutes
            new ArrayList<>(), // skill requirements
            Arrays.asList("Raw beef", "Eye of newt", "Onion") // required items
        ));
        
        // Romeo and Juliet
        questInfo.put("ROMEO_AND_JULIET", new QuestInfo(
            "ROMEO_AND_JULIET",
            "Romeo and Juliet",
            1, // difficulty
            12, // estimated duration minutes
            new ArrayList<>(), // skill requirements
            new ArrayList<>() // no items required to start
        ));
        
        // Sheep Shearer
        questInfo.put("SHEEP_SHEARER", new QuestInfo(
            "SHEEP_SHEARER",
            "Sheep Shearer",
            1, // difficulty
            8, // estimated duration minutes
            new ArrayList<>(), // skill requirements
            new ArrayList<>() // no items required to start
        ));
        
        // The Restless Ghost
        questInfo.put("THE_RESTLESS_GHOST", new QuestInfo(
            "THE_RESTLESS_GHOST",
            "The Restless Ghost",
            1, // difficulty
            10, // estimated duration minutes
            new ArrayList<>(), // skill requirements
            new ArrayList<>() // no items required to start (Ghostspeak amulet given during quest)
        ));
        
        // X Marks the Spot
        questInfo.put("X_MARKS_THE_SPOT", new QuestInfo(
            "X_MARKS_THE_SPOT",
            "X Marks the Spot",
            1, // difficulty
            8, // estimated duration minutes
            new ArrayList<>(), // skill requirements
            Arrays.asList("Spade") // required items - need a spade to dig
        ));

        // Goblin Diplomacy
        questInfo.put("GOBLIN_DIPLOMACY", new QuestInfo(
            "GOBLIN_DIPLOMACY",
            "Goblin Diplomacy",
            1, // difficulty
            8, // estimated duration minutes
            new ArrayList<>(), // skill requirements
            Arrays.asList("Orange dye", "Blue dye", "Goblin mail x3") // purchased via GE
        ));

        // Black Knights' Fortress
        questInfo.put("BLACK_KNIGHTS_FORTRESS", new QuestInfo(
            "BLACK_KNIGHTS_FORTRESS",
            "Black Knights' Fortress",
            2,
            12,
            new ArrayList<>(),
            Arrays.asList("Iron chainbody", "Bronze med helm", "Cabbage")
        ));

        // The Corsair Curse
        questInfo.put("THE_CORSAIR_CURSE", new QuestInfo(
            "THE_CORSAIR_CURSE",
            "The Corsair Curse",
            1, // difficulty
            12, // estimated duration minutes
            new ArrayList<>(),
            Arrays.asList("Spade") // based on log: used spade to dig
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
