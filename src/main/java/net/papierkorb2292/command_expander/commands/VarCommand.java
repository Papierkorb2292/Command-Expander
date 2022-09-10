package net.papierkorb2292.command_expander.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.papierkorb2292.command_expander.CommandExpander;
import net.papierkorb2292.command_expander.variables.Variable;
import net.papierkorb2292.command_expander.variables.VariableIdentifier;
import net.papierkorb2292.command_expander.variables.path.VariablePath;

import java.util.List;

public class VarCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, boolean isDedicated) {
        dispatcher.register(
                CommandManager.literal("var")
                        .then(CommandManager.literal("add")
                                .then(CommandManager.argument("id", VariableNameArgumentType.variableName(false))
                                        .then(CommandManager.argument("type", VariableTypeArgumentType.variableType(false))
                                                .executes(context -> {
                                                    VariableIdentifier id = VariableNameArgumentType.getVariableName(context, "id");
                                                        CommandExpander.getVariableManager(context).add(
                                                                id,
                                                                VariableTypeArgumentType.getType(context, "type"));
                                                    context.getSource().sendFeedback(Text.of(String.format("Successfully added variable '%s'", id)), true);
                                                    return 1;
                                                })))
                        )
                        .then(
                                CommandManager.literal("remove")
                                        .then(CommandManager.argument("id", VariablePathArgumentType.variablePath())
                                            .executes(context -> {
                                                int result = VariablePathArgumentType.getVariablePath(context, "id").remove(context);
                                                context.getSource().sendFeedback(Text.of(String.format("Successfully removed %s %s", result, result == 1 ? "variable" : "variables")), true);
                                                return result;
                                            })))
                        .then(
                                CommandManager.literal("get")
                                        .then(CommandManager.argument("value", VariableImmediateValueArgumentType.variableImmediateValue())
                                                .executes(context ->
                                                        VariableImmediateValueArgumentType.getImmediateValue(context, "value").calculate(context).map(
                                                            holder -> {
                                                                Variable var = holder.variable;
                                                                sendGetFeedback(context, var);
                                                                return var == null ? 0 : var.intValue();
                                                            },
                                                            stream -> stream.mapToInt(var -> {
                                                                sendGetFeedback(context, var);
                                                                return var == null ? 0 : var.intValue();
                                                            }).sum()
                                                )
                                )))
                        .then(
                                CommandManager.literal("set")
                                        .then(CommandManager.argument("id", VariablePathArgumentType.variablePath())
                                                .then(CommandManager.argument("value", VariableImmediateValueArgumentType.variableImmediateValue())
                                                        .executes(context -> {
                                                            int result = VariablePathArgumentType.getVariablePath(context, "id")
                                                                    .set(
                                                                            VariableImmediateValueArgumentType.getImmediateValue(context, "value").calculate(context),
                                                                            context);
                                                            context.getSource().sendFeedback(Text.of(String.format("Successfully set %s %s", result, result == 1 ? "variable" : "variables")), true);
                                                            return result;
                                                        })))
                        )
                        .then(
                                CommandManager.literal("bind")
                                        .then(CommandManager.argument("id", VariablePathArgumentType.variablePath())
                                                .then(CommandManager.argument("criteria", MultiScoreboardCriterionArgumentType.scoreboardCriterion())
                                                        .executes(context -> {
                                                            List<String> criteria = MultiScoreboardCriterionArgumentType.getScoreboardCriteria(context, "criteria");
                                                            VariablePath path = VariablePathArgumentType.getVariablePath(context, "id");
                                                            int result = CommandExpander.getVariableManager(context).bindCriteria(path, criteria, context);
                                                            context.getSource().sendFeedback(Text.of(String.format("Successfully added %s %s", result, result == 1 ? "binding" : "bindings")), true);
                                                            return result;
                                                        })))
                        )
                        .then(
                                CommandManager.literal("unbind")
                                        .then(CommandManager.argument("id", VariablePathArgumentType.variablePath())
                                                .then(CommandManager.argument("criteria", MultiScoreboardCriterionArgumentType.scoreboardCriterion())
                                                    .executes(context -> {
                                                        List<String> criteria = MultiScoreboardCriterionArgumentType.getScoreboardCriteria(context, "criteria");
                                                        VariablePath path = VariablePathArgumentType.getVariablePath(context, "id");
                                                        int result = CommandExpander.getVariableManager(context).unbindCriteria(path, criteria, context);
                                                        context.getSource().sendFeedback(Text.of(String.format("Successfully removed %s %s", result, result == 1 ? "binding" : "bindings")), true);
                                                        return result;
                                                    })))
                        ));
    }

    private static final Text GET_FEEDBACK = Text.of("Variable has the following value: ");

    private static void sendGetFeedback(CommandContext<ServerCommandSource> cc, Variable var) {
        cc.getSource().sendFeedback(GET_FEEDBACK.copy().append(CommandExpander.buildCopyableText(var == null ? "null" : var.stringValue())), false);
    }
}
