package dev.scuffi.scripting.events;

import dev.scuffi.NotEnoughRecipes;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.PolyglotException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bridges JavaScript event registrations to Fabric events.
 * Provides the JavaScript-facing Event class and manages callback execution.
 */
public class EventBridge {
    
    private static final EventBridge INSTANCE = new EventBridge();
    
    // Map of event name -> list of JavaScript callback functions
    private final Map<String, List<Value>> eventHandlers = new HashMap<>();
    
    // Track which script registered which handlers for cleanup
    private final Map<String, List<String>> scriptEventMap = new HashMap<>();
    
    private EventBridge() {}
    
    public static EventBridge getInstance() {
        return INSTANCE;
    }
    
    /**
     * Registers a JavaScript callback for an event.
     * Called from JavaScript: Event.on("event_name", callback)
     * 
     * @param eventName The name of the event (e.g., "item_use", "player_tick")
     * @param callback The JavaScript function to call when the event fires
     * @param scriptName The name of the script registering this handler (for tracking)
     */
    public void registerEventHandler(String eventName, Value callback, String scriptName) {
        if (!callback.canExecute()) {
            NotEnoughRecipes.LOGGER.warn("Script '{}' tried to register non-executable callback for event '{}'", 
                    scriptName, eventName);
            return;
        }
        
        eventHandlers.computeIfAbsent(eventName, k -> new ArrayList<>()).add(callback);
        scriptEventMap.computeIfAbsent(scriptName, k -> new ArrayList<>()).add(eventName);
        
        NotEnoughRecipes.LOGGER.debug("Registered handler for event '{}' from script '{}'", eventName, scriptName);
    }
    
    /**
     * Fires all registered JavaScript handlers for an event.
     * 
     * @param eventName The name of the event to fire
     * @param eventContext The event context object to pass to JavaScript
     */
    public void fireEvent(String eventName, EventContext eventContext) {
        List<Value> handlers = eventHandlers.get(eventName);
        if (handlers == null || handlers.isEmpty()) {
            return;
        }
        
        // Create a copy to avoid concurrent modification if a handler modifies the list
        List<Value> handlersCopy = new ArrayList<>(handlers);
        
        for (Value handler : handlersCopy) {
            try {
                handler.execute(eventContext);
            } catch (PolyglotException e) {
                NotEnoughRecipes.LOGGER.error("Error in JavaScript event handler for '{}': {}", 
                        eventName, formatScriptError(e));
            } catch (Exception e) {
                NotEnoughRecipes.LOGGER.error("Unexpected error in event handler for '{}': {}", 
                        eventName, e.getMessage());
            }
        }
    }
    
    /**
     * Clears all event handlers registered by a specific script.
     * Used during hot-reload to clean up old handlers.
     * 
     * @param scriptName The name of the script whose handlers should be removed
     */
    public void clearScriptHandlers(String scriptName) {
        List<String> events = scriptEventMap.get(scriptName);
        if (events != null) {
            for (String eventName : events) {
                List<Value> handlers = eventHandlers.get(eventName);
                if (handlers != null) {
                    // Remove handlers from this script
                    // Note: We can't easily identify which handlers belong to which script
                    // without more tracking, so for now we'll clear all on reload
                    handlers.clear();
                }
            }
            scriptEventMap.remove(scriptName);
        }
    }
    
    /**
     * Clears all registered event handlers.
     * Used during full reload.
     */
    public void clearAllHandlers() {
        eventHandlers.clear();
        scriptEventMap.clear();
        NotEnoughRecipes.LOGGER.info("Cleared all JavaScript event handlers");
    }
    
    /**
     * Gets the number of handlers registered for an event.
     */
    public int getHandlerCount(String eventName) {
        List<Value> handlers = eventHandlers.get(eventName);
        return handlers != null ? handlers.size() : 0;
    }
    
    /**
     * Gets all registered event names.
     */
    public List<String> getRegisteredEvents() {
        return new ArrayList<>(eventHandlers.keySet());
    }
    
    /**
     * Formats a PolyglotException into a readable error message.
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
}
