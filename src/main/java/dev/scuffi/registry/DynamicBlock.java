package dev.scuffi.registry;

import dev.scuffi.NotEnoughRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic block that can have custom drop definitions.
 */
public class DynamicBlock extends Block {
    
    private final String blockId;
    
    public DynamicBlock(Properties properties, String blockId) {
        super(properties);
        this.blockId = blockId;
    }
    
    @Override
    @NotNull
    public List<ItemStack> getDrops(@NotNull BlockState state, LootParams.@NotNull Builder builder) {
        // Check if we have custom drops defined for this block
        List<DynamicRegistryHelper.BlockDrop> drops = DynamicRegistryHelper.getBlockDrops(blockId);
        
        if (drops == null || drops.isEmpty()) {
            // No custom drops, use vanilla behavior
            return super.getDrops(state, builder);
        }
        
        // Get the tool being used (if any)
        ItemStack tool = builder.getOptionalParameter(LootContextParams.TOOL);
        ServerLevel level = builder.getLevel();
        BlockPos pos = builder.getOptionalParameter(LootContextParams.ORIGIN) != null 
            ? BlockPos.containing(builder.getOptionalParameter(LootContextParams.ORIGIN)) 
            : null;
        
        // Check if block requires correct tool
        boolean requiresCorrectTool = state.requiresCorrectToolForDrops();
        boolean hasCorrectTool = true;
        
        if (requiresCorrectTool && tool != null) {
            // Check if the tool can properly harvest this block
            hasCorrectTool = tool.isCorrectToolForDrops(state);
        }
        
        // If requires correct tool and we don't have it, drop nothing
        if (requiresCorrectTool && !hasCorrectTool) {
            NotEnoughRecipes.LOGGER.debug("Block {} requires correct tool but player doesn't have it", blockId);
            return new ArrayList<>();
        }
        
        // Build the drop list
        List<ItemStack> dropList = new ArrayList<>();
        
        for (DynamicRegistryHelper.BlockDrop drop : drops) {
            // Parse the item ID
            Identifier itemLoc = Identifier.tryParse(drop.item());
            if (itemLoc == null) {
                NotEnoughRecipes.LOGGER.warn("Invalid item ID in drops for block {}: {}", blockId, drop.item());
                continue;
            }
            
            // Get the item from registry
            Item item = BuiltInRegistries.ITEM.getValue(itemLoc);
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                NotEnoughRecipes.LOGGER.warn("Item not found in drops for block {}: {}", blockId, drop.item());
                continue;
            }
            
            // Calculate the actual count (with random range if specified)
            int count = drop.count();
            if (drop.min() > 0 && drop.max() > 0) {
                // Random range specified
                int min = Math.min(drop.min(), drop.max());
                int max = Math.max(drop.min(), drop.max());
                count = min + level.random.nextInt(max - min + 1);
            }
            
            // Check probability
            if (drop.chance() < 1.0f) {
                if (level.random.nextFloat() > drop.chance()) {
                    // Failed probability check, skip this drop
                    continue;
                }
            }
            
            // Create the item stack
            // If this is a custom NER item with stored components, apply them
            ItemStack stack;
            Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId != null && itemId.getNamespace().equals(NotEnoughRecipes.MOD_ID)) {
                // This is a custom NER item - create it with full components
                String itemName = itemId.getPath();
                stack = DynamicRegistryHelper.createItemStack(item, count);
                NotEnoughRecipes.LOGGER.debug("Block {} dropping custom NER item {} x{} with components", 
                    blockId, itemName, count);
            } else {
                // Vanilla item - just create basic stack
                stack = new ItemStack(item, count);
                NotEnoughRecipes.LOGGER.debug("Block {} dropping vanilla item {} x{}", blockId, drop.item(), count);
            }
            
            dropList.add(stack);
        }
        
        return dropList;
    }
    
    public String getBlockId() {
        return blockId;
    }
}
