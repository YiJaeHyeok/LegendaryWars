package com.legendary.offline;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.*;
import org.json.*;

/** All interaction and rendering are local. No WebView, scripts or remote resources. */
public final class WarView extends View {
    private static final int HOME=0,MAP=1,ARMORY=2,BATTLE=3,CODEX=4,HELP=5,LOADOUT=6,OPTIONS=7,MODES=8;
    private static final int GOLD=0xffe7c478,IVORY=0xfff5ecd5,INK=0xff102225,MUTED=0xffb4c2b3;
    private static final String[] REGIONS={"Grasslands","Frostburn","Desert","Swamp","Volcano","Netherworld","Skyhaven"};
    private static final String[] SCENES={"Grass","Snow","Desert","Swamp","Volcano","Netherworld","Skyhaven"};
    private static final int[] GROUND={0xff59784a,0xff718b9e,0xffb49a63,0xff526b60,0xff705348};
    public final Progress progress;
    private final MainActivity activity;private final GameData data;private final Artwork art;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private Typeface serif=Typeface.create("serif",Typeface.BOLD),sans=Typeface.create("sans-serif",Typeface.NORMAL);
    private final ArrayList<Button> buttons=new ArrayList<>();
    private final RectF box=new RectF();
    private BattleEngine battle;
    private int screen,region,selectedUnit,selectedLane=1,codexTier=1,reward,pressed=-1,speed=1;
    private boolean active=true,music=true;
    private float scale=1,offsetX,offsetY;private long lastNanos;private double clock;
    private String notice="";private double noticeUntil;
    private int homeStage,pendingStage,lastMusicScreen=-1;private final LinkedHashSet<Integer> loadout=new LinkedHashSet<>();private boolean spellBook,dragging;private float downX,downY,lastDragX;

    public WarView(MainActivity activity,GameData data,Progress progress){
        super(activity);this.activity=activity;this.data=data;this.progress=progress;art=new Artwork(activity.getAssets(),data);
        setFocusable(true);setFocusableInTouchMode(true);setContentDescription("Legendary Wars 오프라인 복원 게임");
        try{serif=Typeface.createFromAsset(activity.getAssets(),"fonts/Livingst.ttf");sans=Typeface.createFromAsset(activity.getAssets(),"fonts/Museo-700.ttf");}catch(RuntimeException ignored){}
    }
    public void setMusic(boolean on){music=on;activity.music(on);}
    public void resumeDrawing(){active=true;lastNanos=0;postInvalidateOnAnimation();}
    public void pauseDrawing(){active=false;lastNanos=0;if(battle!=null&&battle.state==BattleEngine.PLAYING)battle.paused=true;}
    public void release(){active=false;art.clear();}
    public void back(){if(screen==BATTLE&&battle!=null&&battle.state==BattleEngine.PLAYING){battle.paused=!battle.paused;}else if(screen!=HOME)screen=HOME;else activity.finish();invalidate();}
    private void message(String s){notice=s;noticeUntil=clock+2.2;}
    private int tier(int unit){return Progress.hero(unit)?Math.min(data.units.get(unit).optJSONArray("variants").length(),progress.gear[unit]):1;}
    private boolean heroUnlocked(int type){UnitSpec s=data.specs.get(type);return progress.highestUnlocked()>=s.unlock&&(s.moonstonePrice==0||progress.purchased[type]);}
    private void begin(int stage){
        if(stage<0||stage>progress.highestUnlocked()||stage>=data.plans.size())return;
        pendingStage=stage;BattlePlan plan=data.plans.get(stage);
        if(plan.tutorial()||plan.slots==0){startBattle(stage);return;}
        loadout.removeIf(t->!heroUnlocked(t));if(loadout.isEmpty())for(int type:data.heroTypes)if(heroUnlocked(type)&&loadout.size()<plan.slots)loadout.add(type);
        while(loadout.size()>plan.slots)loadout.remove(loadout.iterator().next());screen=LOADOUT;
    }
    private void startBattle(int stage){
        int[] selected=loadout.stream().mapToInt(Integer::intValue).toArray();
        battle=new OriginalBattle(data.specs,stage,progress,data.plans.get(stage),selected);screen=BATTLE;selectedLane=1;reward=0;speed=1;lastNanos=0;spellBook=false;
        setContentDescription("전투 "+(stage+1)+", 화면의 세 레인 선택 후 아래 유닛을 누르세요");
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);long now=System.nanoTime();double dt=lastNanos==0?0:Math.min(.05,(now-lastNanos)/1e9);lastNanos=now;
        if(active){clock+=dt;if(screen==BATTLE&&battle!=null){for(int i=0;i<speed;i++)battle.update(dt);if(battle.state==BattleEngine.VICTORY){int gain=battle.claimReward();if(gain>0){reward=gain;activity.save(progress);}}}}
        float w=getWidth(),h=getHeight();scale=Math.min(w/1280f,h/720f);offsetX=(w-1280*scale)/2;offsetY=(h-720*scale)/2;
        canvas.drawColor(0xff0b1419);canvas.save();canvas.translate(offsetX,offsetY);canvas.scale(scale,scale);canvas.clipRect(0,0,1280,720);buttons.clear();
        if(lastMusicScreen!=screen){String track=screen==HOME||screen==OPTIONS||screen==MODES?"TitleScreen":screen==ARMORY?"Armory":screen==BATTLE?new String[]{"GrassLevel","SnowLevel","DesertLevel","SwampLevel","VolcanoLevel","Netherworld_Level","SkyHaven"}[data.plans.get(battle.stage).region]:"Overworld";activity.musicTrack(data.json.optJSONObject("music").optString(track));lastMusicScreen=screen;}
        switch(screen){case HOME:originalHome(canvas);break;case MAP:sourceMap(canvas);break;case ARMORY:originalArmory(canvas);break;case BATTLE:originalBattle(canvas);break;case CODEX:originalCodex(canvas);break;case LOADOUT:loadout(canvas);break;case OPTIONS:options(canvas);break;case MODES:modes(canvas);break;default:help(canvas);}
        if(clock<noticeUntil){panel(canvas,360,105,560,52,0xf21a2e30,GOLD);text(canvas,notice,640,138,20,IVORY,true,false);}
        canvas.restore();if(active)postInvalidateOnAnimation();
    }
    private void rect(Canvas c,float x,float y,float w,float h,int color){p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawRect(x,y,x+w,y+h,p);}
    private void panel(Canvas c,float x,float y,float w,float h,int fill,int stroke){
        p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(fill);box.set(x,y,x+w,y+h);c.drawRoundRect(box,14,14,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);p.setColor(stroke);c.drawRoundRect(box,14,14,p);p.setStyle(Paint.Style.FILL);
    }
    private void text(Canvas c,String s,float x,float y,float size,int color,boolean center,boolean heading){
        p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextSize(size);p.setTypeface(heading?serif:sans);p.setTextAlign(center?Paint.Align.CENTER:Paint.Align.LEFT);c.drawText(s,x,y,p);
    }
    private void line(Canvas c,float x,float y,float xx,float yy,int color,float width){p.setShader(null);p.setColor(color);p.setStrokeWidth(width);c.drawLine(x,y,xx,yy,p);}
    private void button(Canvas c,String label,float x,float y,float w,float h,int action,int param,boolean enabled,boolean primary){
        int id=action*100+param+20;boolean down=pressed==id;
        int fill=enabled?(primary?(down?0xff8b6e3d:0xffb58c46):(down?0xff34483e:0xed1b3031)):0xc5202b2b;
        panel(c,x,y,w,h,fill,enabled?(primary?0xfff2d99b:0xff728574):0xff52625b);
        if(enabled)art.draw(c,"scrollnode-vertical-spritesheet-hd",down?"scrollnode-vertical-button-selected2-blue.png":"scrollnode-vertical-button-normal2-blue.png",x,y,w,h);
        text(c,label,x+w/2,y+h/2+7,h<46?17:21,enabled?GOLD:0xff8a968d,true,false);
        if(enabled)buttons.add(new Button(id,x,y,w,h,action,param));
    }
    private void header(Canvas c,String title,String subtitle){
        rect(c,0,0,1280,94,0xf20e2025);line(c,0,93,1280,93,0xff9c8b58,1);
        button(c,"‹  돌아가기",28,23,126,47,1,HOME,true,false);
        text(c,title,184,46,30,IVORY,false,true);text(c,subtitle,185,73,16,MUTED,false,false);
        art.fit(c,"mainmenu-foreground-hd","main-menu-gems-diamond.png",1076,27,42,38);
        text(c,String.valueOf(progress.gems),1132,56,26,GOLD,false,false);
    }
    private void home(Canvas c){
        art.draw(c,"mainmenu-background-hd","mainmenu-sky2.png",0,0,1280,720);
        art.draw(c,"mainmenu-background-hd","mainmenu-background-hills.png",0,330,1280,300);
        p.setShader(new LinearGradient(0,350,0,720,new int[]{0x00112224,0xff101f24},null,Shader.TileMode.CLAMP));c.drawRect(0,0,1280,720,p);p.setShader(null);
        art.fit(c,"mainmenu-background-hd","mainmenu-title-logobanner.png",70,80,720,240);
        art.fit(c,"mainmenu-background-hd","mainmenu-title-helmet.png",340,25,140,125);
        text(c,"레전다리아의 전설을 다시",430,342,27,IVORY,true,true);
        text(c,"성을 지키고, 영웅을 모아 어둠을 몰아내세요.",430,382,20,0xffd3dbcb,true,false);
        int[] heroes={0,1,2,4,3};
        for(int i=0;i<heroes.length;i++){int unit=heroes[i];float x=130+i*140;art.actor(c,unit,tier(unit),"ready",clock+i*.3,x,635,unit==4?.92f:1.12f,false);}
        art.draw(c,"mainmenu-background-hd","mode-grass-foreground.png",0,645,1280,95);
        panel(c,850,173,360,421,0xd9182a2e,0xffa5905c);
        text(c,"모험은 계속된다",1030,223,28,GOLD,true,true);
        int cleared=0;for(int n:progress.stars)if(n>0)cleared++;
        text(c,"캠페인  "+cleared+" / 50",1030,255,18,MUTED,true,false);
        button(c,"모험 시작",880,285,300,68,1,MAP,true,true);
        button(c,"영웅의 무기고",880,370,300,58,1,ARMORY,true,false);
        button(c,"유닛 도감",880,445,300,58,1,CODEX,true,false);
        button(c,"플레이 방법",880,521,176,46,1,HELP,true,false);
        button(c,music?"음악 켬":"음악 끔",1067,521,113,46,60,0,true,false);
        text(c,"LEGENDARY WARS  ·  OFFLINE",40,690,16,0xffd1d7be,false,false);
        text(c,"Original artwork · Reconstructed gameplay",990,690,14,0xffa8b9ac,true,false);
    }
    private void worldMap(Canvas c){
        for(int row=0;row<4;row++)for(int col=0;col<4;col++){
            String atlas=col==3?"worldmapsheet3-hd":row<2?"worldmapsheet1":"worldmapsheet2";
            art.draw(c,atlas,"wmap"+(row+1)+"x"+(col+1)+".png",col*320,row*210-70,321,211);
        }
        rect(c,0,0,1280,720,0x55112420);
    }
    private void map(Canvas c){
        worldMap(c);header(c,"레전다리아 지도","5개 지역 · 50개 전투");
        for(int i=0;i<5;i++)button(c,REGIONS[i],48+i*240,113,224,50,10,i,true,region==i);
        panel(c,104,196,1072,369,0xc4142a2b,0xffa99564);
        text(c,REGIONS[region],640,237,29,GOLD,true,true);
        for(int i=0;i<9;i++){
            float[] a=node(i),b=node(i+1);line(c,a[0],a[1],b[0],b[1],0xff526c58,6);
        }
        for(int i=0;i<10;i++){
            int stage=region*10+i;float[] pos=node(i);boolean open=stage<=progress.highestUnlocked();
            p.setStyle(Paint.Style.FILL);p.setColor(open?0xffe3c781:0xff435950);c.drawCircle(pos[0],pos[1],34,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(open?0xffffe5a8:0xff849183);c.drawCircle(pos[0],pos[1],38,p);p.setStyle(Paint.Style.FILL);
            text(c,open?String.format(Locale.US,"%02d",stage+1):"•",pos[0],pos[1]+9,25,open?INK:0xff9ca797,true,true);
            String stars=progress.stars[stage]==0?(open?"출정 가능":"잠김"):repeat("★",progress.stars[stage]);
            text(c,stars,pos[0],pos[1]+65,17,open?GOLD:0xff9da99f,true,false);
            if(open)buttons.add(new Button(2020+stage,pos[0]-43,pos[1]-43,86,120,20,stage));
        }
        button(c,"계속하기  ·  "+(progress.highestUnlocked()+1)+"번째 전투",380,609,520,66,20,progress.highestUnlocked(),true,true);
        text(c,"한 전투를 완료하면 다음 스테이지와 새로운 영웅이 열립니다.",640,706,17,IVORY,true,false);
    }
    private float[] node(int i){int col=i<5?i:9-i;return new float[]{220+col*210,i<5?317+(col%2)*12:466-(col%2)*12};}
    private static String repeat(String s,int count){StringBuilder b=new StringBuilder();for(int i=0;i<count;i++)b.append(s);return b.toString();}
    private void armory(Canvas c){
        art.draw(c,"mainmenu-background-hd","mainmenu-sky2.png",0,0,1280,720);rect(c,0,0,1280,720,0xbe102522);
        header(c,"영웅의 무기고","영구 강화 · 능력치와 장비 외형 성장");
        for(int i=0;i<7;i++){
            float x=48+i*170;boolean selected=selectedUnit==i,open=progress.highestUnlocked()>=data.specs.get(i).unlock;
            panel(c,x,121,159,112,selected?0xff405b46:0xc51b3031,selected?GOLD:0xff627361);
            art.icon(c,i,tier(i),x+8,130,60,60);text(c,data.label(i),x+83,158,18,open?IVORY:MUTED,true,false);
            text(c,open?"강화 "+progress.ranks[i]:"전투 "+(data.specs.get(i).unlock+1),x+83,191,16,open?GOLD:MUTED,true,false);
            buttons.add(new Button(3020+i,x,121,159,112,30,i));
        }
        button(c,"성벽 강화",49,262,159,51,30,7,true,selectedUnit==7);
        button(c,"채굴 강화",49,327,159,51,30,8,true,selectedUnit==8);
        panel(c,245,262,935,392,0xe6192c2e,0xff867c50);
        if(selectedUnit<7){
            UnitSpec s=data.specs.get(selectedUnit);int rank=progress.ranks[selectedUnit];
            art.actor(c,selectedUnit,tier(selectedUnit),"ready",clock,455,600,1.8f,false);
            text(c,data.label(selectedUnit),690,318,34,GOLD,false,true);
            text(c,"장비 단계 "+tier(selectedUnit)+" / 4  ·  강화 "+rank+" / 12",690,353,20,MUTED,false,false);
            text(c,"체력     "+(int)(s.hp*(1+rank*.13)),690,402,23,IVORY,false,false);
            text(c,"공격력   "+(int)(s.attack*(1+rank*.12)),690,443,23,IVORY,false,false);
            text(c,"소환 비용   "+s.cost+" 보석",690,484,21,IVORY,false,false);
            boolean open=progress.highestUnlocked()>=s.unlock;
            button(c,open?(rank>=12?"최대 강화":"강화하기  ·  "+progress.upgradeCost(selectedUnit)):"전투 "+(s.unlock+1)+"에서 해금",690,541,386,65,31,selectedUnit,open&&rank<12&&progress.gems>=progress.upgradeCost(selectedUnit),true);
            text(c,"3회 강화마다 원작의 다음 장비 외형으로 성장합니다.",690,630,16,MUTED,false,false);
        }else{
            boolean castle=selectedUnit==7;int rank=castle?progress.castleRank:progress.mineRank;
            if(castle)art.fit(c,"castle-default-spritesheet-hd","goodcastle.png",292,325,320,278);else art.actor(c,7,1,"special",clock,450,590,1.8f,false);
            text(c,castle?"레전다리아의 성벽":"광부의 작업장",690,329,32,GOLD,false,true);
            text(c,"강화 "+rank+" / 12",690,373,22,MUTED,false,false);
            text(c,castle?"성 체력  "+(1000+rank*180):"한 번에 채굴  "+(7+rank)+" 보석",690,429,23,IVORY,false,false);
            text(c,castle?"다음 강화: 체력 +180":"다음 강화: 시작 보석 +8, 채굴량 +1",690,477,19,IVORY,false,false);
            button(c,rank>=12?"최대 강화":"강화하기  ·  "+progress.upgradeCost(selectedUnit),690,541,386,65,31,selectedUnit,rank<12&&progress.gems>=progress.upgradeCost(selectedUnit),true);
        }
    }
    private void scenery(Canvas c,int r){
        int top=r==0?0xff93b3a2:r==1?0xffadc7d3:r==2?0xffd9b87e:r==3?0xff697b79:0xffa76c59;
        p.setShader(new LinearGradient(0,85,0,520,top,GROUND[r],Shader.TileMode.CLAMP));c.drawRect(0,80,1280,545,p);p.setShader(null);
        String atlas=SCENES[r]+"Scene-hd",prefix="Level"+(r+1),far=prefix+(r<2?"-L4":"-L5");
        art.draw(c,atlas,far+"L.png",0,94,640,320);art.draw(c,atlas,far+"R.png",640,94,640,320);
        String ground=prefix+(r==4?"-Layer4.png":"-Layer3.png");
        art.draw(c,atlas,ground,0,268,1280,270);
        rect(c,0,284,1280,246,(GROUND[r]&0x00ffffff)|0x66000000);
        for(int lane=0;lane<3;lane++){
            float y=laneY(lane);if(lane==selectedLane){rect(c,115,y-59,1055,76,0x22fff2a8);line(c,117,y+17,1165,y+17,0x55f9e5a0,1);}
        }
    }
    private float laneY(int lane){return 327+lane*85;}
    private void battle(Canvas c){
        if(battle==null){screen=MAP;return;}int r=battle.stage/10;scenery(c,r);
        String castle="castle-"+SCENES[r].toLowerCase(Locale.US)+"-spritesheet-hd";
        if(!art.draw(c,castle,"goodcastle.png",-12,324,230,184))art.draw(c,"castle-default-spritesheet-hd","goodcastle.png",-12,324,230,184);
        if(!art.draw(c,castle,"evilcastle.png",1065,320,230,184))art.draw(c,"castle-default-spritesheet-hd","evilcastle.png",1065,320,230,184);
        for(int lane=0;lane<3;lane++){
            if(!battle.paused&&battle.state==BattleEngine.PLAYING)buttons.add(new Button(4120+lane,205,laneY(lane)-60,875,79,41,lane));
            for(BattleEngine.Actor a:battle.actors){
                if(a.lane!=lane)continue;String state=a.hp<=0?"death":a.attacking?"primary":a.moving?"walk":"ready";
                float sc=a.boss?.88f:.68f;art.actor(c,a.type,a.enemy?1:tier(a.type),state,a.animationAge,(float)a.x,laneY(lane),sc,!a.enemy&&battle.order<0&&a.moving);
                if(a.hp>0&&(a.hp<a.maxHp||a.flash>0||a.boss))bar(c,(float)a.x-24,laneY(lane)-93,48,5,a.hp/a.maxHp,a.enemy?0xffd17d69:0xffa8d77d);
                if(a.boss&&a.hp>0)text(c,"BOSS",(float)a.x,laneY(lane)-107,12,GOLD,true,false);
            }
        }
        for(BattleEngine.Shot s:battle.shots){float f=(float)Math.min(1,s.age/.38),x=(float)(s.from+(s.to-s.from)*f),y=laneY(s.lane)-49;
            p.setColor(s.type==1||s.type==14?0xffe7dbb1:s.enemy?0xffc681e4:0xffffd97c);c.drawCircle(x,y,s.type==1?3:6,p);
        }
        for(int i=0;i<battle.miners;i++)art.actor(c,7,1,((int)(battle.elapsed/2+i)%2)==0?"walk":"special",battle.elapsed+i,145+i*26+(float)Math.sin(battle.elapsed)*15,524,.53f,false);
        for(BattleEngine.Gem g:battle.gemsOnField){float y=485-(float)Math.sin(g.age*5)*6;art.fit(c,"mainmenu-foreground-hd","main-menu-gems-diamond.png",(float)g.x,y,34,29);}
        if(!battle.gemsOnField.isEmpty())buttons.add(new Button(6420,105,453,185,80,64,0));
        if(battle.spellFlash>0)rect(c,0,94,1280,440,((int)(battle.spellFlash*90)<<24)|0xffd277);
        rect(c,0,0,1280,91,0xf3102428);line(c,0,91,1280,91,0xffac9964,1);
        button(c,"Ⅱ",24,20,62,49,45,0,true,false);button(c,speed+"×",100,20,63,49,61,0,true,false);
        text(c,REGIONS[r]+"  "+(battle.stage%10+1)+" / 10",205,45,26,IVORY,false,true);
        text(c,"적 진군  "+battle.wave+" / "+battle.totalWaves()+"    처치 "+battle.kills,205,73,16,MUTED,false,false);
        text(c,(int)(battle.elapsed/60)+":"+String.format(Locale.US,"%02d",(int)battle.elapsed%60),646,49,25,GOLD,true,false);
        art.fit(c,"mainmenu-foreground-hd","main-menu-gems-diamond.png",817,20,43,42);text(c,String.valueOf(battle.gems),879,51,31,GOLD,false,true);
        text(c,"정수  "+battle.essence,1040,50,25,0xffa6d8dc,false,false);
        bar(c,36,108,255,10,battle.playerCastle/battle.maxPlayerCastle,0xffa6d080);bar(c,990,108,255,10,battle.enemyCastle/battle.maxEnemyCastle,0xffcf8975);
        text(c,"우리 성 "+(int)battle.playerCastle,38,140,16,IVORY,false,false);text(c,"적 성 "+(int)battle.enemyCastle,1000,140,16,IVORY,false,false);
        if(battle.elapsed<13&&battle.stage==0){panel(c,350,153,580,52,0xd514292c,0xff849274);text(c,"레인을 누르고 기사를 소환하세요. 광부가 보석을 모읍니다.",640,186,18,IVORY,true,false);}
        battleControls(c);
        if(battle.state!=BattleEngine.PLAYING)result(c);else if(battle.paused)pause(c);
    }
    private void bar(Canvas c,float x,float y,float w,float h,double value,int color){rect(c,x,y,w,h,0xcc142322);rect(c,x+1,y+1,(w-2)*(float)Math.max(0,Math.min(1,value)),h-2,color);}
    private void battleControls(Canvas c){
        rect(c,0,538,1280,182,0xfa12282b);line(c,0,538,1280,538,0xffa7935f,2);
        boolean running=battle.state==BattleEngine.PLAYING&&!battle.paused;
        button(c,"후퇴",25,549,90,38,42,-1,running,battle.order==-1);button(c,"대기",124,549,90,38,42,0,running,battle.order==0);button(c,"전진",223,549,90,38,42,1,running,battle.order==1);
        text(c,"선택 레인 "+(selectedLane+1),358,576,18,GOLD,false,false);
        button(c,"화염 25",669,549,167,38,43,0,running&&battle.essence>=25&&battle.spellCooldowns[0]<=0,false);
        button(c,"치유 20",848,549,167,38,43,1,running&&battle.essence>=20&&battle.spellCooldowns[1]<=0,false);
        button(c,"빙결 30",1027,549,167,38,43,2,running&&battle.essence>=30&&battle.spellCooldowns[2]<=0,false);
        int minerCost=15+battle.miners*10;
        panel(c,22,600,124,107,0xff2d453c,0xff7d8d68);art.icon(c,7,1,48,604,64,60);text(c,"광부 "+battle.miners+"/4",84,679,17,IVORY,true,false);text(c,battle.miners>=4?"최대":minerCost+" 보석",84,701,15,GOLD,true,false);
        if(running&&battle.miners<4&&battle.gems>=minerCost)buttons.add(new Button(4420,22,600,124,107,44,0));
        for(int i=0;i<7;i++){
            float x=160+i*157;UnitSpec s=data.specs.get(i);boolean unlocked=battle.unlocked(i),ready=running&&unlocked&&battle.gems>=s.cost&&battle.cooldowns[i]<=0;
            panel(c,x,600,145,107,ready?0xff355242:0xff243736,ready?0xffc9c184:0xff667765);art.icon(c,i,tier(i),x+8,602,60,64);
            text(c,data.label(i),x+101,627,16,unlocked?IVORY:0xff98a69c,true,false);
            text(c,unlocked?s.cost+" 보석":"잠김",x+101,655,16,unlocked?GOLD:0xff98a69c,true,false);
            if(unlocked&&battle.cooldowns[i]>0){bar(c,x+12,683,120,7,1-battle.cooldowns[i]/Math.max(1.4,s.cooldown*.55),0xff84b8a4);text(c,String.format(Locale.US,"%.1fs",battle.cooldowns[i]),x+73,681,14,MUTED,true,false);}
            else text(c,unlocked?(ready?"소환":"보석 부족"):"전투 "+(s.unlock+1),x+73,696,14,ready?IVORY:MUTED,true,false);
            if(ready)buttons.add(new Button(4020+i,x,600,145,107,40,i));
        }
    }
    private void shade(Canvas c){rect(c,0,0,1280,720,0xaa081516);}
    private void pause(Canvas c){
        buttons.clear();shade(c);panel(c,410,192,460,347,0xfa193033,GOLD);
        text(c,"잠시 쉬어가기",640,251,32,GOLD,true,true);text(c,"레전다리아는 기다리고 있습니다.",640,292,19,MUTED,true,false);
        button(c,"전투 계속",460,324,360,61,45,0,true,true);button(c,"지도",460,402,170,55,1,MAP,true,false);button(c,music?"음악 끄기":"음악 켜기",650,402,170,55,60,0,true,false);
        button(c,"처음부터 재도전",460,476,360,42,46,0,true,false);
    }
    private void result(Canvas c){
        buttons.clear();shade(c);panel(c,342,142,596,449,0xfb193032,GOLD);boolean won=battle.state==BattleEngine.VICTORY;
        text(c,won?"승리!":"성을 잃었습니다",640,214,42,GOLD,true,true);
        int stars=won?progress.stars[battle.stage]:0;text(c,won?repeat("★",stars):"다시 군대를 정비해 보세요.",640,270,won?42:23,won?GOLD:MUTED,true,false);
        text(c,"처치 "+battle.kills+"    ·    "+(int)battle.elapsed+"초",640,329,22,IVORY,true,false);
        text(c,won?"획득 보석  +"+reward:"광부를 늘리고 세 레인을 함께 지켜보세요.",640,371,23,won?GOLD:IVORY,true,false);
        button(c,won&&battle.stage<data.plans.size()-1?"Next Battle":"Try Again",402,417,476,64,won&&battle.stage<data.plans.size()-1?47:46,0,true,true);
        button(c,"지도",402,503,226,54,1,MAP,true,false);button(c,"무기고",652,503,226,54,1,ARMORY,true,false);
    }
    private void codex(Canvas c){
        art.draw(c,"mainmenu-background-hd","mainmenu-sky2.png",0,0,1280,720);rect(c,0,0,1280,720,0xc0102426);header(c,"레전다리아 도감","원작 캐릭터 · 이동과 공격 애니메이션");
        for(int i=0;i<16;i++){
            float x=34+(i%8)*155,y=114+(i/8)*105;panel(c,x,y,143,93,i==selectedUnit?0xff456348:0xda1b3231,i==selectedUnit?GOLD:0xff6c7b64);
            art.icon(c,i,1,x+8,y+7,62,61);text(c,data.label(i),x+72,y+80,15,IVORY,true,false);buttons.add(new Button(3020+i,x,y,143,93,30,i));
        }
        panel(c,112,353,1055,324,0xe91a3032,0xff9f8d5b);
        UnitSpec s=data.specs.get(selectedUnit);boolean hero=selectedUnit<7;
        art.actor(c,selectedUnit,codexTier,"walk",clock,340,635,1.42f,false);
        art.actor(c,selectedUnit,codexTier,"primary",clock,560,635,1.42f,false);
        text(c,data.label(selectedUnit),775,410,34,GOLD,false,true);
        text(c,"체력 "+(int)s.hp+"    공격 "+(int)s.attack,775,455,23,IVORY,false,false);
        text(c,"이동 "+(int)s.speed+"    사거리 "+(int)s.range,775,494,21,MUTED,false,false);
        text(c,"이동",340,660,17,MUTED,true,false);text(c,"기본 공격",560,660,17,MUTED,true,false);
        if(hero)for(int i=1;i<=4;i++)button(c,"장비 "+i,775+(i-1)*88,551,78,51,62,i,true,codexTier==i);
        text(c,hero?"도감에서는 모든 장비 외형을 살펴볼 수 있습니다.":"적군 / 보조 유닛",775,636,16,MUTED,false,false);
    }
    private void help(Canvas c){
        rect(c,0,0,1280,720,0xff152a2c);header(c,"플레이 방법","원작 이미지로 다시 만든 오프라인 전투");
        String[] titles={"01  보석을 모으세요","02  세 레인을 지키세요","03  정수로 전세를 뒤집으세요","04  영웅을 성장시키세요"};
        String[][] lines={{"광부는 자동으로 보석을 모읍니다. 떨어진 보석을 누르면 바로 수집합니다.","전투 중 광부를 최대 네 명까지 늘릴 수 있습니다."},{"전장의 원하는 레인을 누르고, 아래 유닛을 눌러 소환하세요.","전진 · 대기 · 후퇴 명령으로 군대를 함께 지휘합니다."},{"적을 처치하면 정수가 쌓입니다. 화염 · 치유 · 빙결을 사용하세요.","적 성을 파괴하면 승리합니다. 우리 성을 잃으면 패배합니다."},{"승리 보석으로 무기고에서 영웅, 성벽, 채굴 능력을 강화하세요.","강화 세 단계마다 원작의 새로운 장비 이미지로 바뀝니다."}};
        for(int i=0;i<4;i++){float y=126+i*122;text(c,titles[i],84,y+23,25,GOLD,false,true);text(c,lines[i][0],84,y+57,20,IVORY,false,false);text(c,lines[i][1],84,y+87,19,MUTED,false,false);}
        text(c,"원작: Liv Games · 이미지/음원은 제공된 파일에서 추출 · 전투/캠페인 동작은 새로 구현했습니다.",84,670,17,MUTED,false,false);
        button(c,"모험 시작",1030,638,183,53,1,MAP,true,true);
    }
    private void scene(Canvas c,String name){
        rect(c,0,0,1280,720,0xffea8e44);JSONObject s=data.json.optJSONObject("scenes").optJSONObject(name);JSONArray commands=s.optJSONArray("draw");
        for(int i=0;i<commands.length();i++){JSONObject d=commands.optJSONObject(i);JSONArray r=d.optJSONArray("rect");art.draw(c,d.optString("atlas"),d.optString("frame"),(float)r.optDouble(0),(float)r.optDouble(1),(float)r.optDouble(2),(float)r.optDouble(3));}
    }
    private void originalHome(Canvas c){
        String name=homeStage==0?"title":"props";scene(c,name);
        JSONArray controls=data.json.optJSONObject("scenes").optJSONObject(name).optJSONArray("controls");
        for(int i=0;i<controls.length();i++){JSONObject b=controls.optJSONObject(i);JSONArray r=b.optJSONArray("rect");String id=b.optString("id");int action=id.equals("campaign")?1:id.equals("mode")?1:id.equals("options")?1:70,param=id.equals("campaign")?MAP:id.equals("mode")?MODES:id.equals("options")?OPTIONS:id.equals("title")?0:1;
            buttons.add(new Button(7000+i,(float)r.optDouble(0),(float)r.optDouble(1),(float)r.optDouble(2),(float)r.optDouble(3),action,param));
        }
        if(homeStage==0)text(c,"Tap to Continue",640,665,24,GOLD,true,true);
        else{button(c,"Options",25,22,134,45,1,OPTIONS,true,false);button(c,"Armory",25,81,134,45,1,ARMORY,true,false);}
    }
    private ArrayList<Integer> regionLevels(){ArrayList<Integer> ids=new ArrayList<>();for(int i=0;i<data.plans.size();i++)if(data.plans.get(i).region==region)ids.add(i);return ids;}
    private void sourceMap(Canvas c){
        worldMap(c);header(c,"Legendaria",REGIONS[region]);
        for(int i=0;i<7;i++)button(c,REGIONS[i],23+i*177,108,170,45,10,i,true,region==i);
        ArrayList<Integer> ids=regionLevels();JSONObject wm=data.json.optJSONObject("worldMap");JSONArray points=wm.optJSONArray("points");
        for(int k=0;k<ids.size();k++){
            int id=ids.get(k);BattlePlan plan=data.plans.get(id);float x=180+(k%6)*184,y=305+(k/6)*175;
            if(region<5){for(int j=0;j<points.length();j++){JSONObject point=points.optJSONObject(j);if(plan.id.equals(point.optString("id"))){JSONArray pos=point.optJSONArray("position");x=90+(float)pos.optDouble(0)/1924*1090;y=190+(1-(float)pos.optDouble(1)/1262)*375;break;}}}
            boolean open=id<=progress.highestUnlocked();String marker=progress.stars[id]>0?"herobanner.png":"monsterbanner"+Math.min(6,region+1)+".png";
            if(id==0)marker="Map_Grass_Good.png";art.fit(c,"worldmapsheet3-hd",marker,x-35,y-52,70,75);
            text(c,plan.region+1+"-"+plan.number,x,y+43,19,open?IVORY:0xff888b78,true,true);
            if(open)buttons.add(new Button(2020+id,x-42,y-56,84,120,20,id));
        }
        button(c,"Continue",444,633,392,63,20,Math.min(data.plans.size()-1,progress.highestUnlocked()),true,true);
        button(c,"Armory",48,633,220,63,1,ARMORY,true,false);
    }
    private void selectionIcon(Canvas c,int type,float x,float y,float w,float h){String id=data.specs.get(type).id;int t=tier(type);String frame="selection-icon-"+id+(t==1?"":t)+".png";if(art.frame("unit-selection-spritesheet-hd",frame)!=null)art.fit(c,"unit-selection-spritesheet-hd",frame,x,y,w,h);else art.icon(c,type,t,x,y,w,h);}
    private void loadout(Canvas c){
        scene(c,"props");shade(c);art.draw(c,"unit-selection-spritesheet-hd","unit-selection-background.png",250,18,780,680);
        text(c,"Choose your Heroes",640,86,32,GOLD,true,true);BattlePlan plan=data.plans.get(pendingStage);
        text(c,loadout.size()+" / "+plan.slots,640,126,24,INK,true,false);
        for(int i=0;i<data.heroTypes.length;i++){
            int type=data.heroTypes[i];float x=345+(i%4)*152,y=152+(i/4)*143;boolean open=heroUnlocked(type);
            if(loadout.contains(type))panel(c,x-5,y-5,115,126,0x554c8052,GOLD);
            selectionIcon(c,type,x,y,105,91);text(c,data.label(type),x+53,y+112,19,open?INK:0xff817666,true,false);
            if(!open)text(c,data.specs.get(type).moonstonePrice>0?data.specs.get(type).moonstonePrice+" Moonstone":"Level "+(data.specs.get(type).unlock+1),x+53,y+133,14,0xff6b5141,true,false);
            buttons.add(new Button(7120+type,x-5,y-5,120,140,71,type));
        }
        button(c,"Start Battle",415,612,450,68,72,pendingStage,!loadout.isEmpty(),true);button(c,"Back",42,30,145,50,1,MAP,true,false);
    }
    private void originalArmory(Canvas c){
        art.draw(c,"armory-spritesheet-hd","armorybackground.png",0,0,1280,720);rect(c,0,0,1280,720,0x77201c24);header(c,"The Armory",progress.moonstones+" Moonstones");
        if(!Progress.hero(selectedUnit))selectedUnit=0;
        for(int i=0;i<data.heroTypes.length;i++){int type=data.heroTypes[i];float x=39+(i%6)*202,y=113+(i/6)*91;selectionIcon(c,type,x,y,76,76);text(c,data.label(type),x+131,y+35,19,type==selectedUnit?GOLD:IVORY,true,false);text(c,heroUnlocked(type)?"Tier "+tier(type):"Locked",x+131,y+63,15,MUTED,true,false);buttons.add(new Button(3020+type,x,y,190,81,30,type));}
        panel(c,245,318,980,360,0xde243036,0xffb99c68);art.actor(c,selectedUnit,tier(selectedUnit),"ready",clock,425,619,1.7f,false);
        UnitSpec s=data.specs.get(selectedUnit);text(c,data.label(selectedUnit),681,364,33,GOLD,false,true);
        if(!heroUnlocked(selectedUnit)){
            int price=s.moonstonePrice;String label=price>0?"Unlock  ·  "+price+" Moonstone":"Unlocks after Level "+(s.unlock+1);
            button(c,label,681,425,444,65,75,selectedUnit,price>0&&progress.moonstones>=price,true);
        }else{
            String[] names={"Attack","Health","Defense"},keys={"attackPoints","hitPoints","defensePoints"};JSONObject options=data.units.get(selectedUnit).optJSONObject("upgrades");
            for(int i=0;i<3;i++){
                JSONObject option=options==null?null:options.optJSONObject(keys[i]);int base=option==null?40:option.optInt("baseCost",40),mult=option==null?2:option.optInt("costMultiplier",2);int cost=progress.statCost(selectedUnit,i,base,mult);
                float y=393+i*67;text(c,names[i]+"  "+progress.stats[selectedUnit][i]+" / 10",681,y+22,19,IVORY,false,false);
                button(c,"+  "+cost+" Gems",899,y-8,240,47,76,i,progress.stats[selectedUnit][i]<10&&progress.gems>=cost,false);
            }
            int max=data.units.get(selectedUnit).optJSONArray("variants").length();button(c,"Next Armor  ·  "+(s.gearCost+tier(selectedUnit)-1)+" Moonstone",681,606,459,51,77,selectedUnit,tier(selectedUnit)<max&&progress.moonstones>=s.gearCost+tier(selectedUnit)-1,true);
        }
        button(c,"Castle",42,420,167,51,78,7,true,false);button(c,"Miney",42,489,167,51,78,8,true,false);
    }
    private void originalCodex(Canvas c){
        rect(c,0,0,1280,720,INK);header(c,"Heroes of Legendaria","Original unit artwork and animations");
        for(int i=0;i<data.heroTypes.length;i++){int type=data.heroTypes[i];float x=40+(i%6)*202,y=112+(i/6)*94;selectionIcon(c,type,x,y,76,76);text(c,data.label(type),x+133,y+50,18,type==selectedUnit?GOLD:IVORY,true,false);buttons.add(new Button(3020+type,x,y,188,88,30,type));}
        if(!Progress.hero(selectedUnit))selectedUnit=0;panel(c,170,324,940,360,0xff263f3d,GOLD);
        art.actor(c,selectedUnit,codexTier,"walk",clock,370,625,1.6f,false);art.actor(c,selectedUnit,codexTier,"primary",clock,645,625,1.6f,false);
        text(c,data.label(selectedUnit),864,385,32,GOLD,true,true);text(c,"Original Armor",864,430,20,IVORY,true,false);
        int max=data.units.get(selectedUnit).optJSONArray("variants").length();for(int i=1;i<=max;i++)button(c,""+i,768+(i-1)%3*70,465+(i-1)/3*64,61,50,62,i,true,codexTier==i);
    }
    private void options(Canvas c){scene(c,"title");shade(c);panel(c,371,155,538,412,0xf5213436,GOLD);text(c,"Options",640,220,36,GOLD,true,true);button(c,music?"Music: On":"Music: Off",440,272,400,60,60,0,true,false);button(c,"How to Play",440,356,400,60,1,HELP,true,false);button(c,"Back",440,458,400,60,1,HOME,true,false);}
    private void modes(Canvas c){scene(c,"props");shade(c);panel(c,329,135,622,470,0xf5213436,GOLD);text(c,"Game Modes",640,200,37,GOLD,true,true);button(c,"Campaign",421,239,440,61,1,MAP,true,true);button(c,"Armory",421,323,440,61,1,ARMORY,true,false);button(c,"Heroes",421,407,440,61,1,CODEX,true,false);button(c,"Back",421,510,440,54,1,HOME,true,false);}
    private float worldX(OriginalBattle b,double x){return (float)((x-b.camera)*2.25);}
    private float originalLane(double lane){return (float)(720-(52+45*lane)*2.25);}
    private void sourceTerrain(Canvas c,OriginalBattle b){
        int r=b.plan.region;rect(c,0,0,1280,720,r==5?0xff7d5362:r==6?0xffacc9d0:0xffd0c19c);
        JSONObject planJson=data.json.optJSONObject("campaign").optJSONArray("levels").optJSONObject(b.stage);JSONArray layers=planJson.optJSONArray("layers");
        if(layers!=null){ArrayList<JSONObject> sorted=new ArrayList<>();for(int i=0;i<layers.length();i++)sorted.add(layers.optJSONObject(i));sorted.sort((a,z)->Double.compare(a.optDouble("z",0),z.optDouble("z",0)));
            for(JSONObject layer:sorted){String frame=layer.optString("frame"),atlas=layer.optString("sheet","");if(atlas.isEmpty())atlas=SCENES[r]+"Scene";if(!atlas.endsWith("-hd"))atlas+="-hd";
                JSONObject f=art.frame(atlas,frame);if(f==null){atlas=SCENES[r]+"Scene-hd";f=art.frame(atlas,frame);}if(f==null)continue;
                JSONArray size=f.optJSONArray("size"),position=layer.optJSONArray("position"),anchor=layer.optJSONArray("anchor"),ratio=layer.optJSONArray("ratio");
                float w=(float)size.optDouble(0)*1.125f,h=(float)size.optDouble(1)*1.125f;
                double x=position==null?0:position.optDouble(0),y=position==null?0:position.optDouble(1),ax=anchor==null?0:anchor.optDouble(0),ay=anchor==null?0:anchor.optDouble(1);
                String align=layer.optString("align","");if(align.toLowerCase(Locale.US).contains("top"))y=320;
                double camera=layer.optBoolean("onScreen")?0:(b.scrollSpeed>0?b.camera%1700:b.camera)*(ratio==null?1:ratio.optDouble(0,1));
                art.draw(c,atlas,frame,(float)((x-camera)*2.25-ax*w),(float)(720-y*2.25-(1-ay)*h),w,h);
            }
        }
    }
    private void spriteButton(Canvas c,String frame,float x,float y,float size,int action,int param,boolean enabled){
        art.fit(c,"hud-spritesheet-hd",frame,x,y,size,size);if(!enabled)rect(c,x,y,size,size,0x660b2020);else buttons.add(new Button(action*100+param+20,x,y,size,size,action,param));
    }
    private void originalBattle(Canvas c){
        if(!(battle instanceof OriginalBattle)){battle(c);return;}OriginalBattle b=(OriginalBattle)battle;sourceTerrain(c,b);
        if(b.plan.playerCastle)art.draw(c,"castle-default-spritesheet-hd","goodcastle.png",worldX(b,0)-40,originalLane(0)-310,474*1.0f,348*1.0f);
        if(b.plan.enemyHp>0)art.draw(c,"castle-default-spritesheet-hd","evilcastle.png",worldX(b,b.plan.width)-475,originalLane(0)-315,516,368);
        for(int lane=2;lane>=0;lane--)for(BattleEngine.Actor a:b.actors)if(a.lane==lane){
            float x=worldX(b,a.x),feet=originalLane(a.laneVisual);if(x<-350||x>1630)continue;
            String state=a.hp<=0?"death":a.specialTime>0?"special":a.attacking?"primary":a.moving?"walk":"ready";
            if(a.sprite!=null){String atlas=data.spriteAtlas(b.plan.region,a.sprite);if(atlas!=null)art.fit(c,atlas,a.sprite,x-60,feet-100,120,100);}else art.actor(c,a.type,a.enemy?a.tier:Progress.hero(a.type)?Math.max(a.tier,tier(a.type)):a.tier,state,a.animationAge,x,feet,(float)(1.125*a.sizeScale),a.enemy?!a.faceLeft:a.faceLeft);
            if(a==b.selected&&a.hp>0){art.fit(c,"hud-spritesheet-hd","hud-target.png",x-34,feet-18,68,45);}
            if(a.hp>0&&a.hp<a.maxHp)bar(c,x-35,feet-120,70,7,a.hp/a.maxHp,a.enemy?0xffd17d69:0xffa8d77d);
        }
        for(BattleEngine.Shot s:b.shots){float f=(float)Math.min(1,s.age/s.duration),x=worldX(b,s.from+(s.to-s.from)*f),y=originalLane(s.lane)-58;p.setColor(s.enemy?0xffa59b7a:GOLD);c.drawCircle(x,y,4,p);}
        for(int i=0;i<b.miners;i++)art.actor(c,7,1,((int)b.elapsed/4+i)%2==0?"walk":"special",b.elapsed+i,270+i*45,307,1.0f,false);
        for(BattleEngine.Gem g:b.gemsOnField)art.fit(c,"hud-spritesheet-hd","hud-gems-front-topaz.png",(float)g.x*1.2f,270-(float)Math.sin(g.age*5)*6,35,40);
        if(!b.gemsOnField.isEmpty())buttons.add(new Button(6420,215,215,450,130,64,0));
        if(b.spellFlash>0)rect(c,0,0,1280,720,((int)(b.spellFlash*90)<<24)|0xffd277);
        originalHud(c,b);
        if(b.state!=BattleEngine.PLAYING)result(c);else if(b.paused)pause(c);else if(b.cue!=null)dialogue(c,b);
    }
    private void originalHud(Canvas c,OriginalBattle b){
        double hp=b.selected!=null&&b.selected.plot?b.selected.hp: b.playerCastle,max=b.selected!=null&&b.selected.plot?b.selected.maxHp:b.maxPlayerCastle;
        art.draw(c,"hud-spritesheet-hd","hud-meter-backing.png",10,12,202,41);bar(c,46,20,158,11,hp/max,0xffc6534e);art.fit(c,"hud-spritesheet-hd","hud-meter-healthIcon.png",8,13,38,38);
        art.draw(c,"hud-spritesheet-hd","hud-meter-backing.png",10,55,202,41);bar(c,46,65,158,11,b.essence/100.0,0xff67a1ca);art.fit(c,"hud-spritesheet-hd","hud-meter-manaIcon.png",8,56,38,38);
        art.fit(c,"hud-spritesheet-hd","hud-gems-front-topaz.png",251,10,34,42);text(c,""+b.gems,300,43,29,GOLD,false,true);
        spriteButton(c,"icon-production-attack.png",378,10,61,42,1,true);spriteButton(c,"icon-group-defense.png",449,10,61,42,0,true);spriteButton(c,"icon-group-left.png",520,10,61,42,-1,true);spriteButton(c,"icon-group-formup.png",591,10,61,80,0,true);
        spriteButton(c,spellBook?"icon-spellbook-open.png":"icon-spellbook.png",1108,8,70,81,0,true);spriteButton(c,"hud-pause.png",1190,8,74,45,0,true);
        if(spellBook)for(int i=0;i<3;i++)spriteButton(c,new String[]{"icon-spell-arrows.png","icon-spell-fire.png","icon-spell-ice.png"}[i],1020-i*92,87,81,43,i,b.spellCooldowns[i]<=0);
        if(b.plan.mining)spriteButton(c,b.miningReady()?"icon-miner.png":"icon-miner-disabled.png",24,585,112,44,0,b.miningReady());
        int count=b.plan.tutorial()?0:b.loadout.length;
        for(int i=0;i<count;i++){int type=b.loadout[i];float x=25+i*115;art.icon(c,type,tier(type),x,598,105,100);text(c,""+data.specs.get(type).cost,x+52,710,23,GOLD,true,true);if(b.cooldowns[type]>0){rect(c,x,598,105,(float)(100*b.cooldowns[type]/Math.max(.01,data.specs.get(type).cooldown)),0x990b2020);}else if(b.gems>=data.specs.get(type).cost)buttons.add(new Button(4020+type,x,598,105,112,40,type));}
        if(count>0&&b.plan.mining){buttons.removeIf(button->button.action==44);spriteButton(c,b.miningReady()?"icon-miner.png":"icon-miner-disabled.png",25+count*115,598,100,44,0,b.miningReady());}
        art.fit(c,"hud-spritesheet-hd","icon-special-bg.png",1125,565,145,145);art.fit(c,"hud-spritesheet-hd","icon-special-rays.png",1125,565,145,145);art.fit(c,"hud-spritesheet-hd","icon-special-sunfront.png",1125,565,145,145);buttons.add(new Button(8220,1125,565,145,145,82,0));
        if(b.selected!=null){text(c,data.label(b.selected.type),1197,550,19,GOLD,true,true);text(c,""+data.specs.get(b.selected.type).specialCost,1197,716,18,GOLD,true,false);}
    }
    private void wrapped(Canvas c,String value,float x,float y,float width,float size,int color,int max){p.setTypeface(sans);p.setTextSize(size);String line="";int row=0;for(String word:value.split("\\s+")){String next=line.isEmpty()?word:line+" "+word;if(p.measureText(next)>width&&!line.isEmpty()){text(c,line,x,y+row*(size+7),size,color,false,false);if(++row>=max)return;line=word;}else line=next;}if(!line.isEmpty()&&row<max)text(c,line,x,y+row*(size+7),size,color,false,false);}
    private void dialogue(Canvas c,OriginalBattle b){
        BattlePlan.Cue q=b.cue;boolean modal="chat".equals(q.kind);if(modal){buttons.clear();shade(c);}art.draw(c,"unit-selection-spritesheet-hd","unit-selection-background.png",170,modal?357:445,940,modal?350:265);
        text(c,q.title==null||q.title.isEmpty()?"Legendaria":q.title,640,modal?409:488,27,GOLD,true,true);
        wrapped(c,q.text,236,modal?450:526,805,20,INK,modal?6:4);button(c,"Continue",882,646,192,48,83,0,true,true);
    }
    private void action(int action,int param){
        switch(action){
            case 1:screen=param;if(param==MAP)region=data.plans.get(Math.min(progress.highestUnlocked(),data.plans.size()-1)).region;if(param==HOME)homeStage=1;if(param==ARMORY&&!Progress.hero(selectedUnit))selectedUnit=0;if(param==CODEX){selectedUnit=0;codexTier=1;}break;
            case 10:region=param;break;
            case 20:begin(param);break;
            case 30:selectedUnit=param;codexTier=1;break;
            case 31:if(progress.upgrade(param)){activity.save(progress);message("강화 완료");}break;
            case 40:if(battle!=null&&!battle.spawn(param,selectedLane))message("보석과 소환 대기 시간을 확인하세요.");break;
            case 41:selectedLane=param;break;
            case 42:if(battle!=null&&!battle.paused&&battle.state==BattleEngine.PLAYING){if(battle instanceof OriginalBattle)((OriginalBattle)battle).commandOrder(param);else battle.order=param;}break;
            case 43:if(battle!=null)battle.castSpell(param);break;
            case 44:if(battle!=null)battle.hireMiner();break;
            case 45:if(battle!=null)battle.paused=!battle.paused;lastNanos=0;break;
            case 46:if(battle!=null)begin(battle.stage);break;
            case 47:if(battle!=null)begin(battle.stage+1);break;
            case 60:setMusic(!music);break;
            case 61:speed=speed==1?2:1;break;
            case 62:codexTier=param;break;
            case 64:if(battle!=null)battle.collectGems();break;
            case 70:homeStage=param;screen=HOME;break;
            case 71:if(loadout.contains(param))loadout.remove(param);else if(heroUnlocked(param)&&loadout.size()<data.plans.get(pendingStage).slots)loadout.add(param);break;
            case 72:if(!loadout.isEmpty())startBattle(pendingStage);break;
            case 75:if(progress.buyHero(param,data.specs.get(param).moonstonePrice))activity.save(progress);break;
            case 76:{JSONObject upgrades=data.units.get(selectedUnit).optJSONObject("upgrades");JSONObject u=upgrades==null?null:upgrades.optJSONObject(new String[]{"attackPoints","hitPoints","defensePoints"}[param]);if(u!=null&&progress.upgradeStat(selectedUnit,param,u.optInt("baseCost",40),u.optInt("costMultiplier",2)))activity.save(progress);break;}
            case 77:if(progress.upgradeGear(param,data.units.get(param).optJSONArray("variants").length(),data.specs.get(param).gearCost))activity.save(progress);break;
            case 78:if(progress.upgradeSupport(param))activity.save(progress);else message(param==7?"Castle: 250 Gems, maximum 6 upgrades":"Mining yield: 125 Gems, maximum 5 upgrades");break;
            case 80:if(battle instanceof OriginalBattle){OriginalBattle b=(OriginalBattle)battle;b.selected=null;b.selectedType=-1;}break;
            case 81:spellBook=!spellBook;break;
            case 82:if(battle instanceof OriginalBattle)((OriginalBattle)battle).special();break;
            case 83:if(battle instanceof OriginalBattle){((OriginalBattle)battle).acknowledge();lastNanos=0;}break;
            default:break;
        }
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent event){
        float x=(event.getX()-offsetX)/scale,y=(event.getY()-offsetY)/scale;
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){pressed=-1;dragging=false;downX=lastDragX=x;downY=y;for(int i=buttons.size()-1;i>=0;i--){Button b=buttons.get(i);if(b.contains(x,y)){pressed=b.id;break;}}return true;}
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&pressed<0&&battleInputAllowed()){
            OriginalBattle b=(OriginalBattle)battle;if(Math.abs(x-downX)>20)dragging=true;if(dragging&&b.scrollSpeed<=0)b.camera=Math.max(0,Math.min(b.plan.width-1280/2.25,b.camera+(lastDragX-x)/2.25));lastDragX=x;invalidate();return true;
        }
        if(event.getActionMasked()==MotionEvent.ACTION_UP){int id=pressed;pressed=-1;boolean hit=false;for(int i=buttons.size()-1;i>=0;i--){Button b=buttons.get(i);if(b.id==id&&b.contains(x,y)){action(b.action,b.param);performClick();hit=true;break;}}
            if(!hit&&id<0&&!dragging&&battleInputAllowed()){
                OriginalBattle b=(OriginalBattle)battle;double wx=x/2.25+b.camera;int lane=0;float distance=Float.MAX_VALUE;for(int i=0;i<3;i++){float d=Math.abs(y-originalLane(i));if(d<distance){distance=d;lane=i;}}
                BattleEngine.Actor chosen=null;double best=70;for(BattleEngine.Actor a:b.actors)if(a.hp>0&&(a.enemy?!a.untargetable:a.selectable)){double d=Math.hypot(worldX(b,a.x)-x,(originalLane(a.laneVisual)-40-y)*.7);if(d<best){best=d;chosen=a;}}
                if(chosen!=null){if(chosen.enemy)b.target(chosen);else b.selectActor(chosen);}else b.moveSelected(wx,lane);selectedLane=lane;performClick();
            }dragging=false;invalidate();return true;}
        if(event.getActionMasked()==MotionEvent.ACTION_CANCEL){pressed=-1;return true;}return true;
    }
    private boolean battleInputAllowed(){if(screen!=BATTLE||!(battle instanceof OriginalBattle)||battle.paused||battle.state!=BattleEngine.PLAYING)return false;BattlePlan.Cue cue=((OriginalBattle)battle).cue;return cue==null||!"chat".equals(cue.kind);}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public boolean onKeyDown(int key,KeyEvent event){
        if(screen==BATTLE&&battle!=null){
            if(key>=KeyEvent.KEYCODE_1&&key<=KeyEvent.KEYCODE_7){int slot=key-KeyEvent.KEYCODE_1;if(battle instanceof OriginalBattle){int[] types=((OriginalBattle)battle).loadout;if(slot<types.length)action(40,types[slot]);}else action(40,slot);return true;}
            if(key==KeyEvent.KEYCODE_DPAD_UP){selectedLane=Math.max(0,selectedLane-1);return true;}
            if(key==KeyEvent.KEYCODE_DPAD_DOWN){selectedLane=Math.min(2,selectedLane+1);return true;}
            if(key==KeyEvent.KEYCODE_SPACE){action(45,0);return true;}
        }return super.onKeyDown(key,event);
    }
    private static final class Button{
        final int id,action,param;final float x,y,w,h;
        Button(int id,float x,float y,float w,float h,int action,int param){this.id=id;this.x=x;this.y=y;this.w=w;this.h=h;this.action=action;this.param=param;}
        boolean contains(float xx,float yy){return xx>=x&&xx<=x+w&&yy>=y&&yy<=y+h;}
    }
}
