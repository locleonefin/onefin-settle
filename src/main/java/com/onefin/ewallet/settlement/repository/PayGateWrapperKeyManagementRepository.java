package com.onefin.ewallet.settlement.repository;

import com.onefin.ewallet.common.domain.merchant.PGWrapperKeyManagement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.UUID;

@RepositoryRestResource(collectionResourceRel = "metaDatas", exported = false)
public interface PayGateWrapperKeyManagementRepository extends JpaRepository<PGWrapperKeyManagement, UUID> {

	PGWrapperKeyManagement findByMerchantCode(String merchantCode);


	@Query(value = "select max(payment_cycle) from  pg_wrapper_key_management",nativeQuery = true)
	int findMaxPaymentCycle();

}

