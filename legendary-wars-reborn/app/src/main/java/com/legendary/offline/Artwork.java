package com.legendary.offline;

import android.content.res.AssetManager;
import android.graphics.*;
import android.util.LruCache;
import org.json.*;
import java.io.*;
import java.util.*;

/** PNGs are allowlisted and structurally validated before packaging; no network loader. */
public final class Artwork {
    private final AssetManager assets;
    private final GameData data;
    private final LruCache<String,Bitmap> cache;
    private final LruCache<String,Bitmap> thumbnails;
    private final Set<String> failures=new HashSet<>();
    private final Paint bitmapPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Rect src=new Rect();private final RectF dst=new RectF();
    public Artwork(AssetManager assets,GameData data){
        this.assets=assets;this.data=data;
        int budget=(int)Math.min(112*1024*1024,Runtime.getRuntime().maxMemory()/2);
        cache=new LruCache<String,Bitmap>(budget){@Override protected int sizeOf(String k,Bitmap b){return b.getAllocationByteCount();}};
        thumbnails=new LruCache<String,Bitmap>(4*1024*1024){@Override protected int sizeOf(String k,Bitmap b){return b.getAllocationByteCount();}};
    }
    private Bitmap bitmap(String atlas){
        Bitmap b=cache.get(atlas);if(b!=null)return b;if(failures.contains(atlas))return null;
        JSONObject a=data.atlases.optJSONObject(atlas);if(a==null)return null;
        String file=a.optString("file");if(!file.matches("art/[A-Za-z0-9_.-]+\\.png")){failures.add(atlas);return null;}
        try(InputStream in=assets.open(file)){
            BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;
            b=BitmapFactory.decodeStream(in,null,options);
            if(b==null||b.getWidth()!=a.optInt("width")||b.getHeight()!=a.optInt("height")){failures.add(atlas);return null;}
            cache.put(atlas,b);return b;
        }catch(IOException e){failures.add(atlas);return null;}
    }
    public JSONObject frame(String atlas,String name){JSONObject a=data.atlases.optJSONObject(atlas);return a==null?null:a.optJSONObject("frames").optJSONObject(name);}
    public boolean draw(Canvas c,String atlas,String name,float x,float y,float width,float height){
        JSONObject f=frame(atlas,name);if(f==null||width<=0||height<=0)return false;
        Bitmap b=bitmap(atlas);if(b==null)return false;
        JSONArray r=f.optJSONArray("rect"),trim=f.optJSONArray("trim"),size=f.optJSONArray("size");
        float sx=width/(float)size.optDouble(0),sy=height/(float)size.optDouble(1);
        float left=x+(float)trim.optDouble(0)*sx,top=y+(float)trim.optDouble(1)*sy;
        float w=(float)trim.optDouble(2)*sx,h=(float)trim.optDouble(3)*sy;
        src.set(r.optInt(0),r.optInt(1),r.optInt(0)+r.optInt(2),r.optInt(1)+r.optInt(3));
        if(f.optBoolean("rotated")){
            c.save();c.translate(left,top+h);c.rotate(-90);dst.set(0,0,h,w);c.drawBitmap(b,src,dst,bitmapPaint);c.restore();
        }else{dst.set(left,top,left+w,top+h);c.drawBitmap(b,src,dst,bitmapPaint);}
        return true;
    }
    public void fit(Canvas c,String atlas,String name,float x,float y,float width,float height){
        JSONObject f=frame(atlas,name);if(f==null)return;JSONArray s=f.optJSONArray("size");
        float scale=Math.min(width/(float)s.optDouble(0),height/(float)s.optDouble(1));
        float w=(float)s.optDouble(0)*scale,h=(float)s.optDouble(1)*scale;
        if(width<=160&&height<=160){
            String key=atlas+"/"+name;Bitmap thumb=thumbnails.get(key);
            if(thumb==null){
                float ratio=Math.min(128f/(float)s.optDouble(0),128f/(float)s.optDouble(1));
                int tw=Math.max(1,Math.round((float)s.optDouble(0)*ratio)),th=Math.max(1,Math.round((float)s.optDouble(1)*ratio));
                thumb=Bitmap.createBitmap(tw,th,Bitmap.Config.ARGB_8888);
                if(!draw(new Canvas(thumb),atlas,name,0,0,tw,th))return;
                thumbnails.put(key,thumb);
            }
            dst.set(x+(width-w)/2,y+(height-h)/2,x+(width+w)/2,y+(height+h)/2);
            c.drawBitmap(thumb,null,dst,bitmapPaint);return;
        }
        draw(c,atlas,name,x+(width-w)/2,y+(height-h)/2,w,h);
    }
    public void actor(Canvas c,int type,int tier,String state,double age,float x,float feet,float scale,boolean mirror){
        JSONObject variant=data.variant(type,tier),states=variant.optJSONObject("states"),anim=states.optJSONObject(state);
        if(anim==null)anim=states.optJSONObject("ready");if(anim==null)anim=states.optJSONObject("walk");if(anim==null)return;
        JSONArray frames=anim.optJSONArray("frames");int n=frames.length();if(n==0)return;
        int i=(int)(Math.max(0,age)*anim.optDouble("fps",14));
        i=state.equals("death")?Math.min(n-1,i):i%n;
        String atlas=anim.optString("atlas",variant.optString("atlas")),name=frames.optString(i);JSONObject f=frame(atlas,name);if(f==null)return;
        JSONArray size=f.optJSONArray("size");float w=(float)size.optDouble(0)*scale,h=(float)size.optDouble(1)*scale;
        c.save();c.translate(x,feet);if(mirror)c.scale(-1,1);draw(c,atlas,name,-w/2,-h,w,h);c.restore();
    }
    public void icon(Canvas c,int type,int tier,float x,float y,float width,float height){
        JSONObject variant=data.variant(type,tier);String atlas=variant.optString("atlas");JSONObject frames=data.atlases.optJSONObject(atlas).optJSONObject("frames");
        String id=data.specs.get(type).id;
        String preferred="icon-"+(type==7?"miner":id)+".png";
        if(frame(atlas,preferred)!=null){fit(c,atlas,preferred,x,y,width,height);return;}
        Iterator<String> keys=frames.keys();while(keys.hasNext()){String key=keys.next();if(key.startsWith("icon-")&&!key.contains("disabled")&&!key.contains("locked")){fit(c,atlas,key,x,y,width,height);return;}}
        JSONObject states=variant.optJSONObject("states"),ready=states.optJSONObject("ready");if(ready==null)ready=states.optJSONObject("walk");
        if(ready!=null)fit(c,atlas,ready.optJSONArray("frames").optString(0),x,y,width,height);
    }
    public void clear(){cache.evictAll();thumbnails.evictAll();}
}
