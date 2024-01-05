package com.onefin.ewallet.settlement.dto;

import lombok.Data;

@Data
public class NapasSettlement {

	public NapasSettlement(String mTI, String f2, String f3, String sVC, String tCC, String f4, String rTA, String f49,
	                       String f5, String f50, String f9, String f6, String rCA, String f51, String f10, String f11, String f12,
	                       String f13, String f15, String f18, String f22, String f25, String f41, String aCQ, String iSS, String mID,
	                       String bNB, String f102, String f103, String sVFISSNP, String iRFISSACQ, String iRFISSBNB, String sVFACQNP,
	                       String iRFACQISS, String iRFACQBNB, String sVFBNBNP, String iRFBNBISS, String iRFBNBACQ, String f37,
	                       String f38, String tRN, String rRC, String rSV1, String transDate, String orderId, String rSV2, String rSV3,
	                       String cSR, String inputToChecksum) {
		super();
		MTI = mTI;
		F2 = f2;
		F3 = f3;
		SVC = sVC;
		TCC = tCC;
		F4 = f4;
		RTA = rTA;
		F49 = f49;
		F5 = f5;
		F50 = f50;
		F9 = f9;
		F6 = f6;
		RCA = rCA;
		F51 = f51;
		F10 = f10;
		F11 = f11;
		F12 = f12;
		F13 = f13;
		F15 = f15;
		F18 = f18;
		F22 = f22;
		F25 = f25;
		F41 = f41;
		ACQ = aCQ;
		ISS = iSS;
		MID = mID;
		BNB = bNB;
		F102 = f102;
		F103 = f103;
		SVFISSNP = sVFISSNP;
		IRFISSACQ = iRFISSACQ;
		IRFISSBNB = iRFISSBNB;
		SVFACQNP = sVFACQNP;
		IRFACQISS = iRFACQISS;
		IRFACQBNB = iRFACQBNB;
		SVFBNBNP = sVFBNBNP;
		IRFBNBISS = iRFBNBISS;
		IRFBNBACQ = iRFBNBACQ;
		F37 = f37;
		F38 = f38;
		TRN = tRN;
		RRC = rRC;
		RSV1 = rSV1;
		this.transDate = transDate;
		this.orderId = orderId;
		RSV2 = rSV2;
		RSV3 = rSV3;
		CSR = cSR;
		this.inputToChecksum = inputToChecksum;
	}

	private String MTI;

	private String F2;

	private String F3;

	private String SVC;

	private String TCC;

	private String F4;

	private String RTA;

	private String F49;

	private String F5;

	private String F50;

	private String F9;

	private String F6;

	private String RCA;

	private String F51;

	private String F10;

	private String F11;

	private String F12;

	private String F13;

	private String F15;

	private String F18;

	private String F22;

	private String F25;

	private String F41;

	private String ACQ;

	private String ISS;

	private String MID;

	private String BNB;

	private String F102;

	private String F103;

	private String SVFISSNP;

	private String IRFISSACQ;

	private String IRFISSBNB;

	private String SVFACQNP;

	private String IRFACQISS;

	private String IRFACQBNB;

	private String SVFBNBNP;

	private String IRFBNBISS;

	private String IRFBNBACQ;

	private String F37;

	private String F38;

	private String TRN;

	private String RRC;

	private String RSV1;

	private String transDate;

	private String orderId;

	private String RSV2;

	private String RSV3;

	private String CSR;

	private String inputToChecksum;
}
