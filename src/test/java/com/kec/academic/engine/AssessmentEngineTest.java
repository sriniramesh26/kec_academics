package com.kec.academic.engine;

import com.kec.academic.engine.AssessmentEngine.CourseResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Rule checks for KEC R2024 that run without a database. */
class AssessmentEngineTest {

    @Test
    void attendanceBands() {
        assertEquals("ELIGIBLE", AssessmentEngine.attendanceStatus(75.0, false));
        assertEquals("CONDONATION", AssessmentEngine.attendanceStatus(70.0, false));
        assertEquals("ELIGIBLE", AssessmentEngine.attendanceStatus(70.0, true));
        assertEquals("SHORTAGE", AssessmentEngine.attendanceStatus(64.9, true));
    }

    @Test
    void shortageOverridesMarks() {
        CourseResult r = AssessmentEngine.evaluate(EvaluationType.THEORY,
                95.0, 95.0, 95.0, 95.0, false, 100, 60, false);
        assertEquals("SA", r.grade);
        assertEquals(0, r.gradePoint);
        assertFalse(r.pass);
    }

    @Test
    void theoryWeightageAndGrade() {
        // CIA = (80+80)/2*2/3 + 90/3 = 53.33 + 30 = 83.33 ; total = 0.4*83.33 + 0.6*70 = 75.33
        CourseResult r = AssessmentEngine.evaluate(EvaluationType.THEORY,
                80.0, 80.0, 90.0, 70.0, false, 40, 36, false);
        assertEquals("A", r.grade);
        assertEquals(8, r.gradePoint);
        assertTrue(r.pass);
    }

    @Test
    void eseBelowThresholdFailsEvenWithHighAggregate() {
        // ESE 40 < 45 threshold for THEORY -> RA despite high CIA
        CourseResult r = AssessmentEngine.evaluate(EvaluationType.THEORY,
                100.0, 100.0, 100.0, 40.0, false, 40, 40, false);
        assertEquals("RA", r.grade);
        assertFalse(r.pass);
    }

    @Test
    void practicalUsesSixtyFortyWeights() {
        // CIA 90, ESE 60 -> 0.6*90 + 0.4*60 = 78 -> A
        CourseResult r = AssessmentEngine.evaluate(EvaluationType.PRACTICAL,
                90.0, 90.0, 90.0, 60.0, false, 40, 40, false);
        assertEquals("A", r.grade);
        assertEquals(78.0, r.totalMark, 0.001);
    }

    @Test
    void absentIsAb() {
        CourseResult r = AssessmentEngine.evaluate(EvaluationType.EMBEDDED,
                80.0, 80.0, 80.0, null, true, 40, 40, false);
        assertEquals("AB", r.grade);
        assertEquals(0, r.gradePoint);
    }

    @Test
    void sgpaAndPercentage() {
        double[] credits = {4, 3, 2};
        int[] gps = {10, 8, 6};
        double sgpa = AssessmentEngine.sgpa(credits, gps);
        // (40 + 24 + 12) / 9 = 8.44
        assertEquals(8.44, sgpa, 0.001);
        assertEquals(84.4, AssessmentEngine.equivalentPercentage(sgpa), 0.001);
    }

    @Test
    void gradeBoundaries() {
        assertEquals("O", AssessmentEngine.letterGrade(91));
        assertEquals("A+", AssessmentEngine.letterGrade(90.99));
        assertEquals("B", AssessmentEngine.letterGrade(50));
        assertEquals("RA", AssessmentEngine.letterGrade(49.99));
    }
}
