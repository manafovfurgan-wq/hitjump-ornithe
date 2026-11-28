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

    // Movement: Toggle Sprint & QoL
    public boolean toggleSprintEnabled = true;
    public boolean toggleSprintActive = true;
    public int toggleSprintKeyCode = Keyboard.KEY_G;
    public boolean keepMovingAfterGuiEnabled = true;
    public boolean noScrollEnabled = false;

    // Movement: NoSlow
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

    // Visuals: Custom Block Outline & Block Overlay
    public boolean blockOutlineEnabled = true;
    public boolean blockOutlineFullBlock = false;
    public int blockOutlineWidth = 20;
    public int blockOutlineColor = 0xFFFFFF;
    public int blockOutlineOpacity = 85;

    public boolean blockOverlayEnabled = false;
    public boolean blockOverlayFaceOnly = true;
    public int blockOverlayBlendMode = 0; // 0 = Normal, 1 = Additive, 2 = Invert
    public int blockOverlayColor = 0xFFFFFF;
    public int blockOverlayOpacity = 30;

    // Visuals: Night Vision / Fullbright
    public boolean nightVisionEnabled = false;

    // Visuals: Nametags, Distance, Armor, TNT, Hit Color, Inventory, Parallax
    public boolean wallNametagsEnabled = true;
    public int nametagRange = 128;
    public int nametagBgColor = 0x000000;
    public int nametagBgOpacity = 35;

    public boolean showDistanceTag = true;
    public boolean distanceUnitBlocks = false;

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
    public int parodyScale = 100;
    public int parodyTextColor = 0xFFFFFF;
    public boolean parodyTextShadow = true;
    public boolean parodyShowHeader = true;
    public String parodyHeaderText = "Skill Issue+";
    public boolean parodyShowBackground = true;
    public int parodyBgOpacity = 35;
    public boolean parodyChromaWave = false;
    public List<ParodyLine> parodyLines = createDefaultParodyLines();

    // HUD Wave 2: FPS
    public boolean fpsHudEnabled = true;
    public int fpsX = 6;
    public int fpsY = 126;
    public int fpsScale = 100;
    public int fpsColor = 0xFFFFFF;
    public boolean fpsChroma = false;
    public int fpsBgColor = 0x000000;
    public int fpsBgOpacity = 40;

    // HUD Wave 2: CPS
    public boolean cpsHudEnabled = true;
    public int cpsMode = 0; // 0 = LMB | RMB, 1 = LMB Only, 2 = RMB Only
    public int cpsX = 6;
    public int cpsY = 144;
    public int cpsScale = 100;
    public int cpsColor = 0xFFFFFF;
    public boolean cpsChroma = false;
    public int cpsBgColor = 0x000000;
    public int cpsBgOpacity = 40;

    // HUD Wave 2: Keystrokes
    public boolean keystrokesEnabled = true;
    public boolean keystrokesShowCps = true;
    public boolean keystrokesShowSpace = true;
    public int keystrokesX = 6;
    public int keystrokesY = 164;
    public int keystrokesScale = 100;
    public int keystrokesColor = 0xFFFFFF;
    public boolean keystrokesChroma = false;
    public int keystrokesBgColor = 0x000000;
    public int keystrokesBgOpacity = 45;

    // HUD Wave 2: Bedrock Mini-Player (Paper Doll)
    public boolean paperDollEnabled = true;
    public boolean paperDollDynamic = true; // true = Bedrock Dynamic, false = Always Visible
    public int paperDollX = 28;
    public int paperDollY = 58;
    public int paperDollScale = 100;

    // HUD Wave 2: Bedwars Strategy Tips Box
    public boolean bedwarsTipsEnabled = true;
    public boolean bedwarsTipsDemo = false;
    public int bedwarsTipsX = 6;
    public int bedwarsTipsY = 258;
    public int bedwarsTipsScale = 90;
    public int bedwarsTipsColor = 0xFFFFFF;
    public boolean bedwarsTipsChroma = false;
    public int bedwarsTipsBgColor = 0x000000;
    public int bedwarsTipsBgOpacity = 45;

    // HUD Wave 2: Custom Scoreboard (Sidebar)
    public boolean scoreboardHudEnabled = false;
    public boolean scoreboardHideNumbers = true;
    public int scoreboardX = -1; // -1 = auto right edge
    public int scoreboardY = 120;
    public int scoreboardScale = 100;
    public int scoreboardColor = 0xFFFFFF;
    public boolean scoreboardChroma = false;
    public int scoreboardBgColor = 0x000000;
    public int scoreboardBgOpacity = 40;

    // HUD Wave 2: Custom Chat Box
    public boolean chatHudEnabled = false;
    public int chatHudX = 6;
    public int chatHudY = -1; // -1 = auto above hotbar
    public int chatHudScale = 100;
    public int chatHudColor = 0xFFFFFF;
    public boolean chatHudChroma = false;
    public int chatHudBgColor = 0x000000;
    public int chatHudBgOpacity = 25;
    public int chatHudMaxLines = 8;

    // HUD Wave 2: Custom Tab Overlay
    public boolean tabHudEnabled = false;
    public int tabHudX = -1; // -1 = auto center
    public int tabHudY = 14;
    public int tabHudScale = 100;
    public int tabHudColor = 0xFFFFFF;
    public boolean tabHudChroma = false;
    public int tabHudBgColor = 0x000000;
    public int tabHudBgOpacity = 50;

    // Audio: Sound Mixer
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
    public int menuTriggerMode = 0;
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
        this.keepMovingAfterGuiEnabled = true;
        this.noScrollEnabled = false;

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
        this.blockOutlineFullBlock = false;
        this.blockOutlineWidth = 20;
        this.blockOutlineColor = 0xFFFFFF;
        this.blockOutlineOpacity = 85;

        this.blockOverlayEnabled = false;
        this.blockOverlayFaceOnly = true;
        this.blockOverlayBlendMode = 0;
        this.blockOverlayColor = 0xFFFFFF;
        this.blockOverlayOpacity = 30;

        this.nightVisionEnabled = false;

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
        this.parodyHeaderText = "Skill Issue+";
        this.parodyShowBackground = true;
        this.parodyBgOpacity = 35;
        this.parodyChromaWave = false;
        this.parodyLines = createDefaultParodyLines();

        this.fpsHudEnabled = true;
        this.fpsX = 6; this.fpsY = 126; this.fpsScale = 100;
        this.fpsColor = 0xFFFFFF; this.fpsChroma = false;
        this.fpsBgColor = 0x000000; this.fpsBgOpacity = 40;

        this.cpsHudEnabled = true; this.cpsMode = 0;
        this.cpsX = 6; this.cpsY = 144; this.cpsScale = 100;
        this.cpsColor = 0xFFFFFF; this.cpsChroma = false;
        this.cpsBgColor = 0x000000; this.cpsBgOpacity = 40;

        this.keystrokesEnabled = true; this.keystrokesShowCps = true; this.keystrokesShowSpace = true;
        this.keystrokesX = 6; this.keystrokesY = 164; this.keystrokesScale = 100;
        this.keystrokesColor = 0xFFFFFF; this.keystrokesChroma = false;
        this.keystrokesBgColor = 0x000000; this.keystrokesBgOpacity = 45;

        this.paperDollEnabled = true; this.paperDollDynamic = true;
        this.paperDollX = 28; this.paperDollY = 58; this.paperDollScale = 100;

        this.bedwarsTipsEnabled = true; this.bedwarsTipsDemo = false;
        this.bedwarsTipsX = 6; this.bedwarsTipsY = 258; this.bedwarsTipsScale = 90;
        this.bedwarsTipsColor = 0xFFFFFF; this.bedwarsTipsChroma = false;
        this.bedwarsTipsBgColor = 0x000000; this.bedwarsTipsBgOpacity = 45;

        this.scoreboardHudEnabled = false; this.scoreboardHideNumbers = true;
        this.scoreboardX = -1; this.scoreboardY = 120; this.scoreboardScale = 100;
        this.scoreboardColor = 0xFFFFFF; this.scoreboardChroma = false;
        this.scoreboardBgColor = 0x000000; this.scoreboardBgOpacity = 40;

        this.chatHudEnabled = false; this.chatHudMaxLines = 8;
        this.chatHudX = 6; this.chatHudY = -1; this.chatHudScale = 100;
        this.chatHudColor = 0xFFFFFF; this.chatHudChroma = false;
        this.chatHudBgColor = 0x000000; this.chatHudBgOpacity = 25;

        this.tabHudEnabled = false;
        this.tabHudX = -1; this.tabHudY = 14; this.tabHudScale = 100;
        this.tabHudColor = 0xFFFFFF; this.tabHudChroma = false;
        this.tabHudBgColor = 0x000000; this.tabHudBgOpacity = 50;

        this.soundMixerEnabled = true;
        this.soundHitEnabled = true; this.soundHitMuted = false; this.soundHitVolume = 100; this.soundHitPitch = 100;
        this.soundExplosionEnabled = true; this.soundExplosionMuted = false; this.soundExplosionVolume = 100; this.soundExplosionPitch = 100;
        this.soundLightningEnabled = true; this.soundLightningMuted = false; this.soundLightningVolume = 100; this.soundLightningPitch = 100;
        this.soundRainEnabled = false; this.soundRainMuted = false; this.soundRainVolume = 100; this.soundRainPitch = 100;
        this.soundPortalEnabled = false; this.soundPortalMuted = false; this.soundPortalVolume = 100; this.soundPortalPitch = 100;
        this.soundGhastEnabled = false; this.soundGhastMuted = false; this.soundGhastVolume = 100; this.soundGhastPitch = 100;

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
        blockOverlayBlendMode = clamp(blockOverlayBlendMode, 0, 2);
        blockOverlayColor &= 0xFFFFFF;
        blockOverlayOpacity = clamp(blockOverlayOpacity, 0, 100);
        nametagBgColor &= 0xFFFFFF;
        nametagBgOpacity = clamp(nametagBgOpacity, 0, 100);
        customHitColor &= 0xFFFFFF;
        customHitOpacity = clamp(customHitOpacity, 0, 100);

        parodyCorner = clamp(parodyCorner, 0, 3);
        parodyOffsetX = clamp(parodyOffsetX, 0, 300);
        parodyOffsetY = clamp(parodyOffsetY, 0, 300);
        parodyScale = clamp(parodyScale, 50, 200);
        parodyTextColor &= 0xFFFFFF;
        parodyBgOpacity = clamp(parodyBgOpacity, 0, 100);
        if (parodyHeaderText == null) parodyHeaderText = "Skill Issue+";
        if (parodyLines == null) {
            parodyLines = createDefaultParodyLines();
        } else {
            Iterator<ParodyLine> it = parodyLines.iterator();
            while (it.hasNext()) {
                ParodyLine line = it.next();
                if (line == null || line.text == null) it.remove();
            }
        }

        fpsScale = clamp(fpsScale, 50, 200); fpsColor &= 0xFFFFFF; fpsBgColor &= 0xFFFFFF; fpsBgOpacity = clamp(fpsBgOpacity, 0, 100);
        cpsMode = clamp(cpsMode, 0, 2); cpsScale = clamp(cpsScale, 50, 200); cpsColor &= 0xFFFFFF; cpsBgColor &= 0xFFFFFF; cpsBgOpacity = clamp(cpsBgOpacity, 0, 100);
        keystrokesScale = clamp(keystrokesScale, 50, 200); keystrokesColor &= 0xFFFFFF; keystrokesBgColor &= 0xFFFFFF; keystrokesBgOpacity = clamp(keystrokesBgOpacity, 0, 100);
        paperDollScale = clamp(paperDollScale, 50, 200);
        bedwarsTipsScale = clamp(bedwarsTipsScale, 50, 200); bedwarsTipsColor &= 0xFFFFFF; bedwarsTipsBgColor &= 0xFFFFFF; bedwarsTipsBgOpacity = clamp(bedwarsTipsBgOpacity, 0, 100);
        scoreboardScale = clamp(scoreboardScale, 50, 200); scoreboardColor &= 0xFFFFFF; scoreboardBgColor &= 0xFFFFFF; scoreboardBgOpacity = clamp(scoreboardBgOpacity, 0, 100);
        chatHudScale = clamp(chatHudScale, 50, 200); chatHudColor &= 0xFFFFFF; chatHudBgColor &= 0xFFFFFF; chatHudBgOpacity = clamp(chatHudBgOpacity, 0, 100); chatHudMaxLines = clamp(chatHudMaxLines, 3, 16);
        tabHudScale = clamp(tabHudScale, 50, 200); tabHudColor &= 0xFFFFFF; tabHudBgColor &= 0xFFFFFF; tabHudBgOpacity = clamp(tabHudBgOpacity, 0, 100);

        soundHitVolume = clamp(soundHitVolume, 0, 200); soundHitPitch = clamp(soundHitPitch, 50, 200);
        soundExplosionVolume = clamp(soundExplosionVolume, 0, 200); soundExplosionPitch = clamp(soundExplosionPitch, 50, 200);
        soundLightningVolume = clamp(soundLightningVolume, 0, 200); soundLightningPitch = clamp(soundLightningPitch, 50, 200);
        soundRainVolume = clamp(soundRainVolume, 0, 200); soundRainPitch = clamp(soundRainPitch, 50, 200);
        soundPortalVolume = clamp(soundPortalVolume, 0, 200); soundPortalPitch = clamp(soundPortalPitch, 50, 200);
        soundGhastVolume = clamp(soundGhastVolume, 0, 200); soundGhastPitch = clamp(soundGhastPitch, 50, 200);

        if (menuKeyCode <= 0 || menuKeyCode >= 256) menuKeyCode = Keyboard.KEY_H;
        menuTriggerMode = clamp(menuTriggerMode, 0, 2);
        if (menuModifierKey <= 0 || menuModifierKey >= 256) menuModifierKey = Keyboard.KEY_LCONTROL;
    }

    private static boolean isValidBind(int code) {
        return (code >= -100 && code <= -85) || (code > 0 && code < 256);
    }

    private static int clamp(int val, int min, int max) {
        return val < min ? min : (val > max ? max : val);
    }
}
