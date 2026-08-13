package com.gabojago.member.activity.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "user_bucket_list")
public class UserBucketItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "title", length = 128, nullable = false)
  private String title;

  @Column(name = "area", length = 64)
  private String area;

  @Column(name = "note", length = 512)
  private String note;

  @Column(name = "is_completed", nullable = false)
  private boolean completed = false;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  protected UserBucketItem() {}

  public static UserBucketItem of(Long userId, String title, String area, String note) {
    UserBucketItem b = new UserBucketItem();
    b.userId = userId;
    b.title = title;
    b.area = area;
    b.note = note;
    b.createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    return b;
  }

  public void update(String title, String area, String note) {
    this.title = title;
    this.area = area;
    this.note = note;
  }

  public void complete() {
    this.completed = true;
  }

  public Long getId() {
    return id;
  }

  public Long getUserId() {
    return userId;
  }

  public String getTitle() {
    return title;
  }

  public String getArea() {
    return area;
  }

  public String getNote() {
    return note;
  }

  public boolean isCompleted() {
    return completed;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
