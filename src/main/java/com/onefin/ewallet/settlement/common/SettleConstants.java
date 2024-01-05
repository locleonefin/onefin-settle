package com.onefin.ewallet.settlement.common;

import com.onefin.ewallet.common.base.constants.OneFinConstants;

import java.util.stream.Stream;

public class SettleConstants extends OneFinConstants {

	// Settlement transaction
	public static final String SETTLEMENT_PROCESSING = "PROCESSING";
	public static final String SETTLEMENT_PENDING_T0 = "T0_PENDING";
	public static final String SETTLEMENT_PENDING_T1 = "T1_PENDING";
	public static final String SETTLEMENT_PENDING_T2 = "T2_PENDING";
	public static final String SETTLEMENT_SUCCESS = "SUCCESS";
  public static final String SETTLEMENT_ERROR = "ERROR";

  public static final String VTB_VIRTUAL_ACCT = "VIRTUAL_ACCT";
  public static final String VTB_VIRTUAL_ACCT_SETTLEMENT_RECORD_TYPE = "002";
  // Trạng thái 00: Cân khớp
  public static final String VTB_VIRTUAL_ACCT_RECONCILE_STATUS_00 = "00";
  // Trạng thái 01: VietinBank có, Bên SDDV không có
  public static final String VTB_VIRTUAL_ACCT_RECONCILE_STATUS_01 = "01";
  // Trạng thái 02: VietinBank không có, Bên SDDV có
  public static final String VTB_VIRTUAL_ACCT_RECONCILE_STATUS_02 = "02";
  // Trạng thái 03 – Sai lệch dữ liệu
  public static final String VTB_VIRTUAL_ACCT_RECONCILE_STATUS_03 = "03";

  public static final String VTB_VIRTUAL_ACCT_END_RECORD_TYPE = "009";
  public static final String VTB_VIRTUAL_ACCT_RECONSOLIDATE_RECORD_TYPE = "003";

	public static final String BVB_DATE_STRING_HEADER_FORMAT = "yyyyMMddhhmmss";

	public static final String BVB_DATE_STRING_FILE_TITLE_FORMAT = "yyyyMMdd";

	public static final String BVB_DATE_STRING_FILE_TITLE_FORMAT_2 = "yyyyMMdd";

	public enum WL_Merchant {
		PAYMENT_GATEWAY_WL1, PAYMENT_GATEWAY_WL2, PAYMENT_GATEWAY_WL3;
	}

	// Settlement common error
	public static final String ERROR_PREPARE_SETTLE_FILE = "Error in preparing TC settlement file, Contact admin!";
	public static final String ERROR_UPLOAD_SETTLE_FILE = "Error in upload settlement file to MINIO or SFTP, Contact admin!";
	public static final String ERROR_DOWNLOAD_PARTNER_SETTLE_FILE = "Error in downloading settlement file data, Contact admin!";

	public enum NapasSettleRule {

		DR("DR", 0, "", ""), MTI("[MTI]", 4, "0210", "0"), F2("[F2]", 19, "", " "), F3("[F3]", 6, "000000", "0"),
		SVC("[SVC]", 10, "EC_CASHIN", " "), TCC("[TCC]", 2, "04", "0"), F4("[F4]", 12, "", "0"),
		RTA("[RTA]", 12, "", "0"), F49("[F49]", 3, "704", "0"), F5("[F5]", 12, "", "0"), F50("[F50]", 3, "704", "0"),
		F9("[F9]", 8, "00000000", "0"), F6("[F6]", 12, "000000000000", "0"), RCA("[RCA]", 12, "000000000000", "0"),
		F51("[F51]", 3, "704", "0"), F10("[F10]", 8, "00000000", "0"), F11("[F11]", 6, "000000", "0"),
		F12("[F12]", 6, "", "0"), F13("[F13]", 4, "", "0"), F15("[F15]", 4, "", "0"), F18("[F18]", 4, "7399", "0"),
		F22("[F22]", 3, "000", "0"), F25("[F25]", 2, "08", "0"), F41("[F41]", 8, "00005804", "0"),
		ACQ("[ACQ]", 8, "", " "), ISS("[ISS]", 8, "", " "), MID("[MID]", 15, "", " "), BNB("[BNB]", 8, "", " "),
		F102("[F102]", 28, "", " "), F103("[F103]", 28, "", " "), SVFISSNP("[SVFISSNP]", 12, "", "0"),
		IRFISSACQ("[IRFISSACQ]", 12, "", "0"), IRFISSBNB("[IRFISSBNB]", 12, "", "0"),
		SVFACQNP("[SVFACQNP]", 12, "", "0"), IRFACQISS("[IRFACQISS]", 12, "", "0"),
		IRFACQBNB("[IRFACQBNB]", 12, "", "0"), SVFBNBNP("[SVFBNBNP]", 12, "", "0"),
		IRFBNBISS("[IRFBNBISS]", 12, "", "0"), IRFBNBACQ("[IRFBNBACQ]", 12, "", "0"), F37("[F37]", 12, "", " "),
		F38("[F38]", 6, "", " "), TRN("[TRN]", 16, "AAAD2QDOWNo0pQAA", " "), RRC("[RRC]", 4, "", "0"),
		RSV1("[RSV1]", 100, "", " "), RSV2("[RSV2]", 100, "", " "), RSV3("[RSV3]", 100, "", " "),
		CSR("[CSR]", 32, "", ""), TR("TR", 0, "", ""), NOT("[NOT]", 9, "", "0"), CRE("[CRE]", 20, "", " "),
		TIME("[TIME]", 6, "", ""), DATE("[DATE]", 8, "", ""), CSF("[CSF]", 32, "", "");

		private final String field;
		private final int length;
		private final String defaultValue;
		private final String defaultChar;

		NapasSettleRule(String field, int length, String defaultValue, String defaultChar) {
			this.field = field;
			this.length = length;
			this.defaultValue = defaultValue;
			this.defaultChar = defaultChar;
		}

		public String getField() {
			return field;
		}

		public Integer getLength() {
			return length;
		}

		public String getDefaultValue() {
			return defaultValue;
		}

		public String getDefaultChar() {
			return defaultChar;
		}
	}

	public enum NapasSettleRuleEW {

		DR("DR", 0, "", ""), MTI("[MTI]", 4, "0210", "0"), F2("[F2]", 19, "", " "), F3("[F3]", 6, "000000", "0"),
		SVC("[SVC]", 10, "WHITELABEL", " "), TCC("[TCC]", 2, "04", "0"), F4("[F4]", 12, "", "0"),
		RTA("[RTA]", 12, "", "0"), F49("[F49]", 3, "704", "0"), F5("[F5]", 12, "", "0"), F50("[F50]", 3, "704", "0"),
		F9("[F9]", 8, "00000000", "0"), F6("[F6]", 12, "000000000000", "0"), RCA("[RCA]", 12, "000000000000", "0"),
		F51("[F51]", 3, "704", "0"), F10("[F10]", 8, "00000000", "0"), F11("[F11]", 6, "000000", "0"),
		F12("[F12]", 6, "", "0"), F13("[F13]", 4, "", "0"), F15("[F15]", 4, "", "0"), F18("[F18]", 4, "7399", "0"),
		F22("[F22]", 3, "000", "0"), F25("[F25]", 2, "08", "0"), F41("[F41]", 8, "00005870", "0"),
		ACQ("[ACQ]", 8, "", " "), ISS("[ISS]", 8, "", " "), MID("[MID]", 15, "", " "), BNB("[BNB]", 8, "", " "),
		F102("[F102]", 28, "", " "), F103("[F103]", 28, "", " "), SVFISSNP("[SVFISSNP]", 12, "", "0"),
		IRFISSACQ("[IRFISSACQ]", 12, "", "0"), IRFISSBNB("[IRFISSBNB]", 12, "", "0"),
		SVFACQNP("[SVFACQNP]", 12, "", "0"), IRFACQISS("[IRFACQISS]", 12, "", "0"),
		IRFACQBNB("[IRFACQBNB]", 12, "", "0"), SVFBNBNP("[SVFBNBNP]", 12, "", "0"),
		IRFBNBISS("[IRFBNBISS]", 12, "", "0"), IRFBNBACQ("[IRFBNBACQ]", 12, "", "0"), F37("[F37]", 12, "", " "),
		F38("[F38]", 6, "", " "), TRN("[TRN]", 16, "AAAD2QDOWNo0pQAA", " "), RRC("[RRC]", 4, "", "0"),
		RSV1("[RSV1]", 100, "", " "), RSV2("[RSV2]", 100, "", " "), RSV3("[RSV3]", 100, "", " "),
		CSR("[CSR]", 32, "", ""), TR("TR", 0, "", ""), NOT("[NOT]", 9, "", "0"), CRE("[CRE]", 20, "", " "),
		TIME("[TIME]", 6, "", ""), DATE("[DATE]", 8, "", ""), CSF("[CSF]", 32, "", "");

		private final String field;
		private final int length;
		private final String defaultValue;
		private final String defaultChar;

		NapasSettleRuleEW(String field, int length, String defaultValue, String defaultChar) {
			this.field = field;
			this.length = length;
			this.defaultValue = defaultValue;
			this.defaultChar = defaultChar;
		}

		public String getField() {
			return field;
		}

		public Integer getLength() {
			return length;
		}

		public String getDefaultValue() {
			return defaultValue;
		}

		public String getDefaultChar() {
			return defaultChar;
		}
	}

	public enum NapasSettleRuleCO {

		DR("DR", 0, "", ""), MTI("[MTI]", 4, "0210", "0"), F2("[F2]", 19, "", " "), F3("[F3]", 6, "912000", "0"),
		SVC("[SVC]", 10, "IF_DEP", " "), TCC("[TCC]", 2, "97", "0"), F4("[F4]", 12, "", "0"),
		RTA("[RTA]", 12, "", "0"), F49("[F49]", 3, "704", "0"), F5("[F5]", 12, "", "0"), F50("[F50]", 3, "704", "0"),
		F9("[F9]", 8, "00000001", "0"), F6("[F6]", 12, "000000000000", "0"), RCA("[RCA]", 12, "000000000000", "0"),
		F51("[F51]", 3, "704", "0"), F10("[F10]", 8, "00000000", "0"), F11("[F11]", 6, "000000", "0"),
		F12("[F12]", 6, "", "0"), F13("[F13]", 4, "", "0"), F15("[F15]", 4, "", "0"), F18("[F18]", 4, "6011", "0"),
		F22("[F22]", 3, "000", "0"), F25("[F25]", 2, "00", "0"), F41("[F41]", 8, "00000001", "0"),
		ACQ("[ACQ]", 8, "", " "), ISS("[ISS]", 8, "", " "), MID("[MID]", 15, "", " "), BNB("[BNB]", 8, "", " "),
		F102("[F102]", 28, "", " "), F103("[F103]", 28, "", " "), SVFISSNP("[SVFISSNP]", 12, "", "0"),
		IRFISSACQ("[IRFISSACQ]", 12, "", "0"), IRFISSBNB("[IRFISSBNB]", 12, "", "0"),
		SVFACQNP("[SVFACQNP]", 12, "", "0"), IRFACQISS("[IRFACQISS]", 12, "", "0"),
		IRFACQBNB("[IRFACQBNB]", 12, "", "0"), SVFBNBNP("[SVFBNBNP]", 12, "", "0"),
		IRFBNBISS("[IRFBNBISS]", 12, "", "0"), IRFBNBACQ("[IRFBNBACQ]", 12, "", "0"), F37("[F37]", 12, "", " "),
		F38("[F38]", 6, "", " "), TRN("[TRN]", 16, "AAAD2QDOWNo0pQAA", " "), RRC("[RRC]", 4, "", "0"),
		RSV1("[RSV1]", 100, "", " "), RSV2("[RSV2]", 100, "", " "), RSV3("[RSV3]", 100, "", " "),
		CSR("[CSR]", 32, "", ""), TR("TR", 0, "", ""), NOT("[NOT]", 9, "", "0"), CRE("[CRE]", 20, "", " "),
		TIME("[TIME]", 6, "", ""), DATE("[DATE]", 8, "", ""), CSF("[CSF]", 32, "", "");

		private final String field;
		private final int length;
		private final String defaultValue;
		private final String defaultChar;

		NapasSettleRuleCO(String field, int length, String defaultValue, String defaultChar) {
			this.field = field;
			this.length = length;
			this.defaultValue = defaultValue;
			this.defaultChar = defaultChar;
		}

		public String getField() {
			return field;
		}

		public Integer getLength() {
			return length;
		}

		public String getDefaultValue() {
			return defaultValue;
		}

		public String getDefaultChar() {
			return defaultChar;
		}
	}

	public enum VNSmsProviderId {
		VIETTEL("VIETTEL", "109800"), MOBIFONE("MOBIFONE", "109000"), VINAPHONE("VINAPHONE", "109100"), VIETNAMOBILE("VIETNAMOBILE", "109200"), GMOBILE("GMOBILE", "109900"), ITEL("ITEL", "109300");

		private String telco;

		private String providerId;

		VNSmsProviderId(String telco, String providerId) {
			this.telco = telco;
			this.providerId = providerId;
		}

		public String getTelco() {
			return this.telco;
		}

		public String getProviderId() {
			return this.providerId;
		}

		public static Stream<VNSmsProviderId> stream() {
			return Stream.of(VNSmsProviderId.values());
		}
	}

	public enum BVBankReconciliationField {

		STT("STT",-1),
		EXTERNAL_REF_NO("External RefNo",0),
		TRANSACTION_DATE("Transaction Date",1),
		PARTNER_CODE("Partner Code", 2),
		RELATED_ACCOUNT("Related Account",3),
		CREDIT_ACCOUNT("Credit Account",4),
		AMOUNT("Amount", 5),
		CCY("Ccy", 6),
		TRN_REF_NO("trn Ref NO",7),
		NARRATIVE("Narrative", 8),
		CLIENT_USER_ID("ClientUserId",9),
		FC_CORE_REF("fcCoreRef",10),
		NAPAS_TRACE_ID("napasTraceId",11),
		MSG_TYPE("Msg type",12);


		private String field;

		private int index;
		BVBankReconciliationField(String s, int i) {
			field = s;
			index = i;
		}

		public String getField() {
			return field;
		}

		public int getIndex() {
			return index;
		}

		public static Stream<BVBankReconciliationField> stream() {
			return Stream.of(BVBankReconciliationField.values());
		}

	}

	public enum BVBankMonthlyReconciliationField {
		ROW_START("ROW_START",2,""),
		STT("STT",0,"stt"),
		VIRTUAL_ACCOUNT("VIRTUAL_ACCOUNT",1,"virtualAccount"),
		REF_NUM("SỐ GIAO DỊCH\n" +
				"(Reference Number)", 2,"refNum"),
		TRANS_DATE("NGÀY GIAO DỊCH\n" +
				"(Transaction Date)",3,"transDate"),
		VALUE_DATE("NGÀY GIÁ TRỊ\n" +
				"(Value Date)",4,"valueDate"),
		CREDIT_AMOUNT("PHÁT SINH CÓ\n" +
				"(Credit Amount)", 5,"creditAmount"),
		DEBIT_AMOUNT("PHÁT SINH NỢ\n" +
				"(Debit Amount)", 6,"debitAmount"),
		INTEREST("TRẢ LÃI\n" +
				"(Interest)",7,"interest"),
		BALANCE("SỐ DƯ\n" +
				"(Balance)", 8,"balance"),
		NARRATIVE("DIỄN GIẢI\n" +
				"(Narrative)",9,"narrative"),
		TELLER_CODE("GDV\n" +
				"(Teller Code)",10,"tellerCode"),
		TRN_CODE("TRN_CODE",11,"trnCode"),
		EXTERNAL_REF_NO("EXTERNAL_REF_NO",12,"externalRefNo"),
		TRANS_CHANNEL("Kênh giao dịch",13,"transChannel"),
		TRACE_ID("Số Trace",14,"traceId");

		private final String field;

		private final int index;

		private final String dtoField;
		BVBankMonthlyReconciliationField(String s, int i,String dtoField) {
			field = s;
			index = i;
			this.dtoField = dtoField;
		}

		public String getField() {
			return field;
		}

		public int getIndex() {
			return index;
		}

		public String getDtoField() {
			return dtoField;
		}

		public static Stream<BVBankMonthlyReconciliationField> stream() {
			return Stream.of(BVBankMonthlyReconciliationField.values());
		}

	}
}
