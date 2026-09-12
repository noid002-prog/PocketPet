package com.dion.pocketpet;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private GameState state;
    private PetView petView;
    private TextView levelLine, stageLine, speech;
    private TextView happyChip, foodChip, energyChip, cleanChip;
    private ProgressBar expBar;
    private boolean growthDialogOpen = false;
    private boolean hatchDialogOpen = false;
    private long messageUntil = 0L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable restoreSpeech = () -> {
        if (System.currentTimeMillis() >= messageUntil && speech != null) {
            speech.setText(contextStatus());
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        state = GameState.load(this);
        buildUi();
        if (state.stage == GameState.STAGE_EGG && state.hatchCare == 0) {
            say("A mystery egg is waiting for you. Tap it gently! 🥚", 4200);
        }
    }

    @Override protected void onPause() {
        state.save(this);
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (petView != null) {
            state = GameState.load(this);
            refresh();
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.setBackground(gradient(Color.rgb(255,248,226), Color.rgb(231,247,244), 0));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(14), dp(10), dp(14), dp(10));
        header.setElevation(dp(3));
        header.setBackground(round(Color.argb(244,255,255,255), 22));

        TextView title = label("⚡ Pocket Pet", 23, true);
        title.setGravity(Gravity.CENTER);
        header.addView(title);

        levelLine = label("", 14, true);
        levelLine.setGravity(Gravity.CENTER);
        levelLine.setTextColor(Color.rgb(83,89,84));
        header.addView(levelLine);

        stageLine = label("", 11, false);
        stageLine.setGravity(Gravity.CENTER);
        stageLine.setTextColor(Color.rgb(109,114,109));
        stageLine.setPadding(0,dp(2),0,dp(5));
        header.addView(stageLine);

        expBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        expBar.setProgressTintList(ColorStateList.valueOf(Color.rgb(255,190,66)));
        expBar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(235,232,220)));
        header.addView(expBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)));
        root.addView(header);

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setPadding(0, dp(8), 0, dp(6));
        happyChip = statChip(stats, "❤️", Color.rgb(255,226,231));
        foodChip = statChip(stats, "🍎", Color.rgb(255,236,197));
        energyChip = statChip(stats, "⚡", Color.rgb(255,242,174));
        cleanChip = statChip(stats, "🫧", Color.rgb(211,241,248));
        root.addView(stats);

        petView = new PetView(this);
        petView.bind(state);
        petView.setListener(new PetView.Listener() {
            @Override public void onHeadPat() {
                if (state.stage == GameState.STAGE_EGG) {
                    eggCare("The egg wiggles happily.");
                    return;
                }
                state.happy = GameState.clamp(state.happy + 2);
                state.bond = GameState.clamp(state.bond + 1);
                state.petCount++;
                say(state.name + " loves that! ❤️", 1900);
                refreshAndSave();
                checkGrowth();
            }

            @Override public void onPetSwipe() {
                if (state.stage == GameState.STAGE_EGG) {
                    eggCare("You gently rub the warm shell.");
                    return;
                }
                state.happy = GameState.clamp(state.happy + 3);
                state.bond = GameState.clamp(state.bond + 1);
                state.petCount++;
                say("Soft petting makes " + state.name + " smile ✨", 1900);
                refreshAndSave();
                checkGrowth();
            }

            @Override public void onHug() {
                if (state.stage == GameState.STAGE_EGG) {
                    eggCare("You cuddle the little egg safely.");
                    return;
                }
                state.happy = GameState.clamp(state.happy + 5);
                state.bond = GameState.clamp(state.bond + 2);
                state.hugCount++;
                say(state.name + " cuddles back 🤗", 2100);
                refreshAndSave();
                checkGrowth();
            }

            @Override public void onFoodFed(int foodIndex) { rewardFood(foodIndex); }
            @Override public void onBallPlayed() { rewardPlay(); }

            @Override public void onBathComplete() {
                state.clean = 100;
                state.happy = GameState.clamp(state.happy + 5);
                state.bond = GameState.clamp(state.bond + 1);
                say("Sparkling clean! 🫧  " + state.addExp(4), 2400);
                refreshAndSave();
                checkGrowth();
            }
        });
        root.addView(petView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        speech = label("", 14, true);
        speech.setGravity(Gravity.CENTER);
        speech.setTextColor(Color.rgb(69,78,73));
        speech.setPadding(dp(13),dp(9),dp(13),dp(9));
        speech.setBackground(round(Color.argb(246,255,255,255), 20));
        speech.setElevation(dp(2));
        LinearLayout.LayoutParams speechLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        speechLp.setMargins(dp(8),dp(5),dp(8),dp(7));
        root.addView(speech, speechLp);

        GridLayout dock = new GridLayout(this);
        dock.setColumnCount(3);
        dock.setRowCount(2);
        dock.setPadding(0,dp(2),0,0);
        addAction(dock, "🍓", "Feed", Color.rgb(255,210,221), this::feed);
        addAction(dock, "🎾", "Play", Color.rgb(210,239,207), this::play);
        addAction(dock, "🫧", "Bath", Color.rgb(203,237,249), this::clean);
        addAction(dock, "💬", "Talk", Color.rgb(224,218,249), this::talk);
        addAction(dock, "🌙", "Sleep", Color.rgb(218,228,251), this::sleep);
        addAction(dock, "🌳", "Explore", Color.rgb(222,242,205), this::adventure);
        root.addView(dock);

        setContentView(root);
        refresh();
    }

    private TextView statChip(LinearLayout row, String emoji, int color) {
        TextView t = label(emoji + " --", 12, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(4),dp(7),dp(4),dp(7));
        t.setBackground(round(color, 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(2),0,dp(2),0);
        row.addView(t,lp);
        return t;
    }

    private void addAction(GridLayout grid, String emoji, String title, int color, Runnable action) {
        TextView b = label(emoji + "\n" + title, 15, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(4),dp(7),dp(4),dp(7));
        b.setClickable(true);
        b.setFocusable(true);
        b.setElevation(dp(3));
        GradientDrawable shape = round(color, 23);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(55,255,255,255)), shape, null));
        b.setOnClickListener(v -> {
            v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(70).withEndAction(() ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()).start();
            action.run();
        });
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = dp(67);
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(dp(4),dp(4),dp(4),dp(4));
        grid.addView(b,lp);
    }

    private void feed() {
        if (state.stage == GameState.STAGE_EGG) {
            eggCare("You leave a tiny berry beside the egg.");
            return;
        }
        petView.startFoodTray();
        say("Drag a snack from the tray to " + state.name + " 🍓", 3200);
    }

    private void rewardFood(int which) {
        state.feedCount++;
        String food;
        if (which == 0) {
            state.hunger = GameState.clamp(state.hunger - 24);
            state.happy = GameState.clamp(state.happy + 3);
            food = "Berry yum!";
        } else if (which == 1) {
            state.hunger = GameState.clamp(state.hunger - 20);
            state.happy = GameState.clamp(state.happy + 4);
            food = "Crunchy apple!";
        } else {
            state.hunger = GameState.clamp(state.hunger - 10);
            state.happy = GameState.clamp(state.happy + 8);
            food = "Tiny treat!";
        }
        state.bond = GameState.clamp(state.bond + 1);
        say(food + " 😋  " + state.addExp(which == 2 ? 3 : 5), 2400);
        refreshAndSave();
        checkGrowth();
    }

    private void play() {
        if (state.stage == GameState.STAGE_EGG) {
            eggCare("You gently rock the egg like a tiny game.");
            return;
        }
        if (state.energy < 12) {
            say(state.name + " is sleepy. Try a nap first 🌙", 2500);
            return;
        }
        petView.startBall();
        say("Drag the ball and throw it! 🎾", 3000);
    }

    private void rewardPlay() {
        state.playCount++;
        state.energy = GameState.clamp(state.energy - 9);
        state.happy = GameState.clamp(state.happy + 13);
        state.hunger = GameState.clamp(state.hunger + 4);
        state.clean = GameState.clamp(state.clean - 2);
        state.bond = GameState.clamp(state.bond + 2);
        say("Great catch! 🎾  " + state.addExp(7), 2300);
        refreshAndSave();
        checkGrowth();
    }

    private void clean() {
        if (state.stage == GameState.STAGE_EGG) {
            eggCare("You polish the shell until it shines.");
            return;
        }
        petView.startBath();
        say("Swipe gently over " + state.name + " three times 🫧", 3200);
    }

    private void sleep() {
        if (state.stage == GameState.STAGE_EGG) {
            eggCare("You tuck the egg into a warm nest.");
            return;
        }
        petView.cancelMode();
        petView.startSleepAnimation();
        say("Sweet dreams, " + state.name + " 🌙", 1800);
        petView.postDelayed(() -> {
            state.energy = 100;
            state.happy = GameState.clamp(state.happy + 3);
            if (state.megaActive) state.megaActive = false;
            say("Good morning! Ready to play ☀️", 2400);
            refreshAndSave();
        }, 1700);
    }

    private void talk() {
        if (state.stage == GameState.STAGE_EGG) {
            eggCare("Tap tap... something answers from inside!");
            return;
        }
        petView.cancelMode();
        String[] replies = {
                "I'm happy you're here! ❤️",
                state.hunger > 55 ? "A berry sounds yummy! 🍓" : "My tummy feels good 😋",
                state.energy < 30 ? "I'm a little sleepy 🌙" : "Can we play? 🎾",
                "You're my favorite buddy! ✨",
                state.growthRequirement()
        };
        int which = state.talkCount % replies.length;
        state.talkCount++;
        state.happy = GameState.clamp(state.happy + 3);
        state.bond = GameState.clamp(state.bond + 1);
        petView.happySpark();
        say(replies[which], 3000);
        refreshAndSave();
        checkGrowth();
    }

    private void adventure() {
        if (state.stage == GameState.STAGE_EGG) {
            say("The egg needs to hatch before exploring 🥚", 2500);
            return;
        }
        if (state.energy < 18) {
            say("A little nap first, then adventure 🌙", 2500);
            return;
        }
        petView.cancelMode();
        List<String> options = new ArrayList<>();
        options.add("🌿 Forest Walk");
        options.add("⚡ Friendly Battle");
        boolean hasMegaToggle = state.stage == GameState.STAGE_RAICHU && state.megaChoice != 0;
        if (hasMegaToggle) options.add(state.megaActive ? "✨ Rest Mega Form" : "✨ Activate Mega Form");
        options.add("🏠 Stay Home");
        String[] items = options.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("Tiny Adventure")
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        startActivity(new Intent(this, ForestActivity.class));
                    } else if (which == 1) {
                        state.energy = GameState.clamp(state.energy - 10);
                        new BattleDialog(this, state, (won, reward) -> {
                            say(won ? "Great teamwork! " + reward : "Good try! You still learned something ❤️", 3200);
                            refreshAndSave();
                            checkGrowth();
                        }).show();
                    } else if (hasMegaToggle && which == 2) {
                        state.megaActive = !state.megaActive;
                        say(state.megaActive ? "Mega Raichu " + (state.megaChoice == 1 ? "X" : "Y") + " activated! ⚡" : "Back to cozy Raichu form", 2600);
                        refreshAndSave();
                    }
                })
                .show();
    }

    private void eggCare(String action) {
        state.hatchCare = Math.min(4, state.hatchCare + 1);
        petView.wiggleEgg();
        if (state.hatchCare < 4) {
            say(action + "  " + state.hatchCare + "/4 ❤️", 2100);
            refreshAndSave();
        } else {
            refreshAndSave();
            showHatchDialog();
        }
    }

    private void showHatchDialog() {
        if (hatchDialogOpen || state.stage != GameState.STAGE_EGG) return;
        hatchDialogOpen = true;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("✨ The egg is hatching!")
                .setMessage("Your gentle care helped the little buddy hatch.")
                .setPositiveButton("🥚 Hatch!", (d,w) -> finishHatch())
                .setCancelable(false)
                .create();
        dialog.setOnDismissListener(d -> hatchDialogOpen = false);
        dialog.show();
    }

    private void finishHatch() {
        petView.startEvolutionAnimation("Egg → Pichu");
        petView.postDelayed(() -> {
            state.stage = GameState.STAGE_PICHU;
            state.level = 1;
            state.exp = 0;
            state.bond = Math.max(8,state.bond);
            state.name = "Sparky";
            refreshAndSave();
            askNameAfterHatch();
        }, 1000);
    }

    private void askNameAfterHatch() {
        EditText input = new EditText(this);
        input.setHint("Nickname");
        input.setText("Sparky");
        new AlertDialog.Builder(this)
                .setTitle("A Pichu hatched! 🎉")
                .setMessage("Give your new buddy a nickname.")
                .setView(input)
                .setPositiveButton("Let's play", (d,w) -> {
                    String n = input.getText().toString().trim();
                    if (!n.isEmpty()) state.name = n.substring(0,Math.min(n.length(),16));
                    say("Welcome, " + state.name + "! ❤️", 3000);
                    refreshAndSave();
                })
                .setCancelable(false)
                .show();
    }

    private void checkGrowth() {
        if (!state.canGrow() || growthDialogOpen) return;
        if (state.stage == GameState.STAGE_RAICHU) {
            showMegaChoice();
            return;
        }
        String from = state.speciesName();
        String to = state.stage == GameState.STAGE_PICHU ? "Pikachu" : "Raichu";
        growthDialogOpen = true;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("✨ Ready to evolve!")
                .setMessage(state.name + " grew through care and friendship.\n\n" + from + " → " + to)
                .setPositiveButton("✨ Evolve", (d,w) -> {
                    petView.startEvolutionAnimation(from + " → " + to);
                    petView.postDelayed(() -> {
                        state.stage = state.stage == GameState.STAGE_PICHU ? GameState.STAGE_PIKACHU : GameState.STAGE_RAICHU;
                        state.megaActive = false;
                        say(state.name + " evolved into " + to + "! 🎉", 3500);
                        refreshAndSave();
                    },1000);
                })
                .setNegativeButton("Later", null)
                .create();
        dialog.setOnDismissListener(d -> growthDialogOpen = false);
        dialog.show();
    }

    private void showMegaChoice() {
        if (growthDialogOpen) return;
        growthDialogOpen = true;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("⚡ Mega Bond Unlocked!")
                .setMessage("Choose a special form. You can still return to normal Raichu after rest.")
                .setPositiveButton("🔵 Mega X", (d,w) -> chooseMega(1))
                .setNegativeButton("🟡 Mega Y", (d,w) -> chooseMega(2))
                .setNeutralButton("Later", null)
                .create();
        dialog.setOnDismissListener(d -> growthDialogOpen = false);
        dialog.show();
    }

    private void chooseMega(int which) {
        String target = "Mega Raichu " + (which == 1 ? "X" : "Y");
        petView.startEvolutionAnimation("Raichu → " + target);
        petView.postDelayed(() -> {
            state.megaChoice = which;
            state.megaActive = true;
            say(target + " unlocked! ⚡", 3200);
            refreshAndSave();
        },1000);
    }

    private void refreshAndSave() {
        refresh();
        state.save(this);
    }

    private void refresh() {
        happyChip.setText("❤️ " + state.happy);
        foodChip.setText("🍎 " + state.fullPercent());
        energyChip.setText("⚡ " + state.energy);
        cleanChip.setText("🫧 " + state.clean);

        if (state.stage == GameState.STAGE_EGG) {
            expBar.setMax(4);
            expBar.setProgress(state.hatchCare);
            levelLine.setText("Mystery Egg • Care " + state.hatchCare + "/4");
        } else {
            expBar.setMax(state.expToNext());
            expBar.setProgress(state.exp);
            levelLine.setText("Lv." + state.level + "   •   EXP " + state.exp + "/" + state.expToNext() + "   •   ❤️ Bond " + state.bond);
        }
        stageLine.setText(state.speciesName() + "   •   " + state.growthRequirement());
        petView.bind(state);
        if (System.currentTimeMillis() >= messageUntil) speech.setText(contextStatus());
    }

    private String contextStatus() {
        if (state == null) return "";
        if (state.stage == GameState.STAGE_EGG) return state.hatchCare >= 3 ? "The egg is cracking... one more gentle care! 🥚" : "Tap the egg, talk to it, or keep it warm ❤️";
        if (state.hunger > 65) return state.name + " is hungry. Try Feed 🍓";
        if (state.energy < 25) return state.name + " is getting sleepy 🌙";
        if (state.clean < 35) return state.name + " would love a bubbly bath 🫧";
        if (state.happy < 45) return state.name + " wants to play with you 🎾";
        return "Tap, swipe, or hold " + state.name + " to interact ❤️";
    }

    private void say(String msg, long ms) {
        if (speech == null) return;
        messageUntil = System.currentTimeMillis() + ms;
        speech.setText(msg);
        speech.animate().alpha(0.65f).setDuration(60).withEndAction(() -> speech.animate().alpha(1f).setDuration(120).start()).start();
        handler.removeCallbacks(restoreSpeech);
        handler.postDelayed(restoreSpeech, ms + 80);
    }

    private TextView label(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.rgb(62,67,63));
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private GradientDrawable gradient(int top, int bottom, int radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top,bottom});
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
