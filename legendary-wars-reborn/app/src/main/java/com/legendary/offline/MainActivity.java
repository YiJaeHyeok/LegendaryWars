package com.legendary.offline;

import android.app.Activity;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.TextView;
import java.io.IOException;
import org.json.JSONException;

public final class MainActivity extends Activity {
    private WarView game;private SharedPreferences prefs;private LocalAudio audio;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);prefs=getSharedPreferences("legendary_offline_v1",MODE_PRIVATE);audio=new LocalAudio(this);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        try{GameData data=new GameData(getAssets());audio.loadSfx(data.json.optJSONObject("sfx"));game=new WarView(this,data,Progress.decode(prefs.getString("progress",null)));setContentView(game);game.setMusic(prefs.getBoolean("music",true));}
        catch(IOException|JSONException e){TextView error=new TextView(this);error.setText("게임 자료를 읽지 못했습니다. APK를 다시 설치해 주세요.");error.setTextColor(0xfff4e6c7);error.setBackgroundColor(0xff152322);error.setTextSize(22);error.setPadding(48,48,48,48);setContentView(error);}
    }
    public void save(Progress progress){prefs.edit().putString("progress",progress.encode()).apply();}
    public void music(boolean on){prefs.edit().putBoolean("music",on).apply();audio.setEnabled(on);}
    public void musicTrack(String file){audio.play(file);}
    public void sfx(String key){if(audio!=null)audio.playSfx(key);}
    @Override protected void onResume(){super.onResume();if(game!=null)game.resumeDrawing();if(audio!=null)audio.resume();}
    @Override protected void onPause(){if(game!=null){game.pauseDrawing();save(game.progress);}if(audio!=null)audio.pause();super.onPause();}
    @Override protected void onDestroy(){if(game!=null)game.release();if(audio!=null)audio.release();super.onDestroy();}
    @Override public void onBackPressed(){if(game!=null)game.back();else super.onBackPressed();}
}
