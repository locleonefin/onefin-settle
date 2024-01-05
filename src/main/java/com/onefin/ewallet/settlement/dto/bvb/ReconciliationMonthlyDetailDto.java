package com.onefin.ewallet.settlement.dto.bvb;

import lombok.Data;

@Data
public class ReconciliationMonthlyDetailDto {

	private String stt;

	private String virtualAccount;

	private String refNum;

	private String transDate;

	private String valueDate;

	private String creditAmount;

	private String debitAmount;

	private String interest;

	private String balance;

	private String narrative;

	private String tellerCode;

	private String trnCode;

	private String externalRefNo;

	private String transChannel;

	private String traceId;
}
