package quest.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple Quest Mapper
 * Maps quest display names to internal quest IDs for GUI integration
 */
public class SimpleQuestMapper {
    
    private static final Map<String, String> DISPLAY_NAME_TO_ID = new HashMap<>();
    private static final Map<String, String> ID_TO_DISPLAY_NAME = new HashMap<>();
    
    static {
        // Initialize quest mappings
        addMapping("COOKS_ASSISTANT", "Cook's Assistant");
        addMapping("VAMPIRE_SLAYER", "Vampire Slayer");
        addMapping("DEMON_SLAYER", "Demon Slayer");
        addMapping("IMP_CATCHER", "Imp Catcher");
    addMapping("GOBLIN_DIPLOMACY", "Goblin Diplomacy");
    addMapping("BLACK_KNIGHTS_FORTRESS", "Black Knights' Fortress");
    addMapping("THE_CORSAIR_CURSE", "The Corsair Curse");
    }
    
    /**
     * Add a quest mapping
     */
    private static void addMapping(String questId, String displayName) {
        DISPLAY_NAME_TO_ID.put(displayName, questId);
        ID_TO_DISPLAY_NAME.put(questId, displayName);
    }
    
    /**
     * Get quest ID from display name
     */
    public static String getQuestId(String displayName) {
        return DISPLAY_NAME_TO_ID.get(displayName);
    }
    
    /**
     * Get display name from quest ID
     */
    public static String getDisplayName(String questId) {
        return ID_TO_DISPLAY_NAME.get(questId);
    }
    
    /**
     * Check if a quest exists by display name
     */
    public static boolean hasQuest(String displayName) {
        return DISPLAY_NAME_TO_ID.containsKey(displayName);
    }
    
    /**
     * Get all available quest display names
     */
    public static String[] getAllQuestNames() {
        return DISPLAY_NAME_TO_ID.keySet().toArray(new String[0]);
    }
}
