package flow;

enum TokenType {
    // Single-character tokens.
    LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE,
     MINUS, PLUS, SEMICOLON, SLASH, STAR,

    // One or two character tokens.
     EQUAL, CONFLUENCE, BLOCKADE,

    // Literals.
    IDENTIFIER, STRING, NUMBER, FLOW,
    
    // Keywords.
    PRINT, VAR,

    EOF
}