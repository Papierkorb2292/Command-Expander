package net.papierkorb2292.command_expander.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.papierkorb2292.command_expander.mixin_method_interfaces.VariableManagerContainer;
import net.papierkorb2292.command_expander.variables.VariableIdentifier;
import net.papierkorb2292.command_expander.variables.VariableManager;

public class VarCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, boolean isDedicated) {
        dispatcher.register(
                CommandManager.literal("var")
                        .then(CommandManager.literal("add")
                                .then(CommandManager.argument("id", VariableNameArgumentType.variableName(false))
                                        .then(CommandManager.argument("type", VariableTypeArgumentType.variableType())
                                                .executes(context -> {
                                                    VariableIdentifier id = VariableNameArgumentType.getVariableName(context, "id");
                                                        getVariableManager(context).add(
                                                                id,
                                                                VariableTypeArgumentType.getType(context, "type"));
                                                    context.getSource().sendFeedback(Text.of(String.format("Successfully added variable '%s'", id)), true);
                                                    return 1;
                                                })))
                        )
                        .then(
                                CommandManager.literal("remove")
                                        .then(CommandManager.argument("id", VariableNameArgumentType.variableName(true))
                                            .executes(context -> {
                                                VariableIdentifier id = VariableNameArgumentType.getVariableName(context, "id");
                                                getVariableManager(context).remove(id);
                                                context.getSource().sendFeedback(Text.of(String.format("Successfully removed variable '%s'", id)), true);
                                                return 1;
                                            })))
                        .then(
                                CommandManager.literal("get") //TODO: Replace variable id with immediate value
                                        .then(CommandManager.argument("id", VariableNameArgumentType.variableName(true))
                                            .executes(context -> {
                                                VariableIdentifier id = VariableNameArgumentType.getVariableName(context, "id");
                                                return getVariableManager(context).getReadonly(id, value -> context.getSource().sendFeedback(new LiteralText(String.format("Variable '%s' contains the following value: ", id)).append(value), true));
                                            }
                                ))));
    }

    private static VariableManager getVariableManager(CommandContext<ServerCommandSource> context) {
        return ((VariableManagerContainer)context.getSource().getServer()).command_expander$getVariableManager();
    }
}
