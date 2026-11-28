package com.example.hitjump;

import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class BedwarsAdvisor {
    private static final String[] TEAM_NAMES = {
            "Red", "Blue", "Green", "Yellow", "Aqua", "White", "Pink", "Gray"
    };
    private static final String[] TEAM_LOWER = {
            "red", "blue", "green", "yellow", "aqua", "white", "pink", "gray"
    };

    private static final boolean[] bedAlive = new boolean[8];
    private static final boolean[] teamEliminated = new boolean[8];
    private static final int[] bedsBrokenBy = new int[8];
    private static final int[] finalsBy = new int[8];

    private static boolean matchActive = false;
    private static long lastEventMs = 0L;
    private static String lastHeadline = "";
    private static String lastActionTip = "";
    private static int hottestThreatTeam = -1;

    // Recent chat line reflection cache (shared with custom Chat HUD)
    private static boolean chatListSearched = false;
    private static Object chatHudObj = null;
    private static Field chatLinesField = null;
    private static Field chatLineTextField = null;
    private static Method textUnformattedMethod = null;
    private static Method textFormattedMethod = null;
    private static boolean textMethodsSearched = false;

    private static final Set<Object> seenChatLines = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
    private static final List<String> recentFormattedChat = new ArrayList<String>();
    private static Object lastWorldRef = null;

    static {
        resetMatch(false);
    }

    public static void resetMatch(boolean active) {
        matchActive = active;
        lastEventMs = active ? System.currentTimeMillis() : 0L;
        lastHeadline = active ? "Match live - watch your first rush" : "";
        lastActionTip = active ? "Cover bed and contest first bridge" : "";
        hottestThreatTeam = -1;
        for (int i = 0; i < 8; i++) {
            bedAlive[i] = true;
            teamEliminated[i] = false;
            bedsBrokenBy[i] = 0;
            finalsBy[i] = 0;
        }
    }

    public static void tick(Minecraft client) {
        if (client == null || client.world == null || client.player == null) {
            lastWorldRef = null;
            return;
        }
        if (client.world != lastWorldRef) {
            lastWorldRef = client.world;
            resetMatch(false);
        }

        HitJumpConfig config = HitJumpConfig.getInstance();
        if (!config.bedwarsTipsEnabled && !config.chatHudEnabled) {
            return;
        }

        List<?> lines = getChatLines(client);
        if (lines == null || lines.isEmpty()) {
            return;
        }

        // Rebuild recent formatted snapshot for Custom Chat HUD
        if (config.chatHudEnabled) {
            recentFormattedChat.clear();
            int limit = Math.min(lines.size(), Math.max(16, config.chatHudMaxLines + 4));
            for (int i = 0; i < limit; i++) {
                try {
                    Object entry = lines.get(i);
                    String fmt = extractChatText(entry, true);
                    if (fmt != null && !fmt.isEmpty()) {
                        recentFormattedChat.add(fmt);
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        // Process only newly arrived messages (oldest-to-newest among unseen lines)
        List<String> freshPlain = new ArrayList<String>();
        int scanLimit = Math.min(lines.size(), 12);
        for (int i = 0; i < scanLimit; i++) {
            Object entry;
            try {
                entry = lines.get(i);
            } catch (Throwable ignored) {
                break;
            }
            if (entry == null || seenChatLines.contains(entry)) {
                break;
            }
            seenChatLines.add(entry);
            String plain = extractChatText(entry, false);
            if (plain != null && !plain.trim().isEmpty()) {
                freshPlain.add(plain.trim());
            }
        }

        if (seenChatLines.size() > 600) {
            seenChatLines.clear();
            for (int i = 0; i < Math.min(lines.size(), 100); i++) {
                try {
                    Object e = lines.get(i);
                    if (e != null) seenChatLines.add(e);
                } catch (Throwable ignored) {
                }
            }
        }

        for (int i = freshPlain.size() - 1; i >= 0; i--) {
            processChatMessage(freshPlain.get(i));
        }
    }

    public static List<String> getRecentFormattedChat() {
        return recentFormattedChat;
    }

    public static List<String> getDisplayLines(HitJumpConfig config) {
        List<String> out = new ArrayList<String>();
        if (config.bedwarsTipsDemo) {
            out.add("\u00A7lBedwars Advisor");
            out.add("\u00A7bBlue \u00A7fbroke \u00A7cRed \u00A77(12s) -> rotating");
            out.add("\u00A7eThreat: Blue (2 beds) - guard bed!");
            out.add("\u00A7aBeds left: Blue, Yellow, Green");
            return out;
        }

        if (!matchActive && lastHeadline.isEmpty()) {
            return out;
        }

        out.add("\u00A7lBedwars Advisor");
        if (!lastHeadline.isEmpty()) {
            String ago = "";
            if (lastEventMs > 0L) {
                long secs = Math.max(0L, (System.currentTimeMillis() - lastEventMs) / 1000L);
                if (secs < 180L) {
                    ago = " \u00A77(" + secs + "s)";
                }
            }
            out.add(lastHeadline + ago);
        }
        if (!lastActionTip.isEmpty()) {
            out.add(lastActionTip);
        }

        String aliveSummary = buildBedsAliveSummary();
        if (!aliveSummary.isEmpty()) {
            out.add(aliveSummary);
        }
        return out;
    }

    private static void processChatMessage(String raw) {
        String clean = stripFormatting(raw).trim();
        if (clean.isEmpty()) {
            return;
        }
        String lower = clean.toLowerCase(Locale.ROOT);

        // 1. Detect Match Start
        if (lower.contains("protect your bed")
                || lower.contains("destroy the enemy beds")
                || lower.contains("the game has started")
                || lower.contains("all generators have been")) {
            resetMatch(true);
            return;
        }

        // 2. Detect Bed Destruction
        if (lower.contains("bed destruction")
                || lower.contains("bed was destroyed")
                || lower.contains("bed was broken")
                || lower.contains("destroyed your bed")
                || lower.contains("broken by")) {
            matchActive = true;
            lastEventMs = System.currentTimeMillis();

            int[] teams = findMentionedTeamsInOrder(lower);
            int victim = teams[0];
            int attacker = teams[1];

            // Handle "Your Bed was destroyed by Blue!"
            if (lower.contains("your bed") && victim >= 0 && attacker < 0) {
                attacker = victim;
                victim = -1;
            }

            if (victim >= 0) {
                bedAlive[victim] = false;
            }
            if (attacker >= 0) {
                bedsBrokenBy[attacker]++;
                updateThreatTeam();
            }

            if (victim >= 0 && attacker >= 0) {
                lastHeadline = TEAM_NAMES[attacker] + " broke " + TEAM_NAMES[victim];
                if (bedsBrokenBy[attacker] >= 2) {
                    lastActionTip = "Threat: " + TEAM_NAMES[attacker] + " sweeping (" + bedsBrokenBy[attacker] + " beds) - guard!";
                } else {
                    lastActionTip = TEAM_NAMES[attacker] + " at " + TEAM_NAMES[victim] + " -> rotating next";
                }
            } else if (victim >= 0) {
                lastHeadline = TEAM_NAMES[victim] + " bed is gone";
                lastActionTip = TEAM_NAMES[victim] + " has no bed -> push for finals";
            } else if (attacker >= 0) {
                lastHeadline = "Bed broken by " + TEAM_NAMES[attacker];
                lastActionTip = TEAM_NAMES[attacker] + " is pushing hard - stay alert";
            } else {
                lastHeadline = "A bed was destroyed";
                lastActionTip = "Check tab/sidebar for open rush";
            }
            return;
        }

        // 3. Detect Team Elimination
        if (lower.contains("team eliminated") || lower.contains("has been eliminated")) {
            matchActive = true;
            lastEventMs = System.currentTimeMillis();
            int team = findMentionedTeamsInOrder(lower)[0];
            if (team >= 0) {
                bedAlive[team] = false;
                teamEliminated[team] = true;
                lastHeadline = TEAM_NAMES[team] + " eliminated";
                if (hottestThreatTeam >= 0 && !teamEliminated[hottestThreatTeam]) {
                    lastActionTip = "Watch " + TEAM_NAMES[hottestThreatTeam] + " -> likely rotating mid/bases";
                } else {
                    lastActionTip = "1 fewer team -> control mid/diamonds";
                }
            }
            return;
        }

        // 4. Detect Final Kills
        if (lower.contains("final kill")) {
            matchActive = true;
            lastEventMs = System.currentTimeMillis();
            int[] teams = findMentionedTeamsInOrder(lower);
            if (teams[1] >= 0) {
                finalsBy[teams[1]]++;
                updateThreatTeam();
            }
            if (teams[0] >= 0 && teams[1] >= 0) {
                lastHeadline = TEAM_NAMES[teams[1]] + " finaled " + TEAM_NAMES[teams[0]];
                lastActionTip = TEAM_NAMES[teams[1]] + " winning fights -> hold high ground";
            } else if (hottestThreatTeam >= 0 && !teamEliminated[hottestThreatTeam]) {
                lastActionTip = "Active threat: " + TEAM_NAMES[hottestThreatTeam];
            }
        }
    }

    private static void updateThreatTeam() {
        int best = -1;
        int bestScore = 0;
        for (int i = 0; i < 8; i++) {
            if (teamEliminated[i]) continue;
            int score = bedsBrokenBy[i] * 3 + finalsBy[i];
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        hottestThreatTeam = best;
    }

    private static String buildBedsAliveSummary() {
        int count = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (bedAlive[i] && !teamEliminated[i]) {
                if (count > 0 && count < 4) sb.append(", ");
                if (count < 4) sb.append(TEAM_NAMES[i]);
                count++;
            }
        }
        if (count == 0) return "All beds gone - final fight!";
        if (count >= 7) return "Beds alive: " + count + "/8";
        if (count <= 4) return "Beds left (" + count + "): " + sb.toString();
        return "Beds left: " + count + " (" + sb.toString() + "...)";
    }

    private static int[] findMentionedTeamsInOrder(String lower) {
        int firstTeam = -1, firstPos = Integer.MAX_VALUE;
        int secondTeam = -1, secondPos = Integer.MAX_VALUE;

        for (int i = 0; i < TEAM_LOWER.length; i++) {
            int pos = findWordPos(lower, TEAM_LOWER[i]);
            if (pos < 0 && i == 7) {
                pos = findWordPos(lower, "grey");
            }
            if (pos >= 0) {
                if (pos < firstPos) {
                    secondPos = firstPos;
                    secondTeam = firstTeam;
                    firstPos = pos;
                    firstTeam = i;
                } else if (pos < secondPos && i != firstTeam) {
                    secondPos = pos;
                    secondTeam = i;
                }
            }
        }
        return new int[]{firstTeam, secondTeam};
    }

    private static int findWordPos(String text, String word) {
        int idx = 0;
        while ((idx = text.indexOf(word, idx)) >= 0) {
            boolean leftOk = (idx == 0) || !Character.isLetter(text.charAt(idx - 1));
            int end = idx + word.length();
            boolean rightOk = (end >= text.length()) || !Character.isLetter(text.charAt(end));
            if (leftOk && rightOk) {
                return idx;
            }
            idx = end;
        }
        return -1;
    }

    private static String stripFormatting(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00A7' && i + 1 < s.length()) {
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static List<?> getChatLines(Minecraft client) {
        if (!chatListSearched) {
            chatListSearched = true;
            locateChatLinesField(client);
        }
        if (chatHudObj != null && chatLinesField != null) {
            try {
                Object val = chatLinesField.get(chatHudObj);
                return (val instanceof List) ? (List<?>) val : null;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static void locateChatLinesField(Minecraft client) {
        for (Field mcField : client.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(mcField.getModifiers()) || mcField.getType().isPrimitive()) continue;
            Object guiObj;
            try {
                mcField.setAccessible(true);
                guiObj = mcField.get(client);
            } catch (Throwable ignored) {
                continue;
            }
            if (guiObj == null || guiObj.getClass().isArray() || isSkippableClass(guiObj.getClass())) continue;

            // Inspect fields of GameGui to find ChatHud (contains 3 List fields: sentMessages, chatLines, drawnChatLines)
            Class<?> cls = guiObj.getClass();
            while (cls != null && cls != Object.class && !isSkippableClass(cls)) {
                Field[] fields;
                try {
                    fields = cls.getDeclaredFields();
                } catch (Throwable ignored) {
                    break;
                }
                for (Field f : fields) {
                    if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                    Object sub;
                    try {
                        f.setAccessible(true);
                        sub = f.get(guiObj);
                    } catch (Throwable ignored) {
                        continue;
                    }
                    if (sub == null || sub.getClass().isArray() || isSkippableClass(sub.getClass())) continue;
                    if (tryBindChatHud(sub)) {
                        return;
                    }
                }
                cls = cls.getSuperclass();
            }
        }
    }

    private static boolean tryBindChatHud(Object candidate) {
        List<Field> lists = new ArrayList<Field>();
        boolean hasMinecraftRef = false;
        Field[] fields;
        try {
            fields = candidate.getClass().getDeclaredFields();
        } catch (Throwable ignored) {
            return false;
        }
        if (fields.length < 4 || fields.length > 14) {
            return false;
        }
        for (Field f : fields) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            if (f.getType() == Minecraft.class) {
                hasMinecraftRef = true;
            } else if (List.class.isAssignableFrom(f.getType())) {
                try {
                    f.setAccessible(true);
                    lists.add(f);
                } catch (Throwable ignored) {
                }
            }
        }
        // Vanilla GuiNewChat / ChatHud has Minecraft + 3 List fields: [0]=sentMessages, [1]=chatLines, [2]=drawnChatLines
        if (hasMinecraftRef && lists.size() >= 3) {
            chatHudObj = candidate;
            chatLinesField = lists.get(1);
            return true;
        }
        return false;
    }

    private static String extractChatText(Object chatLineObj, boolean formatted) {
        if (chatLineObj == null) return null;
        if (chatLineTextField == null) {
            try {
                for (Field f : chatLineObj.getClass().getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                    if (!isSkippableClass(f.getType())) {
                        f.setAccessible(true);
                        chatLineTextField = f;
                        break;
                    }
                }
            } catch (Throwable ignored) {
                return null;
            }
        }
        if (chatLineTextField == null) return null;
        try {
            Object textObj = chatLineTextField.get(chatLineObj);
            if (textObj == null) return null;
            if (!textMethodsSearched) {
                textMethodsSearched = true;
                Class<?> iface = chatLineTextField.getType();
                Method bestFmt = null;
                Method bestPlain = null;
                for (Method m : iface.getMethods()) {
                    if (m.getParameterTypes().length != 0 || m.getReturnType() != String.class || "toString".equals(m.getName())) {
                        continue;
                    }
                    try {
                        Object res = m.invoke(textObj);
                        if (res instanceof String) {
                            String str = (String) res;
                            if (str.indexOf('\u00A7') >= 0) {
                                bestFmt = m;
                            } else if (bestPlain == null || str.length() > 0) {
                                bestPlain = m;
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
                textFormattedMethod = (bestFmt != null) ? bestFmt : bestPlain;
                textUnformattedMethod = (bestPlain != null) ? bestPlain : bestFmt;
            }

            Method target = formatted ? textFormattedMethod : textUnformattedMethod;
            if (target != null) {
                Object res = target.invoke(textObj);
                if (res instanceof String) {
                    return formatted ? (String) res : stripFormatting((String) res);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean isSkippableClass(Class<?> cls) {
        if (cls == null) return true;
        String name = cls.getName();
        return name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jdk.")
                || name.startsWith("sun.") || name.startsWith("com.google.") || name.startsWith("org.apache.")
                || name.startsWith("io.netty.") || name.startsWith("it.unimi.") || name.startsWith("org.lwjgl.")
                || name.startsWith("org.slf4j.");
    }
}
