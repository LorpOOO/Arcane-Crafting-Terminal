package com.arcaneterminal.menu;

import com.arcaneterminal.block.ArcaneCraftingTerminalBlockEntity;
import com.arcaneterminal.registry.ATRegistry;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneCraftingTransaction;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneWorkbenchContext;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneCraftingStore;
import com.leclowndu93150.thaumaturge.content.recipe.workbench.ArcaneCraftingInput;
import com.leclowndu93150.thaumaturge.content.research.ResearchProgressionEvents;
import com.leclowndu93150.thaumaturge.content.workbench.*;
import com.tom.storagemod.menu.StorageTerminalMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.neoforge.transfer.transaction.RootCommitJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Tom's storage terminal menu with the arcane workbench slots added below the item view. Crafting
 * follows Thaumaturge's own workbench menu: arcane recipes go through
 * {@link ArcaneCraftingTransaction} as a placed workbench, and plain crafting recipes still work.
 */
public class ArcaneCraftingTerminalMenu extends StorageTerminalMenu {
    public static final int RESULT_SLOT = 0;
    public static final int GRID_START = 1;
    public static final int GRID_END = GRID_START + InventoryArcaneWorkbench.CRAFTING_SLOTS;
    public static final int CRYSTAL_START = GRID_END;
    public static final int WAND_SLOT = CRYSTAL_START + InventoryArcaneWorkbench.CRYSTAL_SLOTS;
    public static final int PLAYER_START = WAND_SLOT + 1;
    public static final int PLAYER_END = PLAYER_START + 36;
    public static final int CLEAR_GRID_BUTTON = 0;
    public static final int TOGGLE_MODE_BUTTON = 1;
    /**
     * Message key carrying the id of a recipe to lay out in the grid.
     */
    public static final String FILL_RECIPE_KEY = "arcane_terminal_fill";

    private static final int AURA_REFRESH_INTERVAL = 10;
    private static final int AURA_DATA_SLOT = 0;
    private static final int HOTBAR_LAST_KEY = 8;
    private static final int OFFHAND_KEY = 40;

    private final InventoryArcaneWorkbench inventory;
    private final ResultContainer result = new ResultContainer();
    private final SimpleContainerData auraData = new SimpleContainerData(1);
    private final @Nullable ArcaneCraftingTerminalBlockEntity terminal;
    private final @Nullable ServerPlayer serverPlayer;
    private final @Nullable ServerLevel serverLevel;
    private final Runnable changeListener = this::inventoryChanged;
    private TerminalResultSlot resultSlot;
    private @Nullable RecipeHolder<CraftingRecipe> usedRecipe;
    private boolean arcaneResult;
    private boolean auraRead;
    private long lastAuraRefresh;
    private boolean clientArcaneMode = true;

    /**
     * Client side constructor.
     */
    public ArcaneCraftingTerminalMenu(int id, Inventory playerInventory) {
        super(ATRegistry.ARCANE_CRAFTING_TERMINAL_MENU.get(), id, playerInventory);
        this.inventory = new InventoryArcaneWorkbench();
        this.terminal = null;
        this.serverPlayer = null;
        this.serverLevel = null;
        init(playerInventory);
    }

    /**
     * Server side constructor.
     */
    public ArcaneCraftingTerminalMenu(int id, Inventory playerInventory, @Nullable ArcaneCraftingTerminalBlockEntity terminal) {
        super(ATRegistry.ARCANE_CRAFTING_TERMINAL_MENU.get(), id, playerInventory, terminal);
        this.inventory = Objects.requireNonNull(terminal).getInventory();
        this.terminal = terminal;
        this.serverPlayer = playerInventory.player instanceof ServerPlayer sp ? sp : null;
        this.serverLevel = terminal.getLevel() instanceof ServerLevel sl ? sl : null;
        init(playerInventory);
    }

    private void init(Inventory playerInventory) {
        resultSlot = new TerminalResultSlot(result, this, TerminalLayout.RESULT_X, TerminalLayout.RESULT_Y);
        addSlot(resultSlot);
        for (int cell = 0; cell < InventoryArcaneWorkbench.CRAFTING_SLOTS; cell++) {
            int x = TerminalLayout.GRID_X + cell % TerminalLayout.GRID_SIZE * TerminalLayout.GRID_SPACING;
            int y = TerminalLayout.GRID_Y + cell / TerminalLayout.GRID_SIZE * TerminalLayout.GRID_SPACING;
            addSlot(new Slot(inventory, cell, x, y));
        }
        int index = 0;
        for (ResourceKey<IAspect> primal : MenuArcaneWorkbench.PRIMAL_ORDER) {
            addSlot(new SlotCrystalEssentia(inventory, InventoryArcaneWorkbench.CRAFTING_SLOTS + index, TerminalLayout.crystalX(index), TerminalLayout.crystalY(index), primal));
            index++;
        }
        addSlot(new SlotWorkbenchWand(inventory, InventoryArcaneWorkbench.WAND_SLOT, TerminalLayout.WAND_X, TerminalLayout.WAND_Y));
        addPlayerSlots(playerInventory, TerminalLayout.INVENTORY_X, TerminalLayout.INVENTORY_Y);
        addDataSlots(auraData);
        addDataSlot(new ModeSlot());
        layoutSlots(isArcaneMode());
        if (craftsOnServer()) {
            inventory.addChangedListener(changeListener);
            refreshResult();
        }
    }

    /**
     * Whether the arcane workbench layout is shown; false shows Tom's Crafting Terminal layout.
     */
    public boolean isArcaneMode() {
        return terminal != null ? terminal.isArcaneMode() : clientArcaneMode;
    }

    private final class ModeSlot extends DataSlot {
        @Override
        public int get() {
            return isArcaneMode() ? 1 : 0;
        }

        @Override
        public void set(int value) {
            boolean arcane = value != 0;
            if (arcane != clientArcaneMode) {
                clientArcaneMode = arcane;
                layoutSlots(arcane);
            }
        }
    }

    /**
     * Moves every slot to where the given mode draws it. Tom's terminal keeps the base positions in
     * {@link #slotData} and applies the tall-mode offset on top, so the entries are replaced here.
     */
    private void layoutSlots(boolean arcane) {
        for (int index = 0; index < slots.size(); index++) {
            Slot slot = slots.get(index);
            SlotData current = slotData.get(index);
            int x;
            int y;
            if (index == RESULT_SLOT) {
                x = arcane ? TerminalLayout.RESULT_X : TerminalLayout.CRAFTING_RESULT_X;
                y = arcane ? TerminalLayout.RESULT_Y : TerminalLayout.CRAFTING_RESULT_Y;
            } else if (index < GRID_END) {
                int cell = index - GRID_START;
                int column = cell % TerminalLayout.GRID_SIZE;
                int row = cell / TerminalLayout.GRID_SIZE;
                x = arcane ? TerminalLayout.GRID_X + column * TerminalLayout.GRID_SPACING : TerminalLayout.CRAFTING_GRID_X + column * TerminalLayout.SLOT_SPACING;
                y = arcane ? TerminalLayout.GRID_Y + row * TerminalLayout.GRID_SPACING : TerminalLayout.CRAFTING_GRID_Y + row * TerminalLayout.SLOT_SPACING;
            } else if (index < PLAYER_START) {
                if (arcane) {
                    boolean wand = index == WAND_SLOT;
                    x = wand ? TerminalLayout.WAND_X : TerminalLayout.crystalX(index - CRYSTAL_START);
                    y = wand ? TerminalLayout.WAND_Y : TerminalLayout.crystalY(index - CRYSTAL_START);
                } else {
                    x = TerminalLayout.HIDDEN;
                    y = TerminalLayout.HIDDEN;
                }
            } else {
                int left = arcane ? TerminalLayout.INVENTORY_X : TerminalLayout.CRAFTING_INVENTORY_X;
                int top = arcane ? TerminalLayout.INVENTORY_Y : TerminalLayout.CRAFTING_INVENTORY_Y;
                int cell = index - PLAYER_START;
                if (cell < 27) {
                    x = left + cell % 9 * TerminalLayout.SLOT_SPACING;
                    y = top + cell / 9 * TerminalLayout.SLOT_SPACING;
                } else if (cell < 36) {
                    x = left + (cell - 27) * TerminalLayout.SLOT_SPACING;
                    y = top + 58;
                } else {
                    x = current.x();
                    y = top + 58;
                }
            }
            slotData.set(index, new SlotData(slot, x, y));
        }
        setOffset(0, 0);
    }

    boolean craftsOnServer() {
        return terminal != null && serverPlayer != null && serverLevel != null;
    }

    public InventoryArcaneWorkbench getCraftingInventory() {
        return inventory;
    }

    public int getCachedVis() {
        return auraData.get(AURA_DATA_SLOT);
    }

    private void inventoryChanged() {
        slotsChanged(inventory);
    }

    @Override
    public void slotsChanged(@NonNull Container container) {
        if (container == inventory) {
            refreshResult();
        }
    }

    private ArcaneWorkbenchContext context() {
        return ArcaneWorkbenchContext.placed(Objects.requireNonNull(serverPlayer), Objects.requireNonNull(terminal).getBlockPos(), terminal.hostIdentity(), null);
    }

    private void refreshResult() {
        if (!craftsOnServer()) {
            return;
        }
        arcaneResult = false;
        usedRecipe = null;
        ItemStack output = ItemStack.EMPTY;
        ArcaneCraftingInput arcaneInput = inventory.asPositionedArcaneCraftInput().input();
        if (arcaneInput.width() > 0) {
            ArcaneCraftingTransaction.Result preview = ArcaneCraftingTransaction.preview(context(), serverPlayer, arcaneInput.withPlayer(serverPlayer));
            if (preview.successful() && !preview.output().isEmpty()) {
                arcaneResult = true;
                output = preview.output();
            }
        }
        if (!arcaneResult) {
            CraftingInput craftingInput = inventory.asPositionedCraftInput().input();
            if (craftingInput.width() > 0) {
                Optional<RecipeHolder<CraftingRecipe>> match = Optional.empty();
                if (serverLevel != null) {
                    match = serverLevel.recipeAccess().getRecipeFor(RecipeType.CRAFTING, craftingInput, serverLevel);
                }
                if (match.isPresent()) {
                    usedRecipe = match.get();
                    output = usedRecipe.value().assemble(craftingInput);
                }
            }
        }
        result.setItem(0, output);
    }

    ItemStack takeCraft() {
        if (!craftsOnServer()) {
            return ItemStack.EMPTY;
        }
        List<ItemStack> before = null;
        if (terminal != null) {
            before = terminal.gridSnapshot();
        }
        ItemStack crafted;
        try (Transaction transaction = Transaction.openRoot()) {
            crafted = craft(transaction);
            if (!crafted.isEmpty()) {
                transaction.commit();
            }
        }
        if (crafted.isEmpty()) {
            refreshResult();
        } else {
            if (terminal != null) {
                terminal.refillGrid(before);
            }
        }
        return crafted;
    }

    private ItemStack craft(TransactionContext transaction) {
        ItemStack shown = result.getItem(0).copy();
        if (shown.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack crafted = arcaneResult ? craftArcane(transaction) : craftVanilla(transaction);
        return !crafted.isEmpty() && ItemStack.matches(crafted, shown) ? crafted : ItemStack.EMPTY;
    }

    private ItemStack craftArcane(TransactionContext transaction) {
        ArcaneCraftingInput.Positioned positioned = inventory.asPositionedArcaneCraftInput();
        ArcaneCraftingInput input = positioned.input().withPlayer(serverPlayer);
        if (input.width() == 0) {
            return ItemStack.EMPTY;
        }
        WorkbenchCraftingStore store = new WorkbenchCraftingStore(inventory, serverPlayer, positioned.left(), positioned.top(), input.width(), input.height());
        ArcaneCraftingTransaction.Result crafted = ArcaneCraftingTransaction.craft(context(), serverPlayer, input, store, transaction);
        return crafted.successful() ? crafted.output() : ItemStack.EMPTY;
    }

    private ItemStack craftVanilla(TransactionContext transaction) {
        if (usedRecipe == null) {
            return ItemStack.EMPTY;
        }
        CraftingInput.Positioned positioned = inventory.asPositionedCraftInput();
        CraftingInput input = positioned.input();
        CraftingRecipe recipe = usedRecipe.value();
        if (serverLevel != null && (input.width() == 0 || !recipe.matches(input, serverLevel))) {
            return ItemStack.EMPTY;
        }
        ItemStack output = recipe.assemble(input);
        WorkbenchCraftingStore store = new WorkbenchCraftingStore(inventory, serverPlayer, positioned.left(), positioned.top(), input.width(), input.height());
        IArcaneCraftingStore.Consumption consumption = new IArcaneCraftingStore.Consumption(input.items(), recipe.getRemainingItems(input), AspectList.EMPTY, inventory.wandStack());
        if (!store.consume(consumption, transaction)) {
            return ItemStack.EMPTY;
        }
        ItemStack recorded = output.copy();
        ServerPlayer crafter = serverPlayer;
        new RootCommitJournal(() -> ResearchProgressionEvents.recordCrafted(crafter, recorded)).updateSnapshots(transaction);
        return output;
    }

    @Override
    public void broadcastChanges() {
        if (craftsOnServer()) {
            long time = 0;
            if (serverLevel != null) {
                time = serverLevel.getGameTime();
            }
            if (!auraRead || time < lastAuraRefresh || time - lastAuraRefresh >= AURA_REFRESH_INTERVAL) {
                if (terminal != null) {
                    terminal.refreshAura();
                }
                boolean first = !auraRead;
                auraRead = true;
                lastAuraRefresh = time;
                if (terminal != null && (first || terminal.auraVis != auraData.get(AURA_DATA_SLOT))) {
                    auraData.set(AURA_DATA_SLOT, terminal.auraVis);
                    refreshResult();
                }
            }
        }
        super.broadcastChanges();
    }

    @Override
    public void removed(@NonNull Player player) {
        super.removed(player);
        inventory.removeChangedListener(changeListener);
    }

    /**
     * Fills the grid for a recipe the client picked in JEI; see {@link #FILL_RECIPE_KEY}.
     */
    @Override
    public void receive(ValueInput message) {
        super.receive(message);
        if (serverPlayer != null && (!craftsOnServer() || serverPlayer.isSpectator())) {
            return;
        }
        message.getString(FILL_RECIPE_KEY)
                .map(Identifier::tryParse)
                .flatMap(id -> Objects.requireNonNull(serverLevel).getServer()
                        .getRecipeManager()
                        .byKey(ResourceKey.create(Registries.RECIPE, id)))
                .ifPresent(holder -> {
                    if (terminal != null) {
                        new TerminalRecipeFiller(terminal, serverPlayer).place(holder.value());
                    }
                });
    }

    @Override
    public boolean clickMenuButton(@NonNull Player player, int id) {
        if (id == CLEAR_GRID_BUTTON && terminal != null) {
            terminal.clearGrid(player);
            return true;
        }
        if (id == TOGGLE_MODE_BUTTON && terminal != null) {
            terminal.toggleMode();
            layoutSlots(terminal.isArcaneMode());
            return true;
        }
        return super.clickMenuButton(player, id);
    }

    @Override
    public boolean canTakeItemForPickAll(@NonNull ItemStack carried, Slot slot) {
        return slot.container != result && super.canTakeItemForPickAll(carried, slot);
    }

    @Override
    public void clicked(int slotIndex, int button, @NonNull ContainerInput input, @NonNull Player clicker) {
        if (input == ContainerInput.SWAP && slotIndex == RESULT_SLOT && craftsOnServer()) {
            swapCraft(button, clicker);
            return;
        }
        if (input == ContainerInput.QUICK_MOVE && slotIndex >= PLAYER_START && slotIndex < slots.size() && routeFromPlayer(slots.get(slotIndex))) {
            return;
        }
        super.clicked(slotIndex, button, input, clicker);
    }

    /**
     * Shift-clicking crystals or a wand in the player inventory fills the matching workbench slots
     * instead of sending them into the storage network.
     */
    private boolean routeFromPlayer(Slot slot) {
        if (!isArcaneMode() || !slot.hasItem() || !slot.mayPickup(serverPlayer != null ? serverPlayer : pinv.player)) {
            return false;
        }
        ItemStack stack = slot.getItem();
        boolean wand = SlotWorkbenchWand.isUsableWand(stack);
        boolean crystal = isAnyCrystal(stack);
        if (!wand && !crystal) {
            return false;
        }
        boolean moved = wand && moveItemStackTo(stack, WAND_SLOT, WAND_SLOT + 1, false);
        moved |= crystal && routeCrystals(stack);
        if (!moved) {
            return false;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return true;
    }

    private static boolean isAnyCrystal(ItemStack stack) {
        for (ResourceKey<IAspect> primal : MenuArcaneWorkbench.PRIMAL_ORDER) {
            if (SlotCrystalEssentia.isValidCrystal(stack, primal)) {
                return true;
            }
        }
        return false;
    }

    private boolean routeCrystals(ItemStack stack) {
        boolean moved = false;
        int target = CRYSTAL_START;
        for (ResourceKey<IAspect> primal : MenuArcaneWorkbench.PRIMAL_ORDER) {
            if (stack.isEmpty()) {
                break;
            }
            if (SlotCrystalEssentia.isValidCrystal(stack, primal)) {
                moved |= moveItemStackTo(stack, target, target + 1, false);
            }
            target++;
        }
        return moved;
    }

    private void swapCraft(int key, Player clicker) {
        Inventory carrier = clicker.getInventory();
        ItemStack crafted = canSwapInto(carrier, key) ? takeCraft() : ItemStack.EMPTY;
        if (crafted.isEmpty()) {
            return;
        }
        carrier.setItem(key, crafted);
        resultSlot.onSwapCraft(crafted.getCount());
        resultSlot.onTake(clicker, crafted);
    }

    private boolean canSwapInto(Inventory carrier, int key) {
        boolean hotbarKey = key >= 0 && key <= HOTBAR_LAST_KEY;
        if (!hotbarKey && key != OFFHAND_KEY) {
            return false;
        }
        return carrier.getItem(key).isEmpty() && resultSlot.hasItem();
    }

    @Override
    protected ItemStack shiftClickItems(Player clicker, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        if (index == RESULT_SLOT) {
            return craftsOnServer() ? quickCraft(clicker) : quickCraftPrediction(clicker, slot);
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < GRID_END) {
            return pushGridSlot(clicker, slot);
        }
        if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, false)) {
            return ItemStack.EMPTY;
        }
        return finishMove(clicker, slot, stack, original);
    }

    @SuppressWarnings("SameReturnValue")
    private ItemStack pushGridSlot(Player clicker, Slot slot) {
        if (terminal == null) {
            return ItemStack.EMPTY;
        }
        ItemStack rest = terminal.pushStack(slot.getItem().copy());
        if (!rest.isEmpty()) {
            moveItemStackTo(rest, PLAYER_START, PLAYER_END, false);
        }
        slot.set(rest);
        broadcastChanges();
        return ItemStack.EMPTY;
    }

    private ItemStack finishMove(Player clicker, Slot slot, ItemStack stack, ItemStack original) {
        if (!stack.isEmpty()) {
            slot.setChanged();
        } else {
            slot.setByPlayer(ItemStack.EMPTY);
        }
        if (stack.getCount() != original.getCount()) {
            slot.onTake(clicker, stack);
            return original;
        }
        return ItemStack.EMPTY;
    }

    private ItemStack quickCraftPrediction(Player clicker, Slot slot) {
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
            return ItemStack.EMPTY;
        }
        slot.onQuickCraft(stack, original);
        return finishMove(clicker, slot, stack, original);
    }

    private ItemStack quickCraft(Player clicker) {
        List<ItemStack> before = null;
        if (terminal != null) {
            before = terminal.gridSnapshot();
        }
        ItemStack crafted;
        ItemStack remainder;
        try (Transaction transaction = Transaction.openRoot()) {
            crafted = craft(transaction);
            remainder = crafted.copy();
            if (!crafted.isEmpty() && moveItemStackTo(remainder, PLAYER_START, PLAYER_END, true)) {
                transaction.commit();
            } else {
                crafted = ItemStack.EMPTY;
            }
        }
        if (crafted.isEmpty()) {
            refreshResult();
            return ItemStack.EMPTY;
        }
        if (terminal != null) {
            terminal.refillGrid(before);
        }
        resultSlot.onQuickCraft(remainder, crafted);
        if (!remainder.isEmpty()) {
            clicker.drop(remainder, false);
        }
        resultSlot.onTake(clicker, crafted);
        return crafted.copy();
    }
}
