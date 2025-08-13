package quest.utils;

import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.container.impl.bank.Bank;
import org.dreambot.api.methods.container.impl.bank.BankMode;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.utilities.Logger;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.wrappers.interactive.GameObject;
import quest.utils.GrandExchangeUtil.ItemRequest;
import quest.utils.GrandExchangeUtil.PriceStrategy;

import java.util.*;

/**
 * Universal Item Gathering System
 * Coordinates inventory checking, banking, and Grand Exchange purchases
 * Prevents conflicts between quest scripts by centralizing item acquisition
 */
public class ItemGatheringUtil {
    
    // Bank areas no longer needed - Bank.open() automatically finds closest bank
    
    /**
     * Item Requirement Definition
     */
    public static class ItemRequirement {
        private final String itemName;
        private final int quantity;
        private final PriceStrategy geStrategy;
        private final boolean allowPartial;
        private final ItemSource preferredSource;
        
        public enum ItemSource {
            ANY,                // Try all sources
            INVENTORY_ONLY,     // Only check inventory
            BANK_ONLY,          // Only check bank
            GE_ONLY,            // Only buy from GE
            NO_GE               // Try inventory/bank but don't buy from GE
        }
        
        public ItemRequirement(String itemName, int quantity) {
            this(itemName, quantity, PriceStrategy.MODERATE, false, ItemSource.ANY);
        }
        
        public ItemRequirement(String itemName, int quantity, PriceStrategy geStrategy) {
            this(itemName, quantity, geStrategy, false, ItemSource.ANY);
        }
        
        public ItemRequirement(String itemName, int quantity, PriceStrategy geStrategy, 
                             boolean allowPartial, ItemSource preferredSource) {
            this.itemName = itemName;
            this.quantity = quantity;
            this.geStrategy = geStrategy;
            this.allowPartial = allowPartial;
            this.preferredSource = preferredSource;
        }
        
        // Getters
        public String getItemName() { return itemName; }
        public int getQuantity() { return quantity; }
        public PriceStrategy getGeStrategy() { return geStrategy; }
        public boolean isAllowPartial() { return allowPartial; }
        public ItemSource getPreferredSource() { return preferredSource; }
        
        @Override
        public String toString() {
            return quantity + "x " + itemName + " (source: " + preferredSource + ")";
        }
    }
    
    /**
     * Gathering Result
     */
    public static class GatheringResult {
        private final boolean success;
        private final Map<String, Integer> obtainedItems;
        private final Map<String, Integer> missingItems;
        private final String lastAction;
        
        public GatheringResult(boolean success, Map<String, Integer> obtained, 
                             Map<String, Integer> missing, String lastAction) {
            this.success = success;
            this.obtainedItems = new HashMap<>(obtained);
            this.missingItems = new HashMap<>(missing);
            this.lastAction = lastAction;
        }
        
        public boolean isSuccess() { return success; }
        public Map<String, Integer> getObtainedItems() { return obtainedItems; }
        public Map<String, Integer> getMissingItems() { return missingItems; }
        public String getLastAction() { return lastAction; }
        
        public boolean hasMissingItems() { return !missingItems.isEmpty(); }
    }
    
    /**
     * Main method: Gather all required items using the unified system
     */
    public static GatheringResult gatherItems(ItemRequirement... requirements) {
        return gatherItems(Arrays.asList(requirements));
    }
    
    /**
     * Gather items from a list of requirements
     */
    public static GatheringResult gatherItems(List<ItemRequirement> requirements) {
        Logger.log("Starting item gathering for " + requirements.size() + " requirements");
        
        Map<String, Integer> obtained = new HashMap<>();
        Map<String, Integer> missing = new HashMap<>();
        String lastAction = "Starting item gathering";
        
        for (ItemRequirement req : requirements) {
            Logger.log("Processing requirement: " + req);
            
            // Step 1: Check inventory first
            int inInventory = Inventory.count(req.getItemName());
            if (inInventory >= req.getQuantity()) {
                Logger.log("Found " + inInventory + "x " + req.getItemName() + " in inventory (sufficient)");
                obtained.put(req.getItemName(), req.getQuantity());
                continue;
            }
            
            if (req.getPreferredSource() == ItemRequirement.ItemSource.INVENTORY_ONLY) {
                if (inInventory > 0 && req.isAllowPartial()) {
                    obtained.put(req.getItemName(), inInventory);
                } else {
                    missing.put(req.getItemName(), req.getQuantity() - inInventory);
                }
                continue;
            }
            
            // Step 2: Check bank for items before going to Grand Exchange
            int needed = req.getQuantity() - inInventory;
            Logger.log("Need " + needed + " more " + req.getItemName() + " - checking bank first");
            
            if (req.getPreferredSource() != ItemRequirement.ItemSource.GE_ONLY && needed > 0) {
                int fromBank = withdrawFromBank(req.getItemName(), needed);
                if (fromBank > 0) {
                    Logger.log("Withdrew " + fromBank + "x " + req.getItemName() + " from bank");
                    lastAction = "Withdrew " + fromBank + "x " + req.getItemName() + " from bank";
                    obtained.put(req.getItemName(), inInventory + fromBank);
                    needed -= fromBank;
                }
            }
            
            if (req.getPreferredSource() == ItemRequirement.ItemSource.BANK_ONLY || 
                req.getPreferredSource() == ItemRequirement.ItemSource.NO_GE) {
                if (needed > 0) {
                    missing.put(req.getItemName(), needed);
                } else {
                    obtained.put(req.getItemName(), req.getQuantity());
                }
                continue;
            }
            
            // Step 3: Buy from Grand Exchange
            if (needed > 0 && req.getPreferredSource() != ItemRequirement.ItemSource.NO_GE) {
                Logger.log("Need to buy " + needed + "x " + req.getItemName() + " from Grand Exchange");
                boolean bought = GrandExchangeUtil.buyItem(req.getItemName(), needed, req.getGeStrategy());
                if (bought) {
                    Logger.log("Successfully bought " + needed + "x " + req.getItemName());
                    lastAction = "Bought " + needed + "x " + req.getItemName() + " from Grand Exchange";
                    obtained.put(req.getItemName(), req.getQuantity());
                } else {
                    Logger.log("Failed to buy " + needed + "x " + req.getItemName());
                    lastAction = "Failed to buy " + needed + "x " + req.getItemName();
                    missing.put(req.getItemName(), needed);
                }
            } else if (needed <= 0) {
                obtained.put(req.getItemName(), req.getQuantity());
            }
        }
        
        boolean success = missing.isEmpty();
        Logger.log("Item gathering completed. Success: " + success + 
                  ", Obtained: " + obtained.size() + " items, Missing: " + missing.size() + " items");
        
        return new GatheringResult(success, obtained, missing, lastAction);
    }
    
    /**
     * Withdraw items from bank
     */
    private static int withdrawFromBank(String itemName, int quantity) {
        Logger.log("Withdrawing " + quantity + "x " + itemName + " from bank");
        
        // Use Bank.open() which automatically navigates to the closest bank
        if (!Bank.open()) {
            Logger.log("Failed to open bank - Bank.open() handles navigation automatically");
            return 0;
        }
        
        // Wait for bank interface to be ready using Sleep.sleepUntil()
        if (!Sleep.sleepUntil(() -> Bank.isOpen(), 15000)) {
            Logger.log("Bank interface did not open within timeout");
            return 0;
        }
        Logger.log("Bank interface is now open and ready");
        
        if (!Bank.contains(itemName)) {
            Logger.log("Bank does not contain " + itemName);
            return 0;
        }
        
        int beforeCount = Inventory.count(itemName);
        
        if (quantity == 1) {
            Bank.withdraw(itemName, 1);
        } else {
            Bank.withdraw(itemName, quantity);
        }
        
        // Wait for withdrawal
        Sleep.sleepUntil(() -> Inventory.count(itemName) > beforeCount, 3000);
        
        int afterCount = Inventory.count(itemName);
        int withdrawn = afterCount - beforeCount;
        
        if (withdrawn > 0) {
            Logger.log("Successfully withdrew " + withdrawn + "x " + itemName);
        }
        
        return withdrawn;
    }
    
    // navigateToNearestBank() method removed - Bank.open() handles navigation automatically
    
    /**
     * Quick check if all items are available in inventory
     */
    public static boolean hasItems(String... itemNames) {
        for (String itemName : itemNames) {
            if (!Inventory.contains(itemName)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Check specific quantities
     */
    public static boolean hasItems(Map<String, Integer> itemQuantities) {
        for (Map.Entry<String, Integer> entry : itemQuantities.entrySet()) {
            if (Inventory.count(entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Create quick item requirements for common scenarios
     */
    public static ItemRequirement requirement(String itemName, int quantity) {
        return new ItemRequirement(itemName, quantity);
    }
    
    public static ItemRequirement requirementGE(String itemName, int quantity, PriceStrategy strategy) {
        return new ItemRequirement(itemName, quantity, strategy, false, ItemRequirement.ItemSource.ANY);
    }
    
    public static ItemRequirement requirementNoGE(String itemName, int quantity) {
        return new ItemRequirement(itemName, quantity, PriceStrategy.MODERATE, false, ItemRequirement.ItemSource.NO_GE);
    }
    
    /**
     * Ensure a specific quantity of an item is unnoted in inventory using the bank.
     * Steps:
     * - Open bank
     * - Deposit all of the item (clears noted/unnoted copies)
     * - Withdraw the requested quantity as items (unnoted)
     */
    public static boolean ensureUnnotedInInventory(String itemName, int quantity) {
        Logger.log("Ensuring unnoted in inventory: " + quantity + "x " + itemName);
        if (quantity <= 0) return true;

        // If enough and unnoted copy exists, we’re fine
        if (Inventory.count(itemName) >= quantity) {
            org.dreambot.api.wrappers.items.Item sample = Inventory.get(itemName);
            if (sample != null && !sample.isNoted()) return true;
        }

        if (!org.dreambot.api.methods.container.impl.bank.Bank.open()) {
            Logger.log("Failed to open bank for unnoting: " + itemName);
            return false;
        }
        if (!Sleep.sleepUntil(() -> org.dreambot.api.methods.container.impl.bank.Bank.isOpen(), 15000)) {
            Logger.log("Bank did not open in time for unnoting: " + itemName);
            return false;
        }

        // Deposit all copies first
        org.dreambot.api.methods.container.impl.bank.Bank.depositAll(itemName);
        Sleep.sleep(300, 600);

    // Ensure withdraw mode is unnoted (items) and withdraw
    org.dreambot.api.methods.container.impl.bank.Bank.setWithdrawMode(BankMode.ITEM);
        int before = Inventory.count(itemName);
        boolean ok = org.dreambot.api.methods.container.impl.bank.Bank.withdraw(itemName, quantity);
        if (!ok) return false;
        Sleep.sleepUntil(() -> Inventory.count(itemName) >= before + 1, 4000);
        return Inventory.count(itemName) >= quantity;
    }
}
