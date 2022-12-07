package net.papierkorb2292.command_expander.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_expander.variables.path.VariablePath;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public class VariablePathArgumentType implements ArgumentType<VariablePath>  {

    private static final Collection<String> EXAMPLES = Arrays.asList("my_int", "my_list[3]", "key(my_entries[my_map[(entity)\"63e5bc0e-3ea6-4e4e-b904-47409b7e5864\"]])[2]");

    public static VariablePathArgumentType variablePath() {
        return new VariablePathArgumentType();
    }

    public static VariablePath getVariablePath(CommandContext<ServerCommandSource> context, String name) {
        return context.getArgument(name, VariablePath.class);
    }

    @Override
    public VariablePath parse(StringReader reader) throws CommandSyntaxException {
        return VariablePath.parse(reader);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return ArgumentType.super.listSuggestions(context, builder); //TODO?
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
