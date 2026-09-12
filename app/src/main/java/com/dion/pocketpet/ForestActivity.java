package com.dion.pocketpet;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class ForestActivity extends Activity {
    private GameState state;
    private ForestView forestView;
    private TextView status;
    private TextView jumpButton;
    private boolean rewarded = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        state = GameState.load(this);
        if (state.stage == GameState.STAGE_EGG) { finish(); return; }
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(10), dp(10), dp(10));
        root.setBackgroundColor(Color.rgb(229,247,226));

        TextView title = text("🌲 Forest Walk", 22, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(dp(8), dp(8), dp(8), dp(5));
        title.setBackground(round(Color.argb(245,255,255,255), 22));
        title.setElevation(dp(3));
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        status = text("Tap goodies • jump over logs • enjoy the walk!", 13, true);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(8), dp(7), dp(8), dp(7));
        root.addView(status);

        forestView = new ForestView();
        forestView.setListener((score, goodies, obstacles) -> onWalkComplete(score, goodies, obstacles));
        root.addView(forestView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0,dp(7),0,0);

        jumpButton = action("⬆️\nJUMP", Color.rgb(255,225,139));
        jumpButton.setOnClickListener(v -> {
            bounce(v);
            if (forestView.isComplete()) {
                rewarded = false;
                forestView.restart();
                jumpButton.setText("⬆️\nJUMP");
                status.setText("Tap goodies • jump over logs • enjoy the walk!");
            } else {
                forestView.jump();
            }
        });
        controls.addView(jumpButton, weighted());

        TextView home = action("🏠\nHOME", Color.rgb(214,235,255));
        home.setOnClickListener(v -> { bounce(v); finish(); });
        controls.addView(home, weighted());
        root.addView(controls);

        setContentView(root);
    }

    private void onWalkComplete(int score, int goodies, int obstacles) {
        if (rewarded) return;
        rewarded = true;
        state.energy = GameState.clamp(state.energy - 10);
        state.happy = GameState.clamp(state.happy + 7);
        state.bond = GameState.clamp(state.bond + 2);
        int exp = 10 + Math.min(18, score / 3);
        String expText = state.addExp(exp);
        state.save(this);
        status.setText("Great walk! ✨  " + goodies + " goodies • " + obstacles + " jumps • " + expText);
        jumpButton.setText("🔁\nAGAIN");
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(72), 1f);
        lp.setMargins(dp(5),0,dp(5),0);
        return lp;
    }

    private TextView action(String s, int color) {
        TextView v = text(s, 16, true);
        v.setGravity(Gravity.CENTER);
        v.setClickable(true);
        v.setFocusable(true);
        v.setElevation(dp(3));
        GradientDrawable shape = round(color, 24);
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(55,255,255,255)),shape,null));
        return v;
    }

    private void bounce(View v) {
        v.animate().scaleX(.93f).scaleY(.93f).setDuration(70).withEndAction(() ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(110).start()).start();
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(Color.rgb(56,72,59));
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radiusDp)); return g;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private interface WalkListener { void onComplete(int score, int goodies, int obstacles); }

    private class ForestView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random random = new Random();
        private final List<Goodie> goodies = new ArrayList<>();
        private final List<Obstacle> obstacles = new ArrayList<>();
        private WalkListener listener;
        private long startMs = System.currentTimeMillis();
        private long lastMs = startMs;
        private long nextGoodieMs = startMs + 800;
        private long nextObstacleMs = startMs + 2600;
        private long jumpStart = 0L;
        private long stumbleUntil = 0L;
        private float sceneOffset = 0f;
        private int score = 0, collected = 0, jumps = 0;
        private boolean complete = false;
        private final long durationMs = 30000L;

        ForestView() {
            super(ForestActivity.this);
            txt.setTypeface(Typeface.create("sans",Typeface.BOLD));
            txt.setTextAlign(Paint.Align.CENTER);
            setLayerType(View.LAYER_TYPE_SOFTWARE,null);
            post(tick);
        }

        void setListener(WalkListener l){ listener=l; }
        boolean isComplete(){ return complete; }

        void restart(){
            startMs=System.currentTimeMillis(); lastMs=startMs; nextGoodieMs=startMs+700; nextObstacleMs=startMs+2400;
            jumpStart=0; stumbleUntil=0; sceneOffset=0; score=0; collected=0; jumps=0; complete=false; goodies.clear(); obstacles.clear(); invalidate();
        }

        void jump(){
            if(complete) return;
            long now=System.currentTimeMillis();
            if(now-jumpStart<720) return;
            jumpStart=now; jumps++; invalidate();
        }

        private final Runnable tick = new Runnable(){
            @Override public void run(){
                long now=System.currentTimeMillis();
                float dt=Math.min(.06f,Math.max(.001f,(now-lastMs)/1000f)); lastMs=now;
                if(!complete){
                    float speed=Math.max(dp(92),getWidth()*.24f);
                    sceneOffset += speed*dt;
                    for(Goodie g:goodies) g.x-=speed*dt;
                    for(Obstacle o:obstacles) o.x-=speed*dt;
                    if(now>=nextGoodieMs){ spawnGoodie(); nextGoodieMs=now+750+random.nextInt(650); }
                    if(now>=nextObstacleMs){ spawnObstacle(); nextObstacleMs=now+3100+random.nextInt(2100); }
                    checkObstacle(now);
                    cleanup();
                    if(now-startMs>=durationMs){ complete=true; if(listener!=null) listener.onComplete(score,collected,jumps); }
                }
                invalidate(); postDelayed(this,33);
            }
        };

        @Override protected void onDetachedFromWindow(){ removeCallbacks(tick); super.onDetachedFromWindow(); }

        private void spawnGoodie(){
            if(getWidth()<=0)return;
            int type=random.nextInt(3);
            float y=getHeight()*(.38f+random.nextFloat()*.20f);
            goodies.add(new Goodie(getWidth()+dp(60),y,type));
        }

        private void spawnObstacle(){
            if(getWidth()<=0)return;
            obstacles.add(new Obstacle(getWidth()+dp(80),random.nextBoolean()?0:1));
        }

        private void checkObstacle(long now){
            float petX=getWidth()*.31f;
            float jump=jumpHeight(now);
            for(Obstacle o:obstacles){
                if(!o.passed && Math.abs(o.x-petX)<dp(30)){
                    o.passed=true;
                    if(jump < dp(40)){ stumbleUntil=now+650; }
                    else score+=4;
                }
            }
        }

        private void cleanup(){
            Iterator<Goodie> gi=goodies.iterator(); while(gi.hasNext()) if(gi.next().x<-dp(80)) gi.remove();
            Iterator<Obstacle> oi=obstacles.iterator(); while(oi.hasNext()) if(oi.next().x<-dp(100)) oi.remove();
        }

        private float jumpHeight(long now){
            if(jumpStart==0)return 0f;
            float t=(now-jumpStart)/850f; if(t<0||t>1)return 0f;
            return (float)Math.sin(Math.PI*t)*dp(105);
        }

        @Override protected void onDraw(Canvas c){
            super.onDraw(c); int w=getWidth(),h=getHeight(); if(w<=0||h<=0)return;
            long now=System.currentTimeMillis();
            drawSky(c,w,h); drawParallax(c,w,h); drawPath(c,w,h); drawScenery(c,w,h);
            for(Goodie g:goodies) drawGoodie(c,g);
            for(Obstacle o:obstacles) drawObstacle(c,o,h);
            float jump=jumpHeight(now); drawWalkingPet(c,w*.31f,h*.66f-jump,w,h,now);
            drawHud(c,w,h,now);
            if(now<stumbleUntil) drawStumble(c,w,h);
            if(complete) drawFinish(c,w,h);
        }

        private void drawSky(Canvas c,int w,int h){
            p.setShader(new LinearGradient(0,0,0,h*.68f,Color.rgb(155,222,250),Color.rgb(236,250,226),Shader.TileMode.CLAMP)); c.drawRect(0,0,w,h,p); p.setShader(null);
            p.setColor(Color.rgb(255,238,142)); c.drawCircle(w*.80f,h*.13f,dp(28),p);
            p.setColor(Color.argb(190,255,255,255)); c.drawOval(new RectF(w*.10f,h*.12f,w*.35f,h*.19f),p); c.drawOval(new RectF(w*.53f,h*.20f,w*.73f,h*.26f),p);
        }

        private void drawParallax(Canvas c,int w,int h){
            float far=-(sceneOffset*.18f)% (w*.65f); p.setColor(Color.rgb(129,192,123));
            for(int i=-1;i<4;i++){float x=far+i*w*.65f;c.drawOval(new RectF(x,h*.32f,x+w*.78f,h*.67f),p);} 
            float mid=-(sceneOffset*.42f)%dp(150); for(int i=-2;i<w/dp(150)+3;i++) drawTree(c,mid+i*dp(150),h*.47f,dp(38),Color.rgb(78,151,91),.65f);
        }

        private void drawPath(Canvas c,int w,int h){
            p.setColor(Color.rgb(112,184,92)); c.drawRect(0,h*.57f,w,h,p);
            Path path=new Path(); path.moveTo(0,h*.69f); path.lineTo(w,h*.63f); path.lineTo(w,h); path.lineTo(0,h); path.close(); p.setColor(Color.rgb(224,198,145)); c.drawPath(path,p);
            p.setColor(Color.argb(75,255,255,255)); for(int i=0;i<7;i++){float x=(i*w/6f-(sceneOffset*.75f)% (w/6f));c.drawOval(new RectF(x,h*.80f,x+dp(44),h*.84f),p);}
        }

        private void drawScenery(Canvas c,int w,int h){
            float near=-(sceneOffset)%dp(260); for(int i=-1;i<w/dp(260)+3;i++){float x=near+i*dp(260); drawTree(c,x,h*.61f,dp(54),Color.rgb(61,139,74),1f);}
            float flowers=-(sceneOffset*1.05f)%dp(130); txt.setTextSize(dp(20)); for(int i=-1;i<w/dp(130)+3;i++){float x=flowers+i*dp(130);c.drawText(i%2==0?"🌼":"🌷",x,h*.72f,txt);} 
        }

        private void drawTree(Canvas c,float x,float ground,float r,int leaf,float alpha){
            p.setColor(Color.rgb(129,91,58)); c.drawRoundRect(new RectF(x-r*.12f,ground-r*.2f,x+r*.12f,ground+r*.95f),r*.12f,r*.12f,p);
            p.setColor(leaf); c.drawCircle(x,ground-r*.45f,r*.62f,p); c.drawCircle(x-r*.42f,ground-r*.20f,r*.50f,p); c.drawCircle(x+r*.42f,ground-r*.20f,r*.50f,p);
        }

        private void drawGoodie(Canvas c,Goodie g){
            if(g.collected)return; txt.setTextSize(dp(42)); String e=g.type==0?"🍓":g.type==1?"⭐":"🦋"; c.drawText(e,g.x,g.y,txt);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(Color.argb(90,255,255,255));c.drawCircle(g.x,g.y-dp(12),dp(33),p);p.setStyle(Paint.Style.FILL);
        }

        private void drawObstacle(Canvas c,Obstacle o,int h){
            float y=h*.76f; if(o.type==0){p.setColor(Color.rgb(123,82,49));c.drawRoundRect(new RectF(o.x-dp(35),y-dp(21),o.x+dp(35),y+dp(15)),dp(12),dp(12),p);p.setColor(Color.rgb(91,60,42));c.drawCircle(o.x-dp(30),y-dp(3),dp(13),p);}else{p.setShader(new RadialGradient(o.x,y,dp(48),Color.rgb(150,220,244),Color.rgb(73,164,206),Shader.TileMode.CLAMP));c.drawOval(new RectF(o.x-dp(44),y-dp(10),o.x+dp(44),y+dp(17)),p);p.setShader(null);} 
        }

        private void drawWalkingPet(Canvas c,float cx,float cy,int w,int h,long now){
            float base=Math.min(w,h)/560f; if(state.stage==GameState.STAGE_PICHU)base*=.78f; else if(state.stage==GameState.STAGE_RAICHU)base*=1.02f;
            float step=(float)Math.sin(now/95.0), bob=(float)Math.abs(Math.sin(now/190.0))*dp(4); cy-=bob;
            float lean=now<stumbleUntil?(float)Math.sin(now/45.0)*7f:-3f;
            c.save();c.rotate(lean,cx,cy);p.setShader(new RadialGradient(cx,cy+dp(110)*base,dp(90)*base,Color.argb(80,50,50,40),Color.TRANSPARENT,Shader.TileMode.CLAMP));c.drawOval(new RectF(cx-dp(80)*base,cy+dp(92)*base,cx+dp(80)*base,cy+dp(127)*base),p);p.setShader(null);
            int body=state.stage==GameState.STAGE_RAICHU?Color.rgb(236,165,78):Color.rgb(255,216,72);
            p.setColor(body);c.drawOval(new RectF(cx-dp(55)*base,cy+dp(30)*base,cx+dp(55)*base,cy+dp(125)*base),p);c.drawCircle(cx,cy,dp(70)*base,p);
            if(state.stage!=GameState.STAGE_RAICHU){Path l=new Path();l.moveTo(cx-dp(50)*base,cy-dp(48)*base);l.lineTo(cx-dp(42)*base+step*dp(4),cy-dp(122)*base);l.lineTo(cx-dp(14)*base,cy-dp(61)*base);l.close();c.drawPath(l,p);Path r=new Path();r.moveTo(cx+dp(50)*base,cy-dp(48)*base);r.lineTo(cx+dp(42)*base+step*dp(4),cy-dp(122)*base);r.lineTo(cx+dp(14)*base,cy-dp(61)*base);r.close();c.drawPath(r,p);}else{c.drawOval(new RectF(cx-dp(75)*base,cy-dp(65)*base,cx-dp(25)*base,cy-dp(22)*base),p);c.drawOval(new RectF(cx+dp(25)*base,cy-dp(65)*base,cx+dp(75)*base,cy-dp(22)*base),p);}
            p.setColor(Color.rgb(47,39,33));c.drawCircle(cx-dp(22)*base,cy-dp(7)*base,dp(7)*base,p);c.drawCircle(cx+dp(22)*base,cy-dp(7)*base,dp(7)*base,p);p.setColor(Color.WHITE);c.drawCircle(cx-dp(24)*base,cy-dp(10)*base,dp(2)*base,p);c.drawCircle(cx+dp(20)*base,cy-dp(10)*base,dp(2)*base,p);
            p.setColor(Color.rgb(242,92,78));c.drawCircle(cx-dp(48)*base,cy+dp(17)*base,dp(12)*base,p);c.drawCircle(cx+dp(48)*base,cy+dp(17)*base,dp(12)*base,p);
            p.setColor(Color.rgb(210,145,54));c.drawOval(new RectF(cx-dp(47)*base+step*dp(9),cy+dp(108)*base,cx-dp(4)*base+step*dp(9),cy+dp(132)*base),p);c.drawOval(new RectF(cx+dp(4)*base-step*dp(9),cy+dp(108)*base,cx+dp(47)*base-step*dp(9),cy+dp(132)*base),p);
            c.restore();
        }

        private void drawHud(Canvas c,int w,int h,long now){
            float progress=Math.min(1f,(now-startMs)/(float)durationMs); p.setColor(Color.argb(230,255,255,255));c.drawRoundRect(new RectF(dp(14),dp(14),w-dp(14),dp(70)),dp(22),dp(22),p);
            p.setColor(Color.rgb(225,232,220));c.drawRoundRect(new RectF(dp(30),dp(48),w-dp(30),dp(60)),dp(8),dp(8),p);p.setColor(Color.rgb(96,184,91));c.drawRoundRect(new RectF(dp(30),dp(48),dp(30)+(w-dp(60))*progress,dp(60)),dp(8),dp(8),p);
            txt.setColor(Color.rgb(55,75,58));txt.setTextSize(dp(15));c.drawText("⭐ "+score+"    🍓 "+collected,w*.30f,dp(38),txt);c.drawText("Forest trail",w*.72f,dp(38),txt);
        }

        private void drawStumble(Canvas c,int w,int h){txt.setTextSize(dp(30));c.drawText("😵‍💫",w*.31f,h*.36f,txt);txt.setTextSize(dp(13));txt.setColor(Color.rgb(100,71,51));c.drawText("Oops — you're okay!",w*.50f,h*.34f,txt);}

        private void drawFinish(Canvas c,int w,int h){
            p.setColor(Color.argb(220,255,255,255));c.drawRoundRect(new RectF(w*.10f,h*.31f,w*.90f,h*.62f),dp(28),dp(28),p);txt.setColor(Color.rgb(60,92,62));txt.setTextSize(dp(26));c.drawText("🌟 Walk Complete!",w/2f,h*.40f,txt);txt.setTextSize(dp(17));c.drawText("Score "+score+"  •  Goodies "+collected,w/2f,h*.48f,txt);txt.setTextSize(dp(13));c.drawText("Tap AGAIN for another walk, or HOME",w/2f,h*.56f,txt);
        }

        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getActionMasked()!=MotionEvent.ACTION_DOWN||complete)return true;
            float x=e.getX(),y=e.getY();
            for(Goodie g:goodies){
                if(!g.collected && Math.hypot(x-g.x,y-g.y)<dp(48)){g.collected=true;collected++;score+=g.type==1?5:3;invalidate();return true;}
            }
            return true;
        }

        private class Goodie { float x,y; int type; boolean collected=false; Goodie(float x,float y,int t){this.x=x;this.y=y;this.type=t;} }
        private class Obstacle { float x; int type; boolean passed=false; Obstacle(float x,int t){this.x=x;this.type=t;} }
    }
}
