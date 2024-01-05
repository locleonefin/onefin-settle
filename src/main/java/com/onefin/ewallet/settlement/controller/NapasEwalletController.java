package com.onefin.ewallet.settlement.controller;

import com.onefin.ewallet.common.base.controller.AbstractBaseController;
import com.onefin.ewallet.common.domain.settlement.SettlementTransaction;
import com.onefin.ewallet.settlement.common.SettleConstants;
import com.onefin.ewallet.settlement.common.SettleConstants.WL_Merchant;
import com.onefin.ewallet.settlement.common.SettleHelper;
import com.onefin.ewallet.settlement.repository.SettleTransRepository;
import com.onefin.ewallet.settlement.service.SettleService;
import com.onefin.ewallet.settlement.service.napas.ce.NapasCEProcessTC;
import com.onefin.ewallet.settlement.service.napas.ce.NapasCEProcessXL;
import com.onefin.ewallet.settlement.service.napas.ew1.NapasWL1ProcessTC;
import com.onefin.ewallet.settlement.service.napas.ew1.NapasWL1ProcessXL;
import com.onefin.ewallet.settlement.service.napas.ew2.NapasWL2ProcessTC;
import com.onefin.ewallet.settlement.service.napas.ew2.NapasWL2ProcessXL;
import com.onefin.ewallet.settlement.service.napas.ew3.NapasWL3ProcessTC;
import com.onefin.ewallet.settlement.service.napas.ew3.NapasWL3ProcessXL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@Controller
@Configuration
@RequestMapping("/settle/napas")
public class NapasEwalletController extends AbstractBaseController {

	private static final Logger LOGGER = LoggerFactory.getLogger(NapasEwalletController.class);

	@Autowired
	private SettleService commonSettleService;

	@Autowired
	private SettleTransRepository settleRepo;

	@Autowired(required = false)
	private NapasCEProcessTC napasCEProcessTC;

	@Autowired(required = false)
	private NapasCEProcessXL napasCEProcessXL;

	@Autowired(required = false)
	private NapasWL1ProcessTC napasWL1ProcessTC;

	@Autowired(required = false)
	private NapasWL1ProcessXL napasWL1ProcessXL;

	@Autowired(required = false)
	private NapasWL2ProcessTC napasWL2ProcessTC;

	@Autowired(required = false)
	private NapasWL2ProcessXL napasWL2ProcessXL;

	@Autowired(required = false)
	private NapasWL3ProcessTC napasWL3ProcessTC;

	@Autowired(required = false)
	private NapasWL3ProcessXL napasWL3ProcessXL;

	@Autowired
	private SettleHelper settleHelper;
	
	/* *************************LINK BANK***************************** */

	/**
	 * Trigger process Thanh Cong file from Napas
	 * 
	 * @param settleDate
	 * @param order
	 * @param relatedSettleDate
	 * @param request
	 * @return
	 * @throws Exception 
	 * settleDate(MMddyy) - Current settle date - Settle with all transaction of previous date and other not working date
	 * order - Settle file order - default is 1 (maximum is 2)
	 * relatedSettleDate(yyyyMMdd) - other not working date (ex settle 102521 => relatedSettleDate: 102521, 102421, 102321)
	 */
	@RequestMapping(method = RequestMethod.POST, value = {
			"/trigger/link-bank/TC/settleDate_MMddyy/{settleDate}/{order}/{runSettle}" })
	public @ResponseBody ResponseEntity<?> napasTCEwalletSettlement(@PathVariable(required = true) String settleDate,
			@PathVariable(required = true) int order, @PathVariable(required = true) boolean runSettle, @RequestBody(required = true) List<String> relatedSettleDate,
			HttpServletRequest request) throws Exception {
		LOGGER.info("== RequestID {} - Start Napas TC settlement");
		SettlementTransaction transPendTC = settleRepo.findByPartnerAndDomainAndSettleDate(
				SettleConstants.PARTNER_NAPAS, SettleConstants.LINK_BANK, settleDate);
		if (transPendTC == null) {
			transPendTC = (SettlementTransaction) settleHelper.createModelStructure(new SettlementTransaction());
			transPendTC.getSettleKey().setDomain(SettleConstants.LINK_BANK);
			transPendTC.getSettleKey().setPartner(SettleConstants.PARTNER_NAPAS);
			transPendTC.getSettleKey().setSettleDate(settleDate);
			transPendTC.setStatus(SettleConstants.SETTLEMENT_PENDING_T0);
			commonSettleService.save(transPendTC);
			transPendTC = settleRepo.findByPartnerAndDomainAndSettleDate(SettleConstants.PARTNER_NAPAS,
					SettleConstants.LINK_BANK, settleDate);
			if(runSettle) {
				return napasCEProcessTC.ewalletNapasTCSettlementManually(settleDate, Integer.toString(order), transPendTC,
						relatedSettleDate);
			} else {
				return new ResponseEntity<>(transPendTC, HttpStatus.OK);
			}
		} else {
			if(runSettle) {
				return napasCEProcessTC.ewalletNapasTCSettlementManually(settleDate, Integer.toString(order), transPendTC,
						relatedSettleDate);
			} else {
				return new ResponseEntity<>(transPendTC, HttpStatus.OK);
			}
		}
	}

	// Not use: get directly from Napas sftp
	// Trigger settlement XL manually
	@RequestMapping(method = RequestMethod.POST, value = {
			"/trigger/link-bank/XL/settleDate_MMddyy/{settleDate}/{order}/{diffDay}" })
	public @ResponseBody ResponseEntity<?> napasXLEwalletSettlement(@PathVariable(required = true) String settleDate,
			@PathVariable(required = true) int order, @PathVariable(required = true) int diffDay, HttpServletRequest request) throws Exception {
		LOGGER.info("== RequestID {} - Start Napas XL settlement");
		SettlementTransaction trans = settleRepo.findAllByPartnerAndDomainAndSettleDateAndStatusTransaction(
				SettleConstants.PARTNER_NAPAS, SettleConstants.LINK_BANK, settleDate,
				SettleConstants.SETTLEMENT_PENDING_T1);
		if (trans != null) {
			return napasCEProcessXL.ewalletNapasXLSettlementManually(settleDate, Integer.toString(order), trans, diffDay);
		} else {
			return new ResponseEntity<>("Please init XL Trans", HttpStatus.EXPECTATION_FAILED);
		}
	}

	// Find incompleted trans
	@RequestMapping(method = RequestMethod.GET, value = "/link-bank/incomplete")
	public @ResponseBody ResponseEntity<?> findPendingTrans(HttpServletRequest request) throws Exception {
		return new ResponseEntity<>(
				settleRepo.findAllByPartnerAndDomainAndNotStatusTransaction(SettleConstants.PARTNER_NAPAS,
						SettleConstants.LINK_BANK, SettleConstants.SETTLEMENT_SUCCESS),
				HttpStatus.OK);
	}

	@RequestMapping(method = RequestMethod.POST, value = {
			"/update/link-bank/TC/settleDate_MMddyy/{settleDate}/{transStatus}" })
	public @ResponseBody ResponseEntity<?> napasCreateOrUpdateTCTransEwalletSettlement(
			@PathVariable(required = true) String settleDate, @PathVariable(required = true) String transStatus,
			HttpServletRequest request) throws Exception {
		SettlementTransaction transPendTC = settleRepo.findByPartnerAndDomainAndSettleDate(
				SettleConstants.PARTNER_NAPAS, SettleConstants.LINK_BANK, settleDate);
		if (transPendTC == null) {
			transPendTC = (SettlementTransaction) settleHelper.createModelStructure(new SettlementTransaction());
		}
		transPendTC.getSettleKey().setDomain(SettleConstants.LINK_BANK);
		transPendTC.getSettleKey().setPartner(SettleConstants.PARTNER_NAPAS);
		transPendTC.getSettleKey().setSettleDate(settleDate);
		transPendTC.setStatus(transStatus);
		return new ResponseEntity<>(commonSettleService.save(transPendTC), HttpStatus.OK);
	}
	
	/* *************************LINK BANK***************************** */
	
	/* *************************PAYMENT GATEWAY***************************** */

	/**
	 * Trigger process Thanh Cong file from Napas
	 * 
	 * @param settleDate
	 * @param order
	 * @param relatedSettleDate
	 * @param request
	 * @return
	 * @throws Exception settleDate(MMddyy) - Current settle date - Settle with all
	 *                   transaction of previous date and other not working date
	 *                   order - Settle file order - default is 1 (maximum is 2)
	 *                   relatedSettleDate(MMddyy) - other not working date (ex
	 *                   settle 102521 => relatedSettleDate: 102421, 102321)
	 */
	@RequestMapping(method = RequestMethod.POST, value = {
			"/trigger/payment-gateway/{merchant}/TC/settleDate_MMddyy/{settleDate}/{order}/{runSettle}" })
	public @ResponseBody ResponseEntity<?> napasTCEwalletSettlementEW(@PathVariable(required = true) WL_Merchant merchant,
			@PathVariable(required = true) String settleDate, @PathVariable(required = true) int order,
			@PathVariable(required = true) boolean runSettle,
			@RequestBody(required = true) List<String> relatedSettleDate, HttpServletRequest request) throws Exception {
		LOGGER.info("== RequestID {} - Start Napas TC settlement");
		SettlementTransaction transPendTC = settleRepo.findByPartnerAndDomainAndSettleDate(
				SettleConstants.PARTNER_NAPAS, merchant.toString(), settleDate);
		if (transPendTC == null) {
			transPendTC = (SettlementTransaction) settleHelper.createModelStructure(new SettlementTransaction());
			transPendTC.getSettleKey().setDomain(SettleConstants.PAYMENT_GATEWAYWL1);
			transPendTC.getSettleKey().setPartner(SettleConstants.PARTNER_NAPAS);
			transPendTC.getSettleKey().setSettleDate(settleDate);
			transPendTC.setStatus(SettleConstants.SETTLEMENT_PENDING_T0);
			commonSettleService.save(transPendTC);
			transPendTC = settleRepo.findByPartnerAndDomainAndSettleDate(SettleConstants.PARTNER_NAPAS,
					merchant.toString(), settleDate);
			if (runSettle) {
				if(merchant.toString().equals(SettleConstants.PAYMENT_GATEWAYWL1)) {
					return napasWL1ProcessTC.ewalletNapasTCSettlementManually(settleDate, Integer.toString(order),
							transPendTC, relatedSettleDate);
				}
				if(merchant.toString().equals(SettleConstants.PAYMENT_GATEWAYWL2)) {
					return napasWL2ProcessTC.ewalletNapasTCSettlementManually(settleDate, Integer.toString(order),
							transPendTC, relatedSettleDate);
				}
				if(merchant.toString().equals(SettleConstants.PAYMENT_GATEWAYWL3)) {
					return napasWL3ProcessTC.ewalletNapasTCSettlementManually(settleDate, Integer.toString(order),
							transPendTC, relatedSettleDate);
				}
				return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
			} else {
				return new ResponseEntity<>(transPendTC, HttpStatus.OK);
			}
		} else {
			if (runSettle) {
				if(merchant.toString().equals(SettleConstants.PAYMENT_GATEWAYWL1)) {
					return napasWL1ProcessTC.ewalletNapasTCSettlementManually(settleDate, Integer.toString(order),
							transPendTC, relatedSettleDate);
				}
				if(merchant.toString().equals(SettleConstants.PAYMENT_GATEWAYWL2)) {
					return napasWL2ProcessTC.ewalletNapasTCSettlementManually(settleDate, Integer.toString(order),
							transPendTC, relatedSettleDate);
				}
				if(merchant.toString().equals(SettleConstants.PAYMENT_GATEWAYWL3)) {
					return napasWL3ProcessTC.ewalletNapasTCSettlementManually(settleDate, Integer.toString(order),
							transPendTC, relatedSettleDate);
				}
				return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
			} else {
				return new ResponseEntity<>(transPendTC, HttpStatus.OK);
			}
		}
	}

	// Not use: get directly from Napas sftp
	// Trigger settlement XL manually
	@RequestMapping(method = RequestMethod.POST, value = {
			"/trigger/payment-gateway/{merchant}/XL/settleDate_MMddyy/{settleDate}/{order}/{diffDay}" })
	public @ResponseBody ResponseEntity<?> napasXLEwalletSettlementEW(
			@PathVariable(required = true) WL_Merchant merchant, @PathVariable(required = true) String settleDate,
			@PathVariable(required = true) int order, @PathVariable(required = true) int diffDay,
			HttpServletRequest request) throws Exception {
		LOGGER.info("== RequestID {} - Start Napas XL settlement");
		SettlementTransaction trans = settleRepo.findAllByPartnerAndDomainAndSettleDateAndStatusTransaction(
				SettleConstants.PARTNER_NAPAS, merchant.toString(), settleDate, SettleConstants.SETTLEMENT_PENDING_T1);
		if (trans != null) {
			if (merchant.toString().equals(SettleConstants.PAYMENT_GATEWAYWL1)) {
				return napasWL1ProcessXL.ewalletNapasXLSettlementManually(settleDate, Integer.toString(order), trans,
						diffDay);
			}
			if (merchant.toString().equals(SettleConstants.PAYMENT_GATEWAYWL2)) {
				return napasWL2ProcessXL.ewalletNapasXLSettlementManually(settleDate, Integer.toString(order), trans,
						diffDay);
			}
			if (merchant.toString().equals(SettleConstants.PAYMENT_GATEWAYWL3)) {
				return napasWL3ProcessXL.ewalletNapasXLSettlementManually(settleDate, Integer.toString(order), trans,
						diffDay);
			}
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		} else {
			return new ResponseEntity<>("Please init XL Trans", HttpStatus.EXPECTATION_FAILED);
		}
	}

	// Find incompleted trans
	@RequestMapping(method = RequestMethod.GET, value = "/payment-gateway/{merchant}/incomplete")
	public @ResponseBody ResponseEntity<?> findPendingTrans(@PathVariable(required = true) WL_Merchant merchant,
			HttpServletRequest request) throws Exception {
		return new ResponseEntity<>(settleRepo.findAllByPartnerAndDomainAndNotStatusTransaction(
				SettleConstants.PARTNER_NAPAS, merchant.toString(), SettleConstants.SETTLEMENT_SUCCESS), HttpStatus.OK);
	}

	@RequestMapping(method = RequestMethod.POST, value = {
			"/update/payment-gateway/{merchant}/TC/settleDate_MMddyy/{settleDate}/{transStatus}" })
	public @ResponseBody ResponseEntity<?> napasCreateOrUpdateTCTransEwalletSettlement(@PathVariable(required = true) WL_Merchant merchant,
			@PathVariable(required = true) String settleDate, @PathVariable(required = true) String transStatus,
			HttpServletRequest request) throws Exception {
		SettlementTransaction transPendTC = settleRepo.findByPartnerAndDomainAndSettleDate(
				SettleConstants.PARTNER_NAPAS, merchant.toString(), settleDate);
		if (transPendTC == null) {
			transPendTC = (SettlementTransaction) settleHelper.createModelStructure(new SettlementTransaction());
		}
		transPendTC.getSettleKey().setDomain(merchant.toString());
		transPendTC.getSettleKey().setPartner(SettleConstants.PARTNER_NAPAS);
		transPendTC.getSettleKey().setSettleDate(settleDate);
		transPendTC.setStatus(transStatus);
		return new ResponseEntity<>(commonSettleService.save(transPendTC), HttpStatus.OK);
	}
	
	/* *************************PAYMENT GATEWAY***************************** */

}
