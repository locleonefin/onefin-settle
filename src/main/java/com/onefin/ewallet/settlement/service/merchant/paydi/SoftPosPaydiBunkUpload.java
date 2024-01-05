package com.onefin.ewallet.settlement.service.merchant.paydi;

import com.onefin.ewallet.common.base.constants.OneFinConstants;
import com.onefin.ewallet.common.domain.constants.DomainConstants;
import com.onefin.ewallet.common.domain.merchant.MerchantTransaction;
import com.onefin.ewallet.common.domain.settlement.SettlementTransaction;
import com.onefin.ewallet.common.quartz.entity.SchedulerJobInfo;
import com.onefin.ewallet.common.utility.date.DateTimeHelper;
import com.onefin.ewallet.common.utility.sercurity.SercurityHelper;
import com.onefin.ewallet.settlement.common.SettleConstants;
import com.onefin.ewallet.settlement.common.SettleHelper;
import com.onefin.ewallet.settlement.config.SFTPPaydiIntegration;
import com.onefin.ewallet.settlement.controller.SettleJobController;
import com.onefin.ewallet.settlement.repository.MerchantTrxRepo;
import com.onefin.ewallet.settlement.repository.SettleTransRepository;
import com.onefin.ewallet.settlement.service.ConfigLoader;
import com.onefin.ewallet.settlement.service.SettleService;
import org.joda.time.DateTime;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

@Component
@DisallowConcurrentExecution
public class SoftPosPaydiBunkUpload extends QuartzJobBean {

	private static final Logger LOGGER = LoggerFactory.getLogger(SoftPosPaydiBunkUpload.class);

	private static final String SOFTPOS_TRANS_FILE_NAME = "%s_SOFTPOS_TRANS_ONEFIN_PAYDI.txt";

	private static final String SOFTPOS_TRANS_RESULT_FILE_NAME = "%s_SOFTPOS_TRANS_RESULT_ONEFIN_PAYDI.txt";

	@Autowired
	private ConfigLoader configLoader;

	@Autowired
	private DateTimeHelper dateHelper;

	@Autowired
	@Qualifier("SftpPaydiRemoteFileTemplate")
	private SftpRemoteFileTemplate sftpVietinGateway;

	@Autowired
	private SFTPPaydiIntegration.UploadPaydiGateway gateway;

	@Autowired
	private SettleJobController settleJobController;

	@Autowired
	private MerchantTrxRepo transRepository;

	@Autowired
	private Environment env;

	@Autowired
	private SercurityHelper sercurityHelper;

	@Autowired
	private SettleTransRepository settleRepo;

	@Autowired
	private SettleHelper settleHelper;

	@Autowired
	private SettleService commonSettleService;

	/*
	 * *************************BUNK UPLOAD FILE PROCESSING*****************************
	 */

	/**
	 * @param context
	 */
	@Override
	protected void executeInternal(JobExecutionContext context) {
		DateTime currentTime = dateHelper.currentDateTime(OneFinConstants.HO_CHI_MINH_TIME_ZONE);
		try {
			taskBunkUploadProcess(currentTime);
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
		}
	}

	public void bunkUploadManully(String settleDate) {
		DateTime currentTime = dateHelper.parseDate(settleDate, OneFinConstants.HO_CHI_MINH_TIME_ZONE, OneFinConstants.DATE_FORMAT_ddMMyyyy);
		try {
			taskBunkUploadProcess(currentTime);
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
		}
	}

	/*
	 * *************************BUNK UPLOAD FILE PROCESSING*****************************
	 */

	public void taskBunkUploadProcess(DateTime currentTime) throws Exception {
		LOGGER.info("Start process soft pos bunk upload file {}", currentTime);
		DateTime previousTime = currentTime.minusDays(1);
		Date previousDate = previousTime.toLocalDateTime().toDate();
		SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
		String previousDateString = formatter.format(previousDate);
		String bunkUploadFile = String.format(SOFTPOS_TRANS_FILE_NAME, previousDateString);
		LOGGER.info("Soft pos bunk upload file name {}", bunkUploadFile);
		AtomicReference<Boolean> processedBunkUpload = new AtomicReference<>();
		processedBunkUpload.set(false);

		SettlementTransaction trx = settleRepo.findByPartnerAndDomainAndSettleDate(SettleConstants.PARTNER_PAYDI,
				SettleConstants.SOFTPOS_MERCHANT, new SimpleDateFormat(SettleConstants.DATE_FORMAT_yyyyMMdd).format(currentTime.toLocalDateTime().toDate()));
		if (trx == null) {
			trx = (SettlementTransaction) settleHelper.createModelStructure(new SettlementTransaction());
			trx.getSettleKey().setDomain(SettleConstants.SOFTPOS_MERCHANT);
			trx.getSettleKey().setPartner(SettleConstants.PARTNER_PAYDI);
			trx.getSettleKey().setSettleDate(new SimpleDateFormat(SettleConstants.DATE_FORMAT_yyyyMMdd).format(currentTime.toLocalDateTime().toDate()));
			trx.setStatus(SettleConstants.SETTLEMENT_PROCESSING);
			trx.getFile().add(bunkUploadFile);
			commonSettleService.save(trx);
		}

		try {
			SettlementTransaction finalTrx = trx;
			sftpVietinGateway.get(configLoader.getSftpPaydiRemoteIn() + bunkUploadFile, stream -> {
				FileCopyUtils.copy(stream, new FileOutputStream(configLoader.getSftpPaydiStoreFile() + bunkUploadFile));
				File file = new File(configLoader.getSftpPaydiStoreFile() + bunkUploadFile);
				processBunkUpload(file.getCanonicalPath(), previousDateString, finalTrx);
				processedBunkUpload.set(true);
				SchedulerJobInfo jobTmp = new SchedulerJobInfo();
				jobTmp.setJobClass(SoftPosPaydiBunkUpload.class.getName());
				settleJobController.pauseJob(jobTmp);
				file.delete();
				finalTrx.setStatus(SettleConstants.SETTLEMENT_SUCCESS);
				commonSettleService.update(finalTrx);
				LOGGER.info("End soft pos bunk upload file {}", currentTime);
			});
		} catch (Exception e) {
			LOGGER.warn("Not Found soft pos bunk Upload File {}", previousDate, e);
		}
		Date currentLocalTime = new SimpleDateFormat("HH:mm:ss").parse(String.format("%s:%s:%s", currentTime.getHourOfDay(), currentTime.getMinuteOfHour(), currentTime.getSecondOfMinute()));
		Date settleCompletedBeforeTime = new SimpleDateFormat("HH:mm:ss").parse(env.getProperty("settlement.merchant.paydi.settleCompletedBefore"));
		LOGGER.info("SettleCompletedBeforeTime {}, currentLocalTime {}", settleCompletedBeforeTime, currentLocalTime);
		if (currentLocalTime.after(settleCompletedBeforeTime)) {
			if (processedBunkUpload.get().equals(false)) {
				LOGGER.error("Paydi not send soft pos bunk upload file!!!!");
			}
			SchedulerJobInfo jobTmp = new SchedulerJobInfo();
			jobTmp.setJobClass(SoftPosPaydiBunkUpload.class.getName());
			settleJobController.pauseJob(jobTmp);
		}
	}

	private void processBunkUpload(String disputeFile, String date, SettlementTransaction finalTrx) {
		try (Stream<String> stream = Files.lines(Paths.get(disputeFile))) {
			File resultFile = new File(configLoader.getSftpPaydiStoreFile() + String.format(SOFTPOS_TRANS_RESULT_FILE_NAME, date));
			BufferedWriter writer = new BufferedWriter(new FileWriter(resultFile));
			writeFileResult(writer, new String[]{"RecordType", "RcReconcile", "_id", "bank_code", "odoo_contact_id", "total_amount", "currency", "created_time", "obj_type", "Checksum"});
			AtomicInteger record = new AtomicInteger(0);
			stream.forEach((line) -> {
				try {
					String[] arr = line.split("\\|");
					LOGGER.info("Soft pos trx details: {}", line);
					if (arr[0].equals(configLoader.getRecordTypeDetail())) {
						record.incrementAndGet();
						String checkSumInput = String.format("%s%s%s%s%s%s%s%s", arr[2], arr[3], arr[4], arr[5], arr[6], arr[7], arr[8], configLoader.getPrivateKeyChecksum());
						String checksum = sercurityHelper.MD5Hashing(checkSumInput, false);
						MerchantTransaction trx = new MerchantTransaction();
						if (checksum.equals(arr[9])) {
							trx.setChecksum(true);
							writeFileResult(writer, new String[]{arr[0], arr[1], arr[2], arr[3], arr[4], arr[5], arr[6], arr[7], arr[8], arr[9]});
						} else {
							writeFileResult(writer, new String[]{arr[0], configLoader.getRcReconcileFail(), arr[2], arr[3], arr[4], arr[5], arr[6], arr[7], arr[8], arr[9]});
						}
						Date currentDate = dateHelper.currentDate(OneFinConstants.HO_CHI_MINH_TIME_ZONE);
						trx.setCreatedDate(currentDate);
						trx.setUpdatedDate(currentDate);
						trx.setPartnerTrxId(arr[2]);
						trx.setBankCode(arr[3]);
						trx.setPartnerMid(arr[4]);
						trx.setTrxAmount(new BigDecimal(arr[5]));
						trx.setCurrency(arr[6]);
						trx.setTrxDate(dateHelper.parseDate2(arr[7].split("\\.")[0], "yyyy-MM-dd HH:mm:ss"));
						trx.setTrxType(arr[8]);
						transRepository.save(trx);
					}
					if (arr[0].equals(configLoader.getRecordTypeEnd())) {
						String currentDate = dateHelper.currentDateString(OneFinConstants.HO_CHI_MINH_TIME_ZONE, DomainConstants.DATE_FORMAT_TRANS);
						String checkSumInput = String.format("%s%s%s", arr[3], arr[1], configLoader.getPrivateKeyChecksum());
						String checksum = sercurityHelper.MD5Hashing(checkSumInput, false);
						if (record.get() != Integer.parseInt(arr[1])) {
							LOGGER.error("Paydi total record not match!!!!!");
						}
						if (!checksum.equals(arr[4])) {
							LOGGER.error("Paydi checksum not match!!!!!");
						}
						String checkSumInputResult = String.format("%s%s%s", currentDate, arr[1], configLoader.getPrivateKeyChecksum());
						String checksumResult = sercurityHelper.MD5Hashing(checkSumInputResult, false);
						writeFileResult(writer, new String[]{configLoader.getRecordTypeEnd(), arr[1], "OneFin", currentDate, checksumResult});
					}
				} catch (Exception e) {
					LOGGER.error(e.getMessage(), e);
				}

			});
			writer.close();
			gateway.upload(resultFile);
			finalTrx.getFile().add(resultFile.getName());
			resultFile.delete();
		} catch (IOException e) {
			LOGGER.error("Failed to process soft pos bunk upload file", e);
		}
	}

	private void writeFileResult(BufferedWriter writer, String[] arr) throws IOException {
		try {
			if (arr[0].equals(configLoader.getRecordTypeDetail())) {
				String newRecord = String.format("%s|%s|%s|%s|%s|%s|%s|%s|%s|%s", arr[0], arr[1], arr[2], arr[3], arr[4], arr[5], arr[6], arr[7], arr[8], arr[9]);
				writer.write(newRecord);
				writer.newLine();
			} else if (arr[0].equals(configLoader.getRecordTypeEnd())) {
				String newRecord = String.format("%s|%s|%s|%s|%s", arr[0], arr[1], arr[2], arr[3], arr[4]);
				writer.write(newRecord);
				writer.newLine();
			} else {
				String newRecord = String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s", arr[0], arr[1], arr[2], arr[3], arr[4], arr[5], arr[6], arr[7], arr[8], arr[9]);
				writer.write(newRecord);
				writer.newLine();
			}
		} catch (Exception ex) {
			LOGGER.error(ex.getMessage(), ex);
		}

	}
}
