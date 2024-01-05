package com.onefin.ewallet.settlement.job;

import com.onefin.ewallet.common.base.constants.OneFinConstants;
import com.onefin.ewallet.common.utility.date.DateTimeHelper;
import com.onefin.ewallet.settlement.service.bvbank.BVBankIBFTReconciliation;
import org.joda.time.DateTime;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

@DisallowConcurrentExecution
public class BVBankIBFTUploadReconciliationJob extends QuartzJobBean {

	private static final Logger LOGGER = LoggerFactory.getLogger(BVBankIBFTUploadReconciliationJob.class);

	private static final String BVBANK_IBFT_TRANSACTIONS_UPLOAD_DAILY = "BVBANK IBFT TRANSACTIONS UPLOAD DAILY - ";

	@Autowired
	private BVBankIBFTReconciliation bvBankIBFTReconciliation;

	@Autowired
	private DateTimeHelper dateTimeHelper;

	@Override
	protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
		String currentTime = dateTimeHelper.currentDateString(
				OneFinConstants.HO_CHI_MINH_TIME_ZONE,
				OneFinConstants.DATE_FORMAT_ddMMyyyy
		);
		bvBankIBFTReconciliation.ibftReconciliationUpload(currentTime);
	}
}
