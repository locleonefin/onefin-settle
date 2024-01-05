package com.onefin.ewallet.settlement.dto;

import com.opencsv.bean.CsvBindByPosition;
import lombok.Data;

@Data
public class VietinDisbursement {

	@CsvBindByPosition(position = 0)
	private String recordType;

	@CsvBindByPosition(position = 1)
	private String transId; // Note: providerId at footer

	@CsvBindByPosition(position = 2)
	private String merchantId; // Note: userID at footer

	@CsvBindByPosition(position = 3)
	private String bankCode; // Note: recordNo at footer

	@CsvBindByPosition(position = 4)
	private String branchCode; // Note: transTime at footer

	@CsvBindByPosition(position = 5)
	private String custAcctNo; // Note: fileChecksum at footer

	@CsvBindByPosition(position = 6)
	private String amount;

	@CsvBindByPosition(position = 7)
	private String currencyCode;

	@CsvBindByPosition(position = 8)
	private String payRefNo;

	@CsvBindByPosition(position = 9)
	private String merchantAcctNo;

	@CsvBindByPosition(position = 10)
	private String transTime;

	@CsvBindByPosition(position = 11)
	private String status;

	@CsvBindByPosition(position = 12)
	private String bankTransId;

	//@CsvBindByPosition(position = 13)
	private String reconcileStatus;

	@CsvBindByPosition(position = 13)
	private String recordChecksum;

}
