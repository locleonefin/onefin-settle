package com.onefin.ewallet.settlement.repository;

import com.onefin.ewallet.common.domain.bank.vietin.VietinVirtualAcctTransHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface VietinVirtualAcctTransHistoryRepo extends JpaRepository<VietinVirtualAcctTransHistory, UUID> {
	@Query(value = "SELECT * " +
			"FROM vietin_virtual_acct_trans_history e " +
			"WHERE e.bank_code = :bank_code " +
			"AND e.created_date >= :startOfDay " +
			"AND e.created_date < :startOfNextDay " +
			"AND e.tran_status = 'SUCCESS' " +
			"ORDER BY e.created_date, e.updated_date, e.expire_time", nativeQuery = true)
	List<VietinVirtualAcctTransHistory> findByCodeAndDate(@Param("bank_code") String bank_code, @Param("startOfDay") String startOfDay, @Param("startOfNextDay") String startOfNextDay);
}
