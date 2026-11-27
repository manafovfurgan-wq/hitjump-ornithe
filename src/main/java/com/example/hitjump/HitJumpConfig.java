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

public class HitJumpConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("hitjump.json")
            .toFile();

    private static HitJumpConfig instance = new HitJumpConfig();

    // HitJump settings
    public boolean enabled = true;
    public int jumpFrequency = 100;
    public int latencyTicks = 0;

    // NoJumpDelay settings
    public boolean noJumpDelayEnabled = true;
    public int jumpDelayTicks = 0;

    // NoHitDelay settings
    public boolean noHitDelayEnabled = true;
    public int hitDelayTicks = 0;

    // Menu hotkey
    public int menuKeyCode = Keyboard.KEY_H;

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
        this.jumpFrequency = 100;
        this.latencyTicks = 0;
        this.noJumpDelayEnabled = true;
        this.jumpDelayTicks = 0;
        this.noHitDelayEnabled = true;
        this.hitDelayTicks = 0;
        this.menuKeyCode = Keyboard.KEY_H;
    }

    public void clampValues() {
        if (jumpFrequency < 0) jumpFrequency = 0;
        if (jumpFrequency > 100) jumpFrequency = 100;
        if (latencyTicks < 0) latencyTicks = 0;
        if (latencyTicks > 10) latencyTicks = 10;
        if (jumpDelayTicks < 0) jumpDelayTicks = 0;
        if (jumpDelayTicks > 10) jumpDelayTicks = 10;
        if (hitDelayTicks < 0) hitDelayTicks = 0;
        if (hitDelayTicks > 10) hitDelayTicks = 10;
        if (menuKeyCode <= 0 || menuKeyCode >= 256) menuKeyCode = Keyboard.KEY_H;
    }
}
