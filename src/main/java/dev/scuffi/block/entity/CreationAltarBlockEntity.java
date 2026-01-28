package dev.scuffi.block.entity;

import dev.scuffi.NotEnoughRecipes;
import dev.scuffi.block.menu.CreationAltarMenu;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Block Entity for Creation Altar.
 */
public class CreationAltarBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos> {
    
    public static BlockEntityType<CreationAltarBlockEntity> TYPE;
    
    private final SimpleContainer craftingGrid = new SimpleContainer(9);
    private ItemStack resultItem = ItemStack.EMPTY;
    
    private AltarState state = AltarState.IDLE;
    private int rotationTicks = 0;
    private int itemCycleTimer = 0;
    private int currentDisplayIndex = 0;
    
    private final List<ItemStack> displayItems = new ArrayList<>();
    
    public enum AltarState {
        IDLE, PROCESSING, COMPLETE
    }
    
    public CreationAltarBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE, pos, state);
        initializeDisplayItems();
    }
    
    private void initializeDisplayItems() {
        displayItems.add(new ItemStack(Items.DIAMOND));
        displayItems.add(new ItemStack(Items.EMERALD));
        displayItems.add(new ItemStack(Items.GOLD_INGOT));
        displayItems.add(new ItemStack(Items.NETHERITE_INGOT));
    }
    
    public static void serverTick(Level level, BlockPos pos, BlockState state, CreationAltarBlockEntity altar) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        
        altar.rotationTicks++;
        
        if (altar.state == AltarState.PROCESSING) {
            altar.itemCycleTimer++;
            if (altar.itemCycleTimer >= 5) {
                altar.itemCycleTimer = 0;
                altar.currentDisplayIndex = (altar.currentDisplayIndex + 1) % altar.displayItems.size();
                altar.setChanged();
            }
            
            spawnMatrixParticles(serverLevel, pos);
        }
    }
    
    private static void spawnMatrixParticles(ServerLevel level, BlockPos pos) {
        int particleCount = 2 + level.random.nextInt(2);
        
        for (int i = 0; i < particleCount; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double radius = 0.5 + level.random.nextDouble() * 1.0;
            double x = pos.getX() + 0.5 + Math.cos(angle) * radius;
            double y = pos.getY() + 1.0 + level.random.nextDouble() * 2.0;
            double z = pos.getZ() + 0.5 + Math.sin(angle) * radius;
            
            double vx = (level.random.nextDouble() - 0.5) * 0.02;
            double vy = -0.05 - level.random.nextDouble() * 0.05;
            double vz = (level.random.nextDouble() - 0.5) * 0.02;
            
            level.sendParticles(ParticleTypes.ENCHANT, x, y, z, 1, vx, vy, vz, 0.05);
        }
    }
    
    public void startProcessing() {
        if (state == AltarState.IDLE && !hasEmptyCraftingGrid() && resultItem.isEmpty()) {
            // Consume items from the crafting grid
            consumeCraftingItems();
            
            state = AltarState.PROCESSING;
            itemCycleTimer = 0;
            setChanged();
            syncToClients();
        }
    }
    
    /**
     * Consumes items from the crafting grid.
     * Takes 1 of each item if stacked, or all items if single.
     */
    private void consumeCraftingItems() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = craftingGrid.getItem(i);
            if (!stack.isEmpty()) {
                if (stack.getCount() > 1) {
                    // Take only 1 from the stack
                    stack.shrink(1);
                } else {
                    // Take the entire item
                    craftingGrid.setItem(i, ItemStack.EMPTY);
                }
            }
        }
        setChanged();
    }
    
    public void completeProcessing(ItemStack result) {
        if (state == AltarState.PROCESSING) {
            state = AltarState.COMPLETE;
            resultItem = result.copy();
            setChanged();
            syncToClients();
        }
    }
    
    public void reset() {
        state = AltarState.IDLE;
        resultItem = ItemStack.EMPTY;
        craftingGrid.clearContent();
        setChanged();
        syncToClients();
    }
    
    public void collectResult() {
        if (state == AltarState.COMPLETE && !resultItem.isEmpty()) {
            // Clear the result item (crafting grid was already consumed at start)
            resultItem = ItemStack.EMPTY;
            state = AltarState.IDLE;
            setChanged();
            syncToClients();
        }
    }
    
    private boolean hasEmptyCraftingGrid() {
        for (int i = 0; i < 9; i++) {
            if (!craftingGrid.getItem(i).isEmpty()) return false;
        }
        return true;
    }
    
    public SimpleContainer getCraftingGrid() {
        return craftingGrid;
    }
    
    public ItemStack getResultItem() {
        return resultItem;
    }
    
    public AltarState getState() {
        return state;
    }
    
    public void setState(AltarState newState) {
        this.state = newState;
        setChanged();
        syncToClients();
    }
    
    public int getRotationTicks() {
        return rotationTicks;
    }
    
    public ItemStack getCurrentDisplayItem() {
        if (displayItems.isEmpty()) return ItemStack.EMPTY;
        return displayItems.get(currentDisplayIndex);
    }
    
    private void syncToClients() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
    
    public void dropContents(Level level, BlockPos pos) {
        for (int i = 0; i < 9; i++) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), craftingGrid.getItem(i));
        }
        if (!resultItem.isEmpty()) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), resultItem);
        }
    }
    
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("State", state.name());
        tag.putInt("RotationTicks", rotationTicks);
        // TODO: Save inventory items properly
    }
    
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("State")) {
            try {
                tag.getString("State").ifPresent(stateStr -> {
                    try {
                        state = AltarState.valueOf(stateStr);
                    } catch (Exception e) {
                        state = AltarState.IDLE;
                    }
                });
            } catch (Exception e) {
                state = AltarState.IDLE;
            }
        }
        rotationTicks = tag.getInt("RotationTicks").orElse(0);
        // TODO: Load inventory items properly
    }
    
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }
    
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
    
    @Override
    public Component getDisplayName() {
        return Component.literal("Creation Altar");
    }
    
    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CreationAltarMenu(containerId, playerInventory, this);
    }
    
    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return this.worldPosition;
    }
}
