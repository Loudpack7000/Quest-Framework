package quest.core;

import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.script.AbstractScript;

/**
 * QuestScript Interface - Contract for all automated quest implementations
 * Part of the AI Quest Framework v7.0
 */
public interface QuestScript {
    
    /**
     * Initialize the quest script with necessary dependencies
     */
    void initialize(AbstractScript script, QuestDatabase database);
    
    /**
     * Get the quest identifier
     */
    String getQuestId();
    
    /**
     * Get the quest display name
     */
    String getQuestName();
    
    /**
     * Check if the quest can be started (requirements met)
     */
    boolean canStart();
    
    /**
     * Start the quest
     */
    boolean startQuest();
    
    /**
     * Execute the current step of the quest
     */
    boolean executeCurrentStep();
    
    /**
     * Get the current progress (0-100)
     */
    int getCurrentProgress();
    
    /**
     * Check if the quest is complete
     */
    boolean isComplete();
    
    /**
     * Get the current step description
     */
    String getCurrentStepDescription();
    
    /**
     * Check if the player has all required items
     */
    boolean hasRequiredItems();
    
    /**
     * Get list of required items for current step
     */
    String[] getRequiredItems();
    
    /**
     * Handle quest dialogue
     */
    boolean handleDialogue();
    
    /**
     * Navigate to the current objective location
     */
    boolean navigateToObjective();
    
    /**
     * Clean up resources when quest ends
     */
    void cleanup();
    
    /**
     * Called when quest starts
     */
    void onQuestStart();
    
    /**
     * Called when quest completes
     */
    void onQuestComplete();
}
