package com.onefin.ewallet.settlement.repository;

import com.onefin.ewallet.common.base.repository.mariadb.IBaseTransactionRepository;
import com.onefin.ewallet.common.domain.vnpay.SmsTransaction;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.Date;
import java.util.List;

@RepositoryRestResource(collectionResourceRel = "metaDatas", exported = false)
public interface VNPaySmsTransRepo<T extends SmsTransaction> extends IBaseTransactionRepository<T> {

	@Query("SELECT e FROM SmsTransaction e WHERE e.tranStatus = (:tranStatus) AND DATE(e.updatedDate) >= DATE(:fromDate) AND DATE(e.updatedDate) <= DATE(:toDate) ORDER BY e.createdDate desc")
	List<SmsTransaction> findByTranStatusAndFromToDate(@Param("tranStatus") String tranStatus,
													   @Param("fromDate") Date fromDate, @Param("toDate") Date toDate);

}
