package net.papierkorb2292.command_expander.mixin;

import net.minecraft.command.EntitySelectorReader;
import net.minecraft.command.FloatRangeArgument;
import net.minecraft.entity.Entity;
import net.minecraft.predicate.NumberRange;
import net.minecraft.util.math.Vec3d;
import net.papierkorb2292.command_expander.EntityPredicateContainer;
import net.papierkorb2292.command_expander.mixin_method_interfaces.EntitySelectorReaderEqualityChecker;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

@Mixin(EntitySelectorReader.class)
public abstract class EntitySelectorReaderMixin implements EntitySelectorReaderEqualityChecker {

    @Shadow private boolean includesNonPlayers;
    @Shadow private boolean localWorldOnly;
    @Shadow private int limit;
    @Shadow private NumberRange.FloatRange distance;
    @Shadow private NumberRange.IntRange levelRange;
    @Shadow private @Nullable Double x;
    @Shadow private @Nullable Double y;
    @Shadow private @Nullable Double z;
    @Shadow private @Nullable Double dz;
    @Shadow private @Nullable Double dy;
    @Shadow private @Nullable Double dx;
    @Shadow private FloatRangeArgument pitchRange;
    @Shadow private FloatRangeArgument yawRange;
    @Shadow private BiConsumer<Vec3d, List<? extends Entity>> sorter;
    @Shadow private boolean senderOnly;
    @Shadow private @Nullable String playerName;
    @Shadow private @Nullable UUID uuid;
    private final Set<EntityPredicateContainer> command_expander$entityPredicates = new HashSet<>();
    private boolean command_expander$unequal;

    @Inject(
            method = "setPredicate",
            at = @At("HEAD")
    )
    private void cachePredicate(Predicate<Entity> predicate, CallbackInfo ci) {
        if(!command_expander$unequal) {
            Class<?> predicateClass = predicate.getClass();
            Field[] fields = predicateClass.getDeclaredFields();
            int fieldsLength = fields.length;
            List<Object> params = new ArrayList<>(fieldsLength);
            for (Field field : fields) { // Saving all fields of the predicate (for example captured locals in a lambda)
                                         // to later compare them
                if ((field.getModifiers() & Modifier.STATIC) == Modifier.STATIC) {
                    continue;
                }
                if (field.trySetAccessible()) {
                    try {
                        params.add(field.get(predicate));
                    } catch (IllegalAccessException e) {
                        command_expander$unequal = true;
                        return;
                    }
                } else {
                    command_expander$unequal = true;
                    return;
                }
            }
            command_expander$entityPredicates.add(new EntityPredicateContainer(predicateClass.getSimpleName(), params));
        }
    }

    @Override
    public boolean command_expander$isEqual(EntitySelectorReader other) {
        EntitySelectorReaderMixin otherMixin = (EntitySelectorReaderMixin)(Object)other;
        return !command_expander$unequal && command_expander$entityPredicates.equals(otherMixin.command_expander$entityPredicates)
                && limit == otherMixin.limit && includesNonPlayers == otherMixin.includesNonPlayers && localWorldOnly == otherMixin.localWorldOnly
                && Objects.equals(distance.getMax(), otherMixin.distance.getMax()) && Objects.equals(distance.getMin(), otherMixin.distance.getMin())
                && Objects.equals(levelRange.getMax(), otherMixin.levelRange.getMax()) && Objects.equals(levelRange.getMin(), otherMixin.levelRange.getMin())
                && Objects.equals(x, otherMixin.x) && Objects.equals(y, otherMixin.y) && Objects.equals(z, otherMixin.z)
                && Objects.equals(dx, otherMixin.dx) && Objects.equals(dy, otherMixin.dy) && Objects.equals(dz, otherMixin.dz)
                && Objects.equals(pitchRange.getMax(), otherMixin.pitchRange.getMax()) && Objects.equals(pitchRange.getMin(), otherMixin.pitchRange.getMin())
                && Objects.equals(yawRange.getMax(), otherMixin.yawRange.getMax()) && Objects.equals(yawRange.getMin(), otherMixin.yawRange.getMin())
                && sorter == otherMixin.sorter && senderOnly == otherMixin.senderOnly && Objects.equals(playerName, otherMixin.playerName)
                && Objects.equals(uuid, otherMixin.uuid);
    }
}
