package net.papierkorb2292.command_expander.mixin.immediateValues;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import net.minecraft.command.argument.CommandFunctionArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.function.CommandFunction;
import net.minecraft.text.Texts;
import net.minecraft.util.Identifier;
import net.papierkorb2292.command_expander.variables.StringVariable;
import net.papierkorb2292.command_expander.variables.Variable;
import net.papierkorb2292.command_expander.variables.VariableHolder;
import net.papierkorb2292.command_expander.variables.VariableManager;
import net.papierkorb2292.command_expander.variables.immediate.ImmediateValue;
import net.papierkorb2292.command_expander.variables.immediate.ImmediateValueCompiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Stream;

@Mixin(CommandFunctionArgumentType.class)
public class CommandFunctionArgumentTypeMixin {

    static @Shadow CommandFunction getFunction(CommandContext<ServerCommandSource> context, Identifier id) {
        throw new AssertionError();
    }

    @Inject(
            method = "parse(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/command/argument/CommandFunctionArgumentType$FunctionArgument;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void command_expander$allowImmediateValueFunctionIdentifier(StringReader stringReader, CallbackInfoReturnable<CommandFunctionArgumentType.FunctionArgument> cir) throws CommandSyntaxException {
        if(stringReader.canRead() && stringReader.peek() == '(') {
            stringReader.skip();
            ImmediateValue value = ImmediateValueCompiler.compile(stringReader);
            stringReader.expect(')');
            cir.setReturnValue(new CommandFunctionArgumentType.FunctionArgument() {

                private static final SimpleCommandExceptionType IMMEDIATE_VALUE_FUNCTION_WAS_NULL = new SimpleCommandExceptionType(new LiteralMessage("Immediate value uses as function identifier returned 'null' or an invalid identifier"));

                @Override
                public Collection<CommandFunction> getFunctions(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
                    return value.calculate(context).map(
                            holder -> {
                                try {
                                    return Collections.singleton(getSingleFunction(holder.variable, context));
                                } catch (CommandSyntaxException e) {
                                    context.getSource().sendError(Texts.toText(e.getRawMessage()));
                                    return Collections.emptySet();
                                }
                            },
                            stream -> stream.flatMap(
                                    var -> {
                                        try {
                                            return Stream.of(getSingleFunction(var, context));
                                        } catch (CommandSyntaxException e) {
                                            context.getSource().sendError(Texts.toText(e.getRawMessage()));
                                            return null;
                                        }
                                    }).toList()
                    );
                }

                private static final SimpleCommandExceptionType CALLED_GET_FUNCTION_OR_TAG_WITH_MULTIPLE_VALUES_EXCEPTIONS = new SimpleCommandExceptionType(new LiteralMessage("Tried to decide whether a function specified through an immediate value with a stream as result is a function or tag."));

                @Override
                public Pair<Identifier, Either<CommandFunction, Collection<CommandFunction>>> getFunctionOrTag(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
                    Either<VariableHolder, Stream<Variable>> result = value.calculate(context);
                    if(result.right().isPresent()) {
                        throw CALLED_GET_FUNCTION_OR_TAG_WITH_MULTIPLE_VALUES_EXCEPTIONS.create();
                    }
                    if(result.left().isEmpty()) {
                        throw new IllegalStateException("Invalid Either calculated for immediate value function call. Neither left nor right were present");
                    }
                    CommandFunction function = getSingleFunction(result.left().get().variable, context);
                    return Pair.of(function.getId(), Either.left(function));
                }

                private static CommandFunction getSingleFunction(Variable var, CommandContext<ServerCommandSource> cc) throws CommandSyntaxException {
                    StringVariable string = (StringVariable) VariableManager.castVariable(StringVariable.StringVariableType.INSTANCE, var);
                    if(string == null) {
                        throw IMMEDIATE_VALUE_FUNCTION_WAS_NULL.create();
                    }
                    Identifier id = Identifier.tryParse(string.getString());
                    if(id == null) {
                        throw IMMEDIATE_VALUE_FUNCTION_WAS_NULL.create();
                    }
                    return CommandFunctionArgumentTypeMixin.getFunction(cc, id);
                }
            });
        }
    }
}
