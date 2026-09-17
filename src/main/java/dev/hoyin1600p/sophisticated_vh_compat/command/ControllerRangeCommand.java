package dev.hoyin1600p.sophisticated_vh_compat.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.hoyin1600p.sophisticated_vh_compat.config.ControllerRangeConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.event.RegisterCommandsEvent;

public final class ControllerRangeCommand {
    private ControllerRangeCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("svhc")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("controller-range")
                        .executes(ControllerRangeCommand::showStatus)
                        .then(Commands.literal("enable")
                                .executes(context -> setEnabled(context, true)))
                        .then(Commands.literal("disable")
                                .executes(context -> setEnabled(context, false)))
                        .then(Commands.argument("value", IntegerArgumentType.integer(1, ControllerRangeConfig.MAX_RANGE))
                                .executes(ControllerRangeCommand::setWithWarning))
                        .then(Commands.literal("confirm")
                                .then(Commands.argument("value", IntegerArgumentType.integer(1, ControllerRangeConfig.MAX_RANGE))
                                        .executes(ControllerRangeCommand::setConfirmed)))));
    }

    private static int setWithWarning(CommandContext<CommandSourceStack> context) {
        int range = IntegerArgumentType.getInteger(context, "value");
        if (range <= ControllerRangeConfig.WARNING_RANGE) {
            return save(context, range);
        }

        MutableComponent message = new TextComponent("Warning: controller ranges above "
                + ControllerRangeConfig.WARNING_RANGE
                + " can be expensive. ")
                .withStyle(ChatFormatting.YELLOW)
                .append(new TextComponent("[YES]").withStyle(Style.EMPTY
                        .withColor(ChatFormatting.GREEN)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(
                                ClickEvent.Action.RUN_COMMAND,
                                "/svhc controller-range confirm " + range))))
                .append(new TextComponent(" to save " + range + ".").withStyle(ChatFormatting.YELLOW));
        context.getSource().sendFailure(message);
        return 0;
    }

    private static int setConfirmed(CommandContext<CommandSourceStack> context) {
        return save(context, IntegerArgumentType.getInteger(context, "value"));
    }

    private static int showStatus(CommandContext<CommandSourceStack> context) {
        String state = ControllerRangeConfig.isEnabled() ? "enabled" : "disabled";
        context.getSource().sendSuccess(new TextComponent("Sophisticated Storage controller range override is "
                + state + " at " + ControllerRangeConfig.getControllerRange() + " blocks."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int setEnabled(CommandContext<CommandSourceStack> context, boolean enabled) {
        ControllerRangeConfig.setEnabled(enabled);
        context.getSource().sendSuccess(new TextComponent("Sophisticated Storage controller range override "
                + (enabled ? "enabled at " + ControllerRangeConfig.getControllerRange() + " blocks." : "disabled."))
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int save(CommandContext<CommandSourceStack> context, int range) {
        ControllerRangeConfig.setControllerRange(range);
        context.getSource().sendSuccess(new TextComponent("Sophisticated Storage controller range saved as "
                + ControllerRangeConfig.getControllerRange()
                + " in "
                + ControllerRangeConfig.getConfigPath()
                + (ControllerRangeConfig.isEnabled() ? "." : ". The override remains disabled."))
                .withStyle(ChatFormatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }
}
