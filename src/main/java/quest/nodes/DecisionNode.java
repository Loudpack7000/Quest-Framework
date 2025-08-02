package quest.nodes;

import quest.core.QuestNode;
import java.util.Map;
import java.util.HashMap;

/**
 * Base class for decision nodes - nodes that choose between different paths
 * Examples: Check if player has item, Check quest progress, Check location, etc.
 */
public abstract class DecisionNode extends QuestNode {
    
    protected Map<String, QuestNode> branches;
    protected QuestNode defaultNode;
    
    public DecisionNode(String nodeId, String description) {
        super(nodeId, description);
        this.branches = new HashMap<>();
        this.defaultNode = null;
    }
    
    @Override
    public ExecutionResult execute() {
        try {
            log("Evaluating decision: " + description);
            
            // Get the decision result
            String decision = makeDecision();
            log("Decision result: " + decision);
            
            // Find the appropriate branch
            QuestNode nextNode = branches.get(decision);
            
            if (nextNode == null) {
                nextNode = defaultNode;
                log("Using default branch");
            } else {
                log("Using branch: " + decision);
            }
            
            if (nextNode == null) {
                return ExecutionResult.failure("No valid branch found for decision: " + decision);
            }
            
            return ExecutionResult.success(nextNode);
            
        } catch (Exception e) {
            log("Exception in decision evaluation: " + e.getMessage());
            e.printStackTrace();
            return ExecutionResult.failure("Exception during decision: " + e.getMessage());
        }
    }
    
    /**
     * Make the decision - must be implemented by subclasses
     * @return String key representing the decision outcome
     */
    protected abstract String makeDecision();
    
    /**
     * Add a branch for a specific decision outcome
     */
    public DecisionNode addBranch(String decision, QuestNode node) {
        branches.put(decision, node);
        return this;
    }
    
    /**
     * Set the default branch (used when no specific branch matches)
     */
    public DecisionNode setDefaultBranch(QuestNode node) {
        this.defaultNode = node;
        return this;
    }
    
    /**
     * Get all available branches
     */
    public Map<String, QuestNode> getBranches() {
        return new HashMap<>(branches);
    }
    
    /**
     * Get the default branch
     */
    public QuestNode getDefaultBranch() {
        return defaultNode;
    }
}