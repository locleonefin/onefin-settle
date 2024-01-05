package com.onefin.ewallet.settlement.service.napas.co;


import com.onefin.ewallet.common.base.errorhandler.RuntimeInternalServerException;
import com.onefin.ewallet.common.domain.napas.NapasEwalletCETransactionView;
import com.onefin.ewallet.common.domain.settlement.SettlementTransaction;
import com.onefin.ewallet.common.quartz.entity.SchedulerJobInfo;
import com.onefin.ewallet.common.utility.date.DateTimeHelper;
import com.onefin.ewallet.common.utility.sercurity.SercurityHelper;
import com.onefin.ewallet.settlement.common.SettleConstants;
import com.onefin.ewallet.settlement.common.SettleConstants.NapasSettleRuleCO;
import com.onefin.ewallet.settlement.config.SFTPNapasEwalletIntegrationCO.UploadNapasGatewayCO;
import com.onefin.ewallet.settlement.controller.SettleJobController;
import com.onefin.ewallet.settlement.dto.NapasSettlement;
import com.onefin.ewallet.settlement.pgpas.PGPas;
import com.onefin.ewallet.settlement.pgpas.PKICrypt;
import com.onefin.ewallet.settlement.repository.NapasETransRepoCE;
import com.onefin.ewallet.settlement.repository.SettleTransRepository;
import com.onefin.ewallet.settlement.service.ConfigLoader;
import com.onefin.ewallet.settlement.service.MinioService;
import com.onefin.ewallet.settlement.service.SettleService;
import com.onefin.ewallet.settlement.service.napas.ZipExtractorService;
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
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author thaita
 */
@Component
@DisallowConcurrentExecution
public class NapasCOProcessTC extends QuartzJobBean {

	private static final Logger LOGGER = LoggerFactory.getLogger(NapasCOProcessTC.class);

	private static final String LOG_NAPAS_CO_PREFIX = SettleConstants.PARTNER_NAPAS + " " + SettleConstants.CASH_OUT;

	private static final String NAPAS_CASHOUT_SUBJECT_NO_SETTLE = "[Napas - Cashout] - Settle success";

	private static final String NAPAS_CASHOUT_SUBJECT_SETTLE = "[Napas - Cashout] - Settle TC dispute";

	private static final String NAPAS_CASHOUT_ISSUE_SUBJECT = "[Napas - Cashout] - Settle TC error";

	@Autowired
	private NapasETransRepoCE<?> transRepository;

	@Autowired
	private ConfigLoader configLoader;

	@Autowired
	private UploadNapasGatewayCO gateway;

	@Autowired
	private DateTimeHelper dateHelper;

	@Autowired
	private PGPas pGPas;

	@Autowired
	private PKICrypt pKICrypt;

	@Autowired
	private SettleTransRepository settleRepo;

	@Autowired
	@Qualifier("SftpNapasRemoteFileTemplateCO")
	private SftpRemoteFileTemplate sftpNapasGateway;

	@Autowired
	private SettleService commonSettleService;

	@Autowired
	private SercurityHelper sercurityHelper;

	@Autowired
	private SettleJobController settleJobController;

	@Autowired
	private MinioService minioService;

	@Autowired
	private ZipExtractorService zipExtractorService;

	/* *************************WAITING TC FILE FROM NAPAS***************************** */

	/**
	 * @throws ParseException
	 * @throws IOException    Find in Napas sftp for TC file and process all pending transaction
	 */
	@Override
	protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
		LOGGER.info("{} Start Napas Waiting TC File", LOG_NAPAS_CO_PREFIX);
		// Step 1: Summary day need to be settled
		List<SettlementTransaction> transPendTCs = settleRepo.findAllByPartnerAndDomainAndStatusTransaction(
				SettleConstants.PARTNER_NAPAS, SettleConstants.CASH_OUT,
				SettleConstants.SETTLEMENT_PENDING_T0);
		LOGGER.info("{} Number Napas Settlement Days TC {}", LOG_NAPAS_CO_PREFIX, transPendTCs.size());
		List<String> settleDate = new ArrayList<String>();

		for (SettlementTransaction transPendTC : transPendTCs) {
			settleDate.add(transPendTC.getSettleKey().getSettleDate());
		}
		LOGGER.info("{} Day need to settled {}", LOG_NAPAS_CO_PREFIX, settleDate);
		// Step 2: Find Napas settle file base on settle date
		LOGGER.info("{} Start find TC settle file in SFTP", LOG_NAPAS_CO_PREFIX);
		for (SettlementTransaction transPendTC : transPendTCs) {
			// Download remote to local
			String settleFileName = String.format("%s_%s_%s_%s_%s_%s_%s.zip", transPendTC.getSettleKey().getSettleDate(),
					configLoader.getSettleCONapasZZZ(), configLoader.getSettleCONapasBBB(),
					configLoader.getSettleCONapasTctvCode(), configLoader.getSettleCONapasSettleOrder(),
					configLoader.getSettleCONapasSettleFileTypeTC(), configLoader.getSettleCONapasServiceCodeEcom());
			LOGGER.info("{} File name: {}", LOG_NAPAS_CO_PREFIX, settleFileName);
			try {
				// Try to retrieve Napas File
				sftpNapasGateway.get(configLoader.getSftpCONapasDirectoryRemoteIn() + settleFileName, stream -> {
					FileCopyUtils.copy(stream, new FileOutputStream(
							new File(configLoader.getSftpCONapasDirectoryLocalFile() + settleFileName)));
				});
				//Extract zip file
				zipExtractorService.extractZipFile(configLoader.getSftpCONapasDirectoryLocalFile() + settleFileName, configLoader.getSftpCONapasDirectoryLocalFile());
				//TODO: check if file .dat present

				// Settle completed => paused job
				SchedulerJobInfo jobTmp = new SchedulerJobInfo();
				jobTmp.setJobClass(NapasCOProcessTC.class.getName());
				settleJobController.pauseJob(jobTmp);
				// Update to Pending XL File
				transPendTC.setStatus(SettleConstants.SETTLEMENT_PROCESSING);
				commonSettleService.update(transPendTC);
				// Process SL file and send to Napas
				napasProcessTC(configLoader.getSftpCONapasDirectoryLocalFile() + settleFileName.replace("zip","dat"),
						settleFileName.replace("zip","dat"), transPendTC, settleDate);
			} catch (Exception e) {
				e.printStackTrace();
				LOGGER.info("{} Napas TC Error {}", LOG_NAPAS_CO_PREFIX, e);
				LOGGER.info("{} Not Found Napas Settlement TC {}", LOG_NAPAS_CO_PREFIX, transPendTC.getSettleKey().getSettleDate());
			}
		}
		LOGGER.info("{} End find TC settle file in SFTP", LOG_NAPAS_CO_PREFIX);
	}

	/**
	 * @throws ParseException
	 * @throws IOException    Look up in Napas SFTP IN Folder
	 */
	public ResponseEntity<?> ewalletNapasTCSettlementManually(String date, String order,
	                                                          SettlementTransaction transPendTC, List<String> settleDateList) throws ParseException, IOException {
		LOGGER.info("== Start Napas Settlement TC");
		// Download remote to local
		String settleFileName = String.format("%s_%s_%s_%s_%s_%s_%s.zip", date, configLoader.getSettleNapasZZZ(),
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
	 * @param napasFile
	 * @param napasFileName
	 * @param transPendTCXL
	 * @throws Exception
	 */
	public void napasProcessTC(String napasFile, String napasFileName,
	                           SettlementTransaction transPendTCXL, List<String> settleDateList) throws Exception {
		LOGGER.info("{} Start Napas Settlement Processing SL File {}", LOG_NAPAS_CO_PREFIX, napasFileName);

		File TCFile = new File(napasFile);
		File SLFile = null;
		File OneFinInternal = null;
		String SLFileName = napasFileName.replace(configLoader.getSettleCONapasSettleFileTypeTC(), configLoader.getSettleCONapasSettleFileTypeSL());
		BufferedWriter writer = null;
		BufferedWriter OneFinInternalWriter = null;
		FileReader reader = null;
		BufferedReader br = null;

		// Step 2 Query All Transaction to Settle
		LOGGER.info("{} Query All Transaction to Settle {}", LOG_NAPAS_CO_PREFIX, napasFile);
		List<NapasEwalletCETransactionView> settleNapasSuccess = new ArrayList();
		List<NapasEwalletCETransactionView> settleNapasUnSuccess = new ArrayList();
		LOGGER.info("{} Settle Date List {}", LOG_NAPAS_CO_PREFIX, settleDateList);
		for (String tmp : settleDateList) {
			String previousDate = dateHelper.previousDateString(tmp, SettleConstants.HO_CHI_MINH_TIME_ZONE,
					SettleConstants.DATE_FORMAT_MMddyy);
			String previousDateToSearch = convertDateFormat(previousDate,SettleConstants.DATE_FORMAT_MMddyy,SettleConstants.DATE_FORMAT_yyyyMMdd);
			LOGGER.info("{} Settle Date {}", LOG_NAPAS_CO_PREFIX, previousDate);
			// Query Success trans
			List<NapasEwalletCETransactionView> transTmp1 = transRepository.findByEqualStatusAndNapasTransDate(
					configLoader.getNapasSettleActionCashout(), SettleConstants.TRANS_SUCCESS, previousDateToSearch);
			if (transTmp1.size() != 0) {
				settleNapasSuccess.addAll(transTmp1);
			}
			// Query UnSuccess trans
			List<NapasEwalletCETransactionView> transTmp2 = transRepository.findByNotEqualStatusAndNapasTransDate(
					configLoader.getNapasSettleActionCashout(), SettleConstants.TRANS_SUCCESS, previousDateToSearch);
			if (transTmp2.size() != 0) {
				settleNapasUnSuccess.addAll(transTmp2);
			}
		}
		LOGGER.info("{} Settle Success trans {}", LOG_NAPAS_CO_PREFIX, settleNapasSuccess.size());
		LOGGER.info("{} Settle Fail trans {}", LOG_NAPAS_CO_PREFIX, settleNapasUnSuccess.size());
		int row = 0;
		int rowOneFinInternal = 0;
		// Step 3 Create SL File And Check line by line in TC file with OneFin transaction
		LOGGER.info("{} Create SL File And Check line by line in TC file with OneFin transaction", LOG_NAPAS_CO_PREFIX);
		try {
			SLFile = new File(configLoader.getSftpCONapasDirectoryLocalFile() + SLFileName);
			OneFinInternal = new File(configLoader.getSftpCONapasDirectoryLocalFile() + "OFInternal" + SLFileName);
			writer = new BufferedWriter(new FileWriter(SLFile));
			OneFinInternalWriter = new BufferedWriter(new FileWriter(OneFinInternal));
			reader = new FileReader(napasFile);
			br = new BufferedReader(reader);
			// read line by line
			String line = br.readLine();
			String settleDate = line.substring(line.indexOf("[DATE]") + "[DATE]".length(), line.length());
			writer.write(line);
			writer.newLine();
			List<NapasSettlement> napasSettleTrans = new ArrayList<NapasSettlement>();
			String TRFinal = null;
			LOGGER.info("{} Read TC file successfully", LOG_NAPAS_CO_PREFIX);
			while ((line = br.readLine()) != null) {
				int startIndex = -1;
				// Detail record
				if (line.subSequence(0, 2).equals(NapasSettleRuleCO.DR.getField())) {
					startIndex = line.indexOf(NapasSettleRuleCO.MTI.getField()) + NapasSettleRuleCO.MTI.getField().length();
					String MTI = line.substring(startIndex, startIndex + NapasSettleRuleCO.MTI.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.F2.getField()) + NapasSettleRuleCO.F2.getField().length();
					String F2 = line.substring(startIndex, startIndex + NapasSettleRuleCO.F2.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.F3.getField()) + NapasSettleRuleCO.F3.getField().length();
					String F3 = line.substring(startIndex, startIndex + NapasSettleRuleCO.F3.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.SVC.getField()) + NapasSettleRuleCO.SVC.getField().length();
					String SVC = line.substring(startIndex, startIndex + NapasSettleRuleCO.SVC.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.TCC.getField()) + NapasSettleRuleCO.TCC.getField().length();
					String TCC = line.substring(startIndex, startIndex + NapasSettleRuleCO.TCC.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.F4.getField()) + NapasSettleRuleCO.F4.getField().length();
					String F4 = line.substring(startIndex, startIndex + NapasSettleRuleCO.F4.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.RTA.getField()) + NapasSettleRuleCO.RTA.getField().length();
					String RTA = line.substring(startIndex, startIndex + NapasSettleRuleCO.RTA.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.F49.getField()) + NapasSettleRuleCO.F49.getField().length();
					String F49 = line.substring(startIndex, startIndex + NapasSettleRuleCO.F49.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.F5.getField()) + NapasSettleRuleCO.F5.getField().length();
					String F5 = line.substring(startIndex, startIndex + NapasSettleRuleCO.F5.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.F50.getField()) + NapasSettleRuleCO.F50.getField().length();
					String F50 = line.substring(startIndex, startIndex + NapasSettleRuleCO.F50.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.F9.getField()) + NapasSettleRuleCO.F9.getField().length();
					String F9 = line.substring(startIndex, startIndex + NapasSettleRuleCO.F9.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.F6.getField()) + NapasSettleRuleCO.F6.getField().length();
					String F6 = line.substring(startIndex, startIndex + NapasSettleRuleCO.F6.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.RCA.getField()) + NapasSettleRuleCO.RCA.getField().length();
					String RCA = line.substring(startIndex, startIndex + NapasSettleRuleCO.RCA.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.F51.getField()) + NapasSettleRuleCO.F51.getField().length();
					String F51 = line.substring(startIndex, startIndex + NapasSettleRuleCO.F51.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.F10.getField()) + NapasSettleRuleCO.F10.getField().length();
					String F10 = line.substring(startIndex, startIndex + NapasSettleRuleCO.F10.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.F11.getField()) + NapasSettleRuleCO.F11.getField().length();
					String F11 = line.substring(startIndex, startIndex + NapasSettleRuleCO.F11.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.F12.getField()) + NapasSettleRuleCO.F12.getField().length();
					String F12 = line.substring(startIndex, startIndex + NapasSettleRuleCO.F12.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.F13.getField()) + NapasSettleRuleCO.F13.getField().length();
					String F13 = line.substring(startIndex, startIndex + NapasSettleRuleCO.F13.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.F15.getField()) + NapasSettleRuleCO.F15.getField().length();
					String F15 = line.substring(startIndex, startIndex + NapasSettleRuleCO.F15.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.F18.getField()) + NapasSettleRuleCO.F18.getField().length();
					String F18 = line.substring(startIndex, startIndex + NapasSettleRuleCO.F18.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.F22.getField()) + NapasSettleRuleCO.F22.getField().length();
					String F22 = line.substring(startIndex, startIndex + NapasSettleRuleCO.F22.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.F25.getField()) + NapasSettleRuleCO.F25.getField().length();
					String F25 = line.substring(startIndex, startIndex + NapasSettleRuleCO.F25.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.F41.getField()) + NapasSettleRuleCO.F41.getField().length();
					String F41 = line.substring(startIndex, startIndex + NapasSettleRuleCO.F41.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.ACQ.getField()) + NapasSettleRuleCO.ACQ.getField().length();
					String ACQ = line.substring(startIndex, startIndex + NapasSettleRuleCO.ACQ.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.ISS.getField()) + NapasSettleRuleCO.ISS.getField().length();
					String ISS = line.substring(startIndex, startIndex + NapasSettleRuleCO.ISS.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.MID.getField()) + NapasSettleRuleCO.MID.getField().length();
					String MID = line.substring(startIndex, startIndex + NapasSettleRuleCO.MID.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.BNB.getField()) + NapasSettleRuleCO.BNB.getField().length();
					String BNB = line.substring(startIndex, startIndex + NapasSettleRuleCO.BNB.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.F102.getField())
							+ NapasSettleRuleCO.F102.getField().length();
					String F102 = line.substring(startIndex, startIndex + NapasSettleRuleCO.F102.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.F103.getField())
							+ NapasSettleRuleCO.F103.getField().length();
					String F103 = line.substring(startIndex, startIndex + NapasSettleRuleCO.F103.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.SVFISSNP.getField())
							+ NapasSettleRuleCO.SVFISSNP.getField().length();
					String SVFISSNP = line.substring(startIndex, startIndex + NapasSettleRuleCO.SVFISSNP.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.IRFISSACQ.getField())
							+ NapasSettleRuleCO.IRFISSACQ.getField().length();
					String IRFISSACQ = line.substring(startIndex, startIndex + NapasSettleRuleCO.IRFISSACQ.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.IRFISSBNB.getField())
							+ NapasSettleRuleCO.IRFISSBNB.getField().length();
					String IRFISSBNB = line.substring(startIndex, startIndex + NapasSettleRuleCO.IRFISSBNB.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.SVFACQNP.getField())
							+ NapasSettleRuleCO.SVFACQNP.getField().length();
					String SVFACQNP = line.substring(startIndex, startIndex + NapasSettleRuleCO.SVFACQNP.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.IRFACQISS.getField())
							+ NapasSettleRuleCO.IRFACQISS.getField().length();
					String IRFACQISS = line.substring(startIndex, startIndex + NapasSettleRuleCO.IRFACQISS.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.IRFACQBNB.getField())
							+ NapasSettleRuleCO.IRFACQBNB.getField().length();
					String IRFACQBNB = line.substring(startIndex, startIndex + NapasSettleRuleCO.IRFACQBNB.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.SVFBNBNP.getField())
							+ NapasSettleRuleCO.SVFBNBNP.getField().length();
					String SVFBNBNP = line.substring(startIndex, startIndex + NapasSettleRuleCO.SVFBNBNP.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.IRFBNBISS.getField())
							+ NapasSettleRuleCO.IRFBNBISS.getField().length();
					String IRFBNBISS = line.substring(startIndex, startIndex + NapasSettleRuleCO.IRFBNBISS.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.IRFBNBACQ.getField())
							+ NapasSettleRuleCO.IRFBNBACQ.getField().length();
					String IRFBNBACQ = line.substring(startIndex, startIndex + NapasSettleRuleCO.IRFBNBACQ.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.F37.getField()) + NapasSettleRuleCO.F37.getField().length();
					String F37 = line.substring(startIndex, startIndex + NapasSettleRuleCO.F37.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.F38.getField()) + NapasSettleRuleCO.F38.getField().length();
					String F38 = line.substring(startIndex, startIndex + NapasSettleRuleCO.F38.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.TRN.getField()) + NapasSettleRuleCO.TRN.getField().length();
					String TRN = line.substring(startIndex, startIndex + NapasSettleRuleCO.TRN.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.RRC.getField()) + NapasSettleRuleCO.RRC.getField().length();
					String RRC = line.substring(startIndex, startIndex + NapasSettleRuleCO.RRC.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.RSV1.getField())
							+ NapasSettleRuleCO.RSV1.getField().length();
					String RSV1 = line.substring(startIndex, startIndex + NapasSettleRuleCO.RSV1.getLength());
					String transDate = findRealStringIgnoreSpace(RSV1.substring(20, 28));
					String orderId = findRealStringIgnoreSpace(RSV1.substring(28, 68));
					startIndex = line.indexOf(NapasSettleRuleCO.RSV2.getField())
							+ NapasSettleRuleCO.RSV2.getField().length();
					String RSV2 = line.substring(startIndex, startIndex + NapasSettleRuleCO.RSV2.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.RSV3.getField())
							+ NapasSettleRuleCO.RSV3.getField().length();
					String RSV3 = line.substring(startIndex, startIndex + NapasSettleRuleCO.RSV3.getLength());
					startIndex = line.indexOf(NapasSettleRuleCO.CSR.getField()) + NapasSettleRuleCO.CSR.getField().length();
					String CSR = line.substring(startIndex, startIndex + NapasSettleRuleCO.CSR.getLength());
					String inputCheckSum = line.substring(0, startIndex);
					NapasSettlement napasSettleTran = new NapasSettlement(MTI, F2, F3, SVC, TCC, F4, RTA, F49, F5, F50,
							F9, F6, RCA, F51, F10, F11, F12, F13, F15, F18, F22, F25, F41, ACQ, ISS, MID, BNB, F102,
							F103, SVFISSNP, IRFISSACQ, IRFISSBNB, SVFACQNP, IRFACQISS, IRFACQBNB, SVFBNBNP, IRFBNBISS,
							IRFBNBACQ, F37, F38, TRN, RRC, RSV1, transDate, orderId, RSV2, RSV3, CSR, inputCheckSum);
					napasSettleTrans.add(napasSettleTran);
				}
			}
			LOGGER.info("{} End read TC file", LOG_NAPAS_CO_PREFIX);
			// Compare and check OF-NAPAS
			// Case 1a: Success trans in OF but Napas doesn't have => add to SL file (note
			// check them date)
			LOGGER.info("{} - Start check success trans in OF but Napas doesn't have", LOG_NAPAS_CO_PREFIX);
			for (NapasEwalletCETransactionView tmp : settleNapasSuccess) {
				boolean check = false;
				for (NapasSettlement tmp1 : napasSettleTrans) {
					if (String.format("%s_%s",findRealStringIgnoreSpace(tmp1.getF37()),findRealStringIgnoreSpace(tmp1.getF11())).equals(tmp.getOrderId())
							&& new BigDecimal(tmp1.getF4().substring(0, tmp1.getF4().length() - 2) + '.'
							+ tmp1.getF4().substring(tmp1.getF4().length() - 2, tmp1.getF4().length()))
							.equals(tmp.getAmount())) {
						check = true;
						break;
					}
				}
				if (!check) {
					writer.write(this.createSLLine(tmp.getSenderAccountNumber(), tmp.getCardNumber(), tmp.getAmount(), tmp.getAmount(),
							tmp.getTransactionDate(), settleDate, configLoader.getSettleCONapasTctvCode(),
							configLoader.getSettleCONapasBBB(), tmp.getAcquirerTransId(),
							configLoader.getSettleCONapasGdSL(), tmp.getOrderId(), tmp.getOrderId().split("_")[1]));
					writer.newLine();
					row++;
				}
			}
			LOGGER.info("{} - End check success trans in OF but Napas doesn't have: {}", LOG_NAPAS_CO_PREFIX, row);

			// Case 1b: Success trans in Napas but OF doesn't have => add to SL file (note
			// check them date)
			LOGGER.info("{} - Start check success trans in Napas but OF doesn't have", LOG_NAPAS_CO_PREFIX);
			int row_tmp = 0;
			for (NapasSettlement tmp1 : napasSettleTrans) {
				boolean check = false;
				for (NapasEwalletCETransactionView tmp : settleNapasSuccess) {
					if (String.format("%s_%s",findRealStringIgnoreSpace(tmp1.getF37()),findRealStringIgnoreSpace(tmp1.getF11())).equals(tmp.getOrderId())
							&& new BigDecimal(tmp1.getF4().substring(0, tmp1.getF4().length() - 2) + '.'
							+ tmp1.getF4().substring(tmp1.getF4().length() - 2, tmp1.getF4().length()))
							.equals(tmp.getAmount())) {
						check = true;
						break;
					}
				}
				if (!check) {
					writer.write(this.createSLLineFromNapasTrans(findRealStringIgnoreSpace(tmp1.getF103()), tmp1.getF2(),
							tmp1.getF3(), tmp1.getF4(), tmp1.getRTA(), tmp1.getF5(), tmp1.getF11(), tmp1.getF12(), tmp1.getF13(), tmp1.getF15(), tmp1.getRSV1(),
							configLoader.getSettleCONapasTctvCode(), configLoader.getSettleCONapasBBB(), tmp1.getF37(), configLoader.getSettleCONapasGdBH(), tmp1.getF102(), tmp1.getF103()));
					writer.newLine();
					row++;
					row_tmp++;
				}
			}
			LOGGER.info("{} - End check success trans in Napas but OF doesn't have: {}", LOG_NAPAS_CO_PREFIX, row_tmp);

			// Case 2: Unsuccess trans in OF but Napas have => add to SL file, report to OneFin only
			LOGGER.info("{} - Start check unsuccess trans in OF but Napas have => add to SL file, report to OneFin only", LOG_NAPAS_CO_PREFIX);
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
					OneFinInternalWriter.write(this.createSLLine(tmp.getSenderAccountNumber(), tmp.getCardNumber(), tmp.getAmount(), tmp.getAmount(),
							tmp.getTransactionDate(), settleDate, configLoader.getSettleCONapasTctvCode(),
							configLoader.getSettleCONapasBBB(), tmp.getAcquirerTransId(),
							configLoader.getSettleCONapasGdHTTP(), tmp.getOrderId(), null));
					OneFinInternalWriter.newLine();
					rowOneFinInternal++;
				}
			}
			LOGGER.info("{} - End check unsuccess trans in OF but Napas have => add to SL file, report to OneFin only {}", LOG_NAPAS_CO_PREFIX, rowOneFinInternal);
			// Prepare terminal line
			LOGGER.info("{} - TR Line", LOG_NAPAS_CO_PREFIX);
			String TR = NapasSettleRuleCO.TR.getField();
			String NOTValueTmp = StringUtils.repeat("0", NapasSettleRuleCO.NOT.getLength());
			String NOTValue = NOTValueTmp.substring(0, NOTValueTmp.length() - Integer.toString(row).length());
			String NOT = NapasSettleRuleCO.NOT.getField() + NOTValue + row;
			String CREValueTmp = StringUtils.repeat(" ", NapasSettleRuleCO.CRE.getLength());
			String CREValue = CREValueTmp.substring(0,
					CREValueTmp.length() - configLoader.getSettleCONapasDefaultExecuteUser().length());
			String CRE = NapasSettleRuleCO.CRE.getField() + CREValue + configLoader.getSettleCONapasDefaultExecuteUser();
			String currentDate = dateHelper.currentDateString(SettleConstants.HO_CHI_MINH_TIME_ZONE,
					SettleConstants.DATE_FORMAT_HHmmssddMMyyyy);
			String TIME = NapasSettleRuleCO.TIME.getField() + currentDate.substring(0, 6);
			String DATE = NapasSettleRuleCO.DATE.getField() + currentDate.substring(6, currentDate.length());
			String TRTmp = TR + NOT + CRE + TIME + DATE + NapasSettleRuleCO.CSF.getField();
			TRFinal = TRTmp + sercurityHelper.hashMD5Napas(TRTmp, configLoader.getSettleCONapasTctvCode());
			writer.write(TRFinal);
			LOGGER.info("{} End TR Line", LOG_NAPAS_CO_PREFIX);
		} catch (FileNotFoundException e) {
			transPendTCXL.setStatus(SettleConstants.SETTLEMENT_ERROR);
			LOGGER.error("{} {}", LOG_NAPAS_CO_PREFIX, SettleConstants.ERROR_PREPARE_SETTLE_FILE, e);
		} finally {
			try {
				settleDateList.remove(0);
				updateTransactionList(settleDateList, SettleConstants.SETTLEMENT_SUCCESS);
				writer.close();
				OneFinInternalWriter.close();
				reader.close();
				br.close();

				//Compress to zip file
//				String zipSLFileName = (configLoader.getSftpCONapasDirectoryLocalFile() + SLFileName).replace(".dat",".zip");
//				zipExtractorService.compressToZipFile(configLoader.getSftpCONapasDirectoryLocalFile() + SLFileName, zipSLFileName);

				if (row > 0) {
					LOGGER.info("{} Upload SL file to SFTP", LOG_NAPAS_CO_PREFIX);
					transPendTCXL.setStatus(SettleConstants.SETTLEMENT_PENDING_T1);
					gateway.upload(new File(configLoader.getSftpCONapasDirectoryLocalFile() + SLFileName));
				} else {
					LOGGER.info("{} Not Upload SL file to SFTP", LOG_NAPAS_CO_PREFIX);
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
				minioService.uploadFile(configLoader.getBaseBucket(), configLoader.getSettleNapasCOMinioDefaultFolder() + currentDate + "/" + TCFile.getName(), TCFile, "text/plain");
				TCFile.delete();
				minioService.uploadFile(configLoader.getBaseBucket(), configLoader.getSettleNapasCOMinioDefaultFolder() + currentDate + "/" + SLFile.getName(), SLFile, "text/plain");
				SLFile.delete();
				minioService.uploadFile(configLoader.getBaseBucket(), configLoader.getSettleNapasCOMinioDefaultFolder() + currentDate + "/" + OneFinInternal.getName(), OneFinInternal, "text/plain");
				OneFinInternal.delete();
				LOGGER.info("{} MINIO uploaded successfully", LOG_NAPAS_CO_PREFIX);
				// Notify for admin
				String subject;
				if (row > 0) {
					subject = NAPAS_CASHOUT_SUBJECT_SETTLE;
				} else {
					subject = NAPAS_CASHOUT_SUBJECT_NO_SETTLE;
				}
				commonSettleService.settleCompleted(currentDate, configLoader.getBaseBucket(), configLoader.getSettleNapasCOMinioDefaultFolder() + currentDate + "/" + SLFile.getName(), subject);
				LOGGER.info("{} End Napas Settlement Processing TC File {}", LOG_NAPAS_CO_PREFIX, napasFileName);
			} catch (Exception e) {
				LOGGER.error("{} {}", LOG_NAPAS_CO_PREFIX, SettleConstants.ERROR_UPLOAD_SETTLE_FILE, e);
			}
		}
	}

	private String createSLLine(String senderAccountNumber, String cardNumber, BigDecimal amount, BigDecimal rta, String transDate,
	                            String settleDate, String tctvCode, String bbb, String acquirerTransId, String rrcCode, String orderId, String traceId) {
		String DR = NapasSettleRuleCO.DR.getField();
		String MTIValueTmp = StringUtils.repeat(NapasSettleRuleCO.MTI.getDefaultChar(), NapasSettleRuleCO.MTI.getLength());
		String MTIValue = MTIValueTmp.substring(0,
				MTIValueTmp.length() - NapasSettleRuleCO.MTI.getDefaultValue().length());
		String MTI = NapasSettleRuleCO.MTI.getField() + MTIValue + NapasSettleRuleCO.MTI.getDefaultValue();
		String F2ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F2.getDefaultChar(), NapasSettleRuleCO.F2.getLength());
		String F2Value = F2ValueTmp.substring(0, F2ValueTmp.length() - senderAccountNumber.length());
		String F2 = NapasSettleRuleCO.F2.getField() + F2Value + senderAccountNumber;
		String F3ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F3.getDefaultChar(), NapasSettleRuleCO.F3.getLength());
		String F3Value = F3ValueTmp.substring(0, F3ValueTmp.length() - NapasSettleRuleCO.F3.getDefaultValue().length());
		String F3 = NapasSettleRuleCO.F3.getField() + F3Value + NapasSettleRuleCO.F3.getDefaultValue();
		String SVCValueTmp = StringUtils.repeat(NapasSettleRuleCO.SVC.getDefaultChar(), NapasSettleRuleCO.SVC.getLength());
		String SVCValue = SVCValueTmp.substring(0,
				SVCValueTmp.length() - NapasSettleRuleCO.SVC.getDefaultValue().length());
		String SVC = NapasSettleRuleCO.SVC.getField() + SVCValue + NapasSettleRuleCO.SVC.getDefaultValue();
		String TCCValueTmp = StringUtils.repeat(NapasSettleRuleCO.TCC.getDefaultChar(), NapasSettleRuleCO.TCC.getLength());
		String TCCValue = TCCValueTmp.substring(0,
				TCCValueTmp.length() - NapasSettleRuleCO.TCC.getDefaultValue().length());
		String TCC = NapasSettleRuleCO.TCC.getField() + TCCValue + NapasSettleRuleCO.TCC.getDefaultValue();
		String F4ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F4.getDefaultChar(), NapasSettleRuleCO.F4.getLength());
		String F4Value = F4ValueTmp.substring(0, F4ValueTmp.length() - amount.toString().replace(".", "").length());
		String F4 = NapasSettleRuleCO.F4.getField() + F4Value + amount.toString().replace(".", "");
		String RTAValueTmp = StringUtils.repeat(NapasSettleRuleCO.RTA.getDefaultChar(), NapasSettleRuleCO.RTA.getLength());
		String RTAValue = RTAValueTmp.substring(0, RTAValueTmp.length() - rta.toString().replace(".", "").length());
		String RTA = NapasSettleRuleCO.RTA.getField() + RTAValue + rta.toString().replace(".", "");
		String F49ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F49.getDefaultChar(), NapasSettleRuleCO.F49.getLength());
		String F49Value = F49ValueTmp.substring(0,
				F49ValueTmp.length() - NapasSettleRuleCO.F49.getDefaultValue().length());
		String F49 = NapasSettleRuleCO.F49.getField() + F49Value + NapasSettleRuleCO.F49.getDefaultValue();
		String F5ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F5.getDefaultChar(), NapasSettleRuleCO.F5.getLength());
		String F5Value = F5ValueTmp.substring(0, F5ValueTmp.length() - amount.toString().replace(".", "").length());
		String F5 = NapasSettleRuleCO.F5.getField() + F5Value + amount.toString().replace(".", "");
		String F50ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F50.getDefaultChar(), NapasSettleRuleCO.F50.getLength());
		String F50Value = F50ValueTmp.substring(0,
				F50ValueTmp.length() - NapasSettleRuleCO.F50.getDefaultValue().length());
		String F50 = NapasSettleRuleCO.F50.getField() + F50Value + NapasSettleRuleCO.F50.getDefaultValue();
		String F9ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F9.getDefaultChar(), NapasSettleRuleCO.F9.getLength());
		String F9Value = F9ValueTmp.substring(0, F9ValueTmp.length() - NapasSettleRuleCO.F9.getDefaultValue().length());
		String F9 = NapasSettleRuleCO.F9.getField() + F9Value + NapasSettleRuleCO.F9.getDefaultValue();
		String F6ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F6.getDefaultChar(), NapasSettleRuleCO.F6.getLength());
		String F6Value = F6ValueTmp.substring(0, F6ValueTmp.length() - NapasSettleRuleCO.F6.getDefaultValue().length());
		String F6 = NapasSettleRuleCO.F6.getField() + F6Value + NapasSettleRuleCO.F6.getDefaultValue();
		String RCAValueTmp = StringUtils.repeat(NapasSettleRuleCO.RCA.getDefaultChar(), NapasSettleRuleCO.RCA.getLength());
		String RCAValue = RCAValueTmp.substring(0,
				RCAValueTmp.length() - NapasSettleRuleCO.RCA.getDefaultValue().length());
		String RCA = NapasSettleRuleCO.RCA.getField() + RCAValue + NapasSettleRuleCO.RCA.getDefaultValue();
		String F51ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F51.getDefaultChar(), NapasSettleRuleCO.F51.getLength());
		String F51Value = F51ValueTmp.substring(0,
				F51ValueTmp.length() - NapasSettleRuleCO.F51.getDefaultValue().length());
		String F51 = NapasSettleRuleCO.F51.getField() + F51Value + NapasSettleRuleCO.F51.getDefaultValue();
		String F10ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F10.getDefaultChar(), NapasSettleRuleCO.F10.getLength());
		String F10Value = F10ValueTmp.substring(0,
				F10ValueTmp.length() - NapasSettleRuleCO.F10.getDefaultValue().length());
		String F10 = NapasSettleRuleCO.F10.getField() + F10Value + NapasSettleRuleCO.F10.getDefaultValue();
		String F11ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F11.getDefaultChar(), NapasSettleRuleCO.F11.getLength());
		String F11;
		if (traceId != null){
			F11 = NapasSettleRuleCO.F11.getField() + traceId;
		} else {
			String F11Value = F11ValueTmp.substring(0,
					F11ValueTmp.length() - NapasSettleRuleCO.F11.getDefaultValue().length());
			F11 = NapasSettleRuleCO.F11.getField() + F11Value + NapasSettleRuleCO.F11.getDefaultValue();
		}
		String F12ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F12.getDefaultChar(), NapasSettleRuleCO.F12.getLength());
		String timeArr = transDate.split("T")[1].substring(0, 8).replace(":", "");
		String F12Value = F12ValueTmp.substring(0, F12ValueTmp.length() - timeArr.length());
		String F12 = NapasSettleRuleCO.F12.getField() + F12Value + timeArr;
		String F13ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F13.getDefaultChar(), NapasSettleRuleCO.F13.getLength());
		String dateArr = transDate.split("T")[0].substring(5, 10).replace("-", "");
		String F13Value = F13ValueTmp.substring(0, F13ValueTmp.length() - dateArr.length());
		String F13 = NapasSettleRuleCO.F13.getField() + F13Value + dateArr;
		String F15ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F15.getDefaultChar(), NapasSettleRuleCO.F15.getLength());
		String F15Value = F15ValueTmp.substring(0,
				F15ValueTmp.length() - settleDate.substring(2, 4).concat(settleDate.substring(0, 2)).length());
		String F15 = NapasSettleRuleCO.F15.getField() + F15Value
				+ settleDate.substring(2, 4).concat(settleDate.substring(0, 2));
		String F18ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F18.getDefaultChar(), NapasSettleRuleCO.F18.getLength());
		String F18Value = F18ValueTmp.substring(0,
				F18ValueTmp.length() - NapasSettleRuleCO.F18.getDefaultValue().length());
		String F18 = NapasSettleRuleCO.F18.getField() + F18Value + NapasSettleRuleCO.F18.getDefaultValue();
		String F22ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F22.getDefaultChar(), NapasSettleRuleCO.F22.getLength());
		String F22Value = F22ValueTmp.substring(0,
				F22ValueTmp.length() - NapasSettleRuleCO.F22.getDefaultValue().length());
		String F22 = NapasSettleRuleCO.F22.getField() + F22Value + NapasSettleRuleCO.F22.getDefaultValue();
		String F25ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F25.getDefaultChar(), NapasSettleRuleCO.F25.getLength());
		String F25Value = F25ValueTmp.substring(0,
				F25ValueTmp.length() - NapasSettleRuleCO.F25.getDefaultValue().length());
		String F25 = NapasSettleRuleCO.F25.getField() + F25Value + NapasSettleRuleCO.F25.getDefaultValue();
		String F41ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F41.getDefaultChar(), NapasSettleRuleCO.F41.getLength());
		String F41Value = F41ValueTmp.substring(0, F41ValueTmp.length() - NapasSettleRuleCO.F41.getDefaultValue().length());
		String F41 = NapasSettleRuleCO.F41.getField() + F41Value + NapasSettleRuleCO.F41.getDefaultValue();
		String ACQValueTmp = StringUtils.repeat(NapasSettleRuleCO.ACQ.getDefaultChar(), NapasSettleRuleCO.ACQ.getLength());
		String ACQValue = ACQValueTmp.substring(0, ACQValueTmp.length() - tctvCode.length());
		String ACQ = NapasSettleRuleCO.ACQ.getField() + ACQValue + tctvCode;
		String ISSValueTmp = StringUtils.repeat(NapasSettleRuleCO.ISS.getDefaultChar(), NapasSettleRuleCO.ISS.getLength());
		String ISSValue = ISSValueTmp.substring(0, ISSValueTmp.length() - tctvCode.length());
		String ISS = NapasSettleRuleCO.ISS.getField() + ISSValue + tctvCode;
		String MIDValueTmp = StringUtils.repeat(NapasSettleRuleCO.MID.getDefaultChar(), NapasSettleRuleCO.MID.getLength());
		String MIDValue = MIDValueTmp.substring(0, MIDValueTmp.length() - NapasSettleRuleCO.MID.getDefaultValue().length());
		String MID = NapasSettleRuleCO.MID.getField() + MIDValue + NapasSettleRuleCO.MID.getDefaultValue();
		String BNBValueTmp = StringUtils.repeat(NapasSettleRuleCO.BNB.getDefaultChar(), NapasSettleRuleCO.BNB.getLength());
		String BNBValue = BNBValueTmp.substring(0, BNBValueTmp.length() - cardNumber.substring(0, 6).length());
		String BNB = NapasSettleRuleCO.BNB.getField() + BNBValue + cardNumber.substring(0, 6);
		String F102ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F102.getDefaultChar(), NapasSettleRuleCO.F102.getLength());
		String F102Value = F102ValueTmp.substring(0, F102ValueTmp.length() - senderAccountNumber.length());
		String F102 = NapasSettleRuleCO.F102.getField() + F102Value + senderAccountNumber;
		String F103ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F103.getDefaultChar(), NapasSettleRuleCO.F103.getLength());
		String F103Value = F103ValueTmp.substring(0, F103ValueTmp.length() - cardNumber.replace("x","_").length());
		String F103 = NapasSettleRuleCO.F103.getField() + F103Value + cardNumber.replace("x","_");
		String F37ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F37.getDefaultChar(), NapasSettleRuleCO.F37.getLength());
		String F37Value = F37ValueTmp.substring(0, F37ValueTmp.length() - acquirerTransId.length());
		String F37 = NapasSettleRuleCO.F37.getField() + F37Value + acquirerTransId;
		String F38ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F38.getDefaultChar(), NapasSettleRuleCO.F38.getLength());
		String F38Value = F38ValueTmp.substring(0,
				F38ValueTmp.length() - NapasSettleRuleCO.F38.getDefaultValue().length());
		String F38 = NapasSettleRuleCO.F38.getField() + F38Value + NapasSettleRuleCO.F38.getDefaultValue();
		String TRNValueTmp = StringUtils.repeat(NapasSettleRuleCO.TRN.getDefaultChar(), NapasSettleRuleCO.TRN.getLength());
		String TRNValue = TRNValueTmp.substring(0,
				TRNValueTmp.length() - NapasSettleRuleCO.TRN.getDefaultValue().length());
		String TRN = NapasSettleRuleCO.TRN.getField() + TRNValue + NapasSettleRuleCO.TRN.getDefaultValue();
		String RRCValueTmp = StringUtils.repeat(NapasSettleRuleCO.RRC.getDefaultChar(), NapasSettleRuleCO.RRC.getLength());
		String RRCValue = RRCValueTmp.substring(0, RRCValueTmp.length() - rrcCode.length());
		String RRC = NapasSettleRuleCO.RRC.getField() + RRCValue + rrcCode;
		String RSV1ValueTmp = StringUtils.repeat(NapasSettleRuleCO.RSV1.getDefaultChar(),
				NapasSettleRuleCO.RSV1.getLength());
		dateArr = transDate.split("T")[0].substring(0, 10).replace("-", "");
		String RSV1Value1 = RSV1ValueTmp.substring(0, 20)
				+ dateArr.substring(6, 8).concat(dateArr.substring(4, 6)).concat(dateArr.substring(0, 4));
		String RSV1Value2Tmp = RSV1ValueTmp.substring(28, 68 - orderId.length());
		String RSV1Value2 = RSV1Value2Tmp + orderId;
		String RSV1Value3 = StringUtils.repeat(NapasSettleRuleCO.RSV1.getDefaultChar(), 32);
		String RSV1 = NapasSettleRuleCO.RSV1.getField() + RSV1Value1 + RSV1Value2 + RSV1Value3;
		String RSV2ValueTmp = StringUtils.repeat(NapasSettleRuleCO.RSV2.getDefaultChar(),
				NapasSettleRuleCO.RSV2.getLength());
		String RSV2Value = RSV2ValueTmp.substring(0,
				RSV2ValueTmp.length() - NapasSettleRuleCO.RSV2.getDefaultValue().length());
		String RSV2 = NapasSettleRuleCO.RSV2.getField() + RSV2Value + NapasSettleRuleCO.RSV2.getDefaultValue();
		String RSV3ValueTmp = StringUtils.repeat(NapasSettleRuleCO.RSV3.getDefaultChar(),
				NapasSettleRuleCO.RSV3.getLength());
		String RSV3Value = RSV3ValueTmp.substring(0,
				RSV3ValueTmp.length() - NapasSettleRuleCO.RSV3.getDefaultValue().length());
		String RSV3 = NapasSettleRuleCO.RSV3.getField() + RSV3Value + NapasSettleRuleCO.RSV3.getDefaultValue();
		String CSR = NapasSettleRuleCO.CSR.getField();
		String lineTmp = DR + MTI + F2 + F3 + SVC + TCC + F4 + RTA + F49 + F5 + F50 + F9 + F6 + RCA + F51 + F10 + F11
				+ F12 + F13 + F15 + F18 + F22 + F25 + F41 + ACQ + ISS + MID + BNB + F102 + F103 + F37 + F38 + TRN + RRC
				+ RSV1 + RSV2 + RSV3 + CSR;
		String line = lineTmp + sercurityHelper.hashMD5Napas(lineTmp, tctvCode);
		return line;
	}

	private String createSLLineFromNapasTrans(String cardNumber, String f2, String f3, String f4, String rta, String f5, String f11, String f12, String f13, String f15, String rsv1,
								String tctvCode, String bbb, String f37, String rrcCode, String f102, String f103) {
		String DR = NapasSettleRuleCO.DR.getField();
		String MTIValueTmp = StringUtils.repeat(NapasSettleRuleCO.MTI.getDefaultChar(), NapasSettleRuleCO.MTI.getLength());
		String MTIValue = MTIValueTmp.substring(0,
				MTIValueTmp.length() - NapasSettleRuleCO.MTI.getDefaultValue().length());
		String MTI = NapasSettleRuleCO.MTI.getField() + MTIValue + NapasSettleRuleCO.MTI.getDefaultValue();
		String F2 = NapasSettleRuleCO.F2.getField() + f2;
		String F3 = NapasSettleRuleCO.F3.getField() + f3;
		String F4 = NapasSettleRuleCO.F4.getField() + f4;
		String RTA = NapasSettleRuleCO.RTA.getField() + rta;
		String F5 = NapasSettleRuleCO.F5.getField() + f5;
		String F11 = NapasSettleRuleCO.F11.getField() + f11;
		String F12 = NapasSettleRuleCO.F12.getField() + f12;
		String F13 = NapasSettleRuleCO.F13.getField() + f13;
		String F15 = NapasSettleRuleCO.F15.getField() + f15;
		String RSV1 = NapasSettleRuleCO.RSV1.getField() + rsv1;
		String F37 = NapasSettleRuleCO.F37.getField() +  f37;
		String F102 = NapasSettleRuleCO.F102.getField() + f102;
		String F103 = NapasSettleRuleCO.F103.getField() + f103;
		String SVCValueTmp = StringUtils.repeat(NapasSettleRuleCO.SVC.getDefaultChar(), NapasSettleRuleCO.SVC.getLength());
		String SVCValue = SVCValueTmp.substring(0,
				SVCValueTmp.length() - NapasSettleRuleCO.SVC.getDefaultValue().length());
		String SVC = NapasSettleRuleCO.SVC.getField() + SVCValue + NapasSettleRuleCO.SVC.getDefaultValue();
		String TCCValueTmp = StringUtils.repeat(NapasSettleRuleCO.TCC.getDefaultChar(), NapasSettleRuleCO.TCC.getLength());
		String TCCValue = TCCValueTmp.substring(0,
				TCCValueTmp.length() - NapasSettleRuleCO.TCC.getDefaultValue().length());
		String TCC = NapasSettleRuleCO.TCC.getField() + TCCValue + NapasSettleRuleCO.TCC.getDefaultValue();
		String F49ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F49.getDefaultChar(), NapasSettleRuleCO.F49.getLength());
		String F49Value = F49ValueTmp.substring(0,
				F49ValueTmp.length() - NapasSettleRuleCO.F49.getDefaultValue().length());
		String F49 = NapasSettleRuleCO.F49.getField() + F49Value + NapasSettleRuleCO.F49.getDefaultValue();
		String F50ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F50.getDefaultChar(), NapasSettleRuleCO.F50.getLength());
		String F50Value = F50ValueTmp.substring(0,
				F50ValueTmp.length() - NapasSettleRuleCO.F50.getDefaultValue().length());
		String F50 = NapasSettleRuleCO.F50.getField() + F50Value + NapasSettleRuleCO.F50.getDefaultValue();
		String F9ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F9.getDefaultChar(), NapasSettleRuleCO.F9.getLength());
		String F9Value = F9ValueTmp.substring(0, F9ValueTmp.length() - NapasSettleRuleCO.F9.getDefaultValue().length());
		String F9 = NapasSettleRuleCO.F9.getField() + F9Value + NapasSettleRuleCO.F9.getDefaultValue();
		String F6ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F6.getDefaultChar(), NapasSettleRuleCO.F6.getLength());
		String F6Value = F6ValueTmp.substring(0, F6ValueTmp.length() - NapasSettleRuleCO.F6.getDefaultValue().length());
		String F6 = NapasSettleRuleCO.F6.getField() + F6Value + NapasSettleRuleCO.F6.getDefaultValue();
		String RCAValueTmp = StringUtils.repeat(NapasSettleRuleCO.RCA.getDefaultChar(), NapasSettleRuleCO.RCA.getLength());
		String RCAValue = RCAValueTmp.substring(0,
				RCAValueTmp.length() - NapasSettleRuleCO.RCA.getDefaultValue().length());
		String RCA = NapasSettleRuleCO.RCA.getField() + RCAValue + NapasSettleRuleCO.RCA.getDefaultValue();
		String F51ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F51.getDefaultChar(), NapasSettleRuleCO.F51.getLength());
		String F51Value = F51ValueTmp.substring(0,
				F51ValueTmp.length() - NapasSettleRuleCO.F51.getDefaultValue().length());
		String F51 = NapasSettleRuleCO.F51.getField() + F51Value + NapasSettleRuleCO.F51.getDefaultValue();
		String F10ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F10.getDefaultChar(), NapasSettleRuleCO.F10.getLength());
		String F10Value = F10ValueTmp.substring(0,
				F10ValueTmp.length() - NapasSettleRuleCO.F10.getDefaultValue().length());
		String F10 = NapasSettleRuleCO.F10.getField() + F10Value + NapasSettleRuleCO.F10.getDefaultValue();
		String F11ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F11.getDefaultChar(), NapasSettleRuleCO.F11.getLength());
		String F11Value = F11ValueTmp.substring(0,
				F11ValueTmp.length() - NapasSettleRuleCO.F11.getDefaultValue().length());
		String F18ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F18.getDefaultChar(), NapasSettleRuleCO.F18.getLength());
		String F18Value = F18ValueTmp.substring(0,
				F18ValueTmp.length() - NapasSettleRuleCO.F18.getDefaultValue().length());
		String F18 = NapasSettleRuleCO.F18.getField() + F18Value + NapasSettleRuleCO.F18.getDefaultValue();
		String F22ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F22.getDefaultChar(), NapasSettleRuleCO.F22.getLength());
		String F22Value = F22ValueTmp.substring(0,
				F22ValueTmp.length() - NapasSettleRuleCO.F22.getDefaultValue().length());
		String F22 = NapasSettleRuleCO.F22.getField() + F22Value + NapasSettleRuleCO.F22.getDefaultValue();
		String F25ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F25.getDefaultChar(), NapasSettleRuleCO.F25.getLength());
		String F25Value = F25ValueTmp.substring(0,
				F25ValueTmp.length() - NapasSettleRuleCO.F25.getDefaultValue().length());
		String F25 = NapasSettleRuleCO.F25.getField() + F25Value + NapasSettleRuleCO.F25.getDefaultValue();
		String F41ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F41.getDefaultChar(), NapasSettleRuleCO.F41.getLength());
		String F41Value = F41ValueTmp.substring(0,
				F41ValueTmp.length() - NapasSettleRuleCO.F41.getDefaultValue().length());
		String F41 = NapasSettleRuleCO.F41.getField() + F41Value + NapasSettleRuleCO.F41.getDefaultValue();
		String ACQValueTmp = StringUtils.repeat(NapasSettleRuleCO.ACQ.getDefaultChar(), NapasSettleRuleCO.ACQ.getLength());
		String ACQValue = ACQValueTmp.substring(0, ACQValueTmp.length() - tctvCode.length());
		String ACQ = NapasSettleRuleCO.ACQ.getField() + ACQValue + tctvCode;
		String ISSValueTmp = StringUtils.repeat(NapasSettleRuleCO.ISS.getDefaultChar(), NapasSettleRuleCO.ISS.getLength());
		String ISSValue = ISSValueTmp.substring(0, ISSValueTmp.length() - tctvCode.length());
		String ISS = NapasSettleRuleCO.ISS.getField() + ISSValue + tctvCode;
		String MIDValueTmp = StringUtils.repeat(NapasSettleRuleCO.MID.getDefaultChar(), NapasSettleRuleCO.MID.getLength());
		String MIDValue = MIDValueTmp.substring(0, MIDValueTmp.length() - NapasSettleRuleCO.MID.getDefaultValue().length());
		String MID = NapasSettleRuleCO.MID.getField() + MIDValue + NapasSettleRuleCO.MID.getDefaultValue();
		String BNBValueTmp = StringUtils.repeat(NapasSettleRuleCO.BNB.getDefaultChar(), NapasSettleRuleCO.BNB.getLength());
		String BNBValue = BNBValueTmp.substring(0, BNBValueTmp.length() - cardNumber.substring(0, 6).length());
		String BNB = NapasSettleRuleCO.BNB.getField() + BNBValue + cardNumber.substring(0, 6);
		String F38ValueTmp = StringUtils.repeat(NapasSettleRuleCO.F38.getDefaultChar(), NapasSettleRuleCO.F38.getLength());
		String F38Value = F38ValueTmp.substring(0,
				F38ValueTmp.length() - NapasSettleRuleCO.F38.getDefaultValue().length());
		String F38 = NapasSettleRuleCO.F38.getField() + F38Value + NapasSettleRuleCO.F38.getDefaultValue();
		String TRNValueTmp = StringUtils.repeat(NapasSettleRuleCO.TRN.getDefaultChar(), NapasSettleRuleCO.TRN.getLength());
		String TRNValue = TRNValueTmp.substring(0,
				TRNValueTmp.length() - NapasSettleRuleCO.TRN.getDefaultValue().length());
		String TRN = NapasSettleRuleCO.TRN.getField() + TRNValue + NapasSettleRuleCO.TRN.getDefaultValue();
		String RRCValueTmp = StringUtils.repeat(NapasSettleRuleCO.RRC.getDefaultChar(), NapasSettleRuleCO.RRC.getLength());
		String RRCValue = RRCValueTmp.substring(0, RRCValueTmp.length() - rrcCode.length());
		String RRC = NapasSettleRuleCO.RRC.getField() + RRCValue + rrcCode;
		String RSV2ValueTmp = StringUtils.repeat(NapasSettleRuleCO.RSV2.getDefaultChar(),
				NapasSettleRuleCO.RSV2.getLength());
		String RSV2Value = RSV2ValueTmp.substring(0,
				RSV2ValueTmp.length() - NapasSettleRuleCO.RSV2.getDefaultValue().length());
		String RSV2 = NapasSettleRuleCO.RSV2.getField() + RSV2Value + NapasSettleRuleCO.RSV2.getDefaultValue();
		String RSV3ValueTmp = StringUtils.repeat(NapasSettleRuleCO.RSV3.getDefaultChar(),
				NapasSettleRuleCO.RSV3.getLength());
		String RSV3Value = RSV3ValueTmp.substring(0,
				RSV3ValueTmp.length() - NapasSettleRuleCO.RSV3.getDefaultValue().length());
		String RSV3 = NapasSettleRuleCO.RSV3.getField() + RSV3Value + NapasSettleRuleCO.RSV3.getDefaultValue();
		String CSR = NapasSettleRuleCO.CSR.getField();
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

	private void updateTransactionList(List<String> settleDates, String status) {
		for (String settleDate : settleDates) {
			SettlementTransaction trans = settleRepo.findByPartnerAndDomainAndSettleDate(SettleConstants.PARTNER_NAPAS,
					SettleConstants.CASH_OUT, settleDate);
			trans.setStatus(status);
			commonSettleService.update(trans);
		}
	}

	private String convertDateFormat(String dateFormat, String currentFormat, String formatChanging) {
		try {
			DateFormat currentDate = new SimpleDateFormat(currentFormat);
			DateFormat dateChanging = new SimpleDateFormat(formatChanging);
			Date tempDate = currentDate.parse(dateFormat);
			return dateChanging.format(tempDate);
		}catch (ParseException ex){
			throw new RuntimeInternalServerException("Date Format Wrong");
		}
	}

}
