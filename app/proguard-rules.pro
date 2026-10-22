# keep rules (minify disabled for debug sideload build)

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
# Media3 session service
-keep class com.otoradio.app.playback.** { *; }
