package com.dion.pocketpet;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private GameState state;
    private PetView petView;
    private TextView statusLine, levelLine, stageLine;
    private ProgressBar happyBar, foodBar, energyBar, cleanBar, expBar;
    private boolean growthDialogOpen = false;
    private boolean hatchDialogOpen = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        state = GameState.load(this);
        buildUi();
        if (state.stage == GameState.STAGE_EGG && state.hatchCare == 0) {
            showMessage("A mysterious Pokémon Egg has arrived. Tap, pet, or care for it gently. 🥚");
        }
    }

    @Override protected void onPause() {
        state.save(this);
        super.onPause();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(10), dp(14), dp(10));
        root.setBackgroundColor(Color.rgb(255, 248, 231));

        TextView title = label("⚡ Pocket Pet", 24, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        levelLine = label("", 14, true);
        levelLine.setGravity(Gravity.CENTER);
        root.addView(levelLine);

        stageLine = label("", 12, true);
        stageLine.setGravity(Gravity.CENTER);
        stageLine.setPadding(dp(8), dp(3), dp(8), dp(4));
        root.addView(stageLine);

        expBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        root.addView(expBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)));

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.VERTICAL);
        stats.setPadding(0, dp(8), 0, dp(6));
        happyBar = stat(stats, "Happy");
        foodBar = stat(stats, "Full");
        energyBar = stat(stats, "Energy");
        cleanBar = stat(stats, "Clean");
        root.addView(stats);

        petView = new PetView(this);
        petView.bind(state);
        petView.setListener(new PetView.Listener() {
            @Override public void onHeadPat() {
                if (state.stage == GameState.STAGE_EGG) {
                    eggCare("You softly pat the egg.");
                    return;
                }
                state.happy = GameState.clamp(state.happy + 2);
                state.bond = GameState.clamp(state.bond + 1);
                state.petCount++;
                showMessage(state.name + " loves the head pats! ❤️");
                refreshAndSave();
                checkGrowth();
            }

            @Override public void onPetSwipe() {
                if (state.stage == GameState.STAGE_EGG) {
                    eggCare("You gently rub the shell.");
                    return;
                }
                state.happy = GameState.clamp(state.happy + 3);
                state.bond = GameState.clamp(state.bond + 1);
                state.petCount++;
                showMessage("Gentle petting makes " + state.name + " happy. ✨");
                refreshAndSave();
                checkGrowth();
            }

            @Override public void onHug() {
                if (state.stage == GameState.STAGE_EGG) {
                    eggCare("You cuddle the warm egg.");
                    return;
                }
                state.happy = GameState.clamp(state.happy + 5);
                state.bond = GameState.clamp(state.bond + 1);
                state.hugCount++;
                showMessage(state.name + " cuddles close. 🤗");
                refreshAndSave();
                checkGrowth();
            }

            @Override public void onFoodFed(int foodIndex) {
                rewardFood(foodIndex);
            }

            @Override public void onBallPlayed() {
                rewardPlay(0);
            }

            @Override public void onBathComplete() {
                state.clean = 100;
                state.happy = GameState.clamp(state.happy + 5);
                state.bond = GameState.clamp(state.bond + 1);
                showMessage(state.name + " is sparkling clean! 🫧 " + state.addExp(4));
                refreshAndSave();
                checkGrowth();
            }
        });
        root.addView(petView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        statusLine = label("", 14, false);
        statusLine.setGravity(Gravity.CENTER);
        statusLine.setPadding(0, dp(4), 0, dp(6));
        root.addView(statusLine);

        GridLayout actions = new GridLayout(this);
        actions.setColumnCount(3);
        actions.setRowCount(2);
        addAction(actions, "🍓 Feed", this::feed);
        addAction(actions, "🎾 Play", this::play);
        addAction(actions, "🫧 Clean", this::clean);
        addAction(actions, "💬 Talk", this::talk);
        addAction(actions, "🌙 Sleep", this::sleep);
        addAction(actions, "🌳 Adventure", this::adventure);
        root.addView(actions);

        TextView hint = label("Tap = pat • Swipe = pet • Hold = cuddle • Feed/ball/bath use gestures", 11, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(6), 0, 0);
        root.addView(hint);

        TextView safe = label("Offline prototype • no ads • no public chat • no pet death", 10, false);
        safe.setGravity(Gravity.CENTER);
        root.addView(safe);

        setContentView(root);
        refresh();
    }

    private ProgressBar stat(LinearLayout parent, String name) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = label(name, 13, true);
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        row.addView(t, new LinearLayout.LayoutParams(dp(68), dp(28)));
        row.addView(bar, new LinearLayout.LayoutParams(0, dp(12), 1f));
        parent.addView(row);
        return bar;
    }

    private void addAction(GridLayout grid, String text, Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(v -> action.run());
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = dp(54);
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        grid.addView(b, lp);
    }

    private void eggCare(String action) {
        state.hatchCare = Math.min(4, state.hatchCare + 1);
        petView.wiggleEgg();
        if (state.hatchCare < 4) {
            showMessage(action + " The egg wiggles! Hatch care " + state.hatchCare + "/4. 🥚");
            refreshAndSave();
        } else {
            refreshAndSave();
            showHatchDialog();
        }
    }

    private void showHatchDialog() {
        if (hatchDialogOpen || state.stage != GameState.STAGE_EGG) return;
        hatchDialogOpen = true;
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("✨ The egg is hatching!")
                .setMessage("You cared for it gently. Something tiny is coming out...")
                .setPositiveButton("🥚 TAP TO HATCH", (dialog, which) -> finishHatch())
                .setCancelable(false)
                .create();
        d.setOnDismiss(x -> hatchDialogOpen = false);
        d.show();
    }

    private void finishHatch() {
        petView.startEvolutionAnimation("Egg → Pichu");
        petView.postDelayed(() -> {
            state.stage = GameState.STAGE_PICHU;
            state.level = 1;
            state.exp = 0;
            state.bond = Math.max(8, state.bond);
            state.name = "Sparky";
            refreshAndSave();
            askNameAfterHatch();
        }, 900);
    }

    private void askNameAfterHatch() {
        EditText input = new EditText(this);
        input.setHint("Nickname");
        input.setText("Sparky");
        new AlertDialog.Builder(this)
                .setTitle("A Pichu hatched! 🎉")
                .setMessage("Give your new little buddy a nickname.")
                .setView(input)
                .setPositiveButton("Start", (d, w) -> {
                    String n = input.getText().toString().trim();
                    if (!n.isEmpty()) state.name = n.substring(0, Math.min(n.length(), 16));
                    showMessage("Welcome, " + state.name + "! ❤️");
                    refreshAndSave();
                })
                .setCancelable(false)
                .show();
    }

    private void feed() {
        if (state.stage == GameState.STAGE_EGG) {
            eggCare("You place a berry near the egg. A tiny chirp answers.");
            return;
        }
        String[] food = {"🍓 Berry", "🍎 Apple", "🍪 Tiny Treat"};
        new AlertDialog.Builder(this)
                .setTitle("Choose food")
                .setMessage("Then drag it onto " + state.name + ".")
                .setItems(food, (dialog, which) -> {
                    petView.startFood(which);
                    showMessage(petView.modeHint());
                })
                .setNegativeButton("Not now", null)
                .show();
    }

    private void rewardFood(int which) {
        state.feedCount++;
        String msg;
        if (which == 0) {
            state.hunger = GameState.clamp(state.hunger - 24);
            state.happy = GameState.clamp(state.happy + 3);
            msg = state.name + " munched a berry! " + state.addExp(5);
        } else if (which == 1) {
            state.hunger = GameState.clamp(state.hunger - 20);
            state.happy = GameState.clamp(state.happy + 4);
            msg = "Crunch! " + state.name + " liked the apple. " + state.addExp(5);
        } else {
            state.hunger = GameState.clamp(state.hunger - 10);
            state.happy = GameState.clamp(state.happy + 8);
            msg = "A tiny treat made " + state.name + " smile. " + state.addExp(3);
        }
        state.bond = GameState.clamp(state.bond + 1);
        showMessage(msg);
        refreshAndSave();
        checkGrowth();
    }

    private void play() {
        if (state.stage == GameState.STAGE_EGG) {
            eggCare("You gently rock the egg like a tiny game.");
            return;
        }
        if (state.energy < 12) {
            showMessage(state.name + " is sleepy. A nap will help.");
            return;
        }
        String[] games = {"🎾 Throw Ball", "🙈 Peekaboo", "🎵 Dance"};
        new AlertDialog.Builder(this)
                .setTitle("Play together")
                .setItems(games, (dialog, which) -> {
                    if (which == 0) {
                        petView.startBall();
                        showMessage(petView.modeHint());
                    } else {
                        rewardPlay(which);
                    }
                })
                .setNegativeButton("Later", null)
                .show();
    }

    private void rewardPlay(int which) {
        state.playCount++;
        state.energy = GameState.clamp(state.energy - 10);
        state.happy = GameState.clamp(state.happy + 14);
        state.hunger = GameState.clamp(state.hunger + 4);
        state.clean = GameState.clamp(state.clean - 3);
        state.bond = GameState.clamp(state.bond + 2);
        String activity = which == 0 ? "Ball chase!" : which == 1 ? "Peekaboo! You found each other!" : "Dance party!";
        showMessage(activity + " " + state.addExp(7));
        refreshAndSave();
        checkGrowth();
    }

    private void clean() {
        if (state.stage == GameState.STAGE_EGG) {
            eggCare("You polish the shell until it shines.");
            return;
        }
        petView.startBath();
        showMessage(petView.modeHint());
    }

    private void sleep() {
        if (state.stage == GameState.STAGE_EGG) {
            eggCare("You tuck the egg into a soft warm nest.");
            return;
        }
        petView.startSleepAnimation();
        showMessage(state.name + " curls up for a little nap... 🌙");
        petView.postDelayed(() -> {
            state.energy = 100;
            state.happy = GameState.clamp(state.happy + 3);
            if (state.megaActive) state.megaActive = false;
            showMessage("Good morning! " + state.name + " feels refreshed. ☀️");
            refreshAndSave();
        }, 1200);
    }

    private void talk() {
        if (state.stage == GameState.STAGE_EGG) {
            eggCare("You say hello. Tap tap... something answers from inside!");
            return;
        }
        String[] options = {"😊 How are you?", "🎾 Want to play?", "🍓 Are you hungry?", "🌙 Good night!", "❤️ I love you!", "✨ Growth status"};
        new AlertDialog.Builder(this)
                .setTitle("Talk to " + state.name)
                .setItems(options, (d, which) -> {
                    if (which == 5) {
                        showMessage(state.growthRequirement());
                        checkGrowth();
                        return;
                    }
                    String reply;
                    switch (which) {
                        case 0: reply = state.happy > 65 ? "I'm happy because you're here!" : "Can we spend a little time together?"; break;
                        case 1: reply = state.energy > 25 ? "Yay! Pick a game!" : "I'm sleepy, but I like that you asked."; break;
                        case 2: reply = state.hunger > 55 ? "A berry sounds yummy!" : "My tummy feels good!"; break;
                        case 3: reply = "Good night! I'll rest safely here."; break;
                        default: reply = "I love being your buddy too!"; break;
                    }
                    state.talkCount++;
                    state.happy = GameState.clamp(state.happy + 4);
                    state.bond = GameState.clamp(state.bond + 1);
                    showMessage(reply + " ❤️");
                    refreshAndSave();
                    checkGrowth();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void adventure() {
        if (state.stage == GameState.STAGE_EGG) {
            showMessage("The egg is still too little for adventure. Keep caring for it first. 🥚");
            return;
        }
        if (state.energy < 18) {
            showMessage("Adventure needs a little more energy. Let " + state.name + " rest first.");
            return;
        }
        List<String> options = new ArrayList<>();
        options.add("🌿 Explore Forest");
        options.add("⚔️ Friendly Battle");
        boolean hasMegaToggle = state.stage == GameState.STAGE_RAICHU && state.megaChoice != 0;
        if (hasMegaToggle) options.add(state.megaActive ? "⚡ Rest Mega Form" : "⚡ Activate Mega Form");
        options.add("🏠 Stay Home");
        String[] items = options.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Tiny Adventure")
                .setMessage("Battle is optional. Leaving or losing never removes items or levels.")
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        state.energy = GameState.clamp(state.energy - 8);
                        state.happy = GameState.clamp(state.happy + 5);
                        state.bond = GameState.clamp(state.bond + 1);
                        String[] finds = {"a shiny leaf", "two little berries", "a funny-shaped pebble", "a butterfly friend"};
                        String found = finds[(int)(System.currentTimeMillis() % finds.length)];
                        int reward = state.megaActive && state.megaChoice == 2 ? 14 : 10;
                        showMessage(state.name + " found " + found + "! 🌿 " + state.addExp(reward));
                        refreshAndSave();
                        checkGrowth();
                    } else if (which == 1) {
                        state.energy = GameState.clamp(state.energy - 10);
                        new BattleDialog(this, state, (won, reward) -> {
                            refreshAndSave();
                            checkGrowth();
                            if (won) Toast.makeText(this, "Great teamwork! " + reward, Toast.LENGTH_LONG).show();
                        }).show();
                    } else if (hasMegaToggle && which == 2) {
                        state.megaActive = !state.megaActive;
                        showMessage(state.megaActive ? "Mega Raichu " + (state.megaChoice == 1 ? "X" : "Y") + " activated! ⚡" : "Raichu returns to its normal form.");
                        refreshAndSave();
                    }
                })
                .show();
    }

    private void checkGrowth() {
        if (!state.canGrow() || growthDialogOpen) return;
        if (state.stage == GameState.STAGE_RAICHU) {
            showMegaChoice();
            return;
        }
        final String from = state.speciesName();
        final String to = state.stage == GameState.STAGE_PICHU ? "Pikachu" : "Raichu";
        growthDialogOpen = true;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("✨ Growth time!")
                .setMessage(state.name + " has grown through care, play, and friendship.\n\n" + from + " → " + to)
                .setPositiveButton("✨ EVOLVE", (d, w) -> {
                    petView.startEvolutionAnimation(from + " → " + to);
                    petView.postDelayed(() -> {
                        state.stage = state.stage == GameState.STAGE_PICHU ? GameState.STAGE_PIKACHU : GameState.STAGE_RAICHU;
                        state.megaActive = false;
                        showMessage(state.name + " evolved into " + to + "! 🎉");
                        refreshAndSave();
                    }, 900);
                })
                .setNegativeButton("Later", null)
                .create();
        dialog.setOnDismiss(x -> growthDialogOpen = false);
        dialog.show();
    }

    private void showMegaChoice() {
        if (growthDialogOpen) return;
        growthDialogOpen = true;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("⚡ Mega Bond Unlocked!")
                .setMessage("Your Raichu's bond is strong enough to unlock a special Mega form.\n\nMega X: sturdy & power style.\nMega Y: fast & energetic style.")
                .setPositiveButton("🔵 Mega Raichu X", (d, w) -> chooseMega(1))
                .setNegativeButton("🟡 Mega Raichu Y", (d, w) -> chooseMega(2))
                .setNeutralButton("Later", null)
                .create();
        dialog.setOnDismiss(x -> growthDialogOpen = false);
        dialog.show();
    }

    private void chooseMega(int which) {
        String target = "Mega Raichu " + (which == 1 ? "X" : "Y");
        petView.startEvolutionAnimation("Raichu → " + target);
        petView.postDelayed(() -> {
            state.megaChoice = which;
            state.megaActive = true;
            showMessage(state.name + " unlocked " + target + "! ⚡");
            refreshAndSave();
        }, 900);
    }

    private void refreshAndSave() {
        refresh();
        state.save(this);
    }

    private void refresh() {
        happyBar.setProgress(state.happy);
        foodBar.setProgress(state.fullPercent());
        energyBar.setProgress(state.energy);
        cleanBar.setProgress(state.clean);

        if (state.stage == GameState.STAGE_EGG) {
            expBar.setMax(4);
            expBar.setProgress(state.hatchCare);
            levelLine.setText("A mysterious egg has arrived");
        } else {
            expBar.setMax(state.expToNext());
            expBar.setProgress(state.exp);
            levelLine.setText("Lv." + state.level + " • EXP " + state.exp + "/" + state.expToNext() + " • Bond " + state.bond);
        }
        stageLine.setText(state.speciesName() + " • " + state.growthRequirement());
        petView.bind(state);

        if (state.stage == GameState.STAGE_EGG) statusLine.setText(state.hatchCare >= 3 ? "The egg is wiggling! One more gentle care may hatch it. 🥚" : "Care for the egg gently. It will hatch from your attention. 🥚");
        else if (state.hunger > 65) statusLine.setText(state.name + " looks hungry. 🍓");
        else if (state.energy < 25) statusLine.setText(state.name + " is getting sleepy. 🌙");
        else if (state.clean < 35) statusLine.setText(state.name + " could use a bath. 🫧");
        else if (state.happy < 45) statusLine.setText(state.name + " wants some play time. 🎾");
        else statusLine.setText("Tap, swipe, or hold " + state.name + " to interact! ❤️");
    }

    private void showMessage(String msg) {
        if (statusLine != null) statusLine.setText(msg);
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private TextView label(String text, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(Color.rgb(62, 63, 50));
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
