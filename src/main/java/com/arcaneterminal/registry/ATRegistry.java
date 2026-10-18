package com.arcaneterminal.registry;

import com.arcaneterminal.ArcaneTerminal;
import com.arcaneterminal.block.ArcaneCraftingTerminalBlock;
import com.arcaneterminal.block.ArcaneCraftingTerminalBlockEntity;
import com.arcaneterminal.menu.ArcaneCraftingTerminalMenu;
import com.tom.storagemod.item.BlockItemTSS;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ATRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ArcaneTerminal.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ArcaneTerminal.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ArcaneTerminal.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ArcaneTerminal.MODID);

    public static final DeferredBlock<ArcaneCraftingTerminalBlock> ARCANE_CRAFTING_TERMINAL = BLOCKS.registerBlock(
            "arcane_crafting_terminal", ArcaneCraftingTerminalBlock::new, props -> props
                    .mapColor(MapColor.WOOD)
                    .sound(SoundType.WOOD)
                    .strength(3.0F)
                    .lightLevel(_ -> 8));

    public static final DeferredItem<BlockItemTSS> ARCANE_CRAFTING_TERMINAL_ITEM = ITEMS.registerItem(
            "arcane_crafting_terminal", props -> new BlockItemTSS(ARCANE_CRAFTING_TERMINAL.get(), props),
            Item.Properties::useBlockDescriptionPrefix);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArcaneCraftingTerminalBlockEntity>> ARCANE_CRAFTING_TERMINAL_BE = BLOCK_ENTITIES.register(
            "arcane_crafting_terminal",
            () -> new BlockEntityType<>(ArcaneCraftingTerminalBlockEntity::new, Set.of(ARCANE_CRAFTING_TERMINAL.get())));

    public static final DeferredHolder<MenuType<?>, MenuType<ArcaneCraftingTerminalMenu>> ARCANE_CRAFTING_TERMINAL_MENU = MENUS.register(
            "arcane_crafting_terminal",
            () -> new MenuType<>(ArcaneCraftingTerminalMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private ATRegistry() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
    }
}
