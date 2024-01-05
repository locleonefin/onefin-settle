package com.onefin.ewallet.settlement.controller;

import java.io.File;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.servlet.http.HttpServletRequest;

import com.onefin.ewallet.common.utility.date.DateTimeHelper;
import com.onefin.ewallet.settlement.repository.PayGateWrapperKeyManagementRepository;
import com.onefin.ewallet.settlement.repository.PgTransactionsVwRepo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.onefin.ewallet.common.base.search.CustomRsqlVisitor;
import com.onefin.ewallet.common.domain.settlement.VietinBillReport;
import com.onefin.ewallet.common.domain.settlement.VietinBillSummary;
import com.onefin.ewallet.settlement.repository.VietinBillReportRepository;
import com.onefin.ewallet.settlement.repository.VietinBillSummaryRepository;
import com.onefin.ewallet.settlement.service.vnpay.ReportHelper;

import cz.jirutka.rsql.parser.RSQLParser;
import cz.jirutka.rsql.parser.ast.Node;

@RestController
@RequestMapping("/inside/report")
public class ReportController {	
	private static final Logger LOGGER = LoggerFactory.getLogger(ReportController.class);
	
	@Autowired
	private VietinBillReportRepository vietinBillReportRepository;
	
	@Autowired
	private VietinBillSummaryRepository vietinBillSummaryRepository;
	
	@Autowired
	private ReportHelper reportHelper;

	@Autowired
	private DateTimeHelper dateTimeHelper;

	@Autowired
	private Environment env;

	@Autowired
	private PayGateWrapperKeyManagementRepository payGateWrapperKeyManagementRepository;

	@Autowired
	private PgTransactionsVwRepo pgTransactionsVwRepo;

	@GetMapping("/vietin/bill/list")
	public ResponseEntity<Page<VietinBillReport>> getVietinBillReport(@RequestParam(value = "search", required = false) String search,
	                                         Pageable pageable, HttpServletRequest request) throws Exception {
		Page<VietinBillReport> results;
		if (search != null) {
			Node rootNode = new RSQLParser().parse(search);
			Specification<VietinBillReport> spec = rootNode.accept(new CustomRsqlVisitor<>());
			results = vietinBillReportRepository.findAll(spec, pageable);
		} else {
			results = vietinBillReportRepository.findAll(pageable);
		}
		return new ResponseEntity<>(results, HttpStatus.OK);

	}

	@GetMapping("/vietin/bill/file-export")
	public @ResponseBody
	ResponseEntity<?> exportTransToFile(@RequestParam(value = "search", required = false) String search,
	                                    @PageableDefault(sort = {"createdDate"}, size = Integer.MAX_VALUE, direction = Sort.Direction.DESC)
			                                    Pageable pageable) throws Exception {
		Page<VietinBillReport> results;
		if (search != null) {
			Node rootNode = new RSQLParser().parse(search);
			Specification<VietinBillReport> spec = rootNode.accept(new CustomRsqlVisitor<>());
			results = vietinBillReportRepository.findAll(spec, pageable);
		} else {
			results = vietinBillReportRepository.findAll(pageable);
		}

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType("application/vnd.ms-excel"));
		headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

		File file = new File("merchant_transaction_data.xlsx");
		headers.setContentDispositionFormData(file.getName(), file.getName());
		LOGGER.info("Merchant trx file path: {}", file.getCanonicalPath());
		reportHelper.writeBillExcel(results.getContent(), file.getAbsolutePath());
		byte[] result = Files.readAllBytes(file.toPath());
		file.delete();
		return new ResponseEntity<>(result, headers, HttpStatus.OK);
	}
	
	@GetMapping("/vietin/bill/summary/list")
	public ResponseEntity<?> getVietinBillSummary(@RequestParam(value = "fromDate", required = true) String fromDateString,
		@RequestParam(value = "toDate", required = true) String toDateString, HttpServletRequest request) throws Exception {
		List<VietinBillSummary> results = new ArrayList<VietinBillSummary>();
		Date fromDate = new SimpleDateFormat("dd-MM-yyyy").parse(fromDateString);  
		Date toDate = new SimpleDateFormat("dd-MM-yyyy").parse(toDateString); 
		String dateRange = fromDateString +" - "+ toDateString;
		results = vietinBillSummaryRepository.getSummary(fromDate, toDate, dateRange);
		
		return new ResponseEntity<>(results, HttpStatus.OK);

	}
	
	@GetMapping("/vietin/bill/summary/file-export")
	public ResponseEntity<?> getVietinBillSummaryExport(@RequestParam(value = "fromDate", required = true) String fromDateString,
		@RequestParam(value = "toDate", required = true) String toDateString, HttpServletRequest request) throws Exception {
		List<VietinBillSummary> results = new ArrayList<VietinBillSummary>();
		Date fromDate = new SimpleDateFormat("dd-MM-yyyy").parse(fromDateString);  
		Date toDate = new SimpleDateFormat("dd-MM-yyyy").parse(toDateString); 
		toDate = getEndOfDate(toDate);
		String dateRange = fromDateString +" - "+ toDateString;
		results = vietinBillSummaryRepository.getSummary(fromDate, toDate, dateRange);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType("application/vnd.ms-excel"));
		headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
		File file = new File("merchant_transaction_data.xlsx");
		headers.setContentDispositionFormData(file.getName(), file.getName());
		LOGGER.info("Merchant trx file path: {}", file.getCanonicalPath());
		reportHelper.writeBillSummaryExcel(results, file.getAbsolutePath());
		byte[] result = Files.readAllBytes(file.toPath());
		file.delete();
		return new ResponseEntity<>(result, headers, HttpStatus.OK);
	}
	
	private Date getEndOfDate(Date date) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.add(Calendar.HOUR_OF_DAY, 23);
		calendar.add(Calendar.MINUTE, 59);
		calendar.add(Calendar.SECOND, 59);
		calendar.add(Calendar.MILLISECOND, 999);
		return calendar.getTime();
	}
}
