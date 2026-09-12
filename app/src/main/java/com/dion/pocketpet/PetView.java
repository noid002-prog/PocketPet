package com.dion.pocketpet;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.Random;

public class PetView extends View {
    public interface Listener {
        void onHeadPat();
        void onPetSwipe();
        void onHug();
        void onFoodFed(int foodIndex);
        void onBallPlayed();
        void onBathComplete();
    }

    private static final int MODE_NORMAL = 0;
    private static final int MODE_FEED = 1;
    private static final int MODE_BALL = 2;
    private static final int MODE_BATH = 3;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private GameState state;
    private Listener listener;

    private float bob = 0;
    private boolean up = true;
    private float downX, downY;
    private long downTime;
    private boolean moved;

    private int mode = MODE_NORMAL;
    private int selectedFood = 0;
    private float itemX = -1, itemY = -1;
    private boolean draggingItem = false;
    private float itemDownX, itemDownY;
    private int bathStrokes = 0;

    private float walkX = 0;
    private float walkTarget = 0;
    private int idleTicks = 0;
    private long eggWiggleUntil = 0;
    private long sleepUntil = 0;
    private long evolutionUntil = 0;
    private String evolutionLabel = "Growing!";

    public PetView(Context context) {
        super(context);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
        setClickable(true);
        post(anim);
    }

    public void bind(GameState s) { state = s; invalidate(); }
    public void setListener(Listener l) { listener = l; }

    public void wiggleEgg() {
        eggWiggleUntil = System.currentTimeMillis() + 1500;
        invalidate();
    }

    public void startFood(int foodIndex) {
        if (state == null || state.stage == GameState.STAGE_EGG) return;
        mode = MODE_FEED;
        selectedFood = foodIndex;
        itemX = getWidth() / 2f;
        itemY = Math.max(80, getHeight() - 80);
        invalidate();
    }

    public void startBall() {
        if (state == null || state.stage == GameState.STAGE_EGG) return;
        mode = MODE_BALL;
        itemX = Math.max(55, getWidth() * 0.16f);
        itemY = Math.max(80, getHeight() - 85);
        invalidate();
    }

    public void startBath() {
        if (state == null || state.stage == GameState.STAGE_EGG) return;
        mode = MODE_BATH;
        bathStrokes = 0;
        invalidate();
    }

    public void startSleepAnimation() {
        sleepUntil = System.currentTimeMillis() + 1200;
        invalidate();
    }

    public void startEvolutionAnimation(String label) {
        evolutionLabel = label;
        evolutionUntil = System.currentTimeMillis() + 1800;
        invalidate();
    }

    public String modeHint() {
        if (mode == MODE_FEED) return "Drag the food onto your pet";
        if (mode == MODE_BALL) return "Drag and throw the ball";
        if (mode == MODE_BATH) return "Swipe over your pet 3 times";
        return "Tap = pat • Swipe = pet • Hold = cuddle";
    }

    private final Runnable anim = new Runnable() {
        @Override public void run() {
            bob += up ? 0.55f : -0.55f;
            if (bob > 7) up = false;
            if (bob < -2) up = true;

            if (mode == MODE_NORMAL && state != null && state.stage != GameState.STAGE_EGG) {
                walkX += (walkTarget - walkX) * 0.06f;
                idleTicks++;
                if (idleTicks % 80 == 0) {
                    float limit = Math.max(20, getWidth() * 0.16f);
                    walkTarget = (random.nextFloat() * 2f - 1f) * limit;
                }
                if (idleTicks % 115 == 0) walkTarget = 0;
            } else {
                walkTarget = 0;
                walkX += (0 - walkX) * 0.12f;
            }
            invalidate();
            postDelayed(this, 70);
        }
    };

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(anim);
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth(), h = getHeight();
        c.drawColor(Color.rgb(246, 252, 239));

        p.setColor(Color.rgb(221, 241, 204));
        c.drawOval(new RectF(w * 0.16f, h * 0.78f, w * 0.84f, h * 0.92f), p);
        p.setColor(Color.rgb(236, 224, 190));
        c.drawRoundRect(new RectF(w * 0.05f, h * 0.64f, w * 0.22f, h * 0.79f), 18, 18, p);
        p.setColor(Color.rgb(132, 185, 109));
        c.drawCircle(w * 0.135f, h * 0.63f, 16, p);
        c.drawCircle(w * 0.105f, h * 0.65f, 13, p);
        c.drawCircle(w * 0.165f, h * 0.65f, 13, p);

        float cx = w / 2f + walkX;
        float cy = h * 0.52f + bob;
        float scale = Math.min(w, h) / 480f;

        c.save();
        if (state != null && state.stage == GameState.STAGE_EGG && System.currentTimeMillis() < eggWiggleUntil) {
            float phase = (System.currentTimeMillis() % 360) / 360f;
            c.rotate((float)Math.sin(phase * Math.PI * 2) * 5f, cx, cy);
        }
        if (state == null || state.stage == GameState.STAGE_EGG) drawEgg(c, cx, cy, scale);
        else drawPet(c, cx, cy, scale);
        c.restore();

        if (state != null) {
            text.setColor(Color.rgb(61, 67, 49));
            text.setTextSize(20 * scale + 18);
            String top = state.stage == GameState.STAGE_EGG ? "Mystery Egg" : state.name + " • " + state.speciesName();
            c.drawText(top, w / 2f, h * 0.12f, text);
            text.setTextSize(13 * scale + 14);
            c.drawText("Feels " + state.moodText() + " • " + state.favoriteHabit(), w / 2f, h * 0.19f, text);
        }

        if (mode == MODE_FEED) drawItem(c, foodEmoji(selectedFood), itemX, itemY, 42);
        if (mode == MODE_BALL) drawItem(c, "●", itemX, itemY, 46);
        if (mode == MODE_BATH) drawBath(c, cx, cy, scale);
        if (System.currentTimeMillis() < sleepUntil) drawSleep(c, cx, cy, scale);
        if (System.currentTimeMillis() < evolutionUntil) drawEvolution(c, w, h);
    }

    private void drawItem(Canvas c, String emoji, float x, float y, float size) {
        p.setColor(Color.argb(225,255,255,255));
        c.drawCircle(x,y,size*0.72f,p);
        text.setTextSize(size);
        text.setColor(emoji.equals("●") ? Color.rgb(77,160,87) : Color.rgb(60,60,50));
        c.drawText(emoji, x, y + size * 0.32f, text);
        text.setTextSize(13);
        text.setColor(Color.rgb(60,60,50));
        c.drawText(mode == MODE_BALL ? "THROW" : "DRAG TO PET", x, y + size * 1.15f, text);
    }

    private String foodEmoji(int index) {
        return index == 0 ? "🍓" : index == 1 ? "🍎" : "🍪";
    }

    private void drawBath(Canvas c, float cx, float cy, float s) {
        text.setTextSize(14 + 8*s);
        text.setColor(Color.rgb(45,100,125));
        c.drawText("Bath swipe " + bathStrokes + "/3", getWidth()/2f, getHeight()*0.88f, text);
        long t = System.currentTimeMillis()/90;
        for (int i=0;i<10;i++) {
            float a = (i*37 + t*5)%360;
            float r = 55*s + (i%3)*24*s;
            float x = cx + (float)Math.cos(Math.toRadians(a))*r;
            float y = cy + 45*s + (float)Math.sin(Math.toRadians(a))*r*.55f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2*s);
            p.setColor(Color.argb(150,100,195,225));
            c.drawCircle(x,y,(10+i%4*3)*s,p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    private void drawSleep(Canvas c, float cx, float cy, float s) {
        text.setTextSize(24 + 12*s);
        text.setColor(Color.rgb(85,115,155));
        c.drawText("Z z z", cx + 115*s, cy - 95*s, text);
    }

    private void drawEvolution(Canvas c, int w, int h) {
        float pulse = 0.84f + 0.16f * (float)Math.abs(Math.sin(System.currentTimeMillis()/140.0));
        p.setColor(Color.argb(218,255,246,171));
        c.drawRect(0,0,w,h,p);
        p.setColor(Color.argb(120,255,255,255));
        c.drawCircle(w/2f,h/2f,Math.min(w,h)*0.34f*pulse,p);
        text.setColor(Color.rgb(95,78,30));
        text.setTextSize(31);
        c.drawText("✨ " + evolutionLabel + " ✨", w/2f,h*0.48f,text);
        text.setTextSize(15);
        c.drawText("Growing with friendship...", w/2f,h*0.56f,text);
    }

    private void drawEgg(Canvas c, float cx, float cy, float s) {
        p.setColor(Color.rgb(255, 248, 217));
        RectF egg = new RectF(cx - 78*s, cy - 110*s, cx + 78*s, cy + 95*s);
        c.drawOval(egg, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(5*s);
        p.setColor(Color.rgb(228, 200, 110));
        c.drawOval(egg, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(240, 204, 73));
        c.drawOval(new RectF(cx - 50*s, cy - 55*s, cx - 14*s, cy - 5*s), p);
        c.drawOval(new RectF(cx + 18*s, cy - 5*s, cx + 58*s, cy + 28*s), p);
        c.drawOval(new RectF(cx - 20*s, cy + 35*s, cx + 18*s, cy + 62*s), p);
        if (state != null && state.hatchCare >= 3) {
            p.setColor(Color.rgb(120, 99, 55));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(4*s);
            Path crack = new Path();
            crack.moveTo(cx, cy - 95*s);
            crack.lineTo(cx - 12*s, cy - 68*s);
            crack.lineTo(cx + 4*s, cy - 48*s);
            crack.lineTo(cx - 8*s, cy - 28*s);
            c.drawPath(crack, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    private void drawPet(Canvas c, float cx, float cy, float s) {
        float scale = s;
        if (state.stage == GameState.STAGE_PICHU) scale *= 0.82f;
        else if (state.stage == GameState.STAGE_RAICHU) scale *= 1.08f;
        if (state.megaActive) scale *= 1.07f;

        int yellow = Color.rgb(255,216,77);
        int orange = Color.rgb(237,164,73);
        int bodyColor = state.stage == GameState.STAGE_RAICHU ? orange : yellow;
        if (state.megaActive && state.megaChoice == 1) bodyColor = Color.rgb(231,162,75);
        if (state.megaActive && state.megaChoice == 2) bodyColor = Color.rgb(241,178,74);

        if (state.megaActive) {
            p.setColor(state.megaChoice == 1 ? Color.argb(55,70,150,230) : Color.argb(60,255,210,50));
            c.drawCircle(cx, cy + 15*scale, 155*scale, p);
        }

        p.setColor(bodyColor);
        if (state.stage == GameState.STAGE_RAICHU) {
            c.drawOval(new RectF(cx - 112*scale, cy - 110*scale, cx - 45*scale, cy - 40*scale), p);
            c.drawOval(new RectF(cx + 45*scale, cy - 110*scale, cx + 112*scale, cy - 40*scale), p);
            p.setColor(Color.rgb(95,70,40));
            c.drawCircle(cx - 78*scale, cy - 75*scale, 17*scale, p);
            c.drawCircle(cx + 78*scale, cy - 75*scale, 17*scale, p);
        } else {
            Path leftEar = new Path();
            leftEar.moveTo(cx - 92*scale, cy - 95*scale);
            leftEar.lineTo(cx - 75*scale, cy - 205*scale);
            leftEar.lineTo(cx - 25*scale, cy - 110*scale);
            leftEar.close(); c.drawPath(leftEar, p);
            Path rightEar = new Path();
            rightEar.moveTo(cx + 92*scale, cy - 95*scale);
            rightEar.lineTo(cx + 75*scale, cy - 205*scale);
            rightEar.lineTo(cx + 25*scale, cy - 110*scale);
            rightEar.close(); c.drawPath(rightEar, p);
            p.setColor(Color.rgb(80,58,35));
            float tipLen = state.stage == GameState.STAGE_PICHU ? 70 : 43;
            Path tip = new Path();
            tip.moveTo(cx - 75*scale, cy - 205*scale);
            tip.lineTo(cx - 58*scale, cy - (205-tipLen)*scale);
            tip.lineTo(cx - 72*scale, cy - (195-tipLen)*scale);
            tip.close(); c.drawPath(tip,p);
            tip.reset();
            tip.moveTo(cx + 75*scale, cy - 205*scale);
            tip.lineTo(cx + 58*scale, cy - (205-tipLen)*scale);
            tip.lineTo(cx + 72*scale, cy - (195-tipLen)*scale);
            tip.close(); c.drawPath(tip,p);
        }

        p.setColor(bodyColor);
        c.drawOval(new RectF(cx - 90*scale, cy + 45*scale, cx + 90*scale, cy + 190*scale), p);
        c.drawCircle(cx, cy, 115*scale, p);

        if (state.stage == GameState.STAGE_PICHU) {
            p.setColor(Color.rgb(80,58,35));
            Path collar = new Path();
            collar.moveTo(cx - 70*scale, cy + 80*scale);
            collar.lineTo(cx - 42*scale, cy + 105*scale);
            collar.lineTo(cx - 15*scale, cy + 82*scale);
            collar.lineTo(cx + 15*scale, cy + 106*scale);
            collar.lineTo(cx + 45*scale, cy + 82*scale);
            collar.lineTo(cx + 72*scale, cy + 106*scale);
            collar.lineTo(cx + 65*scale, cy + 125*scale);
            collar.lineTo(cx - 62*scale, cy + 125*scale);
            collar.close(); c.drawPath(collar,p);
        }

        p.setColor(state.stage == GameState.STAGE_RAICHU ? Color.rgb(244,201,84) : Color.rgb(243,91,77));
        c.drawCircle(cx - 78*scale, cy + 28*scale, 20*scale, p);
        c.drawCircle(cx + 78*scale, cy + 28*scale, 20*scale, p);

        boolean sleepy = state.energy < 20 || System.currentTimeMillis() < sleepUntil;
        boolean veryHappy = state.happy >= 80;
        p.setColor(Color.rgb(45,37,30));
        if (sleepy) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(5*scale);
            c.drawArc(new RectF(cx - 52*scale, cy - 22*scale, cx - 20*scale, cy + 2*scale),20,140,false,p);
            c.drawArc(new RectF(cx + 20*scale, cy - 22*scale, cx + 52*scale, cy + 2*scale),20,140,false,p);
            p.setStyle(Paint.Style.FILL);
        } else {
            c.drawOval(new RectF(cx - 48*scale, cy - 35*scale, cx - 25*scale, cy + 5*scale), p);
            c.drawOval(new RectF(cx + 25*scale, cy - 35*scale, cx + 48*scale, cy + 5*scale), p);
            p.setColor(Color.WHITE);
            c.drawCircle(cx - 35*scale, cy - 25*scale, 5*scale, p);
            c.drawCircle(cx + 35*scale, cy - 25*scale, 5*scale, p);
        }

        p.setColor(Color.rgb(76,52,35));
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(5*scale);
        if (veryHappy) c.drawArc(new RectF(cx - 34*scale, cy + 5*scale, cx + 34*scale, cy + 48*scale),5,170,false,p);
        else {
            c.drawArc(new RectF(cx - 30*scale, cy + 5*scale, cx, cy + 40*scale),10,120,false,p);
            c.drawArc(new RectF(cx, cy + 5*scale, cx + 30*scale, cy + 40*scale),50,120,false,p);
        }
        p.setStyle(Paint.Style.FILL);

        drawTail(c,cx,cy,scale);
        if (state.megaActive) {
            text.setTextSize(34*scale);
            text.setColor(state.megaChoice == 1 ? Color.rgb(55,115,190) : Color.rgb(195,135,25));
            c.drawText(state.megaChoice == 1 ? "X" : "Y", cx, cy - 125*scale, text);
        }
    }

    private void drawTail(Canvas c, float cx, float cy, float s) {
        if (state.stage == GameState.STAGE_RAICHU) {
            p.setColor(Color.rgb(100,75,42));
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(8*s);
            Path stem = new Path();
            stem.moveTo(cx + 75*s, cy + 115*s);
            stem.cubicTo(cx + 135*s,cy + 120*s,cx + 135*s,cy + 55*s,cx + 165*s,cy + 48*s);
            c.drawPath(stem,p); p.setStyle(Paint.Style.FILL);
            p.setColor(state.megaActive && state.megaChoice == 1 ? Color.rgb(90,135,185) : Color.rgb(241,185,52));
            Path end = new Path();
            end.moveTo(cx + 145*s,cy + 35*s); end.lineTo(cx + 185*s,cy + 12*s);
            end.lineTo(cx + 175*s,cy + 50*s); end.lineTo(cx + 205*s,cy + 55*s);
            end.lineTo(cx + 165*s,cy + 92*s); end.lineTo(cx + 160*s,cy + 60*s); end.close();
            c.drawPath(end,p);
        } else {
            p.setColor(Color.rgb(241,185,52));
            Path tail = new Path();
            tail.moveTo(cx + 83*s, cy + 105*s); tail.lineTo(cx + 150*s, cy + 65*s);
            tail.lineTo(cx + 125*s, cy + 125*s); tail.lineTo(cx + 185*s, cy + 110*s);
            tail.lineTo(cx + 130*s, cy + 190*s); tail.lineTo(cx + 82*s, cy + 165*s); tail.close();
            c.drawPath(tail,p);
        }
    }

    private boolean near(float x1,float y1,float x2,float y2,float distance) {
        return Math.hypot(x1-x2,y1-y2) <= distance;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (System.currentTimeMillis() < evolutionUntil) return true;
        float petCx = getWidth()/2f + walkX;
        float petCy = getHeight()*0.52f;

        if (mode == MODE_FEED || mode == MODE_BALL) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (near(e.getX(),e.getY(),itemX,itemY,85)) {
                        draggingItem = true;
                        itemDownX = e.getX(); itemDownY = e.getY();
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (draggingItem) { itemX=e.getX(); itemY=e.getY(); invalidate(); }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!draggingItem) return true;
                    draggingItem=false;
                    if (mode == MODE_FEED) {
                        if (near(itemX,itemY,petCx,petCy,145)) {
                            int food = selectedFood;
                            mode=MODE_NORMAL;
                            if(listener!=null) listener.onFoodFed(food);
                        }
                    } else {
                        float dist=(float)Math.hypot(e.getX()-itemDownX,e.getY()-itemDownY);
                        if (dist > 65) {
                            walkTarget = Math.max(-getWidth()*.18f,Math.min(getWidth()*.18f,itemX-getWidth()/2f));
                            mode=MODE_NORMAL;
                            postDelayed(() -> { walkTarget=0; if(listener!=null) listener.onBallPlayed(); }, 450);
                        }
                    }
                    invalidate(); return true;
                default:return true;
            }
        }

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX=e.getX(); downY=e.getY(); downTime=System.currentTimeMillis(); moved=false; return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(e.getX()-downX)>36 || Math.abs(e.getY()-downY)>36) moved=true;
                return true;
            case MotionEvent.ACTION_UP:
                long duration=System.currentTimeMillis()-downTime;
                float distance=(float)Math.hypot(e.getX()-downX,e.getY()-downY);
                if (mode == MODE_BATH && (moved || distance>45)) {
                    bathStrokes++;
                    if (bathStrokes >= 3) {
                        mode=MODE_NORMAL;
                        if(listener!=null) listener.onBathComplete();
                    }
                    invalidate(); return true;
                }
                if (duration>=650 && distance<55) { if(listener!=null) listener.onHug(); }
                else if (moved || distance>55) { if(listener!=null) listener.onPetSwipe(); }
                else { if(listener!=null) listener.onHeadPat(); performClick(); }
                return true;
            default:return true;
        }
    }

    @Override public boolean performClick(){ super.performClick(); return true; }
}
