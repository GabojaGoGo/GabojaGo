package com.gabojago.tourism.route.repository;

import com.gabojago.tourism.route.domain.TravelRoute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TravelRouteRepository extends JpaRepository<TravelRoute, Long> {
    List<TravelRoute> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
