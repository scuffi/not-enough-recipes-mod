package dev.scuffi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.scuffi.block.entity.CreationAltarBlockEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Commands for managing Creation Altar state.
 */
public class CreationAltarCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ner")
            .then(Commands.literal("altar")
                .then(Commands.literal("complete")
                    .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(CreationAltarCommand::complete)))
                .then(Commands.literal("reset")
                    .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(CreationAltarCommand::reset)))
                .then(Commands.literal("setstate")
                    .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("state", StringArgumentType.word())
                            .suggests((context, builder) -> {
                                builder.suggest("idle");
                                builder.suggest("processing");
                                builder.suggest("complete");
                                return builder.buildFuture();
                            })
                            .executes(CreationAltarCommand::setState))))));
    }
    
    private static int complete(CommandContext<CommandSourceStack> context) {
        try {
            CommandSourceStack source = context.getSource();
            BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
            
            var be = source.getLevel().getBlockEntity(pos);
            if (!(be instanceof CreationAltarBlockEntity altar)) {
                source.sendFailure(Component.literal("No Creation Altar found at " + pos));
                return 0;
            }
            
            if (altar.getState() != CreationAltarBlockEntity.AltarState.PROCESSING) {
                source.sendFailure(Component.literal("Altar must be in PROCESSING state. Current: " + altar.getState()));
                return 0;
            }
            
            ItemStack result = ItemStack.EMPTY;
            for (int i = 0; i < 9; i++) {
                ItemStack stack = altar.getCraftingGrid().getItem(i);
                if (!stack.isEmpty()) {
                    result = stack.copy();
                    result.setCount(1);
                    break;
                }
            }
            
            if (result.isEmpty()) {
                result = new ItemStack(Items.DIAMOND);
            }
            
            altar.completeProcessing(result);
            source.sendSuccess(() -> Component.literal("Completed altar at " + pos), true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }
    
    private static int reset(CommandContext<CommandSourceStack> context) {
        try {
            CommandSourceStack source = context.getSource();
            BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
            
            var be = source.getLevel().getBlockEntity(pos);
            if (!(be instanceof CreationAltarBlockEntity altar)) {
                source.sendFailure(Component.literal("No Creation Altar found at " + pos));
                return 0;
            }
            
            altar.reset();
            source.sendSuccess(() -> Component.literal("Reset altar at " + pos), true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }
    
    private static int setState(CommandContext<CommandSourceStack> context) {
        try {
            CommandSourceStack source = context.getSource();
            BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
            String stateStr = StringArgumentType.getString(context, "state");
            
            var be = source.getLevel().getBlockEntity(pos);
            if (!(be instanceof CreationAltarBlockEntity altar)) {
                source.sendFailure(Component.literal("No Creation Altar found at " + pos));
                return 0;
            }
            
            CreationAltarBlockEntity.AltarState state;
            try {
                state = CreationAltarBlockEntity.AltarState.valueOf(stateStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                source.sendFailure(Component.literal("Invalid state: " + stateStr));
                return 0;
            }
            
            altar.setState(state);
            source.sendSuccess(() -> Component.literal("Set altar to: " + state), true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }
}
