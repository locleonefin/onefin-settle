package com.onefin.ewallet.settlement.repository;

import com.onefin.ewallet.common.domain.settlement.SettlementTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;

@RepositoryRestResource(collectionResourceRel = "metaDatas", exported = false)
public interface SettleTransRepository extends JpaRepository<SettlementTransaction, String> {

	Page<SettlementTransaction> findAll(Specification<SettlementTransaction> spec, Pageable pageable);

	@Query(value = "SELECT * FROM settlement_transaction e ORDER BY e.created_date desc LIMIT 1", nativeQuery = true)
	SettlementTransaction findLatestTransaction();

	@Query(value = "SELECT * FROM settlement_transaction e WHERE e.partner = (:partner) AND e.domain = (:domain) AND e.status = (:tranStatus) ORDER BY e.created_date desc", nativeQuery = true)
	List<SettlementTransaction> findAllByPartnerAndDomainAndStatusTransaction(@Param("partner") String partner, @Param("domain") String domain, @Param("tranStatus") String tranStatus);

	@Query(value = "SELECT * FROM settlement_transaction e WHERE e.partner = (:partner) AND e.domain = (:domain) AND e.settle_date = (:settleDate) AND e.status = (:tranStatus)", nativeQuery = true)
	SettlementTransaction findAllByPartnerAndDomainAndSettleDateAndStatusTransaction(@Param("partner") String partner, @Param("domain") String domain, @Param("settleDate") String settleDate, @Param("tranStatus") String tranStatus);

	@Query(value = "SELECT * FROM settlement_transaction e WHERE e.partner = (:partner) AND e.domain = (:domain) AND e.status != (:tranStatus) ORDER BY e.created_date desc", nativeQuery = true)
	List<SettlementTransaction> findAllByPartnerAndDomainAndNotStatusTransaction(@Param("partner") String partner, @Param("domain") String domain, @Param("tranStatus") String tranStatus);

	@Query(value = "SELECT * FROM settlement_transaction e WHERE e.partner = (:partner) AND e.domain = (:domain) AND e.settle_date = (:settleDate)", nativeQuery = true)
	SettlementTransaction findByPartnerAndDomainAndSettleDate(@Param("partner") String partner, @Param("domain") String domain, @Param("settleDate") String settleDate);

	@Query(value = "SELECT * FROM settlement_transaction e WHERE e.partner = (:partner) AND e.domain = (:domain) AND e.settle_date = (:settleDate) AND e.status = (:tranStatus)", nativeQuery = true)
	SettlementTransaction findByPartnerAndDomainAndSettleDateAndStatus(@Param("partner") String partner, @Param("domain") String domain, @Param("settleDate") String settleDate, @Param("tranStatus") String tranStatus);

	@Query(value = "SELECT * FROM settlement_transaction e WHERE e.partner = (:partner) AND e.domain = (:domain) AND e.status = (:tranStatus) ORDER BY e.created_date desc LIMIT :limit", nativeQuery = true)
	List<SettlementTransaction> findLatestByPartnerAndDomainAndStatusTransaction(@Param("partner") String partner, @Param("domain") String domain, @Param("tranStatus") String tranStatus, @Param("limit") int limit);
}
