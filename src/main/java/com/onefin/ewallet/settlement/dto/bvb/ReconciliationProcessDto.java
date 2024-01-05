package com.onefin.ewallet.settlement.dto.bvb;

import lombok.Data;

import java.io.ByteArrayOutputStream;
import java.util.List;

@Data
public class ReconciliationProcessDto {

	private ByteArrayOutputStream outputStreamXslx;

	private String reconciliationDateTitle;

	private String reconciliationDate;

	private String fileLocation;

	private String fileTitle;

	private boolean isError = false;

	private boolean missMatch = false;

	private List<ReconciliationDto> reconciliationDtos;
}
