package com.onefin.ewallet.settlement.service.vietin.disbursement;

import com.onefin.ewallet.common.base.constants.OneFinConstants;
import com.onefin.ewallet.common.base.service.RestTemplateHelper;
import com.onefin.ewallet.common.domain.bank.transfer.BankTransferChildRecords;
import com.onefin.ewallet.common.domain.settlement.SettlementTransaction;
import com.onefin.ewallet.common.quartz.entity.SchedulerJobInfo;
import com.onefin.ewallet.common.utility.date.DateTimeHelper;
import com.onefin.ewallet.common.utility.sercurity.SercurityHelper;
import com.onefin.ewallet.settlement.common.SettleConstants;
import com.onefin.ewallet.settlement.common.SettleHelper;
import com.onefin.ewallet.settlement.config.SFTPVietinDisbursementIntegration;
import com.onefin.ewallet.settlement.controller.SettleJobController;
import com.onefin.ewallet.settlement.dto.VietinDisbursement;
import com.onefin.ewallet.settlement.repository.ChildBankTransferRecordsRepo;
import com.onefin.ewallet.settlement.repository.SettleTransRepository;
import com.onefin.ewallet.settlement.service.ConfigLoader;
import com.onefin.ewallet.settlement.service.MinioService;
import com.onefin.ewallet.settlement.service.SettleService;
import com.opencsv.bean.CsvToBeanBuilder;
import org.apache.commons.io.input.BOMInputStream;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Component
@DisallowConcurrentExecution
public class VietinDisbursementIn extends QuartzJobBean {

	private static final Logger LOGGER = LoggerFactory.getLogger(VietinDisbursementIn.class);

	// Vietin bank transfer error code
	private static final String VTB_BT_SUCCESS_CODE = "00";
	private static final String VTB_BT_PENDING_CODE = "01";

	@Autowired
	private ConfigLoader configLoader;

	@Autowired
	private DateTimeHelper dateHelper;

	@Autowired
	@Qualifier("SftpVietinDisbursementRemoteFileTemplate")
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
	private SettleHelper settleHelper;

	@Autowired
	private SercurityHelper sercurityHelper;

	@Autowired
	private ChildBankTransferRecordsRepo childBankTransferRecordsRepo;

	@Autowired
	private SFTPVietinDisbursementIntegration.UploadVietinDisbursementGateway gateway;

	/**
	 * Check disbursement file from Vietin
	 *
	 * @param context
	 * @throws JobExecutionException
	 */
	@Override
	protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
		LOGGER.info("{} {} Start Settlement Disbursement In", SettleConstants.PARTNER_VIETINBANK, SettleConstants.VTB_DISBURSEMENT);
		DateTime currentTime = dateHelper.currentDateTime(OneFinConstants.HO_CHI_MINH_TIME_ZONE);
		DateTime settleDate = currentTime.minusDays(1);
		try {
			taskEwalletVietinDisputeSettlement(settleDate, false);
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
		}
	}

	public void ewalletVietinDisbursementSettlementManually(DateTime settleDate) {
		try {
			taskEwalletVietinDisputeSettlement(settleDate, true);
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
		}
	}

	public void taskEwalletVietinDisputeSettlement(DateTime settleTime, boolean ignoreSettleBefore) throws Exception {
		LOGGER.info("{} {} Start process Disbursement In file {} {}", SettleConstants.PARTNER_VIETINBANK,
				SettleConstants.VTB_DISBURSEMENT, settleTime, ignoreSettleBefore);
		SimpleDateFormat vietinDateFormat = new SimpleDateFormat(SettleConstants.DATE_FORMAT_yyyyMMDD);
		String vietinSettleDateFormat = vietinDateFormat.format(settleTime.toDate());
		String vietinDisbursementFileName = String.format("%s_%s_DISBURSE_IN.txt", vietinDateFormat.format(settleTime.toDate()), configLoader.getVietinDisbursementProviderId());
		LOGGER.info("{} {} Disbursement file name {}", SettleConstants.PARTNER_VIETINBANK, SettleConstants.VTB_DISBURSEMENT, vietinDisbursementFileName);
		SettlementTransaction trans = settleRepo.findByPartnerAndDomainAndSettleDateAndStatus(
				SettleConstants.PARTNER_VIETINBANK, SettleConstants.VTB_DISBURSEMENT,
				vietinDateFormat.format(settleTime.toLocalDateTime().toDate()), SettleConstants.SETTLEMENT_PENDING_T0);
		SettlementTransaction transExist = settleRepo.findByPartnerAndDomainAndSettleDate(
				SettleConstants.PARTNER_VIETINBANK, SettleConstants.VTB_DISBURSEMENT,
				vietinDateFormat.format(settleTime.toLocalDateTime().toDate()));
		if (transExist == null) {
			LOGGER.info("{} {} Transaction not exist", SettleConstants.PARTNER_VIETINBANK, SettleConstants.VTB_DISBURSEMENT);
			trans = (SettlementTransaction) settleHelper.createModelStructure(new SettlementTransaction());
			trans.getSettleKey().setDomain(SettleConstants.VTB_DISBURSEMENT);
			trans.getSettleKey().setPartner(SettleConstants.PARTNER_VIETINBANK);
			trans.getSettleKey().setSettleDate(vietinDateFormat.format(settleTime.toDate()));
			trans.setStatus(SettleConstants.SETTLEMENT_PENDING_T0);
			commonSettleService.save(trans);
		}
		LOGGER.info("{} {} Try to get file in sftp", SettleConstants.PARTNER_VIETINBANK, SettleConstants.VTB_DISBURSEMENT);
		try {
			SettlementTransaction finalTrans = trans;
			sftpVietinGateway.get(configLoader.getSftpVietinbankDisbursementDirectoryRemoteIn() + vietinDisbursementFileName,
					stream -> {
						LOGGER.info("{} {} Get file in sftp success", SettleConstants.PARTNER_VIETINBANK, SettleConstants.VTB_DISBURSEMENT);
						// Vietin sent file to sftp
						// Copy file to local
						FileCopyUtils.copy(stream, new FileOutputStream(configLoader.getSftpVietinbankDisbursementDirectoryStoreFile() + vietinDisbursementFileName));
						// Init file
						File file = new File(
								configLoader.getSftpVietinbankDisbursementDirectoryStoreFile() + vietinDisbursementFileName);
						// Upload vietin file to minio
						minioService.uploadFile(configLoader.getBaseBucket(),
								configLoader.getSettleVietinDisbursementMinioDefaultFolder() + dateHelper
										.parseDateString(settleTime, SettleConstants.DATE_FORMAT_ddMMyyyy) + "/"
										+ file.getName(),
								file, "text/plain");
						if (!finalTrans.getFile().contains(file.getName())) {
							finalTrans.getFile().add(file.getName());
						}
						// Process Vietin file
						try {
							List<VietinDisbursement> reconcile = reconcileProcess(file.getCanonicalPath(), vietinSettleDateFormat);
							sendFile2Vietin(reconcile, finalTrans, settleTime);
							// Upload file to minio server
							LOGGER.info("{} {} Start upload dispute file to MINIO", SettleConstants.PARTNER_VIETINBANK,
									SettleConstants.VTB_DISBURSEMENT);
							LOGGER.info("{} {} End upload dispute file to MINIO", SettleConstants.PARTNER_VIETINBANK,
									SettleConstants.VTB_DISBURSEMENT);

							commonSettleService.update(finalTrans);
							file.delete();
							// Settle completed => paused job
							SchedulerJobInfo jobTmp = new SchedulerJobInfo();
							jobTmp.setJobClass(VietinDisbursementIn.class.getName());
							settleJobController.pauseJob(jobTmp);
							LOGGER.info("{} {} End process dispute file", SettleConstants.PARTNER_VIETINBANK,
									SettleConstants.VTB_DISBURSEMENT);
						} catch (Exception e) {
							LOGGER.error("{} {} {}", SettleConstants.PARTNER_VIETINBANK, SettleConstants.VTB_DISBURSEMENT,
									"Error in processing dispute file", e);
						}
					});
		} catch (Exception e) {
			LOGGER.warn("{} {} Not Found Settlement Dispute File {}", SettleConstants.PARTNER_VIETINBANK,
					SettleConstants.VTB_DISBURSEMENT, vietinDisbursementFileName, e);
		}
		String[] settleCompletedBefore = env.getProperty("settlement.vietin.disbursement.settleCompletedBefore").split(":");
		LocalTime settleCompletedBeforeTime = new LocalTime(Integer.parseInt(settleCompletedBefore[0]), Integer.parseInt(settleCompletedBefore[1]), Integer.parseInt(settleCompletedBefore[2]));
		LocalTime currentLocalTime = LocalTime.now();
		if (currentLocalTime.compareTo(settleCompletedBeforeTime) == 1 && ignoreSettleBefore == false) {
			LOGGER.error("{} {} {}", SettleConstants.PARTNER_VIETINBANK, SettleConstants.VTB_DISBURSEMENT,
					"Vietin disbursement not access to SFTP");
			SchedulerJobInfo jobTmp = new SchedulerJobInfo();
			jobTmp.setJobClass(VietinDisbursementIn.class.getName());
			settleJobController.pauseJob(jobTmp);
		}
	}

	private List<VietinDisbursement> reconcileProcess(String file, String currDate) {
		LOGGER.info("{} {} Start proccess reconcile transaction", SettleConstants.PARTNER_VIETINBANK, SettleConstants.VTB_DISBURSEMENT);
		List<VietinDisbursement> reconcile = new ArrayList<>();
		try {
			// Load all transaction in Vietin settlement file
			List<VietinDisbursement> bankData = new CsvToBeanBuilder(new InputStreamReader(new BOMInputStream(new ByteArrayInputStream(Files.readAllBytes(Paths.get(file)))), StandardCharsets.UTF_8))
					.withType(VietinDisbursement.class).withSeparator('|')
					.build().parse();
			bankData.remove(0); // Remove header
			List<VietinDisbursement> bankDataClone = new ArrayList<>(bankData);
			bankDataClone.remove(bankData.size() - 1); // Remove footer
			// Load all success transaction in OneFin
			List<BankTransferChildRecords> successData = childBankTransferRecordsRepo.findByStatusAndDate(VTB_BT_SUCCESS_CODE, dateHelper.parseDate2DateString(SettleConstants.DATE_FORMAT_yyyyMMDD, SettleConstants.DATE_FORMAT_yyyyMMdd, currDate));
			Set<String> acceptableId =
					bankDataClone.stream()
							.map(VietinDisbursement::getTransId)
							.collect(Collectors.toSet());
			// Filter data
			List<BankTransferChildRecords> oneFinSuccessVietinNotSuccess = successData.stream()
					.filter(c -> !acceptableId.contains(c.getTransId()))
					.collect(Collectors.toList());

			// Process settlement case
			if (oneFinSuccessVietinNotSuccess.size() > 0) {
				// Case 04: Chênh lệch, ghi nhận thành công phía đối tác nhưng không thấy bank gửi sang
				oneFinSuccessVietinNotSuccess.stream().forEach(e -> {
					updateVietinDisbursementChidTrans(e, SettleConstants.TRANS_RECONCILE_04);
					VietinDisbursement data = new VietinDisbursement();
					data.setRecordType(configLoader.getRecordTypeVietinbankDisbursement());
					data.setTransId(e.getTransId());
					data.setMerchantId(e.getBankTransferTransaction().getMerchantId());
					data.setBankCode(e.getRecvBank().getCode());
					data.setBranchCode(e.getRecvBranchId().getBranchCode());
					data.setCustAcctNo(e.getRecvAcctId());
					data.setAmount(e.getAmount().toString());
					data.setCurrencyCode(e.getCurrency());
					data.setPayRefNo(e.getPayRefNo());
					data.setMerchantAcctNo(e.getSenderAcctId());
					data.setTransTime(dateHelper.parseDate2String(e.getBankTransferTransaction().getBankTransTime(), SettleConstants.DATE_FORMAT_yyyyMMDDHHmmss));
					data.setStatus(e.getBankStatusCode());
					data.setBankTransId(e.getBankTransactionId());
					data.setReconcileStatus(configLoader.getReconcile_04VietinbankDisbursement());
					data.setRecordChecksum(sercurityHelper.MD5Hashing(String.format("%s%s%s%s%s%s%s%s%s%s%s%s%s", data.getTransId(), data.getMerchantId(), data.getBankCode(), data.getBranchCode(), data.getCustAcctNo(), data.getAmount(), data.getPayRefNo(), data.getMerchantAcctNo(), data.getTransTime(), data.getStatus(), data.getBankTransId(), data.getReconcileStatus(), configLoader.getPrivateKeyVietinbankDisbursementChecksum()), false));
					reconcile.add(data);
				});
			}
			bankData.stream().forEach(e -> {
				if (e.getRecordType().equals(configLoader.getRecordTypeVietinbankDisbursement())) {
					// Validate Vietin sent transaction
					// Check checksum
					String md5Hash = sercurityHelper.MD5Hashing(String.format("%s%s%s%s%s%s%s%s%s%s%s%s", e.getTransId(), e.getMerchantId(), e.getBankCode(), e.getBranchCode(), e.getCustAcctNo(), e.getAmount(), e.getPayRefNo(), e.getMerchantAcctNo(), e.getTransTime(), e.getStatus(), e.getBankTransId(), configLoader.getPrivateKeyVietinbankDisbursementChecksum()), false);
					if (!e.getRecordChecksum().equals(md5Hash)) {
						e.setReconcileStatus(configLoader.getReconcile_03VietinbankDisbursement());
						e.setRecordChecksum(sercurityHelper.MD5Hashing(String.format("%s%s%s%s%s%s%s%s%s%s%s%s%s", e.getTransId(), e.getMerchantId(), e.getBankCode(), e.getBranchCode(), e.getCustAcctNo(), e.getAmount(), e.getPayRefNo(), e.getMerchantAcctNo(), e.getTransTime(), e.getStatus(), e.getBankTransId(), e.getReconcileStatus(), configLoader.getPrivateKeyVietinbankDisbursementChecksum()), false));
						reconcile.add(e);
						LOGGER.error("Invalid checksum, record {}, onefin checksum {}, vietin checksum {}", e, md5Hash, e.getRecordChecksum());
					} else {
						BankTransferChildRecords chidData0 = childBankTransferRecordsRepo.findByTransIdAndNotEqualBankStatusCode(e.getTransId(), VTB_BT_SUCCESS_CODE);
						BankTransferChildRecords chidData1 = childBankTransferRecordsRepo.findByTransId(e.getTransId());
						if (chidData0 != null) {
							if (chidData0.getBankStatusCode().equals(VTB_BT_PENDING_CODE)) {
								// Case: Vietin process pending transaction success => update in db to success status
								e.setReconcileStatus(configLoader.getReconcile_00VietinbankDisbursement());
								e.setRecordChecksum(sercurityHelper.MD5Hashing(String.format("%s%s%s%s%s%s%s%s%s%s%s%s%s", e.getTransId(), e.getMerchantId(), e.getBankCode(), e.getBranchCode(), e.getCustAcctNo(), e.getAmount(), e.getPayRefNo(), e.getMerchantAcctNo(), e.getTransTime(), e.getStatus(), e.getBankTransId(), e.getReconcileStatus(), configLoader.getPrivateKeyVietinbankDisbursementChecksum()), false));
								reconcile.add(e);
								LOGGER.info("Update pending status to settled status {}", e);
								// Send request to connector-bank to update transaction status
								updateVietinDisbursementChidTrans(chidData0, SettleConstants.TRANS_SETTLED);
							} else {
								// Case 01: Chênh lệch,Ghi nhận thất bại phía đối tác nhưng bank thành công => update to success
								//e.setStatus(configLoader.getReconcile_01VietinbankDisbursement());
								//e.setRecordChecksum(sercurityHelper.MD5Hashing(String.format("%s%s%s%s%s%s%s%s%s%s%s%s", e.getTransId(), e.getMerchantId(), e.getBankCode(), e.getBranchCode(), e.getCustAcctNo(), e.getAmount(), e.getPayRefNo(), e.getMerchantAcctNo(), e.getTransTime(), e.getStatus(), e.getBankTransId(), configLoader.getPrivateKeyVietinbankDisbursementChecksum()), false));
								e.setReconcileStatus(configLoader.getReconcile_00VietinbankDisbursement());
								e.setRecordChecksum(sercurityHelper.MD5Hashing(String.format("%s%s%s%s%s%s%s%s%s%s%s%s%s", e.getTransId(), e.getMerchantId(), e.getBankCode(), e.getBranchCode(), e.getCustAcctNo(), e.getAmount(), e.getPayRefNo(), e.getMerchantAcctNo(), e.getTransTime(), e.getStatus(), e.getBankTransId(), e.getReconcileStatus(), configLoader.getPrivateKeyVietinbankDisbursementChecksum()), false));
								reconcile.add(e);
								LOGGER.error("OneFin trans error but Vietin success {}", e);
								// Send request to connector-bank to update transaction status
								updateVietinDisbursementChidTrans(chidData0, SettleConstants.TRANS_RECONCILE_01);
							}
						} else if (chidData1 == null) {
							// Case 03: Chênh lệch, không ghi nhận phía đối tác nhưng bank thành công
							e.setReconcileStatus(configLoader.getReconcile_03VietinbankDisbursement());
							e.setRecordChecksum(sercurityHelper.MD5Hashing(String.format("%s%s%s%s%s%s%s%s%s%s%s%s%s", e.getTransId(), e.getMerchantId(), e.getBankCode(), e.getBranchCode(), e.getCustAcctNo(), e.getAmount(), e.getPayRefNo(), e.getMerchantAcctNo(), e.getTransTime(), e.getStatus(), e.getBankTransId(), e.getReconcileStatus(), configLoader.getPrivateKeyVietinbankDisbursementChecksum()), false));
							reconcile.add(e);
							LOGGER.error("Disbursement, record not found in OneFin but success in Vietin Bank");
						} else {
							// Case: Khớp đúng
							e.setReconcileStatus(configLoader.getReconcile_00VietinbankDisbursement());
							e.setRecordChecksum(sercurityHelper.MD5Hashing(String.format("%s%s%s%s%s%s%s%s%s%s%s%s%s", e.getTransId(), e.getMerchantId(), e.getBankCode(), e.getBranchCode(), e.getCustAcctNo(), e.getAmount(), e.getPayRefNo(), e.getMerchantAcctNo(), e.getTransTime(), e.getStatus(), e.getBankTransId(), e.getReconcileStatus(), configLoader.getPrivateKeyVietinbankDisbursementChecksum()), false));
							reconcile.add(e);
							updateVietinDisbursementChidTrans(chidData1, SettleConstants.TRANS_SETTLED);
						}
					}
				}
			});
		} catch (Exception e) {
			LOGGER.error("Process Reconcile Error {}", e);
		}
		LOGGER.info("{} {} End proccess reconcile transaction", SettleConstants.PARTNER_VIETINBANK, SettleConstants.VTB_DISBURSEMENT);
		return reconcile;
	}

	private void sendFile2Vietin(List<VietinDisbursement> reconcile, SettlementTransaction trans, DateTime currDate) throws IOException {
		LOGGER.info("{} {} Start proccess send feedback file", SettleConstants.PARTNER_VIETINBANK, SettleConstants.VTB_DISBURSEMENT);
		File file = new File(configLoader.getSftpVietinbankDisbursementDirectoryStoreFile() + dateHelper.parseDateString(currDate, SettleConstants.DATE_FORMAT_yyyyMMDD) + "_" + configLoader.getVietinDisbursementProviderId() + "_DISBURSE_OUT.txt");
		LOGGER.info("Disburment response file: {}", file);
		BufferedWriter writer = new BufferedWriter(new FileWriter(file));
		writer.write(
				"recordType,transId,merchantId,bankCode,branchCode,custAcctNo,amount,currencyCode,payRefNo,merchantAcctNo,transTime,status,bankTransId,reconcileStatus,recordChecksum");
		writer.newLine();
		LOGGER.info("Disburment response file, write details");
		AtomicReference<String> checkSumDetails = new AtomicReference<>("");
		reconcile.stream().forEach(e -> {
			try {
				LOGGER.info("Disburment response file, record {}", e);
				String checksum = sercurityHelper.MD5Hashing(String.format("%s%s%s%s%s%s%s%s%s%s%s%s%s", e.getTransId(), e.getMerchantId(), e.getBankCode(), e.getBranchCode(), e.getCustAcctNo(), e.getAmount(), e.getPayRefNo(), e.getMerchantAcctNo(), e.getTransTime(), e.getStatus(), e.getBankTransId(), e.getReconcileStatus(), configLoader.getPrivateKeyVietinbankDisbursementChecksum()), false);
				checkSumDetails.set(checkSumDetails + checksum);
				String newRecord = String.format("%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s", e.getRecordType(), e.getTransId(),
						e.getMerchantId(), e.getBankCode(), e.getBranchCode(), e.getCustAcctNo(), e.getAmount(), e.getCurrencyCode(), e.getPayRefNo(), e.getMerchantAcctNo(), e.getTransTime(),
						e.getStatus(), e.getBankTransId(), e.getReconcileStatus(), checksum);
				writer.write(newRecord);
				writer.newLine();
			} catch (Exception e1) {
				LOGGER.error("Error {}", e1);
			}
		});
		LOGGER.info("Disburment response file, write end line");
		String checksum = sercurityHelper.MD5Hashing(String.format("%s%s%d%s%s%s", configLoader.getVietinDisbursementProviderId(), configLoader.getProviderNameVietinbankDisbursement(), reconcile.size(), dateHelper.parseDateString(currDate, SettleConstants.DATE_FORMAT_yyyyMMDDHHmmss), checkSumDetails, configLoader.getPrivateKeyVietinbankDisbursementChecksum()), false);
		String newRecord = String.format("%s|%s|%s|%s|%s|%s", configLoader.getVietinDisbursementEndRecordType(), configLoader.getVietinDisbursementProviderId(), configLoader.getProviderNameVietinbankDisbursement(), reconcile.size(), dateHelper.currentDateString(SettleConstants.HO_CHI_MINH_TIME_ZONE, SettleConstants.DATE_FORMAT_yyyyMMDDHHmmss), checksum);
		writer.write(newRecord);
		writer.newLine();
		writer.close();

		if (!trans.getFile().contains(file.getName())) {
			trans.getFile().add(file.getName());
		}
		// Upload file to minio
		minioService.uploadFile(configLoader.getBaseBucket(),
				configLoader.getSettleVietinDisbursementMinioDefaultFolder()
						+ dateHelper.parseDateString(currDate, SettleConstants.DATE_FORMAT_ddMMyyyy) + "/"
						+ file.getName(),
				file, "text/plain");
		// Upload file to sftp
		gateway.upload(file);
		file.delete();
		LOGGER.info("{} {} End proccess send feedback file", SettleConstants.PARTNER_VIETINBANK, SettleConstants.VTB_DISBURSEMENT);
	}

	private ResponseEntity updateVietinDisbursementChidTrans(BankTransferChildRecords trans, String transStatus) {
		try {
			String url = env.getProperty("conn-service.bank.host") + env.getProperty("conn-service.bank.uri.modifySettleTrx");
			LOGGER.info("== Send update trx to conn-bank {} - url: {}", trans, url);
			HttpHeaders headers = new HttpHeaders();
			headers.setAccept(Collections.singletonList(MediaType.ALL));
			headers.setContentType(MediaType.APPLICATION_JSON);
			HashMap<String, String> headersMap = new HashMap<String, String>();
			for (String header : headers.keySet()) {
				headersMap.put(header, headers.getFirst(header));
			}
			ResponseEntity<BankTransferChildRecords> responseEntity = restTemplateHelper.put(url,
					MediaType.APPLICATION_JSON_VALUE, headersMap, Arrays.asList(SettleConstants.PARTNER_VIETINBANK, SettleConstants.VTB_DISBURSEMENT, trans.getTransId()), new HashMap<String, String>() {{
						put("transStatus", transStatus);
					}},
					null, null, new ParameterizedTypeReference<BankTransferChildRecords>() {
					});
			LOGGER.info("== Success receive response from conn-bank {}", responseEntity.getBody());
			return responseEntity;
		} catch (Exception e) {
			LOGGER.error("Update transaction to Settled ERROR, Please update manually", e);
			return null;
		}
	}

}
