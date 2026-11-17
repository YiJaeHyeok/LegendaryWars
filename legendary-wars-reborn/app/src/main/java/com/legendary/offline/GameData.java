package com.legendary.offline;

import android.content.res.AssetManager;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reads only build-generated local JSON, never original executable/config scripts. */
public final class GameData {
    public final JSONObject json,atlases;
    public final List<UnitSpec> specs=new ArrayList<>();
    public final List<JSONObject> units=new ArrayList<>();
    public final List<BattlePlan> plans=new ArrayList<>();
    public final int[] heroTypes;
    private final Map<String,String> spriteSheets=new HashMap<>();
    public GameData(AssetManager assets)throws IOException,JSONException{this(read(assets));}
    private static String read(AssetManager assets)throws IOException{
        try(InputStream in=assets.open("game-data.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(out.size()+n>16_000_000)throw new IOException("Data limit");out.write(buf,0,n);}
            return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }
    }
    public GameData(String generatedJson)throws IOException,JSONException{
        if(generatedJson==null||generatedJson.length()>16_000_000)throw new IOException("Data limit");json=new JSONObject(generatedJson);
        atlases=json.getJSONObject("atlases");JSONArray list=json.getJSONArray("units");
        if(list.length()<21||list.length()>80)throw new IOException("Unit data incomplete");
        for(int i=0;i<list.length();i++){
            JSONObject u=list.getJSONObject(i);units.add(u);
            JSONObject primary=u.getJSONArray("variants").getJSONObject(0).getJSONObject("states").optJSONObject("primary");
            double interval=u.optDouble("interval",1);
            UnitSpec spec=new UnitSpec(u.getString("id"),u.getDouble("hp"),u.getDouble("attack"),u.getDouble("defense"),u.getDouble("speed"),u.getInt("cost"),u.getDouble("cooldown"),u.getDouble("range"),interval,u.getBoolean("hero"),u.getInt("unlock"));
            spec.hitDelay=u.optDouble("hitDelay",.3);spec.specialCost=u.optInt("specialCost",10);spec.specialDamage=u.optDouble("specialDamage",40);spec.reward=u.optInt("reward",5);spec.healer=u.optBoolean("healer");spec.moonstonePrice=u.optInt("moonstonePrice");spec.critChance=u.optDouble("critChance");
            JSONObject up=u.optJSONObject("upgrades");if(up!=null){JSONObject a=up.optJSONObject("attackPoints"),h=up.optJSONObject("hitPoints"),d=up.optJSONObject("defensePoints");if(a!=null)spec.attackUpgrade=a.optDouble("upgradeAmount",1);if(h!=null)spec.hpUpgrade=h.optDouble("upgradeAmount",5);if(d!=null)spec.defenseUpgrade=d.optDouble("upgradeAmount",1);}spec.gearCost=u.optInt("gearCost",1);
            JSONArray variants=u.getJSONArray("variants");spec.primaryByTier=new UnitSpec.Attack[variants.length()];spec.specialByTier=new UnitSpec.Attack[variants.length()];for(int j=0;j<variants.length();j++){spec.primaryByTier[j]=attack(variants.getJSONObject(j).optJSONObject("primaryCombat"));spec.specialByTier[j]=attack(variants.getJSONObject(j).optJSONObject("specialCombat"));}
            specs.add(spec);
        }
        JSONArray heroes=json.getJSONArray("heroTypes");heroTypes=new int[heroes.length()];for(int i=0;i<heroes.length();i++)heroTypes[i]=heroes.getInt(i);
        JSONArray campaign=json.getJSONObject("campaign").getJSONArray("levels");if(campaign.length()!=73)throw new IOException("Campaign data incomplete");
        for(int i=0;i<campaign.length();i++)plans.add(plan(campaign.getJSONObject(i)));
    }
    private UnitSpec.Attack attack(JSONObject o){UnitSpec.Attack a=new UnitSpec.Attack();if(o==null){a.times=new double[0];a.projectiles=new double[0];return a;}a.interval=Math.max(.05,o.optDouble("interval",1));a.range=Math.max(0,o.optDouble("range",30));a.type=o.optString("type","attack");a.allLanes=o.optBoolean("hitAllLanes");a.speedScalor=o.optDouble("speedScalor");JSONArray hits=o.optJSONArray("hits");int n=hits==null?0:hits.length();a.times=new double[n];a.projectiles=new double[n];for(int i=0;i<n;i++){JSONObject h=hits.optJSONObject(i);a.times[i]=h.optDouble("time");a.projectiles[i]=h.optDouble("projectileSpeed");a.heal|=h.optBoolean("heal");a.area|=h.optBoolean("area");}return a;}
    public String label(int type){return units.get(type).optString("label",specs.get(type).id);}
    public String spriteAtlas(int region,String frame){String key=region+":"+frame;if(spriteSheets.containsKey(key))return spriteSheets.get(key);String prefix=new String[]{"Grass","Snow","Desert","Swamp","Volcano","Netherworld","Skyhaven"}[region];String found=null;for(String name:new String[]{prefix+"Scene-hd",prefix+"SceneScrolling-hd",prefix+"SceneScrolling2-hd"}){JSONObject a=atlases.optJSONObject(name);if(a!=null&&a.optJSONObject("frames").has(frame)){found=name;break;}}if(found==null){Iterator<String> keys=atlases.keys();while(keys.hasNext()){String name=keys.next();if(atlases.optJSONObject(name).optJSONObject("frames").has(frame)){found=name;break;}}}spriteSheets.put(key,found);return found;}
    public JSONObject variant(int type,int tier){
        JSONArray a=units.get(type).optJSONArray("variants");return a.optJSONObject(Math.min(a.length()-1,Math.max(0,tier-1)));
    }
    public int type(String id){if(id==null)return -1;if(id.equals("Zombie"))id="zombie";if(id.equals("vampy{2]"))id="vampy";for(int i=0;i<specs.size();i++)if(specs.get(i).id.equals(id))return i;return -1;}
    private BattlePlan.Trigger trigger(JSONObject o,int depth){
        if(o==null||depth>16)return null;BattlePlan.Trigger t=new BattlePlan.Trigger();t.type=o.optString("type","unknown");t.target=o.optString("target",null);t.parentTime=o.optDouble("parentTime",0);
        JSONArray a=o.optJSONArray("conditions"),s=o.optJSONArray("states");t.conditions=strings(a);t.states=strings(s);t.parent=trigger(o.optJSONObject("parent"),depth+1);return t;
    }
    private String[] strings(JSONArray a){if(a==null)return new String[0];String[] s=new String[Math.min(20,a.length())];for(int i=0;i<s.length;i++)s[i]=a.optString(i);return s;}
    private void event(BattlePlan.Event e,JSONObject o){e.time=Math.max(0,o.optDouble("time",0));e.trigger=trigger(o.optJSONObject("trigger"),0);}
    private BattlePlan plan(JSONObject o)throws JSONException{
        BattlePlan p=new BattlePlan();p.id=o.getString("sourceId");p.title=o.optString("title",p.id);p.mode=o.optString("mode","campaign");p.region=o.getInt("region")-1;p.number=o.getInt("stage");p.slots=Math.max(0,Math.min(7,o.optInt("maxUnitSlots",3)));p.width=Math.max(600,Math.min(16000,o.optDouble("worldWidth",1700)));p.initialGems=Math.max(0,Math.min(9999,o.optInt("initialGem",0)));
        JSONObject hp=o.optJSONObject("castleHp");p.playerCastle=hp!=null&&!hp.isNull("player");p.playerHp=hp==null?500:hp.optDouble("player",500);p.enemyHp=hp==null?0:hp.optDouble("enemy",0);p.mining=o.optJSONObject("mining").optBoolean("enabled");
        JSONArray a=o.getJSONArray("waves");for(int i=0;i<a.length();i++){JSONObject w=a.getJSONObject(i);BattlePlan.Wave v=new BattlePlan.Wave();v.start=w.optDouble("startTime")+w.optDouble("eventTime");v.delay=Math.max(.1,w.optDouble("delayBetweenSpawns",5));v.count=Math.max(1,w.optInt("totalSpawns",1));v.repeat=w.optBoolean("repeatForever");p.waves.add(v);}
        for(int i=0;i<p.waves.size();i++){BattlePlan.Wave w=p.waves.get(i);for(int j=i+1;j<p.waves.size();j++)if(!p.waves.get(j).repeat&&p.waves.get(j).start>w.start)w.stopAt=Math.min(w.stopAt,p.waves.get(j).start);}
        a=o.getJSONArray("spawns");for(int i=0;i<a.length()&&i<10000;i++){
            JSONObject s=a.getJSONObject(i),c=s.optJSONObject("customizer");BattlePlan.Spawn v=new BattlePlan.Spawn();event(v,s);v.unit=s.optString("unitId");v.type=type(v.unit);v.id=s.optString("scriptId",null);v.team=s.optInt("team",1);v.lane=Math.max(0,Math.min(2,s.optInt("lane",1)));v.tier=Math.max(1,Math.min(6,s.optInt("tier",1)));if(s.optString("unitId").equals("vampy{2]"))v.tier=2;
            v.x=s.optDouble("x",Double.NaN);v.left=s.optBoolean("fromLeft");v.hp=Math.max(.01,s.optDouble("hpFactor",1));v.attack=c==null?0:c.optDouble("scaleAttack",0);v.speed=c==null?0:c.optDouble("scaleSpeed",0);v.haste=c==null?0:c.optDouble("scaleHaste",0);v.range=c==null?0:c.optDouble("scaleRange",0);v.addHp=c==null?0:c.optDouble("addHp",0);v.wave=s.optInt("waveIndex",-1);v.plot=s.optBoolean("plot");v.sprite=s.optString("spriteFrame",null);v.ai=s.optString("ai",c==null?"attack":c.optString("ai","attack"));v.hunt=v.ai.equals("hunt");if(c!=null)v.scale=Math.max(.2,Math.min(3,c.optDouble("scaleSize",1)));p.spawns.add(v);
            if(c!=null){v.selectable=c.optBoolean("selectable",true);v.untargetable=c.optBoolean("untargetable");if(v.sprite==null)v.sprite=c.optString("spriteFrame",null);JSONArray zones=c.optJSONArray("zones");if(zones!=null)for(int j=0;j<Math.min(32,zones.length());j++){JSONObject zone=zones.optJSONObject(j);if(zone==null)continue;JSONArray buffs=zone.optJSONArray("buffs");if(buffs==null)continue;for(int k=0;k<Math.min(16,buffs.length());k++){JSONObject buff=buffs.optJSONObject(k);if(buff==null)continue;BattlePlan.Zone z=new BattlePlan.Zone();z.left=zone.optDouble("left",-20);z.width=Math.max(1,Math.min(1000,zone.optDouble("width",40)));z.once=zone.optBoolean("oneTime",true);z.damage=Math.max(0,buff.optDouble("damage"));JSONObject attr=buff.optJSONObject("unitModifierAttributes");if(buff.optString("unitModifier").equals("ModifyFlagBuff")&&attr!=null){z.flag=attr.optString("flag");z.actor=attr.optString("scriptIDRestriction",null);z.amount=Math.max(0,Math.min(100,attr.optInt("amount",1)));}if(z.damage>0||z.flag!=null)v.zones.add(z);}}}
        }
        a=o.getJSONArray("text");for(int i=0;i<a.length()&&i<1000;i++){JSONObject s=a.getJSONObject(i);BattlePlan.Cue v=new BattlePlan.Cue();event(v,s);v.text=s.optString("text");v.title=s.optString("title","");v.kind=s.optString("kind");p.cues.add(v);}
        a=o.getJSONArray("outcomes");for(int i=0;i<a.length();i++){JSONObject s=a.getJSONObject(i);BattlePlan.Outcome v=new BattlePlan.Outcome();event(v,s);v.victory=s.optBoolean("victory");p.outcomes.add(v);}
        a=o.optJSONArray("commands");if(a!=null)for(int i=0;i<a.length()&&i<1000;i++){JSONObject s=a.getJSONObject(i);BattlePlan.Command v=new BattlePlan.Command();event(v,s);v.type=s.optString("type");v.id=s.optString("id",s.optString("target",""));v.bool=s.optBoolean("value");v.increment=s.optBoolean("increment");v.value=s.optDouble("speed",s.optDouble("amount",s.optDouble("value",0)));p.commands.add(v);}
        return p;
    }
}
