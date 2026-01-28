package dev.scuffi.mixin;

import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Collection;

/**
 * Mixin accessor to invoke the package-private bindTags method on Holder.Reference.
 * This is needed to bind empty tags to dynamically registered items/blocks.
 */
@Mixin(Holder.Reference.class)
public interface HolderReferenceAccessor {
    
    @Invoker("bindTags")
    void invokeBindTags(Collection<TagKey<?>> tags);
}
