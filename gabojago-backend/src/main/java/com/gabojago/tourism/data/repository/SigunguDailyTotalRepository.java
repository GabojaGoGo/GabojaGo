package com.gabojago.tourism.data.repository;

import com.gabojago.tourism.data.domain.SigunguDailyTotal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface SigunguDailyTotalRepository extends JpaRepository<SigunguDailyTotal, Long> {
    List<SigunguDailyTotal> findAllByBaseYmd(String baseYmd);
    void deleteAllByBaseYmd(String baseYmd);
    void deleteAllByBaseYmdNot(String baseYmd);
}
