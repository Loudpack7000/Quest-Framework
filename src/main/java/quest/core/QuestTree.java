package quest.core;

import org.dreambot.api.utilities.Logger;
import quest.utils.QuestLogger;

/**
 * Base class for all quest trees
 * Handles tree execution, state management, and logging
 */
public abstract class QuestTree {
    
    protected final String questName;
    protected final QuestLogger questLogger;
    protected QuestNode rootNode;
    protected QuestNode currentNode;
    protected boolean questCompleted;
    protected boolean questFailed;
    
    public QuestTree(String questName) {
        this.questName = questName;
        this.questLogger = QuestLogger.getInstance();
        this.questCompleted = false;
        this.questFailed = false;
        
        // Build the quest tree structure
        buildTree();
        
        Logger.log("=== QUEST TREE INITIALIZED: " + questName + " ===");
        Logger.log("Root node: " + (rootNode != null ? rootNode.getDescription() : "NULL"));
    }
    
    /**
     * Abstract method - each quest must implement its own tree structure
     */
    protected abstract void buildTree();
    
    /**
     * Execute one iteration of the quest tree
     * @return true if quest should continue, false if completed or failed
     */
    public boolean execute() {
        if (questCompleted) {
            Logger.log("Quest " + questName + " already completed!");
            return false;
        }
        
        if (questFailed) {
            Logger.log("Quest " + questName + " has failed!");
            return false;
        }
        
        if (currentNode == null) {
            currentNode = rootNode;
        }
        
        if (currentNode == null) {
            Logger.log("ERROR: No current node to execute!");
            questFailed = true;
            return false;
        }
        
        try {
            Logger.log("=== EXECUTING NODE: " + currentNode.getDescription() + " ===");
            
            // Execute the current node
            QuestNode.ExecutionResult result = currentNode.execute();
            
            switch (result.getStatus()) {
                case SUCCESS:
                    Logger.log("[SUCCESS] Node completed: " + currentNode.getDescription());
                    QuestNode nextNode = result.getNextNode();
                    
                    if (nextNode == null) {
                        Logger.log("=== QUEST COMPLETED: " + questName + " ===");
                        questCompleted = true;
                        return false;
                    } else {
                        currentNode = nextNode;
                        Logger.log("Moving to next node: " + nextNode.getDescription());
                    }
                    break;
                    
                case FAILED:
                    Logger.log("[FAILED] Node failed: " + currentNode.getDescription());
                    Logger.log("Failure reason: " + result.getFailureReason());
                    questFailed = true;
                    return false;
                    
                case IN_PROGRESS:
                    Logger.log("[IN_PROGRESS] Node still executing: " + currentNode.getDescription());
                    break;
                    
                case RETRY:
                    Logger.log("[RETRY] Node needs retry: " + currentNode.getDescription());
                    break;
            }
            
            return true;
            
        } catch (Exception e) {
            Logger.log("CRITICAL ERROR in quest tree execution: " + e.getMessage());
            e.printStackTrace();
            questFailed = true;
            return false;
        }
    }
    
    /**
     * Get the current quest progress as a percentage
     */
    public abstract int getQuestProgress();
    
    /**
     * Check if the quest is complete
     */
    public boolean isQuestComplete() {
        return questCompleted;
    }
    
    /**
     * Check if the quest has failed
     */
    public boolean isQuestFailed() {
        return questFailed;
    }
    
    /**
     * Get the quest name
     */
    public String getQuestName() {
        return questName;
    }
    
    /**
     * Get the current node description for debugging
     */
    public String getCurrentNodeDescription() {
        return currentNode != null ? currentNode.getDescription() : "No current node";
    }
    
    /**
     * Reset the quest tree to start from beginning
     */
    public void reset() {
        currentNode = rootNode;
        questCompleted = false;
        questFailed = false;
        Logger.log("Quest tree reset: " + questName);
    }
    
    /**
     * Force move to a specific node (for debugging/testing)
     */
    public void setCurrentNode(QuestNode node) {
        this.currentNode = node;
        Logger.log("Manually set current node to: " + node.getDescription());
    }
}