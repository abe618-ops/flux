package com.matchday.virtualbet;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

/** Persistent fixture cache. The screen can render instantly before any network request completes. */
public final class MatchCacheDb extends SQLiteOpenHelper {
    private static final String DB="matchday-cache.db";
    public MatchCacheDb(Context c){super(c.getApplicationContext(),DB,null,2);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE fixture_cache(cache_key TEXT PRIMARY KEY, payload TEXT NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX fixture_cache_time ON fixture_cache(updated_at)");
        db.execSQL("CREATE TABLE result_cache(cache_key TEXT PRIMARY KEY, payload TEXT NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX result_cache_time ON result_cache(updated_at)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(oldVersion<2){
            db.execSQL("CREATE TABLE IF NOT EXISTS result_cache(cache_key TEXT PRIMARY KEY, payload TEXT NOT NULL, updated_at INTEGER NOT NULL)");
            db.execSQL("CREATE INDEX IF NOT EXISTS result_cache_time ON result_cache(updated_at)");
        }
    }
    public synchronized ArrayList<JSONObject> getFixtures(int mode,String day){
        ArrayList<JSONObject> out=new ArrayList<>();String key=mode+":"+day;
        try(Cursor c=getReadableDatabase().query("fixture_cache",new String[]{"payload"},"cache_key=?",new String[]{key},null,null,null,"1")){
            if(c.moveToFirst()){JSONArray a=new JSONArray(c.getString(0));for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)out.add(o);}}
        }catch(Exception ignored){}
        return out;
    }
    public synchronized ArrayList<JSONObject> getResults(int mode,String day){
        ArrayList<JSONObject> out=new ArrayList<>();String key=mode+":"+day;
        try(Cursor c=getReadableDatabase().query("result_cache",new String[]{"payload"},"cache_key=?",new String[]{key},null,null,null,"1")){
            if(c.moveToFirst()){JSONArray a=new JSONArray(c.getString(0));for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)out.add(o);}}
        }catch(Exception ignored){}
        return out;
    }
    public synchronized void putResults(int mode,String day,List<JSONObject> data){
        if(data==null||data.isEmpty())return;JSONArray a=new JSONArray();for(JSONObject r:data)a.put(r);ContentValues v=new ContentValues();v.put("cache_key",mode+":"+day);v.put("payload",a.toString());v.put("updated_at",System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("result_cache",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        getWritableDatabase().delete("result_cache","updated_at<?",new String[]{String.valueOf(System.currentTimeMillis()-180L*24*3600000)});
    }
    public synchronized long getUpdatedAt(int mode,String day){String key=mode+":"+day;
        try(Cursor c=getReadableDatabase().query("fixture_cache",new String[]{"updated_at"},"cache_key=?",new String[]{key},null,null,null,"1")){return c.moveToFirst()?c.getLong(0):0;}catch(Exception ignored){return 0;}}
    public synchronized void putFixtures(int mode,String day,List<JSONObject> data){
        JSONArray a=new JSONArray();for(JSONObject f:data)a.put(f);ContentValues v=new ContentValues();v.put("cache_key",mode+":"+day);v.put("payload",a.toString());v.put("updated_at",System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("fixture_cache",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        getWritableDatabase().delete("fixture_cache","updated_at<?",new String[]{String.valueOf(System.currentTimeMillis()-45L*24*3600000)});
    }
}
