package com.onefin.ewallet.settlement.repository;

import com.onefin.ewallet.common.domain.errorCode.PartnerErrorCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(collectionResourceRel = "metaDatas", exported = false)
public interface PartnerErrorCodeRepo extends JpaRepository<PartnerErrorCode, String> {

	@Query(value = "SELECT * FROM partner_error_code e WHERE e.partner = (:partner) AND e.domain = (:domain) AND e.code = (:code)", nativeQuery = true)
	PartnerErrorCode findAllByPartnerAndDomainAndCode(@Param("partner") String partner,
													  @Param("domain") String domain, @Param("code") String code);

}