package com.legendary.offline;

import java.nio.file.*;
import java.util.*;

/** Exercises actual imported unit stats, rewards, unlocks and all 50 encounters. */
public final class CampaignTest {
    public static void main(String[] args)throws Exception{
        List<UnitSpec> specs=new ArrayList<>();
        for(String line:Files.readAllLines(Paths.get(args[0]))){
            String[] s=line.split("\t");specs.add(new UnitSpec(s[0],Double.parseDouble(s[1]),Double.parseDouble(s[2]),Double.parseDouble(s[3]),Double.parseDouble(s[4]),Integer.parseInt(s[5]),Double.parseDouble(s[6]),Double.parseDouble(s[7]),Double.parseDouble(s[8]),Boolean.parseBoolean(s[9]),Integer.parseInt(s[10])));
        }
        Progress p=new Progress();
        for(int stage=0;stage<50;stage++){
            // Spend only earned currency. Distribute upgrades across the army and economy.
            for(int n=0;n<12;n++){
                int choice=(stage+n)%9;
                if(choice>=7||p.highestUnlocked()>=specs.get(choice).unlock)p.upgrade(choice);
            }
            BattleEngine b=new BattleEngine(specs,stage,p);int rotation=0;
            for(int tick=0;tick<24000&&b.state==BattleEngine.PLAYING;tick++){
                if(tick%12==0){
                    b.collectGems();
                    if(b.miners<4&&b.elapsed<25)b.hireMiner();
                    int lane=rotation++%3;
                    // Reinforce the closest dangerous lane with no frontline.
                    double danger=900;
                    for(BattleEngine.Actor a:b.actors)if(a.enemy&&a.hp>0&&a.x<danger){danger=a.x;lane=a.lane;}
                    for(int i:new int[]{4,2,0,1,5,3,6})if(b.unlocked(i))b.spawn(i,lane);
                    if(b.playerCastle<b.maxPlayerCastle*.65)b.castSpell(1);
                    if(b.actors.size()>8)b.castSpell(0);
                }
                b.update(1.0/30);
            }
            if(b.state!=BattleEngine.VICTORY)throw new AssertionError("Stage "+(stage+1)+" not completable by baseline strategy: state="+b.state+" allyCastle="+b.playerCastle+" enemyCastle="+b.enemyCastle+" elapsed="+b.elapsed);
            int reward=b.claimReward();if(reward<=0||p.stars[stage]<=0)throw new AssertionError("Missing campaign progression");
            p=Progress.decode(p.encode());
        }
        System.out.println("PASS 50/50 reconstructed stages using actual unit data and earned upgrades");
    }
}
