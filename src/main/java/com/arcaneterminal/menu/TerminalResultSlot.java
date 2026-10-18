package com.arcaneterminal.menu;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

/** Result slot whose removal runs the craft on the server, like Thaumaturge's arcane result slot. */
public final class TerminalResultSlot extends Slot {
    private final ArcaneCraftingTerminalMenu menu;
    private int amountCrafted;

    public TerminalResultSlot(ResultContainer result, ArcaneCraftingTerminalMenu menu, int x, int y) {
        super(result, 0, x, y);
        this.menu = menu;
    }

    @Override
    public boolean mayPlace(@NonNull ItemStack stack) {
        return false;
    }

    @Override
    public @NonNull ItemStack remove(int amount) {
        return menu.craftsOnServer() ? menu.takeCraft() : super.remove(amount);
    }

    @Override
    protected void onQuickCraft(@NonNull ItemStack stack, int amount) {
        amountCrafted += amount;
    }

    @Override
    protected void onSwapCraft(int numItemsCrafted) {
        amountCrafted += numItemsCrafted;
    }

    @Override
    public void onTake(@NonNull Player player, @NonNull ItemStack stack) {
        amountCrafted = 0;
    }
}
