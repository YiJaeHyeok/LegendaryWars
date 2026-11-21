package com.legendary.offline;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.*;
import java.io.IOException;
import java.util.*;
import org.json.JSONObject;

public final class LocalAudio {
    private final Context context;
    private MediaPlayer player;
    private SoundPool sfxPool;
    private final Map<String,Integer> sfxIds=new HashMap<>();
    private final Map<String,Long> lastPlayed=new HashMap<>();
    private boolean enabled=true;
    private String track="audio/TitleScreen.ogg";
    public LocalAudio(Context context){this.context=context;}
    public void loadSfx(JSONObject sfxMap){
        if(sfxMap==null)return;
        if(sfxPool==null){
            AudioAttributes attrs=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            sfxPool=new SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attrs).build();
        }
        Iterator<String> keys=sfxMap.keys();
        while(keys.hasNext()){
            String key=keys.next(),file=sfxMap.optString(key,"");
            if(!file.matches("audio/[A-Za-z0-9_.-]+\\.wav")||sfxIds.containsKey(key))continue;
            try(AssetFileDescriptor fd=context.getAssets().openFd(file)){
                int id=sfxPool.load(fd,1);
                if(id>0)sfxIds.put(key,id);
            }catch(IOException|RuntimeException ignored){}
        }
    }
    public void playSfx(String key){
        if(!enabled||sfxPool==null||key==null)return;
        Integer id=sfxIds.get(key);if(id==null)return;
        long now=System.nanoTime();Long prev=lastPlayed.get(key);
        if(prev!=null&&now-prev<65_000_000L)return;
        lastPlayed.put(key,now);
        sfxPool.play(id,.45f,.45f,1,0,1f);
    }
    public void setEnabled(boolean on){enabled=on;if(!on)pause();else resume();}
    public void play(String file){if(file==null||!file.matches("audio/[A-Za-z0-9_.-]+\\.(ogg|mp3|wav)")||file.equals(track))return;releaseMusic();track=file;resume();}
    public void resume(){
        if(!enabled)return;
        try{
            if(player==null){
                player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
                try(AssetFileDescriptor fd=context.getAssets().openFd(track)){player.setDataSource(fd.getFileDescriptor(),fd.getStartOffset(),fd.getLength());}
                player.setLooping(true);player.setVolume(.23f,.23f);player.prepare();
            }
            if(!player.isPlaying())player.start();
        }catch(IOException|IllegalStateException e){releaseMusic();}
    }
    public void pause(){if(player!=null)try{if(player.isPlaying())player.pause();}catch(IllegalStateException e){releaseMusic();}}
    private void releaseMusic(){if(player!=null){player.release();player=null;}}
    public void release(){releaseMusic();if(sfxPool!=null){sfxPool.release();sfxPool=null;sfxIds.clear();lastPlayed.clear();}}
}
