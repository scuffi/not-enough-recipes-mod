package dev.scuffi.block.menu;

import dev.scuffi.block.entity.CreationAltarBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for Creation Altar GUI.
 */
public class CreationAltarMenu extends AbstractContainerMenu {
    
    public static MenuType<CreationAltarMenu> TYPE;
    
    private final CreationAltarBlockEntity altar;
    private final Container craftingGrid;
    
    // Constructor for client-side (from packet with BlockPos)
    public CreationAltarMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, getBlockEntity(playerInventory, pos));
    }
    
    // Constructor for server-side
    public CreationAltarMenu(int containerId, Inventory playerInventory, CreationAltarBlockEntity altar) {
        this(containerId, playerInventory, altar, altar != null ? altar.getCraftingGrid() : new SimpleContainer(9));
    }
    
    private static CreationAltarBlockEntity getBlockEntity(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof CreationAltarBlockEntity altar) {
            return altar;
        }
        return null;
    }
    
    private CreationAltarMenu(int containerId, Inventory playerInventory, CreationAltarBlockEntity altar, Container craftingGrid) {
        super(TYPE, containerId);
        this.altar = altar;
        this.craftingGrid = craftingGrid;
        
        checkContainerSize(craftingGrid, 9);
        
        // Crafting grid (3x3) - positioned like crafting table
        int craftingStartX = 30;
        int craftingStartY = 17;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new Slot(craftingGrid, col + row * 3, craftingStartX + col * 18, craftingStartY + row * 18));
            }
        }
        
        // Result slot - positioned like crafting table (to the right of arrow)
        addSlot(new ResultSlot(124, 35, this));
        
        // Player inventory - standard crafting table positioning
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        
        // Hotbar - standard crafting table positioning
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }
    
    public CreationAltarBlockEntity getAltar() {
        return altar;
    }
    
    public Container getCraftingGrid() {
        return craftingGrid;
    }
    
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            
            if (index == 9) { // Result slot
                if (altar != null && altar.getState() == CreationAltarBlockEntity.AltarState.COMPLETE) {
                    if (!moveItemStackTo(stack, 10, 46, true)) return ItemStack.EMPTY;
                    altar.collectResult();
                }
            } else if (index < 9) { // Crafting grid
                if (!moveItemStackTo(stack, 10, 46, true)) return ItemStack.EMPTY;
            } else { // Player inventory
                if (!moveItemStackTo(stack, 0, 9, false)) return ItemStack.EMPTY;
            }
            
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        
        return result;
    }
    
    @Override
    public boolean stillValid(Player player) {
        if (altar != null) {
            return altar.getLevel() != null && 
                   player.distanceToSqr(altar.getBlockPos().getCenter()) <= 64.0;
        }
        return true;
    }
    
    private static class ResultSlot extends Slot {
        private final CreationAltarMenu menu;
        
        public ResultSlot(int x, int y, CreationAltarMenu menu) {
            super(new SimpleContainer(1), 0, x, y);
            this.menu = menu;
        }
        
        @Override
        public boolean mayPickup(Player player) {
            return menu.altar != null && menu.altar.getState() == CreationAltarBlockEntity.AltarState.COMPLETE;
        }
        
        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
        
        @Override
        public ItemStack getItem() {
            return menu.altar != null ? menu.altar.getResultItem() : ItemStack.EMPTY;
        }
        
        @Override
        public void set(ItemStack stack) {
            // Do nothing
        }
        
        @Override
        public void onTake(Player player, ItemStack stack) {
            if (menu.altar != null) {
                menu.altar.collectResult();
            }
        }
    }
}
