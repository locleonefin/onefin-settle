package com.onefin.ewallet.settlement.service.vietin.billpay;

import com.onefin.ewallet.common.base.constants.OneFinConstants;
import com.onefin.ewallet.common.base.service.BaseHelper;
import com.onefin.ewallet.common.base.service.RestTemplateHelper;
import com.onefin.ewallet.common.domain.billpay.vietin.trans.VietinBillPayTransaction;
import com.onefin.ewallet.common.domain.settlement.SettlementTransaction;
import com.onefin.ewallet.common.quartz.entity.SchedulerJobInfo;
import com.onefin.ewallet.common.utility.date.DateTimeHelper;
import com.onefin.ewallet.settlement.common.SettleConstants;
import com.onefin.ewallet.settlement.controller.SettleJobController;
import com.onefin.ewallet.settlement.repository.SettleTransRepository;
import com.onefin.ewallet.settlement.repository.VietinBillPayTransRepo;
import com.onefin.ewallet.settlement.service.ConfigLoader;
import com.onefin.ewallet.settlement.service.MinioService;
import com.onefin.ewallet.settlement.service.SettleService;
import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

import java.io.*;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Component
@DisallowConcurrentExecution
public class VietinBillPayCheckDispute extends QuartzJobBean {

	private static final Logger LOGGER = LoggerFactory.getLogger(VietinBillPayCheckDispute.class);

	private static final String LOG_BILL_PREFIX = SettleConstants.PARTNER_VIETINBANK + " " + SettleConstants.PAYBILL;

	@Autowired
	private VietinBillPayTransRepo<?> trxRepo;

	@Autowired
	private ConfigLoader configLoader;

	@Autowired
	private DateTimeHelper dateHelper;

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
	private SettleJobController settleJobController;

	@Autowired
	private Environment env;

	@Autowired
	private RestTemplateHelper restTemplateHelper;

	@Autowired
	private BaseHelper baseHelper;

	/* *************************DISPUTE FILE PROCESSING***************************** */

	/**
	 * @throws ParseException
	 * @throws IOException    After submitted transaction file to SFTP, VietinBank will process then return back dispute file. OF get and process
	 */
	@Override
	protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
		LOGGER.info("Start Settlement Dispute {}", LOG_BILL_PREFIX);
		DateTime currentTime = dateHelper.currentDateTime(OneFinConstants.HO_CHI_MINH_TIME_ZONE);
		try {
			taskVietinBillPaymentDisputeSettlement(currentTime, false);
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
		}
	}

	public void ewalletVietinBillPaymentDisputeSettlementManually(String settleDate) throws Exception {
		DateTime currentTime = dateHelper.parseDate(settleDate, SettleConstants.HO_CHI_MINH_TIME_ZONE,
				SettleConstants.DATE_FORMAT_ddMMyyyy);
		// Reset to pending status
		LOGGER.info("{} Start Settlement Dispute", LOG_BILL_PREFIX);
		SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
		String date = formatter.format(currentTime.toLocalDateTime().toDate());
		LOGGER.info("{} Dispute Date {}", LOG_BILL_PREFIX, date);
		SettlementTransaction trans = settleRepo.findByPartnerAndDomainAndSettleDate(SettleConstants.PARTNER_VIETINBANK, SettleConstants.PAYBILL, date);
		if (trans != null) {
			LOGGER.info("{} Transaction found", LOG_BILL_PREFIX);
			taskVietinBillPaymentDisputeSettlement(currentTime, true);
		} else {
			LOGGER.info("{} Transaction NOT found", LOG_BILL_PREFIX);
		}
	}
	/* *************************DISPUTE FILE PROCESSING***************************** */

	public void taskVietinBillPaymentDisputeSettlement(DateTime currentTime, boolean ignoreSettleBefore) throws Exception {
		LOGGER.info("{} Start process DISPUTE file {}", LOG_BILL_PREFIX, currentTime);
		DateTime previousTime = currentTime.minusDays(1);
		Date previousDate = previousTime.toLocalDateTime().toDate();
		SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
		String fileName = formatter.format(previousDate);
		String disputeFileName = fileName + "_BILLPAY_TRANS_DISPUTE_" + configLoader.getProviderNameVietinbankBillPay() + ".txt";
		LOGGER.info("{} Dispute file name {}", LOG_BILL_PREFIX, disputeFileName);
		SettlementTransaction trans = settleRepo.findByPartnerAndDomainAndSettleDateAndStatus(SettleConstants.PARTNER_VIETINBANK, SettleConstants.PAYBILL, formatter.format(currentTime.toLocalDateTime().toDate()), SettleConstants.SETTLEMENT_PENDING_T0);
		if (trans == null) {
			LOGGER.info("{} No settle dispute transaction found", LOG_BILL_PREFIX);
			return;
		}
		boolean vtSentDisputeFile = true;
		try {
			sftpVietinGateway.get(configLoader.getSftpVietinbankBillPayDirectoryRemoteIn() + disputeFileName, stream -> {
				FileCopyUtils.copy(stream, new FileOutputStream(
						new File(configLoader.getSftpVietinbankBillPayDirectoryStoreFile() + disputeFileName)));

				// TODO Update transaction
				File file = new File(configLoader.getSftpVietinbankBillPayDirectoryStoreFile() + disputeFileName);
				trans.setStatus(SettleConstants.SETTLEMENT_SUCCESS);
				LOGGER.info("{} Settlement Dispute file found {}", LOG_BILL_PREFIX, disputeFileName);
				try {
					int failTranx = readDisputeFile(file.getCanonicalPath());
					LOGGER.info("{} Read dispute file {}", LOG_BILL_PREFIX, failTranx);
					// Upload to minio server
					LOGGER.info("{} Start upload dispute file to MINIO", LOG_BILL_PREFIX);
					minioService.uploadFile(configLoader.getBaseBucket(), configLoader.getSettleVietinBillPayMinioDefaultFolder() + dateHelper.parseDateString(currentTime, SettleConstants.DATE_FORMAT_ddMMyyyy) + "/" + file.getName(), file, "text/plain");
					LOGGER.info("{} End upload dispute file to MINIO", LOG_BILL_PREFIX);
					if (!trans.getFile().contains(file.getName())) {
						trans.getFile().add(file.getName());
					}
					commonSettleService.update(trans);
					// Send email
//                    LOGGER.info("{} Send mail to report dispute file", LOG_BILL_PREFIX);
//                    String subject = null;
					if (failTranx == 0) {
						// Settle completed => paused job
//                        subject = VIETINBANK_BILLPAY_SUBJECT_NO_SETTLE;
						SchedulerJobInfo jobTmp = new SchedulerJobInfo();
						jobTmp.setJobClass(VietinBillPayCheckDispute.class.getName());
						settleJobController.pauseJob(jobTmp);
					}
//                    else {
//                        subject = VIETINBANK_BILLPAY_SUBJECT_SETTLE;
//                    }
//                    commonSettleService.settleCompleted(currentTime.toString(), configLoader.getBaseBucket(), configLoader.getSettleVietinBillPayMinioDefaultFolder() + dateHelper.parseDateString(currentTime, SettleConstants.DATE_FORMAT_ddMMyyyy) + "/" + file.getName(), subject);
					file.delete();
					LOGGER.info("{} End process dispute file", LOG_BILL_PREFIX);
				} catch (Exception e) {
					LOGGER.error("{} {}", LOG_BILL_PREFIX, "Error in processing dispute file", e);
//                    try {
//                        commonSettleService.reportIssue(currentTime.toString(), new Throwable().getStackTrace()[0].getMethodName(), SettleConstants.ERROR_DOWNLOAD_PARTNER_SETTLE_FILE, VIETINBANK_BILLPAY_ISSUE_SUBJECT);
//                    } catch (Exception e1) {
//                        LOGGER.error("{} Upload dispute file error {}", LOG_BILL_PREFIX, previousDate, e1);
//                    }
				}
			});
		} catch (Exception e) {
			vtSentDisputeFile = false;
			LOGGER.warn("{} Not Found Settlement Dispute File {}", LOG_BILL_PREFIX, previousDate, e);
		}
		String[] settleCompletedBefore = env.getProperty("settlement.vietin.billpay.settleCompletedBefore").split(":");
		LocalTime settleCompletedBeforeTime = new LocalTime(Integer.parseInt(settleCompletedBefore[0]), Integer.parseInt(settleCompletedBefore[1]), Integer.parseInt(settleCompletedBefore[2]));
		LocalTime currentLocalTime = LocalTime.now();
		if (currentLocalTime.compareTo(settleCompletedBeforeTime) == -1) {
			LOGGER.info("{} Find dispute file in backup {}", LOG_BILL_PREFIX, previousDate);
			String providerId = configLoader.getVietinBillPayProviderId();
			String tranxFile = fileName + "_" + providerId + "_BILL_IN.txt";
			boolean finalVtSentDisputeFile = vtSentDisputeFile;
			sftpVietinGateway.get(configLoader.getSftpVietinbankBillPayDirectoryRemoteBackup() + tranxFile, stream -> {

				List<String> statusList = new ArrayList<>();
				statusList.add(OneFinConstants.TRANS_SUCCESS);
				statusList.add(OneFinConstants.TRANS_RECONSOLIDATE_SUCCESS);

				// Find all SUCCESS transaction of previous date
				List<VietinBillPayTransaction> successTrx = trxRepo.findSettleTransaction(
						OneFinConstants.O_PAYBILL, statusList, formatter.format(previousDate));
				if (successTrx.size() > 0 && !finalVtSentDisputeFile) {
					trans.setStatus(SettleConstants.SETTLEMENT_ERROR);
					try {
						LOGGER.error("{} {}", LOG_BILL_PREFIX,
								"Vietin not send DISPUTE file");
						commonSettleService.update(trans);
						SchedulerJobInfo jobTmp = new SchedulerJobInfo();
						jobTmp.setJobClass(VietinBillPayCheckDispute.class.getName());
						settleJobController.pauseJob(jobTmp);
					} catch (Exception e) {
						LOGGER.error(e.getMessage(), e);
					}
				} else {
					trans.setStatus(SettleConstants.SETTLEMENT_SUCCESS);
					try {
						commonSettleService.update(trans);
						SchedulerJobInfo jobTmp = new SchedulerJobInfo();
						jobTmp.setJobClass(VietinBillPayCheckDispute.class.getName());
						settleJobController.pauseJob(jobTmp);
					} catch (Exception e) {
						LOGGER.error(e.getMessage(), e);
					}
				}

				trans.setStatus(SettleConstants.SETTLEMENT_SUCCESS);
				try {
					commonSettleService.update(trans);
					// Send email
//                    LOGGER.info("{} Send mail to report dispute file", LOG_BILL_PREFIX);
//                    commonSettleService.settleCompleted(currentTime.toString(), configLoader.getBaseBucket(),
//                            configLoader.getSettleVietinBillPayMinioDefaultFolder()
//                                    + dateHelper.parseDateString(currentTime, SettleConstants.DATE_FORMAT_ddMMyyyy)
//                                    + "/" + tranxFile,
//                            VIETINBANK_BILLPAY_SUBJECT_NO_SETTLE);
					// Settle completed => paused job
					SchedulerJobInfo jobTmp = new SchedulerJobInfo();
					jobTmp.setJobClass(VietinBillPayCheckDispute.class.getName());
					settleJobController.pauseJob(jobTmp);
				} catch (Exception e) {
					LOGGER.error(e.getMessage(), e);
					SchedulerJobInfo jobTmp = new SchedulerJobInfo();
					jobTmp.setJobClass(VietinBillPayCheckDispute.class.getName());
					settleJobController.pauseJob(jobTmp);
				}
			});
		} else {
			LOGGER.error("{} {}", LOG_BILL_PREFIX,
					"Vietin not access to SFTP");
		}
	}

	private int readDisputeFile(String disputeFile) throws IOException {
		FileReader reader = null;
		int failTranx = 0;
		BufferedReader br = null;
		try {
			reader = new FileReader(disputeFile);
			br = new BufferedReader(reader);
			// read line by line
			String line;
			br.readLine();
			while ((line = br.readLine()) != null) {
				String[] arr = line.split("\\|");
				if (!arr[11].equals(configLoader.getVietinBillPaySuccessStatus())
						&& arr[0].equals(configLoader.getVietinBillPayRecordType())) {
					// Invalid transaction
					// TODO mark transaction
					failTranx++;

					// Mark record as dispute, waiting settle EOM
					VietinBillPayTransaction trx = (VietinBillPayTransaction) baseHelper.createModelStructure(new VietinBillPayTransaction());
					trx.setTranStatus(SettleConstants.TRANS_DISPUTE);
					trx.setApiOperation(SettleConstants.O_PAYBILL);
					trx.setRequestId(arr[1]);
					trx.setMerchantId(arr[2]);
					trx.setServiceType(arr[3]);
					trx.setServiceCode(arr[4]);
					trx.setCustId(arr[5]);
					trx.setAmount(new BigDecimal(arr[6]));
					trx.setCurrencyCode(arr[7]);
					trx.setBillId(arr[8]);
					trx.setBillCycle(arr[9]);
					trx.setTransTime(dateHelper.parseDate2DateString(SettleConstants.DATE_FORMAT_yyyyMMDDHHmmss, SettleConstants.DATE_FORMAT_TRANS, arr[10], SettleConstants.HO_CHI_MINH_TIME_ZONE));
					trx.setBankTransId(arr[12]);

					String url = env.getProperty("conn-service.billpay.host") + env.getProperty("conn-service.billpay.uri.createVietinTrx");
					LOGGER.info("== Send create dispute trx to conn-billpay {} - url: {}", trx, url);
					HttpHeaders headers = new HttpHeaders();
					headers.setAccept(Collections.singletonList(MediaType.ALL));
					headers.setContentType(MediaType.APPLICATION_JSON);
					HashMap<String, String> headersMap = new HashMap<String, String>();
					for (String header : headers.keySet()) {
						headersMap.put(header, headers.getFirst(header));
					}
					ResponseEntity<VietinBillPayTransaction> responseEntity = restTemplateHelper.post(url,
							MediaType.APPLICATION_JSON_VALUE, headersMap, new ArrayList<String>(), new HashMap<>(),
							null, trx, new ParameterizedTypeReference<VietinBillPayTransaction>() {
							});
					LOGGER.info("== Success receive response from conn-bank {}", responseEntity.getBody());
				} else if (arr[0].equals(configLoader.getVietinBillPayRecordType())) {
					// Mark record as settle
					VietinBillPayTransaction trx = trxRepo.findByRequestId(arr[1]);
					trx.setTranStatus(SettleConstants.TRANS_SETTLED);

					String url = env.getProperty("conn-service.billpay.host") + env.getProperty("conn-service.billpay.uri.updateVietinTrx");
					LOGGER.info("== Send update trx to conn-billpay {} - url: {}", trx, url);
					HttpHeaders headers = new HttpHeaders();
					headers.setAccept(Collections.singletonList(MediaType.ALL));
					headers.setContentType(MediaType.APPLICATION_JSON);
					HashMap<String, String> headersMap = new HashMap<String, String>();
					for (String header : headers.keySet()) {
						headersMap.put(header, headers.getFirst(header));
					}
					ResponseEntity<VietinBillPayTransaction> responseEntity = restTemplateHelper.post(url,
							MediaType.APPLICATION_JSON_VALUE, headersMap, new ArrayList<String>(), new HashMap<>(),
							null, trx, new ParameterizedTypeReference<VietinBillPayTransaction>() {
							});
					LOGGER.info("== Success receive response from conn-billpay {}", responseEntity.getBody());
				}
			}
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
		}
		br.close();
		return failTranx;
	}
}
