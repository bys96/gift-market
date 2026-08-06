package com.giftmarket.product.config;

import com.giftmarket.product.entity.Category;
import com.giftmarket.product.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CategoryDataInitializer implements ApplicationRunner {

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (categoryRepository.count() > 0) {
            return;
        }

        Category food = saveRootCategory("식품", 1);
        Category beauty = saveRootCategory("뷰티", 2);
        Category fashion = saveRootCategory("패션", 3);
        Category living = saveRootCategory("리빙", 4);
        Category digital = saveRootCategory("디지털", 5);
        Category kids = saveRootCategory("유아동", 6);
        Category pet = saveRootCategory("반려동물", 7);
        Category giftCard = saveRootCategory("상품권·쿠폰", 8);

        saveChildren(food, List.of(
                "과자·간식",
                "베이커리·떡",
                "커피·차",
                "음료",
                "건강식품",
                "과일",
                "정육·수산",
                "간편식"
        ));

        saveChildren(beauty, List.of(
                "스킨케어",
                "메이크업",
                "향수",
                "헤어케어",
                "바디케어",
                "남성화장품",
                "뷰티기기"
        ));

        saveChildren(fashion, List.of(
                "여성의류",
                "남성의류",
                "가방",
                "신발",
                "지갑",
                "주얼리",
                "패션소품"
        ));

        saveChildren(living, List.of(
                "주방용품",
                "생활용품",
                "침구",
                "인테리어",
                "가구",
                "캔들·디퓨저",
                "꽃·식물"
        ));

        saveChildren(digital, List.of(
                "스마트폰 액세서리",
                "이어폰·헤드폰",
                "스마트워치",
                "컴퓨터 주변기기",
                "생활가전",
                "주방가전",
                "게임"
        ));

        saveChildren(kids, List.of(
                "완구",
                "유아용품",
                "유아식품",
                "아동의류",
                "아동도서",
                "출산선물"
        ));

        saveChildren(pet, List.of(
                "강아지 간식",
                "강아지 용품",
                "고양이 간식",
                "고양이 용품",
                "반려동물 건강용품"
        ));

        saveChildren(giftCard, List.of(
                "카페",
                "외식",
                "편의점",
                "문화·여가",
                "온라인 상품권",
                "모바일 쿠폰"
        ));
    }

    private Category saveRootCategory(String name, int sortOrder) {
        return categoryRepository.save(
                Category.create(null, name, sortOrder)
        );
    }

    private void saveChildren(Category parent, List<String> names) {
        for (int index = 0; index < names.size(); index++) {
            categoryRepository.save(
                    Category.create(parent, names.get(index), index + 1)
            );
        }
    }
}