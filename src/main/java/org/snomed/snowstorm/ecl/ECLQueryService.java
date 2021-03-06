package org.snomed.snowstorm.ecl;

import ch.qos.logback.classic.Level;
import io.kaicode.elasticvc.api.BranchCriteria;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.snomed.langauges.ecl.ECLException;
import org.snomed.langauges.ecl.ECLQueryBuilder;
import org.snomed.snowstorm.core.data.domain.QueryConcept;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.snomed.snowstorm.ecl.domain.expressionconstraint.SExpressionConstraint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Optional;

@Service
public class ECLQueryService {

	@Autowired
	private ECLQueryBuilder queryBuilder;

	@Autowired
	private QueryService queryService;

	@Value("${timer.ecl.duration-threshold}")
	private int eclDurationLoggingThreshold;

	public Page<Long> selectConceptIds(String ecl, BranchCriteria branchCriteria, String path, boolean stated, PageRequest pageRequest) throws ECLException {
		return selectConceptIds(ecl, branchCriteria, path, stated, null, pageRequest);
	}

	public Page<Long> selectConceptIds(String ecl, BranchCriteria branchCriteria, String path, boolean stated, Collection<Long> conceptIdFilter) throws ECLException {
		return selectConceptIds(ecl, branchCriteria, path, stated, conceptIdFilter, null);
	}

	public Page<Long> selectConceptIds(String ecl, BranchCriteria branchCriteria, String path, boolean stated, Collection<Long> conceptIdFilter, PageRequest pageRequest) throws ECLException {
		TimerUtil eclSlowQueryTimer = getEclSlowQueryTimer();
		SExpressionConstraint expressionConstraint = (SExpressionConstraint) queryBuilder.createQuery(ecl);

		// TODO: Attempt to simplify queries here.
		// Changing something like "(id) AND (<<id OR >>id)"  to  "(id AND <<id) OR (id AND >>id)" will run in a fraction of the time because there will be no large fetches

		Optional<Page<Long>> pageOptional = expressionConstraint.select(path, branchCriteria, stated, conceptIdFilter, pageRequest, queryService);
		pageOptional.ifPresent(page -> eclSlowQueryTimer.checkpoint(() -> String.format("ecl:'%s', with %s results in this page.", ecl, page.getNumberOfElements())));

		return pageOptional.orElseGet(() -> {
			BoolQueryBuilder query = ConceptSelectorHelper.getBranchAndStatedQuery(branchCriteria.getEntityBranchCriteria(QueryConcept.class), stated);
			return ConceptSelectorHelper.fetchIds(query, conceptIdFilter, null, pageRequest, queryService);
		});
	}

	private TimerUtil getEclSlowQueryTimer() {
		return new TimerUtil(String.format("ECL took more than %s seconds.", eclDurationLoggingThreshold), Level.INFO, eclDurationLoggingThreshold);
	}

}
