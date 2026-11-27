package com.example.hitjump;

import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SoundMixer {
    private static boolean soundManagerSearched = false;
    private static Object soundEngineObj = null;
    private static Field soundSystemField = null;
    private static List<Field> engineMapFields = new ArrayList<Field>();
    private static Field playingSoundsField = null;

    private static Method sysGetVolumeMethod = null;
    private static Method sysSetVolumeMethod = null;
    private static Method sysGetPitchMethod = null;
    private static Method sysSetPitchMethod = null;
    private static Method sysStopMethod = null;
    private static boolean sysMethodsCached = false;

    private static final Set<String> processedChannels = new HashSet<String>();
    private static int cleanupCounter = 0;

    public static void processActiveSounds(Minecraft client) {
        if (client == null) {
            return;
        }
        HitJumpConfig config = HitJumpConfig.getInstance();
        if (!config.soundMixerEnabled) {
            if (!processedChannels.isEmpty()) {
                processedChannels.clear();
            }
            return;
        }

        ensureSoundEngine(client);
        if (soundEngineObj == null || soundSystemField == null) {
            return;
        }

        Object soundSystem;
        try {
            soundSystem = soundSystemField.get(soundEngineObj);
        } catch (Exception ignored) {
            return;
        }
        if (soundSystem == null) {
            return;
        }
        ensureSoundSystemMethods(soundSystem.getClass());
        if (sysSetVolumeMethod == null || sysSetPitchMethod == null || sysStopMethod == null) {
            return;
        }

        Map<?, ?> playingMap = getPlayingSoundsMap();
        if (playingMap == null || playingMap.isEmpty()) {
            if (!processedChannels.isEmpty()) {
                processedChannels.clear();
            }
            return;
        }

        try {
            for (Map.Entry<?, ?> entry : playingMap.entrySet()) {
                Object key = entry.getKey();
                Object soundObj = entry.getValue();
                if (!(key instanceof String) || soundObj == null) {
                    continue;
                }
                String channelId = (String) key;
                if (processedChannels.contains(channelId)) {
                    continue;
                }
                processedChannels.add(channelId);

                String soundId = extractSoundIdentifier(soundObj);
                if (soundId == null || soundId.isEmpty()) {
                    continue;
                }

                applyCategoryMix(soundSystem, channelId, soundId.toLowerCase(), config);
            }
        } catch (Exception ignored) {
        }

        cleanupCounter++;
        if (cleanupCounter >= 80) {
            cleanupCounter = 0;
            try {
                Iterator<String> it = processedChannels.iterator();
                while (it.hasNext()) {
                    if (!playingMap.containsKey(it.next())) {
                        it.remove();
                    }
                }
            } catch (Exception ignored) {
                processedChannels.clear();
            }
        }
    }

    private static void applyCategoryMix(Object soundSystem, String channelId, String id, HitJumpConfig config) {
        boolean catEnabled = false;
        boolean muted = false;
        int volPercent = 100;
        int pitchPercent = 100;

        if (id.contains("explode")) {
            catEnabled = config.soundExplosionEnabled;
            muted = config.soundExplosionMuted;
            volPercent = config.soundExplosionVolume;
            pitchPercent = config.soundExplosionPitch;
        } else if (id.contains("thunder")) {
            catEnabled = config.soundLightningEnabled;
            muted = config.soundLightningMuted;
            volPercent = config.soundLightningVolume;
            pitchPercent = config.soundLightningPitch;
        } else if (id.contains("rain")) {
            catEnabled = config.soundRainEnabled;
            muted = config.soundRainMuted;
            volPercent = config.soundRainVolume;
            pitchPercent = config.soundRainPitch;
        } else if (id.contains("portal")) {
            catEnabled = config.soundPortalEnabled;
            muted = config.soundPortalMuted;
            volPercent = config.soundPortalVolume;
            pitchPercent = config.soundPortalPitch;
        } else if (id.contains("ghast") || id.contains("fireball") || id.contains("blaze")) {
            catEnabled = config.soundGhastEnabled;
            muted = config.soundGhastMuted;
            volPercent = config.soundGhastVolume;
            pitchPercent = config.soundGhastPitch;
        } else if (id.contains("hurt") || id.contains("hit") || id.contains("damage") || id.contains("successful_hit")) {
            catEnabled = config.soundHitEnabled;
            muted = config.soundHitMuted;
            volPercent = config.soundHitVolume;
            pitchPercent = config.soundHitPitch;
        } else {
            return;
        }

        if (!catEnabled) {
            return;
        }

        try {
            if (muted || volPercent <= 0) {
                sysSetVolumeMethod.invoke(soundSystem, channelId, 0.0F);
                sysStopMethod.invoke(soundSystem, channelId);
                return;
            }

            if (volPercent != 100 && sysGetVolumeMethod != null) {
                float curVol = ((Float) sysGetVolumeMethod.invoke(soundSystem, channelId)).floatValue();
                if (curVol <= 0.0F) {
                    curVol = 1.0F;
                }
                float newVol = curVol * (volPercent / 100.0F);
                if (newVol < 0.0F) newVol = 0.0F;
                if (newVol > 2.0F) newVol = 2.0F;
                sysSetVolumeMethod.invoke(soundSystem, channelId, newVol);
            }

            if (pitchPercent != 100 && sysGetPitchMethod != null) {
                float curPitch = ((Float) sysGetPitchMethod.invoke(soundSystem, channelId)).floatValue();
                if (curPitch <= 0.0F) {
                    curPitch = 1.0F;
                }
                float newPitch = curPitch * (pitchPercent / 100.0F);
                if (newPitch < 0.5F) newPitch = 0.5F;
                if (newPitch > 2.0F) newPitch = 2.0F;
                sysSetPitchMethod.invoke(soundSystem, channelId, newPitch);
            }
        } catch (Exception ignored) {
        }
    }

    private static String extractSoundIdentifier(Object soundObj) {
        try {
            for (Method m : soundObj.getClass().getMethods()) {
                if (m.getParameterTypes().length != 0) continue;
                Class<?> rt = m.getReturnType();
                if (rt.isPrimitive() || rt == String.class || rt.getName().startsWith("java.")) continue;
                Object loc = m.invoke(soundObj);
                if (loc != null) {
                    String s = loc.toString();
                    if (s != null && (s.contains(":") || s.contains("."))) {
                        return s;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Map<?, ?> getPlayingSoundsMap() {
        if (playingSoundsField != null) {
            try {
                Object val = playingSoundsField.get(soundEngineObj);
                return (val instanceof Map) ? (Map<?, ?>) val : null;
            } catch (Exception ignored) {
                return null;
            }
        }

        for (int i = 0; i < engineMapFields.size(); i++) {
            Field f = engineMapFields.get(i);
            try {
                Object val = f.get(soundEngineObj);
                if (val instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) val;
                    if (!map.isEmpty()) {
                        Map.Entry<?, ?> first = map.entrySet().iterator().next();
                        Object k = first.getKey();
                        Object v = first.getValue();
                        if (k instanceof String && v != null && !(v instanceof Number) && !(v instanceof String)) {
                            playingSoundsField = f;
                            return map;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static void ensureSoundEngine(Minecraft client) {
        if (soundManagerSearched) {
            return;
        }
        soundManagerSearched = true;
        for (Field mcField : client.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(mcField.getModifiers()) || mcField.getType().isPrimitive()) continue;
            Object obj;
            try {
                mcField.setAccessible(true);
                obj = mcField.get(client);
            } catch (Exception ignored) {
                continue;
            }
            if (obj == null || obj.getClass().isArray()) continue;

            if (tryBindSoundEngine(obj)) {
                return;
            }
            // Check 1 level deeper (Minecraft -> SoundManager -> SoundEngine)
            for (Field subField : obj.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(subField.getModifiers()) || subField.getType().isPrimitive()) continue;
                Object subObj;
                try {
                    subField.setAccessible(true);
                    subObj = subField.get(obj);
                } catch (Exception ignored) {
                    continue;
                }
                if (subObj == null || subObj.getClass().isArray()) continue;
                if (tryBindSoundEngine(subObj)) {
                    return;
                }
            }
        }
    }

    private static boolean tryBindSoundEngine(Object candidate) {
        Field foundSys = null;
        List<Field> maps = new ArrayList<Field>();

        Class<?> cls = candidate.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Class<?> ft = f.getType();
                if (isPaulscodeSoundSystem(ft)) {
                    f.setAccessible(true);
                    foundSys = f;
                } else if (Map.class.isAssignableFrom(ft)) {
                    f.setAccessible(true);
                    maps.add(f);
                }
            }
            cls = cls.getSuperclass();
        }

        if (foundSys != null && !maps.isEmpty()) {
            soundEngineObj = candidate;
            soundSystemField = foundSys;
            engineMapFields = maps;
            return true;
        }
        return false;
    }

    private static boolean isPaulscodeSoundSystem(Class<?> type) {
        Class<?> c = type;
        while (c != null && c != Object.class) {
            if ("paulscode.sound.SoundSystem".equals(c.getName())) {
                return true;
            }
            c = c.getSuperclass();
        }
        return false;
    }

    private static void ensureSoundSystemMethods(Class<?> sysClass) {
        if (sysMethodsCached) {
            return;
        }
        sysMethodsCached = true;
        try {
            sysGetVolumeMethod = sysClass.getMethod("getVolume", String.class);
            sysSetVolumeMethod = sysClass.getMethod("setVolume", String.class, float.class);
            sysGetPitchMethod = sysClass.getMethod("getPitch", String.class);
            sysSetPitchMethod = sysClass.getMethod("setPitch", String.class, float.class);
            sysStopMethod = sysClass.getMethod("stop", String.class);
        } catch (Exception ignored) {
        }
    }
}
