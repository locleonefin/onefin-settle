package com.onefin.ewallet.settlement.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.onefin.ewallet.common.domain.asc.AscSchoolBankAccount;
import com.onefin.ewallet.common.domain.constants.DomainConstants;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class AscTransactionDetailsFiltered {

	private String trxUniqueKey; // From OneFin or partner

	@JsonProperty("requestId")
	public void setRequestId(String requestId) {
		this.trxUniqueKey = requestId;
	}

	private String itemCode;

	private String itemName;

	private BigDecimal price;

	private int quantity;

	private BigDecimal itemAmount;

	@JsonProperty(value = "price")
	public void setPrice(BigDecimal price) {
		this.price = price.divide(new BigDecimal(100));
	}

	@JsonProperty(value = "itemAmount")
	public void setItemAmount(BigDecimal itemAmount) {
		this.itemAmount = itemAmount.divide(new BigDecimal(100));
	}

	@JsonIgnore
	private String merchantCode;

	@JsonProperty("associateMerchantCode")
	public void setAssociateMerchantCode(String associateMerchantCode) {
		this.merchantCode = associateMerchantCode;
	}

	private String merchantAccId;

	private String bankBranchName;

	private String accountNo;

	private String bankName;

	@JsonProperty(value = "bankAccount")
	public void setBankAccount(AscSchoolBankAccount bankAccount) {
		if (bankAccount != null) {
			this.merchantAccId = bankAccount.getAccId();
			this.bankBranchName = bankAccount.getBankDetails().getBranchName();
			this.accountNo = bankAccount.getAccountNo();
			this.bankName = bankAccount.getBankDetails().getBankList().getName();
		}
	}

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DomainConstants.DATE_FORMAT_TRANS, timezone = DomainConstants.HO_CHI_MINH_TIME_ZONE)
	private Date createdDate;

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DomainConstants.DATE_FORMAT_TRANS, timezone = DomainConstants.HO_CHI_MINH_TIME_ZONE)
	private Date modifiedDate;

	@JsonProperty("modifiedDate")
	public void setModifiedDate(Date modifiedDate) {
		this.modifiedDate = modifiedDate;
	}

	private String trxId;

	@JsonProperty("trxId")
	public void settrxId(String trxId) {
		this.trxId = trxId;
	}


	private String shopName;

	@JsonProperty("shopName")
	public void setshopName(String shopName) {
		this.shopName = shopName;
	}


	private String transStatus;

	@JsonProperty("transStatus")
	public void settransStatus(String transStatus) {
		this.transStatus = transStatus;
	}

	private String memberId;

	@JsonProperty("memberId")
	public void setmemberId(String memberId) {
		this.memberId = memberId;
	}

	private String lastName;

	@JsonProperty("lastName")
	public void setlastName(String lastName) {
		this.lastName = lastName;
	}

	private String firstName;

	@JsonProperty("firstName")
	public void setfirstName(String firstName) {
		this.firstName = firstName;
	}

	private String addressLine1;

	@JsonProperty("addressLine1")
	public void setaddressLine1(String addressLine1) {
		this.addressLine1 = addressLine1;
	}

	private String addressLine2;

	@JsonProperty("addressLine2")
	public void setaddressLine2(String addressLine2) {
		this.addressLine2 = addressLine2;
	}

	private String channelType;

	public AscTransactionDetailsFiltered() {
	}


	public AscTransactionDetailsFiltered(String shopName, String memberId, String firstName, String lastName, String addressLine1, Date createdDate, String trxUniqueKey, String trxId, BigDecimal itemAmount, String channelType, String accountNo, String bankName, String transStatus) {
		this.shopName = shopName;
		this.memberId = memberId;
		this.firstName = firstName;
		this.lastName = lastName;
		this.addressLine1 = addressLine1;
		this.createdDate = createdDate;
		this.trxUniqueKey = trxUniqueKey;
		this.trxId = trxId;
		this.itemAmount = itemAmount;
		this.channelType = channelType;
		this.accountNo = accountNo;
		this.bankName = bankName;
		this.transStatus = transStatus;
	}
}
