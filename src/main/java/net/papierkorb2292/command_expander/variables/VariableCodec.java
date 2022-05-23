package net.papierkorb2292.command_expander.variables;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.datafixers.util.Unit;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.ListBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

abstract class VariableCodec { //TODO: Test error handling (After adding /var Command)

    public <T> DataResult<Pair<Variable, T>> decode(DynamicOps<T> ops, T input, Variable.VariableType type) {
        return ops.getMap(input).flatMap(
                result -> {
                    T value = result.get("value");
                    if(value == null) {
                        return DataResult.success(Pair.of(null, ops.empty()));
                    }
                    return read(ops, result.get("value"), type);
                });
    }

    protected abstract <T> DataResult<Pair<Variable, T>> read(DynamicOps<T> ops, T input, Variable.VariableType type);

    public <T> DataResult<T> encode(Variable input, DynamicOps<T> ops, T prefix) {
        if (input != null) {
            return write(input, ops, ops.empty()).flatMap(
                    result -> ops.mapBuilder().add("value", result).build(prefix));
        }
        return DataResult.success(prefix);
    }

    protected abstract <T> DataResult<T> write(Variable input, DynamicOps<T> ops, T prefix);

    /**
     * @see com.mojang.serialization.codecs.ListCodec#decode
     */
    public static <T> DataResult<Pair<List<Variable>, T>> decodeList(DynamicOps<T> ops, T input, Variable.VariableType elementType) {
        return ops.getList(input).setLifecycle(Lifecycle.stable()).flatMap(stream -> {
            final VariableCodec elementCodec = elementType.getTemplate().codec;
            final ArrayList<Variable> elements = new ArrayList<>();
            final Stream.Builder<T> failed = Stream.builder();

            final AtomicReference<DataResult<Unit>> result = new AtomicReference<>();
            result.setPlain(DataResult.success(Unit.INSTANCE, Lifecycle.stable()));

            stream.accept(t -> {
                final DataResult<Pair<Variable, T>> element = elementCodec.decode(ops, t, elementType);
                element.error().ifPresent(e -> failed.add(t));
                result.setPlain(result.getPlain().apply2stable((r, v) -> {
                    elements.add(v.getFirst());
                    return r;
                }, element));
            });

            final T errors = ops.createList(failed.build());

            final Pair<List<Variable>, T> pair = Pair.of(elements, errors);

            return result.getPlain().map(unit -> pair).setPartial(pair);
        });
    }

    /**
     * @see com.mojang.serialization.codecs.ListCodec#encode
     */
    public static <T> DataResult<T> encodeList(List<Variable> input, DynamicOps<T> ops, T prefix, Variable.VariableType elementType) {
        final VariableCodec elementCodec = elementType.getTemplate().codec;
        final ListBuilder<T> builder = ops.listBuilder();
        final T empty = ops.emptyMap();
        final StringBuilder errorBuilder = new StringBuilder().append('(');

        for (int i = 0; i < input.size(); ++i) {
            //builder.add(elementCodec.encode(variable, ops, ops.emptyMap()));
            final DataResult<T> result = elementCodec.encode(input.get(i), ops, empty);
            if (result.error().isPresent()) {
                errorBuilder.append(result.error().get().message()).append(" at index: ").append(i).append("; ");
            }
            final Either<T, DataResult.PartialResult<T>> data = result.promotePartial(error -> {
            }).get();
            builder.add(data.left().isPresent() ? data.left().get() : empty);
        }

        final String error = errorBuilder.append(')').toString();
        final DataResult<T> result = builder.build(prefix);
        return error.length() == 2 ? result : result.flatMap(value -> DataResult.error(error, value));
    }
}
