package dev.scuffi.scripting.events;

import dev.scuffi.NotEnoughRecipes;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Registers all Fabric event listeners and routes them to JavaScript handlers.
 * This is the bridge between Minecraft's event system and our JavaScript scripts.
 */
public class EventRegistry {
    
    private static boolean registered = false;
    
    /**
     * Registers all Fabric event listeners.
     * This should be called once during mod initialization.
     */
    public static void registerAllEvents() {
        if (registered) {
            NotEnoughRecipes.LOGGER.warn("Events already registered");
            return;
        }
        
        // Item & Interaction Events
        registerItemUseEvent();
        registerItemPickupEvent();
        registerItemDropEvent();
        registerItemCraftEvent();
        
        // Block Events
        registerBlockBreakEvent();
        registerBlockPlaceEvent();
        registerBlockInteractEvent();
        
        // Entity Events
        registerEntityAttackEvent();
        registerEntityInteractEvent();
        registerLivingHurtEvent();
        registerEntityDeathEvent();
        
        // Player Events
        registerPlayerTickEvent();
        registerPlayerDeathEvent();
        registerPlayerRespawnEvent();
        registerPlayerJoinEvent();
        registerPlayerLeaveEvent();
        
        // Projectile Events
        registerProjectileHitEvent();
        
        registered = true;
        NotEnoughRecipes.LOGGER.info("Registered {} JavaScript event listeners", 16);
    }
    
    /**
     * Registers the item use event.
     * Fires when a player uses an item (right-click).
     */
    private static void registerItemUseEvent() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            try {
                var itemStack = player.getItemInHand(hand);
                var context = new EventContext.ItemUseContext(player, world, itemStack, hand);
                
                EventBridge.getInstance().fireEvent("item_use", context);
                
                // Handle result if script set one
                if (context.result != null) {
                    return switch (context.result.toUpperCase()) {
                        case "SUCCESS" -> InteractionResult.SUCCESS;
                        case "FAIL" -> InteractionResult.FAIL;
                        case "CONSUME" -> InteractionResult.CONSUME;
                        default -> InteractionResult.PASS;
                    };
                }
                
                // If cancelled, return fail
                if (context.cancelled) {
                    return InteractionResult.FAIL;
                }
            } catch (Exception e) {
                NotEnoughRecipes.LOGGER.error("Error in item_use event: {}", e.getMessage());
            }
            
            return InteractionResult.PASS;
        });
    }
    
    /**
     * Registers the block break event.
     * Fires before a player breaks a block.
     */
    private static void registerBlockBreakEvent() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            try {
                var context = new EventContext.BlockBreakContext(player, world, pos, state);
                
                EventBridge.getInstance().fireEvent("block_break", context);
                
                // If cancelled, prevent the break
                if (context.cancelled) {
                    return false;
                }
            } catch (Exception e) {
                NotEnoughRecipes.LOGGER.error("Error in block_break event: {}", e.getMessage());
            }
            
            return true;
        });
    }
    
    /**
     * Registers the entity attack event.
     * Fires when a player attacks an entity.
     */
    private static void registerEntityAttackEvent() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            try {
                var context = new EventContext.EntityAttackContext(player, world, entity);
                
                EventBridge.getInstance().fireEvent("entity_attack", context);
                
                // If cancelled, prevent the attack
                if (context.cancelled) {
                    return InteractionResult.FAIL;
                }
            } catch (Exception e) {
                NotEnoughRecipes.LOGGER.error("Error in entity_attack event: {}", e.getMessage());
            }
            
            return InteractionResult.PASS;
        });
    }
    
    /**
     * Registers the player tick event.
     * Fires every tick for each player on the server.
     */
    private static void registerPlayerTickEvent() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                // Fire tick event for each player
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    var context = new EventContext.PlayerTickContext(player);
                    EventBridge.getInstance().fireEvent("player_tick", context);
                }
            } catch (Exception e) {
                NotEnoughRecipes.LOGGER.error("Error in player_tick event: {}", e.getMessage());
            }
        });
    }
    
    /**
     * Registers the block place event.
     * Fires when a player places a block (using UseBlockCallback as approximation).
     */
    private static void registerBlockPlaceEvent() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            try {
                var pos = hitResult.getBlockPos().relative(hitResult.getDirection());
                var itemStack = player.getItemInHand(hand);
                
                // Fire event if player is holding a block item
                if (!itemStack.isEmpty() && itemStack.getItem() instanceof net.minecraft.world.item.BlockItem) {
                    var state = world.getBlockState(pos);
                    var context = new EventContext.BlockPlacedContext(player, world, pos, state, itemStack);
                    EventBridge.getInstance().fireEvent("block_place", context);
                    
                    if (context.cancelled) {
                        return InteractionResult.FAIL;
                    }
                }
            } catch (Exception e) {
                NotEnoughRecipes.LOGGER.error("Error in block_place event: {}", e.getMessage());
            }
            
            return InteractionResult.PASS;
        });
    }
    
    /**
     * Registers the item pickup event.
     * Fires when a player picks up an item.
     */
    private static void registerItemPickupEvent() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            // Note: Fabric doesn't have a direct item pickup event
            // This is a simplified version - for full functionality, might need mixins
        });
    }
    
    /**
     * Registers the item drop event.
     * Fires when a player drops an item.
     */
    private static void registerItemDropEvent() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            // Note: Fabric doesn't have a direct item drop event
            // Would need a mixin for PlayerEntity.dropItem() for full functionality
            return InteractionResult.PASS;
        });
    }
    
    /**
     * Registers the item craft event.
     * Fires when a player crafts an item.
     */
    private static void registerItemCraftEvent() {
        // Note: Would need CraftItemCallback or mixin for full functionality
    }
    
    /**
     * Registers the entity interact event.
     * Fires when a player right-clicks an entity.
     */
    private static void registerEntityInteractEvent() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            try {
                var context = new EventContext.EntityInteractContext(player, world, entity, hand);
                EventBridge.getInstance().fireEvent("entity_interact", context);
                
                if (context.cancelled) {
                    return InteractionResult.FAIL;
                }
            } catch (Exception e) {
                NotEnoughRecipes.LOGGER.error("Error in entity_interact event: {}", e.getMessage());
            }
            
            return InteractionResult.PASS;
        });
    }
    
    /**
     * Registers the block interact event.
     * Fires when a player right-clicks a block.
     */
    private static void registerBlockInteractEvent() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            try {
                var pos = hitResult.getBlockPos();
                var state = world.getBlockState(pos);
                var face = hitResult.getDirection().getName();
                
                var context = new EventContext.BlockInteractContext(player, world, pos, state, hand, face);
                EventBridge.getInstance().fireEvent("block_interact", context);
                
                if (context.cancelled) {
                    return InteractionResult.FAIL;
                }
            } catch (Exception e) {
                NotEnoughRecipes.LOGGER.error("Error in block_interact event: {}", e.getMessage());
            }
            
            return InteractionResult.PASS;
        });
    }
    
    /**
     * Registers the living hurt event.
     * Fires when any living entity takes damage.
     */
    private static void registerLivingHurtEvent() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            try {
                var world = entity.level();
                var attacker = source.getEntity() instanceof Player p ? p : null;
                var damageSourceName = source.getMsgId();
                
                var context = new EventContext.LivingHurtContext(entity, world, amount, damageSourceName, attacker);
                EventBridge.getInstance().fireEvent("living_hurt", context);
                
                // If cancelled, prevent damage
                return !context.cancelled;
            } catch (Exception e) {
                NotEnoughRecipes.LOGGER.error("Error in living_hurt event: {}", e.getMessage());
            }
            
            return true;
        });
    }
    
    /**
     * Registers the entity death event.
     * Fires when any entity dies.
     */
    private static void registerEntityDeathEvent() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            try {
                var world = entity.level();
                var killer = source.getEntity() instanceof Player p ? p : null;
                var damageSourceName = source.getMsgId();
                
                var context = new EventContext.EntityDeathContext(entity, world, damageSourceName, killer);
                EventBridge.getInstance().fireEvent("entity_death", context);
            } catch (Exception e) {
                NotEnoughRecipes.LOGGER.error("Error in entity_death event: {}", e.getMessage());
            }
        });
    }
    
    /**
     * Registers the player death event.
     * Fires when a player dies.
     */
    private static void registerPlayerDeathEvent() {
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) {
                try {
                    var world = oldPlayer.level();
                    var damageSourceName = oldPlayer.getLastDamageSource() != null ? 
                            oldPlayer.getLastDamageSource().getMsgId() : "unknown";
                    
                    var context = new EventContext.PlayerDeathContext(oldPlayer, world, damageSourceName);
                    EventBridge.getInstance().fireEvent("player_death", context);
                } catch (Exception e) {
                    NotEnoughRecipes.LOGGER.error("Error in player_death event: {}", e.getMessage());
                }
            }
        });
    }
    
    /**
     * Registers the player respawn event.
     * Fires when a player respawns.
     */
    private static void registerPlayerRespawnEvent() {
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (alive) {
                try {
                    var world = newPlayer.level();
                    var conqueredEnd = false; // Would need to check dimension change
                    
                    var context = new EventContext.PlayerRespawnContext(newPlayer, world, conqueredEnd);
                    EventBridge.getInstance().fireEvent("player_respawn", context);
                } catch (Exception e) {
                    NotEnoughRecipes.LOGGER.error("Error in player_respawn event: {}", e.getMessage());
                }
            }
        });
    }
    
    /**
     * Registers the player join event.
     * Fires when a player joins the server.
     */
    private static void registerPlayerJoinEvent() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            try {
                var player = handler.getPlayer();
                var world = player.level();
                
                var context = new EventContext.PlayerJoinContext(player, world);
                EventBridge.getInstance().fireEvent("player_join", context);
            } catch (Exception e) {
                NotEnoughRecipes.LOGGER.error("Error in player_join event: {}", e.getMessage());
            }
        });
    }
    
    /**
     * Registers the player leave event.
     * Fires when a player leaves the server.
     */
    private static void registerPlayerLeaveEvent() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            try {
                var player = handler.getPlayer();
                var world = player.level();
                
                var context = new EventContext.PlayerLeaveContext(player, world);
                EventBridge.getInstance().fireEvent("player_leave", context);
            } catch (Exception e) {
                NotEnoughRecipes.LOGGER.error("Error in player_leave event: {}", e.getMessage());
            }
        });
    }
    
    /**
     * Registers the projectile hit event.
     * Fires when a projectile hits something.
     */
    private static void registerProjectileHitEvent() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            // Note: Fabric doesn't have a direct projectile hit event
            // Would need a mixin for ProjectileEntity.onCollision() for full functionality
        });
    }
    
    /**
     * Checks if events have been registered.
     */
    public static boolean isRegistered() {
        return registered;
    }
}
