package com.onefin.ewallet.settlement.service.vnpay;

import com.onefin.ewallet.common.base.constants.OneFinConstants;
import com.onefin.ewallet.common.domain.billpay.vnpay.trans.VnpayTopupTransaction;
import com.onefin.ewallet.common.domain.settlement.SettlementTransaction;
import com.onefin.ewallet.common.utility.date.DateTimeHelper;
import com.onefin.ewallet.settlement.common.SettleConstants;
import com.onefin.ewallet.settlement.common.SettleHelper;
import com.onefin.ewallet.settlement.repository.SettleTransRepository;
import com.onefin.ewallet.settlement.repository.VNPayAirtimeTransRepo;
import com.onefin.ewallet.settlement.service.ConfigLoader;
import com.onefin.ewallet.settlement.service.MinioService;
import com.onefin.ewallet.settlement.service.SettleService;
import org.joda.time.DateTime;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Component
@DisallowConcurrentExecution
public class VNPayAirtimeScheduleTask extends QuartzJobBean {

	private static final Logger LOGGER = LoggerFactory.getLogger(VNPayAirtimeScheduleTask.class);

	@Autowired
	private VNPayAirtimeTransRepo<?> transRepository;

	@Autowired
	private ConfigLoader configLoader;

	@Autowired
	private DateTimeHelper dateHelper;

	@Autowired
	private SettleTransRepository settleRepo;

	@Autowired
	private SettleService commonSettleService;

	@Autowired
	private MinioService minioService;

	@Autowired
	private SettleHelper settleHelper;

	@Autowired
	private ReportHelper reportHelper;

	/*
	 * *************************TRANS FILE PROCESSING*****************************
	 */

	/**
	 * Scheduled job: Statistic all trx monthly
	 *
	 * @throws Exception
	 */
	@Override
	protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
		DateTime currentTime = dateHelper.currentDateTime(OneFinConstants.HO_CHI_MINH_TIME_ZONE);
		try {
			DateTime previousTime = currentTime.minusDays(1);
			Date currentDate = currentTime.toLocalDateTime().toDate();
			Date previousDate = previousTime.toLocalDateTime().toDate();
			LOGGER.info("== Current date {}, Previous date {}", currentDate, previousDate);
			Pair<Date, Date> dateRange = dateHelper.getDateRangeInMonth(previousDate);
			taskEwalletVnpayAirtimeSettlement(previousDate, dateRange);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void scheduleTaskVNPayAirtimeSettlementManually(Date dateInSettleMonth) throws Exception {
		Pair<Date, Date> dateRange = dateHelper.getDateRangeInMonth(dateInSettleMonth);
		taskEwalletVnpayAirtimeSettlement(dateInSettleMonth, dateRange);
	}

	/*
	 * *************************TRANS FILE PROCESSING*****************************
	 */

	public void taskEwalletVnpayAirtimeSettlement(Date dateInSettleMonth, Pair<Date, Date> dateRange) throws Exception {
		LOGGER.info("== Start VNPay Airtime Settlement");
		// Find all SUCCESS transactions of previous month
		List<VnpayTopupTransaction> result = transRepository.findByTranStatusAndFromToDate(SettleConstants.TRANS_SUCCESS, dateRange.getFirst(), dateRange.getSecond());
		LOGGER.info("== Number VNPay Airtime Settlement Transaction: {}", result.size());
		File file = null;
		LOGGER.info("== Start prepare file");
		String settleDate = new SimpleDateFormat(SettleConstants.DATE_FORMAT_yyyyMM).format(new Date(dateInSettleMonth.getTime()));
		SettlementTransaction trx = settleRepo.findByPartnerAndDomainAndSettleDate(SettleConstants.PARTNER_VNPAY,
				SettleConstants.AIRTIME_TOPUP, settleDate);
		try {
			// Create transaction
			if (trx == null) {
				trx = (SettlementTransaction) settleHelper.createModelStructure(new SettlementTransaction());
				trx.getSettleKey().setDomain(SettleConstants.AIRTIME_TOPUP);
				trx.getSettleKey().setPartner(SettleConstants.PARTNER_VNPAY);
				trx.getSettleKey().setSettleDate(settleDate);
				trx.setStatus(SettleConstants.SETTLEMENT_PROCESSING);
				commonSettleService.save(trx);
			} else {
				trx.setStatus(SettleConstants.SETTLEMENT_PROCESSING);
				commonSettleService.update(trx);
			}

			file = new File(configLoader.getSftpVNpayAirtimeDirectoryStoreFile() + settleDate + "_" + SettleConstants.AIRTIME_TOPUP + ".xlsx");
			LOGGER.info("File location: {}", file.getCanonicalPath());


			// write settlement data to excel file
			reportHelper.writeVNPayAirtimeTrxExcel(result, file.getAbsolutePath());
			trx.setStatus(SettleConstants.SETTLEMENT_SUCCESS);
			minioService.uploadFile(configLoader.getBaseBucket(),
					configLoader.getSettleVNPayAirtimeMinioDefaultFolder()
							+ "/" + file.getName(),
					file, "text/plain");
			if (!trx.getFile().contains(file.getName())) {
				trx.getFile().add(file.getName());
			}
			commonSettleService.update(trx);
			file.delete();
		} catch (IOException e) {
			LOGGER.error(SettleConstants.ERROR_PREPARE_SETTLE_FILE, e);
			trx.setStatus(SettleConstants.SETTLEMENT_ERROR);
			commonSettleService.update(trx);
		}
	}
}
