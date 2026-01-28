package dev.scuffi;

import dev.scuffi.command.DynamicRegistryCommand;
import dev.scuffi.command.ScriptCommand;
import dev.scuffi.registry.CreationAltarRegistry;
import dev.scuffi.registry.DynamicRegistryPersistence;
import dev.scuffi.scripting.ScriptManager;
import dev.scuffi.scripting.events.EventRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Not Enough Recipes - Dynamic Registry POC
 * 
 * This mod demonstrates the ability to dynamically unfreeze Minecraft's
 * registries at runtime to register new items and blocks.
 * 
 * WARNING: This is a proof-of-concept for SINGLEPLAYER ONLY.
 * Do not use in multiplayer as clients won't have the dynamically registered content.
 * 
 * Usage:
 *   /dynreg test  - Run the full test (registers a block and item)
 *   /dynreg block - Register a new dynamic block
 *   /dynreg item  - Register a new dynamic item
 *   /dynreg stats - Show registry statistics
 */
public class NotEnoughRecipes implements ModInitializer {
	public static final String MOD_ID = "ner";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Not Enough Recipes - Dynamic Registry POC initializing...");
		
		// Register Creation Altar components
		CreationAltarRegistry.registerServer();
		
		// Register event listeners for JavaScript scripting
		EventRegistry.registerAllEvents();
		
		// Register commands
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			DynamicRegistryCommand.register(dispatcher);
			ScriptCommand.register(dispatcher);
			CreationAltarRegistry.registerCommands(dispatcher);
		});
		
		// Load and register any persisted dynamic items/blocks AFTER registries are frozen
		// This must happen after the server has fully started, not during mod initialization
		// because registries are still being frozen during onInitialize()
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			LOGGER.info("Loading persisted dynamic registry entries after server started...");
			try {
				DynamicRegistryPersistence.loadAndRegisterAll();
			} catch (Exception e) {
				LOGGER.error("Failed to load persisted registry entries", e);
			}
			
		// Initialize and load JavaScript scripts
		LOGGER.info("Initializing JavaScript scripting system...");
		try {
			// Use FabricLoader to get the proper config directory
			Path scriptsPath = FabricLoader.getInstance().getConfigDir()
					.resolve(MOD_ID)
					.resolve("scripts");
			ScriptManager.initialize(scriptsPath);
			
			ScriptManager manager = ScriptManager.getInstance();
			if (manager.isScriptEngineAvailable()) {
				manager.loadAllScripts();
				LOGGER.info("JavaScript scripting system initialized successfully");
			} else {
				LOGGER.warn("Script engine not available, scripting features disabled");
			}
		} catch (Exception e) {
			LOGGER.error("Failed to initialize scripting system", e);
		}
		});
		
		// Shut down scripts when server stops
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			try {
				ScriptManager.getInstance().shutdown();
			} catch (Exception e) {
				LOGGER.warn("Error shutting down script manager: {}", e.getMessage());
			}
		});
		
		LOGGER.info("Not Enough Recipes initialized! Use /dynreg test to test dynamic registry manipulation.");
	}
}
