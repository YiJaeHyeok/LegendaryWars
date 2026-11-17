package com.legendary.offline;

import java.util.*;

public final class OriginalBattle extends BattleEngine {
    public final BattlePlan plan;
    public final int[] loadout;
    public Actor selected;
    public int selectedType=-1;
    public BattlePlan.Cue cue;
    public double camera=0,scrollSpeed;
    private final boolean[] spawned,cued,commanded,ended;
    private final int[] repeats;
    private final Map<String,Double> triggered=new HashMap<>(),events=new HashMap<>();
    private final Map<Integer,Boolean> switches=new HashMap<>();
    private final Map<String,Integer> flags=new HashMap<>();
    private final ArrayDeque<BattlePlan.Cue> messages=new ArrayDeque<>();
    private final double[] minerTimers=new double[8];
    private final Random random=new Random(0x4c574152L);
    private final List<BattlePlan.Event> scriptEvents=new ArrayList<>();
    private double minerCooldown,freeze,chatEnd=-1,lastActivity;
    public OriginalBattle(List<UnitSpec>s,int stage,Progress progress,BattlePlan plan,int[]loadout){
        super(s,stage,progress);this.plan=plan;this.loadout=loadout.clone();
        gems=plan.initialGems;essence=0;miners=0;order=0;
        maxPlayerCastle=playerCastle=plan.playerHp+progress.castleRank*500;
        maxEnemyCastle=enemyCastle=plan.enemyHp;camera=plan.tutorial()?480:0;
        spawned=new boolean[plan.spawns.size()];repeats=new int[spawned.length];cued=new boolean[plan.cues.size()];commanded=new boolean[plan.commands.size()];ended=new boolean[plan.outcomes.size()];
        scriptEvents.addAll(plan.spawns);scriptEvents.addAll(plan.commands);scriptEvents.addAll(plan.cues);scriptEvents.addAll(plan.outcomes);
    }
    @Override public int totalWaves(){return plan.waves.size();}
    @Override public boolean unlocked(int type){return type>=0&&type<specs.size()&&specs.get(type).hero&&progress.highestUnlocked()>=specs.get(type).unlock&&(specs.get(type).moonstonePrice==0||progress.purchased[type]);}
    private boolean selectedInLoadout(int type){for(int t:loadout)if(t==type)return true;return false;}
    @Override public boolean spawn(int type,int lane){
        if(paused||cue!=null&&"chat".equals(cue.kind)||state!=PLAYING||!selectedInLoadout(type)||!unlocked(type)||lane<0||lane>2||cooldowns[type]>0||actors.size()>=160||!eventReady("hudEvent:"+specs.get(type).id))return false;
        UnitSpec s=specs.get(type);if(gems<s.cost)return false;gems-=s.cost;cooldowns[type]=s.cooldown;
        Actor a=make(type,false,lane,195,1,0,0,progress.gear[type]);a.goal=order>0?plan.width-150:order<0?195:Double.NaN;a.retreat=order<0;actors.add(a);selected=a;selectedType=type;emit("hudEvent:"+s.id);return true;
    }
    @Override public boolean hireMiner(){
        if(paused||state!=PLAYING||chatting()||!plan.mining||miners>=1||minerCooldown>0||!eventReady("hudEvent:mining"))return false;
        minerTimers[miners++]=0;minerCooldown=15;emit("hudEvent:mining");emit("hudEvent:miney");return true;
    }
    @Override public void collectGems(){if(paused||chatting()||state!=PLAYING)return;int old=gems;for(Gem g:gemsOnField)gems=Math.min(9999,gems+g.value);gemsOnField.clear();if(gems!=old)emit("gemAmountChanged:");}
    public void selectActor(Actor a){if(a!=null&&!a.enemy&&a.hp>0&&a.selectable){selected=a;selectedType=a.type;emit("levelSelectedUnitEvent:");}}
    public void moveSelected(double x,int lane){if(paused||chatting()||state!=PLAYING||lane<0||lane>2)return;double goal=Math.max(scrollSpeed>0?camera+45:90,Math.min(right()-45,x));for(Actor a:actors)if(!a.enemy&&a.hp>0&&(a==selected||selected==null&&(selectedType<0||a.type==selectedType))){a.retreat=false;a.goal=goal;a.lane=lane;a.target=null;if(!a.usingSpecial){a.attackActive=false;a.attacking=false;}}}
    public void target(Actor enemy){if(paused||chatting()||state!=PLAYING||enemy==null||!enemy.enemy||enemy.untargetable||enemy.hp<=0)return;for(Actor a:actors)if(!a.enemy&&a.hp>0&&(a==selected||selected==null&&(selectedType<0||a.type==selectedType))){a.retreat=false;a.target=enemy;a.lane=enemy.lane;a.goal=enemy.x;}}
    public void commandOrder(int value){if(paused||chatting()||state!=PLAYING)return;order=value;for(Actor a:actors)if(!a.enemy&&a.hp>0&&(selectedType<0||a.type==selectedType)){a.target=null;a.retreat=value<0;a.goal=value<0?195:value>0?plan.width-150:Double.NaN;if(value<0){a.attackActive=false;a.attacking=false;}}}
    public boolean special(){
        if(paused||chatting()||state!=PLAYING||selected==null||selected.hp<=0||selected.usingSpecial||selected.specialTime>0)return false;UnitSpec s=specs.get(selected.type);if(essence<s.specialCost)return false;
        essence-=s.specialCost;selected.specialTime=s.profile(selected.tier,true).interval/UnitSpec.scale(1,selected.haste);selected.animationAge=0;selected.usingSpecial=true;selected.attackActive=true;selected.attackAge=0;selected.hitIndex=0;selected.specialHits.clear();
        return true;
    }
    @Override public boolean castSpell(int kind){
        if(kind<0||kind>2||paused||chatting()||state!=PLAYING||spellCooldowns[kind]>0)return false;
        spellCooldowns[kind]=new double[]{45,60,75}[kind];spellFlash=.5;
        if(kind==2)freeze=5;
        else for(Actor a:actors)if(a.enemy&&a.hp>0){double x=a.x-camera;if(x>=0&&x<=570)damage(null,a,kind==0?90:200);}
        return true;
    }
    public void acknowledge(){if(cue!=null){chatEnd=elapsed;cue=null;if(!messages.isEmpty())cue=messages.removeFirst();}}
    private boolean chatting(){return cue!=null&&"chat".equals(cue.kind);}
    private void emit(String key){events.put(key,elapsed);
        for(int i=0;i<plan.commands.size();i++){BattlePlan.Command c=plan.commands.get(i);if(c.type.equals("flag")&&c.increment&&edge(c.trigger)&&key.equals(c.trigger.type+":"+(c.trigger.target==null?"":c.trigger.target))&&conditions(c.trigger.conditions)){flags.put(c.id,flags.getOrDefault(c.id,0)+(int)c.value);commanded[i]=true;}}
        for(BattlePlan.Event e:scriptEvents)arm(e.trigger,key);
    }
    private boolean edge(BattlePlan.Trigger t){return t!=null&&(t.type.equals("die")||t.type.equals("destroyed")||t.type.equals("controllerDie"));}
    private void arm(BattlePlan.Trigger t,String event){if(t==null)return;arm(t.parent,event);if((t.type+":"+(t.target==null?"":t.target)).equals(event)&&conditions(t.conditions)){double parent=t.parent==null?0:activation(t.parent);if(parent>=0&&elapsed>=parent+t.parentTime)triggered.putIfAbsent(t.key(),elapsed);}}
    private boolean eventReady(String key){for(BattlePlan.Event e:scriptEvents){BattlePlan.Trigger t=e.trigger;if(t!=null&&(t.type+":"+(t.target==null?"":t.target)).equals(key)&&!triggered.containsKey(t.key())&&!conditions(t.conditions))return false;}return true;}
    public boolean miningReady(){return !paused&&!chatting()&&state==PLAYING&&plan.mining&&miners<1&&minerCooldown<=0&&eventReady("hudEvent:mining");}
    public int flag(String id){return flags.getOrDefault(id,0);}
    private double right(){return plan.mode.equals("scrolling")?camera+570:plan.width;}
    @Override public void update(double dt){
        if(paused||state!=PLAYING||!Double.isFinite(dt)||dt<=0)return;
        if(cue!=null&&"chat".equals(cue.kind))return;
        dt=Math.min(.05,dt);elapsed+=dt;
        schedule();
        camera+=scrollSpeed*dt;
        for(int i=0;i<cooldowns.length;i++)cooldowns[i]=Math.max(0,cooldowns[i]-dt);
        for(int i=0;i<3;i++)spellCooldowns[i]=Math.max(0,spellCooldowns[i]-dt);
        freeze=Math.max(0,freeze-dt);spellFlash=Math.max(0,spellFlash-dt);minerCooldown=Math.max(0,minerCooldown-dt);
        for(int i=0;i<miners;i++){minerTimers[i]+=dt;if(minerTimers[i]>=7){minerTimers[i]-=7;if(gemsOnField.size()<16)gemsOnField.add(new Gem(5+Math.min(5,progress.mineRank),230+i*15));}}
        for(Iterator<Gem>it=gemsOnField.iterator();it.hasNext();){Gem g=it.next();g.age+=dt;if(g.age>=3.5){gems=Math.min(9999,gems+g.value);emit("gemAmountChanged:");it.remove();}}
        for(Actor a:actors){
            if(a.matchScroll){a.x+=scrollSpeed*dt;if(!Double.isNaN(a.goal))a.goal+=scrollSpeed*dt;}
            double animationDt=UnitSpec.scale(dt,a.haste);a.age+=dt;a.animationAge+=animationDt;a.flash=Math.max(0,a.flash-dt);a.specialTime=Math.max(0,a.specialTime-dt);double laneDelta=a.lane-a.laneVisual;a.laneVisual+=Math.signum(laneDelta)*Math.min(Math.abs(laneDelta),a.speed*dt/45);
            if(a.hp<=0){if(a.deathAge==0)dead(a);a.deathAge+=dt;continue;}
            if(a.enemy&&freeze>0){a.moving=false;continue;}
            a.attackClock=Math.max(0,a.attackClock-dt);
            if(a.attackActive){a.attackAge+=animationDt;UnitSpec.Attack profile=specs.get(a.type).profile(a.tier,a.usingSpecial);
                if(a.usingSpecial&&profile.type.equals("moveForwardAttack")){a.x=Math.max(scrollSpeed>0?camera+40:40,Math.min(right()-40,a.x+(a.faceLeft?-1:1)*UnitSpec.scale(a.speed,profile.speedScalor)*dt));for(Actor t:actors)if(t.hp>0&&!t.untargetable&&t.enemy!=a.enemy&&t.lane==a.lane&&Math.abs(t.x-a.x)<=profile.range&&a.specialHits.add(t))damage(a,t,UnitSpec.roll(UnitSpec.scale(specs.get(a.type).specialDamage,a.attackScalor),random.nextDouble(),false));}
                while(a.hitIndex<profile.times.length&&a.attackAge+1e-9>=profile.times[a.hitIndex])impact(a,profile,a.hitIndex++);if(a.attackAge>=profile.interval){a.attackActive=false;a.usingSpecial=false;a.pending=null;a.pendingCastle=false;}}
            if(a.retreat){move(a,dt);a.attacking=false;continue;}
            Actor target=a.target!=null&&a.target.hp>0?a.target:null;double nearest=target==null?Double.MAX_VALUE:Math.abs(a.x-target.x);
            if(target==null)for(Actor t:actors)if(t.hp>0&&!t.untargetable&&t.enemy!=a.enemy&&t.lane==a.lane){double d=Math.abs(t.x-a.x);if(d<nearest){nearest=d;target=t;}}
            if(a.hunt&&target==null){double best=Double.MAX_VALUE;for(Actor t:actors)if(t.hp>0&&!t.untargetable&&t.enemy!=a.enemy){double d=Math.abs(t.x-a.x)+Math.abs(t.lane-a.lane)*45;if(d<best){best=d;target=t;}}if(target!=null){a.lane=target.lane;a.goal=target.x;nearest=Math.abs(a.x-target.x);}}
            boolean inRange=target!=null&&target.lane==a.lane&&Math.abs(target.laneVisual-a.laneVisual)<.25&&nearest<=a.range+15;
            boolean atCastle=(!a.enemy&&plan.enemyHp>0||a.enemy&&plan.playerCastle)&&Math.abs((a.enemy?80:plan.width-80)-a.x)<=a.range+30;
            a.attacking=inRange||atCastle;a.moving=false;
            if(a.attacking&&a.specialTime<=0){if(!a.attackActive&&a.attackClock<=0){a.attackClock=a.interval;a.animationAge=0;a.attackAge=0;a.hitIndex=0;a.attackActive=true;a.usingSpecial=false;a.pending=inRange?target:null;a.pendingCastle=!inRange&&atCastle;if(target!=null)a.faceLeft=target.x<a.x;}}
            else if(!a.attackActive&&a.specialTime<=0){if(a.target!=null&&a.target.hp>0)a.goal=a.target.x;move(a,dt);}
        }
        zones();
        for(Iterator<Actor>it=actors.iterator();it.hasNext();){Actor a=it.next();if(a.deathAge>1.4||a.collected||scrollSpeed>0&&a.x<camera-100)it.remove();}
        for(Iterator<Shot>it=shots.iterator();it.hasNext();){Shot s=it.next();s.age+=dt;if(s.age>=s.duration){if(s.target!=null&&s.target.hp>0&&Math.abs(s.target.laneVisual-s.lane)<.35&&Math.abs(s.target.x-s.to)<=35)damage(s.source,s.target,s.damage);it.remove();}}
        if(plan.playerCastle&&playerCastle<=0){emit("die:castle-team0");emit("destroyed:castle-team0");state=DEFEAT;}
        else if(plan.enemyHp>0&&enemyCastle<=0){emit("die:castle-team1");emit("destroyed:castle-team1");state=VICTORY;}
        if(plan.tutorial()){boolean live=false;for(Actor a:actors)if(a.plot&&a.hp>0&&!a.enemy)live=true;if(!live&&elapsed>1)state=DEFEAT;}
        schedule();
        if(state==PLAYING&&elapsed>15&&kills>0&&plan.outcomes.isEmpty()&&allFiniteSpawnsDone()&&noEnemies()&&!plan.tutorial()&&plan.enemyHp<=0&&messages.isEmpty()&&cue==null)state=VICTORY;
    }
    private void move(Actor a,double dt){
        if(Double.isNaN(a.goal)){if(a.enemy&&!a.ai.equals("guard"))a.goal=80;else return;}
        double d=a.goal-a.x;if(Math.abs(d)<2){a.goal=Double.NaN;a.moving=false;return;}
        a.faceLeft=d<0;a.x+=Math.signum(d)*Math.min(Math.abs(d),a.speed*dt);if(scrollSpeed<=0)a.x=Math.max(40,Math.min(plan.width-40,a.x));else if(a.matchScroll)a.x=Math.max(camera+40,Math.min(right()-40,a.x));a.moving=true;
    }
    private void zones(){
        for(Actor a:actors)if(a.hp>0&&!a.collected)for(BattlePlan.Zone z:a.zones){if(z.once&&a.usedZones.contains(z))continue;for(Actor t:actors)if(t!=a&&t.hp>0&&!t.collected&&Math.abs(t.laneVisual-a.laneVisual)<.3&&t.x>=a.x+z.left&&t.x<=a.x+z.left+z.width){
            if(z.flag!=null&&(z.actor==null?!t.enemy:t.scriptId!=null&&z.actor.equals(t.scriptId))){flags.put(z.flag,flags.getOrDefault(z.flag,0)+z.amount);emit("flag:");a.collected=true;a.usedZones.add(z);break;}
            if(z.damage>0&&t.enemy!=a.enemy){damage(null,t,z.damage);a.usedZones.add(z);if(z.once)break;}
        }}
    }
    private Actor make(int type,boolean enemy,int lane,double x,double hp,double attack,double speed,int tier){
        UnitSpec s=specs.get(type);int h=Progress.hero(type)?progress.stats[type][1]:0,d=Progress.hero(type)?progress.stats[type][0]:0;
        UnitSpec.Attack profile=s.profile(tier,false);double defense=s.defense+(!enemy&&Progress.hero(type)?progress.stats[type][2]*s.defenseUpgrade:0);
        Actor a=new Actor(type,enemy,lane,x,(s.hp+(!enemy?h*s.hpUpgrade:0))*hp,UnitSpec.scale(s.attack+(!enemy?d*s.attackUpgrade:0),attack),defense,UnitSpec.scale(s.speed,speed),profile.range,profile.interval);a.tier=tier;a.faceLeft=enemy;a.laneVisual=lane;return a;
    }
    private void impact(Actor a,UnitSpec.Attack profile,int index){
        UnitSpec spec=specs.get(a.type);double amount=UnitSpec.roll(a.usingSpecial?UnitSpec.scale(spec.specialDamage,a.attackScalor):a.attack,random.nextDouble(),random.nextDouble()<spec.critChance);
        if(a.usingSpecial||profile.area){double range=UnitSpec.scale(profile.range,a.rangeScalor);for(Actor t:actors)if(t.hp>0&&t.enemy==(profile.heal?a.enemy:!a.enemy)&&(profile.allLanes||t.lane==a.lane)&&Math.abs(t.x-a.x)<=range){if(profile.heal)t.hp=Math.min(t.maxHp,t.hp+amount);else damage(a,t,amount);}return;}
        if(a.pending!=null&&a.pending.hp>0){double speed=index<profile.projectiles.length?profile.projectiles[index]:0;if(speed>0){Shot shot=new Shot(a.x,a.pending.x,a.lane,a.enemy,a.type);shot.source=a;shot.target=a.pending;shot.damage=amount;shot.duration=Math.max(.01,Math.abs(shot.to-shot.from)/speed);shots.add(shot);}else if(a.pending.lane==a.lane&&Math.abs(a.pending.laneVisual-a.laneVisual)<.25&&Math.abs(a.x-a.pending.x)<=a.range+15)damage(a,a.pending,amount);}
        else if(a.pendingCastle){if(a.enemy)playerCastle=Math.max(0,playerCastle-amount);else enemyCastle=Math.max(0,enemyCastle-amount);}
    }
    private void damage(Actor attacker,Actor target,double amount){
        if(target.hp<=0)return;target.hp-=UnitSpec.mitigate(amount,target.defense,!target.attacking&&!target.moving);target.flash=.15;lastActivity=elapsed;
        if(target.hp<=0){target.animationAge=0;dead(target);}
    }
    private void dead(Actor a){
        if(a.deathAge==0){a.deathAge=.000001;if(a.enemy){kills++;essence=Math.min(100,essence+Math.max(1,specs.get(a.type).reward));boolean explicit=false;for(BattlePlan.Command c:plan.commands)if(c.type.equals("flag")&&c.id.equals("monstersKilled"))explicit=true;if(!explicit)flags.put("monstersKilled",kills);}if(a.plot&&!a.enemy)state=DEFEAT;
            String type=specs.get(a.type).id;for(String event:new String[]{"destroyed","die","controllerDie"}){if(a.scriptId!=null)emit(event+":"+a.scriptId);if(!type.equals(a.scriptId))emit(event+":"+type);emit(event+":");}
        }
    }
    private boolean noEnemies(){for(Actor a:actors)if(a.enemy&&a.hp>0&&specs.get(a.type).speed>0)return false;return true;}
    private boolean allFiniteSpawnsDone(){for(int i=0;i<spawned.length;i++)if(!spawned[i]&&plan.spawns.get(i).trigger==null)return false;return true;}
    private void schedule(){
        for(int i=0;i<plan.spawns.size();i++){
            BattlePlan.Spawn s=plan.spawns.get(i);if(spawned[i]||s.type<0)continue;double base=activation(s.trigger);if(base<0)continue;
            double at=base+s.time;
            BattlePlan.Wave w=s.wave>=0&&s.wave<plan.waves.size()?plan.waves.get(s.wave):null;
            if(w!=null&&w.repeat)at+=repeats[i]*Math.max(w.delay,w.delay*w.count);
            if(w!=null&&w.repeat&&at>=base+w.stopAt){spawned[i]=true;continue;}
            if(elapsed+.00001<at||actors.size()>=160)continue;
            if(s.unit.equals("goodcastle")||s.unit.equals("evilcastle")){spawned[i]=true;continue;}
            if(s.unit.equals("miney")&&s.team==0&&s.zones.isEmpty()&&plan.mining){hireMiner();spawned[i]=true;continue;}
            double x=Double.isNaN(s.x)?(s.left?camera+60:right()-60):s.x+(scrollSpeed>0?camera:0);
            Actor a=make(s.type,s.team!=0,Math.max(0,Math.min(2,s.lane)),x,s.hp,s.attack,s.speed,s.tier);a.scriptId=s.id;a.sprite=s.sprite;a.plot=s.plot;a.hunt=s.hunt;a.sizeScale=s.scale;a.ai=s.ai;a.haste=s.haste;a.attackScalor=s.attack;a.rangeScalor=s.range;a.range=UnitSpec.scale(a.range,s.range);a.interval/=UnitSpec.scale(1,s.haste);a.hp+=s.addHp*s.hp;a.maxHp=a.hp;
            a.selectable=s.selectable;a.untargetable=s.untargetable;a.zones.addAll(s.zones);
            if(a.enemy&&!a.ai.equals("guard"))a.goal=s.left?right()-80:camera+80;actors.add(a);if(!a.enemy&&(a.plot||a.ai.equals("marchRight")))selected=a;
            if(w!=null&&w.repeat&&repeats[i]<100)repeats[i]++;else spawned[i]=true;wave=Math.max(wave,s.wave+1);
        }
        for(int i=0;i<plan.commands.size();i++)if(!commanded[i]){BattlePlan.Command c=plan.commands.get(i);if(c.type.equals("flag")&&c.increment&&edge(c.trigger))continue;double at=activation(c.trigger);if(at<0||elapsed<at+c.time)continue;commanded[i]=true;
            if(c.type.equals("setSwitch")||c.type.equals("clearSwitch")){try{switches.put(Integer.parseInt(c.id),c.bool);}catch(NumberFormatException ignored){}}
            else if(c.type.equals("setGems"))gems=Math.max(0,Math.min(9999,(int)c.value));
            else if(c.type.equals("selectUnit")){for(Actor a:actors)if(c.id.equals(a.scriptId))selectActor(a);}
            else if(c.type.equals("flag"))flags.put(c.id,(c.increment?flags.getOrDefault(c.id,0):0)+(int)c.value);
            else if(c.type.equals("setScrollSpeed"))scrollSpeed=Math.max(0,Math.min(600,-c.value));
            else if(c.type.equals("matchScrollSpeed")){for(Actor a:actors)if(c.id.equals(a.scriptId)){a.matchScroll=c.bool;if(!a.enemy&&a.selectable)selected=a;}}
            else if(c.type.equals("die")){for(Actor a:actors)if(c.id.equals(a.scriptId)){a.hp=0;dead(a);}}
        }
        for(int i=0;i<plan.cues.size();i++)if(!cued[i]){BattlePlan.Cue c=plan.cues.get(i);double at=activation(c.trigger);if(at<0||elapsed<at+c.time)continue;cued[i]=true;messages.add(c);}
        if(cue==null&&!messages.isEmpty())cue=messages.removeFirst();
        for(int i=0;i<plan.outcomes.size();i++)if(!ended[i]){BattlePlan.Outcome o=plan.outcomes.get(i);double at=activation(o.trigger);if(at>=0&&elapsed>=at+o.time){ended[i]=true;state=o.victory?VICTORY:DEFEAT;}}
    }
    private double activation(BattlePlan.Trigger t){
        if(t==null)return 0;String key=t.key();Double known=triggered.get(key);if(known!=null)return known;
        double parent=t.parent==null?0:activation(t.parent);if(parent<0||elapsed<parent+t.parentTime||!conditions(t.conditions))return -1;
        double at=-1;
        if(t.type.equals("gameTime")||t.type.equals("realTime"))at=parent+t.parentTime;
        else if(t.type.equals("levelStateChanged")){for(String s:t.states)if(s.equals("normal")||s.equals("onEnter")||s.equals("initHUD"))at=parent;}
        else if(t.type.equals("chatEnd")){if(chatEnd>=parent&&messages.isEmpty()&&cue==null)at=chatEnd;}
        else if(t.type.equals("flag")||t.type.equals("gemAmountChanged")){if(conditions(t.conditions))at=elapsed;}
        // Edge events are armed in emit() with the conditions at occurrence time.
        if(at>=0)triggered.put(key,at);return at;
    }
    private boolean conditions(String[] list){
        for(String source:list)for(String raw:source.split("&&")){
            String s=raw.trim();if(s.startsWith("(")&&s.endsWith(")"))s=s.substring(1,s.length()-1).trim();
            java.util.regex.Matcher m=java.util.regex.Pattern.compile("SwitchIsOn\\((\\d+)\\)").matcher(s);
            if(m.matches()){if(!switches.getOrDefault(Integer.parseInt(m.group(1)),false))return false;continue;}
            m=java.util.regex.Pattern.compile("LevelGems\\(\\)\\s*>=\\s*(\\d+)").matcher(s);if(m.matches()){if(gems<Integer.parseInt(m.group(1)))return false;continue;}
            m=java.util.regex.Pattern.compile("GetFlag\\(\"([A-Za-z0-9_]+)\"\\)\\s*(>=|==)\\s*(\\d+)").matcher(s);if(m.matches()){int v=flags.getOrDefault(m.group(1),0),n=Integer.parseInt(m.group(3));if(m.group(2).equals(">=")?v<n:v!=n)return false;continue;}
            if(s.equals("LevelCycle()==1"))continue;
            if(s.equals("UnitGetTier(\"healer\") > 0")){if(!progress.purchased[20])return false;continue;}
            return false;
        }return true;
    }
}
