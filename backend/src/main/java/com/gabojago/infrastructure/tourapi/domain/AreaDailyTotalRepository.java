package com.tripmate.backend.infrastructure.tourapi.domain;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AreaDailyTotalRepository extends JpaRepository<AreaDailyTotal, Long> {
    List<AreaDailyTotal> findAllByBaseYmd(String baseYmd);
    void deleteAllByBaseYmd(String baseYmd);
    void deleteAllByBaseYmdNot(String baseYmd);
}
