package net.papierkorb2292.command_expander.variables;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.Deque;
import java.util.LinkedList;
import java.util.Queue;

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

    public boolean lowerAndEquals(Variable other) {
        VariableType.LoweredType type = VariableType.getLoweredType(getType(), other.getType());
        if(type == null) {
            return false;
        }
        try {
            return VariableManager.castVariable(type.type, this).equals(VariableManager.castVariable(type.type, other));
        } catch (CommandSyntaxException e) {
            return false;
        }
    }

    public TypedVariable toTypedVariable() {
        return new TypedVariable(getType(), this);
    }

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
                VariableType first = null;
                int start;
                for(start = 0; start < length; ++start) {
                    first = contentType[start];
                    if(first != null) {
                        break;
                    }
                }
                if(first == null) {
                    return new LoweredType(null);
                }
                for(int i = start + 1; i < length; ++i) {
                    if(contentType[i] == null) {
                        continue;
                    }
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
                            childrenType[i] = contentType[i] == null ? null : contentType[i].getChild(c);
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

        static LoweredType getLoweredType(VariableType first, VariableType second) {
            if(first == null) {
                return new LoweredType(second);
            }
            if(second == null) {
                return new LoweredType(first);
            }
            int firstLoweringTypeMinDepth = 0, secondLoweringTypeMinDepth = 0;
            VariableType current = first;
            while(current != null) {
                ++firstLoweringTypeMinDepth;
                current = current.getNextLoweredType();
            }
            current = second;
            while(current != null) {
                ++secondLoweringTypeMinDepth;
                current = current.getNextLoweredType();
            }
            if(firstLoweringTypeMinDepth == 0 && secondLoweringTypeMinDepth == 0) {
                return new LoweredType(null);
            }
            int loweringTypeMinDepth = firstLoweringTypeMinDepth == 0 ? secondLoweringTypeMinDepth : secondLoweringTypeMinDepth == 0 ? firstLoweringTypeMinDepth : Math.min(firstLoweringTypeMinDepth, secondLoweringTypeMinDepth);
            for(int i = firstLoweringTypeMinDepth; i > loweringTypeMinDepth; --i) {
                first = first.getNextLoweredType();
            }
            for(int i = secondLoweringTypeMinDepth; i > loweringTypeMinDepth; --i) {
                second = second.getNextLoweredType();
            }
            while(loweringTypeMinDepth != 0) {
                if(first.getTemplate() == second.getTemplate()) {
                    int childrenCount = first.getTemplate().childrenCount;
                    if(childrenCount == 0) {
                        return new LoweredType(first);
                    }
                    VariableType result = first.getTemplate().typeFactory.get();
                    boolean hasCompatibleChildren = true;
                    for(int c = 0; c < childrenCount; ++c) {
                        LoweredType loweredChildrenType = getLoweredType(first.getChild(c), second.getChild(c));
                        if(loweredChildrenType == null) {
                            hasCompatibleChildren = false;
                            break;
                        }
                        result.setChild(c, loweredChildrenType.type);
                    }
                    if(hasCompatibleChildren) {
                        return new LoweredType(result);
                    }
                }
                first = first.getNextLoweredType();
                second = second.getNextLoweredType();
                --loweringTypeMinDepth;
            }
            return null;
        }

        default boolean instanceOf(VariableType type) {
            if(type == null) {
                return true;
            }
            VariableType current = this;
            VariableTypeTemplate template = type.getTemplate();
            while(current != null) {
                if(current.getTemplate() == template) {
                    boolean childrenMatch = true;
                    for(int i = 0; i < template.childrenCount; ++i) {
                        if(!current.getChild(i).instanceOf(type.getChild(i))) {
                            childrenMatch = false;
                            break;
                        }
                    }
                    if(childrenMatch) {
                        return true;
                    }
                }
                current = current.getNextLoweredType();
            }
            return false;

        }

        /**
         * @return The name of this variable type used when registering it, like <i>int</i> or <i>map</i>
         */
        String getName();

        /**
         * @return The string that represents this variable type with its children, like <i>int</i> or <i>map&lt;entity, long&gt;</i>
         */
        default String asString() {
            StringBuilder sb = new StringBuilder();
            sb.append(getName());
            int children = getTemplate().childrenCount;
            if(children > 0) {
                sb.append('<');
                for (int i = 0; i < children; ++i) {
                    sb.append(getChild(i).asString());
                }
                sb.append('>');
            }
            return sb.toString();
        }

        static byte[] getEncoded(VariableType type) {
            Deque<VariableType> typeEncoderStack = new LinkedList<>();
            Queue<VariableTypeTemplate> typeEncoderEncounteredTypes = new LinkedList<>();
            int typeSize = 1;
            typeEncoderStack.add(type);
            while(!typeEncoderStack.isEmpty()) {
                type = typeEncoderStack.pop();
                VariableTypeTemplate typeTemplate = type.getTemplate();
                typeEncoderEncounteredTypes.add(typeTemplate);
                int childrenCount = typeTemplate.childrenCount;
                typeSize += childrenCount;
                for(int i = 0; i < childrenCount; ++i) {
                    typeEncoderStack.add(type.getChild(i));
                }
            }
            byte[] typeArray = new byte[typeSize];
            int index = 0;
            while(!typeEncoderEncounteredTypes.isEmpty()) {
                typeArray[index] = typeEncoderEncounteredTypes.remove().id;
                ++index;
            }
            return typeArray;
        }
    }
}
