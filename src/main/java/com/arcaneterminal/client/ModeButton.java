package com.arcaneterminal.client;

import com.leclowndu93150.thaumaturge.TTIds;
import com.tom.storagemod.screen.widget.IconButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Side button in Tom's terminal style that switches between the arcane workbench layout and the
 * Crafting Terminal layout. It shows the block of the layout currently in use.
 */
public final class ModeButton extends IconButton {
    private static final Identifier ARCANE_ICON = TTIds.rl("arcane_workbench");
    private static final Identifier CRAFTING_ICON = Identifier.fromNamespaceAndPath("toms_storage", "crafting_terminal");

    private final ItemStack icon;

    public ModeButton(int x, int y, boolean arcane, Runnable onPress) {
        super(x, y, Component.translatable("narrator.arcane_terminal.mode"), null, onPress);
        this.icon = new ItemStack(BuiltInRegistries.ITEM.getValue(arcane ? ARCANE_ICON : CRAFTING_ICON));
    }

    @Override
    protected void drawIcon(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.item(icon, getX(), getY());
    }
}
