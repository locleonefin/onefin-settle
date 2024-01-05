package com.onefin.ewallet.settlement.service.napas.ew3;

import com.onefin.ewallet.common.domain.settlement.SettlementTransaction;
import com.onefin.ewallet.common.quartz.entity.SchedulerJobInfo;
import com.onefin.ewallet.common.utility.date.DateTimeHelper;
import com.onefin.ewallet.settlement.common.SettleConstants;
import com.onefin.ewallet.settlement.controller.SettleJobController;
import com.onefin.ewallet.settlement.pgpas.PGPas;
import com.onefin.ewallet.settlement.pgpas.PKICrypt;
import com.onefin.ewallet.settlement.repository.SettleTransRepository;
import com.onefin.ewallet.settlement.service.ConfigLoader;
import com.onefin.ewallet.settlement.service.MinioService;
import com.onefin.ewallet.settlement.service.SettleService;
import com.onefin.ewallet.settlement.service.napas.ew1.NapasWL1ProcessXL;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.PrivateKey;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author thaita
 */
@Component
@DisallowConcurrentExecution
public class NapasWL3ProcessXL extends QuartzJobBean {

	private static final Logger LOGGER = LoggerFactory.getLogger(NapasWL3ProcessXL.class);

	private static final String LOG_NAPAS_PG_PREFIX = SettleConstants.PARTNER_NAPAS + " " + SettleConstants.PAYMENT_GATEWAYWL3;

	private static final String NAPAS_PG_ISSUE_SUBJECT_XL = "[Napas - WL2] - Settle XL error";

	private static final String NAPAS_PG_SUBJECT_XL = "[Napas - WL2] - Settle XL dispute";

	@Autowired
	private ConfigLoader configLoader;

	@Autowired
	private DateTimeHelper dateHelper;

	@Autowired
	private PGPas pGPas;

	@Autowired
	private PKICrypt pKICrypt;

	@Autowired
	private SettleTransRepository settleRepo;

	@Autowired
	@Qualifier("SftpNapasRemoteFileTemplateEw")
	private SftpRemoteFileTemplate sftpNapasGateway;

	@Autowired
	private SettleService commonSettleService;

	@Autowired
	private MinioService minioService;

	@Autowired
	private SettleJobController settleJobController;

	/* *************************WATING XL FILE FROM NAPAS***************************** */

	/**
	 * @throws Exception
	 */
	@Override
	protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
		LOGGER.info("{} Start Napas Settlement XL", LOG_NAPAS_PG_PREFIX);
		List<SettlementTransaction> transPendXLs = settleRepo
				.findAllByPartnerAndDomainAndStatusTransaction(SettleConstants.PARTNER_NAPAS, SettleConstants.PAYMENT_GATEWAYWL3, SettleConstants.SETTLEMENT_PENDING_T1);
		LOGGER.info("{} Number Napas Settlement XL {}", LOG_NAPAS_PG_PREFIX, transPendXLs.size());
		if (transPendXLs.size() == 0) {
			// Settle completed => paused job
			SchedulerJobInfo jobTmp = new SchedulerJobInfo();
			jobTmp.setJobClass(NapasWL3ProcessXL.class.getName());
			settleJobController.pauseJob(jobTmp);
		}
		for (SettlementTransaction transPendXL : transPendXLs) {
			// Pre-processing
			LOGGER.info("{} XL Transaction: {}", LOG_NAPAS_PG_PREFIX, transPendXL);
			Date settleTCDate = dateHelper.parseDate2(transPendXL.getSettleKey().getSettleDate(), SettleConstants.HO_CHI_MINH_TIME_ZONE, SettleConstants.DATE_FORMAT_MMddyy);
			Date currentDate = dateHelper.currentDate(SettleConstants.HO_CHI_MINH_TIME_ZONE);
			long diff = currentDate.getTime() - settleTCDate.getTime();
			TimeUnit time = TimeUnit.DAYS;
			long diffrence = time.convert(diff, TimeUnit.MILLISECONDS);

			if (diffrence == 1) {
				// Download remote to local T+1
				String next1Date = dateHelper.nextTDateString(transPendXL.getSettleKey().getSettleDate(), SettleConstants.HO_CHI_MINH_TIME_ZONE,
						SettleConstants.DATE_FORMAT_MMddyy, 1);
				String settleFileName1 = String.format("%s_%s_%s_%s_%s_%s_%s.dat.pgp", next1Date,
						configLoader.getSettleEwNapasZZZ(), configLoader.getSettleEwNapasBBB3(),
						configLoader.getSettleEwNapasTctvCode(), configLoader.getSettleEwNapasSettleOrder(),
						configLoader.getSettleEwNapasSettleFileTypeXL(), configLoader.getSettleEwNapasServiceCodeEcom());
				LOGGER.info("{} Retrieve Napas Settlement XL+1, file name {}", LOG_NAPAS_PG_PREFIX, settleFileName1);
				try {
					sftpNapasGateway.get(configLoader.getSftpEwNapasDirectoryRemoteIn() + settleFileName1, stream -> {
						FileCopyUtils.copy(stream, new FileOutputStream(
								new File(configLoader.getSftpEwNapasDirectoryLocalFile() + settleFileName1)));
					});
					// Settle completed => paused job
					SchedulerJobInfo jobTmp = new SchedulerJobInfo();
					jobTmp.setJobClass(NapasWL3ProcessXL.class.getName());
					settleJobController.pauseJob(jobTmp);
					napasEwalletResponseProcessXL(configLoader.getSftpEwNapasDirectoryLocalFile() + settleFileName1,
							settleFileName1, transPendXL, 1);
				} catch (Exception e) {
					LOGGER.info("{} Not Found Napas Settlement XL+1 {}", LOG_NAPAS_PG_PREFIX, e);
				}
			}

			if (diffrence == 2) {
				// Download remote to local T+2
				String next2Date = dateHelper.nextTDateString(transPendXL.getSettleKey().getSettleDate(), SettleConstants.HO_CHI_MINH_TIME_ZONE,
						SettleConstants.DATE_FORMAT_MMddyy, 2);
				// Download remote to local
				String settleFileName2 = String.format("%s_%s_%s_%s_%s_%s_%s.dat.pgp", next2Date,
						configLoader.getSettleEwNapasZZZ(), configLoader.getSettleEwNapasBBB3(),
						configLoader.getSettleEwNapasTctvCode(), configLoader.getSettleEwNapasSettleOrder(),
						configLoader.getSettleEwNapasSettleFileTypeXL(), configLoader.getSettleEwNapasServiceCodeEcom());
				LOGGER.info("{} Retrieve Napas Settlement XL+2, file name {}", LOG_NAPAS_PG_PREFIX, settleFileName2);
				try {
					sftpNapasGateway.get(configLoader.getSftpEwNapasDirectoryRemoteIn() + settleFileName2, stream -> {
						FileCopyUtils.copy(stream, new FileOutputStream(
								new File(configLoader.getSftpEwNapasDirectoryLocalFile() + settleFileName2)));
					});
					// Settle completed => paused job
					SchedulerJobInfo jobTmp = new SchedulerJobInfo();
					jobTmp.setJobClass(NapasWL3ProcessXL.class.getName());
					settleJobController.pauseJob(jobTmp);
					napasEwalletResponseProcessXL(configLoader.getSftpEwNapasDirectoryLocalFile() + settleFileName2,
							settleFileName2, transPendXL, 2);
				} catch (Exception e) {
					LOGGER.info("{} Not Found Napas Settlement XL+2 {}", LOG_NAPAS_PG_PREFIX, e);
				}
			}

			if (diffrence > 2) {
				LOGGER.info("{} Diffrence over 2 time, admin check {}", LOG_NAPAS_PG_PREFIX);
				transPendXL.setStatus(SettleConstants.SETTLEMENT_SUCCESS);
				try {
					commonSettleService.update(transPendXL);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * @throws ParseException
	 * @throws IOException    Look up in Napas SFTP IN Folder
	 */
	public ResponseEntity<?> ewalletNapasXLSettlementManually(String settleDate, String order,
	                                                          SettlementTransaction transPendXL, int diffDay) throws ParseException, IOException {
		LOGGER.info("== Start Napas Settlement XL");
		// Download remote to local
		String settleFileName = String.format("%s_%s_%s_%s_%s_%s_%s.dat.pgp", settleDate, configLoader.getSettleEwNapasZZZ(),
				configLoader.getSettleEwNapasBBB3(), configLoader.getSettleEwNapasTctvCode(), order,
				configLoader.getSettleEwNapasSettleFileTypeXL(), configLoader.getSettleEwNapasServiceCodeEcom());
		try {
			sftpNapasGateway.get(configLoader.getSftpEwNapasDirectoryRemoteIn() + settleFileName, stream -> {
				FileCopyUtils.copy(stream,
						new FileOutputStream(new File(configLoader.getSftpEwNapasDirectoryLocalFile() + settleFileName)));
			});
			napasEwalletResponseProcessXL(configLoader.getSftpEwNapasDirectoryLocalFile() + settleFileName,
					settleFileName, transPendXL, diffDay);
			return new ResponseEntity<>("Processing", HttpStatus.OK);
		} catch (Exception e) {
			return new ResponseEntity<>("Not Found Napas Settlement XL", HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/* *************************WATING XL FILE FROM NAPAS***************************** */

	/**
	 * @param napasEncode
	 * @param napasFileName
	 * @param transPendTCXL
	 * @throws Exception
	 */
	public void napasEwalletResponseProcessXL(String napasEncode, String napasFileName,
	                                          SettlementTransaction transPendTCXL, int diffDay) throws Exception {
		LOGGER.info("{} Start Napas XL Settlement Processing File {} {}", LOG_NAPAS_PG_PREFIX, diffDay, napasFileName);
		// Decrypt
		LOGGER.info("{} Start Decrypt Napas {} {}", LOG_NAPAS_PG_PREFIX, diffDay, napasEncode);
		PrivateKey privateKey = pKICrypt.getPrivateKey(configLoader.getSftpNapasDirectoryOfPrivateKey(), "");
		String decodeFile = configLoader.getSftpEwNapasDirectoryLocalFile() + napasFileName.replace(".pgp", "");
		try {
			pGPas.PGPdecrypt(napasEncode, decodeFile, privateKey);
			File XLFile = new File(decodeFile);
			if (!transPendTCXL.getFile().contains(XLFile.getName())) {
				transPendTCXL.getFile().add(XLFile.getName());
			}
			String currentDate = dateHelper.currentDateString(SettleConstants.HO_CHI_MINH_TIME_ZONE, SettleConstants.DATE_FORMAT_ddMMyyyy);
			minioService.uploadFile(configLoader.getBaseBucket(), configLoader.getSettleNapasEWMinioDefaultFolder() + currentDate + "/" + XLFile.getName(), XLFile, "text/plain");
			LOGGER.info("{} Uploaded XL File MinIO {} {}", LOG_NAPAS_PG_PREFIX, diffDay, currentDate);
			XLFile.delete();
			new File(napasEncode).delete();
			// Notify for admin
			LOGGER.info("{} Notification Process XL Success File {} {}", LOG_NAPAS_PG_PREFIX, diffDay, currentDate);
			commonSettleService.settleCompleted(currentDate, configLoader.getBaseBucket(), configLoader.getSettleNapasEWMinioDefaultFolder() + currentDate + "/" + XLFile.getName(), NAPAS_PG_SUBJECT_XL);
//			if(diffDay == 1) {
//			transPendTCXL.setStatus(SettleConstants.SETTLEMENT_PENDING_T2);
//			}
			if (diffDay == 2) {
				transPendTCXL.setStatus(SettleConstants.SETTLEMENT_SUCCESS);
			}
			commonSettleService.update(transPendTCXL);
			LOGGER.info("{} End Process XL File {} {}", LOG_NAPAS_PG_PREFIX, diffDay, currentDate);
		} catch (Exception e) {
			LOGGER.info("{} Decrypt Error {} {} {} ", LOG_NAPAS_PG_PREFIX, napasEncode, diffDay, e);
		}
	}

}
