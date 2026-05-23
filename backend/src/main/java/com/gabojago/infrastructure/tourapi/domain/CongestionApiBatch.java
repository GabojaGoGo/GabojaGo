package com.tripmate.backend.infrastructure.tourapi.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "congestion_api_batches",
        indexes = {
                @Index(name = "idx_congestion_batch_scope_ymd", columnList = "scope,requestYmd"),
                @Index(name = "idx_congestion_batch_fetched_at", columnList = "fetchedAt")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CongestionApiBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String scope;

    @Column(nullable = false, length = 8)
    private String requestYmd;

    @Column(nullable = false, length = 128)
    private String endpoint;

    @Column(length = 8)
    private String resultCode;

    @Column(length = 128)
    private String resultMsg;

    @Column(nullable = false)
    private int totalCount;

    @Column(nullable = false)
    private LocalDateTime fetchedAt;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String rawJson;

    @Builder.Default
    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CongestionApiItem> items = new ArrayList<>();

    public void addItem(CongestionApiItem item) {
        items.add(item);
    }
}
