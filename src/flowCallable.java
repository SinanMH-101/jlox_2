package src;

import java.util.List;

interface flowCallable {
    int arity();
    Object call(Interpreter interpreter, List<Object> arguments);
}
