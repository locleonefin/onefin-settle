package com.onefin.ewallet.settlement.controller;


import com.onefin.ewallet.common.base.constants.OneFinConstants;
import com.onefin.ewallet.common.base.service.RestTemplateHelper;
import com.onefin.ewallet.common.domain.bank.vietin.VietinVirtualAcctTransHistory;
import com.onefin.ewallet.common.domain.constants.DomainConstants;
import com.onefin.ewallet.common.utility.date.DateTimeHelper;
import com.onefin.ewallet.settlement.config.SFTPBvbVirtualAcctIntegration;
import com.onefin.ewallet.settlement.repository.VietinVirtualAcctTransHistoryRepo;
import com.onefin.ewallet.settlement.service.ConfigLoader;
import com.onefin.ewallet.settlement.service.bvbank.BVBankReconciliation;
import com.onefin.ewallet.settlement.service.vnpay.ReportHelper;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/bvb/reconciliation/")
public class BvbReconciliationController {
	private static final Logger LOGGER = LoggerFactory.getLogger(BvbReconciliationController.class);
	@Autowired
	RestTemplateHelper restTemplateHelper;
	@Autowired
	private BVBankReconciliation bvBankReconciliation;
	@Autowired
	private DateTimeHelper dateTimeHelper;
	@Autowired
	@Qualifier("SftpBvbVirtualAcctRemoteFileTemplate")
	private SftpRemoteFileTemplate sftpBvbFileTemplate;
	@Autowired
	private ConfigLoader configLoader;
	@Autowired
	private SFTPBvbVirtualAcctIntegration.UploadBvbVirtualAcctGateway gateway;
	@Autowired
	private Environment env;

	@Autowired
	private ReportHelper reportHelper;

	@Autowired
	private VietinVirtualAcctTransHistoryRepo vietinVirtualAcctTransHistoryRepo;

	@GetMapping("/simulate/{currentDateString}")
	public ResponseEntity<?> reconciliationSimulate(
			@PathVariable(required = false) String currentDateString,
			@RequestBody(required = true) Map<String, String> email
	) throws Exception {
		String emailSend = "";
		String emailCC = "";
		if (email.get("send") != null && !email.get("send").isEmpty()) {
			emailSend = email.get("send");
		}
		if (email.get("cc") != null && !email.get("cc").isEmpty()) {
			emailCC = email.get("cc");
		}

		bvBankReconciliation.executeManually(currentDateString, emailSend, emailCC);
		// Return the file content as ResponseEntity with appropriate headers and status
		return new ResponseEntity<>(HttpStatus.OK);
	}


	@GetMapping("/simulate/monthly/{fromDateString}/{toDateString}")
	public ResponseEntity<?> reconciliationSimulateMonthly(
			@RequestParam("file") MultipartFile file,
			@PathVariable(required = false) String fromDateString,
			@PathVariable(required = false) String toDateString

	) throws Exception {

		DateTime fromDate = dateTimeHelper.parseDate(fromDateString,
				OneFinConstants.HO_CHI_MINH_TIME_ZONE,
				OneFinConstants.DATE_FORMAT_ddMMyyyy
		);
		DateTime toDate = dateTimeHelper.parseDate(toDateString,
				OneFinConstants.HO_CHI_MINH_TIME_ZONE,
				OneFinConstants.DATE_FORMAT_ddMMyyyy
		);
		bvBankReconciliation.monthlyReconciliation(fromDate.toDate(), toDate.toDate(),
				(file != null) ? file.getInputStream() : null);
		// Return the file content as ResponseEntity with appropriate headers and status
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@GetMapping("/simulate/monthly-detail/{fromDateString}/{toDateString}")
	public ResponseEntity<?> reconciliationSimulateMonthlyDetail(
			@RequestParam("file") MultipartFile file,
			@PathVariable(required = false) String fromDateString,
			@PathVariable(required = false) String toDateString

	) throws Exception {

		DateTime fromDate = dateTimeHelper.parseDate(fromDateString,
				OneFinConstants.HO_CHI_MINH_TIME_ZONE,
				OneFinConstants.DATE_FORMAT_ddMMyyyy
		);


		DateTime toDate = dateTimeHelper.parseDate(toDateString,
				OneFinConstants.HO_CHI_MINH_TIME_ZONE,
				OneFinConstants.DATE_FORMAT_ddMMyyyy
		);

		bvBankReconciliation.monthlyReconciliationDetail(fromDate.toDate(), toDate.toDate(),
				(file != null) ? file.getInputStream() : null);
		// Return the file content as ResponseEntity with appropriate headers and status
		return new ResponseEntity<>(HttpStatus.OK);

	}

	@GetMapping("/simulate-date/xlsx-export/{currentDateString}")
	public ResponseEntity<?> reconciliationBvbExport(
			@PathVariable(required = true) String currentDateString,
			@RequestBody(required = true) Map<String, String> email
	) throws Exception {

		String emailSend = "";
		String emailCC = "";
		if (email.get("send") != null && !email.get("send").isEmpty()) {
			emailSend = email.get("send");
		}
		if (email.get("cc") != null && !email.get("cc").isEmpty()) {
			emailCC = email.get("cc");
		}

		bvBankReconciliation.executeBVBTransExportManually(currentDateString, emailSend, emailCC);

		// Return the file content as ResponseEntity with appropriate headers and status
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@GetMapping("/simulate-date/xlsx-export/{fromDate}/{toDate}")
	public ResponseEntity<?> reconciliationExport(
			@PathVariable(required = true) String fromDate,
			@PathVariable(required = true) String toDate,
			@RequestBody(required = true) Map<String, String> email
	) throws Exception {

		String emailSend = "";
		String emailCC = "";
		if (email.get("send") != null && !email.get("send").isEmpty()) {
			emailSend = email.get("send");
		}
		if (email.get("cc") != null && !email.get("cc").isEmpty()) {
			emailCC = email.get("cc");
		}
		DateTime fromDateParse = dateTimeHelper.parseDate(fromDate,
				OneFinConstants.HO_CHI_MINH_TIME_ZONE,
				DomainConstants.DATE_FORMAT_TRANS11);
		fromDateParse = fromDateParse.plusHours(1);
		DateTime toDateParse = dateTimeHelper.parseDate(toDate,
				OneFinConstants.HO_CHI_MINH_TIME_ZONE,
				DomainConstants.DATE_FORMAT_TRANS11);
		toDateParse = toDateParse.plusHours(1);
		bvBankReconciliation.executeBVBTransExportFromDateToDateManually(
				dateTimeHelper.parseDateString(
						fromDateParse, DomainConstants.DATE_FORMAT_TRANS11),
				dateTimeHelper.parseDateString(
						toDateParse, DomainConstants.DATE_FORMAT_TRANS11),
				emailSend, emailCC);
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@GetMapping("/simulate-full/xlsx-export/{fromDate}/{toDate}")
	public ResponseEntity<?> reconciliationFullExport(
			@PathVariable(required = true) String fromDate,
			@PathVariable(required = true) String toDate,
			@RequestBody(required = true) Map<String, String> email
	) throws Exception {

		String emailSend = "";
		String emailCC = "";
		if (email.get("send") != null && !email.get("send").isEmpty()) {
			emailSend = email.get("send");
		}
		if (email.get("cc") != null && !email.get("cc").isEmpty()) {
			emailCC = email.get("cc");
		}

		bvBankReconciliation.executeBVBTransExportFromDateToDateFullManually(
				fromDate, toDate,
				emailSend, emailCC);
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@PostMapping("/simulate-full/xlsx-export/test")
	public ResponseEntity<?> reconciliationExportTest(
			@RequestBody(required = true) Map<String, String> test
	) throws Exception {
		LocalDate date = LocalDate.parse(test.get("createdDate"));

		LocalDateTime startOfDay = date.atStartOfDay();

		LocalDateTime startOfNextDay = date.plusDays(1).atStartOfDay();

		// Format ngày
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

		String formattedStartOfDay = startOfDay.format(formatter);

		String formattedStartOfNextDay = startOfNextDay.format(formatter);

		List<VietinVirtualAcctTransHistory> results = vietinVirtualAcctTransHistoryRepo.findByCodeAndDate(test.get("bankCode"), formattedStartOfDay, formattedStartOfNextDay);

		HttpHeaders headers = new HttpHeaders();

		headers.setContentType(MediaType.parseMediaType("application/vnd.ms-excel"));

		headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

		File file = new File("test.xlsx");

		headers.setContentDispositionFormData(file.getName(), file.getName());

		LOGGER.info("Test: {}", file.getCanonicalPath());

		reportHelper.writeExcelByBankCodeAndCreatedDate(results, test.get("createdDate"), file.getAbsolutePath());

		// Read the content of the Excel file into a byte array
		byte[] fileContent = Files.readAllBytes(file.toPath());

		return new ResponseEntity<>(fileContent, headers, HttpStatus.OK);
	}

}
