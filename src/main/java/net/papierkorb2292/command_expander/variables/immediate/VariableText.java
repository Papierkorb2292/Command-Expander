package net.papierkorb2292.command_expander.variables.immediate;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.*;
import net.papierkorb2292.command_expander.variables.Variable;
import net.papierkorb2292.command_expander.variables.VariableHolder;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class VariableText extends BaseText implements ParsableText {

    private final ImmediateValue value;
    private final String rawValue;
    private final Optional<Text> separator;

    public VariableText(ImmediateValue value, String rawValue, Optional<Text> separator) {
        this.value = value;
        this.rawValue = rawValue;
        this.separator = separator;
    }

    public VariableText(String rawValue, Optional<Text> separator) {
        ImmediateValue value;
        try {
            value = ImmediateValueCompiler.compile(new StringReader(rawValue));
        } catch (CommandSyntaxException e) {
            value = null;
        }
        this.value = value;
        this.rawValue = rawValue;
        this.separator = separator;
    }

    @Override
    public VariableText copy() {
        return new VariableText(value, rawValue, separator);
    }

    @Override
    public MutableText parse(@Nullable ServerCommandSource source, @Nullable Entity sender, int depth) throws CommandSyntaxException {
        if(source == null || value == null) {
            return new LiteralText("");
        }
        Either<VariableHolder, Stream<Variable>> result = value.calculate(new CommandContext<>(source, null, null, null, null, null, null, null, null, false));
        if(result.left().isPresent()) {
            Variable var = result.left().get().variable;
            return new LiteralText(var == null ? "null" : var.stringValue());
        }
        if(result.right().isEmpty()) {
            throw new IllegalArgumentException("Invalid Either given to VariableText. Neither left nor right were present");
        }
        Stream<String> texts = result.right().get().map(
                var -> var == null ? "null" : var.stringValue()
        );
        return Texts.parse(source, this.separator, sender, depth)
                .map((textx) -> texts
                        .map(text -> (MutableText)new LiteralText(text))
                        .reduce((accumulator, current) ->
                                        accumulator.append(textx).append(current))
                        .orElseGet(() -> new LiteralText("")))
                .orElseGet(() -> new LiteralText(texts.collect(Collectors.joining(", "))));
    }

    @Override
    public String toString() {
        return "VariableComponent{value=" + rawValue + ", style=" + getStyle() + ", siblings=" + this.siblings + "}";
    }
}
