package dev.scuffi.mixin;

import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Additional accessor for MappedRegistry to access intrusive holder maps.
 * Required for proper registration of items/blocks that use intrusive holders.
 */
@Mixin(MappedRegistry.class)
public interface MappedRegistryIntrusive {
    
    @Accessor("unregisteredIntrusiveHolders")
    Map<?, Holder.Reference<?>> getUnregisteredIntrusiveHolders();
    
    @Accessor("unregisteredIntrusiveHolders")
    void setUnregisteredIntrusiveHolders(Map<?, Holder.Reference<?>> holders);
    
    @Accessor("toId")
    Reference2IntMap<?> getToId();
}
