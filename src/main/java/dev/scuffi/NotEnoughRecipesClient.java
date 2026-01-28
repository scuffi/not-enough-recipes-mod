package dev.scuffi;

import dev.scuffi.registry.CreationAltarRegistry;
import dev.scuffi.resource.DynamicResourceLoader;
import dev.scuffi.resource.DynamicResourcePack;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client-side initialization for Not Enough Recipes.
 * Handles dynamic resource loading and client-specific features.
 */
public class NotEnoughRecipesClient implements ClientModInitializer {
    
    @Override
    public void onInitializeClient() {
        NotEnoughRecipes.LOGGER.info("Not Enough Recipes Client initializing...");
        
        // Initialize the dynamic resource loader
        DynamicResourceLoader.initialize();
        
        // Get reference to the dynamic resource pack
        DynamicResourcePack pack = DynamicResourceLoader.getResourcePack();
        NotEnoughRecipes.LOGGER.info("Dynamic resource pack ready: {}", pack.packId());
        
        // Register Creation Altar client components
        CreationAltarRegistry.registerClient();
        
        NotEnoughRecipes.LOGGER.info("Not Enough Recipes Client initialized!");
        NotEnoughRecipes.LOGGER.info("Dynamic resources folder: {}", DynamicResourceLoader.getDynamicResourcePath());
    }
}
