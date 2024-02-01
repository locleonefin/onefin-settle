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
			"AND e.tran_status = :status " +
			"ORDER BY e.created_date, e.updated_date, e.expire_time", nativeQuery = true)
	List<VietinVirtualAcctTransHistory> findByCodeAndDate(@Param("bank_code") String bank_code,
														  @Param("status") String status,
														  @Param("startOfDay") String startOfDay,
														  @Param("startOfNextDay") String startOfNextDay);

	@Query(value = "SELECT bank_code FROM vietin_virtual_acct_trans_history WHERE bank_code = :bank_code LIMIT 1", nativeQuery = true)
	String findByBankCode(@Param("bank_code") String bank_code);

	@Query(value = "SELECT tran_status FROM vietin_virtual_acct_trans_history WHERE tran_status = :tran_status LIMIT 1", nativeQuery = true)
	String findByStatus(@Param("tran_status") String tran_status);
}
