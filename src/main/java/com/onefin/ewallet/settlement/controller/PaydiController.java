package com.onefin.ewallet.settlement.controller;

import com.onefin.ewallet.common.base.controller.AbstractBaseController;
import com.onefin.ewallet.settlement.service.merchant.paydi.SmartPosPaydiBunkUpload;
import com.onefin.ewallet.settlement.service.merchant.paydi.SoftPosPaydiBunkUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/settle/merchant/paydi")
public class PaydiController extends AbstractBaseController {

	private static final Logger LOGGER = LoggerFactory.getLogger(PaydiController.class);

	@Autowired
	private SmartPosPaydiBunkUpload smartPosPaydiBunkUpload;

	@Autowired
	private SoftPosPaydiBunkUpload softPosPaydiBunkUpload;

	@RequestMapping(method = RequestMethod.GET, value = "/smartpos/{date}")
	public @ResponseBody
	ResponseEntity<?> manuallySmartPosSettlement(@PathVariable(required = false) String date, HttpServletRequest request) throws Exception {
		smartPosPaydiBunkUpload.bunkUploadManully(date);
		return new ResponseEntity<>("Smart pos paydi bunk upload processing", HttpStatus.OK);
	}

	@RequestMapping(method = RequestMethod.GET, value = "/softpos/{date}")
	public @ResponseBody
	ResponseEntity<?> manuallySoftPosSettlement(@PathVariable(required = false) String date, HttpServletRequest request) throws Exception {
		softPosPaydiBunkUpload.bunkUploadManully(date);
		return new ResponseEntity<>("Soft pos paydi bunk upload processing", HttpStatus.OK);
	}

}
