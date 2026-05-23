package com.tripmate.backend.userdata.service;

import com.tripmate.backend.userdata.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class UserDataService {

    private final UserPreferenceRepository prefRepo;
    private final UserFootprintRepository  footprintRepo;
    private final UserBucketItemRepository bucketRepo;
    private final UserBenefitReportRepository benefitRepo;

    public UserDataService(UserPreferenceRepository prefRepo,
                           UserFootprintRepository footprintRepo,
                           UserBucketItemRepository bucketRepo,
                           UserBenefitReportRepository benefitRepo) {
        this.prefRepo      = prefRepo;
        this.footprintRepo = footprintRepo;
        this.bucketRepo    = bucketRepo;
        this.benefitRepo   = benefitRepo;
    }

    // ── 취향 설정 ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public UserPreference getPrefs(String userId) {
        return prefRepo.findByUserId(userId).orElse(null);
    }

    public UserPreference savePrefs(String userId, List<String> purposes, String duration) {
        UserPreference pref = prefRepo.findByUserId(userId)
                .orElseGet(() -> UserPreference.of(userId, purposes, duration));
        pref.update(purposes, duration);
        return prefRepo.save(pref);
    }

    // ── 족적 ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<UserFootprint> getFootprints(String userId) {
        return footprintRepo.findByUserIdOrderByVisitedAtDesc(userId);
    }

    public UserFootprint addFootprint(String userId, String spotId, String spotName,
                                       String regionName, List<String> tags) {
        return footprintRepo.save(
                UserFootprint.of(userId, spotId, spotName, regionName, tags));
    }

    public void deleteFootprint(String userId, Long id) {
        footprintRepo.deleteByIdAndUserId(id, userId);
    }

    // ── 버킷리스트 ────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<UserBucketItem> getBucketList(String userId) {
        return bucketRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public UserBucketItem addBucketItem(String userId, String title, String area, String note) {
        return bucketRepo.save(UserBucketItem.of(userId, title, area, note));
    }

    public UserBucketItem updateBucketItem(String userId, Long id,
                                            String title, String area, String note,
                                            Boolean completed) {
        UserBucketItem item = bucketRepo.findById(id)
                .filter(b -> b.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("not found"));
        if (title != null)  item.update(title, area, note);
        if (Boolean.TRUE.equals(completed)) item.complete();
        return bucketRepo.save(item);
    }

    public void deleteBucketItem(String userId, Long id) {
        bucketRepo.deleteByIdAndUserId(id, userId);
    }

    // ── 혜택 리포트 ───────────────────────────────────────

    @Transactional(readOnly = true)
    public List<UserBenefitReport> getBenefitReports(String userId) {
        return benefitRepo.findByUserIdOrderByAppliedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public int getTotalBenefitAmount(String userId) {
        return benefitRepo.sumAmountByUserId(userId);
    }

    public UserBenefitReport addBenefitReport(String userId, String benefitType,
                                               String benefitLabel, int amount) {
        return benefitRepo.save(
                UserBenefitReport.of(userId, benefitType, benefitLabel, amount));
    }

    public void deleteBenefitReport(String userId, Long id) {
        benefitRepo.deleteByIdAndUserId(id, userId);
    }

    // ── 회원 탈퇴 시 전체 삭제 ────────────────────────────

    public void deleteAllForUser(String userId) {
        prefRepo.deleteById(userId);
        footprintRepo.findByUserIdOrderByVisitedAtDesc(userId)
                .forEach(f -> footprintRepo.deleteById(f.getId()));
        bucketRepo.findByUserIdOrderByCreatedAtDesc(userId)
                .forEach(b -> bucketRepo.deleteById(b.getId()));
        benefitRepo.findByUserIdOrderByAppliedAtDesc(userId)
                .forEach(r -> benefitRepo.deleteById(r.getId()));
    }
}
