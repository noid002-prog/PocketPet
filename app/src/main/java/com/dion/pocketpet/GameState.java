package com.dion.pocketpet;

import android.content.Context;
import android.content.SharedPreferences;

public class GameState {
    private static final String PREF = "pocket_pet_save_v04";

    public static final int STAGE_EGG = 0;
    public static final int STAGE_PICHU = 1;
    public static final int STAGE_PIKACHU = 2;
    public static final int STAGE_RAICHU = 3;

    public String name = "Buddy";
    public int happy = 82;
    public int hunger = 18; // 0 = full, 100 = hungry
    public int energy = 85;
    public int clean = 88;
    public int level = 1;
    public int exp = 0;
    public int bond = 5;

    public int stage = STAGE_EGG;
    public int hatchCare = 0;
    public int megaChoice = 0; // 0 none, 1 X, 2 Y
    public boolean megaActive = false;

    public int feedCount = 0;
    public int playCount = 0;
    public int talkCount = 0;
    public int petCount = 0;
    public int hugCount = 0;

    public long lastUpdated = System.currentTimeMillis();

    public static GameState load(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        GameState s = new GameState();
        s.name = p.getString("name", "Buddy");
        s.happy = p.getInt("happy", 82);
        s.hunger = p.getInt("hunger", 18);
        s.energy = p.getInt("energy", 85);
        s.clean = p.getInt("clean", 88);
        s.level = p.getInt("level", 1);
        s.exp = p.getInt("exp", 0);
        s.bond = p.getInt("bond", 5);
        s.stage = p.getInt("stage", STAGE_EGG);
        s.hatchCare = p.getInt("hatchCare", 0);
        s.megaChoice = p.getInt("megaChoice", 0);
        s.megaActive = p.getBoolean("megaActive", false);
        s.feedCount = p.getInt("feedCount", 0);
        s.playCount = p.getInt("playCount", 0);
        s.talkCount = p.getInt("talkCount", 0);
        s.petCount = p.getInt("petCount", 0);
        s.hugCount = p.getInt("hugCount", 0);
        s.lastUpdated = p.getLong("lastUpdated", System.currentTimeMillis());
        s.applyGentleOfflineChange();
        return s;
    }

    public void save(Context c) {
        lastUpdated = System.currentTimeMillis();
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString("name", name)
                .putInt("happy", happy)
                .putInt("hunger", hunger)
                .putInt("energy", energy)
                .putInt("clean", clean)
                .putInt("level", level)
                .putInt("exp", exp)
                .putInt("bond", bond)
                .putInt("stage", stage)
                .putInt("hatchCare", hatchCare)
                .putInt("megaChoice", megaChoice)
                .putBoolean("megaActive", megaActive)
                .putInt("feedCount", feedCount)
                .putInt("playCount", playCount)
                .putInt("talkCount", talkCount)
                .putInt("petCount", petCount)
                .putInt("hugCount", hugCount)
                .putLong("lastUpdated", lastUpdated)
                .apply();
    }

    private void applyGentleOfflineChange() {
        long now = System.currentTimeMillis();
        long mins = Math.max(0, (now - lastUpdated) / 60000L);
        if (stage != STAGE_EGG) {
            // Child-friendly: only the first 8 hours can reduce needs. After that the pet
            // is considered safely cared for at Professor's Ranch.
            long activeMins = Math.min(mins, 480);
            int steps = (int) (activeMins / 30L);
            if (steps > 0) {
                hunger = clamp(hunger + steps * 2);
                happy = clamp(happy - steps);
                clean = clamp(clean - steps);
                energy = clamp(energy + steps * 2);
            }
        }
        lastUpdated = now;
    }

    public String addExp(int amount) {
        if (stage == STAGE_EGG) return "";
        exp += amount;
        StringBuilder result = new StringBuilder("+").append(amount).append(" EXP");
        while (exp >= expToNext()) {
            int needed = expToNext();
            exp -= needed;
            level++;
            result.append(" • Level Up! Lv.").append(level);
        }
        return result.toString();
    }

    // Accelerated for prototype playtesting. Can be slowed for the final release.
    public int expToNext() {
        return 34 + (level - 1) * 14;
    }

    public int fullPercent() { return 100 - hunger; }

    public String speciesName() {
        if (stage == STAGE_EGG) return "Pokémon Egg";
        if (megaActive && megaChoice == 1) return "Mega Raichu X";
        if (megaActive && megaChoice == 2) return "Mega Raichu Y";
        if (stage == STAGE_PICHU) return "Pichu";
        if (stage == STAGE_PIKACHU) return "Pikachu";
        return "Raichu";
    }

    public String growthRequirement() {
        if (stage == STAGE_EGG) return "Hatch care " + hatchCare + "/4";
        if (stage == STAGE_PICHU) return "Next: Pikachu at Lv.3 + Bond 15";
        if (stage == STAGE_PIKACHU) return "Next: Raichu at Lv.6 + Bond 30";
        if (megaChoice == 0) return "Next: Mega choice at Lv.10 + Bond 50";
        return megaActive ? "Mega form active • sleep returns to Raichu" : "Mega form unlocked";
    }

    public boolean canGrow() {
        if (stage == STAGE_PICHU) return level >= 3 && bond >= 15;
        if (stage == STAGE_PIKACHU) return level >= 6 && bond >= 30;
        return stage == STAGE_RAICHU && megaChoice == 0 && level >= 10 && bond >= 50;
    }

    public String favoriteHabit() {
        if (stage == STAGE_EGG) return "Warm it gently and say hello";
        int max = Math.max(Math.max(feedCount, playCount), Math.max(talkCount, Math.max(petCount, hugCount)));
        if (max < 4) return "Still learning what it loves";
        if (max == hugCount) return "Loves cuddles";
        if (max == playCount) return "Loves play time";
        if (max == feedCount) return "Berry fan";
        if (max == talkCount) return "Little chatterbox";
        return "Loves head pats";
    }

    public String moodText() {
        if (stage == STAGE_EGG) return hatchCare >= 3 ? "the egg is wiggling!" : "a tiny sound comes from inside";
        if (energy < 20) return "sleepy";
        if (hunger > 70) return "hungry";
        if (clean < 30) return "a little messy";
        if (happy >= 80) return "super happy";
        if (happy >= 55) return "happy";
        return "wants some love";
    }

    public static int clamp(int v) { return Math.max(0, Math.min(100, v)); }
}
