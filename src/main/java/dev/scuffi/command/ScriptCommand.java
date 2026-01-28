package dev.scuffi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.scuffi.scripting.ScriptManager;
import dev.scuffi.scripting.events.EventBridge;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Command for managing JavaScript scripts.
 * Provides /ner reload to hot-reload all scripts.
 */
public class ScriptCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ner")
                .then(Commands.literal("reload")
                        .executes(ScriptCommand::reloadScripts))
                .then(Commands.literal("scripts")
                        .executes(ScriptCommand::scriptStats)));
    }
    
    /**
     * Reloads all JavaScript scripts.
     */
    private static int reloadScripts(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        try {
            source.sendSuccess(() -> Component.literal("Reloading scripts..."), true);
            
            ScriptManager manager = ScriptManager.getInstance();
            manager.reload();
            
            int scriptCount = manager.getLoadedScriptCount();
            source.sendSuccess(() -> Component.literal(
                    String.format("§aSuccessfully reloaded %d script(s)", scriptCount)), true);
            
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cFailed to reload scripts: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * Shows script statistics and registered event handlers.
     */
    private static int scriptStats(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        try {
            ScriptManager manager = ScriptManager.getInstance();
            
            if (!manager.isScriptEngineAvailable()) {
                source.sendFailure(Component.literal("§cScript engine failed to initialize. Check server logs for details."));
                return 0;
            }
            
            EventBridge bridge = EventBridge.getInstance();
            
            int scriptCount = manager.getLoadedScriptCount();
            boolean enabled = manager.isEnabled();
            
            source.sendSuccess(() -> Component.literal("§6=== Script System Stats ==="), false);
            source.sendSuccess(() -> Component.literal(String.format("§eEnabled: %s", enabled ? "§aYes" : "§cNo")), false);
            source.sendSuccess(() -> Component.literal(String.format("§eLoaded Scripts: §a%d", scriptCount)), false);
            source.sendSuccess(() -> Component.literal(String.format("§eScripts Directory: §7%s", 
                    manager.getScriptsDirectory())), false);
            
            var events = bridge.getRegisteredEvents();
            if (!events.isEmpty()) {
                source.sendSuccess(() -> Component.literal("§6Event Handlers:"), false);
                for (String eventName : events) {
                    int handlerCount = bridge.getHandlerCount(eventName);
                    source.sendSuccess(() -> Component.literal(String.format("  §e%s: §a%d handler(s)", 
                            eventName, handlerCount)), false);
                }
            } else {
                source.sendSuccess(() -> Component.literal("§7No event handlers registered"), false);
            }
            
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cFailed to get script stats: " + e.getMessage()));
            return 0;
        }
    }
}
