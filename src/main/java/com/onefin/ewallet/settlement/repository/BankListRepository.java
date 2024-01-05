package com.onefin.ewallet.settlement.repository;

import com.onefin.ewallet.common.domain.bank.common.BankList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BankListRepository extends JpaRepository<BankList, String> {

	BankList findByCode(String code);

	@Query(value = "SELECT * from bank_list e WHERE e.headquarters = true ORDER BY e.name ASC", nativeQuery = true)
	List<BankList> findListBankByCitad();

	@Query(value = "SELECT * from bank_list e WHERE e.headquarters = true AND e.napas_bin <> '' ORDER BY e.name ASC", nativeQuery = true)
	List<BankList> findListBankByNapas247();

	List<BankList> findByBankCode(String bankCode);

	@Query(value = "SELECT * FROM bank_list e WHERE e.napas_bin LIKE %:napasBin%", nativeQuery = true)
	BankList findByNapasBin(@Param("napasBin") String napasBin);

	BankList findBankListByCode(String code);

	@Query(value = "SELECT * FROM bank_list e WHERE e.vccb_bank_id = :bankId", nativeQuery = true)
	List<BankList> findByVccbBankId(@Param("bankId") String bankId);

}