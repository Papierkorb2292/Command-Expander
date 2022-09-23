package net.papierkorb2292.command_expander.variables.immediate.operator;

import net.papierkorb2292.command_expander.variables.Variable;

/**
 * Interface for {@link Variable.VariableType}s where variables of that type can be bitwise shifted right in an immediate value using the '>>' operator between two values
 */
public interface BitwiseRightShiftableOperatorVariableType {

    Variable shiftVariablesRight(Variable left, int right);
}
