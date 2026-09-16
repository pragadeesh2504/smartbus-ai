package com.smartbus.driver.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class LocationDbHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "location_cache.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_NAME = "location_cache";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_LATITUDE = "latitude";
    public static final String COLUMN_LONGITUDE = "longitude";
    public static final String COLUMN_SPEED = "speed";
    public static final String COLUMN_HEADING = "heading";
    public static final String COLUMN_RECORDED_AT = "recorded_at";

    public LocationDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_NAME + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_LATITUDE + " REAL, " +
                COLUMN_LONGITUDE + " REAL, " +
                COLUMN_SPEED + " REAL, " +
                COLUMN_HEADING + " REAL, " +
                COLUMN_RECORDED_AT + " TEXT)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    public void insertLocation(double lat, double lng, double speed, double heading, String time) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_LATITUDE, lat);
        values.put(COLUMN_LONGITUDE, lng);
        values.put(COLUMN_SPEED, speed);
        values.put(COLUMN_HEADING, heading);
        values.put(COLUMN_RECORDED_AT, time);
        db.insert(TABLE_NAME, null, values);
        db.close();
    }

    public List<CachedLocation> getAllCachedLocations() {
        List<CachedLocation> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_NAME, null);

        if (cursor.moveToFirst()) {
            do {
                CachedLocation loc = new CachedLocation();
                loc.setId(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)));
                loc.setLatitude(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LATITUDE)));
                loc.setLongitude(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LONGITUDE)));
                loc.setSpeed(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_SPEED)));
                loc.setHeading(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_HEADING)));
                loc.setRecordedAt(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_RECORDED_AT)));
                list.add(loc);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return list;
    }

    public void deleteLocation(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    public static class CachedLocation {
        private int id;
        private double latitude;
        private double longitude;
        private double speed;
        private double heading;
        private String recordedAt;

        // Getters and setters
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public double getLatitude() { return latitude; }
        public void setLatitude(double latitude) { this.latitude = latitude; }
        public double getLongitude() { return longitude; }
        public void setLongitude(double longitude) { this.longitude = longitude; }
        public double getSpeed() { return speed; }
        public void setSpeed(double speed) { this.speed = speed; }
        public double getHeading() { return heading; }
        public void setHeading(double heading) { this.heading = heading; }
        public String getRecordedAt() { return recordedAt; }
        public void setRecordedAt(String recordedAt) { this.recordedAt = recordedAt; }
    }
}
