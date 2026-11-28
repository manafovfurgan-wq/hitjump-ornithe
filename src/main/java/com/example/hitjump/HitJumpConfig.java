package com.example.hitjump;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class HitJumpConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("hitjump.json")
            .toFile();

    private static HitJumpConfig instance = new HitJumpConfig();

    public static class ParodyLine {
        public String text;
        public boolean enabled;

        public ParodyLine() {
            this.text = "";
            this.enabled = true;
        }

        public ParodyLine(String text, boolean enabled) {
            this.text = text;
            this.enabled = enabled;
        }
    }

    // Combat: HitJump
    public boolean enabled = true;
    public boolean hitJumpPlayersOnly = true;
    public int jumpFrequency = 100;
    public int latencyTicks = 0;

    // Combat: NoHitDelay
    public boolean noHitDelayEnabled = true;
    public int hitDelayTicks = 0;

    // Movement: NoJumpDelay
    public boolean noJumpDelayEnabled = true;
    public int jumpDelayTicks = 0;

    // Movement: Toggle Sprint
    public boolean toggleSprintEnabled = true;
    public boolean toggleSprintActive = true;
    public int toggleSprintKeyCode = Keyboard.KEY_G;

    // Movement: NoSlow (defaults to Disabled for multiplayer anti-cheat safety)
    public boolean noSlowEnabled = false;
    public boolean noSlowItems = true;
    public boolean noSlowCobwebs = true;
    public boolean noSlowSoulSand = true;

    // Building: GodPlace (-98 = Middle Mouse / Button 3)
    public boolean godPlaceEnabled = true;
    public int godPlaceDelayTicks = 0;
    public int godPlaceKeyCode = -98;

    // Visuals: Hitboxes, See Through Walls, Invis Color, Eye Line
    public boolean wallHitboxesEnabled = true;
    public boolean hitboxThroughWalls = true;
    public boolean hitboxEyeLineEnabled = true;
    public int hitboxTargetMode = 0; // 0 = Players & Mobs, 1 = Players Only, 2 = All Entities
    public int hitboxRange = 64;

    public boolean invisHitboxEnabled = true;
    public int invisHitboxColor = 0xFF2626;

    // Visuals: Custom Block Outline
    public boolean blockOutlineEnabled = true;
    public int blockOutlineWidth = 20; // 5..80 -> 0.5px..8.0px
    public int blockOutlineColor = 0xFFFFFF;
    public int blockOutlineOpacity = 85; // 0..100%

    // Visuals: Nametags, Distance, Armor, TNT, Hit Color, Inventory, Parallax
    public boolean wallNametagsEnabled = true;
    public int nametagRange = 128;
    public int nametagBgColor = 0x000000;
    public int nametagBgOpacity = 35;

    public boolean showDistanceTag = true;
    public boolean distanceUnitBlocks = false; // false = M (Meters), true = B (Blocks)

    public boolean armorEnchantInfoEnabled = true;
    public boolean tntTimerEnabled = true;

    public boolean customHitColorEnabled = false;
    public int customHitColor = 0xFFFFFF;
    public int customHitOpacity = 45;

    public boolean centerInventoryEnabled = true;
    public boolean parallaxFixEnabled = true;

    // Animations: 1.7 & Viewmodel
    public boolean noSwapDipEnabled = true;
    public boolean blockhitAnimEnabled = true;
    public boolean smoothSneakEnabled = true;
    public boolean headFixEnabled = true;

    // HUD: Parody Corner List
    public boolean parodyHudEnabled = true;
    public int parodyCorner = 1; // 0 = Top-Left, 1 = Top-Right, 2 = Bottom-Left, 3 = Bottom-Right
    public boolean parodySortByWidth = true;
    public int parodyOffsetX = 6;
    public int parodyOffsetY = 6;
    public int parodyScale = 100; // 50..200 (0.50x..2.00x)
    public int parodyTextColor = 0xFFFFFF;
    public boolean parodyTextShadow = true;
    public boolean parodyShowHeader = true;
    public String parodyHeaderText = "Tomato Cream";
    public boolean parodyShowBackground = true;
    public int parodyBgOpacity = 35;
    public boolean parodyChromaWave = false;
    public List<ParodyLine> parodyLines = createDefaultParodyLines();

    // Audio: Sound Mixer (Volume: 0..200%, Pitch: 50..200 -> 0.50x..2.00x)
    public boolean soundMixerEnabled = true;

    public boolean soundHitEnabled = true;
    public boolean soundHitMuted = false;
    public int soundHitVolume = 100;
    public int soundHitPitch = 100;

    public boolean soundExplosionEnabled = true;
    public boolean soundExplosionMuted = false;
    public int soundExplosionVolume = 100;
    public int soundExplosionPitch = 100;

    public boolean soundLightningEnabled = true;
    public boolean soundLightningMuted = false;
    public int soundLightningVolume = 100;
    public int soundLightningPitch = 100;

    public boolean soundRainEnabled = false;
    public boolean soundRainMuted = false;
    public int soundRainVolume = 100;
    public int soundRainPitch = 100;

    public boolean soundPortalEnabled = false;
    public boolean soundPortalMuted = false;
    public int soundPortalVolume = 100;
    public int soundPortalPitch = 100;

    public boolean soundGhastEnabled = false;
    public boolean soundGhastMuted = false;
    public int soundGhastVolume = 100;
    public int soundGhastPitch = 100;

    // Settings: Anti-Accidental Menu Trigger
    public int menuKeyCode = Keyboard.KEY_H;
    public int menuTriggerMode = 0; // 0 = Double-Tap, 1 = Modifier + Key, 2 = Single Press
    public int menuModifierKey = Keyboard.KEY_LCONTROL;

    public static List<ParodyLine> createDefaultParodyLines() {
        List<ParodyLine> list = new ArrayList<ParodyLine>();
        list.add(new ParodyLine("SkillToggled", true));
        list.add(new ParodyLine("WorseHitreg", true));
        list.add(new ParodyLine("UnsafeWalk", true));
        list.add(new ParodyLine("RealLag", true));
        list.add(new ParodyLine("SlowPlace Blatant", true));
        list.add(new ParodyLine("Scaffold ChaCha Mode", true));
        return list;
    }

    public static HitJumpConfig getInstance() {
        return instance;
    }

    public static void load() {
        if (!CONFIG_FILE.exists()) {
            save();
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
            HitJumpConfig loaded = GSON.fromJson(reader, HitJumpConfig.class);
            if (loaded != null) {
                loaded.clampValues();
                instance = loaded;
            }
        } catch (Exception e) {
            e.printStackTrace();
            instance = new HitJumpConfig();
        }
    }

    public static void save() {
        instance.clampValues();
        File parent = CONFIG_FILE.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
            GSON.toJson(instance, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void resetDefaults() {
        this.enabled = true;
        this.hitJumpPlayersOnly = true;
        this.jumpFrequency = 100;
        this.latencyTicks = 0;

        this.noHitDelayEnabled = true;
        this.hitDelayTicks = 0;

        this.noJumpDelayEnabled = true;
        this.jumpDelayTicks = 0;

        this.toggleSprintEnabled = true;
        this.toggleSprintActive = true;
        this.toggleSprintKeyCode = Keyboard.KEY_G;

        this.noSlowEnabled = false;
        this.noSlowItems = true;
        this.noSlowCobwebs = true;
        this.noSlowSoulSand = true;

        this.godPlaceEnabled = true;
        this.godPlaceDelayTicks = 0;
        this.godPlaceKeyCode = -98;

        this.wallHitboxesEnabled = true;
        this.hitboxThroughWalls = true;
        this.hitboxEyeLineEnabled = true;
        this.hitboxTargetMode = 0;
        this.hitboxRange = 64;

        this.invisHitboxEnabled = true;
        this.invisHitboxColor = 0xFF2626;

        this.blockOutlineEnabled = true;
        this.blockOutlineWidth = 20;
        this.blockOutlineColor = 0xFFFFFF;
        this.blockOutlineOpacity = 85;

        this.wallNametagsEnabled = true;
        this.nametagRange = 128;
        this.nametagBgColor = 0x000000;
        this.nametagBgOpacity = 35;

        this.showDistanceTag = true;
        this.distanceUnitBlocks = false;

        this.armorEnchantInfoEnabled = true;
        this.tntTimerEnabled = true;

        this.customHitColorEnabled = false;
        this.customHitColor = 0xFFFFFF;
        this.customHitOpacity = 45;

        this.centerInventoryEnabled = true;
        this.parallaxFixEnabled = true;

        this.noSwapDipEnabled = true;
        this.blockhitAnimEnabled = true;
        this.smoothSneakEnabled = true;
        this.headFixEnabled = true;

        this.parodyHudEnabled = true;
        this.parodyCorner = 1;
        this.parodySortByWidth = true;
        this.parodyOffsetX = 6;
        this.parodyOffsetY = 6;
        this.parodyScale = 100;
        this.parodyTextColor = 0xFFFFFF;
        this.parodyTextShadow = true;
        this.parodyShowHeader = true;
        this.parodyHeaderText = "Tomato Cream";
        this.parodyShowBackground = true;
        this.parodyBgOpacity = 35;
        this.parodyChromaWave = false;
        this.parodyLines = createDefaultParodyLines();

        this.soundMixerEnabled = true;

        this.soundHitEnabled = true;
        this.soundHitMuted = false;
        this.soundHitVolume = 100;
        this.soundHitPitch = 100;

        this.soundExplosionEnabled = true;
        this.soundExplosionMuted = false;
        this.soundExplosionVolume = 100;
        this.soundExplosionPitch = 100;

        this.soundLightningEnabled = true;
        this.soundLightningMuted = false;
        this.soundLightningVolume = 100;
        this.soundLightningPitch = 100;

        this.soundRainEnabled = false;
        this.soundRainMuted = false;
        this.soundRainVolume = 100;
        this.soundRainPitch = 100;

        this.soundPortalEnabled = false;
        this.soundPortalMuted = false;
        this.soundPortalVolume = 100;
        this.soundPortalPitch = 100;

        this.soundGhastEnabled = false;
        this.soundGhastMuted = false;
        this.soundGhastVolume = 100;
        this.soundGhastPitch = 100;

        this.menuKeyCode = Keyboard.KEY_H;
        this.menuTriggerMode = 0;
        this.menuModifierKey = Keyboard.KEY_LCONTROL;
    }

    public void clampValues() {
        jumpFrequency = clamp(jumpFrequency, 0, 100);
        latencyTicks = clamp(latencyTicks, 0, 10);
        jumpDelayTicks = clamp(jumpDelayTicks, 0, 10);
        hitDelayTicks = clamp(hitDelayTicks, 0, 10);
        godPlaceDelayTicks = clamp(godPlaceDelayTicks, 0, 3);

        if (!isValidBind(godPlaceKeyCode)) godPlaceKeyCode = -98;
        if (!isValidBind(toggleSprintKeyCode)) toggleSprintKeyCode = Keyboard.KEY_G;

        hitboxTargetMode = clamp(hitboxTargetMode, 0, 2);
        hitboxRange = clamp(hitboxRange, 8, 256);
        nametagRange = clamp(nametagRange, 16, 256);

        invisHitboxColor &= 0xFFFFFF;

        blockOutlineWidth = clamp(blockOutlineWidth, 5, 80);
        blockOutlineColor &= 0xFFFFFF;
        blockOutlineOpacity = clamp(blockOutlineOpacity, 0, 100);

        nametagBgColor &= 0xFFFFFF;
        nametagBgOpacity = clamp(nametagBgOpacity, 0, 100);

        customHitColor &= 0xFFFFFF;
        customHitOpacity = clamp(customHitOpacity, 0, 100);

        parodyCorner = clamp(parodyCorner, 0, 3);
        parodyOffsetX = clamp(parodyOffsetX, 0, 150);
        parodyOffsetY = clamp(parodyOffsetY, 0, 150);
        parodyScale = clamp(parodyScale, 50, 200);
        parodyTextColor &= 0xFFFFFF;
        parodyBgOpacity = clamp(parodyBgOpacity, 0, 100);
        if (parodyHeaderText == null) {
            parodyHeaderText = "Tomato Cream";
        }
        if (parodyLines == null) {
            parodyLines = createDefaultParodyLines();
        } else {
            Iterator<ParodyLine> it = parodyLines.iterator();
            while (it.hasNext()) {
                ParodyLine line = it.next();
                if (line == null || line.text == null) {
                    it.remove();
                }
            }
        }

        soundHitVolume = clamp(soundHitVolume, 0, 200);
        soundHitPitch = clamp(soundHitPitch, 50, 200);
        soundExplosionVolume = clamp(soundExplosionVolume, 0, 200);
        soundExplosionPitch = clamp(soundExplosionPitch, 50, 200);
        soundLightningVolume = clamp(soundLightningVolume, 0, 200);
        soundLightningPitch = clamp(soundLightningPitch, 50, 200);
        soundRainVolume = clamp(soundRainVolume, 0, 200);
        soundRainPitch = clamp(soundRainPitch, 50, 200);
        soundPortalVolume = clamp(soundPortalVolume, 0, 200);
        soundPortalPitch = clamp(soundPortalPitch, 50, 200);
        soundGhastVolume = clamp(soundGhastVolume, 0, 200);
        soundGhastPitch = clamp(soundGhastPitch, 50, 200);

        if (menuKeyCode <= 0 || menuKeyCode >= 256) menuKeyCode = Keyboard.KEY_H;
        menuTriggerMode = clamp(menuTriggerMode, 0, 2);
        if (menuModifierKey <= 0 || menuModifierKey >= 256) menuModifierKey = Keyboard.KEY_LCONTROL;
    }

    private static boolean isValidBind(int code) {
        boolean mouse = (code >= -100 && code <= -85);
        boolean key = (code > 0 && code < 256);
        return mouse || key;
    }

    private static int clamp(int val, int min, int max) {
        if (val < min) return min;
        if (val > max) return max;
        return val;
    }
}
