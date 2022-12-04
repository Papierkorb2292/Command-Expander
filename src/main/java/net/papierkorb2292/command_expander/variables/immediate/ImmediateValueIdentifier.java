package net.papierkorb2292.command_expander.variables.immediate;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Either;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Texts;
import net.minecraft.util.Identifier;
import net.papierkorb2292.command_expander.variables.StringVariable;
import net.papierkorb2292.command_expander.variables.Variable;
import net.papierkorb2292.command_expander.variables.VariableHolder;
import net.papierkorb2292.command_expander.variables.VariableManager;

import java.util.stream.Stream;

public class ImmediateValueIdentifier extends Identifier {

    private final ImmediateValue value;

    public ImmediateValueIdentifier(ImmediateValue value) {
        super("command_expander", "immediate_value");
        this.value = value;
    }

    public static final SimpleCommandExceptionType VALUE_NULL_EXCEPTION = new SimpleCommandExceptionType(new LiteralMessage("Variable used as id was null"));

    public Either<Identifier, Stream<Identifier>> calculate(CommandContext<ServerCommandSource> cc) throws CommandSyntaxException {
        Either<VariableHolder, Stream<Variable>> result = value.calculate(cc);
        if(result.left().isPresent()) {
            Variable var = result.left().get().variable;
            if(var == null) {
                throw VALUE_NULL_EXCEPTION.create();
            }
            return Either.left(new Identifier(((StringVariable) VariableManager.castVariable(StringVariable.StringVariableType.INSTANCE, var)).getString()));
        }
        if(result.right().isEmpty()) {
            throw new IllegalStateException("Invalid either calculated for id. Neither left nor right were present");
        }
        return Either.right(result.right().get().flatMap(var -> {
            try {
                if (var == null) {
                    throw VALUE_NULL_EXCEPTION.create();
                }
                return Stream.of(new Identifier(((StringVariable) VariableManager.castVariable(StringVariable.StringVariableType.INSTANCE, var)).getString()));
            }
            catch(CommandSyntaxException e) {
                cc.getSource().sendError(Texts.toText(e.getRawMessage()));
                return null;
            }
        }));
    }
}
