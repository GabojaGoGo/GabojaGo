package com.gabojago.userdata.repository;

import com.gabojago.userdata.domain.UserFootprint;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserFootprintRepository extends JpaRepository<UserFootprint, Long> {
    List<UserFootprint> findByUserIdOrderByVisitedAtDesc(String userId);
    void deleteByIdAndUserId(Long id, String userId);
}
