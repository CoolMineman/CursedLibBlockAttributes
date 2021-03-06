/*
 * Copyright (c) 2019 AlexIIL
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package alexiil.mc.lib.attributes;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.tile.Tile;
import alexiil.mc.lib.attributes.misc.LibBlockAttributes;

/** Used by {@link Attribute} to manage the custom adder list.
 * 
 * @param <Instance> The object to map directly against with equals - for example this might be {@link Block}, or
 *            {@link Item}, or {@link BlockEntityType}, or {@link EntityType}.
 * @param <Cls> The class to map directly or hierarchically against - for example this might be {@link Block}, or
 *            {@link Item}, or {@link BlockEntity}, or {@link Entity}. */
final class AdderList<Instance, Cls, Adder> {

    static final int NULL_PRIORITY = 1 << 16;

    // HashMap rather than a ClassValue because the target classes
    // (Block, Item, etc) never unload.
    private static final Map<Class<?>, List<Class<?>>> CLASS_TO_SUPERS = new HashMap<>();

    private final String name;
    private final Class<Cls> usedClass;
    private final ValueEntry<Adder> nullEntry;
    private final Function<Instance, String> toStringFunc;

    int baseOffset = 0;
    int priorityMultiplier = 1;

    // fields rather than an enum map because there's only 2 options
    // and adding types shouldn't happen lightly.

    // Most of these (more complex) fields are null before use
    // as there's a lot of them, but most of them will only be used rarely

    /** {@link AttributeSourceType#INSTANCE} */
    private PriorityEntry instanceValues = null;

    /** {@link AttributeSourceType#COMPAT_WRAPPER} */
    private PriorityEntry compatValues = null;

    private Map<Instance, ValueEntry<Adder>> resolved = null;
    private Map<Class<?>, ValueEntry<Adder>> classResolved = null;

    /** Set to true when a target has been resolved by its class rather than its instance. */
    private boolean resolvedByClass = false;

    /** @param nullValue A non-null value to use to indicate that this doesn't contain any entries for the given key.
     *            Note that this value will not be returned unless it is added to this map separately with any of the
     *            "put" methods. */
    public AdderList(String name, Class<Cls> usedClass, Adder nullValue, Function<Instance, String> toStringFunc) {
        this.name = name;
        this.usedClass = usedClass;
        this.nullEntry = new ValueEntry<>(nullValue, NULL_PRIORITY);
        this.toStringFunc = toStringFunc;
    }

    @Nullable
    public Adder get(Instance key, Class<? extends Cls> clazz) {
        ValueEntry<Adder> value = getEntry(key, clazz);
        return value != nullEntry ? value.value : null;
    }

    public ValueEntry<Adder> getEntry(Instance key, Class<? extends Cls> clazz) {
        ValueEntry<Adder> value = null;
        if (resolved != null) {
            value = resolved.get(key);
            if (value != null && value.priority == 0) {
                return value;
            }
        }
        if (classResolved != null) {
            ValueEntry<Adder> instance = value;
            value = classResolved.get(clazz);
            if (value == null) {
                if (instance != null) {
                    return instance;
                }
            } else {
                if (instance == null) {
                    return value;
                }
                return value.priority < instance.priority ? value : instance;
            }
        }
        if (instanceValues != null) {
            value = instanceValues.get(key, clazz);
            if (value != null) {
                return resolvedByClass ? resolveClassTo(clazz, value) : resolveTo(key, value);
            }
        }
        if (compatValues != null) {
            value = compatValues.get(key, clazz);
            if (value != null) {
                return resolvedByClass ? resolveClassTo(clazz, value) : resolveTo(key, value);
            }
        }
        resolveTo(key, nullEntry);
        return nullEntry;
    }

    private ValueEntry<Adder> resolveTo(Instance key, ValueEntry<Adder> entry) {
        if (resolved == null) {
            resolved = new HashMap<>();
        }
        resolved.put(key, entry);
        return entry;
    }

    private ValueEntry<Adder> resolveClassTo(Class<? extends Cls> key, ValueEntry<Adder> entry) {
        if (classResolved == null) {
            classResolved = new HashMap<>();
        }
        classResolved.put(key, entry);
        return entry;
    }

    private static Iterable<Class<?>> classesToConsider(Class<?> clazz) {
        List<Class<?>> list = CLASS_TO_SUPERS.get(clazz);
        if (list != null) {
            return list;
        }

        Set<Class<?>> classes = new LinkedHashSet<>();
        Class<?> s = clazz;
        do {
            classes.add(s);
            Collections.addAll(classes, s.getInterfaces());
            for (Class<?> cls : s.getInterfaces()) {
                for (Class<?> c2 : classesToConsider(cls)) {
                    classes.add(c2);
                }
            }
        } while ((s = s.getSuperclass()) != null);

        list = new ArrayList<>(classes);
        CLASS_TO_SUPERS.put(clazz, list);
        return list;
    }

    void putExact(AttributeSourceType type, Instance key, Adder value) {
        if (resolved != null) {
            resolved.remove(key);
        }

        PriorityEntry entry = getOrCreateEntry(type);
        if (entry.exactMappings == null) {
            entry.exactMappings = new HashMap<>();
        }

        Adder old = entry.exactMappings.put(key, value);
        LibBlockAttributes.LOGGER
            .warn("Replaced the attribute " + name + " value for " + toStringFunc.apply(key) + " with " + value + " (was " + old + ")");
    }

    void addPredicateBased(
        AttributeSourceType type, boolean specific, Predicate<? super Instance> predicate, Adder value
    ) {
        if (specific) {
            addSpecificPredicateBased(type, predicate, value);
        } else {
            addGeneralPredicateBased(type, predicate, value);
        }
    }

    private void addSpecificPredicateBased(
        AttributeSourceType type, Predicate<? super Instance> predicate, Adder value
    ) {
        clearResolved();

        PriorityEntry entry = getOrCreateEntry(type);
        if (entry.specificPredicates == null) {
            entry.specificPredicates = new ArrayList<>();
        }
        entry.specificPredicates.add(new PredicateEntry<>(predicate, value));
    }

    private boolean hasWarnedAboutUC;

    void putClassBased(AttributeSourceType type, Class<?> clazz, boolean matchSubclasses, Adder value) {

        if (!matchSubclasses) {
            if (clazz.isInterface()) {
                throw new IllegalArgumentException(
                    "The given " + clazz + " is an interface, and matchSubclasses is set to false - "
                        + "which will never match anything, as it's impossible to construct an interface."
                );
            }
            if ((clazz.getModifiers() & Modifier.ABSTRACT) != 0) {
                throw new IllegalArgumentException(
                    "The given " + clazz + " is abstract, and matchSubclasses is set to false - "
                        + "which will never match anything, as it's impossible to construct an abstract class."
                );
            }
        }

        if (clazz.isAssignableFrom(usedClass)) {
                throw new IllegalArgumentException(
                    "The given " + clazz + " is a superclass/superinterface of the base " + usedClass
                        + " - which won't work very well, because it will override everything else."
                );
        }

        clearResolved();

        PriorityEntry entry = getOrCreateEntry(type);
        final Map<Class<?>, Adder> map;
        if (matchSubclasses) {
            if (entry.inheritClassMappings == null) {
                entry.inheritClassMappings = new HashMap<>();
            }
            map = entry.inheritClassMappings;

        } else {
            if (entry.exactClassMappings == null) {
                entry.exactClassMappings = new HashMap<>();
            }
            map = entry.exactClassMappings;

        }
        Adder old = map.put(clazz, value);
        if (old != null) {
            LibBlockAttributes.LOGGER.warn(
                "Replaced the attribute " + name + " value for " + clazz + " with " + value + " (was " + old + ")"
            );
        }
    }

    private void addGeneralPredicateBased(
        AttributeSourceType type, Predicate<? super Instance> predicate, Adder value
    ) {
        clearResolved();

        PriorityEntry entry = getOrCreateEntry(type);
        if (entry.generalPredicates == null) {
            entry.generalPredicates = new ArrayList<>();
        }
        entry.generalPredicates.add(new PredicateEntry<>(predicate, value));
    }

    private PriorityEntry getOrCreateEntry(AttributeSourceType type) {
        switch (type) {
            case INSTANCE: {
                if (instanceValues == null) {
                    instanceValues = new PriorityEntry(8 * (baseOffset + priorityMultiplier * type.ordinal()));
                }
                return instanceValues;
            }
            case COMPAT_WRAPPER: {
                if (compatValues == null) {
                    compatValues = new PriorityEntry(8 * (baseOffset + priorityMultiplier * type.ordinal()));
                }
                return compatValues;
            }
            default: {
                throw new IllegalArgumentException("Unknown AttributeSourceType" + type + "!");
            }
        }
    }

    private void clearResolved() {
        resolved = null;
        classResolved = null;
    }

    static final class PredicateEntry<K, V> {
        final Predicate<? super K> predicate;
        final V value;

        public PredicateEntry(Predicate<? super K> predicate, V value) {
            this.predicate = predicate;
            this.value = value;
        }
    }

    static final class ValueEntry<V> {
        final V value;

        /**
         * <ol>
         * <li>0-7 for {@link AttributeSourceType#INSTANCE}</li>
         * <li>8-15 for {@link AttributeSourceType#COMPAT_WRAPPER}</li>
         * <li>{@link #NULL_PRIORITY} for missing entry.</li>
         * </ol>
         */
        final int priority;

        public ValueEntry(V value, int priority) {
            this.value = value;
            this.priority = priority;
        }
    }

    final class PriorityEntry {
        private final int basePriority;

        private Map<Instance, Adder> exactMappings = null;
        private List<PredicateEntry<Instance, Adder>> specificPredicates = null;
        private Map<Class<?>, Adder> exactClassMappings = null;
        private Map<Class<?>, Adder> inheritClassMappings = null;
        private List<PredicateEntry<Instance, Adder>> generalPredicates = null;

        PriorityEntry(int basePriority) {
            this.basePriority = basePriority;
        }

        @Nullable
        ValueEntry<Adder> get(Instance key, Class<? extends Cls> clazz) {
            resolvedByClass = false;
            Adder value;
            if (exactMappings != null) {
                value = exactMappings.get(key);
                if (value != null) {
                    return new ValueEntry<>(value, basePriority);
                }
            }
            if (specificPredicates != null) {
                for (PredicateEntry<Instance, Adder> entry : specificPredicates) {
                    if (entry.predicate.test(key)) {
                        return new ValueEntry<>(entry.value, basePriority + 1);
                    }
                }
            }
            if (exactClassMappings != null) {
                value = exactMappings.get(clazz);
                if (value != null) {
                    resolvedByClass = true;
                    return new ValueEntry<>(value, basePriority + 2);
                }
            }
            if (inheritClassMappings != null) {
                for (Class<?> cls : classesToConsider(clazz)) {
                    value = inheritClassMappings.get(cls);
                    if (value != null) {
                        resolvedByClass = true;
                        return new ValueEntry<>(value, basePriority + 3);
                    }
                }
            }
            if (generalPredicates != null) {
                for (PredicateEntry<Instance, Adder> entry : generalPredicates) {
                    if (entry.predicate.test(key)) {
                        return new ValueEntry<>(entry.value, basePriority + 4);
                    }
                }
            }
            return null;
        }
    }
}
