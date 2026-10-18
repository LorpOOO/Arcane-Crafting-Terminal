package com.arcaneterminal.block;

import com.mojang.serialization.MapCodec;
import com.tom.storagemod.block.AbstractStorageTerminalBlock;
import java.util.function.Consumer;
import com.arcaneterminal.TomsTooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;

/**
 * A Tom's Simple Storage terminal with a built-in arcane workbench. It reads and writes the storage
 * network it faces like the Crafting Terminal. Its aura comes from around the terminal, or from the
 * 3x3 chunks around the network's Inventory Connector when an Arcane Workbench Charger sits on it.
 */
public class ArcaneCraftingTerminalBlock extends AbstractStorageTerminalBlock {
    public static final MapCodec<ArcaneCraftingTerminalBlock> CODEC = simpleCodec(ArcaneCraftingTerminalBlock::new);
    private static final String TOOLTIP_KEY = "tooltip.arcane_terminal.arcane_crafting_terminal";

    public ArcaneCraftingTerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new ArcaneCraftingTerminalBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, Consumer<Component> tooltip, TooltipFlag flag) {
        TomsTooltip.add(TOOLTIP_KEY, true, tooltip);
    }

    @Override
    protected void affectNeighborsAfterRemoval(
            @NonNull BlockState state,
            @NonNull ServerLevel level,
            @NonNull BlockPos pos,
            boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
}
