package com.gabojago.place.service;

import com.gabojago.place.domain.Category;
import com.gabojago.place.domain.enums.CategoryKind;
import com.gabojago.place.domain.enums.PlacePurposeCode;
import com.gabojago.place.domain.enums.PlaceSubtypeCode;
import com.gabojago.place.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * Enum을 Category 정의의 원본으로 사용해 애플리케이션 시작 시 DB 표현을 동기화한다.
 */
@Component
@RequiredArgsConstructor
public class CategoryDefinitionSynchronizer implements ApplicationRunner {

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (PlaceSubtypeCode subtypeCode : PlaceSubtypeCode.values()) {
            categoryRepository.findByKindAndCode(CategoryKind.SUBTYPE, subtypeCode.name())
                    .ifPresentOrElse(
                            category -> category.synchronize(subtypeCode),
                            () -> categoryRepository.save(Category.subtype(subtypeCode))
                    );
        }

        for (PlacePurposeCode purposeCode : PlacePurposeCode.values()) {
            categoryRepository.findByKindAndCode(CategoryKind.PURPOSE, purposeCode.name())
                    .ifPresentOrElse(
                            category -> category.synchronize(purposeCode),
                            () -> categoryRepository.save(Category.purpose(purposeCode))
                    );
        }

        validateNoUndefinedCategory();
    }

    private void validateNoUndefinedCategory() {
        Set<CategoryKey> definitions = new HashSet<>();
        for (PlaceSubtypeCode subtypeCode : PlaceSubtypeCode.values()) {
            definitions.add(new CategoryKey(CategoryKind.SUBTYPE, subtypeCode.name()));
        }
        for (PlacePurposeCode purposeCode : PlacePurposeCode.values()) {
            definitions.add(new CategoryKey(CategoryKind.PURPOSE, purposeCode.name()));
        }

        var undefinedCategories = categoryRepository.findAll().stream()
                .map(category -> new CategoryKey(category.getKind(), category.getCode()))
                .filter(key -> !definitions.contains(key))
                .toList();

        if (!undefinedCategories.isEmpty()) {
            throw new IllegalStateException(
                    "DB contains categories not defined by enum: " + undefinedCategories
            );
        }
    }

    private record CategoryKey(CategoryKind kind, String code) {
    }
}
