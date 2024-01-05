package com.onefin.ewallet.settlement.service;

import com.onefin.ewallet.common.base.constants.OneFinConstants;
import com.onefin.ewallet.common.base.model.RestProxy;
import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@Service
public class ConfigLoader implements InitializingBean {

	@Value("${proxy.active}")
	private boolean proxActive;

	@Value("${proxy.host}")
	private String proxHost;

	@Value("${proxy.port}")
	private int proxPort;

	@Value("${proxy.activeAuth}")
	private boolean proxActiveAuth;

	@Value("${proxy.userName}")
	private String proxUserName;

	@Value("${proxy.password}")
	private String proxPassword;

	@Value("${sftp.vietin.link_bank.host}")
	private String sftpVietinbankLinkBankHost;

	@Value("${sftp.vietin.link_bank.port}")
	private String sftpVietinbankLinkBankPort;

	@Value("${sftp.vietin.link_bank.username}")
	private String sftpVietinbankLinkBankUsername;

	@Value("${sftp.vietin.link_bank.password}")
	private String sftpVietinbankLinkBankPass;

	@Value("${sftp.vietin.link_bank.directory.remoteOut}")
	private String sftpVietinbankLinkBankDirectoryRemoteOut;

	@Value("${sftp.vietin.link_bank.directory.remoteIn}")
	private String sftpVietinbankLinkBankDirectoryRemoteIn;

	@Value("${sftp.vietin.link_bank.directory.remoteBackup}")
	private String sftpVietinbankLinkBankDirectoryRemoteBackup;

	@Value("${sftp.vietin.billpay.directory.remoteBackup}")
	private String sftpVietinbankBillPayDirectoryRemoteBackup;

	@Value("${sftp.vietin.link_bank.directory.storeFile}")
	private String sftpVietinbankLinkBankDirectoryStoreFile;

	@Value("${sftp.vietin.disbursement.host}")
	private String sftpVietinbankDisbursementHost;

	@Value("${sftp.vietin.disbursement.port}")
	private String sftpVietinbankDisbursementPort;

	@Value("${sftp.vietin.disbursement.username}")
	private String sftpVietinbankDisbursementUsername;

	@Value("${sftp.vietin.disbursement.password}")
	private String sftpVietinbankDisbursementPass;

	@Value("${sftp.vietin.disbursement.directory.remoteOut}")
	private String sftpVietinbankDisbursementDirectoryRemoteOut;

	@Value("${sftp.vietin.disbursement.directory.remoteIn}")
	private String sftpVietinbankDisbursementDirectoryRemoteIn;

	@Value("${sftp.vietin.disbursement.directory.remoteBackup}")
	private String sftpVietinbankDisbursementDirectoryRemoteBackup;

	@Value("${sftp.vietin.disbursement.directory.storeFile}")
	private String sftpVietinbankDisbursementDirectoryStoreFile;

	// ================================ virtualAcct ====================================
	@Value("${sftp.vietin.virtualAcct.host}")
	private String sftpVietinbankVirtualAcctHost;

	@Value("${sftp.vietin.virtualAcct.port}")
	private String sftpVietinbankVirtualAcctPort;

	@Value("${sftp.vietin.virtualAcct.username}")
	private String sftpVietinbankVirtualAcctUsername;

	@Value("${sftp.vietin.virtualAcct.password}")
	private String sftpVietinbankVirtualAcctPass;

	@Value("${sftp.vietin.virtualAcct.directory.remoteOut}")
	private String sftpVietinbankVirtualAcctDirectoryRemoteOut;

	@Value("${sftp.vietin.virtualAcct.directory.remoteIn}")
	private String sftpVietinbankVirtualAcctDirectoryRemoteIn;

	@Value("${sftp.vietin.virtualAcct.directory.remoteBackup}")
	private String sftpVietinbankVirtualAcctDirectoryRemoteBackup;

	// BVBANK ----------------------------------------------------------------
	@Value("${sftp.bvbank.virtualAcct.host}")
	private String sftpBvbankVirtualAcctHost;

	@Value("${sftp.bvbank.virtualAcct.port}")
	private String sftpBvbankVirtualAcctPort;

	@Value("${sftp.bvbank.virtualAcct.username}")
	private String sftpBvbankVirtualAcctUsername;

	@Value("${sftp.bvbank.virtualAcct.password}")
	private String sftpBvbankVirtualAcctPass;

	@Value("${sftp.bvbank.virtualAcct.directory.remoteOut}")
	private String sftpBvbankVirtualAcctDirectoryRemoteOut;

	@Value("${sftp.bvbank.virtualAcct.directory.remoteIn}")
	private String sftpBvbankVirtualAcctDirectoryRemoteIn;

	@Value("${sftp.bvbank.virtualAcct.directory.remoteBackup}")
	private String sftpBvbankVirtualAcctDirectoryRemoteBackup;

	@Value("${sftp.bvbank.virtualAcct.reconciliationTemplate}")
	private String bvbankReconciliationTemplate;

	@Value("${sftp.bvbank.virtualAcct.reconciliationTemplateExport}")
	private String bvbankReconciliationTemplateExport;

	@Value("${sftp.bvbank.virtualAcct.emailUrlPath}")
	private String bvbankUrlEmail;

	// -----------

	@Value("${settlement.vietin.virtualAcct.privateKeyChecksum}")
	private String settlementVietinVirtualAcctPrivateKeyChecksum;

	@Value("${sftp.vietin.virtualAcct.directory.storeFile}")
	private String sftpVietinbankVirtualAcctDirectoryStoreFile;

	@Value("${settlement.vietin.virtualAcct.providerId}")
	private String settlementVietinVirtualAcctProviderId;

	@Value("${settlement.vietin.virtualAcct.merchantId}")
	private String settlementVietinVirtualAcctMerchantId;

	@Value("${settlement.vietin.virtualAcct.settleCompletedBefore}")
	private String settlementVietinVirtualAcctSettleCompletedBefore;

	@Value("${settlement.vietin.virtualAcct.userId}")
	private String settlementVietinVirtualAcctUserId;

	@Value("${settlement.vietin.virtualAcct.minioDefaultFolder}")
	private String settleVietinVirtualAcctMinioDefaultFolder;

	// =================================================================================

	@Value("${sftp.vietin.billpay.host}")
	private String sftpVietinbankBillPayHost;

	@Value("${sftp.vietin.billpay.port}")
	private String sftpVietinbankBillPayPort;

	@Value("${sftp.vietin.billpay.username}")
	private String sftpVietinbankBillPayUsername;

	@Value("${sftp.vietin.billpay.password}")
	private String sftpVietinbankBillPayPass;

	@Value("${sftp.vietin.billpay.directory.remoteOut}")
	private String sftpVietinbankBillPayDirectoryRemoteOut;

	@Value("${sftp.vietin.billpay.directory.remoteIn}")
	private String sftpVietinbankBillPayDirectoryRemoteIn;

	@Value("${sftp.vietin.billpay.directory.storeFile}")
	private String sftpVietinbankBillPayDirectoryStoreFile;

	@Value("${settlement.vietin.link_bank.privateKeyChecksum}")
	private String privateKeyVietinbankLinkBankChecksum;

	@Value("${settlement.vietin.billpay.privateKeyChecksum}")
	private String privateKeyVietinbankBillPayChecksum;

	@Value("${settlement.vietin.disbursement.privateKeyChecksum}")
	private String privateKeyVietinbankDisbursementChecksum;

	@Value("${settlement.vietin.link_bank.providerName}")
	private String providerNameVietinbankLinkBank;

	@Value("${settlement.vietin.disbursement.providerName}")
	private String providerNameVietinbankDisbursement;

	@Value("${settlement.vietin.billpay.providerName}")
	private String providerNameVietinbankBillPay;

	@Value("${settlement.vietin.link_bank.recordTypeDetail}")
	private String recordTypeDetailVietinbankLinkBank;

	@Value("${settlement.vietin.disbursement.recordType}")
	private String recordTypeVietinbankDisbursement;

	@Value("${settlement.vietin.disbursement.reconcile_00}")
	private String reconcile_00VietinbankDisbursement;

	@Value("${settlement.vietin.disbursement.reconcile_01}")
	private String reconcile_01VietinbankDisbursement;

	@Value("${settlement.vietin.disbursement.reconcile_02}")
	private String reconcile_02VietinbankDisbursement;

	@Value("${settlement.vietin.disbursement.reconcile_03}")
	private String reconcile_03VietinbankDisbursement;

	@Value("${settlement.vietin.disbursement.reconcile_04}")
	private String reconcile_04VietinbankDisbursement;

	@Value("${settlement.vietin.link_bank.recordTypeEnd}")
	private String recordTypeEndVietinbank;

	@Value("${settlement.vietin.link_bank.rcReconcileOK}")
	private String rcReconcileOKVietinbank;

	@Value("${settlement.vietin.link_bank.msgTypePayment}")
	private String msgTypePaymentVietinbank;

	@Value("${settlement.vietin.link_bank.msgTypeRefund}")
	private String msgTypeRefundVietinbank;

	@Value("${settlement.vietin.link_bank.msgTypeCashout}")
	private String msgTypeCashoutVietinbank;

	@Value("${settlement.vietin.link_bank.vt0OF1}")
	private String vt0OF1;

	@Value("${settlement.vietin.link_bank.vt1OF0}")
	private String vt1OF0;

	@Value("${settlement.vietin.link_bank.vtOFFail}")
	private String vtOFFail;

	@Value("${settlement.vietin.link_bank.defaultExecuteUser}")
	private String settleVietinDefaultExecuteUser;

	@Value("${settlement.baseUrl}")
	private String minioBaseUrl;

	@Value("${settlement.minioBucket}")
	private String baseBucket;

	@Value("${settlement.vietin.link_bank.minioDefaultFolder}")
	private String settleVietinLinkBankMinioDefaultFolder;

	@Value("${settlement.vietin.disbursement.minioDefaultFolder}")
	private String settleVietinDisbursementMinioDefaultFolder;

	@Value("${settlement.vietin.billpay.minioDefaultFolder}")
	private String settleVietinBillPayMinioDefaultFolder;

	@Value("${sftp.napas.ce.host}")
	private String sftpNapasHost;

	@Value("${sftp.napas.ce.port}")
	private String sftpNapasPort;

	@Value("${sftp.napas.ce.username}")
	private String sftpNapasUsername;

	@Value("${sftp.napas.ce.password}")
	private String sftpNapasPass;

	@Value("${sftp.napas.ce.directory.remoteOut}")
	private String sftpNapasDirectoryRemoteOut;

	@Value("${sftp.napas.ce.directory.remoteIn}")
	private String sftpNapasDirectoryRemoteIn;

	@Value("${sftp.napas.ce.directory.storeFile}")
	private String ceNapasDirectoryLocalFile;

	@Value("${sftp.napas.ce.directory.ofPrivateKey}")
	private String sftpNapasDirectoryOfPrivateKey;

	@Value("${sftp.napas.ce.directory.napasPublicKey}")
	private String sftpNapasDirectoryNapasPublicKey;

	@Value("${settlement.napas.ce.zzz}")
	private String settleNapasZZZ;

	@Value("${settlement.napas.ce.bbb}")
	private String settleNapasBBB;

	@Value("${settlement.napas.ce.tctvCode}")
	private String settleNapasTctvCode;

	@Value("${settlement.napas.ce.settleOrder}")
	private String settleNapasSettleOrder;

	@Value("${settlement.napas.ce.settleFileTypeTC}")
	private String settleNapasSettleFileTypeTC;

	@Value("${settlement.napas.ce.settleFileTypeSL}")
	private String settleNapasSettleFileTypeSL;

	@Value("${settlement.napas.ce.settleFileTypeXL}")
	private String settleNapasSettleFileTypeXL;

	@Value("${settlement.napas.ce.serviceCodeEcom}")
	private String settleNapasServiceCodeEcom;

	@Value("${settlement.napas.ce.defaultExecuteUser}")
	private String settleNapasDefaultExecuteUser;

	@Value("${settlement.napas.ce.gdBH}")
	private String settleNapasGdBH;

	@Value("${settlement.napas.ce.gdHTTP}")
	private String settleNapasGdHTTP;

	@Value("${settlement.napas.ce.gdHTBP}")
	private String settleNapasGdHTBP;

	@Value("${settlement.napas.ce.minioDefaultFolder}")
	private String settleNapasCEMinioDefaultFolder;

	@Value("${sftp.napas.ew.host}")
	private String sftpEwNapasHost;

	@Value("${sftp.napas.ew.port}")
	private String sftpEwNapasPort;

	@Value("${sftp.napas.ew.username}")
	private String sftpEwNapasUsername;

	@Value("${sftp.napas.ew.password}")
	private String sftpEwNapasPass;

	@Value("${sftp.napas.ew.directory.remoteOut}")
	private String sftpEwNapasDirectoryRemoteOut;

	@Value("${sftp.napas.ew.directory.remoteIn}")
	private String sftpEwNapasDirectoryRemoteIn;

	@Value("${sftp.napas.ew.directory.storeFile}")
	private String sftpEwNapasDirectoryLocalFile;

	@Value("${sftp.napas.ew.directory.ofPrivateKey}")
	private String sftpEwNapasDirectoryOfPrivateKey;

	@Value("${sftp.napas.ew.directory.napasPublicKey}")
	private String sftpEwNapasDirectoryEwNapasPublicKey;

	@Value("${settlement.napas.ew.zzz}")
	private String settleEwNapasZZZ;

	@Value("${settlement.napas.ew.bbbWL1}")
	private String settleEwNapasBBB1;

	@Value("${settlement.napas.ew.bbbWL2}")
	private String settleEwNapasBBB2;

	@Value("${settlement.napas.ew.bbbWL3}")
	private String settleEwNapasBBB3;

	@Value("${settlement.napas.ew.tctvCode}")
	private String settleEwNapasTctvCode;

	@Value("${settlement.napas.ew.settleOrder}")
	private String settleEwNapasSettleOrder;

	@Value("${settlement.napas.ew.settleFileTypeTC}")
	private String settleEwNapasSettleFileTypeTC;

	@Value("${settlement.napas.ew.settleFileTypeSL}")
	private String settleEwNapasSettleFileTypeSL;

	@Value("${settlement.napas.ew.settleFileTypeXL}")
	private String settleEwNapasSettleFileTypeXL;

	@Value("${settlement.napas.ew.serviceCodeEcom}")
	private String settleEwNapasServiceCodeEcom;

	@Value("${settlement.napas.ew.defaultExecuteUser}")
	private String settleEwNapasDefaultExecuteUser;

	@Value("${settlement.napas.ew.gdBH}")
	private String settleEwNapasGdBH;

	@Value("${settlement.napas.ew.gdHTTP}")
	private String settleEwNapasGdHTTP;

	@Value("${settlement.napas.ew.gdHTBP}")
	private String settleEwNapasGdHTBP;

	@Value("${settlement.napas.ew.minioDefaultFolder}")
	private String settleNapasEWMinioDefaultFolder;

	@Value("${sftp.napas.co.host}")
	private String sftpCONapasHost;

	@Value("${sftp.napas.co.port}")
	private String sftpCONapasPort;

	@Value("${sftp.napas.co.username}")
	private String sftpCONapasUsername;

	@Value("${sftp.napas.co.password}")
	private String sftpCONapasPass;

	@Value("${sftp.napas.co.directory.remoteOut}")
	private String sftpCONapasDirectoryRemoteOut;

	@Value("${sftp.napas.co.directory.remoteIn}")
	private String sftpCONapasDirectoryRemoteIn;

	@Value("${sftp.napas.co.directory.storeFile}")
	private String sftpCONapasDirectoryLocalFile;

	@Value("${sftp.napas.co.directory.ofPrivateKey}")
	private String sftpCONapasDirectoryOfPrivateKey;

	@Value("${sftp.napas.co.directory.napasPublicKey}")
	private String sftpCONapasDirectoryEwNapasPublicKey;

	@Value("${settlement.napas.co.zzz}")
	private String settleCONapasZZZ;

	@Value("${settlement.napas.co.bbb}")
	private String settleCONapasBBB;

	@Value("${settlement.napas.co.tctvCode}")
	private String settleCONapasTctvCode;

	@Value("${settlement.napas.co.settleOrder}")
	private String settleCONapasSettleOrder;

	@Value("${settlement.napas.co.settleFileTypeTC}")
	private String settleCONapasSettleFileTypeTC;

	@Value("${settlement.napas.co.settleFileTypeSL}")
	private String settleCONapasSettleFileTypeSL;

	@Value("${settlement.napas.co.settleFileTypeXL}")
	private String settleCONapasSettleFileTypeXL;

	@Value("${settlement.napas.co.serviceCodeEcom}")
	private String settleCONapasServiceCodeEcom;

	@Value("${settlement.napas.co.defaultExecuteUser}")
	private String settleCONapasDefaultExecuteUser;

	@Value("${settlement.napas.co.gdBH}")
	private String settleCONapasGdBH;

	@Value("${settlement.napas.co.gdSL}")
	private String settleCONapasGdSL;

	@Value("${settlement.napas.co.gdHTTP}")
	private String settleCONapasGdHTTP;

	@Value("${settlement.napas.co.gdHTBP}")
	private String settleCONapasGdHTBP;

	@Value("${settlement.napas.co.minioDefaultFolder}")
	private String settleNapasCOMinioDefaultFolder;

	@Value("${conn-service.utility.host}")
	private String utilityUrl;

	@Value("${conn-service.utility.uri.notify}")
	private String utilityEmailUrl;

	@Value("${settlement.ofReceiver}")
	private String ofSettleReceiver;

	@Value("${settlement.vnpay.airtime.minioDefaultFolder}")
	private String settleVNPayAirtimeMinioDefaultFolder;

	@Value("${settlement.vnpay.sms.minioDefaultFolder}")
	private String settleVNPaySmsMinioDefaultFolder;

	@Value("${settlement.vnpay.airtime.pendingCode}")
	private String vnpayAirtimePendingCode;

	@Value("${settlement.vnpay.airtime.successCode}")
	private String vnpayAirtimeSuccessCode;

	@Value("${settlement.vnpay.sms.successCode}")
	private String vnpaySmsSuccessCode;

	@Value("${sftp.vnpay.airtime.storeFile}")
	private String SftpVNpayAirtimeDirectoryStoreFile;

	@Value("${sftp.vnpay.sms.storeFile}")
	private String SftpVNpaySmsDirectoryStoreFile;

	@Value("${sftp.vnpay.airtime.templateFile}")
	private String vnpayAirtimeTemplateFile;

	@Value("${settlement.imedia.minioDefaultFolder}")
	private String settleImediaMinioDefaultFolder;

	@Value("${sftp.imedia.transaction.billStoreFile}")
	private String SftpImediaBillDirectoryStoreFile;

	@Value("${sftp.imedia.transaction.topupStoreFile}")
	private String SftpImediaTopupDirectoryStoreFile;

	@Value("${settlement.vietin.billpay.providerId}")
	private String vietinBillPayProviderId;

	@Value("${settlement.vietin.disbursement.providerId}")
	private String vietinDisbursementProviderId;

	@Value("${settlement.vietin.billpay.merchantId}")
	private String vietinBillPayMerchantId;

	@Value("${settlement.vietin.billpay.recordType}")
	private String vietinBillPayRecordType;

	@Value("${settlement.vietin.billpay.reconsolidateRecordType}")
	private String vietinBillPayReconsolidateRecordType;

	@Value("${settlement.vietin.billpay.endRecordType}")
	private String vietinBillPayEndRecordType;

	@Value("${settlement.vietin.disbursement.endRecordType}")
	private String vietinDisbursementEndRecordType;

	@Value("${settlement.vietin.billpay.successStatus}")
	private String vietinBillPaySuccessStatus;

	/******************** Paydi ***************************/
	@Value("${sftp.merchant.paydi.directory.remoteIn}")
	private String sftpPaydiRemoteIn;

	@Value("${sftp.merchant.paydi.directory.remoteOut}")
	private String sftpPaydiRemoteOut;

	@Value("${sftp.merchant.paydi.directory.storeFile}")
	private String sftpPaydiStoreFile;

	@Value("${settlement.merchant.paydi.recordTypeDetail}")
	private String recordTypeDetail;

	@Value("${settlement.merchant.paydi.recordTypeEnd}")
	private String recordTypeEnd;

	@Value("${settlement.merchant.paydi.rcReconcileOK}")
	private String rcReconcileOK;

	@Value("${settlement.merchant.paydi.rcReconcileFail}")
	private String rcReconcileFail;

	@Value("${settlement.merchant.paydi.privateKeyChecksum}")
	private String privateKeyChecksum;

	/******************** Paydi ***************************/

	@Value("${asc.mailReport.midSplitTransByAccount}")
	private List<String> midSplitTransByAccount;

	@Value("${asc.mailReport.mcodeSplitTransByAccount}")
	private List<String> mcodeSplitTransByAccount;

	private RestProxy proxyConfig = new RestProxy();

	private List<String> vietinSettleAction = new ArrayList<String>();

	private List<String> vietinSettleActionTopUp = new ArrayList<String>();

	private List<String> vietinSettleActionWithdraw = new ArrayList<String>();

	private List<String> vietinSettleActionRefund = new ArrayList<String>();

	private List<String> napasSettleAction = new ArrayList<String>();

	private List<String> napasSettleActionCashout = new ArrayList<String>();

	@Override
	public void afterPropertiesSet() throws Exception {
		setProxy();
		initListVietinSettleAction();
		initListVietinSettleActionTopup();
		initListVietinSettleActionWithdraw();
		initListVietinSettleActionRefund();
		initListNapasSettleAction();
		initListNapasSettleActionCashout();
//		Files.createDirectories(Paths.get(sftpVietinbankLinkBankDirectoryStoreFile));
//		Files.createDirectories(Paths.get(sftpVietinbankBillPayDirectoryStoreFile));
//		Files.createDirectories(Paths.get(sftpVietinbankDisbursementDirectoryStoreFile));
//		Files.createDirectories(Paths.get(ceNapasDirectoryLocalFile));
//		Files.createDirectories(Paths.get(SftpVNpayAirtimeDirectoryStoreFile));
//		Files.createDirectories(Paths.get(SftpVNpaySmsDirectoryStoreFile));
//		Files.createDirectories(Paths.get(sftpPaydiStoreFile));
//		Files.createDirectories(Paths.get(sftpVietinbankVirtualAcctDirectoryStoreFile));
	}

	private void setProxy() {
		proxyConfig.setActive(proxActive);
		proxyConfig.setHost(proxHost);
		proxyConfig.setPort(proxPort);
		proxyConfig.setAuth(proxActiveAuth);
		proxyConfig.setUserName(proxUserName);
		proxyConfig.setPassword(proxPassword);
	}

	private void initListVietinSettleAction() {
		vietinSettleAction.add(OneFinConstants.TOPUP_TOKEN);
		vietinSettleAction.add(OneFinConstants.TOPUP_TOKEN_OTP);
		vietinSettleAction.add(OneFinConstants.WITHDRAW);
		vietinSettleAction.add(OneFinConstants.TOKEN_ISSUER_TOPUP);
		vietinSettleAction.add(OneFinConstants.REFUND);
	}

	private void initListVietinSettleActionTopup() {
		vietinSettleActionTopUp.add(OneFinConstants.TOPUP_TOKEN);
		vietinSettleActionTopUp.add(OneFinConstants.TOPUP_TOKEN_OTP);
		vietinSettleActionTopUp.add(OneFinConstants.TOKEN_ISSUER_TOPUP);
	}

	private void initListVietinSettleActionWithdraw() {
		vietinSettleActionWithdraw.add(OneFinConstants.WITHDRAW);
	}

	private void initListVietinSettleActionRefund() {
		vietinSettleActionRefund.add(OneFinConstants.REFUND);
	}

	private void initListNapasSettleAction() {
		napasSettleAction.add(OneFinConstants.NAPAS_PURCHASE_WITH_RETURNED_TOKEN);
		napasSettleAction.add(OneFinConstants.NAPAS_TOPUP_WITH_TOKEN);
		napasSettleAction.add(OneFinConstants.NAPAS_TOPUP_WITHOUT_TOKEN);
	}

	private void initListNapasSettleActionCashout() {
		napasSettleActionCashout.add(OneFinConstants.NAPAS_CASHOUT_ACCOUNT_TRANSFER);
	}

}
