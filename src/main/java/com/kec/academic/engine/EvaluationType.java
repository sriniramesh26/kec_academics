package com.kec.academic.engine;

/**
 * Course types and their KEC R2024 weightage / threshold rules.
 * Marks are on a 0-100 scale for each component; the final mark is out of 100.
 */
public enum EvaluationType {

    //            CIA%  ESE%  ESE min (of 100 scale) overall pass
    THEORY(      0.40, 0.60, 45.0, 50.0),
    PRACTICAL(   0.60, 0.40, 50.0, 50.0),
    EMBEDDED(    0.50, 0.50, 45.0, 50.0);

    private final double ciaWeight;
    private final double eseWeight;
    /** Minimum ESE percentage required (e.g. 45 means 27/60 for THEORY). */
    private final double eseMinPercent;
    private final double overallPassMark;

    EvaluationType(double ciaWeight, double eseWeight, double eseMinPercent, double overallPassMark) {
        this.ciaWeight = ciaWeight;
        this.eseWeight = eseWeight;
        this.eseMinPercent = eseMinPercent;
        this.overallPassMark = overallPassMark;
    }

    public double getCiaWeight() {
        return ciaWeight;
    }

    public double getEseWeight() {
        return eseWeight;
    }

    public double getEseMinPercent() {
        return eseMinPercent;
    }

    public double getOverallPassMark() {
        return overallPassMark;
    }







    public static EvaluationType fromString(String s) {
        return EvaluationType.valueOf(s.trim().toUpperCase());
    }
}
