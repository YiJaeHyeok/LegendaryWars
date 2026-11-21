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
        // --- v0.3.0 Combat Depth, AI, Buff/Stun, Progress & SFX assertions ---
        // 1. Native 0x4b297c damage/armor type matchup
        ok(UnitSpec.applyTypeMatchup(100,UnitSpec.MELEE,UnitSpec.RANGED)==130,"melee vs ranged deals 1.30x");
        ok(UnitSpec.applyTypeMatchup(100,UnitSpec.RANGED,UnitSpec.MAGIC)==140,"ranged vs magic deals 1.40x");
        ok(UnitSpec.applyTypeMatchup(100,UnitSpec.MAGIC,UnitSpec.MELEE)==115,"magic vs melee deals 1.15x");
        ok(UnitSpec.applyTypeMatchup(100,UnitSpec.MELEE,UnitSpec.SIEGE)==0,"non-siege vs armorType==3 deals 0");
        ok(UnitSpec.applyTypeMatchup(100,UnitSpec.SIEGE,UnitSpec.SIEGE)==100,"siege bypasses armorType==3 immunity");
        // 2. Hit stun & hard stun + buffImmune
        List<UnitSpec> combatSpecs=specs();
        combatSpecs.get(0).damageType=UnitSpec.MELEE;combatSpecs.get(0).armorType=UnitSpec.MELEE;
        combatSpecs.get(8).damageType=UnitSpec.RANGED;combatSpecs.get(8).armorType=UnitSpec.RANGED;
        combatSpecs.get(9).buffImmune=true;combatSpecs.get(9).charmImmune=true;
        BattlePlan arena=new BattlePlan();arena.initialGems=100;arena.playerCastle=true;arena.width=1700;
        b=new OriginalBattle(combatSpecs,0,new Progress(),arena,new int[]{0});
        BattleEngine.Actor victim=new BattleEngine.Actor(8,true,1,500,100,10,0,30,25,1);victim.ai="guard";victim.homeX=500;
        BattleEngine.Actor immune=new BattleEngine.Actor(9,true,1,520,100,10,0,30,25,1);immune.ai="guard";
        b.actors.add(victim);b.actors.add(immune);
        UnitSpec.BuffDef stunDef=new UnitSpec.BuffDef();stunDef.name="testStun";stunDef.status="stun";stunDef.duration=2;stunDef.stun=2;
        b.applyBuff(null,victim,stunDef);b.applyBuff(null,immune,stunDef);
        ok(victim.stunTime>1.5&&immune.stunTime==0,"hard stun applies to normal unit and is blocked by buffImmune");
        double stunX=victim.x;victim.goal=400;ticks(b,.5);ok(victim.x==stunX,"stunned unit cannot move");
        // 3. Buffs: DoT (burn), speed scalor, fear, charm + restore
        UnitSpec.BuffDef burnDef=new UnitSpec.BuffDef();burnDef.name="Fire";burnDef.status="burn";burnDef.duration=2;burnDef.dps=20;
        b.applyBuff(null,victim,burnDef);double hpBeforeBurn=victim.hp;ticks(b,1);ok(victim.hp<hpBeforeBurn-15,"burn DoT ticks health down");
        UnitSpec.BuffDef slowDef=new UnitSpec.BuffDef();slowDef.name="slow";slowDef.duration=2;slowDef.speedScalor=-1;
        b.applyBuff(null,victim,slowDef);ok(Math.abs(victim.speed-15)<.01,"negative speedScalor divides speed");
        UnitSpec.BuffDef charmDef=new UnitSpec.BuffDef();charmDef.name="charm";charmDef.status="confuse";charmDef.duration=.5;
        b.applyBuff(null,victim,charmDef);b.applyBuff(null,immune,charmDef);
        ok(!victim.enemy&&immune.enemy,"charm flips team on vulnerable unit and respects charmImmune");
        ticks(b,.6);ok(victim.enemy,"charm expiration restores original team");
        // 4. AI state transitions: Guard hurt -> Hunt + callsForHelp wakes nearby guard -> target dies -> restores Guard
        b=new OriginalBattle(combatSpecs,0,new Progress(),arena,new int[]{0});
        BattleEngine.Actor heroA=new BattleEngine.Actor(0,false,1,400,200,40,0,30,40,1);heroA.ai="guard";heroA.homeX=400;
        BattleEngine.Actor guard1=new BattleEngine.Actor(8,true,1,425,200,10,0,30,25,1);guard1.ai="guard";guard1.homeX=425;
        BattleEngine.Actor guard2=new BattleEngine.Actor(8,true,1,520,200,10,0,30,25,1);guard2.ai="guard";guard2.homeX=520;
        b.actors.add(heroA);b.actors.add(guard1);b.actors.add(guard2);
        ticks(b,1.2);
        ok("hunt".equals(guard1.ai)&&"guard".equals(guard1.savedAi),"guarding unit switches to temporary hunt when hurt");
        ok("hunt".equals(guard2.ai)&&"guard".equals(guard2.savedAi),"nearby guarding ally wakes up via callsForHelp");
        heroA.hp=0;ticks(b,.2);
        ok("guard".equals(guard1.ai)&&guard1.savedAi==null,"unit restores savedAi when hunt target is lost");
        // FormUp rally
        b=new OriginalBattle(combatSpecs,0,new Progress(),arena,new int[]{0});
        BattleEngine.Actor f1=new BattleEngine.Actor(0,false,0,300,100,10,0,30,25,1);
        BattleEngine.Actor f2=new BattleEngine.Actor(0,false,2,500,100,10,0,30,25,1);
        b.actors.add(f1);b.actors.add(f2);b.selectActor(f2);b.formUp();
        ok(f1.goal>450&&"guard".equals(f1.ai)&&b.selected==null,"formUp rallies friendly units toward rally point");
        // 5. Progress support cost scaling & SFX / specialReady
        Progress sp=new Progress();sp.gems=1000;
        ok(sp.supportCost(7)==250&&sp.supportCost(8)==125,"initial support upgrade costs");
        ok(sp.upgradeSupport(7)&&sp.castleRank==1&&sp.gems==750&&sp.supportCost(7)==500,"castle upgrade scales by 250*(rank+1)");
        ok(sp.upgradeSupport(8)&&sp.mineRank==1&&sp.gems==625&&sp.supportCost(8)==250,"miner upgrade scales by 125*(rank+1)");
        b=new OriginalBattle(combatSpecs,0,new Progress(),arena,new int[]{0});
        b.spawn(0,1);ok(!b.specialReady(),"special button inactive without essence");
        b.essence=50;ok(b.specialReady(),"special button active with sufficient essence");
        ok(b.special(),"special activates when ready");
        List<String> sfx=b.drainSfx();ok(sfx.contains("spawn")&&sfx.contains("special"),"battle emits SFX keys for spawn and special");
        // 6. Vertical lane-change walk animation & tutorial offscreen archer / camera follow
        BattlePlan tut=new BattlePlan();tut.mode="tutorial";tut.width=1700;
        BattlePlan.Spawn tHero=new BattlePlan.Spawn();tHero.type=0;tHero.unit="u0";tHero.id="knighty";tHero.team=0;tHero.lane=1;tHero.x=720;tHero.plot=true;tut.spawns.add(tHero);
        BattlePlan.Spawn tArch=new BattlePlan.Spawn();tArch.type=8;tArch.unit="u8";tArch.id="skully";tArch.team=1;tArch.lane=1;tArch.x=1150;tArch.ai="hunt";tut.spawns.add(tArch);
        combatSpecs.get(8).primaryByTier=new UnitSpec.Attack[]{new UnitSpec.Attack()};combatSpecs.get(8).primaryByTier[0].range=500;
        b=new OriginalBattle(combatSpecs,0,new Progress(),tut,new int[0]);ticks(b,.25);
        BattleEngine.Actor th=b.actors.get(0),ta=b.actors.get(1);
        b.selectActor(th);b.moveSelected(th.x,2);b.update(1.0/60);
        ok(th.moving,"vertical lane change sets moving=true so walk animation plays");
        ok(!ta.attacking&&th.hp==100,"off-screen tutorial enemy must walk onto screen before attacking");
        th.x=920;b.moveSelected(1020,1);ticks(b,1.0);ok(b.camera>490,"tutorial camera follows hero toward right edge");
        System.out.println("PASS "+count+" original scenario/economy/migration assertions");
    }
    private static String csv(int n,int value){StringBuilder b=new StringBuilder();for(int i=0;i<n;i++){if(i>0)b.append(',');b.append(value);}return b.toString();}
}