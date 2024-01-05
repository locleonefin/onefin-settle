package com.onefin.ewallet.settlement.dto.bvb.bankTransfer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.lang.reflect.ParameterizedType;

@Data
@JsonPropertyOrder({"responseID", "status", "errorCode", "errorMessage", "data", "sig"})
public class BVBIBFTCommonResponse<T> {

	@JsonProperty("responseID")
	protected String responseId;

	@NotNull(message = "status cannot be null")
	protected String status;

	@NotNull(message = "error code cannot be null")
	protected String errorCode;

	protected String errorMessage = "null";

	//	@NotNull(message = "signature cannot be null")
	@JsonProperty("sig")
	protected String sig;

	//	@JsonProperty("data")
	protected String data;

	@JsonIgnore
	public T getDataParsed() {
		try {
			ObjectMapper mapper = new ObjectMapper();
			return mapper.readValue(data, (Class<T>) ((ParameterizedType) this.getClass()
					.getGenericSuperclass()).getActualTypeArguments()[0]);
		} catch (Exception e) {
			return null;
		}
	}

	@JsonProperty("refTransType")
	private String refTransType;

	@JsonProperty("transType")
	private String transType;

	@JsonProperty("request")
	private Object request;


}
