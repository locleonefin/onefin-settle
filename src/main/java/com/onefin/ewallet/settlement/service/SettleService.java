package com.onefin.ewallet.settlement.service;

import com.onefin.ewallet.common.base.service.RestTemplateHelper;
import com.onefin.ewallet.common.domain.settlement.SettlementTransaction;
import com.onefin.ewallet.common.utility.date.DateTimeHelper;
import com.onefin.ewallet.common.utility.dto.EmailDto;
import com.onefin.ewallet.settlement.common.SettleConstants;
import com.onefin.ewallet.settlement.repository.SettleTransRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SettleService {

	private static final Logger LOGGER = LoggerFactory.getLogger(SettleService.class);

	@Autowired
	private SettleTransRepository transRepository;

	@Autowired
	private DateTimeHelper dateTimeHelper;

	@Autowired
	private ConfigLoader configLoader;

	@Autowired
	private RestTemplateHelper restTemplateHelper;

	@Autowired
	private MinioService minioService;

	public SettlementTransaction save(SettlementTransaction transData) throws Exception {
		transData.setCreatedDate(dateTimeHelper.currentDate(SettleConstants.HO_CHI_MINH_TIME_ZONE));
		transData.setUpdatedDate(dateTimeHelper.currentDate(SettleConstants.HO_CHI_MINH_TIME_ZONE));
		return transRepository.save(transData);
	}

	public SettlementTransaction update(SettlementTransaction transData) {
		transData.setUpdatedDate(dateTimeHelper.currentDate(SettleConstants.HO_CHI_MINH_TIME_ZONE));
		return transRepository.save(transData);
	}

	public Map<String, Object> ascDailySendEmail(List<String> schoolEmail, List<String> ascEmailCC, String schoolName, String fromDate, String toDate, String bucket, String attachment, String subject) {
		Map<String, Object> payload = new HashMap<>();
		payload.put("customerName", schoolName);
		payload.put("fromDate", fromDate);
		payload.put("toDate", toDate);
		EmailDto data = new EmailDto(schoolEmail, ascEmailCC, new ArrayList<>(), payload, bucket, Arrays.asList(attachment), subject, "asc_email_daily");
		return sendEmail(data);
	}

	public Map<String, Object> settleCompleted(String settleDate, String bucket, String attachment, String subject) throws Exception {
		Map<String, Object> payload = new HashMap<>();
		payload.put("customerName", "OneFin Team");
		payload.put("settleDate", settleDate);
		EmailDto data = new EmailDto(Arrays.asList(configLoader.getOfSettleReceiver().split(",")), new ArrayList<>(), new ArrayList<>(), payload, bucket, Arrays.asList(attachment), subject, "settlement_completed");
		return sendEmail(data);
	}

	private Map<String, Object> sendEmail(EmailDto data) {
		String url = configLoader.getUtilityUrl() + configLoader.getUtilityEmailUrl();
		LOGGER.info("== Send Email request {} - url: {}", data, url);
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Collections.singletonList(MediaType.ALL));
		headers.setContentType(MediaType.APPLICATION_JSON);
		HashMap<String, String> headersMap = new HashMap<>();
		for (String header : headers.keySet()) {
			headersMap.put(header, headers.getFirst(header));
		}
		HashMap<String, String> urlParameters = new HashMap<>();
		List<String> pathVariables = new ArrayList<>();
		ResponseEntity<Map<String, Object>> responseEntity = restTemplateHelper.post(url,
				MediaType.APPLICATION_JSON_VALUE, headersMap, pathVariables, urlParameters,
				null, data, new ParameterizedTypeReference<Map<String, Object>>() {
				});
		LOGGER.info("== Success receive response {}", responseEntity.getBody());
		return responseEntity.getBody();
	}

	public byte[] downloadSettlementFile(String partner, String domain, String settleDate, String fileName) {
		if (partner.equals(SettleConstants.PARTNER_VNPAY)) {
			if (domain.equals(SettleConstants.SMS_BRANDNAME)) {
				return minioService.getFile(configLoader.getBaseBucket(),
						configLoader.getSettleVNPaySmsMinioDefaultFolder() + "/" + fileName);
			}
			if (domain.equals(SettleConstants.AIRTIME_TOPUP)) {
				return minioService.getFile(configLoader.getBaseBucket(),
						configLoader.getSettleVNPayAirtimeMinioDefaultFolder() + "/" + fileName);
			}
		}
		if (partner.equals(SettleConstants.PARTNER_IMEDIA)) {
			if (domain.equals(SettleConstants.PAYBILL)) {
				return minioService.getFile(configLoader.getBaseBucket(),
						configLoader.getSettleImediaMinioDefaultFolder() + "/" + fileName);
			}
			if (domain.equals(SettleConstants.AIRTIME_TOPUP)) {
				return minioService.getFile(configLoader.getBaseBucket(),
						configLoader.getSettleImediaMinioDefaultFolder() + "/" + fileName);
			}
		}
		return null;
	}

}
