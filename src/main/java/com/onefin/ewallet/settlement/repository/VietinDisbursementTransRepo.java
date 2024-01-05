package com.onefin.ewallet.settlement.repository;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import com.onefin.ewallet.common.base.repository.mariadb.IBaseTransactionRepository;
import com.onefin.ewallet.common.domain.billpay.vietin.trans.VietinBillPayTransactionView;

@RepositoryRestResource(collectionResourceRel = "metaDatas", exported = false)
public interface VietinDisbursementTransRepo<T extends VietinBillPayTransactionView>
		extends IBaseTransactionRepository<T> {

	@Query("SELECT e FROM VietinBillPayTransactionView e WHERE e.apiOperation = :apiOperation AND e.tranStatus IN (:tranStatus) AND DATE(e.updatedDate) = DATE(:date) ORDER BY e.createdDate desc")
	List<VietinBillPayTransactionView> findSettleTransaction(@Param("apiOperation") String apiOperation,
			@Param("tranStatus") List<String> tranStatus, @Param("date") Date date);

	@Query("SELECT e FROM VietinBillPayTransactionView e WHERE e.apiOperation  = :apiOperation AND e.requestId = (:requestId) AND e.tranStatus = (:tranStatus) AND DATE(e.updatedDate) = DATE(:date) ORDER BY e.createdDate desc")
	VietinBillPayTransactionView findSettleTransactionByRequestIdAndTranStatusAndDate(
			@Param("apiOperation") String apiOperation, @Param("requestId") String requestId,
			@Param("tranStatus") String tranStatus, @Param("date") Date date);

}
