package com.onefin.ewallet.settlement.controller;

import com.onefin.ewallet.common.base.constants.OneFinConstants;
import com.onefin.ewallet.common.domain.bank.transfer.BankTransferTransaction;
import com.onefin.ewallet.common.domain.constants.DomainConstants;
import com.onefin.ewallet.common.domain.settlement.SettlementTransaction;
import com.onefin.ewallet.common.utility.date.DateTimeHelper;
import com.onefin.ewallet.settlement.common.SettleConstants;
import com.onefin.ewallet.settlement.common.SettleHelper;
import com.onefin.ewallet.settlement.dto.bvb.bankTransfer.BVBIBFTReconciliationDTO;
import com.onefin.ewallet.settlement.dto.bvb.bankTransfer.BVBSendReconciliationRequest;
import com.onefin.ewallet.settlement.dto.bvb.bankTransfer.BVBSendReconciliationRequestData;
import com.onefin.ewallet.settlement.dto.bvb.bankTransfer.BVBSendReconciliationResponse;
import com.onefin.ewallet.settlement.repository.BankTransferRepo;
import com.onefin.ewallet.settlement.repository.SettleTransRepository;
import com.onefin.ewallet.settlement.service.ConfigLoader;
import com.onefin.ewallet.settlement.service.SettleService;
import com.onefin.ewallet.settlement.service.bvbank.*;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/bvb-ibft/reconciliation/")
public class BvbIBFTReconciliationController {
	private static final org.apache.logging.log4j.Logger LOGGER
			= LogManager.getLogger(BvbIBFTReconciliationController.class);

	public static final String BVB_IBFT_BACKUP_UPLOAD_RECONCILIATION_PREFIX = "uploadReconciliation";
	private static final String LOG_BVBANK_IBFT_RECONCILIATION_DAILY = "BVBANK IBFT RECONCILIATION DAILY - ";

	@Autowired
	private DateTimeHelper dateTimeHelper;

	@Autowired
	private BVBankReconciliation bvBankReconciliation;

	@Autowired
	private BankTransferRepo bankTransferRepo;

	@Autowired
	private BVBEncryptUtil bvbEncryptUtil;

	@Autowired
	private BVBIBFTPersistance bvbibftPersistance;

	@Autowired
	private BVBTransferRequestUtil bvbTransferRequestUtil;

	@Autowired
	private ConfigLoader configLoader;

	@Value("${bvb.IBFT.onefinMerchantCode}")
	private String requestIdPrefix;

	@Value("${bvb.IBFT.onefinClientCode}")
	private String clientCode;

	@Value("${bvb.IBFT.url.uploadReconciliation}")
	private String bvbIBFTUploadReconciliationUrl;

	@Autowired
	private SettleTransRepository settleRepo;

	@Autowired
	private SettleHelper settleHelper;

	@Autowired
	private SettleService commonSettleService;

	@Autowired
	private BVBankIBFTReconciliation bvBankIBFTReconciliation;

	@PostMapping("/uploadReconciliation/{dateString}")
	public ResponseEntity<?> uploadReconciliation(
			@PathVariable() String dateString
	) throws Exception {

		bvBankIBFTReconciliation.ibftReconciliationUpload(dateString);
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@PostMapping("/uploadReconciliation/download/{dateString}")
	public ResponseEntity<?> uploadReconciliationDownload(
			@PathVariable() String dateString
	) throws Exception {
		Date dateParse = dateTimeHelper.parseDate2(dateString, OneFinConstants.DATE_FORMAT_ddMMyyyy);

		DateTime dateTimeParse = dateTimeHelper.parseDate(dateString,
				OneFinConstants.HO_CHI_MINH_TIME_ZONE, OneFinConstants.DATE_FORMAT_ddMMyyyy);

		List<Date> settleDate = new ArrayList<>();
		DateTime previousTime1 = dateTimeParse.minusDays(1);
		settleDate.add(previousTime1.toDate());
		while (bvBankReconciliation.checkIsBvbHoliday(previousTime1.toLocalDateTime().toDate())) {
			previousTime1 = previousTime1.minusDays(1);
			settleDate.add(previousTime1.toLocalDateTime().toDate());
		}
		settleDate.add(previousTime1.toLocalDateTime().toDate());

		LOGGER.info("list date upload reconciliation: {}", settleDate);
		LOGGER.info("reconciliation from {} to {}", settleDate.get(settleDate.size() - 1), settleDate.get(0));
		List<BankTransferTransaction> bankTransferTransactionList
				= bankTransferRepo.findTransferByDateAndBankCode(
				dateTimeHelper.getBeginingOfDate(settleDate.get(settleDate.size() - 1)),
				dateTimeHelper.getEndOfDate(settleDate.get(0)),
				OneFinConstants.TRANS_SUCCESS,
				OneFinConstants.BankListQrService.VCCB.getBankCode()
		);

		List<BVBIBFTReconciliationDTO> bvbibftReconciliationDTOList
				= bvbibftPersistance.getListIBFTReconciliationDTO(bankTransferTransactionList);
		LOGGER.info("bvbibftReconciliationDTOList: {}", bvbibftReconciliationDTOList.size());
		LOGGER.info("bvbibftReconciliationDTOList: {}", bvbibftReconciliationDTOList);
		String content = bvbTransferRequestUtil.reconciliationFileGen(bvbibftReconciliationDTOList, previousTime1.toLocalDateTime().toDate());
		LOGGER.log(Level.getLevel("INFOWT"), "content: " + content);
		byte[] byteArrray = content.getBytes("UTF-8");
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		try (ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {
			ZipEntry zipEntry = new ZipEntry(bvbTransferRequestUtil.getReconciliationFileName("txt",
					dateTimeHelper.parseDate2String(previousTime1.toLocalDateTime().toDate(), DomainConstants.DATE_FORMAT_TRANS8)));
			zipOutputStream.putNextEntry(zipEntry);
			zipOutputStream.write(byteArrray);
			zipOutputStream.closeEntry();
		}
		byte[] bytes = byteArrayOutputStream.toByteArray();
		String fileName = bvbTransferRequestUtil.getReconciliationFileName("zip",
				dateTimeHelper.parseDate2String(previousTime1.toLocalDateTime().toDate(), DomainConstants.DATE_FORMAT_TRANS8));
		HashMap<String, String> headers = new HashMap<>();
		headers.put(HttpHeaders.CONTENT_TYPE, MediaType.MULTIPART_FORM_DATA_VALUE);

		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("filename", fileName);
		body.add("file", new ByteArrayResource(bytes));

		BVBSendReconciliationRequestData data
				= new BVBSendReconciliationRequestData();
		String processingDate = dateTimeHelper.parseDate2String(dateParse, DomainConstants.DATE_FORMAT_TRANS8);
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
		HttpHeaders headersSend = new HttpHeaders();
		headersSend.setContentType(MediaType.APPLICATION_OCTET_STREAM);
		headersSend.setContentDispositionFormData("attachment", fileName);
		return new ResponseEntity<>(bytes, headersSend, HttpStatus.OK);
	}
}
