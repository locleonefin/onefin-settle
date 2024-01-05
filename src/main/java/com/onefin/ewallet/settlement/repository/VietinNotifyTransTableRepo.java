package com.onefin.ewallet.settlement.repository;

import com.onefin.ewallet.common.domain.bank.vietin.VietinNotifyTransTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public interface VietinNotifyTransTableRepo extends JpaRepository<VietinNotifyTransTable, UUID> {

  @Query(value = "SELECT * FROM vietin_notify_trans e WHERE e.status_code = (:status) AND e.trans_time_date = (:date) ORDER BY e.created_date desc", nativeQuery = true)
  List<VietinNotifyTransTable> findByStatusAndDate(@Param("status") String status, @Param("date") String date);

  List<VietinNotifyTransTable> findByTransId(String s);

	@Query(value = "SELECT * FROM vietin_notify_trans WHERE virtual_acct_var = :virtualAcctVar AND tran_status = :tranStatus", nativeQuery = true)
	List<VietinNotifyTransTable> findByVirtualAcctVarAndTranStatus(@Param("virtualAcctVar") String virtualAcctVar, @Param("tranStatus") String tranStatus);

	@Query(value = "SELECT * FROM vietin_notify_trans e WHERE e.txn_init_dt >= (:startDate) and e.txn_init_dt <= (:endDate) and bank_code = :bankCode GROUP BY trans_id ORDER BY e.created_date desc", nativeQuery = true)
	List<VietinNotifyTransTable> findByFromDateToDate(@Param("startDate") Date startDate, @Param("endDate") Date endDate, @Param("bankCode") String bankCode);

	@Query(value = "SELECT * FROM vietin_notify_trans e WHERE e.txn_init_dt >= (:startDate) and e.txn_init_dt <= (:endDate) and bank_code = :bankCode and msg_type = 'B' ORDER BY e.created_date desc", nativeQuery = true)
	List<VietinNotifyTransTable> findByFromDateToDateMsB(@Param("startDate") Date startDate, @Param("endDate") Date endDate, @Param("bankCode") String bankCode);

	@Query(value = "SELECT * FROM vietin_notify_trans e WHERE e.txn_init_dt >= (:startDate) and e.txn_init_dt <= (:endDate) and bank_code = :bankCode and msg_type = 'I' ORDER BY e.created_date desc", nativeQuery = true)
	List<VietinNotifyTransTable> findByFromDateToDateMsI(@Param("startDate") Date startDate, @Param("endDate") Date endDate, @Param("bankCode") String bankCode);
}
