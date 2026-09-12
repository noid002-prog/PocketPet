package com.dion.pocketpet;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
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
    private int mode = MODE_NORMAL;

    private float bob = 0f;
    private boolean bobUp = true;
    private float walkX = 0f;
    private float walkTarget = 0f;
    private int idleTicks = 0;

    private float downX, downY;
    private long downTime;
    private boolean moved;
    private boolean downOnPet;

    private int selectedFood = -1;
    private boolean draggingFood = false;
    private float dragX, dragY;

    private boolean draggingBall = false;
    private float ballX, ballY;
    private float ballStartX, ballStartY;

    private int bathStrokes = 0;
    private long eggWiggleUntil = 0;
    private long sleepUntil = 0;
    private long evolutionUntil = 0;
    private String evolutionLabel = "Growing!";
    private long happySparkUntil = 0;

    // v0.6 motion layer: small squash/stretch, lean and limb follow-through.
    private static final int REACT_NONE=0, REACT_PAT=1, REACT_PET=2, REACT_HUG=3, REACT_EAT=4, REACT_CHASE=5, REACT_SHAKE=6;
    private int reaction = REACT_NONE;
    private long reactionStart = 0L, reactionUntil = 0L;
    private float reactionSign = 1f;
    private float walkPhase = 0f;
    private float currentWalkSwing = 0f;
    private float currentEarSway = 0f;
    private float currentTailSway = 0f;
    private final float density;

    public PetView(Context context) {
        super(context);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create("sans", Typeface.BOLD));
        setClickable(true);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        density = getResources().getDisplayMetrics().density;
        post(anim);
    }

    public void bind(GameState s) { state = s; invalidate(); }
    public void setListener(Listener l) { listener = l; }

    public void wiggleEgg() { eggWiggleUntil = System.currentTimeMillis() + 1300; invalidate(); }

    public void startFoodTray() {
        if (state == null || state.stage == GameState.STAGE_EGG) return;
        mode = MODE_FEED;
        selectedFood = -1;
        draggingFood = false;
        invalidate();
    }

    public void startBall() {
        if (state == null || state.stage == GameState.STAGE_EGG) return;
        mode = MODE_BALL;
        draggingBall = false;
        ballX = getWidth() * 0.5f;
        ballY = getHeight() * 0.84f;
        invalidate();
    }

    public void startBath() {
        if (state == null || state.stage == GameState.STAGE_EGG) return;
        mode = MODE_BATH;
        bathStrokes = 0;
        invalidate();
    }

    public void cancelMode() {
        mode = MODE_NORMAL;
        selectedFood = -1;
        draggingFood = false;
        draggingBall = false;
        invalidate();
    }

    public void startSleepAnimation() { sleepUntil = System.currentTimeMillis() + 1700; invalidate(); }
    public void startEvolutionAnimation(String label) { evolutionLabel = label; evolutionUntil = System.currentTimeMillis() + 1900; invalidate(); }
    public void happySpark() { happySparkUntil = System.currentTimeMillis() + 900; invalidate(); }

    private void react(int type, long duration, float sign) {
        reaction = type; reactionStart = System.currentTimeMillis(); reactionUntil = reactionStart + duration; reactionSign = sign == 0 ? 1f : sign; invalidate();
    }

    private float dp(float v){ return v*density; }

    private final Runnable anim = new Runnable() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            float breath = (float)Math.sin(now/520.0);
            bob = breath * 2.8f;
            if (mode == MODE_NORMAL && state != null && state.stage != GameState.STAGE_EGG && now >= reactionUntil) {
                float before = walkX;
                walkX += (walkTarget - walkX) * 0.07f;
                float velocity = walkX-before;
                walkPhase += Math.max(0.12f, Math.min(0.55f, Math.abs(velocity)*0.06f + (Math.abs(walkTarget-walkX)>4?0.24f:0.10f)));
                idleTicks++;
                if (idleTicks % 120 == 0) {
                    float limit = Math.max(dp(18),getWidth()*0.15f);
                    walkTarget = (random.nextFloat()*2f-1f)*limit;
                }
                if (idleTicks % 185 == 0) walkTarget = 0f;
            } else if (reaction != REACT_CHASE) {
                walkTarget = 0f;
                walkX += (0f-walkX)*0.12f;
            }
            if(now>=reactionUntil) reaction=REACT_NONE;
            invalidate();
            postDelayed(this,33);
        }
    };

    @Override protected void onDetachedFromWindow() { removeCallbacks(anim); super.onDetachedFromWindow(); }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w=getWidth(), h=getHeight();
        if(w<=0||h<=0)return;
        drawRoom(c,w,h);
        long now=System.currentTimeMillis();
        float cx=w/2f+walkX;
        float cy=h*0.52f+bob;
        float s=Math.min(w,h)/470f;
        float scaleX=1f, scaleY=1f, lift=0f, tilt=0f;
        float breath=(float)Math.sin(now/600.0);
        scaleY += breath*.012f; scaleX -= breath*.006f;
        float moving=Math.min(1f,Math.abs(walkTarget-walkX)/Math.max(dp(18),w*.08f));
        currentWalkSwing=(float)Math.sin(walkPhase)*moving;
        currentEarSway=(float)Math.sin(now/360.0)*.35f + currentWalkSwing*.65f;
        currentTailSway=(float)Math.sin(now/280.0)*.45f + currentWalkSwing*.55f;
        if(reaction!=REACT_NONE && now<reactionUntil){
            float t=(now-reactionStart)/(float)Math.max(1,reactionUntil-reactionStart);
            t=Math.max(0f,Math.min(1f,t));
            float pulse=(float)Math.sin(Math.PI*t);
            if(reaction==REACT_PAT){scaleY-=.10f*pulse;scaleX+=.06f*pulse;lift+=dp(5)*pulse;}
            else if(reaction==REACT_PET){tilt=8f*reactionSign*pulse;lift-=dp(5)*pulse;}
            else if(reaction==REACT_HUG){float hug=(float)Math.sin(Math.PI*Math.min(1f,t*1.25f));scaleX+=.13f*hug;scaleY+=.13f*hug;lift-=dp(15)*hug;}
            else if(reaction==REACT_EAT){tilt=4f*reactionSign*pulse;scaleY-=.035f*(float)Math.abs(Math.sin(t*Math.PI*6));}
            else if(reaction==REACT_CHASE){tilt=7f*reactionSign;lift-=dp(7)*(float)Math.abs(Math.sin(t*Math.PI*5));currentWalkSwing=(float)Math.sin(t*Math.PI*8);}
            else if(reaction==REACT_SHAKE){tilt=(float)Math.sin(t*Math.PI*12)*7f;scaleX+=.025f*pulse;}
        }
        drawGroundShadow(c,cx,cy+lift,s*scaleX);
        c.save();
        c.translate(cx,cy+lift);
        c.rotate(tilt);
        c.scale(scaleX,scaleY);
        c.translate(-cx,-cy);
        if(state!=null && state.stage==GameState.STAGE_EGG && now<eggWiggleUntil){
            float phase=(now%320)/320f;
            c.rotate((float)Math.sin(phase*Math.PI*2)*6f,cx,cy);
        }
        if(state==null||state.stage==GameState.STAGE_EGG)drawEgg(c,cx,cy,s); else drawPet(c,cx,cy,s);
        c.restore();
        drawNamePlate(c,w,h);
        if(mode==MODE_FEED)drawFoodTray(c,w,h);
        if(mode==MODE_BALL)drawBallMode(c,w,h);
        if(mode==MODE_BATH)drawBath(c,cx,cy,s,w,h);
        if(System.currentTimeMillis()<sleepUntil)drawSleep(c,cx,cy,s);
        if(System.currentTimeMillis()<happySparkUntil)drawHearts(c,cx,cy,s);
        if(System.currentTimeMillis()<evolutionUntil)drawEvolution(c,w,h);
    }

    private void drawRoom(Canvas c,int w,int h){
        p.clearShadowLayer();
        p.setShader(new LinearGradient(0,0,0,h*.66f,Color.rgb(255,250,229),Color.rgb(227,247,244),Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h*.67f,p); p.setShader(null);
        p.setShader(new LinearGradient(0,h*.64f,0,h,Color.rgb(242,218,183),Color.rgb(214,181,145),Shader.TileMode.CLAMP));
        c.drawRect(0,h*.64f,w,h,p); p.setShader(null);

        p.setShadowLayer(12,0,5,Color.argb(55,60,70,70)); p.setColor(Color.WHITE);
        RectF window=new RectF(w*.07f,h*.09f,w*.33f,h*.34f); c.drawRoundRect(window,24,24,p); p.clearShadowLayer();
        p.setShader(new LinearGradient(0,window.top,0,window.bottom,Color.rgb(164,224,251),Color.rgb(220,246,255),Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(window.left+8,window.top+8,window.right-8,window.bottom-8),18,18,p); p.setShader(null);
        p.setColor(Color.rgb(255,244,174)); c.drawCircle(window.left+window.width()*.72f,window.top+window.height()*.30f,19,p);
        p.setColor(Color.rgb(119,194,117)); c.drawOval(new RectF(window.left-10,window.bottom-48,window.right+8,window.bottom+3),p);

        p.setShadowLayer(10,0,6,Color.argb(48,70,50,40)); p.setColor(Color.rgb(251,183,184));
        c.drawRoundRect(new RectF(w*.72f,h*.52f,w*.95f,h*.66f),30,30,p); p.clearShadowLayer();
        p.setColor(Color.rgb(255,222,216)); c.drawRoundRect(new RectF(w*.75f,h*.535f,w*.91f,h*.59f),22,22,p);

        p.setColor(Color.rgb(221,151,103)); c.drawRoundRect(new RectF(w*.05f,h*.54f,w*.16f,h*.66f),14,14,p);
        p.setColor(Color.rgb(91,178,112)); c.drawOval(new RectF(w*.02f,h*.46f,w*.11f,h*.58f),p); c.drawOval(new RectF(w*.10f,h*.44f,w*.19f,h*.58f),p); c.drawOval(new RectF(w*.07f,h*.40f,w*.15f,h*.55f),p);

        p.setShadowLayer(10,0,5,Color.argb(38,70,50,40)); p.setColor(Color.rgb(254,238,184));
        c.drawOval(new RectF(w*.20f,h*.67f,w*.80f,h*.91f),p); p.clearShadowLayer();
        p.setColor(Color.rgb(255,248,218)); c.drawOval(new RectF(w*.27f,h*.70f,w*.73f,h*.86f),p);
    }

    private void drawGroundShadow(Canvas c,float cx,float cy,float s){
        p.setShader(new RadialGradient(cx,cy+178*s,120*s,Color.argb(80,80,63,48),Color.TRANSPARENT,Shader.TileMode.CLAMP));
        c.drawOval(new RectF(cx-120*s,cy+145*s,cx+120*s,cy+195*s),p); p.setShader(null);
    }

    private void drawNamePlate(Canvas c,int w,int h){
        if(state==null)return;
        p.setShadowLayer(7,0,3,Color.argb(35,30,30,30)); p.setColor(Color.argb(236,255,255,255));
        c.drawRoundRect(new RectF(w*.24f,h*.055f,w*.76f,h*.16f),28,28,p); p.clearShadowLayer();
        text.setColor(Color.rgb(61,67,64)); text.setTextSize(Math.max(20,w*.055f));
        String title=state.stage==GameState.STAGE_EGG?"Mystery Egg":state.name+"  •  "+state.speciesName();
        c.drawText(title,w/2f,h*.105f,text);
        text.setTextSize(Math.max(12,w*.032f)); text.setColor(Color.rgb(100,111,106)); c.drawText(state.moodText(),w/2f,h*.142f,text);
    }

    private float foodCardSize(int w){ return Math.min(w*.225f, dp(104)); }
    private float foodTokenSize(int w){ return Math.min(w*.145f, dp(72)); }
    private float ballRadius(int w){ return Math.min(w*.125f, dp(64)); }

    private void drawFoodTray(Canvas c,int w,int h){
        float trayTop=h*.69f;
        p.setShadowLayer(dp(10),0,-dp(3),Color.argb(45,40,40,40)); p.setColor(Color.argb(250,255,255,255));
        c.drawRoundRect(new RectF(w*.035f,trayTop,w*.965f,h*.985f),dp(22),dp(22),p); p.clearShadowLayer();
        text.setTextSize(Math.max(dp(13),w*.036f)); text.setColor(Color.rgb(78,86,79)); c.drawText("Hold a snack, then drag it to your buddy",w/2f,trayTop+dp(28),text);
        for(int i=0;i<3;i++){float x=foodHomeX(i,w), y=h*.835f; drawFoodCard(c,i,x,y,selectedFood==i&&draggingFood,w);}
        if(draggingFood&&selectedFood>=0)drawFoodToken(c,selectedFood,dragX,dragY,foodTokenSize(w)*1.10f,true);
    }

    private void drawFoodCard(Canvas c,int index,float x,float y,boolean active,int w){
        float base=foodCardSize(w), size=active?base*1.08f:base; int[] colors={Color.rgb(255,213,225),Color.rgb(255,232,181),Color.rgb(229,215,191)};
        p.setShadowLayer(active?dp(10):dp(6),0,active?dp(5):dp(3),Color.argb(55,60,50,45)); p.setColor(colors[index]);
        c.drawRoundRect(new RectF(x-size/2,y-size/2,x+size/2,y+size/2),dp(22),dp(22),p); p.clearShadowLayer();
        drawFoodToken(c,index,x,y-dp(4),foodTokenSize(w),false); text.setTextSize(dp(12)); text.setColor(Color.rgb(78,72,65));
        c.drawText(index==0?"Berry":index==1?"Apple":"Treat",x,y+size/2+dp(18),text);
    }

    private void drawFoodToken(Canvas c,int index,float x,float y,float size,boolean floating){
        if(floating){p.setShadowLayer(dp(9),0,dp(5),Color.argb(70,50,50,50)); p.setColor(Color.argb(246,255,255,255)); c.drawCircle(x,y,size*.72f,p); p.clearShadowLayer();}
        text.setTextSize(size); text.setColor(Color.rgb(50,50,50)); c.drawText(index==0?"🍓":index==1?"🍎":"🍪",x,y+size*.34f,text);
    }

    private float foodHomeX(int index,int w){return w*(.245f+index*.255f);}

    private void drawBallMode(Canvas c,int w,int h){
        if(!draggingBall){ballX=w*.5f;ballY=h*.82f;}
        float r=ballRadius(w);
        p.setShadowLayer(dp(10),0,dp(6),Color.argb(70,50,50,50)); p.setShader(new RadialGradient(ballX-r*.32f,ballY-r*.35f,r*1.35f,Color.rgb(205,255,190),Color.rgb(67,170,91),Shader.TileMode.CLAMP));
        c.drawCircle(ballX,ballY,r,p); p.setShader(null); p.clearShadowLayer(); p.setColor(Color.argb(220,255,255,255)); c.drawCircle(ballX-r*.30f,ballY-r*.32f,r*.20f,p);
        text.setTextSize(dp(15)); text.setColor(Color.rgb(72,83,75)); c.drawText("Hold, drag, then throw the ball",w/2f,h*.95f,text);
    }

    private void drawBath(Canvas c,float cx,float cy,float s,int w,int h){
        text.setTextSize(15); text.setColor(Color.rgb(48,105,130)); c.drawText("🫧 Gentle bath  "+bathStrokes+"/3",w/2f,h*.91f,text);
        long t=System.currentTimeMillis()/80;
        for(int i=0;i<13;i++){float a=(i*33+t*4)%360,r=62*s+(i%3)*25*s;float x=cx+(float)Math.cos(Math.toRadians(a))*r,y=cy+35*s+(float)Math.sin(Math.toRadians(a))*r*.58f;
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.2f*s);p.setColor(Color.argb(160,100,196,228));c.drawCircle(x,y,(10+i%4*3)*s,p);}
        p.setStyle(Paint.Style.FILL);
    }

    private void drawSleep(Canvas c,float cx,float cy,float s){text.setTextSize(30*s);text.setColor(Color.rgb(97,126,178));c.drawText("Z z z",cx+120*s,cy-100*s,text);}
    private void drawHearts(Canvas c,float cx,float cy,float s){text.setTextSize(28*s);c.drawText("❤️",cx-110*s,cy-125*s,text);c.drawText("✨",cx+110*s,cy-105*s,text);}

    private void drawEvolution(Canvas c,int w,int h){
        float pulse=.85f+.15f*(float)Math.abs(Math.sin(System.currentTimeMillis()/130.0));p.setColor(Color.argb(224,255,246,180));c.drawRect(0,0,w,h,p);
        p.setShader(new RadialGradient(w/2f,h/2f,Math.min(w,h)*.37f,Color.argb(230,255,255,255),Color.argb(30,255,214,95),Shader.TileMode.CLAMP));
        c.drawCircle(w/2f,h/2f,Math.min(w,h)*.34f*pulse,p);p.setShader(null);text.setColor(Color.rgb(92,75,30));text.setTextSize(30);c.drawText("✨ "+evolutionLabel+" ✨",w/2f,h*.48f,text);text.setTextSize(15);c.drawText("Growing with friendship...",w/2f,h*.55f,text);
    }

    private void drawEgg(Canvas c,float cx,float cy,float s){
        p.setShadowLayer(13,0,7,Color.argb(65,85,60,40));p.setShader(new RadialGradient(cx-28*s,cy-52*s,145*s,Color.rgb(255,255,247),Color.rgb(245,218,146),Shader.TileMode.CLAMP));
        RectF egg=new RectF(cx-80*s,cy-112*s,cx+80*s,cy+98*s);c.drawOval(egg,p);p.setShader(null);p.clearShadowLayer();
        p.setColor(Color.rgb(238,200,74));c.drawOval(new RectF(cx-50*s,cy-57*s,cx-13*s,cy-6*s),p);c.drawOval(new RectF(cx+19*s,cy-8*s,cx+59*s,cy+29*s),p);c.drawOval(new RectF(cx-20*s,cy+34*s,cx+19*s,cy+64*s),p);
        p.setColor(Color.argb(115,255,255,255));c.drawOval(new RectF(cx-48*s,cy-78*s,cx-18*s,cy-30*s),p);
        if(state!=null&&state.hatchCare>=3){p.setColor(Color.rgb(112,90,52));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4*s);Path crack=new Path();crack.moveTo(cx,cy-100*s);crack.lineTo(cx-13*s,cy-69*s);crack.lineTo(cx+5*s,cy-49*s);crack.lineTo(cx-9*s,cy-28*s);c.drawPath(crack,p);p.setStyle(Paint.Style.FILL);}
    }

    private void drawPet(Canvas c,float cx,float cy,float baseS){
        float s=baseS;if(state.stage==GameState.STAGE_PICHU)s*=.82f;else if(state.stage==GameState.STAGE_RAICHU)s*=1.08f;if(state.megaActive)s*=1.06f;
        int yellow=Color.rgb(255,216,72),orange=Color.rgb(236,165,78),body=state.stage==GameState.STAGE_RAICHU?orange:yellow;
        if(state.megaActive&&state.megaChoice==1)body=Color.rgb(231,165,84);if(state.megaActive&&state.megaChoice==2)body=Color.rgb(244,181,76);
        if(state.megaActive){p.setShader(new RadialGradient(cx,cy,170*s,state.megaChoice==1?Color.argb(85,70,155,245):Color.argb(90,255,214,70),Color.TRANSPARENT,Shader.TileMode.CLAMP));c.drawCircle(cx,cy+15*s,165*s,p);p.setShader(null);}
        p.setShadowLayer(13,0,7,Color.argb(70,83,62,42));p.setColor(darken(body,.88f));c.drawOval(new RectF(cx-92*s,cy+44*s,cx+92*s,cy+195*s),p);p.clearShadowLayer();p.setColor(body);c.drawOval(new RectF(cx-88*s,cy+37*s,cx+88*s,cy+188*s),p);
        drawEars(c,cx,cy,s,body);p.setShadowLayer(10,0,5,Color.argb(60,83,62,42));p.setColor(body);c.drawCircle(cx,cy,116*s,p);p.clearShadowLayer();
        p.setColor(Color.argb(70,255,255,255));c.drawOval(new RectF(cx-70*s,cy-82*s,cx-12*s,cy-24*s),p);c.drawOval(new RectF(cx-60*s,cy+60*s,cx+20*s,cy+107*s),p);
        if(state.stage==GameState.STAGE_PICHU)drawPichuCollar(c,cx,cy,s);drawFace(c,cx,cy,s);drawArmsFeet(c,cx,cy,s,body);drawTail(c,cx,cy,s);
        if(state.megaActive){text.setTextSize(32*s);text.setColor(state.megaChoice==1?Color.rgb(63,120,196):Color.rgb(196,138,27));c.drawText(state.megaChoice==1?"X":"Y",cx,cy-132*s,text);}
    }

    private void drawEars(Canvas c,float cx,float cy,float s,int body){
        float sway=currentEarSway*11*s;
        p.setColor(body);
        if(state.stage==GameState.STAGE_RAICHU){
            c.drawOval(new RectF(cx-115*s+sway*.25f,cy-114*s,cx-42*s+sway*.25f,cy-34*s),p);
            c.drawOval(new RectF(cx+42*s+sway*.25f,cy-114*s,cx+115*s+sway*.25f,cy-34*s),p);
            p.setColor(Color.rgb(95,70,43));
            c.drawCircle(cx-79*s+sway*.25f,cy-75*s,18*s,p); c.drawCircle(cx+79*s+sway*.25f,cy-75*s,18*s,p);
        } else {
            Path left=new Path(); left.moveTo(cx-92*s,cy-94*s); left.lineTo(cx-74*s+sway,cy-206*s); left.lineTo(cx-25*s,cy-110*s); left.close(); c.drawPath(left,p);
            Path right=new Path(); right.moveTo(cx+92*s,cy-94*s); right.lineTo(cx+74*s+sway,cy-206*s); right.lineTo(cx+25*s,cy-110*s); right.close(); c.drawPath(right,p);
            p.setColor(Color.rgb(74,56,40)); float tip=state.stage==GameState.STAGE_PICHU?68:44;
            Path lt=new Path(); lt.moveTo(cx-74*s+sway,cy-206*s); lt.lineTo(cx-59*s+sway*.72f,cy-(206-tip)*s); lt.lineTo(cx-76*s+sway*.8f,cy-(194-tip)*s); lt.close(); c.drawPath(lt,p);
            Path rt=new Path(); rt.moveTo(cx+74*s+sway,cy-206*s); rt.lineTo(cx+59*s+sway*.72f,cy-(206-tip)*s); rt.lineTo(cx+76*s+sway*.8f,cy-(194-tip)*s); rt.close(); c.drawPath(rt,p);
        }
    }

    private void drawPichuCollar(Canvas c,float cx,float cy,float s){p.setColor(Color.rgb(76,58,42));Path collar=new Path();collar.moveTo(cx-68*s,cy+80*s);collar.lineTo(cx-42*s,cy+106*s);collar.lineTo(cx-14*s,cy+83*s);collar.lineTo(cx+16*s,cy+108*s);collar.lineTo(cx+45*s,cy+83*s);collar.lineTo(cx+70*s,cy+108*s);collar.lineTo(cx+63*s,cy+126*s);collar.lineTo(cx-61*s,cy+126*s);collar.close();c.drawPath(collar,p);}

    private void drawFace(Canvas c,float cx,float cy,float s){
        boolean sleepy=state.energy<20||System.currentTimeMillis()<sleepUntil,happy=state.happy>=78;
        p.setColor(state.stage==GameState.STAGE_RAICHU?Color.rgb(245,203,90):Color.rgb(242,92,78));c.drawCircle(cx-79*s,cy+29*s,21*s,p);c.drawCircle(cx+79*s,cy+29*s,21*s,p);p.setColor(Color.argb(100,255,255,255));c.drawCircle(cx-85*s,cy+22*s,6*s,p);c.drawCircle(cx+73*s,cy+22*s,6*s,p);
        p.setColor(Color.rgb(47,39,33));if(sleepy){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5*s);c.drawArc(new RectF(cx-53*s,cy-22*s,cx-20*s,cy+4*s),20,140,false,p);c.drawArc(new RectF(cx+20*s,cy-22*s,cx+53*s,cy+4*s),20,140,false,p);p.setStyle(Paint.Style.FILL);}else{c.drawOval(new RectF(cx-50*s,cy-37*s,cx-24*s,cy+7*s),p);c.drawOval(new RectF(cx+24*s,cy-37*s,cx+50*s,cy+7*s),p);p.setColor(Color.WHITE);c.drawCircle(cx-36*s,cy-25*s,6*s,p);c.drawCircle(cx+36*s,cy-25*s,6*s,p);}
        p.setColor(Color.rgb(75,52,37));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5*s);if(happy)c.drawArc(new RectF(cx-35*s,cy+6*s,cx+35*s,cy+51*s),4,172,false,p);else{c.drawArc(new RectF(cx-31*s,cy+7*s,cx,cy+40*s),10,120,false,p);c.drawArc(new RectF(cx,cy+7*s,cx+31*s,cy+40*s),50,120,false,p);}p.setStyle(Paint.Style.FILL);
    }

    private void drawArmsFeet(Canvas c,float cx,float cy,float s,int body){
        float swing=currentWalkSwing*18*s;
        p.setColor(darken(body,.96f));
        c.drawOval(new RectF(cx-110*s,cy+74*s+swing*.35f,cx-62*s,cy+128*s+swing*.35f),p);
        c.drawOval(new RectF(cx+62*s,cy+74*s-swing*.35f,cx+110*s,cy+128*s-swing*.35f),p);
        p.setColor(darken(body,.88f));
        c.drawOval(new RectF(cx-77*s+swing*.32f,cy+168*s,cx-12*s+swing*.32f,cy+202*s),p);
        c.drawOval(new RectF(cx+12*s-swing*.32f,cy+168*s,cx+77*s-swing*.32f,cy+202*s),p);
    }

    private void drawTail(Canvas c,float cx,float cy,float s){
        float sway=currentTailSway*16*s;
        if(state.stage==GameState.STAGE_RAICHU){
            p.setColor(Color.rgb(103,77,44)); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(8*s);
            Path stem=new Path(); stem.moveTo(cx+75*s,cy+116*s); stem.cubicTo(cx+136*s,cy+122*s,cx+138*s+sway*.4f,cy+57*s,cx+166*s+sway,cy+48*s); c.drawPath(stem,p); p.setStyle(Paint.Style.FILL);
            p.setColor(state.megaActive&&state.megaChoice==1?Color.rgb(94,140,190):Color.rgb(242,188,55));
            Path end=new Path(); end.moveTo(cx+146*s+sway,cy+35*s); end.lineTo(cx+185*s+sway,cy+12*s); end.lineTo(cx+176*s+sway,cy+51*s); end.lineTo(cx+206*s+sway,cy+56*s); end.lineTo(cx+166*s+sway,cy+92*s); end.lineTo(cx+160*s+sway,cy+61*s); end.close(); c.drawPath(end,p);
        } else {
            p.setColor(Color.rgb(242,188,55));
            Path tail=new Path(); tail.moveTo(cx+83*s,cy+106*s); tail.lineTo(cx+151*s+sway*.45f,cy+66*s); tail.lineTo(cx+126*s+sway*.65f,cy+126*s); tail.lineTo(cx+186*s+sway,cy+111*s); tail.lineTo(cx+131*s+sway*.6f,cy+190*s); tail.lineTo(cx+83*s,cy+166*s); tail.close(); c.drawPath(tail,p);
        }
    }

    private int darken(int color,float factor){int r=Math.round(Color.red(color)*factor),g=Math.round(Color.green(color)*factor),b=Math.round(Color.blue(color)*factor);return Color.rgb(Math.max(0,r),Math.max(0,g),Math.max(0,b));}
    private boolean near(float x1,float y1,float x2,float y2,float d){return Math.hypot(x1-x2,y1-y2)<=d;}
    private boolean isOnPet(float x,float y){float cx=getWidth()/2f+walkX,cy=getHeight()*.52f,r=Math.min(getWidth(),getHeight())*.29f;if(state!=null&&state.stage==GameState.STAGE_EGG)r*=.65f;return near(x,y,cx,cy,r);}
    private int foodAt(float x,float y){int w=getWidth();float homeY=getHeight()*.835f;float hit=foodCardSize(w)*.62f;for(int i=0;i<3;i++)if(near(x,y,foodHomeX(i,w),homeY,hit))return i;return -1;}

    @Override public boolean onTouchEvent(MotionEvent e){
        if(System.currentTimeMillis()<evolutionUntil)return true;
        getParent().requestDisallowInterceptTouchEvent(true);
        float petCx=getWidth()/2f+walkX,petCy=getHeight()*.52f;
        if(mode==MODE_FEED){switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:selectedFood=foodAt(e.getX(),e.getY());if(selectedFood>=0){draggingFood=true;dragX=e.getX();dragY=e.getY();invalidate();}return true;
            case MotionEvent.ACTION_MOVE:if(draggingFood){dragX=e.getX();dragY=e.getY();invalidate();}return true;
            case MotionEvent.ACTION_UP:if(draggingFood&&selectedFood>=0){int food=selectedFood;boolean fed=near(e.getX(),e.getY(),petCx,petCy+dp(6),Math.max(dp(86),Math.min(getWidth(),getHeight())*.24f));draggingFood=false;selectedFood=-1;if(fed){mode=MODE_NORMAL;react(REACT_EAT,720,e.getX()<petCx?-1f:1f);happySpark();if(listener!=null)listener.onFoodFed(food);}invalidate();}return true;
            default:return true;}}
        if(mode==MODE_BALL){switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:if(near(e.getX(),e.getY(),ballX,ballY,ballRadius(getWidth())*1.35f)){draggingBall=true;ballStartX=e.getX();ballStartY=e.getY();}return true;
            case MotionEvent.ACTION_MOVE:if(draggingBall){ballX=e.getX();ballY=e.getY();invalidate();}return true;
            case MotionEvent.ACTION_UP:if(draggingBall){float dist=(float)Math.hypot(e.getX()-ballStartX,e.getY()-ballStartY);draggingBall=false;if(dist>60){float target=Math.max(-getWidth()*.20f,Math.min(getWidth()*.20f,e.getX()-getWidth()/2f));walkTarget=target;react(REACT_CHASE,780,target<walkX?-1f:1f);mode=MODE_NORMAL;postDelayed(()->{walkTarget=0;happySpark();if(listener!=null)listener.onBallPlayed();},720);}invalidate();}return true;
            default:return true;}}
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:downX=e.getX();downY=e.getY();downTime=System.currentTimeMillis();moved=false;downOnPet=isOnPet(downX,downY);return true;
            case MotionEvent.ACTION_MOVE:if(Math.abs(e.getX()-downX)>32||Math.abs(e.getY()-downY)>32)moved=true;return true;
            case MotionEvent.ACTION_UP:long duration=System.currentTimeMillis()-downTime;float distance=(float)Math.hypot(e.getX()-downX,e.getY()-downY);if(mode==MODE_BATH){if(downOnPet&&(moved||distance>35)){bathStrokes++;if(bathStrokes>=3){mode=MODE_NORMAL;react(REACT_SHAKE,760,1f);happySpark();if(listener!=null)listener.onBathComplete();}invalidate();}return true;}if(!downOnPet)return true;happySpark();if(duration>=620&&distance<60){react(REACT_HUG,780,1f);if(listener!=null)listener.onHug();}else if(moved||distance>52){react(REACT_PET,560,e.getX()<downX?-1f:1f);if(listener!=null)listener.onPetSwipe();}else{react(REACT_PAT,430,1f);if(listener!=null)listener.onHeadPat();performClick();}return true;
            default:return true;}
    }

    @Override public boolean performClick(){super.performClick();return true;}
}
