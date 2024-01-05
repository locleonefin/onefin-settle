package com.onefin.ewallet.settlement.repository;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import com.onefin.ewallet.common.domain.settlement.VietinBillSummary;

@RepositoryRestResource(collectionResourceRel = "metaDatas", exported = false)
public interface VietinBillSummaryRepository extends JpaRepository<VietinBillSummary, String> {
	
	@Query(value="SELECT ROW_NUMBER() OVER (ORDER BY svr_provider_code) as id, (:dateRange) as date_range,  svr_provider_code, service_type, count(amount) as transaction_count, sum(amount) as transaction_sum FROM bill_report_view WHERE DATE(created_date) >= DATE(:fromDate) and DATE(created_date) <= DATE(:toDate) GROUP BY service_type, svr_provider_code ORDER BY svr_provider_code asc", nativeQuery = true)
	List<VietinBillSummary> getSummary(@Param("fromDate") Date fromDate, @Param("toDate") Date toDate, @Param("dateRange") String dateRange);
}
