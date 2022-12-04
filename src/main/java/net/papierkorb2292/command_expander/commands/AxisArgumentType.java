package net.papierkorb2292.command_expander.commands;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.math.Direction;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class AxisArgumentType implements ArgumentType<Direction.Axis> {

    private static final DynamicCommandExceptionType INVALID_VALUE_EXCEPTION = new DynamicCommandExceptionType(name -> new LiteralMessage("Invalid value: " + name));
    private static final Collection<String> VALUES = Set.of("X", "Y", "Z");
    public static AxisArgumentType axis() {
        return new AxisArgumentType();
    }

    public static Direction.Axis getAxis(CommandContext<ServerCommandSource> context, String name) {
        return context.getArgument(name, Direction.Axis.class);
    }

    @Override
    public Direction.Axis parse(StringReader reader) throws CommandSyntaxException {
        char name = reader.read();
        return switch (name) {
            case 'X' -> Direction.Axis.X;
            case 'Y' -> Direction.Axis.Y;
            case 'Z' -> Direction.Axis.Z;
            default -> throw INVALID_VALUE_EXCEPTION.createWithContext(reader, name);
        };
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(final CommandContext<S> context, final SuggestionsBuilder builder) {
        return CommandSource.suggestMatching(VALUES, builder);
    }

    @Override
    public Collection<String> getExamples() {
        return VALUES;
    }
}
