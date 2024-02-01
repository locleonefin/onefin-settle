package com.onefin.ewallet.settlement.dto;

import com.onefin.ewallet.common.domain.bank.vietin.VietinVirtualAcctTransHistory;
import com.onefin.ewallet.settlement.repository.VietinVirtualAcctTransHistoryRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VietinVirtualTransHistoryService {
	private final VietinVirtualAcctTransHistoryRepo userRepository;

	private final VietinVirtualTransHistoryMapper userMapper;

	public List<VietinVirtualTransHistoryTestDTO> getExcelByCodeAndDate(VietinVirtualTransHistoryTestDTO test) {
		Date createdDate = test.getCreatedDate();
		Instant instant = createdDate.toInstant();
		ZoneId zoneId = ZoneId.systemDefault();
		LocalDate date = instant.atZone(zoneId).toLocalDate();

		LocalDateTime startOfDay = date.atStartOfDay();
		LocalDateTime startOfNextDay = date.plusDays(1).atStartOfDay();

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
		String formattedStartOfDay = startOfDay.format(formatter);
		String formattedStartOfNextDay = startOfNextDay.format(formatter);

		List<VietinVirtualAcctTransHistory> results = userRepository.findByCodeAndDate(
				test.getBankCode(), test.getTranStatus(), formattedStartOfDay, formattedStartOfNextDay);

		return userMapper.toUserDTO(results); // Assuming mapper handles list of entities
	}

	public boolean isBankCodeValid(String bankCode) {
		String result = userRepository.findByBankCode(bankCode);
		return result != null; // If the list is not empty, bankCode exists in the database
	}

	public boolean isStatusValid(String transStatus) {
		String result = userRepository.findByStatus(transStatus);
		return result != null; // If the list is not empty, bankCode exists in the database
	}

}
