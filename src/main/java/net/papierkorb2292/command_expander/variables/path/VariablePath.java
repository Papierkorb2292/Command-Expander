package net.papierkorb2292.command_expander.variables.path;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Either;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_expander.CommandExpander;
import net.papierkorb2292.command_expander.variables.*;
import net.papierkorb2292.command_expander.variables.immediate.ImmediateValueCompiler;

import java.util.ArrayList;
import java.util.stream.Stream;

/**
 * A VariablePath defines the path to a variable for setting or deleting it. It uses an array of {@link PathChildrenAccessor}s
 * that define how to set, delete or get the specified child. There are accessors for different ways of accessing children like
 * indexing or the key/value of a map entry.
 */
public class VariablePath {

    public static final SimpleCommandExceptionType VARIABLE_NOT_INDEXABLE_EXCEPTION = new SimpleCommandExceptionType(new LiteralMessage("Variable is not indexable"));
    public static final SimpleCommandExceptionType VARIABLE_NOT_ENTRY_EXCEPTION = new SimpleCommandExceptionType(new LiteralMessage("Variable is not an entry of a map"));
    public static final SimpleCommandExceptionType MULTIPLE_VALUES_TO_SINGLE_VARIABLE_EXCEPTION = new SimpleCommandExceptionType(new LiteralMessage("Multiple variables can't be assigned to a single variable"));
    public static final SimpleCommandExceptionType UNABLE_TO_REMOVE_FROM_ENTRY_EXCEPTION = new SimpleCommandExceptionType(new LiteralMessage("Direct children of an entry can't be removed"));

    private static final DynamicCommandExceptionType INVALID_FUNCTION_EXCEPTION = new DynamicCommandExceptionType(function -> new LiteralMessage(String.format("Invalid function '%s'", function)));
    private static final SimpleCommandExceptionType EXPECTED_END_OF_FUNCTION_EXCEPTION = new SimpleCommandExceptionType(new LiteralMessage("Expected end of function"));
    private static final SimpleCommandExceptionType EXPECTED_INDEX_EXCEPTION = new SimpleCommandExceptionType(new LiteralMessage("Expected index"));

    private final VariableIdentifier base;
    private final PathChildrenAccessor[] accessors;

    public VariablePath(VariableIdentifier base, PathChildrenAccessor[] getters) {
        this.base = base;
        this.accessors = getters;
    }
    //TODO: Make set generate parent variables if they don't exist
    /**
     * Set the variable(s) that the path points to. If there's only one value (VariableHolder), all variables will be set to that value.
     * Otherwise, like with immediate values, values of the stream are consumed and assigned to a single variable each (in the order that the variables are found)
     * until no variables or values are left.
     *
     * @param value The value to set it to
     * @param cc    The {@link CommandContext} for loading variables
     * @return How many children were set
     * @throws CommandSyntaxException An error happened when setting the variable(s)
     */
    public int set(Either<VariableHolder, Stream<Variable>> value, CommandContext<ServerCommandSource> cc) throws CommandSyntaxException {
        VariableManager manager = CommandExpander.getVariableManager(cc);
        TypedVariable varBase = manager.get(base);
        if (accessors == null || accessors.length == 0) {
            if (value.left().isPresent()) {
                varBase.castAndSet(value.left().get().variable);
                manager.updateVariableBindingReferences(this, cc);
                return 1;
            }
            throw MULTIPLE_VALUES_TO_SINGLE_VARIABLE_EXCEPTION.create();
        }
        Either<VariableHolder, Stream<Variable>> current = Either.left(new VariableHolder(varBase.var));
        int lastGetter = accessors.length - 1;
        for (int i = 0; i < lastGetter; ++i) {
            current = accessors[i].getChildren(current, cc);
        }
        int result = accessors[lastGetter].setChildren(current, value, cc);
        manager.updateVariableBindingReferences(this, cc);
        return result;
    }

    /**
     * Removes the variable(s) that the path points to.
     *
     * @param cc The {@link CommandContext} for loading variables
     * @return The amount of variables that were removed
     * @throws CommandSyntaxException An error happened when removing the variable(s)
     */
    public int remove(CommandContext<ServerCommandSource> cc) throws CommandSyntaxException {
        VariableManager manager = CommandExpander.getVariableManager(cc);
        if (accessors == null || accessors.length == 0) {
            manager.remove(base);
            manager.removeVariableBindingReferences(this, cc);
            return 1;
        }
        TypedVariable varBase = manager.get(base);
        Either<VariableHolder, Stream<Variable>> current = Either.left(new VariableHolder(varBase.var));
        int lastGetter = accessors.length - 1;
        for (int i = 0; i < lastGetter; ++i) {
            current = accessors[i].getChildren(current, cc);
        }
        int result = accessors[lastGetter].removeChildren(current, cc);
        manager.removeVariableBindingReferences(this, cc);
        return result;
    }

    /**
     * Parses a VariablePath from a {@link StringReader}. The variable path can contain:<br/>
     * - One base variable id, for example: "minecraft:my_var"<br/>
     * - Any number of indexing operations on that variable where the index is an immediate value: "minecraft:my_var[0][minecraft:my_other_var * 2]"<br/>
     * - Functions accessing children (for example "key" or "value" for map entries): value(key(minecraft:my_var[0])[1])[3]
     * @param reader The reader containing the path, ending spaces aren't read
     * @return The parsed VariablePath
     * @throws CommandSyntaxException An error happened when parsing the path
     */
    public static VariablePath parse(StringReader reader) throws CommandSyntaxException {
        VariableIdentifier base = VariableIdentifier.parse(reader);
        if (!reader.canRead()) {
            return new VariablePath(base, null);
        }
        if (reader.peek() != '(') { //  All functions of a VariablePath must be at the beginning.
                                    //  Only indexing operations and closing parentheses are allowed after the variable id
            int cursor = reader.getCursor();
            reader.skipWhitespace();
            if (!reader.canRead()) {
                reader.setCursor(cursor);
                return new VariablePath(base, null);
            }
            if (reader.peek() != '[') { // No indexing operations are present
                reader.setCursor(reader.getCursor() - 1);
                return new VariablePath(base, null);
            }
        }
        ArrayList<PathChildrenAccessor> getters = new ArrayList<>();
        int functions = 0; //Amount of parsed functions is saved, so that the correct amount of closing parentheses is expected
        char c = reader.peek();
        while (c == '(') { // A function is present
            reader.skip();
            if (base.namespace.equals("minecraft")) { // The base is now used as a function name until no more opening
                                                      // parentheses are found
                if (base.path.equals("key")) {
                    // All functions are inserted at the beginning, because they must either the first function
                    // or in the parameter of previously parsed functions
                    getters.add(0, PathChildrenAccessor.KeyAccessor.INSTANCE);
                    ++functions;
                } else if (base.path.equals("value")) {
                    getters.add(0, PathChildrenAccessor.ValueAccessor.INSTANCE);
                    ++functions;
                } else {
                    throw INVALID_FUNCTION_EXCEPTION.create(base.path);
                }
            } else {
                throw INVALID_FUNCTION_EXCEPTION.create(base.path);
            }
            reader.skipWhitespace();
            base = VariableIdentifier.parse(reader);
            if (!reader.canRead()) { // At least closing parentheses have to follow the new base
                throw EXPECTED_END_OF_FUNCTION_EXCEPTION.create();
            }
            c = reader.peek();
        }
        int accessorIndex = 0; // The index accessors can be inserted in-between the functions, so the
                               // index for insertions is increased with every accessor added and when closing
                               // parentheses are found, to skip the accessor of that function
        reader.skipWhitespace();
        int backCursor = reader.getCursor(); // The cursor is saved so that it can be reset after finding whitespaces at the end
        while (c == '[' || (c == ')' && functions > 0)) {
            reader.skip();
            if (c == ')') {
                --functions;
            } else {
                if(!reader.canRead()) {
                    throw EXPECTED_INDEX_EXCEPTION.create();
                }
                getters.add(accessorIndex,
                        reader.peek() == ']'
                                ? PathChildrenAccessor.AllContentAccessor.INSTANCE
                                : new PathChildrenAccessor.IndexedPathChildrenAccessor(ImmediateValueCompiler.compile(reader)));
                reader.expect(']');
            }
            ++accessorIndex;
            backCursor = reader.getCursor();
            reader.skipWhitespace();
            if(!reader.canRead()) {
                break;
            }
            c = reader.peek();
        }
        if (functions > 0) {
            throw EXPECTED_END_OF_FUNCTION_EXCEPTION.create();
        }
        reader.setCursor(backCursor);
        return new VariablePath(base, getters.toArray(new PathChildrenAccessor[0]));
    }

    public VariableIdentifier getBase() {
        return base;
    }
    public int getAccessorLength() {
        return accessors == null ? 0 : accessors.length;
    }
    public PathChildrenAccessor getAccessor(int index) {
        return accessors[index];
    }
}
