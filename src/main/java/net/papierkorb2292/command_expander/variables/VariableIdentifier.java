package net.papierkorb2292.command_expander.variables;

import com.google.common.collect.ImmutableSet;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;

import java.util.Set;

public class VariableIdentifier {
    private static final Set<String> illegalVariableNames = ImmutableSet.of("null");
    public static final Dynamic2CommandExceptionType INVALID_NAME_EXCEPTION = new Dynamic2CommandExceptionType((namespace, path) -> new LiteralMessage(String.format("Variable id '%s:%s' isn't valid", namespace, path)));

    public final String namespace;
    public final String path;

    public VariableIdentifier(String namespace, String path) throws CommandSyntaxException {
        if(isNameInvalid(namespace) || isNameInvalid(path)) {
            throw INVALID_NAME_EXCEPTION.create(namespace, path);
        }
        this.namespace = namespace;
        this.path = path;
    }

    public VariableIdentifier(String name) throws CommandSyntaxException {
        int split = name.indexOf(':');
        if(split >= 0) {
            String namespace = name.substring(0, split), path = name.substring(split + 1);
            if(isNameInvalid(namespace) || isNameInvalid(path)) {
                throw INVALID_NAME_EXCEPTION.create(namespace, path);
            }
            this.namespace = namespace;
            this.path = path;
            return;
        }
        if(isNameInvalid(name)) {
            throw INVALID_NAME_EXCEPTION.create("minecraft", name);
        }
        namespace = "minecraft";
        path = name;
    }

    public static VariableIdentifier parse(StringReader reader) throws CommandSyntaxException {
        if(!reader.canRead()) {
            throw INVALID_NAME_EXCEPTION.create("minecraft", "");
        }
        int startIndex = reader.getCursor();
        if(isCharInvalidFirst(reader.peek())) {
            throw INVALID_NAME_EXCEPTION.create("minecraft", reader.read());
        }
        reader.skip();
        while(reader.canRead() && !isCharInvalidGeneral(reader.peek())) {
            reader.skip();
        }
        String name = reader.getString().substring(startIndex, reader.getCursor());
        if(reader.canRead() && reader.peek() == ':') {
            reader.skip();
            if(!reader.canRead()) {
                throw INVALID_NAME_EXCEPTION.create(name, "");
            }
            startIndex = reader.getCursor();
            if(isCharInvalidFirst(reader.peek())) {
                throw INVALID_NAME_EXCEPTION.create(reader.read(), name);
            }
            while(reader.canRead() && !isCharInvalidGeneral(reader.peek())) {
                reader.skip();
            }
            return new VariableIdentifier(name, reader.getString().substring(startIndex, reader.getCursor()));
        }
        return new VariableIdentifier("minecraft", name);
    }

    @Override
    public String toString() {
        return namespace + ':' + path;
    }

    public static boolean isNameInvalid(String name) {
        if(name.isEmpty() || illegalVariableNames.contains(name) || VariableManager.getTypes().contains(name)) {
            return true;
        }
        if(isCharInvalidFirst(name.charAt(0))) {
            return true;
        }
        for(int i = 1; i < name.length(); ++i) {
            char c = name.charAt(i);
            if (isCharInvalidGeneral(c)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isCharInvalidFirst(char c) {
        return c != '$' && (c < 'a' || c > 'z') && (c < 'A' || c > 'Z') && c != '_';
    }

    public static boolean isCharInvalidGeneral(char c) {
        return c != '$' && (c < 'a' || c > 'z') && (c < 'A' || c > 'Z') && c != '_' && c != '.' && (c < '0' || c > '9');
    }

    @Override
    public boolean equals(Object obj) {
        if(obj == this) {
            return true;
        }
        if(!(obj instanceof VariableIdentifier that)) {
            return false;
        }
        return this.path.equals(that.path) && this.namespace.equals(that.namespace);
    }

    @Override
    public int hashCode() {
        return this.path.hashCode() ^ this.namespace.hashCode();
    }
}
