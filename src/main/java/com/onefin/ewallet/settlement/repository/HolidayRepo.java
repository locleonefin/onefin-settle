package com.onefin.ewallet.settlement.repository;

import com.onefin.ewallet.common.domain.holiday.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.Date;

@RepositoryRestResource(collectionResourceRel = "metaDatas", exported = false)
public interface HolidayRepo extends JpaRepository<Holiday, Long> {

	Holiday findByScheduleDate(Date date);

}
