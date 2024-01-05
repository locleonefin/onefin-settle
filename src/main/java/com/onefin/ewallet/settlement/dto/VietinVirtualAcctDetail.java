package com.onefin.ewallet.settlement.dto;

import com.opencsv.bean.CsvBindByPosition;
import lombok.Data;

@Data
public class VietinVirtualAcctDetail {

	@CsvBindByPosition(position = 0)
	private String recordType = "";

	@CsvBindByPosition(position = 1)
	private String transId = "";

  @CsvBindByPosition(position = 2)
  private String providerId = "";

  @CsvBindByPosition(position = 3)
	private String merchantId = "";

	@CsvBindByPosition(position = 4)
	private String bankCode = "";

	@CsvBindByPosition(position = 5)
	private String branchCode = "";

	@CsvBindByPosition(position = 6)
	private String custAcctNo = "";

  @CsvBindByPosition(position = 7)
  private String recvVirtualAcctId = "";
  @CsvBindByPosition(position = 8)
  private String recvVirtualAcctName = "";
  @CsvBindByPosition(position = 9)
  private String custCode = "";
  @CsvBindByPosition(position = 10)
  private String custName = "";
  @CsvBindByPosition(position = 11)
  private String amount = "";
  @CsvBindByPosition(position = 12)
  private String currencyCode = "";
  @CsvBindByPosition(position = 13)
  private String payRefNo = "";
  @CsvBindByPosition(position = 14)
  private String merchantAcctNo = "";
  @CsvBindByPosition(position = 15)
  private String billCycle = "";
  @CsvBindByPosition(position = 16)
  private String billId = "";
  @CsvBindByPosition(position = 17)
  private String transTime = "";
  @CsvBindByPosition(position = 18)
  private String status = "";
  @CsvBindByPosition(position = 19)
  private String bankTransId = "";

  @CsvBindByPosition(position = 20)
  private String addField1 = "";

  @CsvBindByPosition(position = 21)
  private String addField2 = "";

  @CsvBindByPosition(position = 22)
  private String addField3 = "";

  @CsvBindByPosition(position = 23)
  private String addField4 = "";

  @CsvBindByPosition(position = 24)
  private String addField5 = "";

  @CsvBindByPosition(position = 25)
  private String recordChecksum = "";

}
