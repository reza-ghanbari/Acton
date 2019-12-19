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
import main.ast.node.expression.values.BooleanValue;
import main.ast.node.expression.values.IntValue;
import main.ast.node.expression.values.StringValue;
import main.ast.node.statement.*;
import main.ast.type.DynamicType;
import main.ast.type.Type;
import main.ast.type.actorType.ActorType;
import main.ast.type.arrayType.ArrayType;
import main.ast.type.noType.NoType;
import main.ast.type.primitiveType.BooleanType;
import main.ast.type.primitiveType.IntType;
import main.ast.type.primitiveType.StringType;
import main.symbolTable.*;
import main.symbolTable.itemException.ItemNotFoundException;
import main.symbolTable.symbolTableVariableItem.SymbolTableVariableItem;
import main.visitor.VisitorImpl;

import java.util.ArrayList;

public class TypeChecker extends VisitorImpl {

    SymbolTable root = SymbolTable.root;
    SymbolTable handlerSymbolTable = null;
    SymbolTable actorSymbolTable = null;
    ArrayList<String> errors = new ArrayList<>();
    private boolean isInsideFor = false;

    private boolean isActorExist(String name){
        try{
            root.get(SymbolTableActorItem.STARTKEY + name);
            return true;
        }
        catch (ItemNotFoundException e){
            return false;
        }
    }

    public ArrayList<String> getErrors() {
        return this.errors;
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
                    errors.add("Line:" + actorDeclaration.getLine() + ":actor " + actorDeclaration.getParentName().getName() + " is not declared");
                }
            }
            ArrayList<VarDeclaration> knownActors = actorDeclaration.getKnownActors();
            if(knownActors!=null){
                for(VarDeclaration knownActor : knownActors) {
                    ActorType type = (ActorType) knownActor.getType();
                    if(!isActorExist(type.getName().getName())) {
                        // compile err: 4
                        errors.add("Line:" + knownActor.getIdentifier().getLine() + ":actor " + knownActor.getType().toString() + " is not declared");
                        try {
                            ((SymbolTableVariableItem) ((SymbolTableActorItem)root.get(SymbolTableActorItem.STARTKEY+actorDeclaration.getName().getName())).getActorSymbolTable().get(SymbolTableVariableItem.STARTKEY + knownActor.getIdentifier().getName())).setType(new NoType());
                        } catch (ItemNotFoundException e) {

                        }
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
                this.actorSymbolTable = actorTable;
                this.handlerSymbolTable = handlerTable;
                visit(initHandler);
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
                    this.actorSymbolTable = actorTable;
                    this.handlerSymbolTable = handlerTable;
                    visit(msgHandlerDeclaration);
                }
            }
        }
    }

    @Override
    public void visit(HandlerDeclaration handlerDeclaration) {
        if(handlerDeclaration!=null) {
            ArrayList<Statement> statements = handlerDeclaration.getBody();
            if(statements!=null) {
                for(Statement statement : statements) {
                    visitStatement(statement);
                }
            }
        }
    }

    @Override
    public void visit(VarDeclaration varDeclaration) {
        return;
    }

    @Override
    public void visit(Main mainActors) {
        if(mainActors!=null){
            try {
                SymbolTableMainItem main = (SymbolTableMainItem) root.get(SymbolTableMainItem.STARTKEY+"main");
                handlerSymbolTable = main.getMainSymbolTable();
            }
            catch (ItemNotFoundException e){
                // err :? main not exist
                errors.add("Line:" + mainActors.getLine() + ": main doesn't exist.");
            }
            ArrayList<ActorInstantiation> instantiations = mainActors.getMainActors();
            if(instantiations!=null){
                for(ActorInstantiation instantiation : instantiations){
                    visit(instantiation);
                }
            }
        }
    }

    @Override
    public void visit(ActorInstantiation actorInstantiation) {
        if(actorInstantiation != null){
            ActorType type = (ActorType) actorInstantiation.getType();
            ActorDeclaration declaration;
            try {
                declaration = ((SymbolTableActorItem) root.get(SymbolTableActorItem.STARTKEY + type.toString().trim())).getActorDeclaration();
                if(isActorExist(type.getName().getName())) {
//                    System.err.println("type is " + type.getName().getName());
                    ArrayList<Identifier> knownActorInput = actorInstantiation.getKnownActors();
                    ArrayList<Expression> initArgsInput = actorInstantiation.getInitArgs();
                    ArrayList<VarDeclaration> knownActorDefine = new ArrayList<>();
                    if (declaration.getKnownActors() != null) {
                        knownActorDefine = declaration.getKnownActors();
                    }
                    ArrayList<VarDeclaration> initArgsDefine = new ArrayList<>();
                    if (declaration.getInitHandler() != null) {
                        initArgsDefine = declaration.getInitHandler().getArgs();
                    }
                    if(knownActorInput.size() != knownActorDefine.size()){
                        // compile err: 8
                        errors.add("Line:" + actorInstantiation.getLine() + ": knownactors does not match with definition");
                        for (Identifier id : knownActorInput) {
                            ExpressionChecker checker = new ExpressionChecker(root,((SymbolTableMainItem)root.get(SymbolTableMainItem.STARTKEY + "main")).getMainSymbolTable(),handlerSymbolTable, errors);
                            checker.check(id);
                        }
                    }
                    else {
                        for(int i = 0;i < knownActorInput.size(); i++) {
                            ExpressionChecker checker = new ExpressionChecker(root,((SymbolTableMainItem)root.get(SymbolTableMainItem.STARTKEY + "main")).getMainSymbolTable(),handlerSymbolTable, errors);
                            Type checkedType = checker.check(knownActorInput.get(i));
//                            System.err.println("Checked type : " + checkedType.toString());
//                            System.err.println("Define type : " + knownActorDefine.get(i).getType().toString());
                            if(!ExpressionChecker.isSubType(checkedType,knownActorDefine.get(i).getType())){
                                // compile err: 8
                                errors.add("Line:" + actorInstantiation.getLine() + ": knownactors does not match with definition");
                                break;
                            }
                        }
                    }
                    if(initArgsInput.size()!=initArgsDefine.size()) {
                        // compile err: not listed
                        errors.add("Line:" + initArgsInput.get(0).getLine() + ":number of passed arguments("+initArgsInput.size()+") does not match number of required("+initArgsDefine.size()+").");
                    }
                    else {
                        for(int i=0;i<initArgsInput.size();i++){
                            ExpressionChecker checker = new ExpressionChecker(root, ((SymbolTableMainItem) root.get(SymbolTableMainItem.STARTKEY + "main")).getMainSymbolTable(), handlerSymbolTable, errors);
//                            System.out.println("Here we are in arg checker -> " + initArgsInput.get(i).toString());
                            if (initArgsInput.get(i) instanceof ActorVarAccess) {
                                errors.add("Line:" + initArgsInput.get(i).getLine() + ": no self in main");
                            } else if (initArgsInput.get(i) instanceof Sender) {
                                errors.add("Line:" + initArgsInput.get(i).getLine() + ": no sender in main");
                            } else {
                                Type checkedType = checker.check(initArgsInput.get(i));
                                if (!ExpressionChecker.isSubType(checkedType, initArgsDefine.get(i).getType())) {
                                    // compile err: not listed
                                    errors.add("Line:" + initArgsInput.get(i).getLine() + ":type of passed argument doesn't match with required type.");
                                }
                            }
                        }
                    }
                }
                else{
                    // compile err : 4
                    errors.add("Line:" + actorInstantiation.getLine() + ":actor " + type.getName().getName() + " is not declared");
                    try {
                        ((SymbolTableVariableItem) actorSymbolTable.get(SymbolTableVariableItem.STARTKEY + actorInstantiation.getIdentifier().getName())).setType(new NoType());
                    } catch (ItemNotFoundException e) {

                    }
                }
            }
            catch (ItemNotFoundException e) {
//                System.err.println("Declaration is null.");
                errors.add("Line:" + actorInstantiation.getLine() + ":actor " + type.getName().getName() + " is not declared");
            }
        }
    }

    @Override
    public void visit(UnaryExpression unaryExpression) {
        return;
    }

    @Override
    public void visit(BinaryExpression binaryExpression) {
        return;
    }

    @Override
    public void visit(ArrayCall arrayCall) {
        return;
    }

    @Override
    public void visit(ActorVarAccess actorVarAccess) {
        return;
    }

    @Override
    public void visit(Identifier identifier) {
        return;
    }

    @Override
    public void visit(Self self) {
        return;
    }

    @Override
    public void visit(Sender sender) {
        return;
    }

    @Override
    public void visit(BooleanValue value) {
        return;
    }

    @Override
    public void visit(IntValue value) {
        return;
    }

    @Override
    public void visit(StringValue value) {
        return;
    }

    @Override
    public void visit(Block block) {
        if(block!=null){
            ArrayList<Statement> statements = block.getStatements();
            for(Statement statement : statements){
                visitStatement(statement);
            }
        }
    }

    @Override
    public void visit(Conditional conditional) {
        if(conditional!=null){
            Expression expression = conditional.getExpression();
            ExpressionChecker checker = new ExpressionChecker(root,actorSymbolTable,handlerSymbolTable, errors);
            if(ExpressionChecker.isSubType(checker.check(expression),new BooleanType())){

            }
            else {
                // compile err: 3
                errors.add("Line:" + conditional.getLine() + ":condition type must be Boolean");
            }
            Statement thenBody = conditional.getThenBody();
            if(thenBody!=null){
                visitStatement(thenBody);
            }
            Statement elseBody = conditional.getElseBody();
            if(elseBody!=null){
                visitStatement(elseBody);
            }
        }
    }

    @Override
    public void visit(For loop) {
        if(loop!=null){
            Assign assignInit = loop.getInitialize();
            if(assignInit!=null){
                visitStatement(assignInit);
            }
            Expression condition = loop.getCondition();
            ExpressionChecker checker = new ExpressionChecker(root,actorSymbolTable,handlerSymbolTable, errors);
            if(!ExpressionChecker.isSubType(checker.check(condition),new BooleanType())){
                // compile err : 3
                errors.add("Line:" + condition.getLine() + ": condition type must be Boolean");
            }
            Assign assignUpdate = loop.getUpdate();
            if(assignUpdate!=null){
                visitStatement(assignUpdate);
            }
            Statement body = loop.getBody();
            if(body!=null){
                boolean isInsideForPrev = this.isInsideFor;
                this.isInsideFor = true;
                visitStatement(body);
                this.isInsideFor = isInsideForPrev;
            }
        }
    }

    @Override
    public void visit(Break breakLoop) {
        if(breakLoop!=null){
            if(!isInsideFor){
                // compile err : 10
                errors.add("Line:" + breakLoop.getLine() + ": break statement not within loop");
            }
        }
    }

    @Override
    public void visit(Continue continueLoop) {
        if(continueLoop!=null){
            if(!isInsideFor){
                // compile err : 10
                errors.add("Line:" + continueLoop.getLine() + ": continue statement not within loop");
            }
        }
    }

    @Override
    public void visit(MsgHandlerCall msgHandlerCall) {
        SymbolTableHandlerItem handlerItem;
        if(msgHandlerCall!=null) {
            Expression instance = msgHandlerCall.getInstance();
            ExpressionChecker Checker = new ExpressionChecker(root,actorSymbolTable,handlerSymbolTable, errors);
            Type instanceType = Checker.check(instance);
            try {
                SymbolTableActorItem currentItem = (SymbolTableActorItem) root.get(SymbolTableActorItem.STARTKEY + actorSymbolTable.getName());
                boolean isNotSender = ExpressionChecker.isSubType(instanceType, new ActorType(currentItem.getActorDeclaration().getName()));
                if (!isNotSender) {
                    ArrayList<VarDeclaration> knownactors = currentItem.getActorDeclaration().getKnownActors();
                    for (VarDeclaration knownactor : knownactors) {
                        if (ExpressionChecker.isSubType(instanceType, knownactor.getType())) {
                            try {
                                currentItem = (SymbolTableActorItem) root.get(SymbolTableActorItem.STARTKEY + knownactor.getType().toString());
                                isNotSender = true;
                            } catch (ItemNotFoundException e) {
                                System.err.println("Couldn't find " + SymbolTableActorItem.STARTKEY + knownactor.getType().toString());
                                System.err.println("Keys are : ");
                                root.getSymbolTableItems().forEach((key, value) -> System.err.println(key));
                            }
                        }
                    }
                }
                if(isNotSender) {
                    SymbolTableActorItem item = currentItem;
                    boolean isActorMsgHandler = false;
                    try {
                        boolean found = false;
                        while (!found) {
                            ArrayList<MsgHandlerDeclaration> msgHandlers = item.getActorDeclaration().getMsgHandlers();
                            for (MsgHandlerDeclaration msgHandler : msgHandlers) {
                                if (msgHandler.getName().getName().equals(msgHandlerCall.getMsgHandlerName().getName())) {
                                    isActorMsgHandler = true;
                                    found = true;
                                    break;
                                }
                            }
                            if (item.getParentName() != null) {
                                item = (SymbolTableActorItem) root.get(SymbolTableActorItem.STARTKEY + item.getParentName());
                            } else break;
                        }
                    } catch(ItemNotFoundException e) {
                        System.err.println("Couldn't find " + SymbolTableActorItem.STARTKEY + item.getParentName());
                        System.err.println("Keys are : ");
                        root.getSymbolTableItems().forEach((key, value) -> System.err.println(key));
                    }
                    if (!isActorMsgHandler) {
                        // compile error : 5
                        errors.add("Line:" + msgHandlerCall.getLine() + ": there is no msghandler name " + msgHandlerCall.getMsgHandlerName().getName()
                                + " in actor " + currentItem.getActorDeclaration().getName().getName());
                        ExpressionChecker checker = new ExpressionChecker(root, actorSymbolTable, handlerSymbolTable, errors);
                        for (Expression argument : msgHandlerCall.getArgs()) {
                            checker.check(argument);
                        }
                    } else {
                        handlerItem = ((SymbolTableHandlerItem) item.getActorSymbolTable().get(msgHandlerCall.getMsgHandlerName().getName())); //TODO: Shouldn't we use getInCurrentScope?
                        ArrayList<Type> argTypes = handlerItem.getArgTypes();
                        ArrayList<Expression> msgHandlerArgs = msgHandlerCall.getArgs();
                        if (argTypes.size() != msgHandlerArgs.size()) {
                            // compile error : number of passed arguments is not the same as number of required.
                            errors.add("Line:" + msgHandlerCall.getLine() + ":number of passed arguments is not the same as number of required.");
                        } else {
                            ExpressionChecker checker = new ExpressionChecker(root, actorSymbolTable, handlerSymbolTable, errors);
                            for (int i = 0; i < msgHandlerArgs.size(); i++) {
                                if (argTypes.get(i) != checker.check(msgHandlerArgs.get(i))) {//TODO: what would we use to check equality of two types?
                                    //compile error : argument doesn't have same type
                                    errors.add("Line:" + msgHandlerArgs.get(i).getLine() + ":argument types is not the same as message handler args.");
                                }
                            }
                        }
                    }
                }
                else if(ExpressionChecker.isSubType(instanceType, new DynamicType())) {
                    ExpressionChecker checker = new ExpressionChecker(root, actorSymbolTable, handlerSymbolTable, errors);
                    ArrayList<Expression> msgHandlerCallArgs = msgHandlerCall.getArgs();
                    for (Expression arg : msgHandlerCallArgs) {
                        checker.check(arg);
                    }
                }
                else{
                    // compile err: 9
                    errors.add("Line:" + instance.getLine() + ":variable " + ((Identifier)instance).getName() +" is not callable");
                }
            } catch (ItemNotFoundException e) {
                System.err.println("Couldn't find " + SymbolTableActorItem.STARTKEY + actorSymbolTable.getName());
            }
//            if (ExpressionChecker.isSubType(instanceType, new ActorType(new Identifier("temp")))) {
//                try {
//                    SymbolTableActorItem item = (SymbolTableActorItem) root.get(SymbolTableActorItem.STARTKEY + actorSymbolTable.getName());
//                    handlerItem = ((SymbolTableHandlerItem) item.getActorSymbolTable().get(msgHandlerCall.getMsgHandlerName().getName()));
//                    ArrayList<MsgHandlerDeclaration> msgHandlers = item.getActorDeclaration().getMsgHandlers();
//                    boolean isActorMsgHandler = false;
//                    for (MsgHandlerDeclaration msgHandler : msgHandlers) {
//                        if (msgHandler.getName().getName().equals(msgHandlerCall.getMsgHandlerName().getName())) {
//                            isActorMsgHandler = true;
//                            break;
//                        }
//                    }
//                    if (!isActorMsgHandler) {
//                        // compile error : 5
//                        errors.add("Line:" + msgHandlerCall.getLine() + ":there is no msghandler name " + msgHandlerCall.getMsgHandlerName().getName()
//                                + " in actor " + item.getActorDeclaration().getName().getName());
//                    }
//                    ArrayList<Type> argTypes = handlerItem.getArgTypes();
//                    ArrayList<Expression> msgHandlerArgs = msgHandlerCall.getArgs();
//                    if (argTypes.size() != msgHandlerArgs.size()) {
//                        // compile error : number of passed arguments is not the same as number of required.
//                        errors.add("Line:" + msgHandlerCall.getLine() + ":number of passed arguments is not the same as number of required.");
//                    } else {
//                        ExpressionChecker checker = new ExpressionChecker(root, actorSymbolTable, handlerSymbolTable, errors);
//                        for (int i = 0; i < msgHandlerArgs.size(); i++) {
//                            if (argTypes.get(i) != checker.check(msgHandlerArgs.get(i))) {
//                                //compile error : argument doesn't have same type
//                                errors.add("Line:" + msgHandlerArgs.get(i).getLine() + ":argument types is not the same as message handler args.");
//                            }
//                        }
//                    }
//                } catch (ItemNotFoundException e) {
//                    System.err.println("Couldn't find " + SymbolTableActorItem.STARTKEY + actorSymbolTable.getName());
//                }
//            }
        }
    }

    @Override
    public void visit(Print print) {
        if(print!=null){
            Expression expression = print.getArg();
            if(expression!=null){
                ExpressionChecker checker = new ExpressionChecker(root,actorSymbolTable,handlerSymbolTable, errors);
                Type type = checker.check(expression);
                boolean isValid = ExpressionChecker.isSubType(type,new IntType()) || ExpressionChecker.isSubType(type,new StringType()) || ExpressionChecker.isSubType(type,new BooleanType()) || ExpressionChecker.isSubType(type,new ArrayType(0));
                if(!isValid){
                    // compile err : 6
                    errors.add("Line:" + print.getLine() + ": unsupported type for print");
                }
            }
        }
    }

    @Override
    public void visit(Assign assign) {
        if(assign!=null){
            ExpressionChecker checker = new ExpressionChecker(root,actorSymbolTable,handlerSymbolTable, errors);
            Type LType = checker.check(assign.getlValue());
            Type RType = checker.check(assign.getrValue());
            boolean isLValue = ExpressionChecker.isLeftValue(assign.getlValue());
            if(isLValue){
                if(!ExpressionChecker.isSubType(RType,LType)){
                    //TODO : change subtype rule or this part of code because of notype may appears as leftvalue of assignment
                    // compile err : 2
                    errors.add("Line:" + assign.getlValue().getLine() + ": unsupported operand type for assign");
                }
            }
            else {
                // compile err : 7
                errors.add("Line:" + assign.getlValue().getLine() + ": left side of assignment must be a valid lvalue");
            }
        }
    }
}
