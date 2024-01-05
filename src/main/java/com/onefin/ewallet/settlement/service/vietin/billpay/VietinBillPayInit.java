package com.onefin.ewallet.settlement.service.vietin.billpay;

import com.onefin.ewallet.common.base.constants.OneFinConstants;
import com.onefin.ewallet.common.domain.billpay.vietin.trans.VietinBillPayTransaction;
import com.onefin.ewallet.common.domain.settlement.SettlementTransaction;
import com.onefin.ewallet.common.utility.date.DateTimeHelper;
import com.onefin.ewallet.common.utility.sercurity.SercurityHelper;
import com.onefin.ewallet.settlement.common.SettleConstants;
import com.onefin.ewallet.settlement.common.SettleHelper;
import com.onefin.ewallet.settlement.config.SFTPVietinBillPaywalletIntegration.UploadVietinBillPayGateway;
import com.onefin.ewallet.settlement.repository.SettleTransRepository;
import com.onefin.ewallet.settlement.repository.VietinBillPayTransRepo;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Component
@DisallowConcurrentExecution
public class VietinBillPayInit extends QuartzJobBean {

	private static final Logger LOGGER = LoggerFactory.getLogger(VietinBillPayInit.class);

	private static final String LOG_BILL_PREFIX = SettleConstants.PARTNER_VIETINBANK + " " + SettleConstants.PAYBILL;

//    private static final String VIETINBANK_BILLPAY_ISSUE_SUBJECT = "[VietinBank - BillPayment] - Settle error";

	@Autowired
	private VietinBillPayTransRepo<?> billpayTransRepository;

	@Autowired
	private ConfigLoader configLoader;

	@Autowired
	private UploadVietinBillPayGateway gateway;

	@Autowired
	private DateTimeHelper dateHelper;

	@Autowired
	private SercurityHelper sercurityHelper;

	@Autowired
	@Qualifier("SftpVietinBillPayRemoteFileTemplate")
	private SftpRemoteFileTemplate sftpVietinGateway;

	@Autowired
	private SettleTransRepository settleRepo;

	@Autowired
	private SettleService commonSettleService;

	@Autowired
	private MinioService minioService;

	@Autowired
	private SettleHelper settleHelper;

	/* *************************TRANS FILE PROCESSING***************************** */

	/**
	 * @throws Exception Scheduled at scheduled time, OneFin creates settlement file included success transaction of previous day and send to VietinBank
	 */
	@Override
	protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
		DateTime currentTime = dateHelper.currentDateTime(OneFinConstants.HO_CHI_MINH_TIME_ZONE);
		try {
			taskVietinBillPaymentSettlement(currentTime);
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
		}
	}

	public void scheduleTaskVietinBillPaymentSettlementManually(String settleDate) throws Exception {
		DateTime currentTime = dateHelper.parseDate(settleDate, SettleConstants.HO_CHI_MINH_TIME_ZONE,
				SettleConstants.DATE_FORMAT_ddMMyyyy);
		taskVietinBillPaymentSettlement(currentTime);
	}

	/* *************************TRANS FILE PROCESSING***************************** */

	public void taskVietinBillPaymentSettlement(DateTime currentTime) throws Exception {
		LOGGER.info("Start Settlement {}", LOG_BILL_PREFIX);
		DateTime previousTime = currentTime.minusDays(1);
		Date currentDate = currentTime.toLocalDateTime().toDate();
		Date previousDate = previousTime.toLocalDateTime().toDate();
		SimpleDateFormat formatterSearchDate = new SimpleDateFormat("yyyy-MM-dd");
		LOGGER.info("{} Current date {}, Previous date {}", LOG_BILL_PREFIX, currentDate, previousDate);
		BufferedWriter writer = null;

		String merchantId = configLoader.getVietinBillPayMerchantId();
		String providerId = configLoader.getVietinBillPayProviderId();

		List<String> statusList = new ArrayList<>();
		statusList.add(OneFinConstants.TRANS_SUCCESS);
		statusList.add(OneFinConstants.TRANS_RECONSOLIDATE_SUCCESS);

		// Find all SUCCESS transaction of previous date
		List<VietinBillPayTransaction> result = billpayTransRepository.findSettleTransaction(
				OneFinConstants.O_PAYBILL, statusList, formatterSearchDate.format(previousDate));

		LOGGER.info("{} Number Settlement Transaction: {}", LOG_BILL_PREFIX, result.size());
		File file = null;
		SettlementTransaction trans = null;
		// Process file to upload FTP folder
		try {
			LOGGER.info("{} Start prepare file to upload Vietin FTP server", LOG_BILL_PREFIX);
			SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
			String fileName = formatter.format(previousDate);

			// Create transaction
			trans = settleRepo.findByPartnerAndDomainAndSettleDate(SettleConstants.PARTNER_VIETINBANK, SettleConstants.PAYBILL, formatter.format(currentDate));
			LOGGER.info("{} Settle Transaction details: {}", LOG_BILL_PREFIX, trans);
			if (trans == null) {
				LOGGER.info("{} Transaction exist", LOG_BILL_PREFIX);
				trans = (SettlementTransaction) settleHelper.createModelStructure(new SettlementTransaction());
				trans.getSettleKey().setDomain(SettleConstants.PAYBILL);
				trans.getSettleKey().setPartner(SettleConstants.PARTNER_VIETINBANK);
				trans.getSettleKey().setSettleDate(formatter.format(currentDate));
				trans.setStatus(SettleConstants.SETTLEMENT_PROCESSING);
				commonSettleService.save(trans);
			} else {
				LOGGER.info("{} Transaction not exist", LOG_BILL_PREFIX);
				trans.setStatus(SettleConstants.SETTLEMENT_PROCESSING);
				commonSettleService.update(trans);
			}

			file = new File(configLoader.getSftpVietinbankBillPayDirectoryStoreFile() + fileName + "_" + providerId + "_BILL_IN.txt");
			LOGGER.info("{} Settle file name {}", LOG_BILL_PREFIX, file.getName());
			LOGGER.info("{} File location: {}", LOG_BILL_PREFIX, file.getCanonicalPath());
			writer = new BufferedWriter(new FileWriter(file));

			// write record header
			writer.write("recordType,requestId,merchantId,serviceType,svrProviderCode,custId,amount,currencyCode,billId,billCycle,transTime,status,bankTransId,recordChecksum");
			writer.newLine();

			// Write record data
			LOGGER.info("{} Start write record to file", LOG_BILL_PREFIX);
			StringBuffer sbfCheckum = new StringBuffer();
			String checksumAllRecord = "";
			for (VietinBillPayTransaction tmp : result) {
				String recordType = configLoader.getVietinBillPayRecordType();                        // default is success
				if (OneFinConstants.TRANS_RECONSOLIDATE_SUCCESS.equals(tmp.getTranStatus())) {
					recordType = configLoader.getVietinBillPayReconsolidateRecordType();            // in case reconsolidated
				}
				String requestId = tmp.getRequestId();
				String serviceType = tmp.getServiceType();
				String svrProviderCode = tmp.getSvrProviderCode();
				String custId = tmp.getCustId();
				String amount = tmp.getAmount().longValue() + "";
				String currencyCode = StringUtils.isEmpty(tmp.getCurrencyCode()) ? OneFinConstants.CURRENCY_VND : tmp.getCurrencyCode();
				String billId = tmp.getBillId();
				String billCycle = tmp.getBillCycle();
				String transTime = dateHelper.parseDate2DateString(SettleConstants.DATE_FORMAT_TRANS, SettleConstants.DATE_FORMAT_yyyyMMDDHHmmss, tmp.getTransTime());
				String status = configLoader.getVietinBillPaySuccessStatus();
				String bankTransId = tmp.getBankTransId();

				String tranId = tmp.getRequestId();
				String checkSumInput = String.format("%s%s%s%s%s%s%s%s%s%s%s%s",
						tranId, merchantId, svrProviderCode, serviceType, custId, amount, billId, billCycle, transTime, status, bankTransId, configLoader.getPrivateKeyVietinbankBillPayChecksum());

				String recordChecksum = sercurityHelper.MD5Hashing(checkSumInput, false);

				String newRecord = String.format("%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s",
						recordType, requestId, merchantId, serviceType, svrProviderCode, custId, amount, currencyCode, billId, billCycle, transTime, status, bankTransId, recordChecksum);
				writer.write(newRecord);
				writer.newLine();

				sbfCheckum.append(recordChecksum);
				checksumAllRecord = checksumAllRecord + recordChecksum;
			}
			LOGGER.info("{} End write record to file", LOG_BILL_PREFIX);
			// Write end file
			String endRecordType = configLoader.getVietinBillPayEndRecordType();
			String recordNumber = String.valueOf(result.size());

			String createUser = configLoader.getSettleVietinDefaultExecuteUser();
			String createFileTime = dateHelper.currentDateString(SettleConstants.HO_CHI_MINH_TIME_ZONE, SettleConstants.DATE_FORMAT_yyyyMMDDHHmmss);

			String checkSumInput = String.format("%s%s%s%s%s%s",
					providerId, createUser, recordNumber, createFileTime, checksumAllRecord, configLoader.getPrivateKeyVietinbankBillPayChecksum());
			String checksum = sercurityHelper.MD5Hashing(checkSumInput, false);

			String newRecord = String.format("%s|%s|%s|%s|%s|%s",
					endRecordType, providerId, createUser, recordNumber, createFileTime, checksum);
			writer.write(newRecord);

			trans.setStatus(SettleConstants.SETTLEMENT_PENDING_T0);

		} catch (IOException e) {
			trans.setStatus(SettleConstants.SETTLEMENT_ERROR);
			LOGGER.error("{} {}", LOG_BILL_PREFIX, SettleConstants.ERROR_PREPARE_SETTLE_FILE, e);
//            commonSettleService.reportIssue(currentDate.toString(), new Throwable().getStackTrace()[0].getMethodName(), SettleConstants.ERROR_PREPARE_SETTLE_FILE, VIETINBANK_BILLPAY_ISSUE_SUBJECT);
		} finally {
			try {
				writer.close();
				LOGGER.info("{} End prepare settlement file", LOG_BILL_PREFIX);
				LOGGER.info("{} Start upload settle file to SFTP", LOG_BILL_PREFIX);
				gateway.upload(file);
				if (!trans.getFile().contains(file.getName())) {
					trans.getFile().add(file.getName());
				}
				LOGGER.info("{} End upload settle file to SFTP", LOG_BILL_PREFIX);
				commonSettleService.update(trans);
				LOGGER.info("{} Start upload settle file to MINIO", LOG_BILL_PREFIX);
				minioService.uploadFile(configLoader.getBaseBucket(), configLoader.getSettleVietinBillPayMinioDefaultFolder() + dateHelper.parseDateString(currentTime, SettleConstants.DATE_FORMAT_ddMMyyyy) + "/" + file.getName(), file, "text/plain");
				LOGGER.info("{} END upload settle file to MINIO", LOG_BILL_PREFIX);
				file.delete();
			} catch (Exception e) {
				trans.setStatus(SettleConstants.SETTLEMENT_ERROR);
				commonSettleService.update(trans);
				LOGGER.error("{} {}", LOG_BILL_PREFIX, SettleConstants.ERROR_UPLOAD_SETTLE_FILE, e);
//                commonSettleService.reportIssue(currentDate.toString(), new Throwable().getStackTrace()[0].getMethodName(), SettleConstants.ERROR_UPLOAD_SETTLE_FILE, VIETINBANK_BILLPAY_ISSUE_SUBJECT);
			}
		}
	}

}
