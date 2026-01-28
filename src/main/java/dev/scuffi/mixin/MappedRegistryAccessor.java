package dev.scuffi.mixin;

import net.minecraft.core.MappedRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Mixin accessor to get and set the frozen state of a MappedRegistry.
 * This allows us to unfreeze the registry at runtime to register new entries.
 */
@Mixin(MappedRegistry.class)
public interface MappedRegistryAccessor {
    
    @Accessor("frozen")
    boolean isFrozen();
    
    @Accessor("frozen")
    void setFrozen(boolean frozen);
}
