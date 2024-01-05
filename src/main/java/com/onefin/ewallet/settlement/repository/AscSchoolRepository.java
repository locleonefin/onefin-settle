package com.onefin.ewallet.settlement.repository;

import com.onefin.ewallet.common.domain.asc.AscSchool;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.UUID;

@RepositoryRestResource(collectionResourceRel = "metaDatas", exported = false)
public interface AscSchoolRepository extends JpaRepository<AscSchool, UUID> {

	AscSchool findAscSchoolByCode(String code);

	List<AscSchool> findAscSchoolByAssociateMid(String primaryMid);

	Page<AscSchool> findAll(Specification<AscSchool> spec, Pageable pageable);

	@Query(value = "DELETE FROM asc_school WHERE id = :id", nativeQuery = true)
	void deleteById(@Param("id") UUID id);
}
