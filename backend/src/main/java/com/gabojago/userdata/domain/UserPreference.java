package com.tripmate.backend.userdata.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "user_preferences")
public class UserPreference {

    @Id
    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "purposes", length = 256)
    private String purposes;   // comma-separated

    @Column(name = "duration", length = 16)
    private String duration;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected UserPreference() {}

    public static UserPreference of(String userId, List<String> purposes, String duration) {
        UserPreference p = new UserPreference();
        p.userId   = userId;
        p.purposes = purposes.isEmpty() ? null : String.join(",", purposes);
        p.duration = duration;
        p.updatedAt = LocalDateTime.now();
        return p;
    }

    public void update(List<String> purposes, String duration) {
        this.purposes   = purposes.isEmpty() ? null : String.join(",", purposes);
        this.duration   = duration;
        this.updatedAt  = LocalDateTime.now();
    }

    public String getUserId()          { return userId; }
    public List<String> getPurposes()  {
        if (purposes == null || purposes.isBlank()) return Collections.emptyList();
        return Arrays.asList(purposes.split(","));
    }
    public String getDuration()        { return duration; }
    public LocalDateTime getUpdatedAt(){ return updatedAt; }
}
