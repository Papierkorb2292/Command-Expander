package net.papierkorb2292.command_expander.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.papierkorb2292.command_expander.mixin_method_interfaces.VariableManagerContainer;
import net.papierkorb2292.command_expander.variables.VariableManager;

import java.util.function.Consumer;

public class VarCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, boolean isDedicated) {
        dispatcher.register(
                CommandManager.literal("var")
                        .then(CommandManager.literal("add")
                                .then(CommandManager.argument("id", IdentifierArgumentType.identifier())
                                        .then(CommandManager.argument("type", VariableTypeArgumentType.variableType())
                                                .executes(context -> {
                                                    Identifier id = IdentifierArgumentType.getIdentifier(context, "id");
                                                        getVariableManager(context).add(
                                                                id,
                                                                VariableTypeArgumentType.getType(context, "type"));
                                                    context.getSource().sendFeedback(Text.of(String.format("Successfully added variable '%s'", id)), true);
                                                    return 1;
                                                })))
                        )
                        .then(
                                addExistingVariableId(CommandManager.literal("remove"), arg ->
                                        arg.executes(context -> {
                                            Identifier id = IdentifierArgumentType.getIdentifier(context, "id");
                                            getVariableManager(context).remove(id);
                                            context.getSource().sendFeedback(Text.of(String.format("Successfully removed variable '%s'", id)), true);
                                            return 1;
                                        })))
                        .then(
                                addExistingVariableId(CommandManager.literal("get"), arg -> //TODO: Replace variable id with immediate value
                                        arg.executes(context -> {
                                            Identifier id = IdentifierArgumentType.getIdentifier(context, "id");
                                            return getVariableManager(context).getReadonly(id, value -> context.getSource().sendFeedback(new LiteralText(String.format("Variable '%s' contains the following value: ", id)).append(value), true));
                                        }
                                ))));
    }

    private static <T extends ArgumentBuilder<ServerCommandSource, T>> T addExistingVariableId(T commandBuilder, Consumer<RequiredArgumentBuilder<ServerCommandSource, Identifier>> argumentConsumer) {
        RequiredArgumentBuilder<ServerCommandSource, Identifier> arg = CommandManager.argument("id", IdentifierArgumentType.identifier())
                .suggests((context, suggestionsBuilder) -> CommandSource.suggestIdentifiers(getVariableManager(context).getIds(), suggestionsBuilder));
        argumentConsumer.accept(arg);
        return commandBuilder.then(arg);
    }

    private static VariableManager getVariableManager(CommandContext<ServerCommandSource> context) {
        return ((VariableManagerContainer)context.getSource().getServer()).command_expander$getVariableManager();
    }
}
