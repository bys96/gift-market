package com.giftmarket.global.storage.provider;

import com.giftmarket.global.storage.config.MinioProperties;
import com.giftmarket.global.storage.config.S3Properties;
import com.giftmarket.global.storage.dto.PresignedUrlRequest;
import com.giftmarket.global.storage.service.StorageService;
import com.giftmarket.global.storage.type.StorageType;
import io.minio.MinioClient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** 실제 SDK 서명을 생성하고 독립적인 SigV4 계산으로 변조를 검증한다. 외부 요청은 하지 않는다. */
class StorageUploadSignatureTest {
    private static final long MB = 1024 * 1024L;

    @ParameterizedTest
    @EnumSource(StorageType.class)
    void s3BindsValidatedSizeAndMime(StorageType type) throws Exception {
        String access = UUID.randomUUID().toString();
        String secret = UUID.randomUUID().toString();
        try (var presigner = S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(access, secret)))
                .build()) {
            verifyPolicy(new S3StorageProvider(mock(S3Client.class), presigner,
                    new S3Properties("ap-northeast-2", "upload-test", access, secret)), type, secret);
        }
    }

    @ParameterizedTest
    @EnumSource(StorageType.class)
    void minioBindsValidatedSizeAndMime(StorageType type) throws Exception {
        String access = UUID.randomUUID().toString();
        String secret = UUID.randomUUID().toString();
        // 명시적 region으로 SDK의 bucket region 네트워크 조회를 방지한다.
        try (var client = MinioClient.builder().endpoint("https://storage.example.test")
                .region("us-east-1").credentials(access, secret).build()) {
            verifyPolicy(new MinioStorageProvider(client,
                    new MinioProperties("https://storage.example.test", access, secret, "upload-test")), type, secret);
        }
    }

    private void verifyPolicy(StorageProvider provider, StorageType type, String secret) throws Exception {
        boolean video = type == StorageType.PRODUCT_CONTENT_VIDEO;
        String mime = video ? "video/mp4" : "image/png";
        String name = video ? "clip.mp4" : "image.png";
        long max = switch (type) {
            case PRODUCT_CONTENT_VIDEO -> 50 * MB;
            case PRODUCT_REPRESENTATIVE, PRODUCT_GALLERY, PRODUCT_CONTENT -> 20 * MB;
            default -> 5 * MB;
        };
        var service = new StorageService(provider);
        String url = service.createPresignedUrl(12L, new PresignedUrlRequest(type, name, mime, MB)).uploadUrl();
        assertThat(query(URI.create(url)).get("X-Amz-SignedHeaders"))
                .isEqualTo("content-length;content-type;host");
        assertThat(query(URI.create(url)).get("X-Amz-Expires")).isEqualTo("300");
        assertThat(signatureMatches(url, MB, mime, secret)).isTrue();
        // metadata 1MB로 받은 URL에 실제 51MB 영상 / 21MB 상품 이미지 / 6MB 기타 이미지를 보낼 때.
        assertThat(signatureMatches(url, max + MB, mime, secret)).isFalse();
        assertThat(signatureMatches(url, MB + 1, mime, secret)).isFalse();
        assertThat(signatureMatches(url, MB, "application/octet-stream", secret)).isFalse();
        assertThat(signatureMatches(url, null, mime, secret)).isFalse();

        String boundaryUrl = service.createPresignedUrl(12L, new PresignedUrlRequest(type, name, mime, max)).uploadUrl();
        assertThat(signatureMatches(boundaryUrl, max, mime, secret)).isTrue();
        assertThat(signatureMatches(boundaryUrl, max + 1, mime, secret)).isFalse();
        assertThatThrownBy(() -> service.createPresignedUrl(12L,
                new PresignedUrlRequest(type, name, mime, max + 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Map<String, String> query(URI uri) {
        return Arrays.stream(uri.getRawQuery().split("&"))
                .map(part -> part.split("=", 2))
                .collect(Collectors.toMap(part -> decode(part[0]), part -> decode(part[1])));
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private boolean signatureMatches(String url, Long length, String mime, String secret) throws Exception {
        URI uri = URI.create(url);
        var params = query(uri);
        var headers = new TreeMap<String, String>();
        if (length != null) headers.put("content-length", length.toString());
        headers.put("content-type", mime);
        headers.put("host", uri.getRawAuthority());
        String signedHeaders = params.get("X-Amz-SignedHeaders");
        for (String header : signedHeaders.split(";")) {
            if (!headers.containsKey(header)) return false;
        }
        String canonicalQuery = Arrays.stream(uri.getRawQuery().split("&"))
                .filter(part -> !part.startsWith("X-Amz-Signature="))
                .sorted().collect(Collectors.joining("&"));
        String canonicalHeaders = headers.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue() + "\n")
                .collect(Collectors.joining());
        String canonical = "PUT\n" + uri.getRawPath() + "\n" + canonicalQuery + "\n"
                + canonicalHeaders + "\n" + signedHeaders + "\nUNSIGNED-PAYLOAD";
        String scope = params.get("X-Amz-Credential").split("/", 2)[1];
        String[] scopeParts = scope.split("/");
        byte[] key = ("AWS4" + secret).getBytes(StandardCharsets.UTF_8);
        for (String part : scopeParts) key = hmac(key, part);
        String hashed = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        String stringToSign = "AWS4-HMAC-SHA256\n" + params.get("X-Amz-Date") + "\n" + scope + "\n" + hashed;
        return HexFormat.of().formatHex(hmac(key, stringToSign)).equals(params.get("X-Amz-Signature"));
    }

    private byte[] hmac(byte[] key, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }
}
