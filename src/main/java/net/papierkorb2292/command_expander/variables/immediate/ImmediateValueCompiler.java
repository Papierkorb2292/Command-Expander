package net.papierkorb2292.command_expander.variables.immediate;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.command.EntitySelectorReader;
import net.papierkorb2292.command_expander.variables.*;

import java.util.*;

public class ImmediateValueCompiler {

    private static final SimpleCommandExceptionType OPEN_PARENTHESES_EXCEPTION = new SimpleCommandExceptionType(new LiteralMessage("Encountered unclosed parentheses"));
    private static final SimpleCommandExceptionType EXPECTED_VALUE_EXCEPTION = new SimpleCommandExceptionType(new LiteralMessage("Expected value"));
    private static final DynamicCommandExceptionType EXPECTED_VALUE_OR_END_OF_EXCEPTION = new DynamicCommandExceptionType(o -> new LiteralMessage("Expected value or end of " + o));
    private static final Dynamic2CommandExceptionType UNKNOWN_FUNCTION_EXCEPTION = new Dynamic2CommandExceptionType((name, paramCount) -> new LiteralMessage(String.format("Unknown function '%s' with %s parameters", name, paramCount)));
    private static final SimpleCommandExceptionType ENCOUNTERED_MULTIPLE_RADIX_POINTS_EXCEPTION = new SimpleCommandExceptionType(new LiteralMessage("Encountered multiple radix points"));
    private static final SimpleCommandExceptionType NUMBER_EMPTY_EXCEPTION = new SimpleCommandExceptionType(new LiteralMessage("Number must contain at least one digit"));

    private static final Map<FunctionDescriptor, Instruction> FUNCTIONS = new HashMap<>();
    private static final Map<String, Operator> OPERATORS = new HashMap<>();

    /**
     * Compiles an immediate value term into a list of instructions created by this method. The list is put into an {@link InstructionListImmediateValue}, which is then optimized.
     * @param reader The reader for the term, ending spaces aren't read
     * @return The optimized immediate value for evaluating the term
     * @throws CommandSyntaxException An error happened when parsing the term
     * @see #compileTerm
     */
    public static ImmediateValue compile(StringReader reader) throws CommandSyntaxException {
        List<Instruction> instructions = new ArrayList<>();
        compileTerm(instructions, 0, reader);
        return new InstructionListImmediateValue(instructions).optimize();
    }

    /**
     * Compiles an immediate value term into a list of instructions. A term consists of one or multiple values that are connected trough operators.
     * A value can also have an index operator.
     * @param instructions The list to add all instructions to
     * @param instructionIndex The index on the list for the new instructions. The term could be used as a value by using parentheses:
     *                         1 + (minecraft:a * 3), in this case the instructionIndex isn't 0. It's also possible that the instructionIndex isn't the end of the list,
     *                         when casts have already been compiled: (int)(minecraft:a * 3) Because the cast instruction comes after the instruction of the value
     * @param reader The reader for the term, ending spaces aren't read
     * @return The amount of added instructions, so that the caller can change the index of added instructions accordingly
     * @throws CommandSyntaxException An error happened when parsing the term
     */
    private static int compileTerm(List<Instruction> instructions, int instructionIndex, StringReader reader) throws CommandSyntaxException {
        reader.skipWhitespace();
        int addedInstructions = 0;
        addedInstructions += compileValue(instructions, instructionIndex, reader);
        addedInstructions += compileOptionalIndexing(instructions, instructionIndex + addedInstructions, reader);
        Deque<IndexedOperator> ops = new LinkedList<>();
        Operator op;
        while((op = checkMatchingOperator(reader)) != null) {
            reader.skipWhitespace();
            addedInstructions += compileValue(instructions, instructionIndex + addedInstructions, reader);
            addedInstructions += compileOptionalIndexing(instructions, instructionIndex + addedInstructions, reader);
            ops.push(new IndexedOperator(instructionIndex, op));
        }
        if(ops.size() > 0) {
            //Rearranges operations according to their order and adds them to the instructions
            //A higher order is run before a lower order
            Deque<IndexedOperator> movingOps = new LinkedList<>(); //Multiple operations can be moved at the same time
            IndexedOperator currentOp = ops.removeLast();
            movingOps.push(currentOp);
            while(ops.size() > 0) {
                //noinspection ConstantConditions <- IntelliJ marks a possible NullPointerException at ops.peekLast() because of the following calls to instructions.add and movingOps.pop
                while(currentOp.op.order >= ops.peekLast().op.order) { //If the next operator has a lower order, the current operator is immediately added
                    instructions.add(currentOp.destIndex + addedInstructions, currentOp.op.instruction);
                    ++addedInstructions;
                    movingOps.pop();
                    if(movingOps.size() > 0) {
                        movingOps.peek().destIndex = currentOp.destIndex; //The next moving operator is moved to the position of the previous operator
                    }
                    currentOp = movingOps.peek();
                    if(currentOp == null) {
                        break;
                    }
                }
                currentOp = ops.removeLast(); //The next operator, which has a higher order than the current operator, becomes the current operator
                movingOps.push(currentOp);
            }
            if(movingOps.size() > 0) { //All operators left are added to the instructions
                int destIndex = movingOps.peek().destIndex;
                while (movingOps.size() > 0) {
                    instructions.add(destIndex + addedInstructions, movingOps.pop().op.instruction);
                    ++addedInstructions;
                }
            }
        }
        return addedInstructions;
    }

    /**
     * Compiles optional indexing operators (either list<b>[&lt;immediate value term&gt;]</b> or list<b>[]</b>) of immediate values into a list of instructions.
     * Doesn't change the reader cursor position when no indexing is present. Multiple indexing operators can be used successively! Looking at you, NBT paths...
     * @param instructions The list to add all instructions to
     * @param instructionIndex The index on the list for the new instructions
     * @param reader The reader for the term, ending spaces aren't read
     * @return The amount of added instructions, so that the caller can change the index of added instructions accordingly
     * @throws CommandSyntaxException An error happened when parsing the indexing
     */
    private static int compileOptionalIndexing(List<Instruction> instructions, int instructionIndex, StringReader reader) throws CommandSyntaxException {
        int cursor = reader.getCursor();
        int addedInstructions = 0;
        reader.skipWhitespace();
        while(reader.canRead() && reader.peek() == '[') {
            reader.skip();
            reader.skipWhitespace();
            if(!reader.canRead()) {
                throw EXPECTED_VALUE_OR_END_OF_EXCEPTION.createWithContext(reader, "indexing");
            }
            if(reader.peek() == ']') { // No index exists, all contents of the variable are returned
                reader.skip();
                instructions.add(instructionIndex + addedInstructions, Instructions.GET_ALL_CONTENTS);
                ++addedInstructions;
            } else { // The index is compiled with the compileTerm method
                addedInstructions += compileTerm(instructions, instructionIndex + addedInstructions, reader);
                reader.skipWhitespace();
                reader.expect(']');
                instructions.add(instructionIndex + addedInstructions, Instructions.GET_INDEXED_CONTENTS);
                ++addedInstructions;
            }
            cursor = reader.getCursor();
            reader.skipWhitespace();
        }
        reader.setCursor(cursor);
        return addedInstructions;
    }

    /**
     * Compiles a single value of a term into a list of instructions. This value can be for example numbers or strings,
     * but also a function like sin(&lt;term&gt;) or another term in parentheses. Casts before the value are also compiled.
     * @param instructions The list to add all instructions to
     * @param instructionIndex The index on the list for the new instructions
     * @param reader The reader for the value, ending spaces aren't read
     * @return The amount of added instructions, so that the caller can change the index of added instructions accordingly
     * @throws CommandSyntaxException An error happened when parsing the value
     */
    private static int compileValue(List<Instruction> instructions, int instructionIndex, StringReader reader) throws CommandSyntaxException {
        char c = reader.read();
        int addedInstructions = 0, addedPostInstructions = 0;
        while(c == '(' || c == '-') { // Casts and negation operation can be used before the value any amount of times
            if(c == '-') { // A  negation operator is present. The instruction is added to the list and the loop starts over
                reader.skipWhitespace();
                if(!reader.canRead()) {
                    throw EXPECTED_VALUE_EXCEPTION.createWithContext(reader);
                }
                ++addedPostInstructions;
                instructions.add(instructionIndex, Instructions.NEGATE);
                c = reader.read();
                continue;
            }
            // Open parentheses are present, they are either a cast or contain another term
            int openParentheses = 1, cursor = reader.getCursor(); // Skipping to the closing parentheses
            while(reader.canRead() && openParentheses != 0) {
                c = reader.read();
                if(c == ')') {
                    --openParentheses;
                    continue;
                }
                if(c == '(') {
                    ++openParentheses;
                }
            }
            if(openParentheses != 0) {
                throw OPEN_PARENTHESES_EXCEPTION.createWithContext(reader);
            }
            reader.skipWhitespace();
            boolean isCasting = true;
            if(reader.canRead()) { // If an operator is present or the reader has reached the end, the content has to be a term
                Operator op = checkMatchingOperator(reader);
                if(op != null) {
                    isCasting = false;
                }
            } else {
                isCasting = false;
            }
            int endCursor = reader.getCursor();
            reader.setCursor(cursor);
            if(isCasting) {
                Variable.VariableType type = VariableManager.parseType(reader, true);
                reader.skip();
                if(reader.getCursor() != endCursor) {
                    throw VariableManager.UNKNOWN_TYPE_EXCEPTION.createWithContext(reader, reader.getString().substring(cursor, endCursor));
                }
                instructions.add(instructionIndex, Instructions.getCast(type));
                ++addedPostInstructions;
                reader.skipWhitespace();
                if(!reader.canRead()) {
                    throw EXPECTED_VALUE_EXCEPTION.createWithContext(reader);
                }
                c = reader.read();
                continue;
            }
            addedPostInstructions += compileTerm(instructions, instructionIndex, reader);
            reader.skipWhitespace();
            reader.skip();
            return addedPostInstructions;
        }
        if(c == '{') { // The value is a list (or map) or map entry
            int length = 0;
            reader.skipWhitespace();
            if(!reader.canRead()) {
                throw EXPECTED_VALUE_OR_END_OF_EXCEPTION.createWithContext(reader, "list");
            }
            if(reader.peek() != '}') {
                // The list (or map) isn't empty or the value is a map entry
                addedInstructions += compileTerm(instructions, instructionIndex, reader); // The first value is compiled
                reader.skipWhitespace();
                if(reader.peek() == ':') {
                    // THe value is a map entry
                    reader.skip();
                    reader.skipWhitespace();
                    addedInstructions += compileTerm(instructions, instructionIndex + addedInstructions, reader);
                    reader.skipWhitespace();
                    reader.expect('}');
                    instructions.add(instructionIndex + addedInstructions, Instructions.BUILD_MAP_ENTRY);
                    return addedInstructions + addedPostInstructions + 1;
                }
                length = 1;
                while(reader.peek() != '}') { // Remaining values of the list (or map) are compiled
                    reader.expect(',');
                    reader.skipWhitespace();
                    addedInstructions += compileTerm(instructions, instructionIndex, reader);
                    ++length;
                    reader.skipWhitespace();
                }
                reader.expect('}');
            } else {
                reader.skip();
            }
            // If the map is empty, length is still 0
            instructionIndex += addedInstructions;
            instructions.add(instructionIndex, Instructions.getBuildListOrMap(length));
        } else {
            if(c == '"') {
                String value = reader.readStringUntil('"');
                // The value is a string
                //TODO: Add String variable type and load value
            } else {
                reader.setCursor(reader.getCursor() - 1);
                if (c >= '0' && c <= '9' || c == '.') {
                    // The value is a number
                    instructions.add(instructionIndex, Instructions.getLoadConstant(readNumber(reader)));
                } else if(c == '@') {
                    // The value is an entity selector
                    instructions.add(instructionIndex, Instructions.getLoadFromEntitySelector(new EntitySelectorReader(reader).read()));
                } else if(
                        c == 'n' &&
                        reader.canRead(4) &&
                        reader.getString().startsWith("null", reader.getCursor()) && (
                            !reader.canRead() || //The name might just start with null, like "null_"
                            VariableIdentifier.isCharInvalidGeneral(reader.peek(4)))) {
                    reader.setCursor(reader.getCursor() + 4);
                    instructions.add(instructionIndex, Instructions.getLoadConstant(null));
                } else {
                    // The value is either a variable or function
                    VariableIdentifier identifier = VariableIdentifier.parse(reader);
                    int cursor = reader.getCursor();
                    if(reader.canRead() && reader.peek() == ' ') {
                        reader.skipWhitespace();
                    }
                    if(reader.canRead() && reader.peek() == '(') {
                        // The value is a function
                        int parametersCursor = reader.getCursor();
                        reader.skip();
                        reader.skipWhitespace();
                        if (!reader.canRead()) {
                            throw EXPECTED_VALUE_OR_END_OF_EXCEPTION.createWithContext(reader, "parameters");
                        }
                        c = reader.peek();
                        int paramCount = 0;
                        if (c != ')') {
                            while (true) { // Compiling the parameters until closing parentheses are found
                                addedInstructions += compileTerm(instructions, instructionIndex, reader);
                                reader.skipWhitespace();
                                ++paramCount;
                                if (!reader.canRead()) {
                                    throw EXPECTED_VALUE_OR_END_OF_EXCEPTION.createWithContext(reader, "parameters");
                                }
                                reader.skipWhitespace();
                                c = reader.peek();
                                if (c == ')') {
                                    reader.skip();
                                    break;
                                }
                                reader.expect(',');
                                reader.skipWhitespace();
                            }
                            instructionIndex += addedInstructions;
                        }
                        String namespace = identifier.namespace, path = identifier.path;
                        if (namespace.equals("minecraft") && path.equals("all")) {
                            // Because the "all" function can have any number of parameters, it's handled separately
                            instructions.add(instructionIndex, Instructions.getLoadStreamAll(paramCount));
                        } else {
                            String function = identifier.toString();
                            FunctionDescriptor descriptor = new FunctionDescriptor(function, paramCount);
                            if (!FUNCTIONS.containsKey(descriptor)) {
                                reader.setCursor(parametersCursor);
                                throw UNKNOWN_FUNCTION_EXCEPTION.createWithContext(reader, function, paramCount);
                            }
                            instructions.add(instructionIndex, FUNCTIONS.get(new FunctionDescriptor(function, paramCount)));
                        }
                    } else {
                        // The value is a variable
                        reader.setCursor(cursor);
                        instructions.add(instructionIndex, Instructions.getLoadVariable(identifier));
                    }
                }
            }
        }
        return addedInstructions + addedPostInstructions + 1;
    }

    /**
     * <p>Reads a number in an immediate value and returns the corresponding variable. A number can be in decimal (902) binary (<b>0b</b>101),
     * octal (<b>0</b>76) or hexadecimal (<b>0x</b>F280) format. A number can have a leading sign.</p>
     * <p>Decimal and hexadecimal numbers can have radix points and are then parsed to a double if not further specified using a suffix.</p>
     * Possible suffixes are: TODO: Add missing types
     * <ul>
     *     <li> "L" : An integer is parsed as long. It isn't read when a radix point is found</li>
     * </ul>
     * @param reader The reader for the number, ending spaces aren't read
     * @return The number as variable
     * @throws CommandSyntaxException The number contained no digits or contained multiple radix points
     */
    private static Variable readNumber(StringReader reader) throws CommandSyntaxException {
        char c;
        int startIndex = reader.getCursor();
        boolean empty = true;
        c = reader.peek();
        if(c == '-' || c == '+') {
            reader.skip();
            c = reader.peek();
        }
        if(c == '0') {
            if(reader.canRead(2)) {
                reader.skip();
                c = reader.peek();
                if (c == 'x' || c == 'X') {
                    reader.skip();
                    c = reader.peek();
                    boolean encounteredPoint = false;
                    while (c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F' || c == '.') {
                        empty = false;
                        if (c == '.') {
                            if (encounteredPoint) {
                                throw ENCOUNTERED_MULTIPLE_RADIX_POINTS_EXCEPTION.createWithContext(reader);
                            }
                            encounteredPoint = true;
                        }
                        reader.skip();
                        if(!reader.canRead()) {
                            break;
                        }
                        c = reader.peek();
                    }
                    if (empty) {
                        throw NUMBER_EMPTY_EXCEPTION.createWithContext(reader);
                    }
                    if (encounteredPoint) {
                        return DoubleVariable.parse(reader.getString().substring(startIndex, reader.getCursor()) + "p0");
                    }
                    if(c == 'L' || c == 'l') {
                        reader.skip();
                        return LongVariable.parseHexadecimal(reader.getString().substring(startIndex + 2, reader.getCursor()));
                    }
                    return IntVariable.parseHexadecimal(reader.getString().substring(startIndex + 2, reader.getCursor()));

                } else if (c == 'b' || c == 'B') {
                    reader.skip();
                    c = reader.peek();
                    while (c == '0' || c == '1') {
                        empty = false;
                        reader.skip();
                        if(!reader.canRead()) {
                            break;
                        }
                        c = reader.peek();
                    }
                    if (empty) {
                        throw NUMBER_EMPTY_EXCEPTION.createWithContext(reader);
                    }
                    String value = reader.getString().substring(startIndex + 2, reader.getCursor());
                    if(c == 'L' || c == 'l') {
                        reader.skip();
                        return LongVariable.parseBinary(value);
                    }
                    return IntVariable.parseBinary(value);
                } else if (c >= '0' && c <= '9') {
                    while (c >= '0' && c <= '7') {
                        empty = false;
                        reader.skip();
                        if(!reader.canRead()) {
                            break;
                        }
                        c = reader.peek();
                    }
                    if (empty) {
                        throw NUMBER_EMPTY_EXCEPTION.createWithContext(reader);
                    }
                    String value = reader.getString().substring(startIndex + 1, reader.getCursor());
                    if(c == 'L' || c == 'l') {
                        reader.skip();
                        return LongVariable.parseOctal(value);
                    }
                    return IntVariable.parseOctal(value);
                }
                c = '0';
                reader.setCursor(startIndex);
            }
        }
        boolean encounteredPoint = false;
        while(c >= '0' && c <= '9' || c == '.') {
            if(c == '.') {
                if(encounteredPoint) {
                    throw ENCOUNTERED_MULTIPLE_RADIX_POINTS_EXCEPTION.createWithContext(reader);
                }
                encounteredPoint = true;
            } else {
                empty = false;
            }
            reader.skip();
            if(!reader.canRead()) {
                break;
            }
            c = reader.peek();
        }
        if(empty) {
            throw NUMBER_EMPTY_EXCEPTION.createWithContext(reader);
        }
        String value = reader.getString().substring(startIndex, reader.getCursor());
        if(encounteredPoint) {
            return DoubleVariable.parse(value);
        }
        if(c == 'L' || c == 'l') {
            reader.skip();
            return LongVariable.parseDecimal(value);
        }
        return IntVariable.parseDecimal(value);
    }

    /**
     * Reads an immediate value operator and returns it, doesn't change the cursor of the reader if no operator is present.
     * Whitespaces can be in front of the operator
     * @param reader The reader for the operator, ending spaces aren't read
     * @return The operator or null, if none is found
     */
    private static Operator checkMatchingOperator(StringReader reader) {
        int cursor = reader.getCursor();
        Set<String> ops = new HashSet<>(OPERATORS.keySet());
        int index = 0;
        Operator result = null;
        reader.skipWhitespace();
        while(!ops.isEmpty() && reader.canRead()) {
            char c = reader.read();
            Iterator<String> opIt = ops.iterator();
            while (opIt.hasNext()) {
                String op = opIt.next();
                if (op.length() <= index || op.charAt(index) != c) {
                    opIt.remove();
                    continue;
                }
                if (op.length() - 1 == index) {
                    result = OPERATORS.get(op);
                }
            }
            ++index;
        }
        if(result != null) {
            cursor = reader.getCursor() - 1;
        }
        reader.setCursor(cursor);
        return result;
    }

    static {
        FUNCTIONS.put(new FunctionDescriptor("minecraft:sin", 1), Instructions.SIN);
        OPERATORS.put("+", new Operator(Instructions.ADD, 1));
        OPERATORS.put("*", new Operator(Instructions.MUL, 2));
        OPERATORS.put("::", new Operator(Instructions.RANGE, 0));
    }

    private static record FunctionDescriptor(String name, int parameters) { }

    private static record Operator(Instruction instruction, int order) { }

    private static final class IndexedOperator {
        public int destIndex;
        public final Operator op;

        private IndexedOperator(int destIndex, Operator op) {
            this.destIndex = destIndex;
            this.op = op;
        }
    }
}
