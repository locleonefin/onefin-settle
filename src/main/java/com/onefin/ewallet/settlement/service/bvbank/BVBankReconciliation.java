package com.onefin.ewallet.settlement.service.bvbank;


import com.onefin.ewallet.common.base.constants.BankConstants;
import com.onefin.ewallet.common.base.constants.OneFinConstants;
import com.onefin.ewallet.common.base.errorhandler.RuntimeInternalServerException;
import com.onefin.ewallet.common.base.service.RestTemplateHelper;
import com.onefin.ewallet.common.domain.bank.vietin.VietinNotifyTrans;
import com.onefin.ewallet.common.domain.bank.vietin.VietinNotifyTransTable;
import com.onefin.ewallet.common.domain.constants.DomainConstants;
import com.onefin.ewallet.common.domain.holiday.Holiday;
import com.onefin.ewallet.common.domain.settlement.SettlementTransaction;
import com.onefin.ewallet.common.quartz.entity.SchedulerJobInfo;
import com.onefin.ewallet.common.utility.date.DateTimeHelper;
import com.onefin.ewallet.common.utility.dto.EmailDto;
import com.onefin.ewallet.common.utility.file.ExcelHelper;
import com.onefin.ewallet.settlement.common.SettleConstants;
import com.onefin.ewallet.settlement.common.SettleHelper;
import com.onefin.ewallet.settlement.config.SFTPBvbVirtualAcctIntegration;
import com.onefin.ewallet.settlement.controller.SettleJobController;
import com.onefin.ewallet.settlement.dto.bvb.ReconciliationDto;
import com.onefin.ewallet.settlement.dto.bvb.ReconciliationDtoExport;
import com.onefin.ewallet.settlement.dto.bvb.ReconciliationMonthlyDetailDto;
import com.onefin.ewallet.settlement.dto.bvb.ReconciliationProcessDto;
import com.onefin.ewallet.settlement.job.BVBankReconciliationComponent;
import com.onefin.ewallet.settlement.job.BVBankTransactionListExportJob;
import com.onefin.ewallet.settlement.repository.HolidayRepo;
import com.onefin.ewallet.settlement.repository.SettleTransRepository;
import com.onefin.ewallet.settlement.repository.VietinNotifyTransTableRepo;
import com.onefin.ewallet.settlement.service.ConfigLoader;
import com.onefin.ewallet.settlement.service.MinioService;
import com.onefin.ewallet.settlement.service.SettleService;
import com.opencsv.bean.CsvToBeanBuilder;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.joda.time.DateTime;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import org.springframework.stereotype.Service;
import org.apache.commons.io.FilenameUtils;

import java.io.*;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class BVBankReconciliation {

//	private final static Logger LOGGER = LoggerFactory.getLogger(BVBankReconciliation.class);

	private static final org.apache.logging.log4j.Logger LOGGER = LogManager.getLogger(BVBankReconciliation.class);

	private static final String LOG_BVBANK_RECONCILIATION_DAILY = "BVBANK RECONCILIATION DAILY - ";

	private static final String BVBANK_TRANSACTIONS_EXPORT_DAILY = "BVBANK TRANSACTIONS EXPORT DAILY - ";

	@Autowired
	private VietinNotifyTransTableRepo vietinNotifyTransTableRepo;

	@Autowired
	private ModelMapper modelMapper;

	@Autowired
	private ExcelHelper excelHelper;

	@Autowired
	private DateTimeHelper dateTimeHelper;

	@Autowired
	private MinioService minioService;

	@Autowired
	private Environment env;

	@Autowired
	private ConfigLoader configLoader;

	@Autowired
	@Qualifier("SftpBvbVirtualAcctRemoteFileTemplate")
	private SftpRemoteFileTemplate sftpBvbFileTemplate;

	@Autowired
	private SFTPBvbVirtualAcctIntegration.UploadBvbVirtualAcctGateway gateway;

	@Value("${batchUpdate.size}")
	private int batchSize;

	@Autowired
	private RestTemplateHelper restTemplateHelper;

	@Autowired
	private HolidayRepo holidayRepo;
	@Autowired
	private SettleTransRepository settleRepo;
	@Autowired
	private SettleHelper settleHelper;

	@Autowired
	private SettleService commonSettleService;

	@Autowired
	private SettleJobController settleJobController;

	public boolean compareReconciliation(ReconciliationDto reconciliationDto,
										 VietinNotifyTransTable vietinNotifyTransTable) throws ParseException {
		try {
			Date reconciliationDate
					= dateTimeHelper.parseDate2(
					reconciliationDto.getTransactionDate(),
					SettleConstants.BVB_DATE_STRING_HEADER_FORMAT
			);

//			LOGGER.info("Compare date: {} \n {}", reconciliationDate, vietinNotifyTransTable.getTransactionDate());

			if (vietinNotifyTransTable.getMsgType().equals("I")) {
				if (!vietinNotifyTransTable.getTrnRefNo().equals(reconciliationDto.getTrnRefNo())) {
//					LOGGER.info("virtual trans id miss match");
					return false;
				}
			} else if (vietinNotifyTransTable.getMsgType().equals("B")) {
				if (!vietinNotifyTransTable.getTraceId().equals(reconciliationDto.getTrnRefNo())) {
//					LOGGER.info("virtual trans id miss match");
					return false;
				}

				if (reconciliationDate.compareTo(vietinNotifyTransTable.getTransactionDate()) != 0) {
//				LOGGER.info("Transaction date miss match");
					return false;
				}
			}

//			LOGGER.info("Compare amount: {} \n {}", reconciliationDto.getAmount(),
//					BigDecimal.valueOf(Long.parseLong(vietinNotifyTransTable.getAmount())));
			if (!reconciliationDto.getAmount().equals(
					BigDecimal.valueOf(Long.parseLong(vietinNotifyTransTable.getAmount())))) {
//				LOGGER.info("Amount miss match");
				return false;
			}

//			LOGGER.info("Compare getRelatedAccount: {} \n {}", reconciliationDto.getRelatedAccount(),
//					vietinNotifyTransTable.getRecvVirtualAcctId());
			if (!reconciliationDto.getRelatedAccount().equals(vietinNotifyTransTable.getRecvVirtualAcctId())) {
//				LOGGER.info("virtual acct miss match");
				return false;
			}

			if (!reconciliationDto.getExternalRefNo().equals(vietinNotifyTransTable.getTransId())) {
//				LOGGER.info("virtual acct miss match");
				return false;
			}

//			LOGGER.info("virtual trans id matched");
			return true;
		} catch (Exception e) {
			LOGGER.error("Error occurred while checking trans ", e);
		}
		return false;
	}

	private boolean reconciliationProcess(
			Queue<ReconciliationDto> queueReconciliation,
			Queue<VietinNotifyTransTable> queueVietinNotifyTransTable,
			List<ReconciliationDto> reconciliationDtoList,
			List<VietinNotifyTransTable> vietinNotifyTransTableList) {
		try {
			int numThread = (int) reconciliationDtoList.size() / batchSize;
			numThread = Math.max(numThread, 1);
			ExecutorService executor = Executors.newFixedThreadPool(numThread);
			final AtomicInteger sublist = new AtomicInteger();
			AtomicInteger count_row = new AtomicInteger();
			CompletableFuture[] futures = reconciliationDtoList.stream()
					.collect(Collectors.groupingBy(t -> sublist.getAndIncrement() / batchSize))
					.values()
					.stream()
					.map(ul -> CompletableFuture.runAsync(() -> {
						IntStream.range(0, ul.size()).forEach(index -> {
							int currentIndex = 0;
							currentIndex = count_row.addAndGet(1);
//						LOGGER.info("currentIndex: {}", currentIndex);
							vietinNotifyTransTableList.parallelStream().forEach(
									noti -> {
										try {
											if (getReconciliationString(noti).equals(
													ul.get(index).getReconciliationString())) {
												if (!queueReconciliation.contains(ul.get(index))) {
													queueReconciliation.add(ul.get(index));
												}
												if (!queueVietinNotifyTransTable.contains(noti)) {
													queueVietinNotifyTransTable.add(noti);
												}
											}
										} catch (Exception ex) {
											LOGGER.error(ex.getMessage(), ex);
										}
									}
							);
						});
					}, executor)).toArray(CompletableFuture[]::new);
			CompletableFuture<Void> run = CompletableFuture.allOf(futures);
			run.get();
			LOGGER.info("count_row: {}", count_row.get());
			LOGGER.info("vietinNotifyTransTableList size: {}", vietinNotifyTransTableList.size());
			LOGGER.info("queueVietinNotifyTransTable size: {}", queueVietinNotifyTransTable.size());
			LOGGER.info("queueReconciliation size: {}", queueReconciliation.size());
			LOGGER.info("reconciliationDtoList size: {}", reconciliationDtoList.size());
			if ((queueReconciliation.size() == reconciliationDtoList.size())
					&& (queueVietinNotifyTransTable.size() == vietinNotifyTransTableList.size())) {
				LOGGER.info("reconciliation complete successfully");
				return true;
			} else {
				LOGGER.info("reconciliation failed");
				return false;
			}
		} catch (Exception e) {
			LOGGER.error("Error occurred reconciliation failed", e);
			return false;
		}
	}

	public String getReconciliationString(VietinNotifyTransTable e) {
		if (e.getMsgType().equals("I")) {
			return e.getTrnRefNo() + "|" + e.getAmount() + "|" + e.getRecvVirtualAcctId() + "|" + e.getTransId();
		} else if (e.getMsgType().equals("B")) {
			return e.getTraceId()
//					+ dateTimeHelper.parseDate2String(e.getTransactionDate(),
//					SettleConstants.BVB_DATE_STRING_HEADER_FORMAT)
					+ "|" + e.getAmount() + "|" + e.getRecvVirtualAcctId() + "|" + e.getTransId();
		} else {
			throw new RuntimeInternalServerException("Unknown message type for reconciliation");
		}
	}

	public ReconciliationProcessDto compareList(List<ReconciliationDto> reconciliationDtoList,
												Date executeDate,
												Date dateFile, String fileTitle, boolean isGetByte) throws IOException {

		Queue<ReconciliationDto> queueReconciliation
				= new ConcurrentLinkedQueue<ReconciliationDto>();
		Queue<VietinNotifyTransTable> queueVietinNotifyTransTable
				= new ConcurrentLinkedQueue<VietinNotifyTransTable>();

		Date dateFileStart = dateTimeHelper.getBeginingOfDate(dateFile);
		Date dateFileEnd = dateTimeHelper.getEndOfDate(dateFile);
		LOGGER.info("date file from {} to {}", dateFileStart, dateFileEnd);
		List<VietinNotifyTransTable> vietinNotifyTransTableList
				= vietinNotifyTransTableRepo.findByFromDateToDate(dateFileStart,
				dateFileEnd, OneFinConstants.BankListQrService.VCCB.getBankCode());
//		LOGGER.info("vietinNotifyTransTableList");
//		vietinNotifyTransTableList.forEach(e -> {
//			LOGGER.log(Level.getLevel("INFOWT"), "vietinNotifyTransTableList::"
//					+ getReconciliationString(e));
//		});
		LOGGER.info("vietinNotifyTransTableList size: {}", vietinNotifyTransTableList.size());

		boolean isSuccess = reconciliationProcess(
				queueReconciliation,
				queueVietinNotifyTransTable,
				reconciliationDtoList,
				vietinNotifyTransTableList);

		// Reconciliation failed
		if (!isSuccess) {
			InputStream templateInputStream = null;
			Workbook workbook = null;
			try {
				ClassLoader classLoader = getClass().getClassLoader();
				templateInputStream =
						Files.newInputStream(Paths.get(configLoader.getBvbankReconciliationTemplate()));

				// If the template file isn't found in the resources folder, you can't proceed
				if (templateInputStream == null) {
					throw new FileNotFoundException("Template file not found in resources folder");
				}

				workbook = new XSSFWorkbook(templateInputStream);
				// get the sheet from workbook
				Sheet sheet = workbook.getSheetAt(0);
				int rowIndex = 6; // start from index 6
				Row rowData;
				Cell cellData;
				CellRangeAddress mergedRegion;

				// Optionally, set some styling for the merged cell (e.g., alignment, border, etc.)
				CellStyle style = excelHelper.styleForSettleExcel(sheet.getWorkbook());

				// Date info
				String reconciliationDateTitle
						= dateTimeHelper.parseDate2String(dateFile,
						OneFinConstants.DATE_FORMAT_yyyyMMdd);
				String reconciliationDate
						= dateTimeHelper.parseDate2String(executeDate,
						DomainConstants.DATE_FORMAT_TRANS);
				// Set date value
				rowData = (sheet.getRow(1) == null)
						? sheet.createRow(1) : sheet.getRow(1);
				cellData = rowData.createCell(4);
				cellData.setCellValue(reconciliationDateTitle);
				cellData.setCellStyle(style);
				rowData = (sheet.getRow(2) == null)
						? sheet.createRow(2) : sheet.getRow(2);
				cellData = rowData.createCell(4);
				cellData.setCellValue(reconciliationDate);
				cellData.setCellStyle(style);

				// Create a row for the header
				Row headerRow = (sheet.getRow(rowIndex) == null)
						? sheet.createRow(rowIndex) : sheet.getRow(rowIndex);
				for (SettleConstants.BVBankReconciliationField e:
						SettleConstants.BVBankReconciliationField.stream().collect(Collectors.toList())) {
					cellData = headerRow.createCell(e.getIndex()+2);
					cellData.setCellValue(e.getField());
					cellData.setCellStyle(style);
				}

				rowIndex = rowIndex + 1;
				int firstRowIndex = rowIndex;
				int lastRowIndex = rowIndex;
				int stt = 0;

//				LOGGER.info("ReconciliationDto no t found list-------------");
				List<ReconciliationDto> reconciliationDtoListMismatch
						= reconciliationDtoList.parallelStream()
						.filter(e -> !queueReconciliation.contains(e))
						.collect(Collectors.toList());
				for (ReconciliationDto e : reconciliationDtoListMismatch) {
					stt += 1;
					// Create a row for the header
					setCellFromReconciliationDto(
							e, rowData, cellData, sheet, rowIndex, style, stt,0
					);
					rowIndex = rowIndex + 1;
					lastRowIndex = rowIndex - 1;
				}

				if (lastRowIndex > firstRowIndex) {
					mergedRegion = new CellRangeAddress(firstRowIndex, lastRowIndex, 0, 0);
					rowData = sheet.getRow(firstRowIndex);
					cellData = rowData.createCell(0);
					cellData.setCellValue("Bvbank mismatch");
					sheet.addMergedRegion(mergedRegion);
					// Apply the cell style to all cells in the merged region
					for (int row = mergedRegion.getFirstRow(); row <= mergedRegion.getLastRow(); row++) {
						Row currentRow = sheet.getRow(row);
						if (currentRow == null) {
							currentRow = sheet.createRow(row);
						}
						for (int col = mergedRegion.getFirstColumn(); col <= mergedRegion.getLastColumn(); col++) {
							cellData = currentRow.getCell(col);
							if (cellData == null) {
								cellData = currentRow.createCell(col);
							}
							cellData.setCellStyle(style);
						}
					}
				} else {
					rowData = (sheet.getRow(firstRowIndex) == null)
							? sheet.createRow(firstRowIndex) : sheet.getRow(firstRowIndex);
					cellData = rowData.createCell(0);
					cellData.setCellValue("Bvbank mismatch");
					cellData.setCellStyle(style);
				}

				firstRowIndex = rowIndex;
				// Set the value for the merged cell
//				LOGGER.info("vietinNotifyTransTableList not found list-------------");
				List<VietinNotifyTransTable> vietinNotifyTransTableListMisMatch
						= vietinNotifyTransTableList.parallelStream()
						.filter(e -> !queueVietinNotifyTransTable.contains(e))
						.collect(Collectors.toList());
				stt = 0;
				for (VietinNotifyTransTable e : vietinNotifyTransTableListMisMatch) {
					stt += 1;
					setCellFromVietinNotifyTransTable(
							e,rowData, cellData, sheet, rowIndex, style, stt, 0
					);
					rowIndex = rowIndex + 1;
					lastRowIndex = rowIndex - 1;
				}

				if (lastRowIndex > firstRowIndex) {
					mergedRegion = new CellRangeAddress(firstRowIndex, lastRowIndex, 0, 0);
					rowData = sheet.getRow(firstRowIndex);
					cellData = rowData.createCell(0);
					cellData.setCellValue("onefin mismatch");
					//	Auto-size the columns for better readability (optional)
					for (int i = 0; i < SettleConstants.BVBankReconciliationField.stream().count(); i++) {
						sheet.autoSizeColumn(i);
					}
					sheet.addMergedRegion(mergedRegion);
					// Apply the cell style to all cells in the merged region
					for (int row = mergedRegion.getFirstRow(); row <= mergedRegion.getLastRow(); row++) {
						Row currentRow = sheet.getRow(row);
						if (currentRow == null) {
							currentRow = sheet.createRow(row);
						}
						for (int col = mergedRegion.getFirstColumn(); col <= mergedRegion.getLastColumn(); col++) {
							cellData = currentRow.getCell(col);
							if (cellData == null) {
								cellData = currentRow.createCell(col);
							}
							cellData.setCellStyle(style);
						}
					}
				} else {
					rowData = (sheet.getRow(firstRowIndex) == null)
							? sheet.createRow(firstRowIndex) : sheet.getRow(firstRowIndex);
					cellData = rowData.createCell(0);
					cellData.setCellValue("onefin mismatch");
					cellData.setCellStyle(style);
					//	Auto-size the columns for better readability (optional)
					for (int i = 0; i < SettleConstants.BVBankReconciliationField.stream().count(); i++) {
						sheet.autoSizeColumn(i);
					}
				}


				// Convert the Workbook to a byte array
				ByteArrayOutputStream outputStreamXslx = new ByteArrayOutputStream();
				workbook.write(outputStreamXslx);

				String reconciliationFolder
						= dateTimeHelper.parseDate2String(executeDate,
						SettleConstants.BVB_DATE_STRING_FILE_TITLE_FORMAT);
				// Get the file extension
				String filenameSaved = FilenameUtils.removeExtension(fileTitle) + "_" + "RECONCILIATION" + ".xlsx";

				LOGGER.info("File saved: {}", filenameSaved);
				String fileLocation = OneFinConstants.PARTNER_BVBANK + "/EMAIL_RECONCILIATION_REPORT/"
						+ reconciliationFolder + OneFinConstants.SLASH + filenameSaved;

				minioService.uploadByte(env.getProperty("minio.bvbReconciliationBucket"),
						fileLocation,
						outputStreamXslx.toByteArray(),
						"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

				ReconciliationProcessDto reconciliationProcessDto = new ReconciliationProcessDto();
				reconciliationProcessDto.setReconciliationDateTitle(reconciliationDateTitle);
				reconciliationProcessDto.setFileLocation(fileLocation);
				reconciliationProcessDto.setFileTitle(fileTitle);
				reconciliationProcessDto.setReconciliationDate(reconciliationDate);
				reconciliationProcessDto.setMissMatch(true);
				if (isGetByte){
					reconciliationProcessDto.setOutputStreamXslx(outputStreamXslx);
				}
				return reconciliationProcessDto;

			} catch (Exception e) {
				LOGGER.error("Cannot write excel file", e);
				ReconciliationProcessDto reconciliationProcessDto = new ReconciliationProcessDto();
				reconciliationProcessDto.setFileTitle(fileTitle);
				reconciliationProcessDto.setError(true);
				return reconciliationProcessDto;
			} finally {
				if (templateInputStream != null) {
					templateInputStream.close();
				}
				if (workbook != null) {
					workbook.close();
				}
			}
		}
		ReconciliationProcessDto reconciliationProcessDto = new ReconciliationProcessDto();
		reconciliationProcessDto.setFileTitle(fileTitle);
		return reconciliationProcessDto;
	}

	public ReconciliationProcessDto buildBVBTransList(List<ReconciliationDto> reconciliationDtoList,
												Date currentTime,
												Date dateFile, String fileTitle,
													  boolean isGetByte) throws IOException {

		InputStream templateInputStream = null;
		Workbook workbook = null;
		try {
			ClassLoader classLoader = getClass().getClassLoader();
			templateInputStream =
					Files.newInputStream(Paths.get(configLoader.getBvbankReconciliationTemplateExport()));

			workbook = new XSSFWorkbook(templateInputStream);
			// get the sheet from workbook
			Sheet sheet = workbook.getSheetAt(0);
			int rowIndex = 6; // start from index 6
			Row rowData;
			Cell cellData;

			// Optionally, set some styling for the merged cell (e.g., alignment, border, etc.)
			CellStyle style = excelHelper.styleForSettleExcel(sheet.getWorkbook());

			// Date info
			String reconciliationDateTitle
					= dateTimeHelper.parseDate2String(dateFile,
					OneFinConstants.DATE_FORMAT_yyyyMMdd);

			String reconciliationDate
					= dateTimeHelper.parseDate2String(currentTime,
					DomainConstants.DATE_FORMAT_TRANS);

			// Set date value
			rowData = (sheet.getRow(1) == null)
					? sheet.createRow(1) : sheet.getRow(1);
			cellData = rowData.createCell(4);
			cellData.setCellValue(reconciliationDateTitle);
			cellData.setCellStyle(style);
			rowData = (sheet.getRow(2) == null)
					? sheet.createRow(2) : sheet.getRow(2);
			cellData = rowData.createCell(4);
			cellData.setCellValue(reconciliationDateTitle);
			cellData.setCellStyle(style);

			// Create a row for the header
			Row headerRow = (sheet.getRow(rowIndex) == null)
					? sheet.createRow(rowIndex) : sheet.getRow(rowIndex);
			for (SettleConstants.BVBankReconciliationField e:
					SettleConstants.BVBankReconciliationField.stream().collect(Collectors.toList())) {
				cellData = headerRow.createCell(e.getIndex()+1);
				cellData.setCellValue(e.getField());
				cellData.setCellStyle(style);
			}

			rowIndex = rowIndex + 1;
			int startRow = rowIndex;
			int stt = 0;
			for (ReconciliationDto e : reconciliationDtoList) {
				stt += 1;
				// Create a row for the header
				setCellFromReconciliationDto(
						e, rowData, cellData, sheet, rowIndex, style, stt,-1
				);
				rowIndex = rowIndex + 1;
			}
			int endRow = rowIndex;
			// Set Total Transaction amount
			// Formula Evaluator
			FormulaEvaluator  formulaEvaluator =
					workbook.getCreationHelper().createFormulaEvaluator();
			String formulaString = String.format("SUM(G%d:G%d)",startRow+1,endRow);
			rowData =
					(sheet.getRow(3) == null) ?
							sheet.createRow(3) : sheet.getRow(3);
			cellData = rowData.createCell(4);
			cellData.setCellValue(stt);
			cellData.setCellStyle(style);
			rowData =
					(sheet.getRow(4) == null) ?
							sheet.createRow(4) : sheet.getRow(4);
			cellData = rowData.createCell(4);

			cellData.setCellFormula(formulaString);
			formulaEvaluator.evaluateFormulaCell(cellData);
			cellData.setCellStyle(style);



			// Convert the Workbook to a byte array
			ByteArrayOutputStream outputStreamXslx = new ByteArrayOutputStream();
			workbook.write(outputStreamXslx);

			// Get the file extension
			String filenameSaved = FilenameUtils.removeExtension(fileTitle) + "_" + "EXPORT" + ".xlsx";

			LOGGER.info("File saved: {}", filenameSaved);
			String MinioSavedFolder
					= dateTimeHelper.parseDate2String(currentTime,
					DomainConstants.DATE_FORMAT_TRANS3);
			String fileLocation = OneFinConstants.PARTNER_BVBANK + "/EMAIL_RECONCILIATION_EXPORT_BVB/"
				+ MinioSavedFolder + OneFinConstants.SLASH + filenameSaved;
			minioService.uploadByte(env.getProperty("minio.bvbReconciliationBucket"),
					fileLocation,
					outputStreamXslx.toByteArray(),
					"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

			ReconciliationProcessDto reconciliationProcessDto = new ReconciliationProcessDto();
			reconciliationProcessDto.setReconciliationDateTitle(reconciliationDateTitle);
			reconciliationProcessDto.setFileLocation(fileLocation);
			reconciliationProcessDto.setFileTitle(fileTitle);
			reconciliationProcessDto.setReconciliationDate(reconciliationDate);
			if (isGetByte){
				reconciliationProcessDto.setOutputStreamXslx(outputStreamXslx);
			}
			reconciliationProcessDto.setReconciliationDtos(reconciliationDtoList);
			return reconciliationProcessDto;
		} catch (Exception e) {
			LOGGER.error("Cannot write excel file", e);
			ReconciliationProcessDto reconciliationProcessDto = new ReconciliationProcessDto();
			reconciliationProcessDto.setFileTitle(fileTitle);
			reconciliationProcessDto.setError(true);
			return reconciliationProcessDto;
		} finally {
			if (templateInputStream != null) {
				templateInputStream.close();
			}
			if (workbook != null) {
				workbook.close();
			}
		}
	}



	private int setCellFromReconciliationDto(
			ReconciliationDto e,
			Row rowData,
			Cell cellData,
			Sheet sheet,
			Integer rowIndex,
			CellStyle style,
			int stt,
			int startColumnIndex
	){
		rowData = (sheet.getRow(rowIndex) == null)
				? sheet.createRow(rowIndex) : sheet.getRow(rowIndex);
		if (startColumnIndex < 0 ){
			cellData = (rowData.getCell(0) == null)
					? rowData.createCell(0) : rowData.getCell(0);
		}else{
			cellData = (rowData.getCell(startColumnIndex) == null)
					? rowData.createCell(startColumnIndex) : rowData.getCell(startColumnIndex);
			cellData = (rowData.getCell(startColumnIndex + 1) == null)
					? rowData.createCell(startColumnIndex + 1) : rowData.getCell(startColumnIndex + 1);
		}

		cellData.setCellValue(stt);
		cellData.setCellStyle(style);
		cellData = rowData.createCell(startColumnIndex + 2);
		cellData.setCellValue(e.getExternalRefNo());
		cellData.setCellStyle(style);
		cellData = rowData.createCell(startColumnIndex + 3);
		cellData.setCellValue(dateTimeHelper.parseDate2DateString(
				DomainConstants.DATE_FORMAT_TRANS9,
				DomainConstants.DATE_FORMAT_TRANS,
				e.getTransactionDate())
		);
		cellData.setCellStyle(style);
		cellData = rowData.createCell(startColumnIndex + 4);
		cellData.setCellValue(e.getPartnerCode());
		cellData.setCellStyle(style);
		cellData = rowData.createCell(startColumnIndex + 5);
		cellData.setCellValue(e.getRelatedAccount());
		cellData.setCellStyle(style);
		cellData = rowData.createCell(startColumnIndex + 6);
		cellData.setCellValue(e.getCreditAccount());
		cellData.setCellStyle(style);
		cellData = rowData.createCell(startColumnIndex + 7);
		cellData.setCellValue(e.getAmount().intValue());
		cellData.setCellStyle(style);
		cellData = rowData.createCell(startColumnIndex + 8);
		cellData.setCellValue(e.getCcy());
		cellData.setCellStyle(style);
		cellData = rowData.createCell(startColumnIndex + 9);
		cellData.setCellValue(e.getTrnRefNo());
		cellData.setCellStyle(style);
		cellData = rowData.createCell(startColumnIndex + 10);
		cellData.setCellValue(e.getNarrative());
		cellData.setCellStyle(style);
		cellData = rowData.createCell(startColumnIndex + 11);
		cellData.setCellValue(e.getClientUserId());
		cellData.setCellStyle(style);
		cellData = rowData.createCell(startColumnIndex + 12);
		cellData.setCellValue(e.getFcCoreRef());
		cellData.setCellStyle(style);
		cellData = rowData.createCell(startColumnIndex + 13);
		cellData.setCellValue(e.getNapasTraceId());
		cellData.setCellStyle(style);

		return e.getAmount().intValue();
	}



	private void setCellFromVietinNotifyTransTable(
			VietinNotifyTransTable e,
			Row rowData,
			Cell cellData,
			Sheet sheet,
			Integer rowIndex,
			CellStyle style,
			int stt,
			int startColumnIndex

	){
		ReconciliationDtoExport reconciliationDtoExport
				= modelMapper.map(e, ReconciliationDtoExport.class);
		rowData = (sheet.getRow(rowIndex) == null)
				? sheet.createRow(rowIndex) : sheet.getRow(rowIndex);
		if (startColumnIndex < 0 ){
			cellData = (rowData.getCell(0) == null)
					? rowData.createCell(0)
					: rowData.getCell(0);
		}else{
			cellData = (rowData.getCell(startColumnIndex) == null)
					? rowData.createCell(startColumnIndex)
					: rowData.getCell(startColumnIndex);
			cellData = (rowData.getCell(startColumnIndex + 1) == null)
					? rowData.createCell(startColumnIndex + 1)
					: rowData.getCell(startColumnIndex + 1);
		}
		cellData.setCellValue(stt);
		cellData.setCellStyle(style);
		cellData = rowData.createCell(startColumnIndex + 2);
		cellData.setCellValue(reconciliationDtoExport.getTransId());
		cellData.setCellStyle(style);
		cellData = rowData.createCell(startColumnIndex + 3);
		cellData.setCellValue(reconciliationDtoExport.getTxnInitDt());
		cellData.setCellStyle(style);
		cellData = rowData.createCell(startColumnIndex + 4);
		cellData.setCellValue(reconciliationDtoExport.getPartnerCode());
		cellData.setCellStyle(style);
		cellData = rowData.createCell(startColumnIndex + 5);
		cellData.setCellValue(reconciliationDtoExport.getRecvVirtualAcctId());
		cellData.setCellStyle(style);
		cellData = rowData.createCell(startColumnIndex + 6);
		cellData.setCellValue(reconciliationDtoExport.getCreditAccount());
		cellData.setCellStyle(style);
		cellData = rowData.createCell(startColumnIndex + 7);
		cellData.setCellValue(reconciliationDtoExport.getAmount());
		cellData.setCellStyle(style);
		cellData = rowData.createCell(startColumnIndex + 8);
		cellData.setCellValue(reconciliationDtoExport.getCurrencyCode());
		cellData.setCellStyle(style);
		cellData = rowData.createCell(startColumnIndex + 9);
		cellData.setCellValue(reconciliationDtoExport.getTrnRefNoExport());
		cellData.setCellStyle(style);
		cellData = rowData.createCell(startColumnIndex + 10);
		cellData.setCellValue(reconciliationDtoExport.getRemark());
		cellData.setCellStyle(style);
		cellData = rowData.createCell(startColumnIndex + 11);
		cellData.setCellValue(reconciliationDtoExport.getCustCode());
		cellData.setCellStyle(style);
		cellData = rowData.createCell(startColumnIndex + 12);
		cellData.setCellValue(e.getTrnRefNo());
		cellData.setCellStyle(style);
		cellData = rowData.createCell(startColumnIndex + 13);
		cellData.setCellValue(reconciliationDtoExport.getTraceId());
		cellData.setCellStyle(style);
		cellData = rowData.createCell(startColumnIndex + 14);
		cellData.setCellValue(reconciliationDtoExport.getMsgType());
		cellData.setCellStyle(style);

	}

	public <T> List<T> csvToBeanInputStream(InputStream f, Class<T> clazz) {

		CsvToBeanBuilder<T> beanBuilder =
				new CsvToBeanBuilder<>(
						new InputStreamReader(new BOMInputStream(f),
								StandardCharsets.UTF_8));
		beanBuilder.withType(clazz);
		// build methods returns a list of Beans
		return beanBuilder
				.withIgnoreLeadingWhiteSpace(true)
				.withSeparator('|')
				.build().parse();
	}

	public Map<String, Object> sendEmail(EmailDto data) {
		String url = configLoader.getUtilityUrl() + configLoader.getBvbankUrlEmail();
		LOGGER.info("== Send Email request {} - url: {}", data, url);
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Collections.singletonList(MediaType.ALL));
		headers.setContentType(MediaType.APPLICATION_JSON);
		HashMap<String, String> headersMap = new HashMap<>();
		for (String header : headers.keySet()) {
			headersMap.put(header, headers.getFirst(header));
		}
		HashMap<String, String> urlParameters = new HashMap<>();
		List<String> pathVariables = new ArrayList<>();
		ResponseEntity<Map<String, Object>> responseEntity = restTemplateHelper.post(url,
				MediaType.APPLICATION_JSON_VALUE, headersMap, pathVariables, urlParameters,
				null, data, new ParameterizedTypeReference<Map<String, Object>>() {
				});
		LOGGER.log(Level.getLevel("INFOWT"), "== Success receive response" + responseEntity.getBody());
		return responseEntity.getBody();
	}

	public ReconciliationProcessDto processReconciliation(String dateString, Date executeDate) {

		Date currentDate = dateTimeHelper.parseDate2(dateString,
				SettleConstants.BVB_DATE_STRING_FILE_TITLE_FORMAT);

		String fileSFTPName =
				env.getProperty("sftp.bvbank.virtualAcct.fileNamePrefix") + dateString + ".txt";

		String fileGet = configLoader.getSftpBvbankVirtualAcctDirectoryRemoteIn()
				+ fileSFTPName;

		AtomicReference<ReconciliationProcessDto> reconciliationProcessDtoAtomicReference =
				new AtomicReference<>();
		LOGGER.info("File get {}:", fileGet);
		try {
			boolean status = sftpBvbFileTemplate.get(fileGet
					, stream -> {
						List<ReconciliationDto> reconciliationDtoList = null;
						reconciliationDtoList =
								csvToBeanInputStream(stream, ReconciliationDto.class);
//						LOGGER.info("File get list Record");
//						reconciliationDtoList.forEach(
//								e -> {
//									LOGGER.log(Level.getLevel("INFOWT"), "reconciliationDtoList: {}", e.getReconciliationString());
//								}
//						);
						ReconciliationProcessDto fileContent =
								compareList(reconciliationDtoList, executeDate,
										currentDate, fileSFTPName,false);
						if (fileContent != null) {
							reconciliationProcessDtoAtomicReference.set(fileContent);
						} else {
							ReconciliationProcessDto e = new ReconciliationProcessDto();
							e.setFileTitle(fileSFTPName);
							reconciliationProcessDtoAtomicReference.set(e);
						}
					});
		} catch (Exception e) {
			LOGGER.error("Error occurred while processing", e);
			ReconciliationProcessDto reconciliationProcessDto = new ReconciliationProcessDto();
			reconciliationProcessDto.setFileTitle(fileSFTPName);
			reconciliationProcessDto.setError(true);
			return reconciliationProcessDto;
		}
		return reconciliationProcessDtoAtomicReference.get();
	}

	public ReconciliationProcessDto processBVBTransExport(String dateString, DateTime currentTime) {

		Date currentDate = dateTimeHelper.parseDate2(dateString,
				SettleConstants.BVB_DATE_STRING_FILE_TITLE_FORMAT);

		String fileSFTPName =
				env.getProperty("sftp.bvbank.virtualAcct.fileNamePrefix") + dateString + ".txt";

		String fileGet = configLoader.getSftpBvbankVirtualAcctDirectoryRemoteIn()
				+ fileSFTPName;

		AtomicReference<ReconciliationProcessDto> reconciliationProcessDtoAtomicReference =
				new AtomicReference<>();
		LOGGER.info("File get {}:", fileGet);
		try {
			boolean status = sftpBvbFileTemplate.get(fileGet
					, stream -> {
						List<ReconciliationDto> reconciliationDtoList = null;
						reconciliationDtoList =
								csvToBeanInputStream(stream, ReconciliationDto.class);
//						LOGGER.info("File get list Record");
//						reconciliationDtoList.forEach(
//								e -> {
//									LOGGER.log(Level.getLevel("INFOWT"), "reconciliationDtoList: {}", e.getReconciliationString());
//								}
//						);
						ReconciliationProcessDto fileContent =
								buildBVBTransList(reconciliationDtoList, currentTime.toDate(),
										currentDate, fileSFTPName,false);
						if (fileContent != null) {
							reconciliationProcessDtoAtomicReference.set(fileContent);
						} else {
							ReconciliationProcessDto e = new ReconciliationProcessDto();
							e.setFileTitle(fileSFTPName);
							reconciliationProcessDtoAtomicReference.set(e);
						}
					});
		} catch (Exception e) {
			LOGGER.error("Error occurred while processing", e);
			ReconciliationProcessDto reconciliationProcessDto = new ReconciliationProcessDto();
			reconciliationProcessDto.setFileTitle(fileSFTPName);
			reconciliationProcessDto.setError(true);
			return reconciliationProcessDto;
		}
		return reconciliationProcessDtoAtomicReference.get();
	}

	public List<ReconciliationDto> getBVBTransExportDateToDate(
			String dateString) {

		String fileSFTPName =
				env.getProperty("sftp.bvbank.virtualAcct.fileNameHourPrefix")
						+ dateString + ".txt";

		String fileGet = configLoader.getSftpBvbankVirtualAcctDirectoryRemoteIn()
				+ fileSFTPName;

		AtomicReference<List<ReconciliationDto>> reconciliationProcessDtoAtomicReference =
				new AtomicReference<>();
		LOGGER.info("File get {}:", fileGet);
		try {
			boolean status = sftpBvbFileTemplate.get(fileGet
					, stream -> {
						List<ReconciliationDto> reconciliationDtoList = null;
						reconciliationDtoList =
								csvToBeanInputStream(stream, ReconciliationDto.class);

						reconciliationProcessDtoAtomicReference.set(reconciliationDtoList);
					});
		} catch (Exception e) {
			return new ArrayList<>();
		}
		return reconciliationProcessDtoAtomicReference.get();
	}

	public List<ReconciliationDto> getBVBTransExportFullDateToDate(
			String dateString) {

		String fileSFTPName =
				env.getProperty("sftp.bvbank.virtualAcct.fileNamePrefix")
						+ dateString + ".txt";

		String fileGet = configLoader.getSftpBvbankVirtualAcctDirectoryRemoteIn()
				+ fileSFTPName;

		AtomicReference<List<ReconciliationDto>> reconciliationProcessDtoAtomicReference =
				new AtomicReference<>();
		LOGGER.info("File get {}:", fileGet);
		try {
			boolean status = sftpBvbFileTemplate.get(fileGet
					, stream -> {
						List<ReconciliationDto> reconciliationDtoList = null;
						reconciliationDtoList =
								csvToBeanInputStream(stream, ReconciliationDto.class);

						reconciliationProcessDtoAtomicReference.set(reconciliationDtoList);
					});
		} catch (Exception e) {
			LOGGER.error("Error occurred: ",e);
			return new ArrayList<>();
		}
		return reconciliationProcessDtoAtomicReference.get();
	}

	public void reconciliationExport(String fromDateString, String toDateString) {

		Date fromDate = dateTimeHelper.parseDate2(fromDateString,
				DomainConstants.DATE_FORMAT_TRANS5);

		Date toDate = dateTimeHelper.parseDate2(toDateString,
				DomainConstants.DATE_FORMAT_TRANS5);

		List<VietinNotifyTransTable> vietinNotifyTransTableListMgB
				= vietinNotifyTransTableRepo.findByFromDateToDateMsB(
						dateTimeHelper.getBeginingOfDate(fromDate),
				dateTimeHelper.getEndOfDate(toDate),
				OneFinConstants.BankListQrService.VCCB.getBankCode());

		List<VietinNotifyTransTable> vietinNotifyTransTableListMgI
				= vietinNotifyTransTableRepo.findByFromDateToDateMsI(
				dateTimeHelper.getBeginingOfDate(fromDate),
				dateTimeHelper.getEndOfDate(toDate),
				OneFinConstants.BankListQrService.VCCB.getBankCode());

		InputStream templateInputStream = null;
		Workbook workbook = null;

		try {
			ClassLoader classLoader = getClass().getClassLoader();
			templateInputStream =
					Files.newInputStream(Paths.get(configLoader.getBvbankReconciliationTemplateExport()));

			// If the template file isn't found in the resources folder, you can't proceed
			if (templateInputStream == null) {
				throw new FileNotFoundException("Template file not found in resources folder");
			}

			workbook = new XSSFWorkbook(templateInputStream);
			// get the sheet from workbook
			Sheet sheet = workbook.getSheetAt(0);
			int rowIndex = 6; // start from index 6
			Row rowData;
			Cell cellData;
			CellRangeAddress mergedRegion;


			// Optionally, set some styling for the merged cell (e.g., alignment, border, etc.)
			CellStyle style = excelHelper.styleForSettleExcel(sheet.getWorkbook());

			// Date info
			Date currentDate = dateTimeHelper.currentDate(OneFinConstants.HO_CHI_MINH_TIME_ZONE);

			// Set date value
			rowData =
					(sheet.getRow(1) == null) ?
							sheet.createRow(1) : sheet.getRow(1);
			cellData = rowData.createCell(4);
			cellData.setCellValue(fromDateString);
			cellData.setCellStyle(style);
			rowData =
					(sheet.getRow(2) == null) ?
							sheet.createRow(2) : sheet.getRow(2);
			cellData = rowData.createCell(4);
			cellData.setCellValue(toDateString);
			cellData.setCellStyle(style);

			// Create a row for the header
			Row headerRow = (sheet.getRow(rowIndex) == null) ? sheet.createRow(rowIndex) : sheet.getRow(rowIndex);
			for (SettleConstants.BVBankReconciliationField e:
					SettleConstants.BVBankReconciliationField.stream().collect(Collectors.toList())) {
				cellData = headerRow.createCell(e.getIndex()+1);
				cellData.setCellValue(e.getField());
				cellData.setCellStyle(style);
			}

			rowIndex = rowIndex + 1;
			int firstRowIndex = rowIndex;
			int lastRowIndex = rowIndex;
			int sst = 0;
			for (VietinNotifyTransTable e : vietinNotifyTransTableListMgB) {
				sst += 1;
				setCellFromVietinNotifyTransTable(
					e,rowData, cellData, sheet, rowIndex, style,sst,-1
				);

				rowIndex = rowIndex + 1;
				lastRowIndex = rowIndex - 1;
			}
			// Convert the Workbook to a byte array
			ByteArrayOutputStream outputStreamXslxB = new ByteArrayOutputStream();
			workbook.write(outputStreamXslxB);

			ByteArrayOutputStream outputStreamXslxI = new ByteArrayOutputStream();

			workbook.close();

			templateInputStream =
					Files.newInputStream(Paths.get(configLoader.getBvbankReconciliationTemplateExport()));


			workbook = new XSSFWorkbook(templateInputStream);
			// get the sheet from workbook
			sheet = workbook.getSheetAt(0);
			style = excelHelper.styleForSettleExcel(sheet.getWorkbook());
			rowIndex = 6; // start from index 6

			// Set date value
			rowData =
					(sheet.getRow(1) == null) ?
							sheet.createRow(1) : sheet.getRow(1);
			cellData = rowData.createCell(4);
			cellData.setCellValue(fromDateString);
			cellData.setCellStyle(style);
			rowData =
					(sheet.getRow(2) == null) ?
							sheet.createRow(2) : sheet.getRow(2);
			cellData = rowData.createCell(4);
			cellData.setCellValue(toDateString);
			cellData.setCellStyle(style);

			// Create a row for the header
			headerRow = (sheet.getRow(rowIndex) == null) ? sheet.createRow(rowIndex) : sheet.getRow(rowIndex);
			for (SettleConstants.BVBankReconciliationField e:
					SettleConstants.BVBankReconciliationField.stream().collect(Collectors.toList())) {
				cellData = headerRow.createCell(e.getIndex()+1);
				cellData.setCellValue(e.getField());
				cellData.setCellStyle(style);
			}

			rowIndex = rowIndex + 1;
			firstRowIndex = rowIndex;
			lastRowIndex = rowIndex;
			sst = 0;
			for (VietinNotifyTransTable e : vietinNotifyTransTableListMgI) {
				ReconciliationDtoExport reconciliationDtoExport
						= modelMapper.map(e, ReconciliationDtoExport.class);
				sst += 1;
				setCellFromVietinNotifyTransTable(
						e,rowData, cellData, sheet, rowIndex,  style, sst, -1
				);
				rowIndex = rowIndex + 1;
				lastRowIndex = rowIndex - 1;
			}
			workbook.write(outputStreamXslxI);

			// Get the file extension

			String fileNameMsgB = env.getProperty("sftp.bvbank.virtualAcct.fileNamePrefix")
					+ fromDateString+"_"+toDateString +"_MsgB_" + ".xlsx";
			String fileNameMsgI = env.getProperty("sftp.bvbank.virtualAcct.fileNamePrefix")
					+ fromDateString+"_"+toDateString +"_MsgI_" + ".xlsx";
			LOGGER.info("File saved: {}", fileNameMsgB);
			LOGGER.info("File saved: {}", fileNameMsgI);
			String fileLocationB = OneFinConstants.PARTNER_BVBANK + "/EMAIL_RECONCILIATION_EXPORT/"
					+ fromDateString+"_"+ toDateString + "/"+ fileNameMsgB;
			String fileLocationI = OneFinConstants.PARTNER_BVBANK + "/EMAIL_RECONCILIATION_EXPORT/"
					+ fromDateString+"_"+ toDateString + "/"+ fileNameMsgI;
			minioService.uploadByte(env.getProperty("minio.bvbReconciliationBucket"),
					fileLocationB,
					outputStreamXslxB.toByteArray(),
					"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
			minioService.uploadByte(env.getProperty("minio.bvbReconciliationBucket"),
					fileLocationI,
					outputStreamXslxI.toByteArray(),
					"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

		}catch (Exception ex){
			LOGGER.error("Error occurred",ex);
		}
	}

	public boolean checkIsBvbHoliday(Date date) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);

		Holiday holiday = holidayRepo.findByScheduleDate(calendar.getTime());
		if (holiday.getBvbankStatus().equals(OneFinConstants.HolidayStatus.HOLIDAY.getValue())) {
			return true;
		}
		return false;
	}


	public void executeManually(String executeDate,String emailSend,String emailCC) {
		List<Date> reconciliationDate = new ArrayList<>();
		DateTime currentTime = dateTimeHelper.parseDate(executeDate,
				OneFinConstants.HO_CHI_MINH_TIME_ZONE,
				OneFinConstants.DATE_FORMAT_ddMMyyyy
		);
		String currentTimeString = dateTimeHelper.parseDateString(currentTime,
				OneFinConstants.DATE_FORMAT_yyyyMMDD);

		if (checkIsBvbHoliday(currentTime.toLocalDateTime().toDate())) {
			LOGGER.info("{}Today is bvbank holiday, no task", LOG_BVBANK_RECONCILIATION_DAILY);
			return;
		}

		SettlementTransaction trans = settleRepo.findByPartnerAndDomainAndSettleDate(SettleConstants.PARTNER_BVBANK,
				SettleConstants.VIRTUAL_ACCT, currentTimeString);

		if (trans == null) {
			try {
				trans = (SettlementTransaction) settleHelper.createModelStructure(new SettlementTransaction());
				trans.getSettleKey().setDomain(SettleConstants.VIRTUAL_ACCT);
				trans.getSettleKey().setPartner(SettleConstants.PARTNER_BVBANK);
				trans.getSettleKey().setSettleDate(currentTimeString);
				trans.setStatus(SettleConstants.SETTLEMENT_PENDING_T0);
				commonSettleService.save(trans);
				LOGGER.info("{} Created Pending Trans Successfully {}", LOG_BVBANK_RECONCILIATION_DAILY, currentTimeString);
			} catch (Exception e) {
				LOGGER.error("Create TC transaction error", e);
				return;
			}
		}

		if (trans.getStatus().equals(SettleConstants.SETTLEMENT_SUCCESS)) {
			LOGGER.info("{} Settle for the day have already successful for day {}",
					LOG_BVBANK_RECONCILIATION_DAILY, currentTimeString);
			return;

		}

		DateTime previousTime = currentTime.minusDays(1);
		while (checkIsBvbHoliday(previousTime.toLocalDateTime().toDate())) {
			reconciliationDate.add(previousTime.toLocalDateTime().toDate());
			previousTime = previousTime.minusDays(1);
		}
		reconciliationDate.add(previousTime.toDate());

		LOGGER.info("{}Reconciliation date list: {}", LOG_BVBANK_RECONCILIATION_DAILY, reconciliationDate);
		try {
			List<String> listDate = reconciliationDate.stream().map(
					e -> {
						String dateString = dateTimeHelper.parseDate2String(
								e,
								SettleConstants.BVB_DATE_STRING_FILE_TITLE_FORMAT
						);
						return env.getProperty("sftp.bvbank.virtualAcct.fileNamePrefix") + dateString + ".txt";
					}
			).collect(Collectors.toList());
			trans.setFile(listDate);
			trans.setStatus(SettleConstants.SETTLEMENT_PROCESSING);
			commonSettleService.save(trans);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		boolean isSuccess = sendReconciliationEmail(reconciliationDate, currentTime,emailSend, emailCC);
		if (isSuccess) {
			try {
				trans.setStatus(SettleConstants.SETTLEMENT_SUCCESS);
				commonSettleService.save(trans);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			// pause job
			// Settle completed => paused job
			SchedulerJobInfo jobTmp = new SchedulerJobInfo();
			jobTmp.setJobClass(BVBankReconciliationComponent.class.getName());
			settleJobController.pauseJob(jobTmp);
		}
	}

	public void executeBVBTransExportManually(String executeDate, String emailSend,
											  String emailCC) throws IOException {
		List<Date> reconciliationDate = new ArrayList<>();
		DateTime currentTime = dateTimeHelper.parseDate(executeDate,
				OneFinConstants.HO_CHI_MINH_TIME_ZONE,
				OneFinConstants.DATE_FORMAT_ddMMyyyy
		);
		String currentTimeString = dateTimeHelper.parseDateString(currentTime, OneFinConstants.DATE_FORMAT_yyyyMMDD);

		if (checkIsBvbHoliday(currentTime.toLocalDateTime().toDate())) {
			LOGGER.info("{}Today is bvbank holiday, no task", BVBANK_TRANSACTIONS_EXPORT_DAILY);
			return;
		}

		SettlementTransaction trans = settleRepo.findByPartnerAndDomainAndSettleDate(
				SettleConstants.PARTNER_BVBANK,
				SettleConstants.BVB_TRANS_EXPORT,
				currentTimeString);

		if (trans == null) {
			try {
				trans = (SettlementTransaction) settleHelper.createModelStructure(new SettlementTransaction());
				trans.getSettleKey().setDomain(SettleConstants.BVB_TRANS_EXPORT);
				trans.getSettleKey().setPartner(SettleConstants.PARTNER_BVBANK);
				trans.getSettleKey().setSettleDate(currentTimeString);
				trans.setStatus(SettleConstants.SETTLEMENT_PENDING_T0);
				commonSettleService.save(trans);
				LOGGER.info("{} Created Pending Trans Successfully {}", BVBANK_TRANSACTIONS_EXPORT_DAILY, currentTimeString);
			} catch (Exception e) {
				LOGGER.error("Create TC transaction error", e);
				return;
			}
		}

		if (trans.getStatus().equals(SettleConstants.SETTLEMENT_SUCCESS)) {
			LOGGER.info("{} Settle for the day have already successful for day {}",
					BVBANK_TRANSACTIONS_EXPORT_DAILY, currentTimeString);
			return;
		}

		DateTime previousTime = currentTime.minusDays(1);
		while (checkIsBvbHoliday(previousTime.toLocalDateTime().toDate())) {
			reconciliationDate.add(previousTime.toLocalDateTime().toDate());
			previousTime = previousTime.minusDays(1);
		}
		reconciliationDate.add(previousTime.toDate());
		LOGGER.info("{}Reconciliation date list: {}", BVBANK_TRANSACTIONS_EXPORT_DAILY, reconciliationDate);
		try {
			List<String> listDate = reconciliationDate.stream().map(
					e -> {
						String dateString = dateTimeHelper.parseDate2String(
								e,
								SettleConstants.BVB_DATE_STRING_FILE_TITLE_FORMAT
						);
						return env.getProperty("sftp.bvbank.virtualAcct.fileNamePrefix") + dateString + ".txt";
					}
			).collect(Collectors.toList());
			trans.setFile(listDate);
			trans.setStatus(SettleConstants.SETTLEMENT_PROCESSING);
			commonSettleService.save(trans);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		boolean isSuccess = sendBVBTransExportEmail(reconciliationDate,currentTime, emailSend, emailCC);
		if (isSuccess) {
			try {
				trans.setStatus(SettleConstants.SETTLEMENT_SUCCESS);
				commonSettleService.save(trans);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			// pause job
			// Settle completed => paused job
			SchedulerJobInfo jobTmp = new SchedulerJobInfo();
			jobTmp.setJobClass(BVBankTransactionListExportJob.class.getName());
			settleJobController.pauseJob(jobTmp);
		}

	}

	public void executeBVBTransExportFromDateToDateManually(String fromDateString,
			String toDateString, String emailSend, String emailCC) throws IOException {

		DateTime fromDate = dateTimeHelper.parseDate(fromDateString,
				OneFinConstants.HO_CHI_MINH_TIME_ZONE,
				DomainConstants.DATE_FORMAT_TRANS11
		);
		DateTime toDate = dateTimeHelper.parseDate(toDateString,
				OneFinConstants.HO_CHI_MINH_TIME_ZONE,
				DomainConstants.DATE_FORMAT_TRANS11
		);

		List<Date> listDateHourDiff
				= dateTimeHelper.hoursDiff(fromDate.toDate(),toDate.toDate() );

		boolean isSuccess = sendBVBTransExportFromDateToDateEmail(listDateHourDiff,
				emailSend, emailCC);
		if (isSuccess){
			LOGGER.info("Exportation successfully");
		}


	}

	public void executeBVBTransExportFromDateToDateFullManually(String fromDateString,
															String toDateString,
																String emailSend,
																String emailCC) throws IOException {

		DateTime fromDate = dateTimeHelper.parseDate(fromDateString,
				OneFinConstants.HO_CHI_MINH_TIME_ZONE,
				OneFinConstants.DATE_FORMAT_ddMMyyyy
		);
		DateTime toDate = dateTimeHelper.parseDate(toDateString,
				OneFinConstants.HO_CHI_MINH_TIME_ZONE,
				OneFinConstants.DATE_FORMAT_ddMMyyyy
		);

		List<Date> listDate
				= dateTimeHelper.dateDiff(fromDate.toDate(),toDate.toDate() );

		boolean isSuccess = sendBVBTransExportFromDateToDateFullEmail(listDate,
				emailSend, emailCC);
		if (isSuccess){
			LOGGER.info("Exportation successfully");
		}


	}

	public boolean sendReconciliationEmail(
			List<Date> reconciliationListDate,
		   	DateTime currentDate,String emailSend, String emailSendCC) {

		List<ReconciliationProcessDto> reconciliationProcessDtoList = new ArrayList<>();
		reconciliationListDate.forEach(
				date -> {
					try {
						String dateString = dateTimeHelper.parseDate2String(
								date,
								SettleConstants.BVB_DATE_STRING_FILE_TITLE_FORMAT
						);
						ReconciliationProcessDto reconciliationProcessDto
								= processReconciliation(dateString, currentDate.toDate());
						if (reconciliationProcessDto != null) {
							reconciliationProcessDtoList.add(reconciliationProcessDto);
						}
					} catch (Exception e) {
						LOGGER.error("Error occurred while process sendReconciliationEmail", e);
					}
				}
		);

		// Send email
		if (!reconciliationProcessDtoList.isEmpty()) {

			if (reconciliationProcessDtoList.stream().anyMatch(
					ReconciliationProcessDto::isError
			)) {
				List<ReconciliationProcessDto> reconciliationProcessDtoListError
						= reconciliationProcessDtoList.stream().filter(ReconciliationProcessDto::isError)
						.collect(Collectors.toList());
				LOGGER.error("Reconciliation list contain error, no sending email and return false");
				LOGGER.info("Error list");
				reconciliationProcessDtoListError.forEach(e->LOGGER.info("{}",e));
				return false;
			}
			// Call api to send email
			Map<String, Object> payload = new HashMap<>();
			String reconciliationDate = dateTimeHelper.parseDateString(
					currentDate,
					DomainConstants.DATE_FORMAT_TRANS
			);
			payload.put("customerName", "Ngân hàng TMCP Bản Việt");
			payload.put("customerNameEnglish", "VIET CAPITAL COMMERCIAL JOINT STOCK BANK");
			payload.put("reconciliationDate", reconciliationDate);
			ArrayList<String> listFileMismatch = new ArrayList<>();
			ArrayList<String> listFileError = new ArrayList<>();
			ArrayList<String> listFileFull = new ArrayList<>();
			List<String> attachList = new ArrayList<>();
			for (ReconciliationProcessDto e : reconciliationProcessDtoList) {
				listFileFull.add(e.getFileTitle());
				if (e.isMissMatch()) {
					attachList.add(e.getFileLocation());
					listFileMismatch.add(e.getFileTitle());
				}
				if (e.isError()) {
					listFileError.add(e.getFileTitle());
				}
			}

			if (listFileError.isEmpty() && listFileMismatch.isEmpty()) {
				LOGGER.info("Reconciliation successful, no need to send email!");
				return true;
			}

			payload.put("listFileFull", listFileFull);
			payload.put("listFileError", listFileError);
			payload.put("listFile", listFileMismatch);
			Date toDate = dateTimeHelper.getEndOfDate(
					reconciliationListDate.get(0)
			);
			Date fromDate = dateTimeHelper.getBeginingOfDate(
					reconciliationListDate.get(reconciliationListDate.size()-1)
			);

			String fromDateString = dateTimeHelper.parseDate2String(
					fromDate, DomainConstants.DATE_FORMAT_TRANS5
			);
			String toDateString = dateTimeHelper.parseDate2String(
					toDate, DomainConstants.DATE_FORMAT_TRANS5
			);

			String emailTitle = "[ONEFIN - BVBANK] Phản hồi kết quả đối soát từ ngày "
					+ fromDateString + " đến ngày "+ toDateString;


			List<String> emailList = new ArrayList<>();
			List<String> emailCC = new ArrayList<>();
			List<String> emailBCC = new ArrayList<>();
			emailList.addAll(Arrays.asList(emailSend.split(",")));
			emailBCC.add("locle@onefin.vn");
			if (emailSendCC!=null && !emailSendCC.isEmpty()){
				emailCC.addAll(Arrays.asList(emailSendCC.split(",")));
			}

			EmailDto data = new EmailDto(
					emailList,
					emailCC, emailBCC,
					payload, env.getProperty("minio.bvbReconciliationBucket"),
					attachList,
					emailTitle,
					"bvb_reconciliation");
			sendEmail(data);
		}
		LOGGER.info("Reconciliation complete!");
		return true;
	}

	public boolean sendBVBTransExportEmail(
			List<Date> reconciliationListDate,
			DateTime currentTime,
			String emailSend, String emailSendCC) throws IOException {

		List<ReconciliationProcessDto> reconciliationProcessDtoList = new ArrayList<>();
		reconciliationListDate.forEach(
				date -> {
					try {
						String dateString = dateTimeHelper.parseDate2String(
								date,
								SettleConstants.BVB_DATE_STRING_FILE_TITLE_FORMAT
						);
						ReconciliationProcessDto reconciliationProcessDto
								= processBVBTransExport(dateString, currentTime);
						if (reconciliationProcessDto != null) {
							reconciliationProcessDtoList.add(reconciliationProcessDto);
						}
					} catch (Exception e) {
						LOGGER.error("Error occurred while process sendBVBTransExportEmail", e);
					}
				}
		);

		Date toDate = dateTimeHelper.getEndOfDate(
				reconciliationListDate.get(0)
		);
		Date fromDate = dateTimeHelper.getBeginingOfDate(
				reconciliationListDate.get(reconciliationListDate.size()-1)
		);

		String fromDateString = dateTimeHelper.parseDate2String(
				fromDate, DomainConstants.DATE_FORMAT_TRANS5
		);
		String toDateString = dateTimeHelper.parseDate2String(
				toDate, DomainConstants.DATE_FORMAT_TRANS5
		);

		String fileLocationFull = "";

		List<ReconciliationProcessDto> reconciliationProcessDtoListSort = reconciliationProcessDtoList.stream()
				.sorted(Comparator.comparing(ReconciliationProcessDto::getReconciliationDate))
				.collect(Collectors.toList());
		List<ReconciliationDto> reconciliationDtoFull =
				reconciliationProcessDtoListSort.stream()
						.flatMap(e->e.getReconciliationDtos().stream())
						.sorted(Comparator.comparing(ReconciliationDto::getTransactionDate))
						.collect(Collectors.toList());

		//
		InputStream templateInputStream = null;
		Workbook workbook = null;
		try {
			ClassLoader classLoader = getClass().getClassLoader();
			templateInputStream =
					Files.newInputStream(Paths.get(configLoader.getBvbankReconciliationTemplateExport()));

			workbook = new XSSFWorkbook(templateInputStream);
			// get the sheet from workbook
			Sheet sheet = workbook.getSheetAt(0);
			int rowIndex = 6; // start from index 6
			Row rowData;
			Cell cellData;

			// Optionally, set some styling for the merged cell (e.g., alignment, border, etc.)
			CellStyle style = excelHelper.styleForSettleExcel(sheet.getWorkbook());


			// Set date value
			rowData = (sheet.getRow(1) == null)
					? sheet.createRow(1) : sheet.getRow(1);
			cellData = rowData.createCell(4);
			cellData.setCellValue(dateTimeHelper.parseDate2String(
					fromDate, DomainConstants.DATE_FORMAT_TRANS3
			));
			cellData.setCellStyle(style);
			rowData = (sheet.getRow(2) == null)
					? sheet.createRow(2) : sheet.getRow(2);
			cellData = rowData.createCell(4);
			cellData.setCellValue(dateTimeHelper.parseDate2String(
					toDate, DomainConstants.DATE_FORMAT_TRANS3
			));
			cellData.setCellStyle(style);

			// Create a row for the header
			Row headerRow = (sheet.getRow(rowIndex) == null)
					? sheet.createRow(rowIndex) : sheet.getRow(rowIndex);
			for (SettleConstants.BVBankReconciliationField e:
					SettleConstants.BVBankReconciliationField.stream().collect(Collectors.toList())) {
				cellData = headerRow.createCell(e.getIndex()+1);
				cellData.setCellValue(e.getField());
				cellData.setCellStyle(style);
			}

			rowIndex = rowIndex + 1;
			int startRow = rowIndex;
			int stt = 0;
			for (ReconciliationDto e : reconciliationDtoFull) {
				stt += 1;
				// Create a row for the header
				setCellFromReconciliationDto(
						e, rowData, cellData, sheet, rowIndex, style, stt,-1
				);
				rowIndex = rowIndex + 1;
			}
			int endRow = rowIndex;
			// Set Total Transaction amount
			// Formula Evaluator
			FormulaEvaluator  formulaEvaluator =
					workbook.getCreationHelper().createFormulaEvaluator();
			String formulaString = String.format("SUM(G%d:G%d)",startRow+1,endRow);
			rowData =
					(sheet.getRow(3) == null) ?
							sheet.createRow(3) : sheet.getRow(3);
			cellData = rowData.createCell(4);
			cellData.setCellValue(stt);
			cellData.setCellStyle(style);
			rowData =
					(sheet.getRow(4) == null) ?
							sheet.createRow(4) : sheet.getRow(4);
			cellData = rowData.createCell(4);

			cellData.setCellFormula(formulaString);
			formulaEvaluator.evaluateFormulaCell(cellData);
			cellData.setCellStyle(style);

			// Convert the Workbook to a byte array
			ByteArrayOutputStream outputStreamXslx = new ByteArrayOutputStream();
			workbook.write(outputStreamXslx);

			// Get the file extension
			String filenameSaved = "VA_ONEFIN_ALL_"+ fromDateString+ "_"
					+ toDateString+ "_FULL_EXPORT" + ".xlsx";

			LOGGER.info("File saved: {}", filenameSaved);
			String MinioSavedFolder
					= dateTimeHelper.parseDate2String(currentTime.toDate(),
					DomainConstants.DATE_FORMAT_TRANS3);
			 fileLocationFull = OneFinConstants.PARTNER_BVBANK + "/EMAIL_RECONCILIATION_EXPORT_BVB/"
					+ MinioSavedFolder + OneFinConstants.SLASH + filenameSaved;
			minioService.uploadByte(env.getProperty("minio.bvbReconciliationBucket"),
					fileLocationFull,
					outputStreamXslx.toByteArray(),
					"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

		} catch (Exception e) {
			LOGGER.error("Cannot write excel file", e);
		} finally {
			if (templateInputStream != null) {
				templateInputStream.close();
			}
			if (workbook != null) {
				workbook.close();
			}
		}


		LOGGER.info("reconciliationDtoFull number of record: {}", reconciliationDtoFull.size());
		// Send email
		if (!reconciliationProcessDtoList.isEmpty()) {

			if (reconciliationProcessDtoList.stream().anyMatch(
					ReconciliationProcessDto::isError
			)) {
				List<ReconciliationProcessDto> reconciliationProcessDtoListError
						= reconciliationProcessDtoList.stream().filter(ReconciliationProcessDto::isError)
						.collect(Collectors.toList());
				LOGGER.error("Reconciliation list contain error, no sending email and return false");
				LOGGER.info("Error list");
				reconciliationProcessDtoListError.forEach(e->LOGGER.info("{}",e));
				return false;
			}
			// Call api to send email
			Map<String, Object> payload = new HashMap<>();
			payload.put("fromDate", fromDateString);
			payload.put("toDate", toDateString);
			List<String> attachList = new ArrayList<>();
			if (!fileLocationFull.isEmpty()){
				attachList.add(fileLocationFull);
			}
			for (ReconciliationProcessDto e : reconciliationProcessDtoList) {
				attachList.add(e.getFileLocation());
			}

			String emailTitle = "[ONEFIN - BVBANK] Danh sách giao dịch từ ngày "
					+ fromDateString + " đến ngày "+ toDateString;

			List<String> emailList = new ArrayList<>();
			List<String> emailCC = new ArrayList<>();
			List<String> emailBCC = new ArrayList<>();
			emailList.addAll(Arrays.asList(emailSend.split(",")));
//			emailBCC.add("locle@onefin.vn");
			if (emailSendCC!=null && !emailSendCC.isEmpty()){
				emailCC.addAll(Arrays.asList(emailSendCC.split(",")));
			}

			EmailDto data = new EmailDto(
					emailList,
					emailCC, emailBCC,
					payload, env.getProperty("minio.bvbReconciliationBucket"),
					attachList,
					emailTitle,
					"bvb_reconciliation_export");
			sendEmail(data);
		}
		LOGGER.info("Reconciliation complete!");
		return true;
	}

	public boolean sendBVBTransExportFromDateToDateEmail(
			List<Date> reconciliationListDate,
			String emailSend, String emailSendCC) throws IOException {

		List<ReconciliationDto> reconciliationProcessDtoList = new ArrayList<>();
		reconciliationListDate.forEach(
				date -> {
					try {
						String dateString = dateTimeHelper.parseDate2String(
								date,
								DomainConstants.DATE_FORMAT_TRANS10
						);
						reconciliationProcessDtoList.addAll(getBVBTransExportDateToDate(dateString));

					} catch (Exception e) {
						LOGGER.error("Error occurred while process sendBVBTransExportEmail", e);
					}
				}
		);

		Date toDate = new DateTime(
				(reconciliationListDate.get(reconciliationListDate.size()-1))).minusHours(1).minusSeconds(1).toDate();

		Date fromDate = new DateTime(
				(reconciliationListDate.get(0))).minusHours(1).toDate();

		String fromDateString = dateTimeHelper.parseDate2String(
				fromDate, DomainConstants.DATE_FORMAT_TRANS2
		);
		String toDateString = dateTimeHelper.parseDate2String(
				toDate, DomainConstants.DATE_FORMAT_TRANS2
		);

		String fileLocationFull = "";

		List<ReconciliationDto> reconciliationDtoFull =
				reconciliationProcessDtoList.stream()
						.sorted(Comparator.comparing(ReconciliationDto::getTransactionDate))
						.collect(Collectors.toList());

		//
		InputStream templateInputStream = null;
		Workbook workbook = null;
		try {
			ClassLoader classLoader = getClass().getClassLoader();
			templateInputStream =
					Files.newInputStream(Paths.get(configLoader.getBvbankReconciliationTemplateExport()));

			workbook = new XSSFWorkbook(templateInputStream);
			// get the sheet from workbook
			Sheet sheet = workbook.getSheetAt(0);
			int rowIndex = 6; // start from index 6
			Row rowData;
			Cell cellData;

			// Optionally, set some styling for the merged cell (e.g., alignment, border, etc.)
			CellStyle style = excelHelper.styleForSettleExcel(sheet.getWorkbook());


			// Set date value
			rowData = (sheet.getRow(1) == null)
					? sheet.createRow(1) : sheet.getRow(1);
			cellData = rowData.createCell(4);
			cellData.setCellValue(fromDateString);
			cellData.setCellStyle(style);
			rowData = (sheet.getRow(2) == null)
					? sheet.createRow(2) : sheet.getRow(2);
			cellData = rowData.createCell(4);
			cellData.setCellValue(toDateString);
			cellData.setCellStyle(style);

			// Create a row for the header
			Row headerRow = (sheet.getRow(rowIndex) == null)
					? sheet.createRow(rowIndex) : sheet.getRow(rowIndex);
			for (SettleConstants.BVBankReconciliationField e:
					SettleConstants.BVBankReconciliationField.stream().collect(Collectors.toList())) {
				cellData = headerRow.createCell(e.getIndex()+1);
				cellData.setCellValue(e.getField());
				cellData.setCellStyle(style);
			}

			rowIndex = rowIndex + 1;
			int startRow = rowIndex;
			int stt = 0;
			for (ReconciliationDto e : reconciliationDtoFull) {
				stt += 1;
				// Create a row for the header
				setCellFromReconciliationDto(
						e, rowData, cellData, sheet, rowIndex, style, stt,-1
				);
				rowIndex = rowIndex + 1;
			}
			int endRow = rowIndex;
			// Set Total Transaction amount
			// Formula Evaluator
			FormulaEvaluator  formulaEvaluator =
					workbook.getCreationHelper().createFormulaEvaluator();
			String formulaString = String.format("SUM(G%d:G%d)",startRow+1,endRow);
			rowData =
					(sheet.getRow(3) == null) ?
							sheet.createRow(3) : sheet.getRow(3);
			cellData = rowData.createCell(4);
			cellData.setCellValue(stt);
			cellData.setCellStyle(style);
			rowData =
					(sheet.getRow(4) == null) ?
							sheet.createRow(4) : sheet.getRow(4);
			cellData = rowData.createCell(4);

			cellData.setCellFormula(formulaString);
			formulaEvaluator.evaluateFormulaCell(cellData);
			cellData.setCellStyle(style);



			// Convert the Workbook to a byte array
			ByteArrayOutputStream outputStreamXslx = new ByteArrayOutputStream();
			workbook.write(outputStreamXslx);

			// Get the file extension
			String filenameSaved = "VA_ONEFIN_"+ dateTimeHelper.parseDate2String(
					fromDate, DomainConstants.DATE_FORMAT_TRANS11
			)+ "_"
					+  dateTimeHelper.parseDate2String(
					toDate, DomainConstants.DATE_FORMAT_TRANS11
			) + ".xlsx";

			LOGGER.info("File saved: {}", filenameSaved);
			String MinioSavedFolder
					= dateTimeHelper.parseDate2String(fromDate,
					DomainConstants.DATE_FORMAT_TRANS3);
			fileLocationFull = OneFinConstants.PARTNER_BVBANK + "/EMAIL_RECONCILIATION_EXPORT_BVB/"
					+ MinioSavedFolder + OneFinConstants.SLASH + filenameSaved;
			minioService.uploadByte(env.getProperty("minio.bvbReconciliationBucket"),
					fileLocationFull,
					outputStreamXslx.toByteArray(),
					"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

		} catch (Exception e) {
			LOGGER.error("Cannot write excel file", e);
		} finally {
			if (templateInputStream != null) {
				templateInputStream.close();
			}
			if (workbook != null) {
				workbook.close();
			}
		}


		LOGGER.info("reconciliationDtoFull number of record: {}", reconciliationDtoFull.size());
		// Send email
		if (!reconciliationProcessDtoList.isEmpty()) {

			// Call api to send email
			Map<String, Object> payload = new HashMap<>();
			payload.put("fromDate", fromDateString);
			payload.put("toDate", toDateString);
			List<String> attachList = new ArrayList<>();
			if (!fileLocationFull.isEmpty()){
				attachList.add(fileLocationFull);
			}

			String emailTitle = "[ONEFIN - BVBANK] Danh sách giao dịch theo giờ từ "
					+ fromDateString + " đến "+ toDateString;

			List<String> emailList = new ArrayList<>();
			List<String> emailCC = new ArrayList<>();
			List<String> emailBCC = new ArrayList<>();
			emailList.addAll(Arrays.asList(emailSend.split(",")));
//			emailBCC.add("locle@onefin.vn");
			if (emailSendCC!=null && !emailSendCC.isEmpty()){
				emailCC.addAll(Arrays.asList(emailSendCC.split(",")));
			}

			EmailDto data = new EmailDto(
					emailList,
					emailCC, emailBCC,
					payload, env.getProperty("minio.bvbReconciliationBucket"),
					attachList,
					emailTitle,
					"bvb_reconciliation_export");
			sendEmail(data);
		}
		LOGGER.info("Reconciliation complete!");
		return true;
	}

	public boolean sendBVBTransExportFromDateToDateFullEmail(
			List<Date> reconciliationListDate,
			String emailSend, String emailSendCC) throws IOException {

		List<ReconciliationDto> reconciliationProcessDtoList = new ArrayList<>();
		reconciliationListDate.forEach(
				date -> {
					try {
						String dateString = dateTimeHelper.parseDate2String(
								date,
								BankConstants.DATE_FORMAT_yyyyMMDD
						);
						reconciliationProcessDtoList.addAll(getBVBTransExportFullDateToDate(dateString));

					} catch (Exception e) {
						LOGGER.error("Error occurred while process sendBVBTransExportEmail", e);
					}
				}
		);

		Date toDate = new DateTime(
				(reconciliationListDate.get(reconciliationListDate.size()-1))).toDate();

		Date fromDate = new DateTime(
				(reconciliationListDate.get(0))).toDate();

		String fromDateString = dateTimeHelper.parseDate2String(
				fromDate, DomainConstants.DATE_FORMAT_TRANS5
		);
		String toDateString = dateTimeHelper.parseDate2String(
				toDate, DomainConstants.DATE_FORMAT_TRANS5
		);

		String fileLocationFull = "";
		String fileLocationFullTotal = "";

		List<ReconciliationDto> reconciliationDtoFull =
				reconciliationProcessDtoList.stream()
						.sorted(Comparator.comparing(ReconciliationDto::getTransactionDate))
						.collect(Collectors.toList());

		//
		InputStream templateInputStream = null;
		Workbook workbook = null;
		SXSSFWorkbook sxssfWorkbook = null;
		try {
			ClassLoader classLoader = getClass().getClassLoader();

			int totalAmount = 0;
			sxssfWorkbook = new SXSSFWorkbook(null);
			SXSSFSheet sheet = sxssfWorkbook.createSheet();
			CellStyle style = excelHelper.styleForSettleExcel(sxssfWorkbook);

			int rowIndex = 0; // start from index 6
			Row rowData = null;
			Cell cellData = null;

			// Create a row for the header
			Row headerRow = (sheet.getRow(rowIndex) == null)
					? sheet.createRow(rowIndex) : sheet.getRow(rowIndex);
			for (SettleConstants.BVBankReconciliationField e:
					SettleConstants.BVBankReconciliationField.stream().collect(Collectors.toList())) {
				cellData = headerRow.createCell(e.getIndex()+1);
				cellData.setCellValue(e.getField());
				cellData.setCellStyle(style);
			}

			rowIndex = rowIndex + 1;
			int stt = 0;
			for (ReconciliationDto e : reconciliationDtoFull) {
				stt += 1;
				// Create a row for the header
				totalAmount = totalAmount + setCellFromReconciliationDto(
						e, rowData, cellData, sheet, rowIndex, style, stt,-1
				);
				rowIndex = rowIndex + 1;
			}

			// Set Total Transaction amount
			templateInputStream =
					Files.newInputStream(Paths.get(configLoader.getBvbankReconciliationTemplateExport()));
			workbook = new XSSFWorkbook(templateInputStream);
			// get the sheet from workbook
			Sheet headerSheet = workbook.getSheetAt(0);
			// Optionally, set some styling for the merged cell (e.g., alignment, border, etc.)
			style = excelHelper.styleForSettleExcel(headerSheet.getWorkbook());

			// Set date value
			rowData = headerSheet.getRow(1);
			if (rowData == null){
				rowData = headerSheet.createRow(1);
			}
//			rowData = ( == null)
//					? sheet.createRow(1) : sheet.getRow(1);
			cellData = rowData.createCell(4);
			cellData.setCellValue(fromDateString);
			cellData.setCellStyle(style);
			rowData = (headerSheet.getRow(2) == null)
					? headerSheet.createRow(2) : headerSheet.getRow(2);
			cellData = rowData.createCell(4);
			cellData.setCellValue(toDateString);
			cellData.setCellStyle(style);

			rowData =
					(headerSheet.getRow(3) == null) ?
							headerSheet.createRow(3) : headerSheet.getRow(3);
			cellData = rowData.createCell(4);
			cellData.setCellValue(stt);
			cellData.setCellStyle(style);
			rowData =
					(headerSheet.getRow(4) == null) ?
							headerSheet.createRow(4) : headerSheet.getRow(4);
			cellData = rowData.createCell(4);
			cellData.setCellValue(totalAmount);
			cellData.setCellStyle(style);

			// Convert the Workbook to a byte array
			ByteArrayOutputStream outputStreamXslx = new ByteArrayOutputStream();
			sxssfWorkbook.write(outputStreamXslx);

			ByteArrayOutputStream outputStreamXslxTotal = new ByteArrayOutputStream();
			workbook.write(outputStreamXslxTotal);

			// Get the file extension
			String filenameSaved = "VA_ONEFIN_ALL_"+ dateTimeHelper.parseDate2String(
					fromDate, DomainConstants.DATE_FORMAT_TRANS5
			)+ "_"
					+  dateTimeHelper.parseDate2String(
					toDate, DomainConstants.DATE_FORMAT_TRANS5
			) + ".xlsx";

			String filenameSavedTotal = "VA_ONEFIN_ALL_"+ dateTimeHelper.parseDate2String(
					fromDate, DomainConstants.DATE_FORMAT_TRANS5
			)+ "_"
					+  dateTimeHelper.parseDate2String(
					toDate, DomainConstants.DATE_FORMAT_TRANS5
			) + "_total.xlsx";

			LOGGER.info("File saved: {}", filenameSaved);
			LOGGER.info("File saved: {}", filenameSavedTotal);
			String MinioSavedFolder
					= dateTimeHelper.parseDate2String(fromDate,
					OneFinConstants.DATE_FORMAT_yyyyMM);
			fileLocationFull = OneFinConstants.PARTNER_BVBANK + "/EMAIL_RECONCILIATION_EXPORT_BVB/"
					+ MinioSavedFolder + OneFinConstants.SLASH + filenameSaved;

			fileLocationFullTotal = OneFinConstants.PARTNER_BVBANK + "/EMAIL_RECONCILIATION_EXPORT_BVB/"
					+ MinioSavedFolder + OneFinConstants.SLASH + filenameSavedTotal;
			minioService.uploadByte(env.getProperty("minio.bvbReconciliationBucket"),
					fileLocationFull,
					outputStreamXslx.toByteArray(),
					"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

			minioService.uploadByte(env.getProperty("minio.bvbReconciliationBucket"),
					fileLocationFullTotal,
					outputStreamXslxTotal.toByteArray(),
					"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

		} catch (Exception e) {
			LOGGER.error("Cannot write excel file", e);
		} finally {
			if (templateInputStream != null) {
				templateInputStream.close();
			}

			if (sxssfWorkbook != null) {
				sxssfWorkbook.close();
			}
			if (workbook != null) {
				workbook.close();
			}
		}


		LOGGER.info("reconciliationDtoFull number of record: {}", reconciliationDtoFull.size());
		// Send email
		if (!reconciliationProcessDtoList.isEmpty()) {

			// Call api to send email
			Map<String, Object> payload = new HashMap<>();
			payload.put("fromDate", fromDateString);
			payload.put("toDate", toDateString);
			List<String> attachList = new ArrayList<>();
			if (!fileLocationFull.isEmpty()){
				attachList.add(fileLocationFull);
			}

			if (!fileLocationFullTotal.isEmpty()){
				attachList.add(fileLocationFullTotal);
			}

			String emailTitle = "[ONEFIN - BVBANK] Danh sách giao dịch tổng hợp từ "
					+ fromDateString + " đến "+ toDateString;

			List<String> emailList = new ArrayList<>();
			List<String> emailCC = new ArrayList<>();
			List<String> emailBCC = new ArrayList<>();
			emailList.addAll(Arrays.asList(emailSend.split(",")));
//			emailBCC.add("locle@onefin.vn");
			if (emailSendCC!=null && !emailSendCC.isEmpty()){
				emailCC.addAll(Arrays.asList(emailSendCC.split(",")));
			}

			EmailDto data = new EmailDto(
					emailList,
					emailCC, emailBCC,
					payload, env.getProperty("minio.bvbReconciliationBucket"),
					attachList,
					emailTitle,
					"bvb_reconciliation_export");
			sendEmail(data);
		}
		LOGGER.info("Reconciliation complete!");
		return true;
	}

	public void monthlyReconciliation(
			Date fromDate, Date toDate, InputStream in
	) throws IOException {
		if (in != null) {
			//creating workbook instance that refers to .xls file
			XSSFWorkbook wb = new XSSFWorkbook(in);
			FormulaEvaluator formulaEvaluator = wb.getCreationHelper().createFormulaEvaluator();
			//creating a Sheet object to retrieve the object
			XSSFSheet sheet = wb.getSheetAt(0);

			XSSFCell cell = sheet.getRow(11).getCell(2);
			String phiPhatHanhMaThanhToanSoluongGiaoDichThanhcong = ExcelHelper.readCell(formulaEvaluator, sheet, cell);

			cell = sheet.getRow(11).getCell(3);
			String phiPhatHanhMaThanhToanSotienGiaoDichThanhCong = ExcelHelper.readCell(formulaEvaluator, sheet, cell);

			cell = sheet.getRow(11).getCell(4);
			String phiPhatHanhMaThanhToanPhiDichVu = ExcelHelper.readCell(formulaEvaluator, sheet, cell);

			cell = sheet.getRow(12).getCell(2);
			String phiXuLyGiaoDichSoluongGiaoDichThanhcong = ExcelHelper.readCell(formulaEvaluator, sheet, cell);

			cell = sheet.getRow(12).getCell(3);
			String phiXuLyGiaoDichSotienGiaoDichThanhCong = ExcelHelper.readCell(formulaEvaluator, sheet, cell);

			cell = sheet.getRow(12).getCell(4);
			String phiXuLyGiaoDichPhiDichVu = ExcelHelper.readCell(formulaEvaluator, sheet, cell);

			cell = sheet.getRow(13).getCell(2);
			String congSoluongGiaoDichThanhcong = ExcelHelper.readCell(formulaEvaluator, sheet, cell);

			cell = sheet.getRow(13).getCell(3);
			String congSotienGiaoDichThanhCong = ExcelHelper.readCell(formulaEvaluator, sheet, cell);

			cell = sheet.getRow(13).getCell(4);
			String congPhiDichVu = ExcelHelper.readCell(formulaEvaluator, sheet, cell);
			LOGGER.info("phiPhatHanhMaThanhToanSoluongGiaoDichThanhcong: {}", phiPhatHanhMaThanhToanSoluongGiaoDichThanhcong);
			LOGGER.info("phiPhatHanhMaThanhToanSotienGiaoDichThanhCong: {}", phiPhatHanhMaThanhToanSotienGiaoDichThanhCong);
			LOGGER.info("phiPhatHanhMaThanhToanPhiDichVu: {}", phiPhatHanhMaThanhToanPhiDichVu);
			LOGGER.info("phiXuLyGiaoDichSoluongGiaoDichThanhcong: {}", phiXuLyGiaoDichSoluongGiaoDichThanhcong);
			LOGGER.info("phiXuLyGiaoDichSotienGiaoDichThanhCong: {}", phiXuLyGiaoDichSotienGiaoDichThanhCong);
			LOGGER.info("phiXuLyGiaoDichPhiDichVu: {}", phiXuLyGiaoDichPhiDichVu);
			LOGGER.info("congSoluongGiaoDichThanhcong: {}", congSoluongGiaoDichThanhcong);
			LOGGER.info("congSotienGiaoDichThanhCong: {}", congSotienGiaoDichThanhCong);
			LOGGER.info("congPhiDichVu: {}", congPhiDichVu);
		}

		List<VietinNotifyTransTable> vietinNotifyTransTableList
				= vietinNotifyTransTableRepo.findByFromDateToDate(dateTimeHelper.getBeginingOfDate(fromDate),
				dateTimeHelper.getEndOfDate(toDate),
				OneFinConstants.BankListQrService.VCCB.getBankCode());

		List<VietinNotifyTransTable> finalList = new ArrayList<>();
		vietinNotifyTransTableList.forEach(
				e -> {
					if (!finalList.stream()
							.map(VietinNotifyTrans::getTransId).anyMatch(id -> id.equals(e.getTransId()))) {
						finalList.add(e);
					}
				}
		);

		BigDecimal totalTransactionAmount
				= finalList.stream().map(e -> BigDecimal.valueOf(Long.parseLong(e.getAmount()))).reduce(BigDecimal.valueOf(0), BigDecimal::add);
		int totalTransaction = finalList.size();
		LOGGER.info("total transaction list:");
		vietinNotifyTransTableList.forEach(e -> LOGGER.log(Level.getLevel("INFOWT"), "{}", e));
		LOGGER.info("total transaction: {}", totalTransaction);
		LOGGER.info("total transaction amount: {}", totalTransactionAmount);
	}

	public void monthlyReconciliationDetail(Date fromDate, Date toDate, InputStream in)
			throws IOException {

		List<ReconciliationMonthlyDetailDto> bvbTransList = new ArrayList<>();
		Queue<ReconciliationMonthlyDetailDto> queueReconciliation
				= new ConcurrentLinkedQueue<ReconciliationMonthlyDetailDto>();
		Queue<VietinNotifyTransTable> queueVietinNotifyTransTable
				= new ConcurrentLinkedQueue<VietinNotifyTransTable>();

		if (in != null) {
			//creating workbook instance that refers to .xls file
			XSSFWorkbook wb = new XSSFWorkbook(in);
			FormulaEvaluator formulaEvaluator = wb.getCreationHelper().createFormulaEvaluator();
			//creating a Sheet object to retrieve the object
			XSSFSheet sheet = wb.getSheetAt(0);
			for (Row row : sheet) {
				ReconciliationMonthlyDetailDto reconciliationMonthlyDetailDto
						= getInstanceFromRow(formulaEvaluator, sheet, row);
				if (reconciliationMonthlyDetailDto != null) {
					bvbTransList.add(reconciliationMonthlyDetailDto);
				}
			}
			LOGGER.info("number record: {}", bvbTransList.size());
			LOGGER.log(Level.getLevel("INFOWT"), "bvbTransList: {}", bvbTransList);

			List<VietinNotifyTransTable> vietinNotifyTransTableList
					= vietinNotifyTransTableRepo.findByFromDateToDate(dateTimeHelper.getBeginingOfDate(fromDate),
					dateTimeHelper.getEndOfDate(toDate),
					OneFinConstants.BankListQrService.VCCB.getBankCode());
			try {
				int numThread = (int) bvbTransList.size() / batchSize;
				numThread = Math.max(numThread, 1);
				ExecutorService executor = Executors.newFixedThreadPool(numThread);
				final AtomicInteger sublist = new AtomicInteger();
				AtomicInteger count_row = new AtomicInteger();
				CompletableFuture[] futures = bvbTransList.stream()
					.collect(Collectors.groupingBy(t -> sublist.getAndIncrement() / batchSize))
					.values()
					.stream()
					.map(ul -> CompletableFuture.runAsync(() -> {
						IntStream.range(0, ul.size()).forEach(index -> {
							int currentIndex = 0;
							currentIndex = count_row.addAndGet(1);
							try {
								vietinNotifyTransTableList.parallelStream().forEach(
									e -> {
										if (e.getTransId().equals(ul.get(index).getExternalRefNo())) {
											if (!queueReconciliation.contains(ul.get(index))) {
												queueReconciliation.add(ul.get(index));
											}
											if (!queueVietinNotifyTransTable.contains(e)) {
												queueVietinNotifyTransTable.add(e);
											}
										}
									}
								);
							} catch (Exception ex) {
								LOGGER.error(ex.getMessage(), ex);
							}
						});
					}, executor)).toArray(CompletableFuture[]::new);
				CompletableFuture<Void> run = CompletableFuture.allOf(futures);
				run.get();

				if (queueVietinNotifyTransTable.size() == vietinNotifyTransTableList.size()
						&& bvbTransList.size() == queueReconciliation.size()) {
					LOGGER.info("Reconciliation successful");
				} else {
					List<ReconciliationMonthlyDetailDto> bvbTransListMisMatch =
						bvbTransList.parallelStream()
							.filter(e -> !queueReconciliation.contains(e))
							.collect(Collectors.toList());
					bvbTransListMisMatch.forEach(e -> {
						LOGGER.log(Level.getLevel("INFOWT"), "bvbTransList mis match: {}", e.getExternalRefNo());
					});

					List<VietinNotifyTransTable> vietinNotifyTransTableListMisMatch =
						vietinNotifyTransTableList.parallelStream()
							.filter(e -> !queueVietinNotifyTransTable.contains(e))
							.collect(Collectors.toList());
					vietinNotifyTransTableListMisMatch.forEach(e -> {
						LOGGER.log(Level.getLevel("INFOWT"), "vietinNotifyTransTableList mis match: {}", getReconciliationString(e));

					});
				}
			} catch (Exception e) {
				LOGGER.error("Error occurred reconciliation failed", e);
			}
		}
	}

	public ReconciliationMonthlyDetailDto getInstanceFromRow(FormulaEvaluator workbook,
															 Sheet sheet, Row row) {
		try {
			if (row.getRowNum() < SettleConstants.BVBankMonthlyReconciliationField.ROW_START.getIndex()) {
				LOGGER.info("row {} Not record row, ignore", row.getRowNum());
				return null;
			}
			ReconciliationMonthlyDetailDto reconciliationMonthlyDetailDto
					= new ReconciliationMonthlyDetailDto();
			SettleConstants.BVBankMonthlyReconciliationField.stream().forEach(
					e -> {
						try {
							if (e.getDtoField().isEmpty()) {
								return;
							}

							Field nameField = reconciliationMonthlyDetailDto.getClass()
									.getDeclaredField(e.getDtoField());
							nameField.setAccessible(true);
							Cell cell = row.getCell(e.getIndex());
							String value = ExcelHelper.readCell(workbook, sheet, cell);
							if (e.getField().equals(SettleConstants.BVBankMonthlyReconciliationField.STT.getField())
									&& value.isEmpty()) {
								throw new RuntimeException("not a record row, throw exception");
							}
							nameField.set(reconciliationMonthlyDetailDto, value);
						} catch (NoSuchFieldException | IllegalAccessException ex) {
							throw new RuntimeException(ex);
						}
					}
			);
			return reconciliationMonthlyDetailDto;

		} catch (Exception e) {
			LOGGER.error("Error Occurred: ", e);
			return null;
		}

	}
}
