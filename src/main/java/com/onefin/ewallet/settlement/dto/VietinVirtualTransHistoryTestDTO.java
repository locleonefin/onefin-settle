package com.onefin.ewallet.settlement.dto;

import org.codehaus.jackson.annotate.JsonProperty;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Date;

public class VietinVirtualTransHistoryTestDTO {
	@NotNull(message = "bankCode can't be null")
	@NotEmpty(message = "bankCode can't be empty")
	@JsonProperty("bankCode")
	private String bankCode;

	@NotNull(message = "Status can't be null")
	@NotEmpty(message = "status can't be empty")
	@JsonProperty("tranStatus")
	private String tranStatus;

	@NotNull(message = "createdDate can't be null")
	@VietinValidator()
	@JsonProperty("createdDate")
	private Date createdDate;

	private BigDecimal amount;

	private Date expireTime;

	private Date updatedDate;
	private String merchantCode;
	private String virtualAcctVar;

	public Date getExpireTime() {
		return expireTime;
	}

	public void setExpireTime(Date expireTime) {
		this.expireTime = expireTime;
	}

	public Date getUpdatedDate() {
		return updatedDate;
	}

	public void setUpdatedDate(Date updatedDate) {
		this.updatedDate = updatedDate;
	}

	public String getMerchantCode() {
		return merchantCode;
	}

	public void setMerchantCode(String merchantCode) {
		this.merchantCode = merchantCode;
	}

	public String getVirtualAcctVar() {
		return virtualAcctVar;
	}

	public void setVirtualAcctVar(String virtualAcctVar) {
		this.virtualAcctVar = virtualAcctVar;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public String getBankCode() {
		return bankCode;
	}

	public void setBankCode(String bankCode) {
		this.bankCode = bankCode;
	}

	public String getTranStatus() {
		return tranStatus;
	}

	public void setTranStatus(String tranStatus) {
		this.tranStatus = tranStatus;
	}

	public Date getCreatedDate() {
		return createdDate;
	}

	public void setCreatedDate(Date createdDate) {
		this.createdDate = createdDate;
	}

//	@JsonProperty("createdDate")
//	public void setCreatedDate(String createdDate) {
//		LocalDateTime dateTime = LocalDateTime.parse("2018-05-05T11:50:55");
//
//	}

}
