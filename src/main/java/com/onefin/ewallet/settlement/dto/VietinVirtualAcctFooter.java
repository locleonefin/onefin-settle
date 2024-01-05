package com.onefin.ewallet.settlement.dto;

import com.opencsv.bean.CsvBindByPosition;
import lombok.Data;

@Data
public class VietinVirtualAcctFooter {

	@CsvBindByPosition(position = 0)
	private String recordType;

	@CsvBindByPosition(position = 1)
	private String providerId;

  @CsvBindByPosition(position = 2)
  private String userID;

  @CsvBindByPosition(position = 3)
	private String recordNo;

	@CsvBindByPosition(position = 4)
	private String transTime;

	@CsvBindByPosition(position = 5)
	private String fileChecksum;
}
