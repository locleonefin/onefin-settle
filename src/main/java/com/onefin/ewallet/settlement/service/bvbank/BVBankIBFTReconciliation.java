package com.onefin.ewallet.settlement.service.bvbank;

import com.onefin.ewallet.common.base.constants.OneFinConstants;
import com.onefin.ewallet.common.domain.bank.transfer.BankTransferChildRecords;
import com.onefin.ewallet.common.domain.bank.transfer.BankTransferTransaction;
import com.onefin.ewallet.common.domain.constants.DomainConstants;
import com.onefin.ewallet.common.domain.settlement.SettlementTransaction;
import com.onefin.ewallet.common.quartz.entity.SchedulerJobInfo;
import com.onefin.ewallet.common.utility.date.DateTimeHelper;
import com.onefin.ewallet.settlement.common.SettleConstants;
import com.onefin.ewallet.settlement.common.SettleHelper;
import com.onefin.ewallet.settlement.controller.SettleJobController;
import com.onefin.ewallet.settlement.dto.bvb.ConnResponse;
import com.onefin.ewallet.settlement.dto.bvb.bankTransfer.BVBIBFTReconciliationDTO;
import com.onefin.ewallet.settlement.dto.bvb.bankTransfer.BVBSendReconciliationRequest;
import com.onefin.ewallet.settlement.dto.bvb.bankTransfer.BVBSendReconciliationRequestData;
import com.onefin.ewallet.settlement.dto.bvb.bankTransfer.BVBSendReconciliationResponse;
import com.onefin.ewallet.settlement.job.BVBankIBFTUploadReconciliationJob;
import com.onefin.ewallet.settlement.job.BVBankTransactionListExportJob;
import com.onefin.ewallet.settlement.repository.BankTransferRepo;
import com.onefin.ewallet.settlement.repository.SettleTransRepository;
import com.onefin.ewallet.settlement.service.ConfigLoader;
import com.onefin.ewallet.settlement.service.MinioService;
import com.onefin.ewallet.settlement.service.SettleService;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class BVBankIBFTReconciliation {
	private static final org.apache.logging.log4j.Logger LOGGER
			= LogManager.getLogger(BVBankIBFTReconciliation.class);
	@Autowired
	private DateTimeHelper dateTimeHelper;


	@Value("${bvb.IBFT.onefinClientCode}")
	private String clientCode;

	@Autowired
	private BVBEncryptUtil bvbEncryptUtil;


	public static final String BVB_IBFT_BACKUP_UPLOAD_RECONCILIATION_PREFIX = "uploadReconciliation";
	private static final String LOG_BVBANK_IBFT_RECONCILIATION_DAILY = "BVBANK IBFT RECONCILIATION DAILY - ";

	@Autowired
	private BVBankReconciliation bvBankReconciliation;

	@Autowired
	private BankTransferRepo bankTransferRepo;

	@Autowired
	private BVBIBFTPersistance bvbibftPersistance;

	@Autowired
	private BVBTransferRequestUtil bvbTransferRequestUtil;

	@Autowired
	private ConfigLoader configLoader;

	@Value("${bvb.IBFT.onefinMerchantCode}")
	private String requestIdPrefix;

	@Value("${bvb.IBFT.url.uploadReconciliation}")
	private String bvbIBFTUploadReconciliationUrl;

	@Autowired
	private SettleTransRepository settleRepo;

	@Autowired
	private SettleHelper settleHelper;

	@Autowired
	private SettleService commonSettleService;

	@Autowired
	private SettleJobController settleJobController;

	@Autowired
	private MinioService minioService;

	@Autowired
	private Environment env;

	public void ibftReconciliationUpload(String dateString){
		DateTime dateTimeParse = dateTimeHelper.parseDate(dateString,
				OneFinConstants.HO_CHI_MINH_TIME_ZONE, OneFinConstants.DATE_FORMAT_ddMMyyyy);

		String currentTimeString = dateTimeHelper.parseDateString(dateTimeParse,
				OneFinConstants.DATE_FORMAT_yyyyMMDD);

		SettlementTransaction trans = settleRepo.findByPartnerAndDomainAndSettleDate(
				SettleConstants.PARTNER_BVBANK,
				SettleConstants.BVB_IBFT, currentTimeString);

		if (trans == null) {
			try {
				trans = (SettlementTransaction) settleHelper.createModelStructure(new SettlementTransaction());
				trans.getSettleKey().setDomain(SettleConstants.BVB_IBFT);
				trans.getSettleKey().setPartner(SettleConstants.PARTNER_BVBANK);
				trans.getSettleKey().setSettleDate(currentTimeString);
				trans.setStatus(SettleConstants.SETTLEMENT_PENDING_T0);
				commonSettleService.save(trans);
				LOGGER.info("{} Created Pending Trans Successfully {}",
						LOG_BVBANK_IBFT_RECONCILIATION_DAILY, currentTimeString);
			} catch (Exception e) {
				LOGGER.error("Create TC transaction error", e);
				return;
			}
		}

		if (trans.getStatus().equals(SettleConstants.SETTLEMENT_SUCCESS)){
			LOGGER.info("{} Settle for the day have already successful for day {}",
					LOG_BVBANK_IBFT_RECONCILIATION_DAILY, currentTimeString);
			return;
		}

		List<Date> settleDate = new ArrayList<>();
		DateTime previousTime1 = dateTimeParse.minusDays(1);
		settleDate.add(previousTime1.toDate());

		LOGGER.info("list date upload reconciliation: {}", settleDate);
		LOGGER.info("reconciliation from {} to {}", settleDate.get(settleDate.size() - 1), settleDate.get(0));
		AtomicBoolean status = new AtomicBoolean(true);
		List<Date> listErrorDate = new ArrayList<>();
		List<String> listFile = new ArrayList<>();
		settleDate.forEach(
			e -> {
				try {
					String fileName = bvbTransferRequestUtil.getReconciliationFileName("zip",
							dateTimeHelper.parseDate2String(e, DomainConstants.DATE_FORMAT_TRANS8));
					listFile.add(fileName);
					List<BankTransferTransaction> bankTransferTransactionList
						= bankTransferRepo.findTransferByDateAndBankCode(
						dateTimeHelper.getBeginingOfDate(e),
						dateTimeHelper.getEndOfDate(e),
						OneFinConstants.TRANS_SUCCESS,
						OneFinConstants.BankListQrService.VCCB.getBankCode()
					);

					List<BVBIBFTReconciliationDTO> bvbibftReconciliationDTOList
							= bvbibftPersistance.getListIBFTReconciliationDTO(bankTransferTransactionList);

					LOGGER.info("bvbibftReconciliationDTOList: {}", bvbibftReconciliationDTOList.size());
					LOGGER.info("bvbibftReconciliationDTOList: {}", bvbibftReconciliationDTOList);
					String content = bvbTransferRequestUtil.reconciliationFileGen(bvbibftReconciliationDTOList, e);
					LOGGER.log(Level.getLevel("INFOWT"), "content: " + content);

					if (bvbibftReconciliationDTOList.isEmpty()){
						LOGGER.info("not found transaction in date: {}, no need to reconciliation",	e);
						return;
					}
					byte[] byteArrray = content.getBytes("UTF-8");
					ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
					try (ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {
						ZipEntry zipEntry = new ZipEntry(
							bvbTransferRequestUtil.getReconciliationFileName("txt",
								dateTimeHelper.parseDate2String(e, DomainConstants.DATE_FORMAT_TRANS8)));
						zipOutputStream.putNextEntry(zipEntry);
						zipOutputStream.write(byteArrray);
						zipOutputStream.closeEntry();
					}
					byte[] bytes = byteArrayOutputStream.toByteArray();

					LOGGER.info("File saved: {}", fileName);
					String MinioSavedFolder
						= dateTimeHelper.parseDate2String(dateTimeParse.toDate(),
						DomainConstants.DATE_FORMAT_TRANS3);
					String fileLocationFull = OneFinConstants.PARTNER_BVBANK + "/EMAIL_RECONCILIATION_IBFT_BVB/"
						+ MinioSavedFolder + OneFinConstants.SLASH + fileName;
					minioService.uploadByte(env.getProperty("minio.bvbReconciliationBucket"),
						fileLocationFull,
						bytes,
						"application/zip");


					FileOutputStream outputStream = new FileOutputStream(fileName);
					outputStream.write(bytes);
					outputStream.close();

					BVBSendReconciliationRequestData data
						= new BVBSendReconciliationRequestData();
					String processingDate = dateTimeHelper.parseDate2String(e, DomainConstants.DATE_FORMAT_TRANS8);
					data.setProcessingDate(Integer.parseInt(processingDate));
					BVBSendReconciliationRequest requestBody
						= new BVBSendReconciliationRequest();

					Date currentDate = dateTimeHelper.currentDate(OneFinConstants.HO_CHI_MINH_TIME_ZONE);
					String currentDateRequestId = dateTimeHelper.parseDate2String(currentDate, DomainConstants.DATE_FORMAT_TRANS7);
					String requestId = requestIdPrefix + currentDateRequestId + RandomStringUtils.random(6, false, true);
					String clientUserId = RandomStringUtils.random(12, true, true);
					requestBody.setRequestId(requestId);
					requestBody.setClientCode(clientCode);
					requestBody.setClientUserId(clientUserId);
					requestBody.setTime(currentDate);
					requestBody.setData(data);
					ResponseEntity<?> ibftTransfer = bvbTransferRequestUtil.ibftRequest(requestBody,
						bvbIBFTUploadReconciliationUrl,
						bytes,
						fileName,
						requestBody.getRequestId(),
						BVB_IBFT_BACKUP_UPLOAD_RECONCILIATION_PREFIX,
						BVBSendReconciliationRequest.class,
						BVBSendReconciliationResponse.class
					);

					LOGGER.log(Level.getLevel("INFOWT"), "ibftTransfer: " + ibftTransfer.getBody());

					// validate dto
					ConnResponse isDtoValid = bvbTransferRequestUtil.checkDTOAndReturnIfNotValid(ibftTransfer.getBody());
					if (isDtoValid != null) {
						LOGGER.log(Level.getLevel("INFOWT"), "ibftTransfer dto Validate failed: " + ibftTransfer.getBody());
						status.set(false);
						listErrorDate.add(e);
						return ;
					}

					// validate signature
					ConnResponse isSignatureValid = bvbTransferRequestUtil.checkSignature((BVBSendReconciliationResponse)ibftTransfer.getBody());
					if (isSignatureValid != null) {
						LOGGER.log(Level.getLevel("INFOWT"), "ibftTransfer signature Validate failed: " + ibftTransfer.getBody());
						status.set(false);
						listErrorDate.add(e);
						return ;
					}

				} catch (Exception ex) {
					LOGGER.error("Error occurred at date: {}", e, ex);
					status.set(false);
					listErrorDate.add(e);
				}
			}
		);
		trans.setFile(listFile);
		if (status.get()){
			try {
				trans.setStatus(SettleConstants.SETTLEMENT_SUCCESS);
				commonSettleService.save(trans);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			// pause job
			// Settle completed => paused job
			SchedulerJobInfo jobTmp = new SchedulerJobInfo();
			jobTmp.setJobClass(BVBankIBFTUploadReconciliationJob.class.getName());
			settleJobController.pauseJob(jobTmp);
		}else{
			LOGGER.info("List Date failed: {}",listErrorDate);
		}
	}

}
