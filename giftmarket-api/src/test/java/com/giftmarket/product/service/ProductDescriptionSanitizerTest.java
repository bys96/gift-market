package com.giftmarket.product.service;

import com.giftmarket.product.exception.ProductException;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ProductDescriptionSanitizerTest {
    private final ProductDescriptionSanitizer sanitizer = new ProductDescriptionSanitizer();
    private static final String ID = "12345678-1234-1234-1234-123456789abc";

    @Test
    void videoKeepsSourceAndEnforcesControlsAndMetadata() {
        String result = sanitizer.sanitize("<video src=\"https://storage.example/clip.mp4\" controls preload=\"auto\" autoplay muted loop onclick=\"alert(1)\"></video>");
        var video = Jsoup.parseBodyFragment(result).selectFirst("video");
        assertThat(video).isNotNull();
        assertThat(video.attr("src")).isEqualTo("https://storage.example/clip.mp4");
        assertThat(video.hasAttr("controls")).isTrue();
        assertThat(video.attr("preload")).isEqualTo("metadata");
        assertThat(result).doesNotContain("autoplay", "onclick", "muted", "loop");
    }

    @Test
    void unsafeVideoSourcesEventsAndStylesAreRemoved() {
        for (String src : new String[]{"javascript:alert(1)", "data:video/mp4;base64,AA", "file:///tmp/clip.mp4", "//evil.example/clip.mp4"}) {
            String result = sanitizer.sanitize("<video src=\"" + src + "\" onerror=\"alert(1)\" style=\"background:url(javascript:alert(1))\"></video><script>alert(1)</script>");
            assertThat(result).doesNotContain("src=", "onerror", "style=", "script", "alert(");
        }
    }

    @Test
    void storageKeysSurviveWithoutPersistingStorageOrigin() {
        String key = "products/12/content/video/" + ID + ".mp4";
        String imageKey = "products/12/content/" + ID + ".png";
        String result = sanitizer.sanitize("<video data-storage-key=\"" + key + "\" src=\"https://old.example/a.mp4\"></video><img data-storage-key=\"" + imageKey + "\">");
        assertThat(result).contains(key, imageKey, "controls", "preload=\"metadata\"").doesNotContain("src=", "old.example");
        assertThat(sanitizer.sanitize(result)).isEqualTo(result);
    }

    @Test
    void invalidKeysAndFourthVideoAreRejected() {
        for (String key : new String[]{"https://evil.example/x", "products/12/content/../../secret", "profiles/12/" + ID + ".png"}) {
            assertThatThrownBy(() -> sanitizer.sanitize("<img data-storage-key=\"" + key + "\">"))
                    .isInstanceOf(ProductException.class);
        }
        String video = "<video src=\"https://storage.example/a.mp4\"></video>";
        assertThat(sanitizer.sanitize(video.repeat(3))).contains("video");
        assertThatThrownBy(() -> sanitizer.sanitize(video.repeat(4)))
                .isInstanceOf(ProductException.class).hasMessageContaining("3개");
    }

    @Test
    void imagesLinksTablesAndTextAlignmentRemainCompatible() {
        String result = sanitizer.sanitize("<p style=\"text-align: center;position:fixed\"><strong>상품</strong><a href=\"https://example.com\">링크</a></p><table><tr><td>구성</td></tr></table><img src=\"http://localhost:9000/gift-market/products/12/content/" + ID + ".png\" alt=\"설명\" width=\"640\" onerror=\"alert(1)\"><img src=\"javascript:alert(1)\">");
        assertThat(result).contains("text-align: center", "<strong>상품</strong>", "https://example.com", "<table>", "<td>구성</td>", "http://localhost:9000", "alt=\"설명\"", "width=\"640\"")
                .doesNotContain("position", "onerror", "javascript:");
        assertThat(sanitizer.sanitize(null)).isEmpty();
        assertThat(sanitizer.sanitize(" ")).isEmpty();
    }
}
