package com.onefin.ewallet.settlement.service.bvbank;

import com.onefin.ewallet.common.base.constants.OneFinConstants;
import com.onefin.ewallet.common.base.constants.OneFinEnum;
import com.onefin.ewallet.common.base.errorhandler.RuntimeInternalServerException;
import com.onefin.ewallet.common.domain.bank.common.BankList;
import com.onefin.ewallet.common.domain.bank.transfer.BankTransferChildRecords;
import com.onefin.ewallet.common.domain.bank.transfer.BankTransferTransaction;
import com.onefin.ewallet.common.domain.constants.DomainConstants;
import com.onefin.ewallet.common.domain.holiday.Holiday;
import com.onefin.ewallet.common.utility.date.DateTimeHelper;
import com.onefin.ewallet.settlement.dto.bvb.bankTransfer.BVBIBFTReconciliationDTO;
import com.onefin.ewallet.settlement.repository.BankListRepository;
import com.onefin.ewallet.settlement.repository.BankTransferRepo;
import com.onefin.ewallet.settlement.repository.ChildBankTransferRecordsRepo;
import com.onefin.ewallet.settlement.repository.HolidayRepo;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BVBIBFTPersistance {

	private static final org.apache.logging.log4j.Logger LOGGER = LogManager.getLogger(BVBIBFTPersistance.class);

	@Autowired
	private DateTimeHelper dateTimeHelper;

	@Autowired
	private BankListRepository bankListRepository;

	@Autowired
	private BankTransferRepo bankTransferRepo;

	@Autowired
	private ChildBankTransferRecordsRepo childBankTransferRecordsRepo;

	@Autowired
	private BVBEncryptUtil bvbEncryptUtil;

	@Autowired
	private HolidayRepo holidayRepo;
	@Value("${bvb.IBFT.onefinClientCode}")
	private String clientCode;

	@Value("${bvb.IBFT.onefinMerchantCode}")
	private String requestIdPrefix;

	public List<BVBIBFTReconciliationDTO> getListIBFTReconciliationDTO(List<BankTransferTransaction> bankTransferTransactionList) {
		return bankTransferTransactionList.stream().map(
				e -> {
					String transactionDateLog1 = dateTimeHelper.parseDate2String(e.getUpdatedDate(),
							DomainConstants.DATE_FORMAT_TRANS9);

					BankTransferChildRecords childRecord = new ArrayList<>(e.getRecords()).get(0);
					String currencyCode = childRecord.getCurrency() == null ? "VND" : childRecord.getCurrency();
					String bankTransId = childRecord.getBankTransactionId()==null?"":childRecord.getBankTransactionId();
					BVBIBFTReconciliationDTO bvbibftReconciliationDTO1
							= new BVBIBFTReconciliationDTO(clientCode,
							childRecord.getTransId(), transactionDateLog1, "",
							"N", e.getFeeType(), String.valueOf(childRecord.getAmount().intValue()), currencyCode, "",
							bankTransId, "N", childRecord.getBankStatusCode(), "2601");
					bvbibftReconciliationDTO1.setCheckSum(bvbEncryptUtil.MD5Hashing(bvbibftReconciliationDTO1.getCheckSumString()));
					return bvbibftReconciliationDTO1;
				}
		).collect(Collectors.toList());
	}

}
