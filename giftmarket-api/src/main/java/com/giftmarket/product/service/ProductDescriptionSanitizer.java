package com.giftmarket.product.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

@Component
public class ProductDescriptionSanitizer {

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

        return Jsoup.clean(
                html,
                "",
                safelist,
                outputSettings
        );
    }
}