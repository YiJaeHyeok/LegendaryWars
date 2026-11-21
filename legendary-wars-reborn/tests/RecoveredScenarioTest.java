package com.legendary.offline;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Exercises the APK's real JSON loader and recovered script definitions. */
public final class RecoveredScenarioTest {
    private static int checks;
    private static void ok(boolean b,String s){if(!b)throw new AssertionError(s);checks++;}
    private static void step(OriginalBattle b,double t){for(int i=0;i<Math.ceil(t*60);i++){if(b.cue!=null)b.acknowledge();b.update(1.0/60);}}
    public static void main(String[] args)throws Exception{
        GameData d=new GameData(new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8));
        ok(d.specs.size()==69&&d.heroTypes.length==12&&d.plans.size()==73,"load all recovered data with the real game loader");
        UnitSpec knight=d.specs.get(0);ok(knight.hp==100&&knight.attack==20&&knight.speed==25&&knight.cost==10&&knight.cooldown==5,"Knighty's source stats");
        ok(Math.abs(knight.interval-10.0/15)<1e-10,"native default FPS15 and ten-frame attack cycle");
        ok(Math.abs(d.specs.get(d.type("centaur")).interval-16.0/24)<1e-10,"source Centaur FPS24 overrides native default");
        ok(d.specs.get(d.type("rogue")).profile(1,false).times.length==2,"both source Rogue dagger events survive");
        ok(d.specs.get(d.type("obstacle")).profile(1,false).times.length==0,"static crates must not acquire a synthesized attack");
        ok("GrassSceneScrolling-hd".equals(d.spriteAtlas(0,"grass-scrolling-wheel.png"))&&"SwampSceneScrolling-hd".equals(d.spriteAtlas(3,"swamp-scrolling-wheel.png")),"literal hazards resolve their scrolling atlases and remain visible");
        OriginalBattle b=new OriginalBattle(d.specs,0,new Progress(),d.plans.get(0),new int[0]);step(b,.3);
        BattleEngine.Actor hero=b.selected;ok(hero!=null&&hero.plot&&hero.x==720,"original tutorial creates and selects Knighty");
        ok(Math.abs(hero.speed-27.5)<1e-9&&Math.abs(hero.attack-44)<1e-9,"native source customizers apply to original base stats");
        double elapsed=b.elapsed;step(b,2);ok(b.elapsed>elapsed,"source dialogue acknowledgement advances the tutorial");
        // Try a small set of legal dodge/special timings. No HP, stats, or state are edited.
        for(int strategy=0;strategy<12;strategy++){
        if(strategy>0){b=new OriginalBattle(d.specs,0,new Progress(),d.plans.get(0),new int[0]);step(b,.3);hero=b.selected;}
        for(int tick=0;tick<12000&&b.state==BattleEngine.PLAYING;tick++){
            if(b.cue!=null){b.acknowledge();continue;}
            BattleEngine.Actor target=null;double nearest=Double.MAX_VALUE;
            for(BattleEngine.Actor a:b.actors)if(a.enemy&&a.hp>0){double distance=Math.abs(a.x-hero.x);if(distance<nearest){nearest=distance;target=a;}}
            if(target!=null){if(d.specs.get(target.type).range>100&&nearest>35){if(tick%new int[]{12,18,24,6}[strategy%4]==0)b.moveSelected(target.x,(hero.lane+1)%3);}else b.target(target);if(nearest<new int[]{60,45,35}[strategy/4]&&target.hp>35)b.special();}b.update(1.0/60);
        }
        if(b.state==BattleEngine.VICTORY){System.out.println("Tutorial input strategy "+strategy+" won with HP="+hero.hp);break;}
        }
        ok(b.state==BattleEngine.VICTORY&&b.kills>0,"fresh recovered tutorial is winnable through movement/targeting state="+b.state+" elapsed="+b.elapsed+" hp="+hero.hp+" kills="+b.kills+" actors="+b.actors.size()+" cue="+(b.cue==null?"none":b.cue.text));
        // Casual player check: simply tap nearest visible enemy and press special when ready (no lane-dodge bot)
        OriginalBattle casual=new OriginalBattle(d.specs,0,new Progress(),d.plans.get(0),new int[0]);step(casual,.3);
        BattleEngine.Actor cHero=casual.selected;
        for(int tick=0;tick<15000&&casual.state==BattleEngine.PLAYING;tick++){
            if(casual.cue!=null){casual.acknowledge();continue;}
            if(tick%20==0){
                BattleEngine.Actor target=null;double nearest=Double.MAX_VALUE;
                for(BattleEngine.Actor a:casual.actors)if(a.enemy&&a.hp>0&&a.x>=casual.camera&&a.x<=casual.camera+568){double dist=Math.abs(a.x-cHero.x);if(dist<nearest){nearest=dist;target=a;}}
                if(target!=null){casual.target(target);if(nearest<70&&casual.specialReady())casual.special();}
            }
            casual.update(1.0/60);
        }
        System.out.println("Casual tutorial player result: state="+casual.state+" HP="+cHero.hp+" kills="+casual.kills);
        ok(casual.state==BattleEngine.VICTORY&&cHero.hp>=30,"casual player can beat tutorial comfortably without frame-perfect dodging");
        Progress p=new Progress();p.stars[0]=3;b=new OriginalBattle(d.specs,1,p,d.plans.get(1),new int[]{0});
        ok(!b.hireMiner(),"mining tutorial action waits for source switch, preventing early-input softlock");step(b,2);
        ok(b.hireMiner(),"source mining event is emitted after its switch arms");step(b,20);b.collectGems();step(b,2.2);
        ok(b.gems>=10&&b.spawn(0,1),"mining and gem triggers enable Knighty recruitment");step(b,1);
        boolean intro=false;for(BattleEngine.Actor a:b.actors)if("introSkully".equals(a.scriptId))intro=true;
        ok(intro,"source recruitment event activates the tutorial enemy");
        for(int tick=0;tick<30000&&b.state==BattleEngine.PLAYING;tick++){
            if(b.cue!=null){b.acknowledge();continue;}
            if(tick%24==0){b.collectGems();b.spawn(0,1);b.selected=null;b.selectedType=0;b.commandOrder(1);BattleEngine.Actor target=null;for(BattleEngine.Actor a:b.actors)if(a.enemy&&a.hp>0&&(target==null||a.x<target.x))target=a;if(target!=null)b.target(target);}
            b.update(1.0/60);
        }
        ok(b.state==BattleEngine.VICTORY,"recovered mining/recruitment mission reaches its original finalSkully victory, state="+b.state+" t="+b.elapsed);
        ok(UnitSpec.scale(100,.2)==120&&Math.abs(UnitSpec.scale(100,-.2)-100/1.2)<1e-9,"native positive and negative scaling");
        ok(UnitSpec.mitigate(100,50,true)==50&&UnitSpec.mitigate(100,50,false)==75&&UnitSpec.mitigate(100,999,true)==25,"native percentage defense and minimum quarter damage");
        ok(UnitSpec.roll(100,0,false)==75&&UnitSpec.roll(100,.99999,false)==100&&UnitSpec.roll(100,0,true)==150,"native random damage interval and doubled critical roll");
        ok(knight.profile(1,true).speedScalor==.5&&d.specs.get(d.type("golem")).profile(1,true).speedScalor==4,"original special movement scalors are retained");
        ok(d.specs.get(d.type("caty")).damageType==UnitSpec.SIEGE&&d.specs.get(d.type("caty")).armorType==UnitSpec.MELEE,"source Caty siege damageType and melee armorType recovered");
        ok(d.specs.get(d.type("elfy")).damageType==UnitSpec.RANGED&&d.specs.get(d.type("wizard")).damageType==UnitSpec.MAGIC,"source Elfy ranged and Wizard magic types assigned");
        ok(d.specs.get(d.type("golem")).buffImmune&&d.specs.get(d.type("troll")).buffImmune&&!knight.buffImmune,"source buffImmune flags recovered");
        ok(d.specs.get(d.type("dwarfy")).profile(2,true).buffs.length>0&&"burn".equals(d.specs.get(d.type("dwarfy")).profile(2,true).buffs[0].status),"source tier-2 Dwarfy special burn buff recovered");
        ok(d.json.optJSONObject("sfx")!=null&&d.json.optJSONObject("sfx").length()==18,"curated 18 validated WAV SFX entries packaged");
        Progress rescueProgress=new Progress();for(int i=0;i<4;i++)rescueProgress.stars[i]=3;
        b=new OriginalBattle(d.specs,4,rescueProgress,d.plans.get(4),new int[0]);step(b,.3);ok(b.selected!=null&&"introelfy".equals(b.selected.scriptId)&&b.scrollSpeed==125,"original scrolling speed and controlled Elfy are applied");
        for(int tick=0;tick<10800&&b.state==BattleEngine.PLAYING&&b.flag("elfysRecruited")<10;tick++){
            if(b.cue!=null){b.acknowledge();continue;}BattleEngine.Actor next=null;
            for(BattleEngine.Actor a:b.actors)if(a.hp>0&&!a.collected&&!a.zones.isEmpty()&&a.zones.get(0).flag!=null&&a.x>b.selected.x&&(next==null||a.x<next.x))next=a;
            int wanted=next==null?b.selected.lane:next.lane;boolean danger=false;for(BattleEngine.Actor a:b.actors)if(a.hp>0&&a.untargetable&&a.x-b.selected.x>-25&&a.x-b.selected.x<160&&a.lane==wanted)danger=true;
            if(danger){for(int lane=0;lane<3;lane++){boolean safe=true;for(BattleEngine.Actor a:b.actors)if(a.hp>0&&a.untargetable&&a.x-b.selected.x>-25&&a.x-b.selected.x<220&&a.lane==lane)safe=false;if(safe){wanted=lane;break;}}}
            if(b.selected.lane!=wanted)b.moveSelected(b.selected.x,wanted);
            b.update(1.0/60);
        }
        ok(b.flag("elfysRecruited")>=1,"actual rescue stream increments literal elf pickup objective, state="+b.state+" count="+b.flag("elfysRecruited"));
        ok(b.flag("elfysRecruited")==10&&b.state==BattleEngine.VICTORY,"fifth source mission is winnable with literal pickup zones and original rescue threshold");
        System.out.println("Elfy rescue source test: rescued="+b.flag("elfysRecruited")+", state="+b.state+", elapsed="+b.elapsed);
        System.out.println("PASS "+checks+" actual source loader/tutorial/native-combat assertions");
    }
}