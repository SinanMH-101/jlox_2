package test;

import java.util.List;

class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
    final Environment globals = new Environment();
    private Environment environment = globals;

    private double rainfall = 1.0;

    static final class FlowSeries {
        final double[] days; // length 7

        FlowSeries(double[] days) {
            this.days = days;
        }
    }

    static final class Flow {
        final double cubicPerSecond; // already multiplied by rainfall

        Flow(double v) {
            this.cubicPerSecond = v;
        }
    }

    void setRainfall(double r) {
        this.rainfall = r;
        // keep a global numeric 'rainfall' available to programs
        globals.define("rainfall", r); // define() overwrites in your Environment
    }

    Interpreter() {
        globals.define("clock", new LoxCallable() {
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return (double) System.currentTimeMillis() / 1000.0;
            }

            @Override
            public String toString() {
                return "<native fn>";
            }
        });

        globals.define("rainfall", rainfall);
    }

    void interpret(List<Stmt> statements) {
        try {
            for (Stmt statement : statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
            myLox.runtimeError(error);
        }
    }

    // ===== Expressions =====

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        Object v = expr.value;

        // "Nx" -> FlowSeries that decays over 7 days with rainfall scaling
        if (v instanceof String s && s.endsWith("x")) {
            String core = s.substring(0, s.length() - 1);
            double coeff = Double.parseDouble(core);
            return seriesFromCoeff(coeff);
        }

        return v; // numbers, strings, booleans as-is
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case BANG:
                return !isTruthy(right);
            case MINUS:
                checkNumberOperand(expr.operator, right);
                return -(double) right;
        }
        return null;
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        return environment.get(expr.name);
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);
        environment.assign(expr.name, value);
        return value;
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case CONFLUENCE: { // ~~ : sum day-by-day
                FlowSeries L = toSeries(expr.operator, left);
                FlowSeries R = toSeries(expr.operator, right);
                return addSeries(L, R);
            }
            case BLOCKADE: { // !~ : subtract day-by-day
                FlowSeries L = toSeries(expr.operator, left);
                FlowSeries R = toSeries(expr.operator, right);
                return subSeries(L, R);
            }

            // ----- Normal arithmetic still supported -----
            case MINUS: {
                checkNumberOperands(expr.operator, left, right);
                return (double) left - (double) right;
            }
            case PLUS: {
                if (left instanceof Double && right instanceof Double) {
                    return (double) left + (double) right;
                }
                if (left instanceof String && right instanceof String) {
                    return (String) left + (String) right;
                }
                throw new RuntimeError(expr.operator, "Operands must be two numbers or two strings.");
            }
            case SLASH: {
                checkNumberOperands(expr.operator, left, right);
                double denom = (double) right;
                if (denom == 0.0)
                    throw new RuntimeError(expr.operator, "Division by zero.");
                return (double) left / denom;
            }
            case STAR: {
                checkNumberOperands(expr.operator, left, right);
                return (double) left * (double) right;
            }

            case GREATER: {
                checkNumberOperands(expr.operator, left, right);
                return (double) left > (double) right;
            }
            case GREATER_EQUAL: {
                checkNumberOperands(expr.operator, left, right);
                return (double) left >= (double) right;
            }
            case LESS: {
                checkNumberOperands(expr.operator, left, right);
                return (double) left < (double) right;
            }
            case LESS_EQUAL: {
                checkNumberOperands(expr.operator, left, right);
                return (double) left <= (double) right;
            }
            case BANG_EQUAL:
                return !isEqual(left, right);
            case EQUAL_EQUAL:
                return isEqual(left, right);
        }
        return null;
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left);

        if (expr.operator.type == TokenType.OR) {
            if (isTruthy(left))
                return left;
        } else {
            if (!isTruthy(left))
                return left;
        }
        return evaluate(expr.right);
    }

    @Override
    public Object visitCallExpr(Expr.Call expr) {
        Object callee = evaluate(expr.callee);

        List<Object> arguments = new java.util.ArrayList<>();
        for (Expr argument : expr.arguments) {
            arguments.add(evaluate(argument));
        }

        if (!(callee instanceof LoxCallable)) {
            throw new RuntimeError(expr.paren, "Can only call functions and classes.");
        }

        LoxCallable function = (LoxCallable) callee;
        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren,
                    "Expected " + function.arity() + " arguments but got " + arguments.size() + ".");
        }
        return function.call(this, arguments);
    }

    // ===== Statements =====

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        String label = extractName(stmt.expression);
        Object value = evaluate(stmt.expression);

        String out;
        if (value instanceof FlowSeries fs) {
            out = formatSeries(fs);
        } else if (value instanceof Double d) {
            out = trimNum(d); // plain numbers: no unit
        } else {
            out = stringify(value);
        }

        if (label != null) {
            System.out.println(label + ": " + out);
        } else {
            System.out.println(out);
        }
        return null;
    }

    private String extractName(Expr expr) {
        if (expr instanceof Expr.Variable v)
            return v.name.lexeme;
        if (expr instanceof Expr.Assign a)
            return a.name.lexeme;
        if (expr instanceof Expr.Grouping g)
            return extractName(g.expression);
        return null;
    }

    /* ===== single-line formatting ===== */
    private String formatValue(Object v) {
        if (v == null)
            return "nil";

        if (v instanceof Flow f) {
            return trimNum(f.cubicPerSecond) + " m3/s";
        }

        if (v instanceof Double d) {
            return trimNum(d); // plain numbers (e.g., gates) => no unit
        }

        if (v instanceof java.util.List<?> list) {
            // Join list elements on one line
            java.util.List<String> parts = new java.util.ArrayList<>();
            for (Object elem : list) {
                if (elem instanceof Flow f) {
                    parts.add(trimNum(f.cubicPerSecond) + " m3/s");
                } else if (elem instanceof Double dd) {
                    parts.add(trimNum(dd));
                } else {
                    parts.add(String.valueOf(elem));
                }
            }
            return String.join(" ", parts);
        }

        if (v instanceof String s)
            return s;
        if (v instanceof Boolean b)
            return b.toString();

        return v.toString();
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = null;
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }
        environment.define(stmt.name.lexeme, value);
        return null;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    void executeBlock(List<Stmt> statements, Environment environment) {
        Environment previous = this.environment;
        try {
            this.environment = environment;
            for (Stmt statement : statements) {
                execute(statement);
            }
        } finally {
            this.environment = previous;
        }
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        while (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.body);
        }
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        LoxFunction function = new LoxFunction(stmt, environment);
        environment.define(stmt.name.lexeme, function);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        Object value = null;
        if (stmt.value != null)
            value = evaluate(stmt.value);
        throw new Return(value);
    }

    // ===== Helpers =====
    // Make a 7-day series from a flow coefficient like 4.0x
    // Day t = max(coeff - t, 1) * rainfall for t=0..6
    private FlowSeries seriesFromCoeff(double coeff) {
        double[] arr = new double[7];
        for (int i = 0; i < 7; i++) {
            double base = Math.max(coeff - i, 1.0);
            arr[i] = base * rainfall;
        }
        return new FlowSeries(arr);
    }

    // Promote various values to a FlowSeries (for ~~ and !~)
    private FlowSeries toSeries(Token op, Object v) {
        if (v instanceof FlowSeries fs)
            return fs;

        if (v instanceof Double d) {
            // constant series (plain numbers treated as m3/s constants)
            double[] a = new double[7];
            for (int i = 0; i < 7; i++)
                a[i] = d;
            return new FlowSeries(a);
        }

        if (v instanceof String s && s.endsWith("x")) {
            String core = s.substring(0, s.length() - 1);
            try {
                double coeff = Double.parseDouble(core);
                return seriesFromCoeff(coeff);
            } catch (NumberFormatException e) {
                throw new RuntimeError(op, "Invalid flow literal: " + s);
            }
        }

        throw new RuntimeError(op, "Expected a flow literal (e.g., 3x) or number, got: " + stringify(v));
    }

    // Elementwise ops
    private FlowSeries addSeries(FlowSeries a, FlowSeries b) {
        double[] out = new double[7];
        for (int i = 0; i < 7; i++)
            out[i] = a.days[i] + b.days[i];
        return new FlowSeries(out);
    }

    private FlowSeries subSeries(FlowSeries a, FlowSeries b) {
        double[] out = new double[7];
        for (int i = 0; i < 7; i++)
            out[i] = a.days[i] - b.days[i];
        return new FlowSeries(out);
    }

    // Pretty for printing a series on one line: "3 m3/s, 2 m3/s, 1 m3/s, ..."
    private String formatSeries(FlowSeries fs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fs.days.length; i++) {
            String t = trimNum(fs.days[i]) + " m3/s";
            if (i > 0)
                sb.append(", ");
            sb.append(t);
        }
        return sb.toString();
    }

    private String trimNum(double d) {
        String t = Double.toString(d);
        if (t.endsWith(".0"))
            t = t.substring(0, t.length() - 2);
        return t;
    }

    private double toFlow(Token op, Object v) {
        if (v instanceof Flow f)
            return f.cubicPerSecond;
        if (v instanceof Double d)
            return d; // allow plain numbers to mix with flows
        if (v instanceof String s && s.endsWith("x")) {
            String core = s.substring(0, s.length() - 1);
            try {
                return Double.parseDouble(core) * rainfall;
            } catch (NumberFormatException e) {
                throw new RuntimeError(op, "Invalid flow literal: " + s);
            }
        }
        throw new RuntimeError(op, "Expected a flow (e.g., 3.2x) or number, got: " + stringify(v));
    }

    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    private boolean isTruthy(Object object) {
        if (object == null)
            return false;
        if (object instanceof Boolean)
            return (boolean) object;
        return true;
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null)
            return true;
        if (a == null)
            return false;
        return a.equals(b);
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

    // ===== Flow helpers =====

    private boolean isFlowVal(Object v) {
        return (v instanceof String) && ((String) v).endsWith("x");
    }

    private double asFlow(Token op, Object v) {
        if (v instanceof Double d)
            return d;
        if (isFlowVal(v)) {
            String s = (String) v; // e.g., "4x"
            String core = s.substring(0, s.length() - 1);
            try {
                return Double.parseDouble(core) * rainfall;
            } catch (NumberFormatException e) {
                throw new RuntimeError(op, "Invalid flow literal: " + s);
            }
        }
        throw new RuntimeError(op, "Expected a flow like 3.2x (or a number), got: " + stringify(v));
    }

    private String formatFlow(double v) {
        // normalize trailing .0
        String text = Double.toString(v);
        if (text.endsWith(".0"))
            text = text.substring(0, text.length() - 2);
        return text + "x";
    }

    String stringify(Object object) {
        if (object == null)
            return "nil";
        if (object instanceof Double d)
            return trimNum(d);
        if (object instanceof String s)
            return s;
        if (object instanceof Boolean b)
            return b.toString();
        if (object instanceof FlowSeries fs)
            return formatSeries(fs);
        return object.toString();
    }

}
