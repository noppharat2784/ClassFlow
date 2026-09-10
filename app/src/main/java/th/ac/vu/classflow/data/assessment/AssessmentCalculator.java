package th.ac.vu.classflow.data.assessment;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import th.ac.vu.classflow.data.model.AssessmentCalculation;
import th.ac.vu.classflow.data.model.DomainResult;

public final class AssessmentCalculator {

    private AssessmentCalculator() { }

    public static AssessmentCalculation calculate(@Nullable th.ac.vu.classflow.data.model.AssessmentData assessmentData) {
        return calculate(assessmentData != null ? assessmentData.getCriteria() : null);
    }

    public static AssessmentCalculation calculate(@Nullable Map<String, Object> criteria) {
        List<DomainResult> domainResults = new ArrayList<>();
        List<AssessmentRubricCatalog.DomainDef> domainDefs =
                AssessmentRubricCatalog.getDomainsForTrack(AssessmentRubricCatalog.TRACK_ID_SS1);

        int availableDomainsCount = 0;
        double sumDomainScores = 0.0;

        for (AssessmentRubricCatalog.DomainDef domainDef : domainDefs) {
            int observedNumericCount = 0;
            double sumNumericValues = 0.0;
            int totalCriteriaInDomain = domainDef.getCriteria().size();

            if (criteria != null) {
                for (AssessmentRubricCatalog.CriterionDef criterion : domainDef.getCriteria()) {
                    Object val = criteria.get(criterion.getId());
                    if (val instanceof Number) {
                        Number num = (Number) val;
                        double d = num.doubleValue();
                        if (!Double.isNaN(d) && !Double.isInfinite(d) && Math.floor(d) == d && d >= 1.0 && d <= 4.0) {
                            observedNumericCount++;
                            sumNumericValues += (int) d;
                        }
                    }
                }
            }

            if (observedNumericCount >= 2) {
                double domainScore = sumNumericValues / observedNumericCount;
                domainResults.add(new DomainResult(
                        domainDef.getId(),
                        domainDef.getTitle(),
                        domainScore,
                        observedNumericCount,
                        totalCriteriaInDomain,
                        true
                ));
                availableDomainsCount++;
                sumDomainScores += domainScore;
            } else {
                domainResults.add(new DomainResult(
                        domainDef.getId(),
                        domainDef.getTitle(),
                        null,
                        observedNumericCount,
                        totalCriteriaInDomain,
                        false
                ));
            }
        }

        if (availableDomainsCount >= 4) {
            double overallScore = sumDomainScores / availableDomainsCount;
            String level;
            if (overallScore >= 3.50) {
                level = AssessmentCalculation.LEVEL_STRONG;
            } else if (overallScore >= 2.75) {
                level = AssessmentCalculation.LEVEL_READY;
            } else if (overallScore >= 2.00) {
                level = AssessmentCalculation.LEVEL_DEVELOPING;
            } else {
                level = AssessmentCalculation.LEVEL_NEED_SUPPORT;
            }
            return new AssessmentCalculation(domainResults, overallScore, true, level);
        } else {
            return new AssessmentCalculation(domainResults, null, false, null);
        }
    }
}
