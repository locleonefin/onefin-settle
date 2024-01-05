package com.onefin.ewallet.settlement.dto.bvb;

import com.onefin.ewallet.settlement.common.SettleConstants;
import lombok.Data;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;


@Data
public class ReconciliationDtoExport {

	private String transId;

	private Date txnInitDt;

	private String partnerCode;

	// Related account
	private String recvVirtualAcctId;

	// Credit account
	private String creditAccount;

	// amount
	private String amount;

	// ccy
	private String currencyCode;

	// trnRefNo when callback I
	private String trnRefNo;

	// trnRefNo when callback B
	private String traceId;

	// narrative
	private String remark;

	// client user id
	private String custCode;

	private String msgType;

	private String napasTraceId;

	private String fcCoreRef;

	public String getTxnInitDt(){
		SimpleDateFormat formatter =
				new SimpleDateFormat(SettleConstants.BVB_DATE_STRING_HEADER_FORMAT);
		return formatter.format(txnInitDt);
//		if (msgType.equals("I")){
//			SimpleDateFormat formatter =
//					new SimpleDateFormat(SettleConstants.BVB_DATE_STRING_HEADER_FORMAT);
//			return formatter.format(txnInitDt);
//		}else{
//			return "";
//		}
	}

	public String getTrnRefNoExport(){
		if (msgType.equals("I")){
			return trnRefNo;
		}else{
			return traceId;
		}
	}

	public String toStringExport(){

		String returnString = "";

		SimpleDateFormat formatter =
				new SimpleDateFormat(SettleConstants.BVB_DATE_STRING_HEADER_FORMAT);
		String dateConvert = formatter.format(txnInitDt);

		List<String> strings = new ArrayList<>();
		if (msgType.equals("I")){
			strings = Arrays.asList(transId, dateConvert,partnerCode,
					recvVirtualAcctId,creditAccount,amount, currencyCode, trnRefNo,remark, custCode,msgType);
		}else if (msgType.equals("B")){
			strings = Arrays.asList(transId, "",partnerCode,
					recvVirtualAcctId,creditAccount,amount, currencyCode, traceId,remark, custCode,msgType);
		}

		return String.join("|",strings);
	}
}
