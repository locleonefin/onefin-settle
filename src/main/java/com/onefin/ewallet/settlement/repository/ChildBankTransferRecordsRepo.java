package com.onefin.ewallet.settlement.repository;

import com.onefin.ewallet.common.domain.bank.transfer.BankTransferChildRecords;
import com.onefin.ewallet.common.domain.napas.NapasEwalletCETransactionView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;

@RepositoryRestResource(collectionResourceRel = "metaDatas", exported = false)
public interface ChildBankTransferRecordsRepo extends JpaRepository<BankTransferChildRecords, String> {

	BankTransferChildRecords findByTransId(String transId);

	@Query(value = "SELECT * FROM bank_transfer_child_records e WHERE e.trans_id = (:transId) AND  e.bank_status_code != (:bankStatusCode)", nativeQuery = true)
	BankTransferChildRecords findByTransIdAndNotEqualBankStatusCode(@Param("transId") String transId, @Param("bankStatusCode") String bankStatusCode);

	@Query(value = "SELECT * FROM bank_transfer_child_records e WHERE e.bank_status_code = (:status) AND e.updated_date LIKE CONCAT(:date,'%')", nativeQuery = true)
	List<BankTransferChildRecords> findByStatusAndDate(@Param("status") String status, @Param("date") String date);

}
