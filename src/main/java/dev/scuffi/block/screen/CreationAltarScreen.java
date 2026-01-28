package dev.scuffi.block.screen;

import dev.scuffi.NotEnoughRecipes;
import dev.scuffi.block.entity.CreationAltarBlockEntity;
import dev.scuffi.block.menu.CreationAltarMenu;
import dev.scuffi.network.packet.CreationAltarProcessPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for Creation Altar GUI - matches crafting table layout.
 */
public class CreationAltarScreen extends AbstractContainerScreen<CreationAltarMenu> {
    
    // Custom creation altar texture (from src/main/resources/assets/ner/textures/gui/)
    private static final Identifier TEXTURE = Identifier.parse(NotEnoughRecipes.MOD_ID + ":textures/gui/creation_altar.png");
    private static final int[] PROCESSING_COLORS = {0xFF00AA00, 0xFF00CC00, 0xFF00FF00, 0xFF00CC00}; // Green cycle
    private static final int PROCESS_BUTTON_OFFSET_X = 95; // centered between 3x3 grid (ends at 84) and result slot (starts at 124)
    private static final int PROCESS_BUTTON_OFFSET_Y = 34;
    
    private Button processButton;
    private int colorCycleIndex = 0;
    private int colorCycleTick = 0;
    
    public CreationAltarScreen(CreationAltarMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageHeight = 166; // Standard crafting table height
        this.inventoryLabelY = this.imageHeight - 94;
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Button positioned where the crafting arrow would be (between grid and result)
        int buttonX = this.leftPos + PROCESS_BUTTON_OFFSET_X;
        int buttonY = this.topPos + PROCESS_BUTTON_OFFSET_Y;
        
        processButton = Button.builder(Component.literal("→"), button -> onButtonPress())
            .bounds(buttonX, buttonY, 18, 18) // Small square button for icon
            .build();
        
        addRenderableWidget(processButton);
        updateButtonState();
    }
    
    private void updateButtonState() {
        if (processButton == null) return;
        
        CreationAltarBlockEntity altar = menu.getAltar();
        if (altar == null) {
            processButton.active = false;
            return;
        }
        
        // Button is greyed out if:
        // - Grid is empty (IDLE with no items)
        // - Currently processing
        // - Result slot has an item waiting to be collected
        processButton.active = altar.getState() == CreationAltarBlockEntity.AltarState.IDLE 
            && !hasEmptyCraftingGrid() 
            && altar.getResultItem().isEmpty();
    }
    
    private boolean hasEmptyCraftingGrid() {
        for (int i = 0; i < 9; i++) {
            if (!menu.getCraftingGrid().getItem(i).isEmpty()) return false;
        }
        return true;
    }
    
    private void onButtonPress() {
        CreationAltarBlockEntity altar = menu.getAltar();
        if (altar != null && altar.getState() == CreationAltarBlockEntity.AltarState.IDLE && altar.getResultItem().isEmpty()) {
            CreationAltarProcessPacket.send(altar.getBlockPos());
        }
    }
    
    @Override
    public void containerTick() {
        super.containerTick();
        updateButtonState();
        
        // Update color cycle for processing animation
        CreationAltarBlockEntity altar = menu.getAltar();
        if (altar != null && altar.getState() == CreationAltarBlockEntity.AltarState.PROCESSING) {
            colorCycleTick++;
            if (colorCycleTick >= 10) { // Change color every 10 ticks (0.5 seconds)
                colorCycleTick = 0;
                colorCycleIndex = (colorCycleIndex + 1) % PROCESSING_COLORS.length;
            }
        } else {
            colorCycleIndex = 0;
            colorCycleTick = 0;
        }
    }
    
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Use the RenderPipeline-based overload (matches vanilla container screens in 1.21+).
        // Params: (pipeline, texture, x, y, u, v, width, height, texWidth, texHeight)
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
            this.leftPos, this.topPos,
            0.0f, 0.0f,
            this.imageWidth, this.imageHeight,
            256, 256
        );
        
        // Draw processing button with color animation (overlay on top of texture)
        CreationAltarBlockEntity altar = menu.getAltar();
        if (altar != null && altar.getState() == CreationAltarBlockEntity.AltarState.PROCESSING && processButton != null) {
            int buttonX = this.leftPos + PROCESS_BUTTON_OFFSET_X;
            int buttonY = this.topPos + PROCESS_BUTTON_OFFSET_Y;
            graphics.fill(buttonX, buttonY, buttonX + 18, buttonY + 18, PROCESSING_COLORS[colorCycleIndex]);
        }
    }
    
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
    
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, 
            (this.imageWidth - this.font.width(this.title)) / 2, 6, 4210752, false);
        graphics.drawString(this.font, this.playerInventoryTitle, 8, this.inventoryLabelY, 4210752, false);
    }
}
