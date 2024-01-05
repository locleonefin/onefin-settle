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
import com.onefin.ewallet.settlement.service.napas.ew2.NapasWL2ProcessTC;
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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;


@DisallowConcurrentExecution
public class BVBankReconciliationComponent extends QuartzJobBean {

	private static final Logger LOGGER = LoggerFactory.getLogger(BVBankReconciliationComponent.class);

	private static final String LOG_BVBANK_RECONCILIATION_DAILY = "BVBANK RECONCILIATION DAILY - ";

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

	@Value("${sftp.bvbank.virtualAcct.reconciliationEmail}")
	private String emailSend;

	@Value("${sftp.bvbank.virtualAcct.reconciliationCCEmail}")
	private String emailSendCC;

	@Override
	protected void executeInternal(JobExecutionContext jobExecutionContext) throws JobExecutionException {
		List<Date> reconciliationDate = new ArrayList<>();
		DateTime currentTime = dateTimeHelper.currentDateTime(OneFinConstants.HO_CHI_MINH_TIME_ZONE);
		String currentTimeString = dateTimeHelper.parseDateString(
				currentTime,OneFinConstants.DATE_FORMAT_yyyyMMDD);
		if (bvBankReconciliation.checkIsBvbHoliday(currentTime.toLocalDateTime().toDate())) {
			LOGGER.info("{}Today is BVBank holiday, no task", LOG_BVBANK_RECONCILIATION_DAILY);
			return;
		}

		SettlementTransaction trans = settleRepo.findByPartnerAndDomainAndSettleDate(SettleConstants.PARTNER_BVBANK,
				SettleConstants.VIRTUAL_ACCT, currentTimeString);

		if (trans == null) {
			try {
				trans = (SettlementTransaction) settleHelper.createModelStructure(new SettlementTransaction());
				trans.getSettleKey().setDomain(SettleConstants.VIRTUAL_ACCT);
				trans.getSettleKey().setPartner(SettleConstants.PARTNER_BVBANK);
				trans.getSettleKey().setSettleDate(currentTimeString);
				trans.setStatus(SettleConstants.SETTLEMENT_PENDING_T0);
				commonSettleService.save(trans);
				LOGGER.info("{} Created Pending Trans Successfully {}", LOG_BVBANK_RECONCILIATION_DAILY, currentTimeString);
			} catch (Exception e) {
				LOGGER.error("Create TC transaction error", e);
				return;
			}
		}

		if (trans.getStatus().equals(SettleConstants.SETTLEMENT_SUCCESS)){
			LOGGER.info("{} Settle for the day have already successful for day {}",
					LOG_BVBANK_RECONCILIATION_DAILY, currentTimeString);
			return;
		}

		DateTime previousTime = currentTime.minusDays(1);
		while (bvBankReconciliation.checkIsBvbHoliday(previousTime.toLocalDateTime().toDate())) {
			reconciliationDate.add(previousTime.toLocalDateTime().toDate());
			previousTime = previousTime.minusDays(1);
		}
		reconciliationDate.add(previousTime.toDate());
		LOGGER.info("{}Reconciliation date list: {}", LOG_BVBANK_RECONCILIATION_DAILY, reconciliationDate);
		try {
			List<String> listDate = reconciliationDate.stream().map(
					e -> {
						String dateString = dateTimeHelper.parseDate2String(
								e,
								SettleConstants.BVB_DATE_STRING_FILE_TITLE_FORMAT
						);
						return env.getProperty("sftp.bvbank.virtualAcct.fileNamePrefix") + dateString + ".txt";
					}
			).collect(Collectors.toList());
			trans.setFile(listDate);
			trans.setStatus(SettleConstants.SETTLEMENT_PROCESSING);
			commonSettleService.save(trans);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		boolean isSuccess = bvBankReconciliation.sendReconciliationEmail(reconciliationDate,currentTime,emailSend, emailSendCC);
		if (isSuccess){
			try {
				trans.setStatus(SettleConstants.SETTLEMENT_SUCCESS);
				commonSettleService.save(trans);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			// pause job
			// Settle completed => paused job
			SchedulerJobInfo jobTmp = new SchedulerJobInfo();
			jobTmp.setJobClass(BVBankReconciliationComponent.class.getName());
			settleJobController.pauseJob(jobTmp);
		}

	}
}
