package net.papierkorb2292.command_expander.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_expander.variables.Variable;
import net.papierkorb2292.command_expander.variables.VariableManager;
import net.papierkorb2292.command_expander.variables.VariableTypeTemplate;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public class VariableTypeArgumentType implements ArgumentType<Variable.VariableType> {

    private static final Collection<String> EXAMPLES = Arrays.asList("int", "list<float>", "map<entity, list<double>>");

    private final boolean allowNull;

    public VariableTypeArgumentType(boolean allowNull) {
        this.allowNull = allowNull;
    }

    public static VariableTypeArgumentType variableType(boolean allowNull) {
        return new VariableTypeArgumentType(allowNull);
    }

    public static Variable.VariableType getType(CommandContext<ServerCommandSource> context, String name) {
        return context.getArgument(name, Variable.VariableType.class);
    }

    @Override
    public Variable.VariableType parse(StringReader reader) throws CommandSyntaxException {
        return VariableManager.parseType(reader, allowNull);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        parseTypeSuggestions(
                new StringReader(builder.getRemainingLowerCase()),
                new StringBuilder(),
                builder, false);
        return builder.buildFuture();
    }

    /**
     * @see VariableManager#parseType
     */
    private boolean parseTypeSuggestions(StringReader reader, StringBuilder completedNames, SuggestionsBuilder builder, boolean appendComma) {
        int currentStartCursor = reader.getCursor(), endCursor = -1;
        char c;
        boolean encounteredChildren = false, isComplete = false;
        while(reader.canRead()) {
            if(reader.peek() == ',' || reader.peek() == '>') {
                endCursor = reader.getCursor();
                isComplete = true;
                break;
            }
            c = reader.read();
            if(c == '<') {
                endCursor = reader.getCursor() - 1;
                encounteredChildren = true;
                isComplete = true;
                break;
            }
            if(c == ' ') {
                endCursor = reader.getCursor() - 1;
                isComplete = true;
                reader.skipWhitespace();
                if(reader.canRead() && reader.peek() == '<') {
                    encounteredChildren = true;
                    reader.skip();
                }
                break;
            }
        }
        reader.skipWhitespace();
        if(!isComplete) {
            String previous = completedNames.toString(), current = reader.getString().substring(currentStartCursor, reader.getCursor());
            boolean foundMatch = false;
            for(String type : VariableManager.getTypes()) {
                if(type.equals(current)) {
                    completedNames.append(type);
                    foundMatch = VariableManager.getType(type).childrenCount == 0;
                    if(!foundMatch) {
                        completedNames.append('<');
                        builder.suggest(completedNames.toString());
                    }
                    break;
                }
                if(type.startsWith(current)) {
                    String suggestion = previous + type;
                    if(VariableManager.getType(type).childrenCount != 0) {
                        suggestion += '<';
                    }
                    else if(appendComma) {
                        suggestion += ", ";
                    }
                    builder.suggest(suggestion);
                }
            }
            return !foundMatch;
        }
        String typeName = reader.getString().substring(currentStartCursor, endCursor);
        VariableTypeTemplate typeTemplate = VariableManager.getType(typeName);
        if(typeTemplate == null) {
            return !allowNull;
        }
        boolean hasChildren = typeTemplate.childrenCount != 0;
        completedNames.append(typeName);
        if(hasChildren && encounteredChildren) {
            completedNames.append('<');
            for(int i = 0; i < typeTemplate.childrenCount; ++i) {
                boolean isEnd = i == typeTemplate.childrenCount - 1;
                reader.skipWhitespace();
                if(parseTypeSuggestions(reader, completedNames, builder, !isEnd)) {
                    return true;
                }
                reader.skipWhitespace();
                if(isEnd) {
                    completedNames.append('>');
                    if(reader.canRead() && reader.read() == '>') {
                        break;
                    }
                    builder.suggest(completedNames.toString());
                    return true;
                }
                if(!reader.canRead() || reader.read() != ',') {
                    builder.suggest(completedNames.toString());
                    return true;
                }
                reader.skipWhitespace();
            }
        }
        if(appendComma) {
            completedNames.append(", ");
        }
        return false;
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
