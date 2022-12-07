package net.papierkorb2292.command_expander.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.papierkorb2292.command_expander.variables.VariableIdentifier;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public class VariableNameArgumentType implements ArgumentType<VariableIdentifier> {

    private static final Collection<String> EXAMPLES = Arrays.asList("my_list", "minecraft:players52", "other_namespace:some.path");
    private static final DynamicCommandExceptionType CHAR_NOT_ALLOWED_EXCEPTION = new DynamicCommandExceptionType(c -> Text.literal(String.format("The char '%s' isn't allowed in a variable name", c)));

    public static VariableNameArgumentType variableName() {
        return new VariableNameArgumentType();
    }

    public static VariableIdentifier getVariableName(CommandContext<ServerCommandSource> context, String name) {
        return context.getArgument(name, VariableIdentifier.class);
    }

    @Override
    public VariableIdentifier parse(StringReader reader) throws CommandSyntaxException {
        StringBuilder builder = new StringBuilder();
        boolean isFirst = true;
        while(reader.canRead()) {
            char c = reader.peek();
            if(c == ' ') {
                break;
            }
            reader.skip();
            builder.append(c);
            if(c == ':') {
                isFirst = true;
                continue;
            }
            if(isFirst) {
                if(VariableIdentifier.isCharInvalidFirst(c)) {
                    throw CHAR_NOT_ALLOWED_EXCEPTION.create(c);
                }
                isFirst = false;
            }
            else {
                if(VariableIdentifier.isCharInvalidGeneral(c)) {
                    throw CHAR_NOT_ALLOWED_EXCEPTION.create(c);
                }
            }
        }
        return new VariableIdentifier(builder.toString());
    }

    /**
     * @see CommandSource#forEachMatching(Iterable, String, java.util.function.Function, java.util.function.Consumer)
     */
    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        //if(!suggestsExisting) {
        //    return Suggestions.empty();
        //}
        //S source = context.getSource();
        //if(source instanceof ServerCommandSource serverCommandSource) {
        //    String remaining = builder.getRemaining();
        //    boolean hasColon = remaining.indexOf(':') > -1;
        //    for (VariableIdentifier id : (Iterable<VariableIdentifier>)((VariableManagerContainer)serverCommandSource.getServer()).command_expander$getVariableManager().getIds()::iterator) {
        //        if (hasColon) {
        //            String string = id.toString();
        //            if (!CommandSource.shouldSuggest(remaining, string)) {
        //                continue;
        //            }
        //            builder.suggest(string);
        //            continue;
        //        }
        //        if (!CommandSource.shouldSuggest(remaining, id.namespace) && (!id.namespace.equals("minecraft") || !CommandSource.shouldSuggest(remaining, id.path))) {
        //            continue;
        //        }
        //        builder.suggest(id.toString());
        //    }
        //    return builder.buildFuture();
        //}
        //if(source instanceof CommandSource commandSource) {
        //    return commandSource.getCompletions(context);
        //}
        return Suggestions.empty();
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
