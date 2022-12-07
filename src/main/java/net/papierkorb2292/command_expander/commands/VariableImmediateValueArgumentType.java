package net.papierkorb2292.command_expander.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_expander.variables.immediate.ImmediateValue;
import net.papierkorb2292.command_expander.variables.immediate.ImmediateValueCompiler;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public class VariableImmediateValueArgumentType implements ArgumentType<ImmediateValue> {

    private static final Collection<String> EXAMPLES = Arrays.asList("3", "\"I am a string!\"", "0x3", "5-2", "{ sin(a:my_var[\"2\" + \"292\"] - (int)b:my_other_var * -7), (1 + key(c:some_elses_var)) * 3.5f }");

    public static VariableImmediateValueArgumentType variableImmediateValue() {
        return new VariableImmediateValueArgumentType();
    }

    public static ImmediateValue getImmediateValue(CommandContext<ServerCommandSource> context, String name) {
        return context.getArgument(name, ImmediateValue.class);
    }

    @Override
    public ImmediateValue parse(StringReader reader) throws CommandSyntaxException {
        return ImmediateValueCompiler.compile(reader);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return ArgumentType.super.listSuggestions(context, builder); //TODO?? If I am not too lazy
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
