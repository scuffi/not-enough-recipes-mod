package dev.scuffi.scripting.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.graalvm.polyglot.HostAccess;

/**
 * JavaScript-friendly wrapper for Minecraft Player objects.
 * Provides simplified access to common player properties and methods.
 */
public class PlayerWrapper {
    
    private final Player player;
    
    public PlayerWrapper(Player player) {
        this.player = player;
    }
    
    // === Basic Properties ===
    
    @HostAccess.Export
    public String getName() {
        return player.getName().getString();
    }
    
    @HostAccess.Export
    public String getUuid() {
        return player.getUUID().toString();
    }
    
    // === Health and Status ===
    
    @HostAccess.Export
    public float getHealth() {
        return player.getHealth();
    }
    
    @HostAccess.Export
    public float getMaxHealth() {
        return player.getMaxHealth();
    }
    
    @HostAccess.Export
    public float getFoodLevel() {
        return player.getFoodData().getFoodLevel();
    }
    
    @HostAccess.Export
    public float getSaturation() {
        return player.getFoodData().getSaturationLevel();
    }
    
    @HostAccess.Export
    public boolean isCreative() {
        return player.getAbilities().instabuild;
    }
    
    @HostAccess.Export
    public boolean isSurvival() {
        return !player.isCreative() && !player.isSpectator();
    }
    
    @HostAccess.Export
    public boolean isSpectator() {
        return player.isSpectator();
    }
    
    @HostAccess.Export
    public boolean isAlive() {
        return player.isAlive();
    }
    
    @HostAccess.Export
    public boolean isOnGround() {
        return player.onGround();
    }
    
    @HostAccess.Export
    public boolean isSneaking() {
        return player.isCrouching();
    }
    
    @HostAccess.Export
    public boolean isSprinting() {
        return player.isSprinting();
    }
    
    @HostAccess.Export
    public boolean isSwimming() {
        return player.isSwimming();
    }
    
    @HostAccess.Export
    public boolean isOnFire() {
        return player.isOnFire();
    }
    
    // === Position and Movement ===
    
    @HostAccess.Export
    public double getX() {
        return player.getX();
    }
    
    @HostAccess.Export
    public double getY() {
        return player.getY();
    }
    
    @HostAccess.Export
    public double getZ() {
        return player.getZ();
    }
    
    @HostAccess.Export
    public BlockPosWrapper getBlockPos() {
        return new BlockPosWrapper(player.blockPosition());
    }
    
    @HostAccess.Export
    public Vec3 getPosition() {
        return player.position();
    }
    
    @HostAccess.Export
    public float getYaw() {
        return player.getYRot();
    }
    
    @HostAccess.Export
    public float getPitch() {
        return player.getXRot();
    }
    
    // === Experience ===
    
    @HostAccess.Export
    public int getExperienceLevel() {
        return player.experienceLevel;
    }
    
    @HostAccess.Export
    public int getTotalExperience() {
        return player.totalExperience;
    }
    
    // === Direct Java Access ===
    
    /**
     * Gets the underlying Minecraft Player object for direct Java API access.
     * This allows scripts to call any Player method directly.
     */
    @HostAccess.Export
    public Player getJavaObject() {
        return player;
    }
    
    // === Convenience Methods ===
    
    @HostAccess.Export
    public void heal(float amount) {
        player.heal(amount);
    }
    
    @HostAccess.Export
    public void setHealth(float health) {
        player.setHealth(health);
    }
    
    @HostAccess.Export
    public void giveExperiencePoints(int points) {
        player.giveExperiencePoints(points);
    }
    
    @HostAccess.Export
    public void giveExperienceLevels(int levels) {
        player.giveExperienceLevels(levels);
    }
}
