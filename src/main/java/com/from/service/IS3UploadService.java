package com.from.service;

public interface IS3UploadService {

    /**
     * 이미지 byte[]를 S3의 지정 폴더에 업로드하고 접근 가능한 URL을 반환한다.
     *
     * @param data        이미지 바이너리
     * @param contentType MIME 타입 (예: image/png)
     * @param folder      S3 키 prefix (예: "inputs", "outputs")
     * @return S3 객체 URL
     */
    String upload(byte[] data, String contentType, String folder);
}