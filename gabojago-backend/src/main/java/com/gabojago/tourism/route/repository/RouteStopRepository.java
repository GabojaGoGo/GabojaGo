package com.gabojago.tourism.route.repository;

import com.gabojago.tourism.route.domain.RouteStop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RouteStopRepository extends JpaRepository<RouteStop, Long> {
    List<RouteStop> findAllByRoute_IdOrderByStopOrder(Long routeId);
}
