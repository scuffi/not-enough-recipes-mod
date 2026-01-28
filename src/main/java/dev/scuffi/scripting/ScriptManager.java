package dev.scuffi.scripting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.scuffi.NotEnoughRecipes;
import dev.scuffi.scripting.api.NER;
import dev.scuffi.scripting.events.EventBridge;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Manages script loading, caching, and reloading.
 * Loads all .js files from the scripts directory and manages their lifecycle.
 */
public class ScriptManager {
    
    private static ScriptManager instance;
    
    private final Path scriptsDirectory;
    private final Path configPath;
    private ScriptEngine scriptEngine;
    private final Map<String, Long> scriptModificationTimes = new HashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    private ScriptConfig config;
    
    /**
     * Configuration for the script system.
     */
    public static class ScriptConfig {
        public boolean enabled = true;
        public SandboxConfig sandbox = new SandboxConfig();
        
        public static class SandboxConfig {
            public boolean allow_file_access = false;
            public boolean allow_network_access = false;
            public long max_execution_time_ms = 5000;
        }
    }
    
    private ScriptManager(Path scriptsDirectory) {
        this.scriptsDirectory = scriptsDirectory;
        this.configPath = scriptsDirectory.resolve("config.json");
        this.config = loadConfig();
        
        // Initialize script engine with config
        try {
            ScriptEngine.ScriptConfig engineConfig = new ScriptEngine.ScriptConfig(
                    config.sandbox.allow_file_access,
                    config.sandbox.allow_network_access,
                    config.sandbox.max_execution_time_ms
            );
            this.scriptEngine = new ScriptEngine(engineConfig);
            NotEnoughRecipes.LOGGER.info("Successfully created ScriptEngine");
        } catch (Exception e) {
            NotEnoughRecipes.LOGGER.error("Failed to create ScriptEngine, scripts will be disabled", e);
            this.scriptEngine = null;
        }
    }
    
    public static void initialize(Path scriptsDirectory) {
        if (instance != null) {
            NotEnoughRecipes.LOGGER.warn("ScriptManager already initialized");
            return;
        }
        
        instance = new ScriptManager(scriptsDirectory);
        NotEnoughRecipes.LOGGER.info("Initialized ScriptManager with directory: {}", scriptsDirectory);
    }
    
    public static ScriptManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ScriptManager not initialized");
        }
        return instance;
    }
    
    /**
     * Loads configuration from config.json if it exists.
     */
    private ScriptConfig loadConfig() {
        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                ScriptConfig loaded = gson.fromJson(json, ScriptConfig.class);
                NotEnoughRecipes.LOGGER.info("Loaded script configuration from {}", configPath);
                return loaded;
            } catch (Exception e) {
                NotEnoughRecipes.LOGGER.warn("Failed to load config.json, using defaults: {}", e.getMessage());
            }
        } else {
            // Create default config file
            try {
                Files.createDirectories(scriptsDirectory);
                Files.writeString(configPath, gson.toJson(new ScriptConfig()));
                NotEnoughRecipes.LOGGER.info("Created default config.json at {}", configPath);
            } catch (Exception e) {
                NotEnoughRecipes.LOGGER.warn("Failed to create default config.json: {}", e.getMessage());
            }
        }
        
        return new ScriptConfig();
    }
    
    /**
     * Loads all scripts from the scripts directory.
     */
    public void loadAllScripts() {
        if (!config.enabled) {
            NotEnoughRecipes.LOGGER.info("Script system is disabled in config");
            return;
        }
        
        if (scriptEngine == null) {
            NotEnoughRecipes.LOGGER.warn("Script engine failed to initialize, cannot load scripts");
            return;
        }
        
        // Ensure scripts directory exists
        try {
            Files.createDirectories(scriptsDirectory);
        } catch (IOException e) {
            NotEnoughRecipes.LOGGER.error("Failed to create scripts directory: {}", e.getMessage());
            return;
        }
        
        // Set up global bindings before loading scripts
        setupGlobalBindings();
        
        // Find all .js files
        List<Path> scriptFiles = findScriptFiles();
        
        if (scriptFiles.isEmpty()) {
            NotEnoughRecipes.LOGGER.info("No script files found in {}", scriptsDirectory);
            return;
        }
        
        NotEnoughRecipes.LOGGER.info("Loading {} script(s) from {}", scriptFiles.size(), scriptsDirectory);
        
        int successCount = 0;
        int failCount = 0;
        
        for (Path scriptPath : scriptFiles) {
            String scriptName = scriptPath.getFileName().toString();
            
            try {
                long modTime = Files.getLastModifiedTime(scriptPath).toMillis();
                scriptModificationTimes.put(scriptName, modTime);
                
                if (scriptEngine.loadScript(scriptPath, scriptName)) {
                    successCount++;
                } else {
                    failCount++;
                }
            } catch (Exception e) {
                NotEnoughRecipes.LOGGER.error("Failed to load script '{}': {}", scriptName, e.getMessage());
                failCount++;
            }
        }
        
        NotEnoughRecipes.LOGGER.info("Script loading complete: {} successful, {} failed", successCount, failCount);
        logEventHandlerStats();
    }
    
    /**
     * Finds all .js files in the scripts directory.
     */
    private List<Path> findScriptFiles() {
        List<Path> scriptFiles = new ArrayList<>();
        
        try (Stream<Path> paths = Files.walk(scriptsDirectory, 1)) {
            paths.filter(Files::isRegularFile)
                 .filter(path -> path.toString().endsWith(".js"))
                 .forEach(scriptFiles::add);
        } catch (IOException e) {
            NotEnoughRecipes.LOGGER.error("Failed to scan scripts directory: {}", e.getMessage());
        }
        
        return scriptFiles;
    }
    
    /**
     * Sets up global JavaScript bindings.
     */
    private void setupGlobalBindings() {
        // Bind the NER helper API
        scriptEngine.bindGlobal("NER", new NER());
        
        // Create and bind the Event object with registration functionality
        EventRegistrar eventRegistrar = new EventRegistrar();
        scriptEngine.bindGlobal("Event", eventRegistrar);
        
        NotEnoughRecipes.LOGGER.debug("Set up global JavaScript bindings");
    }
    
    /**
     * JavaScript-facing Event object for registering handlers.
     */
    public static class EventRegistrar {
        private String currentScriptName = "unknown";
        
        public void setCurrentScript(String name) {
            this.currentScriptName = name;
        }
        
        /**
         * Registers an event handler.
         * Called from JavaScript: Event.on("event_name", callback)
         */
        public void on(String eventName, Value callback) {
            EventBridge.getInstance().registerEventHandler(eventName, callback, currentScriptName);
        }
    }
    
    /**
     * Reloads all scripts from disk.
     */
    public void reload() {
        NotEnoughRecipes.LOGGER.info("Reloading all scripts...");
        
        // Clear all event handlers
        EventBridge.getInstance().clearAllHandlers();
        
        // Reload config
        config = loadConfig();
        
        // Reinitialize script engine with new config
        ScriptEngine.ScriptConfig engineConfig = new ScriptEngine.ScriptConfig(
                config.sandbox.allow_file_access,
                config.sandbox.allow_network_access,
                config.sandbox.max_execution_time_ms
        );
        scriptEngine.close();
        scriptEngine = new ScriptEngine(engineConfig);
        
        // Clear modification times
        scriptModificationTimes.clear();
        
        // Reload all scripts
        loadAllScripts();
        
        NotEnoughRecipes.LOGGER.info("Script reload complete");
    }
    
    /**
     * Logs statistics about registered event handlers.
     */
    private void logEventHandlerStats() {
        EventBridge bridge = EventBridge.getInstance();
        List<String> events = bridge.getRegisteredEvents();
        
        if (events.isEmpty()) {
            NotEnoughRecipes.LOGGER.info("No event handlers registered");
            return;
        }
        
        NotEnoughRecipes.LOGGER.info("Registered event handlers:");
        for (String eventName : events) {
            int count = bridge.getHandlerCount(eventName);
            NotEnoughRecipes.LOGGER.info("  - {}: {} handler(s)", eventName, count);
        }
    }
    
    /**
     * Shuts down the script manager and cleans up resources.
     */
    public void shutdown() {
        NotEnoughRecipes.LOGGER.info("Shutting down ScriptManager...");
        
        if (scriptEngine != null) {
            scriptEngine.close();
        }
        
        EventBridge.getInstance().clearAllHandlers();
        scriptModificationTimes.clear();
        
        NotEnoughRecipes.LOGGER.info("ScriptManager shut down");
    }
    
    /**
     * Gets the scripts directory path.
     */
    public Path getScriptsDirectory() {
        return scriptsDirectory;
    }
    
    /**
     * Checks if the script system is enabled.
     */
    public boolean isEnabled() {
        return config.enabled && scriptEngine != null;
    }
    
    /**
     * Checks if the script engine is available.
     */
    public boolean isScriptEngineAvailable() {
        return scriptEngine != null;
    }
    
    /**
     * Gets the number of loaded scripts.
     */
    public int getLoadedScriptCount() {
        return scriptModificationTimes.size();
    }
}
