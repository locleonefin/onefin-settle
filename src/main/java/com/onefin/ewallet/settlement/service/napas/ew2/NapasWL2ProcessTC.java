package com.onefin.ewallet.settlement.service.napas.ew2;

import com.onefin.ewallet.common.domain.napas.NapasEwalletEWTransactionView;
import com.onefin.ewallet.common.domain.settlement.SettlementTransaction;
import com.onefin.ewallet.common.quartz.entity.SchedulerJobInfo;
import com.onefin.ewallet.common.utility.date.DateTimeHelper;
import com.onefin.ewallet.common.utility.sercurity.SercurityHelper;
import com.onefin.ewallet.settlement.common.SettleConstants;
import com.onefin.ewallet.settlement.common.SettleConstants.NapasSettleRuleEW;
import com.onefin.ewallet.settlement.config.SFTPNapasEwalletIntegrationEW.UploadNapasGatewayEW;
import com.onefin.ewallet.settlement.controller.SettleJobController;
import com.onefin.ewallet.settlement.dto.NapasSettlement;
import com.onefin.ewallet.settlement.pgpas.PGPas;
import com.onefin.ewallet.settlement.pgpas.PKICrypt;
import com.onefin.ewallet.settlement.repository.NapasETransRepoEW;
import com.onefin.ewallet.settlement.repository.SettleTransRepository;
import com.onefin.ewallet.settlement.service.ConfigLoader;
import com.onefin.ewallet.settlement.service.MinioService;
import com.onefin.ewallet.settlement.service.SettleService;
import org.apache.commons.lang3.StringUtils;
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

import java.io.*;
import java.math.BigDecimal;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author thaita
 */
@Component
@DisallowConcurrentExecution
public class NapasWL2ProcessTC extends QuartzJobBean {

	private static final Logger LOGGER = LoggerFactory.getLogger(NapasWL2ProcessTC.class);

	private static final String LOG_NAPAS_PG_PREFIX = SettleConstants.PARTNER_NAPAS + " " + SettleConstants.PAYMENT_GATEWAYWL2;

	private static final String NAPAS_PG_SUBJECT_NO_SETTLE = "[Napas - WL2] - Settle success";

	private static final String NAPAS_PG_SUBJECT_SETTLE = "[Napas - WL2] - Settle TC dispute";

	private static final String NAPAS_PG_ISSUE_SUBJECT = "[Napas - WL2] - Settle TC error";

	@Autowired
	private NapasETransRepoEW<?> transRepository;

	@Autowired
	private ConfigLoader configLoader;

	@Autowired
	private UploadNapasGatewayEW gateway;

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
	private SercurityHelper sercurityHelper;

	@Autowired
	private MinioService minioService;

	@Autowired
	private SettleJobController settleJobController;

	/* *************************WATING TC FILE FROM NAPAS***************************** */

	/**
	 * @throws ParseException
	 * @throws IOException    1. Summary all date not yet settled
	 *                        2. Compare with OneFin trans with Napas TC trans
	 */
	@Override
	protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
		LOGGER.info("{} Start Napas Waiting TC File", LOG_NAPAS_PG_PREFIX);
		// Step 1: Summary day need to be settled
		List<SettlementTransaction> transPendTCs = settleRepo
				.findAllByPartnerAndDomainAndStatusTransaction(SettleConstants.PARTNER_NAPAS, SettleConstants.PAYMENT_GATEWAYWL2, SettleConstants.SETTLEMENT_PENDING_T0);
		LOGGER.info("{} Number Napas Settlement Days TC {}", LOG_NAPAS_PG_PREFIX, transPendTCs.size());
		List<String> settleDate = new ArrayList<String>();

		for (SettlementTransaction transPendTC : transPendTCs) {
			settleDate.add(transPendTC.getSettleKey().getSettleDate());
		}
		LOGGER.info("{} Day need to settled {}", LOG_NAPAS_PG_PREFIX, settleDate);
		// Step 2: Find Napas settle file base on settle date
		LOGGER.info("{} Start find TC settle file in SFTP", LOG_NAPAS_PG_PREFIX);
		for (SettlementTransaction transPendTC : transPendTCs) {
			// Download remote to local
			String settleFileName = String.format("%s_%s_%s_%s_%s_%s_%s.dat.pgp", transPendTC.getSettleKey().getSettleDate(),
					configLoader.getSettleEwNapasZZZ(), configLoader.getSettleEwNapasBBB2(),
					configLoader.getSettleEwNapasTctvCode(), configLoader.getSettleEwNapasSettleOrder(),
					configLoader.getSettleEwNapasSettleFileTypeTC(), configLoader.getSettleEwNapasServiceCodeEcom());
			LOGGER.info("{} File name: {}", LOG_NAPAS_PG_PREFIX, settleFileName);
			try {
				// Try to retreive Napas File
				sftpNapasGateway.get(configLoader.getSftpEwNapasDirectoryRemoteIn() + settleFileName, stream -> {
					FileCopyUtils.copy(stream, new FileOutputStream(
							new File(configLoader.getSftpEwNapasDirectoryLocalFile() + settleFileName)));
				});
				// Settle completed => paused job
				SchedulerJobInfo jobTmp = new SchedulerJobInfo();
				jobTmp.setJobClass(NapasWL2ProcessTC.class.getName());
				settleJobController.pauseJob(jobTmp);
				// Update to Pending XL File
				transPendTC.setStatus(SettleConstants.SETTLEMENT_PROCESSING);
				commonSettleService.update(transPendTC);
				// Process SL file and send to Napas
				napasProcessTC(configLoader.getSftpEwNapasDirectoryLocalFile() + settleFileName,
						settleFileName, transPendTC, settleDate);
			} catch (Exception e) {
				LOGGER.info("{} Napas TC Error {}", LOG_NAPAS_PG_PREFIX, e);
				LOGGER.info("{} Not Found Napas Settlement TC {}", LOG_NAPAS_PG_PREFIX, transPendTC.getSettleKey().getSettleDate());
			}
		}
		LOGGER.info("{} End find TC settle file in SFTP", LOG_NAPAS_PG_PREFIX);
	}

	/**
	 * @throws ParseException
	 * @throws IOException    Look up in Napas SFTP IN Folder
	 */
	public ResponseEntity<?> ewalletNapasTCSettlementManually(String date, String order,
	                                                          SettlementTransaction transPendTC, List<String> settleDateList) throws ParseException, IOException {
		LOGGER.info("== Start Napas Settlement TC");
		// Download remote to local
		String settleFileName = String.format("%s_%s_%s_%s_%s_%s_%s.dat.pgp", date, configLoader.getSettleEwNapasZZZ(),
				configLoader.getSettleEwNapasBBB2(), configLoader.getSettleEwNapasTctvCode(), order,
				configLoader.getSettleEwNapasSettleFileTypeTC(), configLoader.getSettleEwNapasServiceCodeEcom());
		try {
			sftpNapasGateway.get(configLoader.getSftpEwNapasDirectoryRemoteIn() + settleFileName, stream -> {
				FileCopyUtils.copy(stream,
						new FileOutputStream(new File(configLoader.getSftpEwNapasDirectoryLocalFile() + settleFileName)));
			});
			// Update to Pending XL File
			transPendTC.setStatus(SettleConstants.SETTLEMENT_PROCESSING);
			commonSettleService.update(transPendTC);
			napasProcessTC(configLoader.getSftpEwNapasDirectoryLocalFile() + settleFileName,
					settleFileName, transPendTC, settleDateList);
			return new ResponseEntity<>("Processing", HttpStatus.OK);
		} catch (Exception e) {
			LOGGER.info("== Napas TC Error {}", e);
			LOGGER.info("== Not Found Napas Settlement TC {}", transPendTC.getSettleKey().getSettleDate());
			return new ResponseEntity<>("Not Found Napas Settlement TC", HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/* *************************WATING TC FILE FROM NAPAS***************************** */

	/**
	 * @param napasEncode
	 * @param napasFileName
	 * @param settleDateList
	 * @throws Exception
	 */
	public void napasProcessTC(String napasEncode, String napasFileName,
	                           SettlementTransaction transPendTCXL, List<String> settleDateList) throws Exception {
		LOGGER.info("{} Start Napas Settlement Processing SL File {}", LOG_NAPAS_PG_PREFIX, napasFileName);
		// Step 1 Decrypt Napas TC File
		LOGGER.info("== Start Decrypt Napas {}", napasEncode);
		PrivateKey privateKey = pKICrypt.getPrivateKey(configLoader.getSftpEwNapasDirectoryOfPrivateKey(), "");
		String decodeFile = configLoader.getSftpEwNapasDirectoryLocalFile() + napasFileName.replace(".pgp", "");
		try {
			pGPas.PGPdecrypt(napasEncode, decodeFile, privateKey);
		} catch (Exception e) {
			LOGGER.info("{} Decrypt Error {} {}", LOG_NAPAS_PG_PREFIX, napasEncode, e);
		}
		LOGGER.info("{} Success Decrypt Napas {}", LOG_NAPAS_PG_PREFIX, napasEncode);
		File TCFile = new File(decodeFile);
		File SLFile = null;
		File OneFinInternal = null;
		String SLFileName = napasFileName
				.replace(configLoader.getSettleEwNapasSettleFileTypeTC(), configLoader.getSettleEwNapasSettleFileTypeSL())
				.replace(".pgp", "");
		BufferedWriter writer = null;
		BufferedWriter OneFinInternalWriter = null;
		FileReader reader = null;
		BufferedReader br = null;

		// Step 2 Query All Trans to Settle
		List<NapasEwalletEWTransactionView> settleNapasSuccess = new ArrayList<NapasEwalletEWTransactionView>();
		List<NapasEwalletEWTransactionView> settleNapasUnSuccess = new ArrayList<NapasEwalletEWTransactionView>();
		LOGGER.info("{} Settle Date List {}", LOG_NAPAS_PG_PREFIX, settleDateList);
		for (String tmp : settleDateList) {
			String previousDate = dateHelper.previousDateString(tmp, SettleConstants.HO_CHI_MINH_TIME_ZONE,
					SettleConstants.DATE_FORMAT_MMddyy);
			LOGGER.info("{} Settle Date {}", LOG_NAPAS_PG_PREFIX, previousDate);
			// Query Success trans
			List<NapasEwalletEWTransactionView> transTmp1 = transRepository.findByEqualStatusAndNapasTransDate(
					configLoader.getNapasSettleAction(), SettleConstants.TRANS_SUCCESS, previousDate, configLoader.getSettleEwNapasBBB2());
			if (transTmp1.size() != 0) {
				settleNapasSuccess.addAll(transTmp1);
			}
			// Query UnSuccess trans
			List<NapasEwalletEWTransactionView> transTmp2 = transRepository.findByNotEqualStatusAndNapasTransDate(
					configLoader.getNapasSettleAction(), SettleConstants.TRANS_SUCCESS, previousDate, configLoader.getSettleEwNapasBBB2());
			if (transTmp2.size() != 0) {
				settleNapasUnSuccess.addAll(transTmp2);
			}
		}
		LOGGER.info("{} Settle Success trans {}", LOG_NAPAS_PG_PREFIX, settleNapasSuccess.size());
		LOGGER.info("{} Settle Fail trans {}", LOG_NAPAS_PG_PREFIX, settleNapasUnSuccess.size());
		int row = 0;
		int rowOneFinInternal = 0;
		// Step 3 Create SL File And Check line by line in TC file with OneFin trans
		LOGGER.info("{} Create SL File And Check line by line in TC file with OneFin transaction", LOG_NAPAS_PG_PREFIX);
		try {
			SLFile = new File(configLoader.getSftpEwNapasDirectoryLocalFile() + SLFileName);
			OneFinInternal = new File(configLoader.getCeNapasDirectoryLocalFile() + "OFInternal2" + SLFileName);
			writer = new BufferedWriter(new FileWriter(SLFile));
			OneFinInternalWriter = new BufferedWriter(new FileWriter(OneFinInternal));
			reader = new FileReader(decodeFile);
			br = new BufferedReader(reader);
			// read line by line
			String line = br.readLine();
			String settleDate = line.substring(line.indexOf("[DATE]") + "[DATE]".length(), line.length());
			writer.write(line);
			writer.newLine();
			List<NapasSettlement> napasSettleTrans = new ArrayList<NapasSettlement>();
			String TRFinal = null;
			LOGGER.info("{} Read TC file successfully", LOG_NAPAS_PG_PREFIX);
			while ((line = br.readLine()) != null) {
				int startIndex = -1;
				// Detail record
				if (line.subSequence(0, 2).equals(NapasSettleRuleEW.DR.getField())) {
					startIndex = line.indexOf(NapasSettleRuleEW.MTI.getField()) + NapasSettleRuleEW.MTI.getField().length();
					String MTI = line.substring(startIndex, startIndex + NapasSettleRuleEW.MTI.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.F2.getField()) + NapasSettleRuleEW.F2.getField().length();
					String F2 = line.substring(startIndex, startIndex + NapasSettleRuleEW.F2.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.F3.getField()) + NapasSettleRuleEW.F3.getField().length();
					String F3 = line.substring(startIndex, startIndex + NapasSettleRuleEW.F3.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.SVC.getField()) + NapasSettleRuleEW.SVC.getField().length();
					String SVC = line.substring(startIndex, startIndex + NapasSettleRuleEW.SVC.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.TCC.getField()) + NapasSettleRuleEW.TCC.getField().length();
					String TCC = line.substring(startIndex, startIndex + NapasSettleRuleEW.TCC.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.F4.getField()) + NapasSettleRuleEW.F4.getField().length();
					String F4 = line.substring(startIndex, startIndex + NapasSettleRuleEW.F4.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.RTA.getField()) + NapasSettleRuleEW.RTA.getField().length();
					String RTA = line.substring(startIndex, startIndex + NapasSettleRuleEW.RTA.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.F49.getField()) + NapasSettleRuleEW.F49.getField().length();
					String F49 = line.substring(startIndex, startIndex + NapasSettleRuleEW.F49.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.F5.getField()) + NapasSettleRuleEW.F5.getField().length();
					String F5 = line.substring(startIndex, startIndex + NapasSettleRuleEW.F5.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.F50.getField()) + NapasSettleRuleEW.F50.getField().length();
					String F50 = line.substring(startIndex, startIndex + NapasSettleRuleEW.F50.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.F9.getField()) + NapasSettleRuleEW.F9.getField().length();
					String F9 = line.substring(startIndex, startIndex + NapasSettleRuleEW.F9.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.F6.getField()) + NapasSettleRuleEW.F6.getField().length();
					String F6 = line.substring(startIndex, startIndex + NapasSettleRuleEW.F6.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.RCA.getField()) + NapasSettleRuleEW.RCA.getField().length();
					String RCA = line.substring(startIndex, startIndex + NapasSettleRuleEW.RCA.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.F51.getField()) + NapasSettleRuleEW.F51.getField().length();
					String F51 = line.substring(startIndex, startIndex + NapasSettleRuleEW.F51.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.F10.getField()) + NapasSettleRuleEW.F10.getField().length();
					String F10 = line.substring(startIndex, startIndex + NapasSettleRuleEW.F10.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.F11.getField()) + NapasSettleRuleEW.F11.getField().length();
					String F11 = line.substring(startIndex, startIndex + NapasSettleRuleEW.F11.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.F12.getField()) + NapasSettleRuleEW.F12.getField().length();
					String F12 = line.substring(startIndex, startIndex + NapasSettleRuleEW.F12.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.F13.getField()) + NapasSettleRuleEW.F13.getField().length();
					String F13 = line.substring(startIndex, startIndex + NapasSettleRuleEW.F13.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.F15.getField()) + NapasSettleRuleEW.F15.getField().length();
					String F15 = line.substring(startIndex, startIndex + NapasSettleRuleEW.F15.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.F18.getField()) + NapasSettleRuleEW.F18.getField().length();
					String F18 = line.substring(startIndex, startIndex + NapasSettleRuleEW.F18.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.F22.getField()) + NapasSettleRuleEW.F22.getField().length();
					String F22 = line.substring(startIndex, startIndex + NapasSettleRuleEW.F22.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.F25.getField()) + NapasSettleRuleEW.F25.getField().length();
					String F25 = line.substring(startIndex, startIndex + NapasSettleRuleEW.F25.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.F41.getField()) + NapasSettleRuleEW.F41.getField().length();
					String F41 = line.substring(startIndex, startIndex + NapasSettleRuleEW.F41.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.ACQ.getField()) + NapasSettleRuleEW.ACQ.getField().length();
					String ACQ = line.substring(startIndex, startIndex + NapasSettleRuleEW.ACQ.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.ISS.getField()) + NapasSettleRuleEW.ISS.getField().length();
					String ISS = line.substring(startIndex, startIndex + NapasSettleRuleEW.ISS.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.MID.getField()) + NapasSettleRuleEW.MID.getField().length();
					String MID = line.substring(startIndex, startIndex + NapasSettleRuleEW.MID.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.BNB.getField()) + NapasSettleRuleEW.BNB.getField().length();
					String BNB = line.substring(startIndex, startIndex + NapasSettleRuleEW.BNB.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.F102.getField())
							+ NapasSettleRuleEW.F102.getField().length();
					String F102 = line.substring(startIndex, startIndex + NapasSettleRuleEW.F102.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.F103.getField())
							+ NapasSettleRuleEW.F103.getField().length();
					String F103 = line.substring(startIndex, startIndex + NapasSettleRuleEW.F103.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.SVFISSNP.getField())
							+ NapasSettleRuleEW.SVFISSNP.getField().length();
					String SVFISSNP = line.substring(startIndex, startIndex + NapasSettleRuleEW.SVFISSNP.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.IRFISSACQ.getField())
							+ NapasSettleRuleEW.IRFISSACQ.getField().length();
					String IRFISSACQ = line.substring(startIndex, startIndex + NapasSettleRuleEW.IRFISSACQ.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.IRFISSBNB.getField())
							+ NapasSettleRuleEW.IRFISSBNB.getField().length();
					String IRFISSBNB = line.substring(startIndex, startIndex + NapasSettleRuleEW.IRFISSBNB.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.SVFACQNP.getField())
							+ NapasSettleRuleEW.SVFACQNP.getField().length();
					String SVFACQNP = line.substring(startIndex, startIndex + NapasSettleRuleEW.SVFACQNP.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.IRFACQISS.getField())
							+ NapasSettleRuleEW.IRFACQISS.getField().length();
					String IRFACQISS = line.substring(startIndex, startIndex + NapasSettleRuleEW.IRFACQISS.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.IRFACQBNB.getField())
							+ NapasSettleRuleEW.IRFACQBNB.getField().length();
					String IRFACQBNB = line.substring(startIndex, startIndex + NapasSettleRuleEW.IRFACQBNB.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.SVFBNBNP.getField())
							+ NapasSettleRuleEW.SVFBNBNP.getField().length();
					String SVFBNBNP = line.substring(startIndex, startIndex + NapasSettleRuleEW.SVFBNBNP.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.IRFBNBISS.getField())
							+ NapasSettleRuleEW.IRFBNBISS.getField().length();
					String IRFBNBISS = line.substring(startIndex, startIndex + NapasSettleRuleEW.IRFBNBISS.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.IRFBNBACQ.getField())
							+ NapasSettleRuleEW.IRFBNBACQ.getField().length();
					String IRFBNBACQ = line.substring(startIndex, startIndex + NapasSettleRuleEW.IRFBNBACQ.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.F37.getField()) + NapasSettleRuleEW.F37.getField().length();
					String F37 = line.substring(startIndex, startIndex + NapasSettleRuleEW.F37.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.F38.getField()) + NapasSettleRuleEW.F38.getField().length();
					String F38 = line.substring(startIndex, startIndex + NapasSettleRuleEW.F38.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.TRN.getField()) + NapasSettleRuleEW.TRN.getField().length();
					String TRN = line.substring(startIndex, startIndex + NapasSettleRuleEW.TRN.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.RRC.getField()) + NapasSettleRuleEW.RRC.getField().length();
					String RRC = line.substring(startIndex, startIndex + NapasSettleRuleEW.RRC.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.RSV1.getField())
							+ NapasSettleRuleEW.RSV1.getField().length();
					String RSV1 = line.substring(startIndex, startIndex + NapasSettleRuleEW.RSV1.getLength());
					String transDate = findRealStringIgnoreSpace(RSV1.substring(20, 28));
					String orderId = findRealStringIgnoreSpace(RSV1.substring(28, 68));
					startIndex = line.indexOf(NapasSettleRuleEW.RSV2.getField())
							+ NapasSettleRuleEW.RSV2.getField().length();
					String RSV2 = line.substring(startIndex, startIndex + NapasSettleRuleEW.RSV2.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.RSV3.getField())
							+ NapasSettleRuleEW.RSV3.getField().length();
					String RSV3 = line.substring(startIndex, startIndex + NapasSettleRuleEW.RSV3.getLength());
					startIndex = line.indexOf(NapasSettleRuleEW.CSR.getField()) + NapasSettleRuleEW.CSR.getField().length();
					String CSR = line.substring(startIndex, startIndex + NapasSettleRuleEW.CSR.getLength());
					String inputCheckSum = line.substring(0, startIndex);
					NapasSettlement napasSettleTran = new NapasSettlement(MTI, F2, F3, SVC, TCC, F4, RTA, F49, F5, F50,
							F9, F6, RCA, F51, F10, F11, F12, F13, F15, F18, F22, F25, F41, ACQ, ISS, MID, BNB, F102,
							F103, SVFISSNP, IRFISSACQ, IRFISSBNB, SVFACQNP, IRFACQISS, IRFACQBNB, SVFBNBNP, IRFBNBISS,
							IRFBNBACQ, F37, F38, TRN, RRC, RSV1, transDate, orderId, RSV2, RSV3, CSR, inputCheckSum);
					napasSettleTrans.add(napasSettleTran);
				}
			}
			LOGGER.info("{} End read TC file", LOG_NAPAS_PG_PREFIX);
			// Compare and check OF-NAPAS
			// Case 1: Success trans in OF but Napas doesn't have => add to SL file (note check them date)
			// check them date)
			LOGGER.info("{} Start check success trans in OF but Napas doesn't have", LOG_NAPAS_PG_PREFIX);
			for (NapasEwalletEWTransactionView tmp : settleNapasSuccess) {
				LOGGER.info("== Proccess settleNapasSuccess");
				boolean check = false;
				for (NapasSettlement tmp1 : napasSettleTrans) {
					if (tmp1.getOrderId().equals(tmp.getOrderId())
							&& findRealStringIgnoreSpace(tmp1.getF37()).equals(tmp.getAcquirerTransId())
							&& new BigDecimal(tmp1.getF4().substring(0, tmp1.getF4().length() - 2) + '.'
							+ tmp1.getF4().substring(tmp1.getF4().length() - 2, tmp1.getF4().length()))
							.equals(tmp.getAmount())) {
						check = true;
						break;
					}
				}
				if (check == false) {
					writer.write(this.createSLLine(tmp.getCardNumber(), tmp.getAmount(), tmp.getAmount(), tmp.getTransactionDate(),
							settleDate, configLoader.getSettleEwNapasTctvCode(), configLoader.getSettleEwNapasBBB2(),
							tmp.getAcquirerTransId(), configLoader.getSettleEwNapasGdBH(), tmp.getOrderId(), tmp.getSubMerCode()));
					writer.newLine();
					row++;
				}
			}
			LOGGER.info("{} End check success trans in OF but Napas doesn't have: {}", LOG_NAPAS_PG_PREFIX, row);
			// Case 2: Unsuccess trans in OF but Napas have => add to SL file
			// Just summary in OneFinInternalWriter file, not send to Napas (this case we already refund in e-wallet money for user)
			LOGGER.info("{} Start check unsuccess trans in OF but Napas have => add to SL file, report to OneFin only", LOG_NAPAS_PG_PREFIX);
			for (NapasEwalletEWTransactionView tmp : settleNapasUnSuccess) {
				LOGGER.info("== Proccess settleNapasUnSuccess");
				boolean check = true;
				for (NapasSettlement tmp1 : napasSettleTrans) {
					if (tmp1.getOrderId().equals(tmp.getOrderId())) {
						check = false;
						break;
					}
				}
				if (check == false) {
					OneFinInternalWriter.write(this.createSLLine(tmp.getCardNumber(), tmp.getAmount(), tmp.getAmount(), tmp.getTransactionDate(),
							settleDate, configLoader.getSettleEwNapasTctvCode(), configLoader.getSettleEwNapasBBB2(),
							tmp.getAcquirerTransId(), configLoader.getSettleEwNapasGdHTTP(), tmp.getOrderId(), ""));
					OneFinInternalWriter.newLine();
					rowOneFinInternal++;
				}
			}
			LOGGER.info("{} End check unsuccess trans in OF but Napas have => add to SL file, report to OneFin only {}", LOG_NAPAS_PG_PREFIX, rowOneFinInternal);
			// Prepare terminal line
			LOGGER.info("{} TR Line", LOG_NAPAS_PG_PREFIX);
			String TR = NapasSettleRuleEW.TR.getField();
			String NOTValueTmp = StringUtils.repeat("0", NapasSettleRuleEW.NOT.getLength());
			String NOTValue = NOTValueTmp.substring(0, NOTValueTmp.length() - Integer.toString(row).length());
			String NOT = NapasSettleRuleEW.NOT.getField() + NOTValue + Integer.toString(row);
			String CREValueTmp = StringUtils.repeat(" ", NapasSettleRuleEW.CRE.getLength());
			String CREValue = CREValueTmp.substring(0,
					CREValueTmp.length() - configLoader.getSettleEwNapasDefaultExecuteUser().length());
			String CRE = NapasSettleRuleEW.CRE.getField() + CREValue + configLoader.getSettleEwNapasDefaultExecuteUser();
			String currentDate = dateHelper.currentDateString(SettleConstants.HO_CHI_MINH_TIME_ZONE,
					SettleConstants.DATE_FORMAT_HHmmssddMMyyyy);
			String TIME = NapasSettleRuleEW.TIME.getField() + currentDate.substring(0, 6);
			String DATE = NapasSettleRuleEW.DATE.getField() + currentDate.substring(6, currentDate.length());
			String TRTmp = TR + NOT + CRE + TIME + DATE + NapasSettleRuleEW.CSF.getField();
			TRFinal = TRTmp + sercurityHelper.hashMD5Napas(TRTmp, configLoader.getSettleEwNapasTctvCode());
			writer.write(TRFinal);
		} catch (FileNotFoundException e) {
			transPendTCXL.setStatus(SettleConstants.SETTLEMENT_ERROR);
			LOGGER.error("{} {}", LOG_NAPAS_PG_PREFIX, SettleConstants.ERROR_PREPARE_SETTLE_FILE, e);
		} finally {
			try {
				settleDateList.remove(0);
				updateTransactionList(settleDateList, SettleConstants.SETTLEMENT_SUCCESS);
				writer.close();
				OneFinInternalWriter.close();
				reader.close();
				br.close();
				// Encode SL file to pgp
				LOGGER.info("{} Start Encode Napas {}", LOG_NAPAS_PG_PREFIX, napasEncode);
				PublicKey publicKeyNapas = pKICrypt.getPublickey(configLoader.getSftpNapasDirectoryNapasPublicKey());
				String encodeFile = SLFile + ".pgp";
				try {
					pGPas.PGPencrypt(SLFile.getPath(), encodeFile, publicKeyNapas);
				} catch (Exception e) {
					LOGGER.info("{} Encrypt Error {} {}", LOG_NAPAS_PG_PREFIX, napasEncode, e);
				}
				LOGGER.info("{} End Encrypt Napas {}", LOG_NAPAS_PG_PREFIX, napasEncode);
				if (row > 0) {
					LOGGER.info("{} Upload SL file to SFTP", LOG_NAPAS_PG_PREFIX);
					transPendTCXL.setStatus(SettleConstants.SETTLEMENT_PENDING_T1);
					gateway.upload(new File(encodeFile));
				} else {
					LOGGER.info("{} Not Upload SL file to SFTP", LOG_NAPAS_PG_PREFIX);
					transPendTCXL.setStatus(SettleConstants.SETTLEMENT_SUCCESS);
				}
				if (!transPendTCXL.getFile().contains(TCFile.getName())) {
					transPendTCXL.getFile().add(TCFile.getName());
				}
				if (!transPendTCXL.getFile().contains(SLFile.getName())) {
					transPendTCXL.getFile().add(SLFile.getName());
				}
				if (!transPendTCXL.getFile().contains(OneFinInternal.getName())) {
					transPendTCXL.getFile().add(OneFinInternal.getName());
				}
				commonSettleService.update(transPendTCXL);
				String currentDate = dateHelper.currentDateString(SettleConstants.HO_CHI_MINH_TIME_ZONE, SettleConstants.DATE_FORMAT_ddMMyyyy);
				minioService.uploadFile(configLoader.getBaseBucket(), configLoader.getSettleNapasEWMinioDefaultFolder() + currentDate + "/" + TCFile.getName(), TCFile, "text/plain");
				TCFile.delete();
				minioService.uploadFile(configLoader.getBaseBucket(), configLoader.getSettleNapasEWMinioDefaultFolder() + currentDate + "/" + SLFile.getName(), SLFile, "text/plain");
				SLFile.delete();
				minioService.uploadFile(configLoader.getBaseBucket(), configLoader.getSettleNapasEWMinioDefaultFolder() + currentDate + "/" + OneFinInternal.getName(), OneFinInternal, "text/plain");
				OneFinInternal.delete();
				LOGGER.info("{} MINIO uploaded successfully", LOG_NAPAS_PG_PREFIX);
				new File(decodeFile).delete();
				// Notify for admin
				String subject = null;
				if (row > 0) {
					subject = NAPAS_PG_SUBJECT_SETTLE;
				} else {
					subject = NAPAS_PG_SUBJECT_NO_SETTLE;
				}
				// Notify for admin
				commonSettleService.settleCompleted(currentDate, configLoader.getBaseBucket(), configLoader.getSettleNapasEWMinioDefaultFolder() + currentDate + "/" + SLFile.getName(), subject);
				LOGGER.info("== End Napas Settlement Processing File {}", napasFileName);
			} catch (Exception e) {
				LOGGER.error("{} {}", LOG_NAPAS_PG_PREFIX, SettleConstants.ERROR_UPLOAD_SETTLE_FILE, e);
			}
		}
	}

	private String createSLLine(String cardNumber, BigDecimal amount, BigDecimal rta, String transDate, String settleDate,
	                            String tctvCode, String bbb, String acquirerTransId, String rrcCode, String orderId, String subMerchantCode) {
		String DR = NapasSettleRuleEW.DR.getField();
		String MTIValueTmp = StringUtils.repeat(NapasSettleRuleEW.MTI.getDefaultChar(), NapasSettleRuleEW.MTI.getLength());
		String MTIValue = MTIValueTmp.substring(0,
				MTIValueTmp.length() - NapasSettleRuleEW.MTI.getDefaultValue().length());
		String MTI = NapasSettleRuleEW.MTI.getField() + MTIValue + NapasSettleRuleEW.MTI.getDefaultValue();
		String F2ValueTmp = StringUtils.repeat(NapasSettleRuleEW.F2.getDefaultChar(), NapasSettleRuleEW.F2.getLength());
		String F2Value = F2ValueTmp.substring(0, F2ValueTmp.length() - cardNumber.replace("x", "_").length());
		String F2 = NapasSettleRuleEW.F2.getField() + F2Value + cardNumber.replace("x", "_");
		String F3ValueTmp = StringUtils.repeat(NapasSettleRuleEW.F3.getDefaultChar(), NapasSettleRuleEW.F3.getLength());
		String F3Value = F3ValueTmp.substring(0, F3ValueTmp.length() - NapasSettleRuleEW.F3.getDefaultValue().length());
		String F3 = NapasSettleRuleEW.F3.getField() + F3Value + NapasSettleRuleEW.F3.getDefaultValue();
		String SVCValueTmp = StringUtils.repeat(NapasSettleRuleEW.SVC.getDefaultChar(), NapasSettleRuleEW.SVC.getLength());
		String SVCValue = SVCValueTmp.substring(0,
				SVCValueTmp.length() - NapasSettleRuleEW.SVC.getDefaultValue().length());
		String SVC = NapasSettleRuleEW.SVC.getField() + SVCValue + NapasSettleRuleEW.SVC.getDefaultValue();
		String TCCValueTmp = StringUtils.repeat(NapasSettleRuleEW.TCC.getDefaultChar(), NapasSettleRuleEW.TCC.getLength());
		String TCCValue = TCCValueTmp.substring(0,
				TCCValueTmp.length() - NapasSettleRuleEW.TCC.getDefaultValue().length());
		String TCC = NapasSettleRuleEW.TCC.getField() + TCCValue + NapasSettleRuleEW.TCC.getDefaultValue();
		String F4ValueTmp = StringUtils.repeat(NapasSettleRuleEW.F4.getDefaultChar(), NapasSettleRuleEW.F4.getLength());
		String F4Value = F4ValueTmp.substring(0, F4ValueTmp.length() - amount.toString().replace(".", "").length());
		String F4 = NapasSettleRuleEW.F4.getField() + F4Value + amount.toString().replace(".", "");
		String RTAValueTmp = StringUtils.repeat(NapasSettleRuleEW.RTA.getDefaultChar(), NapasSettleRuleEW.RTA.getLength());
		String RTAValue = RTAValueTmp.substring(0, RTAValueTmp.length() - rta.toString().replace(".", "").length());
		String RTA = NapasSettleRuleEW.RTA.getField() + RTAValue + rta.toString().replace(".", "");
		String F49ValueTmp = StringUtils.repeat(NapasSettleRuleEW.F49.getDefaultChar(), NapasSettleRuleEW.F49.getLength());
		String F49Value = F49ValueTmp.substring(0,
				F49ValueTmp.length() - NapasSettleRuleEW.F49.getDefaultValue().length());
		String F49 = NapasSettleRuleEW.F49.getField() + F49Value + NapasSettleRuleEW.F49.getDefaultValue();
		String F5ValueTmp = StringUtils.repeat(NapasSettleRuleEW.F5.getDefaultChar(), NapasSettleRuleEW.F5.getLength());
		String F5Value = F5ValueTmp.substring(0, F5ValueTmp.length() - amount.toString().replace(".", "").length());
		String F5 = NapasSettleRuleEW.F5.getField() + F5Value + amount.toString().replace(".", "");
		String F50ValueTmp = StringUtils.repeat(NapasSettleRuleEW.F50.getDefaultChar(), NapasSettleRuleEW.F50.getLength());
		String F50Value = F50ValueTmp.substring(0,
				F50ValueTmp.length() - NapasSettleRuleEW.F50.getDefaultValue().length());
		String F50 = NapasSettleRuleEW.F50.getField() + F50Value + NapasSettleRuleEW.F50.getDefaultValue();
		String F9ValueTmp = StringUtils.repeat(NapasSettleRuleEW.F9.getDefaultChar(), NapasSettleRuleEW.F9.getLength());
		String F9Value = F9ValueTmp.substring(0, F9ValueTmp.length() - NapasSettleRuleEW.F9.getDefaultValue().length());
		String F9 = NapasSettleRuleEW.F9.getField() + F9Value + NapasSettleRuleEW.F9.getDefaultValue();
		String F6ValueTmp = StringUtils.repeat(NapasSettleRuleEW.F6.getDefaultChar(), NapasSettleRuleEW.F6.getLength());
		String F6Value = F6ValueTmp.substring(0, F6ValueTmp.length() - NapasSettleRuleEW.F6.getDefaultValue().length());
		String F6 = NapasSettleRuleEW.F6.getField() + F6Value + NapasSettleRuleEW.F6.getDefaultValue();
		String RCAValueTmp = StringUtils.repeat(NapasSettleRuleEW.RCA.getDefaultChar(), NapasSettleRuleEW.RCA.getLength());
		String RCAValue = RCAValueTmp.substring(0,
				RCAValueTmp.length() - NapasSettleRuleEW.RCA.getDefaultValue().length());
		String RCA = NapasSettleRuleEW.RCA.getField() + RCAValue + NapasSettleRuleEW.RCA.getDefaultValue();
		String F51ValueTmp = StringUtils.repeat(NapasSettleRuleEW.F51.getDefaultChar(), NapasSettleRuleEW.F51.getLength());
		String F51Value = F51ValueTmp.substring(0,
				F51ValueTmp.length() - NapasSettleRuleEW.F51.getDefaultValue().length());
		String F51 = NapasSettleRuleEW.F51.getField() + F51Value + NapasSettleRuleEW.F51.getDefaultValue();
		String F10ValueTmp = StringUtils.repeat(NapasSettleRuleEW.F10.getDefaultChar(), NapasSettleRuleEW.F10.getLength());
		String F10Value = F10ValueTmp.substring(0,
				F10ValueTmp.length() - NapasSettleRuleEW.F10.getDefaultValue().length());
		String F10 = NapasSettleRuleEW.F10.getField() + F10Value + NapasSettleRuleEW.F10.getDefaultValue();
		String F11ValueTmp = StringUtils.repeat(NapasSettleRuleEW.F11.getDefaultChar(), NapasSettleRuleEW.F11.getLength());
		String F11Value = F11ValueTmp.substring(0,
				F11ValueTmp.length() - NapasSettleRuleEW.F11.getDefaultValue().length());
		String F11 = NapasSettleRuleEW.F11.getField() + F11Value + NapasSettleRuleEW.F11.getDefaultValue();
		String F12ValueTmp = StringUtils.repeat(NapasSettleRuleEW.F12.getDefaultChar(), NapasSettleRuleEW.F12.getLength());
		String timeArr = transDate.split("T")[1].substring(0, 8).replace(":", "");
		String F12Value = F12ValueTmp.substring(0, F12ValueTmp.length() - timeArr.length());
		String F12 = NapasSettleRuleEW.F12.getField() + F12Value + timeArr;
		String F13ValueTmp = StringUtils.repeat(NapasSettleRuleEW.F13.getDefaultChar(), NapasSettleRuleEW.F13.getLength());
		String dateArr = transDate.split("T")[0].substring(5, 10).replace("-", "");
		String F13Value = F13ValueTmp.substring(0, F13ValueTmp.length() - dateArr.length());
		String F13 = NapasSettleRuleEW.F13.getField() + F13Value + dateArr;
		String F15ValueTmp = StringUtils.repeat(NapasSettleRuleEW.F15.getDefaultChar(), NapasSettleRuleEW.F15.getLength());
		String F15Value = F15ValueTmp.substring(0, F15ValueTmp.length() - settleDate.substring(2, 4).concat(settleDate.substring(0, 2)).length());
		String F15 = NapasSettleRuleEW.F15.getField() + F15Value + settleDate.substring(2, 4).concat(settleDate.substring(0, 2));
		String F18ValueTmp = StringUtils.repeat(NapasSettleRuleEW.F18.getDefaultChar(), NapasSettleRuleEW.F18.getLength());
		String F18Value = F18ValueTmp.substring(0,
				F18ValueTmp.length() - NapasSettleRuleEW.F18.getDefaultValue().length());
		String F18 = NapasSettleRuleEW.F18.getField() + F18Value + NapasSettleRuleEW.F18.getDefaultValue();
		String F22ValueTmp = StringUtils.repeat(NapasSettleRuleEW.F22.getDefaultChar(), NapasSettleRuleEW.F22.getLength());
		String F22Value = F22ValueTmp.substring(0,
				F22ValueTmp.length() - NapasSettleRuleEW.F22.getDefaultValue().length());
		String F22 = NapasSettleRuleEW.F22.getField() + F22Value + NapasSettleRuleEW.F22.getDefaultValue();
		String F25ValueTmp = StringUtils.repeat(NapasSettleRuleEW.F25.getDefaultChar(), NapasSettleRuleEW.F25.getLength());
		String F25Value = F25ValueTmp.substring(0,
				F25ValueTmp.length() - NapasSettleRuleEW.F25.getDefaultValue().length());
		String F25 = NapasSettleRuleEW.F25.getField() + F25Value + NapasSettleRuleEW.F25.getDefaultValue();
		String F41ValueTmp = StringUtils.repeat(NapasSettleRuleEW.F41.getDefaultChar(), NapasSettleRuleEW.F41.getLength());
		String F41Value = F41ValueTmp.substring(0,
				F41ValueTmp.length() - NapasSettleRuleEW.F41.getDefaultValue().length());
		String F41 = NapasSettleRuleEW.F41.getField() + F41Value + NapasSettleRuleEW.F41.getDefaultValue();
		String ACQValueTmp = StringUtils.repeat(NapasSettleRuleEW.ACQ.getDefaultChar(), NapasSettleRuleEW.ACQ.getLength());
		String ACQValue = ACQValueTmp.substring(0, ACQValueTmp.length() - tctvCode.length());
		String ACQ = NapasSettleRuleEW.ACQ.getField() + ACQValue + tctvCode;
		String ISSValueTmp = StringUtils.repeat(NapasSettleRuleEW.ISS.getDefaultChar(), NapasSettleRuleEW.ISS.getLength());
		String ISSValue = ISSValueTmp.substring(0, ISSValueTmp.length() - cardNumber.substring(0, 6).length());
		String ISS = NapasSettleRuleEW.ISS.getField() + ISSValue + cardNumber.substring(0, 6);
		String MIDValueTmp = StringUtils.repeat(NapasSettleRuleEW.MID.getDefaultChar(), NapasSettleRuleEW.MID.getLength());
		String MIDValue = MIDValueTmp.substring(0, MIDValueTmp.length() - bbb.length());
		String MID = NapasSettleRuleEW.MID.getField() + MIDValue + bbb;
		String BNBValueTmp = StringUtils.repeat(NapasSettleRuleEW.BNB.getDefaultChar(), NapasSettleRuleEW.BNB.getLength());
		String BNBValue = BNBValueTmp.substring(0,
				BNBValueTmp.length() - NapasSettleRuleEW.BNB.getDefaultValue().length());
		String BNB = NapasSettleRuleEW.BNB.getField() + BNBValue + NapasSettleRuleEW.BNB.getDefaultValue();
		String F102ValueTmp = StringUtils.repeat(NapasSettleRuleEW.F102.getDefaultChar(),
				NapasSettleRuleEW.F102.getLength());
		String F102Value = F102ValueTmp.substring(0,
				F102ValueTmp.length() - NapasSettleRuleEW.F102.getDefaultValue().length());
		String F102 = NapasSettleRuleEW.F102.getField() + F102Value + NapasSettleRuleEW.F102.getDefaultValue();
		String F103ValueTmp = StringUtils.repeat(NapasSettleRuleEW.F103.getDefaultChar(),
				NapasSettleRuleEW.F103.getLength());
		String F103Value = F103ValueTmp.substring(0,
				F103ValueTmp.length() - NapasSettleRuleEW.F103.getDefaultValue().length());
		String F103 = NapasSettleRuleEW.F103.getField() + F103Value + NapasSettleRuleEW.F103.getDefaultValue();
		String F37ValueTmp = StringUtils.repeat(NapasSettleRuleEW.F37.getDefaultChar(), NapasSettleRuleEW.F37.getLength());
		String F37Value = F37ValueTmp.substring(0, F37ValueTmp.length() - acquirerTransId.length());
		String F37 = NapasSettleRuleEW.F37.getField() + F37Value + acquirerTransId;
		String F38ValueTmp = StringUtils.repeat(NapasSettleRuleEW.F38.getDefaultChar(), NapasSettleRuleEW.F38.getLength());
		String F38Value = F38ValueTmp.substring(0,
				F38ValueTmp.length() - NapasSettleRuleEW.F38.getDefaultValue().length());
		String F38 = NapasSettleRuleEW.F38.getField() + F38Value + NapasSettleRuleEW.F38.getDefaultValue();
		String TRNValueTmp = StringUtils.repeat(NapasSettleRuleEW.TRN.getDefaultChar(), NapasSettleRuleEW.TRN.getLength());
		String TRNValue = TRNValueTmp.substring(0,
				TRNValueTmp.length() - NapasSettleRuleEW.TRN.getDefaultValue().length());
		String TRN = NapasSettleRuleEW.TRN.getField() + TRNValue + NapasSettleRuleEW.TRN.getDefaultValue();
		String RRCValueTmp = StringUtils.repeat(NapasSettleRuleEW.RRC.getDefaultChar(), NapasSettleRuleEW.RRC.getLength());
		String RRCValue = RRCValueTmp.substring(0, RRCValueTmp.length() - rrcCode.length());
		String RRC = NapasSettleRuleEW.RRC.getField() + RRCValue + rrcCode;
		String RSV1ValueTmp = StringUtils.repeat(NapasSettleRuleEW.RSV1.getDefaultChar(),
				NapasSettleRuleEW.RSV1.getLength());
		dateArr = transDate.split("T")[0].substring(0, 10).replace("-", "");
		String RSV1Value1 = RSV1ValueTmp.substring(0, 20 - subMerchantCode.length()) + subMerchantCode
				+ dateArr.substring(6, 8).concat(dateArr.substring(4, 6)).concat(dateArr.substring(0, 4));
		String RSV1Value2Tmp = RSV1ValueTmp.substring(28, 68 - orderId.length());
		String RSV1Value2 = RSV1Value2Tmp + orderId;
		String RSV1Value3 = StringUtils.repeat(NapasSettleRuleEW.RSV1.getDefaultChar(), 32);
		String RSV1 = NapasSettleRuleEW.RSV1.getField() + RSV1Value1 + RSV1Value2 + RSV1Value3;
		String RSV2ValueTmp = StringUtils.repeat(NapasSettleRuleEW.RSV2.getDefaultChar(),
				NapasSettleRuleEW.RSV2.getLength());
		String RSV2Value = RSV2ValueTmp.substring(0,
				RSV2ValueTmp.length() - NapasSettleRuleEW.RSV2.getDefaultValue().length());
		String RSV2 = NapasSettleRuleEW.RSV2.getField() + RSV2Value + NapasSettleRuleEW.RSV2.getDefaultValue();
		String RSV3ValueTmp = StringUtils.repeat(NapasSettleRuleEW.RSV3.getDefaultChar(),
				NapasSettleRuleEW.RSV3.getLength());
		String RSV3Value = RSV3ValueTmp.substring(0,
				RSV3ValueTmp.length() - NapasSettleRuleEW.RSV3.getDefaultValue().length());
		String RSV3 = NapasSettleRuleEW.RSV3.getField() + RSV3Value + NapasSettleRuleEW.RSV3.getDefaultValue();
		String CSR = NapasSettleRuleEW.CSR.getField();
		String lineTmp = DR + MTI + F2 + F3 + SVC + TCC + F4 + RTA + F49 + F5 + F50 + F9 + F6 + RCA + F51 + F10 + F11
				+ F12 + F13 + F15 + F18 + F22 + F25 + F41 + ACQ + ISS + MID + BNB + F102 + F103 + F37 + F38 + TRN + RRC
				+ RSV1 + RSV2 + RSV3 + CSR;
		String line = lineTmp + sercurityHelper.hashMD5Napas(lineTmp, tctvCode);
		return line;
	}

	private String findRealStringIgnoreSpace(String input) {
		int index = 0;
		for (char tmp : input.toCharArray()) {
			if (tmp != ' ') {
				break;
			}
			index++;
		}
		return input.substring(index, input.length());
	}

	private void updateTransactionList(List<String> settleDates, String status) throws Exception {
		for (String settleDate : settleDates) {
			SettlementTransaction trans = settleRepo.findByPartnerAndDomainAndSettleDate(SettleConstants.PARTNER_NAPAS,
					SettleConstants.PAYMENT_GATEWAYWL2, settleDate);
			trans.setStatus(status);
			commonSettleService.update(trans);
		}
	}

}
