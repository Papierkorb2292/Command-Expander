package net.papierkorb2292.command_expander.variables;

import com.google.common.collect.AbstractIterator;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.papierkorb2292.command_expander.commands.VarCommand;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class IteratorVariable extends Variable {

    private final Iterator iterator;
    private final IteratorVariableType type;

    public IteratorVariable(Iterator iterator, IteratorVariableType type) {
        this.iterator = iterator;
        this.type = type;
    }

    @Override
    public int intValue() {
        return iterator.getCount();
    }

    @Override
    public long longValue() {
        return iterator.getCount();
    }

    @Override
    public float floatValue() {
        return iterator.getCount();
    }

    @Override
    public double doubleValue() {
        return iterator.getCount();
    }

    @Override
    public String stringValue() {
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        sb.append(getType().asString());
        sb.append("){ ");
        if(!iterator.hasNext()) {
            sb.append('}');
            return sb.toString();
        }
        while(true) {
            Variable current = null;
            try {
                current = iterator.next();
            } catch (CommandSyntaxException ignored) { }
            sb.append(current == null ? "null" : current.stringValue());
            if(!iterator.hasNext()) {
                sb.append(" }");
                return sb.toString();
            }
            sb.append(", ");
        }
    }

    @Override
    public VariableType getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof IteratorVariable && ((IteratorVariable)o).iterator.equals(iterator);
    }

    @Override
    public int hashCode() {
        return iterator.hashCode();
    }

    @Override
    public NbtElement toNbt() throws CommandSyntaxException {
        NbtList list = new NbtList();
        while(iterator.hasNext()) {
            list.add(Variable.createNbt(iterator.next()));
        }
        return list;
    }

    public IteratorVariable copy() {
        return new IteratorVariable(iterator.copy(), type);
    }

    public Stream<Variable> all(Consumer<Text> errorConsumer) {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(new AbstractIterator<>() {

            @Nullable
            @Override
            protected Variable computeNext() {
                while(iterator.hasNext()) {
                    try {
                        return iterator.next();
                    } catch (CommandSyntaxException e) {
                        errorConsumer.accept(Texts.toText(e.getRawMessage()));
                    }
                }
                return endOfData();
            }
        }, 0), false);
    }

    public Variable next() throws CommandSyntaxException {
        return iterator.next();
    }

    public Stream<Variable> next(int count, Consumer<Text> errorConsumer) {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(new AbstractIterator<>() {

            private int currentCount = count;

            @Nullable
            @Override
            protected Variable computeNext() {
                while(iterator.hasNext() && currentCount > 0) {
                    currentCount--;
                    try {
                        return iterator.next();
                    } catch (CommandSyntaxException e) {
                        errorConsumer.accept(Texts.toText(e.getRawMessage()));
                    }
                }
                return endOfData();
            }
        }, 0), false);
    }

    public boolean empty() {
        return !iterator.hasNext();
    }

    public static class IteratorVariableType implements VariableType {

        public VariableType content;

        public IteratorVariableType() { }
        public IteratorVariableType(VariableType content) {
            this.content = content;
        }

        @Override
        public Variable createVariable() {
            return new IteratorVariable(Iterator.Empty.INSTANCE, this);
        }

        @Override
        public VariableTypeTemplate getTemplate() {
            return TEMPLATE;
        }

        @Override
        public VariableType getNextLoweredType() {
            return content instanceof MapEntryVariable.MapEntryVariableType entryType
                    ? new MapVariable.MapVariableType(entryType.key, entryType.value)
                    : new ListVariable.ListVariableType(content);
        }

        @Override
        public String getName() {
            return "iterator";
        }

        public void setChild(int index, VariableType child) {
            if(index == 0) {
                content = child;
            }
        }

        public VariableType getChild(int index) {
            return index == 0 ? content : null;
        }

        public static VariableTypeTemplate TEMPLATE = new VariableTypeTemplate(1, IteratorVariableType::new, (type, var) -> {
            IteratorVariableType itType = (IteratorVariableType) type;
            if(var instanceof ListVariable list) {
                return new IteratorVariable(new Iterator.List(
                        (ListVariable) VariableManager.castVariable(
                                new ListVariable.ListVariableType(itType.content),
                                list)),
                        itType);
            }
            if(!(var instanceof IteratorVariable iterator)) {
                throw VariableManager.INCOMPATIBLE_TYPES_EXCEPTION.create(type.asString(), var.getType().asString());
            }
            Iterator it = iterator.iterator;
            if(itType.typeEquals(iterator.type)) {
                return new IteratorVariable(it, iterator.type);
            }
            return new IteratorVariable(it.cast(itType.content), itType);
        }, new VariableCodec() {
            @Override
            protected <T> DataResult<Pair<Variable, T>> read(DynamicOps<T> ops, T input, VariableType type) {
                IteratorVariableType itType = (IteratorVariableType) type;
                return ops.getMap(input).flatMap(
                        map -> ops.getNumberValue(map.get("id")).flatMap(
                                id -> Iterator.DECODERS.get(id.intValue()).decode(ops, input, itType.content).map(
                                        iteratorPair -> iteratorPair.mapFirst(
                                                iterator -> new IteratorVariable(iterator, itType)))));
            }

            @Override
            protected <T> DataResult<T> write(Variable input, DynamicOps<T> ops, T prefix) {
                Iterator iterator = ((IteratorVariable)input).iterator;
                return iterator.getEncoder().encode(iterator, ops, prefix)
                        .flatMap(encoded -> ops.mapBuilder()
                                .add("id", ops.createInt(iterator.getID()))
                                .build(encoded));
            }
        });

        static {
            Iterator.Casted.ID = Iterator.DECODERS.size();
            Iterator.DECODERS.add(Iterator.Casted.CODEC);
            Iterator.Empty.ID = Iterator.DECODERS.size();
            Iterator.DECODERS.add(Iterator.Empty.CODEC);
            VarCommand.RectangleIterator.ID = Iterator.DECODERS.size();
            Iterator.DECODERS.add(VarCommand.RectangleIterator.CODEC);
            VarCommand.EllipseIterator.ID = Iterator.DECODERS.size();
            Iterator.DECODERS.add(VarCommand.EllipseIterator.CODEC);
            VarCommand.CuboidIterator.ID = Iterator.DECODERS.size();
            Iterator.DECODERS.add(VarCommand.CuboidIterator.CODEC);
            VarCommand.EllipsoidIterator.ID = Iterator.DECODERS.size();
            Iterator.DECODERS.add(VarCommand.EllipsoidIterator.CODEC);
            Iterator.List.ID = Iterator.DECODERS.size();
            Iterator.DECODERS.add(Iterator.List.CODEC);
        }
    }

    public interface Iterator {

        ArrayList<Iterator.IteratorCodec> DECODERS = new ArrayList<>();

        boolean hasNext();
        Variable next() throws CommandSyntaxException;
        int getCount();
        int getID();
        boolean equals(Object o);
        int hashCode();

        IteratorCodec getEncoder();

        Iterator copy();

        default Iterator cast(VariableType contentType) {
            return new Casted(this, contentType);
        }

        interface IteratorCodec {
            <T> DataResult<Pair<Iterator, T>> decode(DynamicOps<T> ops, T input, Variable.VariableType contentType);
            <T> DataResult<T> encode(Iterator input, DynamicOps<T> ops, T prefix);
        }

        class Casted implements Iterator {

            public static int ID;
            public static final IteratorCodec CODEC = new IteratorCodec() {

                @Override
                public <T> DataResult<Pair<Iterator, T>> decode(DynamicOps<T> ops, T input, VariableType contentType) {
                    return ops.getMap(input).flatMap(
                            map -> ops.getByteBuffer(map.get("type")).flatMap(
                                    rawType -> Variable.VariableType.decodeType(rawType.array(), new VariableType.OffsetHolder()).flatMap(
                                            type -> ops.getMap(map.get("prev")).flatMap(
                                                    rawPrev -> ops.getNumberValue(rawPrev.get("id")).flatMap(
                                                            prevId -> Iterator.DECODERS.get(prevId.intValue()).decode(ops, map.get("prev"), type).map(
                                                                    prevPair -> prevPair.mapFirst(prev -> new Casted(prev, new IteratorVariableType(type)))))))));
                }

                @Override
                public <T> DataResult<T> encode(Iterator input, DynamicOps<T> ops, T prefix) {
                    Iterator prev = ((Casted)input).prev;
                    return ops.mapBuilder()
                            .add("type", ops.createByteList(ByteBuffer.wrap(VariableType.getEncoded(((Casted)input).contentType))))
                            .add("prev", prev.getEncoder().encode(prev, ops, ops.emptyMap())
                                    .flatMap(encodedPrev -> ops.mapBuilder()
                                            .add("id", ops.createInt(prev.getID()))
                                            .build(encodedPrev)))
                            .build(prefix);
                }
            };

            private final Iterator prev;
            private final VariableType contentType;

            public Casted(Iterator prev, VariableType contentType) {
                this.prev = prev;
                this.contentType = contentType;
            }

            @Override
            public int getCount() {
                return prev.getCount();
            }

            @Override
            public int getID() {
                return ID;
            }

            @Override
            public boolean equals(Object o) {
                return o instanceof Casted casted && casted.contentType.equals(contentType) && casted.prev.equals(casted);
            }

            @Override
            public int hashCode() {
                return prev.hashCode() ^ contentType.hashCode();
            }

            @Override
            public IteratorCodec getEncoder() {
                return CODEC;
            }

            @Override
            public Iterator copy() {
                return new Casted(prev.copy(), contentType);
            }

            @Override
            public boolean hasNext() {
                return prev.hasNext();
            }

            @Override
            public Variable next() throws CommandSyntaxException {
                return VariableManager.castVariable(contentType, prev.next());
            }
        }

        class Empty implements Iterator {

            public static int ID;
            public static final Empty INSTANCE = new Empty();
            public static final IteratorCodec CODEC = new IteratorCodec() {
                @Override
                public <T> DataResult<Pair<Iterator, T>> decode(DynamicOps<T> ops, T input, VariableType contentType) {
                    return DataResult.success(Pair.of(Empty.INSTANCE, ops.empty()));
                }

                @Override
                public <T> DataResult<T> encode(Iterator input, DynamicOps<T> ops, T prefix) {
                    return DataResult.success(prefix);
                }
            };

            @Override
            public boolean hasNext() {
                return false;
            }

            @Override
            public Variable next() throws CommandSyntaxException {
                throw new NoSuchElementException();
            }

            @Override
            public int getCount() {
                return 0;
            }

            @Override
            public int getID() {
                return ID;
            }

            @Override
            public boolean equals(Object o) {
                return o instanceof Empty;
            }

            @Override
            public int hashCode() {
                return 0;
            }

            @Override
            public IteratorCodec getEncoder() {
                return CODEC;
            }

            @Override
            public Iterator copy() {
                return this;
            }
        }

        class List implements Iterator {

            public static int ID;

            private final ListVariable list;

            private int index;

            public List(ListVariable list) {
                this(copyList(list), 0);
            }

            private static ListVariable copyList(ListVariable list) {
                ListVariable result = new ListVariable(list.type);
                result.value.addAll(list.value);
                return result;
            }

            protected  List(ListVariable list, int index) {
                this.list = list;
                this.index = index;
            }

            @Override
            public boolean hasNext() {
                return index < list.intValue();
            }

            @Override
            public Variable next() throws CommandSyntaxException {
                return list.value.get(index++);
            }

            @Override
            public int getCount() {
                int count = list.intValue() - index;
                index = list.intValue();
                return count;
            }

            @Override
            public int getID() {
                return ID;
            }

            @Override
            public IteratorCodec getEncoder() {
                return CODEC;
            }

            @Override
            public Iterator copy() {
                return new List(list, index);
            }

            public static final IteratorCodec CODEC = new IteratorCodec() {

                @Override
                public <T> DataResult<T> encode(Iterator input, DynamicOps<T> ops, T prefix) {
                    List it = (List) input;
                    return ops.mapBuilder()
                            .add("index", ops.createInt(it.index))
                            .add("content", VariableCodec.encodeList(it.list.value, ops, ops.empty(), it.list.type.content))
                            .build(prefix);
                }

                @Override
                public <T> DataResult<Pair<Iterator, T>> decode(DynamicOps<T> ops, T input, VariableType contentType) {
                    return ops.getMap(input).flatMap(
                            map -> ops.getNumberValue(map.get("index")).flatMap(
                                            index -> VariableCodec.decodeList(ops, map.get("content"), contentType).map(
                                                    content -> {
                                                        ListVariable var = new ListVariable(new ListVariable.ListVariableType(contentType));
                                                        var.value.addAll(content.getFirst());
                                                        return new Pair<>(new List(var, index.intValue()), ops.empty());
                                                    })));
                }
            };
        }
    }
}
