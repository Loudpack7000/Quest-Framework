package quest.quests;

import org.dreambot.api.methods.combat.Combat;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.dialogues.Dialogues;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.skills.Skill;
import org.dreambot.api.methods.skills.Skills;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.methods.settings.PlayerSettings;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.wrappers.items.Item;
import org.dreambot.api.utilities.Logger;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.script.AbstractScript;
import quest.core.QuestScript;
import quest.core.QuestDatabase;
import quest.utils.ItemGatheringUtil;
import quest.utils.ItemGatheringUtil.ItemRequirement;
import quest.utils.GrandExchangeUtil;
import quest.utils.DialogueUtil;
import quest.utils.DialogueUtil.DialogueStep;

/**
 * VampireSlayerScript - Automated Vampire Slayer quest implementation
 * Integrates with ItemGatheringUtil for unified item management
 * Config varbit: 178 (0=not started, 1=in progress, 2=complete)
 */
public class VampireSlayerScript implements QuestScript {
    
    // Quest Configuration
    private static final int QUEST_CONFIG_ID = 178;
    private static final String QUEST_NAME = "Vampire Slayer";
    private static final String QUEST_ID = "VAMPIRE_SLAYER";
    
    // Quest NPCs
    private static final String MORGAN_NPC = "Morgan";
    private static final String HARLOW_NPC = "Dr Harlow";
    private static final String COUNT_DRAYNOR_NPC = "Count Draynor";
    
    // Quest Items
    private static final String BEER = "Beer";
    private static final String GARLIC = "Garlic";
    private static final String STAKE = "Stake";
    private static final String HAMMER = "Hammer";
    
    // Quest Areas
    private static final Area DRAYNOR_VILLAGE = new Area(3078, 3243, 3110, 3261);
    private static final Area BLUE_MOON_INN = new Area(3217, 3393, 3230, 3402);
    private static final Area DRAYNOR_MANOR = new Area(3096, 3327, 3124, 3363);
    
    // State tracking
    private String currentStepDescription = "Starting Vampire Slayer quest";
    private boolean questStarted = false;
    private boolean spokeToHarlow = false;
    private AbstractScript script;
    private QuestDatabase database;
    
    @Override
    public void initialize(AbstractScript script, QuestDatabase database) {
        this.script = script;
        this.database = database;
    }
    
    @Override
    public String getQuestId() {
        return QUEST_ID;
    }
    
    @Override
    public String getQuestName() {
        return QUEST_NAME;
    }
    
    @Override
    public String getCurrentStepDescription() {
        return currentStepDescription;
    }
    
    @Override
    public boolean canStart() {
        return getQuestConfigValue() == 0 && Skills.getBoostedLevel(Skill.ATTACK) >= 1;
    }
    
    @Override
    public boolean startQuest() {
        Logger.log("VampireSlayer DEBUG: startQuest() called");
        return startQuestInternal();
    }
    
    @Override
    public boolean executeCurrentStep() {
        Logger.log("VampireSlayer DEBUG: executeCurrentStep() called");
        
        // Check quest completion first
        int questConfig = getQuestConfigValue();
        Logger.log("VampireSlayer DEBUG: Quest config value: " + questConfig);
        
        if (questConfig == 2) {
            Logger.log("VampireSlayer DEBUG: Quest already completed!");
            currentStepDescription = "Quest completed!";
            return true;
        }
        
        try {
            // Step 1: Start quest if not started
            if (questConfig == 0) {
                Logger.log("VampireSlayer DEBUG: Quest not started, calling startQuestInternal()");
                return startQuestInternal();
            }
            
            // Step 2: Progress quest if in progress
            if (questConfig == 1) {
                Logger.log("VampireSlayer DEBUG: Quest in progress, calling progressQuest()");
                return progressQuest();
            }
            
            Logger.log("VampireSlayer DEBUG: Unknown quest state: " + questConfig);
            return false;
            
        } catch (Exception e) {
            Logger.log("VampireSlayer ERROR: Exception in executeCurrentStep(): " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    @Override
    public int getCurrentProgress() {
        int config = getQuestConfigValue();
        return config == 2 ? 100 : (config == 1 ? 50 : 0);
    }
    
    @Override
    public boolean isComplete() {
        return getQuestConfigValue() == 2;
    }
    
    @Override
    public boolean hasRequiredItems() {
        // Always return true since this script can automatically gather items from Grand Exchange
        Logger.log("VampireSlayer DEBUG: hasRequiredItems() called - returning true (auto-gathering enabled)");
        return true;
    }
    
    @Override
    public String[] getRequiredItems() {
        return new String[]{BEER, GARLIC, STAKE, HAMMER};
    }
    
    @Override
    public boolean handleDialogue() {
        // Handled within individual methods
        return true;
    }
    
    @Override
    public boolean navigateToObjective() {
        // Handled within individual methods
        return true;
    }
    
    @Override
    public void cleanup() {
        Logger.log("VampireSlayer DEBUG: Quest script cleanup");
    }
    
    @Override
    public void onQuestStart() {
        Logger.log("VampireSlayer DEBUG: Quest script started");
        currentStepDescription = "Initializing Vampire Slayer quest automation";
    }
    
    @Override
    public void onQuestComplete() {
        Logger.log("VampireSlayer DEBUG: Quest completed!");
    }
    
    private int getQuestConfigValue() {
        return PlayerSettings.getConfig(QUEST_CONFIG_ID);
    }
    
    private boolean startQuestInternal() {
        Logger.log("VampireSlayer DEBUG: startQuestInternal() called");
        
        currentStepDescription = "Starting quest - talking to Morgan in Draynor Village";
        
        // Walk to Draynor Village if not there
        if (!DRAYNOR_VILLAGE.contains(Players.getLocal())) {
            Logger.log("VampireSlayer DEBUG: Walking to Draynor Village");
            if (!Walking.walk(DRAYNOR_VILLAGE.getRandomTile())) {
                Logger.log("VampireSlayer DEBUG: Failed to walk to Draynor Village");
                return false;
            }
            Sleep.sleepUntil(() -> DRAYNOR_VILLAGE.contains(Players.getLocal()), 5000);
        }
        
        // Find and talk to Morgan
        NPC morgan = NPCs.closest(MORGAN_NPC);
        if (morgan == null) {
            Logger.log("VampireSlayer DEBUG: Morgan not found in Draynor Village");
            return false;
        }
        
        Logger.log("VampireSlayer DEBUG: Found Morgan, attempting to interact");
        if (!morgan.interact("Talk-to")) {
            Logger.log("VampireSlayer DEBUG: Failed to interact with Morgan");
            return false;
        }
        
        // Handle dialogue to start quest
        Sleep.sleepUntil(() -> Dialogues.inDialogue(), 3000);
        if (Dialogues.inDialogue()) {
            Logger.log("VampireSlayer DEBUG: In dialogue with Morgan, handling quest start dialogue");
            
            // Use DialogueUtil to handle quest start
            DialogueStep[] questStartDialogue = {
                new DialogueStep(DialogueUtil.DialogueStep.StepType.SELECT_OPTION, "Yes.", "Accept quest"),
                new DialogueStep(DialogueUtil.DialogueStep.StepType.SELECT_OPTION, "Ok, I'll help you.", "Confirm help")
            };
            
            if (DialogueUtil.handleDialogueSequence(questStartDialogue)) {
                Logger.log("VampireSlayer DEBUG: Quest dialogue completed successfully");
                questStarted = true;
                
                // Wait for quest to actually start
                Sleep.sleepUntil(() -> getQuestConfigValue() == 1, 3000);
                Logger.log("VampireSlayer DEBUG: Quest config after dialogue: " + getQuestConfigValue());
                return true;
            } else {
                Logger.log("VampireSlayer DEBUG: Failed to complete quest start dialogue");
                return false;
            }
        }
        
        Logger.log("VampireSlayer DEBUG: Failed to enter dialogue with Morgan");
        return false;
    }
    
    private boolean progressQuest() {
        Logger.log("VampireSlayer DEBUG: progressQuest() called");
        
        // Step 1: Ensure we have all basic quest items using unified gathering
        if (!hasBasicQuestItems()) {
            Logger.log("VampireSlayer DEBUG: Missing basic quest items, gathering them");
            currentStepDescription = "Gathering required quest items";
            ItemGatheringUtil.GatheringResult result = gatherQuestItems();
            
            if (!result.isSuccess()) {
                Logger.log("VampireSlayer DEBUG: Item gathering failed, handling missing items");
                return handleMissingItems(result);
            } else {
                Logger.log("VampireSlayer DEBUG: All basic quest items gathered successfully");
            }
        } else {
            Logger.log("VampireSlayer DEBUG: All basic quest items available, proceeding with quest");
        }
        
        // Step 2: Talk to Dr Harlow if we haven't yet
        if (!spokeToHarlow) {
            Logger.log("VampireSlayer DEBUG: Need to speak to Dr Harlow");
            return talkToHarlow();
        }
        
        // Step 3: Fight Count Draynor
        Logger.log("VampireSlayer DEBUG: Ready for final battle with Count Draynor");
        return fightCountDraynor();
    }
    
    private boolean hasBasicQuestItems() {
        Logger.log("VampireSlayer DEBUG: hasBasicQuestItems() called");
        
        boolean hasBeer = Inventory.contains(BEER);
        boolean hasGarlic = Inventory.contains(GARLIC);
        boolean hasStake = Inventory.contains(STAKE);
        boolean hasHammer = Inventory.contains(HAMMER);
        
        Logger.log("VampireSlayer DEBUG: Inventory check - Beer: " + hasBeer + ", Garlic: " + hasGarlic + ", Stake: " + hasStake + ", Hammer: " + hasHammer);
        
        return hasBeer && hasGarlic && hasStake && hasHammer;
    }
    
    private ItemGatheringUtil.GatheringResult gatherQuestItems() {
        Logger.log("VampireSlayer DEBUG: gatherQuestItems() called - using unified ItemGatheringUtil");
        
        // Create item requirements for Vampire Slayer quest with explicit Grand Exchange usage
        ItemRequirement[] requirements = {
            new ItemRequirement(BEER, 1, GrandExchangeUtil.PriceStrategy.AGGRESSIVE),
            new ItemRequirement(GARLIC, 1, GrandExchangeUtil.PriceStrategy.AGGRESSIVE), 
            new ItemRequirement(STAKE, 1, GrandExchangeUtil.PriceStrategy.AGGRESSIVE),
            new ItemRequirement(HAMMER, 1, GrandExchangeUtil.PriceStrategy.AGGRESSIVE)
        };
        
        Logger.log("VampireSlayer DEBUG: Created " + requirements.length + " item requirements with AGGRESSIVE GE strategy");
        for (ItemRequirement req : requirements) {
            Logger.log("VampireSlayer DEBUG: Requirement - " + req.toString());
        }
        
        // Use unified gathering system
        Logger.log("VampireSlayer DEBUG: Calling ItemGatheringUtil.gatherItems()");
        ItemGatheringUtil.GatheringResult result = ItemGatheringUtil.gatherItems(requirements);
        
        Logger.log("VampireSlayer DEBUG: Item gathering result - Success: " + result.isSuccess());
        Logger.log("VampireSlayer DEBUG: Obtained items: " + result.getObtainedItems());
        Logger.log("VampireSlayer DEBUG: Missing items: " + result.getMissingItems());
        Logger.log("VampireSlayer DEBUG: Last action: " + result.getLastAction());
        
        return result;
    }
    
    private boolean handleMissingItems(ItemGatheringUtil.GatheringResult result) {
        Logger.log("VampireSlayer DEBUG: handleMissingItems() called");
        
        StringBuilder missingItemsList = new StringBuilder();
        for (String item : result.getMissingItems().keySet()) {
            if (missingItemsList.length() > 0) {
                missingItemsList.append(", ");
            }
            missingItemsList.append(item);
        }
        
        currentStepDescription = "Missing items: " + missingItemsList.toString();
        Logger.log("VampireSlayer DEBUG: " + currentStepDescription);
        
        // Strategy suggestions for missing items
        for (String missingItem : result.getMissingItems().keySet()) {
            switch (missingItem) {
                case BEER:
                    Logger.log("VampireSlayer DEBUG: Missing Beer - can be bought from taverns or Grand Exchange");
                    break;
                case GARLIC:
                    Logger.log("VampireSlayer DEBUG: Missing Garlic - can be picked from garlic field near Draynor or bought from GE");
                    break;
                case STAKE:
                    Logger.log("VampireSlayer DEBUG: Missing Stake - can be bought from GE or made with Crafting");
                    break;
                case HAMMER:
                    Logger.log("VampireSlayer DEBUG: Missing Hammer - can be bought from general stores or GE");
                    break;
            }
        }
        
        return false; // Can't proceed without required items
    }
    
    private boolean talkToHarlow() {
        Logger.log("VampireSlayer DEBUG: talkToHarlow() called");
        
        currentStepDescription = "Talking to Dr Harlow in Blue Moon Inn";
        
        // Walk to Blue Moon Inn if not there
        if (!BLUE_MOON_INN.contains(Players.getLocal())) {
            Logger.log("VampireSlayer DEBUG: Walking to Blue Moon Inn");
            if (!Walking.walk(BLUE_MOON_INN.getRandomTile())) {
                Logger.log("VampireSlayer DEBUG: Failed to walk to Blue Moon Inn");
                return false;
            }
            Sleep.sleepUntil(() -> BLUE_MOON_INN.contains(Players.getLocal()), 8000);
        }
        
        // Find Dr Harlow
        NPC harlow = NPCs.closest(HARLOW_NPC);
        if (harlow == null) {
            Logger.log("VampireSlayer DEBUG: Dr Harlow not found in Blue Moon Inn");
            return false;
        }
        
        // Give beer to Dr Harlow
        Logger.log("VampireSlayer DEBUG: Found Dr Harlow, giving him beer");
        Item beer = Inventory.get(BEER);
        if (beer == null) {
            Logger.log("VampireSlayer DEBUG: No beer in inventory to give to Harlow");
            return false;
        }
        
        if (!beer.useOn(harlow)) {
            Logger.log("VampireSlayer DEBUG: Failed to use beer on Dr Harlow");
            return false;
        }
        
        // Handle dialogue
        Sleep.sleepUntil(() -> Dialogues.inDialogue(), 3000);
        if (Dialogues.inDialogue()) {
            Logger.log("VampireSlayer DEBUG: In dialogue with Dr Harlow");
            
            DialogueStep[] harlowDialogue = {
                new DialogueStep(DialogueStep.StepType.CONTINUE, "Continue through dialogue"),
                new DialogueStep(DialogueStep.StepType.CONTINUE, "Continue through dialogue")
            };
            
            if (DialogueUtil.handleDialogueSequence(harlowDialogue)) {
                Logger.log("VampireSlayer DEBUG: Dr Harlow dialogue completed");
                spokeToHarlow = true;
                return true;
            } else {
                Logger.log("VampireSlayer DEBUG: Failed to complete Dr Harlow dialogue");
                return false;
            }
        }
        
        Logger.log("VampireSlayer DEBUG: Failed to enter dialogue with Dr Harlow");
        return false;
    }
    
    private boolean fightCountDraynor() {
        Logger.log("VampireSlayer DEBUG: fightCountDraynor() called");
        
        currentStepDescription = "Fighting Count Draynor in Draynor Manor";
        
        // Walk to Draynor Manor if not there
        if (!DRAYNOR_MANOR.contains(Players.getLocal())) {
            Logger.log("VampireSlayer DEBUG: Walking to Draynor Manor");
            if (!Walking.walk(DRAYNOR_MANOR.getRandomTile())) {
                Logger.log("VampireSlayer DEBUG: Failed to walk to Draynor Manor");
                return false;
            }
            Sleep.sleepUntil(() -> DRAYNOR_MANOR.contains(Players.getLocal()), 8000);
        }
        
        // Find Count Draynor
        NPC countDraynor = NPCs.closest(COUNT_DRAYNOR_NPC);
        if (countDraynor == null) {
            Logger.log("VampireSlayer DEBUG: Count Draynor not found in manor");
            return false;
        }
        
        // Equip stake if not already equipped
        Item stake = Inventory.get(STAKE);
        if (stake != null && !stake.isNoted()) {
            Logger.log("VampireSlayer DEBUG: Equipping stake for final battle");
            stake.interact("Wield");
            Sleep.sleep(1000);
        }
        
        // Attack Count Draynor
        Logger.log("VampireSlayer DEBUG: Attacking Count Draynor");
        if (!countDraynor.interact("Attack")) {
            Logger.log("VampireSlayer DEBUG: Failed to attack Count Draynor");
            return false;
        }
        
        // Wait for combat and quest completion
        Sleep.sleepUntil(() -> getQuestConfigValue() == 2, 10000);
        
        // Give it some time for combat
        Sleep.sleep(2000);
        
        // Check if quest completed
        if (getQuestConfigValue() == 2) {
            Logger.log("VampireSlayer DEBUG: Quest completed successfully!");
            currentStepDescription = "Quest completed! Count Draynor defeated!";
            return true;
        }
        
        Logger.log("VampireSlayer DEBUG: Combat finished but quest not completed yet");
        return false;
    }
    
    @Override
    public String toString() {
        return "VampireSlayerScript{" +
                "questName='" + QUEST_NAME + '\'' +
                ", configId=" + QUEST_CONFIG_ID +
                ", currentStep='" + currentStepDescription + '\'' +
                ", questStarted=" + questStarted +
                ", spokeToHarlow=" + spokeToHarlow +
                '}';
    }
}
