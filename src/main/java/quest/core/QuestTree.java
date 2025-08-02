package quest.core;

import org.dreambot.api.utilities.Logger;

/**
 * Base class for tree-based quest implementations
 * Provides a structured approach to quest execution using decision trees
 */
public abstract class QuestTree {
    
    protected final String questName;
    protected QuestNode rootNode;
    protected QuestNode currentNode;
    protected boolean questComplete = false;
    protected boolean questFailed = false;
    protected String failureReason = null;
    
    public QuestTree(String questName) {
        this.questName = questName;
        buildTree();
    }
    
    /**
     * Build the quest decision tree - must be implemented by subclasses
     */
    protected abstract void buildTree();
    
    /**
     * Execute one iteration of the quest tree
     * @return true if quest should continue, false if quest is complete/failed
     */
    public boolean execute() {
        try {
            // Initialize current node if not set
            if (currentNode == null) {
                currentNode = rootNode;
            }
            
            if (currentNode == null) {
                log("ERROR: No current node to execute");
                questFailed = true;
                failureReason = "No current node to execute";
                return false;
            }
            
            log("Executing node: " + currentNode.getDescription());
            QuestNode.ExecutionResult result = currentNode.execute();
            
            if (result.isSuccess()) {
                QuestNode nextNode = result.getNextNode();
                if (nextNode != null) {
                    log("Moving to next node: " + nextNode.getDescription());
                    currentNode = nextNode;
                } else {
                    // Return to root node (smart decision node)
                    log("Action completed, returning to root decision node");
                    currentNode = rootNode;
                }
                return true; // Continue executing
                
            } else if (result.isFailed()) {
                log("Quest failed: " + result.getFailureReason());
                questFailed = true;
                failureReason = result.getFailureReason();
                return false;
                
            } else if (result.isRetry()) {
                log("Retrying current node: " + result.getStatusMessage());
                // Keep current node the same for retry
                return true;
                
            } else if (result.isInProgress()) {
                log("Node in progress: " + result.getStatusMessage());
                return true;
            }
            
            // Unknown result status
            log("Unknown execution result status: " + result.getStatus());
            return true;
            
        } catch (Exception e) {
            log("Exception during quest tree execution: " + e.getMessage());
            e.printStackTrace();
            questFailed = true;
            failureReason = "Exception during execution: " + e.getMessage();
            return false;
        }
    }
    
    /**
     * Check if the quest is complete
     */
    public boolean isQuestComplete() {
        return questComplete;
    }
    
    /**
     * Check if the quest has failed
     */
    public boolean isQuestFailed() {
        return questFailed;
    }
    
    /**
     * Get the failure reason if quest failed
     */
    public String getFailureReason() {
        return failureReason;
    }
    
    /**
     * Get the quest name
     */
    public String getQuestName() {
        return questName;
    }
    
    /**
     * Reset the quest tree to initial state
     */
    public void reset() {
        currentNode = rootNode;
        questComplete = false;
        questFailed = false;
        failureReason = null;
        log("Quest tree reset to initial state");
    }
    
    /**
     * Mark the quest as complete
     */
    protected void setQuestComplete() {
        questComplete = true;
        log("Quest marked as complete: " + questName);
    }
    
    /**
     * Mark the quest as failed
     */
    protected void setQuestFailed(String reason) {
        questFailed = true;
        failureReason = reason;
        log("Quest marked as failed: " + questName + " - " + reason);
    }
    
    /**
     * Logging method for quest trees
     */
    protected void log(String message) {
        String logMessage = "[" + questName + "] " + message;
        Logger.log(logMessage);
    }
    
    /**
     * Get current node for debugging
     */
    public QuestNode getCurrentNode() {
        return currentNode;
    }
    
    /**
     * Get root node for debugging
     */
    public QuestNode getRootNode() {
        return rootNode;
    }
    
    /**
     * Get quest progress as a percentage (0-100)
     * Subclasses should override this to provide quest-specific progress tracking
     */
    public int getQuestProgress() {
        // Default implementation - return 0 if not started, 100 if complete
        if (questComplete) {
            return 100;
        } else if (questFailed) {
            return 0;
        } else {
            return 50; // In progress
        }
    }
    
    /**
     * Get description of the current node being executed
     */
    public String getCurrentNodeDescription() {
        if (currentNode != null) {
            return currentNode.getDescription();
        } else {
            return "No current node";
        }
    }
}