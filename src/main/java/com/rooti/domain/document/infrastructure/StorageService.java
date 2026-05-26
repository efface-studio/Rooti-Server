package com.rooti.domain.document.infrastructure;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * Abstraction over "where files live". Two implementations ship:
 *
 * <ul>
 *   <li>{@link LocalDiskStorageService} — dev / on-prem
 *   <li>(planned) S3StorageService — production / multi-AZ
 * </ul>
 */
public interface StorageService {

    /** Result of a successful upload. */
    record Uploaded(String key, String url, long size, String contentType) {}

    Uploaded store(String pathPrefix, String originalFilename, InputStream content, long size, String contentType);

    InputStream open(String key);

    Path resolve(String key);

    void delete(String key);
}
