package com.onefin.ewallet.settlement.repository;

import com.onefin.ewallet.common.base.repository.mariadb.IBaseTransactionRepository;
import com.onefin.ewallet.common.domain.bank.transfer.BankTransferTransaction;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.Date;
import java.util.List;

@RepositoryRestResource(collectionResourceRel = "metaDatas", exported = false)
public interface BankTransferRepo<T extends BankTransferTransaction>
		extends IBaseTransactionRepository<T> {

	List<BankTransferTransaction> findByClientRequestId(String clientRequestId);

	@Query(value = "SELECT * FROM bank_transfer_transaction e WHERE e.tran_status = :tranStatus and e.created_date >= :startDate and e.created_date <= :endDate and provider_bank_code = :providerBankCode", nativeQuery = true)
	List<BankTransferTransaction> findTransferByDateAndBankCode(@Param("startDate") Date beginingOfDate,
																@Param("endDate") Date endOfDate,
																@Param("tranStatus") String tranStatus,
																@Param("providerBankCode") String providerBankCode);

	@Query(value = "SELECT * FROM bank_transfer_transaction e WHERE e.request_id = :requestId and provider_bank_code = :providerBankCode", nativeQuery = true)
	List<BankTransferTransaction> findByRequestIdVCCB(@Param("requestId") String requestId,
													  @Param("providerBankCode") String providerBankCode);


}
