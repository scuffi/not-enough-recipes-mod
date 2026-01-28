package dev.scuffi.registry;

import dev.scuffi.NotEnoughRecipes;
import dev.scuffi.block.CreationAltarBlock;
import dev.scuffi.block.entity.CreationAltarBlockEntity;
import dev.scuffi.block.menu.CreationAltarMenu;
import dev.scuffi.block.screen.CreationAltarScreen;
import dev.scuffi.command.CreationAltarCommand;
import dev.scuffi.network.packet.CreationAltarProcessPacket;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Registers all Creation Altar components.
 */
public class CreationAltarRegistry {
    
    public static Block CREATION_ALTAR_BLOCK;
    public static BlockEntityType<CreationAltarBlockEntity> CREATION_ALTAR_BLOCK_ENTITY_TYPE;
    public static MenuType<CreationAltarMenu> CREATION_ALTAR_MENU_TYPE;
    
    public static void registerServer() {
        // Create the ResourceKey for the block FIRST
        Identifier blockId = Identifier.parse(NotEnoughRecipes.MOD_ID + ":creation_altar");
        ResourceKey<Block> blockKey = ResourceKey.create(BuiltInRegistries.BLOCK.key(), blockId);
        
        // Create block properties and set the ID BEFORE creating the block
        var blockProperties = BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE)
            .strength(5.0f, 1200.0f)
            .requiresCorrectToolForDrops()
            .lightLevel(state -> 7)
            .setId(blockKey);
        
        // Create the block with properties that have the ID set
        CREATION_ALTAR_BLOCK = new CreationAltarBlock(blockProperties);
        
        // Register the block
        Registry.register(BuiltInRegistries.BLOCK, blockKey, CREATION_ALTAR_BLOCK);
        
        CREATION_ALTAR_BLOCK_ENTITY_TYPE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            blockId,
            FabricBlockEntityTypeBuilder.create(CreationAltarBlockEntity::new, CREATION_ALTAR_BLOCK).build()
        );
        
        CreationAltarBlockEntity.TYPE = CREATION_ALTAR_BLOCK_ENTITY_TYPE;
        
        CREATION_ALTAR_MENU_TYPE = Registry.register(
            BuiltInRegistries.MENU,
            blockId,
            new ExtendedScreenHandlerType<>(CreationAltarMenu::new, BlockPos.STREAM_CODEC)
        );
        
        CreationAltarMenu.TYPE = CREATION_ALTAR_MENU_TYPE;
        
        // Create the ResourceKey for the block item
        ResourceKey<Item> itemKey = ResourceKey.create(BuiltInRegistries.ITEM.key(), blockId);
        var itemProperties = new Item.Properties().setId(itemKey);
        
        Registry.register(
            BuiltInRegistries.ITEM,
            itemKey,
            new BlockItem(CREATION_ALTAR_BLOCK, itemProperties)
        );
        
        CreationAltarProcessPacket.register();
        
        NotEnoughRecipes.LOGGER.info("Creation Altar server components registered");
    }
    
    public static void registerClient() {
        MenuScreens.register(CREATION_ALTAR_MENU_TYPE, CreationAltarScreen::new);
        // TODO: Register BlockEntityRenderer when API compatibility is resolved
        // BlockEntityRenderers.register(CREATION_ALTAR_BLOCK_ENTITY_TYPE, CreationAltarRenderer::new);
        NotEnoughRecipes.LOGGER.info("Creation Altar client components registered (renderer disabled for now)");
    }
    
    public static void registerCommands(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        CreationAltarCommand.register(dispatcher);
    }
}
