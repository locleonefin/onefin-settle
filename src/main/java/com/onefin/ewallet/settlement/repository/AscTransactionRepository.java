package com.onefin.ewallet.settlement.repository;

import com.onefin.ewallet.common.domain.asc.AscTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.UUID;

@RepositoryRestResource(collectionResourceRel = "metaDatas", exported = false)
public interface AscTransactionRepository extends JpaRepository<AscTransaction, UUID> {

	List<AscTransaction> findAll(Specification<AscTransaction> spec);

	List<AscTransaction> findByRequestId(String requestId);

	@Query("SELECT a, m FROM AscTransaction a INNER JOIN MerchantTransactionSS m ON a.requestId = m.trxUniqueKey AND a.associateMerchantCode = m.merchantCode WHERE a IN :ascTrans AND m.transStatus = 'SETTLED' AND m.trxType <> 'SPENDING_CHARGE'")
	List<Object[]> findAllWithParentTransaction(@Param("ascTrans") List<AscTransaction> ascTrans);


}
