package net.papierkorb2292.command_expander.mixin.immediateValues;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.datafixers.util.Either;
import net.minecraft.nbt.*;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.papierkorb2292.command_expander.mixin_method_interfaces.OptionalImmediateValueSupporter;
import net.papierkorb2292.command_expander.variables.Variable;
import net.papierkorb2292.command_expander.variables.VariableHolder;
import net.papierkorb2292.command_expander.variables.immediate.*;
import net.papierkorb2292.command_expander.variables.path.VariablePath;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Stream;

@Mixin(StringNbtReader.class)
public class StringNbtReaderMixin implements OptionalImmediateValueSupporter {

    private @Final @Shadow StringReader reader;

    @Shadow @Final public static Dynamic2CommandExceptionType LIST_MIXED;
    private @Unique boolean command_expander$supportImmediateValues;

    @Override
    public @Unique void command_expander$setSupportImmediateValue(boolean supportImmediateValue) {
        command_expander$supportImmediateValues = supportImmediateValue;
    }

    @Inject(
            method = "parseElement",
            at = @At(
                    value = "INVOKE",
                    target = "net/minecraft/nbt/StringNbtReader.parseElementPrimitive ()Lnet/minecraft/nbt/NbtElement;"
            ),
            cancellable = true
    )
    private void command_expander$tryParseElementImmediateValue(CallbackInfoReturnable<NbtElement> cir) throws CommandSyntaxException {
        if(command_expander$supportImmediateValues && reader.peek() == '(') {
            reader.skip();
            ImmediateValue value = ImmediateValueCompiler.compile(reader);
            reader.expect(')');
            cir.setReturnValue(new NbtProviderElement(context -> {
                try {
                    Either<VariableHolder, Stream<Variable>> result = value.calculate(context);
                    if(result.left().isEmpty()) {
                        throw VariablePath.MULTIPLE_VALUES_TO_SINGLE_VARIABLE_EXCEPTION.create();
                    }
                    return Variable.createNbt(result.left().get().variable);
                } catch (CommandSyntaxException e) {
                    context.getSource().sendError(Texts.toText(e.getRawMessage()));
                }
                return NbtEnd.INSTANCE;
            }));
        }
    }

    @ModifyReturnValue(
            method = "parseCompound",
            at = @At("RETURN")
    )
    private NbtCompound command_expander$createCompoundProviderIfNeeded(NbtCompound original) {
        return NbtProviderCompound.createProviderIfNeeded(original);
    }

    private @Unique NbtType<?> command_expander$listContentType;

    @ModifyVariable(
            method = "parseList",
            at = @At("STORE"),
            ordinal = 0
    )
    private NbtType<?> command_expander$saveListContentType(NbtType<?> original) {
        command_expander$listContentType = original;
        return original;
    }

    @WrapOperation(
            method = "parseList",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/NbtElement;getNbtType()Lnet/minecraft/nbt/NbtType;"
            )
    )
    private NbtType<?> command_expander$omitListGetNbtTypeForProviders(NbtElement element, Operation<NbtType<?>> op) {
        if(command_expander$supportImmediateValues && element instanceof NbtProvider<?>) {
            return command_expander$listContentType;
        }
        return op.call(element);
    }

    @ModifyReturnValue(
            method = "parseList",
            at = @At("RETURN")
    )
    private NbtElement command_expander$createListProviderIfNeeded(NbtElement original) {
        if(!(original instanceof NbtList list) || list.stream().noneMatch(NbtProvider.INSTANCE_OF)) {
            return original;
        }
        NbtType<?> content = command_expander$listContentType;
        return new NbtProviderElement(context -> {
            NbtType<?> type = content;
            int removed = 0;
            for(int i = 0; i < list.size();) {
                NbtElement element = list.get(i);
                if(element instanceof NbtProvider<?> provider) {
                    element = provider.applyContext(context);
                    NbtType<?> type2 = element.getNbtType();
                    if(type == null) {
                        type = type2;
                    } else if(type != type2) {
                        list.remove(i);
                        context.getSource().sendError(Text.of("Return type of immediate value used in nbt list didn't match the type of the list at index " + (i + removed)));
                        ++removed;
                        continue;
                    }
                    list.set(i, element);
                }
                ++i; //Only increase when the index wasn't removed
            }
            return list;
        });
    }

    @WrapOperation(
            method = "parseList",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/NbtList;add(Ljava/lang/Object;)Z"
            )
    )
    private boolean command_expander$avoidListTypeCheckForProviders(NbtList list, Object element, Operation<Boolean> op) {
        if(element instanceof NbtProvider<?> && element instanceof NbtElement nbt) {
            return ((NbtListAccessor) list).getValue().add(nbt);
        }
        return op.call(list, element);
    }
}
