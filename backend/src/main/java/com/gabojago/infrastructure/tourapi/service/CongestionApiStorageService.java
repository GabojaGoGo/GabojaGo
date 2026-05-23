package com.tripmate.backend.infrastructure.tourapi.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.tripmate.backend.common.aop.TrackExecutionTime;
import com.tripmate.backend.infrastructure.tourapi.domain.AreaDailyTotal;
import com.tripmate.backend.infrastructure.tourapi.domain.AreaDailyTotalRepository;
import com.tripmate.backend.infrastructure.tourapi.domain.CongestionApiBatch;
import com.tripmate.backend.infrastructure.tourapi.domain.CongestionApiBatchRepository;
import com.tripmate.backend.infrastructure.tourapi.domain.CongestionApiItem;
import com.tripmate.backend.infrastructure.tourapi.domain.SigunguDailyTotal;
import com.tripmate.backend.infrastructure.tourapi.domain.SigunguDailyTotalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
@Service
@RequiredArgsConstructor
@TrackExecutionTime
public class CongestionApiStorageService {
    private static final String SIGUNGU_SCOPE = "sigungu";
    private static final String AREA_SCOPE = "area";
    private final CongestionApiBatchRepository batchRepository;
    private final SigunguDailyTotalRepository sigunguDailyTotalRepository;
    private final AreaDailyTotalRepository areaDailyTotalRepository;
    @Transactional
    public void saveBatch(
            String scope,
            String requestYmd,
            String endpoint,
            String resultCode,
            String resultMsg,
            int totalCount,
            String rawJson,
            JsonNode itemNode,
            String primaryCodeKey,
            String secondaryCodeKey,
            String tertiaryCodeKey,
            String nameKey
    ) {
        CongestionApiBatch batch = CongestionApiBatch.builder()
                .scope(scope)
                .requestYmd(requestYmd)
                .endpoint(endpoint)
                .resultCode(resultCode)
                .resultMsg(resultMsg)
                .totalCount(totalCount)
                .fetchedAt(LocalDateTime.now())
                .rawJson(rawJson)
                .build();
        if (itemNode != null && !itemNode.isMissingNode() && !itemNode.isNull()) {
            if (itemNode.isArray()) {
                itemNode.forEach(node -> addItem(batch, node, primaryCodeKey, secondaryCodeKey, tertiaryCodeKey, nameKey));
            } else if (itemNode.isObject()) {
                addItem(batch, itemNode, primaryCodeKey, secondaryCodeKey, tertiaryCodeKey, nameKey);
            }
        }
        batchRepository.save(batch);
        replaceDailyTotals(scope, requestYmd, itemNode, primaryCodeKey, secondaryCodeKey, tertiaryCodeKey, nameKey);
    }
    private void replaceDailyTotals(
            String scope,
            String requestYmd,
            JsonNode itemNode,
            String primaryCodeKey,
            String secondaryCodeKey,
            String tertiaryCodeKey,
            String nameKey
    ) {
        Map<String, AggregatedTotal> totalsByCode = aggregateTotals(
                itemNode,
                requestYmd,
                primaryCodeKey,
                secondaryCodeKey,
                tertiaryCodeKey,
                nameKey
        );
        if (totalsByCode.isEmpty()) {
            return;
        }
        LocalDateTime fetchedAt = LocalDateTime.now();
        if (SIGUNGU_SCOPE.equals(scope)) {
            sigunguDailyTotalRepository.deleteAllByBaseYmd(requestYmd);
            List<SigunguDailyTotal> totals = new ArrayList<>();
            for (Map.Entry<String, AggregatedTotal> entry : totalsByCode.entrySet()) {
                AggregatedTotal total = entry.getValue();
                totals.add(SigunguDailyTotal.builder()
                        .baseYmd(requestYmd)
                        .signguCode(entry.getKey())
                        .signguName(total.name())
                        .daywkDivCd(total.daywkDivCd())
                        .totalTouNum(total.totalTouNum())
                        .fetchedAt(fetchedAt)
                        .build());
            }
            sigunguDailyTotalRepository.saveAll(totals);
            sigunguDailyTotalRepository.deleteAllByBaseYmdNot(requestYmd);
            return;
        }
        if (AREA_SCOPE.equals(scope)) {
            areaDailyTotalRepository.deleteAllByBaseYmd(requestYmd);
            List<AreaDailyTotal> totals = new ArrayList<>();
            for (Map.Entry<String, AggregatedTotal> entry : totalsByCode.entrySet()) {
                AggregatedTotal total = entry.getValue();
                totals.add(AreaDailyTotal.builder()
                        .baseYmd(requestYmd)
                        .areaCode(entry.getKey())
                        .areaName(total.name())
                        .daywkDivCd(total.daywkDivCd())
                        .totalTouNum(total.totalTouNum())
                        .fetchedAt(fetchedAt)
                        .build());
            }
            areaDailyTotalRepository.saveAll(totals);
            areaDailyTotalRepository.deleteAllByBaseYmdNot(requestYmd);
        }
    }
    private Map<String, AggregatedTotal> aggregateTotals(
            JsonNode itemNode,
            String requestYmd,
            String primaryCodeKey,
            String secondaryCodeKey,
            String tertiaryCodeKey,
            String nameKey
    ) {
        Map<String, AggregatedTotal> totalsByCode = new LinkedHashMap<>();
        if (itemNode == null || itemNode.isMissingNode() || itemNode.isNull()) {
            return totalsByCode;
        }
        if (itemNode.isArray()) {
            itemNode.forEach(node -> accumulateTotal(totalsByCode, node, requestYmd, primaryCodeKey, secondaryCodeKey, tertiaryCodeKey, nameKey));
        } else if (itemNode.isObject()) {
            accumulateTotal(totalsByCode, itemNode, requestYmd, primaryCodeKey, secondaryCodeKey, tertiaryCodeKey, nameKey);
        }
        return totalsByCode;
    }
    private void accumulateTotal(
            Map<String, AggregatedTotal> totalsByCode,
            JsonNode node,
            String requestYmd,
            String primaryCodeKey,
            String secondaryCodeKey,
            String tertiaryCodeKey,
            String nameKey
    ) {
        String code = firstNonBlank(
                text(node, primaryCodeKey),
                text(node, secondaryCodeKey),
                text(node, tertiaryCodeKey)
        );
        if (code == null) {
            return;
        }
        String baseYmd = text(node, "baseYmd");
        if (baseYmd == null || !requestYmd.equals(baseYmd)) {
            return;
        }
        String touDivCd = text(node, "touDivCd");
        if (touDivCd == null || !isSupportedTouDivCd(touDivCd)) {
            return;
        }
        Double touNum = node.path("touNum").isNumber() ? node.path("touNum").asDouble() : parseDoubleOrNull(text(node, "touNum"));
        if (touNum == null) {
            return;
        }
        Integer daywkDivCd = node.path("daywkDivCd").canConvertToInt() ? node.path("daywkDivCd").asInt() : null;
        String name = text(node, nameKey);
        AggregatedTotal current = totalsByCode.get(code);
        if (current == null) {
            totalsByCode.put(code, new AggregatedTotal(name, daywkDivCd, touNum));
            return;
        }
        totalsByCode.put(code, new AggregatedTotal(
                current.name() != null ? current.name() : name,
                current.daywkDivCd() != null ? current.daywkDivCd() : daywkDivCd,
                current.totalTouNum() + touNum
        ));
    }
    private boolean isSupportedTouDivCd(String touDivCd) {
        return "1".equals(touDivCd) || "2".equals(touDivCd) || "3".equals(touDivCd);
    }
    private void addItem(
            CongestionApiBatch batch,
            JsonNode node,
            String primaryCodeKey,
            String secondaryCodeKey,
            String tertiaryCodeKey,
            String nameKey
    ) {
        String code = firstNonBlank(
                text(node, primaryCodeKey),
                text(node, secondaryCodeKey),
                text(node, tertiaryCodeKey)
        );
        if (code == null) {
            return;
        }
        Integer daywkDivCd = node.path("daywkDivCd").canConvertToInt() ? node.path("daywkDivCd").asInt() : null;
        Double touNum = node.path("touNum").isNumber() ? node.path("touNum").asDouble() : parseDoubleOrNull(text(node, "touNum"));
        CongestionApiItem item = CongestionApiItem.builder()
                .batch(batch)
                .code(code)
                .name(text(node, nameKey))
                .daywkDivCd(daywkDivCd)
                .daywkDivNm(text(node, "daywkDivNm"))
                .touDivCd(text(node, "touDivCd"))
                .touDivNm(text(node, "touDivNm"))
                .touNum(touNum)
                .baseYmd(text(node, "baseYmd"))
                .build();
        batch.addItem(item);
    }
    private String text(JsonNode node, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String value = node.path(key).asText("");
        return value.isBlank() ? null : value;
    }
    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
    private Double parseDoubleOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
    private record AggregatedTotal(String name, Integer daywkDivCd, double totalTouNum) {
    }
}
