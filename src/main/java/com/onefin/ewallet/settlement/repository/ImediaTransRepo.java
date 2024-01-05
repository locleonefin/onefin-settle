package com.onefin.ewallet.settlement.repository;

import com.onefin.ewallet.common.base.repository.mariadb.IBaseTransactionRepository;
import com.onefin.ewallet.common.domain.billpay.imedia.IMediaBillPayTransaction;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.Date;
import java.util.List;

@RepositoryRestResource(collectionResourceRel = "metaDatas", exported = false)
public interface ImediaTransRepo<T extends IMediaBillPayTransaction> extends IBaseTransactionRepository<T> {

	@Query(value = "SELECT * FROM imedia_billpay_transaction e WHERE e.tran_status = (:tranStatus) AND DATE(e.updated_date) >= DATE(:fromDate) AND DATE(e.updated_date) <= DATE(:toDate) ORDER BY e.created_date desc", nativeQuery = true)
	List<IMediaBillPayTransaction> findByTranStatusAndFromToDate(@Param("tranStatus") String tranStatus,
	                                                          @Param("fromDate") Date fromDate,
	                                                          @Param("toDate") Date toDate);

	@Query(value = "SELECT * FROM imedia_billpay_transaction e WHERE e.tran_status = (:tranStatus) AND e.api_operation = (:apiOperation) AND DATE(e.updated_date) >= DATE(:fromDate) AND DATE(e.updated_date) <= DATE(:toDate) ORDER BY e.created_date desc", nativeQuery = true)
	List<IMediaBillPayTransaction> findByTranStatusAndApiOperationAndFromToDate(@Param("tranStatus") String tranStatus, @Param("apiOperation") String apiOperation,
																 @Param("fromDate") Date fromDate,
																 @Param("toDate") Date toDate);

	@Query(value = "SELECT * FROM imedia_billpay_transaction e WHERE e.tran_status = (:tranStatus) AND e.api_operation IN (:apiOperationList) AND DATE(e.updated_date) >= DATE(:fromDate) AND DATE(e.updated_date) <= DATE(:toDate) ORDER BY e.created_date desc", nativeQuery = true)
	List<IMediaBillPayTransaction> findByTranStatusAndApiOperationListAndFromToDate(@Param("tranStatus") String tranStatus, @Param("apiOperationList") List<String> apiOperationList,
																				@Param("fromDate") Date fromDate,
																				@Param("toDate") Date toDate);
}
