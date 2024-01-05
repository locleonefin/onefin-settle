package com.onefin.ewallet.settlement.repository;

import com.onefin.ewallet.common.domain.merchant.MerchantTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.UUID;

@RepositoryRestResource(collectionResourceRel = "metaDatas", exported = false)
public interface MerchantTrxRepo extends JpaRepository<MerchantTransaction, UUID> {


}
