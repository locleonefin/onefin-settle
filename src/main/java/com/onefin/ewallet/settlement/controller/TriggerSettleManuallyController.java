package com.onefin.ewallet.settlement.controller;

import com.onefin.ewallet.settlement.common.SettleConstants;
import com.onefin.ewallet.settlement.service.asc.AscDailyReportSendEmail;
import com.onefin.ewallet.settlement.service.imedia.ImediaBillPayScheduleTask;
import com.onefin.ewallet.settlement.service.imedia.ImediaTopupScheduleTask;
import com.onefin.ewallet.settlement.service.vietin.billpay.VietinBillPayCheckDispute;
import com.onefin.ewallet.settlement.service.vietin.billpay.VietinBillPayInit;
import com.onefin.ewallet.settlement.service.vietin.disbursement.VietinDisbursementIn;
import com.onefin.ewallet.settlement.service.vietin.linkbank.VietinLinkBankCheckDispute;
import com.onefin.ewallet.settlement.service.vietin.linkbank.VietinLinkBankInit;
import com.onefin.ewallet.settlement.service.vietin.virtualAcct.VietinVirtualAcctSv;
import com.onefin.ewallet.settlement.service.vnpay.VNPayAirtimeScheduleTask;
import com.onefin.ewallet.settlement.service.vnpay.VNPaySmsScheduleTask;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;

@RestController
@RequestMapping("/settle/trigger")
public class TriggerSettleManuallyController {

	private static final Logger LOGGER = LoggerFactory.getLogger(TriggerSettleManuallyController.class);

	@Autowired
	private VNPayAirtimeScheduleTask vnPayAirtimeScheduleTask;

	@Autowired
	private ImediaBillPayScheduleTask imediaBillPayScheduleTask;

	@Autowired
	private ImediaTopupScheduleTask imediaTopupScheduleTask;

	@Autowired
	private VNPaySmsScheduleTask vnPaySmsScheduleTask;

	@Autowired
	private VietinDisbursementIn vietinDisbursementIn;

	@Autowired
	private VietinVirtualAcctSv vietinVirtualAcctSv;

	@Autowired(required = false)
	private VietinLinkBankInit vietinLinkBankInit;

	@Autowired(required = false)
	private VietinLinkBankCheckDispute vietinLinkBankCheckDispute;

	@Autowired(required = false)
	private VietinBillPayInit vietinBillPayInit;

	@Autowired(required = false)
	private VietinBillPayCheckDispute vietinBillPayCheckDispute;

	@Autowired(required = false)
	private AscDailyReportSendEmail ascDailyReportSendEmail;

	/********************** Vietin Link Bank **********************/

	@GetMapping("/vietin/link-bank/settleDate_ddMMyyyy/{settleDate}")
	public @ResponseBody
	ResponseEntity<?> manuallyVietinESettlement(@PathVariable(required = true) String settleDate,
												HttpServletRequest request) throws Exception {
		LOGGER.info("== RequestID {} - Start VietinBank Settlement");
		vietinLinkBankInit.scheduleTaskEwalletVietinSettlementManually(settleDate);
		return new ResponseEntity<>("Settlement VietinBank processing", HttpStatus.OK);
	}

	@RequestMapping(method = RequestMethod.GET, value = "/vietin/link-bank/disputeSettleDate_ddMMyyyy/{settleDate}")
	public @ResponseBody
	ResponseEntity<?> manuallyVietinESettlementDispute(
			@PathVariable(required = false) String settleDate, HttpServletRequest request) throws Exception {
		LOGGER.info("{} {} Start dispute process manually", SettleConstants.PARTNER_VIETINBANK,
				SettleConstants.LINK_BANK);
		vietinLinkBankCheckDispute.ewalletVietinDisputeSettlementManually(settleDate);
		return new ResponseEntity<>("Settlement dispute vietinBank processing", HttpStatus.OK);
	}

	/********************** Vietin Link Bank **********************/

	/********************** Bill Payment **********************/

	@GetMapping("/trigger/paybill/settleDate_ddMMyyyy/{settleDate}")
	public @ResponseBody
	ResponseEntity<?> manuallyVietinBillPaySettlement(
			@PathVariable(required = true) String settleDate, HttpServletRequest request) throws Exception {
		LOGGER.info("== RequestID {} - Start VietinBank Bill Payment Settlement");
		vietinBillPayInit.scheduleTaskVietinBillPaymentSettlementManually(settleDate);
		return new ResponseEntity<>("Settlement VietinBank Bill Payment processing", HttpStatus.OK);
	}

	@RequestMapping(method = RequestMethod.GET, value = "/trigger/paybill/disputeSettleDate_ddMMyyyy/{settleDate}")
	public @ResponseBody
	ResponseEntity<?> manuallyVietinBillPaymentSettlementDispute(
			@PathVariable(required = false) String settleDate, HttpServletRequest request) throws Exception {
		LOGGER.info("== RequestID {} - Start VietinBank Bill Payment Settlement");
		vietinBillPayCheckDispute.ewalletVietinBillPaymentDisputeSettlementManually(settleDate);
		return new ResponseEntity<>("Settlement VietinBank Bill Payment processing", HttpStatus.OK);
	}

	/********************** Bill Payment **********************/

	@GetMapping("/vnpay/sms")
	public @ResponseBody
	ResponseEntity<?> settleSmsFile(@RequestParam(value = "settleDate") @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateInSettleMonth) throws Exception {
		vnPaySmsScheduleTask.scheduleTaskVNPaySmsSettlementManually(dateInSettleMonth);
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@GetMapping("/vnpay/airtime")
	public @ResponseBody
	ResponseEntity<?> settleAirtimeFile(@RequestParam(value = "settleDate") @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateInSettleMonth) throws Exception {
		vnPayAirtimeScheduleTask.scheduleTaskVNPayAirtimeSettlementManually(dateInSettleMonth);
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@GetMapping("/imedia/bill")
	public @ResponseBody
	ResponseEntity<?> settleImediaBillFile(@RequestParam(value = "settleDate") @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateInSettleMonth) throws Exception {
		imediaBillPayScheduleTask.scheduleTaskImediaBillSettlementManually(dateInSettleMonth);
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@GetMapping("/imedia/topup")
	public @ResponseBody
	ResponseEntity<?> settleImediaTopupFile(@RequestParam(value = "settleDate") @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateInSettleMonth) throws Exception {
		imediaTopupScheduleTask.scheduleTaskImediaTopupSettlementManually(dateInSettleMonth);
		return new ResponseEntity<>(HttpStatus.OK);
	}

	/********************** Vietin Disbursement **********************/

	@GetMapping("/vietin/disbursement")
	public @ResponseBody
	ResponseEntity<?> settleVietinDisbursement(@RequestParam(value = "settleDate") @DateTimeFormat(pattern = "yyyy-MM-dd") Date settleDate) throws Exception {
		vietinDisbursementIn.ewalletVietinDisbursementSettlementManually(new DateTime(settleDate));
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@GetMapping("/vietin/virtualAcct")
	public @ResponseBody
	ResponseEntity<?> settleVietinVirtualAcct(@RequestParam(value = "settleDate") @DateTimeFormat(pattern = "yyyy-MM-dd") Date settleDate) throws Exception {
		vietinVirtualAcctSv.ewalletVietinVirtualAcctSettlementManually(new DateTime(settleDate));
		return new ResponseEntity<>(HttpStatus.OK);
	}

	/********************** Vietin Disbursement **********************/

	/********************** Asc daily report **********************/
	@RequestMapping(method = RequestMethod.GET, value = "/asc/daily_mail_report/{settleDate}")
	public @ResponseBody
	ResponseEntity<?> manuallyAscDailySendEmail(
			@PathVariable() String settleDate, @RequestParam(name = "primaryMid", required = false) String primaryMid, HttpServletRequest request) throws Exception {
		ascDailyReportSendEmail.executeManually(settleDate, primaryMid);
		return new ResponseEntity<>("Send email completed!!!!", HttpStatus.OK);
	}
	/********************** Asc daily report **********************/

}
