package com.onefin.ewallet.settlement.job;

import com.onefin.ewallet.common.base.constants.OneFinConstants;
import com.onefin.ewallet.common.domain.settlement.SettlementTransaction;
import com.onefin.ewallet.common.quartz.entity.SchedulerJobInfo;
import com.onefin.ewallet.common.utility.date.DateTimeHelper;
import com.onefin.ewallet.settlement.common.SettleConstants;
import com.onefin.ewallet.settlement.common.SettleHelper;
import com.onefin.ewallet.settlement.controller.SettleJobController;
import com.onefin.ewallet.settlement.repository.HolidayRepo;
import com.onefin.ewallet.settlement.repository.SettleTransRepository;
import com.onefin.ewallet.settlement.service.ConfigLoader;
import com.onefin.ewallet.settlement.service.MinioService;
import com.onefin.ewallet.settlement.service.SettleService;
import com.onefin.ewallet.settlement.service.bvbank.BVBankReconciliation;
import org.joda.time.DateTime;
import org.modelmapper.ModelMapper;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@DisallowConcurrentExecution
public class BVBankTransactionListExportJob extends QuartzJobBean {
	private static final Logger LOGGER = LoggerFactory.getLogger(BVBankTransactionListExportJob.class);

	private static final String BVBANK_TRANSACTIONS_EXPORT_DAILY = "BVBANK TRANSACTIONS EXPORT DAILY - ";

	@Autowired
	private HolidayRepo holidayRepo;

	@Autowired
	private ModelMapper modelMapper;

	@Autowired
	private DateTimeHelper dateTimeHelper;

	@Autowired
	private MinioService minioService;

	@Autowired
	private Environment env;

	@Autowired
	private ConfigLoader configLoader;

	@Autowired
	private BVBankReconciliation bvBankReconciliation;

	@Autowired
	private SettleService commonSettleService;

	@Autowired
	private SettleJobController settleJobController;

	@Autowired
	private SettleTransRepository settleRepo;

	@Autowired
	private SettleHelper settleHelper;

	@Value("${sftp.bvbank.transExport.reconciliationEmail}")
	private String emailSend;

	@Value("${sftp.bvbank.transExport.reconciliationCCEmail}")
	private String emailSendCC;

	@Override
	protected void executeInternal(JobExecutionContext context) throws JobExecutionException {

		String currentTime = dateTimeHelper.currentDateString(
				OneFinConstants.HO_CHI_MINH_TIME_ZONE,
				OneFinConstants.DATE_FORMAT_ddMMyyyy
		);

		try {
			bvBankReconciliation.executeBVBTransExportManually(currentTime,emailSend,emailSendCC);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

	}
}
