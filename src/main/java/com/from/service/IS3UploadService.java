package com.from.service;

public interface IS3UploadService {


    //s3에 업로드
    String upload(byte[] data, String contentType, String folder);
}