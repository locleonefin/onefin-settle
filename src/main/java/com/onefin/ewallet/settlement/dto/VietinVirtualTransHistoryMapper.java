package com.onefin.ewallet.settlement.dto;

import com.onefin.ewallet.common.domain.bank.vietin.VietinVirtualAcctTransHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class VietinVirtualTransHistoryMapper {
	private static final Logger LOGGER = LoggerFactory.getLogger(VietinVirtualTransHistoryMapper.class);

	public List<VietinVirtualTransHistoryTestDTO> toUserDTO(List<VietinVirtualAcctTransHistory> userList) {
		List<VietinVirtualTransHistoryTestDTO> userDTOList = new ArrayList<>();

		for (VietinVirtualAcctTransHistory user : userList) {
			VietinVirtualTransHistoryTestDTO userDTO = new VietinVirtualTransHistoryTestDTO();
			userDTO.setAmount(user.getAmount());
			userDTO.setBankCode(user.getBankCode());
			userDTO.setCreatedDate(user.getCreatedDate());
			userDTO.setTranStatus(user.getTranStatus());
			userDTO.setUpdatedDate(user.getUpdatedDate());
			userDTO.setExpireTime(user.getExpireTime());
			userDTO.setVirtualAcctVar(user.getVirtualAcctVar());
			userDTO.setMerchantCode(user.getMerchantCode());
			userDTOList.add(userDTO);
		}

		return userDTOList;
	}
}
