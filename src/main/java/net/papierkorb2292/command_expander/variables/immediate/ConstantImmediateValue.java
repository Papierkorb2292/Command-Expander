package net.papierkorb2292.command_expander.variables.immediate;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.datafixers.util.Either;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_expander.variables.Variable;
import net.papierkorb2292.command_expander.variables.VariableHolder;

import java.util.List;
import java.util.stream.Stream;

/**
 * An optimized immediate value that always has the same constant result
 */
public class ConstantImmediateValue implements ImmediateValue {

    private final Either<VariableHolder, List<Variable>> constant;

    public ConstantImmediateValue(Either<VariableHolder, List<Variable>> constant) {
        this.constant = constant;
    }

    @Override
    public Either<VariableHolder, Stream<Variable>> calculate(CommandContext<ServerCommandSource> cc) {
        return constant.mapBoth(
                holder -> holder,
                List::stream
        );
    }
}
