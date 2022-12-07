package net.papierkorb2292.command_expander.mixin.immediateValues;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.injector.InitVariable;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.text.MutableText;
import net.minecraft.util.JsonHelper;
import net.papierkorb2292.command_expander.variables.immediate.VariableTextContent;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.lang.reflect.Type;
import java.util.Optional;

@Mixin(Text.Serializer.class)
public abstract class TextSerializerMixin {

    @Shadow protected abstract Optional<Text> getSeparator(Type type, JsonDeserializationContext context, JsonObject json);

    @InitVariable(
            method = "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lnet/minecraft/text/MutableText;",
            at = @At(
                    value = "MIXINEXTRAS:THROW",
                    ordinal = 2
            )
    )
    private MutableText command_expander$parseVariableJSONText(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) {
        JsonObject obj = (JsonObject)jsonElement;
        if(!obj.has("variable")) {
            return null;
        }
        return MutableText.of(new VariableTextContent(JsonHelper.getString(obj, "variable"), getSeparator(type, jsonDeserializationContext, obj)));
    }

    @WrapWithCondition(
            method = "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lnet/minecraft/text/MutableText;",
            at = @At(
                    value = "MIXINEXTRAS:THROW",
                    ordinal = 2
            )
    )
    private boolean command_expander$allowVariableJSONText(Throwable throwable, JsonElement jsonElement) {
        return !((JsonObject)jsonElement).has("variable");
    }
}
