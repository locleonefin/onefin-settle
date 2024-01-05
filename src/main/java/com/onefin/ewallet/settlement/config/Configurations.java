package com.onefin.ewallet.settlement.config;

import com.onefin.ewallet.common.base.config.OkHttpUtil;
import io.minio.MinioClient;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class Configurations {

	@Autowired
	private Environment env;

	@Bean
	public ModelMapper modelMapper() {
		ModelMapper modelMapper = new ModelMapper();
		modelMapper.getConfiguration()
				.setMatchingStrategy(MatchingStrategies.STRICT);
		return modelMapper;
	}

	@Bean(name = "minioClient")
	public MinioClient generateMinioClient() {
		try {
			OkHttpUtil okHttpUtil = new OkHttpUtil(true);
			MinioClient client = MinioClient.builder().endpoint(env.getProperty("minio.host"))
					.credentials(env.getProperty("minio.accessKey"), env.getProperty("minio.secretKey"))
					.httpClient(okHttpUtil.getClient()).build();
			return client;
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage());
		}

	}
}