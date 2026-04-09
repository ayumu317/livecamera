package com.example.livecamera;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PilgrimDao {

    @Insert
    long insert(PilgrimRecord record);

    @Query("SELECT * FROM pilgrim_records ORDER BY timestamp DESC")
    List<PilgrimRecord> getAllRecordsByNewest();
}
