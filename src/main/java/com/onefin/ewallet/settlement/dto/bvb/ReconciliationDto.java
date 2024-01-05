package com.onefin.ewallet.settlement.dto.bvb;


import com.onefin.ewallet.common.base.errorhandler.RuntimeInternalServerException;
import com.onefin.ewallet.common.domain.bank.vietin.VietinNotifyTransTable;
import com.onefin.ewallet.settlement.common.SettleConstants;
import com.opencsv.bean.CsvBindByPosition;
import lombok.Data;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Data
public class ReconciliationDto {

	@CsvBindByPosition(position = 0)
	private String externalRefNo;

	@CsvBindByPosition(position = 1)
	private String transactionDate;

	@CsvBindByPosition(position = 2)
	private String partnerCode;

	@CsvBindByPosition(position = 3)
	private String relatedAccount;

	@CsvBindByPosition(position = 4)
	private String creditAccount;

	@CsvBindByPosition(position = 5)
	private BigDecimal amount;

	@CsvBindByPosition(position = 6)
	private String ccy;

	@CsvBindByPosition(position = 7)
	private String trnRefNo;

	@CsvBindByPosition(position = 8)
	private String narrative;

	@CsvBindByPosition(position = 9)
	private String clientUserId;

	@CsvBindByPosition(position = 10)
	private String fcCoreRef;

	@CsvBindByPosition(position = 11)
	private String napasTraceId;



	public String toStringExport() {

		String returnString = "";


		List<String> strings = new ArrayList<>();
		strings = Arrays.asList(externalRefNo, transactionDate, partnerCode,
				relatedAccount, creditAccount, amount.toString(), ccy, trnRefNo, narrative, clientUserId);

		return String.join("|", strings);
	}

	public String getReconciliationString() {
		return trnRefNo + "|" + amount + "|" + relatedAccount + "|" + externalRefNo;
	}


}
