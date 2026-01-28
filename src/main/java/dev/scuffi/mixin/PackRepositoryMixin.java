package dev.scuffi.mixin;

import dev.scuffi.NotEnoughRecipes;
import dev.scuffi.resource.DynamicResourceLoader;
import dev.scuffi.resource.DynamicResourcePack;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Mixin to inject our dynamic resource pack into Minecraft's resource loading.
 * This ensures our dynamically loaded textures and models are available to the game.
 */
@Mixin(PackRepository.class)
public class PackRepositoryMixin {
    
    @Inject(method = "openAllSelected", at = @At("RETURN"), cancellable = true)
    private void injectDynamicPack(CallbackInfoReturnable<List<PackResources>> cir) {
        NotEnoughRecipes.LOGGER.info("[PackRepositoryMixin] openAllSelected called, injecting dynamic pack");
        
        DynamicResourcePack dynamicPack = DynamicResourceLoader.getResourcePack();
        
        if (dynamicPack != null) {
            // Always inject our pack - even if empty, for debugging
            List<PackResources> packs = new ArrayList<>(cir.getReturnValue());
            packs.add(dynamicPack);
            cir.setReturnValue(packs);
            
            NotEnoughRecipes.LOGGER.info("[PackRepositoryMixin] Injected dynamic pack with {} resources", 
                dynamicPack.getResourceCount());
            NotEnoughRecipes.LOGGER.info("[PackRepositoryMixin] Pack list now has {} packs:", packs.size());
            for (PackResources pack : packs) {
                NotEnoughRecipes.LOGGER.info("[PackRepositoryMixin]   - {}", pack.packId());
            }
        } else {
            NotEnoughRecipes.LOGGER.warn("[PackRepositoryMixin] dynamicPack is null!");
        }
    }
}
