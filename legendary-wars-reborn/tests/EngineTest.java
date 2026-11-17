package com.legendary.offline;

import java.util.*;

public final class EngineTest {
    private static int passed;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);passed++;}
    private static List<UnitSpec> specs(){
        List<UnitSpec> out=new ArrayList<>();
        for(int i=0;i<16;i++)out.add(new UnitSpec("unit"+i,100,20,0,40,10,2,50,1,i<7,i<2?0:3*(i-1)));
        return out;
    }
    private static void seconds(BattleEngine b,double s){for(int i=0;i<Math.ceil(s*60);i++)b.update(1.0/60);}
    public static void main(String[] args){
        Progress p=new Progress();BattleEngine b=new BattleEngine(specs(),0,p);
        int before=b.gems;
        check(!b.spawn(0,-1)&&!b.spawn(-1,0)&&!b.spawn(99,0),"invalid input must be rejected");
        check(b.gems==before&&b.actors.isEmpty(),"invalid input must not spend gems or create actors");
        check(b.spawn(0,1),"valid affordable summon");
        int spent=b.gems;
        check(!b.spawn(0,1)&&b.gems==spent,"rapid duplicate touch must obey cooldown without spending");
        check(!b.spawn(6,0),"campaign unit unlock must be enforced");
        b.paused=true;double t=b.elapsed;seconds(b,10);
        check(b.elapsed==t&&b.gems==spent,"background pause must freeze simulation/resources");
        check(!b.castSpell(0)&&!b.hireMiner(),"pause must reject battle actions");
        b.paused=false;seconds(b,6);
        check(!b.gemsOnField.isEmpty()||b.gems>spent,"miners must produce collectable or deposited gems");
        int money=b.gems;b.collectGems();
        check(b.gems>=money&&b.gemsOnField.isEmpty(),"collecting gems must deposit and remove each pickup");
        b=new BattleEngine(specs(),0,new Progress());
        BattleEngine.Actor ally=new BattleEngine.Actor(0,false,1,500,100,20,0,0,1000,1);
        BattleEngine.Actor enemy=new BattleEngine.Actor(8,true,0,510,100,20,0,0,1000,1);
        b.actors.add(ally);b.actors.add(enemy);seconds(b,1);
        check(ally.hp==100&&enemy.hp==100,"units cannot attack across lanes");
        enemy.lane=1;seconds(b,2);
        check(ally.hp<100&&enemy.hp<100,"same-lane units must fight");
        b=new BattleEngine(specs(),0,new Progress());
        ally=new BattleEngine.Actor(0,false,1,500,100,20,0,40,50,1);
        enemy=new BattleEngine.Actor(8,true,1,540,100,20,0,0,50,1);
        b.actors.add(ally);b.actors.add(enemy);b.order=-1;seconds(b,1);
        check(ally.x<490&&enemy.hp==100,"retreat must disengage an ally without attacking its opponent");
        b=new BattleEngine(specs(),0,new Progress());
        ally=new BattleEngine.Actor(0,false,1,1150,100,20,0,40,50,1);b.actors.add(ally);b.order=-1;seconds(b,1);
        check(ally.x<1140&&b.enemyCastle==320,"retreat must stop castle attacks and return home");
        b=new BattleEngine(specs(),0,new Progress());b.playerCastle=0;seconds(b,.1);
        check(b.state==BattleEngine.DEFEAT,"destroyed player castle causes defeat");
        check(!b.spawn(0,0)&&b.claimReward()==0,"defeat cannot summon or claim victory rewards");
        p=new Progress();b=new BattleEngine(specs(),0,p);b.enemyCastle=0;seconds(b,.1);
        check(b.state==BattleEngine.VICTORY,"destroyed enemy castle causes victory");
        int wallet=p.gems;int reward=b.claimReward();
        check(reward>0&&p.gems==wallet+reward&&p.stars[0]>0,"victory awards and saves completion");
        check(b.claimReward()==0&&p.gems==wallet+reward,"duplicate result callback must not duplicate reward");
        check(p.highestUnlocked()==1,"first victory unlocks next stage");
        String saved=p.encode();Progress restored=Progress.decode(saved);
        check(restored.gems==p.gems&&restored.stars[0]==p.stars[0],"progress survives serialize and restore");
        check(Progress.decode("bad|not-a-number").gems==new Progress().gems,"malformed saves recover safely");
        check(Progress.decode("v1|2147483647|-5|garbage").highestUnlocked()==0,"invalid save cannot unlock arbitrary stages");
        p=new Progress();p.gems=0;
        check(!p.upgrade(0)&&p.ranks[0]==0,"insufficient upgrade currency does not upgrade");
        p.gems=100000;check(p.upgrade(0)&&p.ranks[0]==1&&p.gems<100000,"upgrade spends persistent currency");
        int cash=p.gems;
        check(!p.upgrade(-1)&&!p.upgrade(100)&&p.gems==cash,"invalid upgrade target rejected");
        b=new BattleEngine(specs(),0,new Progress());
        seconds(b,240);
        check(b.state==BattleEngine.DEFEAT,"unattended battle must eventually lose");
        System.out.println("PASS "+passed+" engine/progression assertions");
    }
}
