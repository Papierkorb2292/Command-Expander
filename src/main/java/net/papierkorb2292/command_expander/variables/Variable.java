package net.papierkorb2292.command_expander.variables;

public abstract class Variable {

    public abstract int intValue();

    public abstract long longValue();

    public abstract float floatValue();

    public abstract double doubleValue();

    public abstract String stringValue();

    public byte byteValue() {
        return (byte)intValue();
    }

    public short shortValue() {
        return (short)intValue();
    }

    public abstract VariableType getType();

    @Override
    public abstract boolean equals(Object o);

    @Override
    public abstract int hashCode();

    public interface VariableType {

        Variable createVariable();
        VariableTypeTemplate getTemplate();
        VariableType getNextLoweredType();
        default void setChild(int index, VariableType child) { }
        default VariableType getChild(int index) {
            return null;
        }

        /**
         * Finds the next common ancestor through the lowered types of the contents of the array and also
         * searches for lowered types in the children of the types
         * @return null if no lowered type was found, otherwise a wrapper containing the lowered type. If all types are null, a wrapper of null is returned.
         */
        static LoweredType getLoweredType(VariableType[] types) {
            int length = types.length;
            VariableType[] contentType = new VariableType[length], childrenType = null;
            int[] contentLoweringTypeDepth = new int[length];
            int loweringTypeMinDepth = 0;
            for (int i = 0; i < length; ++i) { //Search for lowest amount of lowered types before each of the types
                VariableType type = types[i];
                if(type == null) continue;
                contentType[i] = type;
                int currentDepth = 0;
                while (type != null) {
                    ++currentDepth;
                    type = type.getNextLoweredType();
                }
                contentLoweringTypeDepth[i] = currentDepth;
                if (currentDepth < loweringTypeMinDepth || loweringTypeMinDepth == 0) {
                    loweringTypeMinDepth = currentDepth;
                }
            }
            if(loweringTypeMinDepth == 0) { //All types are null
                return new LoweredType(null);
            }
            for (int i = 0; i < length; ++i) { //Lowers all types to match the minimum
                VariableType type = contentType[i];
                for(int depth = contentLoweringTypeDepth[i]; depth > loweringTypeMinDepth; --depth) {
                    type = type.getNextLoweredType();
                }
                contentType[i] = type;
            }
            // Lowers all types further until all are null (no matching type found)
            // or all are the same base type (same template) and
            // their children can be lowered
            while(loweringTypeMinDepth != 0) {
                boolean isEqual = true;
                VariableType first = contentType[0];
                for(int i = 1; i < length; ++i) {
                    if(first.getTemplate() != contentType[i].getTemplate()) {
                        isEqual = false;
                    }
                }
                if(isEqual) {
                    int childrenCount = first.getTemplate().childrenCount;
                    if(childrenCount == 0) {
                        return new LoweredType(first);
                    }
                    if(childrenType == null) {
                        childrenType = new VariableType[length];
                    }
                    VariableType result = first.getTemplate().typeFactory.get();
                    for(int c = 0; c < childrenCount; ++c) {
                        for(int i = 0; i < length; ++i) {
                            childrenType[i] = contentType[i].getChild(c);
                        }
                        LoweredType loweredChildrenType = getLoweredType(childrenType);
                        if(loweredChildrenType == null) {
                            isEqual = false;
                            break;
                        }
                        result.setChild(c, loweredChildrenType.type);
                    }
                    if(isEqual) {
                        return new LoweredType(result);
                    }
                }
                for(int i = 0; i < length; ++i) {
                    Variable.VariableType type = contentType[i];
                    if(type != null) {
                        contentType[i] = type.getNextLoweredType();
                    }
                }
                --loweringTypeMinDepth;
            }
            return null;
        }

        final class LoweredType {
            public final Variable.VariableType type;

            private LoweredType(Variable.VariableType type) {
                this.type = type;
            }
        }
    }
}
