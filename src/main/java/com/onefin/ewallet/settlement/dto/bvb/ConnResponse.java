package com.onefin.ewallet.settlement.dto.bvb;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.onefin.ewallet.common.base.model.BaseConnResponse;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ConnResponse extends BaseConnResponse {

	// new version
	private Object response;

	private String version;

	private String type;
}
