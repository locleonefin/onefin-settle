package com.onefin.ewallet.settlement.repository;

import com.onefin.ewallet.common.base.repository.mariadb.IBaseTransactionRepository;
import com.onefin.ewallet.common.domain.billpay.vnpay.trans.VnpayTopupTransaction;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.Date;
import java.util.List;

@RepositoryRestResource(collectionResourceRel = "metaDatas", exported = false)
public interface VNPayAirtimeTransRepo<T extends VnpayTopupTransaction> extends IBaseTransactionRepository<T> {

	@Query(value = "SELECT * FROM vnpay_topup_transaction e WHERE e.tran_status = (:tranStatus) AND DATE(e.updated_date) >= DATE(:fromDate) AND DATE(e.updated_date) <= DATE(:toDate) ORDER BY e.created_date desc", nativeQuery = true)
	List<VnpayTopupTransaction> findByTranStatusAndFromToDate(@Param("tranStatus") String tranStatus,
	                                                          @Param("fromDate") Date fromDate,
	                                                          @Param("toDate") Date toDate);

}
