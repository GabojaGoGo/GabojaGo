package com.gabojago.member.activity.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "user_footprints")
public class UserFootprint {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "spot_id", length = 64)
  private String spotId;

  @Column(name = "spot_name", length = 128, nullable = false)
  private String spotName;

  @Column(name = "region_name", length = 64, nullable = false)
  private String regionName;

  @Column(name = "visited_at", nullable = false)
  private LocalDateTime visitedAt;

  @Column(name = "tags", length = 256)
  private String tags; // comma-separated

  protected UserFootprint() {}

  public static UserFootprint of(
      Long userId, String spotId, String spotName, String regionName, List<String> tags) {
    UserFootprint f = new UserFootprint();
    f.userId = userId;
    f.spotId = spotId;
    f.spotName = spotName;
    f.regionName = regionName;
    f.visitedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    f.tags = tags.isEmpty() ? null : String.join(",", tags);
    return f;
  }

  public Long getId() {
    return id;
  }

  public Long getUserId() {
    return userId;
  }

  public String getSpotId() {
    return spotId;
  }

  public String getSpotName() {
    return spotName;
  }

  public String getRegionName() {
    return regionName;
  }

  public LocalDateTime getVisitedAt() {
    return visitedAt;
  }

  public List<String> getTags() {
    if (tags == null || tags.isBlank()) return Collections.emptyList();
    return Arrays.asList(tags.split(","));
  }
}
