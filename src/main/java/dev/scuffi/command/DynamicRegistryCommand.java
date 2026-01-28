package dev.scuffi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.scuffi.NotEnoughRecipes;
import dev.scuffi.registry.DynamicRegistryHelper;
import dev.scuffi.registry.DynamicRegistryPersistence;
import dev.scuffi.resource.DynamicResourceLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Command to test dynamic registry and resource manipulation.
 * Usage:
 *   /ner block - Registers a new dynamic block and gives it to the player
 *   /ner item - Registers a new dynamic item and gives it to the player
 *   /ner stats - Shows registry statistics
 *   /ner test - Full test: registers both a block and item
 *   /ner resource load - Load all resources from dynamic folder
 *   /ner resource list - List available resource files
 *   /ner resource stats - Show resource statistics
 *   /ner resource reload - Trigger a full resource reload
 *   /ner resource texture <type> <name> - Load a specific texture
 */
public class DynamicRegistryCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ner")
                .then(Commands.literal("block")
                        .executes(DynamicRegistryCommand::registerBlock)
                        // /ner block <texture_name> - creates block with custom texture
                        .then(Commands.argument("texture", StringArgumentType.word())
                                .executes(DynamicRegistryCommand::registerBlockWithTexture)
                                // /ner block <texture> <components> - with data components
                                .then(Commands.argument("components", StringArgumentType.greedyString())
                                        .executes(DynamicRegistryCommand::registerBlockWithComponents))))
                .then(Commands.literal("item")
                        .executes(DynamicRegistryCommand::registerItem)
                        // /ner item <texture_name> - creates item with custom texture
                        .then(Commands.argument("texture", StringArgumentType.word())
                                .executes(DynamicRegistryCommand::registerItemWithTexture)
                                // /ner item <texture> <components> - with data components
                                .then(Commands.argument("components", StringArgumentType.greedyString())
                                        .executes(DynamicRegistryCommand::registerItemWithComponents))))
                .then(Commands.literal("stats")
                        .executes(DynamicRegistryCommand::showStats))
                .then(Commands.literal("test")
                        .executes(DynamicRegistryCommand::runFullTest))
                // Resource subcommands
                .then(Commands.literal("resource")
                        .then(Commands.literal("load")
                                .executes(DynamicRegistryCommand::loadAllResources))
                        .then(Commands.literal("list")
                                .executes(DynamicRegistryCommand::listResources))
                        .then(Commands.literal("stats")
                                .executes(DynamicRegistryCommand::showResourceStats))
                        .then(Commands.literal("reload")
                                .executes(DynamicRegistryCommand::reloadResources))
                        .then(Commands.literal("debug")
                                .executes(DynamicRegistryCommand::debugResourcePack))
                        .then(Commands.literal("texture")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(DynamicRegistryCommand::loadTexture))))
                        .then(Commands.literal("path")
                                .executes(DynamicRegistryCommand::showResourcePath)))
                // Persistence subcommands
                .then(Commands.literal("persist")
                        .then(Commands.literal("list")
                                .executes(DynamicRegistryCommand::listPersisted))
                        .then(Commands.literal("stats")
                                .executes(DynamicRegistryCommand::showPersistedStats))
                        .then(Commands.literal("clear")
                                .executes(DynamicRegistryCommand::clearPersisted))
                        .then(Commands.literal("reload")
                                .executes(DynamicRegistryCommand::reloadPersisted))
                        .then(Commands.literal("path")
                                .executes(DynamicRegistryCommand::showPersistencePath)))
                // Give command - gives a registered dynamic item with its components
                .then(Commands.literal("give")
                        .then(Commands.argument("itemId", StringArgumentType.word())
                                .executes(context -> giveItem(context, 1))
                                .then(Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 64))
                                        .executes(context -> giveItem(context, 
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "count"))))))
        );
        
        NotEnoughRecipes.LOGGER.info("Registered /ner command");
    }
    
    // ==================== Give Command ====================
    
    /**
     * Give a registered dynamic item to the player with its stored components applied.
     * Usage: /ner give <itemId> [count]
     */
    private static int giveItem(CommandContext<CommandSourceStack> context, int count) {
        CommandSourceStack source = context.getSource();
        String itemId = StringArgumentType.getString(context, "itemId");
        
        try {
            if (!source.isPlayer()) {
                source.sendFailure(Component.literal("This command can only be used by players"));
                return 0;
            }
            
            // Try to find the item in the registry
            var fullId = net.minecraft.resources.Identifier.parse(NotEnoughRecipes.MOD_ID + ":" + itemId);
            Item item = BuiltInRegistries.ITEM.getValue(fullId);
            
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                source.sendFailure(Component.literal("Item not found: " + itemId));
                source.sendSuccess(() -> Component.literal("Use /ner persist list to see registered items"), false);
                return 0;
            }
            
            // Create stack with components applied
            ItemStack stack = DynamicRegistryHelper.createItemStack(item, count);
            
            // Check if components were applied
            boolean hasComponents = DynamicRegistryHelper.hasItemComponents(itemId) 
                    || DynamicRegistryHelper.hasBlockComponents(itemId);
            
            // Give to player
            source.getPlayer().getInventory().add(stack);
            
            String componentInfo = hasComponents ? " with components" : "";
            source.sendSuccess(() -> Component.literal("Gave you " + count + "x " + fullId + componentInfo), false);
            
            return 1;
        } catch (Exception e) {
            NotEnoughRecipes.LOGGER.error("Failed to give item", e);
            source.sendFailure(Component.literal("Failed: " + e.getMessage()));
            return 0;
        }
    }
    
    // ==================== Block Registration ====================
    
    private static int registerBlock(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        try {
            source.sendSuccess(() -> Component.literal("Attempting to register dynamic block..."), false);
            
            Block block = DynamicRegistryHelper.registerDynamicBlock();
            
            // Get the block's registry name
            var blockId = BuiltInRegistries.BLOCK.getKey(block);
            source.sendSuccess(() -> Component.literal("Successfully registered block: " + blockId), true);
            
            // Try to give the player the block item
            if (source.isPlayer()) {
                Item blockItem = BuiltInRegistries.ITEM.getValue(blockId);
                if (blockItem != null) {
                    ItemStack stack = new ItemStack(blockItem, 64);
                    source.getPlayer().getInventory().add(stack);
                    source.sendSuccess(() -> Component.literal("Gave you 64x " + blockId), false);
                }
            }
            
            return 1;
        } catch (Exception e) {
            NotEnoughRecipes.LOGGER.error("Failed to register dynamic block", e);
            source.sendFailure(Component.literal("Failed to register block: " + e.getMessage()));
            return 0;
        }
    }
    
    private static int registerItem(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        try {
            source.sendSuccess(() -> Component.literal("Attempting to register dynamic item..."), false);
            
            Item item = DynamicRegistryHelper.registerDynamicItem();
            
            // Get the item's registry name
            var itemId = BuiltInRegistries.ITEM.getKey(item);
            source.sendSuccess(() -> Component.literal("Successfully registered item: " + itemId), true);
            
            // Try to give the player the item
            if (source.isPlayer()) {
                ItemStack stack = new ItemStack(item, 64);
                source.getPlayer().getInventory().add(stack);
                source.sendSuccess(() -> Component.literal("Gave you 64x " + itemId), false);
            }
            
            return 1;
        } catch (Exception e) {
            NotEnoughRecipes.LOGGER.error("Failed to register dynamic item", e);
            source.sendFailure(Component.literal("Failed to register item: " + e.getMessage()));
            return 0;
        }
    }
    
    private static int registerItemWithTexture(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String textureName = StringArgumentType.getString(context, "texture");
        
        try {
            // Check if the texture file exists
            boolean textureExists = DynamicResourceLoader.textureFileExists("item", textureName);
            
            if (textureExists) {
                source.sendSuccess(() -> Component.literal("Found texture: " + textureName + ".png"), false);
            } else {
                source.sendSuccess(() -> Component.literal("Warning: Texture '" + textureName + ".png' not found in textures/item/"), false);
                source.sendSuccess(() -> Component.literal("Item will have missing texture. Path: " + 
                        DynamicResourceLoader.getDynamicResourcePath() + "/textures/item/"), false);
            }
            
            source.sendSuccess(() -> Component.literal("Registering item with texture: " + textureName), false);
            
            // Register the item with the texture name as the item name
            Item item = DynamicRegistryHelper.registerDynamicItemWithTexture(textureName, textureName);
            
            // Get the item's registry name
            var itemId = BuiltInRegistries.ITEM.getKey(item);
            source.sendSuccess(() -> Component.literal("Successfully registered item: " + itemId), true);
            
            // Try to give the player the item (using createItemStack to apply any components)
            if (source.isPlayer()) {
                ItemStack stack = DynamicRegistryHelper.createItemStack(item, 64);
                source.getPlayer().getInventory().add(stack);
                source.sendSuccess(() -> Component.literal("Gave you 64x " + itemId), false);
            }
            
            // Inform user about resource reload
            if (textureExists) {
                source.sendSuccess(() -> Component.literal(""), false);
                source.sendSuccess(() -> Component.literal("Texture loaded! Press F3+T to reload resources and see the texture."), false);
                source.sendSuccess(() -> Component.literal("Or run: /ner resource reload"), false);
            }
            
            return 1;
        } catch (Exception e) {
            NotEnoughRecipes.LOGGER.error("Failed to register dynamic item with texture", e);
            source.sendFailure(Component.literal("Failed to register item: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * Register an item with texture and data components.
     * Uses /give command format for components.
     * Example: /ner item diamond_sword [custom_name="Magic Sword",max_stack_size=16,unbreakable={}]
     */
    private static int registerItemWithComponents(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String textureName = StringArgumentType.getString(context, "texture");
        String componentString = StringArgumentType.getString(context, "components");
        
        try {
            source.sendSuccess(() -> Component.literal("Components: " + componentString), false);
            
            // Check if texture exists
            boolean textureExists = DynamicResourceLoader.textureFileExists("item", textureName);
            if (textureExists) {
                source.sendSuccess(() -> Component.literal("Found texture: " + textureName + ".png"), false);
            }
            
            // Convert component string to JSON object and register item
            // For now, store the component string as a JSON property that will be parsed later
            com.google.gson.JsonObject components = new com.google.gson.JsonObject();
            components.addProperty("_raw_snbt", componentString);
            
            DynamicRegistryPersistence.ItemDefinition definition = new DynamicRegistryPersistence.ItemDefinition(textureName, textureName, components);
            Item item = DynamicRegistryHelper.registerDynamicItemFromDefinition(definition);
            
            var itemId = BuiltInRegistries.ITEM.getKey(item);
            source.sendSuccess(() -> Component.literal("Successfully registered item: " + itemId), true);
            
            // Give player the item with components applied
            if (source.isPlayer()) {
                ItemStack stack = DynamicRegistryHelper.createItemStack(item, 64);
                source.getPlayer().getInventory().add(stack);
                source.sendSuccess(() -> Component.literal("Gave you 64x " + itemId + " with components"), false);
            }
            
            if (textureExists) {
                source.sendSuccess(() -> Component.literal("Press F3+T to reload resources."), false);
            }
            
            return 1;
        } catch (Exception e) {
            NotEnoughRecipes.LOGGER.error("Failed to register item with components", e);
            source.sendFailure(Component.literal("Failed: " + e.getMessage()));
            return 0;
        }
    }
    
    private static int registerBlockWithTexture(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String textureName = StringArgumentType.getString(context, "texture");
        
        try {
            // Check if the texture file exists
            boolean textureExists = DynamicResourceLoader.textureFileExists("block", textureName);
            
            if (textureExists) {
                source.sendSuccess(() -> Component.literal("Found texture: " + textureName + ".png"), false);
            } else {
                source.sendSuccess(() -> Component.literal("Warning: Texture '" + textureName + ".png' not found in textures/block/"), false);
                source.sendSuccess(() -> Component.literal("Block will have missing texture. Path: " + 
                        DynamicResourceLoader.getDynamicResourcePath() + "/textures/block/"), false);
            }
            
            source.sendSuccess(() -> Component.literal("Registering block with texture: " + textureName), false);
            
            // Register the block with texture - this handles loading texture, creating models, etc.
            Block block = DynamicRegistryHelper.registerDynamicBlockWithTexture(textureName, textureName);
            
            // Get the block's registry name
            var blockId = BuiltInRegistries.BLOCK.getKey(block);
            source.sendSuccess(() -> Component.literal("Successfully registered block: " + blockId), true);
            
            // Try to give the player the block item (using createItemStack for components)
            if (source.isPlayer()) {
                Item blockItem = BuiltInRegistries.ITEM.getValue(blockId);
                if (blockItem != null) {
                    ItemStack stack = DynamicRegistryHelper.createItemStack(blockItem, 64);
                    source.getPlayer().getInventory().add(stack);
                    source.sendSuccess(() -> Component.literal("Gave you 64x " + blockId), false);
                }
            }
            
            // Inform user about resource reload
            if (textureExists) {
                source.sendSuccess(() -> Component.literal(""), false);
                source.sendSuccess(() -> Component.literal("Texture loaded! Press F3+T to reload resources and see the texture."), false);
                source.sendSuccess(() -> Component.literal("Or run: /ner resource reload"), false);
            }
            
            return 1;
        } catch (Exception e) {
            NotEnoughRecipes.LOGGER.error("Failed to register dynamic block with texture", e);
            source.sendFailure(Component.literal("Failed to register block: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * Register a block with texture and data components.
     * Uses /give command format for components.
     * Example: /ner block stone [custom_name="Magic Stone",enchantment_glint_override=true]
     */
    private static int registerBlockWithComponents(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String textureName = StringArgumentType.getString(context, "texture");
        String componentString = StringArgumentType.getString(context, "components");
        
        try {
            source.sendSuccess(() -> Component.literal("Components: " + componentString), false);
            
            // Check texture
            boolean textureExists = DynamicResourceLoader.textureFileExists("block", textureName);
            if (textureExists) {
                source.sendSuccess(() -> Component.literal("Found texture: " + textureName + ".png"), false);
            }
            
            // Convert component string to JSON object and register block
            com.google.gson.JsonObject components = new com.google.gson.JsonObject();
            components.addProperty("_raw_snbt", componentString);
            
            DynamicRegistryPersistence.BlockDefinition definition = new DynamicRegistryPersistence.BlockDefinition(textureName, textureName, new DynamicRegistryPersistence.BlockProperties(), components);
            Block block = DynamicRegistryHelper.registerDynamicBlockFromDefinition(definition);
            
            var blockId = BuiltInRegistries.BLOCK.getKey(block);
            source.sendSuccess(() -> Component.literal("Successfully registered block: " + blockId), true);
            
            // Give player the block item with components
            if (source.isPlayer()) {
                Item blockItem = BuiltInRegistries.ITEM.getValue(blockId);
                if (blockItem != null) {
                    ItemStack stack = DynamicRegistryHelper.createItemStack(blockItem, 64);
                    source.getPlayer().getInventory().add(stack);
                    source.sendSuccess(() -> Component.literal("Gave you 64x " + blockId + " with components"), false);
                }
            }
            
            if (textureExists) {
                source.sendSuccess(() -> Component.literal("Press F3+T to reload resources."), false);
            }
            
            return 1;
        } catch (Exception e) {
            NotEnoughRecipes.LOGGER.error("Failed to register block with components", e);
            source.sendFailure(Component.literal("Failed: " + e.getMessage()));
            return 0;
        }
    }
    
    private static int showStats(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        String stats = DynamicRegistryHelper.getRegistryStats();
        for (String line : stats.split("\n")) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        
        return 1;
    }
    
    private static int runFullTest(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        source.sendSuccess(() -> Component.literal("========== Dynamic Registry POC Test =========="), false);
        source.sendSuccess(() -> Component.literal("This test will:"), false);
        source.sendSuccess(() -> Component.literal("  1. Unfreeze the block & item registries"), false);
        source.sendSuccess(() -> Component.literal("  2. Register a new block (with block item)"), false);
        source.sendSuccess(() -> Component.literal("  3. Register a new standalone item"), false);
        source.sendSuccess(() -> Component.literal("  4. Refreeze the registries"), false);
        source.sendSuccess(() -> Component.literal("=============================================="), false);
        
        // Show initial stats
        source.sendSuccess(() -> Component.literal("Initial registry state:"), false);
        showStats(context);
        
        // Register block
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("Step 1: Registering dynamic block..."), false);
        int blockResult = registerBlock(context);
        
        // Register item
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("Step 2: Registering dynamic item..."), false);
        int itemResult = registerItem(context);
        
        // Show final stats
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("Final registry state:"), false);
        showStats(context);
        
        // Summary
        source.sendSuccess(() -> Component.literal(""), false);
        if (blockResult == 1 && itemResult == 1) {
            source.sendSuccess(() -> Component.literal("Test completed successfully!"), true);
            source.sendSuccess(() -> Component.literal("Proof of concept: Registries CAN be modified at runtime."), false);
        } else {
            source.sendFailure(Component.literal("Test completed with errors."));
        }
        
        return (blockResult == 1 && itemResult == 1) ? 1 : 0;
    }
    
    // ==================== Resource Commands ====================
    
    private static int loadAllResources(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        source.sendSuccess(() -> Component.literal("Loading all dynamic resources..."), false);
        
        try {
            int count = DynamicResourceLoader.loadAllResources();
            
            if (count > 0) {
                // Rebuild the resource pack
                DynamicResourceLoader.getResourcePack().rebuild();
                
                source.sendSuccess(() -> Component.literal("Loaded " + count + " resources successfully!"), true);
                source.sendSuccess(() -> Component.literal("Use '/ner resource reload' to apply textures."), false);
            } else {
                source.sendSuccess(() -> Component.literal("No resources found in dynamic folder."), false);
                source.sendSuccess(() -> Component.literal("Add files to: " + DynamicResourceLoader.getDynamicResourcePath()), false);
            }
            
            return count > 0 ? 1 : 0;
        } catch (Exception e) {
            NotEnoughRecipes.LOGGER.error("Failed to load resources", e);
            source.sendFailure(Component.literal("Failed to load resources: " + e.getMessage()));
            return 0;
        }
    }
    
    private static int listResources(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        List<String> available = DynamicResourceLoader.listAvailableResources();
        
        if (available.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No resource files found in dynamic folder."), false);
            source.sendSuccess(() -> Component.literal("Path: " + DynamicResourceLoader.getDynamicResourcePath()), false);
        } else {
            source.sendSuccess(() -> Component.literal("Available resource files (" + available.size() + "):"), false);
            for (String resource : available) {
                source.sendSuccess(() -> Component.literal("  - " + resource), false);
            }
        }
        
        return 1;
    }
    
    private static int showResourceStats(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        String stats = DynamicResourceLoader.getStats();
        for (String line : stats.split("\n")) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        
        return 1;
    }
    
    private static int reloadResources(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        source.sendSuccess(() -> Component.literal("Triggering resource reload..."), false);
        source.sendSuccess(() -> Component.literal("This will reload all resource packs including dynamic resources."), false);
        
        try {
            // Rebuild the pack first
            DynamicResourceLoader.getResourcePack().rebuild();
            
            // Trigger the reload
            DynamicResourceLoader.triggerResourceReload();
            
            source.sendSuccess(() -> Component.literal("Resource reload initiated!"), true);
            return 1;
        } catch (Exception e) {
            NotEnoughRecipes.LOGGER.error("Failed to reload resources", e);
            source.sendFailure(Component.literal("Failed to reload: " + e.getMessage()));
            return 0;
        }
    }
    
    private static int loadTexture(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        String type = StringArgumentType.getString(context, "type");
        String name = StringArgumentType.getString(context, "name");
        
        // Validate type
        if (!type.equals("item") && !type.equals("block")) {
            source.sendFailure(Component.literal("Invalid type. Use 'item' or 'block'."));
            return 0;
        }
        
        source.sendSuccess(() -> Component.literal("Loading texture: " + type + "/" + name + ".png"), false);
        
        boolean success = DynamicResourceLoader.loadTexture(type, name);
        
        if (success) {
            // Also generate a default model for it
            DynamicResourceLoader.loadModel(type, name);
            
            // Rebuild the pack
            DynamicResourceLoader.getResourcePack().rebuild();
            
            source.sendSuccess(() -> Component.literal("Texture loaded successfully!"), true);
            source.sendSuccess(() -> Component.literal("Use '/ner resource reload' to apply."), false);
            return 1;
        } else {
            source.sendFailure(Component.literal("Failed to load texture. Check if file exists."));
            source.sendFailure(Component.literal("Expected path: " + DynamicResourceLoader.getDynamicResourcePath() + 
                    "/textures/" + type + "/" + name + ".png"));
            return 0;
        }
    }
    
    private static int showResourcePath(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        var path = DynamicResourceLoader.getDynamicResourcePath();
        source.sendSuccess(() -> Component.literal("Dynamic resource folder:"), false);
        source.sendSuccess(() -> Component.literal(path.toString()), false);
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("Folder structure:"), false);
        source.sendSuccess(() -> Component.literal("  textures/item/   - Item textures (.png)"), false);
        source.sendSuccess(() -> Component.literal("  textures/block/  - Block textures (.png)"), false);
        source.sendSuccess(() -> Component.literal("  models/item/     - Item models (.json)"), false);
        source.sendSuccess(() -> Component.literal("  models/block/    - Block models (.json)"), false);
        
        return 1;
    }
    
    private static int debugResourcePack(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        source.sendSuccess(() -> Component.literal("=== Dynamic Resource Pack Debug ==="), false);
        
        var pack = DynamicResourceLoader.getResourcePack();
        if (pack == null) {
            source.sendFailure(Component.literal("Resource pack is NULL!"));
            return 0;
        }
        
        source.sendSuccess(() -> Component.literal("Pack ID: " + pack.packId()), false);
        source.sendSuccess(() -> Component.literal("Has resources: " + pack.hasResources()), false);
        source.sendSuccess(() -> Component.literal("Resource count: " + pack.getResourceCount()), false);
        
        // List all resources in the pack
        var resources = pack.getResourcePaths();
        if (resources.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No resources in pack."), false);
        } else {
            source.sendSuccess(() -> Component.literal("Resources in pack:"), false);
            for (String res : resources) {
                source.sendSuccess(() -> Component.literal("  - " + res), false);
            }
        }
        
        // Check namespaces
        var namespaces = pack.getNamespaces(net.minecraft.server.packs.PackType.CLIENT_RESOURCES);
        source.sendSuccess(() -> Component.literal("Namespaces: " + namespaces), false);
        
        return 1;
    }
    
    // ==================== Persistence Commands ====================
    
    private static int listPersisted(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        source.sendSuccess(() -> Component.literal("=== Persisted Dynamic Entries ==="), false);
        
        // List items
        var items = DynamicRegistryPersistence.loadItemDefinitions();
        if (items.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Items: (none)"), false);
        } else {
            source.sendSuccess(() -> Component.literal("Items (" + items.size() + "):"), false);
            for (var item : items) {
                source.sendSuccess(() -> Component.literal("  - " + item.id + " (texture: " + item.texture + ")"), false);
                
                // Show components summary
                if (item.components != null && !item.components.isEmpty()) {
                    StringBuilder comps = new StringBuilder();
                    for (String key : item.components.keySet()) {
                        comps.append(key).append(", ");
                    }
                    if (comps.length() > 0) {
                        String compsStr = comps.substring(0, comps.length() - 2); // Remove trailing ", "
                        source.sendSuccess(() -> Component.literal("      components: [" + compsStr + "]"), false);
                    }
                }
                
                // Show tags if present
                if (item.tags != null && !item.tags.isEmpty()) {
                    source.sendSuccess(() -> Component.literal("      tags: " + item.tags), false);
                }
            }
        }
        
        // List blocks
        var blocks = DynamicRegistryPersistence.loadBlockDefinitions();
        if (blocks.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Blocks: (none)"), false);
        } else {
            source.sendSuccess(() -> Component.literal("Blocks (" + blocks.size() + "):"), false);
            for (var block : blocks) {
                source.sendSuccess(() -> Component.literal("  - " + block.id + " (texture: " + block.texture + ")"), false);
                
                // Show block properties summary
                if (block.properties != null) {
                    StringBuilder props = new StringBuilder();
                    if (block.properties.hardness != null) props.append("hardness=" + block.properties.hardness + ", ");
                    if (block.properties.resistance != null) props.append("resistance=" + block.properties.resistance + ", ");
                    if (block.properties.lightLevel != null) props.append("light=" + block.properties.lightLevel + ", ");
                    if (block.properties.soundType != null) props.append("sound=" + block.properties.soundType + ", ");
                    
                    if (props.length() > 0) {
                        String propsStr = props.substring(0, props.length() - 2); // Remove trailing ", "
                        source.sendSuccess(() -> Component.literal("      block: [" + propsStr + "]"), false);
                    }
                }
                
                // Show components summary
                if (block.components != null && !block.components.isEmpty()) {
                    StringBuilder comps = new StringBuilder();
                    for (String key : block.components.keySet()) {
                        comps.append(key).append(", ");
                    }
                    if (comps.length() > 0) {
                        String compsStr = comps.substring(0, comps.length() - 2); // Remove trailing ", "
                        source.sendSuccess(() -> Component.literal("      components: [" + compsStr + "]"), false);
                    }
                }
                
                // Show tags if present
                if (block.tags != null && !block.tags.isEmpty()) {
                    source.sendSuccess(() -> Component.literal("      tags: " + block.tags), false);
                }
            }
        }
        
        return 1;
    }
    
    private static int showPersistedStats(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        String stats = DynamicRegistryPersistence.getStats();
        for (String line : stats.split("\n")) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        
        return 1;
    }
    
    private static int clearPersisted(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        source.sendSuccess(() -> Component.literal("Clearing all persisted definitions..."), false);
        DynamicRegistryPersistence.clearAll();
        source.sendSuccess(() -> Component.literal("Cleared! Note: Already registered items/blocks will remain until restart."), true);
        source.sendSuccess(() -> Component.literal("They will not be re-registered on next startup."), false);
        
        return 1;
    }
    
    private static int reloadPersisted(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        source.sendSuccess(() -> Component.literal("Reloading persisted items and blocks from JSON files..."), false);
        
        try {
            // Load all definitions from JSON
            var items = DynamicRegistryPersistence.loadItemDefinitions();
            var blocks = DynamicRegistryPersistence.loadBlockDefinitions();
            
            int itemsRegistered = 0;
            int itemsUpdated = 0;
            int blocksRegistered = 0;
            int blocksUpdated = 0;
            
            // Process items
            source.sendSuccess(() -> Component.literal("Processing " + items.size() + " items..."), false);
            for (var itemDef : items) {
                var itemId = net.minecraft.resources.Identifier.parse(NotEnoughRecipes.MOD_ID + ":" + itemDef.id);
                
                // Check if item already exists in registry
                var existingItem = BuiltInRegistries.ITEM.getValue(itemId);
                if (existingItem != null && existingItem != net.minecraft.world.item.Items.AIR) {
                    // Item already exists - update its component data
                    DynamicRegistryHelper.updateItemComponents(itemDef.id, itemDef.components);
                    itemsUpdated++;
                    source.sendSuccess(() -> Component.literal("  Updated components for: " + itemDef.id), false);
                    NotEnoughRecipes.LOGGER.debug("Item {} already registered, updated components", itemId);
                    continue;
                }
                
                // Register the item (this will also load its resources)
                try {
                    DynamicRegistryHelper.registerDynamicItemFromDefinition(itemDef);
                    itemsRegistered++;
                    source.sendSuccess(() -> Component.literal("  Registered item: " + itemDef.id), false);
                } catch (Exception e) {
                    NotEnoughRecipes.LOGGER.error("Failed to register item: {}", itemDef.id, e);
                    source.sendFailure(Component.literal("  Failed to register item: " + itemDef.id + " - " + e.getMessage()));
                }
            }
            
            // Process blocks
            source.sendSuccess(() -> Component.literal("Processing " + blocks.size() + " blocks..."), false);
            for (var blockDef : blocks) {
                var blockId = net.minecraft.resources.Identifier.parse(NotEnoughRecipes.MOD_ID + ":" + blockDef.id);
                
                // Check if block already exists in registry
                var existingBlock = BuiltInRegistries.BLOCK.getValue(blockId);
                if (existingBlock != null && existingBlock != net.minecraft.world.level.block.Blocks.AIR) {
                    // Block already exists - update its component data and drops
                    DynamicRegistryHelper.updateBlockComponents(blockDef.id, blockDef.components);
                    
                    // Update drops if specified
                    if (blockDef.drops != null && blockDef.drops.size() > 0) {
                        java.util.List<DynamicRegistryHelper.BlockDrop> drops = DynamicRegistryHelper.parseDropsFromJson(blockDef.drops);
                        DynamicRegistryHelper.updateBlockDrops(blockDef.id, drops);
                    }
                    
                    blocksUpdated++;
                    source.sendSuccess(() -> Component.literal("  Updated components for: " + blockDef.id), false);
                    NotEnoughRecipes.LOGGER.debug("Block {} already registered, updated components and drops", blockId);
                    continue;
                }
                
                // Register the block (this will also load its resources)
                try {
                    DynamicRegistryHelper.registerDynamicBlockFromDefinition(blockDef);
                    blocksRegistered++;
                    source.sendSuccess(() -> Component.literal("  Registered block: " + blockDef.id), false);
                } catch (Exception e) {
                    NotEnoughRecipes.LOGGER.error("Failed to register block: {}", blockDef.id, e);
                    source.sendFailure(Component.literal("  Failed to register block: " + blockDef.id + " - " + e.getMessage()));
                }
            }
            
            // Summary
            final int finalItemsRegistered = itemsRegistered;
            final int finalItemsUpdated = itemsUpdated;
            final int finalBlocksRegistered = blocksRegistered;
            final int finalBlocksUpdated = blocksUpdated;
            
            source.sendSuccess(() -> Component.literal(""), false);
            source.sendSuccess(() -> Component.literal("=== Reload Summary ==="), false);
            source.sendSuccess(() -> Component.literal("Items: " + finalItemsRegistered + " registered, " + finalItemsUpdated + " updated"), false);
            source.sendSuccess(() -> Component.literal("Blocks: " + finalBlocksRegistered + " registered, " + finalBlocksUpdated + " updated"), false);
            
            // Trigger resource reload if any items/blocks were registered or updated
            if (itemsRegistered > 0 || blocksRegistered > 0) {
                source.sendSuccess(() -> Component.literal(""), false);
                source.sendSuccess(() -> Component.literal("Triggering resource reload..."), false);
                
                try {
                    // Rebuild the pack first
                    DynamicResourceLoader.getResourcePack().rebuild();
                    
                    // Trigger the reload
                    DynamicResourceLoader.triggerResourceReload();
                    
                    source.sendSuccess(() -> Component.literal("Resource reload completed!"), true);
                } catch (Exception e) {
                    NotEnoughRecipes.LOGGER.error("Failed to reload resources", e);
                    source.sendFailure(Component.literal("Warning: Resource reload failed - " + e.getMessage()));
                    source.sendSuccess(() -> Component.literal("You may need to press F3+T manually to see textures."), false);
                }
            } else if (itemsUpdated > 0 || blocksUpdated > 0) {
                source.sendSuccess(() -> Component.literal(""), false);
                source.sendSuccess(() -> Component.literal("Components updated! Changes will apply to newly created ItemStacks."), false);
                source.sendSuccess(() -> Component.literal("Existing items in inventories will keep their old data."), false);
            } else {
                source.sendSuccess(() -> Component.literal("No changes to apply."), false);
            }
            
            return 1;
        } catch (Exception e) {
            NotEnoughRecipes.LOGGER.error("Failed to reload persisted entries", e);
            source.sendFailure(Component.literal("Failed to reload: " + e.getMessage()));
            return 0;
        }
    }
    
    private static int showPersistencePath(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        var path = DynamicRegistryPersistence.getRegistryPath();
        source.sendSuccess(() -> Component.literal("Persistence storage folder:"), false);
        source.sendSuccess(() -> Component.literal(path.toString()), false);
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("Files:"), false);
        source.sendSuccess(() -> Component.literal("  items.json  - Persisted item definitions"), false);
        source.sendSuccess(() -> Component.literal("  blocks.json - Persisted block definitions"), false);
        
        return 1;
    }
}
