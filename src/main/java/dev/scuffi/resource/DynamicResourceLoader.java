package dev.scuffi.resource;

import dev.scuffi.NotEnoughRecipes;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages dynamic resource loading at runtime.
 * Resources are loaded from: config/not-enough-recipes/dynamic/
 * 
 * Folder structure:
 *   dynamic/
 *     textures/
 *       item/     - Item textures (16x16 PNG files)
 *       block/    - Block textures (16x16 PNG files)
 *     models/
 *       item/     - Item models (JSON)
 *       block/    - Block models (JSON)
 */
public class DynamicResourceLoader {
    
    private static final String DYNAMIC_FOLDER = "dynamic";
    private static final String TEXTURES_FOLDER = "textures";
    private static final String MODELS_FOLDER = "models";
    
    private static Path dynamicResourcePath;
    private static final Map<Identifier, byte[]> dynamicTextures = new ConcurrentHashMap<>();
    private static final Map<Identifier, String> dynamicModels = new ConcurrentHashMap<>();
    private static final Set<String> loadedResources = ConcurrentHashMap.newKeySet();
    
    private static DynamicResourcePack resourcePack;
    private static boolean initialized = false;
    
    /**
     * Initialize the dynamic resource system.
     * Creates necessary folders and sets up the resource pack.
     */
    public static void initialize() {
        if (initialized) {
            return;
        }
        
        // Get the config directory
        Path configDir = FabricLoader.getInstance().getConfigDir();
        dynamicResourcePath = configDir.resolve(NotEnoughRecipes.MOD_ID).resolve(DYNAMIC_FOLDER);
        
        // Create folder structure
        try {
            Files.createDirectories(dynamicResourcePath.resolve(TEXTURES_FOLDER).resolve("item"));
            Files.createDirectories(dynamicResourcePath.resolve(TEXTURES_FOLDER).resolve("block"));
            Files.createDirectories(dynamicResourcePath.resolve(MODELS_FOLDER).resolve("item"));
            Files.createDirectories(dynamicResourcePath.resolve(MODELS_FOLDER).resolve("block"));
            
            NotEnoughRecipes.LOGGER.info("Dynamic resource folder created at: {}", dynamicResourcePath);
            
            // Create a readme file to help users
            Path readmePath = dynamicResourcePath.resolve("README.txt");
            if (!Files.exists(readmePath)) {
                String readme = """
                    Dynamic Resource Loader - Not Enough Recipes
                    =============================================
                    
                    Place your dynamic resources in the following folders:
                    
                    textures/item/   - Item textures (16x16 PNG files)
                    textures/block/  - Block textures (16x16 PNG files)
                    models/item/     - Item model JSON files
                    models/block/    - Block model JSON files
                    
                    File naming:
                    - Use lowercase names with underscores (e.g., my_custom_item.png)
                    - The filename (without extension) becomes the resource ID
                    
                    Example:
                    - textures/item/ruby.png -> not-enough-recipes:item/ruby
                    
                    Use the /dynreg command in-game to load and test resources.
                    """;
                Files.writeString(readmePath, readme);
            }
            
        } catch (IOException e) {
            NotEnoughRecipes.LOGGER.error("Failed to create dynamic resource folders", e);
        }
        
        // Create our dynamic resource pack
        resourcePack = new DynamicResourcePack();
        
        // Load static GUI textures from the mod jar into the dynamic pack
        // This ensures they're served by the dynamic pack and not blocked by resource loading issues
        loadStaticGuiTextures();
        
        initialized = true;
        NotEnoughRecipes.LOGGER.info("Dynamic resource loader initialized");
    }
    
    /**
     * Load static GUI textures from the mod jar into the dynamic pack.
     * This works around issues with resource pack loading order.
     */
    private static void loadStaticGuiTextures() {
        try {
            // Load creation_altar.png from the mod's resources
            String resourcePath = "/assets/" + NotEnoughRecipes.MOD_ID + "/textures/gui/creation_altar.png";
            try (var inputStream = DynamicResourceLoader.class.getResourceAsStream(resourcePath)) {
                if (inputStream != null) {
                    byte[] textureData = inputStream.readAllBytes();
                    String packPath = "assets/" + NotEnoughRecipes.MOD_ID + "/textures/gui/creation_altar.png";
                    resourcePack.addRawResource(packPath, textureData);
                    NotEnoughRecipes.LOGGER.info("Loaded static GUI texture: creation_altar.png ({} bytes)", textureData.length);
                } else {
                    NotEnoughRecipes.LOGGER.warn("Could not find static GUI texture: {}", resourcePath);
                }
            }
        } catch (IOException e) {
            NotEnoughRecipes.LOGGER.error("Failed to load static GUI textures", e);
        }
    }
    
    /**
     * Get the path to the dynamic resources folder.
     */
    public static Path getDynamicResourcePath() {
        if (!initialized) {
            initialize();
        }
        return dynamicResourcePath;
    }
    
    /**
     * Load a texture from the dynamic folder.
     * @param type The type of texture ("item" or "block")
     * @param name The name of the texture file (without .png extension)
     * @return true if loaded successfully
     */
    public static boolean loadTexture(String type, String name) {
        if (!initialized) {
            initialize();
        }
        
        Path texturePath = dynamicResourcePath.resolve(TEXTURES_FOLDER).resolve(type).resolve(name + ".png");
        
        if (!Files.exists(texturePath)) {
            NotEnoughRecipes.LOGGER.warn("Texture file not found: {}", texturePath);
            return false;
        }
        
        try {
            byte[] textureData = Files.readAllBytes(texturePath);
            Identifier textureId = Identifier.parse(NotEnoughRecipes.MOD_ID + ":" + type + "/" + name);
            
            dynamicTextures.put(textureId, textureData);
            loadedResources.add("texture:" + type + "/" + name);
            
            NotEnoughRecipes.LOGGER.info("Loaded dynamic texture: {} ({} bytes)", textureId, textureData.length);
            return true;
            
        } catch (IOException e) {
            NotEnoughRecipes.LOGGER.error("Failed to load texture: {}", texturePath, e);
            return false;
        }
    }
    
    /**
     * Load a model from the dynamic folder.
     * @param type The type of model ("item" or "block")
     * @param name The name of the model file (without .json extension)
     * @return true if loaded successfully
     */
    public static boolean loadModel(String type, String name) {
        if (!initialized) {
            initialize();
        }
        
        Path modelPath = dynamicResourcePath.resolve(MODELS_FOLDER).resolve(type).resolve(name + ".json");
        
        if (!Files.exists(modelPath)) {
            // Generate a simple default model if no custom model exists
            String defaultModel = generateDefaultModel(type, name);
            dynamicModels.put(
                Identifier.parse(NotEnoughRecipes.MOD_ID + ":" + type + "/" + name),
                defaultModel
            );
            loadedResources.add("model:" + type + "/" + name);
            NotEnoughRecipes.LOGGER.info("Generated default model for: {}/{}", type, name);
            return true;
        }
        
        try {
            String modelJson = Files.readString(modelPath);
            Identifier modelId = Identifier.parse(NotEnoughRecipes.MOD_ID + ":" + type + "/" + name);
            
            dynamicModels.put(modelId, modelJson);
            loadedResources.add("model:" + type + "/" + name);
            
            NotEnoughRecipes.LOGGER.info("Loaded dynamic model: {}", modelId);
            return true;
            
        } catch (IOException e) {
            NotEnoughRecipes.LOGGER.error("Failed to load model: {}", modelPath, e);
            return false;
        }
    }
    
    /**
     * Generate a simple default item model JSON.
     */
    private static String generateDefaultModel(String type, String name) {
        if ("item".equals(type)) {
            return String.format("""
                {
                    "parent": "minecraft:item/generated",
                    "textures": {
                        "layer0": "%s:item/%s"
                    }
                }
                """, NotEnoughRecipes.MOD_ID, name);
        } else if ("block".equals(type)) {
            return String.format("""
                {
                    "parent": "minecraft:block/cube_all",
                    "textures": {
                        "all": "%s:block/%s"
                    }
                }
                """, NotEnoughRecipes.MOD_ID, name);
        }
        return "{}";
    }
    
    /**
     * Add a custom item model that uses a specific texture.
     * This allows creating an item with a different texture than its name.
     * 
     * @param itemName The item's registry name (e.g., "ruby_sword")
     * @param textureName The texture to use (e.g., "ruby" will use textures/item/ruby.png)
     */
    public static void addCustomItemModel(String itemName, String textureName) {
        if (!initialized) {
            initialize();
        }
        
        // Check if we have this texture loaded - if so, use our texture
        // Otherwise fall back to a vanilla texture for testing
        String textureReference;
        if (isTextureLoaded("item", textureName)) {
            textureReference = NotEnoughRecipes.MOD_ID + ":item/" + textureName;
        } else {
            // Fall back to vanilla diamond as a test
            textureReference = "minecraft:item/diamond";
            NotEnoughRecipes.LOGGER.warn("Texture '{}' not loaded, falling back to vanilla diamond texture", textureName);
        }
        
        // Create the model JSON (traditional model format)
        String modelJson = String.format("""
            {
                "parent": "minecraft:item/generated",
                "textures": {
                    "layer0": "%s"
                }
            }
            """, textureReference);
        
        Identifier modelId = Identifier.parse(NotEnoughRecipes.MOD_ID + ":item/" + itemName);
        dynamicModels.put(modelId, modelJson);
        loadedResources.add("model:item/" + itemName);
        
        // IMPORTANT: In Minecraft 1.21+, we also need an item definition file
        // This goes in assets/<namespace>/items/<item_name>.json
        // It tells Minecraft which model to use for the item
        String itemDefinitionJson = String.format("""
            {
                "model": {
                    "type": "minecraft:model",
                    "model": "%s:item/%s"
                }
            }
            """, NotEnoughRecipes.MOD_ID, itemName);
        
        Identifier itemDefId = Identifier.parse(NotEnoughRecipes.MOD_ID + ":items/" + itemName);
        dynamicModels.put(itemDefId, itemDefinitionJson);
        loadedResources.add("itemdef:" + itemName);
        
        NotEnoughRecipes.LOGGER.info("Created custom model for item '{}' using texture reference '{}'", itemName, textureReference);
        NotEnoughRecipes.LOGGER.info("Created item definition for '{}'", itemName);
    }
    
    /**
     * Add a custom block model that uses a specific texture.
     * Generates all required files for a block in Minecraft 1.21+:
     * - Block model (models/block/<name>.json)
     * - Item model (models/item/<name>.json)
     * - Blockstate definition (blockstates/<name>.json)
     * - Item definition (items/<name>.json)
     * 
     * @param blockName The block's registry name
     * @param textureName The texture to use
     */
    public static void addCustomBlockModel(String blockName, String textureName) {
        if (!initialized) {
            initialize();
        }
        
        // Check if we have this texture loaded - if so, use our texture
        String textureReference;
        if (isTextureLoaded("block", textureName)) {
            textureReference = NotEnoughRecipes.MOD_ID + ":block/" + textureName;
        } else {
            // Fall back to vanilla stone as a test
            textureReference = "minecraft:block/stone";
            NotEnoughRecipes.LOGGER.warn("Block texture '{}' not loaded, falling back to vanilla stone texture", textureName);
        }
        
        // 1. Block model - for when the block is placed in the world
        String blockModelJson = String.format("""
            {
                "parent": "minecraft:block/cube_all",
                "textures": {
                    "all": "%s"
                }
            }
            """, textureReference);
        
        Identifier blockModelId = Identifier.parse(NotEnoughRecipes.MOD_ID + ":block/" + blockName);
        dynamicModels.put(blockModelId, blockModelJson);
        loadedResources.add("model:block/" + blockName);
        
        // 2. Item model - for when the block is held in hand/inventory
        String itemModelJson = String.format("""
            {
                "parent": "%s:block/%s"
            }
            """, NotEnoughRecipes.MOD_ID, blockName);
        
        Identifier itemModelId = Identifier.parse(NotEnoughRecipes.MOD_ID + ":item/" + blockName);
        dynamicModels.put(itemModelId, itemModelJson);
        loadedResources.add("model:item/" + blockName);
        
        // 3. Blockstate definition - tells Minecraft which model to use for each block state
        String blockstateJson = String.format("""
            {
                "variants": {
                    "": {
                        "model": "%s:block/%s"
                    }
                }
            }
            """, NotEnoughRecipes.MOD_ID, blockName);
        
        Identifier blockstateId = Identifier.parse(NotEnoughRecipes.MOD_ID + ":blockstates/" + blockName);
        dynamicModels.put(blockstateId, blockstateJson);
        loadedResources.add("blockstate:" + blockName);
        
        // 4. Item definition for the block item (required in Minecraft 1.21+)
        String itemDefinitionJson = String.format("""
            {
                "model": {
                    "type": "minecraft:model",
                    "model": "%s:item/%s"
                }
            }
            """, NotEnoughRecipes.MOD_ID, blockName);
        
        Identifier itemDefId = Identifier.parse(NotEnoughRecipes.MOD_ID + ":items/" + blockName);
        dynamicModels.put(itemDefId, itemDefinitionJson);
        loadedResources.add("itemdef:" + blockName);
        
        NotEnoughRecipes.LOGGER.info("Created block resources for '{}' using texture '{}'", blockName, textureName);
        NotEnoughRecipes.LOGGER.info("  - Block model: {}", blockModelId);
        NotEnoughRecipes.LOGGER.info("  - Item model: {}", itemModelId);
        NotEnoughRecipes.LOGGER.info("  - Blockstate: {}", blockstateId);
        NotEnoughRecipes.LOGGER.info("  - Item definition: {}", itemDefId);
    }
    
    /**
     * Check if a texture file exists in the dynamic folder.
     * 
     * @param type The type ("item" or "block")
     * @param name The texture name (without .png)
     * @return true if the file exists
     */
    public static boolean textureFileExists(String type, String name) {
        if (!initialized) {
            initialize();
        }
        Path texturePath = dynamicResourcePath.resolve(TEXTURES_FOLDER).resolve(type).resolve(name + ".png");
        return Files.exists(texturePath);
    }
    
    /**
     * Load all resources from the dynamic folder.
     * @return number of resources loaded
     */
    public static int loadAllResources() {
        if (!initialized) {
            initialize();
        }
        
        int count = 0;
        
        // Load all item textures
        count += loadAllFromFolder(TEXTURES_FOLDER + "/item", "item", ".png", true);
        
        // Load all block textures  
        count += loadAllFromFolder(TEXTURES_FOLDER + "/block", "block", ".png", true);
        
        // Load all item models
        count += loadAllFromFolder(MODELS_FOLDER + "/item", "item", ".json", false);
        
        // Load all block models
        count += loadAllFromFolder(MODELS_FOLDER + "/block", "block", ".json", false);
        
        return count;
    }
    
    private static int loadAllFromFolder(String folder, String type, String extension, boolean isTexture) {
        Path folderPath = dynamicResourcePath.resolve(folder.replace("/", java.io.File.separator));
        
        if (!Files.exists(folderPath)) {
            return 0;
        }
        
        int count = 0;
        try (var stream = Files.list(folderPath)) {
            var files = stream
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(extension))
                .toList();
                
            for (Path file : files) {
                String fileName = file.getFileName().toString();
                String name = fileName.substring(0, fileName.length() - extension.length());
                
                boolean success = isTexture ? loadTexture(type, name) : loadModel(type, name);
                if (success) {
                    count++;
                }
            }
        } catch (IOException e) {
            NotEnoughRecipes.LOGGER.error("Failed to list files in: {}", folderPath, e);
        }
        
        return count;
    }
    
    /**
     * Get all loaded dynamic textures.
     */
    public static Map<Identifier, byte[]> getDynamicTextures() {
        return Collections.unmodifiableMap(dynamicTextures);
    }
    
    /**
     * Get all loaded dynamic models.
     */
    public static Map<Identifier, String> getDynamicModels() {
        return Collections.unmodifiableMap(dynamicModels);
    }
    
    /**
     * Get the dynamic resource pack instance.
     */
    public static DynamicResourcePack getResourcePack() {
        if (!initialized) {
            initialize();
        }
        return resourcePack;
    }
    
    /**
     * Check if a texture is loaded.
     */
    public static boolean isTextureLoaded(String type, String name) {
        return loadedResources.contains("texture:" + type + "/" + name);
    }
    
    /**
     * Check if a model is loaded.
     */
    public static boolean isModelLoaded(String type, String name) {
        return loadedResources.contains("model:" + type + "/" + name);
    }
    
    /**
     * Get statistics about loaded resources.
     */
    public static String getStats() {
        return String.format(
            "Dynamic Resources: %d textures, %d models loaded\nPath: %s",
            dynamicTextures.size(),
            dynamicModels.size(),
            dynamicResourcePath != null ? dynamicResourcePath.toString() : "not initialized"
        );
    }
    
    /**
     * List all available resource files (not yet loaded).
     */
    public static List<String> listAvailableResources() {
        if (!initialized) {
            initialize();
        }
        
        List<String> available = new ArrayList<>();
        
        // Check textures/item
        listFilesInFolder(dynamicResourcePath.resolve(TEXTURES_FOLDER).resolve("item"), ".png")
            .forEach(name -> available.add("textures/item/" + name));
            
        // Check textures/block
        listFilesInFolder(dynamicResourcePath.resolve(TEXTURES_FOLDER).resolve("block"), ".png")
            .forEach(name -> available.add("textures/block/" + name));
            
        // Check models/item
        listFilesInFolder(dynamicResourcePath.resolve(MODELS_FOLDER).resolve("item"), ".json")
            .forEach(name -> available.add("models/item/" + name));
            
        // Check models/block
        listFilesInFolder(dynamicResourcePath.resolve(MODELS_FOLDER).resolve("block"), ".json")
            .forEach(name -> available.add("models/block/" + name));
        
        return available;
    }
    
    private static List<String> listFilesInFolder(Path folder, String extension) {
        if (!Files.exists(folder)) {
            return Collections.emptyList();
        }
        
        try (var stream = Files.list(folder)) {
            return stream
                .filter(Files::isRegularFile)
                .map(p -> p.getFileName().toString())
                .filter(name -> name.endsWith(extension))
                .toList();
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }
    
    /**
     * Trigger a resource reload to apply dynamic resources.
     * This should be called on the client after loading new resources.
     */
    public static void triggerResourceReload() {
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            // Schedule reload on render thread
            client.execute(() -> {
                NotEnoughRecipes.LOGGER.info("Triggering resource reload for dynamic resources...");
                client.reloadResourcePacks();
            });
        }
    }
}
