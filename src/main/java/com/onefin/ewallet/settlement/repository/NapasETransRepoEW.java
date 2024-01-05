package com.onefin.ewallet.settlement.repository;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import com.onefin.ewallet.common.base.repository.mariadb.IBaseTransactionRepository;
import com.onefin.ewallet.common.domain.napas.NapasEwalletCETransactionView;
import com.onefin.ewallet.common.domain.napas.NapasEwalletEWTransactionView;

@RepositoryRestResource(collectionResourceRel = "metaDatas", exported = false)
public interface NapasETransRepoEW<T extends NapasEwalletEWTransactionView> extends IBaseTransactionRepository<T> {

	@Query(value = "SELECT * FROM napas_ewallet_ew_transaction_view net WHERE net.acquirer_trans_id = (:acquirerTransId) AND net.order_id = (:orderId)", nativeQuery = true)
	NapasEwalletEWTransactionView findByAcquirerTransIdAndOrderId(@Param("acquirerTransId") String acquirerTransId,
			@Param("orderId") String orderId);

	@Query(value = "SELECT * FROM napas_ewallet_ew_transaction_view net WHERE net.api_operation IN (:apiOperation) AND net.tran_status = (:status) AND DATE(net.updated_date) = DATE(:date)", nativeQuery = true)
	List<NapasEwalletEWTransactionView> findByEqualStatusAndUpdatedDate(
			@Param("apiOperation") List<String> apiOperation, @Param("status") String status, @Param("date") Date date);

	@Query(value = "SELECT * FROM napas_ewallet_ew_transaction_view net WHERE net.api_operation IN (:apiOperation) AND  net.tran_status != (:status) AND DATE(net.updated_date) = DATE(:date)", nativeQuery = true)
	List<NapasEwalletEWTransactionView> findByNotEqualStatusAndUpdatedDate(
			@Param("apiOperation") List<String> apiOperation, @Param("status") String status, @Param("date") Date date);
	
	@Query(value = "SELECT * FROM napas_ewallet_ew_transaction_view net WHERE net.api_operation IN (:apiOperation) AND net.tran_status = (:status) AND net.merchant_id = (:merchantId) AND net.transaction_date LIKE CONCAT(:date,'%')", nativeQuery = true)
	List<NapasEwalletEWTransactionView> findByEqualStatusAndNapasTransDate(
			@Param("apiOperation") List<String> apiOperation, @Param("status") String status, @Param("date") String date, @Param("merchantId") String merchantId);

	@Query(value = "SELECT * FROM napas_ewallet_ew_transaction_view net WHERE net.api_operation IN (:apiOperation) AND  net.tran_status != (:status) AND net.merchant_id = (:merchantId) AND net.transaction_date LIKE CONCAT(:date,'%')", nativeQuery = true)
	List<NapasEwalletEWTransactionView> findByNotEqualStatusAndNapasTransDate(
			@Param("apiOperation") List<String> apiOperation, @Param("status") String status, @Param("date") String date, @Param("merchantId") String merchantId);

}
