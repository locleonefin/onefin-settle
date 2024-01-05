package com.onefin.ewallet.settlement.job;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.quartz.QuartzJobBean;

import com.onefin.ewallet.common.quartz.config.QuartzConstants;
import com.onefin.ewallet.common.quartz.entity.SchedulerJobInfo;
import com.onefin.ewallet.common.quartz.repository.SchedulerRepository;
import com.onefin.ewallet.settlement.controller.SettleJobController;

@DisallowConcurrentExecution
public class ResumeJobDispatcher extends QuartzJobBean {

	private static final Logger LOGGER = LoggerFactory.getLogger(ResumeJobDispatcher.class);

	@Autowired
	private SettleJobController settleJobController;
	
	@Autowired
	private SchedulerRepository schedulerRepository;
	
	@Value("${spring.quartz.properties.org.quartz.scheduler.instanceName}")
	private String jobGroup;

	@Override
	protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
		LOGGER.info("Start resume all job");
		List<String> listStatus = new ArrayList<>(Arrays.asList(QuartzConstants.JOB_STATUS_PAUSED));
		List<SchedulerJobInfo> jobs = schedulerRepository.findByJobGroupAndListJobStatus(jobGroup, listStatus);
		for (SchedulerJobInfo job : jobs) {
			settleJobController.resumeJob(job);
		}
		LOGGER.info("End resume all job");
	}
}
