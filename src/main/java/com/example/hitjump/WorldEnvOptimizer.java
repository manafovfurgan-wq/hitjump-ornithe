package com.example.hitjump;

import net.minecraft.client.Minecraft;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class WorldEnvOptimizer {
    private static final Random RANDOM = new Random();

    // Time & Weather reflection caches
    private static boolean worldFieldsSearched = false;
    private static Field worldInfoField = null;
    private static Field worldTimeField = null;

    private static Field prevRainField = null;
    private static Field rainField = null;
    private static Field prevThunderField = null;
    private static Field thunderField = null;

    // Particle Optimizer reflection caches
    private static boolean particleMgrSearched = false;
    private static Object particleMgrObj = null;
    private static Field fxLayersField = null;

    private static boolean fxFieldsSearched = false;
    private static Field fxTexXField = null;
    private static Field fxTexYField = null;
    private static Field fxAgeField = null;
    private static Field fxMaxAgeField = null;
    private static Field fxScaleField = null;
    private static Field fxRedField = null;
    private static Field fxGreenField = null;
    private static Field fxBlueField = null;

    private static final Set<Object> processedParticles =
            Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
    private static int pruneCounter = 0;

    public static void onClientTick(Minecraft client) {
        if (client == null || client.world == null) {
            return;
        }
        HitJumpConfig config = HitJumpConfig.getInstance();
        applyTimeAndWeather(client, config);
        if (config.particleOptEnabled) {
            optimizeCritAndSharpParticles(client, config);
        }
    }

    public static void onRenderTick(Minecraft client) {
        if (client == null || client.world == null) {
            return;
        }
        HitJumpConfig config = HitJumpConfig.getInstance();
        if (config.timeChangerEnabled || config.weatherMode != 0) {
            applyTimeAndWeather(client, config);
        }
    }

    private static void applyTimeAndWeather(Minecraft client, HitJumpConfig config) {
        if (!config.timeChangerEnabled && config.weatherMode == 0) {
            return;
        }
        ensureWorldEnvFields(client.world);

        if (config.timeChangerEnabled && worldInfoField != null && worldTimeField != null) {
            try {
                Object info = worldInfoField.get(client.world);
                if (info != null) {
                    worldTimeField.setLong(info, config.customTimeTicks);
                }
            } catch (Throwable ignored) {
            }
        }

        if (config.weatherMode != 0 && rainField != null && prevRainField != null
                && thunderField != null && prevThunderField != null) {
            try {
                float rainVal = (config.weatherMode == 2 || config.weatherMode == 3) ? 1.0F : 0.0F;
                float thunderVal = (config.weatherMode == 3) ? 1.0F : 0.0F;
                prevRainField.setFloat(client.world, rainVal);
                rainField.setFloat(client.world, rainVal);
                prevThunderField.setFloat(client.world, thunderVal);
                thunderField.setFloat(client.world, thunderVal);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void optimizeCritAndSharpParticles(Minecraft client, HitJumpConfig config) {
        ensureParticleManager(client);
        if (particleMgrObj == null || fxLayersField == null) {
            return;
        }

        Object layersObj;
        try {
            layersObj = fxLayersField.get(particleMgrObj);
        } catch (Throwable ignored) {
            return;
        }
        if (!(layersObj instanceof List[][])) {
            return;
        }
        List<?>[][] layers = (List<?>[][]) layersObj;
        if (layers.length == 0 || layers[0] == null) {
            return;
        }

        HitJumpMod.ensureEntityMotionFields(client.player);

        // Crit and Sharpness (EntityCrit2FX) live in particle layer 0
        for (int sub = 0; sub < layers[0].length; sub++) {
            List<?> rawList = layers[0][sub];
            if (rawList == null || rawList.isEmpty()) {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) rawList;
            List<Object> toAdd = null;

            int size = list.size();
            for (int i = 0; i < size; i++) {
                Object fx;
                try {
                    fx = list.get(i);
                } catch (Throwable ignored) {
                    break;
                }
                if (fx == null || processedParticles.contains(fx)) {
                    continue;
                }
                processedParticles.add(fx);

                ensureEntityFxFields(fx);
                if (fxTexXField == null || fxTexYField == null || fxScaleField == null) {
                    continue;
                }

                try {
                    int texX = fxTexXField.getInt(fx);
                    int texY = fxTexYField.getInt(fx);

                    // Vanilla 1.8.9 particle atlas: Crit = index 65 (x=1, y=4), Sharpness/Magic = index 66 (x=2, y=4)
                    boolean isCrit = (texY == 4 && texX == 1);
                    boolean isSharp = (texY == 4 && texX == 2);
                    if (!isCrit && !isSharp) {
                        continue;
                    }

                    int amountPct = isSharp ? config.sharpParticleAmount : config.critParticleAmount;
                    int scalePct = isSharp ? config.sharpParticleScale : config.critParticleScale;

                    // Amount < 100%: hide particle completely or probabilistically
                    if (amountPct <= 0 || (amountPct < 100 && RANDOM.nextInt(100) >= amountPct)) {
                        if (fxAgeField != null && fxMaxAgeField != null) {
                            fxAgeField.setInt(fx, fxMaxAgeField.getInt(fx) + 1);
                        }
                        if (HitJumpMod.entityDeadField != null) {
                            HitJumpMod.entityDeadField.setBoolean(fx, true);
                        }
                        continue;
                    }

                    // Apply custom scale
                    float origScale = fxScaleField.getFloat(fx);
                    float newScale = origScale * (scalePct / 100.0F);
                    if (newScale < 0.05F) newScale = 0.05F;
                    if (newScale > 5.0F) newScale = 5.0F;
                    fxScaleField.setFloat(fx, newScale);

                    // Amount > 100%: spawn additional cloned particles around the hit
                    if (amountPct > 100) {
                        int extraCount = (amountPct - 100) / 100;
                        int remainder = (amountPct - 100) % 100;
                        if (remainder > 0 && RANDOM.nextInt(100) < remainder) {
                            extraCount++;
                        }
                        if (extraCount > 0 && list.size() < 2400) {
                            for (int c = 0; c < extraCount; c++) {
                                Object clone = cloneParticle(client.world, fx, texX, texY, newScale);
                                if (clone != null) {
                                    processedParticles.add(clone);
                                    if (toAdd == null) toAdd = new ArrayList<Object>();
                                    toAdd.add(clone);
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
            }

            if (toAdd != null && !toAdd.isEmpty()) {
                try {
                    list.addAll(toAdd);
                } catch (Throwable ignored) {
                }
            }
        }

        pruneCounter++;
        if (pruneCounter >= 200 || processedParticles.size() > 3500) {
            pruneCounter = 0;
            processedParticles.clear();
            for (int sub = 0; sub < layers[0].length; sub++) {
                List<?> l = layers[0][sub];
                if (l == null) continue;
                for (int i = 0; i < l.size(); i++) {
                    try {
                        Object o = l.get(i);
                        if (o != null) processedParticles.add(o);
                    } catch (Throwable ignored) {
                        break;
                    }
                }
            }
        }
    }

    private static Object cloneParticle(Object world, Object template, int texX, int texY, float scaledSize) {
        if (HitJumpMod.entityXField == null || HitJumpMod.entityVelXField == null) {
            return null;
        }
        try {
            double x = HitJumpMod.entityXField.getDouble(template) + (RANDOM.nextDouble() - 0.5D) * 0.22D;
            double y = HitJumpMod.entityYField.getDouble(template) + (RANDOM.nextDouble() - 0.5D) * 0.22D;
            double z = HitJumpMod.entityZField.getDouble(template) + (RANDOM.nextDouble() - 0.5D) * 0.22D;
            double vx = HitJumpMod.entityVelXField.getDouble(template) + (RANDOM.nextDouble() - 0.5D) * 0.16D;
            double vy = HitJumpMod.entityVelYField.getDouble(template) + (RANDOM.nextDouble() - 0.5D) * 0.14D;
            double vz = HitJumpMod.entityVelZField.getDouble(template) + (RANDOM.nextDouble() - 0.5D) * 0.16D;

            Constructor<?>[] ctors = template.getClass().getDeclaredConstructors();
            Object created = null;
            for (Constructor<?> ctor : ctors) {
                Class<?>[] p = ctor.getParameterTypes();
                if (p.length == 7 && p[0].isInstance(world)
                        && p[1] == double.class && p[2] == double.class && p[3] == double.class
                        && p[4] == double.class && p[5] == double.class && p[6] == double.class) {
                    ctor.setAccessible(true);
                    created = ctor.newInstance(world, x, y, z, vx, vy, vz);
                    break;
                }
            }
            if (created == null) {
                return null;
            }

            fxTexXField.setInt(created, texX);
            fxTexYField.setInt(created, texY);
            fxScaleField.setFloat(created, scaledSize);
            if (fxRedField != null && fxGreenField != null && fxBlueField != null) {
                fxRedField.setFloat(created, fxRedField.getFloat(template));
                fxGreenField.setFloat(created, fxGreenField.getFloat(template));
                fxBlueField.setFloat(created, fxBlueField.getFloat(template));
            }
            return created;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void ensureWorldEnvFields(Object world) {
        if (worldFieldsSearched || world == null) {
            return;
        }
        worldFieldsSearched = true;
        Class<?> cls = world.getClass();
        while (cls != null && cls != Object.class && !isSkippableClass(cls)) {
            Field[] fields;
            try {
                fields = cls.getDeclaredFields();
            } catch (Throwable ignored) {
                break;
            }

            // Locate weather fields: int updateLCG, final int DIST_HASH_MAGIC(1013904223), 4x float rain/thunder, int lastLightning
            if (rainField == null) {
                for (int i = 0; i <= fields.length - 7; i++) {
                    if (isInstanceField(fields[i], int.class)
                            && isInstanceField(fields[i + 1], int.class)
                            && isInstanceField(fields[i + 2], float.class)
                            && isInstanceField(fields[i + 3], float.class)
                            && isInstanceField(fields[i + 4], float.class)
                            && isInstanceField(fields[i + 5], float.class)
                            && isInstanceField(fields[i + 6], int.class)) {
                        try {
                            fields[i + 1].setAccessible(true);
                            if (fields[i + 1].getInt(world) == 1013904223) {
                                prevRainField = fields[i + 2];
                                rainField = fields[i + 3];
                                prevThunderField = fields[i + 4];
                                thunderField = fields[i + 5];
                                prevRainField.setAccessible(true);
                                rainField.setAccessible(true);
                                prevThunderField.setAccessible(true);
                                thunderField.setAccessible(true);
                                break;
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }

            // Locate WorldInfo -> worldTime field: int spawnX, int spawnY, int spawnZ, long totalTime, long worldTime, long lastPlayed, long sizeOnDisk
            if (worldInfoField == null) {
                for (Field f : fields) {
                    if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive() || isSkippableClass(f.getType())) {
                        continue;
                    }
                    Field timeF = findWorldTimeField(f.getType());
                    if (timeF != null) {
                        try {
                            f.setAccessible(true);
                            worldInfoField = f;
                            worldTimeField = timeF;
                            break;
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }

            cls = cls.getSuperclass();
        }
    }

    private static Field findWorldTimeField(Class<?> candidateClass) {
        Class<?> c = candidateClass;
        while (c != null && c != Object.class && !isSkippableClass(c)) {
            Field[] fields;
            try {
                fields = c.getDeclaredFields();
            } catch (Throwable ignored) {
                return null;
            }
            for (int i = 0; i <= fields.length - 7; i++) {
                if (isInstanceField(fields[i], int.class)
                        && isInstanceField(fields[i + 1], int.class)
                        && isInstanceField(fields[i + 2], int.class)
                        && isInstanceField(fields[i + 3], long.class)
                        && isInstanceField(fields[i + 4], long.class)
                        && isInstanceField(fields[i + 5], long.class)
                        && isInstanceField(fields[i + 6], long.class)) {
                    try {
                        fields[i + 4].setAccessible(true);
                        return fields[i + 4];
                    } catch (Throwable ignored) {
                        return null;
                    }
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static void ensureParticleManager(Minecraft client) {
        if (particleMgrSearched || client == null) {
            return;
        }
        particleMgrSearched = true;
        for (Field f : client.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
            Object val;
            try {
                f.setAccessible(true);
                val = f.get(client);
            } catch (Throwable ignored) {
                continue;
            }
            if (val == null || val.getClass().isArray() || isSkippableClass(val.getClass())) continue;

            Field[] subFields;
            try {
                subFields = val.getClass().getDeclaredFields();
            } catch (Throwable ignored) {
                continue;
            }
            for (Field sf : subFields) {
                if (!Modifier.isStatic(sf.getModifiers()) && sf.getType() == List[][].class) {
                    try {
                        sf.setAccessible(true);
                        particleMgrObj = val;
                        fxLayersField = sf;
                        return;
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }

    private static void ensureEntityFxFields(Object fx) {
        if (fxFieldsSearched || fx == null) {
            return;
        }
        fxFieldsSearched = true;
        Class<?> cls = fx.getClass();
        while (cls != null && cls != Object.class && !isSkippableClass(cls)) {
            Field[] fields;
            try {
                fields = cls.getDeclaredFields();
            } catch (Throwable ignored) {
                break;
            }
            // Matches vanilla 1.8.9 EntityFX layout:
            // int texX, int texY, float jitterX, float jitterY, int age, int maxAge,
            // float scale, float gravity, float red, float green, float blue, float alpha
            for (int i = 0; i <= fields.length - 12; i++) {
                if (isInstanceField(fields[i], int.class)
                        && isInstanceField(fields[i + 1], int.class)
                        && isInstanceField(fields[i + 2], float.class)
                        && isInstanceField(fields[i + 3], float.class)
                        && isInstanceField(fields[i + 4], int.class)
                        && isInstanceField(fields[i + 5], int.class)
                        && isInstanceField(fields[i + 6], float.class)
                        && isInstanceField(fields[i + 7], float.class)
                        && isInstanceField(fields[i + 8], float.class)
                        && isInstanceField(fields[i + 9], float.class)
                        && isInstanceField(fields[i + 10], float.class)
                        && isInstanceField(fields[i + 11], float.class)) {
                    try {
                        fxTexXField = fields[i];
                        fxTexYField = fields[i + 1];
                        fxAgeField = fields[i + 4];
                        fxMaxAgeField = fields[i + 5];
                        fxScaleField = fields[i + 6];
                        fxRedField = fields[i + 8];
                        fxGreenField = fields[i + 9];
                        fxBlueField = fields[i + 10];
                        fxTexXField.setAccessible(true);
                        fxTexYField.setAccessible(true);
                        fxAgeField.setAccessible(true);
                        fxMaxAgeField.setAccessible(true);
                        fxScaleField.setAccessible(true);
                        fxRedField.setAccessible(true);
                        fxGreenField.setAccessible(true);
                        fxBlueField.setAccessible(true);
                        return;
                    } catch (Throwable ignored) {
                    }
                }
            }
            cls = cls.getSuperclass();
        }
    }

    private static boolean isInstanceField(Field f, Class<?> t) {
        return !Modifier.isStatic(f.getModifiers()) && f.getType() == t;
    }

    private static boolean isSkippableClass(Class<?> cls) {
        if (cls == null) return true;
        String n = cls.getName();
        return n.startsWith("java.") || n.startsWith("javax.") || n.startsWith("jdk.") || n.startsWith("sun.")
                || n.startsWith("com.google.") || n.startsWith("org.apache.") || n.startsWith("io.netty.")
                || n.startsWith("it.unimi.") || n.startsWith("org.lwjgl.") || n.startsWith("org.slf4j.");
    }
}
