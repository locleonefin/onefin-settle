package com.onefin.ewallet.settlement.repository;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import com.onefin.ewallet.common.domain.settlement.VietinBillReport;
import com.onefin.ewallet.common.domain.settlement.VietinBillSummary;

@RepositoryRestResource(collectionResourceRel = "metaDatas", exported = false)
public interface VietinBillReportRepository extends JpaRepository<VietinBillReport, String> {
	List<VietinBillReport> findAll(Specification<VietinBillReport> spec);

	Page<VietinBillReport> findAll(Specification<VietinBillReport> spec, Pageable pageable);
	
	@Query(value="SELECT e.svr_provider_code, e.service_type, count(e.amount) as transaction_count, sum(e.amount) as transaction_sum FROM bill_report_view e GROUP BY e.service_type, e.svr_provider_code ORDER BY e.svr_provider_code desc", nativeQuery = true)
	List<String> getSummary(@Param("fromDate") Date fromDate, @Param("toDate") Date toDate);
}
