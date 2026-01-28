package dev.scuffi.scripting.api;

import dev.scuffi.NotEnoughRecipes;
import dev.scuffi.registry.DynamicRegistryHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.InteractionHand;
import org.graalvm.polyglot.HostAccess;

/**
 * Main helper API exported to JavaScript as global `NER` object.
 * Provides convenient methods for common operations in scripts.
 */
public class NER {
    
    // === Item Checks ===
    
    /**
     * Checks if a player is holding a specific item in either hand.
     */
    @HostAccess.Export
    public boolean isHolding(Player player, String itemId) {
        return isHolding(player, itemId, "MAIN_HAND") || isHolding(player, itemId, "OFF_HAND");
    }
    
    /**
     * Checks if a player is holding a specific item in either hand (wrapper overload).
     */
    @HostAccess.Export
    public boolean isHolding(PlayerWrapper player, String itemId) {
        return isHolding(player.getJavaObject(), itemId);
    }
    
    /**
     * Checks if a player is holding a specific item in a specific hand.
     */
    @HostAccess.Export
    public boolean isHolding(Player player, String itemId, String hand) {
        InteractionHand interactionHand = hand.equals("OFF_HAND") ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack stack = player.getItemInHand(interactionHand);
        return isCustomItem(stack, itemId);
    }
    
    /**
     * Checks if a player is holding a specific item in a specific hand (wrapper overload).
     */
    @HostAccess.Export
    public boolean isHolding(PlayerWrapper player, String itemId, String hand) {
        return isHolding(player.getJavaObject(), itemId, hand);
    }
    
    /**
     * Checks if a player is wearing a specific item in an armor slot.
     */
    @HostAccess.Export
    public boolean isWearing(Player player, String itemId, String slot) {
        EquipmentSlot equipmentSlot = switch (slot.toUpperCase()) {
            case "HEAD", "HELMET" -> EquipmentSlot.HEAD;
            case "CHEST", "CHESTPLATE" -> EquipmentSlot.CHEST;
            case "LEGS", "LEGGINGS" -> EquipmentSlot.LEGS;
            case "FEET", "BOOTS" -> EquipmentSlot.FEET;
            default -> null;
        };
        
        if (equipmentSlot == null) {
            NotEnoughRecipes.LOGGER.warn("Invalid equipment slot: {}", slot);
            return false;
        }
        
        ItemStack stack = player.getItemBySlot(equipmentSlot);
        return isCustomItem(stack, itemId);
    }
    
    /**
     * Checks if a player is wearing a specific item in an armor slot (wrapper overload).
     */
    @HostAccess.Export
    public boolean isWearing(PlayerWrapper player, String itemId, String slot) {
        return isWearing(player.getJavaObject(), itemId, slot);
    }
    
    /**
     * Checks if a player has a specific item in their inventory.
     */
    @HostAccess.Export
    public boolean hasInInventory(Player player, String itemId) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isCustomItem(stack, itemId)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Checks if a player has a specific item in their inventory (wrapper overload).
     */
    @HostAccess.Export
    public boolean hasInInventory(PlayerWrapper player, String itemId) {
        return hasInInventory(player.getJavaObject(), itemId);
    }
    
    /**
     * Gets the item stack in a specific hand.
     */
    @HostAccess.Export
    public ItemStack getHeldItem(Player player, String hand) {
        InteractionHand interactionHand = hand.equals("OFF_HAND") ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        return player.getItemInHand(interactionHand);
    }
    
    /**
     * Gets the item stack in a specific hand (wrapper overload).
     */
    @HostAccess.Export
    public ItemStack getHeldItem(PlayerWrapper player, String hand) {
        return getHeldItem(player.getJavaObject(), hand);
    }
    
    // === Custom Item Utilities ===
    
    /**
     * Checks if an ItemStack is a specific custom NER item.
     */
    @HostAccess.Export
    public boolean isCustomItem(ItemStack stack, String itemId) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return false;
        }
        
        // Check if it's an NER item with the matching path
        return id.getNamespace().equals(NotEnoughRecipes.MOD_ID) && id.getPath().equals(itemId);
    }
    
    /**
     * Checks if an ItemStack is a specific custom NER item (wrapper overload).
     */
    @HostAccess.Export
    public boolean isCustomItem(ItemStackWrapper stack, String itemId) {
        return isCustomItem(stack.getJavaObject(), itemId);
    }
    
    /**
     * Gets the custom item ID from an ItemStack (returns null if not a custom item).
     */
    @HostAccess.Export
    public String getCustomItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null && id.getNamespace().equals(NotEnoughRecipes.MOD_ID)) {
            return id.getPath();
        }
        
        return null;
    }
    
    /**
     * Gets the custom item ID from an ItemStack (wrapper overload).
     */
    @HostAccess.Export
    public String getCustomItemId(ItemStackWrapper stack) {
        return getCustomItemId(stack.getJavaObject());
    }
    
    /**
     * Checks if an ItemStack is any custom NER item.
     */
    @HostAccess.Export
    public boolean isAnyCustomItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getNamespace().equals(NotEnoughRecipes.MOD_ID);
    }
    
    /**
     * Checks if an ItemStack is any custom NER item (wrapper overload).
     */
    @HostAccess.Export
    public boolean isAnyCustomItem(ItemStackWrapper stack) {
        return isAnyCustomItem(stack.getJavaObject());
    }
    
    // === World Manipulation ===
    
    /**
     * Spawns a particle at a position.
     */
    @HostAccess.Export
    public void spawnParticle(Level world, Vec3 pos, String particleType) {
        if (world.isClientSide()) return; // Only spawn on server
        
        if (!(world instanceof ServerLevel serverLevel)) return;
        
        try {
            // Parse particle type
            ParticleOptions particle = getParticleType(particleType);
            if (particle != null) {
                serverLevel.sendParticles(particle, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            }
        } catch (Exception e) {
            NotEnoughRecipes.LOGGER.warn("Failed to spawn particle '{}': {}", particleType, e.getMessage());
        }
    }
    
    /**
     * Spawns a particle at a position (WorldWrapper overload).
     */
    @HostAccess.Export
    public void spawnParticle(WorldWrapper world, Vec3 pos, String particleType) {
        spawnParticle(world.getJavaObject(), pos, particleType);
    }
    
    /**
     * Spawns a particle at a position (BlockPosWrapper overload).
     */
    @HostAccess.Export
    public void spawnParticle(Level world, BlockPosWrapper pos, String particleType) {
        BlockPos blockPos = pos.getJavaObject();
        spawnParticle(world, new Vec3(blockPos.getX(), blockPos.getY(), blockPos.getZ()), particleType);
    }
    
    /**
     * Spawns a particle at a position (both wrappers overload).
     */
    @HostAccess.Export
    public void spawnParticle(WorldWrapper world, BlockPosWrapper pos, String particleType) {
        spawnParticle(world.getJavaObject(), pos, particleType);
    }
    
    /**
     * Plays a sound at a position.
     */
    @HostAccess.Export
    public void playSound(Level world, Vec3 pos, String soundId, float volume, float pitch) {
        if (world.isClientSide()) return; // Only play on server
        
        try {
            Identifier id = Identifier.tryParse(soundId);
            if (id != null) {
                var soundRef = BuiltInRegistries.SOUND_EVENT.getValue(id);
                if (soundRef != null) {
                    var soundHolder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(soundRef);
                    world.playSound(null, pos.x, pos.y, pos.z, soundHolder, SoundSource.PLAYERS, volume, pitch);
                }
            }
        } catch (Exception e) {
            NotEnoughRecipes.LOGGER.warn("Failed to play sound '{}': {}", soundId, e.getMessage());
        }
    }
    
    /**
     * Plays a sound at a position (WorldWrapper overload).
     */
    @HostAccess.Export
    public void playSound(WorldWrapper world, Vec3 pos, String soundId, float volume, float pitch) {
        playSound(world.getJavaObject(), pos, soundId, volume, pitch);
    }
    
    /**
     * Plays a sound at a position (BlockPosWrapper overload).
     */
    @HostAccess.Export
    public void playSound(Level world, BlockPosWrapper pos, String soundId, float volume, float pitch) {
        BlockPos blockPos = pos.getJavaObject();
        playSound(world, new Vec3(blockPos.getX(), blockPos.getY(), blockPos.getZ()), soundId, volume, pitch);
    }
    
    /**
     * Plays a sound at a position (both wrappers overload).
     */
    @HostAccess.Export
    public void playSound(WorldWrapper world, BlockPosWrapper pos, String soundId, float volume, float pitch) {
        playSound(world.getJavaObject(), pos, soundId, volume, pitch);
    }
    
    // === Player Utilities ===
    
    /**
     * Gives an item to a player.
     */
    @HostAccess.Export
    public void giveItem(Player player, String itemId, int count) {
        try {
            // Try to parse as full ID (namespace:path)
            Identifier id = Identifier.tryParse(itemId);
            if (id == null) {
                // If no namespace, assume it's an NER item
                id = Identifier.tryParse(NotEnoughRecipes.MOD_ID + ":" + itemId);
            }
            
            if (id != null) {
                Item item = BuiltInRegistries.ITEM.getValue(id);
                if (item != null && item != net.minecraft.world.item.Items.AIR) {
                    ItemStack stack;
                    // If it's an NER custom item, create with components
                    if (id.getNamespace().equals(NotEnoughRecipes.MOD_ID)) {
                        stack = DynamicRegistryHelper.createItemStack(item, count);
                    } else {
                        stack = new ItemStack(item, count);
                    }
                    player.addItem(stack);
                } else {
                    NotEnoughRecipes.LOGGER.warn("Item not found: {}", itemId);
                }
            }
        } catch (Exception e) {
            NotEnoughRecipes.LOGGER.warn("Failed to give item '{}': {}", itemId, e.getMessage());
        }
    }
    
    /**
     * Gives an item to a player (wrapper overload).
     */
    @HostAccess.Export
    public void giveItem(PlayerWrapper player, String itemId, int count) {
        giveItem(player.getJavaObject(), itemId, count);
    }
    
    /**
     * Sends a message to a player.
     */
    @HostAccess.Export
    public void sendMessage(Player player, String message) {
        player.displayClientMessage(Component.literal(message), false);
    }
    
    /**
     * Sends a message to a player (wrapper overload).
     */
    @HostAccess.Export
    public void sendMessage(PlayerWrapper player, String message) {
        sendMessage(player.getJavaObject(), message);
    }
    
    /**
     * Applies a potion effect to a player.
     */
    @HostAccess.Export
    public void applyEffect(Player player, String effectId, int duration, int amplifier) {
        try {
            Identifier id = Identifier.tryParse(effectId);
            if (id != null) {
                MobEffect effect = BuiltInRegistries.MOB_EFFECT.getValue(id);
                if (effect != null) {
                    player.addEffect(new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect), 
                            duration, amplifier));
                } else {
                    NotEnoughRecipes.LOGGER.warn("Effect not found: {}", effectId);
                }
            }
        } catch (Exception e) {
            NotEnoughRecipes.LOGGER.warn("Failed to apply effect '{}': {}", effectId, e.getMessage());
        }
    }
    
    /**
     * Applies a potion effect to a player (wrapper overload).
     */
    @HostAccess.Export
    public void applyEffect(PlayerWrapper player, String effectId, int duration, int amplifier) {
        applyEffect(player.getJavaObject(), effectId, duration, amplifier);
    }
    
    // === Logging ===
    
    /**
     * Logs a message to the console.
     */
    @HostAccess.Export
    public void log(String message) {
        NotEnoughRecipes.LOGGER.info("[Script] {}", message);
    }
    
    /**
     * Logs a debug message to the console.
     */
    @HostAccess.Export
    public void debug(String message) {
        NotEnoughRecipes.LOGGER.debug("[Script] {}", message);
    }
    
    // === Helper Methods ===
    
    private static ParticleOptions getParticleType(String name) {
        // Handle common particle types
        return switch (name.toLowerCase().replace("minecraft:", "")) {
            case "flame" -> ParticleTypes.FLAME;
            case "smoke" -> ParticleTypes.SMOKE;
            case "heart" -> ParticleTypes.HEART;
            case "enchant" -> ParticleTypes.ENCHANT;
            case "portal" -> ParticleTypes.PORTAL;
            case "explosion" -> ParticleTypes.EXPLOSION;
            case "cloud" -> ParticleTypes.CLOUD;
            case "bubble" -> ParticleTypes.BUBBLE;
            case "splash" -> ParticleTypes.SPLASH;
            case "crit" -> ParticleTypes.CRIT;
            case "enchanted_hit" -> ParticleTypes.ENCHANTED_HIT;
            case "happy_villager" -> ParticleTypes.HAPPY_VILLAGER;
            case "angry_villager" -> ParticleTypes.ANGRY_VILLAGER;
            case "end_rod" -> ParticleTypes.END_ROD;
            case "dragon_breath" -> ParticleTypes.DRIPPING_WATER;
            default -> {
                NotEnoughRecipes.LOGGER.warn("Unknown particle type: {}", name);
                yield ParticleTypes.FLAME;
            }
        };
    }
}
