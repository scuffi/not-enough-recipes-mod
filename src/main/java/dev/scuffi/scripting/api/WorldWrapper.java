package dev.scuffi.scripting.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.graalvm.polyglot.HostAccess;

/**
 * JavaScript-friendly wrapper for Minecraft Level (World) objects.
 * Provides simplified access to common world properties and methods.
 */
public class WorldWrapper {
    
    private final Level world;
    
    public WorldWrapper(Level world) {
        this.world = world;
    }
    
    // === Basic Properties ===
    
    @HostAccess.Export
    public String getDimensionKey() {
        return world.dimension().registry().toString();
    }
    
    @HostAccess.Export
    public boolean isClientSide() {
        return world.isClientSide();
    }
    
    @HostAccess.Export
    public boolean isServerSide() {
        return !world.isClientSide();
    }
    
    // === Time and Weather ===
    
    @HostAccess.Export
    public long getGameTime() {
        return world.getGameTime();
    }
    
    @HostAccess.Export
    public long getDayTime() {
        return world.getDayTime();
    }
    
    @HostAccess.Export
    public boolean isDay() {
        long dayTime = world.getDayTime() % 24000;
        return dayTime >= 0 && dayTime < 13000;
    }
    
    @HostAccess.Export
    public boolean isNight() {
        long dayTime = world.getDayTime() % 24000;
        return dayTime >= 13000 && dayTime < 24000;
    }
    
    @HostAccess.Export
    public boolean isRaining() {
        return world.isRaining();
    }
    
    @HostAccess.Export
    public boolean isThundering() {
        return world.isThundering();
    }
    
    @HostAccess.Export
    public float getRainLevel() {
        return world.getRainLevel(1.0f);
    }
    
    // === Block Access ===
    
    @HostAccess.Export
    public BlockState getBlockState(BlockPos pos) {
        return world.getBlockState(pos);
    }
    
    @HostAccess.Export
    public BlockState getBlockStateAt(int x, int y, int z) {
        return world.getBlockState(new BlockPos(x, y, z));
    }
    
    @HostAccess.Export
    public String getBlockId(BlockPos pos) {
        return world.getBlockState(pos).getBlock().getName().getString();
    }
    
    @HostAccess.Export
    public String getBlockIdAt(int x, int y, int z) {
        return world.getBlockState(new BlockPos(x, y, z)).getBlock().getName().getString();
    }
    
    @HostAccess.Export
    public boolean isAir(BlockPos pos) {
        return world.isEmptyBlock(pos);
    }
    
    @HostAccess.Export
    public boolean isAirAt(int x, int y, int z) {
        return world.isEmptyBlock(new BlockPos(x, y, z));
    }
    
    // === Block Manipulation ===
    
    @HostAccess.Export
    public boolean destroyBlock(BlockPos pos, boolean dropItems) {
        return world.destroyBlock(pos, dropItems);
    }
    
    @HostAccess.Export
    public boolean destroyBlockAt(int x, int y, int z, boolean dropItems) {
        return world.destroyBlock(new BlockPos(x, y, z), dropItems);
    }
    
    @HostAccess.Export
    public boolean setBlock(BlockPos pos, BlockState state) {
        return world.setBlock(pos, state, 3);
    }
    
    // === Direct Java Access ===
    
    /**
     * Gets the underlying Minecraft Level object for direct Java API access.
     * This allows scripts to call any Level method directly.
     */
    @HostAccess.Export
    public Level getJavaObject() {
        return world;
    }
}
