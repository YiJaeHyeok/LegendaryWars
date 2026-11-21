package com.legendary.offline;

public final class Progress {
    public int gems=150,castleRank,mineRank,moonstones;
    public final int[] stars=new int[73],ranks=new int[21],gear=new int[21];
    public final int[][] stats=new int[21][];
    public final boolean[] purchased=new boolean[21];
    public Progress(){java.util.Arrays.fill(gear,1);for(int i=0;i<stats.length;i++)stats[i]=new int[3];}
    public static boolean hero(int unit){return unit>=0&&(unit<7||unit>=16&&unit<=20);}
    public int highestUnlocked(){int i=0;while(i<stars.length-1&&stars[i]>0)i++;return i;}
    public int upgradeCost(int unit){if(!hero(unit)&&unit!=7&&unit!=8)return Integer.MAX_VALUE;int r=hero(unit)?ranks[unit]:unit==7?castleRank:mineRank;return 65+r*45;}
    public boolean upgrade(int unit){
        if(!hero(unit)&&unit!=7&&unit!=8)return false;
        int rank=hero(unit)?ranks[unit]:unit==7?castleRank:mineRank;
        int cost=upgradeCost(unit);if(rank>=12||gems<cost)return false;
        gems-=cost;if(hero(unit)){ranks[unit]++;for(int i=0;i<3;i++)stats[unit][i]=Math.min(10,stats[unit][i]+1);}else if(unit==7)castleRank++;else mineRank++;return true;
    }
    public int complete(int stage,int rating){
        if(stage<0||stage>=stars.length||stage>highestUnlocked())return 0;
        if(stars[stage]==0&&(stage+1)%10==0)moonstones=Math.min(999,moonstones+1);
        int amount=stars[stage]==0?100+stage*12:20+stage*2;
        stars[stage]=Math.max(stars[stage],Math.max(1,Math.min(3,rating)));
        int previous=gems;gems=Math.min(1_000_000,gems+amount);return gems-previous;
    }
    public String encode(){
        StringBuilder b=new StringBuilder("v2|").append(gems).append('|').append(castleRank).append('|').append(mineRank).append('|');
        for(int i=0;i<stars.length;i++){if(i>0)b.append(',');b.append(stars[i]);}b.append('|');
        for(int i=0;i<ranks.length;i++){if(i>0)b.append(',');b.append(ranks[i]);}b.append('|').append(moonstones).append('|');
        for(int i=0;i<21;i++){if(i>0)b.append(',');b.append(purchased[i]?1:0);}b.append('|');
        for(int i=0;i<21;i++){if(i>0)b.append(',');b.append(gear[i]);}b.append('|');
        for(int i=0;i<63;i++){if(i>0)b.append(',');b.append(stats[i/3][i%3]);}return b.toString();
    }
    private static int bounded(String s,int max){return Math.max(0,Math.min(max,Integer.parseInt(s)));}
    public static Progress decode(String text){
        Progress p=new Progress();if(text==null||text.length()>4096)return p;
        try{
            String[] parts=text.split("\\|",-1);boolean legacy=parts.length==6&&parts[0].equals("v1");if(!legacy&&(parts.length!=10||!parts[0].equals("v2")))return p;
            String[] stars=parts[4].split(",",-1),ranks=parts[5].split(",",-1);if(legacy?(stars.length!=50||ranks.length!=7):(stars.length!=73||ranks.length!=21))return p;
            p.gems=bounded(parts[1],1_000_000);p.castleRank=bounded(parts[2],12);p.mineRank=bounded(parts[3],12);
            boolean gap=false;
            for(int i=0;i<stars.length;i++){int n=bounded(stars[i],3);if(n==0)gap=true;p.stars[i]=gap?0:n;}
            for(int i=0;i<ranks.length;i++)p.ranks[i]=bounded(ranks[i],12);
            if(legacy){p.moonstones=p.highestUnlocked()/10;for(int i=0;i<7;i++){p.gear[i]=Math.min(4,1+p.ranks[i]/3);java.util.Arrays.fill(p.stats[i],Math.min(10,p.ranks[i]));}}
            else{
                p.moonstones=bounded(parts[6],999);String[] bought=parts[7].split(","),gear=parts[8].split(","),stats=parts[9].split(",");if(bought.length!=21||gear.length!=21||stats.length!=63)return new Progress();
                for(int i=0;i<21;i++){p.purchased[i]=bounded(bought[i],1)==1;p.gear[i]=Math.max(1,bounded(gear[i],6));}
                for(int i=0;i<63;i++)p.stats[i/3][i%3]=bounded(stats[i],10);
            }
            return p;
        }catch(NumberFormatException e){return new Progress();}
    }
    public boolean buyHero(int type,int price){if(!hero(type)||price<1||purchased[type]||moonstones<price)return false;moonstones-=price;purchased[type]=true;return true;}
    public boolean upgradeGear(int type,int maxTier,int basePrice){if(!hero(type))return false;int price=Math.max(1,basePrice)+gear[type]-1;if(gear[type]>=maxTier||moonstones<price)return false;moonstones-=price;gear[type]++;return true;}
    public int statCost(int type,int stat,int base,int multiplier){if(!hero(type)||stat<0||stat>2)return Integer.MAX_VALUE;return Math.max(1,base+stats[type][stat]*multiplier);}
    public boolean upgradeStat(int type,int stat,int base,int multiplier){int cost=statCost(type,stat,base,multiplier);if(!hero(type)||stat<0||stat>2||stats[type][stat]>=10||gems<cost)return false;gems-=cost;stats[type][stat]++;ranks[type]=Math.max(ranks[type],stats[type][stat]);return true;}
    public int supportMax(int type){return type==7?6:type==8?5:0;}
    public int supportRank(int type){return type==7?castleRank:type==8?mineRank:0;}
    public int supportCost(int type){if(type!=7&&type!=8)return Integer.MAX_VALUE;int base=type==7?250:125;return base*(supportRank(type)+1);}
    public boolean upgradeSupport(int type){int max=supportMax(type),rank=supportRank(type),cost=supportCost(type);if(max==0||rank>=max||gems<cost)return false;gems-=cost;if(type==7)castleRank++;else mineRank++;return true;}
}
