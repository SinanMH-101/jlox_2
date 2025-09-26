package flow;

import static flow.TokenType.*;

import java.util.List;

public class Parser {
    private static class ParseError extends RuntimeException {
    }

    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    Expr parse() {
        try {
            return expression();
        } catch (ParseError error) {
            return null;
        }
    }

    // expression → flow ;
    private Expr expression() {
        return flow();
    }

    // flow → primary ( (CONFLUENCE | BLOCKADE) primary )* ;
    private Expr flow() {
        Expr expr = primary();

        while (match(CONFLUENCE, BLOCKADE)) {
            Token operator = previous();
            Expr right = primary();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    // primary → FLOW | NUMBER | STRING | "(" expression ")" ;
    // (Keep NUMBER/STRING only if you left those tokens in TokenType)
    private Expr primary() {
        if (match(FLOW)) {
            return new Expr.LiteralFlow(previous().literal);
        }
        if (match(NUMBER)) {
            return new Expr.Literal(previous().literal);
        }
        if (match(STRING)) {
            return new Expr.Literal(previous().literal);
        }
        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        throw error(peek(), "Expect expression.");
    }

    /* ------------ helpers ------------ */

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type))
            return advance();
        throw error(peek(), message);
    }

    private boolean check(TokenType type) {
        if (isAtEnd())
            return false;
        return peek().type == type;
    }

    private Token advance() {
        if (!isAtEnd())
            current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private ParseError error(Token token, String message) {
        myFlow.error(token, message); // keep your existing reporter
        return new ParseError();
    }

    // // With only expressions, synchronization is trivial.
    // private void synchronize() {
    // while (!isAtEnd()) {
    // if (previous().type == SEMICOLON) return;
    // advance();
    // }
    // }
}
