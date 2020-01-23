package ca.uhn.fhir.jpa.dao.predicate;

import ca.uhn.fhir.jpa.dao.SearchBuilder;
import ca.uhn.fhir.jpa.model.entity.ResourceIndexedSearchParamNumber;
import ca.uhn.fhir.jpa.model.entity.ResourceTable;
import ca.uhn.fhir.model.api.IQueryParameterType;
import ca.uhn.fhir.rest.param.NumberParam;
import ca.uhn.fhir.rest.param.ParamPrefixEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.persistence.criteria.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

@Component
@Scope("prototype")
class PredicateBuilderNumber extends BasePredicateBuilder {
	private static final Logger ourLog = LoggerFactory.getLogger(PredicateBuilderNumber.class);

	PredicateBuilderNumber(SearchBuilder theSearchBuilder) {
		super(theSearchBuilder);
	}

	public Predicate addPredicateNumber(String theResourceName,
													String theParamName,
													List<? extends IQueryParameterType> theList,
													SearchFilterParser.CompareOperation operation) {

		Join<ResourceTable, ResourceIndexedSearchParamNumber> join = createJoin(SearchBuilderJoinEnum.NUMBER, theParamName);

		if (theList.get(0).getMissing() != null) {
			addPredicateParamMissing(theResourceName, theParamName, theList.get(0).getMissing(), join);
			return null;
		}

		List<Predicate> codePredicates = new ArrayList<>();
		for (IQueryParameterType nextOr : theList) {

			if (nextOr instanceof NumberParam) {
				NumberParam param = (NumberParam) nextOr;

				BigDecimal value = param.getValue();
				if (value == null) {
					continue;
				}

				final Expression<BigDecimal> fromObj = join.get("myValue");
				ParamPrefixEnum prefix = defaultIfNull(param.getPrefix(), ParamPrefixEnum.EQUAL);
				if (operation == SearchFilterParser.CompareOperation.ne) {
					prefix = ParamPrefixEnum.NOT_EQUAL;
				} else if (operation == SearchFilterParser.CompareOperation.lt) {
					prefix = ParamPrefixEnum.LESSTHAN;
				} else if (operation == SearchFilterParser.CompareOperation.le) {
					prefix = ParamPrefixEnum.LESSTHAN_OR_EQUALS;
				} else if (operation == SearchFilterParser.CompareOperation.gt) {
					prefix = ParamPrefixEnum.GREATERTHAN;
				} else if (operation == SearchFilterParser.CompareOperation.ge) {
					prefix = ParamPrefixEnum.GREATERTHAN_OR_EQUALS;
				} else if (operation == SearchFilterParser.CompareOperation.eq) {
					prefix = ParamPrefixEnum.EQUAL;
				} else if (operation != null) {
					throw new IllegalArgumentException("Invalid operator specified for number type");
				}


				String invalidMessageName = "invalidNumberPrefix";

				Predicate predicateNumeric = createPredicateNumeric(theResourceName, theParamName, join, myBuilder, nextOr, prefix, value, fromObj, invalidMessageName);
				Predicate predicateOuter = combineParamIndexPredicateWithParamNamePredicate(theResourceName, theParamName, join, predicateNumeric);
				codePredicates.add(predicateOuter);

			} else {
				throw new IllegalArgumentException("Invalid token type: " + nextOr.getClass());
			}

		}

		Predicate predicate = myBuilder.or(toArray(codePredicates));
		myQueryRoot.addPredicate(predicate);
		return predicate;
	}
}