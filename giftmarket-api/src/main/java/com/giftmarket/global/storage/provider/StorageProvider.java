package com.giftmarket.global.storage.provider;

public interface StorageProvider {

    String createUploadUrl(
            String objectKey,
            int expirationSeconds,
            long contentLength,
            String contentType
    );

    String createReadUrl(
            String objectKey,
            int expirationSeconds
    );

    boolean objectExists(String objectKey);

    void deleteObject(String objectKey);
}
