package com.onefin.ewallet.settlement.repository;

import java.util.List;

import com.onefin.ewallet.common.domain.bank.common.LinkBankTransaction;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import com.onefin.ewallet.common.base.repository.mariadb.IBaseTransactionRepository;

@RepositoryRestResource(collectionResourceRel = "metaDatas", exported = false)
public interface LinkBankTransRepo<T extends LinkBankTransaction>
        extends IBaseTransactionRepository<T> {

    @Query("SELECT e FROM LinkBankTransaction e WHERE e.apiOperation IN (:apiOperation) AND e.tranStatus = (:tranStatus) AND SUBSTRING(e.transDate, 1, 8) = (:date) ORDER BY e.createdDate desc")
    List<LinkBankTransaction> findSettleTransaction(@Param("apiOperation") List<String> apiOperation,
                                                    @Param("tranStatus") String tranStatus, @Param("date") String date);

//    @Query("SELECT e FROM VietinLinkBankTransaction e WHERE e.apiOperation IN (:apiOperation) AND e.requestId = (:requestId) AND e.tranStatus = (:tranStatus) AND DATE(e.updatedDate) = DATE(:date) ORDER BY e.createdDate desc")
//    VietinLinkBankTransaction findSettleTransactionByRequestIdAndTranStatusAndDate(
//            @Param("apiOperation") List<String> apiOperation, @Param("requestId") String requestId,
//            @Param("tranStatus") String tranStatus, @Param("date") Date date);

}
