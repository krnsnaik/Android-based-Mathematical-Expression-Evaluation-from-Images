package edu.nitk.cse.mathEvaluator.math;

import com.fathzer.soft.javaluator.DoubleEvaluator;



public class ExpressionParser {

    public static Double parse(String exp) throws Exception {
        exp = preprocessExpression(exp);

        DoubleEvaluator evaluator = new DoubleEvaluator();
        Double result = evaluator.evaluate(exp);
        return result;
    }

    private static String preprocessExpression(String exp) {
        return exp.replace('x', '*')
                .replace("--", "-");
    }
}
