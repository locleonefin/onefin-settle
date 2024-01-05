package com.onefin.ewallet.settlement.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.onefin.ewallet.common.base.errorhandler.RuntimeBadRequestException;
import com.onefin.ewallet.common.domain.constants.DomainConstants;
import com.onefin.ewallet.common.domain.settlement.SettleKey;
import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class SettlementDetails extends SettlementTransaction {

	private String status;

	List<FileName> files;

	@JsonProperty(value = "file")
	public void setFile(List<String> file) {
		files = new ArrayList<>();
		file.forEach(e -> {
			FileName f = new FileName();
			f.setFileName(e);
			this.files.add(f);
		});
	}

}
