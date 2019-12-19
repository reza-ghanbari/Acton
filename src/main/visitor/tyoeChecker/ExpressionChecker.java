package main.visitor.tyoeChecker;

import main.ast.node.declaration.ActorDeclaration;
import main.ast.node.expression.*;
import main.ast.node.expression.operators.BinaryOperator;
import main.ast.node.expression.operators.UnaryOperator;
import main.ast.node.expression.values.BooleanValue;
import main.ast.node.expression.values.IntValue;
import main.ast.node.expression.values.StringValue;
import main.ast.type.DynamicType;
import main.ast.type.Type;
import main.ast.type.actorType.ActorType;
import main.ast.type.arrayType.ArrayType;
import main.ast.type.noType.NoType;
import main.ast.type.primitiveType.BooleanType;
import main.ast.type.primitiveType.IntType;
import main.ast.type.primitiveType.StringType;
import main.symbolTable.SymbolTable;
import main.symbolTable.SymbolTableActorItem;
import main.symbolTable.SymbolTableItem;
import main.symbolTable.itemException.ItemNotFoundException;
import main.symbolTable.symbolTableVariableItem.SymbolTableActorVariableItem;
import main.symbolTable.symbolTableVariableItem.SymbolTableVariableItem;

import java.sql.SQLSyntaxErrorException;
import java.util.ArrayList;

public class ExpressionChecker {

    SymbolTable actorSymbolTable;
    SymbolTable handlerSymbolTable;
    static SymbolTable root;
    static ArrayList<String> errors;

    ExpressionChecker(SymbolTable root,SymbolTable actorSymbolTable,SymbolTable handlerSymbolTable, ArrayList<String> errors){
        this.actorSymbolTable = actorSymbolTable;
        this.handlerSymbolTable = handlerSymbolTable;
        this.root = root;
        this.errors = errors;
    }

    public Type check(Expression expression) {
        if(expression instanceof BooleanValue || expression instanceof IntValue || expression instanceof StringValue){
            return primitiveTypeCheck(expression);
        }
        else if(expression instanceof Self){
            return selfTypeCheck();
        }
        else if(expression instanceof Sender){
            if(handlerSymbolTable.getName().equals("initial")){
                // err : 11
                errors.add("Line:" + expression.getLine() + ": no sender in initial msghandler");
                return new NoType();
            }
            return new DynamicType();
        }
        else if(expression instanceof Identifier){
            return identifierTypeCheck((Identifier)expression);
        }
        else if(expression instanceof ArrayCall){
            return arrayCallTypeCheck((ArrayCall)expression);
        }
        else if(expression instanceof ActorVarAccess){
            return actorVarAccessTypeCheck((ActorVarAccess)expression);
        }
        else if(expression instanceof BinaryExpression){
            BinaryExpression binaryExpression = (BinaryExpression) expression;
            Type left = check(binaryExpression.getLeft());
            Type right = check(binaryExpression.getRight());
            BinaryOperator operator = binaryExpression.getBinaryOperator();
            return binaryExpressionTypeCheck(left,right,operator, binaryExpression.getLine());
        }
        else if(expression instanceof UnaryExpression){
            UnaryExpression unaryExpression = (UnaryExpression) expression;
            Type operand = check(unaryExpression.getOperand());
            UnaryOperator operator = unaryExpression.getUnaryOperator();
            if (operator == UnaryOperator.postdec || operator == UnaryOperator.postinc || operator == UnaryOperator.predec || operator == UnaryOperator.preinc) {
                if (!isLeftValue(expression)) {
                    // compile err : 12
                    if (operator == UnaryOperator.postinc || operator == UnaryOperator.preinc) {
                        errors.add("Line:" + expression.getLine() + ": lvalue required as increment operand");
                    } else {
                        errors.add("Line:" + expression.getLine() + ": lvalue required as decrement operand");
                    }
                }
            }
            return unaryExpressionTypeCheck(expression, operand,operator);
        }
        return new NoType();
    }

    static boolean isLeftValue(Expression expression){
        if(expression instanceof ActorVarAccess || expression instanceof ArrayCall || expression instanceof Identifier || expression.getType() instanceof NoType){
           return true;
        }
        return false;
    }

    private Type primitiveTypeCheck(Expression expression){
        return expression.getType();
    }

    private  Type selfTypeCheck(){
        try {
            SymbolTableActorItem item = (SymbolTableActorItem) root.get(SymbolTableActorItem.STARTKEY+actorSymbolTable.getName());
            ActorType type = new ActorType(item.getActorDeclaration().getName());
            type.setActorDeclaration(item.getActorDeclaration());
            return type;
        }
        catch (ItemNotFoundException e){
            // this error never occurs
            return new NoType();
        }
    }

    private Type identifierTypeCheck(Identifier id){
        try {
            SymbolTableVariableItem item = (SymbolTableVariableItem) handlerSymbolTable.get(SymbolTableVariableItem.STARTKEY+id.getName());
            return item.getType();
        }
        catch (ItemNotFoundException e){
            // compile err : 1
            errors.add("Line:" + id.getLine() + ": variable " + id.getName() + " is not declared");
            return new NoType();
        }
    }

    private Type arrayCallTypeCheck(ArrayCall call){
        Type type= check(call.getArrayInstance());
        if(!(type instanceof ArrayType)){
            //err : bracket used on not array type
            errors.add("Line:" + call.getLine() + ": unsupported operand type for Array call");
        }
        Type indexType = check(call.getIndex());
        if(indexType instanceof IntType){
            return new IntType();
        }
        else{
            return new NoType();
        }
    }

    private Type actorVarAccessTypeCheck(ActorVarAccess varAccess){
        Identifier id = varAccess.getVariable();
        try {
            SymbolTableItem item = actorSymbolTable.get(SymbolTableVariableItem.STARTKEY+id.getName());
            if(item instanceof SymbolTableActorVariableItem){
                SymbolTableActorVariableItem actorvar = (SymbolTableActorVariableItem) item;
                return actorvar.getType();
            }
            throw new ItemNotFoundException();
        }
        catch (ItemNotFoundException e){
            // compile err: 1
            errors.add("Line:" + id.getLine() + ": variable " + id.getName() + " is not declared");
            return new NoType();
        }
    }

    static boolean isChildOf(ActorDeclaration b, ActorDeclaration a){
        while (b!=null){
//            System.err.println("b : " + b.getName().getName());
//            System.err.println("Parent b : " + b.getParentName().getName());
//            System.err.println("a :" + a.getName().getName());
            if(a.getName().getName().equals(b.getName().getName())){
                return true;
            }
            if(b.getParentName()!=null){
                try {
                    b = ((SymbolTableActorItem)root.get(SymbolTableActorItem.STARTKEY+b.getParentName().getName())).getActorDeclaration();
                }
                catch (ItemNotFoundException e){
                    // compile err: 4
//                    errors.add("Line:" + b.getLine() +":actor " + b.getParentName().getName() + " is not declared");
                    break;
                }
            }
            else break;
        }
        return false;
    }

    static boolean isSubType(Type a, Type b){
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
                ActorDeclaration aDeclaration, bDeclaration;
                try {
                    aDeclaration = ((SymbolTableActorItem) root.get(SymbolTableActorItem.STARTKEY + ((ActorType) a).getName().getName())).getActorDeclaration();
                } catch (ItemNotFoundException e) {
//                    System.err.println("Cannot find :" + SymbolTableActorItem.STARTKEY + ((ActorType) b).getName().getName() + ":");
//                    System.err.println("Keys are:");
//                    root.getSymbolTableItems().forEach((key, value) -> System.err.println(key));
                    return false;
                }
                try {
                    bDeclaration = ((SymbolTableActorItem) root.get(SymbolTableActorItem.STARTKEY + ((ActorType) b).getName().getName())).getActorDeclaration();
                }catch (ItemNotFoundException e) {
//                    System.err.println("Cannot find :" + SymbolTableActorItem.STARTKEY + ((ActorType) a).getName().getName() + ":");
//                    System.err.println("Keys are:");
//                    root.getSymbolTableItems().forEach((key, value) -> System.err.println(key));
                    return false;
                }
                return isChildOf(aDeclaration, bDeclaration);
            }
        }
        else if(a instanceof DynamicType){
            return (b instanceof DynamicType);
        }
        return false;
    }

    private Type binaryExpressionTypeCheck(Type left, Type right, BinaryOperator operator, int line) {
        if (operator == BinaryOperator.mult || operator == BinaryOperator.add || operator == BinaryOperator.sub || operator == BinaryOperator.mod ||
                operator == BinaryOperator.div || operator == BinaryOperator.gt || operator == BinaryOperator.lt) {
            if (left instanceof IntType && right instanceof IntType) {
                if (operator == BinaryOperator.gt || operator == BinaryOperator.lt)
                    return new BooleanType();
                else
                    return new IntType();
            } else if ((left instanceof IntType && right instanceof NoType)
                    || (left instanceof NoType && right instanceof IntType)
                    || (left instanceof NoType && right instanceof NoType)) {
                return new NoType();
            }
            else {
                // compile err : 2
                errors.add("Line:" + line + ": unsupported operand type for " + operator.name());
            }
        } else if (operator == BinaryOperator.or || operator == BinaryOperator.and) {
            if (left instanceof BooleanType && right instanceof BooleanType) {
                return new BooleanType();
            } else if ((left instanceof BooleanType && right instanceof NoType)
                    || (left instanceof NoType && right instanceof BooleanType)
                    || (left instanceof NoType && right instanceof NoType)) {
                return new NoType();
            }
            else {
                // compile err : 2
                errors.add("Line:" + line + ": unsupported operand type for " + operator.name());
            }
        } else if (operator == BinaryOperator.eq || operator == BinaryOperator.neq) {
            if (left instanceof NoType || right instanceof NoType)
                return new NoType();
            if (isSubType(left, right) && isSubType(right, left))
                return new BooleanType();
            else {
                // compile err : 2
                errors.add("Line:" + line + ": unsupported operand type for " + operator.name());
                return new NoType();
            }
        }
        return new NoType();
    }

    private Type unaryExpressionTypeCheck(Expression expression,Type type, UnaryOperator operator) {
        if (operator == UnaryOperator.postdec || operator == UnaryOperator.predec || operator == UnaryOperator.postinc
                || operator == UnaryOperator.preinc || operator == UnaryOperator.minus) {
            if (type instanceof IntType)
                return type;
            else if (type instanceof NoType)
                return new NoType();
            else {
                // compile err : 2
                errors.add("Line:" + expression.getLine() + ": unsupported operand type for " + operator.name());
                return new NoType();
            }
        } else if (operator == UnaryOperator.not) {
            if (type instanceof BooleanType)
                return type;
            else if (type instanceof NoType)
                return new NoType();
            else {
                // compile err : 2
//                System.err.println(type.toString() + " " + operator.name());
                errors.add("Line:" + expression.getLine() + ": unsupported operand type for " + operator.name());
                return new NoType();
            }
        }
        return new NoType();
    }
}
