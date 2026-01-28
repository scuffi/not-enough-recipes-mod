package dev.scuffi.registry;

import dev.scuffi.NotEnoughRecipes;
import dev.scuffi.mixin.HolderReferenceAccessor;
import dev.scuffi.mixin.MappedRegistryAccessor;
import dev.scuffi.mixin.MappedRegistryIntrusive;
import dev.scuffi.resource.DynamicResourceLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.item.ItemParser;

import net.minecraft.tags.TagKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class to dynamically unfreeze registries and register new entries at runtime.
 * WARNING: This is a proof-of-concept for singleplayer only. Do not use in multiplayer!
 * 
 * Supports data components in the format used by /give commands:
 * - custom_name, lore, max_stack_size, unbreakable, enchantment_glint_override, rarity, etc.
 */
public class DynamicRegistryHelper {
    
    private static int dynamicBlockCounter = 0;
    private static int dynamicItemCounter = 0;
    
    // Store component JSON objects for registered items (keyed by item ID)
    // Components are stored as JSON objects for easy LLM generation
    private static final Map<String, com.google.gson.JsonObject> itemComponentObjects = new HashMap<>();
    private static final Map<String, com.google.gson.JsonObject> blockComponentObjects = new HashMap<>();
    
    // Store block drop definitions (keyed by block ID)
    private static final Map<String, List<BlockDrop>> blockDrops = new HashMap<>();
    
    /**
     * Represents a custom drop for a block.
     * 
     * @param item The item ID to drop (e.g., "minecraft:diamond", "ner:custom_item")
     * @param count The exact count to drop (used if min/max not specified)
     * @param min Minimum count for random range
     * @param max Maximum count for random range
     * @param chance Probability of drop (0.0 to 1.0, default 1.0)
     */
    public record BlockDrop(String item, int count, int min, int max, float chance) {
        public BlockDrop(String item, int count) {
            this(item, count, 0, 0, 1.0f);
        }
        
        public BlockDrop(String item, int min, int max) {
            this(item, 1, min, max, 1.0f);
        }
        
        public BlockDrop(String item, int count, float chance) {
            this(item, count, 0, 0, chance);
        }
    }
    
    /**
     * Unfreezes a registry to allow new registrations.
     */
    public static <T> void unfreezeRegistry(Registry<T> registry) {
        if (registry instanceof MappedRegistry<T> mappedRegistry) {
            MappedRegistryAccessor accessor = (MappedRegistryAccessor) mappedRegistry;
            
            if (accessor.isFrozen()) {
                // Clear any existing intrusive holders before unfreezing
                // This prevents "unregistered intrusive holders" errors
                MappedRegistryIntrusive intrusive = (MappedRegistryIntrusive) mappedRegistry;
                if (intrusive.getUnregisteredIntrusiveHolders() != null) {
                    intrusive.setUnregisteredIntrusiveHolders(null);
                    NotEnoughRecipes.LOGGER.debug("Cleared existing intrusive holders for: {}", registry.key());
                }
                
                accessor.setFrozen(false);
                NotEnoughRecipes.LOGGER.info("Unfroze registry: {}", registry.key());
                
                // Re-enable intrusive holder registration by creating a new map
                if (intrusive.getUnregisteredIntrusiveHolders() == null) {
                    intrusive.setUnregisteredIntrusiveHolders(new IdentityHashMap<>());
                    NotEnoughRecipes.LOGGER.debug("Re-enabled intrusive holders for: {}", registry.key());
                }
            }
        }
    }
    
    /**
     * Freezes a registry to prevent further modifications.
     */
    public static <T> void freezeRegistry(Registry<T> registry) {
        if (registry instanceof MappedRegistry<T> mappedRegistry) {
            MappedRegistryAccessor accessor = (MappedRegistryAccessor) mappedRegistry;
            
            if (!accessor.isFrozen()) {
                // Clear intrusive holders before freezing
                MappedRegistryIntrusive intrusive = (MappedRegistryIntrusive) mappedRegistry;
                if (intrusive.getUnregisteredIntrusiveHolders() != null) {
                    intrusive.setUnregisteredIntrusiveHolders(null);
                }
                
                accessor.setFrozen(true);
                NotEnoughRecipes.LOGGER.info("Froze registry: {}", registry.key());
            }
        }
    }
    
    /**
     * Checks if a registry is frozen.
     */
    public static <T> boolean isRegistryFrozen(Registry<T> registry) {
        if (registry instanceof MappedRegistry<T> mappedRegistry) {
            return ((MappedRegistryAccessor) mappedRegistry).isFrozen();
        }
        return true; // Assume frozen if we can't check
    }
    
    /**
     * Registers a new dynamic block and its corresponding item.
     * Returns the registered block.
     */
    public static Block registerDynamicBlock() {
        String blockId = "dynamic_block_" + dynamicBlockCounter++;
        return registerDynamicBlockInternal(blockId, null);
    }
    
    /**
     * Registers a new dynamic block with a custom name and optional texture.
     * If textureName is provided, attempts to load the texture and create all required models.
     * This will persist the definition to storage.
     * 
     * @param name The name for the block (will be prefixed with the mod ID)
     * @param textureName The texture filename (without .png), or null for default
     * @return The registered block, or null if registration failed
     */
    public static Block registerDynamicBlockWithTexture(String name, String textureName) {
        return registerDynamicBlockInternal(name, textureName, true);
    }
    
    /**
     * Registers a new dynamic block without persisting to storage.
     * Used when loading from persisted definitions on startup.
     */
    public static Block registerDynamicBlockWithTextureNoPersist(String name, String textureName) {
        return registerDynamicBlockInternal(name, textureName, false);
    }
    
    /**
     * Internal method to register a dynamic block with optional texture.
     */
    private static Block registerDynamicBlockInternal(String blockId, String textureName) {
        return registerDynamicBlockInternal(blockId, textureName, true);
    }
    
    /**
     * Internal method to register a dynamic block with optional texture.
     * @param persist If true, saves the definition to persistent storage
     */
    private static Block registerDynamicBlockInternal(String blockId, String textureName, boolean persist) {
        NotEnoughRecipes.LOGGER.info("Attempting to register dynamic block: {}:{}", NotEnoughRecipes.MOD_ID, blockId);
        
        // If texture name provided, try to load the texture and create all models
        boolean hasTexture = false;
        if (textureName != null && !textureName.isEmpty()) {
            hasTexture = DynamicResourceLoader.loadTexture("block", textureName);
            if (hasTexture) {
                // Generate all required resources for this block
                // This creates: block model, item model, blockstate, and item definition
                DynamicResourceLoader.addCustomBlockModel(blockId, textureName);
                // Rebuild the resource pack
                DynamicResourceLoader.getResourcePack().rebuild();
                NotEnoughRecipes.LOGGER.info("Loaded texture '{}' for block '{}'", textureName, blockId);
            } else {
                NotEnoughRecipes.LOGGER.warn("Texture '{}' not found, block will have missing texture", textureName);
            }
        }
        
        // Track if registries were frozen before we started
        // During mod init, registries are NOT frozen yet - we shouldn't freeze them
        // During runtime (after game loads), registries ARE frozen - we need to re-freeze
        boolean blockWasFrozen = isRegistryFrozen(BuiltInRegistries.BLOCK);
        boolean itemWasFrozen = isRegistryFrozen(BuiltInRegistries.ITEM);
        
        // Unfreeze both registries (if they were frozen)
        unfreezeRegistry(BuiltInRegistries.BLOCK);
        unfreezeRegistry(BuiltInRegistries.ITEM);
        
        try {
            // Create the Identifier (ResourceLocation equivalent)
            Identifier blockLocation = Identifier.parse(NotEnoughRecipes.MOD_ID + ":" + blockId);
            
            // Create the ResourceKey for the block
            ResourceKey<Block> blockKey = ResourceKey.create(BuiltInRegistries.BLOCK.key(), blockLocation);
            
            // Create block properties and set the ID BEFORE creating the block
            var blockProperties = BlockBehaviour.Properties.of()
                    .strength(2.0f, 3.0f)
                    .requiresCorrectToolForDrops()
                    .setId(blockKey);
            
            // Create the block with properties that have the ID set
            // Use DynamicBlock if we might have custom drops, otherwise use vanilla Block
            Block block = new DynamicBlock(blockProperties, blockId);
            
            // Register the block
            Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
            NotEnoughRecipes.LOGGER.info("Successfully registered block: {}", blockLocation);
            
            // IMPORTANT: Bind empty tags to the block holder to prevent "Tags not bound" crash
            bindEmptyTags(BuiltInRegistries.BLOCK, block);
            
            // Create the ResourceKey for the block item
            ResourceKey<Item> itemKey = ResourceKey.create(BuiltInRegistries.ITEM.key(), blockLocation);
            
            // Create item properties with ID set
            var itemProperties = new Item.Properties().setId(itemKey);
            
            // Create and register the block item
            BlockItem blockItem = new BlockItem(block, itemProperties);
            Registry.register(BuiltInRegistries.ITEM, itemKey, blockItem);
            NotEnoughRecipes.LOGGER.info("Successfully registered block item: {}", blockLocation);
            
            // IMPORTANT: Bind empty tags to the item holder as well
            bindEmptyTags(BuiltInRegistries.ITEM, blockItem);
            
            // Save to persistent storage if requested
            if (persist && textureName != null) {
                DynamicRegistryPersistence.saveBlockDefinition(blockId, textureName);
            }
            
            return block;
        } finally {
            // Only re-freeze if they were frozen before we started
            // This prevents freezing during mod init when Fabric hasn't frozen them yet
            if (blockWasFrozen) {
                freezeRegistry(BuiltInRegistries.BLOCK);
            }
            if (itemWasFrozen) {
                freezeRegistry(BuiltInRegistries.ITEM);
            }
        }
    }
    
    /**
     * Registers a new dynamic standalone item (not a block item).
     * Returns the registered item.
     */
    public static Item registerDynamicItem() {
        String itemId = "dynamic_item_" + dynamicItemCounter++;
        return registerDynamicItemInternal(itemId, null);
    }
    
    /**
     * Registers a new dynamic item with a custom name and optional texture.
     * If textureName is provided, attempts to load the texture and create a model.
     * This will persist the definition to storage.
     * 
     * @param name The name for the item (will be prefixed with the mod ID)
     * @param textureName The texture filename (without .png), or null for default
     * @return The registered item, or null if registration failed
     */
    public static Item registerDynamicItemWithTexture(String name, String textureName) {
        return registerDynamicItemInternal(name, textureName, true);
    }
    
    /**
     * Registers a new dynamic item without persisting to storage.
     * Used when loading from persisted definitions on startup.
     */
    public static Item registerDynamicItemWithTextureNoPersist(String name, String textureName) {
        return registerDynamicItemInternal(name, textureName, false);
    }
    
    /**
     * Internal method to register a dynamic item with optional texture.
     */
    private static Item registerDynamicItemInternal(String itemId, String textureName) {
        return registerDynamicItemInternal(itemId, textureName, true);
    }
    
    /**
     * Internal method to register a dynamic item with optional texture.
     * @param persist If true, saves the definition to persistent storage
     */
    private static Item registerDynamicItemInternal(String itemId, String textureName, boolean persist) {
        NotEnoughRecipes.LOGGER.info("Attempting to register dynamic item: {}:{}", NotEnoughRecipes.MOD_ID, itemId);
        
        // If texture name provided, try to load the texture and model
        boolean hasTexture = false;
        if (textureName != null && !textureName.isEmpty()) {
            hasTexture = DynamicResourceLoader.loadTexture("item", textureName);
            if (hasTexture) {
                // Generate a model for this item that uses the loaded texture
                DynamicResourceLoader.loadModel("item", itemId);
                // Update the model to use the correct texture
                DynamicResourceLoader.addCustomItemModel(itemId, textureName);
                // Rebuild the resource pack
                DynamicResourceLoader.getResourcePack().rebuild();
                NotEnoughRecipes.LOGGER.info("Loaded texture '{}' for item '{}'", textureName, itemId);
            } else {
                NotEnoughRecipes.LOGGER.warn("Texture '{}' not found, item will have missing texture", textureName);
            }
        }
        
        // Track if registry was frozen before we started
        // During mod init, registries are NOT frozen yet - we shouldn't freeze them
        // During runtime (after game loads), registries ARE frozen - we need to re-freeze
        boolean wasFrozen = isRegistryFrozen(BuiltInRegistries.ITEM);
        
        // Unfreeze the item registry (if it was frozen)
        unfreezeRegistry(BuiltInRegistries.ITEM);
        
        try {
            // Create the Identifier
            Identifier itemLocation = Identifier.parse(NotEnoughRecipes.MOD_ID + ":" + itemId);
            
            // Create the ResourceKey
            ResourceKey<Item> itemKey = ResourceKey.create(BuiltInRegistries.ITEM.key(), itemLocation);
            
            // Create item properties with ID set BEFORE creating the item
            var itemProperties = new Item.Properties()
                    .stacksTo(64)
                    .setId(itemKey);
            
            // Create a simple item with proper properties
            Item item = new Item(itemProperties);
            
            // Register the item
            Registry.register(BuiltInRegistries.ITEM, itemKey, item);
            NotEnoughRecipes.LOGGER.info("Successfully registered item: {}", itemLocation);
            
            // IMPORTANT: Bind empty tags to the holder to prevent "Tags not bound" crash
            // This is normally done during data pack loading, but we need to do it manually
            bindEmptyTags(BuiltInRegistries.ITEM, item);
            
            // Save to persistent storage if requested
            if (persist && textureName != null) {
                DynamicRegistryPersistence.saveItemDefinition(itemId, textureName);
            }
            
            return item;
        } finally {
            // Only re-freeze if it was frozen before we started
            // This prevents freezing during mod init when Fabric hasn't frozen them yet
            if (wasFrozen) {
                freezeRegistry(BuiltInRegistries.ITEM);
            }
        }
    }
    
    /**
     * Gets registry statistics for debugging.
     */
    public static String getRegistryStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("Registry Statistics:\n");
        sb.append(String.format("  Block Registry: %d entries, frozen=%b\n", 
                BuiltInRegistries.BLOCK.size(), 
                isRegistryFrozen(BuiltInRegistries.BLOCK)));
        sb.append(String.format("  Item Registry: %d entries, frozen=%b\n", 
                BuiltInRegistries.ITEM.size(), 
                isRegistryFrozen(BuiltInRegistries.ITEM)));
        sb.append(String.format("  Dynamic blocks created: %d\n", dynamicBlockCounter));
        sb.append(String.format("  Dynamic items created: %d\n", dynamicItemCounter));
        return sb.toString();
    }
    
    // ==================== Codec-Based Registration ====================
    
    /**
     * Registers a dynamic item from a codec-based definition.
     * Uses Minecraft's built-in Item.Properties and DataComponents.
     */
    public static Item registerDynamicItemFromDefinition(DynamicRegistryPersistence.ItemDefinition definition) {
        NotEnoughRecipes.LOGGER.info("Registering item from definition: {}:{}", NotEnoughRecipes.MOD_ID, definition.id);
        
        // Load texture if provided
        if (definition.texture != null && !definition.texture.isEmpty()) {
            boolean hasTexture = DynamicResourceLoader.loadTexture("item", definition.texture);
            if (hasTexture) {
                DynamicResourceLoader.loadModel("item", definition.id);
                DynamicResourceLoader.addCustomItemModel(definition.id, definition.texture);
                DynamicResourceLoader.getResourcePack().rebuild();
                NotEnoughRecipes.LOGGER.info("Loaded texture '{}' for item '{}'", definition.texture, definition.id);
            } else {
                NotEnoughRecipes.LOGGER.warn("Texture '{}' not found for item '{}'", definition.texture, definition.id);
            }
        }
        
        boolean wasFrozen = isRegistryFrozen(BuiltInRegistries.ITEM);
        unfreezeRegistry(BuiltInRegistries.ITEM);
        
        try {
            Identifier itemLocation = Identifier.parse(NotEnoughRecipes.MOD_ID + ":" + definition.id);
            
            // Create the ResourceKey for the item - REQUIRED in Fabric 1.21.11
            ResourceKey<Item> itemKey = ResourceKey.create(BuiltInRegistries.ITEM.key(), itemLocation);
            
            // Build Item.Properties - extract from components if present, otherwise default
            // IMPORTANT: Must call setId() before creating the item in Fabric 1.21.11
            Item.Properties itemProperties = new Item.Properties().setId(itemKey);
            if (definition.components != null) {
                // Extract max_stack_size and max_damage from components if present
                if (definition.components.has("max_stack_size")) {
                    itemProperties = itemProperties.stacksTo(definition.components.get("max_stack_size").getAsInt());
                } else {
                    itemProperties = itemProperties.stacksTo(64);
                }
                if (definition.components.has("max_damage")) {
                    itemProperties = itemProperties.durability(definition.components.get("max_damage").getAsInt());
                }
            } else {
                itemProperties = itemProperties.stacksTo(64);
            }
            
            // Create and register the item
            Item item = new Item(itemProperties);
            Registry.register(BuiltInRegistries.ITEM, itemKey, item);
            NotEnoughRecipes.LOGGER.info("Successfully registered item: {}", itemLocation);
            
            bindEmptyTags(BuiltInRegistries.ITEM, item);
            
            // Bind tags to the item
            List<String> tags = definition.tags != null ? definition.tags : Collections.emptyList();
            bindTags(BuiltInRegistries.ITEM, item, tags);
            if (!tags.isEmpty()) {
                NotEnoughRecipes.LOGGER.info("Bound {} tags to item '{}': {}", tags.size(), definition.id, tags);
            }
            
            // Store the component JSON object for later application to ItemStacks
            if (definition.components != null && !definition.components.isEmpty()) {
                itemComponentObjects.put(definition.id, definition.components);
                NotEnoughRecipes.LOGGER.info("Stored components for item '{}'", definition.id);
            }
            
            return item;
        } finally {
            if (wasFrozen) {
                freezeRegistry(BuiltInRegistries.ITEM);
            }
        }
    }
    
    /**
     * Registers a dynamic block from a codec-based definition.
     * Uses Minecraft's built-in BlockBehaviour.Properties.
     */
    public static Block registerDynamicBlockFromDefinition(DynamicRegistryPersistence.BlockDefinition definition) {
        NotEnoughRecipes.LOGGER.info("Registering block from definition: {}:{}", NotEnoughRecipes.MOD_ID, definition.id);
        
        // Load texture if provided
        if (definition.texture != null && !definition.texture.isEmpty()) {
            boolean hasTexture = DynamicResourceLoader.loadTexture("block", definition.texture);
            if (hasTexture) {
                DynamicResourceLoader.addCustomBlockModel(definition.id, definition.texture);
                DynamicResourceLoader.getResourcePack().rebuild();
                NotEnoughRecipes.LOGGER.info("Loaded texture '{}' for block '{}'", definition.texture, definition.id);
            } else {
                NotEnoughRecipes.LOGGER.warn("Texture '{}' not found for block '{}'", definition.texture, definition.id);
            }
        }
        
        boolean blockWasFrozen = isRegistryFrozen(BuiltInRegistries.BLOCK);
        boolean itemWasFrozen = isRegistryFrozen(BuiltInRegistries.ITEM);
        
        unfreezeRegistry(BuiltInRegistries.BLOCK);
        unfreezeRegistry(BuiltInRegistries.ITEM);
        
        try {
            Identifier blockLocation = Identifier.parse(NotEnoughRecipes.MOD_ID + ":" + definition.id);
            
            // Create the ResourceKey for the block - REQUIRED in Fabric 1.21.11
            ResourceKey<Block> blockKey = ResourceKey.create(BuiltInRegistries.BLOCK.key(), blockLocation);
            
            // Build BlockBehaviour.Properties from definition
            // IMPORTANT: Must call setId() before creating the block in Fabric 1.21.11
            BlockBehaviour.Properties blockProperties;
            if (definition.properties != null) {
                blockProperties = definition.properties.toMinecraftProperties().setId(blockKey);
            } else {
                blockProperties = BlockBehaviour.Properties.of()
                        .strength(2.0f, 3.0f)
                        .requiresCorrectToolForDrops()
                        .setId(blockKey);
            }
            
            // Create and register the block using DynamicBlock for custom drops support
            Block block = new DynamicBlock(blockProperties, definition.id);
            Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
            NotEnoughRecipes.LOGGER.info("Successfully registered block: {}", blockLocation);
            
            bindEmptyTags(BuiltInRegistries.BLOCK, block);
            
            // Bind tags to the block
            List<String> tags = definition.tags != null ? definition.tags : Collections.emptyList();
            bindTags(BuiltInRegistries.BLOCK, block, tags);
            if (!tags.isEmpty()) {
                NotEnoughRecipes.LOGGER.info("Bound {} tags to block '{}': {}", tags.size(), definition.id, tags);
            }
            
            // Create the ResourceKey for the block item
            ResourceKey<Item> itemKey = ResourceKey.create(BuiltInRegistries.ITEM.key(), blockLocation);
            
            // Build Item.Properties for the BlockItem - extract from components if present
            // IMPORTANT: Must call setId() before creating the item in Fabric 1.21.11
            Item.Properties itemProperties = new Item.Properties().setId(itemKey);
            if (definition.components != null) {
                if (definition.components.has("max_stack_size")) {
                    itemProperties = itemProperties.stacksTo(definition.components.get("max_stack_size").getAsInt());
                }
                if (definition.components.has("max_damage")) {
                    itemProperties = itemProperties.durability(definition.components.get("max_damage").getAsInt());
                }
            }
            
            // Create and register the block item
            BlockItem blockItem = new BlockItem(block, itemProperties);
            Registry.register(BuiltInRegistries.ITEM, itemKey, blockItem);
            NotEnoughRecipes.LOGGER.info("Successfully registered block item: {}", blockLocation);
            
            bindEmptyTags(BuiltInRegistries.ITEM, blockItem);
            
            // Store the component JSON object for later application to ItemStacks
            if (definition.components != null && !definition.components.isEmpty()) {
                blockComponentObjects.put(definition.id, definition.components);
                NotEnoughRecipes.LOGGER.info("Stored components for block '{}'", definition.id);
            }
            
            // Parse and store drops
            if (definition.drops != null && definition.drops.size() > 0) {
                List<BlockDrop> drops = parseDropsFromJson(definition.drops);
                if (!drops.isEmpty()) {
                    setBlockDrops(definition.id, drops);
                    NotEnoughRecipes.LOGGER.info("Registered {} custom drops for block '{}'", drops.size(), definition.id);
                }
            }
            
            return block;
        } finally {
            if (blockWasFrozen) {
                freezeRegistry(BuiltInRegistries.BLOCK);
            }
            if (itemWasFrozen) {
                freezeRegistry(BuiltInRegistries.ITEM);
            }
        }
    }
    
    /**
     * Binds tags to a holder.
     * This is normally done during data pack loading, but for dynamically registered
     * entries we need to do it manually.
     * 
     * @param registry The registry containing the entry
     * @param entry The entry to bind tags to
     * @param tagIds List of tag IDs as strings (e.g., ["minecraft:boots", "ner:custom_items"])
     */
    @SuppressWarnings("unchecked")
    private static <T> void bindTags(Registry<T> registry, T entry, List<String> tagIds) {
        try {
            // Get the holder for this entry
            var holder = registry.wrapAsHolder(entry);
            
            if (holder instanceof Holder.Reference<T> reference) {
                // Convert tag ID strings to TagKey objects
                List<TagKey<T>> tagKeys = new ArrayList<>();
                ResourceKey<? extends Registry<T>> registryKey = registry.key();
                
                for (String tagId : tagIds) {
                    try {
                        Identifier tagLocation = Identifier.parse(tagId);
                        TagKey<T> tagKey = TagKey.create(registryKey, tagLocation);
                        tagKeys.add(tagKey);
                        NotEnoughRecipes.LOGGER.debug("Created tag key: {} for registry {}", tagKey, registryKey);
                    } catch (Exception e) {
                        NotEnoughRecipes.LOGGER.warn("Failed to parse tag ID '{}': {}", tagId, e.getMessage());
                    }
                }
                
                // Use our mixin accessor to call the package-private bindTags method
                ((HolderReferenceAccessor) (Object) reference).invokeBindTags((Collection) tagKeys);
                NotEnoughRecipes.LOGGER.info("Bound {} tags to holder for: {}", tagKeys.size(), registry.getKey(entry));
            }
        } catch (Exception e) {
            NotEnoughRecipes.LOGGER.warn("Failed to bind tags to holder: {}", e.getMessage());
        }
    }
    
    /**
     * Binds empty tags to a holder to prevent "Tags not bound" crashes.
     * This is normally done during data pack loading, but for dynamically registered
     * entries we need to do it manually.
     */
    @SuppressWarnings("unchecked")
    private static <T> void bindEmptyTags(Registry<T> registry, T entry) {
        bindTags(registry, entry, Collections.emptyList());
    }
    
    // ==================== Data Component Support ====================
    
    /**
     * Check if an item has stored components.
     */
    public static boolean hasItemComponents(String itemId) {
        com.google.gson.JsonObject comps = itemComponentObjects.get(itemId);
        return comps != null && !comps.isEmpty();
    }
    
    /**
     * Check if a block has stored components.
     */
    public static boolean hasBlockComponents(String blockId) {
        com.google.gson.JsonObject comps = blockComponentObjects.get(blockId);
        return comps != null && !comps.isEmpty();
    }
    
    /**
     * Update component data for an already registered item.
     * This allows reloading component data from JSON without re-registering the item.
     */
    public static void updateItemComponents(String itemId, com.google.gson.JsonObject components) {
        if (components != null && !components.isEmpty()) {
            itemComponentObjects.put(itemId, components);
            NotEnoughRecipes.LOGGER.info("Updated components for item '{}'", itemId);
        } else {
            itemComponentObjects.remove(itemId);
            NotEnoughRecipes.LOGGER.info("Removed components for item '{}'", itemId);
        }
    }
    
    /**
     * Update component data for an already registered block.
     * This allows reloading component data from JSON without re-registering the block.
     */
    public static void updateBlockComponents(String blockId, com.google.gson.JsonObject components) {
        if (components != null && !components.isEmpty()) {
            blockComponentObjects.put(blockId, components);
            NotEnoughRecipes.LOGGER.info("Updated components for block '{}'", blockId);
        } else {
            blockComponentObjects.remove(blockId);
            NotEnoughRecipes.LOGGER.info("Removed components for block '{}'", blockId);
        }
    }
    
    /**
     * Creates an ItemStack for a dynamic item with its stored components applied.
     * Converts JSON components to SNBT format and uses Minecraft's ItemParser.
     * 
     * @param item The item to create a stack for
     * @param count Number of items in the stack
     * @return ItemStack with components applied
     */
    public static ItemStack createItemStack(Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        
        // Get the item's ID
        var itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId != null && itemId.getNamespace().equals(NotEnoughRecipes.MOD_ID)) {
            String path = itemId.getPath();
            
            // Get stored component JSON object (item components take priority)
            com.google.gson.JsonObject componentObj = itemComponentObjects.get(path);
            if (componentObj == null || componentObj.isEmpty()) {
                componentObj = blockComponentObjects.get(path);
            }
            
            // Convert JSON to SNBT and apply components
            if (componentObj != null && !componentObj.isEmpty()) {
                String componentString = convertJsonComponentsToSNBT(componentObj);
                if (componentString != null && !componentString.isEmpty()) {
                    applyComponentsFromString(stack, componentString);
                }
            }
        }
        
        return stack;
    }
    
    /**
     * Converts a JSON components object to SNBT format for ItemParser.
     * Handles common components like custom_name, lore, enchantments, etc.
     * If _raw_snbt is present, it's used directly (for backward compatibility).
     */
    private static String convertJsonComponentsToSNBT(com.google.gson.JsonObject components) {
        if (components == null || components.isEmpty()) {
            return "";
        }
        
        // If _raw_snbt is present, use it directly (for command-based registration)
        if (components.has("_raw_snbt")) {
            String raw = components.get("_raw_snbt").getAsString();
            // Ensure it has brackets
            if (!raw.trim().startsWith("[")) {
                return "[" + raw.trim() + "]";
            }
            return raw.trim();
        }
        
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        
        for (Map.Entry<String, com.google.gson.JsonElement> entry : components.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            
            String key = entry.getKey();
            com.google.gson.JsonElement value = entry.getValue();
            
            sb.append(key).append("=");
            
            // Handle different value types
            if (value.isJsonPrimitive()) {
                var prim = value.getAsJsonPrimitive();
                if (prim.isString()) {
                    // Strings need single quotes in SNBT
                    sb.append("'").append(escapeSNBTString(prim.getAsString())).append("'");
                } else if (prim.isBoolean()) {
                    sb.append(prim.getAsBoolean());
                } else if (prim.isNumber()) {
                    sb.append(prim.getAsNumber());
                }
            } else if (value.isJsonObject()) {
                // Objects: custom_name, enchantments, etc.
                if (key.equals("custom_name") || key.equals("item_name")) {
                    // Text component: convert to SNBT string format
                    sb.append(convertTextComponentToSNBT(value.getAsJsonObject()));
                } else if (key.equals("unbreakable")) {
                    // Empty object
                    sb.append("{}");
                } else {
                    // Generic object: convert to SNBT map format
                    sb.append(convertJsonObjectToSNBT(value.getAsJsonObject()));
                }
            } else if (value.isJsonArray()) {
                // Arrays: lore, attribute_modifiers, etc.
                if (key.equals("lore")) {
                    // Lore: array of text components
                    sb.append(convertLoreArrayToSNBT(value.getAsJsonArray()));
                } else {
                    // Generic array
                    sb.append(convertJsonArrayToSNBT(value.getAsJsonArray()));
                }
            }
        }
        
        return "[" + sb.toString() + "]";
    }
    
    private static String convertTextComponentToSNBT(com.google.gson.JsonObject text) {
        // Convert JSON object to SNBT map format
        // Minecraft's ItemParser expects text components as SNBT maps, not JSON strings
        // Example: {"text":"Hello","color":"red"} becomes {text:"Hello",color:"red"}
        // We use the existing convertJsonObjectToSNBT method which handles this correctly
        return convertJsonObjectToSNBT(text);
    }
    
    private static String convertLoreArrayToSNBT(com.google.gson.JsonArray lore) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < lore.size(); i++) {
            if (i > 0) sb.append(",");
            var element = lore.get(i);
            if (element.isJsonArray()) {
                // Handle multi-colored text on a single line (array of text components)
                // Example: [{"text":"Part1","color":"red"},{"text":"Part2","color":"blue"}]
                // This becomes a single line with multiple colored parts
                sb.append(convertMultiColoredLineToSNBT(element.getAsJsonArray()));
            } else if (element.isJsonObject()) {
                sb.append(convertTextComponentToSNBT(element.getAsJsonObject()));
            } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                sb.append("'\"").append(escapeSNBTString(element.getAsString())).append("\"'");
            }
        }
        sb.append("]");
        return sb.toString();
    }
    
    private static String convertMultiColoredLineToSNBT(com.google.gson.JsonArray parts) {
        // Convert an array of text components into a single line with multiple colors
        // Minecraft uses an array format for this: [{text:"Part1",color:"red"},{text:"Part2",color:"blue"}]
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(",");
            var part = parts.get(i);
            if (part.isJsonObject()) {
                sb.append(convertTextComponentToSNBT(part.getAsJsonObject()));
            } else if (part.isJsonPrimitive() && part.getAsJsonPrimitive().isString()) {
                // Plain string part
                sb.append("{text:\"").append(escapeSNBTString(part.getAsString())).append("\"}");
            }
        }
        sb.append("]");
        return sb.toString();
    }
    
    private static String convertJsonObjectToSNBT(com.google.gson.JsonObject obj) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, com.google.gson.JsonElement> entry : obj.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append(entry.getKey()).append(":");
            var val = entry.getValue();
            if (val.isJsonPrimitive()) {
                var prim = val.getAsJsonPrimitive();
                if (prim.isString()) {
                    // Use double quotes for SNBT strings (Minecraft's standard format)
                    sb.append("\"").append(escapeSNBTString(prim.getAsString())).append("\"");
                } else if (prim.isNumber()) {
                    sb.append(prim.getAsNumber());
                } else if (prim.isBoolean()) {
                    sb.append(prim.getAsBoolean());
                } else {
                    sb.append(prim);
                }
            } else if (val.isJsonObject()) {
                sb.append(convertJsonObjectToSNBT(val.getAsJsonObject()));
            } else if (val.isJsonArray()) {
                sb.append(convertJsonArrayToSNBT(val.getAsJsonArray()));
            }
        }
        sb.append("}");
        return sb.toString();
    }
    
    private static String convertJsonArrayToSNBT(com.google.gson.JsonArray arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.size(); i++) {
            if (i > 0) sb.append(",");
            var element = arr.get(i);
            if (element.isJsonPrimitive()) {
                var prim = element.getAsJsonPrimitive();
                if (prim.isString()) {
                    // Use double quotes for SNBT strings (Minecraft's standard format)
                    sb.append("\"").append(escapeSNBTString(prim.getAsString())).append("\"");
                } else {
                    sb.append(prim);
                }
            } else if (element.isJsonObject()) {
                sb.append(convertJsonObjectToSNBT(element.getAsJsonObject()));
            }
        }
        sb.append("]");
        return sb.toString();
    }
    
    private static String escapeSNBTString(String str) {
        // Escape quotes and backslashes for SNBT
        return str.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"");
    }
    
    /**
     * Apply components to an ItemStack using Minecraft's built-in parser.
     * This uses the same parsing logic as the /give command.
     * 
     * @param stack The ItemStack to modify
     * @param componentString Components in /give format: [custom_name="test",unbreakable={}]
     */
    public static void applyComponentsFromString(ItemStack stack, String componentString) {
        if (componentString == null || componentString.isEmpty()) {
            return;
        }
        
        try {
            // Get the item's full ID
            var itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId == null) {
                NotEnoughRecipes.LOGGER.warn("Cannot apply components: item not registered");
                return;
            }
            
            // Build the full item string: "namespace:item_id[components]"
            String fullItemString = itemId.toString() + componentString;
            
            // Get registry access from the client's current level or integrated server
            net.minecraft.core.HolderLookup.Provider registryAccess = getRegistryAccess();
            if (registryAccess == null) {
                NotEnoughRecipes.LOGGER.warn("Cannot apply components: no registry access available");
                return;
            }
            
            // Use Minecraft's ItemParser to parse the component string
            // This is the same parser used by /give command
            var parser = new ItemParser(registryAccess);
            var result = parser.parse(new StringReader(fullItemString));
            
            // Apply the parsed components to our stack
            stack.applyComponents(result.components());
            
            NotEnoughRecipes.LOGGER.debug("Applied components to {}: {}", itemId, componentString);
        } catch (CommandSyntaxException e) {
            NotEnoughRecipes.LOGGER.warn("Failed to parse components '{}': {}", componentString, e.getMessage());
        } catch (Exception e) {
            NotEnoughRecipes.LOGGER.error("Error applying components", e);
        }
    }
    
    /**
     * Get the registry access from the current game context.
     * IMPORTANT: Must use server's registry access for proper network serialization.
     */
    private static net.minecraft.core.HolderLookup.Provider getRegistryAccess() {
        try {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft == null) {
                return null;
            }
            
            // MUST use integrated server's registry access for proper enchantment/attribute serialization
            // The client level's registry doesn't have proper ID mappings for network packets
            var server = minecraft.getSingleplayerServer();
            if (server != null) {
                return server.registryAccess();
            }
            
            NotEnoughRecipes.LOGGER.warn("No server registry access available - components may not serialize correctly");
            return null;
        } catch (Exception e) {
            NotEnoughRecipes.LOGGER.debug("Failed to get registry access: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Creates an ItemStack for a dynamic item by ID with its stored components applied.
     */
    public static ItemStack createItemStackById(String itemId, int count) {
        Identifier id = Identifier.parse(NotEnoughRecipes.MOD_ID + ":" + itemId);
        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item != null && item != net.minecraft.world.item.Items.AIR) {
            return createItemStack(item, count);
        }
        return ItemStack.EMPTY;
    }
    
    /**
     * Gets the drop definitions for a block.
     */
    public static List<BlockDrop> getBlockDrops(String blockId) {
        return blockDrops.get(blockId);
    }
    
    /**
     * Sets the drop definitions for a block.
     */
    public static void setBlockDrops(String blockId, List<BlockDrop> drops) {
        if (drops != null && !drops.isEmpty()) {
            blockDrops.put(blockId, new ArrayList<>(drops));
            NotEnoughRecipes.LOGGER.info("Set {} drops for block '{}'", drops.size(), blockId);
        } else {
            blockDrops.remove(blockId);
            NotEnoughRecipes.LOGGER.info("Removed drops for block '{}'", blockId);
        }
    }
    
    /**
     * Updates the drop definitions for an already registered block.
     */
    public static void updateBlockDrops(String blockId, List<BlockDrop> drops) {
        setBlockDrops(blockId, drops);
    }
    
    /**
     * Parses a JSON array of drop definitions into BlockDrop objects.
     * 
     * Expected JSON format:
     * [
     *   {"item": "minecraft:diamond", "count": 2},
     *   {"item": "minecraft:emerald", "min": 1, "max": 3},
     *   {"item": "ner:custom_item", "count": 1, "chance": 0.5}
     * ]
     */
    public static List<BlockDrop> parseDropsFromJson(com.google.gson.JsonArray dropsArray) {
        List<BlockDrop> drops = new ArrayList<>();
        
        for (com.google.gson.JsonElement elem : dropsArray) {
            if (!elem.isJsonObject()) {
                NotEnoughRecipes.LOGGER.warn("Drop entry is not an object: {}", elem);
                continue;
            }
            
            com.google.gson.JsonObject dropObj = elem.getAsJsonObject();
            
            if (!dropObj.has("item")) {
                NotEnoughRecipes.LOGGER.warn("Drop entry missing 'item' field: {}", dropObj);
                continue;
            }
            
            String item = dropObj.get("item").getAsString();
            int count = dropObj.has("count") ? dropObj.get("count").getAsInt() : 1;
            int min = dropObj.has("min") ? dropObj.get("min").getAsInt() : 0;
            int max = dropObj.has("max") ? dropObj.get("max").getAsInt() : 0;
            float chance = dropObj.has("chance") ? dropObj.get("chance").getAsFloat() : 1.0f;
            
            BlockDrop drop = new BlockDrop(item, count, min, max, chance);
            drops.add(drop);
            
            NotEnoughRecipes.LOGGER.debug("Parsed drop: {} x{} (min:{}, max:{}, chance:{})", 
                item, count, min, max, chance);
        }
        
        return drops;
    }
}