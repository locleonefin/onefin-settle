package com.onefin.ewallet.settlement.controller;

import com.onefin.ewallet.common.base.search.CustomRsqlVisitor;
import com.onefin.ewallet.common.domain.settlement.SettlementTransaction;
import com.onefin.ewallet.settlement.common.SettleConstants;
import com.onefin.ewallet.settlement.dto.SettlementDetails;
import com.onefin.ewallet.settlement.repository.SettleTransRepository;
import com.onefin.ewallet.settlement.service.SettleService;
import cz.jirutka.rsql.parser.RSQLParser;
import cz.jirutka.rsql.parser.ast.Node;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inside/settle")
public class PortalSettleController {

	private static final Logger LOGGER = LoggerFactory.getLogger(PortalSettleController.class);

	@Autowired
	private SettleTransRepository settleRepo;

	@Autowired
	private SettleService settleService;

	@Autowired
	private ModelMapper modelMapper;

	@GetMapping("/list")
	public ResponseEntity<?> getSettleListTrx(@RequestParam(value = "search", required = false) String search,
	                                          @PageableDefault(sort = {"updatedDate"}, direction = Sort.Direction.DESC) Pageable pageable) {
		Page<SettlementTransaction> smsSettleTrx;
		Page<com.onefin.ewallet.settlement.dto.SettlementTransaction> smsSettleTrxDto;
		if (search != null) {
			search = String.format("%s;%s;%s;%s", search, String.format("settleKey.partner!=%s", SettleConstants.PARTNER_NAPAS), String.format("settleKey.partner!=%s", SettleConstants.PARTNER_VIETINBANK), String.format("settleKey.partner!=%s", SettleConstants.PARTNER_PAYDI));
			Node rootNode = new RSQLParser().parse(search);
			Specification<SettlementTransaction> spec = rootNode.accept(new CustomRsqlVisitor<>());
			smsSettleTrx = settleRepo.findAll(spec, pageable);
			smsSettleTrxDto = smsSettleTrx.map(entity -> {
				com.onefin.ewallet.settlement.dto.SettlementTransaction dto = modelMapper.map(entity, com.onefin.ewallet.settlement.dto.SettlementTransaction.class);
				return dto;
			});
		} else {
			search = String.format("%s;%s;%s", String.format("settleKey.partner!=%s", SettleConstants.PARTNER_NAPAS), String.format("settleKey.partner!=%s", SettleConstants.PARTNER_VIETINBANK), String.format("settleKey.partner!=%s", SettleConstants.PARTNER_PAYDI));
			Node rootNode = new RSQLParser().parse(search);
			Specification<SettlementTransaction> spec = rootNode.accept(new CustomRsqlVisitor<>());
			smsSettleTrx = settleRepo.findAll(spec, pageable);
			smsSettleTrxDto = smsSettleTrx.map(entity -> {
				com.onefin.ewallet.settlement.dto.SettlementTransaction dto = modelMapper.map(entity, com.onefin.ewallet.settlement.dto.SettlementTransaction.class);
				return dto;
			});
		}
		return new ResponseEntity<>(smsSettleTrxDto, HttpStatus.OK);
	}

	@GetMapping()
	public ResponseEntity<?> getSettleDetail(@RequestParam(value = "partner") String partner,
	                                         @RequestParam(value = "domain") String domain,
	                                         @RequestParam(value = "settleDate") String settleDate) {
		SettlementTransaction trx = settleRepo.findByPartnerAndDomainAndSettleDate(partner, domain, settleDate);
		SettlementDetails trxResult = modelMapper.map(trx, SettlementDetails.class);
		return new ResponseEntity<>(trxResult, HttpStatus.OK);
	}

	@GetMapping("/file-export")
	public @ResponseBody
	ResponseEntity<?> getSettleFile(@RequestParam(value = "partner") String partner,
	                                @RequestParam(value = "domain") String domain,
	                                @RequestParam(value = "settleDate") String settleDate,
	                                @RequestParam(value = "fileName") String fileName) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType("application/vnd.ms-excel"));
		headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
		headers.setContentDispositionFormData("settleFile", fileName);
		return new ResponseEntity<>(settleService.downloadSettlementFile(partner, domain, settleDate, fileName), headers, HttpStatus.OK);
	}

//	@GetMapping("/file-export")
//	public @ResponseBody
//	ResponseEntity<?> getSettleFile(@RequestParam("refresh") boolean refresh,
//	                                @RequestParam @DateTimeFormat(pattern = SettleConstants.DATE_FORMAT_yyyyMMdd) Date settleDate) throws Exception {
//
//		HttpHeaders headers = new HttpHeaders();
//		headers.setContentType(MediaType.parseMediaType("application/vnd.ms-excel"));
//		headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
//		if (refresh) {
//			vnpaySmsScheduleTask.scheduleTaskVNPaySmsSettlementManually(new Date(settleDate.getTime() + VNPaySmsScheduleTask.MILLIS_IN_A_DAY));
//			SettlementTransaction trx = settleRepo.findByPartnerAndDomainAndSettleDate(SettleConstants.PARTNER_VNPAY,
//					SettleConstants.SMS_BRANDNAME, new SimpleDateFormat(SettleConstants.DATE_FORMAT_yyyyMM).format(settleDate));
//			headers.setContentDispositionFormData(trx.getFile().get(0), trx.getFile().get(0));
//			byte[] result = vnpaySmsScheduleTask.downloadReportFile(trx.getFile().get(0));
//			return new ResponseEntity<>(result, headers, HttpStatus.OK);
//		} else {
//			SettlementTransaction trx = settleRepo.findByPartnerAndDomainAndSettleDate(SettleConstants.PARTNER_VNPAY,
//					SettleConstants.SMS_BRANDNAME, new SimpleDateFormat(SettleConstants.DATE_FORMAT_yyyyMM).format(settleDate));
//			headers.setContentDispositionFormData(trx.getFile().get(0), trx.getFile().get(0));
//			byte[] result = vnpaySmsScheduleTask.downloadReportFile(trx.getFile().get(0));
//			return new ResponseEntity<>(result, headers, HttpStatus.OK);
//		}
//	}
}
