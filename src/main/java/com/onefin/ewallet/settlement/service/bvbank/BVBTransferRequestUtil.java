package com.onefin.ewallet.settlement.service.bvbank;

import com.onefin.ewallet.common.base.constants.BankConstants;
import com.onefin.ewallet.common.base.constants.OneFinConstants;
import com.onefin.ewallet.common.base.service.BackupService;
import com.onefin.ewallet.common.base.service.RestTemplateHelper;
import com.onefin.ewallet.common.domain.constants.DomainConstants;
import com.onefin.ewallet.common.domain.errorCode.PartnerErrorCode;
import com.onefin.ewallet.common.utility.date.DateTimeHelper;
import com.onefin.ewallet.common.utility.json.JSONHelper;
import com.onefin.ewallet.settlement.dto.bvb.ConnResponse;
import com.onefin.ewallet.settlement.dto.bvb.bankTransfer.BVBIBFTCommonRequest;
import com.onefin.ewallet.settlement.dto.bvb.bankTransfer.BVBIBFTCommonResponse;
import com.onefin.ewallet.settlement.dto.bvb.bankTransfer.BVBIBFTReconciliationDTO;
import com.onefin.ewallet.settlement.repository.PartnerErrorCodeRepo;
import com.onefin.ewallet.settlement.repository.BankListRepository;
import com.onefin.ewallet.settlement.service.ConfigLoader;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.PrivateKey;
import java.util.*;

@Service
public class BVBTransferRequestUtil {

	private static final org.apache.logging.log4j.Logger LOGGER = LogManager.getLogger(BVBTransferRequestUtil.class);

	@Autowired
	private BVBEncryptUtil bvbEncryptUtil;

	@Autowired
	private Environment env;

	@Autowired
	private JSONHelper jsonHelper;

	@Value("${bvb.IBFT.onefinPrivateKey}")
	private String privateKeyPath;

	@Value("${bvb.IBFT.onefinClientCode}")
	private String clientCode;

	@Value("${bvb.IBFT.onefinMerchantCode}")
	private String requestIdPrefix;

	@Autowired
	private DateTimeHelper dateTimeHelper;

	@Autowired
	private ConfigLoader configLoader;

	@Autowired
	private BackupService backupService;

	@Autowired
	private PartnerErrorCodeRepo partnerErrorCodeRepo;

	@Autowired
	protected RestTemplateHelper restTemplateHelper;

	@Autowired
	private ModelMapper modelMapper;

	@Value("{bvb.IBFT.merchantId}")
	private String bvbTransferMerchantId;

	@Autowired
	private BankListRepository bankListRepository;

	public <T extends BVBIBFTCommonResponse<?>> String rawDataConstruct(T requestBody) {
		StringBuilder sb = new StringBuilder();
		sb.append(requestBody.getResponseId());
		sb.append("|");
		sb.append(requestBody.getStatus());
		sb.append("|");
		sb.append(requestBody.getErrorCode());
		sb.append("|");
		sb.append(requestBody.getErrorMessage());
		sb.append("|");
		if ((requestBody.getData() == null) || (requestBody.getData().isEmpty())) {
			sb.append("null");
		} else {
			sb.append(requestBody.getData());
		}
		return sb.toString();
	}

	public <T extends BVBIBFTCommonRequest<?>> String rawDataConstruct(T requestBody) {
		StringBuilder sb = new StringBuilder();
		sb.append(requestBody.getRequestId());
		sb.append("|");
		sb.append(requestBody.getClientCode());
		sb.append("|");
		sb.append(requestBody.getClientUserId());
		sb.append("|");
		sb.append(requestBody.getTime().getTime());
		sb.append("|");
		if (requestBody.getData() == null) {
			sb.append("null");
		} else {
			sb.append(jsonHelper.convertMap2JsonString(requestBody.getData()));
		}

		return sb.toString();
	}

	public String signatureConstruction(BVBIBFTCommonRequest<?> requestBody) throws Exception {
		PrivateKey privateKey =
				bvbEncryptUtil.readPrivateKeyBVB(privateKeyPath);
		String rawData = rawDataConstruct(requestBody);
		LOGGER.log(Level.getLevel("INFOWT"), "rawData: " + rawData);
		String digitalSignature = bvbEncryptUtil.signHex(rawData, privateKey);
		LOGGER.log(Level.getLevel("INFOWT"), "digitalSignature: " + digitalSignature);
		return digitalSignature;
	}

	public String getRequestId() {
		StringBuilder requestId = new StringBuilder();
		String dateString = dateTimeHelper.currentDateString(OneFinConstants.HO_CHI_MINH_TIME_ZONE, DomainConstants.DATE_FORMAT_TRANS7);
		String randomString = RandomStringUtils.random(6, false, true);
		requestId.append(requestIdPrefix);
		requestId.append(dateString);
		requestId.append(randomString);
		return requestId.toString();
	}

	public String getRequestId(String suffix) {
		StringBuilder requestId = new StringBuilder();
		String dateString = dateTimeHelper.currentDateString(OneFinConstants.HO_CHI_MINH_TIME_ZONE, DomainConstants.DATE_FORMAT_TRANS7);
		requestId.append(requestIdPrefix);
		requestId.append(dateString);
		requestId.append(suffix);
		return requestId.toString();
	}

	public String reconciliationFileGen(List<BVBIBFTReconciliationDTO> bvbibftReconciliationDTOList, Date transDate) {
		StringBuilder sb = new StringBuilder();
		String header = "ClientCode,Transaction ID,Transaction Date,Card No,Card Ind,Business Type,Amount,Currency Code,Description,Bank Transaction ID,Reversal Indicator,Transaction Status,Transaction Type,Checksum";
		sb.append(header);
		sb.append("\n");
		for (BVBIBFTReconciliationDTO e : bvbibftReconciliationDTOList) {
			e.setCheckSum(bvbEncryptUtil.MD5Hashing(e.getCheckSumString()));
			sb.append(e.getReconciliationLine());
			sb.append("\n");
		}
		String transDateParse = dateTimeHelper.parseDate2String(transDate, DomainConstants.DATE_FORMAT_TRANS8);
		sb.append(bvbEncryptUtil.MD5Hashing(transDateParse + bvbibftReconciliationDTOList.size()));

		return sb.toString();
	}

	public String getReconciliationFileName(String extension, String dateString) {
		StringBuilder sb = new StringBuilder();
		sb.append(clientCode);
		sb.append("_");
		sb.append(dateString);
		sb.append(".").append(extension);
		return sb.toString();

	}

	public HttpHeaders buildHeader(boolean hasFile) throws Exception {

		HttpHeaders header = new HttpHeaders();
		if (hasFile) {
			header.setContentType(MediaType.MULTIPART_FORM_DATA);
		} else {
			header.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		}
		return header;
	}

	public <T extends BVBIBFTCommonRequest<?>, R extends BVBIBFTCommonResponse<?>> ResponseEntity<R> requestBVB(
			T dataRequest,
			String url,
			String requestId,
			String prefix,
			Class<R> rClassRef
	) throws Exception {

		return postRequest(url, dataRequest, requestId, prefix, rClassRef);

	}

	public <T extends BVBIBFTCommonRequest<?>, R extends BVBIBFTCommonResponse<?>> ResponseEntity<R> requestBVB(
			T dataRequest,
			String url, byte[] fileSend, String fileName,
			String requestId,
			String prefix,
			Class<R> rClassRef
	) throws Exception {

		return postRequest(url, fileSend, fileName, dataRequest, requestId, prefix, rClassRef);

	}

	public <Q extends BVBIBFTCommonRequest<?>, R extends BVBIBFTCommonResponse<?>>
	ResponseEntity<R> postRequest(String url, Q dataRequest, String requestId, String prefix, Class<R> typeRef) throws Exception {
		HttpHeaders header = buildHeader(false);


		ResponseEntity<R> responseEntity = urlEncodedFormRequest(url, header, dataRequest, typeRef);

		// backup api
		try {
			backUpRequestResponse(prefix, requestId, dataRequest, responseEntity.getBody(), header);
		} catch (Exception e) {
			LOGGER.error("Api backup Failed: url - {}\n" +
					"requestId: {}\n" +
					"dataRequest: {}\n" +
					"response: {}", url, requestId, dataRequest, responseEntity.getBody());
		}

		return responseEntity;
	}

	public <Q extends BVBIBFTCommonRequest<?>, R extends BVBIBFTCommonResponse<?>>
	ResponseEntity<R> postRequest(String url, byte[] fileSend, String fileName,
								  Q dataRequest, String requestId, String prefix, Class<R> typeRef) throws Exception {
		HttpHeaders header = buildHeader(true);

		ResponseEntity<R> responseEntity = urlEncodedFormRequest(url, header, dataRequest, fileSend, fileName, typeRef);

		// backup api
		try {
			backUpRequestResponse(prefix, requestId, dataRequest, responseEntity.getBody(), header);
		} catch (Exception e) {
			LOGGER.error("Api backup Failed: url - {}\n" +
					"requestId: {}\n" +
					"dataRequest: {}\n" +
					"response: {}", url, requestId, dataRequest, responseEntity.getBody());
		}

		return responseEntity;
	}

	public void backUpRequestResponse(
			String prefix,
			String requestId,
			Object request,
			Object response,
			Object header
	) throws Exception {
		if (request != null) {

			backupService.backup(env.getProperty("sftp.bvbank.virtualAcct.uriBvbVirtualAcct"), requestId, request,
					prefix + "-" + BankConstants.BACKUP_REQUEST);
		}
		if (response != null) {
			backupService.backup(env.getProperty("sftp.bvbank.virtualAcct.uriBvbVirtualAcct"), requestId, response,
					prefix + "-" + BankConstants.BACKUP_RESPONSE);
		}
		if (header != null) {
			backupService.backup(env.getProperty("sftp.bvbank.virtualAcct.uriBvbVirtualAcct"), requestId, header,
					prefix + "-" + BankConstants.BACKUP_HEADER);
		}
	}

	public <T extends BVBIBFTCommonRequest<?>> ConnResponse checkSignature(T responseEntity) {
		if (!validateIBFTSignature(responseEntity)) {
			LOGGER.error("Invalid signature from BVB !!!");
			ConnResponse response = new ConnResponse();
			transformErrorCode(
					response,
					BankConstants.BVBIBFTErrorCodeFromOnefin.WRONG_SIGNATURE.getPartnerCode(),
					BankConstants.BVBIBFTErrorCodeFromOnefin.WRONG_SIGNATURE.getDomainCode(),
					BankConstants.BVBIBFTErrorCodeFromOnefin.WRONG_SIGNATURE.getCode(),
					OneFinConstants.LANGUAGE.VIETNAMESE.getValue()
			);
			return response;
		} else {
			return null;
		}
	}

	public <T extends BVBIBFTCommonResponse<?>> ConnResponse checkSignature(T responseEntity) {
		if (!validateIBFTSignature(responseEntity)) {
			LOGGER.error("Invalid signature from BVB !!!");
			ConnResponse response = new ConnResponse();
			transformErrorCode(
					response,
					BankConstants.BVBIBFTErrorCodeFromOnefin.WRONG_SIGNATURE.getPartnerCode(),
					BankConstants.BVBIBFTErrorCodeFromOnefin.WRONG_SIGNATURE.getDomainCode(),
					BankConstants.BVBIBFTErrorCodeFromOnefin.WRONG_SIGNATURE.getCode(),
					OneFinConstants.LANGUAGE.VIETNAMESE.getValue()
			);
			return response;
		} else {
			return null;
		}
	}

	public <T extends BVBIBFTCommonRequest<?>> boolean validateIBFTSignature(T responseBody) {
		try {
			String rawData = rawDataConstruct(responseBody);
			String stringHex = responseBody.getSignature();
			InputStream readKey = new FileInputStream(env.getProperty("bvb.virtualAcct.onefinPublicKey"));
			boolean verifyResult = bvbEncryptUtil.bvbVerifyHex(readKey, rawData, stringHex);
			if (verifyResult) {
				LOGGER.info("Validate BVB signature successfully!");
			} else {
				LOGGER.error("Validate BVB signature failed!");
			}
			return verifyResult;
		} catch (Exception e) {
			LOGGER.error("Error when validate signature!");
			return false;
		}
	}

	public <T extends BVBIBFTCommonResponse<?>> boolean validateIBFTSignature(T responseBody) {
		try {
			String rawData = rawDataConstruct(responseBody);
			LOGGER.log(Level.getLevel("INFOWT"), "validateIBFTSignature rawData: " + rawData);
			String stringHex = responseBody.getSig();
			InputStream readKey = new FileInputStream(env.getProperty("bvb.IBFT.bvbPublicKey"));
			boolean verifyResult = bvbEncryptUtil.bvbVerifyHex(readKey, rawData, stringHex);
			if (verifyResult) {
				LOGGER.info("Validate BVB signature successfully!");
			} else {
				LOGGER.error("Validate BVB signature failed!");
			}
			return verifyResult;
		} catch (Exception e) {
			LOGGER.error("Error when validate signature!");
			return false;
		}
	}

	public <T> boolean dtoValidate(T object) {
		StringBuilder validateMessage = new StringBuilder();
		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		Validator validator = factory.getValidator();
		Set<ConstraintViolation<T>> constraintViolations
				= validator.validate(object);

		LOGGER.info("list constraintViolations: {}", constraintViolations);
		for (ConstraintViolation<T> constraintViolation : constraintViolations) {
			LOGGER.info("get constraintViolation message: {}", constraintViolation.getMessage());
			validateMessage.append(constraintViolation.getMessage()).append(" - ");
		}

		if (!constraintViolations.isEmpty()) {
			LOGGER.error("DTO validation failed: {}", constraintViolations);
			LOGGER.error("validateMessage: {}", validateMessage);
			return false;
		}
		return true;
	}

	public void transformErrorCode(
			ConnResponse data,
			String partner,
			String domain,
			String code,
			String lang) {
		PartnerErrorCode partnerCode = partnerErrorCodeRepo
				.findAllByPartnerAndDomainAndCode(
						partner, domain, code);
		// LOGGER.info("transformErrorCode code: {} {} {}", OneFinConstants.PARTNER_VIETINBANK, OneFinConstants.VIRTUAL_ACCT, code);
		if (partnerCode == null) {
			LOGGER.warn("No error code found, please check the config file: {}", code);
		}
		assert partnerCode != null;
		data.setConnectorCode(partnerCode.getBaseErrorCode().getCode());
		if (lang.equals(OneFinConstants.LANGUAGE.VIETNAMESE.getValue())) {
			data.setMessage(partnerCode.getBaseErrorCode().getMessageVi());
		} else if (lang.equals(OneFinConstants.LANGUAGE.ENGLISH.getValue())) {
			data.setMessage(partnerCode.getBaseErrorCode().getMessageEn());
		} else {
			data.setMessage(partnerCode.getBaseErrorCode().getMessageEn());
		}
	}

	public <T> ConnResponse checkDTOAndReturnIfNotValid(T object) {
		boolean isDtoValid = dtoValidate(object);
		if (!isDtoValid) {
			ConnResponse response = new ConnResponse();
			transformErrorCode(
					response,
					BankConstants.BVBIBFTErrorCodeFromOnefin.INVALID_PARAM.getPartnerCode(),
					BankConstants.BVBIBFTErrorCodeFromOnefin.INVALID_PARAM.getDomainCode(),
					BankConstants.BVBIBFTErrorCodeFromOnefin.INVALID_PARAM.getCode(),
					OneFinConstants.LANGUAGE.VIETNAMESE.getValue()
			);
			return response;
		}
		return null;
	}

	public <T extends BVBIBFTCommonRequest<?>, R extends BVBIBFTCommonResponse<?>> ResponseEntity<?>
	ibftRequest(T requestBody,
				String url, byte[] fileSend, String filename,
				String requestId,
				String prefix,
				Class<T> requestClass, Class<R> responseClass) throws Exception {
		LOGGER.info("URL: {}", url);

		// Request api
		ResponseEntity<R> bvbResponse =
				requestBVB(requestBody,
						url, fileSend, filename,
						requestId,
						prefix,
						responseClass
				);
		LOGGER.info("bvbResponse type: {}", Objects.requireNonNull(bvbResponse.getBody()).getClass());

		R responseBody = bvbResponse.getBody();

		// validate dto
		ConnResponse isDtoValid = checkDTOAndReturnIfNotValid(responseBody);
		if (isDtoValid != null) {
			return new ResponseEntity<>(isDtoValid, HttpStatus.OK);
		}

		// validate signature
		ConnResponse isSignatureValid = checkSignature(responseBody);
		if (isSignatureValid != null) {
			return new ResponseEntity<>(isSignatureValid, HttpStatus.OK);
		}
		responseBody.setRequest(requestBody);
		return new ResponseEntity<>(responseBody, HttpStatus.OK);
	}



	public MultiValueMap<String, Object> buildRequestForm(BVBIBFTCommonRequest<?> requestBody) throws Exception {
		try {
			MultiValueMap<String, Object> multipartMap = new LinkedMultiValueMap<String, Object>();
			multipartMap.add("requestID", requestBody.getRequestId());
			multipartMap.add("clientCode", requestBody.getClientCode());
			multipartMap.add("clientUserID", requestBody.getClientUserId());
			multipartMap.add("time", requestBody.getTime().getTime());
			multipartMap.add("data", jsonHelper.convertMap2JsonString(requestBody.getData()));
			String stringHex = signatureConstruction(requestBody);
			requestBody.setSignature(stringHex);
			multipartMap.add("signature", requestBody.getSignature());
			LOGGER.log(Level.getLevel("INFOWT"), "(Map<String, Object>) requestBody: {}", multipartMap);
			return multipartMap;
		} catch (Exception e) {
			LOGGER.error("Error occurred when build Request form: ", e);
			return new LinkedMultiValueMap<String, Object>();
		}
	}

	public MultiValueMap<String, Object> buildRequestForm(BVBIBFTCommonRequest<?> requestBody,
														  byte[] fileSend, String filename) throws Exception {
		try {
			MultiValueMap<String, Object> multipartMap = new LinkedMultiValueMap<String, Object>();
			multipartMap.add("requestID", requestBody.getRequestId());
			multipartMap.add("clientCode", requestBody.getClientCode());
			multipartMap.add("clientUserID", requestBody.getClientUserId());
			multipartMap.add("time", String.valueOf(requestBody.getTime().getTime()));
			multipartMap.add("data", jsonHelper.convertMap2JsonString(requestBody.getData()));
			String stringHex = signatureConstruction(requestBody);
			requestBody.setSignature(stringHex);
			multipartMap.add("signature", requestBody.getSignature());
			MultiValueMap<String, String> fileMap = new LinkedMultiValueMap<>();
			ContentDisposition contentDisposition = ContentDisposition
					.builder("form-data")
					.name("file")
					.filename(filename)
					.build();
			fileMap.add(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString());
			HttpEntity<byte[]> fileEntity = new HttpEntity<>(fileSend, fileMap);
			multipartMap.add("file", fileEntity);
			LOGGER.log(Level.getLevel("INFOWT"), "(Map<String, Object>) requestBody: {}", multipartMap);
			return multipartMap;
		} catch (Exception e) {
			LOGGER.error("Error occurred when build Request form: ", e);
			return new LinkedMultiValueMap<String, Object>();
		}
	}

	public String buildUrl(String path) {
		return restTemplateHelper.buildUrl(path, new ArrayList<String>(), new HashMap<>());
	}

	public <T extends BVBIBFTCommonRequest<?>, R extends BVBIBFTCommonResponse<?>>
	ResponseEntity<R> urlEncodedFormRequest(String path,
											HttpHeaders header,
											T requestBody,
											Class<R> referenceType) throws Exception {

		String url = buildUrl(path);
		MultiValueMap<String, Object> multipartMap = buildRequestForm(requestBody);
		RestTemplate restTemplate = null;
		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<MultiValueMap<String, Object>>(multipartMap, header);

		if (restTemplateHelper.isContainHttps(url)) {
			restTemplate = restTemplateHelper.buildForHttps(configLoader.getProxyConfig());
		} else {
			restTemplate = restTemplateHelper.build(configLoader.getProxyConfig());
		}

		MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();

		restTemplate.getMessageConverters().add(new FormHttpMessageConverter());
		restTemplate.getMessageConverters().add(converter);

		return restTemplate.exchange(url, HttpMethod.POST, requestEntity, referenceType);
	}

	public <T extends BVBIBFTCommonRequest<?>, R extends BVBIBFTCommonResponse<?>>
	ResponseEntity<R> urlEncodedFormRequest(String path,
											HttpHeaders header,
											T requestBody,
											byte[] fileSend,
											String filename,
											Class<R> referenceType) throws Exception {

		String url = buildUrl(path);
		MultiValueMap<String, Object> multipartMap = buildRequestForm(requestBody, fileSend, filename);
		RestTemplate restTemplate = null;
		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<MultiValueMap<String, Object>>(multipartMap, header);

		if (restTemplateHelper.isContainHttps(url)) {
			restTemplate = restTemplateHelper.buildForHttps(configLoader.getProxyConfig());
		} else {
			restTemplate = restTemplateHelper.build(configLoader.getProxyConfig());
		}

		MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();

		restTemplate.getMessageConverters().add(new FormHttpMessageConverter());
		restTemplate.getMessageConverters().add(converter);

		return restTemplate.exchange(url, HttpMethod.POST, requestEntity, referenceType);
	}
}

