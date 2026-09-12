package com.dion.pocketpet;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Random;

public class BattleDialog {
    public interface Callback { void onFinished(boolean won, String reward); }

    private final Context context;
    private final GameState state;
    private final Callback callback;
    private int petHp;
    private int friendHp;
    private int maxPetHp;
    private int maxFriendHp;
    private boolean ended = false;
    private final Random random = new Random();
    private String friendName;

    public BattleDialog(Context context, GameState state, Callback callback) {
        this.context = context;
        this.state = state;
        this.callback = callback;
    }

    public void show() {
        String[] friends = {"Sproutling", "Bubbly", "Pebblit"};
        friendName = friends[random.nextInt(friends.length)];
        maxPetHp = 70 + state.level * 10 + (state.megaActive && state.megaChoice == 1 ? 30 : 0);
        maxFriendHp = 55 + state.level * 8;
        petHp = maxPetHp;
        friendHp = maxFriendHp;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(42, 24, 42, 24);

        TextView title = text("Friendly Forest Battle", 21, true);
        title.setGravity(Gravity.CENTER);
        TextView foe = text(friendName + " wants a friendly match!", 16, false);
        foe.setGravity(Gravity.CENTER);
        TextView status = text("Choose a move. Nobody gets hurt or loses items.", 15, false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 16, 0, 16);

        TextView friendLabel = text(friendName, 14, true);
        ProgressBar friendBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        friendBar.setMax(maxFriendHp);
        friendBar.setProgress(friendHp);
        TextView petLabel = text(state.name, 14, true);
        ProgressBar petBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        petBar.setMax(maxPetHp);
        petBar.setProgress(petHp);

        Button quick = new Button(context);
        quick.setText("Quick Move");
        Button special = new Button(context);
        special.setText("Spark Move");
        Button cheer = new Button(context);
        cheer.setText("Cheer / Rest");

        root.addView(title);
        root.addView(foe);
        root.addView(friendLabel);
        root.addView(friendBar);
        root.addView(petLabel);
        root.addView(petBar);
        root.addView(status);
        root.addView(quick);
        root.addView(special);
        root.addView(cheer);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(root)
                .setNegativeButton("Leave", (d, which) -> {
                    if (!ended && callback != null) callback.onFinished(false, "No penalty. Adventure can wait!");
                })
                .create();

        View.OnClickListener attack = v -> {
            if (ended) return;
            int damage;
            if (v == special) {
                damage = 12 + state.level * 3 + (state.megaActive ? (state.megaChoice == 2 ? 9 : 6) : 0) + random.nextInt(12);
                status.setText(state.name + " used Spark Move! -" + damage + " stamina");
            } else {
                damage = 8 + state.level * 2 + (state.megaActive ? (state.megaChoice == 2 ? 6 : 4) : 0) + random.nextInt(9);
                status.setText(state.name + " used Quick Move! -" + damage + " stamina");
            }
            friendHp = Math.max(0, friendHp - damage);
            friendBar.setProgress(friendHp);
            if (friendHp <= 0) {
                ended = true;
                String reward = state.addExp(25 + state.level * 2);
                state.happy = GameState.clamp(state.happy + 6);
                state.bond = GameState.clamp(state.bond + 2);
                status.setText("Nice teamwork! " + friendName + " gives a happy wave. " + reward);
                disable(quick, special, cheer);
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setText("Done");
                if (callback != null) callback.onFinished(true, reward);
                return;
            }
            friendTurn(status, petBar, quick, special, cheer, dialog);
        };

        quick.setOnClickListener(attack);
        special.setOnClickListener(attack);
        cheer.setOnClickListener(v -> {
            if (ended) return;
            int heal = 12 + state.level * 2;
            petHp = Math.min(maxPetHp, petHp + heal);
            petBar.setProgress(petHp);
            status.setText("You cheered! " + state.name + " recovered " + heal + " stamina.");
            friendTurn(status, petBar, quick, special, cheer, dialog);
        });

        dialog.show();
    }

    private void friendTurn(TextView status, ProgressBar petBar, Button a, Button b, Button c, AlertDialog dialog) {
        if (ended) return;
        int damage = 6 + state.level + random.nextInt(8);
        petHp = Math.max(0, petHp - damage);
        petBar.setProgress(petHp);
        status.append("\n" + friendName + " bounced back! -" + damage + " stamina");
        if (petHp <= 0) {
            ended = true;
            String reward = state.addExp(6);
            status.append("\nGood try! " + state.name + " learned from the match. " + reward);
            disable(a, b, c);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setText("Rest at Home");
            if (callback != null) callback.onFinished(false, reward);
        }
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(context);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.rgb(50, 55, 45));
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private void disable(Button... buttons) {
        for (Button b : buttons) b.setEnabled(false);
    }
}
