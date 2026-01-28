package dev.scuffi.resource;

import dev.scuffi.NotEnoughRecipes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.Optional;

/**
 * A virtual resource pack that provides dynamically loaded resources.
 * This pack injects textures and models loaded at runtime into Minecraft's resource system.
 */
public class DynamicResourcePack implements PackResources {
    
    private static final String PACK_ID = NotEnoughRecipes.MOD_ID + "_dynamic";
    
    // Maps resource paths to their data
    private final Map<String, byte[]> resources = new HashMap<>();
    
    // Static resources that should persist across rebuilds (like GUI textures)
    private final Map<String, byte[]> staticResources = new HashMap<>();
    
    public DynamicResourcePack() {
        NotEnoughRecipes.LOGGER.info("Created dynamic resource pack: {}", PACK_ID);
    }
    
    /**
     * Add a texture resource to the pack.
     */
    public void addTexture(String namespace, String path, byte[] data) {
        String fullPath = "assets/" + namespace + "/textures/" + path + ".png";
        resources.put(fullPath, data);
        NotEnoughRecipes.LOGGER.debug("Added texture to dynamic pack: {}", fullPath);
    }
    
    /**
     * Add a raw resource to the pack (for loading complete paths like GUI textures).
     * These resources persist across rebuilds.
     */
    public void addRawResource(String fullPath, byte[] data) {
        staticResources.put(fullPath, data);
        resources.put(fullPath, data);
        NotEnoughRecipes.LOGGER.info("Added static resource to dynamic pack: {} ({} bytes)", fullPath, data.length);
    }
    
    /**
     * Add a model resource to the pack.
     */
    public void addModel(String namespace, String path, String json) {
        String fullPath = "assets/" + namespace + "/models/" + path + ".json";
        resources.put(fullPath, json.getBytes());
        NotEnoughRecipes.LOGGER.debug("Added model to dynamic pack: {}", fullPath);
    }
    
    /**
     * Add an item definition resource to the pack.
     * In Minecraft 1.21+, items need a definition file at assets/<namespace>/items/<name>.json
     */
    public void addItemDefinition(String namespace, String itemName, String json) {
        String fullPath = "assets/" + namespace + "/items/" + itemName + ".json";
        resources.put(fullPath, json.getBytes());
        NotEnoughRecipes.LOGGER.debug("Added item definition to dynamic pack: {}", fullPath);
    }
    
    /**
     * Add a blockstate definition resource to the pack.
     * Blockstates tell Minecraft which model to use for each block state.
     */
    public void addBlockstate(String namespace, String blockName, String json) {
        String fullPath = "assets/" + namespace + "/blockstates/" + blockName + ".json";
        resources.put(fullPath, json.getBytes());
        NotEnoughRecipes.LOGGER.debug("Added blockstate to dynamic pack: {}", fullPath);
    }
    
    /**
     * Rebuild the pack from currently loaded dynamic resources.
     */
    public void rebuild() {
        resources.clear();
        
        // Re-add static resources first (GUI textures, etc)
        resources.putAll(staticResources);
        NotEnoughRecipes.LOGGER.info("Rebuild: Re-added {} static resources", staticResources.size());
        
        // Add all loaded textures
        for (var entry : DynamicResourceLoader.getDynamicTextures().entrySet()) {
            Identifier id = entry.getKey();
            byte[] data = entry.getValue();
            String path = id.getPath(); // e.g., "item/my_texture" or "block/my_texture"
            addTexture(id.getNamespace(), path, data);
        }
        
        // Add all loaded models, item definitions, and blockstates
        for (var entry : DynamicResourceLoader.getDynamicModels().entrySet()) {
            Identifier id = entry.getKey();
            String json = entry.getValue();
            String path = id.getPath(); // e.g., "item/my_model", "items/my_item", "blockstates/my_block"
            
            if (path.startsWith("items/")) {
                // Item definitions go in assets/<namespace>/items/<name>.json
                addItemDefinition(id.getNamespace(), path.substring(6), json); // Remove "items/" prefix
            } else if (path.startsWith("blockstates/")) {
                // Blockstates go in assets/<namespace>/blockstates/<name>.json
                addBlockstate(id.getNamespace(), path.substring(12), json); // Remove "blockstates/" prefix
            } else {
                // Regular models go in assets/<namespace>/models/<path>.json
                addModel(id.getNamespace(), path, json);
            }
        }
        
        NotEnoughRecipes.LOGGER.info("Rebuilt dynamic resource pack with {} resources:", resources.size());
        for (String key : resources.keySet()) {
            NotEnoughRecipes.LOGGER.info("  - {}", key);
        }
    }
    
    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... pathParts) {
        // Handle pack.mcmeta request
        if (pathParts.length == 1 && pathParts[0].equals("pack.mcmeta")) {
            String packMeta = """
                {
                    "pack": {
                        "pack_format": 46,
                        "description": "Dynamic resources for Not Enough Recipes"
                    }
                }
                """;
            return () -> new ByteArrayInputStream(packMeta.getBytes());
        }
        return null;
    }
    
    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier id) {
        if (type != PackType.CLIENT_RESOURCES) {
            return null;
        }
        
        // The Identifier's path already includes the resource type folder (textures/, models/, etc.)
        // So we just need to construct: assets/<namespace>/<path>
        String resourcePath = "assets/" + id.getNamespace() + "/" + id.getPath();
        
        // Log all requests for our namespace to debug
        if (id.getNamespace().equals(NotEnoughRecipes.MOD_ID)) {
            NotEnoughRecipes.LOGGER.info("getResource called: {} -> looking for: {}", id, resourcePath);
            NotEnoughRecipes.LOGGER.info("  Have {} resources: {}", resources.size(), resources.keySet());
        }
        
        if (resources.containsKey(resourcePath)) {
            byte[] data = resources.get(resourcePath);
            NotEnoughRecipes.LOGGER.info("SERVING dynamic resource: {} ({} bytes)", resourcePath, data.length);
            return () -> new ByteArrayInputStream(data);
        }
        
        // Try alternate paths in case there's a mismatch
        // Sometimes requests come in different formats
        for (String key : resources.keySet()) {
            if (key.endsWith("/" + id.getPath()) || key.endsWith(id.getPath())) {
                byte[] data = resources.get(key);
                NotEnoughRecipes.LOGGER.info("SERVING dynamic resource (alternate match): {} -> {} ({} bytes)", 
                    resourcePath, key, data.length);
                return () -> new ByteArrayInputStream(data);
            }
        }
        
        return null;
    }
    
    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES) {
            return;
        }
        
        // Log when this is called for our namespace
        if (namespace.equals(NotEnoughRecipes.MOD_ID)) {
            NotEnoughRecipes.LOGGER.info("listResources called for namespace={}, path={}", namespace, path);
            NotEnoughRecipes.LOGGER.info("  Current resources in pack: {}", resources.keySet());
        }
        
        // path is like "textures/item" or "models/item" or just "models"
        // We need to match resources that are in this path or subdirectories
        String prefix = "assets/" + namespace + "/" + path;
        
        for (Map.Entry<String, byte[]> entry : resources.entrySet()) {
            String resourcePath = entry.getKey();
            // Check if resource starts with the prefix (path might or might not have trailing /)
            if (resourcePath.startsWith(prefix + "/") || resourcePath.startsWith(prefix)) {
                // Make sure we're in the right directory (not a partial match)
                String afterPrefix = resourcePath.substring(("assets/" + namespace + "/").length());
                if (!afterPrefix.startsWith(path)) {
                    continue;
                }
                
                // Extract the relative path - the Identifier path is just everything after assets/namespace/
                // e.g., "models/item/apple.json" for an Identifier of modid:models/item/apple.json
                Identifier resourceId = Identifier.parse(namespace + ":" + afterPrefix);
                
                byte[] data = entry.getValue();
                output.accept(resourceId, () -> new ByteArrayInputStream(data));
                
                if (namespace.equals(NotEnoughRecipes.MOD_ID)) {
                    NotEnoughRecipes.LOGGER.info("  Providing resource: {} from {}", resourceId, resourcePath);
                }
            }
        }
    }
    
    @Override
    public Set<String> getNamespaces(PackType type) {
        NotEnoughRecipes.LOGGER.info("getNamespaces called for type: {}", type);
        
        if (type != PackType.CLIENT_RESOURCES) {
            NotEnoughRecipes.LOGGER.info("  Returning empty (not client resources)");
            return Collections.emptySet();
        }
        
        Set<String> namespaces = new HashSet<>();
        for (String path : resources.keySet()) {
            if (path.startsWith("assets/")) {
                String[] parts = path.split("/");
                if (parts.length > 1) {
                    namespaces.add(parts[1]);
                }
            }
        }
        
        // Always include our namespace
        namespaces.add(NotEnoughRecipes.MOD_ID);
        
        NotEnoughRecipes.LOGGER.info("  Returning namespaces: {}", namespaces);
        return namespaces;
    }
    
    @Nullable
    @Override
    public <T> T getMetadataSection(MetadataSectionType<T> type) {
        // We don't provide metadata sections
        return null;
    }
    
    @Override
    public PackLocationInfo location() {
        return new PackLocationInfo(
            PACK_ID,
            Component.literal("Dynamic Resources"),
            net.minecraft.server.packs.repository.PackSource.BUILT_IN,
            Optional.empty()
        );
    }
    
    @Override
    public String packId() {
        return PACK_ID;
    }
    
    @Override
    public void close() {
        // Nothing to close
    }
    
    /**
     * Check if this pack has any resources.
     */
    public boolean hasResources() {
        return !resources.isEmpty();
    }
    
    /**
     * Get the number of resources in this pack.
     */
    public int getResourceCount() {
        return resources.size();
    }
    
    /**
     * Get all resource paths for debugging.
     */
    public java.util.Set<String> getResourcePaths() {
        return resources.keySet();
    }
}
