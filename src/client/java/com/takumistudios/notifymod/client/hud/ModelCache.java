package com.takumistudios.notifymod.client.hud;

import com.takumistudios.notifymod.NotifyMod;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Modelos 3D de las presentaciones (§8.3, pista {@code model}): ítems y bloques por su id de ítem, y mobs por su id de
 * entidad. Las entidades son copias locales que nunca se añaden al mundo; se rehacen al cambiar de mundo. Solo en el
 * hilo de render.
 */
final class ModelCache {
    private final Map<String, ItemStack> items = new HashMap<>();
    private final Map<String, LivingEntity> entities = new HashMap<>();
    private final Set<String> warned = new HashSet<>();
    private Level entitiesLevel;
    private int nextId = -1_000_000;
    private int disabled;

    ItemStack item(String id) {
        return items.computeIfAbsent(id, key -> {
            Identifier identifier = Identifier.tryParse(key);
            var item = identifier == null ? null : BuiltInRegistries.ITEM.getOptional(identifier).orElse(null);
            if (item == null) {
                warn(key, "no existe ese ítem");
                return ItemStack.EMPTY;
            }
            return new ItemStack(item);
        });
    }

    LivingEntity entity(String id) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        if (level != entitiesLevel) {
            entities.clear();
            entitiesLevel = level;
        }
        if (entities.containsKey(id)) {
            return entities.get(id);
        }
        LivingEntity living = null;
        try {
            Identifier identifier = Identifier.tryParse(id);
            var type = identifier == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(identifier).orElse(null);
            Entity entity = type == null ? null : type.create(level, EntitySpawnReason.LOAD);
            if (entity instanceof LivingEntity l) {
                // Sin id, el render de mobs con equipo (zombis, esqueletos...) lanza una excepción. Ids negativos y
                // propios: nunca coinciden con los de las entidades reales, que el servidor numera desde 1
                l.setId(nextId--);
                living = l;
            } else {
                warn(id, type == null ? "no existe esa entidad" : "solo se pueden mostrar mobs (entidades vivas)");
            }
        } catch (RuntimeException e) {
            warn(id, e.toString());
        }
        entities.put(id, living);
        return living;
    }

    /** Un modelo que falló al dibujarse: no se vuelve a intentar hasta cambiar de mundo (o recargar, para ítems). */
    void disable(String id, boolean entity, RuntimeException e) {
        if (entity) {
            entities.put(id, null);
        } else {
            items.put(id, ItemStack.EMPTY);
        }
        if (warned.add(id + "#render")) {
            disabled++;
            NotifyMod.LOGGER.warn("Modelo {}: falló al dibujarse y se desactiva", id, e);
        }
    }

    int disabledCount() {
        return disabled;
    }

    private void warn(String id, String why) {
        if (warned.add(id)) {
            NotifyMod.LOGGER.warn("Modelo {}: {}", id, why);
        }
    }
}

