package com.onefin.ewallet.settlement.repository;


import com.onefin.ewallet.common.domain.merchant.PGWrapperKeyManagement;
import com.onefin.ewallet.common.domain.merchant.PgMerchantTransactionsVw;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@RepositoryRestResource(collectionResourceRel = "metaDatas", exported = false)
public interface PgTransactionsVwRepo extends JpaRepository<PgMerchantTransactionsVw, String> {

	Page<PgMerchantTransactionsVw> findAll(Specification<PgMerchantTransactionsVw> spec, Pageable pageable);

	Optional<PgMerchantTransactionsVw> findByMerchantTrxId (String merchantTrxId);

	PgMerchantTransactionsVw findByMerchantCodeAndTrxUniqueKey (String merchantCode, String trxUniqueKey);

	@Query(value = "SELECT * FROM pg_merchant_transactions_vw e WHERE e.trx_date > (:date) and e.trx_date < (:date) + INTERVAL 1 day AND trans_status = :transStatus ORDER BY e.trx_date desc", nativeQuery = true)
	List<PgMerchantTransactionsVw> findByDate24Hour(
			@Param("date") Date date,
			@Param("transStatus") String transStatus
	);

	@Query(value = "SELECT * FROM pg_merchant_transactions_vw e WHERE e.trx_date > (:dateStart) and e.trx_date < (:dateEnd) AND trans_status = :transStatus ORDER BY e.trx_date desc", nativeQuery = true)
	List<PgMerchantTransactionsVw> findFromDateToDate(
			@Param("dateStart") Date dateStart,
			@Param("dateEnd") Date dateEnd,
			@Param("transStatus") String transStatus
	);

	@Query(value = "SELECT * FROM pg_merchant_transactions_vw e WHERE e.trx_date > (:dateStart) and e.trx_date < (:dateEnd) AND e.trans_status = :transStatus AND e.merchant_code = :merchantCode ORDER BY e.trx_date desc", nativeQuery = true)
	List<PgMerchantTransactionsVw> findFromDateToDateByMerchantCode(
			@Param("dateStart") Date dateStart,
			@Param("dateEnd") Date dateEnd,
			@Param("transStatus") String transStatus,
			@Param("merchantCode") String merchantCode
	);

	@Query(value = "SELECT * FROM pg_merchant_transactions_vw e WHERE e.trx_date > (:dateStart) and e.trx_date < (:dateEnd) AND e.trans_status = :transStatus  AND e.amount_charges = 0 group by e.merchant_code", nativeQuery = true)
	List<PgMerchantTransactionsVw> findMerchantCodeFromDateToDate(
			@Param("dateStart") Date dateStart,
			@Param("dateEnd") Date dateEnd,
			@Param("transStatus") String transStatus
	);

	@Query(value = "SELECT * FROM pg_merchant_transactions_vw e WHERE e.merchant_code = :merchantCode order by modified_date desc limit 1", nativeQuery = true)
	Optional<PgMerchantTransactionsVw> findFirstMerchantCode(
			@Param("merchantCode") String merchantCode
	);

	@Query(value = "SELECT * FROM pg_merchant_transactions_vw e WHERE e.trx_date > (:dateStart) and e.trx_date < (:dateEnd) AND e.trans_status = :transStatus AND e.amount_charges > 0 group by e.merchant_code", nativeQuery = true)
	List<PgMerchantTransactionsVw> findMerchantCodeUserFeeBearingFromDateToDate(
			@Param("dateStart") Date dateStart,
			@Param("dateEnd") Date dateEnd,
			@Param("transStatus") String transStatus
	);

}
