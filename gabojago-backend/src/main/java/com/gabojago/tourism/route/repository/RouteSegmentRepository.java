package com.gabojago.tourism.route.repository;

import com.gabojago.tourism.route.domain.RouteSegment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RouteSegmentRepository extends JpaRepository<RouteSegment, Long> {
    List<RouteSegment> findAllByRoute_Id(Long routeId);
}
