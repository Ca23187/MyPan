package com.mypan.service.file.storage;

import java.io.InputStream;

public interface BasicStorageService {

    void save(String objectKey, InputStream in, long size, String contentType);

    InputStream get(String objectKey);

    void delete(String objectKey);

}
