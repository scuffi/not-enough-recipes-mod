package dev.scuffi.registry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.scuffi.NotEnoughRecipes;
import dev.scuffi.resource.DynamicResourceLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Handles persistence of dynamically registered items and blocks using structured JSON.
 * 
 * Items use a 'components' JSON object that maps component names to their values.
 * This is much easier for LLMs to generate correctly than string-based SNBT format.
 * 
 * Example item:
 * {
 *   "id": "magic_sword",
 *   "texture": "diamond_sword",
 *   "components": {
 *     "custom_name": {"text": "Magic Sword", "color": "gold"},
 *     "lore": [
 *       {"text": "A powerful weapon"},
 *       {"text": "Deals extra damage"}
 *     ],
 *     "enchantments": {"sharpness": 5, "unbreaking": 3},
 *     "max_stack_size": 1,
 *     "rarity": "epic"
 *   },
 *   "tags": ["minecraft:swords"]
 * }
 * 
 * Blocks keep 'properties' for BlockBehaviour.Properties (hardness, resistance, etc.)
 * and 'components' for the BlockItem's data components.
 */
public class DynamicRegistryPersistence {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static final String REGISTRY_FOLDER = "dynamic_registry";
    private static final String ITEMS_FILE = "items.json";
    private static final String BLOCKS_FILE = "blocks.json";

    private static Path registryPath;
    private static boolean initialized = false;

    // ==================== Item Definition ====================

    /**
     * Represents an item definition.
     * All item properties are specified via the 'components' JSON object.
     */
    public static class ItemDefinition {
        public String id;
        public String texture;
        /** 
         * Data components as a JSON object.
         * Keys are component names (e.g., "custom_name", "lore", "enchantments").
         * Values are the component data in JSON format.
         * 
         * Supported components (see https://minecraft.wiki/w/Data_component_format):
         * - custom_name: Text component object
         * - lore: Array of text component objects
         * - enchantments: Object mapping enchantment IDs to levels
         * - attribute_modifiers: Array of modifier objects
         * - max_stack_size: Integer
         * - max_damage: Integer
         * - rarity: String ("common", "uncommon", "rare", "epic")
         * - enchantment_glint_override: Boolean
         * - unbreakable: Object (empty {} for true)
         * - food: Object with nutrition, saturation, etc.
         * - And all other Minecraft 1.21.11 data components
         */
        public JsonObject components;
        /**
         * Item tags to bind to this item.
         * Array of tag IDs (e.g., ["minecraft:boots", "ner:custom_items"]).
         * Tags are bound to the registered item itself, not just stored as components.
         * This allows the item to be identified by tag queries (e.g., #minecraft:boots).
         */
        public List<String> tags;

        public ItemDefinition() {
            this.tags = new ArrayList<>();
        }

        public ItemDefinition(String id, String texture) {
            this.id = id;
            this.texture = texture;
            this.components = new JsonObject();
            this.tags = new ArrayList<>();
        }

        public ItemDefinition(String id, String texture, JsonObject components) {
            this.id = id;
            this.texture = texture;
            this.components = components != null ? components : new JsonObject();
            this.tags = new ArrayList<>();
        }
    }

    // ==================== Block Definition ====================

    /**
     * Represents a block definition.
     * Block properties (hardness, resistance, etc.) are separate from data components.
     */
    public static class BlockDefinition {
        public String id;
        public String texture;
        /** Block properties (hardness, resistance, light level, etc.) */
        public BlockProperties properties;
        /** 
         * Data components for the BlockItem as a JSON object.
         * Same format as ItemDefinition.components.
         */
        public JsonObject components;
        /**
         * Block tags to bind to this block.
         * Array of tag IDs (e.g., ["minecraft:mineable/pickaxe", "ner:custom_blocks"]).
         * Tags are bound to the registered block itself, not just stored as components.
         * This allows the block to be identified by tag queries (e.g., #minecraft:mineable/pickaxe).
         */
        public List<String> tags;
        /**
         * Custom drops for this block when mined.
         * If null or empty, uses vanilla drop behavior (drops itself).
         * Works with requires_correct_tool property - if true, drops only when mined with correct tool.
         */
        public JsonArray drops;

        public BlockDefinition() {
            this.properties = new BlockProperties();
            this.components = new JsonObject();
            this.tags = new ArrayList<>();
        }

        public BlockDefinition(String id, String texture) {
            this.id = id;
            this.texture = texture;
            this.properties = new BlockProperties();
            this.components = new JsonObject();
            this.tags = new ArrayList<>();
        }

        public BlockDefinition(String id, String texture, BlockProperties properties, JsonObject components) {
            this.id = id;
            this.texture = texture;
            this.properties = properties != null ? properties : new BlockProperties();
            this.components = components != null ? components : new JsonObject();
            this.tags = new ArrayList<>();
        }
    }

    /**
     * Block properties that can be serialized to JSON.
     * Maps to Minecraft's BlockBehaviour.Properties (not data components).
     */
    public static class BlockProperties {
        public Float hardness; // destroyTime
        public Float resistance; // explosionResistance
        public Boolean requiresCorrectTool;
        public Integer lightLevel; // 0-15
        public Float friction; // 0.6 is normal, lower = slippery
        public Float speedFactor; // 1.0 is normal
        public Float jumpFactor; // 1.0 is normal
        public String soundType; // wood, stone, metal, glass, wool, sand, gravel, etc.
        public Boolean noOcclusion; // Transparent blocks
        public Boolean noCollision; // No collision (like flowers)

        public BlockProperties() {}

        /**
         * Build Minecraft BlockBehaviour.Properties from this definition.
         */
        public net.minecraft.world.level.block.state.BlockBehaviour.Properties toMinecraftProperties() {
            var props = net.minecraft.world.level.block.state.BlockBehaviour.Properties.of();

            if (hardness != null && resistance != null) {
                props = props.strength(hardness, resistance);
            } else if (hardness != null) {
                props = props.strength(hardness);
            }

            if (requiresCorrectTool != null && requiresCorrectTool) {
                props = props.requiresCorrectToolForDrops();
            }

            if (lightLevel != null && lightLevel > 0) {
                final int light = Math.min(15, Math.max(0, lightLevel));
                props = props.lightLevel(state -> light);
            }

            if (friction != null) {
                props = props.friction(friction);
            }
            if (speedFactor != null) {
                props = props.speedFactor(speedFactor);
            }
            if (jumpFactor != null) {
                props = props.jumpFactor(jumpFactor);
            }

            if (soundType != null) {
                props = props.sound(parseSoundType(soundType));
            }

            if (noOcclusion != null && noOcclusion) {
                props = props.noOcclusion();
            }
            if (noCollision != null && noCollision) {
                props = props.noCollision();
            }

            return props;
        }
    }

    // ==================== Codec Definitions ====================

    public static final Codec<BlockProperties> BLOCK_PROPERTIES_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.FLOAT.optionalFieldOf("hardness").forGetter(p -> Optional.ofNullable(p.hardness)),
                    Codec.FLOAT.optionalFieldOf("resistance").forGetter(p -> Optional.ofNullable(p.resistance)),
                    Codec.BOOL.optionalFieldOf("requires_correct_tool").forGetter(p -> Optional.ofNullable(p.requiresCorrectTool)),
                    Codec.INT.optionalFieldOf("light_level").forGetter(p -> Optional.ofNullable(p.lightLevel)),
                    Codec.FLOAT.optionalFieldOf("friction").forGetter(p -> Optional.ofNullable(p.friction)),
                    Codec.FLOAT.optionalFieldOf("speed_factor").forGetter(p -> Optional.ofNullable(p.speedFactor)),
                    Codec.FLOAT.optionalFieldOf("jump_factor").forGetter(p -> Optional.ofNullable(p.jumpFactor)),
                    Codec.STRING.optionalFieldOf("sound_type").forGetter(p -> Optional.ofNullable(p.soundType)),
                    Codec.BOOL.optionalFieldOf("no_occlusion").forGetter(p -> Optional.ofNullable(p.noOcclusion)),
                    Codec.BOOL.optionalFieldOf("no_collision").forGetter(p -> Optional.ofNullable(p.noCollision))
            ).apply(instance, (hardness, resistance, tool, light, friction, speed, jump, sound, occlusion, collision) -> {
                var props = new BlockProperties();
                props.hardness = hardness.orElse(null);
                props.resistance = resistance.orElse(null);
                props.requiresCorrectTool = tool.orElse(null);
                props.lightLevel = light.orElse(null);
                props.friction = friction.orElse(null);
                props.speedFactor = speed.orElse(null);
                props.jumpFactor = jump.orElse(null);
                props.soundType = sound.orElse(null);
                props.noOcclusion = occlusion.orElse(null);
                props.noCollision = collision.orElse(null);
                return props;
            })
    );

    // Note: We use Gson directly for serialization instead of Codec for simplicity
    // JsonObject components are serialized/deserialized directly by Gson

    // ==================== Helper Methods ====================

    private static SoundType parseSoundType(String soundType) {
        if (soundType == null) return SoundType.STONE;
        return switch (soundType.toLowerCase()) {
            case "wood" -> SoundType.WOOD;
            case "gravel" -> SoundType.GRAVEL;
            case "grass" -> SoundType.GRASS;
            case "metal" -> SoundType.METAL;
            case "glass" -> SoundType.GLASS;
            case "wool" -> SoundType.WOOL;
            case "sand" -> SoundType.SAND;
            case "snow" -> SoundType.SNOW;
            case "chain" -> SoundType.CHAIN;
            case "anvil" -> SoundType.ANVIL;
            case "slime" -> SoundType.SLIME_BLOCK;
            case "honey" -> SoundType.HONEY_BLOCK;
            case "coral" -> SoundType.CORAL_BLOCK;
            case "bamboo" -> SoundType.BAMBOO;
            case "nether_wood", "crimson", "warped" -> SoundType.NETHER_WOOD;
            case "netherite" -> SoundType.NETHERITE_BLOCK;
            case "ancient_debris" -> SoundType.ANCIENT_DEBRIS;
            case "bone" -> SoundType.BONE_BLOCK;
            case "nether_ore" -> SoundType.NETHER_ORE;
            case "nether_bricks" -> SoundType.NETHER_BRICKS;
            case "nether_gold_ore" -> SoundType.NETHER_GOLD_ORE;
            case "deepslate" -> SoundType.DEEPSLATE;
            case "deepslate_bricks" -> SoundType.DEEPSLATE_BRICKS;
            case "deepslate_tiles" -> SoundType.DEEPSLATE_TILES;
            case "copper" -> SoundType.COPPER;
            case "amethyst" -> SoundType.AMETHYST;
            case "amethyst_cluster" -> SoundType.AMETHYST_CLUSTER;
            default -> SoundType.STONE;
        };
    }

    // ==================== Initialization ====================

    public static void initialize() {
        if (initialized) return;

        registryPath = FabricLoader.getInstance().getConfigDir()
                .resolve(NotEnoughRecipes.MOD_ID)
                .resolve(REGISTRY_FOLDER);

        try {
            Files.createDirectories(registryPath);
            NotEnoughRecipes.LOGGER.info("Dynamic registry persistence initialized at: {}", registryPath);
            initialized = true;

            // Create example files if they don't exist
            createExampleFiles();
        } catch (IOException e) {
            NotEnoughRecipes.LOGGER.error("Failed to create registry persistence directory", e);
        }
    }

    /**
     * Creates example items.json and blocks.json with documentation.
     */
    private static void createExampleFiles() {
        Path itemsFile = registryPath.resolve(ITEMS_FILE);
        Path blocksFile = registryPath.resolve(BLOCKS_FILE);

        // Only create if files don't exist
        if (!Files.exists(itemsFile)) {
            List<ItemDefinition> exampleItems = createExampleItems();
            saveItemDefinitions(exampleItems);
            NotEnoughRecipes.LOGGER.info("Created example items.json");
        }

        if (!Files.exists(blocksFile)) {
            List<BlockDefinition> exampleBlocks = createExampleBlocks();
            saveBlockDefinitions(exampleBlocks);
            NotEnoughRecipes.LOGGER.info("Created example blocks.json");
        }
    }

    private static List<ItemDefinition> createExampleItems() {
        List<ItemDefinition> items = new ArrayList<>();

        // Example golden apple with components
        ItemDefinition goldenApple = new ItemDefinition();
        goldenApple.id = "golden_apple";
        goldenApple.texture = "golden_apple";
        goldenApple.components = new JsonObject();
        goldenApple.components.add("custom_name", GSON.fromJson("{\"text\":\"Golden Apple of Power\",\"color\":\"gold\"}", JsonObject.class));
        goldenApple.components.add("lore", GSON.fromJson("[{\"text\":\"A mystical golden apple\",\"italic\":false},{\"text\":\"Grants great power to the eater\",\"italic\":false}]", JsonElement.class));
        goldenApple.components.addProperty("enchantment_glint_override", true);
        goldenApple.components.addProperty("max_stack_size", 16);
        goldenApple.components.addProperty("rarity", "rare");
        
        // Add consumable component (REQUIRED in 1.21.11+ for items to be edible)
        JsonObject consumable = new JsonObject();
        consumable.addProperty("consume_seconds", 1.6);
        consumable.addProperty("animation", "eat");
        consumable.addProperty("has_consume_particles", true);
        goldenApple.components.add("consumable", consumable);
        
        // Add food component (nutrition stats)
        JsonObject food = new JsonObject();
        food.addProperty("nutrition", 8);
        food.addProperty("saturation", 1.2);
        food.addProperty("can_always_eat", true);
        goldenApple.components.add("food", food);
        items.add(goldenApple);

        return items;
    }

    private static List<BlockDefinition> createExampleBlocks() {
        List<BlockDefinition> blocks = new ArrayList<>();

        // Example gold ore with custom properties
        BlockDefinition goldOre = new BlockDefinition();
        goldOre.id = "rich_gold_ore";
        goldOre.texture = "gold_ore";
        goldOre.properties = new BlockProperties();
        goldOre.properties.hardness = 3.0f;
        goldOre.properties.resistance = 3.0f;
        goldOre.properties.requiresCorrectTool = true;
        goldOre.properties.soundType = "stone";
        goldOre.properties.lightLevel = 5;
        goldOre.components = new JsonObject();
        goldOre.components.add("custom_name", GSON.fromJson("{\"text\":\"Rich Gold Ore\",\"color\":\"gold\"}", JsonObject.class));
        goldOre.components.add("lore", GSON.fromJson("[{\"text\":\"A particularly rich vein of gold\",\"italic\":false},{\"text\":\"+50% gold yield when smelted\",\"color\":\"gold\"}]", JsonElement.class));
        goldOre.components.addProperty("rarity", "uncommon");
        blocks.add(goldOre);

        return blocks;
    }

    // ==================== Save/Load Methods ====================

    public static void saveItemDefinition(String id, String textureName) {
        saveItemDefinition(new ItemDefinition(id, textureName));
    }

    public static void saveItemDefinition(ItemDefinition definition) {
        initialize();

        List<ItemDefinition> items = loadItemDefinitions();

        // Update or add
        boolean found = false;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id.equals(definition.id)) {
                items.set(i, definition);
                found = true;
                break;
            }
        }
        if (!found) {
            items.add(definition);
        }

        saveItemDefinitions(items);
        NotEnoughRecipes.LOGGER.info("Saved item definition: {}", definition.id);
    }

    public static void saveBlockDefinition(String id, String textureName) {
        saveBlockDefinition(new BlockDefinition(id, textureName));
    }

    public static void saveBlockDefinition(BlockDefinition definition) {
        initialize();

        List<BlockDefinition> blocks = loadBlockDefinitions();

        // Update or add
        boolean found = false;
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).id.equals(definition.id)) {
                blocks.set(i, definition);
                found = true;
                break;
            }
        }
        if (!found) {
            blocks.add(definition);
        }

        saveBlockDefinitions(blocks);
        NotEnoughRecipes.LOGGER.info("Saved block definition: {}", definition.id);
    }

    public static List<ItemDefinition> loadItemDefinitions() {
        initialize();

        Path itemsFile = registryPath.resolve(ITEMS_FILE);

        if (!Files.exists(itemsFile)) {
            return new ArrayList<>();
        }

        try {
            String json = Files.readString(itemsFile);
            JsonElement element = JsonParser.parseString(json);
            
            List<ItemDefinition> items = new ArrayList<>();
            if (element.isJsonArray()) {
                for (JsonElement itemElem : element.getAsJsonArray()) {
                    JsonObject itemObj = itemElem.getAsJsonObject();
                    ItemDefinition item = new ItemDefinition();
                    item.id = itemObj.get("id").getAsString();
                    item.texture = itemObj.get("texture").getAsString();
                    item.components = itemObj.has("components") ? itemObj.getAsJsonObject("components") : new JsonObject();
                    // Parse tags
                    if (itemObj.has("tags") && itemObj.get("tags").isJsonArray()) {
                        item.tags = new ArrayList<>();
                        for (JsonElement tagElem : itemObj.getAsJsonArray("tags")) {
                            item.tags.add(tagElem.getAsString());
                        }
                    } else {
                        item.tags = new ArrayList<>();
                    }
                    items.add(item);
                }
            }
            return items;
        } catch (IOException e) {
            NotEnoughRecipes.LOGGER.error("Failed to load item definitions", e);
            return new ArrayList<>();
        } catch (Exception e) {
            NotEnoughRecipes.LOGGER.error("Failed to parse item definitions", e);
            return new ArrayList<>();
        }
    }

    public static List<BlockDefinition> loadBlockDefinitions() {
        initialize();

        Path blocksFile = registryPath.resolve(BLOCKS_FILE);

        if (!Files.exists(blocksFile)) {
            return new ArrayList<>();
        }

        try {
            String json = Files.readString(blocksFile);
            JsonElement element = JsonParser.parseString(json);
            
            List<BlockDefinition> blocks = new ArrayList<>();
            if (element.isJsonArray()) {
                for (JsonElement blockElem : element.getAsJsonArray()) {
                    JsonObject blockObj = blockElem.getAsJsonObject();
                    BlockDefinition block = new BlockDefinition();
                    block.id = blockObj.get("id").getAsString();
                    block.texture = blockObj.get("texture").getAsString();
                    
                    // Load block properties
                    if (blockObj.has("properties")) {
                        var propsResult = BLOCK_PROPERTIES_CODEC.parse(JsonOps.INSTANCE, blockObj.get("properties"));
                        if (propsResult.result().isPresent()) {
                            block.properties = propsResult.result().get();
                        }
                    }
                    
                    // Load components
                    block.components = blockObj.has("components") ? blockObj.getAsJsonObject("components") : new JsonObject();
                    // Parse tags
                    if (blockObj.has("tags") && blockObj.get("tags").isJsonArray()) {
                        block.tags = new ArrayList<>();
                        for (JsonElement tagElem : blockObj.getAsJsonArray("tags")) {
                            block.tags.add(tagElem.getAsString());
                        }
                    } else {
                        block.tags = new ArrayList<>();
                    }
                    // Parse drops
                    if (blockObj.has("drops") && blockObj.get("drops").isJsonArray()) {
                        block.drops = blockObj.getAsJsonArray("drops");
                    }
                    blocks.add(block);
                }
            }
            return blocks;
        } catch (IOException e) {
            NotEnoughRecipes.LOGGER.error("Failed to load block definitions", e);
            return new ArrayList<>();
        } catch (Exception e) {
            NotEnoughRecipes.LOGGER.error("Failed to parse block definitions", e);
            return new ArrayList<>();
        }
    }

    private static void saveItemDefinitions(List<ItemDefinition> items) {
        Path itemsFile = registryPath.resolve(ITEMS_FILE);

        try {
            String json = GSON.toJson(items);
            Files.writeString(itemsFile, json);
        } catch (IOException e) {
            NotEnoughRecipes.LOGGER.error("Failed to save item definitions", e);
        }
    }

    private static void saveBlockDefinitions(List<BlockDefinition> blocks) {
        Path blocksFile = registryPath.resolve(BLOCKS_FILE);

        try {
            String json = GSON.toJson(blocks);
            Files.writeString(blocksFile, json);
        } catch (IOException e) {
            NotEnoughRecipes.LOGGER.error("Failed to save block definitions", e);
        }
    }

    // ==================== Registration Integration ====================

    /**
     * Load and register all persisted items and blocks.
     * Should be called during mod initialization.
     */
    public static void loadAndRegisterAll() {
        initialize();

        NotEnoughRecipes.LOGGER.info("Loading persisted dynamic registry entries...");

        // First, initialize the resource loader
        DynamicResourceLoader.initialize();

        // Load and register items
        List<ItemDefinition> items = loadItemDefinitions();
        int itemCount = 0;
        for (ItemDefinition item : items) {
            try {
                NotEnoughRecipes.LOGGER.info("Restoring item: {} with texture {}", item.id, item.texture);
                DynamicRegistryHelper.registerDynamicItemFromDefinition(item);
                itemCount++;
            } catch (Exception e) {
                NotEnoughRecipes.LOGGER.error("Failed to restore item: {}", item.id, e);
            }
        }

        // Load and register blocks
        List<BlockDefinition> blocks = loadBlockDefinitions();
        int blockCount = 0;
        for (BlockDefinition block : blocks) {
            try {
                NotEnoughRecipes.LOGGER.info("Restoring block: {} with texture {}", block.id, block.texture);
                DynamicRegistryHelper.registerDynamicBlockFromDefinition(block);
                blockCount++;
            } catch (Exception e) {
                NotEnoughRecipes.LOGGER.error("Failed to restore block: {}", block.id, e);
            }
        }

        NotEnoughRecipes.LOGGER.info("Restored {} items and {} blocks from persistent storage", itemCount, blockCount);
        
        // Rebuild resource pack and trigger reload so textures are visible immediately
        if (itemCount > 0 || blockCount > 0) {
            try {
                NotEnoughRecipes.LOGGER.info("Rebuilding resource pack and triggering resource reload...");
                DynamicResourceLoader.getResourcePack().rebuild();
                DynamicResourceLoader.triggerResourceReload();
                NotEnoughRecipes.LOGGER.info("Resource reload completed - textures should now be visible");
            } catch (Exception e) {
                NotEnoughRecipes.LOGGER.warn("Failed to trigger resource reload at startup: {}", e.getMessage());
                NotEnoughRecipes.LOGGER.warn("Textures may not be visible until /ner resource reload is run");
            }
        }
    }

    // ==================== Utility Methods ====================

    public static boolean removeItemDefinition(String id) {
        initialize();

        List<ItemDefinition> items = loadItemDefinitions();
        boolean removed = items.removeIf(item -> item.id.equals(id));

        if (removed) {
            saveItemDefinitions(items);
            NotEnoughRecipes.LOGGER.info("Removed item definition: {}", id);
        }

        return removed;
    }

    public static boolean removeBlockDefinition(String id) {
        initialize();

        List<BlockDefinition> blocks = loadBlockDefinitions();
        boolean removed = blocks.removeIf(block -> block.id.equals(id));

        if (removed) {
            saveBlockDefinitions(blocks);
            NotEnoughRecipes.LOGGER.info("Removed block definition: {}", id);
        }

        return removed;
    }

    public static void clearAll() {
        initialize();

        saveItemDefinitions(new ArrayList<>());
        saveBlockDefinitions(new ArrayList<>());
        NotEnoughRecipes.LOGGER.info("Cleared all persisted definitions");
    }

    public static Path getRegistryPath() {
        initialize();
        return registryPath;
    }

    public static String getStats() {
        List<ItemDefinition> items = loadItemDefinitions();
        List<BlockDefinition> blocks = loadBlockDefinitions();

        return String.format("Persisted: %d items, %d blocks\nPath: %s",
                items.size(), blocks.size(), registryPath);
    }
}
