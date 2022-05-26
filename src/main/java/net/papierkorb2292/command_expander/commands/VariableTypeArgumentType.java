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

    public static VariableTypeArgumentType variableType() {
        return new VariableTypeArgumentType();
    }

    public static Variable.VariableType getType(CommandContext<ServerCommandSource> context, String name) {
        return context.getArgument(name, Variable.VariableType.class);
    }

    @Override
    public Variable.VariableType parse(StringReader reader) throws CommandSyntaxException {
        return VariableManager.parseType(reader);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        parseTypeSuggestions(
                new StringReader(builder.getRemainingLowerCase()),
                new StringBuilder(), new StringBuilder(),
                builder, false);
        return builder.buildFuture();
    }

    /**
     * @see VariableManager#parseType
     */
    private boolean parseTypeSuggestions(StringReader reader, StringBuilder completedNames, StringBuilder currentName, SuggestionsBuilder builder, boolean appendComma) {
        currentName.setLength(0);
        char c;
        boolean encounteredChildren = false, isComplete = false;
        while(reader.canRead()) {
            if(reader.peek() == ',' || reader.peek() == '>') {
                isComplete = true;
                break;
            }
            c = reader.read();
            if(c == '<') {
                encounteredChildren = true;
                isComplete = true;
                break;
            }
            if(c == ' ') {
                isComplete = true;
                reader.skipWhitespace();
                if(reader.canRead() && reader.peek() == '<') {
                    encounteredChildren = true;
                    reader.skip();
                }
                break;
            }
            currentName.append(c);
        }
        reader.skipWhitespace();
        if(!isComplete) {
            String previous = completedNames.toString(), current = currentName.toString();
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
        String typeName = currentName.toString();
        VariableTypeTemplate typeTemplate = VariableManager.getType(typeName);
        if(typeTemplate == null) {
            return true;
        }
        boolean hasChildren = typeTemplate.childrenCount != 0;
        completedNames.append(typeName);
        if(hasChildren && encounteredChildren) {
            completedNames.append('<');
            for(int i = 0; i < typeTemplate.childrenCount; ++i) {
                boolean isEnd = i == typeTemplate.childrenCount - 1;
                reader.skipWhitespace();
                if(parseTypeSuggestions(reader, completedNames, currentName, builder, !isEnd)) {
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
