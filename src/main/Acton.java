package main;

import main.ast.node.Program;
import main.compileError.CompileErrorException;
import main.visitor.nameAnalyser.NameAnalyser;
import main.visitor.tyoeChecker.ExpressionChecker;
import main.visitor.tyoeChecker.TypeChecker;
import org.antlr.v4.runtime.*;
import parsers.actonLexer;
import parsers.actonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

// Visit https://stackoverflow.com/questions/26451636/how-do-i-use-antlr-generated-parser-and-lexer
public class Acton {
    public static void main(String[] args) throws IOException {
        CharStream reader = CharStreams.fromFileName(args[0]);
        actonLexer lexer = new actonLexer(reader);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        actonParser parser = new actonParser(tokens);
        Program program = null;
        try{
            program = parser.program().p; // program is starting production rule
            NameAnalyser nameAnalyser = new NameAnalyser();
            nameAnalyser.visit(program);
            if( nameAnalyser.numOfErrors() > 0 )
                throw new CompileErrorException();
        }
        catch(CompileErrorException compileError){
        }
        TypeChecker typeChecker = new TypeChecker();
        typeChecker.visit(program);
        ArrayList<String> errors = typeChecker.getErrors();
//        Collections.sort(errors);
        for (String error : errors) {
            System.out.println(error);
        }
    }
}