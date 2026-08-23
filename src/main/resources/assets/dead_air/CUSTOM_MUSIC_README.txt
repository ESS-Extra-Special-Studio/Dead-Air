Custom music tracks (Dead Air)
==============================

Use .OGG files only (Minecraft does not use WAV). Convert your tracks to OGG first.

1) Put your .ogg files here:
   assets/dead_air/sounds/
   e.g.  assets/dead_air/sounds/music/my_track.ogg

2) Add an entry for each track in sounds.json (same folder as this README).
   For music longer than ~4 seconds use "stream": true, e.g.:

   "music.my_track": {
     "category": "music",
     "sounds": [{ "name": "dead_air:music/my_track", "stream": true }]
   }

3) Register each sound in DeadAirSounds.java (add a line like CUSTOM_TRACK_1).

4) Tell us which station(s) each track should go on – we’ll wire them to those stations only.
