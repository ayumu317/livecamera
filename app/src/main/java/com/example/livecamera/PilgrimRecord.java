package com.example.livecamera;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "pilgrim_records")
public class PilgrimRecord {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String animeName;
    public String locationName;
    public String description;
    public String localImageUri;
    public String referenceImageUrl;
    public long timestamp;
}
