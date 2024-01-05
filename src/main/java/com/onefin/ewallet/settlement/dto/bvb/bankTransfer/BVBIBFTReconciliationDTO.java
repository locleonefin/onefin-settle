package com.onefin.ewallet.settlement.dto.bvb.bankTransfer;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class BVBIBFTReconciliationDTO {

	@NotNull
	private String clientCode;

	private String transactionId;

	private String transactionDate;

	private String cardNo;

	private String cardInd;

	private String businessType;

	private String amount;

	private String currencyCode;

	private String description;

	private String bankTransactionId;

	private String reversalIndicator;

	private String transactionStatus;

	private String transactionType;

	private String checkSum;

	public BVBIBFTReconciliationDTO() {

	}

	public BVBIBFTReconciliationDTO(String clientCode,
									String transactionId,
									String transactionDate,
									String cardNo,
									String cardInd,
									String businessType,
									String amount,
									String currencyCode,
									String description,
									String bankTransactionId,
									String reversalIndicator,
									String transactionStatus,
									String transactionType) {
		this.clientCode = clientCode;
		this.transactionId = transactionId;
		this.transactionDate = transactionDate;
		this.cardNo = cardNo;
		this.cardInd = cardInd;
		this.businessType = businessType;
		this.amount = amount;
		this.currencyCode = currencyCode;
		this.description = description;
		this.bankTransactionId = bankTransactionId;
		this.reversalIndicator = reversalIndicator;
		this.transactionStatus = transactionStatus;
		this.transactionType = transactionType;
	}

	public String getCheckSumString() {
		return clientCode +
				transactionId +
				transactionDate +
				cardNo +
				cardInd +
				businessType +
				amount +
				currencyCode +
				description +
				bankTransactionId +
				reversalIndicator +
				transactionStatus +
				transactionType;
	}

	public String getReconciliationLine() {
		return clientCode + "," +
				transactionId + "," +
				transactionDate + "," +
				cardNo + "," +
				cardInd + "," +
				businessType + "," +
				amount + "," +
				currencyCode + "," +
				description + "," +
				bankTransactionId + "," +
				reversalIndicator + "," +
				transactionStatus + "," +
				transactionType + "," +
				checkSum;
	}
}