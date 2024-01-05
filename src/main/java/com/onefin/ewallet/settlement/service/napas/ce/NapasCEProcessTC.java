package com.onefin.ewallet.settlement.service.napas.ce;

import com.onefin.ewallet.common.domain.napas.NapasEwalletCETransactionView;
import com.onefin.ewallet.common.domain.settlement.SettlementTransaction;
import com.onefin.ewallet.common.quartz.entity.SchedulerJobInfo;
import com.onefin.ewallet.common.utility.date.DateTimeHelper;
import com.onefin.ewallet.common.utility.sercurity.SercurityHelper;
import com.onefin.ewallet.settlement.common.SettleConstants;
import com.onefin.ewallet.settlement.common.SettleConstants.NapasSettleRule;
import com.onefin.ewallet.settlement.config.SFTPNapasEwalletIntegrationCE.UploadNapasGateway;
import com.onefin.ewallet.settlement.controller.SettleJobController;
import com.onefin.ewallet.settlement.dto.NapasSettlement;
import com.onefin.ewallet.settlement.pgpas.PGPas;
import com.onefin.ewallet.settlement.pgpas.PKICrypt;
import com.onefin.ewallet.settlement.repository.NapasETransRepoCE;
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
public class NapasCEProcessTC extends QuartzJobBean {

	private static final Logger LOGGER = LoggerFactory.getLogger(NapasCEProcessTC.class);

	private static final String LOG_NAPAS_CE_PREFIX = SettleConstants.PARTNER_NAPAS + " " + SettleConstants.LINK_BANK;

	private static final String NAPAS_LINKBANK_SUBJECT_NO_SETTLE = "[Napas - Cashin Ecom] - Settle success";

	private static final String NAPAS_LINKBANK_SUBJECT_SETTLE = "[Napas - Cashin Ecom] - Settle TC dispute";

	private static final String NAPAS_LINKBANK_ISSUE_SUBJECT = "[Napas - Cashin Ecom] - Settle TC error";

	@Autowired
	private NapasETransRepoCE<?> transRepository;

	@Autowired
	private ConfigLoader configLoader;

	@Autowired
	private UploadNapasGateway gateway;

	@Autowired
	private DateTimeHelper dateHelper;

	@Autowired
	private PGPas pGPas;

	@Autowired
	private PKICrypt pKICrypt;

	@Autowired
	private SettleTransRepository settleRepo;

	@Autowired
	@Qualifier("SftpNapasRemoteFileTemplate")
	private SftpRemoteFileTemplate sftpNapasGateway;

	@Autowired
	private SettleService commonSettleService;

	@Autowired
	private SercurityHelper sercurityHelper;

	@Autowired
	private SettleJobController settleJobController;

	@Autowired
	private MinioService minioService;

	/* *************************WAITING TC FILE FROM NAPAS***************************** */

	/**
	 * @throws ParseException
	 * @throws IOException    Find in Napas sftp for TC file and process all pending transaction
	 */
	@Override
	protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
		LOGGER.info("{} Start Napas Waiting TC File", LOG_NAPAS_CE_PREFIX);
		// Step 1: Summary day need to be settled
		List<SettlementTransaction> transPendTCs = settleRepo.findAllByPartnerAndDomainAndStatusTransaction(
				SettleConstants.PARTNER_NAPAS, SettleConstants.LINK_BANK,
				SettleConstants.SETTLEMENT_PENDING_T0);
		LOGGER.info("{} Number Napas Settlement Days TC {}", LOG_NAPAS_CE_PREFIX, transPendTCs.size());
		List<String> settleDate = new ArrayList<String>();

		for (SettlementTransaction transPendTC : transPendTCs) {
			settleDate.add(transPendTC.getSettleKey().getSettleDate());
		}
		LOGGER.info("{} Day need to settled {}", LOG_NAPAS_CE_PREFIX, settleDate);
		// Step 2: Find Napas settle file base on settle date
		LOGGER.info("{} Start find TC settle file in SFTP", LOG_NAPAS_CE_PREFIX);
		for (SettlementTransaction transPendTC : transPendTCs) {
			// Download remote to local
			String settleFileName = String.format("%s_%s_%s_%s_%s_%s_%s.dat.pgp", transPendTC.getSettleKey().getSettleDate(),
					configLoader.getSettleNapasZZZ(), configLoader.getSettleNapasBBB(),
					configLoader.getSettleNapasTctvCode(), configLoader.getSettleNapasSettleOrder(),
					configLoader.getSettleNapasSettleFileTypeTC(), configLoader.getSettleNapasServiceCodeEcom());
			LOGGER.info("{} File name: {}", LOG_NAPAS_CE_PREFIX, settleFileName);
			try {
				// Try to retrieve Napas File
				sftpNapasGateway.get(configLoader.getSftpNapasDirectoryRemoteIn() + settleFileName, stream -> {
					FileCopyUtils.copy(stream, new FileOutputStream(
							new File(configLoader.getCeNapasDirectoryLocalFile() + settleFileName)));
				});
				// Settle completed => paused job
				SchedulerJobInfo jobTmp = new SchedulerJobInfo();
				jobTmp.setJobClass(NapasCEProcessTC.class.getName());
				settleJobController.pauseJob(jobTmp);
				// Update to Pending XL File
				transPendTC.setStatus(SettleConstants.SETTLEMENT_PROCESSING);
				commonSettleService.update(transPendTC);
				// Process SL file and send to Napas
				napasProcessTC(configLoader.getCeNapasDirectoryLocalFile() + settleFileName,
						settleFileName, transPendTC, settleDate);
			} catch (Exception e) {
				LOGGER.info("{} Napas TC Error {}", LOG_NAPAS_CE_PREFIX, e);
				LOGGER.info("{} Not Found Napas Settlement TC {}", LOG_NAPAS_CE_PREFIX, transPendTC.getSettleKey().getSettleDate());
			}
		}
		LOGGER.info("{} End find TC settle file in SFTP", LOG_NAPAS_CE_PREFIX);
	}

	/**
	 * @throws ParseException
	 * @throws IOException    Look up in Napas SFTP IN Folder
	 */
	public ResponseEntity<?> ewalletNapasTCSettlementManually(String date, String order,
	                                                          SettlementTransaction transPendTC, List<String> settleDateList) throws ParseException, IOException {
		LOGGER.info("== Start Napas Settlement TC");
		// Download remote to local
		String settleFileName = String.format("%s_%s_%s_%s_%s_%s_%s.dat.pgp", date, configLoader.getSettleNapasZZZ(),
				configLoader.getSettleNapasBBB(), configLoader.getSettleNapasTctvCode(), order,
				configLoader.getSettleNapasSettleFileTypeTC(), configLoader.getSettleNapasServiceCodeEcom());
		try {
			sftpNapasGateway.get(configLoader.getSftpNapasDirectoryRemoteIn() + settleFileName, stream -> {
				FileCopyUtils.copy(stream,
						new FileOutputStream(new File(configLoader.getCeNapasDirectoryLocalFile() + settleFileName)));
			});
			// Update to Pending XL File
			transPendTC.setStatus(SettleConstants.SETTLEMENT_PROCESSING);
			commonSettleService.update(transPendTC);
			napasProcessTC(configLoader.getCeNapasDirectoryLocalFile() + settleFileName,
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
	 * @param transPendTCXL
	 * @throws Exception
	 */
	public void napasProcessTC(String napasEncode, String napasFileName,
	                           SettlementTransaction transPendTCXL, List<String> settleDateList) throws Exception {
		LOGGER.info("{} Start Napas Settlement Processing SL File {}", LOG_NAPAS_CE_PREFIX, napasFileName);
		// Step 1 Decrypt Napas TC File
		LOGGER.info("== Start Decrypt Napas {}", napasEncode);
		PrivateKey privateKey = pKICrypt.getPrivateKey(configLoader.getSftpNapasDirectoryOfPrivateKey(), "");
		String decodeFile = configLoader.getCeNapasDirectoryLocalFile() + napasFileName.replace(".pgp", "");
		try {
			pGPas.PGPdecrypt(napasEncode, decodeFile, privateKey);
		} catch (Exception e) {
			LOGGER.info("{} Decrypt Error {} {}", LOG_NAPAS_CE_PREFIX, napasEncode, e);
		}
		LOGGER.info("{} Success Decrypt Napas {}", LOG_NAPAS_CE_PREFIX, napasEncode);
		File TCFile = new File(decodeFile);
		File SLFile = null;
		File OneFinInternal = null;
		String SLFileName = napasFileName
				.replace(configLoader.getSettleNapasSettleFileTypeTC(), configLoader.getSettleNapasSettleFileTypeSL())
				.replace(".pgp", "");
		BufferedWriter writer = null;
		BufferedWriter OneFinInternalWriter = null;
		FileReader reader = null;
		BufferedReader br = null;

		// Step 2 Query All Transaction to Settle
		LOGGER.info("{} Query All Transaction to Settle {}", LOG_NAPAS_CE_PREFIX, napasEncode);
		List<NapasEwalletCETransactionView> settleNapasSuccess = new ArrayList<NapasEwalletCETransactionView>();
		List<NapasEwalletCETransactionView> settleNapasUnSuccess = new ArrayList<NapasEwalletCETransactionView>();
		LOGGER.info("{} Settle Date List {}", LOG_NAPAS_CE_PREFIX, settleDateList);
		for (String tmp : settleDateList) {
			String previousDate = dateHelper.previousDateString(tmp, SettleConstants.HO_CHI_MINH_TIME_ZONE,
					SettleConstants.DATE_FORMAT_MMddyy);
			LOGGER.info("{} Settle Date {}", LOG_NAPAS_CE_PREFIX, previousDate);
			// Query Success trans
			List<NapasEwalletCETransactionView> transTmp1 = transRepository.findByEqualStatusAndNapasTransDate(
					configLoader.getNapasSettleAction(), SettleConstants.TRANS_SUCCESS, previousDate);
			if (transTmp1.size() != 0) {
				settleNapasSuccess.addAll(transTmp1);
			}
			// Query UnSuccess trans
			List<NapasEwalletCETransactionView> transTmp2 = transRepository.findByNotEqualStatusAndNapasTransDate(
					configLoader.getNapasSettleAction(), SettleConstants.TRANS_SUCCESS, previousDate);
			if (transTmp2.size() != 0) {
				settleNapasUnSuccess.addAll(transTmp2);
			}
		}
		LOGGER.info("{} Settle Success trans {}", LOG_NAPAS_CE_PREFIX, settleNapasSuccess.size());
		LOGGER.info("{} Settle Fail trans {}", LOG_NAPAS_CE_PREFIX, settleNapasUnSuccess.size());
		int row = 0;
		int rowOneFinInternal = 0;
		// Step 3 Create SL File And Check line by line in TC file with OneFin transaction
		LOGGER.info("{} Create SL File And Check line by line in TC file with OneFin transaction", LOG_NAPAS_CE_PREFIX);
		try {
			SLFile = new File(configLoader.getCeNapasDirectoryLocalFile() + SLFileName);
			OneFinInternal = new File(configLoader.getCeNapasDirectoryLocalFile() + "OFInternal" + SLFileName);
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
			LOGGER.info("{} Read TC file successfully", LOG_NAPAS_CE_PREFIX);
			while ((line = br.readLine()) != null) {
				int startIndex = -1;
				// Detail record
				if (line.subSequence(0, 2).equals(NapasSettleRule.DR.getField())) {
					startIndex = line.indexOf(NapasSettleRule.MTI.getField()) + NapasSettleRule.MTI.getField().length();
					String MTI = line.substring(startIndex, startIndex + NapasSettleRule.MTI.getLength());
					startIndex = line.indexOf(NapasSettleRule.F2.getField()) + NapasSettleRule.F2.getField().length();
					String F2 = line.substring(startIndex, startIndex + NapasSettleRule.F2.getLength());
					startIndex = line.indexOf(NapasSettleRule.F3.getField()) + NapasSettleRule.F3.getField().length();
					String F3 = line.substring(startIndex, startIndex + NapasSettleRule.F3.getLength());
					startIndex = line.indexOf(NapasSettleRule.SVC.getField()) + NapasSettleRule.SVC.getField().length();
					String SVC = line.substring(startIndex, startIndex + NapasSettleRule.SVC.getLength());
					startIndex = line.indexOf(NapasSettleRule.TCC.getField()) + NapasSettleRule.TCC.getField().length();
					String TCC = line.substring(startIndex, startIndex + NapasSettleRule.TCC.getLength());
					startIndex = line.indexOf(NapasSettleRule.F4.getField()) + NapasSettleRule.F4.getField().length();
					String F4 = line.substring(startIndex, startIndex + NapasSettleRule.F4.getLength());
					startIndex = line.indexOf(NapasSettleRule.RTA.getField()) + NapasSettleRule.RTA.getField().length();
					String RTA = line.substring(startIndex, startIndex + NapasSettleRule.RTA.getLength());
					startIndex = line.indexOf(NapasSettleRule.F49.getField()) + NapasSettleRule.F49.getField().length();
					String F49 = line.substring(startIndex, startIndex + NapasSettleRule.F49.getLength());
					startIndex = line.indexOf(NapasSettleRule.F5.getField()) + NapasSettleRule.F5.getField().length();
					String F5 = line.substring(startIndex, startIndex + NapasSettleRule.F5.getLength());
					startIndex = line.indexOf(NapasSettleRule.F50.getField()) + NapasSettleRule.F50.getField().length();
					String F50 = line.substring(startIndex, startIndex + NapasSettleRule.F50.getLength());
					startIndex = line.indexOf(NapasSettleRule.F9.getField()) + NapasSettleRule.F9.getField().length();
					String F9 = line.substring(startIndex, startIndex + NapasSettleRule.F9.getLength());
					startIndex = line.indexOf(NapasSettleRule.F6.getField()) + NapasSettleRule.F6.getField().length();
					String F6 = line.substring(startIndex, startIndex + NapasSettleRule.F6.getLength());
					startIndex = line.indexOf(NapasSettleRule.RCA.getField()) + NapasSettleRule.RCA.getField().length();
					String RCA = line.substring(startIndex, startIndex + NapasSettleRule.RCA.getLength());
					startIndex = line.indexOf(NapasSettleRule.F51.getField()) + NapasSettleRule.F51.getField().length();
					String F51 = line.substring(startIndex, startIndex + NapasSettleRule.F51.getLength());
					startIndex = line.indexOf(NapasSettleRule.F10.getField()) + NapasSettleRule.F10.getField().length();
					String F10 = line.substring(startIndex, startIndex + NapasSettleRule.F10.getLength());
					startIndex = line.indexOf(NapasSettleRule.F11.getField()) + NapasSettleRule.F11.getField().length();
					String F11 = line.substring(startIndex, startIndex + NapasSettleRule.F11.getLength());
					startIndex = line.indexOf(NapasSettleRule.F12.getField()) + NapasSettleRule.F12.getField().length();
					String F12 = line.substring(startIndex, startIndex + NapasSettleRule.F12.getLength());
					startIndex = line.indexOf(NapasSettleRule.F13.getField()) + NapasSettleRule.F13.getField().length();
					String F13 = line.substring(startIndex, startIndex + NapasSettleRule.F13.getLength());
					startIndex = line.indexOf(NapasSettleRule.F15.getField()) + NapasSettleRule.F15.getField().length();
					String F15 = line.substring(startIndex, startIndex + NapasSettleRule.F15.getLength());
					startIndex = line.indexOf(NapasSettleRule.F18.getField()) + NapasSettleRule.F18.getField().length();
					String F18 = line.substring(startIndex, startIndex + NapasSettleRule.F18.getLength());
					startIndex = line.indexOf(NapasSettleRule.F22.getField()) + NapasSettleRule.F22.getField().length();
					String F22 = line.substring(startIndex, startIndex + NapasSettleRule.F22.getLength());
					startIndex = line.indexOf(NapasSettleRule.F25.getField()) + NapasSettleRule.F25.getField().length();
					String F25 = line.substring(startIndex, startIndex + NapasSettleRule.F25.getLength());
					startIndex = line.indexOf(NapasSettleRule.F41.getField()) + NapasSettleRule.F41.getField().length();
					String F41 = line.substring(startIndex, startIndex + NapasSettleRule.F41.getLength());
					startIndex = line.indexOf(NapasSettleRule.ACQ.getField()) + NapasSettleRule.ACQ.getField().length();
					String ACQ = line.substring(startIndex, startIndex + NapasSettleRule.ACQ.getLength());
					startIndex = line.indexOf(NapasSettleRule.ISS.getField()) + NapasSettleRule.ISS.getField().length();
					String ISS = line.substring(startIndex, startIndex + NapasSettleRule.ISS.getLength());
					startIndex = line.indexOf(NapasSettleRule.MID.getField()) + NapasSettleRule.MID.getField().length();
					String MID = line.substring(startIndex, startIndex + NapasSettleRule.MID.getLength());
					startIndex = line.indexOf(NapasSettleRule.BNB.getField()) + NapasSettleRule.BNB.getField().length();
					String BNB = line.substring(startIndex, startIndex + NapasSettleRule.BNB.getLength());
					startIndex = line.indexOf(NapasSettleRule.F102.getField())
							+ NapasSettleRule.F102.getField().length();
					String F102 = line.substring(startIndex, startIndex + NapasSettleRule.F102.getLength());
					startIndex = line.indexOf(NapasSettleRule.F103.getField())
							+ NapasSettleRule.F103.getField().length();
					String F103 = line.substring(startIndex, startIndex + NapasSettleRule.F103.getLength());
					startIndex = line.indexOf(NapasSettleRule.SVFISSNP.getField())
							+ NapasSettleRule.SVFISSNP.getField().length();
					String SVFISSNP = line.substring(startIndex, startIndex + NapasSettleRule.SVFISSNP.getLength());
					startIndex = line.indexOf(NapasSettleRule.IRFISSACQ.getField())
							+ NapasSettleRule.IRFISSACQ.getField().length();
					String IRFISSACQ = line.substring(startIndex, startIndex + NapasSettleRule.IRFISSACQ.getLength());
					startIndex = line.indexOf(NapasSettleRule.IRFISSBNB.getField())
							+ NapasSettleRule.IRFISSBNB.getField().length();
					String IRFISSBNB = line.substring(startIndex, startIndex + NapasSettleRule.IRFISSBNB.getLength());
					startIndex = line.indexOf(NapasSettleRule.SVFACQNP.getField())
							+ NapasSettleRule.SVFACQNP.getField().length();
					String SVFACQNP = line.substring(startIndex, startIndex + NapasSettleRule.SVFACQNP.getLength());
					startIndex = line.indexOf(NapasSettleRule.IRFACQISS.getField())
							+ NapasSettleRule.IRFACQISS.getField().length();
					String IRFACQISS = line.substring(startIndex, startIndex + NapasSettleRule.IRFACQISS.getLength());
					startIndex = line.indexOf(NapasSettleRule.IRFACQBNB.getField())
							+ NapasSettleRule.IRFACQBNB.getField().length();
					String IRFACQBNB = line.substring(startIndex, startIndex + NapasSettleRule.IRFACQBNB.getLength());
					startIndex = line.indexOf(NapasSettleRule.SVFBNBNP.getField())
							+ NapasSettleRule.SVFBNBNP.getField().length();
					String SVFBNBNP = line.substring(startIndex, startIndex + NapasSettleRule.SVFBNBNP.getLength());
					startIndex = line.indexOf(NapasSettleRule.IRFBNBISS.getField())
							+ NapasSettleRule.IRFBNBISS.getField().length();
					String IRFBNBISS = line.substring(startIndex, startIndex + NapasSettleRule.IRFBNBISS.getLength());
					startIndex = line.indexOf(NapasSettleRule.IRFBNBACQ.getField())
							+ NapasSettleRule.IRFBNBACQ.getField().length();
					String IRFBNBACQ = line.substring(startIndex, startIndex + NapasSettleRule.IRFBNBACQ.getLength());
					startIndex = line.indexOf(NapasSettleRule.F37.getField()) + NapasSettleRule.F37.getField().length();
					String F37 = line.substring(startIndex, startIndex + NapasSettleRule.F37.getLength());
					startIndex = line.indexOf(NapasSettleRule.F38.getField()) + NapasSettleRule.F38.getField().length();
					String F38 = line.substring(startIndex, startIndex + NapasSettleRule.F38.getLength());
					startIndex = line.indexOf(NapasSettleRule.TRN.getField()) + NapasSettleRule.TRN.getField().length();
					String TRN = line.substring(startIndex, startIndex + NapasSettleRule.TRN.getLength());
					startIndex = line.indexOf(NapasSettleRule.RRC.getField()) + NapasSettleRule.RRC.getField().length();
					String RRC = line.substring(startIndex, startIndex + NapasSettleRule.RRC.getLength());
					startIndex = line.indexOf(NapasSettleRule.RSV1.getField())
							+ NapasSettleRule.RSV1.getField().length();
					String RSV1 = line.substring(startIndex, startIndex + NapasSettleRule.RSV1.getLength());
					String transDate = findRealStringIgnoreSpace(RSV1.substring(20, 28));
					String orderId = findRealStringIgnoreSpace(RSV1.substring(28, 68));
					startIndex = line.indexOf(NapasSettleRule.RSV2.getField())
							+ NapasSettleRule.RSV2.getField().length();
					String RSV2 = line.substring(startIndex, startIndex + NapasSettleRule.RSV2.getLength());
					startIndex = line.indexOf(NapasSettleRule.RSV3.getField())
							+ NapasSettleRule.RSV3.getField().length();
					String RSV3 = line.substring(startIndex, startIndex + NapasSettleRule.RSV3.getLength());
					startIndex = line.indexOf(NapasSettleRule.CSR.getField()) + NapasSettleRule.CSR.getField().length();
					String CSR = line.substring(startIndex, startIndex + NapasSettleRule.CSR.getLength());
					String inputCheckSum = line.substring(0, startIndex);
					NapasSettlement napasSettleTran = new NapasSettlement(MTI, F2, F3, SVC, TCC, F4, RTA, F49, F5, F50,
							F9, F6, RCA, F51, F10, F11, F12, F13, F15, F18, F22, F25, F41, ACQ, ISS, MID, BNB, F102,
							F103, SVFISSNP, IRFISSACQ, IRFISSBNB, SVFACQNP, IRFACQISS, IRFACQBNB, SVFBNBNP, IRFBNBISS,
							IRFBNBACQ, F37, F38, TRN, RRC, RSV1, transDate, orderId, RSV2, RSV3, CSR, inputCheckSum);
					napasSettleTrans.add(napasSettleTran);
				}
			}
			LOGGER.info("{} End read TC file", LOG_NAPAS_CE_PREFIX);
			// Compare and check OF-NAPAS
			// Case 1: Success trans in OF but Napas doesn't have => add to SL file (note
			// check them date)
			LOGGER.info("{} Start check success trans in OF but Napas doesn't have", LOG_NAPAS_CE_PREFIX);
			for (NapasEwalletCETransactionView tmp : settleNapasSuccess) {
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
					writer.write(this.createSLLine(tmp.getCardNumber(), tmp.getAmount(), tmp.getAmount(),
							tmp.getTransactionDate(), settleDate, configLoader.getSettleNapasTctvCode(),
							configLoader.getSettleNapasBBB(), tmp.getAcquirerTransId(),
							configLoader.getSettleNapasGdBH(), tmp.getOrderId()));
					writer.newLine();
					row++;
				}
			}
			LOGGER.info("{} End check success trans in OF but Napas doesn't have: {}", LOG_NAPAS_CE_PREFIX, row);
			// Case 2: Unsuccess trans in OF but Napas have => add to SL file, report to OneFin only
			LOGGER.info("{} Start check unsuccess trans in OF but Napas have => add to SL file, report to OneFin only", LOG_NAPAS_CE_PREFIX);
			for (NapasEwalletCETransactionView tmp : settleNapasUnSuccess) {
				LOGGER.info("== Proccess settleNapasUnSuccess");
				boolean check = true;
				for (NapasSettlement tmp1 : napasSettleTrans) {
					if (tmp1.getOrderId().equals(tmp.getOrderId())) {
						check = false;
						break;
					}
				}
				if (check == false) {
					OneFinInternalWriter.write(this.createSLLine(tmp.getCardNumber(), tmp.getAmount(), tmp.getAmount(),
							tmp.getTransactionDate(), settleDate, configLoader.getSettleNapasTctvCode(),
							configLoader.getSettleNapasBBB(), tmp.getAcquirerTransId(),
							configLoader.getSettleNapasGdHTTP(), tmp.getOrderId()));
					OneFinInternalWriter.newLine();
					rowOneFinInternal++;
				}
			}
			LOGGER.info("{} End check unsuccess trans in OF but Napas have => add to SL file, report to OneFin only {}", LOG_NAPAS_CE_PREFIX, rowOneFinInternal);
			// Prepare terminal line
			LOGGER.info("{} TR Line", LOG_NAPAS_CE_PREFIX);
			String TR = NapasSettleRule.TR.getField();
			String NOTValueTmp = StringUtils.repeat("0", NapasSettleRule.NOT.getLength());
			String NOTValue = NOTValueTmp.substring(0, NOTValueTmp.length() - Integer.toString(row).length());
			String NOT = NapasSettleRule.NOT.getField() + NOTValue + Integer.toString(row);
			String CREValueTmp = StringUtils.repeat(" ", NapasSettleRule.CRE.getLength());
			String CREValue = CREValueTmp.substring(0,
					CREValueTmp.length() - configLoader.getSettleNapasDefaultExecuteUser().length());
			String CRE = NapasSettleRule.CRE.getField() + CREValue + configLoader.getSettleNapasDefaultExecuteUser();
			String currentDate = dateHelper.currentDateString(SettleConstants.HO_CHI_MINH_TIME_ZONE,
					SettleConstants.DATE_FORMAT_HHmmssddMMyyyy);
			String TIME = NapasSettleRule.TIME.getField() + currentDate.substring(0, 6);
			String DATE = NapasSettleRule.DATE.getField() + currentDate.substring(6, currentDate.length());
			String TRTmp = TR + NOT + CRE + TIME + DATE + NapasSettleRule.CSF.getField();
			TRFinal = TRTmp + sercurityHelper.hashMD5Napas(TRTmp, configLoader.getSettleNapasTctvCode());
			writer.write(TRFinal);
			LOGGER.info("{} End TR Line", LOG_NAPAS_CE_PREFIX);
		} catch (FileNotFoundException e) {
			transPendTCXL.setStatus(SettleConstants.SETTLEMENT_ERROR);
			LOGGER.error("{} {}", LOG_NAPAS_CE_PREFIX, SettleConstants.ERROR_PREPARE_SETTLE_FILE, e);
		} finally {
			try {
				settleDateList.remove(0);
				updateTransactionList(settleDateList, SettleConstants.SETTLEMENT_SUCCESS);
				writer.close();
				OneFinInternalWriter.close();
				reader.close();
				br.close();
				// Encode SL file to pgp
				LOGGER.info("{} Start Encode Napas {}", LOG_NAPAS_CE_PREFIX, napasEncode);
				PublicKey publicKeyNapas = pKICrypt.getPublickey(configLoader.getSftpNapasDirectoryNapasPublicKey());
				String encodeFile = SLFile + ".pgp";
				try {
					pGPas.PGPencrypt(SLFile.getPath(), encodeFile, publicKeyNapas);
				} catch (Exception e) {
					LOGGER.info("{} Encrypt Error {} {}", LOG_NAPAS_CE_PREFIX, napasEncode, e);
				}
				LOGGER.info("{} End Encrypt Napas {}", LOG_NAPAS_CE_PREFIX, napasEncode);
				if (row > 0) {
					LOGGER.info("{} Upload SL file to SFTP", LOG_NAPAS_CE_PREFIX);
					transPendTCXL.setStatus(SettleConstants.SETTLEMENT_PENDING_T1);
					gateway.upload(new File(encodeFile));
				} else {
					LOGGER.info("{} Not Upload SL file to SFTP", LOG_NAPAS_CE_PREFIX);
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
				minioService.uploadFile(configLoader.getBaseBucket(), configLoader.getSettleNapasCEMinioDefaultFolder() + currentDate + "/" + TCFile.getName(), TCFile, "text/plain");
				TCFile.delete();
				minioService.uploadFile(configLoader.getBaseBucket(), configLoader.getSettleNapasCEMinioDefaultFolder() + currentDate + "/" + SLFile.getName(), SLFile, "text/plain");
				SLFile.delete();
				minioService.uploadFile(configLoader.getBaseBucket(), configLoader.getSettleNapasCEMinioDefaultFolder() + currentDate + "/" + OneFinInternal.getName(), OneFinInternal, "text/plain");
				OneFinInternal.delete();
				LOGGER.info("{} MINIO uploaded successfully", LOG_NAPAS_CE_PREFIX);
				// Notify for admin
				String subject = null;
				if (row > 0) {
					subject = NAPAS_LINKBANK_SUBJECT_SETTLE;
				} else {
					subject = NAPAS_LINKBANK_SUBJECT_NO_SETTLE;
				}
				commonSettleService.settleCompleted(currentDate, configLoader.getBaseBucket(), configLoader.getSettleNapasCEMinioDefaultFolder() + currentDate + "/" + SLFile.getName(), subject);
				LOGGER.info("{} End Napas Settlement Processing TC File {}", LOG_NAPAS_CE_PREFIX, napasFileName);
			} catch (Exception e) {
				LOGGER.error("{} {}", LOG_NAPAS_CE_PREFIX, SettleConstants.ERROR_UPLOAD_SETTLE_FILE, e);
			}
		}
	}

	private String createSLLine(String cardNumber, BigDecimal amount, BigDecimal rta, String transDate,
	                            String settleDate, String tctvCode, String bbb, String acquirerTransId, String rrcCode, String orderId) {
		String DR = NapasSettleRule.DR.getField();
		String MTIValueTmp = StringUtils.repeat(NapasSettleRule.MTI.getDefaultChar(), NapasSettleRule.MTI.getLength());
		String MTIValue = MTIValueTmp.substring(0,
				MTIValueTmp.length() - NapasSettleRule.MTI.getDefaultValue().length());
		String MTI = NapasSettleRule.MTI.getField() + MTIValue + NapasSettleRule.MTI.getDefaultValue();
		String F2ValueTmp = StringUtils.repeat(NapasSettleRule.F2.getDefaultChar(), NapasSettleRule.F2.getLength());
		String F2Value = F2ValueTmp.substring(0, F2ValueTmp.length() - cardNumber.replace("x", "_").length());
		String F2 = NapasSettleRule.F2.getField() + F2Value + cardNumber.replace("x", "_");
		String F3ValueTmp = StringUtils.repeat(NapasSettleRule.F3.getDefaultChar(), NapasSettleRule.F3.getLength());
		String F3Value = F3ValueTmp.substring(0, F3ValueTmp.length() - NapasSettleRule.F3.getDefaultValue().length());
		String F3 = NapasSettleRule.F3.getField() + F3Value + NapasSettleRule.F3.getDefaultValue();
		String SVCValueTmp = StringUtils.repeat(NapasSettleRule.SVC.getDefaultChar(), NapasSettleRule.SVC.getLength());
		String SVCValue = SVCValueTmp.substring(0,
				SVCValueTmp.length() - NapasSettleRule.SVC.getDefaultValue().length());
		String SVC = NapasSettleRule.SVC.getField() + SVCValue + NapasSettleRule.SVC.getDefaultValue();
		String TCCValueTmp = StringUtils.repeat(NapasSettleRule.TCC.getDefaultChar(), NapasSettleRule.TCC.getLength());
		String TCCValue = TCCValueTmp.substring(0,
				TCCValueTmp.length() - NapasSettleRule.TCC.getDefaultValue().length());
		String TCC = NapasSettleRule.TCC.getField() + TCCValue + NapasSettleRule.TCC.getDefaultValue();
		String F4ValueTmp = StringUtils.repeat(NapasSettleRule.F4.getDefaultChar(), NapasSettleRule.F4.getLength());
		String F4Value = F4ValueTmp.substring(0, F4ValueTmp.length() - amount.toString().replace(".", "").length());
		String F4 = NapasSettleRule.F4.getField() + F4Value + amount.toString().replace(".", "");
		String RTAValueTmp = StringUtils.repeat(NapasSettleRule.RTA.getDefaultChar(), NapasSettleRule.RTA.getLength());
		String RTAValue = RTAValueTmp.substring(0, RTAValueTmp.length() - rta.toString().replace(".", "").length());
		String RTA = NapasSettleRule.RTA.getField() + RTAValue + rta.toString().replace(".", "");
		String F49ValueTmp = StringUtils.repeat(NapasSettleRule.F49.getDefaultChar(), NapasSettleRule.F49.getLength());
		String F49Value = F49ValueTmp.substring(0,
				F49ValueTmp.length() - NapasSettleRule.F49.getDefaultValue().length());
		String F49 = NapasSettleRule.F49.getField() + F49Value + NapasSettleRule.F49.getDefaultValue();
		String F5ValueTmp = StringUtils.repeat(NapasSettleRule.F5.getDefaultChar(), NapasSettleRule.F5.getLength());
		String F5Value = F5ValueTmp.substring(0, F5ValueTmp.length() - amount.toString().replace(".", "").length());
		String F5 = NapasSettleRule.F5.getField() + F5Value + amount.toString().replace(".", "");
		String F50ValueTmp = StringUtils.repeat(NapasSettleRule.F50.getDefaultChar(), NapasSettleRule.F50.getLength());
		String F50Value = F50ValueTmp.substring(0,
				F50ValueTmp.length() - NapasSettleRule.F50.getDefaultValue().length());
		String F50 = NapasSettleRule.F50.getField() + F50Value + NapasSettleRule.F50.getDefaultValue();
		String F9ValueTmp = StringUtils.repeat(NapasSettleRule.F9.getDefaultChar(), NapasSettleRule.F9.getLength());
		String F9Value = F9ValueTmp.substring(0, F9ValueTmp.length() - NapasSettleRule.F9.getDefaultValue().length());
		String F9 = NapasSettleRule.F9.getField() + F9Value + NapasSettleRule.F9.getDefaultValue();
		String F6ValueTmp = StringUtils.repeat(NapasSettleRule.F6.getDefaultChar(), NapasSettleRule.F6.getLength());
		String F6Value = F6ValueTmp.substring(0, F6ValueTmp.length() - NapasSettleRule.F6.getDefaultValue().length());
		String F6 = NapasSettleRule.F6.getField() + F6Value + NapasSettleRule.F6.getDefaultValue();
		String RCAValueTmp = StringUtils.repeat(NapasSettleRule.RCA.getDefaultChar(), NapasSettleRule.RCA.getLength());
		String RCAValue = RCAValueTmp.substring(0,
				RCAValueTmp.length() - NapasSettleRule.RCA.getDefaultValue().length());
		String RCA = NapasSettleRule.RCA.getField() + RCAValue + NapasSettleRule.RCA.getDefaultValue();
		String F51ValueTmp = StringUtils.repeat(NapasSettleRule.F51.getDefaultChar(), NapasSettleRule.F51.getLength());
		String F51Value = F51ValueTmp.substring(0,
				F51ValueTmp.length() - NapasSettleRule.F51.getDefaultValue().length());
		String F51 = NapasSettleRule.F51.getField() + F51Value + NapasSettleRule.F51.getDefaultValue();
		String F10ValueTmp = StringUtils.repeat(NapasSettleRule.F10.getDefaultChar(), NapasSettleRule.F10.getLength());
		String F10Value = F10ValueTmp.substring(0,
				F10ValueTmp.length() - NapasSettleRule.F10.getDefaultValue().length());
		String F10 = NapasSettleRule.F10.getField() + F10Value + NapasSettleRule.F10.getDefaultValue();
		String F11ValueTmp = StringUtils.repeat(NapasSettleRule.F11.getDefaultChar(), NapasSettleRule.F11.getLength());
		String F11Value = F11ValueTmp.substring(0,
				F11ValueTmp.length() - NapasSettleRule.F11.getDefaultValue().length());
		String F11 = NapasSettleRule.F11.getField() + F11Value + NapasSettleRule.F11.getDefaultValue();
		String F12ValueTmp = StringUtils.repeat(NapasSettleRule.F12.getDefaultChar(), NapasSettleRule.F12.getLength());
		String timeArr = transDate.split("T")[1].substring(0, 8).replace(":", "");
		String F12Value = F12ValueTmp.substring(0, F12ValueTmp.length() - timeArr.length());
		String F12 = NapasSettleRule.F12.getField() + F12Value + timeArr;
		String F13ValueTmp = StringUtils.repeat(NapasSettleRule.F13.getDefaultChar(), NapasSettleRule.F13.getLength());
		String dateArr = transDate.split("T")[0].substring(5, 10).replace("-", "");
		String F13Value = F13ValueTmp.substring(0, F13ValueTmp.length() - dateArr.length());
		String F13 = NapasSettleRule.F13.getField() + F13Value + dateArr;
		String F15ValueTmp = StringUtils.repeat(NapasSettleRule.F15.getDefaultChar(), NapasSettleRule.F15.getLength());
		String F15Value = F15ValueTmp.substring(0,
				F15ValueTmp.length() - settleDate.substring(2, 4).concat(settleDate.substring(0, 2)).length());
		String F15 = NapasSettleRule.F15.getField() + F15Value
				+ settleDate.substring(2, 4).concat(settleDate.substring(0, 2));
		String F18ValueTmp = StringUtils.repeat(NapasSettleRule.F18.getDefaultChar(), NapasSettleRule.F18.getLength());
		String F18Value = F18ValueTmp.substring(0,
				F18ValueTmp.length() - NapasSettleRule.F18.getDefaultValue().length());
		String F18 = NapasSettleRule.F18.getField() + F18Value + NapasSettleRule.F18.getDefaultValue();
		String F22ValueTmp = StringUtils.repeat(NapasSettleRule.F22.getDefaultChar(), NapasSettleRule.F22.getLength());
		String F22Value = F22ValueTmp.substring(0,
				F22ValueTmp.length() - NapasSettleRule.F22.getDefaultValue().length());
		String F22 = NapasSettleRule.F22.getField() + F22Value + NapasSettleRule.F22.getDefaultValue();
		String F25ValueTmp = StringUtils.repeat(NapasSettleRule.F25.getDefaultChar(), NapasSettleRule.F25.getLength());
		String F25Value = F25ValueTmp.substring(0,
				F25ValueTmp.length() - NapasSettleRule.F25.getDefaultValue().length());
		String F25 = NapasSettleRule.F25.getField() + F25Value + NapasSettleRule.F25.getDefaultValue();
		String F41ValueTmp = StringUtils.repeat(NapasSettleRule.F41.getDefaultChar(), NapasSettleRule.F41.getLength());
		String F41Value = F41ValueTmp.substring(0,
				F41ValueTmp.length() - NapasSettleRule.F41.getDefaultValue().length());
		String F41 = NapasSettleRule.F41.getField() + F41Value + NapasSettleRule.F41.getDefaultValue();
		String ACQValueTmp = StringUtils.repeat(NapasSettleRule.ACQ.getDefaultChar(), NapasSettleRule.ACQ.getLength());
		String ACQValue = ACQValueTmp.substring(0, ACQValueTmp.length() - tctvCode.length());
		String ACQ = NapasSettleRule.ACQ.getField() + ACQValue + tctvCode;
		String ISSValueTmp = StringUtils.repeat(NapasSettleRule.ISS.getDefaultChar(), NapasSettleRule.ISS.getLength());
		String ISSValue = ISSValueTmp.substring(0, ISSValueTmp.length() - cardNumber.substring(0, 6).length());
		String ISS = NapasSettleRule.ISS.getField() + ISSValue + cardNumber.substring(0, 6);
		String MIDValueTmp = StringUtils.repeat(NapasSettleRule.MID.getDefaultChar(), NapasSettleRule.MID.getLength());
		String MIDValue = MIDValueTmp.substring(0, MIDValueTmp.length() - bbb.length());
		String MID = NapasSettleRule.MID.getField() + MIDValue + bbb;
		String BNBValueTmp = StringUtils.repeat(NapasSettleRule.BNB.getDefaultChar(), NapasSettleRule.BNB.getLength());
		String BNBValue = BNBValueTmp.substring(0,
				BNBValueTmp.length() - NapasSettleRule.BNB.getDefaultValue().length());
		String BNB = NapasSettleRule.BNB.getField() + BNBValue + NapasSettleRule.BNB.getDefaultValue();
		String F102ValueTmp = StringUtils.repeat(NapasSettleRule.F102.getDefaultChar(),
				NapasSettleRule.F102.getLength());
		String F102Value = F102ValueTmp.substring(0,
				F102ValueTmp.length() - NapasSettleRule.F102.getDefaultValue().length());
		String F102 = NapasSettleRule.F102.getField() + F102Value + NapasSettleRule.F102.getDefaultValue();
		String F103ValueTmp = StringUtils.repeat(NapasSettleRule.F103.getDefaultChar(),
				NapasSettleRule.F103.getLength());
		String F103Value = F103ValueTmp.substring(0,
				F103ValueTmp.length() - NapasSettleRule.F103.getDefaultValue().length());
		String F103 = NapasSettleRule.F103.getField() + F103Value + NapasSettleRule.F103.getDefaultValue();
		String F37ValueTmp = StringUtils.repeat(NapasSettleRule.F37.getDefaultChar(), NapasSettleRule.F37.getLength());
		String F37Value = F37ValueTmp.substring(0, F37ValueTmp.length() - acquirerTransId.length());
		String F37 = NapasSettleRule.F37.getField() + F37Value + acquirerTransId;
		String F38ValueTmp = StringUtils.repeat(NapasSettleRule.F38.getDefaultChar(), NapasSettleRule.F38.getLength());
		String F38Value = F38ValueTmp.substring(0,
				F38ValueTmp.length() - NapasSettleRule.F38.getDefaultValue().length());
		String F38 = NapasSettleRule.F38.getField() + F38Value + NapasSettleRule.F38.getDefaultValue();
		String TRNValueTmp = StringUtils.repeat(NapasSettleRule.TRN.getDefaultChar(), NapasSettleRule.TRN.getLength());
		String TRNValue = TRNValueTmp.substring(0,
				TRNValueTmp.length() - NapasSettleRule.TRN.getDefaultValue().length());
		String TRN = NapasSettleRule.TRN.getField() + TRNValue + NapasSettleRule.TRN.getDefaultValue();
		String RRCValueTmp = StringUtils.repeat(NapasSettleRule.RRC.getDefaultChar(), NapasSettleRule.RRC.getLength());
		String RRCValue = RRCValueTmp.substring(0, RRCValueTmp.length() - rrcCode.length());
		String RRC = NapasSettleRule.RRC.getField() + RRCValue + rrcCode;
		String RSV1ValueTmp = StringUtils.repeat(NapasSettleRule.RSV1.getDefaultChar(),
				NapasSettleRule.RSV1.getLength());
		dateArr = transDate.split("T")[0].substring(0, 10).replace("-", "");
		String RSV1Value1 = RSV1ValueTmp.substring(0, 20)
				+ dateArr.substring(6, 8).concat(dateArr.substring(4, 6)).concat(dateArr.substring(0, 4));
		String RSV1Value2Tmp = RSV1ValueTmp.substring(28, 68 - orderId.length());
		String RSV1Value2 = RSV1Value2Tmp + orderId;
		String RSV1Value3 = StringUtils.repeat(NapasSettleRule.RSV1.getDefaultChar(), 32);
		String RSV1 = NapasSettleRule.RSV1.getField() + RSV1Value1 + RSV1Value2 + RSV1Value3;
		String RSV2ValueTmp = StringUtils.repeat(NapasSettleRule.RSV2.getDefaultChar(),
				NapasSettleRule.RSV2.getLength());
		String RSV2Value = RSV2ValueTmp.substring(0,
				RSV2ValueTmp.length() - NapasSettleRule.RSV2.getDefaultValue().length());
		String RSV2 = NapasSettleRule.RSV2.getField() + RSV2Value + NapasSettleRule.RSV2.getDefaultValue();
		String RSV3ValueTmp = StringUtils.repeat(NapasSettleRule.RSV3.getDefaultChar(),
				NapasSettleRule.RSV3.getLength());
		String RSV3Value = RSV3ValueTmp.substring(0,
				RSV3ValueTmp.length() - NapasSettleRule.RSV3.getDefaultValue().length());
		String RSV3 = NapasSettleRule.RSV3.getField() + RSV3Value + NapasSettleRule.RSV3.getDefaultValue();
		String CSR = NapasSettleRule.CSR.getField();
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
					SettleConstants.LINK_BANK, settleDate);
			trans.setStatus(status);
			commonSettleService.update(trans);
		}
	}

}
