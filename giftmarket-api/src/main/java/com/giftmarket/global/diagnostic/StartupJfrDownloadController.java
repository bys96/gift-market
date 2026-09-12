package com.giftmarket.global.diagnostic;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/admin/diagnostics")
@ConditionalOnProperty(
        name = "startup.diagnostics.jfr-download-enabled",
        havingValue = "true"
)
public class StartupJfrDownloadController {

    private static final Path STARTUP_JFR_PATH = Path.of(
            "/tmp/giftmarket-startup.jfr"
    );

    @GetMapping("/startup-jfr")
    public ResponseEntity<Resource> downloadStartupJfr() {
        if (!Files.isRegularFile(STARTUP_JFR_PATH)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(STARTUP_JFR_PATH);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("giftmarket-startup.jfr")
                .build();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        disposition.toString()
                )
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
