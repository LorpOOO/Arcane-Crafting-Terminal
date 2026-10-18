package com.arcaneterminal.client;

import com.arcaneterminal.ArcaneTerminal;
import com.arcaneterminal.menu.ArcaneCraftingTerminalMenu;
import com.arcaneterminal.menu.TerminalLayout;
import com.leclowndu93150.thaumaturge.TTIds;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneRecipe;
import com.leclowndu93150.thaumaturge.content.recipe.ThaumaturgeCraftingManager;
import com.leclowndu93150.thaumaturge.content.recipe.workbench.ArcaneCraftingInput;
import com.leclowndu93150.thaumaturge.content.wands.WandEconomy;
import com.leclowndu93150.thaumaturge.content.wands.WandTooltips;
import com.leclowndu93150.thaumaturge.content.workbench.MenuArcaneWorkbench;
import com.leclowndu93150.thaumaturge.content.workbench.WorkbenchPayment;
import com.tom.storagemod.screen.AbstractStorageTerminalScreen;
import com.tom.storagemod.screen.CraftingTerminalScreen;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public class ArcaneCraftingTerminalScreen extends AbstractStorageTerminalScreen<ArcaneCraftingTerminalMenu> {
    private static final Identifier STORAGE_TEXTURE = ArcaneTerminal.id("textures/gui/arcane_crafting_terminal.png");
    private static final Identifier CRAFTING_TEXTURE = Identifier.fromNamespaceAndPath("toms_storage", "textures/gui/crafting_terminal.png");
    private static final Identifier ARCANE_TEXTURE = TTIds.rl("textures/gui/arcane_workbench.png");
    private static final Identifier WAND_SLOT_TEXTURE = TTIds.rl("textures/gui/workbench_wand_slot.png");
    private static final Identifier FLOATING_SLOT = Identifier.fromNamespaceAndPath("toms_storage", "widget/floating_slot");
    private static final int TEX_SIZE = 256;

    private static final int STORAGE_ROW_LAST_V = TerminalLayout.STORAGE_SLOT_Y + 4 * TerminalLayout.ROW_HEIGHT;
    private static final int STORAGE_BOTTOM_V = TerminalLayout.STORAGE_SLOT_Y + 5 * TerminalLayout.ROW_HEIGHT;
    private static final int STORAGE_BOTTOM_HEIGHT = 7;

    private static final int WAND_SLOT_TEX_W = 38;
    private static final int WAND_SLOT_TEX_H = 34;
    private static final int WAND_SLOT_TEX_OFFSET_X = 10;
    private static final int WAND_SLOT_TEX_OFFSET_Y = 6;
    private static final int WAND_COST_Y = TerminalLayout.ARCANE_Y + 129;
    private static final int TEXT_HOVER_ABOVE = 1;
    private static final int TEXT_HOVER_BELOW = 6;
    private static final int WAND_COST_HOVER_MIN_HALF_WIDTH = 10;
    private static final int SLOT_SIZE = 16;
    private static final DecimalFormat WAND_COST_FORMAT = new DecimalFormat("#.##");

    private static final int GLOW_U = 192;
    private static final int GLOW_V = 0;
    private static final int GLOW_SOURCE_SIZE = 64;
    private static final int GLOW_SIZE = GLOW_SOURCE_SIZE / 2;
    private static final int GLOW_ORIGIN = -GLOW_SIZE / 2;
    private static final int GLOW_ALPHA = 128;
    private static final float GLOW_SLOT_PHASE = 60.0F;
    private static final float GLOW_TICKS_PER_RADIAN = 75.0F;
    private static final float GLOW_PIVOT_X = 7.5F;
    private static final float GLOW_PIVOT_Y = 8.0F;

    private static final int VIS_TEXT_X = TerminalLayout.ARCANE_X + 168;
    private static final int VIS_AVAILABLE_Y = TerminalLayout.ARCANE_Y + 46;
    private static final int VIS_COST_Y = TerminalLayout.ARCANE_Y + 38;
    private static final float TEXT_SCALE = 0.5F;
    private static final int VIS_SHORT_COLOR = 0xFFEE6E6E;
    private static final int VIS_ENOUGH_COLOR = 0xFF6E6EEE;
    private static final int VIS_COST_COLOR = 0xFFC0FFFF;
    private static final int WAND_COST_COLOR = 0xFFFFFFFF;
    private static final int PERCENT = 100;
    private static final String WAND_COST_SEPARATOR = " | ";
    private static final int HIDDEN_LABEL = -10000;

    private final Inventory playerInventory;
    /** The layout this screen instance was built for; a mode change swaps in a new screen. */
    private final boolean arcane;

    public ArcaneCraftingTerminalScreen(ArcaneCraftingTerminalMenu menu, Inventory inventory, Component title) {
        this(menu, inventory, title, menu.isArcaneMode());
    }

    private ArcaneCraftingTerminalScreen(ArcaneCraftingTerminalMenu menu, Inventory inventory, Component title, boolean arcane) {
        super(menu, inventory, title, arcane ? TerminalLayout.STORAGE_ROWS : TerminalLayout.CRAFTING_STORAGE_ROWS, TerminalLayout.WIDTH, arcane ? TerminalLayout.HEIGHT : TerminalLayout.CRAFTING_HEIGHT,
                TerminalLayout.STORAGE_SLOT_X, TerminalLayout.STORAGE_SLOT_Y);
        this.playerInventory = inventory;
        this.arcane = arcane;
    }

    @Override
    public Identifier getGui() {
        return arcane ? STORAGE_TEXTURE : CRAFTING_TEXTURE;
    }

    /** How far tall mode pushed the section under the storage view down. */
    private int rowShift() {
        return (rowCount - textureSlotCount) * TerminalLayout.ROW_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        int clearX = arcane ? TerminalLayout.CLEAR_BUTTON_X : TerminalLayout.CRAFTING_CLEAR_BUTTON_X;
        int clearY = arcane ? TerminalLayout.CLEAR_BUTTON_Y + rowShift() : 20 + rowCount * TerminalLayout.ROW_HEIGHT;
        if (arcane) {
            inventoryLabelY = HIDDEN_LABEL;
        }
        CraftingTerminalScreen.ButtonClear clear = new CraftingTerminalScreen.ButtonClear(leftPos + clearX, topPos + clearY, button -> clickButton(ArcaneCraftingTerminalMenu.CLEAR_GRID_BUTTON));
        clear.setTooltip(Tooltip.create(Component.translatable("tooltip.arcane_terminal.clear_grid")));
        addRenderableWidget(clear);
        ModeButton mode = new ModeButton(leftPos - 18, topPos + 5 + 18 * 6, arcane, () -> clickButton(ArcaneCraftingTerminalMenu.TOGGLE_MODE_BUTTON));
        mode.setTooltip(Tooltip.create(Component.translatable(arcane ? "tooltip.arcane_terminal.mode_arcane" : "tooltip.arcane_terminal.mode_crafting")));
        addRenderableWidget(mode);
        onPacket();
    }

    private void clickButton(int id) {
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (menu.isArcaneMode() != arcane) {
            minecraft.setScreen(new ArcaneCraftingTerminalScreen(menu, playerInventory, title, menu.isArcaneMode()));
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (!arcane) {
            super.extractBackground(graphics, mouseX, mouseY, partialTick);
            return;
        }
        extractTransparentBackground(graphics);
        extractStoragePanel(graphics);
        int arcaneTop = topPos + TerminalLayout.ARCANE_Y + rowShift();
        graphics.blit(RenderPipelines.GUI_TEXTURED, ARCANE_TEXTURE, leftPos + TerminalLayout.ARCANE_X, arcaneTop, 0.0F, 0.0F, TerminalLayout.ARCANE_GUI_WIDTH, TerminalLayout.ARCANE_GUI_HEIGHT, TEX_SIZE,
                TEX_SIZE);
        Slot offhand = menu.offhand;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FLOATING_SLOT, leftPos + offhand.x - 8, topPos + offhand.y - 8, 32, 32);
        extractArcaneOverlay(graphics, mouseX, mouseY, partialTick);
    }

    private void extractStoragePanel(GuiGraphicsExtractor graphics) {
        int width = imageWidth;
        int rowHeight = TerminalLayout.ROW_HEIGHT;
        int slotY = TerminalLayout.STORAGE_SLOT_Y;
        graphics.blit(RenderPipelines.GUI_TEXTURED, STORAGE_TEXTURE, leftPos, topPos, 0.0F, 0.0F, width, slotY, TEX_SIZE, TEX_SIZE);
        for (int row = 0; row < rowCount; row++) {
            int v;
            if (row == 0) {
                v = slotY;
            } else if (row == rowCount - 1) {
                v = STORAGE_ROW_LAST_V;
            } else {
                v = slotY + rowHeight;
            }
            graphics.blit(RenderPipelines.GUI_TEXTURED, STORAGE_TEXTURE, leftPos, topPos + slotY + row * rowHeight, 0.0F, v, width, rowHeight, TEX_SIZE, TEX_SIZE);
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, STORAGE_TEXTURE, leftPos, topPos + slotY + rowCount * rowHeight, 0.0F, STORAGE_BOTTOM_V, width, STORAGE_BOTTOM_HEIGHT, TEX_SIZE, TEX_SIZE);
    }

    private void extractArcaneOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int shift = rowShift();
        graphics.blit(RenderPipelines.GUI_TEXTURED, WAND_SLOT_TEXTURE, leftPos + TerminalLayout.WAND_X - WAND_SLOT_TEX_OFFSET_X, topPos + TerminalLayout.WAND_Y + shift - WAND_SLOT_TEX_OFFSET_Y, 0.0F, 0.0F,
                WAND_SLOT_TEX_W, WAND_SLOT_TEX_H, WAND_SLOT_TEX_W, WAND_SLOT_TEX_H);
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            return;
        }
        IArcaneRecipe recipe = currentRecipe(level, player);
        WorkbenchPayment.Plan plan = recipe == null ? null : WorkbenchPayment.plan(recipe, menu.getCraftingInventory(), player);
        int available = menu.getCachedVis();
        int required = plan == null ? 0 : plan.auraVis();
        if (plan != null) {
            drawCrystalGlows(graphics, plan, player, partialTick, shift);
        }
        boolean visShort = available < required;
        Component availableLine = Component.translatable("gui.thaumaturge.arcane_workbench.vis_available", available);
        drawHalfText(graphics, availableLine, VIS_TEXT_X, VIS_AVAILABLE_Y + shift, visShort ? VIS_SHORT_COLOR : VIS_ENOUGH_COLOR);
        if (isOverHalfText(availableLine, VIS_TEXT_X, VIS_AVAILABLE_Y + shift, 0, mouseX, mouseY)) {
            graphics.setComponentTooltipForNextFrame(font, visAvailableTooltip(visShort), mouseX, mouseY);
        }
        if (recipe != null && plan != null && required > 0) {
            Component costLine = costLine(recipe, plan);
            drawHalfText(graphics, costLine, VIS_TEXT_X, VIS_COST_Y + shift, VIS_COST_COLOR);
            if (isOverHalfText(costLine, VIS_TEXT_X, VIS_COST_Y + shift, 0, mouseX, mouseY)) {
                graphics.setComponentTooltipForNextFrame(font, costTooltip(recipe, plan), mouseX, mouseY);
            }
        }
        if (plan != null) {
            showCrystalTooltip(graphics, plan, mouseX, mouseY, shift);
            if (!plan.wandCentivis().isEmpty()) {
                drawWandPayment(graphics, level.registryAccess(), plan.wandCentivis(), mouseX, mouseY, shift);
            }
        }
    }

    private @Nullable IArcaneRecipe currentRecipe(ClientLevel level, LocalPlayer player) {
        ArcaneCraftingInput input = menu.getCraftingInventory().asPositionedArcaneCraftInput().input();
        if (input.width() == 0) {
            return null;
        }
        return ThaumaturgeCraftingManager.findMatchingArcaneRecipe(level, input.withPlayer(player), player);
    }

    private void drawCrystalGlows(GuiGraphicsExtractor graphics, WorkbenchPayment.Plan plan, LocalPlayer player, float partialTick, int shift) {
        Entity camera = minecraft.getCameraEntity();
        float age = (camera == null ? player.tickCount : camera.tickCount) + partialTick;
        for (AspectInstance entry : plan.crystalsToConsume().entries()) {
            int slot = crystalSlot(entry);
            if (slot < 0) {
                continue;
            }
            int color = ARGB.color(GLOW_ALPHA, entry.aspect().value().color());
            graphics.pose().pushMatrix();
            graphics.pose().translate(leftPos + TerminalLayout.crystalX(slot) + GLOW_PIVOT_X, topPos + TerminalLayout.crystalY(slot) + shift + GLOW_PIVOT_Y);
            graphics.pose().rotate(slot * GLOW_SLOT_PHASE + age / GLOW_TICKS_PER_RADIAN);
            graphics.blit(RenderPipelines.GUI_TEXTURED, ARCANE_TEXTURE, GLOW_ORIGIN, GLOW_ORIGIN, GLOW_U, GLOW_V, GLOW_SIZE, GLOW_SIZE, GLOW_SOURCE_SIZE, GLOW_SOURCE_SIZE, TEX_SIZE, TEX_SIZE, color);
            graphics.pose().popMatrix();
        }
    }

    private void showCrystalTooltip(GuiGraphicsExtractor graphics, WorkbenchPayment.Plan plan, int mouseX, int mouseY, int shift) {
        for (AspectInstance entry : plan.crystalsToConsume().entries()) {
            int slot = crystalSlot(entry);
            if (slot < 0) {
                continue;
            }
            int x0 = leftPos + TerminalLayout.crystalX(slot);
            int y0 = topPos + TerminalLayout.crystalY(slot) + shift;
            if (!isInside(x0, y0, x0 + SLOT_SIZE, y0 + SLOT_SIZE, mouseX, mouseY)) {
                continue;
            }
            List<Component> lines = new ArrayList<>();
            ItemStack crystal = menu.getCraftingInventory().getItem(MenuArcaneWorkbench.CRYSTAL_SLOT_START + slot);
            if (!crystal.isEmpty()) {
                lines.add(crystal.getHoverName());
            }
            lines.add(Component.translatable("gui.thaumaturge.arcane_workbench.crystal_needed.tooltip", WandEconomy.CRYSTAL_SUBSTITUTE_VIS).withStyle(ChatFormatting.GRAY));
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }
    }

    private static int crystalSlot(AspectInstance entry) {
        ResourceKey<IAspect> key = entry.aspect().getKey();
        return key == null ? -1 : MenuArcaneWorkbench.PRIMAL_ORDER.indexOf(key);
    }

    private List<Component> visAvailableTooltip(boolean insufficient) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.thaumaturge.arcane_workbench.vis_available.tooltip.title").withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("gui.arcane_terminal.vis_available.tooltip").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("gui.arcane_terminal.charger.tooltip").withStyle(ChatFormatting.DARK_PURPLE));
        if (insufficient) {
            lines.add(Component.translatable("gui.thaumaturge.arcane_workbench.vis_available.tooltip.short").withStyle(ChatFormatting.RED));
        }
        return lines;
    }

    private static Component costLine(IArcaneRecipe recipe, WorkbenchPayment.Plan plan) {
        int vis = plan.auraVis();
        int discount = discountPercent(recipe, plan);
        if (discount > 0) {
            return Component.translatable("gui.thaumaturge.arcane_workbench.required_vis_discount", vis, discount);
        }
        return plan.crystalsToConsume().isEmpty() ? Component.translatable("gui.thaumaturge.arcane_workbench.required_vis", vis) : Component.translatable("gui.thaumaturge.arcane_workbench.required_vis_crude", vis);
    }

    private static List<Component> costTooltip(IArcaneRecipe recipe, WorkbenchPayment.Plan plan) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.thaumaturge.arcane_workbench.required_vis.tooltip.title").withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("gui.thaumaturge.arcane_workbench.required_vis.tooltip").withStyle(ChatFormatting.GRAY));
        int discount = discountPercent(recipe, plan);
        if (discount > 0) {
            lines.add(Component.translatable("gui.thaumaturge.arcane_workbench.required_vis_discount.tooltip", discount).withStyle(ChatFormatting.GREEN));
        } else if (!plan.crystalsToConsume().isEmpty()) {
            int surcharge = Math.round((WandEconomy.CRAFT_AURA_SURCHARGE - 1.0F) * PERCENT);
            lines.add(Component.translatable("gui.thaumaturge.arcane_workbench.required_vis_crude.tooltip", surcharge).withStyle(ChatFormatting.GOLD));
        }
        return lines;
    }

    private static int discountPercent(IArcaneRecipe recipe, WorkbenchPayment.Plan plan) {
        int base = recipe.visCost();
        if (base <= 0 || plan.auraVis() >= base) {
            return 0;
        }
        return Math.round(PERCENT - plan.auraVis() * (float) PERCENT / base);
    }

    private void drawWandPayment(GuiGraphicsExtractor graphics, HolderLookup.Provider registries, Map<ResourceKey<IAspect>, Integer> wandCentivis, int mouseX, int mouseY, int shift) {
        MutableComponent line = Component.empty();
        boolean first = true;
        for (Map.Entry<ResourceKey<IAspect>, Integer> entry : wandCentivis.entrySet()) {
            if (!first) {
                line.append(Component.literal(WAND_COST_SEPARATOR).withStyle(ChatFormatting.DARK_GRAY));
            }
            String amount = WAND_COST_FORMAT.format(entry.getValue() / (double) WandEconomy.CENTIVIS_PER_VIS);
            line.append(Component.literal(amount).withStyle(WandTooltips.primalColor(registries, entry.getKey())));
            first = false;
        }
        int centreX = TerminalLayout.WAND_X + SLOT_SIZE / 2;
        drawHalfText(graphics, line, centreX, WAND_COST_Y + shift, WAND_COST_COLOR);
        if (isOverHalfText(line, centreX, WAND_COST_Y + shift, WAND_COST_HOVER_MIN_HALF_WIDTH, mouseX, mouseY)) {
            List<Component> tooltip = List.of(Component.translatable("gui.thaumaturge.arcane_workbench.wand_pay.tooltip.title").withStyle(ChatFormatting.AQUA),
                    Component.translatable("gui.thaumaturge.arcane_workbench.wand_pay.tooltip", WandEconomy.CRYSTAL_SUBSTITUTE_VIS).withStyle(ChatFormatting.GRAY));
            graphics.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
        }
    }

    private boolean isOverHalfText(Component text, int centreX, int top, int minHalfWidth, int mouseX, int mouseY) {
        int halfWidth = Math.max(font.width(text) / 2 / 2, minHalfWidth);
        int cx = leftPos + centreX;
        int y = topPos + top;
        return isInside(cx - halfWidth, y - TEXT_HOVER_ABOVE, cx + halfWidth, y + TEXT_HOVER_BELOW, mouseX, mouseY);
    }

    private static boolean isInside(int x0, int y0, int x1, int y1, int mouseX, int mouseY) {
        return x0 <= mouseX && mouseX < x1 && y0 <= mouseY && mouseY < y1;
    }

    private void drawHalfText(GuiGraphicsExtractor graphics, Component text, int centreX, int top, int color) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(leftPos + centreX, topPos + top);
        graphics.pose().scale(TEXT_SCALE, TEXT_SCALE);
        graphics.text(font, text, -font.width(text) / 2, 0, color, false);
        graphics.pose().popMatrix();
    }
}
