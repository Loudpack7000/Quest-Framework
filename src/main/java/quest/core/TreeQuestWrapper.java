package quest.core;

import org.dreambot.api.utilities.Logger;
import quest.utils.QuestLogger;
import quest.utils.RunEnergyUtil;

/**
 * Wrapper class to make tree-based quests compatible with the existing QuestScript interface
 * This allows tree-based quests to work with the existing QuestExecutor system
 */
public class TreeQuestWrapper implements QuestScript {
    
    private final QuestTree questTree;
    private final String questId;
    private final String questName;
    
    public TreeQuestWrapper(QuestTree questTree, String questId, String questName) {
        this.questTree = questTree;
        this.questId = questId;
        this.questName = questName;
    }
    
    @Override
    public String getQuestName() {
        return questName;
    }
    
    @Override
    public String getQuestId() {
        return questId;
    }
    
    @Override
    public void initialize(org.dreambot.api.script.AbstractScript script, QuestDatabase database) {
        // Tree-based quests don't need additional initialization
        Logger.log("Tree-based quest initialized: " + questName);
    }
    
    @Override
    public boolean canStart() {
        // Tree-based quests can always start
        return true;
    }
    
    @Override
    public boolean startQuest() {
        Logger.log("=== STARTING TREE-BASED QUEST: " + questName + " ===");
        Logger.log("Quest tree initialized and ready for execution");
        return true;
    }
    
    @Override
    public boolean executeCurrentStep() {
        try {
            // Ensure run energy is managed every loop (energy pots + run toggle)
            RunEnergyUtil.handleRunEnergy();

            // Execute one iteration of the quest tree
            boolean shouldContinue = questTree.execute();
            
            if (questTree.isQuestComplete()) {
                Logger.log("=== QUEST COMPLETED: " + questName + " ===");
                return false; // Quest is done
            }
            
            if (questTree.isQuestFailed()) {
                Logger.log("=== QUEST FAILED: " + questName + " ===");
                return false; // Quest failed
            }
            
            return shouldContinue; // Continue if tree says so
            
        } catch (Exception e) {
            Logger.log("ERROR in tree-based quest execution: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    @Override
    public boolean hasRequiredItems() {
        // Tree-based quests handle their own item requirements
        // For now, assume all items are available (they can be bought via GE)
        return true;
    }
    
    @Override
    public String[] getRequiredItems() {
        // Return empty array - tree-based quests handle their own requirements
        return new String[0];
    }
    
    @Override
    public boolean handleDialogue() {
        // Tree-based quests handle dialogue through their nodes
        return true;
    }
    
    @Override
    public boolean navigateToObjective() {
        // Tree-based quests handle navigation through their nodes
        return true;
    }
    
    @Override
    public void cleanup() {
        Logger.log("Tree-based quest cleanup: " + questName);
    }
    
    @Override
    public boolean isComplete() {
        return questTree.isQuestComplete();
    }
    
    @Override
    public int getCurrentProgress() {
        return questTree.getQuestProgress();
    }
    
    @Override
    public String getCurrentStepDescription() {
        return questTree.getCurrentNodeDescription();
    }
    
    @Override
    public void onQuestStart() {
        Logger.log("=== STARTING TREE-BASED QUEST: " + questName + " ===");
        Logger.log("Quest tree initialized and ready for execution");
        // Initialize quest logger and capture starting inventory snapshot
        try {
            QuestLogger.getInstance().initializeQuest(questName);
            QuestLogger.getInstance().logInventorySnapshot("Starting inventory");
            Logger.log("[QuestLogger] Writing to file: " + QuestLogger.getInstance().getCurrentLogFile());
        } catch (Throwable t) {
            Logger.log("QuestLogger init failed: " + t.getMessage());
        }
    }
    
    @Override
    public void onQuestComplete() {
        Logger.log("=== TREE-BASED QUEST FINISHED: " + questName + " ===");
    }
    
    public void reset() {
        questTree.reset();
        Logger.log("Tree-based quest reset: " + questName);
    }
} 