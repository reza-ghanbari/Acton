package main.visitor.tyoeChecker;

import main.ast.node.Main;
import main.ast.node.Program;
import main.ast.node.declaration.ActorDeclaration;
import main.ast.node.declaration.ActorInstantiation;
import main.ast.node.declaration.VarDeclaration;
import main.ast.node.declaration.handler.HandlerDeclaration;
import main.ast.node.declaration.handler.InitHandlerDeclaration;
import main.ast.node.declaration.handler.MsgHandlerDeclaration;
import main.ast.node.expression.*;
import main.ast.node.expression.operators.BinaryOperator;
import main.ast.node.expression.values.BooleanValue;
import main.ast.node.expression.values.IntValue;
import main.ast.node.expression.values.StringValue;
import main.ast.node.statement.*;
import main.ast.type.Type;
import main.ast.type.actorType.ActorType;
import main.ast.type.arrayType.ArrayType;
import main.ast.type.noType.NoType;
import main.ast.type.primitiveType.BooleanType;
import main.ast.type.primitiveType.IntType;
import main.ast.type.primitiveType.StringType;
import main.symbolTable.SymbolTable;
import main.symbolTable.SymbolTableActorItem;
import main.symbolTable.SymbolTableHandlerItem;
import main.symbolTable.SymbolTableItem;
import main.symbolTable.itemException.ItemNotFoundException;
import main.symbolTable.symbolTableVariableItem.SymbolTableActorVariableItem;
import main.symbolTable.symbolTableVariableItem.SymbolTableVariableItem;
import main.visitor.VisitorImpl;

import java.util.ArrayList;

public class TypeChecker extends VisitorImpl {
    SymbolTable root = SymbolTable.root;
    ArrayList<String> errors = new ArrayList<>();

    private boolean isChildOf(ActorDeclaration a,ActorDeclaration b){
        while (b!=null){
            if(a.getName().getName().equals(b.getName().getName())){
                return true;
            }
            if(b.getParentName()!=null){
                try {
                    b = ((SymbolTableActorItem)root.get(SymbolTableActorItem.STARTKEY+b.getParentName().getName())).getActorDeclaration();
                }
                catch (ItemNotFoundException e){
                    // compile err: 4
                }
            }
            else break;
        }
        return false;
    }

    private boolean isSubType(Type a,Type b){
        // true if a<:b
        if(a instanceof NoType){
            return true;
        }
        else if(b instanceof NoType){
            return false;
        }
        else if(a instanceof BooleanType){
            return (b instanceof BooleanType);
        }
        else if(a instanceof IntType){
            return (b instanceof IntType);
        }
        else if(a instanceof StringType){
            return (b instanceof StringType);
        }
        else if(a instanceof ArrayType){
            return (b instanceof ArrayType);
        }
        else if(a instanceof ActorType){
            if(b instanceof ActorType){
                return isChildOf(((ActorType) a).getActorDeclaration(),((ActorType) b).getActorDeclaration());
            }
        }
        return false;
    }

    private boolean isActorExist(String name){
        try{
            root.get(SymbolTableActorItem.STARTKEY + name);
            return true;
        }
        catch (ItemNotFoundException e){
            return false;
        }
    }

    private Type binaryExpressionTypeCheck(Type left, Type right, BinaryOperator operator) {

    }

    private Type binaryExpressionTypeCheck(Type type, BinaryOperator operator) {

    }

    private Type typeCheck(Expression expression,SymbolTable handlerSym,SymbolTable actorSym){
        if(expression instanceof BooleanValue || expression instanceof IntValue || expression instanceof StringValue){
            return expression.getType();
        }
        else if(expression instanceof Self){
            try {
                SymbolTableActorItem item = (SymbolTableActorItem) root.get(SymbolTableActorItem.STARTKEY+actorSym.getName());
                ActorType type = new ActorType(item.getActorDeclaration().getName());
                type.setActorDeclaration(item.getActorDeclaration());
                return type;
            }
            catch (ItemNotFoundException e){
                // this error never occurs
                return new NoType();
            }
        }
        else if(expression instanceof Sender){
            return new NoType(); /// ??? ask from baharan
        }
        else if(expression instanceof Identifier){
            try {
                Identifier id = (Identifier) expression;
                SymbolTableVariableItem item = (SymbolTableVariableItem) handlerSym.get(SymbolTableVariableItem.STARTKEY+id.getName());
                return item.getType();
            }
            catch (ItemNotFoundException e){
                // compile err : 1
                return new NoType();
            }
        }
        else if(expression instanceof ArrayCall){
            ArrayCall call = (ArrayCall) expression;
            typeCheck(call.getArrayInstance(),handlerSym,actorSym);
            Type indexType = typeCheck(call.getIndex(),handlerSym,actorSym);
            if(indexType instanceof IntType){
                return indexType;
            }
            else{
                return new NoType();
            }
        }
        else if(expression instanceof ActorVarAccess){
            ActorVarAccess varAccess = (ActorVarAccess) expression;
            Identifier id = varAccess.getVariable();
            try {
                SymbolTableItem item = actorSym.get(SymbolTableVariableItem.STARTKEY+id.getName());
                if(item instanceof SymbolTableActorVariableItem){
                    SymbolTableActorVariableItem actorvar = (SymbolTableActorVariableItem) item;
                    return actorvar.getType();
                }
                throw new ItemNotFoundException();
            }
            catch (ItemNotFoundException e){
                // compile err: 1
            }
        }
        else if(expression instanceof BinaryExpression){
            BinaryExpression binaryExpression = (BinaryExpression) expression;
            Type left = typeCheck(binaryExpression.getLeft(),handlerSym,actorSym);
            Type right = typeCheck(binaryExpression.getRight(),handlerSym,actorSym);
            BinaryOperator operator = binaryExpression.getBinaryOperator();
            return binaryExpressionTypeCheck(left,right,operator);
        }
    }

    @Override
    public void visit(Program program) {
        if(program!=null){
            ArrayList<ActorDeclaration> declarations = program.getActors();
            for(ActorDeclaration declaration : declarations){
                declaration.accept(this);
            }
            Main main = program.getMain();
            if(main!=null){
                main.accept(this);
            }
        }
    }

    @Override
    public void visit(ActorDeclaration actorDeclaration) {
        if(actorDeclaration!=null){
            if(actorDeclaration.getParentName()!=null){
                if(!isActorExist(actorDeclaration.getParentName().getName())){
                    //compile err: 4
                }
            }
            ArrayList<VarDeclaration> knownActors = actorDeclaration.getKnownActors();
            if(knownActors!=null){
                for(VarDeclaration knownActor : knownActors){
                    ActorType type = (ActorType) knownActor.getType();
                    if(!isActorExist(type.getName().getName())){
                        // compile err: 4
                    }
                }
            }
            InitHandlerDeclaration initHandler = actorDeclaration.getInitHandler();
            ArrayList<MsgHandlerDeclaration> msgHandlers = actorDeclaration.getMsgHandlers();
            SymbolTable actorTable = null;
            SymbolTable handlerTable = null;
            if(initHandler!=null){
                try {
                    actorTable = ((SymbolTableActorItem)root.get(SymbolTableActorItem.STARTKEY+actorDeclaration.getName().getName())).getActorSymbolTable();
                    handlerTable = ((SymbolTableHandlerItem)actorTable.get(SymbolTableHandlerItem.STARTKEY+initHandler.getName().getName())).getHandlerSymbolTable();
                }
                catch (ItemNotFoundException e){
                    // err : handler or actor not found but I think it will never happened
                }
                visitHandlers(initHandler,actorTable,handlerTable);
            }
            if(msgHandlers!=null){
                for(MsgHandlerDeclaration msgHandlerDeclaration : msgHandlers){
                    try {
                        actorTable = ((SymbolTableActorItem)root.get(SymbolTableActorItem.STARTKEY+actorDeclaration.getName().getName())).getActorSymbolTable();
                        handlerTable = ((SymbolTableHandlerItem)actorTable.get(SymbolTableHandlerItem.STARTKEY+msgHandlerDeclaration.getName().getName())).getHandlerSymbolTable();
                    }
                    catch (ItemNotFoundException e){
                        // err : handler or actor not found but I think it will never happened
                    }
                    visitHandlers(msgHandlerDeclaration,actorTable,handlerTable);
                }
            }
        }
    }

    public void visitHandlers(HandlerDeclaration handlerDeclaration,SymbolTable actorSymbolTable,SymbolTable handlerSymbolTable){
        if(handlerDeclaration!=null){
            boolean init = (handlerDeclaration.getName().getName().equals("init"));
            ArrayList<Statement> statements = handlerDeclaration.getBody();
            if(statements!=null){
                for(Statement statement : statements){

                }
            }
        }
    }

    @Override
    public void visit(HandlerDeclaration handlerDeclaration) {
        return;
    }

    @Override
    public void visit(VarDeclaration varDeclaration) {
        return;
    }

    @Override
    public void visit(Main mainActors) {
        //TODO: implement appropriate visit functionality
    }

    @Override
    public void visit(ActorInstantiation actorInstantiation) {
        //TODO: implement appropriate visit functionality
    }


    @Override
    public void visit(UnaryExpression unaryExpression) {
        //TODO: implement appropriate visit functionality
    }

    @Override
    public void visit(BinaryExpression binaryExpression) {
        //TODO: implement appropriate visit functionality
    }

    @Override
    public void visit(ArrayCall arrayCall) {
        //TODO: implement appropriate visit functionality
    }

    @Override
    public void visit(ActorVarAccess actorVarAccess) {
        //TODO: implement appropriate visit functionality
    }

    @Override
    public void visit(Identifier identifier) {
        //TODO: implement appropriate visit functionality
    }

    @Override
    public void visit(Self self) {
        //TODO: implement appropriate visit functionality
    }

    @Override
    public void visit(Sender sender) {
        //TODO: implement appropriate visit functionality
    }

    @Override
    public void visit(BooleanValue value) {
        //TODO: implement appropriate visit functionality
    }

    @Override
    public void visit(IntValue value) {
        //TODO: implement appropriate visit functionality
    }

    @Override
    public void visit(StringValue value) {
        //TODO: implement appropriate visit functionality
    }

    @Override
    public void visit(Block block) {
        //TODO: implement appropriate visit functionality
    }

    @Override
    public void visit(Conditional conditional) {
        //TODO: implement appropriate visit functionality
    }

    @Override
    public void visit(For loop) {
        //TODO: implement appropriate visit functionality
    }

    @Override
    public void visit(Break breakLoop) {
        //TODO: implement appropriate visit functionality
    }

    @Override
    public void visit(Continue continueLoop) {
        //TODO: implement appropriate visit functionality
    }

    @Override
    public void visit(MsgHandlerCall msgHandlerCall) {
        //TODO: implement appropriate visit functionality
    }

    @Override
    public void visit(Print print) {
        //TODO: implement appropriate visit functionality
    }

    @Override
    public void visit(Assign assign) {
        //TODO: implement appropriate visit functionality
    }
}
