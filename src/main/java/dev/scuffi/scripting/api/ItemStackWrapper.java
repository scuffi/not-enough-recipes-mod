package dev.scuffi.scripting.api;

import dev.scuffi.NotEnoughRecipes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.graalvm.polyglot.HostAccess;

/**
 * JavaScript-friendly wrapper for Minecraft ItemStack objects.
 * Provides simplified access to common item properties.
 */
public class ItemStackWrapper {
    
    private final ItemStack stack;
    
    public ItemStackWrapper(ItemStack stack) {
        this.stack = stack;
    }
    
    // === Basic Properties ===
    
    @HostAccess.Export
    public String getId() {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null ? id.toString() : "unknown";
    }
    
    @HostAccess.Export
    public String getNamespace() {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null ? id.getNamespace() : "unknown";
    }
    
    @HostAccess.Export
    public String getPath() {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null ? id.getPath() : "unknown";
    }
    
    @HostAccess.Export
    public String getDisplayName() {
        return stack.getHoverName().getString();
    }
    
    @HostAccess.Export
    public int getCount() {
        return stack.getCount();
    }
    
    @HostAccess.Export
    public int getMaxStackSize() {
        return stack.getMaxStackSize();
    }
    
    @HostAccess.Export
    public boolean isEmpty() {
        return stack.isEmpty();
    }
    
    // === Durability ===
    
    @HostAccess.Export
    public boolean isDamageable() {
        return stack.isDamageableItem();
    }
    
    @HostAccess.Export
    public int getDamage() {
        return stack.getDamageValue();
    }
    
    @HostAccess.Export
    public int getMaxDamage() {
        return stack.getMaxDamage();
    }
    
    @HostAccess.Export
    public int getRemainingDurability() {
        return stack.getMaxDamage() - stack.getDamageValue();
    }
    
    // === NER Custom Item Checks ===
    
    @HostAccess.Export
    public boolean isCustomItem() {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getNamespace().equals(NotEnoughRecipes.MOD_ID);
    }
    
    @HostAccess.Export
    public String getCustomItemId() {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null && id.getNamespace().equals(NotEnoughRecipes.MOD_ID)) {
            return id.getPath();
        }
        return null;
    }
    
    // === Direct Java Access ===
    
    /**
     * Gets the underlying Minecraft ItemStack object for direct Java API access.
     * This allows scripts to call any ItemStack method directly.
     */
    @HostAccess.Export
    public ItemStack getJavaObject() {
        return stack;
    }
}
