package com.onefin.ewallet.settlement.service.vnpay;

import com.onefin.ewallet.common.base.constants.OneFinConstants;
import com.onefin.ewallet.common.domain.bank.vietin.VietinVirtualAcctTransHistory;
import com.onefin.ewallet.common.domain.base.AbstractBaseEwalletTrans;
import com.onefin.ewallet.common.domain.billpay.imedia.IMediaBillPayTransaction;
import com.onefin.ewallet.common.domain.billpay.vnpay.trans.VnpayTopupTransaction;
import com.onefin.ewallet.common.domain.settlement.VietinBillReport;
import com.onefin.ewallet.common.domain.settlement.VietinBillSummary;
import com.onefin.ewallet.common.utility.date.DateTimeHelper;
import com.onefin.ewallet.common.utility.file.ExcelHelper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class ReportHelper extends ExcelHelper {

	// Sms
	public static final int COLUMN_INDEX_TELCO = 0;
	public static final int COLUMN_INDEX_NUMSMS = 1;
	private static final Logger LOGGER = LoggerFactory.getLogger(ReportHelper.class);

	private static DateTimeHelper dateTimeHelper;

	/**
	 * Write sms summary data to file
	 *
	 * @param telcoSumSmsMap
	 * @param excelFilePath
	 * @param title
	 * @throws IOException
	 */
	public void writeTelcoSumSmsExcel(Map<String, Long> telcoSumSmsMap, String excelFilePath, String title) throws IOException {
		Workbook workbook = null;
		try {
			// Create Workbook
			workbook = super.createWorkbook(excelFilePath);
			// Create sheet
			Sheet sheet = workbook.createSheet("Settlement SMS");
			int rowIndex = 0;
			// Write header
			rowIndex = writeHeaderSms(sheet, rowIndex, title);
			// Write data
			for (Map.Entry<String, Long> telcoSumSms : telcoSumSmsMap.entrySet()) {
				// Write data on row
				rowIndex = writeTelcoSumSms(sheet, rowIndex, telcoSumSms.getKey(), telcoSumSms.getValue());
			}
			// Write footer
			writeFooterSms(sheet, rowIndex, telcoSumSmsMap);
			// Auto resize column witdth
			int numberOfColumn = getRow(sheet, 0).getPhysicalNumberOfCells();
			autosizeColumn(sheet, numberOfColumn);
			// Create file excel
			createOutputFile(workbook, excelFilePath);
		} catch (Exception e) {
			LOGGER.error("Cannot write SettlementExcelFile", e);
		} finally {
			workbook.close();
		}
	}

	/**
	 * Write VNPay airtime top-up transaction to file
	 *
	 * @param trxDetails
	 * @param excelFilePath
	 * @throws IOException
	 */
	public void writeVNPayAirtimeTrxExcel(List<VnpayTopupTransaction> trxDetails, String excelFilePath) throws IOException {
		Workbook workbook = null;
		InputStream templateInputStream = null;
		try {
			// Load template file from resources folder
			ClassLoader classLoader = getClass().getClassLoader();
			templateInputStream = classLoader.getResourceAsStream("template.xlsx");
			// If the template file isn't found in the resources folder, you can't proceed
			if (templateInputStream == null) {
				throw new FileNotFoundException("Template file not found in resources folder");
			}
			workbook = new XSSFWorkbook(templateInputStream);
			workbook.setSheetName(0, "VNPAY Airtime Topup");
			// get the sheet from workbook
			Sheet sheet = workbook.getSheetAt(0);
			// write headers into the sheet at 7th row using modified writeHeaderTrx
			int rowIndex = 6;  // since headers start from row 7 (0-based index)
			rowIndex = writeVnPayHeaderTrx(sheet, rowIndex, VnpayTopupTransaction.class);
			// write data into the sheet
			int counter = 1;
			NumberFormat currencyFormat = NumberFormat.getNumberInstance();
			currencyFormat.setGroupingUsed(true);
			for (VnpayTopupTransaction e : trxDetails) {
				// Write data on row
				rowIndex = writeVnPayTrx(sheet, rowIndex, e, VnpayTopupTransaction.class, currencyFormat);
				counter++;
			}
			// Set counter value in column A starting from row 8
			rowIndex = 7;  // Starting from row 8 (0-based index 7)
			for (int i = 0; i < trxDetails.size(); i++) {
				Row row = getRow(sheet, rowIndex++);
				Cell cell = getCell(row, 0); // Column A
				cell.setCellValue(i + 1);  // Set counter value
			}
			// Apply formatting to the cells
			CellStyle style = workbook.createCellStyle();
			style.setAlignment(HorizontalAlignment.CENTER);
			style.setVerticalAlignment(VerticalAlignment.CENTER);
			style.setBorderTop(BorderStyle.THIN);
			style.setBorderRight(BorderStyle.THIN);
			style.setBorderBottom(BorderStyle.THIN);
			Font font = workbook.createFont();
			font.setFontName("Times New Roman");
			style.setFont(font);
			for (int i = 7; i < rowIndex; i++) {
				Row row = getRow(sheet, i);
				for (int j = 0; j < row.getLastCellNum(); j++) {
					Cell cell = getCell(row, j);
					cell.setCellStyle(style);
				}
			}
			// write aggregated data
			DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern(OneFinConstants.DATE_FORMAT_yyyyMMDDHHmmss);
			DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern(OneFinConstants.DATE_FORMAT_dd_MM_yyyy);
			String earliestTransTime = trxDetails.stream()
					.map(VnpayTopupTransaction::getLocalDateTime)
					.filter(Objects::nonNull)
					.map(timeStr -> LocalDateTime.parse(timeStr, inputFormatter))
					.min(LocalDateTime::compareTo)
					.map(time -> time.toLocalDate().format(outputFormatter))
					.orElse(null);
			String latestTransTime = trxDetails.stream()
					.map(VnpayTopupTransaction::getLocalDateTime)
					.filter(Objects::nonNull)
					.map(timeStr -> LocalDateTime.parse(timeStr, inputFormatter))
					.max(LocalDateTime::compareTo)
					.map(time -> time.toLocalDate().format(outputFormatter))
					.orElse(null);
			BigDecimal totalPaidAmount = trxDetails.stream()
					.map(VnpayTopupTransaction::getRequestAmount)
					.filter(Objects::nonNull) // Filter out null values
					.reduce(BigDecimal.ZERO, BigDecimal::add);
			Row row1 = sheet.getRow(1);
			if (row1 == null) {
				row1 = sheet.createRow(1);
			}
			Cell cellF2 = row1.getCell(5);
			if (cellF2 == null) {
				cellF2 = row1.createCell(5);
			}
			cellF2.setCellValue(earliestTransTime);
			Cell cellF3 = sheet.getRow(2).getCell(5);
			cellF3.setCellValue(latestTransTime);
			Cell cellF4 = sheet.getRow(3).getCell(5);
			cellF4.setCellValue(trxDetails.size());
			Cell cellF5 = sheet.getRow(4).getCell(5);
			cellF5.setCellValue(totalPaidAmount.doubleValue());
			// Save file to excelFilePath
			try (FileOutputStream outputStream = new FileOutputStream(excelFilePath)) {
				workbook.write(outputStream);
			}

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
	}

	public void writeImediaTrxExcel(List<IMediaBillPayTransaction> trxDetails, String excelFilePath) throws IOException {
		Workbook workbook = null;
		InputStream templateInputStream = null;
		try {
			// Load template file from resources folder
			ClassLoader classLoader = getClass().getClassLoader();
			templateInputStream = classLoader.getResourceAsStream("template.xlsx");
			// If the template file isn't found in the resources folder, you can't proceed
			if (templateInputStream == null) {
				throw new FileNotFoundException("Template file not found in resources folder");
			}
			workbook = new XSSFWorkbook(templateInputStream);
			// get the sheet from workbook
			Sheet sheet = workbook.getSheetAt(0);
			// write headers into the sheet at 7th row using modified writeHeaderTrx
			int rowIndex = 6;  // since headers start from row 7 (0-based index)
			rowIndex = writeImediaHeaderTrx(sheet, rowIndex, IMediaBillPayTransaction.class);
			// write data into the sheet
			int counter = 1;
			NumberFormat currencyFormat = NumberFormat.getNumberInstance();
			currencyFormat.setGroupingUsed(true);
			for (IMediaBillPayTransaction e : trxDetails) {
				// Write data on row
				rowIndex = writeImediaTrx(sheet, rowIndex, e, IMediaBillPayTransaction.class, currencyFormat);
				counter++;
			}
			// Set counter value in column A starting from row 8
			rowIndex = 7;  // Starting from row 8 (0-based index 7)
			for (int i = 0; i < trxDetails.size(); i++) {
				Row row = getRow(sheet, rowIndex++);
				Cell cell = getCell(row, 0); // Column A
				cell.setCellValue(i + 1);  // Set counter value
			}
			// Apply formatting to the cells
			CellStyle style = workbook.createCellStyle();
			style.setAlignment(HorizontalAlignment.CENTER);
			style.setVerticalAlignment(VerticalAlignment.CENTER);
			style.setBorderTop(BorderStyle.THIN);
			style.setBorderRight(BorderStyle.THIN);
			style.setBorderBottom(BorderStyle.THIN);
			Font font = workbook.createFont();
			font.setFontName("Times New Roman");
			style.setFont(font);
			for (int i = 7; i < rowIndex; i++) {
				Row row = getRow(sheet, i);
				for (int j = 0; j < row.getLastCellNum(); j++) {
					Cell cell = getCell(row, j);
					cell.setCellStyle(style);
				}
			}
			// write aggregated data
			DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern(OneFinConstants.DATE_FORMAT_TRANS);
			DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern(OneFinConstants.DATE_FORMAT_dd_MM_yyyy);
			String earliestTransTime = trxDetails.stream()
					.map(IMediaBillPayTransaction::getTransTime)
					.filter(Objects::nonNull)
					.map(timeStr -> LocalDateTime.parse(timeStr, inputFormatter))
					.min(LocalDateTime::compareTo)
					.map(time -> time.toLocalDate().format(outputFormatter))
					.orElse(null);
			String latestTransTime = trxDetails.stream()
					.map(IMediaBillPayTransaction::getTransTime)
					.filter(Objects::nonNull)
					.map(timeStr -> LocalDateTime.parse(timeStr, inputFormatter))
					.max(LocalDateTime::compareTo)
					.map(time -> time.toLocalDate().format(outputFormatter))
					.orElse(null);
			BigDecimal totalPaidAmount = trxDetails.stream()
					.map(IMediaBillPayTransaction::getPaidAmount)
					.filter(Objects::nonNull) // Filter out null values
					.reduce(BigDecimal.ZERO, BigDecimal::add);
			Row row1 = sheet.getRow(1);
			if (row1 == null) {
				row1 = sheet.createRow(1);
			}
			Cell cellF2 = row1.getCell(5);
			if (cellF2 == null) {
				cellF2 = row1.createCell(5);
			}
			cellF2.setCellValue(earliestTransTime);
			Cell cellF3 = sheet.getRow(2).getCell(5);
			cellF3.setCellValue(latestTransTime);
			Cell cellF4 = sheet.getRow(3).getCell(5);
			cellF4.setCellValue(trxDetails.size());
			Cell cellF5 = sheet.getRow(4).getCell(5);
			cellF5.setCellValue(totalPaidAmount.doubleValue());
			// Save file to excelFilePath
			try (FileOutputStream outputStream = new FileOutputStream(excelFilePath)) {
				workbook.write(outputStream);
			}

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
	}

	private int writeImediaTrx(Sheet sheet, int rowIndex, IMediaBillPayTransaction trx, Class<?> clazz, NumberFormat currencyFormat) {
		Map<String, String> fieldToHeaderMap = new LinkedHashMap<>();
		fieldToHeaderMap.put("originalTransId", "Mã GD");
		fieldToHeaderMap.put("billingCode", "Mã người dùng");
		fieldToHeaderMap.put("billName", "Họ tên");
		fieldToHeaderMap.put("transTime", "Thời gian GD");
		fieldToHeaderMap.put("paidAmount", "Số tiền");
		fieldToHeaderMap.put("amount", "Giá trị đơn hàng");
		fieldToHeaderMap.put("providerName", "Dịch vụ thanh toán");
		fieldToHeaderMap.put("apiOperation", "Loại GD");

		Row row = getRow(sheet, rowIndex);
		int cellIndex = 1;  // skip first column (A)
		for (String fieldName : fieldToHeaderMap.keySet()) {
			try {
				Field field = getField(clazz, fieldName);
				field.setAccessible(true);
				Cell cell = getCell(row, cellIndex++);
				Object fieldValue = field.get(trx);
				cell.setCellValue(fieldValue != null ? String.valueOf(fieldValue) : "");
			} catch (Exception e) {
				LOGGER.error(e.getMessage(), e);
			}
		}
		rowIndex++;
		BigDecimal paidAmount = trx.getPaidAmount();
		if (paidAmount != null) {
			Cell paidAmountCell = getCell(row, 5); // Column F
			paidAmountCell.setCellValue(currencyFormat.format(paidAmount.doubleValue()));
		}
		// Write the amount column
		BigDecimal amount = trx.getAmount();
		if (amount != null) {
			Cell amountCell = getCell(row, 6); // Column G
			amountCell.setCellValue(currencyFormat.format(amount.doubleValue()));
		}
		return rowIndex;
	}


	private int writeVnPayTrx(Sheet sheet, int rowIndex, VnpayTopupTransaction trx, Class<?> clazz, NumberFormat currencyFormat) {
		Map<String, String> fieldToHeaderMap = new LinkedHashMap<>();
		fieldToHeaderMap.put("requestId", "Mã yêu cầu");
		fieldToHeaderMap.put("walletId", "Mã ví");
		fieldToHeaderMap.put("mobileNo", "SĐT yêu cầu");
		fieldToHeaderMap.put("localDateTime", "Thời gian GD");
		fieldToHeaderMap.put("requestAmount", "Số tiền thanh toán");
		fieldToHeaderMap.put("amount", "Giá trị đơn hàng");
		fieldToHeaderMap.put("providerName", "Dịch vụ thanh toán");
		fieldToHeaderMap.put("apiOperation", "Loại GD");

		DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern(OneFinConstants.DATE_FORMAT_yyyyMMDDHHmmss);
		DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern(OneFinConstants.DATE_FORMAT_ddSMMSyyyy_HHTDmmTDss);

		Row row = getRow(sheet, rowIndex);
		int cellIndex = 1;  // skip first column (A)
		for (String fieldName : fieldToHeaderMap.keySet()) {
			try {
				Field field = getField(clazz, fieldName);
				field.setAccessible(true);
				Cell cell = getCell(row, cellIndex++);
				if (fieldName.equals("localDateTime")) {
					String dateStr = String.valueOf(field.get(trx));
					LocalDateTime dateTime = LocalDateTime.parse(dateStr, inputFormatter);
					String formattedDate = dateTime.format(outputFormatter);
					cell.setCellValue(formattedDate);
				} else {
					Object fieldValue = field.get(trx);
					cell.setCellValue(fieldValue != null ? String.valueOf(fieldValue) : "");
				}
			} catch (Exception e) {
				LOGGER.error(e.getMessage(), e);
			}
		}
		rowIndex++;
		BigDecimal paidAmount = trx.getRequestAmount();
		if (paidAmount != null) {
			Cell paidAmountCell = getCell(row, 5); // Column F
			paidAmountCell.setCellValue(currencyFormat.format(paidAmount.doubleValue()));
		}
		// Write the amount column
		BigDecimal amount = trx.getAmount();
		if (amount != null) {
			Cell amountCell = getCell(row, 6); // Column G
			amountCell.setCellValue(currencyFormat.format(amount.doubleValue()));
		}
		return rowIndex;
	}

	private Field getField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
		try {
			return clazz.getDeclaredField(fieldName);
		} catch (NoSuchFieldException e) {
			Class<?> superclass = clazz.getSuperclass();
			if (superclass == null) {
				throw e;  // No superclass to look into, rethrow the exception
			} else {
				return getField(superclass, fieldName);  // Recursive call with the superclass
			}
		}
	}

	private int writeImediaHeaderTrx(Sheet sheet, int rowIndex, Class<?> clazz) {
		Map<String, String> fieldToHeaderMap = new LinkedHashMap<>();
		fieldToHeaderMap.put("originalTransId", "Mã GD");
		fieldToHeaderMap.put("billingCode", "Mã người dùng");
		fieldToHeaderMap.put("billName", "Họ tên");
		fieldToHeaderMap.put("transTime", "Thời gian GD");
		fieldToHeaderMap.put("paidAmount", "Số tiền thanh toán");
		fieldToHeaderMap.put("amount", "Giá trị đơn hàng");
		fieldToHeaderMap.put("providerName", "Dịch vụ thanh toán");
		fieldToHeaderMap.put("apiOperation", "Loại GD");

		Row row = getRow(sheet, rowIndex);
		int cellIndex = 1;  // skip first column (A)
		for (String fieldName : fieldToHeaderMap.keySet()) {
			Cell cell = getCell(row, cellIndex++);
			cell.setCellValue(fieldToHeaderMap.get(fieldName));
		}
		rowIndex++;
		return rowIndex;
	}

	private int writeVnPayHeaderTrx(Sheet sheet, int rowIndex, Class<?> clazz) {
		Map<String, String> fieldToHeaderMap = new LinkedHashMap<>();
		fieldToHeaderMap.put("requestId", "Mã yêu cầu");
		fieldToHeaderMap.put("walletId", "Mã ví");
		fieldToHeaderMap.put("mobileNo", "SĐT yêu cầu");
		fieldToHeaderMap.put("localDateTime", "Thời gian GD");
		fieldToHeaderMap.put("requestAmount", "Số tiền thanh toán");
		fieldToHeaderMap.put("amount", "Giá trị đơn hàng");
		fieldToHeaderMap.put("providerName", "Dịch vụ thanh toán");
		fieldToHeaderMap.put("apiOperation", "Loại GD");
		Row row = getRow(sheet, rowIndex);
		int cellIndex = 1;  // skip first column (A)
		for (String fieldName : fieldToHeaderMap.keySet()) {
			Cell cell = getCell(row, cellIndex++);
			cell.setCellValue(fieldToHeaderMap.get(fieldName));
		}
		rowIndex++;
		return rowIndex;
	}

	// Sms
	private int writeHeaderSms(Sheet sheet, int rowIndex, String title) {
		// create CellStyle
		CellStyle cellStyle = createStyleForHeader(sheet);

		// Create row title
		Row row = getRow(sheet, rowIndex);
		Cell cell = getCell(row, COLUMN_INDEX_TELCO);
		cell.setCellValue(title);
		rowIndex += 2;

		// Create cells
		row = getRow(sheet, rowIndex);
		cell = getCell(row, COLUMN_INDEX_TELCO);
		cell.setCellStyle(cellStyle);
		cell.setCellValue("TELCO");
		cell = getCell(row, COLUMN_INDEX_NUMSMS);
		cell.setCellStyle(cellStyle);
		cell.setCellValue("numSMS");
		rowIndex++;

		return rowIndex;
	}

	private int writeTelcoSumSms(Sheet sheet, int rowIndex, String telco, Long sumSms) {
		Row row = getRow(sheet, rowIndex);
		Cell cell = getCell(row, COLUMN_INDEX_TELCO);
		cell.setCellValue(telco);
		cell = getCell(row, COLUMN_INDEX_NUMSMS);
		cell.setCellValue(sumSms);
		rowIndex++;
		return rowIndex;
	}

	private void writeFooterSms(Sheet sheet, int rowIndex, Map<String, Long> telcoSumSmsMap) {
		// Create row
		Row row = getRow(sheet, rowIndex);

		Cell cell = getCell(row, COLUMN_INDEX_TELCO);
		cell.setCellValue("TOTAL");

		// totalSms = sum(numSms)
		cell = getCell(row, COLUMN_INDEX_NUMSMS);
		cell.setCellValue(telcoSumSmsMap.values().stream().mapToLong(Long::valueOf).sum());
	}

	private int writeHeaderTrx(Sheet sheet, int rowIndex, Class<?> clazz) {
		Field[] fieldsClass = clazz.getDeclaredFields();
		Field[] fieldSupperClass = clazz.getSuperclass().getDeclaredFields();
		List<Field> fields = new ArrayList<>();
		for (int i = 0; i < fieldSupperClass.length; i++) {
			fields.add(fieldSupperClass[i]);
		}
		for (int i = 0; i < fieldsClass.length; i++) {
			fields.add(fieldsClass[i]);
		}
		Row row = getRow(sheet, rowIndex);
		for (int i = 0; i < fields.size(); i++) {
			Cell cell = getCell(row, i);
			cell.setCellValue(fields.get(i).getName());
		}
		rowIndex++;
		return rowIndex;
	}

	private int writeTrx(Sheet sheet, int rowIndex, Object trx, Class<?> clazz) {
		Field[] fieldsClass = clazz.getDeclaredFields();
		Field[] fieldsSupperClass = clazz.getSuperclass().getDeclaredFields();
		List<Field> fields = new ArrayList<>();
		for (int i = 0; i < fieldsSupperClass.length; i++) {
			fields.add(fieldsSupperClass[i]);
		}
		for (int i = 0; i < fieldsClass.length; i++) {
			fields.add(fieldsClass[i]);
		}
		Row row = getRow(sheet, rowIndex);
		for (int i = 0; i < fields.size(); i++) {
			Cell cell = getCell(row, i);
			try {
				Field field;
				if (fields.get(i).getDeclaringClass().getName().equals(AbstractBaseEwalletTrans.class.getName())) {
					field = trx.getClass().getSuperclass().getDeclaredField(fields.get(i).getName());
				} else {
					field = trx.getClass().getDeclaredField(fields.get(i).getName());
				}
				field.setAccessible(true);
				cell.setCellValue(String.valueOf(field.get(trx)));
			} catch (Exception e) {
				LOGGER.error(e.getMessage(), e);
			}
		}
		rowIndex++;
		return rowIndex;
	}


	public void writeBillExcel(List<VietinBillReport> vietinBillReports, String excelFilePath) throws IOException {
		Workbook workbook = null;
		try {
			// Create Workbook
			workbook = super.createWorkbook(excelFilePath);
			// Create sheet
			Sheet sheet = workbook.createSheet("transaction");
			int rowIndex = 0;
			// Write header
			rowIndex = writeHeaderBillReport(sheet, rowIndex, VietinBillReport.class);
			// Write data
			for (VietinBillReport e : vietinBillReports) {
				// Write data on row
				rowIndex = writeBillReport(sheet, rowIndex, e, VietinBillReport.class);
			}
			// Auto resize column witdth
			int numberOfColumn = getRow(sheet, 0).getPhysicalNumberOfCells();
			autosizeColumn(sheet, numberOfColumn);
			// Create file excel
			createOutputFile(workbook, excelFilePath);
		} catch (Exception e) {
			LOGGER.error("Cannot write excel file", e);
		} finally {
			workbook.close();
		}
	}

	public void writeBillSummaryExcel(
			List<VietinBillSummary> vietinBillSummarys,
			String excelFilePath) throws IOException {
		Workbook workbook = null;
		try {
			// Create Workbook
			workbook = super.createWorkbook(excelFilePath);
			// Create sheet
			Sheet sheet = workbook.createSheet("transaction");
			int rowIndex = 0;
			// Write header
			rowIndex = writeHeaderBillReport(sheet, rowIndex, VietinBillSummary.class);
			// Write data
			for (VietinBillSummary e : vietinBillSummarys) {
				// Write data on row
				rowIndex = writeBillSummaryReport(sheet, rowIndex, e, VietinBillSummary.class);
			}
			// Auto resize column witdth
			int numberOfColumn = getRow(sheet, 0).getPhysicalNumberOfCells();
			autosizeColumn(sheet, numberOfColumn);
			// Create file excel
			createOutputFile(workbook, excelFilePath);
		} catch (Exception e) {
			LOGGER.error("Cannot write excel file", e);
		} finally {
			workbook.close();
		}
	}


	private int writeHeaderBillReport(Sheet sheet, int rowIndex, Class<?> clazz) {
		Field[] fields = clazz.getDeclaredFields();
		Row row = getRow(sheet, rowIndex);
		for (int i = 0; i < fields.length; i++) {
			Cell cell = getCell(row, i);
			cell.setCellValue(fields[i].getName());
		}
		rowIndex++;
		return rowIndex;
	}

	private int writeBillReport(Sheet sheet, int rowIndex, VietinBillReport trx, Class<?> clazz) {
		Field[] fields = clazz.getDeclaredFields();
		Row row = getRow(sheet, rowIndex);
		for (int i = 0; i < fields.length; i++) {
			Cell cell = getCell(row, i);
			try {
				Field field = trx.getClass().getDeclaredField(fields[i].getName());
				field.setAccessible(true);
				cell.setCellValue(String.valueOf(field.get(trx)));
			} catch (Exception e) {
				LOGGER.error(e.getMessage(), e);
			}
		}
		rowIndex++;
		return rowIndex;
	}

	private int writeBillSummaryReport(Sheet sheet, int rowIndex, VietinBillSummary trx, Class<?> clazz) {
		Field[] fields = clazz.getDeclaredFields();
		Row row = getRow(sheet, rowIndex);
		for (int i = 0; i < fields.length; i++) {
			Cell cell = getCell(row, i);
			try {
				Field field = trx.getClass().getDeclaredField(fields[i].getName());
				field.setAccessible(true);
				cell.setCellValue(String.valueOf(field.get(trx)));
			} catch (Exception e) {
				LOGGER.error(e.getMessage(), e);
			}
		}
		rowIndex++;
		return rowIndex;
	}

	private int writeBillByBankCodeAndCreatedDate(Sheet sheet, int rowIndex, VietinVirtualAcctTransHistory trx, Class<?> clazz) {
		Field[] fields = clazz.getDeclaredFields();
		Row row = getRow(sheet, rowIndex);
		for (int i = 0; i < fields.length; i++) {
			Cell cell = getCell(row, i);
			try {
				Field field = trx.getClass().getDeclaredField(fields[i].getName());
				field.setAccessible(true);
				cell.setCellValue(String.valueOf(field.get(trx)));
			} catch (Exception e) {
				LOGGER.error(e.getMessage(), e);
			}
		}
		rowIndex++;
		return rowIndex;
	}

	public void writeExcelByBankCodeAndCreatedDate(List<VietinVirtualAcctTransHistory> trxDetails, String date, String excelFilePath) throws IOException {
		Workbook workbook = null;
		InputStream templateInputStream = null;
		try {
			// Load template file from resources folder
			ClassLoader classLoader = getClass().getClassLoader();
			templateInputStream = classLoader.getResourceAsStream("template.xlsx");
			// If the template file isn't found in the resources folder, you can't proceed
			if (templateInputStream == null) {
				throw new FileNotFoundException("Template file not found in resources folder");
			}
			workbook = new XSSFWorkbook(templateInputStream);
			// get the sheet from workbook
			Sheet sheet = workbook.getSheetAt(0);
			// write headers into the sheet at 7th row using modified writeHeaderTrx
			int rowIndex = 6;  // since headers start from row 7 (0-based index)
			rowIndex = writeHeaderExcel(sheet, rowIndex, VietinVirtualAcctTransHistory.class);
			// write data into the sheet
			int counter = 1;
			NumberFormat currencyFormat = NumberFormat.getNumberInstance();
			currencyFormat.setGroupingUsed(true);
			for (VietinVirtualAcctTransHistory e : trxDetails) {
				// Write data on row
				rowIndex = writeBodyExcel(sheet, rowIndex, e, VietinVirtualAcctTransHistory.class, currencyFormat);
				counter++;
			}
			// Set counter value in column A starting from row 8
			rowIndex = 7;  // Starting from row 8 (0-based index 7)
			for (int i = 0; i < trxDetails.size(); i++) {
				Row row = getRow(sheet, rowIndex++);
				Cell cell = getCell(row, 0); // Column A
				cell.setCellValue(i + 1);  // Set counter value
			}
			// Apply formatting to the cells
			CellStyle style = workbook.createCellStyle();
			style.setAlignment(HorizontalAlignment.CENTER);
			style.setVerticalAlignment(VerticalAlignment.CENTER);
			style.setBorderTop(BorderStyle.THIN);
			style.setBorderRight(BorderStyle.THIN);
			style.setBorderBottom(BorderStyle.THIN);
			Font font = workbook.createFont();
			font.setFontName("Times New Roman");
			style.setFont(font);
			for (int i = 7; i < rowIndex; i++) {
				Row row = getRow(sheet, i);
				for (int j = 0; j < row.getLastCellNum(); j++) {
					Cell cell = getCell(row, j);
					cell.setCellStyle(style);
				}
			}
			// write aggregated data
			DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern(OneFinConstants.DATE_FORMAT_TRANS);
			DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern(OneFinConstants.DATE_FORMAT_dd_MM_yyyy);
			BigDecimal totalPaidAmount = trxDetails.stream()
					.map(VietinVirtualAcctTransHistory::getAmount)
					.filter(Objects::nonNull) // Filter out null values
					.reduce(BigDecimal.ZERO, BigDecimal::add);
			Row row1 = sheet.getRow(1);
			if (row1 == null) {
				row1 = sheet.createRow(1);
			}
			Cell cellF2 = row1.getCell(5);
			if (cellF2 == null) {
				cellF2 = row1.createCell(5);
			}
			cellF2.setCellValue(date);
			Cell cellF3 = sheet.getRow(2).getCell(5);
			cellF3.setCellValue(date);
			Cell cellF4 = sheet.getRow(3).getCell(5);
			cellF4.setCellValue(trxDetails.size());
			Cell cellF5 = sheet.getRow(4).getCell(5);
			cellF5.setCellValue(totalPaidAmount.doubleValue());
			// Save file to excelFilePath
			try (FileOutputStream outputStream = new FileOutputStream(excelFilePath)) {
				workbook.write(outputStream);
			}

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
	}

	//Map data vào excel
	private int writeBodyExcel(Sheet sheet, int rowIndex, VietinVirtualAcctTransHistory trx, Class<?> clazz, NumberFormat currencyFormat) {
		Map<String, String> fieldToHeaderMap = new LinkedHashMap<>();
		fieldToHeaderMap.put("amount", "Amount");
		fieldToHeaderMap.put("bankCode", "Bank Code");
		fieldToHeaderMap.put("createdDate", "Ngày tạo giao dịch");
		fieldToHeaderMap.put("updatedDate", "Ngày cập nhật giao dịch");
		fieldToHeaderMap.put("expireTime", "Ngày hết hạn giao dịch");
		fieldToHeaderMap.put("tranStatus", "Status");
		fieldToHeaderMap.put("virtualAcctVar", "Virtual Account");
		fieldToHeaderMap.put("merchantCode", "Merchant code");

		Row row = getRow(sheet, rowIndex);
		int cellIndex = 1;  // skip first column (A)
		for (String fieldName : fieldToHeaderMap.keySet()) {
			try {
				Field field = getField(clazz, fieldName);
				field.setAccessible(true);
				Cell cell = getCell(row, cellIndex++);
				Object fieldValue = field.get(trx);
				cell.setCellValue(fieldValue != null ? String.valueOf(fieldValue) : "");
			} catch (Exception e) {
				LOGGER.error(e.getMessage(), e);
			}
		}
		rowIndex++;
		return rowIndex;
	}


	private int writeHeaderExcel(Sheet sheet, int rowIndex, Class<?> clazz) {
		Map<String, String> fieldToHeaderMap = new LinkedHashMap<>();
		fieldToHeaderMap.put("amount", "Amount");
		fieldToHeaderMap.put("bankCode", "Bank Code");
		fieldToHeaderMap.put("createdDate", "Ngày tạo giao dịch");
		fieldToHeaderMap.put("updatedDate", "Ngày cập nhật giao dịch");
		fieldToHeaderMap.put("expireTime", "Ngày hết hạn giao dịch");
		fieldToHeaderMap.put("tranStatus", "Status");
		fieldToHeaderMap.put("virtualAcctVar", "Virtual Account");
		fieldToHeaderMap.put("merchantCode", "Merchant code");

		Row row = getRow(sheet, rowIndex);
		int cellIndex = 1;  // skip first column (A)
		for (String fieldName : fieldToHeaderMap.keySet()) {
			Cell cell = getCell(row, cellIndex++);
			cell.setCellValue(fieldToHeaderMap.get(fieldName));
		}
		rowIndex++;
		return rowIndex;
	}

}