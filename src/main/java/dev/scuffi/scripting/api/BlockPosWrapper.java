package dev.scuffi.scripting.api;

import net.minecraft.core.BlockPos;
import org.graalvm.polyglot.HostAccess;

/**
 * JavaScript-friendly wrapper for Minecraft BlockPos objects.
 * Provides simplified access to block positions.
 */
public class BlockPosWrapper {
    
    private final BlockPos pos;
    
    public BlockPosWrapper(BlockPos pos) {
        this.pos = pos;
    }
    
    public BlockPosWrapper(int x, int y, int z) {
        this.pos = new BlockPos(x, y, z);
    }
    
    // === Coordinates ===
    
    @HostAccess.Export
    public int getX() {
        return pos.getX();
    }
    
    @HostAccess.Export
    public int getY() {
        return pos.getY();
    }
    
    @HostAccess.Export
    public int getZ() {
        return pos.getZ();
    }
    
    // === Offsets ===
    
    @HostAccess.Export
    public BlockPos offset(int x, int y, int z) {
        return pos.offset(x, y, z);
    }
    
    @HostAccess.Export
    public BlockPos above() {
        return pos.above();
    }
    
    @HostAccess.Export
    public BlockPos above(int distance) {
        return pos.above(distance);
    }
    
    @HostAccess.Export
    public BlockPos below() {
        return pos.below();
    }
    
    @HostAccess.Export
    public BlockPos below(int distance) {
        return pos.below(distance);
    }
    
    @HostAccess.Export
    public BlockPos north() {
        return pos.north();
    }
    
    @HostAccess.Export
    public BlockPos south() {
        return pos.south();
    }
    
    @HostAccess.Export
    public BlockPos east() {
        return pos.east();
    }
    
    @HostAccess.Export
    public BlockPos west() {
        return pos.west();
    }
    
    // === Distance ===
    
    @HostAccess.Export
    public double distanceTo(BlockPos other) {
        return Math.sqrt(pos.distSqr(other));
    }
    
    @HostAccess.Export
    public double distanceToPos(int x, int y, int z) {
        return Math.sqrt(pos.distSqr(new BlockPos(x, y, z)));
    }
    
    // === Direct Java Access ===
    
    /**
     * Gets the underlying Minecraft BlockPos object for direct Java API access.
     * This allows scripts to call any BlockPos method directly.
     */
    @HostAccess.Export
    public BlockPos getJavaObject() {
        return pos;
    }
    
    @Override
    public String toString() {
        return String.format("BlockPos(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ());
    }
}
