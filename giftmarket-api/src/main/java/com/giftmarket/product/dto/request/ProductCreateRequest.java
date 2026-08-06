package com.giftmarket.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProductCreateRequest(

        @NotNull(message = "카테고리를 선택해주세요.")
        Long categoryId,

        @NotBlank(message = "상품명을 입력해주세요.")
        @Size(max = 200, message = "상품명은 200자 이하입니다.")
        String name,

        @Size(max = 100, message = "브랜드명은 100자 이하입니다.")
        String brandName,

        @Size(max = 500, message = "상품 요약은 500자 이하입니다.")
        String summary,

        @Size(
                max = 50000,
                message = "상품 상세 설명은 50000자 이하입니다."
        )
        String description,

        @NotNull(message = "상품 가격을 입력해주세요.")
        @Positive(message = "상품 가격은 0원보다 커야 합니다.")
        Long price,

        @NotNull(message = "재고 수량을 입력해주세요.")
        @PositiveOrZero(message = "재고 수량은 0개 이상이어야 합니다.")
        Integer stockQuantity,

        @Size(
                max = 1000,
                message = "대표 이미지 키는 1000자 이하입니다."
        )
        String representativeImageKey,

        @Size(
                max = 10,
                message = "갤러리 이미지는 최대 10장까지 등록할 수 있습니다."
        )
        List<
                @NotBlank(message = "갤러리 이미지 키는 비어 있을 수 없습니다.")
                @Size(
                        max = 1000,
                        message = "갤러리 이미지 키는 1000자 이하입니다."
                )
                        String
                > galleryImageKeys,

        @NotNull(message = "무료배송 여부를 선택해주세요.")
        Boolean freeShipping,

        @NotNull(message = "배송비를 입력해주세요.")
        @PositiveOrZero(message = "배송비는 0원 이상이어야 합니다.")
        Long shippingFee,

        @NotNull(message = "판매 시작 여부를 선택해주세요.")
        Boolean startSale

) {

    public List<String> normalizedGalleryImageKeys() {
        return galleryImageKeys == null
                ? List.of()
                : List.copyOf(galleryImageKeys);
    }

    public long normalizedShippingFee() {
        return Boolean.TRUE.equals(freeShipping)
                ? 0L
                : shippingFee;
    }
}