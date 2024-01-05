package com.onefin.ewallet.settlement.service.asc;

import com.onefin.ewallet.common.base.constants.OneFinConstants;
import com.onefin.ewallet.common.base.search.CustomRsqlVisitor;
import com.onefin.ewallet.common.domain.asc.AscSchool;
import com.onefin.ewallet.common.domain.asc.AscSchoolBankAccount;
import com.onefin.ewallet.common.domain.asc.AscTransaction;
import com.onefin.ewallet.common.domain.holiday.Holiday;
import com.onefin.ewallet.common.domain.merchant.MerchantTransactionSS;
import com.onefin.ewallet.common.utility.date.DateTimeHelper;
import com.onefin.ewallet.common.utility.file.ExcelHelper;
import com.onefin.ewallet.common.utility.string.StringHelper;
import com.onefin.ewallet.settlement.common.SettleConstants;
import com.onefin.ewallet.settlement.dto.AscTransactionDetailsFiltered;
import com.onefin.ewallet.settlement.repository.AscSchoolRepository;
import com.onefin.ewallet.settlement.repository.AscTransactionRepository;
import com.onefin.ewallet.settlement.repository.HolidayRepo;
import com.onefin.ewallet.settlement.repository.MerchantTrxRepoSS;
import com.onefin.ewallet.settlement.service.ConfigLoader;
import com.onefin.ewallet.settlement.service.MinioService;
import com.onefin.ewallet.settlement.service.SettleService;
import cz.jirutka.rsql.parser.RSQLParser;
import cz.jirutka.rsql.parser.ast.Node;
import org.apache.poi.hssf.usermodel.HSSFDataFormat;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.joda.time.DateTime;
import org.modelmapper.ModelMapper;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@DisallowConcurrentExecution
public class AscDailyReportSendEmail extends QuartzJobBean {

	private static final Logger LOGGER = LoggerFactory.getLogger(AscDailyReportSendEmail.class);

	private static final String LOG_ASC_SEND_EMAIL_DAILY = "ASC_SEND_EMAIL_DAILY - ";
	private static final String CELL_DATE_FORMAT = "dd/MM/yyyy";

	private static final int NUM_COLUMN = 11;

	public static final int COLUMN_INDEX_DATE_FROM = 5;
	public static final int COLUMN_INDEX_DATE_TO = 5;
	public static final int COLUMN_INDEX_COUNT_APPROVED_TRANS = 5;
	public static final int COLUMN_INDEX_SUM_APPROVED_TRANS = 5;
	public static final int COLUMN_INDEX_SUM_SETTLED_TRANS = 5;

	public static final int COLUMN_INDEX_ORDER = 0;
	public static final int COLUMN_INDEX_SCHOOL_NAME = 1;
	public static final int COLUMN_INDEX_USER_CODE = 2;
	public static final int COLUMN_INDEX_FULL_NAME = 3;
	public static final int COLUMN_INDEX_CLASS = 4;
	public static final int COLUMN_INDEX_TRANS_DATE = 5;
	public static final int COLUMN_INDEX_ASC_TRANS_ID = 6;
	public static final int COLUMN_INDEX_ONEFIN_TRANS_ID = 7;
	public static final int COLUMN_INDEX_TRANS_AMOUNT = 8;
	public static final int COLUMN_INDEX_PAYMENT_CHANNEL = 9;
	public static final int COLUMN_INDEX_CARD_TYPE = 10;

	public static final int COLUMN_INDEX_ACCOUNT_NUMBER = 11;

	public static final int COLUMN_INDEX_ACCOUNT_BANK_NAME = 12;

	public static final int START_ROW_INDEX = 9;


	@Autowired
	private DateTimeHelper dateHelper;

	@Autowired
	private HolidayRepo holidayRepo;

	@Autowired
	private AscSchoolRepository ascSchoolRepository;

	@Autowired
	private MerchantTrxRepoSS merchantTrxRepoSS;

	@Autowired
	private MinioService minioService;

	@Autowired
	private Environment env;

	@Autowired
	private ConfigLoader configLoader;

	@Autowired
	private DateTimeHelper dateTimeHelper;

	@Autowired
	private SettleService settleService;

	@Autowired
	private StringHelper stringHelper;

	@Autowired
	private AscTransactionRepository ascTransactionRepository;

	@Autowired
	private ModelMapper modelMapper;

	/**
	 * @param context
	 * @throws JobExecutionException
	 */
	@Override
	protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
		List<Date> settleDate = new ArrayList<>();
		DateTime currentTime = dateHelper.currentDateTime(OneFinConstants.HO_CHI_MINH_TIME_ZONE);
		if (checkIsHoliday(currentTime.toLocalDateTime().toDate())) {
			LOGGER.info("{}Today is holiday, no task", LOG_ASC_SEND_EMAIL_DAILY);
			return;
		}
		DateTime previousTime = currentTime.minusDays(1);
		settleDate.add(previousTime.toLocalDateTime().toDate());
		while (checkIsHoliday(previousTime.toLocalDateTime().toDate())) {
			previousTime = previousTime.minusDays(1);
			settleDate.add(previousTime.toLocalDateTime().toDate());
		}
		LOGGER.info("{}Settle date list: {}", LOG_ASC_SEND_EMAIL_DAILY, settleDate);

		List<AscSchool> listSchool = ascSchoolRepository.findAll();
		listSchool.stream().forEach(e -> {
			try {
				if (e.getSendMail().equals(new Boolean(true))) {
					LOGGER.info("{}Process send email to shool: {}, {}",
							LOG_ASC_SEND_EMAIL_DAILY, e.getName(), e.getAssociateMid());
					OptionalInt indexOpt = IntStream.range(0, configLoader.getMidSplitTransByAccount().size()).filter(i -> e.getAssociateMid().equals(configLoader.getMidSplitTransByAccount().get(i))).findFirst();
					if (indexOpt.isPresent()) {
						sendEmailByBankAccount(e, configLoader.getMcodeSplitTransByAccount().get(indexOpt.getAsInt()), settleDate, currentTime);
					} else {
						sendEmailByShool(e, settleDate, currentTime);
					}
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		});
	}

	/**
	 * Execute send email to asc school manually
	 * primaryMid empty => send email to all school
	 *
	 * @param executeDate
	 * @param primaryMid
	 */
	public void executeManually(String executeDate, String primaryMid) {
		List<Date> settleDate = new ArrayList<>();
		DateTime currentTime = dateHelper.parseDate(executeDate, SettleConstants.HO_CHI_MINH_TIME_ZONE, SettleConstants.DATE_FORMAT_ddMMyyyy);
		if (checkIsHoliday(currentTime.toLocalDateTime().toDate())) {
			LOGGER.info("{}Today is holiday, no task", LOG_ASC_SEND_EMAIL_DAILY);
			return;
		}
		DateTime previousTime = currentTime.minusDays(1);
		settleDate.add(previousTime.toLocalDateTime().toDate());
		while (checkIsHoliday(previousTime.toLocalDateTime().toDate())) {
			previousTime = previousTime.minusDays(1);
			settleDate.add(previousTime.toLocalDateTime().toDate());
		}
		LOGGER.info("{}Settle date list: {}", LOG_ASC_SEND_EMAIL_DAILY, settleDate);

		List<AscSchool> listSchool = new ArrayList<>();
		if (primaryMid == null) {
			listSchool = ascSchoolRepository.findAll();
			LOGGER.info("{}All school list: {}", LOG_ASC_SEND_EMAIL_DAILY, listSchool);
		} else {
			listSchool = ascSchoolRepository.findAscSchoolByAssociateMid(primaryMid);
			LOGGER.info("{}{} school list: {}", LOG_ASC_SEND_EMAIL_DAILY, primaryMid, listSchool);
		}
		listSchool.stream().forEach(e -> {
			try {
				if (e.getSendMail().equals(new Boolean(true))) {
					LOGGER.info("{}Process send email to shool: {}, {}", LOG_ASC_SEND_EMAIL_DAILY, e.getName(), e.getAssociateMid());
					OptionalInt indexOpt = IntStream.range(0, configLoader.getMidSplitTransByAccount().size()).filter(i -> e.getAssociateMid().equals(configLoader.getMidSplitTransByAccount().get(i))).findFirst();
					if (indexOpt.isPresent()) {
						sendEmailByBankAccount(e, configLoader.getMcodeSplitTransByAccount().get(indexOpt.getAsInt()), settleDate, currentTime);
					} else {
						sendEmailByShool(e, settleDate, currentTime);
					}
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		});

	}


	private void sendEmailByShool(AscSchool school, List<Date> settleDate, DateTime currentTime) throws Exception {
		String search = String.format("primaryMid==%s;transStatus=in=(APPROVED,SETTLED);trxDate=ge=%s;trxDate=le=%s;trxType=out=(%s)", school.getAssociateMid(), dateHelper.parseDate2String(settleDate.get(settleDate.size() - 1), OneFinConstants.DATE_FORMAT_dd_MM_yyyy), dateHelper.parseDate2String(settleDate.get(0), OneFinConstants.DATE_FORMAT_dd_MM_yyyy), OneFinConstants.SPENDING_CHARGE);
		LOGGER.info("{}query: {}", LOG_ASC_SEND_EMAIL_DAILY, search);
		if (search != null) {
			Node rootNode = new RSQLParser().parse(search);
			Specification<MerchantTransactionSS> spec = rootNode.accept(new CustomRsqlVisitor<>());
			List<MerchantTransactionSS> ascTransactions = merchantTrxRepoSS.findAll(spec);
			if (ascTransactions.size() == 0) {
				LOGGER.info("{}No transaction => Not send email: {}, {}", LOG_ASC_SEND_EMAIL_DAILY, school.getName(), school.getAssociateMid());
				return;
			}
			if (school.getEmail().size() == 0) {
				LOGGER.error("{}School {}, {}, Email not available, Please help to check!", LOG_ASC_SEND_EMAIL_DAILY, school.getName(), school.getAssociateMid());
				return;
			}
			for (String email : school.getEmail()) {
				if (stringHelper.checkNullEmptyBlank(email)) {
					return;
				}
			}
			File templateFile = getTemplate(school, currentTime);

			// Write to excel file
			templateFile = writeData2File(ascTransactions, settleDate, templateFile, school);
			if (templateFile != null) {
				LOGGER.info("{}Write to file successfully, upload to minio and send email: {}, {}", LOG_ASC_SEND_EMAIL_DAILY, school.getName(), school.getAssociateMid());
				minioService.uploadFile(env.getProperty("minio.ewalletCommonFileBucket"), OneFinConstants.PARTNER_ASC + "/EMAIL_DAILY_REPORT/" + school.getAssociateMid() + "/" + templateFile.getName(), templateFile, "text/plain");
				settleService.ascDailySendEmail(school.getEmail(), school.getCcEmail(), school.getName(), dateHelper.parseDate2String(settleDate.get(settleDate.size() - 1), OneFinConstants.DATE_FORMAT_dd_MM_yyyy), dateHelper.parseDate2String(settleDate.get(0), OneFinConstants.DATE_FORMAT_dd_MM_yyyy), env.getProperty("minio.ewalletCommonFileBucket"), OneFinConstants.PARTNER_ASC + "/EMAIL_DAILY_REPORT/" + school.getAssociateMid() + "/" + templateFile.getName(), "ONEFIN - Tổng hợp giao dịch đối soát theo ngày – " + school.getName());
				templateFile.delete();
			}
		}
	}

	private void sendEmailByBankAccount(AscSchool school, String merchantCode, List<Date> settleDate, DateTime currentTime) throws Exception {
		String search = String.format("primaryMid==%s;transStatus=in=(APPROVED,SETTLED);trxDate=ge=%s;trxDate=le=%s;trxType=out=(%s)", school.getAssociateMid(), dateHelper.parseDate2String(settleDate.get(settleDate.size() - 1), OneFinConstants.DATE_FORMAT_dd_MM_yyyy), dateHelper.parseDate2String(settleDate.get(0), OneFinConstants.DATE_FORMAT_dd_MM_yyyy), OneFinConstants.SPENDING_CHARGE);
		LOGGER.info("{}query: {}", LOG_ASC_SEND_EMAIL_DAILY, search);
		if (search != null) {
			Node rootNode = new RSQLParser().parse(search);
			Specification<MerchantTransactionSS> spec = rootNode.accept(new CustomRsqlVisitor<>());
			List<MerchantTransactionSS> ascTransactions = merchantTrxRepoSS.findAll(spec);
			if (ascTransactions.size() == 0) {
				LOGGER.info("{}No transaction => Not send email: {}, {}", LOG_ASC_SEND_EMAIL_DAILY, school.getName(), school.getAssociateMid());
				return;
			}
			if (school.getEmail().size() == 0) {
				LOGGER.error("{}School {}, {}, Email not available, Please help to check!", LOG_ASC_SEND_EMAIL_DAILY, school.getName(), school.getAssociateMid());
				return;
			}
			for (String email : school.getEmail()) {
				if (stringHelper.checkNullEmptyBlank(email)) {
					return;
				}
			}

			// Process searchChildAscTransBySearch
			String searchChild = String.format("associateMerchantCode==%s;createdDate=ge=%s;createdDate=le=%s", merchantCode, dateHelper.parseDate2String(settleDate.get(settleDate.size() - 1), OneFinConstants.DATE_FORMAT_dd_MM_yyyy), dateHelper.parseDate2String(settleDate.get(0), OneFinConstants.DATE_FORMAT_dd_MM_yyyy));
			List<AscTransactionDetailsFiltered> ascChildTransactions = searchChildAscTrans(searchChild);
			LOGGER.info("AscTransactionDetailsFiltered size: {}", ascChildTransactions.size());

			File templateFile = getTemplateByBankAccount(school, currentTime);

			// Write to excel file
			templateFile = writeData2FileByBankAccount(ascTransactions, ascChildTransactions, settleDate, templateFile, school);

			if (templateFile != null) {
				LOGGER.info("{}Write to file successfully, upload to minio and send email: {}, {}", LOG_ASC_SEND_EMAIL_DAILY, school.getName(), school.getAssociateMid());
				minioService.uploadFile(env.getProperty("minio.ewalletCommonFileBucket"), OneFinConstants.PARTNER_ASC + "/EMAIL_DAILY_REPORT/" + school.getAssociateMid() + "/" + templateFile.getName(), templateFile, "text/plain");
				settleService.ascDailySendEmail(school.getEmail(), school.getCcEmail(), school.getName(), dateHelper.parseDate2String(settleDate.get(settleDate.size() - 1), OneFinConstants.DATE_FORMAT_dd_MM_yyyy), dateHelper.parseDate2String(settleDate.get(0), OneFinConstants.DATE_FORMAT_dd_MM_yyyy), env.getProperty("minio.ewalletCommonFileBucket"), OneFinConstants.PARTNER_ASC + "/EMAIL_DAILY_REPORT/" + school.getAssociateMid() + "/" + templateFile.getName(), "ONEFIN - Tổng hợp giao dịch đối soát theo ngày – " + school.getName());
				templateFile.delete();
			}
		}
	}

	private File writeData2File(List<MerchantTransactionSS> ascTransactions, List<Date> settleDate, File templateFile, AscSchool school) throws IOException {
		//Prepare data
		long approvedTransNum = ascTransactions.stream().filter(e -> e.getTransStatus().equals("APPROVED") || e.getTransStatus().equals("SETTLED")).count();
		double approvedSumAmount = ascTransactions.stream().filter(e -> e.getTransStatus().equals("APPROVED") || e.getTransStatus().equals("SETTLED")).mapToDouble(e -> e.getTrxAmount().doubleValue()).sum();
		double settledSumAmount = ascTransactions.stream().filter(e -> e.getTransStatus().equals("SETTLED")).mapToDouble(e -> e.getTrxAmount().doubleValue()).sum();
		LOGGER.info("{}School MID {}, total settled amount {}", LOG_ASC_SEND_EMAIL_DAILY, school.getAssociateMid(), settledSumAmount / 100);
		if (approvedSumAmount != settledSumAmount) {
			LOGGER.error("{}Approved amount {} and settled amount {} not match, Please check: {}, {}", LOG_ASC_SEND_EMAIL_DAILY, String.format("%.0f", approvedSumAmount / 100), String.format("%.0f", settledSumAmount / 100), school.getName(), school.getAssociateMid());
			return null;
		}
		// Write to excel file
		// Create Workbook
		Workbook templateWorkbook = getWorkbook(new FileInputStream(templateFile.getCanonicalPath()), templateFile.getCanonicalPath());
		// Create sheet
		Sheet temolateSheet = templateWorkbook.getSheetAt(0);
		templateWorkbook.setSheetName(templateWorkbook.getSheetIndex(temolateSheet), school.getName());
		int rowIndex = 1;
		Row row = temolateSheet.getRow(rowIndex);
		Cell cell0 = row.getCell(COLUMN_INDEX_DATE_FROM);
		cell0.setCellValue(settleDate.get(settleDate.size() - 1));
		cell0.setCellStyle(createCellDateType(templateWorkbook, true, false));
		rowIndex += 1;

		row = temolateSheet.getRow(rowIndex);
		Cell cell1 = row.getCell(COLUMN_INDEX_DATE_TO);
		cell1.setCellValue(settleDate.get(0));
		cell1.setCellStyle(createCellDateType(templateWorkbook, true, false));
		rowIndex += 1;

		row = temolateSheet.getRow(rowIndex);
		Cell cell2 = row.getCell(COLUMN_INDEX_COUNT_APPROVED_TRANS);
		cell2.setCellValue(approvedTransNum);
		cell2.setCellStyle(createCellDateType(templateWorkbook, false, true));
		rowIndex += 1;

		row = temolateSheet.getRow(rowIndex);
		Cell cell3 = row.getCell(COLUMN_INDEX_SUM_APPROVED_TRANS);
		cell3.setCellValue(approvedSumAmount / 100);
		cell3.setCellStyle(createCellDateType(templateWorkbook, false, true));
		rowIndex += 1;

		row = temolateSheet.getRow(rowIndex);
		Cell cell4 = row.getCell(COLUMN_INDEX_SUM_SETTLED_TRANS);
		cell4.setCellValue(settledSumAmount / 100);
		cell4.setCellStyle(createCellDateType(templateWorkbook, false, true));
		rowIndex += 4;

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		templateWorkbook.write(baos);
		SXSSFWorkbook workbook = new SXSSFWorkbook(new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray())), 100);
		SXSSFSheet sheet = workbook.getSheetAt(0);
		CellStyle style = workbook.createCellStyle();
		style.setAlignment(HorizontalAlignment.CENTER);
		style.setBorderBottom(BorderStyle.THIN);
		style.setBorderTop(BorderStyle.THIN);
		style.setBorderRight(BorderStyle.THIN);
		style.setBorderLeft(BorderStyle.THIN);
		DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S");
		DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern(OneFinConstants.DATE_FORMAT_ddSMMSyyyy_HHTDmmTDss);

		for (int i = 1; i <= NUM_COLUMN; i++) {
			sheet.setColumnWidth(i, 35 * 256);
		}
		int order = 1;
		for (MerchantTransactionSS ascTransaction : ascTransactions) {
			//LOGGER.info("{}Row: {}", LOG_ASC_SEND_EMAIL_DAILY, ascTransaction);
			// Create row
			Row row0 = sheet.createRow(rowIndex);
			// Write data on row
			//LOGGER.info("{}Debug row: {}, sheet: {}, fileData: {}", LOG_ASC_SEND_EMAIL_DAILY, row, sheet, templateFile);
			Cell cell = row0.createCell(COLUMN_INDEX_ORDER);
			cell.setCellValue(order);
			cell.setCellStyle(style);

			cell = row0.createCell(COLUMN_INDEX_SCHOOL_NAME);
			cell.setCellValue(ascTransaction.getShopName());
			cell.setCellStyle(style);

			cell = row0.createCell(COLUMN_INDEX_USER_CODE);
			cell.setCellValue(ascTransaction.getMemberId());
			cell.setCellStyle(style);

			cell = row0.createCell(COLUMN_INDEX_FULL_NAME);
			cell.setCellValue(ascTransaction.getLastName() + " " + ascTransaction.getFirstName());
			cell.setCellStyle(style);

			cell = row0.createCell(COLUMN_INDEX_CLASS);
			cell.setCellValue(ascTransaction.getAddressLine1());
			cell.setCellStyle(style);

			Cell cell5 = row0.createCell(COLUMN_INDEX_TRANS_DATE);
			LocalDateTime dateTime = LocalDateTime.parse(String.valueOf(ascTransaction.getTrxDate()), inputFormatter);
			String formattedDate = dateTime.format(outputFormatter);
			cell5.setCellValue(formattedDate);
			cell5.setCellStyle(style);

			cell = row0.createCell(COLUMN_INDEX_ASC_TRANS_ID);
			cell.setCellValue(ascTransaction.getTrxUniqueKey());
			cell.setCellStyle(style);

			cell = row0.createCell(COLUMN_INDEX_ONEFIN_TRANS_ID);
			cell.setCellValue(ascTransaction.getTrxId());
			cell.setCellStyle(style);

			cell = row0.createCell(COLUMN_INDEX_TRANS_AMOUNT);
			cell.setCellValue(ascTransaction.getTrxAmount().doubleValue() / 100);
			cell.setCellStyle(style);

			cell = row0.createCell(COLUMN_INDEX_PAYMENT_CHANNEL);
			if (ascTransaction.getChannelType().equals(OneFinConstants.SoftSpaceChanelType.SPENDING_ECOMMERCE_QR_DYNAMIC.getValue())) {
				cell.setCellValue("VDT");
				cell.setCellStyle(style);
			}
			if (ascTransaction.getChannelType().equals(OneFinConstants.SoftSpaceChanelType.SPENDING_CHANNEL_TYPE_LOCAL_DEBIT.getValue())) {
				cell.setCellValue("CTT");
				cell.setCellStyle(style);
			}
			if (ascTransaction.getChannelType().equals(OneFinConstants.SoftSpaceChanelType.SPENDING_CHANNEL_TYPE_THIRD_PARTY_BANK_PAYMENT.getValue())) {
				cell.setCellValue("CTT");
				cell.setCellStyle(style);
			}
			if (ascTransaction.getChannelType().equals(OneFinConstants.SoftSpaceChanelType.SPENDING_CHANNEL_TYPE_CREDIT_DEBIT_CARD.getValue()) || ascTransaction.getChannelType().equals(OneFinConstants.SoftSpaceChanelType.SPENDING_CHANNEL_TYPE_CREDIT_DEBIT_CARD_INT.getValue())) {
				cell.setCellValue("CTT");
				cell.setCellStyle(style);
			}

			cell = row0.createCell(COLUMN_INDEX_CARD_TYPE);
			if (ascTransaction.getChannelType().equals(OneFinConstants.SoftSpaceChanelType.SPENDING_CHANNEL_TYPE_LOCAL_DEBIT.getValue())) {
				cell.setCellValue("ATM");
				cell.setCellStyle(style);
			}
			if (ascTransaction.getChannelType().equals(OneFinConstants.SoftSpaceChanelType.SPENDING_CHANNEL_TYPE_THIRD_PARTY_BANK_PAYMENT.getValue())) {
				cell.setCellValue("VietQR");
				cell.setCellStyle(style);
			}
			if (ascTransaction.getChannelType().equals(OneFinConstants.SoftSpaceChanelType.SPENDING_CHANNEL_TYPE_CREDIT_DEBIT_CARD.getValue()) || ascTransaction.getChannelType().equals(OneFinConstants.SoftSpaceChanelType.SPENDING_CHANNEL_TYPE_CREDIT_DEBIT_CARD_INT.getValue())) {
				cell.setCellValue("Visa/Master/JCB");
				cell.setCellStyle(style);
			}

			rowIndex++;
			order++;
		}
		try (FileOutputStream fos = new FileOutputStream(templateFile); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
			workbook.write(bos);
			workbook.dispose();
			// Write the byte array to the temporary file
			fos.write(bos.toByteArray());
		} finally {
			workbook.close();
		}
		return templateFile;
	}

	private File writeData2FileByBankAccount(List<MerchantTransactionSS> ascTransactions, List<AscTransactionDetailsFiltered> ascChildTransRaw, List<Date> settleDate, File templateFile, AscSchool school) throws IOException {

		double approvedSumAmount = ascTransactions.stream().filter(e -> e.getTransStatus().equals("APPROVED") || e.getTransStatus().equals("SETTLED")).mapToDouble(e -> e.getTrxAmount().doubleValue()).sum();
		double settledSumAmount = ascTransactions.stream().filter(e -> e.getTransStatus().equals("SETTLED")).mapToDouble(e -> e.getTrxAmount().doubleValue()).sum();

		LOGGER.info("{}School MID {}, total settled amount {}", LOG_ASC_SEND_EMAIL_DAILY, school.getAssociateMid(), settledSumAmount / 100);

		if (approvedSumAmount != settledSumAmount) {
			LOGGER.error("{}Approved amount {} and settled amount {} not match, Please check: {}, {}", LOG_ASC_SEND_EMAIL_DAILY, String.format("%.0f", approvedSumAmount / 100), String.format("%.0f", settledSumAmount / 100), school.getName(), school.getName(), school.getAssociateMid());
			return null;
		}

		// Write to excel file
		// Create Workbook
		Workbook templateWorkbook = getWorkbook(new FileInputStream(templateFile.getCanonicalPath()), templateFile.getCanonicalPath());
		DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S");
		DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern(OneFinConstants.DATE_FORMAT_ddSMMSyyyy_HHTDmmTDss);
		List<AscSchoolBankAccount> schoolBankAccounts = new ArrayList<>(school.getBankAccounts());
		int rowIndex;
		for (int i = 0; i < school.getBankAccounts().size() - 1; i++) {
			templateWorkbook.cloneSheet(0);
		}

		for (int i = 0; i < school.getBankAccounts().size(); i++) {

			int finalI = i;

			List<AscTransactionDetailsFiltered> ascTransactionsFilters = ascChildTransRaw.stream().filter(e -> e.getMerchantAccId().equals(schoolBankAccounts.get(finalI).getAccId())).collect(Collectors.groupingBy(p -> p.getTrxUniqueKey(), Collectors.mapping(p -> new AscTransactionDetailsFiltered(p.getShopName(), p.getMemberId(), p.getFirstName(), p.getLastName(), p.getAddressLine1(), p.getCreatedDate(), p.getTrxUniqueKey(), p.getTrxId(), p.getItemAmount(), p.getChannelType(), p.getAccountNo(), p.getBankName(), p.getTransStatus()), Collectors.reducing((p1, p2) -> new AscTransactionDetailsFiltered(p1.getShopName(), p1.getMemberId(), p1.getFirstName(), p1.getLastName(), p1.getAddressLine1(), p1.getCreatedDate(), p1.getTrxUniqueKey(), p1.getTrxId(), p1.getItemAmount().add(p2.getItemAmount()), p1.getChannelType(), p1.getAccountNo(), p1.getBankName(), p1.getTransStatus()))))).values().stream().map(Optional::get).collect(Collectors.toList());

			LOGGER.info("AscTransactionsFilters size: {}", ascTransactionsFilters.size());


			templateWorkbook.setSheetName(i, schoolBankAccounts.get(i).getAccountNo() + "_" + schoolBankAccounts.get(i).getBankDetails().getBankList().getName().split("_")[0]);

			Sheet templateSheet = templateWorkbook.getSheetAt(i);

			double approvedSumAmountChild = ascTransactionsFilters.stream().filter(e -> e.getTransStatus().equals("APPROVED") || e.getTransStatus().equals("SETTLED")).mapToDouble(e -> e.getItemAmount().doubleValue()).sum();
			double settledSumAmountChild = ascTransactionsFilters.stream().filter(e -> e.getTransStatus().equals("SETTLED")).mapToDouble(e -> e.getItemAmount().doubleValue()).sum();
			rowIndex = 1;

			Row row = templateSheet.getRow(rowIndex);
			Cell cell0 = row.getCell(COLUMN_INDEX_DATE_FROM);
			cell0.setCellValue(settleDate.get(settleDate.size() - 1));
			cell0.setCellStyle(createCellDateType(templateWorkbook, true, false));
			rowIndex += 1;

			row = templateSheet.getRow(rowIndex);
			Cell cell1 = row.getCell(COLUMN_INDEX_DATE_TO);
			cell1.setCellValue(settleDate.get(0));
			cell1.setCellStyle(createCellDateType(templateWorkbook, true, false));
			rowIndex += 1;

			row = templateSheet.getRow(rowIndex);
			Cell cell2 = row.getCell(COLUMN_INDEX_COUNT_APPROVED_TRANS);
			cell2.setCellValue(ascTransactionsFilters.size());
			cell2.setCellStyle(createCellDateType(templateWorkbook, false, true));
			rowIndex += 1;

			row = templateSheet.getRow(rowIndex);
			Cell cell3 = row.getCell(COLUMN_INDEX_SUM_APPROVED_TRANS);
			cell3.setCellValue(approvedSumAmountChild);
			cell3.setCellStyle(createCellDateType(templateWorkbook, false, true));
			rowIndex += 1;

			row = templateSheet.getRow(rowIndex);
			Cell cell4 = row.getCell(COLUMN_INDEX_SUM_SETTLED_TRANS);
			cell4.setCellValue(settledSumAmountChild);
			cell4.setCellStyle(createCellDateType(templateWorkbook, false, true));
			rowIndex += 4;

		}

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		templateWorkbook.write(baos);

		// Create the SXSSFWorkbook from the baos
		SXSSFWorkbook workbook = new SXSSFWorkbook(new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray())), 100);
		baos.close();
		CellStyle style = workbook.createCellStyle();
		style.setAlignment(HorizontalAlignment.CENTER);
		style.setBorderBottom(BorderStyle.THIN);
		style.setBorderTop(BorderStyle.THIN);
		style.setBorderRight(BorderStyle.THIN);
		style.setBorderLeft(BorderStyle.THIN);

		for (int i = 0; i < school.getBankAccounts().size(); i++) {
			rowIndex = START_ROW_INDEX;
			int finalI = i;
			int order = 1;
			List<AscTransactionDetailsFiltered> ascTransactionsFilters = ascChildTransRaw.stream().filter(e -> e.getMerchantAccId().equals(schoolBankAccounts.get(finalI).getAccId())).collect(Collectors.groupingBy(p -> p.getTrxUniqueKey(), Collectors.mapping(p -> new AscTransactionDetailsFiltered(p.getShopName(), p.getMemberId(), p.getFirstName(), p.getLastName(), p.getAddressLine1(), p.getCreatedDate(), p.getTrxUniqueKey(), p.getTrxId(), p.getItemAmount(), p.getChannelType(), p.getAccountNo(), p.getBankName(), p.getTransStatus()), Collectors.reducing((p1, p2) -> new AscTransactionDetailsFiltered(p1.getShopName(), p1.getMemberId(), p1.getFirstName(), p1.getLastName(), p1.getAddressLine1(), p1.getCreatedDate(), p1.getTrxUniqueKey(), p1.getTrxId(), p1.getItemAmount().add(p2.getItemAmount()), p1.getChannelType(), p1.getAccountNo(), p1.getBankName(), p1.getTransStatus()))))).values().stream().map(Optional::get).collect(Collectors.toList());

			SXSSFSheet sheet = workbook.getSheetAt(i);
			for (int j = 1; j <= NUM_COLUMN; j++) {
				sheet.setColumnWidth(j, 35 * 256);
			}
			for (AscTransactionDetailsFiltered ascTransactionsFilter : ascTransactionsFilters) {
				//LOGGER.info("{}Row: {}", LOG_ASC_SEND_EMAIL_DAILY, ascTransaction);
				// Create row
				Row row0 = sheet.createRow(rowIndex);
				// Write data on row
				//LOGGER.info("{}Debug row: {}, sheet: {}, fileData: {}", LOG_ASC_SEND_EMAIL_DAILY, row, sheet, templateFile);
				Cell cell = row0.createCell(COLUMN_INDEX_ORDER);
				cell.setCellValue(order);
				cell.setCellStyle(style);

				cell = row0.createCell(COLUMN_INDEX_SCHOOL_NAME);
				cell.setCellValue(ascTransactionsFilter.getShopName());
				cell.setCellStyle(style);

				cell = row0.createCell(COLUMN_INDEX_USER_CODE);
				cell.setCellValue(ascTransactionsFilter.getMemberId());
				cell.setCellStyle(style);

				cell = row0.createCell(COLUMN_INDEX_FULL_NAME);
				cell.setCellValue(ascTransactionsFilter.getLastName() + " " + ascTransactionsFilter.getFirstName());
				cell.setCellStyle(style);

				cell = row0.createCell(COLUMN_INDEX_CLASS);
				cell.setCellValue(ascTransactionsFilter.getAddressLine1());
				cell.setCellStyle(style);

				Cell cell5 = row0.createCell(COLUMN_INDEX_TRANS_DATE);
				LocalDateTime dateTime = LocalDateTime.parse(String.valueOf(ascTransactionsFilter.getCreatedDate()), inputFormatter);
				String formattedDate = dateTime.format(outputFormatter);
				cell5.setCellValue(formattedDate);
				cell5.setCellStyle(style);

				cell = row0.createCell(COLUMN_INDEX_ASC_TRANS_ID);
				cell.setCellValue(ascTransactionsFilter.getTrxUniqueKey());
				cell.setCellStyle(style);

				cell = row0.createCell(COLUMN_INDEX_ONEFIN_TRANS_ID);
				cell.setCellValue(ascTransactionsFilter.getTrxId());
				cell.setCellStyle(style);

				cell = row0.createCell(COLUMN_INDEX_TRANS_AMOUNT);
				cell.setCellValue(ascTransactionsFilter.getItemAmount().doubleValue());
				cell.setCellStyle(style);

				cell = row0.createCell(COLUMN_INDEX_PAYMENT_CHANNEL);
				if (ascTransactionsFilter.getChannelType().equals(OneFinConstants.SoftSpaceChanelType.SPENDING_ECOMMERCE_QR_DYNAMIC.getValue())) {
					cell.setCellValue("VDT");
					cell.setCellStyle(style);
				}
				if (ascTransactionsFilter.getChannelType().equals(OneFinConstants.SoftSpaceChanelType.SPENDING_CHANNEL_TYPE_LOCAL_DEBIT.getValue())) {
					cell.setCellValue("CTT");
					cell.setCellStyle(style);
				}
				if (ascTransactionsFilter.getChannelType().equals(OneFinConstants.SoftSpaceChanelType.SPENDING_CHANNEL_TYPE_THIRD_PARTY_BANK_PAYMENT.getValue())) {
					cell.setCellValue("CTT");
					cell.setCellStyle(style);
				}
				if (ascTransactionsFilter.getChannelType().equals(OneFinConstants.SoftSpaceChanelType.SPENDING_CHANNEL_TYPE_CREDIT_DEBIT_CARD.getValue()) || ascTransactionsFilter.getChannelType().equals(OneFinConstants.SoftSpaceChanelType.SPENDING_CHANNEL_TYPE_CREDIT_DEBIT_CARD_INT.getValue())) {
					cell.setCellValue("CTT");
					cell.setCellStyle(style);
				}

				cell = row0.createCell(COLUMN_INDEX_CARD_TYPE);
				if (ascTransactionsFilter.getChannelType().equals(OneFinConstants.SoftSpaceChanelType.SPENDING_CHANNEL_TYPE_LOCAL_DEBIT.getValue())) {
					cell.setCellValue("ATM");
					cell.setCellStyle(style);
				}
				if (ascTransactionsFilter.getChannelType().equals(OneFinConstants.SoftSpaceChanelType.SPENDING_CHANNEL_TYPE_THIRD_PARTY_BANK_PAYMENT.getValue())) {
					cell.setCellValue("VietQR");
					cell.setCellStyle(style);
				}
				if (ascTransactionsFilter.getChannelType().equals(OneFinConstants.SoftSpaceChanelType.SPENDING_CHANNEL_TYPE_CREDIT_DEBIT_CARD.getValue()) || ascTransactionsFilter.getChannelType().equals(OneFinConstants.SoftSpaceChanelType.SPENDING_CHANNEL_TYPE_CREDIT_DEBIT_CARD_INT.getValue())) {
					cell.setCellValue("Visa/Master/JCB");
					cell.setCellStyle(style);
				}

				cell = row0.createCell(COLUMN_INDEX_ACCOUNT_NUMBER);
				cell.setCellValue(ascTransactionsFilter.getAccountNo());
				cell.setCellStyle(style);

				cell = row0.createCell(COLUMN_INDEX_ACCOUNT_BANK_NAME);
				cell.setCellValue(ascTransactionsFilter.getBankName().split("_")[0]);
				cell.setCellStyle(style);
				rowIndex++;
				order++;
			}

		}

		try (FileOutputStream fos = new FileOutputStream(templateFile); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
			workbook.write(bos);
			fos.write(bos.toByteArray());
		} finally {
			workbook.close();
			workbook.dispose();
		}

		return templateFile;
	}

	private CellStyle createCellDateType(Workbook workbook, boolean isDate, boolean isNum) {
		CellStyle cellStyle = workbook.createCellStyle();
		CreationHelper createHelper = workbook.getCreationHelper();
		if (isDate) {
			cellStyle.setDataFormat(createHelper.createDataFormat().getFormat(CELL_DATE_FORMAT));
		}
		if (isNum) {
			cellStyle.setDataFormat(HSSFDataFormat.getBuiltinFormat("#,##0"));
		}
		cellStyle.setBorderBottom(BorderStyle.THIN);
		cellStyle.setBorderRight(BorderStyle.THIN);
		cellStyle.setBorderTop(BorderStyle.THIN);
		cellStyle.setBorderLeft(BorderStyle.THIN);
		return cellStyle;
	}

	private File getTemplate(AscSchool school, DateTime currentTime) {
		byte[] result = minioService.getFile(env.getProperty("minio.ewalletCommonFileBucket"), "/TEMPLATE/SendEmail2SchoolDaily.xlsx");
		File outputFile = new File(System.getProperty("java.io.tmpdir") + "/" + dateTimeHelper.parseDate2String(currentTime.toLocalDateTime().toDate(), OneFinConstants.DATE_FORMAT_dd_MM_yyyy) + "_BC_Chi_Tiet_GD.xlsx");
		try {
			try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
				outputStream.write(result);
			}
			return outputFile;
		} catch (Exception e) {
			LOGGER.error("{}{}", LOG_ASC_SEND_EMAIL_DAILY, e);
		}
		return null;
	}

	private File getTemplateByBankAccount(AscSchool school, DateTime currentTime) {
		byte[] result = minioService.getFile(env.getProperty("minio.ewalletCommonFileBucket"), "/TEMPLATE/SendEmail2SchoolDaily_subTrans.xlsx");
		File outputFile = new File(System.getProperty("java.io.tmpdir") + "/" + dateTimeHelper.parseDate2String(currentTime.toLocalDateTime().toDate(), OneFinConstants.DATE_FORMAT_dd_MM_yyyy) + "_BC_Chi_Tiet_GD.xlsx");
		try {
			try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
				outputStream.write(result);
			}
			return outputFile;
		} catch (Exception e) {
			LOGGER.error("{}{}", LOG_ASC_SEND_EMAIL_DAILY, e);
		}
		return null;
	}

	private boolean checkIsHoliday(Date date) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);

		Holiday holiday = holidayRepo.findByScheduleDate(calendar.getTime());
		if (holiday.getStatus().equals(OneFinConstants.HolidayStatus.HOLIDAY.getValue())) {
			return true;
		}
		return false;
	}

	// Get Workbook
	public static Workbook getWorkbook(InputStream inputStream, String excelFilePath) throws IOException {
		Workbook workbook = null;
		if (excelFilePath.endsWith("xlsx")) {
			workbook = new XSSFWorkbook(inputStream);
		} else if (excelFilePath.endsWith("xls")) {
			workbook = new HSSFWorkbook(inputStream);
		} else {
			throw new IllegalArgumentException("The specified file is not Excel file");
		}

		return workbook;
	}

	public List<AscTransactionDetailsFiltered> searchChildAscTrans(String search) {
		List<AscTransaction> ascTrans;
		List<AscTransactionDetailsFiltered> ascTransView = new ArrayList<>();
		Node rootNode = new RSQLParser().parse(search);
		Specification<AscTransaction> spec = rootNode.accept(new CustomRsqlVisitor<>());
		ascTrans = ascTransactionRepository.findAll(spec);
		List<Object[]> ascTransWithParent = ascTransactionRepository.findAllWithParentTransaction(ascTrans);
		List<AscTransactionDetailsFiltered> finalAscTransView = ascTransView;
		ascTrans.stream().forEach(entity -> {
			//LOGGER.info("Process bank-account: {}", entity.getRequestId());
			Object[] ascTransAndParent = ascTransWithParent.stream().filter((o) -> {
				AscTransaction item = (AscTransaction) o[0];
				return entity.getId().equals(item.getId());
			}).findFirst().orElse(null);
			if (ascTransAndParent != null) {
				AscTransactionDetailsFiltered dto = modelMapper.map(ascTransAndParent[0], AscTransactionDetailsFiltered.class);
				if (ascTransAndParent[1] != null) {
					modelMapper.map(ascTransAndParent[1], dto);
				}
				finalAscTransView.add(dto);
			}
		});
		return finalAscTransView;
	}

}
