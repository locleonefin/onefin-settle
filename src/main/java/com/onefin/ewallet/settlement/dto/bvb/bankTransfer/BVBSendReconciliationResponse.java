package com.onefin.ewallet.settlement.dto.bvb.bankTransfer;

import lombok.Data;

@Data
public class BVBSendReconciliationResponse extends BVBIBFTCommonResponse<Object> {

	@Override
	public String toString() {
		return "BVBSendReconciliationResponse{" +
				"responseId='" + responseId + '\'' +
				", status='" + status + '\'' +
				", errorCode='" + errorCode + '\'' +
				", errorMessage='" + errorMessage + '\'' +
				", sig='" + sig + '\'' +
				", data='" + data + '\'' +
				'}';
	}
}