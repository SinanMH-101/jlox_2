package flow;

class Interpreter implements Expr.Visitor<Object> {

    void interpret(Expr expression) {
        try {
            Object value = evaluate(expression);
            System.out.print("Output flow: " + stringify(value));
        } catch (RuntimeError error) {
            myFlow.runtimeError(error);
        }
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case CONFLUENCE: // ~~ operator
                // checkNumberOperands(expr.operator, left, right);

                String leftStr = left.toString();
                String rightStr = right.toString();

                // remove trailing 'x' if present
                if (leftStr.endsWith("x")) {
                    leftStr = leftStr.substring(0, leftStr.length() - 1);
                }
                if (rightStr.endsWith("x")) {
                    rightStr = rightStr.substring(0, rightStr.length() - 1);
                }

                double leftVal = Double.parseDouble(leftStr);
                double rightVal = Double.parseDouble(rightStr);

                double result = leftVal + rightVal;
                return result + "x";

            case BLOCKADE: // !~ operator
                String lStr = left.toString();
                String rStr = right.toString();

                if (lStr.endsWith("x")) {
                    lStr = lStr.substring(0, lStr.length() - 1);
                }
                if (rStr.endsWith("x")) {
                    rStr = rStr.substring(0, rStr.length() - 1);
                }

                double lVal = Double.parseDouble(lStr);
                double rVal = Double.parseDouble(rStr);

                double diff = lVal - rVal;
                return diff + "x";

            //kept for future, seems useful to have
            case PLUS: {
                checkNumberOperands(expr.operator, left, right);
                return (double) left + (double) right;
            }

            case MINUS: {
                checkNumberOperands(expr.operator, left, right);
                return (double) left - (double) right;
            }

            case STAR: {
                checkNumberOperands(expr.operator, left, right);
                return (double) left * (double) right;
            }

            case SLASH: {
                checkNumberOperands(expr.operator, left, right);
                double denom = (double) right;
                if (denom == 0.0) {
                    throw new RuntimeError(expr.operator, "Division by zero.");
                }
                return (double) left / denom;
            }
        }

        // Unreachable for now
        return null;
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case MINUS: // kept just incase for now
                checkNumberOperand(expr.operator, right);
                return -(double) right;
        }

        return null;
    }

    /* ---------- helpers ---------- */

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double)
            return;
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    private void checkNumberOperands(Token operator, Object left, Object right) {
        if (left instanceof Double && right instanceof Double)
            return;
        throw new RuntimeError(operator, "Operands must be numbers.");
    }

    private String stringify(Object object) {
        if (object == null)
            return "nil";

        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text + "x"; // append 'x' to show it's a flow literal
        }

        return object.toString();
    }
}
