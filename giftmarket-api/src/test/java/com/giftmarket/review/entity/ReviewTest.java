package com.giftmarket.review.entity;

import com.giftmarket.order.entity.OrderItem;
import com.giftmarket.product.entity.Product;
import com.giftmarket.product.entity.ProductVariant;
import com.giftmarket.user.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ReviewTest {
    private final User user = mock(User.class);
    private final OrderItem item = mock(OrderItem.class);
    private final Product product = mock(Product.class);

    @Test void ratingOneIsAllowed() { assertThat(review(1).getRating()).isEqualTo(1); }
    @Test void ratingFiveIsAllowed() { assertThat(review(5).getRating()).isEqualTo(5); }
    @Test void ratingZeroIsRejected() { assertThatThrownBy(() -> review(0)).isInstanceOf(IllegalArgumentException.class); }
    @Test void ratingSixIsRejected() { assertThatThrownBy(() -> review(6)).isInstanceOf(IllegalArgumentException.class); }
    @Test void blankContentIsRejected() { assertThatThrownBy(() -> create(5, "  ")).isInstanceOf(IllegalArgumentException.class); }
    @Test void tooLongContentIsRejected() { assertThatThrownBy(() -> create(5, "a".repeat(2001))).isInstanceOf(IllegalArgumentException.class); }
    @Test void contentIsTrimmed() { assertThat(create(5, " good ").getContent()).isEqualTo("good"); }
    @Test void targetSnapshotIsStored() { Review r=review(5); assertThat(r.getProductNameSnapshot()).isEqualTo("상품"); assertThat(r.getOptionSnapshot()).isEqualTo("색상: 파랑"); assertThat(r.getUnitPriceSnapshot()).isEqualTo(1000); }
    @Test void variantCanBeNull() { assertThat(review(5).getVariant()).isNull(); }
    @Test void deleteIsSoftDelete() { Review r=review(5); r.delete(LocalDateTime.now()); assertThat(r.getDeletedAt()).isNotNull(); }
    @Test void restoreClearsDeletedAtAndRefreshesTarget() { Review r=review(5); r.delete(LocalDateTime.now()); ProductVariant variant=mock(ProductVariant.class); r.restore(product,variant,"교환 상품","색상: 빨강",1200,4,"교환 후기"); assertThat(r.getDeletedAt()).isNull(); assertThat(r.getVariant()).isSameAs(variant); assertThat(r.getOptionSnapshot()).isEqualTo("색상: 빨강"); }
    @Test void updateDoesNotChangeTarget() { Review r=review(5); r.update(3,"수정"); assertThat(r.getProduct()).isSameAs(product); assertThat(r.getRating()).isEqualTo(3); }
    @Test void invalidPriceIsRejected() { assertThatThrownBy(() -> Review.create(user,item,product,null,"상품",null,0,5,"후기")).isInstanceOf(IllegalArgumentException.class); }
    @Test void imageAcceptsFirstAndLastOrder() { Review r=review(5); assertThat(ReviewImage.create(r,"reviews/1/a.jpg",0).getSortOrder()).isZero(); assertThat(ReviewImage.create(r,"reviews/1/e.jpg",4).getSortOrder()).isEqualTo(4); }
    @Test void imageRejectsSixthOrder() { assertThatThrownBy(() -> ReviewImage.create(review(5),"reviews/1/f.jpg",5)).isInstanceOf(IllegalArgumentException.class); }

    private Review review(int rating) { return create(rating,"좋아요"); }
    private Review create(int rating,String content) { return Review.create(user,item,product,null,"상품","색상: 파랑",1000,rating,content); }
}
