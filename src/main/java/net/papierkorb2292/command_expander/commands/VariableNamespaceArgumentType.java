package net.papierkorb2292.command_expander.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_expander.variables.VariableIdentifier;

public class VariableNamespaceArgumentType implements ArgumentType<String> {

    public static VariableNamespaceArgumentType variableNamespace() {
        return new VariableNamespaceArgumentType();
    }

    public static String getVariableNamespace(CommandContext<ServerCommandSource> context, String name) {
        return context.getArgument(name, String.class);
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        if(!reader.canRead()) {
            throw VariableIdentifier.INVALID_NAME_EXCEPTION.createWithContext(reader, "", "");
        }
        int start = reader.getCursor();
        char c;
        if(VariableIdentifier.isCharInvalidFirst(c = reader.read())) {
            throw VariableNameArgumentType.CHAR_NOT_ALLOWED_EXCEPTION.createWithContext(reader, c);
        }
        while(reader.canRead()) {
            c = reader.peek();
            if(c == ' ') {
                break;
            }
            reader.skip();
            if(VariableIdentifier.isCharInvalidGeneral(c)) {
                throw VariableNameArgumentType.CHAR_NOT_ALLOWED_EXCEPTION.create(c);
            }
        }
        return reader.getString().substring(start);
    }
}
