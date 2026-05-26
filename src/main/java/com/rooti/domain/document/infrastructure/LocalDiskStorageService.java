package com.rooti.domain.document.infrastructure;

import com.rooti.global.config.StorageProperties;
import com.rooti.global.exception.BusinessException;
import com.rooti.global.exception.ErrorCode;
import com.rooti.global.util.Ulid;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rooti.storage.driver", havingValue = "local", matchIfMissing = true)
public class LocalDiskStorageService implements StorageService {

    private final StorageProperties properties;

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Path.of(properties.local().root()));
            log.info("LocalDiskStorageService root = {}", properties.local().root());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot initialize storage root", e);
        }
    }

    @Override
    public Uploaded store(
            String prefix, String originalFilename, InputStream content, long size, String contentType) {
        String safePrefix = prefix == null ? "misc" : prefix.replaceAll("[^a-zA-Z0-9_/\\-]", "_");
        String ext = FilenameUtils.getExtension(originalFilename);
        String key = safePrefix + "/" + Ulid.next() + (ext.isEmpty() ? "" : "." + ext);

        Path target = Path.of(properties.local().root()).resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to write file to {}", target, e);
            throw new BusinessException(ErrorCode.STORAGE_UPLOAD_FAILED);
        }
        String url = properties.local().publicUrl() + "/" + key;
        return new Uploaded(key, url, size, contentType);
    }

    @Override
    public InputStream open(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
    }

    @Override
    public Path resolve(String key) {
        return Path.of(properties.local().root()).resolve(key).normalize();
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ignored) {
            // Best-effort delete; surfacing failure would be noisier than helpful here.
        }
    }
}
