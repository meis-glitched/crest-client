package com.crest.client.music.playlist;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Playlist {
    private final String id;
    private String name;
    private String description;
    private final List<PlaylistTrack> tracks;
    private final Instant created;
    private Instant modified;
    
    public Playlist(String name) {
        this(UUID.randomUUID().toString(), name, "", new ArrayList<>(), Instant.now(), Instant.now());
    }
    
    public Playlist(String id, String name, String description, List<PlaylistTrack> tracks, Instant created, Instant modified) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.tracks = new ArrayList<>(tracks);
        this.created = created;
        this.modified = modified;
    }
    
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<PlaylistTrack> getTracks() { return new ArrayList<>(tracks); }
    public Instant getCreated() { return created; }
    public Instant getModified() { return modified; }
    
    public void setName(String name) {
        this.name = name;
        touch();
    }
    
    public void setDescription(String description) {
        this.description = description;
        touch();
    }
    
    public void addTrack(PlaylistTrack track) {
        tracks.add(track);
        touch();
    }
    
    public void removeTrack(int index) {
        if (index >= 0 && index < tracks.size()) {
            tracks.remove(index);
            touch();
        }
    }
    
    public void moveTrack(int from, int to) {
        if (from >= 0 && from < tracks.size() && to >= 0 && to < tracks.size()) {
            PlaylistTrack track = tracks.remove(from);
            tracks.add(to, track);
            touch();
        }
    }
    
    public void clear() {
        tracks.clear();
        touch();
    }
    
    public int size() {
        return tracks.size();
    }
    
    private void touch() {
        this.modified = Instant.now();
    }
    
    public static class PlaylistTrack {
        private final String url;
        private final String title;
        private final String artist;
        private final long duration;
        private final Instant added;
        
        public PlaylistTrack(String url, String title, String artist, long duration) {
            this(url, title, artist, duration, Instant.now());
        }
        
        public PlaylistTrack(String url, String title, String artist, long duration, Instant added) {
            this.url = url;
            this.title = title;
            this.artist = artist;
            this.duration = duration;
            this.added = added;
        }
        
        public String getUrl() { return url; }
        public String getTitle() { return title; }
        public String getArtist() { return artist; }
        public long getDuration() { return duration; }
        public Instant getAdded() { return added; }
    }
}
