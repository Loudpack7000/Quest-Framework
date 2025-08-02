package quest.trees;

import quest.core.QuestTree;
import quest.core.QuestNode;
import quest.nodes.ActionNode;
import quest.nodes.actions.TalkToNPCNode;
import quest.nodes.actions.WalkToLocationNode;
import quest.nodes.actions.InteractWithObjectNode;
import quest.nodes.decisions.QuestProgressDecisionNode;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.settings.PlayerSettings;

/**
 * Romeo and Juliet Quest Tree
 * Based on quest log data from user completion
 * Config 144 tracks quest progress: 0 -> 10 -> 20 -> 30 -> 40 -> 50 -> 60 (complete)
 */
public class RomeoAndJulietTree extends QuestTree {
    
    // Quest progress config ID
    private static final int QUEST_CONFIG = 144;
    
    // Key locations from quest log
    private static final Tile ROMEO_LOCATION_1 = new Tile(3219, 3427, 0); // Initial Romeo location
    private static final Tile ROMEO_LOCATION_2 = new Tile(3209, 3423, 0); // Romeo's second location
    private static final Tile JULIET_HOUSE_GROUND = new Tile(3156, 3435, 0); // Ground floor of Juliet's house
    private static final Tile JULIET_LOCATION = new Tile(3158, 3425, 1); // Juliet upstairs
    private static final Tile FATHER_LAWRENCE_LOCATION = new Tile(3254, 3487, 0); // Father Lawrence
    private static final Tile CADAVA_BUSH_LOCATION = new Tile(3271, 3366, 0); // Cadava berries
    private static final Tile APOTHECARY_LOCATION = new Tile(3197, 3406, 0); // Apothecary
    
    // Quest nodes
    private QuestNode questProgressCheck;
    private QuestNode talkToRomeoInitial;
    private QuestNode walkToJulietHouse;
    private QuestNode climbUpToJuliet;
    private QuestNode talkToJuliet;
    private QuestNode climbDownFromJuliet;
    private QuestNode returnToRomeo;
    private QuestNode walkToFatherLawrence;
    private QuestNode talkToFatherLawrence;
    private QuestNode walkToCadavaBush;
    private QuestNode pickCadavaBerries;
    private QuestNode walkToApothecary;
    private QuestNode talkToApothecary;
    private QuestNode returnToJulietWithPotion;
    private QuestNode climbUpToJulietFinal;
    private QuestNode giveJulietPotion;
    private QuestNode climbDownFinal;
    private QuestNode finalReturnToRomeo;
    private QuestNode questComplete;
    
    public RomeoAndJulietTree() {
        super("Romeo and Juliet");
    }
    
    @Override
    protected void buildTree() {
        // Build all the nodes
        createNodes();
        
        // Link the nodes together
        linkNodes();
        
        // Set the root node
        rootNode = questProgressCheck;
    }
    
    private void createNodes() {
        // Progress check node - determines where we are in the quest
        questProgressCheck = new QuestProgressDecisionNode("progress_check", "Romeo and Juliet", QUEST_CONFIG);
        
        // Step 1: Talk to Romeo initially (config 0 -> 10)
        talkToRomeoInitial = new TalkToNPCNode("talk_romeo_initial", "Romeo", ROMEO_LOCATION_1,
            new String[]{"Yes.", "No."}, "Yes", null);
        
        // Step 2: Go to Juliet
        walkToJulietHouse = new WalkToLocationNode("walk_juliet_house", JULIET_HOUSE_GROUND, "Juliet's house");
        climbUpToJuliet = new InteractWithObjectNode("climb_up_juliet", "Staircase", "Climb-up", 
            new Tile(3156, 3435, 0), "Juliet's house stairs");
        talkToJuliet = new TalkToNPCNode("talk_juliet", "Juliet", JULIET_LOCATION);
        climbDownFromJuliet = new InteractWithObjectNode("climb_down_juliet", "Staircase", "Climb-down",
            new Tile(3156, 3435, 1), "Juliet's house stairs");
        
        // Step 3: Return to Romeo with message (config 10 -> 20 -> 30)
        returnToRomeo = new TalkToNPCNode("return_romeo", "Romeo", ROMEO_LOCATION_2,
            null, "Ok, thanks", null);
        
        // Step 4: Go to Father Lawrence (config 30 -> 40)
        walkToFatherLawrence = new WalkToLocationNode("walk_father_lawrence", FATHER_LAWRENCE_LOCATION, "Father Lawrence");
        talkToFatherLawrence = new TalkToNPCNode("talk_father_lawrence", "Father Lawrence", FATHER_LAWRENCE_LOCATION);
        
        // Step 5: Get Cadava berries and go to Apothecary (config 40 -> 50)
        walkToCadavaBush = new WalkToLocationNode("walk_cadava_bush", CADAVA_BUSH_LOCATION, "Cadava bush");
        pickCadavaBerries = new InteractWithObjectNode("pick_cadava_berries", "Cadava bush", "Pick-from",
            CADAVA_BUSH_LOCATION, "Cadava bush");
        walkToApothecary = new WalkToLocationNode("walk_apothecary", APOTHECARY_LOCATION, "Apothecary");
        talkToApothecary = new TalkToNPCNode("talk_apothecary", "Apothecary", APOTHECARY_LOCATION,
            new String[]{"Talk about Romeo & Juliet.", "Do you know a potion to make hair fall out?", 
                        "Have you got any good potions to give away?", "No thanks."}, 
            "Talk about Romeo & Juliet", null);
        
        // Step 6: Return to Juliet with potion (config 50 -> 60)
        returnToJulietWithPotion = new WalkToLocationNode("return_juliet_potion", JULIET_HOUSE_GROUND, "Juliet's house for potion");
        climbUpToJulietFinal = new InteractWithObjectNode("climb_up_juliet_final", "Staircase", "Climb-up",
            new Tile(3156, 3435, 0), "Juliet's house stairs final");
        giveJulietPotion = new TalkToNPCNode("give_juliet_potion", "Juliet", JULIET_LOCATION);
        climbDownFinal = new InteractWithObjectNode("climb_down_final", "Staircase", "Climb-down",
            new Tile(3156, 3435, 1), "Juliet's house stairs final");
        
        // Step 7: Final return to Romeo (quest complete)
        finalReturnToRomeo = new TalkToNPCNode("final_return_romeo", "Romeo", ROMEO_LOCATION_2);
        
        // Quest completion node
        questComplete = new ActionNode("quest_complete", "Quest Complete") {
            @Override
            protected boolean performAction() {
                log("Romeo and Juliet quest completed!");
                return true;
            }
        };
    }
    
    private void linkNodes() {
        // Set up the progress check branches
        QuestProgressDecisionNode progressNode = (QuestProgressDecisionNode) questProgressCheck;
        progressNode.addStepBranch(0, talkToRomeoInitial);      // Quest not started
        progressNode.addStepBranch(10, walkToJulietHouse);      // Talked to Romeo, go to Juliet
        progressNode.addStepBranch(20, returnToRomeo);          // Talked to Juliet, return to Romeo
        progressNode.addStepBranch(30, walkToFatherLawrence);   // Romeo sent us to Father Lawrence
        progressNode.addStepBranch(40, walkToCadavaBush);       // Father Lawrence told us about berries
        progressNode.addStepBranch(50, returnToJulietWithPotion); // Got potion, return to Juliet
        progressNode.addStepBranch(60, questComplete);          // Quest complete
        progressNode.setDefaultBranch(talkToRomeoInitial);      // Default to start
        
        // Link the action sequences
        // Step 1: Talk to Romeo -> Go to Juliet
        ((ActionNode) talkToRomeoInitial).setNextNode(walkToJulietHouse);
        ((ActionNode) walkToJulietHouse).setNextNode(climbUpToJuliet);
        ((ActionNode) climbUpToJuliet).setNextNode(talkToJuliet);
        ((ActionNode) talkToJuliet).setNextNode(climbDownFromJuliet);
        ((ActionNode) climbDownFromJuliet).setNextNode(returnToRomeo);
        
        // Step 2: Return to Romeo -> Go to Father Lawrence
        ((ActionNode) returnToRomeo).setNextNode(walkToFatherLawrence);
        ((ActionNode) walkToFatherLawrence).setNextNode(talkToFatherLawrence);
        
        // Step 3: Father Lawrence -> Get berries -> Apothecary
        ((ActionNode) talkToFatherLawrence).setNextNode(walkToCadavaBush);
        ((ActionNode) walkToCadavaBush).setNextNode(pickCadavaBerries);
        ((ActionNode) pickCadavaBerries).setNextNode(walkToApothecary);
        ((ActionNode) walkToApothecary).setNextNode(talkToApothecary);
        
        // Step 4: Apothecary -> Return to Juliet with potion
        ((ActionNode) talkToApothecary).setNextNode(returnToJulietWithPotion);
        ((ActionNode) returnToJulietWithPotion).setNextNode(climbUpToJulietFinal);
        ((ActionNode) climbUpToJulietFinal).setNextNode(giveJulietPotion);
        ((ActionNode) giveJulietPotion).setNextNode(climbDownFinal);
        
        // Step 5: Final return to Romeo
        ((ActionNode) climbDownFinal).setNextNode(finalReturnToRomeo);
        ((ActionNode) finalReturnToRomeo).setNextNode(questComplete);
        
        // Quest complete has no next node (null = quest finished)
    }
    
    @Override
    public int getQuestProgress() {
        int configValue = PlayerSettings.getConfig(QUEST_CONFIG);
        
        // Convert config value to percentage
        switch (configValue) {
            case 0: return 0;    // Not started
            case 10: return 15;  // Talked to Romeo
            case 20: return 30;  // Talked to Juliet
            case 30: return 45;  // Returned to Romeo
            case 40: return 60;  // Talked to Father Lawrence
            case 50: return 80;  // Got potion from Apothecary
            case 60: return 100; // Quest complete
            default: 
                // Handle any intermediate values
                return Math.min(100, (configValue * 100) / 60);
        }
    }
}