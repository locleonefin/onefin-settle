package com.onefin.ewallet.settlement.service.vietin.virtualAcct;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onefin.ewallet.common.base.constants.OneFinConstants;
import com.onefin.ewallet.common.domain.bank.vietin.VietinNotifyTransTable;
import com.onefin.ewallet.common.domain.settlement.SettlementTransaction;
import com.onefin.ewallet.common.quartz.entity.SchedulerJobInfo;
import com.onefin.ewallet.common.utility.date.DateTimeHelper;
import com.onefin.ewallet.common.utility.sercurity.SercurityHelper;
import com.onefin.ewallet.settlement.common.SettleConstants;
import com.onefin.ewallet.settlement.common.SettleHelper;
import com.onefin.ewallet.settlement.config.SFTPVietinVirtualAcctIntegration;
import com.onefin.ewallet.settlement.controller.SettleJobController;
import com.onefin.ewallet.settlement.dto.VietinVirtualAcctDetail;
import com.onefin.ewallet.settlement.dto.VietinVirtualAcctDetailOut;
import com.onefin.ewallet.settlement.repository.SettleTransRepository;
import com.onefin.ewallet.settlement.repository.VietinNotifyTransTableRepo;
import com.onefin.ewallet.settlement.service.ConfigLoader;
import com.onefin.ewallet.settlement.service.MinioService;
import com.onefin.ewallet.settlement.service.SettleService;
import com.opencsv.bean.CsvToBeanBuilder;
import org.apache.commons.io.input.BOMInputStream;
import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.modelmapper.ModelMapper;
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
import org.springframework.util.FileCopyUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@DisallowConcurrentExecution
public class VietinVirtualAcctSv extends QuartzJobBean {

  private static final Logger LOGGER = LoggerFactory.getLogger(VietinVirtualAcctSv.class);

  // Vietin bank transfer error code
  private static final String VTB_BT_SUCCESS_CODE = "00";
  private static final String VTB_BT_PENDING_CODE = "01";

  @Autowired
  private ConfigLoader configLoader;

  @Autowired
  private DateTimeHelper dateHelper;

  @Autowired
  @Qualifier("SftpVietinVirtualAcctRemoteFileTemplate")
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
  private SettleHelper settleHelper;

  @Autowired
  private SercurityHelper sercurityHelper;

  @Autowired
  private VietinNotifyTransTableRepo vietinNotifyTransTableRepo;

  @Autowired
  private SFTPVietinVirtualAcctIntegration.UploadVietinVirtualAcctGateway gateway;

  @Autowired
  private ModelMapper modelMapper;

  /**
   * Check disbursement file from Vietin
   *
   * @param context
   * @throws JobExecutionException
   */
  @Override
  protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
    LOGGER.info("{} {} Start Settlement VirtualAcct In", SettleConstants.PARTNER_VIETINBANK, SettleConstants.VTB_VIRTUAL_ACCT);
    DateTime currentTime = dateHelper.currentDateTime(OneFinConstants.HO_CHI_MINH_TIME_ZONE);
    DateTime settleDate = currentTime.minusDays(1);
    try {
      taskEwalletVietinVirtualAcctSettlement(settleDate, false);
    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

  public void ewalletVietinVirtualAcctSettlementManually(DateTime settleDate) {
    try {
      taskEwalletVietinVirtualAcctSettlement(settleDate, true);
    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

  public void taskEwalletVietinVirtualAcctSettlement(DateTime settleTime, boolean ignoreSettleBefore) throws Exception {
    LOGGER.info("{} {} Start process VirtualAcct in {}, ignoreSettleBefore: {}", SettleConstants.PARTNER_VIETINBANK, SettleConstants.VTB_VIRTUAL_ACCT, settleTime, ignoreSettleBefore);
    SimpleDateFormat vietinDateFormat = new SimpleDateFormat(SettleConstants.DATE_FORMAT_yyyyMMDD);
    String vietinSettleDateFormat = vietinDateFormat.format(settleTime.toDate());
    String vietinVirtualAcctFileName = String.format("%s_%s_THUHOTKAO_IN.txt", vietinDateFormat.format(settleTime.toDate()), configLoader.getSettlementVietinVirtualAcctProviderId());
    LOGGER.info("{} {} VirtualAcct file name {}", SettleConstants.PARTNER_VIETINBANK, SettleConstants.VTB_VIRTUAL_ACCT, vietinVirtualAcctFileName);

    SettlementTransaction trans = settleRepo.findByPartnerAndDomainAndSettleDateAndStatus(
      SettleConstants.PARTNER_VIETINBANK,
      SettleConstants.VTB_VIRTUAL_ACCT,
      vietinDateFormat.format(settleTime.toLocalDateTime().toDate()),
      SettleConstants.SETTLEMENT_PENDING_T0
    );
    SettlementTransaction transExist = settleRepo.findByPartnerAndDomainAndSettleDate(
      SettleConstants.PARTNER_VIETINBANK,
      SettleConstants.VTB_VIRTUAL_ACCT,
      vietinDateFormat.format(settleTime.toLocalDateTime().toDate())
    );
    if (transExist == null) {
      LOGGER.info("{} {} Transaction not exist", SettleConstants.PARTNER_VIETINBANK, SettleConstants.VTB_VIRTUAL_ACCT);
      trans = (SettlementTransaction) settleHelper.createModelStructure(new SettlementTransaction());
      trans.getSettleKey().setDomain(SettleConstants.VTB_VIRTUAL_ACCT);
      trans.getSettleKey().setPartner(SettleConstants.PARTNER_VIETINBANK);
      trans.getSettleKey().setSettleDate(vietinDateFormat.format(settleTime.toDate()));
      trans.setStatus(SettleConstants.SETTLEMENT_PENDING_T0);
      commonSettleService.save(trans);
    }
    LOGGER.info("{} {} Try to get file in sftp", SettleConstants.PARTNER_VIETINBANK, SettleConstants.VTB_VIRTUAL_ACCT);
    try {
      SettlementTransaction finalTrans = trans;
      String remotePath = configLoader.getSftpVietinbankVirtualAcctDirectoryRemoteIn() + vietinVirtualAcctFileName;
      sftpVietinGateway.get(
        remotePath,
        stream -> {
          LOGGER.info("{} {} Get file in sftp success", SettleConstants.PARTNER_VIETINBANK, SettleConstants.VTB_VIRTUAL_ACCT);
          // Vietin sent file to sftp
          // Copy file to local
          String settlementFileLocal = configLoader.getSftpVietinbankVirtualAcctDirectoryStoreFile() + vietinVirtualAcctFileName;
          FileCopyUtils.copy(stream, Files.newOutputStream(Paths.get(settlementFileLocal)));
          // Init file
          File file = new File(settlementFileLocal);
          // Upload vietin file to minio
          minioService.uploadFile(
            configLoader.getBaseBucket(),
            configLoader.getSettleVietinVirtualAcctMinioDefaultFolder() + dateHelper
              .parseDateString(settleTime, SettleConstants.DATE_FORMAT_ddMMyyyy) + "/"
              + file.getName(),
            file,
            "text/plain"
          );
          // add file name if finalTrans list files don't have
          if (!finalTrans.getFile().contains(file.getName())) {
            finalTrans.getFile().add(file.getName());
          }
          // Process Vietin file
          try {
            List<VietinVirtualAcctDetailOut> reconcile = reconcileProcess(file.getCanonicalPath(), vietinSettleDateFormat);
            sendFile2Vietin(reconcile, finalTrans, settleTime);

            commonSettleService.update(finalTrans);
            file.delete();
            // Settle completed => paused job
            SchedulerJobInfo jobTmp = new SchedulerJobInfo();
            jobTmp.setJobClass(VietinVirtualAcctSv.class.getName());
            settleJobController.pauseJob(jobTmp);
            LOGGER.info("{} {} End process virtualAcct file", SettleConstants.PARTNER_VIETINBANK, SettleConstants.VTB_VIRTUAL_ACCT);
          } catch (Exception e) {
            LOGGER.error("{} {} {}", SettleConstants.PARTNER_VIETINBANK, SettleConstants.VTB_VIRTUAL_ACCT,
              "Error in processing virtualAcct file", e);
          }
        }
      );
    } catch (Exception e) {
      LOGGER.warn("{} {} Not Found Settlement VirtualAcct File {}", SettleConstants.PARTNER_VIETINBANK,
        SettleConstants.VTB_VIRTUAL_ACCT, vietinVirtualAcctFileName, e);
    }
    String[] settleCompletedBefore = configLoader.getSettlementVietinVirtualAcctSettleCompletedBefore().split(":");
    LocalTime settleCompletedBeforeTime = new LocalTime(Integer.parseInt(settleCompletedBefore[0]), Integer.parseInt(settleCompletedBefore[1]), Integer.parseInt(settleCompletedBefore[2]));
    LocalTime currentLocalTime = LocalTime.now();
    if (currentLocalTime.compareTo(settleCompletedBeforeTime) == 1 && ignoreSettleBefore == false) {
      LOGGER.error("{} {} {}", SettleConstants.PARTNER_VIETINBANK, SettleConstants.VTB_VIRTUAL_ACCT,
        "Vietin VirtualAcct not access to SFTP");
      SchedulerJobInfo jobTmp = new SchedulerJobInfo();
      jobTmp.setJobClass(VietinVirtualAcctSv.class.getName());
      settleJobController.pauseJob(jobTmp);
    }
  }

  private void reconcileProcessRecord(Map<String, VietinNotifyTransTable> successDataMap, VietinVirtualAcctDetail e, List<VietinVirtualAcctDetailOut> reconcile) {
    VietinNotifyTransTable vietinNotifyTransTable = successDataMap.get(e.getTransId());
    // check match on both
    if (vietinNotifyTransTable != null) {
      // check whether important keys, value is matching.
      VietinVirtualAcctDetailOut vietinVirtualAcctDetailOut = modelMapper.map(e, VietinVirtualAcctDetailOut.class);

      if ((e.getTransId().equals(vietinNotifyTransTable.getTransId()))
        && (e.getAmount().equals(vietinNotifyTransTable.getAmount()))
        && (e.getTransTime().equals(vietinNotifyTransTable.getTransTime()))) {
        // Trạng thái 00: tồn tại TransId và trùng khớp dữ liệu
        vietinVirtualAcctDetailOut.setReconcileStatus(SettleConstants.VTB_VIRTUAL_ACCT_RECONCILE_STATUS_00);
      } else {
        // Trạng thái 03: tồn tại TransId nhưng sai lệch dữ liệu
        vietinVirtualAcctDetailOut.setReconcileStatus(SettleConstants.VTB_VIRTUAL_ACCT_RECONCILE_STATUS_03);
      }
      reconcile.add(vietinVirtualAcctDetailOut);
      successDataMap.remove(e.getTransId());
    } else {
      // Trạng thái 01: VietinBank có, Bên SDDV không có
      VietinVirtualAcctDetailOut vietinVirtualAcctDetailOut = modelMapper.map(e, VietinVirtualAcctDetailOut.class);

      vietinVirtualAcctDetailOut.setReconcileStatus(SettleConstants.VTB_VIRTUAL_ACCT_RECONCILE_STATUS_01);
      reconcile.add(vietinVirtualAcctDetailOut);
    }
  }

  private void verifyVietinVirtualAcctRecordChecksum(VietinVirtualAcctDetail e, String filename) {
    String checksum = sercurityHelper.MD5Hashing(String.format("%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s", e.getRecordType(), e.getTransId(), e.getProviderId(), e.getMerchantId(), e.getBankCode(), e.getBranchCode(), e.getCustAcctNo(), e.getRecvVirtualAcctId(), e.getRecvVirtualAcctName(), e.getCustCode(), e.getCustName(), e.getAmount(), e.getCurrencyCode(), e.getPayRefNo(), e.getMerchantAcctNo(), e.getBillCycle(), e.getBillId(), e.getTransTime(), e.getStatus(), e.getBankTransId(), e.getAddField1(), e.getAddField2(), e.getAddField3(), e.getAddField4(), e.getAddField5(), configLoader.getSettlementVietinVirtualAcctPrivateKeyChecksum()), false);

    if (!checksum.equals(e.getRecordChecksum())) {
      LOGGER.error("VietinVirtualAcctSv verifyVietinVirtualAcctRecordChecksum wrong checksum in file: {} | element: {}", filename, e);
    }
  }

  private void verifyVietinVirtualAcctFooterChecksum(VietinVirtualAcctDetail e, String filename) throws JsonProcessingException {
    // mapping footer value to the right fields
    LOGGER.info("verifyVietinVirtualAcctFooterChecksum {}", e);
    String recordType = e.getRecordType();
    String providerId = e.getTransId();
    String userID = e.getProviderId();
    String recordNo = e.getMerchantId();
    String transTime = e.getBankCode();
    String fileChecksum = e.getBranchCode();

    String checksum = sercurityHelper.MD5Hashing(String.format("%s%s%s%s%s%s", recordType, providerId, userID, recordNo, transTime, configLoader.getSettlementVietinVirtualAcctPrivateKeyChecksum()), false);
    if (!checksum.equals(fileChecksum)) {
      LOGGER.error("VietinVirtualAcctSv verifyVietinVirtualAcctFooterChecksum wrong checksum in file: {} | element: {}", filename, e);
    }
  }

  private List<VietinVirtualAcctDetailOut> reconcileProcess(String file, String currDate) {
    // currDate format: yyyyMMdd
    LOGGER.info("{} {} Start proccess reconcile transaction", SettleConstants.PARTNER_VIETINBANK, SettleConstants.VTB_VIRTUAL_ACCT);
    List<VietinVirtualAcctDetailOut> reconcile = new ArrayList<>();
    try {
      // Load all transaction in Vietin settlement file
      List<VietinVirtualAcctDetail> bankDataIncludeFooter = new CsvToBeanBuilder(new InputStreamReader(new BOMInputStream(new ByteArrayInputStream(Files.readAllBytes(Paths.get(file)))), StandardCharsets.UTF_8))
        .withType(VietinVirtualAcctDetail.class).withSeparator('|')
        .build().parse();

      bankDataIncludeFooter.remove(0); // Remove header
      List<VietinVirtualAcctDetail> bankData = new ArrayList<>(bankDataIncludeFooter);
      verifyVietinVirtualAcctFooterChecksum(bankData.get(bankData.size() - 1), file);
      bankData.remove(bankData.size() - 1); // Remove footer
      // Load all success transaction in OneFin
      Map<String, VietinNotifyTransTable> successDataMap = vietinNotifyTransTableRepo.findByStatusAndDate(VTB_BT_SUCCESS_CODE, currDate).stream().collect(Collectors.toMap(VietinNotifyTransTable::getTransId, Function.identity()));

      DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
      String currDateLastDay = LocalDate.parse(currDate, dateTimeFormatter).minusDays(1).format(dateTimeFormatter);
      Map<String, VietinNotifyTransTable> successDataLastDayMap = vietinNotifyTransTableRepo.findByStatusAndDate(
        VTB_BT_SUCCESS_CODE,
        currDateLastDay
      ).stream().collect(Collectors.toMap(VietinNotifyTransTable::getTransId, Function.identity()));

      // find matching trans between OneFin and bank
      bankData.forEach(e -> {
        verifyVietinVirtualAcctRecordChecksum(e, file);
        if (e.getRecordType().equals(SettleConstants.VTB_VIRTUAL_ACCT_SETTLEMENT_RECORD_TYPE)) {
          // VTB_VIRTUAL_ACCT_RECONSOLIDATE_RECORD_TYPE = "002"
          reconcileProcessRecord(successDataMap, e, reconcile);
        } else if (e.getRecordType().equals(SettleConstants.VTB_VIRTUAL_ACCT_RECONSOLIDATE_RECORD_TYPE)) {
          // VTB_VIRTUAL_ACCT_RECONSOLIDATE_RECORD_TYPE = "003"
          // Load all success transaction in OneFin
          reconcileProcessRecord(successDataLastDayMap, e, reconcile);
        }
      });

      // Trạng thái 02: VietinBank không có, Bên SDDV có
      for (String transId : successDataMap.keySet()) {
        VietinNotifyTransTable vietinNotifyTransTable = successDataMap.get(transId);
        VietinVirtualAcctDetailOut vietinVirtualAcctDetailOut = new VietinVirtualAcctDetailOut();

        vietinVirtualAcctDetailOut.setRecordType(SettleConstants.VTB_VIRTUAL_ACCT_SETTLEMENT_RECORD_TYPE);
        vietinVirtualAcctDetailOut.setTransId(vietinNotifyTransTable.getTransId());
        vietinVirtualAcctDetailOut.setProviderId(configLoader.getSettlementVietinVirtualAcctProviderId());
        vietinVirtualAcctDetailOut.setMerchantId(configLoader.getSettlementVietinVirtualAcctMerchantId());
        vietinVirtualAcctDetailOut.setRecvVirtualAcctId(vietinNotifyTransTable.getRecvVirtualAcctId());
        vietinVirtualAcctDetailOut.setRecvVirtualAcctName(vietinNotifyTransTable.getRecvVirtualAcctName());
        vietinVirtualAcctDetailOut.setCustCode(vietinNotifyTransTable.getCustCode());
        vietinVirtualAcctDetailOut.setCustName(vietinNotifyTransTable.getCustName());
        vietinVirtualAcctDetailOut.setAmount(vietinNotifyTransTable.getAmount());
        vietinVirtualAcctDetailOut.setCurrencyCode(vietinNotifyTransTable.getCurrencyCode());
        vietinVirtualAcctDetailOut.setPayRefNo(vietinNotifyTransTable.getPayRefNo());
        vietinVirtualAcctDetailOut.setTransTime(vietinNotifyTransTable.getTransTime());
        vietinVirtualAcctDetailOut.setStatus(vietinNotifyTransTable.getStatusCode());
        vietinVirtualAcctDetailOut.setBankTransId(vietinNotifyTransTable.getBankTransId());
        vietinVirtualAcctDetailOut.setReconcileStatus(SettleConstants.VTB_VIRTUAL_ACCT_RECONCILE_STATUS_02);
        reconcile.add(vietinVirtualAcctDetailOut);
      }
    } catch (Exception e) {
      LOGGER.error("Process Reconcile Error {}", e);
    }
    LOGGER.info("{} {} End proccess reconcile transaction", SettleConstants.PARTNER_VIETINBANK, SettleConstants.VTB_VIRTUAL_ACCT);
    return reconcile;
  }

  private void sendFile2Vietin(List<VietinVirtualAcctDetailOut> reconcile, SettlementTransaction trans, DateTime currDate) throws IOException {
    LOGGER.info("{} {} Start proccess send feedback file", SettleConstants.PARTNER_VIETINBANK, SettleConstants.VTB_VIRTUAL_ACCT);
    File file = new File(configLoader.getSftpVietinbankVirtualAcctDirectoryStoreFile() + dateHelper.parseDateString(currDate, SettleConstants.DATE_FORMAT_yyyyMMDD) + "_" + configLoader.getSettlementVietinVirtualAcctProviderId() + "_THUHOTKAO_IN.txt");
    LOGGER.info("VirtualAcct response file: {}", file);
    BufferedWriter writer = new BufferedWriter(new FileWriter(file));
    writer.write(
      "recordType,TransId,providerId,merchantId,bankCode,branchCode,custAcctNo,recvVirtualAcctId,recvVirtualAcctName,custCode,custName,amount,currencyCode,billCycle,billId,payRefNo,merchantAcctNo,transTime,status,bankTransId,addField1,addField2,addField3,addField4,addField5,reconcileStatus,recordChecksum");
    writer.newLine();
    LOGGER.info("VirtualAcct response file, write details");

    int countSettlementRecordType = 0;
    for (VietinVirtualAcctDetailOut e : reconcile) {
      try {
        if (e.getRecordType().equals(SettleConstants.VTB_VIRTUAL_ACCT_SETTLEMENT_RECORD_TYPE)) {
          countSettlementRecordType = countSettlementRecordType + 1;
        }

        LOGGER.info("VirtualAcct response file, record {}", e);
        String checksum = sercurityHelper.MD5Hashing(String.format("%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s%s", e.getRecordType(), e.getTransId(), e.getProviderId(), e.getMerchantId(), e.getBankCode(), e.getBranchCode(), e.getCustAcctNo(), e.getRecvVirtualAcctId(), e.getRecvVirtualAcctName(), e.getCustCode(), e.getCustName(), e.getAmount(), e.getCurrencyCode(), e.getBillCycle(), e.getBillId(), e.getPayRefNo(), e.getMerchantAcctNo(), e.getTransTime(), e.getStatus(), e.getBankTransId(), e.getAddField1(), e.getAddField2(), e.getAddField3(), e.getAddField4(), e.getAddField5(), e.getReconcileStatus(), configLoader.getSettlementVietinVirtualAcctPrivateKeyChecksum()), false);

        String newRecord = String.format("%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s", e.getRecordType(), e.getTransId(), e.getProviderId(), e.getMerchantId(), e.getBankCode(), e.getBranchCode(), e.getCustAcctNo(), e.getRecvVirtualAcctId(), e.getRecvVirtualAcctName(), e.getCustCode(), e.getCustName(), e.getAmount(), e.getCurrencyCode(), e.getBillCycle(), e.getBillId(), e.getPayRefNo(), e.getMerchantAcctNo(), e.getTransTime(), e.getStatus(), e.getBankTransId(), e.getAddField1(), e.getAddField2(), e.getAddField3(), e.getAddField4(), e.getAddField5(), e.getReconcileStatus(), checksum);
        writer.write(newRecord);
        writer.newLine();
      } catch (Exception e1) {
        LOGGER.error("Error {}", e1);
      }
    }
    LOGGER.info("VirtualAcct response file, write end line");
    String checksum = sercurityHelper.MD5Hashing(String.format("%s%s%s%s%s%s",
      SettleConstants.VTB_VIRTUAL_ACCT_END_RECORD_TYPE,
      configLoader.getSettlementVietinVirtualAcctProviderId(),
      configLoader.getSettlementVietinVirtualAcctUserId(),
      countSettlementRecordType,
      dateHelper.currentDateString(SettleConstants.HO_CHI_MINH_TIME_ZONE, SettleConstants.DATE_FORMAT_yyyyMMDDHHmmss),
      configLoader.getSettlementVietinVirtualAcctPrivateKeyChecksum()
    ), false);
    String newRecord = String.format("%s|%s|%s|%s|%s|%s",
      SettleConstants.VTB_VIRTUAL_ACCT_END_RECORD_TYPE,
      configLoader.getSettlementVietinVirtualAcctProviderId(),
      configLoader.getSettlementVietinVirtualAcctUserId(),
      countSettlementRecordType,
      dateHelper.currentDateString(SettleConstants.HO_CHI_MINH_TIME_ZONE, SettleConstants.DATE_FORMAT_yyyyMMDDHHmmss),
      checksum);
    writer.write(newRecord);
    writer.newLine();
    writer.close();

    if (!trans.getFile().contains(file.getName())) {
      trans.getFile().add(file.getName());
    }

    // Upload file to minio server
    LOGGER.info("{} {} Start upload {} file to MINIO", SettleConstants.PARTNER_VIETINBANK, SettleConstants.VTB_VIRTUAL_ACCT, file.getName());
    // Upload file to minio
    minioService.uploadFile(configLoader.getBaseBucket(),
      configLoader.getSettleVietinVirtualAcctMinioDefaultFolder()
        + dateHelper.parseDateString(currDate, SettleConstants.DATE_FORMAT_ddMMyyyy) + "/"
        + file.getName(),
      file, "text/plain");
    LOGGER.info("{} {} End upload {} file to MINIO", SettleConstants.PARTNER_VIETINBANK, SettleConstants.VTB_VIRTUAL_ACCT, file.getName());

    // Upload file to sftp
    gateway.upload(file);
    file.delete();
    LOGGER.info("{} {} End proccess send feedback file", SettleConstants.PARTNER_VIETINBANK, SettleConstants.VTB_VIRTUAL_ACCT);
  }
}
