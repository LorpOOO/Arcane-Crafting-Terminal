package com.arcaneterminal.compat.jei;

import com.arcaneterminal.menu.ArcaneCraftingTerminalMenu;
import com.arcaneterminal.registry.ATRegistry;
import com.tom.storagemod.inventory.TerminalItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * JEI's "+" button for the Arcane Crafting Terminal. Instead of JEI's own slot shuffling, which can
 * only use the player's inventory, it asks the server to lay the recipe out in the grid and crystal
 * slots, taking items from the storage network first, as Tom's Crafting Terminal does.
 */
public final class TerminalRecipeTransferHandler<R extends RecipeHolder<?>> implements IRecipeTransferHandler<ArcaneCraftingTerminalMenu, R> {
    private static final String MISSING_KEY = "tooltip.toms_storage.items_missing";

    private final IRecipeType<R> recipeType;
    private final IRecipeTransferHandlerHelper helper;

    public TerminalRecipeTransferHandler(IRecipeType<R> recipeType, IRecipeTransferHandlerHelper helper) {
        this.recipeType = recipeType;
        this.helper = helper;
    }

    @Override
    public @NonNull Class<? extends ArcaneCraftingTerminalMenu> getContainerClass() {
        return ArcaneCraftingTerminalMenu.class;
    }

    @Override
    public @NonNull Optional<MenuType<ArcaneCraftingTerminalMenu>> getMenuType() {
        return Optional.of(ATRegistry.ARCANE_CRAFTING_TERMINAL_MENU.get());
    }

    @Override
    public @NonNull IRecipeType<R> getRecipeType() {
        return recipeType;
    }

    @SuppressWarnings("removal")
    @Override
    public @Nullable IRecipeTransferError transferRecipe(
            @NonNull ArcaneCraftingTerminalMenu menu,
            @NonNull R recipe,
            IRecipeSlotsView recipeSlots,
            @NonNull Player player,
            boolean maxTransfer,
            boolean doTransfer) {
        List<ItemStack> available = availableStacks(menu, player);
        List<IRecipeSlotView> missing = new ArrayList<>();
        for (IRecipeSlotView view : recipeSlots.getSlotViews(RecipeIngredientRole.INPUT)) {
            List<ItemStack> options = view.getIngredients(VanillaTypes.ITEM_STACK).toList();
            if (!options.isEmpty() && options.stream().noneMatch(option -> available.stream().anyMatch(have -> ItemStack.isSameItemSameComponents(have, option)))) {
                missing.add(view);
            }
        }
        if (doTransfer) {
            CompoundTag message = new CompoundTag();
            message.putString(ArcaneCraftingTerminalMenu.FILL_RECIPE_KEY, recipe.id().identifier().toString());
            menu.sendMessage(message);
        }
        if (!missing.isEmpty()) {
            return new Warning(helper.createUserErrorForMissingSlots(Component.translatable(MISSING_KEY), missing));
        }
        return null;
    }

    /** Everything the server can fill from: the storage network, the player's inventory and the grid itself. */
    private static List<ItemStack> availableStacks(ArcaneCraftingTerminalMenu menu, Player player) {
        List<ItemStack> available = new ArrayList<>();
        for (TerminalItemStack stored : menu.itemList) {
            available.add(stored.getStack());
        }
        available.addAll(player.getInventory().getNonEquipmentItems());
        for (int index = ArcaneCraftingTerminalMenu.GRID_START; index < ArcaneCraftingTerminalMenu.WAND_SLOT; index++) {
            Slot slot = menu.slots.get(index);
            available.add(slot.getItem());
        }
        available.removeIf(ItemStack::isEmpty);
        return available;
    }

    /** Shows which ingredients could not be found, but still lets the transfer go ahead. */
    private record Warning(IRecipeTransferError parent) implements IRecipeTransferError {
        @Override
        public @NonNull Type getType() {
            return Type.COSMETIC;
        }

        @Override
        public void showError(
                @NonNull GuiGraphicsExtractor graphics,
                int mouseX,
                int mouseY,
                @NonNull IRecipeSlotsView recipeSlotsView,
                int recipeX,
                int recipeY) {
            parent.showError(graphics, mouseX, mouseY, recipeSlotsView, recipeX, recipeY);
        }
    }
}
