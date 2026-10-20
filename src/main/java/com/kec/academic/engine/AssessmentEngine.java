package com.kec.academic.engine;

/**
 * Pure business logic for KEC R2024 evaluation. No database access here,
 * so every rule can be unit-tested in isolation.
 */
public final class AssessmentEngine {

    /** Result of evaluating one course for one student. */
    public static final class CourseResult {
        public final Double ciaMark;      // weighted CIA contribution out of 100, null if incomplete
        public final Double eseMark;      // ESE out of 100, null if not entered
        public final Double totalMark;    // final aggregate out of 100, null if not computable
        public final String grade;        // O, A+, A, B+, B, RA, SA, AB
        public final int gradePoint;      // 0..10
        public final boolean pass;
        public final String attendanceStatus; // ELIGIBLE, CONDONATION, SHORTAGE

        CourseResult(Double cia, Double ese, Double total, String grade, int gp,
                     boolean pass, String attendanceStatus) {
            this.ciaMark = cia;
            this.eseMark = ese;
            this.totalMark = total;
            this.grade = grade;
            this.gradePoint = gp;
            this.pass = pass;
            this.attendanceStatus = attendanceStatus;
        }
    }

    private AssessmentEngine() {
    }

    /**
     * Attendance percentage. Returns 100 when no classes have been held yet.
     */
    public static double attendancePercent(int held, int attended) {
        if (held <= 0) {
            return 100.0;
        }
        return attended * 100.0 / held;
    }

    /**
     * Attendance rule:
     *   >= 75%        -> ELIGIBLE
     *   65 - 74.9%    -> CONDONATION (eligible only with medical condonation)
     *   < 65%         -> SHORTAGE (SA, barred from ESE)
     */
    public static String attendanceStatus(double percent, boolean medicalCondonation) {
        if (percent >= 75.0) {
            return "ELIGIBLE";
        }
        if (percent >= 65.0) {
            return medicalCondonation ? "ELIGIBLE" : "CONDONATION";
        }
        return "SHORTAGE";
    }

    /** Weighted CIA score out of 100, from the components that have been entered. */
    public static Double ciaScore(Double cia1, Double cia2, Double ciaAssignment) {
        if (cia1 == null || cia2 == null || ciaAssignment == null) {
            return null;
        }
        // CIA internal = average of the two mid-semester tests and the assignment/lab score.
        // Mid-semester tests carry 2/3 of the weight, the assignment/lab carries 1/3.
        double midSem = (cia1 + cia2) / 2.0;
        return round2(midSem * 2.0 / 3.0 + ciaAssignment / 3.0);
    }

    /**
     * Converts a 100-scale aggregate into a letter grade and grade point.
     * RA covers both aggregate below 50 and ESE failure (handled by caller).
     */
    public static String letterGrade(double total) {
        if (total >= 91) return "O";
        if (total >= 81) return "A+";
        if (total >= 71) return "A";
        if (total >= 61) return "B+";
        if (total >= 50) return "B";
        return "RA";
    }

    public static int gradePoint(String grade) {
        switch (grade) {
            case "O":  return 10;
            case "A+": return 9;
            case "A":  return 8;
            case "B+": return 7;
            case "B":  return 6;
            default:   return 0; // RA, SA, AB
        }
    }

    /**
     * Full evaluation of one enrolment.
     *
     * @param type         THEORY / PRACTICAL / EMBEDDED
     * @param cia1         CIA test 1 (nullable)
     * @param cia2         CIA test 2 (nullable)
     * @param ciaAssign    assignment / quiz / lab (nullable)
     * @param ese          end-semester mark (nullable)
     * @param absentEse    true if the student was absent for ESE (AB)
     * @param held         classes held
     * @param attended     classes attended
     * @param condonation  medical condonation approved
     */
    public static CourseResult evaluate(EvaluationType type, Double cia1, Double cia2,
                                        Double ciaAssign, Double ese, boolean absentEse,
                                        int held, int attended, boolean condonation) {

        double attPct = attendancePercent(held, attended);
        String attStatus = attendanceStatus(attPct, condonation);

        // Attendance shortage overrides everything: SA, barred from ESE.
        if ("SHORTAGE".equals(attStatus)) {
            return new CourseResult(null, ese, null, "SA", 0, false, attStatus);
        }

        Double cia = ciaScore(cia1, cia2, ciaAssign);

        // ESE absence: grade AB, no pass.
        if (absentEse) {
            return new CourseResult(cia, null, null, "AB", 0, false, attStatus);
        }

        // Still in progress: not enough marks to produce a result.
        if (cia == null || ese == null) {
            return new CourseResult(cia, ese, null, "-", 0, false, attStatus);
        }

        double eseMinOutOf100 = type.getEseMinPercent();
        double total = round2(cia * type.getCiaWeight() + ese * type.getEseWeight());

        boolean eseFailed = ese < eseMinOutOf100;
        boolean aggregateFailed = total < type.getOverallPassMark();

        if (eseFailed || aggregateFailed) {
            return new CourseResult(cia, ese, total, "RA", 0, false, attStatus);
        }

        String grade = letterGrade(total);
        return new CourseResult(cia, ese, total, grade, gradePoint(grade), true, attStatus);
    }

    /**
     * SGPA = sum(credits * grade point) / sum(credits).
     * Credits list and grade point list must be the same length.
     */
    public static double sgpa(double[] credits, int[] gradePoints) {
        if (credits.length != gradePoints.length) {
            throw new IllegalArgumentException("credits and grade points must be the same length");
        }
        double weighted = 0;
        double totalCredits = 0;
        for (int i = 0; i < credits.length; i++) {
            weighted += credits[i] * gradePoints[i];
            totalCredits += credits[i];
        }
        if (totalCredits == 0) {
            return 0.0;
        }
        return round2(weighted / totalCredits);
    }

    /** Equivalent percentage = SGPA * 10. */
    public static double equivalentPercentage(double sgpa) {
        return round2(sgpa * 10.0);
    }

    static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
