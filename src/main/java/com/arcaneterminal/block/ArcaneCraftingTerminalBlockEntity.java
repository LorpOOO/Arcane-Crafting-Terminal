package com.arcaneterminal.block;

import com.arcaneterminal.menu.ArcaneCraftingTerminalMenu;
import com.arcaneterminal.registry.ATRegistry;
import com.leclowndu93150.thaumaturge.api.aura.AuraHelper;
import com.leclowndu93150.thaumaturge.content.workbench.InventoryArcaneWorkbench;
import com.leclowndu93150.thaumaturge.registry.TTBlocks;
import com.tom.storagemod.block.AbstractStorageTerminalBlock;
import com.tom.storagemod.block.AbstractStorageTerminalBlock.TerminalPos;
import com.tom.storagemod.block.entity.InventoryConnectorBlockEntity;
import com.tom.storagemod.block.entity.StorageTerminalBlockEntity;
import com.tom.storagemod.inventory.InventoryCableNetwork;
import com.tom.storagemod.inventory.StoredItemStack;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Storage terminal holding an arcane workbench inventory: the 3x3 grid, the six primal crystal
 * slots and the wand slot, laid out exactly like Thaumaturge's {@link InventoryArcaneWorkbench}.
 * Aura is drawn the way a placed arcane workbench draws it. An Arcane Workbench Charger placed on
 * the network's Inventory Connector extends the reach to the 3x3 chunks around that connector.
 */
public class ArcaneCraftingTerminalBlockEntity extends StorageTerminalBlockEntity implements Clearable {
    private static final int CHARGER_RADIUS_CHUNKS = 1;
    private static final int MIN_ANCHOR_REQUEST = 1;
    private static final double DRAIN_EPSILON = 0.01;
    private static final String HOST_SEPARATOR = ":";
    private static final String HOST_PREFIX = "arcane_terminal:";
    private static final String GRID_KEY = "ArcaneGrid";
    private static final String MODE_KEY = "ArcaneMode";

    public int auraVis;
    private boolean arcaneMode = true;

    private final InventoryArcaneWorkbench inventory = new InventoryArcaneWorkbench();

    public ArcaneCraftingTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ATRegistry.ARCANE_CRAFTING_TERMINAL_BE.get(), pos, state);
        inventory.addChangedListener(this::setChanged);
    }

    public InventoryArcaneWorkbench getInventory() {
        return inventory;
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new ArcaneCraftingTerminalMenu(id, playerInventory, this);
    }

    @Override
    public @NonNull Component getDisplayName() {
        return Component.translatable("menu.arcane_terminal.arcane_crafting_terminal");
    }

    /** Whether the GUI shows the arcane workbench layout rather than Tom's Crafting Terminal layout. */
    public boolean isArcaneMode() {
        return arcaneMode;
    }

    public void toggleMode() {
        arcaneMode = !arcaneMode;
        setChanged();
    }

    /**
     * Finds the Inventory Connector this terminal reads from that has an Arcane Workbench Charger
     * on top: the block the terminal faces, or any connector on its cable network.
     *
     * @return the connector position, or null when there is none
     */
    public @Nullable BlockPos findChargedConnector(ServerLevel serverLevel) {
        BlockState state = getBlockState();
        Direction facing = state.getValue(AbstractStorageTerminalBlock.FACING);
        TerminalPos placement = state.getValue(AbstractStorageTerminalBlock.TERMINAL_POS);
        if (placement == TerminalPos.UP) {
            facing = Direction.UP;
        } else if (placement == TerminalPos.DOWN) {
            facing = Direction.DOWN;
        }
        BlockPos front = worldPosition.relative(facing);
        if (isChargedConnector(serverLevel, front)) {
            return front;
        }
        for (BlockPos node : InventoryCableNetwork.getNetwork(serverLevel).getNetworkNodes(worldPosition)) {
            if (isChargedConnector(serverLevel, node)) {
                return node;
            }
        }
        return null;
    }

    private static boolean isChargedConnector(ServerLevel serverLevel, BlockPos pos) {
        return serverLevel.isLoaded(pos) && serverLevel.getBlockEntity(pos) instanceof InventoryConnectorBlockEntity && serverLevel.getBlockState(pos.above()).is(TTBlocks.ARCANE_WORKBENCH_CHARGER);
    }

    public void refreshAura() {
        if (level instanceof ServerLevel serverLevel) {
            auraVis = anchors(serverLevel).stream().mapToInt(anchor -> (int) AuraHelper.getVis(serverLevel, anchor)).sum();
        }
    }

    /**
     * Drains aura vis for a craft inside the given transaction, spreading the request over the
     * anchors like the arcane workbench does.
     *
     * @return true when the full amount was drained
     */
    public boolean spendAura(int vis, TransactionContext transaction) {
        if (vis <= 0) {
            return true;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        List<BlockPos> anchors = anchors(serverLevel);
        if (anchors.isEmpty()) {
            return false;
        }
        int perAnchor = Math.max(MIN_ANCHOR_REQUEST, vis / anchors.size());
        double owed = vis;
        while (owed > DRAIN_EPSILON) {
            double progress = 0.0;
            for (BlockPos anchor : anchors) {
                if (owed <= DRAIN_EPSILON) {
                    break;
                }
                float request = (float) Math.min(owed, perAnchor);
                float drained = AuraHelper.drainVis(serverLevel, anchor, request, transaction);
                owed -= drained;
                progress += drained;
            }
            if (progress < DRAIN_EPSILON && owed > DRAIN_EPSILON) {
                return false;
            }
        }
        return true;
    }

    public UUID hostIdentity() {
        Level current = level;
        if (current == null) {
            throw new IllegalStateException("Terminal host identity requires a level");
        }
        String key = HOST_PREFIX + current.dimension().identifier() + HOST_SEPARATOR + worldPosition.asLong();
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private List<BlockPos> anchors(ServerLevel serverLevel) {
        List<BlockPos> anchors = new ArrayList<>();
        BlockPos connector = findChargedConnector(serverLevel);
        if (connector != null) {
            int y = connector.getY();
            ChunkPos centre = ChunkPos.containing(connector);
            for (int chunkX = centre.x() - CHARGER_RADIUS_CHUNKS; chunkX <= centre.x() + CHARGER_RADIUS_CHUNKS; chunkX++) {
                for (int chunkZ = centre.z() - CHARGER_RADIUS_CHUNKS; chunkZ <= centre.z() + CHARGER_RADIUS_CHUNKS; chunkZ++) {
                    anchors.add(new ChunkPos(chunkX, chunkZ).getMiddleBlockPosition(y));
                }
            }
        } else {
            anchors.add(worldPosition);
        }
        //noinspection deprecation
        anchors.removeIf(anchor -> !serverLevel.hasChunkAt(anchor));
        return anchors;
    }

    /**
     * Refills grid cells that a craft emptied, pulling one matching item per cell from the storage
     * network, the way Tom's Crafting Terminal keeps its grid stocked.
     *
     * @param before the grid contents before the craft
     */
    public void refillGrid(List<ItemStack> before) {
        boolean changed = false;
        for (int slot = 0; slot < InventoryArcaneWorkbench.CRAFTING_SLOTS; slot++) {
            ItemStack previous = before.get(slot);
            if (previous.isEmpty() || !inventory.getItem(slot).isEmpty()) {
                continue;
            }
            StoredItemStack pulled = pullStack(new StoredItemStack(previous), 1);
            if (pulled != null) {
                inventory.getItems().set(slot, pulled.getActualStack());
                changed = true;
            }
        }
        if (changed) {
            inventory.setChanged();
        }
    }

    public List<ItemStack> gridSnapshot() {
        List<ItemStack> grid = new ArrayList<>(InventoryArcaneWorkbench.CRAFTING_SLOTS);
        for (int slot = 0; slot < InventoryArcaneWorkbench.CRAFTING_SLOTS; slot++) {
            grid.add(inventory.getItem(slot).copy());
        }
        return grid;
    }

    /**
     * Moves the crafting grid back into the storage network, then into the player's inventory, and
     * drops whatever fits nowhere. Crystals and the wand stay in place.
     */
    public void clearGrid(Player player) {
        for (int slot = 0; slot < InventoryArcaneWorkbench.CRAFTING_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            inventory.getItems().set(slot, ItemStack.EMPTY);
            ItemStack rest = pushStack(stack);
            if (!rest.isEmpty()) {
                player.getInventory().add(rest);
            }
            if (!rest.isEmpty()) {
                dropItem(rest);
            }
        }
        inventory.setChanged();
    }

    @Override
    public void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output.child(GRID_KEY), inventory.getItems());
        output.putBoolean(MODE_KEY, arcaneMode);
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        Collections.fill(inventory.getItems(), ItemStack.EMPTY);
        input.child(GRID_KEY).ifPresent(grid -> ContainerHelper.loadAllItems(grid, inventory.getItems()));
        arcaneMode = input.getBooleanOr(MODE_KEY, true);
    }

    @Override
    public void preRemoveSideEffects(@NonNull BlockPos pos, @NonNull BlockState state) {
        if (level != null && !level.isClientSide()) {
            Containers.dropContents(level, pos, inventory);
        }
    }

    @Override
    public void clearContent() {
        inventory.clearContent();
    }
}
