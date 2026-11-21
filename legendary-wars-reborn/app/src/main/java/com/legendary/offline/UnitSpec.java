package com.legendary.offline;

public final class UnitSpec {
    public static final int MELEE=0,RANGED=1,MAGIC=2,SIEGE=3,NEUTRAL=4;
    public final String id;
    public final double hp,attack,defense,speed,cooldown,range,interval;
    public final int cost,unlock;
    public final boolean hero;
    public double hitDelay=.3,specialDamage=40,attackUpgrade=1,hpUpgrade=5,defenseUpgrade=1,critChance,huntLimit;
    public int specialCost=10,moonstonePrice,reward=5,gearCost=1,damageType=MELEE,armorType=MELEE;
    public boolean healer,buffImmune,charmImmune;
    public Attack[] primaryByTier=new Attack[0],specialByTier=new Attack[0];
    public Attack profile(int tier,boolean special){Attack[] list=special?specialByTier:primaryByTier;if(list.length>0)return list[Math.min(list.length-1,Math.max(0,tier-1))];Attack a=new Attack();a.interval=interval;a.range=range;a.times=new double[]{hitDelay};return a;}
    /** Native libverde 0x37f8cc: positive scalors add; negative scalors divide. */
    public static double scale(double base,double modifier){return modifier>=0?base*(1+modifier):base/(1-modifier);}
    public static double mitigate(double rolled,double defense,boolean guarding){double percent=Math.max(25,100-(guarding?Math.floor(defense):Math.floor(defense/2)));return Math.round(rolled*percent/100);}
    /** Native libverde 0x4b297c: damageType vs armorType multiplier (melee>ranged 1.3x, ranged>magic 1.4x, magic>melee 1.15x, siege bypasses armorType==3 immunity). */
    public static double typeModifier(int damageType,int armorType){
        if(damageType==SIEGE)return 1.0;
        if(damageType==MELEE&&armorType==RANGED)return 1.3;
        if(damageType==RANGED&&armorType==MAGIC)return 1.4;
        if(damageType==MAGIC&&armorType==MELEE)return 1.15;
        if(armorType==SIEGE)return 0.0;
        return 1.0;
    }
    /** Native libverde 0x483d04..0x483d30: apply type multiplier in float32 after defense mitigation and truncate toward zero. */
    public static double applyTypeMatchup(double mitigated,int damageType,int armorType){return (int)((float)mitigated*(float)typeModifier(damageType,armorType));}
    public static double roll(double base,double uniform,boolean critical){return Math.round(base*(.75+.25*uniform))*(critical?2:1);}
    public static final class BuffDef {
        public String name="buff",status="";
        public double duration,chance=1,dps,hps,stun,speedScalor,hasteScalor,attackScalor,defenseScalor,rangeScalor,lifeDrainPercent,auraRadius;
        public boolean root,selfBuff,friendlyAura;
    }
    public static final class Attack {
        public double interval=1,range=30,speedScalor,stallStun;
        public int damageType=-1;
        public String type="attack";
        public boolean allLanes,heal,area;
        public double[] times={.3},projectiles={0};
        public BuffDef[] buffs=new BuffDef[0];
        public BuffDef selfBuff;
    }
    public UnitSpec(String id,double hp,double attack,double defense,double speed,int cost,double cooldown,double range,double interval,boolean hero,int unlock){
        this.id=id;this.hp=hp;this.attack=attack;this.defense=defense;this.speed=speed;this.cost=cost;this.cooldown=cooldown;this.range=range;this.interval=interval;this.hero=hero;this.unlock=unlock;
    }
}
