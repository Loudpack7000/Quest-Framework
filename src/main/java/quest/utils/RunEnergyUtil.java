package quest.utils;

import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.container.impl.bank.Bank;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.wrappers.items.Item;

/**
 * Utility class for managing run energy and energy potions.
 */
public class RunEnergyUtil {

    private static final String ENERGY_POTION = "Energy potion";
    private static final String EMPTY_VIAL = "Empty vial";
    private static final int DRINK_THRESHOLD = 20; // Drink when below 20%
    private static final int RUN_ON_THRESHOLD = 30; // Enable run when above 30%

    /**
     * Manages run energy by drinking potions when low and enabling run when high.
     * Call this at the very start of onLoop().
     */
    public static void handleRunEnergy() {
        try {
            int energy = (int) Walking.getRunEnergy();

            if (energy < DRINK_THRESHOLD) {
                // Drink highest-dose Energy potion available
                if (hasEnergyPotions()) {
                    drinkHighestDoseEnergyPotion();
                }
            } else if (energy > RUN_ON_THRESHOLD && !Walking.isRunEnabled()) {
                // Enable run if we have enough energy
                Walking.toggleRun();
                Sleep.sleepUntil(Walking::isRunEnabled, 1200);
            }
        } catch (Throwable t) {
            // Be silent in production; avoid breaking the main loop due to energy handling
        }
    }

    /**
     * Backward-compatible helper that attempts to drink a potion if needed.
     * @return true if a potion was consumed.
     */
    public static boolean manageRunEnergy() {
        int before = (int) Walking.getRunEnergy();
        boolean hadPotion = Inventory.contains(item -> item != null && item.getName() != null && item.getName().startsWith("Energy potion("));
        handleRunEnergy();
        // If we had a potion and energy increased or vial appeared, assume we drank
        boolean drank = hadPotion && ((int) Walking.getRunEnergy() > before);
        return drank;
    }

    /**
     * Convenience: do we have any energy potions right now?
     */
    public static boolean hasEnergyPotions() {
        return Inventory.contains(item -> item != null && item.getName() != null && item.getName().startsWith("Energy potion("));
    }

    /**
     * Drink the highest-dose Energy potion available: (4) -> (3) -> (2) -> (1)
     * Uses precise item names to avoid misclicks and waits until energy increases
     */
    private static boolean drinkHighestDoseEnergyPotion() {
        String[] doses = new String[] {
            ENERGY_POTION + "(4)",
            ENERGY_POTION + "(3)",
            ENERGY_POTION + "(2)",
            ENERGY_POTION + "(1)"
        };

        int beforeEnergy = (int) Walking.getRunEnergy();
        for (String name : doses) {
            org.dreambot.api.wrappers.items.Item item = Inventory.get(name);
            if (item != null) {
                int beforeCount = Inventory.count(name);
                if (item.interact("Drink")) {
                    boolean ok = Sleep.sleepUntil(
                        () -> (int) Walking.getRunEnergy() > beforeEnergy || Inventory.count(name) < beforeCount || Inventory.contains(EMPTY_VIAL),
                        3000
                    );
                    if (ok) {
                        dropEmptyVial();
                        return true;
                    }
                }
                // If interaction failed, try next available dose
            }
        }
        return false;
    }

    /**
     * Restock energy potions from the bank (withdraws any dose available).
     */
    public static boolean restockEnergyPotions() {
        if (hasEnergyPotions()) return true;

        if (!Bank.isOpen()) {
            Bank.open();
            if (!Sleep.sleepUntil(Bank::isOpen, 5000)) {
                return false;
            }
        }

        // Try highest to lowest doses
        boolean withdrew = false;
        if (Bank.contains(ENERGY_POTION + "(4)")) {
            withdrew = Bank.withdraw(ENERGY_POTION + "(4)", 5);
        } else if (Bank.contains(ENERGY_POTION + "(3)")) {
            withdrew = Bank.withdraw(ENERGY_POTION + "(3)", 7);
        } else if (Bank.contains(ENERGY_POTION + "(2)")) {
            withdrew = Bank.withdraw(ENERGY_POTION + "(2)", 10);
        } else if (Bank.contains(ENERGY_POTION + "(1)")) {
            withdrew = Bank.withdraw(ENERGY_POTION + "(1)", 20);
        }

        if (withdrew) {
            Sleep.sleep(600, 1000);
        }
        Bank.close();
        return withdrew;
    }

    private static void dropEmptyVial() {
        Item vial = Inventory.get(EMPTY_VIAL);
        if (vial != null) {
            vial.interact("Drop");
            Sleep.sleep(300, 600);
        }
    }
}