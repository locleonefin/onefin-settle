package com.onefin.ewallet.settlement.service.vnpay;

import com.onefin.ewallet.common.base.constants.OneFinConstants;
import com.onefin.ewallet.common.domain.settlement.SettlementTransaction;
import com.onefin.ewallet.common.domain.vnpay.SmsTransaction;
import com.onefin.ewallet.common.utility.date.DateTimeHelper;
import com.onefin.ewallet.settlement.common.SettleConstants;
import com.onefin.ewallet.settlement.common.SettleHelper;
import com.onefin.ewallet.settlement.repository.SettleTransRepository;
import com.onefin.ewallet.settlement.repository.VNPaySmsTransRepo;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
@DisallowConcurrentExecution
public class VNPaySmsScheduleTask extends QuartzJobBean {

	private static final Logger LOGGER = LoggerFactory.getLogger(VNPaySmsScheduleTask.class);

	private static final String SMS_REPORT_TITLE = "Thống kê SMS VNPAY %s";

	@Autowired
	private VNPaySmsTransRepo<?> transRepository;

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
	 * Scheduled job: Statistic all sms monthly
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
			taskEwalletVnpaySmsSettlement(previousDate, dateRange);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void scheduleTaskVNPaySmsSettlementManually(Date dateInSettleMonth) throws Exception {
		Pair<Date, Date> dateRange = dateHelper.getDateRangeInMonth(dateInSettleMonth);
		taskEwalletVnpaySmsSettlement(dateInSettleMonth, dateRange);
	}

	/*
	 * *************************TRANS FILE PROCESSING*****************************
	 */

	public void taskEwalletVnpaySmsSettlement(Date dateInSettleMonth, Pair<Date, Date> dateRange) throws Exception {
		LOGGER.info("== Start VNPay Sms Settlement");
		// Find all SUCCESS transactions of previous month
		List<SmsTransaction> result = transRepository.findByTranStatusAndFromToDate(
				configLoader.getVnpaySmsSuccessCode(), dateRange.getFirst(), dateRange.getSecond());
		LOGGER.info("== Number VNPay SMS Settlement Transaction: {}", result.size());
		File file = null;
		LOGGER.info("== Start prepare file");
		String settleDate = new SimpleDateFormat(SettleConstants.DATE_FORMAT_yyyyMM).format(new Date(dateInSettleMonth.getTime()));
		SettlementTransaction trx = settleRepo.findByPartnerAndDomainAndSettleDate(SettleConstants.PARTNER_VNPAY,
				SettleConstants.SMS_BRANDNAME, settleDate);
		// Process file to upload FTP folder
		try {
			// Create transaction
			if (trx == null) {
				trx = (SettlementTransaction) settleHelper.createModelStructure(new SettlementTransaction());
				trx.getSettleKey().setDomain(SettleConstants.SMS_BRANDNAME);
				trx.getSettleKey().setPartner(SettleConstants.PARTNER_VNPAY);
				trx.getSettleKey().setSettleDate(settleDate);
				trx.setStatus(SettleConstants.SETTLEMENT_PROCESSING);
				commonSettleService.save(trx);
			} else {
				trx.setStatus(SettleConstants.SETTLEMENT_PROCESSING);
				commonSettleService.update(trx);
			}

			file = new File(configLoader.getSftpVNpaySmsDirectoryStoreFile() + settleDate + "_" + SettleConstants.SMS_BRANDNAME + ".xlsx");
			LOGGER.info("File location: {}", file.getCanonicalPath());

			AtomicLong viettel = new AtomicLong();
			AtomicLong mobifone = new AtomicLong();
			AtomicLong vinaphone = new AtomicLong();
			AtomicLong vietnamobile = new AtomicLong();
			AtomicLong gmobile = new AtomicLong();
			AtomicLong itel = new AtomicLong();
			// Write record data
			result.forEach(e -> {
				if (e.getProviderId().equals(SettleConstants.VNSmsProviderId.VIETTEL.getProviderId())) {
					viettel.getAndIncrement();
				}
				if (e.getProviderId().equals(SettleConstants.VNSmsProviderId.MOBIFONE.getProviderId())) {
					mobifone.getAndIncrement();
				}
				if (e.getProviderId().equals(SettleConstants.VNSmsProviderId.VINAPHONE.getProviderId())) {
					vinaphone.getAndIncrement();
				}
				if (e.getProviderId().equals(SettleConstants.VNSmsProviderId.VIETNAMOBILE.getProviderId())) {
					vietnamobile.getAndIncrement();
				}
				if (e.getProviderId().equals(SettleConstants.VNSmsProviderId.GMOBILE.getProviderId())) {
					gmobile.getAndIncrement();
				}
				if (e.getProviderId().equals(SettleConstants.VNSmsProviderId.ITEL.getProviderId())) {
					itel.getAndIncrement();
				}
			});
			Map<String, Long> telcoSumSmsMap = new LinkedHashMap<>();
			telcoSumSmsMap.put(SettleConstants.VNSmsProviderId.VIETTEL.getTelco(), viettel.get());
			telcoSumSmsMap.put(SettleConstants.VNSmsProviderId.MOBIFONE.getTelco(), mobifone.get());
			telcoSumSmsMap.put(SettleConstants.VNSmsProviderId.VINAPHONE.getTelco(), vinaphone.get());
			telcoSumSmsMap.put(SettleConstants.VNSmsProviderId.VIETNAMOBILE.getTelco(), vietnamobile.get());
			telcoSumSmsMap.put(SettleConstants.VNSmsProviderId.GMOBILE.getTelco(), gmobile.get());
			telcoSumSmsMap.put(SettleConstants.VNSmsProviderId.ITEL.getTelco(), itel.get());

			// write settlement data to excel file
			reportHelper.writeTelcoSumSmsExcel(telcoSumSmsMap, file.getAbsolutePath(),
					String.format(SMS_REPORT_TITLE, settleDate));
			trx.setStatus(SettleConstants.SETTLEMENT_SUCCESS);
			minioService.uploadFile(configLoader.getBaseBucket(),
					configLoader.getSettleVNPaySmsMinioDefaultFolder()
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
