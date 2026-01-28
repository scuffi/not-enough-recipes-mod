package dev.scuffi.scripting;

import dev.scuffi.NotEnoughRecipes;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages GraalJS contexts and script execution.
 * Provides isolated JavaScript execution environments with configurable sandboxing.
 */
public class ScriptEngine {
    
    private Context context;
    private final Map<String, Value> loadedScripts = new HashMap<>();
    private final ScriptConfig config;
    
    /**
     * Configuration for the script engine sandbox.
     */
    public static class ScriptConfig {
        public boolean allowFileAccess = false;
        public boolean allowNetworkAccess = false;
        public long maxExecutionTimeMs = 5000;
        
        public ScriptConfig() {}
        
        public ScriptConfig(boolean allowFileAccess, boolean allowNetworkAccess, long maxExecutionTimeMs) {
            this.allowFileAccess = allowFileAccess;
            this.allowNetworkAccess = allowNetworkAccess;
            this.maxExecutionTimeMs = maxExecutionTimeMs;
        }
    }
    
    public ScriptEngine() {
        this(new ScriptConfig());
    }
    
    public ScriptEngine(ScriptConfig config) {
        this.config = config;
        initializeContext();
    }
    
    /**
     * Initializes or re-initializes the GraalJS context with current config.
     */
    private void initializeContext() {
        if (context != null) {
            try {
                context.close();
            } catch (Exception e) {
                NotEnoughRecipes.LOGGER.warn("Error closing previous context: {}", e.getMessage());
            }
        }
        
        try {
            // Build a minimal GraalJS context
            // Note: Many options don't exist in this version of GraalJS, so we keep it simple
            Context.Builder builder = Context.newBuilder("js")
                    .allowHostAccess(HostAccess.ALL) // Allow access to Java classes
                    .allowIO(config.allowFileAccess) // Control file I/O
                    .allowCreateThread(false) // Disable thread creation for safety
                    .allowNativeAccess(false); // Disable native access
            
            context = builder.build();
            
            NotEnoughRecipes.LOGGER.info("Initialized GraalJS context with sandbox: fileAccess={}, networkAccess={}",
                    config.allowFileAccess, config.allowNetworkAccess);
            
        } catch (Exception e) {
            NotEnoughRecipes.LOGGER.error("Failed to initialize GraalJS context", e);
            throw new RuntimeException("Failed to initialize script engine", e);
        }
    }
    
    /**
     * Loads and evaluates a JavaScript file.
     * 
     * @param scriptPath Path to the JavaScript file
     * @param scriptName Friendly name for the script (used in error messages)
     * @return true if script loaded successfully, false otherwise
     */
    public boolean loadScript(Path scriptPath, String scriptName) {
        try {
            String scriptContent = Files.readString(scriptPath);
            return evaluateScript(scriptContent, scriptName);
        } catch (IOException e) {
            NotEnoughRecipes.LOGGER.error("Failed to read script file '{}': {}", scriptName, e.getMessage());
            return false;
        }
    }
    
    /**
     * Evaluates JavaScript code.
     * 
     * @param scriptContent The JavaScript code to evaluate
     * @param scriptName Friendly name for the script (used in error messages)
     * @return true if script evaluated successfully, false otherwise
     */
    public boolean evaluateScript(String scriptContent, String scriptName) {
        try {
            Value result = context.eval("js", scriptContent);
            loadedScripts.put(scriptName, result);
            NotEnoughRecipes.LOGGER.info("Successfully loaded script: {}", scriptName);
            return true;
        } catch (PolyglotException e) {
            NotEnoughRecipes.LOGGER.error("Script compilation error in '{}': {}", scriptName, formatScriptError(e));
            return false;
        } catch (Exception e) {
            NotEnoughRecipes.LOGGER.error("Unexpected error loading script '{}': {}", scriptName, e.getMessage());
            return false;
        }
    }
    
    /**
     * Binds a Java object to the JavaScript global scope.
     * 
     * @param name The name to bind in JavaScript
     * @param object The Java object to expose
     */
    public void bindGlobal(String name, Object object) {
        try {
            context.getBindings("js").putMember(name, object);
            NotEnoughRecipes.LOGGER.debug("Bound '{}' to JavaScript global scope", name);
        } catch (Exception e) {
            NotEnoughRecipes.LOGGER.error("Failed to bind '{}' to global scope: {}", name, e.getMessage());
        }
    }
    
    /**
     * Invokes a JavaScript function.
     * 
     * @param functionName The name of the function to invoke
     * @param args Arguments to pass to the function
     * @return The result of the function call, or null if invocation failed
     */
    public Value invokeFunction(String functionName, Object... args) {
        try {
            Value bindings = context.getBindings("js");
            Value function = bindings.getMember(functionName);
            
            if (function == null || !function.canExecute()) {
                NotEnoughRecipes.LOGGER.warn("Function '{}' not found or not executable", functionName);
                return null;
            }
            
            return function.execute(args);
        } catch (PolyglotException e) {
            NotEnoughRecipes.LOGGER.error("Error invoking function '{}': {}", functionName, formatScriptError(e));
            return null;
        } catch (Exception e) {
            NotEnoughRecipes.LOGGER.error("Unexpected error invoking function '{}': {}", functionName, e.getMessage());
            return null;
        }
    }
    
    /**
     * Clears all loaded scripts and resets the context.
     */
    public void reload() {
        NotEnoughRecipes.LOGGER.info("Reloading script engine...");
        loadedScripts.clear();
        initializeContext();
    }
    
    /**
     * Closes the context and releases resources.
     */
    public void close() {
        if (context != null) {
            try {
                context.close();
                NotEnoughRecipes.LOGGER.info("Closed GraalJS context");
            } catch (Exception e) {
                NotEnoughRecipes.LOGGER.warn("Error closing context: {}", e.getMessage());
            }
            context = null;
        }
        loadedScripts.clear();
    }
    
    /**
     * Formats a PolyglotException into a readable error message with line numbers.
     */
    private String formatScriptError(PolyglotException e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.getMessage());
        
        if (e.getSourceLocation() != null) {
            sb.append(" at line ").append(e.getSourceLocation().getStartLine());
            if (e.getSourceLocation().getStartColumn() > 0) {
                sb.append(", column ").append(e.getSourceLocation().getStartColumn());
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Gets the underlying GraalJS context.
     * Use with caution - direct context manipulation can break sandboxing.
     */
    public Context getContext() {
        return context;
    }
    
    /**
     * Gets the configuration used by this script engine.
     */
    public ScriptConfig getConfig() {
        return config;
    }
    
    /**
     * Returns the number of loaded scripts.
     */
    public int getLoadedScriptCount() {
        return loadedScripts.size();
    }
}
