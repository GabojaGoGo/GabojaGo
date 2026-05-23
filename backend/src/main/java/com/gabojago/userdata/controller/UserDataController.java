package com.tripmate.backend.userdata.controller;

import com.tripmate.backend.userdata.domain.*;
import com.tripmate.backend.userdata.service.UserDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/me")
public class UserDataController {

    private final UserDataService svc;

    public UserDataController(UserDataService svc) {
        this.svc = svc;
    }

    // ── 취향 설정 ─────────────────────────────────────────

    @GetMapping("/prefs")
    public ResponseEntity<Map<String, Object>> getPrefs(@AuthenticationPrincipal String userId) {
        UserPreference p = svc.getPrefs(userId);
        if (p == null) return ResponseEntity.ok(Map.of("purposes", List.of(), "duration", ""));
        return ResponseEntity.ok(Map.of(
                "purposes",  p.getPurposes(),
                "duration",  p.getDuration() == null ? "" : p.getDuration(),
                "updatedAt", p.getUpdatedAt().toString()
        ));
    }

    @PutMapping("/prefs")
    public ResponseEntity<Void> savePrefs(@AuthenticationPrincipal String userId,
                                           @RequestBody PrefsRequest req) {
        svc.savePrefs(userId, req.purposes(), req.duration());
        return ResponseEntity.ok().build();
    }

    // ── 족적 ──────────────────────────────────────────────

    @GetMapping("/footprints")
    public List<Map<String, Object>> getFootprints(@AuthenticationPrincipal String userId) {
        return svc.getFootprints(userId).stream().map(f -> Map.<String, Object>of(
                "id",         f.getId(),
                "spotId",     f.getSpotId() == null ? "" : f.getSpotId(),
                "spotName",   f.getSpotName(),
                "regionName", f.getRegionName(),
                "visitedAt",  f.getVisitedAt().toString(),
                "tags",       f.getTags()
        )).toList();
    }

    @PostMapping("/footprints")
    public ResponseEntity<Map<String, Object>> addFootprint(
            @AuthenticationPrincipal String userId,
            @RequestBody FootprintRequest req) {
        UserFootprint f = svc.addFootprint(userId, req.spotId(), req.spotName(),
                req.regionName(), req.tags());
        return ResponseEntity.ok(Map.of("id", f.getId()));
    }

    @DeleteMapping("/footprints/{id}")
    public ResponseEntity<Void> deleteFootprint(@AuthenticationPrincipal String userId,
                                                  @PathVariable Long id) {
        svc.deleteFootprint(userId, id);
        return ResponseEntity.noContent().build();
    }

    // ── 버킷리스트 ────────────────────────────────────────

    @GetMapping("/bucket-list")
    public List<Map<String, Object>> getBucketList(@AuthenticationPrincipal String userId) {
        return svc.getBucketList(userId).stream().map(b -> Map.<String, Object>of(
                "id",        b.getId(),
                "title",     b.getTitle(),
                "area",      b.getArea() == null ? "" : b.getArea(),
                "note",      b.getNote() == null ? "" : b.getNote(),
                "completed", b.isCompleted(),
                "createdAt", b.getCreatedAt().toString()
        )).toList();
    }

    @PostMapping("/bucket-list")
    public ResponseEntity<Map<String, Object>> addBucketItem(
            @AuthenticationPrincipal String userId,
            @RequestBody BucketRequest req) {
        UserBucketItem b = svc.addBucketItem(userId, req.title(), req.area(), req.note());
        return ResponseEntity.ok(Map.of("id", b.getId()));
    }

    @PatchMapping("/bucket-list/{id}")
    public ResponseEntity<Void> updateBucketItem(
            @AuthenticationPrincipal String userId,
            @PathVariable Long id,
            @RequestBody BucketUpdateRequest req) {
        svc.updateBucketItem(userId, id, req.title(), req.area(), req.note(), req.completed());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/bucket-list/{id}")
    public ResponseEntity<Void> deleteBucketItem(@AuthenticationPrincipal String userId,
                                                   @PathVariable Long id) {
        svc.deleteBucketItem(userId, id);
        return ResponseEntity.noContent().build();
    }

    // ── 혜택 리포트 ───────────────────────────────────────

    @GetMapping("/benefit-reports")
    public ResponseEntity<Map<String, Object>> getBenefitReports(
            @AuthenticationPrincipal String userId) {
        List<Map<String, Object>> items = svc.getBenefitReports(userId).stream()
                .map(r -> Map.<String, Object>of(
                        "id",           r.getId(),
                        "benefitType",  r.getBenefitType(),
                        "benefitLabel", r.getBenefitLabel(),
                        "amount",       r.getAmount(),
                        "appliedAt",    r.getAppliedAt().toString()
                )).toList();
        return ResponseEntity.ok(Map.of(
                "totalSaved", svc.getTotalBenefitAmount(userId),
                "items",      items
        ));
    }

    @PostMapping("/benefit-reports")
    public ResponseEntity<Map<String, Object>> addBenefitReport(
            @AuthenticationPrincipal String userId,
            @RequestBody BenefitRequest req) {
        UserBenefitReport r = svc.addBenefitReport(userId, req.benefitType(),
                req.benefitLabel(), req.amount());
        return ResponseEntity.ok(Map.of("id", r.getId()));
    }

    @DeleteMapping("/benefit-reports/{id}")
    public ResponseEntity<Void> deleteBenefitReport(@AuthenticationPrincipal String userId,
                                                      @PathVariable Long id) {
        svc.deleteBenefitReport(userId, id);
        return ResponseEntity.noContent().build();
    }

    // ── Request records ───────────────────────────────────

    record PrefsRequest(List<String> purposes, String duration) {}

    record FootprintRequest(String spotId, String spotName, String regionName, List<String> tags) {}

    record BucketRequest(String title, String area, String note) {}

    record BucketUpdateRequest(String title, String area, String note, Boolean completed) {}

    record BenefitRequest(String benefitType, String benefitLabel, int amount) {}
}
