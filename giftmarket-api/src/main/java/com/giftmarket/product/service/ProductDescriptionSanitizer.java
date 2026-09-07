package com.giftmarket.product.service;

import com.giftmarket.product.exception.ProductException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

@Component
public class ProductDescriptionSanitizer {

    private static final int MAX_VIDEO_COUNT = 3;
    private static final String IMAGE_KEY_PATTERN = "products/[1-9][0-9]*/content/[a-fA-F0-9-]{36}\\.(jpg|jpeg|png|webp|gif)";
    private static final String VIDEO_KEY_PATTERN = "products/[1-9][0-9]*/content/video/[a-fA-F0-9-]{36}\\.mp4";
    private final Safelist safelist;

    public ProductDescriptionSanitizer() {
        this.safelist = Safelist.relaxed()

                // 이미지
                .addTags("img")
                .addAttributes(
                        "img",
                        "src",
                        "alt",
                        "title",
                        "width",
                        "height"
                )
                .addAttributes("img", "data-storage-key")

                // 상세 미디어는 object key 또는 http/https src만 허용한다.
                .addTags("video")
                .addAttributes("video", "src", "data-storage-key", "controls", "preload")
                .addProtocols("video", "src", "http", "https")
                .addEnforcedAttribute("video", "controls", "controls")
                .addEnforcedAttribute("video", "preload", "metadata")

                // 표
                .addTags(
                        "table",
                        "thead",
                        "tbody",
                        "tfoot",
                        "tr",
                        "th",
                        "td"
                )
                .addAttributes(
                        "table",
                        "border",
                        "cellpadding",
                        "cellspacing"
                )

                // 정렬
                .addAttributes(
                        ":all",
                        "style"
                )

                // 링크
                .addProtocols(
                        "a",
                        "href",
                        "http",
                        "https"
                )

                // 이미지
                .addProtocols(
                        "img",
                        "src",
                        "http",
                        "https"
                );
    }

    public String sanitize(String html) {

        if (html == null || html.isBlank()) {
            return "";
        }

        Document.OutputSettings outputSettings =
                new Document.OutputSettings()
                        .prettyPrint(false);

        String cleaned = Jsoup.clean(
                html,
                "",
                safelist,
                outputSettings
        );

        Document document = Jsoup.parseBodyFragment(cleaned);
        document.outputSettings(outputSettings);
        if (document.select("video").size() > MAX_VIDEO_COUNT) {
            throw new ProductException("상세 설명에는 동영상을 최대 3개까지 등록할 수 있습니다.");
        }
        for (Element media : document.select("img[data-storage-key], video[data-storage-key]")) {
            String pattern = media.normalName().equals("video") ? VIDEO_KEY_PATTERN : IMAGE_KEY_PATTERN;
            if (!media.attr("data-storage-key").matches(pattern)) {
                throw new ProductException("상품 상세 미디어 키가 올바르지 않습니다.");
            }
            // CDN/storage origin은 DB에 저장하지 않고 표시할 때 결정한다.
            media.removeAttr("src");
        }
        for (Element element : document.select("[style]")) {
            // 에디터의 텍스트 정렬만 유지하고 임의 CSS/url()/expression()은 제거한다.
            String alignment = "";
            for (String declaration : element.attr("style").split(";")) {
                String value = declaration.trim().toLowerCase();
                if (value.matches("text-align\\s*:\\s*(left|center|right|justify)")) {
                    alignment = value;
                }
            }
            element.removeAttr("style");
            if (!alignment.isEmpty() && !element.normalName().equals("video")) {
                element.attr("style", alignment);
            }
        }
        return document.body().html();
    }
}
