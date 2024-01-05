package com.onefin.ewallet.settlement.dto.bvb.bankTransfer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.onefin.ewallet.settlement.config.DateUnixTimeSerializedJson;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.Date;

@Data
@JsonPropertyOrder({"requestID", "clientCode", "clientUserID", "time", "data", "signature"})
public class BVBIBFTCommonRequest<T> {

	@NotNull(message = "requestId cannot be null")
	@JsonProperty("requestID")
	protected String requestId;

	@JsonProperty("clientCode")
	@NotNull(message = "clientCode cannot be null")
	protected String clientCode;

	@JsonProperty("clientUserID")
//    @NotNull(message = "clientUserId cannot be null")
	protected String clientUserId = "";

	@JsonProperty("time")
	@NotNull(message = "time cannot be null")
	@JsonSerialize(using = DateUnixTimeSerializedJson.class)
	protected Date time;

	@JsonProperty("signature")
	protected String signature = "";

	//	@NotNull(message = "data cannot be null")
	@JsonProperty("data")
	protected T data;

//	public T getDataParsed() {
//
//		try {
//			ObjectMapper mapper = new ObjectMapper();
//			return mapper.readValue(data, new TypeReference<T>() {
//			});
//		} catch (Exception e) {
//			return null;
//		}
//	}
}
