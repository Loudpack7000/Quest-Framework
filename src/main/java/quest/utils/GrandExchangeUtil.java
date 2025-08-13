package quest.utils;

import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.container.impl.equipment.Equipment;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.grandexchange.GrandExchange;
import org.dreambot.api.methods.grandexchange.LivePrices;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.utilities.Logger;
import java.util.Arrays;

/**
 * Grand Exchange Utility for automated trading
 * Handles buying items with intelligent price increases
 */
public class GrandExchangeUtil {
    
    // Grand Exchange location - Using more standard coordinates
    private static final Area GE_AREA = new Area(3159, 3484, 3170, 3497); // Standard GE area
    private static final Tile GE_CENTER = new Tile(3164, 3491, 0); // Central GE tile
    private static final Tile GE_FALLBACK = new Tile(3165, 3488, 0); // South entrance
    
    // Price increase strategies
    public enum PriceStrategy {
        CONSERVATIVE(5),     // 5% increases
        MODERATE(10),        // 10% increases  
        AGGRESSIVE(15),      // 15% increases
        INSTANT(50),         // 50% over market price
        EXTREME(200),        // 200% over market price (for stuck offers)
        FIXED_500_GP(0);     // Force 500 gp per item (instant buy for cheap items)
        
        private final int increasePercent;
        
        PriceStrategy(int increasePercent) {
            this.increasePercent = increasePercent;
        }
        
        public int getIncreasePercent() {
            return increasePercent;
        }
    }
    
    /**
     * Buy a single item from Grand Exchange
     */
    public static boolean buyItem(String itemName, int quantity) {
        return buyItem(itemName, quantity, PriceStrategy.MODERATE);
    }
    
    /**
     * Buy item with specific price strategy
     */
    public static boolean buyItem(String itemName, int quantity, PriceStrategy strategy) {
        return buyItem(itemName, quantity, strategy, 300000); // 5 minute timeout
    }
    
    /**
     * Buy item with full control
     */
    public static boolean buyItem(String itemName, int quantity, PriceStrategy strategy, int timeoutMs) {
        if (!isExecutionActive()) {
            Logger.log("[ABORT] Execution paused/stopped - aborting GE buyItem");
            return false;
        }
        Logger.log("Attempting to buy " + quantity + "x " + itemName + " using " + strategy + " strategy");
        
        if (!navigateToGrandExchange()) {
            Logger.log("Failed to navigate to Grand Exchange");
            return false;
        }
        
        if (!openGrandExchange()) {
            Logger.log("Failed to open Grand Exchange interface");
            return false;
        }
        
        // Collect any ready items first to clear slots
        if (GrandExchange.isReadyToCollect()) {
            Logger.log("Collecting any ready items before placing new order...");
            GrandExchange.collect();
            Sleep.sleep(1000, 2000);
        }
        
        boolean result = executeBuyOrder(itemName, quantity, strategy, timeoutMs);
        
        // Close Grand Exchange interface when done
        try {
            if (GrandExchange.isOpen()) {
                Logger.log("Closing Grand Exchange interface...");
                GrandExchange.close();
                Sleep.sleep(1000, 2000);
            }
        } catch (Exception e) {
            Logger.log("Error closing Grand Exchange: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Buy multiple items in sequence
     */
    public static boolean buyItems(ItemRequest... itemRequests) {
        if (!isExecutionActive()) {
            Logger.log("[ABORT] Execution paused/stopped - aborting GE buyItems");
            return false;
        }
        Logger.log("Buying " + itemRequests.length + " different items from Grand Exchange");
        
        if (!navigateToGrandExchange()) {
            Logger.log("Failed to navigate to Grand Exchange");
            return false;
        }
        
        if (!openGrandExchange()) {
            Logger.log("Failed to open Grand Exchange interface");
            return false;
        }
        
        // Collect any ready items first
        if (GrandExchange.isReadyToCollect()) {
            Logger.log("Collecting any ready items before placing new orders...");
            GrandExchange.collect();
            Sleep.sleep(1000, 2000);
        }
        
        boolean allSuccess = true;
        for (ItemRequest request : itemRequests) {
            if (!isExecutionActive()) { allSuccess = false; break; }
            if (!executeBuyOrder(request.getItemName(), request.getQuantity(), 
                               request.getStrategy(), request.getTimeoutMs())) {
                Logger.log("Failed to buy: " + request.getItemName());
                allSuccess = false;
                break; // Stop on first failure
            }
            
            // Small delay between orders
            Sleep.sleep(1000, 2000);
        }
        
        // Close Grand Exchange interface when done
        try {
            if (GrandExchange.isOpen()) {
                Logger.log("Closing Grand Exchange interface...");
                GrandExchange.close();
                Sleep.sleep(1000, 2000);
            }
        } catch (Exception e) {
            Logger.log("Error closing Grand Exchange: " + e.getMessage());
        }
        
        return allSuccess;
    }
    
    /**
     * Navigate to Grand Exchange - Simple and reliable method
     */
    private static boolean navigateToGrandExchange() {
        if (!isExecutionActive()) {
            Logger.log("[ABORT] Execution paused/stopped - aborting GE navigation");
            return false;
        }
        Logger.log("=== GRAND EXCHANGE NAVIGATION DEBUG ===");
        Logger.log("Current player location: " + Players.getLocal().getTile());
        Logger.log("Target GE_CENTER: " + GE_CENTER);
        Logger.log("Target GE_AREA: " + GE_AREA);
        
        // Check if already at GE
        if (GE_AREA.contains(Players.getLocal())) {
            Logger.log("Already at Grand Exchange!");
            return true;
        }
        
        Logger.log("Walking to Grand Exchange...");
        Logger.log("Distance to GE: " + Players.getLocal().distance(GE_CENTER));
        
        // Method 1: Try DreamBot's built-in GE navigation first
        Logger.log("Attempting Method 1: Built-in GE navigation");
        if (GrandExchange.open()) {
            Logger.log("GrandExchange.open() succeeded - checking if we're at GE...");
            Sleep.sleep(2000); // Give it a moment
            if (GE_AREA.contains(Players.getLocal()) || GrandExchange.isOpen()) {
                Logger.log("Successfully navigated to GE using built-in method!");
                return true;
            }
        }
        
        // Method 2: Manual walking with movement detection
        Logger.log("Attempting Method 2: Manual walking");
        
        // Try multiple times with movement detection
        for (int attempt = 1; attempt <= 3; attempt++) {
            if (!isExecutionActive()) {
                Logger.log("[ABORT] Execution paused/stopped during GE navigation");
                return false;
            }
            Logger.log("GE navigation attempt " + attempt + "/3");
            
            Tile target = (attempt == 1) ? GE_CENTER : GE_FALLBACK;
            Logger.log("Using target: " + target);
            
            if (Walking.walk(target)) {
                Logger.log("Walk command initiated successfully with target: " + target);
                
                // Wait with movement detection and multiple checks
                final int[] logCounter = {0}; // Counter for reduced logging
                boolean arrived = Sleep.sleepUntil(() -> {
                    if (!isExecutionActive()) return true; // end wait early
                    Tile currentPos = Players.getLocal().getTile();
                    boolean inArea = GE_AREA.contains(currentPos);
                    boolean isMoving = Players.getLocal().isMoving();
                    
                    // REDUCED LOGGING: Only log every 20th check to prevent spam
                    logCounter[0]++;
                    if (logCounter[0] % 20 == 0) {
                        Logger.log("GE Progress: Current=" + currentPos + 
                                  ", InGEArea=" + inArea + 
                                  ", IsMoving=" + isMoving + 
                                  ", DistanceToTarget=" + String.format("%.1f", currentPos.distance(target)));
                    }
                    
                    // Check if we arrived
                    if (inArea) {
                        Logger.log("SUCCESS: Arrived at Grand Exchange!");
                        return true;
                    }
                    
                    // Check if we're still moving (not stuck)
                    if (isMoving) {
                        return false; // Keep waiting, still moving
                    }
                    
                    // If not moving and not at GE, try walking again (only log re-walk attempts)
                    if (logCounter[0] % 5 == 0) { // Log re-walk attempts less frequently
                        Logger.log("WARNING: Not moving, re-initiating walk to " + target + "...");
                    }
                    boolean walkResult = Walking.walk(target);
                    return false; // Keep waiting
                    
                }, 60000); // 60 seconds per attempt
                
                if (arrived) {
                    Logger.log("Successfully arrived at Grand Exchange!");
                    return true;
                }
            } else {
                Logger.log("Walk command failed for attempt " + attempt);
            }
            
            Sleep.sleep(1000); // Brief pause between attempts
        }
        
        Logger.log("FAILED: Could not reach Grand Exchange after 3 attempts");
        Logger.log("Final position: " + Players.getLocal().getTile());
        return false;
    }
    
    /**
     * Open Grand Exchange interface
     */
    private static boolean openGrandExchange() {
        if (!isExecutionActive()) return false;
        if (GrandExchange.isOpen()) {
            Logger.log("Grand Exchange interface already open");
            return true;
        }
        
        Logger.log("Attempting to open Grand Exchange interface...");
        
        // Try the built-in method first
        if (GrandExchange.open()) {
            Logger.log("GrandExchange.open() succeeded, waiting for interface...");
            return Sleep.sleepUntil(() -> GrandExchange.isOpen(), 10000);
        }
        
        // Fallback: Find clerk manually
        GameObject geClerk = GameObjects.closest("Grand Exchange Clerk");
        if (geClerk == null) {
            Logger.log("Trying alternative clerk name...");
            geClerk = GameObjects.closest("Clerk");
        }
        
        if (geClerk != null) {
            Logger.log("Found GE Clerk, attempting to interact...");
            if (geClerk.interact("Exchange")) {
                return Sleep.sleepUntil(() -> GrandExchange.isOpen(), 8000);
            }
        }
        
        Logger.log("Failed to open Grand Exchange interface");
        return false;
    }
    
    /**
     * Execute the actual buy order with price management
     */
    private static boolean executeBuyOrder(String itemName, int quantity, PriceStrategy strategy, int timeoutMs) {
        if (!isExecutionActive()) return false;
        long startTime = System.currentTimeMillis();
        int attempts = 0;
        final int maxAttempts = 5;
        int initialCount = Inventory.count(itemName); // Track initial count
        
        while (attempts < maxAttempts && (System.currentTimeMillis() - startTime) < timeoutMs) {
            if (!isExecutionActive()) return false;
            attempts++;
            Logger.log("Buy attempt " + attempts + " for " + itemName);
            
            // Get current market price
            int marketPrice = LivePrices.get(itemName);
            if (marketPrice <= 0) {
                Logger.log("Could not get market price for " + itemName + ", using default pricing");
                marketPrice = 1000; // Default fallback
            }
            
            // Calculate offer price based on strategy and attempts
            int offerPrice = calculateOfferPrice(marketPrice, strategy, attempts);
            Logger.log("Market price: " + marketPrice + ", Offering: " + offerPrice + " (" + 
                      ((offerPrice - marketPrice) * 100 / marketPrice) + "% increase)");
            
            // Place buy order
            Logger.log("Attempting to place buy order for " + quantity + "x " + itemName + " @ " + offerPrice + " gp");
            if (placeBuyOrder(itemName, quantity, offerPrice)) {
                Logger.log("Buy order placed successfully, waiting for completion...");
                // Wait for order completion
                if (waitForOrderCompletion(itemName, quantity, 45000)) { // 45 seconds per attempt
                    Logger.log("[SUCCESS] Successfully bought " + quantity + "x " + itemName);
                    return true;
                } else {
                    Logger.log("[WARNING] Order timed out, increasing price and retrying...");
                    cancelAllPendingOrders();
                }
            } else {
                Logger.log("[ERROR] Failed to place buy order for " + itemName);
            }
            
            Sleep.sleep(2000, 4000); // Wait between attempts
        }
        
        Logger.log("Failed to buy " + itemName + " after " + maxAttempts + " attempts");
        
        // Final collection attempt - maybe some orders completed
        if (GrandExchange.isReadyToCollect()) {
            Logger.log("Final collection attempt before giving up...");
            GrandExchange.collect();
            Sleep.sleep(2000);
            
            // Check if we now have the required items
            int finalCount = Inventory.count(itemName);
            if (finalCount >= initialCount + quantity) {
                Logger.log("SUCCESS: Got all required items from final collection! " + (finalCount - initialCount) + "x " + itemName);
                return true;
            } else if (finalCount > initialCount) {
                Logger.log("Partial success from final collection: Got " + (finalCount - initialCount) + "/" + quantity + " " + itemName);
            }
        }
        
        return false;
    }
    
    /**
     * Calculate offer price based on strategy and attempt number
     */
    private static int calculateOfferPrice(int marketPrice, PriceStrategy strategy, int attempt) {
        if (strategy == PriceStrategy.FIXED_500_GP) {
            return 500; // Always offer 500 gp per item
        }
        // Base increase from strategy
        double multiplier = 1.0 + (strategy.getIncreasePercent() / 100.0);
        
        // Additional increase for each attempt (compound)
        for (int i = 1; i < attempt; i++) {
            multiplier *= 1.05; // 5% additional per retry
        }
        
        return (int) (marketPrice * multiplier);
    }
    
    /**
     * Place buy order on Grand Exchange - FIXED IMPLEMENTATION
     */
    private static boolean placeBuyOrder(String itemName, int quantity, int price) {
        Logger.log("PLACING BUY ORDER: " + quantity + "x " + itemName + " @ " + price + " gp each");
        
        try {
            // Step 1: Ensure Grand Exchange interface is open
            if (!GrandExchange.isOpen()) {
                Logger.log("[ERROR] GE interface not open, cannot place order");
                return false;
            }
            
            Logger.log("[SUCCESS] Grand Exchange interface is open, proceeding with order...");
            
            // Step 2: Try the DreamBot simplified method with proper error handling
            Logger.log("Attempting to place buy order using DreamBot API...");
            
            // Check if we have enough coins first
            int coinsNeeded = quantity * price;
            int coinsAvailable = Inventory.count("Coins");
            Logger.log("Coins needed: " + coinsNeeded + ", Coins available: " + coinsAvailable);
            
            if (coinsAvailable < coinsNeeded) {
                Logger.log("[ERROR] Not enough coins! Need " + coinsNeeded + " but only have " + coinsAvailable);
                return false;
            }
            
            // Find an empty buy slot
            int emptySlot = -1;
            for (int i = 0; i < 4; i++) {
                if (!GrandExchange.slotContainsItem(i)) {
                    emptySlot = i;
                    Logger.log("Found empty slot at index: " + i);
                    break;
                }
            }
            
            if (emptySlot == -1) {
                Logger.log("[ERROR] No empty buy slots available");
                return false;
            }
            
            // Use DreamBot's buyItem method but with proper verification
            Logger.log("Attempting to buy " + quantity + "x " + itemName + " at " + price + " gp each...");
            boolean orderPlaced = GrandExchange.buyItem(itemName, quantity, price);
            
            if (orderPlaced) {
                Logger.log("[SUCCESS] Buy order placed successfully!");
                
                // Wait a moment for the order to register
                Sleep.sleepUntil(() -> {
                    // Check if the slot now contains our item
                    for (int slot = 0; slot < 4; slot++) {
                        if (GrandExchange.slotContainsItem(slot)) {
                            Logger.log("Order registered in slot " + slot);
                            return true;
                        }
                    }
                    return false;
                }, 5000);
                
                return true;
            } else {
                Logger.log("[WARNING] DreamBot buyItem() method returned false, trying manual approach...");
                
                // Manual approach: Click the buy slot and set up the order manually
                return placeBuyOrderManually(itemName, quantity, price, emptySlot);
            }
            
        } catch (Exception e) {
            Logger.log("[CRITICAL ERROR] Exception in placeBuyOrder: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Manual buy order placement using widget interactions
     */
    private static boolean placeBuyOrderManually(String itemName, int quantity, int price, int slotIndex) {
        if (!isExecutionActive()) return false;
        Logger.log("Attempting manual buy order placement...");
        
        try {
            // For now, return false with helpful information
            // This method would need specific widget IDs which vary by DreamBot version
            Logger.log("[INFO] Manual GE interaction requires specific widget mapping");
            Logger.log("[INFO] Recommended: Clear some GE slots manually and try again");
            Logger.log("[INFO] Alternative: Use GrandExchange.collect() to free up slots");
            
            // Try to collect any ready items to free up slots
            if (GrandExchange.isReadyToCollect()) {
                Logger.log("Collecting ready items to free up slots...");
                GrandExchange.collect();
                Sleep.sleepUntil(() -> !GrandExchange.isReadyToCollect(), 5000);
                
                // Try the simplified method again after collecting
                Logger.log("Retrying simplified method after collecting...");
                return GrandExchange.buyItem(itemName, quantity, price);
            }
            
            return false;
            
        } catch (Exception e) {
            Logger.log("[ERROR] Manual buy order failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Wait for order to complete
     */
    private static boolean waitForOrderCompletion(String itemName, int quantity, int timeoutMs) {
        long startTime = System.currentTimeMillis();
        int initialCount = Inventory.count(itemName);
        int checkCount = 0;
        
        Logger.log("Waiting for " + quantity + "x " + itemName + " purchase completion...");
        Logger.log("Initial inventory count: " + initialCount);
        
        while ((System.currentTimeMillis() - startTime) < timeoutMs) {
            if (!isExecutionActive()) return false;
            checkCount++;
            
            // Log progress every 5 checks to avoid spam
            if (checkCount % 5 == 0) {
                Logger.log("Checking GE order status... (attempt " + checkCount + ")");
            }
            
            // PROPER DREAMBOT API: Check if any orders are ready to collect
            if (GrandExchange.isReadyToCollect()) {
                Logger.log("Order ready to collect! Collecting items...");
                
                // Collect completed orders to inventory
                if (GrandExchange.collect()) {
                    Logger.log("Successfully collected items from Grand Exchange");
                    
                    // Small delay for items to appear in inventory
                    Sleep.sleep(1000, 2000);
                    
                    // Check if we now have the required items
                    int currentCount = Inventory.count(itemName);
                    if (currentCount >= initialCount + quantity) {
                        Logger.log("SUCCESS: Got all required items! " + (currentCount - initialCount) + "x " + itemName);
                        return true;
                    } else if (currentCount > initialCount) {
                        Logger.log("Partial success: Got " + (currentCount - initialCount) + "/" + quantity + " " + itemName);
                        // Continue waiting for remaining items
                    }
                } else {
                    Logger.log("Failed to collect items, retrying...");
                }
            }
            
            // Check current inventory count
            int currentCount = Inventory.count(itemName);
            if (currentCount >= initialCount + quantity) {
                Logger.log("SUCCESS: All items obtained! " + (currentCount - initialCount) + "x " + itemName);
                return true;
            }
            
            Sleep.sleep(3000, 4000); // Check every 3-4 seconds
        }
        
        Logger.log("Purchase timeout for " + itemName + " after " + (timeoutMs/1000) + " seconds");
        
        // Final collection attempt before giving up
        if (GrandExchange.isReadyToCollect()) {
            Logger.log("Final collection attempt...");
            GrandExchange.collect();
            Sleep.sleep(2000);
            int finalCount = Inventory.count(itemName);
            return finalCount >= initialCount + quantity;
        }
        
        return false;
    }

    private static boolean isExecutionActive() {
        try {
            // Avoid compile-time dependency on QuestExecutor by using reflection
            Class<?> executorClass = Class.forName("quest.core.QuestExecutor");
            java.lang.reflect.Method getInstanceMethod = executorClass.getMethod("getInstance");
            Object executorInstance = getInstanceMethod.invoke(null);
            // Prefer the simple boolean helper if available
            try {
                java.lang.reflect.Method isActiveMethod = executorClass.getMethod("isActive");
                Object result = isActiveMethod.invoke(executorInstance);
                if (result instanceof Boolean) {
                    return (Boolean) result;
                }
            } catch (NoSuchMethodException ignored) {
                // Fall back to checking state enum if helper is missing
                java.lang.reflect.Method getCurrentStateMethod = executorClass.getMethod("getCurrentState");
                Object state = getCurrentStateMethod.invoke(executorInstance);
                return state != null && state.toString().equals("EXECUTING");
            }
        } catch (Throwable t) {
            // Fail-open to avoid blocking GE utilities in non-executor contexts
            return true;
        }
        return true;
    }
    
    /**
     * Check if we have any active orders in the Grand Exchange
     * Uses proper DreamBot API to check GE slots
     */
    private static boolean hasActiveOrders() {
        try {
            // Check if any slot contains items (active orders)
            for (int slot = 0; slot < 8; slot++) {
                if (GrandExchange.slotContainsItem(slot)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            Logger.log("Error checking active orders: " + e.getMessage());
            return false; // Assume no active orders if we can't check
        }
    }
    
    /**
     * Cancel all pending orders using proper DreamBot API
     */
    private static void cancelAllPendingOrders() {
        Logger.log("Cancelling all pending Grand Exchange orders...");
        try {
            if (GrandExchange.cancelAll()) {
                Logger.log("Successfully cancelled all pending orders");
                Sleep.sleep(2000, 3000); // Allow time for cancellation to process
            } else {
                Logger.log("Failed to cancel orders or no orders to cancel");
            }
        } catch (Exception e) {
            Logger.log("Error cancelling orders: " + e.getMessage());
        }
    }
    
    /**
     * Get total cost for buying items
     */
    public static int calculateTotalCost(ItemRequest... itemRequests) {
        int totalCost = 0;
        
        for (ItemRequest request : itemRequests) {
            int marketPrice = LivePrices.get(request.getItemName());
            if (marketPrice > 0) {
                int offerPrice = calculateOfferPrice(marketPrice, request.getStrategy(), 1);
                totalCost += offerPrice * request.getQuantity();
            }
        }
        
        return totalCost;
    }
    
    /**
     * Check if we have enough coins for purchases
     */
    public static boolean hasEnoughCoins(ItemRequest... itemRequests) {
        int requiredCoins = calculateTotalCost(itemRequests);
        int availableCoins = Inventory.count("Coins");
        
        Logger.log("Required coins: " + requiredCoins + ", Available: " + availableCoins);
        return availableCoins >= requiredCoins;
    }
    
    /**
     * Item Request Data Class
     */
    public static class ItemRequest {
        private final String itemName;
        private final int quantity;
        private final PriceStrategy strategy;
        private final int timeoutMs;
        
        public ItemRequest(String itemName, int quantity) {
            this(itemName, quantity, PriceStrategy.MODERATE, 300000);
        }
        
        public ItemRequest(String itemName, int quantity, PriceStrategy strategy) {
            this(itemName, quantity, strategy, 300000);
        }
        
        public ItemRequest(String itemName, int quantity, PriceStrategy strategy, int timeoutMs) {
            this.itemName = itemName;
            this.quantity = quantity;
            this.strategy = strategy;
            this.timeoutMs = timeoutMs;
        }
        
        // Getters
        public String getItemName() { return itemName; }
        public int getQuantity() { return quantity; }
        public PriceStrategy getStrategy() { return strategy; }
        public int getTimeoutMs() { return timeoutMs; }
        
        @Override
        public String toString() {
            return quantity + "x " + itemName + " (" + strategy + ")";
        }
    }
    
    /**
     * Quest Tree Node wrapper for Grand Exchange operations
     * This allows GrandExchangeUtil to be used in the tree-based quest system
     */
    public static abstract class GrandExchangeNode {
        protected final ItemRequest[] itemRequests;
        protected final String description;
        
        public GrandExchangeNode(String description, ItemRequest... itemRequests) {
            this.description = description;
            this.itemRequests = itemRequests;
        }
        
        /**
         * Execute the Grand Exchange purchase
         * @return true if all items were successfully purchased
         */
        public boolean execute() {
            Logger.log("=== QUEST TREE: " + description + " ===");
            
            // Log what we're trying to buy
            Logger.log("Purchasing quest items:");
            for (ItemRequest request : itemRequests) {
                Logger.log("  - " + request.toString());
            }
            
            // Calculate total cost
            int totalCost = calculateTotalCost(itemRequests);
            int availableCoins = Inventory.count("Coins");
            Logger.log("Total cost: " + totalCost + " gp, Available: " + availableCoins + " gp");
            
            // Check if we have enough coins
            if (!hasEnoughCoins(itemRequests)) {
                Logger.log("[ERROR] Not enough coins for quest items!");
                return false;
            }
            
            // Execute the purchase
            boolean success = buyItems(itemRequests);
            
            if (success) {
                Logger.log("[SUCCESS] All quest items purchased successfully!");
            } else {
                Logger.log("[FAILED] Could not purchase all required quest items");
            }
            
            return success;
        }
        
        /**
         * Get description of this node's purpose
         */
        public String getDescription() {
            return description;
        }
        
        /**
         * Get the items this node will purchase
         */
        public ItemRequest[] getItemRequests() {
            return itemRequests.clone();
        }
        
        /**
         * Check if we already have all required items
         * @return true if no purchase needed
         */
        public boolean isAlreadyComplete() {
            for (ItemRequest request : itemRequests) {
                if (Inventory.count(request.getItemName()) < request.getQuantity()) {
                    return false;
                }
            }
            Logger.log("All items already in inventory, skipping GE purchase");
            return true;
        }
        
        /**
         * Get items we still need to buy
         */
        public ItemRequest[] getMissingItems() {
            java.util.List<ItemRequest> missing = new java.util.ArrayList<>();
            
            for (ItemRequest request : itemRequests) {
                int currentCount = Inventory.count(request.getItemName());
                int needed = request.getQuantity() - currentCount;
                
                if (needed > 0) {
                    missing.add(new ItemRequest(
                        request.getItemName(), 
                        needed, 
                        request.getStrategy(), 
                        request.getTimeoutMs()
                    ));
                }
            }
            
            return missing.toArray(new ItemRequest[0]);
        }
    }
    
    /**
     * Pre-built Grand Exchange nodes for common quest scenarios
     */
    public static class QuestGENodes {
        
        /**
         * Cook's Assistant quest items
         */
        public static GrandExchangeNode cooksAssistantItems() {
            return new GrandExchangeNode("Cook's Assistant - Buy Ingredients") {
                {
                    // Constructor body - items are set in parent constructor
                }
            };
        }
        
        /**
         * Create a custom quest item buying node
         */
        public static GrandExchangeNode questItems(String questName, ItemRequest... items) {
            return new GrandExchangeNode(questName + " - Buy Quest Items", items) {
                {
                    // Constructor body - items are set in parent constructor
                }
            };
        }
        
        /**
         * Emergency supplies node (food, teleports, etc.)
         */
        public static GrandExchangeNode emergencySupplies() {
            return new GrandExchangeNode("Buy Emergency Supplies",
                new ItemRequest("Lobster", 5, PriceStrategy.MODERATE),
                new ItemRequest("Varrock teleport", 2, PriceStrategy.MODERATE)
            ) {
                {
                    // Constructor body - items are set in parent constructor
                }
            };
        }
    }
}
