package com.legendary.offline;

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
    private static final String[] BATTLE_TRACKS={"GrassLevel","SnowLevel","DesertLevel","SwampLevel","VolcanoLevel","Netherworld_Level","SkyHaven"};
    public final Progress progress;
    private final MainActivity activity;private final GameData data;private final Artwork art;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private Typeface serif=Typeface.create("serif",Typeface.BOLD),sans=Typeface.create("sans-serif",Typeface.NORMAL);
    private final ArrayList<Button> buttons=new ArrayList<>();
    private final RectF box=new RectF();
    private OriginalBattle battle;
    private int screen,region,selectedUnit,selectedLane=1,codexTier=1,reward,pressed=-1,speed=1;
    private boolean active=true,music=true;
    private float scale=1,offsetX,offsetY;private long lastNanos;private double clock;
    private String notice="",lastMusicTrack="";private double noticeUntil;
    private int homeStage,pendingStage;private final LinkedHashSet<Integer> loadout=new LinkedHashSet<>();private boolean spellBook,dragging;private float downX,downY,lastDragX;

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
        if(active){
            clock+=dt;
            if(screen==BATTLE&&battle!=null){
                for(int i=0;i<speed;i++)battle.update(dt);
                for(String key:battle.drainSfx())activity.sfx(key);
                if(battle.state==BattleEngine.VICTORY){int gain=battle.claimReward();if(gain>0){reward=gain;activity.save(progress);}}
            }
        }
        float w=getWidth(),h=getHeight();scale=Math.min(w/1280f,h/720f);offsetX=(w-1280*scale)/2;offsetY=(h-720*scale)/2;
        canvas.drawColor(0xff0b1419);canvas.save();canvas.translate(offsetX,offsetY);canvas.scale(scale,scale);canvas.clipRect(0,0,1280,720);buttons.clear();
        String trackKey=screen==HOME||screen==OPTIONS||screen==MODES?"TitleScreen":screen==ARMORY?"Armory":screen==BATTLE&&battle!=null?BATTLE_TRACKS[data.plans.get(battle.stage).region]:"Overworld";
        if(!trackKey.equals(lastMusicTrack)){activity.musicTrack(data.json.optJSONObject("music").optString(trackKey));lastMusicTrack=trackKey;}
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
        text(c,label,x+w/2,y+h/2+7,h<46?16:20,enabled?GOLD:0xff8a968d,true,false);
        if(enabled)buttons.add(new Button(id,x,y,w,h,action,param));
    }
    private void header(Canvas c,String title,String subtitle){
        rect(c,0,0,1280,94,0xf20e2025);line(c,0,93,1280,93,0xff9c8b58,1);
        button(c,"‹  돌아가기",28,23,126,47,1,HOME,true,false);
        text(c,title,184,46,30,IVORY,false,true);text(c,subtitle,185,73,16,MUTED,false,false);
        art.fit(c,"mainmenu-foreground-hd","main-menu-gems-diamond.png",1076,27,42,38);
        text(c,String.valueOf(progress.gems),1132,56,26,GOLD,false,false);
    }
    private void worldMap(Canvas c){
        for(int row=0;row<4;row++)for(int col=0;col<4;col++){
            String atlas=col==3?"worldmapsheet3-hd":row<2?"worldmapsheet1":"worldmapsheet2";
            art.draw(c,atlas,"wmap"+(row+1)+"x"+(col+1)+".png",col*320,row*210-70,321,211);
        }
        rect(c,0,0,1280,720,0x55112420);
    }
    private static String repeat(String s,int count){StringBuilder b=new StringBuilder();for(int i=0;i<count;i++)b.append(s);return b.toString();}
    private void bar(Canvas c,float x,float y,float w,float h,double value,int color){rect(c,x,y,w,h,0xcc142322);rect(c,x+1,y+1,(w-2)*(float)Math.max(0,Math.min(1,value)),h-2,color);}
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
        text(c,won?"획득 보석  +"+reward:"광부를 고용하고 상성에 맞춰 영웅을 배치해 보세요.",640,371,21,won?GOLD:IVORY,true,false);
        button(c,won&&battle.stage<data.plans.size()-1?"Next Battle":"Try Again",402,417,476,64,won&&battle.stage<data.plans.size()-1?47:46,0,true,true);
        button(c,"지도",402,503,226,54,1,MAP,true,false);button(c,"무기고",652,503,226,54,1,ARMORY,true,false);
    }
    private void help(Canvas c){
        rect(c,0,0,1280,720,0xff152a2c);header(c,"플레이 방법","원작 전투 상성 · 버프/경직 · 오프라인 복원");
        String[] titles={"01  보석과 광부","02  유닛 지휘와 상성","03  필살기와 마법책","04  무기고 성장"};
        String[][] lines={
            {"광부(Miney)를 고용하면 7초마다 보석을 채굴합니다. 떨어진 보석을 눌러 바로 수집하세요.","보석으로 영웅을 소환하고, 무기고에서 성벽(최대 6단계)과 채굴량(최대 5단계)을 강화하세요."},
            {"유닛을 눌러 선택하고 레인·적을 터치해 이동/공격합니다. 상단 버튼으로 전진·방어·후퇴·집결을 지시하세요.","원작 상성: 근접 > 원거리(1.3x) · 원거리 > 마법(1.4x) · 마법 > 근접(1.15x). 강타/치명타 시 경직이 발생합니다."},
            {"적을 처치해 정수가 차면 우측 하단 필살기 버튼이 빛납니다. 영웅마다 화상·빙결·스턴·오라 효과를 발동합니다.","우측 상단 마법책은 재사용 대기시간으로 화살비(원거리) · 화염폭풍(화상) · 빙결폭풍(5초 빙결)을 시전합니다."},
            {"승리 보석으로 공격·체력·방어를 강화하고, 문스톤으로 새 영웅 해금과 상위 장비 외형을 개방하세요.","장비 단계가 오르면 원작 고티어 애니메이션과 특수 버프/스턴 효과가 함께 해금됩니다."}
        };
        for(int i=0;i<4;i++){float y=126+i*122;text(c,titles[i],84,y+23,24,GOLD,false,true);text(c,lines[i][0],84,y+57,18,IVORY,false,false);text(c,lines[i][1],84,y+87,17,MUTED,false,false);}
        text(c,"원작: Liv Games · 이미지/음원은 정적 검증 후 추출 · 전투/AI/UI는 클린룸으로 새로 구현했습니다.",84,670,16,MUTED,false,false);
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
        int cRank=progress.castleRank,cMax=progress.supportMax(7),cCost=progress.supportCost(7);
        int mRank=progress.mineRank,mMax=progress.supportMax(8),mCost=progress.supportCost(8);
        text(c,"Castle Lv "+cRank+"/"+cMax,126,392,16,GOLD,true,false);
        button(c,cRank>=cMax?"MAX":cCost+" Gems",28,402,196,51,78,7,cRank<cMax&&progress.gems>=cCost,false);
        text(c,"Miney Lv "+mRank+"/"+mMax,126,488,16,GOLD,true,false);
        button(c,mRank>=mMax?"MAX":mCost+" Gems",28,498,196,51,78,8,mRank<mMax&&progress.gems>=mCost,false);
    }
    private void originalCodex(Canvas c){
        rect(c,0,0,1280,720,INK);header(c,"Heroes of Legendaria","Original unit artwork and animations");
        for(int i=0;i<data.heroTypes.length;i++){int type=data.heroTypes[i];float x=40+(i%6)*202,y=112+(i/6)*94;selectionIcon(c,type,x,y,76,76);text(c,data.label(type),x+133,y+50,18,type==selectedUnit?GOLD:IVORY,true,false);buttons.add(new Button(3020+type,x,y,188,88,30,type));}
        if(!Progress.hero(selectedUnit))selectedUnit=0;panel(c,170,324,940,360,0xff263f3d,GOLD);
        art.actor(c,selectedUnit,codexTier,"walk",clock,370,625,1.6f,false);art.actor(c,selectedUnit,codexTier,"primary",clock,645,625,1.6f,false);
        text(c,data.label(selectedUnit),864,385,32,GOLD,true,true);text(c,"Original Armor",864,430,20,IVORY,true,false);
        int max=data.units.get(selectedUnit).optJSONArray("variants").length();for(int i=1;i<=max;i++)button(c,""+i,768+(i-1)%3*70,465+(i-1)/3*64,61,50,62,i,true,codexTier==i);
    }
    private void options(Canvas c){scene(c,"title");shade(c);panel(c,371,155,538,412,0xf5213436,GOLD);text(c,"Options",640,220,36,GOLD,true,true);button(c,music?"Sound & Music: On":"Sound & Music: Off",440,272,400,60,60,0,true,false);button(c,"How to Play",440,356,400,60,1,HELP,true,false);button(c,"Back",440,458,400,60,1,HOME,true,false);}
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
    private static int statusColor(BattleEngine.Actor a){
        if(a.stunTime>0)return "freeze".equals(a.statusTag)?0xff6ce0ff:0xfff5d759;
        if("burn".equals(a.statusTag))return 0xffff7838;
        if("poison".equals(a.statusTag))return 0xff68d85c;
        if("fear".equals(a.statusTag)||"confuse".equals(a.statusTag))return 0xffd278ff;
        if("buff".equals(a.statusTag))return 0xffffe57f;
        return 0;
    }
    private void originalBattle(Canvas c){
        if(battle==null){screen=MAP;return;}
        OriginalBattle b=battle;sourceTerrain(c,b);
        String castleAtlas="castle-"+SCENES[b.plan.region].toLowerCase(Locale.US)+"-spritesheet-hd";
        if(b.plan.playerCastle){
            if(!art.draw(c,castleAtlas,"goodcastle.png",worldX(b,0)-40,originalLane(0)-310,474,348))
                art.draw(c,"castle-default-spritesheet-hd","goodcastle.png",worldX(b,0)-40,originalLane(0)-310,474,348);
        }
        if(b.plan.enemyHp>0){
            if(!art.draw(c,castleAtlas,"evilcastle.png",worldX(b,b.plan.width)-475,originalLane(0)-315,516,368))
                art.draw(c,"castle-default-spritesheet-hd","evilcastle.png",worldX(b,b.plan.width)-475,originalLane(0)-315,516,368);
        }
        for(int lane=2;lane>=0;lane--)for(BattleEngine.Actor a:b.actors)if(a.lane==lane){
            float x=worldX(b,a.x),feet=originalLane(a.laneVisual);if(x<-350||x>1630)continue;
            String state=a.hp<=0?"death":a.specialTime>0?"special":a.attacking?"primary":a.moving?"walk":"ready";
            float jitter=a.hitStun>0?(float)(Math.sin(clock*55)*4):0;
            if(a.sprite!=null){String atlas=data.spriteAtlas(b.plan.region,a.sprite);if(atlas!=null)art.fit(c,atlas,a.sprite,x-60+jitter,feet-100,120,100);}
            else art.actor(c,a.type,a.baseEnemy?a.tier:Progress.hero(a.type)?Math.max(a.tier,tier(a.type)):a.tier,state,a.animationAge,x+jitter,feet,(float)(1.125*a.sizeScale),a.baseEnemy?!a.faceLeft:a.faceLeft);
            if(a==b.selected&&a.hp>0){art.fit(c,"hud-spritesheet-hd","hud-target.png",x-34,feet-18,68,45);}
            if(a.hp>0&&a.hp<a.maxHp)bar(c,x-35,feet-120,70,7,a.hp/a.maxHp,a.enemy?0xffd17d69:0xffa8d77d);
            int sc=a.hp>0?statusColor(a):0;
            if(sc!=0){p.setStyle(Paint.Style.FILL);p.setColor(sc);c.drawCircle(x,feet-132,6,p);}
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
        double hp=b.selected!=null&&b.selected.plot?b.selected.hp:b.playerCastle,max=b.selected!=null&&b.selected.plot?b.selected.maxHp:b.maxPlayerCastle;
        art.draw(c,"hud-spritesheet-hd","hud-meter-backing.png",10,12,202,41);bar(c,46,20,158,11,hp/Math.max(1,max),0xffc6534e);art.fit(c,"hud-spritesheet-hd","hud-meter-healthIcon.png",8,13,38,38);
        art.draw(c,"hud-spritesheet-hd","hud-meter-backing.png",10,55,202,41);bar(c,46,65,158,11,b.essence/100.0,0xff67a1ca);art.fit(c,"hud-spritesheet-hd","hud-meter-manaIcon.png",8,56,38,38);
        art.fit(c,"hud-spritesheet-hd","hud-gems-front-topaz.png",251,10,34,42);text(c,""+b.gems,300,43,29,GOLD,false,true);
        spriteButton(c,"icon-production-attack.png",378,10,61,42,1,true);spriteButton(c,"icon-group-defense.png",449,10,61,42,0,true);spriteButton(c,"icon-group-left.png",520,10,61,42,-1,true);spriteButton(c,"icon-group-formup.png",591,10,61,80,0,true);
        if(b.plan.enemyHp>0){
            art.draw(c,"hud-spritesheet-hd","hud-meter-backing.png",870,12,202,41);
            bar(c,906,20,158,11,b.enemyCastle/Math.max(1,b.maxEnemyCastle),0xffd95b43);
            art.fit(c,"hud-spritesheet-hd","hud-meter-healthIcon.png",868,13,38,38);
            text(c,"적 성 "+(int)Math.ceil(b.enemyCastle),971,71,16,IVORY,true,false);
        }
        spriteButton(c,spellBook?"icon-spellbook-open.png":"icon-spellbook.png",1108,8,70,81,0,true);spriteButton(c,"hud-pause.png",1190,8,74,45,0,true);
        if(spellBook)for(int i=0;i<3;i++)spriteButton(c,new String[]{"icon-spell-arrows.png","icon-spell-fire.png","icon-spell-ice.png"}[i],1020-i*92,87,81,43,i,b.spellCooldowns[i]<=0);
        int count=b.plan.tutorial()?0:b.loadout.length;
        for(int i=0;i<count;i++){int type=b.loadout[i];float x=25+i*115;art.icon(c,type,tier(type),x,598,105,100);text(c,""+data.specs.get(type).cost,x+52,710,23,GOLD,true,true);if(b.cooldowns[type]>0){rect(c,x,598,105,(float)(100*b.cooldowns[type]/Math.max(.01,data.specs.get(type).cooldown)),0x990b2020);}else if(b.gems>=data.specs.get(type).cost)buttons.add(new Button(4020+type,x,598,105,112,40,type));}
        if(b.plan.mining){float mx=count==0?24:25+count*115,my=count==0?585:598,ms=count==0?112:100;spriteButton(c,b.miningReady()?"icon-miner.png":"icon-miner-disabled.png",mx,my,ms,44,0,b.miningReady());}
        boolean spReady=b.specialReady();
        art.fit(c,"hud-spritesheet-hd","icon-special-bg.png",1125,565,145,145);
        if(spReady)art.fit(c,"hud-spritesheet-hd","icon-special-rays.png",1125,565,145,145);
        art.fit(c,"hud-spritesheet-hd","icon-special-sunfront.png",1125,565,145,145);
        if(spReady)buttons.add(new Button(8220,1125,565,145,145,82,0));
        else rect(c,1125,565,145,145,0x880b2020);
        if(b.selected!=null){text(c,data.label(b.selected.type),1197,550,19,spReady?GOLD:MUTED,true,true);text(c,""+data.specs.get(b.selected.type).specialCost,1197,716,18,spReady?GOLD:MUTED,true,false);}
    }
    private void wrapped(Canvas c,String value,float x,float y,float width,float size,int color,int max){p.setTypeface(sans);p.setTextSize(size);String line="";int row=0;for(String word:value.split("\\s+")){String next=line.isEmpty()?word:line+" "+word;if(p.measureText(next)>width&&!line.isEmpty()){text(c,line,x,y+row*(size+7),size,color,false,false);if(++row>=max)return;line=word;}else line=next;}if(!line.isEmpty()&&row<max)text(c,line,x,y+row*(size+7),size,color,false,false);}
    private void dialogue(Canvas c,OriginalBattle b){
        BattlePlan.Cue q=b.cue;boolean modal="chat".equals(q.kind);if(modal){buttons.clear();shade(c);}art.draw(c,"unit-selection-spritesheet-hd","unit-selection-background.png",170,modal?357:445,940,modal?350:265);
        text(c,q.title==null||q.title.isEmpty()?"Legendaria":q.title,640,modal?409:488,27,GOLD,true,true);
        wrapped(c,q.text,236,modal?450:526,805,20,INK,modal?6:4);button(c,"Continue",882,646,192,48,83,0,true,true);
    }
    private void action(int action,int param){
        switch(action){
            case 1:activity.sfx("click");screen=param;if(param==MAP)region=data.plans.get(Math.min(progress.highestUnlocked(),data.plans.size()-1)).region;if(param==HOME)homeStage=1;if(param==ARMORY&&!Progress.hero(selectedUnit))selectedUnit=0;if(param==CODEX){selectedUnit=0;codexTier=1;}break;
            case 10:activity.sfx("click");region=param;break;
            case 20:activity.sfx("click");begin(param);break;
            case 30:activity.sfx("click");selectedUnit=param;codexTier=1;break;
            case 40:if(battle!=null&&!battle.spawn(param,selectedLane))message("보석과 소환 대기 시간을 확인하세요.");break;
            case 42:if(battle!=null&&!battle.paused&&battle.state==BattleEngine.PLAYING){battle.commandOrder(param);activity.sfx("click");}break;
            case 43:if(battle!=null)battle.castSpell(param);break;
            case 44:if(battle!=null)battle.hireMiner();break;
            case 45:if(battle!=null){battle.paused=!battle.paused;activity.sfx("click");}lastNanos=0;break;
            case 46:if(battle!=null)begin(battle.stage);break;
            case 47:if(battle!=null)begin(battle.stage+1);break;
            case 60:setMusic(!music);break;
            case 61:speed=speed==1?2:1;break;
            case 62:activity.sfx("click");codexTier=param;break;
            case 64:if(battle!=null)battle.collectGems();break;
            case 70:activity.sfx("click");homeStage=param;screen=HOME;break;
            case 71:activity.sfx("click");if(loadout.contains(param))loadout.remove(param);else if(heroUnlocked(param)&&loadout.size()<data.plans.get(pendingStage).slots)loadout.add(param);break;
            case 72:if(!loadout.isEmpty()){activity.sfx("click");startBattle(pendingStage);}break;
            case 75:if(progress.buyHero(param,data.specs.get(param).moonstonePrice)){activity.save(progress);activity.sfx("upgrade");}break;
            case 76:{JSONObject upgrades=data.units.get(selectedUnit).optJSONObject("upgrades");JSONObject u=upgrades==null?null:upgrades.optJSONObject(new String[]{"attackPoints","hitPoints","defensePoints"}[param]);if(u!=null&&progress.upgradeStat(selectedUnit,param,u.optInt("baseCost",40),u.optInt("costMultiplier",2))){activity.save(progress);activity.sfx("upgrade");}break;}
            case 77:if(progress.upgradeGear(param,data.units.get(param).optJSONArray("variants").length(),data.specs.get(param).gearCost)){activity.save(progress);activity.sfx("upgrade");}break;
            case 78:if(progress.upgradeSupport(param)){activity.save(progress);activity.sfx("upgrade");message(param==7?"성벽 강화 완료 (Lv "+progress.castleRank+"/"+progress.supportMax(7)+")":"채굴 강화 완료 (Lv "+progress.mineRank+"/"+progress.supportMax(8)+")");}else message(param==7?"성벽 강화: "+progress.supportCost(7)+" Gems (최대 6단계)":"채굴 강화: "+progress.supportCost(8)+" Gems (최대 5단계)");break;
            case 80:if(battle!=null)battle.formUp();break;
            case 81:activity.sfx("click");spellBook=!spellBook;break;
            case 82:if(battle!=null)battle.special();break;
            case 83:if(battle!=null){battle.acknowledge();activity.sfx("click");lastNanos=0;}break;
            default:break;
        }
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent event){
        float x=(event.getX()-offsetX)/scale,y=(event.getY()-offsetY)/scale;
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){pressed=-1;dragging=false;downX=lastDragX=x;downY=y;for(int i=buttons.size()-1;i>=0;i--){Button b=buttons.get(i);if(b.contains(x,y)){pressed=b.id;break;}}return true;}
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&pressed<0&&battleInputAllowed()){
            if(Math.abs(x-downX)>20)dragging=true;if(dragging&&battle.scrollSpeed<=0)battle.camera=Math.max(0,Math.min(battle.plan.width-1280/2.25,battle.camera+(lastDragX-x)/2.25));lastDragX=x;invalidate();return true;
        }
        if(event.getActionMasked()==MotionEvent.ACTION_UP){int id=pressed;pressed=-1;boolean hit=false;for(int i=buttons.size()-1;i>=0;i--){Button b=buttons.get(i);if(b.id==id&&b.contains(x,y)){action(b.action,b.param);performClick();hit=true;break;}}
            if(!hit&&id<0&&!dragging&&battleInputAllowed()){
                double wx=x/2.25+battle.camera;int lane=0;float distance=Float.MAX_VALUE;for(int i=0;i<3;i++){float d=Math.abs(y-originalLane(i));if(d<distance){distance=d;lane=i;}}
                BattleEngine.Actor chosen=null;double best=70;for(BattleEngine.Actor a:battle.actors)if(a.hp>0&&(a.enemy?!a.untargetable:a.selectable)){double d=Math.hypot(worldX(battle,a.x)-x,(originalLane(a.laneVisual)-40-y)*.7);if(d<best){best=d;chosen=a;}}
                if(chosen!=null){if(chosen.enemy)battle.target(chosen);else battle.selectActor(chosen);}else battle.moveSelected(wx,lane);selectedLane=lane;performClick();
            }dragging=false;invalidate();return true;}
        if(event.getActionMasked()==MotionEvent.ACTION_CANCEL){pressed=-1;return true;}return true;
    }
    private boolean battleInputAllowed(){if(screen!=BATTLE||battle==null||battle.paused||battle.state!=BattleEngine.PLAYING)return false;BattlePlan.Cue cue=battle.cue;return cue==null||!"chat".equals(cue.kind);}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public boolean onKeyDown(int key,KeyEvent event){
        if(screen==BATTLE&&battle!=null){
            if(key>=KeyEvent.KEYCODE_1&&key<=KeyEvent.KEYCODE_7){int slot=key-KeyEvent.KEYCODE_1;int[] types=battle.loadout;if(slot<types.length)action(40,types[slot]);return true;}
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
