package com.tripmate.backend.infrastructure.tourapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "congestion_api_items",
        indexes = {
                @Index(name = "idx_congestion_item_code_base_ymd", columnList = "code,baseYmd"),
                @Index(name = "idx_congestion_item_daywk", columnList = "daywkDivCd")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CongestionApiItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private CongestionApiBatch batch;

    @Column(nullable = false, length = 16)
    private String code;

    @Column(length = 128)
    private String name;

    @Column
    private Integer daywkDivCd;

    @Column(length = 32)
    private String daywkDivNm;

    @Column(length = 8)
    private String touDivCd;

    @Column(length = 64)
    private String touDivNm;

    @Column
    private Double touNum;

    @Column(length = 8)
    private String baseYmd;
}
