package com.onefin.ewallet.settlement.service.napas.ew3;

import com.onefin.ewallet.common.domain.settlement.SettlementTransaction;
import com.onefin.ewallet.common.utility.date.DateTimeHelper;
import com.onefin.ewallet.settlement.common.SettleConstants;
import com.onefin.ewallet.settlement.common.SettleHelper;
import com.onefin.ewallet.settlement.repository.SettleTransRepository;
import com.onefin.ewallet.settlement.service.SettleService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

/**
 * @author thaita
 */
@Component
@DisallowConcurrentExecution
public class NapasWL3InitPendTC extends QuartzJobBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(NapasWL3InitPendTC.class);

    private static final String LOG_NAPAS_PG_PREFIX = SettleConstants.PARTNER_NAPAS + " " + SettleConstants.PAYMENT_GATEWAYWL3;

    @Autowired
    private DateTimeHelper dateHelper;

    @Autowired
    private SettleTransRepository settleRepo;

    @Autowired
    private SettleService commonSettleService;

    @Autowired
    private SettleHelper settleHelper;

    /* *************************CREATE TRANSACTION***************************** */

    /**
     * @throws Exception
     * Create transaction with status "T0_PENDING" everyday to wait for Napas TC file (Thanh cong) uploading to sftp
     */
    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        String currentDate = dateHelper.currentDateString(SettleConstants.HO_CHI_MINH_TIME_ZONE,
                SettleConstants.DATE_FORMAT_MMddyy);
        LOGGER.info("{} Start Create Pending Trans {}", LOG_NAPAS_PG_PREFIX, currentDate);
        SettlementTransaction trans = settleRepo.findByPartnerAndDomainAndSettleDate(SettleConstants.PARTNER_NAPAS,
                SettleConstants.PAYMENT_GATEWAYWL3, currentDate);
        if (trans == null) {
            try {
                trans = (SettlementTransaction) settleHelper.createModelStructure(new SettlementTransaction());

                trans.getSettleKey().setDomain(SettleConstants.PAYMENT_GATEWAYWL3);
                trans.getSettleKey().setPartner(SettleConstants.PARTNER_NAPAS);
                trans.getSettleKey().setSettleDate(currentDate);
                trans.setStatus(SettleConstants.SETTLEMENT_PENDING_T0);
                commonSettleService.save(trans);
                LOGGER.info("{} Created Pending Trans Successfully {}", LOG_NAPAS_PG_PREFIX, currentDate);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        LOGGER.info("{} End Create Pending Trans {}", LOG_NAPAS_PG_PREFIX, currentDate);
    }

    /* *************************CREATE TRANSACTION***************************** */

}
