package net.papierkorb2292.command_expander.variables;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.stream.Stream;

public abstract class IndexableVariable extends Variable {

    public abstract Variable get(Variable indexVar);

    /**
     * @return whether the index existed already
     */
    public abstract boolean ensureIndexExists(Variable indexVar);
    public abstract boolean set(Variable indexVar, Variable value);
    public abstract boolean remove(Variable indexVar);
    public abstract Variable ensureIndexCompatible(Variable indexVar) throws CommandSyntaxException;
    public abstract int clear();
    public abstract int setAll(Variable value);

    public abstract VariableType getContentType();
    public abstract Stream<Variable> getContents();

    /**
     * @return All indices used by variable. The indices should be different instances whose value isn't changed, so that they can be saved.
     */
    public abstract Stream<Variable> getIndices();

    public Variable ensureIndexAndGetNonNull(Variable indexVar) throws CommandSyntaxException {
        indexVar = ensureIndexCompatible(indexVar);
        if (ensureIndexExists(indexVar)) {
            Variable value = get(indexVar);
            if(value != null) {
                return value;
            }
        }
        Variable value = getContentType().createVariable();
        set(indexVar, value);
        return value;
    }

    public boolean ensureIndexAndSet(Variable indexVar, Variable value) throws CommandSyntaxException {
        indexVar = ensureIndexCompatible(indexVar);
        ensureIndexExists(indexVar);
        return set(indexVar, value);
    }
}
