package quest.nodes.decisions;

import quest.nodes.DecisionNode;
import org.dreambot.api.methods.settings.PlayerSettings;

/**
 * Decision node for checking quest progress using config values
 * Branches based on quest step/progress
 */
public class QuestProgressDecisionNode extends DecisionNode {
    
    private final int configId;
    private final String questName;
    
    public QuestProgressDecisionNode(String nodeId, String questName, int configId) {
        super(nodeId, "Check " + questName + " progress (config " + configId + ")");
        this.questName = questName;
        this.configId = configId;
    }
    
    @Override
    protected String makeDecision() {
        try {
            int currentValue = PlayerSettings.getConfig(configId);
            log("Quest " + questName + " config " + configId + " = " + currentValue);
            
            // Return the current config value as a string
            // This allows the tree to branch based on specific quest steps
            return String.valueOf(currentValue);
            
        } catch (Exception e) {
            log("Exception checking quest progress: " + e.getMessage());
            e.printStackTrace();
            return "error";
        }
    }
    
    /**
     * Add a branch for a specific quest step value
     */
    public QuestProgressDecisionNode addStepBranch(int stepValue, quest.core.QuestNode node) {
        return (QuestProgressDecisionNode) addBranch(String.valueOf(stepValue), node);
    }
    
    /**
     * Add a branch for quest completion (usually high values like 60+)
     */
    public QuestProgressDecisionNode addCompletionBranch(quest.core.QuestNode node) {
        // Romeo and Juliet completes at config value 60
        return (QuestProgressDecisionNode) addBranch("60", node);
    }
    
    /**
     * Add a branch for quest not started (value 0)
     */
    public QuestProgressDecisionNode addNotStartedBranch(quest.core.QuestNode node) {
        return (QuestProgressDecisionNode) addBranch("0", node);
    }
}