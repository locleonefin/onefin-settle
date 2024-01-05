package com.onefin.ewallet.settlement.service.vietin.linkbank;

import com.onefin.ewallet.common.base.constants.OneFinConstants;
import com.onefin.ewallet.common.base.service.BaseHelper;
import com.onefin.ewallet.common.base.service.RestTemplateHelper;
import com.onefin.ewallet.common.domain.bank.common.LinkBankTransaction;
import com.onefin.ewallet.common.domain.settlement.SettlementTransaction;
import com.onefin.ewallet.common.quartz.entity.SchedulerJobInfo;
import com.onefin.ewallet.common.utility.date.DateTimeHelper;
import com.onefin.ewallet.settlement.common.SettleConstants;
import com.onefin.ewallet.settlement.controller.SettleJobController;
import com.onefin.ewallet.settlement.repository.SettleTransRepository;
import com.onefin.ewallet.settlement.repository.LinkBankTransRepo;
import com.onefin.ewallet.settlement.service.ConfigLoader;
import com.onefin.ewallet.settlement.service.MinioService;
import com.onefin.ewallet.settlement.service.SettleService;
import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Component
@DisallowConcurrentExecution
public class VietinLinkBankCheckDispute extends QuartzJobBean {

	private static final Logger LOGGER = LoggerFactory.getLogger(VietinLinkBankCheckDispute.class);

	@Autowired
	private ConfigLoader configLoader;

	@Autowired
	private DateTimeHelper dateHelper;

	@Autowired
	@Qualifier("SftpVietinLinkBankRemoteFileTemplate")
	private SftpRemoteFileTemplate sftpVietinGateway;

	@Autowired
	private SettleTransRepository settleRepo;

	@Autowired
	private SettleService commonSettleService;

	@Autowired
	private MinioService minioService;

	@Autowired
	private SettleJobController settleJobController;

	@Autowired
	private LinkBankTransRepo<?> transRepository;

	@Autowired
	private Environment env;

	@Autowired
	private RestTemplateHelper restTemplateHelper;

	@Autowired
	private BaseHelper baseHelper;

	/*
	 * *************************DISPUTE FILE PROCESSING*****************************
	 */

	/**
	 * @throws ParseException
	 * @throws IOException    After submitted transaction file to SFTP, VietinBank
	 *                        will process then return back dispute file. OF get and process
	 */
	@Override
	protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
		LOGGER.info("Start Settlement Dispute {} {}", SettleConstants.PARTNER_VIETINBANK, SettleConstants.LINK_BANK);
		DateTime currentTime = dateHelper.currentDateTime(OneFinConstants.HO_CHI_MINH_TIME_ZONE);
		try {
			taskEwalletVietinDisputeSettlement(currentTime, false);
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
		}
	}

	public void ewalletVietinDisputeSettlementManually(String settleDate) throws Exception {
		DateTime currentTime = dateHelper.parseDate(settleDate, SettleConstants.HO_CHI_MINH_TIME_ZONE,
				SettleConstants.DATE_FORMAT_ddMMyyyy);
		// Reset to pending status
		LOGGER.info("{} {} Start Settlement Dispute", SettleConstants.PARTNER_VIETINBANK, SettleConstants.LINK_BANK);
		SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
		String date = formatter.format(currentTime.toLocalDateTime().toDate());
		LOGGER.info("{} {} Dispute Date {}", SettleConstants.PARTNER_VIETINBANK, SettleConstants.LINK_BANK, date);
		SettlementTransaction trans = settleRepo.findByPartnerAndDomainAndSettleDate(SettleConstants.PARTNER_VIETINBANK,
				SettleConstants.LINK_BANK, date);
		if (trans != null) {
			LOGGER.info("{} {} Transaction found", SettleConstants.PARTNER_VIETINBANK, SettleConstants.LINK_BANK);
			taskEwalletVietinDisputeSettlement(currentTime, true);
		} else {
			LOGGER.info("{} {} Transaction NOT found", SettleConstants.PARTNER_VIETINBANK, SettleConstants.LINK_BANK);
		}
	}
	/*
	 * *************************DISPUTE FILE PROCESSING*****************************
	 */

	public void taskEwalletVietinDisputeSettlement(DateTime currentTime, boolean ignoreSettleBefore) throws Exception {
		LOGGER.info("{} {} Start process DISPUTE file {}", SettleConstants.PARTNER_VIETINBANK,
				SettleConstants.LINK_BANK, currentTime);
		DateTime previousTime = currentTime.minusDays(1);
		Date previousDate = previousTime.toLocalDateTime().toDate();
		SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
		String fileName = formatter.format(previousDate);
		String disputeFileName = fileName + "_TRANS_DISPUTE_" + configLoader.getProviderNameVietinbankLinkBank()
				+ ".txt";
		LOGGER.info("{} {} Dispute file name {}", SettleConstants.PARTNER_VIETINBANK, SettleConstants.LINK_BANK,
				disputeFileName);
		SettlementTransaction trans = settleRepo.findByPartnerAndDomainAndSettleDateAndStatus(
				SettleConstants.PARTNER_VIETINBANK, SettleConstants.LINK_BANK,
				formatter.format(currentTime.toLocalDateTime().toDate()), SettleConstants.SETTLEMENT_PENDING_T0);
		if (trans == null) {
			LOGGER.info("{} {} No settle dispute transaction found", SettleConstants.PARTNER_VIETINBANK,
					SettleConstants.LINK_BANK);
			return;
		}
		boolean vtSentDisputeFile = true;
		try {
			sftpVietinGateway.get(configLoader.getSftpVietinbankLinkBankDirectoryRemoteIn() + disputeFileName,
					stream -> {
						FileCopyUtils.copy(stream, new FileOutputStream(new File(
								configLoader.getSftpVietinbankLinkBankDirectoryStoreFile() + disputeFileName)));
						// TODO Update transaction
						File file = new File(
								configLoader.getSftpVietinbankLinkBankDirectoryStoreFile() + disputeFileName);
						trans.setStatus(SettleConstants.SETTLEMENT_SUCCESS);
						LOGGER.info("{} {} Settlement Dispute file found {}", SettleConstants.PARTNER_VIETINBANK,
								SettleConstants.LINK_BANK, disputeFileName);
						try {
							int failTranx = readDisputeFile(file.getCanonicalPath());
							LOGGER.info("{} {} Read dispute file {}", SettleConstants.PARTNER_VIETINBANK,
									SettleConstants.LINK_BANK, failTranx);
							// Upload to minio server
							LOGGER.info("{} {} Start upload dispute file to MINIO", SettleConstants.PARTNER_VIETINBANK,
									SettleConstants.LINK_BANK);
							minioService.uploadFile(configLoader.getBaseBucket(),
									configLoader.getSettleVietinLinkBankMinioDefaultFolder() + dateHelper
											.parseDateString(currentTime, SettleConstants.DATE_FORMAT_ddMMyyyy) + "/"
											+ file.getName(),
									file, "text/plain");
							LOGGER.info("{} {} End upload dispute file to MINIO", SettleConstants.PARTNER_VIETINBANK,
									SettleConstants.LINK_BANK);
							if (!trans.getFile().contains(file.getName())) {
								trans.getFile().add(file.getName());
							}
							commonSettleService.update(trans);
							// Send email
//                            LOGGER.info("{} {} Send mail to report dispute file", SettleConstants.PARTNER_VIETINBANK,
//                                    SettleConstants.LINK_BANK);
//                            String subject = null;
							if (failTranx == 0) {
								// Settle completed => paused job
//                                subject = VIETINBANK_LINKBANK_SUBJECT_NO_SETTLE;
								SchedulerJobInfo jobTmp = new SchedulerJobInfo();
								jobTmp.setJobClass(VietinLinkBankCheckDispute.class.getName());
								settleJobController.pauseJob(jobTmp);
							}
//							else {
//                                subject = VIETINBANK_LINKBANK_SUBJECT_SETTLE;
//                            }
//							commonSettleService.settleCompleted(currentTime.toString(), configLoader.getBaseBucket(),
//									configLoader.getSettleVietinLinkBankMinioDefaultFolder() + dateHelper
//											.parseDateString(currentTime, SettleConstants.DATE_FORMAT_ddMMyyyy) + "/"
//											+ file.getName(),
//									subject);
							file.delete();
							LOGGER.info("{} {} End process dispute file", SettleConstants.PARTNER_VIETINBANK,
									SettleConstants.LINK_BANK);
						} catch (Exception e) {
							LOGGER.error("{} {} {}", SettleConstants.PARTNER_VIETINBANK, SettleConstants.LINK_BANK,
									"Error in processing dispute file", e);
//                            try {
//                                commonSettleService.reportIssue(currentTime.toString(),
//                                        new Throwable().getStackTrace()[0].getMethodName(),
//                                        SettleConstants.ERROR_DOWNLOAD_PARTNER_SETTLE_FILE,
//                                        VIETINBANK_LINKBANK_ISSUE_SUBJECT);
//                            } catch (Exception e1) {
//                                LOGGER.error("{} {} Upload dispute file error {} {}",
//                                        SettleConstants.PARTNER_VIETINBANK, SettleConstants.LINK_BANK, previousDate,
//                                        e1);
//                            }
						}
					});
		} catch (Exception e) {
			LOGGER.warn("{} {} Not Found Settlement Dispute File {}", SettleConstants.PARTNER_VIETINBANK,
					SettleConstants.LINK_BANK, previousDate, e);
			vtSentDisputeFile = false;
		}
		String[] settleCompletedBefore = env.getProperty("settlement.vietin.link_bank.settleCompletedBefore").split(":");
		LocalTime settleCompletedBeforeTime = new LocalTime(Integer.parseInt(settleCompletedBefore[0]), Integer.parseInt(settleCompletedBefore[1]), Integer.parseInt(settleCompletedBefore[2]));
		LocalTime currentLocalTime = LocalTime.now();
		if (currentLocalTime.compareTo(settleCompletedBeforeTime) == -1 || ignoreSettleBefore == true) {
			LOGGER.info("{} {} Find dispute file in backup {}", SettleConstants.PARTNER_VIETINBANK,
					SettleConstants.LINK_BANK, previousDate);
			String trxFile = fileName + "_TRANS_" + configLoader.getProviderNameVietinbankLinkBank() + ".txt";
			boolean finalVtSentDisputeFile = vtSentDisputeFile;
			sftpVietinGateway.get(configLoader.getSftpVietinbankLinkBankDirectoryRemoteBackup() + trxFile, stream -> {
				List<LinkBankTransaction> successTrx = transRepository.findSettleTransaction(
						configLoader.getVietinSettleAction(), OneFinConstants.TRANS_SUCCESS, formatter.format(previousDate));
				if (successTrx.size() > 0 && !finalVtSentDisputeFile) {
					trans.setStatus(SettleConstants.SETTLEMENT_ERROR);
					try {
						LOGGER.error("{} {} {}", SettleConstants.PARTNER_VIETINBANK, SettleConstants.LINK_BANK,
								"Vietin not send DISPUTE file");
						commonSettleService.update(trans);
						SchedulerJobInfo jobTmp = new SchedulerJobInfo();
						jobTmp.setJobClass(VietinLinkBankCheckDispute.class.getName());
						settleJobController.pauseJob(jobTmp);
					} catch (Exception e) {
						LOGGER.error(e.getMessage(), e);
					}
				} else {
					trans.setStatus(SettleConstants.SETTLEMENT_SUCCESS);
					try {
						commonSettleService.update(trans);
						SchedulerJobInfo jobTmp = new SchedulerJobInfo();
						jobTmp.setJobClass(VietinLinkBankCheckDispute.class.getName());
						settleJobController.pauseJob(jobTmp);
					} catch (Exception e) {
						LOGGER.error(e.getMessage(), e);
					}
				}
			});
		} else {
			LOGGER.error("{} {} {}", SettleConstants.PARTNER_VIETINBANK, SettleConstants.LINK_BANK,
					"Vietin not access to SFTP");
			SchedulerJobInfo jobTmp = new SchedulerJobInfo();
			jobTmp.setJobClass(VietinLinkBankCheckDispute.class.getName());
			settleJobController.pauseJob(jobTmp);
		}
	}

	private int readDisputeFile(String disputeFile) throws Exception {
		FileReader reader = null;
		int failTranx = 0;
		BufferedReader br = null;
		try {
			reader = new FileReader(disputeFile);
			br = new BufferedReader(reader);
			// read line by line
			String line;
			br.readLine();
			while ((line = br.readLine()) != null) {
				String[] arr = line.split("\\|");
				if (!arr[1].equals(configLoader.getRcReconcileOKVietinbank())
						&& arr[0].equals(configLoader.getRecordTypeDetailVietinbankLinkBank())) {
					// Invalid transaction
					failTranx++;
					try {
						LinkBankTransaction trx = transRepository.findByRequestId(arr[5]);
						updateVietinLinkBankTrans(trx, SettleConstants.TRANS_RECONCILE_02);
					} catch (Exception e) {
						LOGGER.error("Can't find vietin link bank transaction {}", arr[5], e);
					}
				} else if (arr[0].equals(configLoader.getRecordTypeDetailVietinbankLinkBank())) {
					// Mark record as settle
					LinkBankTransaction trx = transRepository.findByRequestId(arr[5]);
					updateVietinLinkBankTrans(trx, SettleConstants.TRANS_SETTLED);
				}
			}
		} catch (FileNotFoundException e) {
			LOGGER.error(e.getMessage(), e);
		}
		br.close();
		return failTranx;
	}

	private ResponseEntity updateVietinLinkBankTrans(LinkBankTransaction trans, String transStatus) {
		try {
			String url = env.getProperty("conn-service.bank.host") + env.getProperty("conn-service.bank.uri.modifySettleTrx");
			LOGGER.info("== Send update trx to conn-bank {} - url: {}", trans, url);
			HttpHeaders headers = new HttpHeaders();
			headers.setAccept(Collections.singletonList(MediaType.ALL));
			headers.setContentType(MediaType.APPLICATION_JSON);
			HashMap<String, String> headersMap = new HashMap<String, String>();
			for (String header : headers.keySet()) {
				headersMap.put(header, headers.getFirst(header));
			}
			ResponseEntity<LinkBankTransaction> responseEntity = restTemplateHelper.put(url,
					MediaType.APPLICATION_JSON_VALUE, headersMap, Arrays.asList(SettleConstants.PARTNER_VIETINBANK, SettleConstants.LINK_BANK, trans.getRequestId()), new HashMap<String, String>() {{
						put("transStatus", transStatus);
					}},
					null, null, new ParameterizedTypeReference<LinkBankTransaction>() {
					});
			LOGGER.info("== Success receive response from conn-bank {}", responseEntity.getBody());
			return responseEntity;
		} catch (Exception e) {
			LOGGER.error("Update transaction to Settled ERROR, Please update manually", e);
			return null;
		}
	}
}
