package com.onefin.ewallet.settlement.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.onefin.ewallet.common.base.service.BaseMinIOAdapter;

import io.minio.MinioClient;

import java.io.File;

@Service
public class MinioService extends BaseMinIOAdapter {

	@Autowired
	@Qualifier("minioClient")
	public void setMinioClinet(MinioClient MinioClient) {
		this.setBaseMinioClient(MinioClient);
	}

}