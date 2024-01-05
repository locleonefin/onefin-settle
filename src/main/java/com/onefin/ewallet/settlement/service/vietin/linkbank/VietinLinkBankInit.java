package com.onefin.ewallet.settlement.service.vietin.linkbank;

import com.onefin.ewallet.common.base.constants.OneFinConstants;
import com.onefin.ewallet.common.domain.bank.common.LinkBankTransaction;
import com.onefin.ewallet.common.domain.settlement.SettlementTransaction;
import com.onefin.ewallet.common.utility.date.DateTimeHelper;
import com.onefin.ewallet.common.utility.sercurity.SercurityHelper;
import com.onefin.ewallet.settlement.common.SettleConstants;
import com.onefin.ewallet.settlement.common.SettleHelper;
import com.onefin.ewallet.settlement.config.SFTPVietinLinkBankwalletIntegration.UploadVietinLinkBankGateway;
import com.onefin.ewallet.settlement.repository.SettleTransRepository;
import com.onefin.ewallet.settlement.repository.LinkBankTransRepo;
import com.onefin.ewallet.settlement.service.ConfigLoader;
import com.onefin.ewallet.settlement.service.MinioService;
import com.onefin.ewallet.settlement.service.SettleService;
import org.joda.time.DateTime;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Component
@DisallowConcurrentExecution
public class VietinLinkBankInit extends QuartzJobBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(VietinLinkBankInit.class);

//	private static final String VIETINBANK_LINKBANK_ISSUE_SUBJECT = "[VietinBank - LinkBank] - Settle error";

    @Autowired
    private LinkBankTransRepo<?> transRepository;

    @Autowired
    private ConfigLoader configLoader;

    @Autowired
    private UploadVietinLinkBankGateway gateway;

    @Autowired
    private DateTimeHelper dateHelper;

    @Autowired
    private SercurityHelper sercurityHelper;

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
    private SettleHelper settleHelper;

    /*
     * *************************TRANS FILE PROCESSING*****************************
     */

    /**
     * @throws Exception Scheduled at scheduled time, OneFin creates settlement file included success transaction of previous day and send to VietinBank
     */
    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        DateTime currentTime = dateHelper.currentDateTime(OneFinConstants.HO_CHI_MINH_TIME_ZONE);
        try {
            taskEwalletVietinSettlement(currentTime);
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    public void scheduleTaskEwalletVietinSettlementManually(String settleDate) throws Exception {
        DateTime currentTime = dateHelper.parseDate(settleDate, SettleConstants.HO_CHI_MINH_TIME_ZONE,
                SettleConstants.DATE_FORMAT_ddMMyyyy);
        taskEwalletVietinSettlement(currentTime);
    }
    /*
     * *************************TRANS FILE PROCESSING*****************************
     */

    public void taskEwalletVietinSettlement(DateTime currentTime) throws Exception {
        LOGGER.info("Start Settlement {} {}", SettleConstants.PARTNER_VIETINBANK, SettleConstants.LINK_BANK);
        DateTime previousTime = currentTime.minusDays(1);
        Date currentDate = currentTime.toLocalDateTime().toDate();
        Date previousDate = previousTime.toLocalDateTime().toDate();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
        LOGGER.info("{} {} Current date {}, Previous date {}", SettleConstants.PARTNER_VIETINBANK, SettleConstants.LINK_BANK, currentDate, previousDate);
        BufferedWriter writer = null;

        // Find all SUCCESS transaction of previous date
        List<LinkBankTransaction> result = transRepository.findSettleTransaction(
                configLoader.getVietinSettleAction(), OneFinConstants.TRANS_SUCCESS, formatter.format(previousDate));
        LOGGER.info("{} {} Number Settlement Transaction: {}", SettleConstants.PARTNER_VIETINBANK, SettleConstants.LINK_BANK, result.size());
        File file = null;
        SettlementTransaction trans = null;
        // Process file to upload FTP folder
        try {
            LOGGER.info("{} {} Start prepare file to upload Vietin FTP server", SettleConstants.PARTNER_VIETINBANK, SettleConstants.LINK_BANK);
            String fileName = formatter.format(previousDate);

            // Create transaction
            trans = settleRepo.findByPartnerAndDomainAndSettleDate(SettleConstants.PARTNER_VIETINBANK,
                    SettleConstants.LINK_BANK, formatter.format(currentDate));
            LOGGER.info("{} {} Settle Transaction details: {}", SettleConstants.PARTNER_VIETINBANK, SettleConstants.LINK_BANK, trans);
            if (trans == null) {
                LOGGER.info("{} {} Transaction exist", SettleConstants.PARTNER_VIETINBANK, SettleConstants.LINK_BANK);
                trans = (SettlementTransaction) settleHelper.createModelStructure(new SettlementTransaction());
                trans.getSettleKey().setDomain(SettleConstants.LINK_BANK);
                trans.getSettleKey().setPartner(SettleConstants.PARTNER_VIETINBANK);
                trans.getSettleKey().setSettleDate(formatter.format(currentDate));
                trans.setStatus(SettleConstants.SETTLEMENT_PROCESSING);
                commonSettleService.save(trans);
            } else {
                LOGGER.info("{} {} Transaction not exist", SettleConstants.PARTNER_VIETINBANK, SettleConstants.LINK_BANK);
                trans.setStatus(SettleConstants.SETTLEMENT_PROCESSING);
                commonSettleService.update(trans);
            }

            file = new File(configLoader.getSftpVietinbankLinkBankDirectoryStoreFile() + fileName + "_TRANS_"
                    + configLoader.getProviderNameVietinbankLinkBank() + ".txt");
            LOGGER.info("{} {} Settle file name {}", SettleConstants.PARTNER_VIETINBANK, SettleConstants.LINK_BANK, file.getName());
            LOGGER.info("{} {} File location: {}", SettleConstants.PARTNER_VIETINBANK, SettleConstants.LINK_BANK, file.getCanonicalPath());
            writer = new BufferedWriter(new FileWriter(file));
            writer.write(
                    "RecordType,RcReconcile,MsgType,CurCode,Amount,TranId,RefundId,TranDate,MerchantId,BankTrxSeq,BankResponseCode,CardNumber,Checksum");
            writer.newLine();
            // Write record data
            LOGGER.info("{} {} Start write record to file", SettleConstants.PARTNER_VIETINBANK, SettleConstants.LINK_BANK);
            for (LinkBankTransaction tmp : result) {
                String recordType = configLoader.getRecordTypeDetailVietinbankLinkBank();
                String rcReconcile = configLoader.getRcReconcileOKVietinbank();
                String msgType = "";
                if (configLoader.getVietinSettleActionTopUp().contains(tmp.getApiOperation())) {
                    msgType = configLoader.getMsgTypePaymentVietinbank();
                }
                if (configLoader.getVietinSettleActionWithdraw().contains(tmp.getApiOperation())) {
                    msgType = configLoader.getMsgTypeCashoutVietinbank();
                }
                if (configLoader.getVietinSettleActionRefund().contains(tmp.getApiOperation())) {
                    msgType = configLoader.getMsgTypeRefundVietinbank();
                }
                String curCode = StringUtils.isEmpty(tmp.getCurrency()) ? OneFinConstants.CURRENCY_VND
                        : tmp.getCurrency();
                String[] amountTmp = tmp.getAmount().toString().split("\\.");
                String amount = "";
                if (Integer.parseInt(amountTmp[1]) == 0) {
                    amount = amountTmp[0];
                } else {
                    amount = tmp.getAmount().toString();
                }
                String tranId = tmp.getRequestId();
                String refundId = tmp.getRefundId() == null ? "" : tmp.getRefundId();
                SimpleDateFormat formatter1 = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
//                SimpleDateFormat formatter2 = new SimpleDateFormat("yyyyMMddHHmmss");
                Date tranDateTmp = tmp.getTransDate();
                String tranDate = formatter1.format(tranDateTmp);
                String merchantId = tmp.getMerchantId();
                String bankTrxSeq = tmp.getBankTransactionId() == null ? "" : tmp.getBankTransactionId();
                String bankResponseCode = tmp.getBankStatusCode() == null ? "" : tmp.getBankStatusCode();
                // String cardNumber = tmp.getToken();
                String cardNumber = "";
                String checkSumInput = String.format("%s%s%s%s%s%s%s%s%s%s%s%s%s", recordType, rcReconcile, msgType,
                        curCode, amount, tranId, refundId, tranDate, merchantId, bankTrxSeq, bankResponseCode,
                        cardNumber, configLoader.getPrivateKeyVietinbankLinkBankChecksum());
                String checksum = sercurityHelper.MD5Hashing(checkSumInput, true);
                String newRecord = String.format("%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s", recordType, rcReconcile,
                        msgType, curCode, amount, tranId, refundId, tranDate, merchantId, bankTrxSeq, bankResponseCode,
                        cardNumber, checksum);
                writer.write(newRecord);
                writer.newLine();
            }
            LOGGER.info("{} {} End write record to file", SettleConstants.PARTNER_VIETINBANK, SettleConstants.LINK_BANK);
            // Write end file
            String endRecordType = configLoader.getRecordTypeEndVietinbank();
            String recordNumber = String.valueOf(result.size());
            String createUser = configLoader.getSettleVietinDefaultExecuteUser();
            String createFileTime = dateHelper.currentDateString(SettleConstants.HO_CHI_MINH_TIME_ZONE,
                    SettleConstants.DATE_FORMAT_ddSMMSyyyy_HHTDmmTDss);
            String checkSumInput = String.format("%s%s%s", createFileTime, recordNumber,
                    configLoader.getPrivateKeyVietinbankLinkBankChecksum());
            String checksum = sercurityHelper.MD5Hashing(checkSumInput, true);
            String newRecord = String.format("%s|%s|%s|%s|%s", endRecordType, recordNumber, createUser, createFileTime,
                    checksum);
            writer.write(newRecord);
            trans.setStatus(SettleConstants.SETTLEMENT_PENDING_T0);
        } catch (Exception e) {
            trans.setStatus(SettleConstants.SETTLEMENT_ERROR);
            LOGGER.error("{} {} {}", SettleConstants.PARTNER_VIETINBANK, SettleConstants.LINK_BANK, SettleConstants.ERROR_PREPARE_SETTLE_FILE, e);
//			commonSettleService.reportIssue(currentDate.toString(), new Throwable().getStackTrace()[0].getMethodName(),
//					SettleConstants.ERROR_PREPARE_SETTLE_FILE, VIETINBANK_LINKBANK_ISSUE_SUBJECT);
        } finally {
            try {
                writer.close();
                LOGGER.info("{} {} End prepare settlement file", SettleConstants.PARTNER_VIETINBANK, SettleConstants.LINK_BANK);
                LOGGER.info("{} {} Start upload settle file to SFTP", SettleConstants.PARTNER_VIETINBANK, SettleConstants.LINK_BANK);
                gateway.upload(file);
                if (!trans.getFile().contains(file.getName())) {
                    trans.getFile().add(file.getName());
                }
                LOGGER.info("{} {} End upload settle file to SFTP", SettleConstants.PARTNER_VIETINBANK, SettleConstants.LINK_BANK);
                commonSettleService.update(trans);
                LOGGER.info("{} {} Start upload settle file to MINIO", SettleConstants.PARTNER_VIETINBANK, SettleConstants.LINK_BANK);
                minioService.uploadFile(configLoader.getBaseBucket(),
                        configLoader.getSettleVietinLinkBankMinioDefaultFolder()
                                + dateHelper.parseDateString(currentTime, SettleConstants.DATE_FORMAT_ddMMyyyy) + "/"
                                + file.getName(),
                        file, "text/plain");
                LOGGER.info("{} {} END upload settle file to MINIO", SettleConstants.PARTNER_VIETINBANK, SettleConstants.LINK_BANK);
                file.delete();
            } catch (Exception e) {
                trans.setStatus(SettleConstants.SETTLEMENT_ERROR);
                commonSettleService.update(trans);
                LOGGER.error("{} {} {}", SettleConstants.PARTNER_VIETINBANK, SettleConstants.LINK_BANK, SettleConstants.ERROR_UPLOAD_SETTLE_FILE, e);
//				commonSettleService.reportIssue(currentDate.toString(),
//						new Throwable().getStackTrace()[0].getMethodName(), SettleConstants.ERROR_UPLOAD_SETTLE_FILE,
//						VIETINBANK_LINKBANK_ISSUE_SUBJECT);
            }
        }
    }
}
