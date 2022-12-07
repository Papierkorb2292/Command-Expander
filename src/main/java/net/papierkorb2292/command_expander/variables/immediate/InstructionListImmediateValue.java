package net.papierkorb2292.command_expander.variables.immediate;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Either;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_expander.variables.Variable;
import net.papierkorb2292.command_expander.variables.VariableHolder;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

/**
 * An immediate value using a list of instructions that are applied successively on an {@link Instruction.CalculationContext}
 * to calculate the result
 */
public class InstructionListImmediateValue implements ImmediateValue {

     private static final SimpleCommandExceptionType STACK_EMPTY_EXCEPTION = new SimpleCommandExceptionType(new LiteralMessage("Stack of immediate value calculation didn't have enough elements"));

     private final List<Instruction> instructions;

     public InstructionListImmediateValue(List<Instruction> instructions) {
          this.instructions = instructions;
     }

     public Either<VariableHolder, Stream<Variable>> calculate(CommandContext<ServerCommandSource> cc) throws CommandSyntaxException  {
          throwIfFeatureDisabled(cc);
          Deque<Either<VariableHolder, Stream<Variable>>> stack = new LinkedList<>();
          Instruction.CalculationContext context = new Instruction.CalculationContext(stack, cc);
          try {
               for (Instruction in : instructions) {
                    in.apply(context);
               }
               return stack.pop();
          }
          catch(NoSuchElementException e) {
               throw STACK_EMPTY_EXCEPTION.create();
          }
     }

     /**
      * Searches and applies optimization to the immediate value
      * @return The optimized immediate value, which might be another instance when for example it was possible to optimize it to a {@link ConstantImmediateValue}
      */
     public ImmediateValue optimize() {
          //TODO
          if(instructions.size() == 1 && instructions.get(0) instanceof Instructions.LoadConstant con) {
               return new ConstantImmediateValue(con.value);
          }
          return this;
     }
}
