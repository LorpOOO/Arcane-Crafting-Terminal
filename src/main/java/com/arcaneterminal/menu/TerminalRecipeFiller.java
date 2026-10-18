package com.arcaneterminal.menu;

import com.arcaneterminal.block.ArcaneCraftingTerminalBlockEntity;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneRecipe;
import com.leclowndu93150.thaumaturge.content.recipe.workbench.ArcaneShapedCraftingRecipe;
import com.leclowndu93150.thaumaturge.content.workbench.InventoryArcaneWorkbench;
import com.leclowndu93150.thaumaturge.content.workbench.MenuArcaneWorkbench;
import com.leclowndu93150.thaumaturge.content.workbench.SlotCrystalEssentia;
import com.tom.storagemod.inventory.StoredItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;

/**
 * Fills the terminal's crafting grid and crystal slots for a recipe chosen in JEI, taking items from
 * the storage network first and the player's inventory second, like Tom's Crafting Terminal does.
 */
final class TerminalRecipeFiller {
    private static final int GRID_WIDTH = 3;

    private final ArcaneCraftingTerminalBlockEntity terminal;
    private final InventoryArcaneWorkbench inventory;
    private final Player player;

    TerminalRecipeFiller(ArcaneCraftingTerminalBlockEntity terminal, Player player) {
        this.terminal = terminal;
        this.inventory = terminal.getInventory();
        this.player = player;
    }

    void place(Recipe<?> recipe) {
        terminal.clearGrid(player);
        List<StoredItemStack> stored = new ArrayList<>(terminal.getStacks().keySet());
        placeGrid(recipe, stored);
        if (recipe instanceof IArcaneRecipe arcane) {
            placeCrystals(arcane, stored);
        }
        inventory.setChanged();
    }

    private void placeGrid(Recipe<?> recipe, List<StoredItemStack> stored) {
        List<Optional<Ingredient>> cells;
        int width;
        if (recipe instanceof ShapedRecipe shaped) {
            cells = shaped.getIngredients();
            width = shaped.getWidth();
        } else if (recipe instanceof ArcaneShapedCraftingRecipe shaped) {
            cells = shaped.getIngredients();
            width = shaped.getWidth();
        } else {
            cells = recipe.placementInfo().ingredients().stream().map(Optional::of).toList();
            width = GRID_WIDTH;
        }
        for (int cell = 0; cell < cells.size() && cell < InventoryArcaneWorkbench.CRAFTING_SLOTS; cell++) {
            Optional<Ingredient> ingredient = cells.get(cell);
            if (ingredient.isEmpty()) {
                continue;
            }
            int slot = cell % width + cell / width * GRID_WIDTH;
            ItemStack taken = take(ingredient.get(), 1, stored);
            if (!taken.isEmpty()) {
                inventory.getItems().set(slot, taken);
            }
        }
    }

    /** Tops each crystal slot up to the amount the recipe asks for. */
    private void placeCrystals(IArcaneRecipe recipe, List<StoredItemStack> stored) {
        for (AspectInstance entry : recipe.crystalCost().entries()) {
            ResourceKey<IAspect> primal = entry.aspect().getKey();
            int index = primal == null ? -1 : MenuArcaneWorkbench.PRIMAL_ORDER.indexOf(primal);
            if (index < 0) {
                continue;
            }
            int slot = InventoryArcaneWorkbench.CRAFTING_SLOTS + index;
            ItemStack current = inventory.getItem(slot);
            if (!current.isEmpty() && !SlotCrystalEssentia.isValidCrystal(current, primal)) {
                continue;
            }
            int missing = entry.amount() - current.getCount();
            if (missing <= 0) {
                continue;
            }
            Predicate<ItemStack> matches = stack -> SlotCrystalEssentia.isValidCrystal(stack, primal) && (current.isEmpty() || ItemStack.isSameItemSameComponents(stack, current));
            ItemStack taken = take(matches, missing, stored);
            if (taken.isEmpty()) {
                continue;
            }
            if (current.isEmpty()) {
                inventory.getItems().set(slot, taken);
            } else {
                current.grow(taken.getCount());
            }
        }
    }

    /** Takes up to {@code count} matching items, from storage first, then the player's inventory. */
    private ItemStack take(Predicate<ItemStack> matches, int count, List<StoredItemStack> stored) {
        for (StoredItemStack candidate : stored) {
            if (matches.test(candidate.getStack())) {
                StoredItemStack pulled = terminal.pullStack(candidate, count);
                if (pulled != null) {
                    return pulled.getActualStack();
                }
            }
        }
        Inventory playerInventory = player.getInventory();
        for (int slot = 0; slot < playerInventory.getNonEquipmentItems().size(); slot++) {
            ItemStack stack = playerInventory.getItem(slot);
            if (!stack.isEmpty() && matches.test(stack)) {
                return playerInventory.removeItem(slot, Math.min(count, stack.getCount()));
            }
        }
        return ItemStack.EMPTY;
    }
}
