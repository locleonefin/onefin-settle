package com.onefin.ewallet.settlement.repository;

import com.onefin.ewallet.common.domain.merchant.MerchantTransactionSS;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@RepositoryRestResource(collectionResourceRel = "metaDatas", exported = false)
public interface MerchantTrxRepoSS extends JpaRepository<MerchantTransactionSS, String> {

	@RestResource(exported = false)
	@Query(value = "SELECT bt FROM MerchantTransactionSS bt WHERE bt.id = :id ")
	MerchantTransactionSS findByPartnerId(String id);

	@RestResource(exported = false)
	@Query(value = "SELECT bt FROM MerchantTransactionSS bt WHERE bt.trxId = :trxId ")
	MerchantTransactionSS findByPartnerTrxId(String trxId);

	Page<MerchantTransactionSS> findAll(Specification<MerchantTransactionSS> spec, Pageable pageable);

	List<MerchantTransactionSS> findAll(Specification<MerchantTransactionSS> spec);

	Page<MerchantTransactionSS> findAll(Pageable pageable);

	@Query(value = "SELECT * FROM softspace_transactions e WHERE e.TRX_UNIQUE_KEY = :trxUniqueKey AND e.trx_type = :trxType", nativeQuery = true)
	MerchantTransactionSS findByTrxUniqueKeyAndTransType(@Param("trxUniqueKey") String trxUniqueKey, @Param("trxType") String trxType);

}

