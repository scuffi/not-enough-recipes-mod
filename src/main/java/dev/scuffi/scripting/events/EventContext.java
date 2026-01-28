package dev.scuffi.scripting.events;

import dev.scuffi.scripting.api.PlayerWrapper;
import dev.scuffi.scripting.api.WorldWrapper;
import dev.scuffi.scripting.api.ItemStackWrapper;
import dev.scuffi.scripting.api.BlockPosWrapper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import org.graalvm.polyglot.HostAccess;

/**
 * Base class for event context objects passed to JavaScript event handlers.
 * Wraps Minecraft event data in a JavaScript-friendly format.
 */
public class EventContext {
    
    @HostAccess.Export
    public boolean cancelled = false;
    
    @HostAccess.Export
    public String result = null;
    
    /**
     * Cancels the event (if cancellable).
     */
    @HostAccess.Export
    public void cancel() {
        this.cancelled = true;
    }
    
    /**
     * Sets the result of the event.
     * @param result The result string (e.g., "SUCCESS", "FAIL", "PASS")
     */
    @HostAccess.Export
    public void setResult(String result) {
        this.result = result;
    }
    
    /**
     * Checks if the event was cancelled.
     */
    @HostAccess.Export
    public boolean isCancelled() {
        return cancelled;
    }
    
    /**
     * Gets the result of the event.
     */
    @HostAccess.Export
    public String getResult() {
        return result;
    }
    
    // Specific event context types
    
    /**
     * Context for item use events.
     */
    public static class ItemUseContext extends EventContext {
        @HostAccess.Export
        public final PlayerWrapper player;
        
        @HostAccess.Export
        public final WorldWrapper world;
        
        @HostAccess.Export
        public final ItemStackWrapper itemStack;
        
        @HostAccess.Export
        public final String hand;
        
        public ItemUseContext(Player player, Level world, ItemStack itemStack, InteractionHand hand) {
            this.player = new PlayerWrapper(player);
            this.world = new WorldWrapper(world);
            this.itemStack = new ItemStackWrapper(itemStack);
            this.hand = hand.name();
        }
    }
    
    /**
     * Context for block break events.
     */
    public static class BlockBreakContext extends EventContext {
        @HostAccess.Export
        public final PlayerWrapper player;
        
        @HostAccess.Export
        public final WorldWrapper world;
        
        @HostAccess.Export
        public final BlockPosWrapper pos;
        
        @HostAccess.Export
        public final String blockId;
        
        private final BlockState blockState;
        
        public BlockBreakContext(Player player, Level world, BlockPos pos, BlockState state) {
            this.player = new PlayerWrapper(player);
            this.world = new WorldWrapper(world);
            this.pos = new BlockPosWrapper(pos);
            this.blockState = state;
            this.blockId = state.getBlock().getName().getString();
        }
        
        @HostAccess.Export
        public Object getBlockState() {
            return blockState;
        }
    }
    
    /**
     * Context for entity attack events.
     */
    public static class EntityAttackContext extends EventContext {
        @HostAccess.Export
        public final PlayerWrapper player;
        
        @HostAccess.Export
        public final WorldWrapper world;
        
        @HostAccess.Export
        public final String entityType;
        
        private final Entity entity;
        
        public EntityAttackContext(Player player, Level world, Entity entity) {
            this.player = new PlayerWrapper(player);
            this.world = new WorldWrapper(world);
            this.entity = entity;
            this.entityType = entity.getType().toString();
        }
        
        @HostAccess.Export
        public Object getEntity() {
            return entity;
        }
    }
    
    /**
     * Context for player tick events.
     */
    public static class PlayerTickContext extends EventContext {
        @HostAccess.Export
        public final PlayerWrapper player;
        
        @HostAccess.Export
        public final WorldWrapper world;
        
        public PlayerTickContext(Player player) {
            this.player = new PlayerWrapper(player);
            this.world = new WorldWrapper(player.level());
        }
    }
    
    /**
     * Context for block placed events.
     */
    public static class BlockPlacedContext extends EventContext {
        @HostAccess.Export
        public final PlayerWrapper player;
        
        @HostAccess.Export
        public final WorldWrapper world;
        
        @HostAccess.Export
        public final BlockPosWrapper pos;
        
        @HostAccess.Export
        public final String blockId;
        
        @HostAccess.Export
        public final ItemStackWrapper itemStack;
        
        public BlockPlacedContext(Player player, Level world, BlockPos pos, BlockState state, ItemStack stack) {
            this.player = new PlayerWrapper(player);
            this.world = new WorldWrapper(world);
            this.pos = new BlockPosWrapper(pos);
            this.blockId = state.getBlock().getName().getString();
            this.itemStack = new ItemStackWrapper(stack);
        }
    }
    
    /**
     * Context for item consumed events (eating/drinking).
     */
    public static class ItemConsumedContext extends EventContext {
        @HostAccess.Export
        public final PlayerWrapper player;
        
        @HostAccess.Export
        public final WorldWrapper world;
        
        @HostAccess.Export
        public final ItemStackWrapper itemStack;
        
        public ItemConsumedContext(Player player, Level world, ItemStack itemStack) {
            this.player = new PlayerWrapper(player);
            this.world = new WorldWrapper(world);
            this.itemStack = new ItemStackWrapper(itemStack);
        }
    }
    
    /**
     * Context for item pickup events.
     */
    public static class ItemPickupContext extends EventContext {
        @HostAccess.Export
        public final PlayerWrapper player;
        
        @HostAccess.Export
        public final WorldWrapper world;
        
        @HostAccess.Export
        public final ItemStackWrapper itemStack;
        
        private final Entity entity;
        
        public ItemPickupContext(Player player, Level world, ItemStack itemStack, Entity entity) {
            this.player = new PlayerWrapper(player);
            this.world = new WorldWrapper(world);
            this.itemStack = new ItemStackWrapper(itemStack);
            this.entity = entity;
        }
        
        @HostAccess.Export
        public Object getItemEntity() {
            return entity;
        }
    }
    
    /**
     * Context for item drop events.
     */
    public static class ItemDropContext extends EventContext {
        @HostAccess.Export
        public final PlayerWrapper player;
        
        @HostAccess.Export
        public final WorldWrapper world;
        
        @HostAccess.Export
        public final ItemStackWrapper itemStack;
        
        public ItemDropContext(Player player, Level world, ItemStack itemStack) {
            this.player = new PlayerWrapper(player);
            this.world = new WorldWrapper(world);
            this.itemStack = new ItemStackWrapper(itemStack);
        }
    }
    
    /**
     * Context for item craft events.
     */
    public static class ItemCraftContext extends EventContext {
        @HostAccess.Export
        public final PlayerWrapper player;
        
        @HostAccess.Export
        public final WorldWrapper world;
        
        @HostAccess.Export
        public final ItemStackWrapper itemStack;
        
        public ItemCraftContext(Player player, Level world, ItemStack itemStack) {
            this.player = new PlayerWrapper(player);
            this.world = new WorldWrapper(world);
            this.itemStack = new ItemStackWrapper(itemStack);
        }
    }
    
    /**
     * Context for entity interact events (right-click entity).
     */
    public static class EntityInteractContext extends EventContext {
        @HostAccess.Export
        public final PlayerWrapper player;
        
        @HostAccess.Export
        public final WorldWrapper world;
        
        @HostAccess.Export
        public final String entityType;
        
        @HostAccess.Export
        public final String hand;
        
        private final Entity entity;
        
        public EntityInteractContext(Player player, Level world, Entity entity, InteractionHand hand) {
            this.player = new PlayerWrapper(player);
            this.world = new WorldWrapper(world);
            this.entity = entity;
            this.entityType = entity.getType().toString();
            this.hand = hand.name();
        }
        
        @HostAccess.Export
        public Object getEntity() {
            return entity;
        }
    }
    
    /**
     * Context for block interact events (right-click block).
     */
    public static class BlockInteractContext extends EventContext {
        @HostAccess.Export
        public final PlayerWrapper player;
        
        @HostAccess.Export
        public final WorldWrapper world;
        
        @HostAccess.Export
        public final BlockPosWrapper pos;
        
        @HostAccess.Export
        public final String blockId;
        
        @HostAccess.Export
        public final String hand;
        
        @HostAccess.Export
        public final String face;
        
        private final BlockState blockState;
        
        public BlockInteractContext(Player player, Level world, BlockPos pos, BlockState state, InteractionHand hand, String face) {
            this.player = new PlayerWrapper(player);
            this.world = new WorldWrapper(world);
            this.pos = new BlockPosWrapper(pos);
            this.blockState = state;
            this.blockId = state.getBlock().getName().getString();
            this.hand = hand.name();
            this.face = face;
        }
        
        @HostAccess.Export
        public Object getBlockState() {
            return blockState;
        }
    }
    
    /**
     * Context for living hurt events (damage).
     */
    public static class LivingHurtContext extends EventContext {
        @HostAccess.Export
        public final WorldWrapper world;
        
        @HostAccess.Export
        public final String entityType;
        
        @HostAccess.Export
        public final float damage;
        
        @HostAccess.Export
        public final String damageSource;
        
        @HostAccess.Export
        public PlayerWrapper attacker; // May be null
        
        private final Entity entity;
        
        public LivingHurtContext(Entity entity, Level world, float damage, String damageSource, Player attacker) {
            this.world = new WorldWrapper(world);
            this.entity = entity;
            this.entityType = entity.getType().toString();
            this.damage = damage;
            this.damageSource = damageSource;
            this.attacker = attacker != null ? new PlayerWrapper(attacker) : null;
        }
        
        @HostAccess.Export
        public Object getEntity() {
            return entity;
        }
    }
    
    /**
     * Context for entity death events.
     */
    public static class EntityDeathContext extends EventContext {
        @HostAccess.Export
        public final WorldWrapper world;
        
        @HostAccess.Export
        public final String entityType;
        
        @HostAccess.Export
        public final String damageSource;
        
        @HostAccess.Export
        public PlayerWrapper killer; // May be null
        
        private final Entity entity;
        
        public EntityDeathContext(Entity entity, Level world, String damageSource, Player killer) {
            this.world = new WorldWrapper(world);
            this.entity = entity;
            this.entityType = entity.getType().toString();
            this.damageSource = damageSource;
            this.killer = killer != null ? new PlayerWrapper(killer) : null;
        }
        
        @HostAccess.Export
        public Object getEntity() {
            return entity;
        }
    }
    
    /**
     * Context for player death events.
     */
    public static class PlayerDeathContext extends EventContext {
        @HostAccess.Export
        public final PlayerWrapper player;
        
        @HostAccess.Export
        public final WorldWrapper world;
        
        @HostAccess.Export
        public final String damageSource;
        
        public PlayerDeathContext(Player player, Level world, String damageSource) {
            this.player = new PlayerWrapper(player);
            this.world = new WorldWrapper(world);
            this.damageSource = damageSource;
        }
    }
    
    /**
     * Context for player respawn events.
     */
    public static class PlayerRespawnContext extends EventContext {
        @HostAccess.Export
        public final PlayerWrapper player;
        
        @HostAccess.Export
        public final WorldWrapper world;
        
        @HostAccess.Export
        public final boolean conqueredEnd;
        
        public PlayerRespawnContext(Player player, Level world, boolean conqueredEnd) {
            this.player = new PlayerWrapper(player);
            this.world = new WorldWrapper(world);
            this.conqueredEnd = conqueredEnd;
        }
    }
    
    /**
     * Context for player join events.
     */
    public static class PlayerJoinContext extends EventContext {
        @HostAccess.Export
        public final PlayerWrapper player;
        
        @HostAccess.Export
        public final WorldWrapper world;
        
        public PlayerJoinContext(Player player, Level world) {
            this.player = new PlayerWrapper(player);
            this.world = new WorldWrapper(world);
        }
    }
    
    /**
     * Context for player leave events.
     */
    public static class PlayerLeaveContext extends EventContext {
        @HostAccess.Export
        public final PlayerWrapper player;
        
        @HostAccess.Export
        public final WorldWrapper world;
        
        public PlayerLeaveContext(Player player, Level world) {
            this.player = new PlayerWrapper(player);
            this.world = new WorldWrapper(world);
        }
    }
    
    /**
     * Context for projectile hit events.
     */
    public static class ProjectileHitContext extends EventContext {
        @HostAccess.Export
        public final WorldWrapper world;
        
        @HostAccess.Export
        public final String projectileType;
        
        @HostAccess.Export
        public final String hitType; // "BLOCK", "ENTITY", or "MISS"
        
        @HostAccess.Export
        public PlayerWrapper shooter; // May be null
        
        @HostAccess.Export
        public BlockPosWrapper hitPos; // May be null
        
        @HostAccess.Export
        public String hitEntityType; // May be null
        
        private final Entity projectile;
        private final Entity hitEntity;
        
        public ProjectileHitContext(Entity projectile, Level world, String hitType, Player shooter, BlockPos hitPos, Entity hitEntity) {
            this.world = new WorldWrapper(world);
            this.projectile = projectile;
            this.projectileType = projectile.getType().toString();
            this.hitType = hitType;
            this.shooter = shooter != null ? new PlayerWrapper(shooter) : null;
            this.hitPos = hitPos != null ? new BlockPosWrapper(hitPos) : null;
            this.hitEntity = hitEntity;
            this.hitEntityType = hitEntity != null ? hitEntity.getType().toString() : null;
        }
        
        @HostAccess.Export
        public Object getProjectile() {
            return projectile;
        }
        
        @HostAccess.Export
        public Object getHitEntity() {
            return hitEntity;
        }
    }
    
    /**
     * Context for container open events.
     */
    public static class ContainerOpenContext extends EventContext {
        @HostAccess.Export
        public final PlayerWrapper player;
        
        @HostAccess.Export
        public final WorldWrapper world;
        
        @HostAccess.Export
        public final BlockPosWrapper pos;
        
        @HostAccess.Export
        public final String containerType;
        
        public ContainerOpenContext(Player player, Level world, BlockPos pos, String containerType) {
            this.player = new PlayerWrapper(player);
            this.world = new WorldWrapper(world);
            this.pos = new BlockPosWrapper(pos);
            this.containerType = containerType;
        }
    }
}
