package io.github.frankdattalo.flisp.interpreter;

import io.github.frankdattalo.flisp.ast.*;
import io.github.frankdattalo.flisp.parser.Parser;
import io.github.frankdattalo.flisp.parser.ParserException;
import javaslang.Tuple2;
import javaslang.collection.List;

public class SpecialFormInterpreter implements Interpreter<FlispExpression> {

    private static final SpecialFormInterpreter instance;

    static {
        instance = new SpecialFormInterpreter();
    }

    public boolean isSpecialForm(FlispSymbol symbol) {
        switch(symbol.getValue()) {
            case "def":
            case "fn":
            case "literal":
            case "import":
            case "export":
            case "case": return true;
            default: return false;
        }
    }

    @Override
    public FlispExpression interpret(Environment environment,
                                     FlispExpression expression) {

        if(!(expression instanceof  FlispList)) {
            throw new InterpreterException("Expected list, but found " +
                    expression.getClass());
        }

        FlispList flist = (FlispList) expression;

        List<FlispExpression> list = flist.getList();


        if(!(list.head() instanceof FlispSymbol)) {
            throw new InterpreterException("Expected symbol, but found " +
                    list.head().getClass());
        }

        FlispSymbol fsymbol = (FlispSymbol) list.head();

        switch (fsymbol.getValue()) {
        case "def": {
            if(list.length() != 3) {
                throw new InterpreterException("def takes two " +
                        "parameters, a symbol and it's definition" +
                        ".");
            }

            if(!(list.get(1) instanceof FlispSymbol)) {
                throw new InterpreterException("first parameter " +
                        "to def must be a symbol");
            }

            environment.put((FlispSymbol) list.get(1),
                    ExpressionInterpreter.run(
                            environment,
                            list.get(2)));

            return FlispList.EMPTY;
        }
        case "fn": {
            if(list.length() != 3) {
                throw new InterpreterException("lambda takes two " +
                        "parameters, a list of formal parameters," +
                        " and a function body.");
            }

            if(!(list.get(1) instanceof FlispList)) {
                throw new InterpreterException("Parameters must be a list");
            }

            FlispList params = (FlispList) list.get(1);

            return new FlispLambda(
                    environment,
                    params.getList().map(i -> {
                        if(!(i instanceof FlispSymbol)) {
                            throw new InterpreterException("lambda " +
                                    "takes two parameters, a list" +
                                    " of formal parameters, and a" +
                                    " function body.");
                        }

                        return (FlispSymbol) i;
                    }),
                    list.get(2));
        }
        case "literal": {
            if(list.length() != 2) {
                throw new InterpreterException("literal takes one" +
                        " parameter, which is the expression to " +
                        "interpret as a literal");
            }

            return list.get(1);
        }
        case "case": {
            for(FlispExpression expr: list.tail()) {
                if(!(expr instanceof FlispList)) {
                    throw new InterpreterException("Arguments to " +
                            "case are a list of boolean " +
                            "expression pairs");
                }

                List<FlispExpression> tf = ((FlispList) expr)
                        .getList();

                if(tf.length() != 2) {
                    throw new InterpreterException("Cases should " +
                            "be a pair of a predicate and " +
                            "expression");
                }

                FlispExpression predicate = ExpressionInterpreter
                        .run(environment, tf.get(0));

                if(predicate instanceof  FlispSymbol &&
                        ((FlispSymbol) predicate).getValue()
                        .equals("true")) {

                    return ExpressionInterpreter.run(
                            environment, list.get(1));
                }
            }

            throw new InterpreterException("No true cases for " +
                    "case");
        }
        case "import": {
            if(list.length() != 2 || !(list.get(1) instanceof FlispString)) {
                throw new InterpreterException("import takes a file location " +
                        "as a string as an argument");
            }

            try {
                FlispList program =
                        Parser.fromFile(((FlispString) list.get(1))
                        .getValue()).parse();

                Environment exports = ProgramInterpreter.run(program);

                for (Tuple2<FlispSymbol, FlispExpression> export : exports.getExports()) {
                    environment.put(export._1(), export._2());
                }

                return FlispList.EMPTY;

            } catch (ParserException e) {
                throw new InterpreterException(e.getLocalizedMessage());
            }
        }
        case "export": {
            if(list.length() != 2 || !(list.get(1) instanceof FlispSymbol)) {
                throw new InterpreterException("export takes a file location " +
                        "as a symbol as an argument");
            }

            environment.export((FlispSymbol) list.get(1));

            return FlispList.EMPTY;
        }
        default: throw new InterpreterException("Invalid type" +
                    " passe to interpret");
        }
    }

    public static boolean specialForm(FlispExpression head) {
        return instance.isSpecialForm((FlispSymbol) head);
    }

    public static FlispExpression run(Environment environment,
                                      FlispExpression expression) {
        return instance.interpret(environment, expression);
    }
}
