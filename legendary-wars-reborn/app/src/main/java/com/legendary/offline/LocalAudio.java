package com.legendary.offline;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.*;
import java.io.IOException;

public final class LocalAudio {
    private final Context context;private MediaPlayer player;private boolean enabled=true;private String track="audio/TitleScreen.ogg";
    public LocalAudio(Context context){this.context=context;}
    public void setEnabled(boolean on){enabled=on;if(!on)pause();else resume();}
    public void play(String file){if(file==null||!file.matches("audio/[A-Za-z0-9_.-]+\\.(ogg|mp3|wav)")||file.equals(track))return;release();track=file;resume();}
    public void resume(){
        if(!enabled)return;
        try{
            if(player==null){
                player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
                try(AssetFileDescriptor fd=context.getAssets().openFd(track)){player.setDataSource(fd.getFileDescriptor(),fd.getStartOffset(),fd.getLength());}
                player.setLooping(true);player.setVolume(.23f,.23f);player.prepare();
            }
            if(!player.isPlaying())player.start();
        }catch(IOException|IllegalStateException e){release();}
    }
    public void pause(){if(player!=null)try{if(player.isPlaying())player.pause();}catch(IllegalStateException e){release();}}
    public void release(){if(player!=null){player.release();player=null;}}
}
