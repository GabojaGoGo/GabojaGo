package com.tripmate.backend.infrastructure.tourapi.domain;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
@Entity
@Table(
        name = "sigungu_daily_totals",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_sigungu_total_base_code", columnNames = {"baseYmd", "signguCode"})
        },
        indexes = {
                @Index(name = "idx_sigungu_total_base_ymd", columnList = "baseYmd")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SigunguDailyTotal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 8)
    private String baseYmd;
    @Column(nullable = false, length = 20)
    private String signguCode;
    @Column
    private String signguName;
    @Column
    private Integer daywkDivCd;
    @Column(nullable = false)
    private Double totalTouNum;
    @Column(nullable = false)
    private LocalDateTime fetchedAt;
}
