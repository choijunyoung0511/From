package com.from.service.impl;

import com.from.service.IS3UploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor //final 필드 생성자 주입
public class  S3UploadService implements IS3UploadService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.region}")
    private String region;

    @Override
    //s3에 이미지 업로드 before,after폴더로 나눠서 구분
    public String upload(byte[] data, String contentType, String folder) {
        log.info("{}.upload Start! - folder:{}, contentType:{}, size:{}bytes",
                this.getClass().getName(), folder, contentType, data.length);

        String ext = (contentType != null && contentType.contains("png")) ? "png" : "jpg";
        String key = folder + "/" + UUID.randomUUID() + "." + ext;
        String resolvedType = (contentType != null) ? contentType : "image/png";

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(resolvedType)
                .contentLength((long) data.length)
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(data));

        String url = "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + key;
        log.info("{}.upload End! - url:{}", this.getClass().getName(), url);
        return url;
    }
}