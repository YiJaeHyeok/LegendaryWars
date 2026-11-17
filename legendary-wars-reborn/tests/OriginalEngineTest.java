package com.legendary.offline;

import java.util.*;

public final class OriginalEngineTest {
    private static int count;
    private static void ok(boolean b,String s){if(!b)throw new AssertionError(s);count++;}
    private static List<UnitSpec> specs(){List<UnitSpec>s=new ArrayList<>();for(int i=0;i<21;i++)s.add(new UnitSpec("u"+i,100,20,0,25,10,5,25,1,i<7||i>=16,0));return s;}
    private static void ticks(OriginalBattle b,double t){for(int i=0;i<(int)Math.ceil(t*60);i++)b.update(1.0/60);}
    public static void main(String[]args){
        BattlePlan p=new BattlePlan();p.id="level-01-2";p.mining=true;p.playerCastle=true;p.playerHp=500;p.width=1700;p.initialGems=50;
        OriginalBattle b=new OriginalBattle(specs(),0,new Progress(),p,new int[]{0,1,2});
        ok(b.playerCastle==500&&b.gems==50,"use source castle HP and initial gems");
        ok(b.spawn(0,1),"selected hero can be recruited");
        ticks(b,3);ok(!b.spawn(0,1),"source five-second cooldown cannot be shortened");
        ticks(b,2.1);ok(b.spawn(0,1),"source summon cooldown expires");
        ok(!b.spawn(4,1),"non-selected hero cannot be recruited");
        ok(b.hireMiner(),"original mining cost zero allows a miner");
        int gems=b.gems;ticks(b,7.1);ok(b.gemsOnField.size()==1,"source miner produces a gem after seven seconds");
        b.collectGems();ok(b.gems==gems+5,"original gem yield is five");
        BattlePlan.Spawn scout=new BattlePlan.Spawn();scout.time=20;scout.type=8;scout.unit="u8";scout.id="scout";scout.x=1200;p.spawns.add(scout);
        b=new OriginalBattle(specs(),0,new Progress(),p,new int[]{0});ticks(b,19.9);ok(b.actors.isEmpty(),"source encounter cannot spawn early");ticks(b,.2);ok(b.actors.size()==1,"source encounter spawns at its timestamp");
        BattlePlan tutorial=new BattlePlan();tutorial.mode="tutorial";tutorial.playerCastle=false;
        BattlePlan.Spawn hero=new BattlePlan.Spawn();hero.type=0;hero.unit="u0";hero.id="knighty";hero.team=0;hero.x=720;hero.plot=true;tutorial.spawns.add(hero);
        b=new OriginalBattle(specs(),0,new Progress(),tutorial,new int[]{0});ticks(b,.1);ok(b.actors.size()==1&&b.actors.get(0).x==720,"tutorial begins with source Knighty position");
        b.selectActor(b.actors.get(0));b.moveSelected(800,2);ticks(b,1);ok(b.actors.get(0).x>740&&b.actors.get(0).lane==2,"tapping a lane directs selected original hero");
        b.actors.get(0).hp=0;ticks(b,.1);ok(b.state==BattleEngine.DEFEAT,"source tutorial loses when its plot hero dies");
        String old="v1|225|2|1|"+csv(50,1)+"|"+csv(7,2);
        Progress migrated=Progress.decode(old);ok(migrated.gems==225&&migrated.stars.length==73&&migrated.stars[49]==1&&migrated.stars[50]==0,"previous progress migrates to original campaign length");
        ok(Progress.decode(migrated.encode()).ranks[0]==2,"new save round trip retains migrated upgrades");
        BattlePlan move=new BattlePlan();move.playerCastle=true;BattlePlan.Spawn foe=new BattlePlan.Spawn();foe.type=8;foe.unit="u8";foe.x=1000;foe.speed=.1;move.spawns.add(foe);
        b=new OriginalBattle(specs(),0,new Progress(),move,new int[]{0});ticks(b,1);ok(Math.abs(b.actors.get(0).speed-27.5)<.0001,"native additive speed modifier: 25 * (1 + .1)");
        ok(b.actors.get(0).x<975,"enemy actually advances with small positive source modifier");
        BattlePlan duel=new BattlePlan();duel.initialGems=100;duel.playerCastle=true;
        b=new OriginalBattle(specs(),0,new Progress(),duel,new int[]{0});b.spawn(0,1);BattleEngine.Actor ally=b.actors.get(0);
        BattleEngine.Actor near=new BattleEngine.Actor(8,true,1,215,1000,0,0,0,25,1),far=new BattleEngine.Actor(8,true,1,260,1000,0,0,0,25,1);b.actors.add(near);b.actors.add(far);b.target(far);ticks(b,.8);ok(near.hp==1000,"explicit target must not be replaced by a nearer enemy");
        b.commandOrder(-1);double enemyHp=far.hp;ticks(b,2);ok(far.hp==enemyHp,"retreat cancels pending attacks");
        BattlePlan counters=new BattlePlan();counters.initialGems=100;
        for(int i=0;i<6;i++){BattlePlan.Spawn e=new BattlePlan.Spawn();e.type=8;e.unit="u8";e.id="mantis";e.x=300;e.ai="guard";counters.spawns.add(e);}
        BattlePlan.Command increment=new BattlePlan.Command();increment.type="flag";increment.id="mantisKilled";increment.value=1;increment.increment=true;increment.trigger=new BattlePlan.Trigger();increment.trigger.type="die";increment.trigger.target="mantis";counters.commands.add(increment);
        b=new OriginalBattle(specs(),0,new Progress(),counters,new int[]{0});ticks(b,.1);b.castSpell(1);ticks(b,.1);ok(b.flag("mantisKilled")==6,"every repeated death increments its source objective exactly once");
        BattlePlan pickups=new BattlePlan();pickups.mining=true;BattlePlan.Spawn miner=new BattlePlan.Spawn();miner.type=7;miner.unit="miney";miner.team=0;miner.ai="guard";miner.x=300;BattlePlan.Zone zone=new BattlePlan.Zone();zone.flag="mineyRescued";zone.actor="courier";miner.zones.add(zone);pickups.spawns.add(miner);
        BattlePlan.Spawn courier=new BattlePlan.Spawn();courier.type=0;courier.unit="u0";courier.team=0;courier.id="courier";courier.ai="marchRight";courier.x=300;pickups.spawns.add(courier);
        b=new OriginalBattle(specs(),0,new Progress(),pickups,new int[]{0});ticks(b,.1);ok(b.miners==0&&b.flag("mineyRescued")==1,"rescue miner is a one-time zone pickup, not a mine purchase");ticks(b,1);ok(b.flag("mineyRescued")==1,"collected rescue cannot increment twice");
        System.out.println("PASS "+count+" original scenario/economy/migration assertions");
    }
    private static String csv(int n,int value){StringBuilder b=new StringBuilder();for(int i=0;i<n;i++){if(i>0)b.append(',');b.append(value);}return b.toString();}
}
