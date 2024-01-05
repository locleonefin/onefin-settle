package com.onefin.ewallet.settlement.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.onefin.ewallet.common.base.application.BaseApplication;

@IntegrationComponentScan("com.onefin.ewallet.settlement")
@EnableIntegration
@EnableScheduling
@EntityScan(basePackages = {"com.onefin.ewallet.common.domain.bank.vietin", "com.onefin.ewallet.common.domain.napas",
		"com.onefin.ewallet.common.domain.settlement", "com.onefin.ewallet.common.domain.vnpay","com.onefin.ewallet.common.domain.billpay.imedia","com.onefin.ewallet.common.domain.billpay.base",
		"com.onefin.ewallet.common.domain.billpay.vietin.trans", "com.onefin.ewallet.common.domain.billpay.vnpay.trans","com.onefin.ewallet.common.domain.errorCode",
		"com.onefin.ewallet.common.quartz.entity", "com.onefin.ewallet.common.domain.merchant", "com.onefin.ewallet.common.domain.bank.transfer",
		"com.onefin.ewallet.common.domain.bank.common", "com.onefin.ewallet.common.domain.holiday", "com.onefin.ewallet.common.domain.asc"})
@EnableJpaRepositories(basePackages = {"com.onefin.ewallet.settlement", "com.onefin.ewallet.common.quartz.repository"})
public class MainApplication extends BaseApplication {

	public static void main(String[] args) {
		SpringApplication.run(MainApplication.class, args);
	}

	@Bean
	public TaskScheduler taskScheduler() {
		final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(100);
		return scheduler;
	}

}
