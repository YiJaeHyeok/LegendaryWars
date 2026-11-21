package com.legendary.offline;

import java.util.*;

public class BattleEngine {
    public static final int PLAYING=0,VICTORY=1,DEFEAT=2;
    public final List<Actor> actors=new ArrayList<>();
    public final List<Gem> gemsOnField=new ArrayList<>();
    public final List<Shot> shots=new ArrayList<>();
    public final List<UnitSpec> specs;
    public final Progress progress;
    public final int stage;
    public final double[] cooldowns;
    public final double[] spellCooldowns=new double[3];
    public int gems=65,state=PLAYING,miners=1,essence=15,order=1,kills,wave;
    public boolean paused;
    public double elapsed,playerCastle=1000,enemyCastle=320,maxPlayerCastle,maxEnemyCastle,spellFlash;
    private double mineTimer,nextWave=8,freeze;
    private boolean rewardClaimed;
    public BattleEngine(List<UnitSpec> specs,int stage,Progress progress){
        if(specs==null||specs.size()<16||progress==null||stage<0||stage>=progress.stars.length||stage>progress.highestUnlocked())throw new IllegalArgumentException("Invalid battle");
        this.specs=Collections.unmodifiableList(new ArrayList<>(specs));this.stage=stage;this.progress=progress;
        cooldowns=new double[specs.size()];
        maxPlayerCastle=playerCastle=1000+progress.castleRank*180;
        maxEnemyCastle=enemyCastle=320+stage*45;
        gems+=progress.mineRank*8;
    }
    public boolean unlocked(int type){return type>=0&&type<7&&progress.highestUnlocked()>=specs.get(type).unlock;}
    public int totalWaves(){return 8+stage/3;}
    public void update(double dt){
        if(paused||state!=PLAYING||!Double.isFinite(dt)||dt<=0)return;
        dt=Math.min(dt,.05);elapsed+=dt;
        if(playerCastle<=0){state=DEFEAT;return;}if(enemyCastle<=0){state=VICTORY;return;}
        for(int i=0;i<7;i++)cooldowns[i]=Math.max(0,cooldowns[i]-dt);
        for(int i=0;i<3;i++)spellCooldowns[i]=Math.max(0,spellCooldowns[i]-dt);
        spellFlash=Math.max(0,spellFlash-dt);freeze=Math.max(0,freeze-dt);
        mineTimer+=dt;
        if(mineTimer>=5){mineTimer-=5;for(int i=0;i<miners&&gemsOnField.size()<24;i++)gemsOnField.add(new Gem(7+progress.mineRank,155+i*22));}
        for(Iterator<Gem> it=gemsOnField.iterator();it.hasNext();){Gem g=it.next();g.age+=dt;if(g.age>3){gems=Math.min(9999,gems+g.value);it.remove();}}
        nextWave-=dt;
        if(nextWave<=0&&wave<totalWaves()){
            spawnWave();wave++;nextWave=Math.max(2.6,7.3-stage*.065);
        }
        for(Actor a:actors){
            a.age+=dt;a.animationAge+=dt;a.flash=Math.max(0,a.flash-dt);
            if(a.hp<=0){a.deathAge+=dt;continue;}
            if(a.enemy&&freeze>0){a.moving=false;a.attacking=false;continue;}
            if(!a.enemy&&order<0){a.attacking=false;a.moving=a.x>145;a.x=Math.max(145,a.x-a.speed*dt);continue;}
            a.attackClock=Math.max(0,a.attackClock-dt);
            Actor target=null;double nearest=Double.MAX_VALUE;
            for(Actor t:actors){
                if(t.hp<=0||t.enemy==a.enemy||t.lane!=a.lane)continue;
                double d=Math.abs(t.x-a.x);if(d<nearest){nearest=d;target=t;}
            }
            boolean castle=target==null||nearest>a.range+25;
            double castleDistance=Math.abs((a.enemy?100:1180)-a.x);
            boolean inRange=target!=null&&nearest<=a.range+25;
            boolean atCastle=castle&&castleDistance<=a.range+20;
            a.attacking=inRange||atCastle;a.moving=false;
            if(a.attacking){
                if(a.attackClock<=0){
                    a.attackClock=a.interval;a.animationAge=0;
                    if(inRange){
                        double damage=Math.max(2,a.attack-target.defense*.65);target.hp-=damage;target.flash=.12;
                        if(a.range>90)shots.add(new Shot(a.x,target.x,a.lane,a.enemy,a.type));
                        if(target.hp<=0){target.deathAge=0;target.animationAge=0;if(!a.enemy){kills++;essence=Math.min(100,essence+4);gems=Math.min(9999,gems+3+stage/10);}}
                    }else if(a.enemy)playerCastle=Math.max(0,playerCastle-a.attack);else enemyCastle=Math.max(0,enemyCastle-a.attack);
                }
            }else{
                int direction=a.enemy?-1:order;
                if(!a.enemy&&order<0&&a.x<=145)direction=0;
                if(direction!=0){a.x=Math.max(110,Math.min(1170,a.x+direction*a.speed*dt));a.moving=true;}
            }
        }
        for(Iterator<Actor> it=actors.iterator();it.hasNext();)if(it.next().deathAge>1.3)it.remove();
        for(Iterator<Shot> it=shots.iterator();it.hasNext();){Shot s=it.next();s.age+=dt;if(s.age>.38)it.remove();}
        if(playerCastle<=0)state=DEFEAT;else if(enemyCastle<=0)state=VICTORY;
    }
    private void spawnWave(){
        int region=stage/10;
        int type=8;
        if(stage>=2&&wave%3==1)type=9;
        if(stage>=5&&wave%4==2)type=10;
        if(region>=1&&wave%4==0)type=11;
        if(region>=2&&wave%5==2)type=12;
        if(region>=3&&wave%4==3)type=13;
        if(region>=1&&wave%5==4)type=14;
        if(region>=4&&wave%5==0)type=15;
        addEnemy(type,(wave+stage)%3,stage%10==9&&wave==totalWaves()-1?2.3:1);
        if(stage>=8&&wave%3==0)addEnemy(8,(wave+stage+1)%3,1);
    }
    private void addEnemy(int type,int lane,double boss){
        if(actors.size()>=90)return;UnitSpec s=specs.get(type);double factor=(.72+stage*.032)*boss;
        Actor a=new Actor(type,true,lane,1180,s.hp*factor,s.attack*(.7+stage*.02)*boss,s.defense,Math.max(35,s.speed*1.7),s.range,Math.max(.65,s.interval));a.boss=boss>1;actors.add(a);
    }
    public boolean spawn(int unit,int lane){
        if(paused||state!=PLAYING||!unlocked(unit)||lane<0||lane>2||actors.size()>=90||cooldowns[unit]>.001)return false;
        UnitSpec s=specs.get(unit);if(gems<s.cost)return false;
        gems-=s.cost;cooldowns[unit]=Math.max(1.4,s.cooldown*.55);
        int rank=progress.ranks[unit];Actor a=new Actor(unit,false,lane,130,s.hp*(1+rank*.13),s.attack*(1+rank*.12),s.defense+rank,Math.max(36,s.speed*1.8),s.range,Math.max(.65,s.interval));actors.add(a);return true;
    }
    public boolean hireMiner(){
        int cost=15+miners*10;if(paused||state!=PLAYING||miners>=4||gems<cost)return false;gems-=cost;miners++;return true;
    }
    public boolean castSpell(int kind){
        if(kind<0||kind>2||paused||state!=PLAYING||spellCooldowns[kind]>0)return false;
        int cost=kind==0?25:kind==1?20:30;if(essence<cost)return false;essence-=cost;spellCooldowns[kind]=kind==0?14:kind==1?18:22;spellFlash=.5;
        if(kind==0){for(Actor a:actors)if(a.enemy&&a.hp>0){a.hp-=80+stage*2;a.flash=.3;if(a.hp<=0){a.animationAge=0;kills++;essence=Math.min(100,essence+3);}}}
        else if(kind==1){playerCastle=Math.min(maxPlayerCastle,playerCastle+250);for(Actor a:actors)if(!a.enemy&&a.hp>0)a.hp=Math.min(a.maxHp,a.hp+a.maxHp*.4);}
        else freeze=5;
        return true;
    }
    public void collectGems(){if(paused||state!=PLAYING)return;for(Gem g:gemsOnField)gems=Math.min(9999,gems+g.value);gemsOnField.clear();}
    public int claimReward(){if(state!=VICTORY||rewardClaimed)return 0;rewardClaimed=true;return progress.complete(stage,playerCastle/maxPlayerCastle>.75?3:playerCastle/maxPlayerCastle>.4?2:1);}
    public static final class Buff {
        public final String name,status;
        public double remaining,dps,hps,speedScalor,hasteScalor,attackScalor,defenseScalor,rangeScalor,lifeDrainPercent;
        public boolean stun,root,fear,charm;
        public Actor source;
        public Buff(String name,String status,double duration){this.name=name;this.status=status==null?"":status;this.remaining=duration;}
    }
    public static final class Actor {
        public final int type;public boolean enemy;public final boolean baseEnemy;public int lane;public double x,hp,maxHp,attack,defense,speed,range,interval;
        public double baseAttack,baseDefense,baseSpeed,baseRange,baseHaste;
        public double age,animationAge,attackClock,deathAge,flash,hitStun,stunTime;public boolean moving,attacking,boss,rooted,feared,charmed;
        public String scriptId,sprite,statusTag="";public int tier=1;public boolean plot,faceLeft,hunt;public double goal=Double.NaN,laneVisual,windup,specialTime,sizeScale=1,homeX=Double.NaN,huntLimit;public Actor target,pending;public boolean pendingCastle;
        public double haste,rangeScalor,attackScalor,attackAge,lifeDrain;public int hitIndex;public boolean attackActive,usingSpecial,retreat;public String ai="attack",savedAi;
        public final List<Buff> buffs=new ArrayList<>();
        public final Set<Actor> specialHits=Collections.newSetFromMap(new IdentityHashMap<Actor,Boolean>());
        public final List<BattlePlan.Zone> zones=new ArrayList<>();public final Set<BattlePlan.Zone> usedZones=Collections.newSetFromMap(new IdentityHashMap<BattlePlan.Zone,Boolean>());public boolean selectable=true,untargetable,matchScroll,collected;
        public Actor(int type,boolean enemy,int lane,double x,double hp,double attack,double defense,double speed,double range,double interval){this.type=type;this.enemy=enemy;this.baseEnemy=enemy;this.lane=lane;this.x=x;this.homeX=x;this.hp=hp;this.maxHp=hp;this.attack=this.baseAttack=attack;this.defense=this.baseDefense=defense;this.speed=this.baseSpeed=speed;this.range=this.baseRange=range;this.interval=interval;}
    }
    public static final class Gem{public final int value;public final double x;public double age;Gem(int value,double x){this.value=value;this.x=x;}}
    public static final class Shot{public final double from,to;public final int lane,type;public final boolean enemy;public double age,duration=.38,damage;public int damageType=UnitSpec.RANGED;public boolean critical;public UnitSpec.BuffDef[] buffs=new UnitSpec.BuffDef[0];public Actor source,target;Shot(double from,double to,int lane,boolean enemy,int type){this.from=from;this.to=to;this.lane=lane;this.enemy=enemy;this.type=type;}}
}
