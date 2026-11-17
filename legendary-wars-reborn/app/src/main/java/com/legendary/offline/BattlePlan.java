package com.legendary.offline;

import java.util.*;

/** A finite, typed subset of original scenario DATA. No source expressions execute. */
public final class BattlePlan {
    public String id,title,mode="campaign";
    public int region,number,slots=3,initialGems;
    public double width=1700,playerHp=500,enemyHp;
    public boolean mining,playerCastle;
    public final List<Spawn> spawns=new ArrayList<>();
    public final List<Cue> cues=new ArrayList<>();
    public final List<Command> commands=new ArrayList<>();
    public final List<Outcome> outcomes=new ArrayList<>();
    public final List<Wave> waves=new ArrayList<>();
    public boolean tutorial(){return "tutorial".equals(mode);}
    public static final class Trigger {
        public String type,target;public String[] conditions=new String[0],states=new String[0];
        public Trigger parent;public double parentTime;
        public String key(){return type+":"+target+":"+Arrays.toString(conditions)+":"+Arrays.toString(states)+":"+(parent==null?"":parent.key())+":"+parentTime;}
    }
    public static class Event {public double time;public Trigger trigger;}
    public static final class Spawn extends Event {
        public String unit,id,sprite,ai="attack";public int type=-1,team=1,lane=1,tier=1,wave=-1;public double x=Double.NaN,hp=1,attack,speed,haste,range,addHp,scale=1;public boolean left,plot,hunt;
        public boolean selectable=true,untargetable;public final List<Zone> zones=new ArrayList<>();
    }
    public static final class Zone {public String flag,actor;public int amount=1;public double left=-20,width=40,damage;public boolean once=true;}
    public static final class Cue extends Event {public String text,title,kind;}
    public static final class Command extends Event {public String type,id;public double value;public boolean bool,increment;}
    public static final class Outcome extends Event {public boolean victory;}
    public static final class Wave {public double start,delay=5,stopAt=Double.POSITIVE_INFINITY;public int count;public boolean repeat;}
}
