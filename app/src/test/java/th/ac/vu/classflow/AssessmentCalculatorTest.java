package th.ac.vu.classflow;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import th.ac.vu.classflow.data.assessment.AssessmentCalculator;
import th.ac.vu.classflow.data.assessment.AssessmentRubricCatalog;
import th.ac.vu.classflow.data.model.AssessmentCalculation;
import th.ac.vu.classflow.data.model.AssessmentData;
import th.ac.vu.classflow.data.model.DomainResult;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AssessmentCalculatorTest {

    @Test
    public void testRubricCatalogStructure() {
        List<AssessmentRubricCatalog.DomainDef> domains =
                AssessmentRubricCatalog.getDomainsForTrack(AssessmentRubricCatalog.TRACK_ID_SS1);
        assertEquals(5, domains.size());

        List<String> criteriaKeys = AssessmentRubricCatalog.getApprovedCriteriaKeys();
        assertEquals(15, criteriaKeys.size());

        for (AssessmentRubricCatalog.DomainDef domain : domains) {
            assertEquals(3, domain.getCriteria().size());
        }
    }

    @Test
    public void testAllCriteriaObservedStrong() {
        Map<String, Object> criteria = new HashMap<>();
        for (String key : AssessmentRubricCatalog.getApprovedCriteriaKeys()) {
            criteria.put(key, 4);
        }

        AssessmentCalculation calc = AssessmentCalculator.calculate(criteria);
        assertTrue(calc.isOverallAvailable());
        assertNotNull(calc.getOverallScore());
        assertEquals(4.0, calc.getOverallScore(), 0.001);
        assertEquals(AssessmentCalculation.LEVEL_STRONG, calc.getOverallLevel());
        assertEquals(15, calc.getObservedCriteriaCount());
        assertEquals(15, calc.getTotalCriteriaCount());
        assertEquals(100, calc.getCriteriaCoveragePercent());
        assertEquals(5, calc.getAvailableDomainCount());
        assertEquals(5, calc.getTotalDomainCount());

        for (DomainResult dr : calc.getDomainResults()) {
            assertTrue(dr.isAvailable());
            assertEquals(4.0, dr.getScore(), 0.001);
            assertEquals(3, dr.getObservedCount());
            assertEquals(3, dr.getTotalCount());
        }
    }

    @Test
    public void testAllFifteenNotObserved_IsValidDraftWithNoAvailableDomains() {
        // A. All 15 NOT_OBSERVED: valid criteria, 0 available domains, overall unavailable, level unavailable
        Map<String, Object> criteria = new HashMap<>();
        for (String key : AssessmentRubricCatalog.getApprovedCriteriaKeys()) {
            criteria.put(key, AssessmentData.VALUE_NOT_OBSERVED);
        }

        // Verify all 15 values are valid
        assertEquals(15, criteria.size());
        for (Map.Entry<String, Object> entry : criteria.entrySet()) {
            assertTrue("Key must be valid", AssessmentRubricCatalog.getApprovedCriteriaKeys().contains(entry.getKey()));
            assertTrue("NOT_OBSERVED must be valid", AssessmentRubricCatalog.isValidCriterionValue(entry.getValue()));
        }

        AssessmentCalculation calc = AssessmentCalculator.calculate(criteria);
        assertFalse("Overall score must not be available", calc.isOverallAvailable());
        assertNull("Overall score must be null", calc.getOverallScore());
        assertEquals("Level must be INSUFFICIENT EVIDENCE",
                AssessmentCalculation.LEVEL_INSUFFICIENT_EVIDENCE, calc.getOverallLevel());
        assertEquals(0, calc.getObservedCriteriaCount());
        assertEquals(15, calc.getTotalCriteriaCount());
        assertEquals(0, calc.getCriteriaCoveragePercent());
        assertEquals(0, calc.getAvailableDomainCount());
        assertEquals(5, calc.getTotalDomainCount());

        for (DomainResult dr : calc.getDomainResults()) {
            assertFalse(dr.isAvailable());
            assertNull(dr.getScore());
            assertEquals(0, dr.getObservedCount());
            assertEquals(3, dr.getTotalCount());
        }
    }

    @Test
    public void testNumberParsingAndNormalization() {
        // B. Number parsing: Long(4) accepted, Integer(3) accepted, Double(2.0) accepted
        assertTrue(AssessmentRubricCatalog.isValidCriterionValue(Long.valueOf(4)));
        assertTrue(AssessmentRubricCatalog.isValidCriterionValue(Integer.valueOf(3)));
        assertTrue(AssessmentRubricCatalog.isValidCriterionValue(Double.valueOf(2.0)));

        Map<String, Object> rawMap = new HashMap<>();
        rawMap.put("rubricId", AssessmentRubricCatalog.RUBRIC_ID_SS1);
        Map<String, Object> rawCriteria = new HashMap<>();
        rawCriteria.put("1.1", Long.valueOf(4));
        rawCriteria.put("1.2", Integer.valueOf(3));
        rawCriteria.put("1.3", Double.valueOf(2.0));
        rawMap.put("criteria", rawCriteria);

        AssessmentData parsed = AssessmentData.fromMap(rawMap);
        assertNotNull(parsed);
        assertEquals(Integer.valueOf(4), parsed.getCriteria().get("1.1"));
        assertEquals(Integer.valueOf(3), parsed.getCriteria().get("1.2"));
        assertEquals(Integer.valueOf(2), parsed.getCriteria().get("1.3"));
    }

    @Test
    public void testInvalidValuesStrictlyRejected() {
        // C. Invalid values: 0 rejected, 5 rejected, negative rejected, fractional rejected, unexpected String rejected, null rejected
        assertFalse("0 must be rejected", AssessmentRubricCatalog.isValidCriterionValue(0));
        assertFalse("5 must be rejected", AssessmentRubricCatalog.isValidCriterionValue(5));
        assertFalse("-1 must be rejected", AssessmentRubricCatalog.isValidCriterionValue(-1));
        assertFalse("2.5 (Double) must be rejected", AssessmentRubricCatalog.isValidCriterionValue(Double.valueOf(2.5)));
        assertFalse("2.5 (Float) must be rejected", AssessmentRubricCatalog.isValidCriterionValue(Float.valueOf(2.5f)));
        assertFalse("Unexpected string '2' must be rejected", AssessmentRubricCatalog.isValidCriterionValue("2"));
        assertFalse("Unexpected string 'foo' must be rejected", AssessmentRubricCatalog.isValidCriterionValue("foo"));
        assertFalse("Lowercase string 'not_observed' must be rejected", AssessmentRubricCatalog.isValidCriterionValue("not_observed"));
        assertFalse("null must be rejected", AssessmentRubricCatalog.isValidCriterionValue(null));

        // Test that AssessmentCalculator does not silently coerce 2.5 to 2
        Map<String, Object> invalidCriteria = new HashMap<>();
        invalidCriteria.put("1.1", 2.5);
        invalidCriteria.put("1.2", 2.5);
        invalidCriteria.put("1.3", "NOT_OBSERVED");
        AssessmentCalculation calc = AssessmentCalculator.calculate(invalidCriteria);
        DomainResult d1 = calc.getDomainResults().get(0);
        // Domain 1 must NOT count 2.5 as observed
        assertEquals(0, d1.getObservedCount());
        assertFalse(d1.isAvailable());

        // Test that AssessmentData.fromMap does not parse string "2" into integer 2
        Map<String, Object> rawMap = new HashMap<>();
        Map<String, Object> rawCriteria = new HashMap<>();
        rawCriteria.put("1.1", "2");
        rawCriteria.put("1.2", 2.5);
        rawMap.put("criteria", rawCriteria);
        AssessmentData data = AssessmentData.fromMap(rawMap);
        assertNotNull(data);
        assertEquals("String '2' must remain String", "2", data.getCriteria().get("1.1"));
        assertEquals("Fractional 2.5 must remain Double", Double.valueOf(2.5), data.getCriteria().get("1.2"));
    }

    @Test
    public void testExactlyFifteenCriterionKeysRequired() {
        // D. Exactly 15 criterion keys required
        List<String> approved = AssessmentRubricCatalog.getApprovedCriteriaKeys();
        assertEquals(15, approved.size());

        Map<String, Object> criteria = new HashMap<>();
        for (String key : approved) {
            criteria.put(key, 3);
        }
        assertEquals(15, criteria.keySet().size());
        assertTrue(criteria.keySet().containsAll(approved));

        // Adding an unexpected key violates exact 15 keys
        criteria.put("6.1", 3);
        assertFalse("16 keys must not equal approved 15 keys",
                criteria.keySet().equals(new java.util.HashSet<>(approved)));
    }

    @Test
    public void testNotObservedSemantics_NeverTreatedAsZero() {
        // Domain 1: two 4s and one NOT_OBSERVED -> average must be 4.0 (not (4+4+0)/3 = 2.67)
        Map<String, Object> criteria = new HashMap<>();
        criteria.put("1.1", 4);
        criteria.put("1.2", 4);
        criteria.put("1.3", "NOT_OBSERVED");

        criteria.put("2.1", 4);
        criteria.put("2.2", 4);
        criteria.put("2.3", 4);

        criteria.put("3.1", 4);
        criteria.put("3.2", 4);
        criteria.put("3.3", 4);

        criteria.put("4.1", 4);
        criteria.put("4.2", 4);
        criteria.put("4.3", 4);

        criteria.put("5.1", 4);
        criteria.put("5.2", 4);
        criteria.put("5.3", 4);

        AssessmentCalculation calc = AssessmentCalculator.calculate(criteria);
        assertTrue(calc.isOverallAvailable());
        assertEquals(14, calc.getObservedCriteriaCount());
        assertEquals(15, calc.getTotalCriteriaCount());
        assertEquals(93, calc.getCriteriaCoveragePercent());

        DomainResult d1 = calc.getDomainResults().get(0);
        assertTrue(d1.isAvailable());
        assertEquals(2, d1.getObservedCount());
        assertEquals(3, d1.getTotalCount());
        assertEquals(4.0, d1.getScore(), 0.001); // (4 + 4) / 2 = 4.0
    }

    @Test
    public void testDomainRule_LessThanTwoObservedMakesDomainIncomplete() {
        // Domain 1: only 1 observed criterion (1 out of 3)
        Map<String, Object> criteria = new HashMap<>();
        criteria.put("1.1", 4);
        criteria.put("1.2", "NOT_OBSERVED");
        criteria.put("1.3", "NOT_OBSERVED");

        criteria.put("2.1", 3);
        criteria.put("2.2", 3);
        criteria.put("2.3", 3);

        criteria.put("3.1", 3);
        criteria.put("3.2", 3);
        criteria.put("3.3", 3);

        criteria.put("4.1", 3);
        criteria.put("4.2", 3);
        criteria.put("4.3", 3);

        criteria.put("5.1", 3);
        criteria.put("5.2", 3);
        criteria.put("5.3", 3);

        AssessmentCalculation calc = AssessmentCalculator.calculate(criteria);

        // Domain 1 is incomplete (only 1 observed)
        DomainResult d1 = calc.getDomainResults().get(0);
        assertFalse(d1.isAvailable());
        assertNull(d1.getScore());
        assertEquals(1, d1.getObservedCount());

        // Domains 2..5 are available (4 available domains) -> overall is available!
        assertEquals(4, calc.getAvailableDomainCount());
        assertTrue(calc.isOverallAvailable());
        assertNotNull(calc.getOverallScore());
        assertEquals(3.0, calc.getOverallScore(), 0.001);
        assertEquals(AssessmentCalculation.LEVEL_READY, calc.getOverallLevel());
    }

    @Test
    public void testOverallRule_LessThanFourDomainsMakesOverallIncomplete() {
        Map<String, Object> criteria = new HashMap<>();
        for (String key : AssessmentRubricCatalog.getApprovedCriteriaKeys()) {
            criteria.put(key, "NOT_OBSERVED");
        }

        criteria.put("3.1", 4);
        criteria.put("3.2", 4);
        criteria.put("3.3", 4);

        criteria.put("4.1", 4);
        criteria.put("4.2", 4);
        criteria.put("4.3", 4);

        criteria.put("5.1", 4);
        criteria.put("5.2", 4);
        criteria.put("5.3", 4);

        AssessmentCalculation calc = AssessmentCalculator.calculate(criteria);
        assertEquals(3, calc.getAvailableDomainCount());
        assertFalse(calc.isOverallAvailable());
        assertNull(calc.getOverallScore());
        assertEquals(AssessmentCalculation.LEVEL_INSUFFICIENT_EVIDENCE, calc.getOverallLevel());
    }

    @Test
    public void testCompetenceLevelThresholds() {
        // STRONG: >= 3.50
        // READY: >= 2.75
        // DEVELOPING: >= 2.00
        // NEED SUPPORT: < 2.00

        Map<String, Object> criteria = new HashMap<>();
        for (String key : AssessmentRubricCatalog.getApprovedCriteriaKeys()) {
            criteria.put(key, 3);
        }
        // D1: 4, 3, 4 -> 3.667
        // D2: 4, 3, 4 -> 3.667
        // D3: 4, 3, 4 -> 3.667
        // D4: 3, 3, 3 -> 3.000
        // D5: 3, 3, 4 -> 3.333
        // Overall = (3.667 + 3.667 + 3.667 + 3.000 + 3.333) / 5 = 17.334 / 5 = 3.467 -> READY (< 3.50)
        criteria.put("1.1", 4); criteria.put("1.2", 3); criteria.put("1.3", 4);
        criteria.put("2.1", 4); criteria.put("2.2", 3); criteria.put("2.3", 4);
        criteria.put("3.1", 4); criteria.put("3.2", 3); criteria.put("3.3", 4);
        criteria.put("4.1", 3); criteria.put("4.2", 3); criteria.put("4.3", 3);
        criteria.put("5.1", 3); criteria.put("5.2", 3); criteria.put("5.3", 4);

        AssessmentCalculation calcReady = AssessmentCalculator.calculate(criteria);
        assertEquals(AssessmentCalculation.LEVEL_READY, calcReady.getOverallLevel());

        // Now boost D4 to (4, 3, 3) -> 3.333, Overall = (3.667 + 3.667 + 3.667 + 3.333 + 3.333) / 5 = 17.667 / 5 = 3.533 -> STRONG
        criteria.put("4.1", 4);
        AssessmentCalculation calcStrong = AssessmentCalculator.calculate(criteria);
        assertEquals(AssessmentCalculation.LEVEL_STRONG, calcStrong.getOverallLevel());

        // Test DEVELOPING: e.g. 2.00
        for (String key : AssessmentRubricCatalog.getApprovedCriteriaKeys()) {
            criteria.put(key, 2);
        }
        AssessmentCalculation calcDev = AssessmentCalculator.calculate(criteria);
        assertEquals(2.0, calcDev.getOverallScore(), 0.001);
        assertEquals(AssessmentCalculation.LEVEL_DEVELOPING, calcDev.getOverallLevel());

        // Test NEED SUPPORT: e.g. 1.00
        for (String key : AssessmentRubricCatalog.getApprovedCriteriaKeys()) {
            criteria.put(key, 1);
        }
        AssessmentCalculation calcSupport = AssessmentCalculator.calculate(criteria);
        assertEquals(1.0, calcSupport.getOverallScore(), 0.001);
        assertEquals(AssessmentCalculation.LEVEL_NEED_SUPPORT, calcSupport.getOverallLevel());
    }
}
