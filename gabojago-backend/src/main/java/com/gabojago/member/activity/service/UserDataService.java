package com.gabojago.member.activity.service;

import com.gabojago.member.activity.domain.*;
import com.gabojago.member.activity.exception.BucketItemNotFoundException;
import com.gabojago.member.activity.repository.*;
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
        return prefRepo.findByUserId(parseUserId(userId)).orElse(null);
    }

    public UserPreference savePrefs(String userId, List<String> purposes, String duration) {
        Long userPk = parseUserId(userId);
        UserPreference pref = prefRepo.findByUserId(userPk)
                .orElseGet(() -> UserPreference.of(userPk, purposes, duration));
        pref.update(purposes, duration);
        return prefRepo.save(pref);
    }

    // ── 족적 ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<UserFootprint> getFootprints(String userId) {
        return footprintRepo.findByUserIdOrderByVisitedAtDesc(parseUserId(userId));
    }

    public UserFootprint addFootprint(String userId, String spotId, String spotName,
                                       String regionName, List<String> tags) {
        return footprintRepo.save(
                UserFootprint.of(parseUserId(userId), spotId, spotName, regionName, tags));
    }

    public void deleteFootprint(String userId, Long id) {
        footprintRepo.deleteByIdAndUserId(id, parseUserId(userId));
    }

    // ── 버킷리스트 ────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<UserBucketItem> getBucketList(String userId) {
        return bucketRepo.findByUserIdOrderByCreatedAtDesc(parseUserId(userId));
    }

    public UserBucketItem addBucketItem(String userId, String title, String area, String note) {
        return bucketRepo.save(UserBucketItem.of(parseUserId(userId), title, area, note));
    }

    public UserBucketItem updateBucketItem(String userId, Long id,
                                            String title, String area, String note,
                                            Boolean completed) {
        Long userPk = parseUserId(userId);
        UserBucketItem item = bucketRepo.findById(id)
                .filter(b -> b.getUserId().equals(userPk))
                .orElseThrow(() -> new BucketItemNotFoundException(id));
        if (title != null)  item.update(title, area, note);
        if (Boolean.TRUE.equals(completed)) item.complete();
        return bucketRepo.save(item);
    }

    public void deleteBucketItem(String userId, Long id) {
        bucketRepo.deleteByIdAndUserId(id, parseUserId(userId));
    }

    // ── 혜택 리포트 ───────────────────────────────────────

    @Transactional(readOnly = true)
    public List<UserBenefitReport> getBenefitReports(String userId) {
        return benefitRepo.findByUserIdOrderByAppliedAtDesc(parseUserId(userId));
    }

    @Transactional(readOnly = true)
    public int getTotalBenefitAmount(String userId) {
        return benefitRepo.sumAmountByUserId(parseUserId(userId));
    }

    public UserBenefitReport addBenefitReport(String userId, String benefitType,
                                               String benefitLabel, int amount) {
        return benefitRepo.save(
                UserBenefitReport.of(parseUserId(userId), benefitType, benefitLabel, amount));
    }

    public void deleteBenefitReport(String userId, Long id) {
        benefitRepo.deleteByIdAndUserId(id, parseUserId(userId));
    }

    // ── 회원 탈퇴 시 전체 삭제 ────────────────────────────

    public void deleteAllForUser(String userId) {
        Long userPk = parseUserId(userId);
        prefRepo.deleteById(userPk);
        footprintRepo.findByUserIdOrderByVisitedAtDesc(userPk)
                .forEach(f -> footprintRepo.deleteById(f.getId()));
        bucketRepo.findByUserIdOrderByCreatedAtDesc(userPk)
                .forEach(b -> bucketRepo.deleteById(b.getId()));
        benefitRepo.findByUserIdOrderByAppliedAtDesc(userPk)
                .forEach(r -> benefitRepo.deleteById(r.getId()));
    }

    private static Long parseUserId(String userId) {
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("유효하지 않은 사용자 ID", e);
        }
    }
}
