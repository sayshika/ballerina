/*
*  Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing,
*  software distributed under the License is distributed on an
*  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*  KIND, either express or implied.  See the License for the
*  specific language governing permissions and limitations
*  under the License.
*/
package org.wso2.ballerina.core.model.builder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.ballerina.core.exception.ParserException;
import org.wso2.ballerina.core.model.Annotation;
import org.wso2.ballerina.core.model.BTypeConvertor;
import org.wso2.ballerina.core.model.BallerinaAction;
import org.wso2.ballerina.core.model.BallerinaConnector;
import org.wso2.ballerina.core.model.BallerinaFile;
import org.wso2.ballerina.core.model.BallerinaFunction;
import org.wso2.ballerina.core.model.ConnectorDcl;
import org.wso2.ballerina.core.model.Const;
import org.wso2.ballerina.core.model.ImportPackage;
import org.wso2.ballerina.core.model.NodeLocation;
import org.wso2.ballerina.core.model.Operator;
import org.wso2.ballerina.core.model.Parameter;
import org.wso2.ballerina.core.model.Resource;
import org.wso2.ballerina.core.model.Service;
import org.wso2.ballerina.core.model.Struct;
import org.wso2.ballerina.core.model.StructDcl;
import org.wso2.ballerina.core.model.SymbolName;
import org.wso2.ballerina.core.model.VariableDcl;
import org.wso2.ballerina.core.model.expressions.ActionInvocationExpr;
import org.wso2.ballerina.core.model.expressions.AddExpression;
import org.wso2.ballerina.core.model.expressions.AndExpression;
import org.wso2.ballerina.core.model.expressions.ArrayInitExpr;
import org.wso2.ballerina.core.model.expressions.ArrayMapAccessExpr;
import org.wso2.ballerina.core.model.expressions.BacktickExpr;
import org.wso2.ballerina.core.model.expressions.BasicLiteral;
import org.wso2.ballerina.core.model.expressions.BinaryExpression;
import org.wso2.ballerina.core.model.expressions.DivideExpr;
import org.wso2.ballerina.core.model.expressions.EqualExpression;
import org.wso2.ballerina.core.model.expressions.Expression;
import org.wso2.ballerina.core.model.expressions.FunctionInvocationExpr;
import org.wso2.ballerina.core.model.expressions.GreaterEqualExpression;
import org.wso2.ballerina.core.model.expressions.GreaterThanExpression;
import org.wso2.ballerina.core.model.expressions.InstanceCreationExpr;
import org.wso2.ballerina.core.model.expressions.KeyValueExpression;
import org.wso2.ballerina.core.model.expressions.LessEqualExpression;
import org.wso2.ballerina.core.model.expressions.LessThanExpression;
import org.wso2.ballerina.core.model.expressions.MapInitExpr;
import org.wso2.ballerina.core.model.expressions.MultExpression;
import org.wso2.ballerina.core.model.expressions.NotEqualExpression;
import org.wso2.ballerina.core.model.expressions.OrExpression;
import org.wso2.ballerina.core.model.expressions.ReferenceExpr;
import org.wso2.ballerina.core.model.expressions.StructFieldAccessExpr;
import org.wso2.ballerina.core.model.expressions.StructInitExpr;
import org.wso2.ballerina.core.model.expressions.SubtractExpression;
import org.wso2.ballerina.core.model.expressions.TypeCastExpression;
import org.wso2.ballerina.core.model.expressions.UnaryExpression;
import org.wso2.ballerina.core.model.expressions.VariableRefExpr;
import org.wso2.ballerina.core.model.statements.ActionInvocationStmt;
import org.wso2.ballerina.core.model.statements.AssignStmt;
import org.wso2.ballerina.core.model.statements.BlockStmt;
import org.wso2.ballerina.core.model.statements.FunctionInvocationStmt;
import org.wso2.ballerina.core.model.statements.IfElseStmt;
import org.wso2.ballerina.core.model.statements.ReplyStmt;
import org.wso2.ballerina.core.model.statements.ReturnStmt;
import org.wso2.ballerina.core.model.statements.Statement;
import org.wso2.ballerina.core.model.statements.WhileStmt;
import org.wso2.ballerina.core.model.symbols.SymbolScope;
import org.wso2.ballerina.core.model.types.BStructType;
import org.wso2.ballerina.core.model.types.BType;
import org.wso2.ballerina.core.model.types.BTypes;
import org.wso2.ballerina.core.model.values.BBoolean;
import org.wso2.ballerina.core.model.values.BDouble;
import org.wso2.ballerina.core.model.values.BFloat;
import org.wso2.ballerina.core.model.values.BInteger;
import org.wso2.ballerina.core.model.values.BLong;
import org.wso2.ballerina.core.model.values.BString;
import org.wso2.ballerina.core.model.values.BValueType;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code BLangModelBuilder} provides an high-level API to create Ballerina language object model.
 *
 * @since 0.8.0
 */
public class BLangModelBuilder {
    private static final Logger log = LoggerFactory.getLogger(BLangModelBuilder.class);

    private String pkgName;
    private BallerinaFile.BFileBuilder bFileBuilder = new BallerinaFile.BFileBuilder();

    private SymbolScope currentScope;

    // Builds connectors and services.
    private CallableUnitGroupBuilder currentCUGroupBuilder;

    // Builds functions, actions and resources.
    private CallableUnitBuilder currentCUBuilder;

    // Builds user defined structs.
    private Struct.StructBuilder structBuilder;

    private Stack<Annotation.AnnotationBuilder> annotationBuilderStack = new Stack<>();
    private Stack<BlockStmt.BlockStmtBuilder> blockStmtBuilderStack = new Stack<>();
    private Stack<IfElseStmt.IfElseStmtBuilder> ifElseStmtBuilderStack = new Stack<>();
    private Queue<BType> typeQueue = new LinkedList<>();
    private Stack<String> pkgNameStack = new Stack<>();
    private Stack<SymbolName> symbolNameStack = new Stack<>();
    private Stack<Expression> exprStack = new Stack<>();
    private Stack<KeyValueExpression> keyValueStack = new Stack<>();

    // Holds ExpressionLists required for return statements, function/action invocations and connector declarations
    private Stack<List<Expression>> exprListStack = new Stack<>();
    private Stack<List<Annotation>> annotationListStack = new Stack<>();
    private Stack<List<KeyValueExpression>> mapInitKeyValueListStack = new Stack<>();

    public BLangModelBuilder() {
    }

    public BLangModelBuilder(SymbolScope packageScope) {
        this.currentScope = packageScope;
    }

    public BallerinaFile build() {
        return bFileBuilder.build();
    }

    // Identifiers

    public void createSymbolName(String name) {
        if (pkgNameStack.isEmpty()) {
            symbolNameStack.push(new SymbolName(name));
        } else {
            symbolNameStack.push(new SymbolName(name, pkgNameStack.pop()));
        }
    }

    public void createSymbolName(String connectorName, String actionName) {
        SymbolName symbolName;
        if (pkgNameStack.isEmpty()) {
            symbolName = new SymbolName(actionName);
        } else {
            symbolName = new SymbolName(actionName, pkgNameStack.pop());
        }

        symbolName.setConnectorName(connectorName);
        symbolNameStack.push(symbolName);
    }

    // Packages and import packages

    public void createPackageName(String pkgName) {
        pkgNameStack.push(pkgName);
    }

    public void createPackageDcl() {
        pkgName = getPkgName();
        bFileBuilder.setPkgName(pkgName);
    }

    public void addImportPackage(String pkgName, NodeLocation location) {
        String pkgPath = getPkgName();
        if (pkgName != null) {
            bFileBuilder.addImportPackage(new ImportPackage(location, pkgPath, pkgName));
        } else {
            bFileBuilder.addImportPackage(new ImportPackage(location, pkgPath));
        }
    }

    // Annotations

    public void createInstanceCreaterExpr(String typeName, boolean exprListAvailable, NodeLocation location) {
        BType type = BTypes.getType(typeName);
        if (type == null || type instanceof BStructType) {
            // if the type is undefined or of struct type, treat it as a user defined struct
            createStructInitExpr(location, typeName);
            return;
        }

        if (exprListAvailable) {
            // This is not yet supported. Therefore ignoring for the moment.
            exprListStack.pop();
        }

        InstanceCreationExpr expression = new InstanceCreationExpr(location, null);
        expression.setType(type);
        exprStack.push(expression);
    }

    public void startAnnotation() {
        annotationBuilderStack.push(new Annotation.AnnotationBuilder());
    }

    public void createAnnotationKeyValue(String key) {
        //        // Assuming the annotation value is a string literal
        //        String value = exprStack.pop().getBValueRef().getString();
        //
        //        Annotation.AnnotationBuilder annotationBuilder = annotationBuilderStack.peek();
        //        annotationBuilder.addKeyValuePair(new Identifier(key), value);

        log.warn("Warning: Key/Value pairs in annotations are not supported");
    }

    public void endAnnotation(String name, boolean valueAvailable, NodeLocation location) {
        Annotation.AnnotationBuilder annotationBuilder = annotationBuilderStack.pop();
        annotationBuilder.setNodeLocation(location);
        annotationBuilder.setName(new SymbolName(name));

        if (valueAvailable) {
            Expression expr = exprStack.pop();

            // Assuming the annotation value is a string literal
            if (expr instanceof BasicLiteral && expr.getType() == BTypes.STRING_TYPE) {
                String value = ((BasicLiteral) expr).getBValue().stringValue();
                annotationBuilder.setValue(value);
            } else {
                throw new RuntimeException("Annotations with key/value pars are not support at the moment" + " in " +
                        location.getFileName() + ":" + location.getLineNumber());
            }
        }

        List<Annotation> annotationList = annotationListStack.peek();
        Annotation annotation = annotationBuilder.build();
        annotationList.add(annotation);
    }

    // Function parameters and types

    /**
     * Create a function parameter and a corresponding variable reference expression.
     * <p/>
     * Set the even function to get the value from the function arguments with the correct index.
     * Store the reference in the symbol table.
     *
     * @param paramName name of the function parameter
     */
    public void createParam(String paramName, NodeLocation location) {
        SymbolName paramNameId = new SymbolName(paramName);
        BType paramType = typeQueue.remove();
        Parameter param = new Parameter(location, paramType, paramNameId);

        if (currentCUBuilder != null) {
            // Add the parameter to callableUnitBuilder.
            currentCUBuilder.addParameter(param);
        } else {
            currentCUGroupBuilder.addParameter(param);
        }
    }

    public void createType(String typeName, NodeLocation location) {
        BType type = BTypes.getType(typeName);
        if (type == null) {
            type = new BStructType(typeName);
        }
        typeQueue.add(type);
    }

    public void createArrayType(String typeName, NodeLocation location) {
        BType type = BTypes.getArrayType(typeName);
        typeQueue.add(type);
    }

    public void registerConnectorType(String typeName) {
        //TODO: We might have to do this through a symbol table in the future
        BTypes.addConnectorType(typeName);
    }

    public void createReturnTypes(NodeLocation location) {
        while (!typeQueue.isEmpty()) {
            BType paramType = typeQueue.remove();
            Parameter param = new Parameter(location, paramType, null);
            currentCUBuilder.addReturnParameter(param);
        }
    }

    public void createNamedReturnParams(String paramName, NodeLocation location) {
        SymbolName paramNameId = new SymbolName(paramName);
        BType paramType = typeQueue.remove();

        Parameter param = new Parameter(location, paramType, paramNameId);
        currentCUBuilder.addReturnParameter(param);
    }

    // Variable declarations, reference expressions

    public void createConstant(String constName, NodeLocation location) {
        SymbolName symbolName = new SymbolName(constName);
        BType type = typeQueue.remove();

        Const.ConstBuilder builder = new Const.ConstBuilder();
        builder.setNodeLocation(location);
        builder.setType(type);
        builder.setSymbolName(symbolName);
        builder.setValueExpr(exprStack.pop());

        Const constant = builder.build();
        bFileBuilder.addConst(constant);
    }

    public void createVariableDcl(String varName, NodeLocation location) {
        // Create a variable declaration
        SymbolName localVarId = new SymbolName(varName);
        BType localVarType = typeQueue.remove();

        VariableDcl variableDcl = new VariableDcl(location, localVarType, localVarId);

        // Add this variable declaration to the current callable unit or callable unit group
        if (currentCUBuilder != null) {
            // This connector declaration should added to the relevant function/action or resource
//            currentCUBuilder.addVariableDcl(variableDcl);
        } else {
            currentCUGroupBuilder.addVariableDcl(variableDcl);
        }

    }

    public void createConnectorDcl(String varName, NodeLocation location) {
        // Here we build the object model for the following line

        // Here we need to pop the symbolName stack twice as the connector name appears twice in the declaration.
        if (symbolNameStack.size() < 2) {
            IllegalStateException ex = new IllegalStateException("symbol stack size should be " +
                    "greater than or equal to two");
            throw new ParserException("Failed to parse connector declaration" + varName + " in " +
                    location.getFileName() + ":" + location.getLineNumber(), ex);
        }

        symbolNameStack.pop();
        SymbolName cSymName = symbolNameStack.pop();
        List<Expression> exprList = exprListStack.pop();

        ConnectorDcl.ConnectorDclBuilder builder = new ConnectorDcl.ConnectorDclBuilder();
        builder.setConnectorName(cSymName);
        builder.setVarName(new SymbolName(varName));
        builder.setExprList(exprList);
        builder.setNodeLocation(location);

        ConnectorDcl connectorDcl = builder.build();
        if (currentCUBuilder != null) {
            // This connector declaration should added to the relevant function/action or resource
//            currentCUBuilder.addConnectorDcl(connectorDcl);
        } else {
            currentCUGroupBuilder.addConnectorDcl(connectorDcl);
        }
    }

    public void startVarRefList() {
        exprListStack.push(new ArrayList<>());
    }

    public void endVarRefList(int exprCount) {
        List<Expression> exprList = exprListStack.peek();
        addExprToList(exprList, exprCount);
    }

    /**
     * Create variable reference expression.
     * <p/>
     * There are three types of variables references as per the grammar file.
     * 1) Simple variable references. a, b, index etc
     * 2) Map or array access a[1], m["key"]
     * 3) Struct field access  Person.name
     */
    public void createVarRefExpr(String varName, NodeLocation location) {
        SymbolName symName = new SymbolName(varName);
        VariableRefExpr variableRefExpr = new VariableRefExpr(location, symName);
        exprStack.push(variableRefExpr);
    }

    public void createMapArrayVarRefExpr(String varName, NodeLocation location) {
        SymbolName symName = new SymbolName(varName);

        Expression indexExpr = exprStack.pop();
        VariableRefExpr arrayVarRefExpr = new VariableRefExpr(location, symName);

        ArrayMapAccessExpr.ArrayMapAccessExprBuilder builder = new ArrayMapAccessExpr.ArrayMapAccessExprBuilder();
        builder.setVarName(symName);
        builder.setIndexExpr(indexExpr);
        builder.setArrayMapVarRefExpr(arrayVarRefExpr);
        builder.setNodeLocation(location);

        ArrayMapAccessExpr accessExpr = builder.build();
        exprStack.push(accessExpr);
    }

    // Expressions

    public void createBinaryExpr(String opStr, NodeLocation location) {
        Expression rExpr = exprStack.pop();
        Expression lExpr = exprStack.pop();

        BinaryExpression expr;
        switch (opStr) {
            case "+":
                expr = new AddExpression(location, lExpr, rExpr);
                break;

            case "-":
                expr = new SubtractExpression(location, lExpr, rExpr);
                break;

            case "*":
                expr = new MultExpression(location, lExpr, rExpr);
                break;

            case "/":
                expr = new DivideExpr(location, lExpr, rExpr);
                break;

            case "&&":
                expr = new AndExpression(location, lExpr, rExpr);
                break;

            case "||":
                expr = new OrExpression(location, lExpr, rExpr);
                break;

            case "==":
                expr = new EqualExpression(location, lExpr, rExpr);
                break;

            case "!=":
                expr = new NotEqualExpression(location, lExpr, rExpr);
                break;

            case ">=":
                expr = new GreaterEqualExpression(location, lExpr, rExpr);
                break;

            case ">":
                expr = new GreaterThanExpression(location, lExpr, rExpr);
                break;

            case "<":
                expr = new LessThanExpression(location, lExpr, rExpr);
                break;

            case "<=":
                expr = new LessEqualExpression(location, lExpr, rExpr);
                break;

            default:
                throw new ParserException("Unsupported operator '" + opStr + "' in " +
                        location.getFileName() + ":" + location.getLineNumber());
        }

        exprStack.push(expr);
    }

    public void createUnaryExpr(String op, NodeLocation location) {
        Expression rExpr = exprStack.pop();

        UnaryExpression expr;
        switch (op) {
            case "+":
                expr = new UnaryExpression(location, Operator.ADD, rExpr);
                break;

            case "-":
                expr = new UnaryExpression(location, Operator.SUB, rExpr);
                break;

            case "!":
                expr = new UnaryExpression(location, Operator.NOT, rExpr);
                break;

            default:
                throw new ParserException("Unsupported operator '" + op + "' in " +
                        location.getFileName() + ":" + location.getLineNumber());
        }

        exprStack.push(expr);
    }

    public void createBacktickExpr(String stringContent, NodeLocation location) {
        String templateStr = getValueWithinBackquote(stringContent);
        BacktickExpr backtickExpr = new BacktickExpr(location, templateStr);
        exprStack.push(backtickExpr);
    }

    public void startExprList() {
        exprListStack.push(new ArrayList<>());
    }

    public void endExprList(int exprCount) {
        List<Expression> exprList = exprListStack.peek();
        addExprToList(exprList, exprCount);
    }

    public void createFunctionInvocationExpr(NodeLocation location) {
        CallableUnitInvocationExprBuilder cIExprBuilder = new CallableUnitInvocationExprBuilder();
        cIExprBuilder.setExpressionList(exprListStack.pop());
        cIExprBuilder.setName(symbolNameStack.pop());
        cIExprBuilder.setNodeLocation(location);

        FunctionInvocationExpr invocationExpr = cIExprBuilder.buildFuncInvocExpr();
        exprStack.push(invocationExpr);
    }

    public void createTypeCastExpr(String targetTypeName, NodeLocation location) {
        TypeCastExpression typeCastExpression = new TypeCastExpression(location, exprStack.pop(),
                BTypes.getType(targetTypeName));
        //Remove the type added to type queue
        typeQueue.remove();
        exprStack.push(typeCastExpression);
    }

    public void createActionInvocationExpr(NodeLocation location) {
        CallableUnitInvocationExprBuilder cIExprBuilder = new CallableUnitInvocationExprBuilder();
        cIExprBuilder.setExpressionList(exprListStack.pop());
        cIExprBuilder.setName(symbolNameStack.pop());
        cIExprBuilder.setNodeLocation(location);

        ActionInvocationExpr invocationExpr = cIExprBuilder.buildActionInvocExpr();
        exprStack.push(invocationExpr);
    }

    public void createArrayInitExpr(NodeLocation location) {
        List<Expression> argList;
        if (!exprListStack.isEmpty()) {
            argList = exprListStack.pop();
        } else {
            argList = new ArrayList<>(0);
        }

        ArrayInitExpr arrayInitExpr = new ArrayInitExpr(location, argList.toArray(new Expression[argList.size()]));
        exprStack.push(arrayInitExpr);
    }

    public void createMapInitExpr(NodeLocation location) {
        List<KeyValueExpression> argList;
        if (!mapInitKeyValueListStack.isEmpty()) {
            argList = mapInitKeyValueListStack.pop();
        } else {
            argList = new ArrayList<>(0);
        }

        MapInitExpr mapInitExpr = new MapInitExpr(location, argList.toArray(new Expression[argList.size()]));
        exprStack.push(mapInitExpr);
    }

    public void startMapInitKeyValue() {
        mapInitKeyValueListStack.push(new ArrayList<>());
    }

    public void endMapInitKeyValue(int exprCount) {
        List<KeyValueExpression> keyValueList = mapInitKeyValueListStack.peek();
        addKeyValueToList(keyValueList, exprCount);
    }

    public void createMapInitKeyValue(String key, NodeLocation location) {
        if (!exprStack.isEmpty()) {
            Expression currentExpression = exprStack.pop();
            keyValueStack.push(new KeyValueExpression(location, key, currentExpression));
        } else {
            keyValueStack.push(new KeyValueExpression(location, key, null));
        }


    }

    // Functions, Actions and Resources

    public void startCallableUnitBody() {
        blockStmtBuilderStack.push(new BlockStmt.BlockStmtBuilder());
    }

    public void endCallableUnitBody() {
        BlockStmt.BlockStmtBuilder blockStmtBuilder = blockStmtBuilderStack.pop();
        BlockStmt blockStmt = blockStmtBuilder.build();

        currentCUBuilder.setBody(blockStmt);
    }

    public void startCallableUnit() {
        currentCUBuilder = new CallableUnitBuilder();
        annotationListStack.push(new ArrayList<>());
    }

    public void createFunction(String name, boolean isPublic, NodeLocation location) {
        currentCUBuilder.setName(new SymbolName(name, pkgName));
        currentCUBuilder.setPublic(isPublic);
        currentCUBuilder.setNodeLocation(location);

        List<Annotation> annotationList = annotationListStack.pop();
        // TODO Improve this implementation
        annotationList.forEach(currentCUBuilder::addAnnotation);

        BallerinaFunction function = currentCUBuilder.buildFunction();
        bFileBuilder.addFunction(function);

        currentCUBuilder = null;
    }

    public void createTypeConverter(String name, boolean isPublic, NodeLocation location) {
        currentCUBuilder.setName(new SymbolName(name, pkgName));
        currentCUBuilder.setPublic(isPublic);
        currentCUBuilder.setNodeLocation(location);
        BTypeConvertor typeConvertor = currentCUBuilder.buildTypeConverter();
        bFileBuilder.addTypeConverter(typeConvertor);
        currentCUBuilder = null;
    }

    public void createResource(String name, NodeLocation location) {
        currentCUBuilder.setName(new SymbolName(name, pkgName));
        currentCUBuilder.setNodeLocation(location);

        List<Annotation> annotationList = annotationListStack.pop();
        // TODO Improve this implementation
        annotationList.forEach(currentCUBuilder::addAnnotation);

        Resource resource = currentCUBuilder.buildResource();
        currentCUGroupBuilder.addResource(resource);

        currentCUBuilder = null;
    }

    public void createAction(String name, NodeLocation location) {
        currentCUBuilder.setName(new SymbolName(name, pkgName));
        currentCUBuilder.setNodeLocation(location);

        List<Annotation> annotationList = annotationListStack.pop();
        // TODO Improve this implementation
        annotationList.forEach(currentCUBuilder::addAnnotation);

        BallerinaAction action = currentCUBuilder.buildAction();
        currentCUGroupBuilder.addAction(action);

        currentCUBuilder = null;
    }

    // Services and Connectors

    public void startCallableUnitGroup() {
        currentCUGroupBuilder = new CallableUnitGroupBuilder();
        annotationListStack.push(new ArrayList<>());
    }

    public void createService(String name, NodeLocation location) {
        currentCUGroupBuilder.setName(new SymbolName(name, pkgName));
        currentCUGroupBuilder.setNodeLocation(location);

        List<Annotation> annotationList = annotationListStack.pop();
        // TODO Improve this implementation
        annotationList.forEach(currentCUGroupBuilder::addAnnotation);

        Service service = currentCUGroupBuilder.buildService();
        bFileBuilder.addService(service);

        currentCUGroupBuilder = null;
    }

    public void createConnector(String name, NodeLocation location) {
        currentCUGroupBuilder.setName(new SymbolName(name, pkgName));
        currentCUGroupBuilder.setNodeLocation(location);

        List<Annotation> annotationList = annotationListStack.pop();
        // TODO Improve this implementation
        annotationList.forEach(currentCUGroupBuilder::addAnnotation);

        BallerinaConnector connector = currentCUGroupBuilder.buildConnector();
        bFileBuilder.addConnector(connector);

        currentCUGroupBuilder = null;
    }

    // Statements

    public void createAssignmentStmt(NodeLocation location) {
        Expression rExpr = exprStack.pop();
        List<Expression> lExprList = exprListStack.pop();

        AssignStmt assignStmt = new AssignStmt(location, lExprList.toArray(new Expression[lExprList.size()]), rExpr);
        addToBlockStmt(assignStmt);
    }

    public void createReturnStmt(NodeLocation location) {
        Expression[] exprs;
        // Get the expression list from the expression list stack
        if (!exprListStack.isEmpty()) {
            // Return statement with empty expression list.
            // Just a return statement
            List<Expression> exprList = exprListStack.pop();
            exprs = exprList.toArray(new Expression[exprList.size()]);
        } else {
            exprs = new Expression[0];
        }

        ReturnStmt returnStmt = new ReturnStmt(location, exprs);
        addToBlockStmt(returnStmt);
    }

    public void createReplyStmt(NodeLocation location) {
        ReplyStmt replyStmt = new ReplyStmt(location, exprStack.pop());
        addToBlockStmt(replyStmt);
    }

    public void startWhileStmt() {
        blockStmtBuilderStack.push(new BlockStmt.BlockStmtBuilder());
    }

    public void endWhileStmt(NodeLocation location) {
        // Create a while statement builder
        WhileStmt.WhileStmtBuilder whileStmtBuilder = new WhileStmt.WhileStmtBuilder();
        whileStmtBuilder.setNodeLocation(location);

        // Get the expression at the top of the expression stack and set it as the while condition
        whileStmtBuilder.setCondition(exprStack.pop());

        // Get the statement block at the top of the block statement stack and set as the while body.
        whileStmtBuilder.setWhileBody(blockStmtBuilderStack.pop().build());

        // Add the while statement to the statement block which is at the top of the stack.
        WhileStmt whileStmt = whileStmtBuilder.build();
        blockStmtBuilderStack.peek().addStmt(whileStmt);
    }

    public void startIfElseStmt(NodeLocation location) {
        IfElseStmt.IfElseStmtBuilder ifElseStmtBuilder = new IfElseStmt.IfElseStmtBuilder();
        ifElseStmtBuilder.setNodeLocation(location);
        ifElseStmtBuilderStack.push(ifElseStmtBuilder);

        BlockStmt.BlockStmtBuilder blockStmtBuilder = new BlockStmt.BlockStmtBuilder();
        blockStmtBuilder.setNodeLocation(location);
        blockStmtBuilderStack.push(blockStmtBuilder);
    }

    public void startElseIfClause(NodeLocation location) {
        BlockStmt.BlockStmtBuilder blockStmtBuilder = new BlockStmt.BlockStmtBuilder();
        blockStmtBuilder.setNodeLocation(location);
        blockStmtBuilderStack.push(blockStmtBuilder);
    }

    public void endElseIfClause() {
        IfElseStmt.IfElseStmtBuilder ifElseStmtBuilder = ifElseStmtBuilderStack.peek();

        BlockStmt.BlockStmtBuilder blockStmtBuilder = blockStmtBuilderStack.pop();
        BlockStmt elseIfStmtBlock = blockStmtBuilder.build();
        ifElseStmtBuilder.addElseIfBlock(elseIfStmtBlock.getNodeLocation(), exprStack.pop(), elseIfStmtBlock);
    }

    public void startElseClause() {
        blockStmtBuilderStack.push(new BlockStmt.BlockStmtBuilder());
    }

    public void endElseClause() {
        IfElseStmt.IfElseStmtBuilder ifElseStmtBuilder = ifElseStmtBuilderStack.peek();
        BlockStmt.BlockStmtBuilder blockStmtBuilder = blockStmtBuilderStack.pop();
        BlockStmt elseStmt = blockStmtBuilder.build();
        ifElseStmtBuilder.setElseBody(elseStmt);
    }

    public void endIfElseStmt() {
        IfElseStmt.IfElseStmtBuilder ifElseStmtBuilder = ifElseStmtBuilderStack.pop();
        ifElseStmtBuilder.setIfCondition(exprStack.pop());

        BlockStmt.BlockStmtBuilder blockStmtBuilder = blockStmtBuilderStack.pop();
        ifElseStmtBuilder.setThenBody(blockStmtBuilder.build());

        IfElseStmt ifElseStmt = ifElseStmtBuilder.build();
        addToBlockStmt(ifElseStmt);
    }

    public void createFunctionInvocationStmt(NodeLocation location) {
        CallableUnitInvocationExprBuilder cIExprBuilder = new CallableUnitInvocationExprBuilder();
        cIExprBuilder.setExpressionList(exprListStack.pop());
        cIExprBuilder.setName(symbolNameStack.pop());
        cIExprBuilder.setNodeLocation(location);

        FunctionInvocationExpr invocationExpr = cIExprBuilder.buildFuncInvocExpr();
        FunctionInvocationStmt functionInvocationStmt = new FunctionInvocationStmt(location, invocationExpr);
        blockStmtBuilderStack.peek().addStmt(functionInvocationStmt);
    }

    public void createActionInvocationStmt(NodeLocation location) {
        CallableUnitInvocationExprBuilder cIExprBuilder = new CallableUnitInvocationExprBuilder();
        cIExprBuilder.setExpressionList(exprListStack.pop());
        cIExprBuilder.setName(symbolNameStack.pop());
        cIExprBuilder.setNodeLocation(location);

        ActionInvocationExpr invocationExpr = cIExprBuilder.buildActionInvocExpr();

        ActionInvocationStmt actionInvocationStmt = new ActionInvocationStmt(location, invocationExpr);
        blockStmtBuilderStack.peek().addStmt(actionInvocationStmt);
    }

    // Literal Values

    public void createIntegerLiteral(String value, NodeLocation location) {
        BValueType bValue = new BInteger(Integer.parseInt(value));
        createLiteral(bValue, BTypes.INT_TYPE, location);
    }

    public void createLongLiteral(String value, NodeLocation location) {
        BValueType bValue = new BLong(Long.parseLong(value));
        createLiteral(bValue, BTypes.LONG_TYPE, location);
    }

    public void createFloatLiteral(String value, NodeLocation location) {
        BValueType bValue = new BFloat(Float.parseFloat(value));
        createLiteral(bValue, BTypes.FLOAT_TYPE, location);
    }

    public void createDoubleLiteral(String value, NodeLocation location) {
        BValueType bValue = new BDouble(Double.parseDouble(value));
        createLiteral(bValue, BTypes.DOUBLE_TYPE, location);
    }

    public void createStringLiteral(String value, NodeLocation location) {
        BValueType bValue = new BString(value);
        createLiteral(bValue, BTypes.STRING_TYPE, location);
    }

    public void createBooleanLiteral(String value, NodeLocation location) {
        BValueType bValue = new BBoolean(Boolean.parseBoolean(value));
        createLiteral(bValue, BTypes.BOOLEAN_TYPE, location);
    }

    public void createNullLiteral(String value, NodeLocation location) {
        throw new RuntimeException("Null values are not yet supported in Ballerina in " + location.getFileName()
                + ":" + location.getLineNumber());
    }

    // Private methods

    private void addToBlockStmt(Statement stmt) {
        BlockStmt.BlockStmtBuilder blockStmtBuilder = blockStmtBuilderStack.peek();
        blockStmtBuilder.addStmt(stmt);
    }

    private void createLiteral(BValueType bValueType, BType type, NodeLocation location) {
        BasicLiteral basicLiteral = new BasicLiteral(location, bValueType);
        basicLiteral.setType(type);
        exprStack.push(basicLiteral);
    }

    /**
     * @param exprList List<Expression>
     * @param n        number of expression to be added the given list
     */
    private void addExprToList(List<Expression> exprList, int n) {

        if (exprStack.isEmpty()) {
            throw new IllegalStateException("Expression stack cannot be empty in processing an ExpressionList");
        }

        if (n == 1) {
            Expression expr = exprStack.pop();
            exprList.add(expr);
        } else {
            Expression expr = exprStack.pop();
            addExprToList(exprList, n - 1);
            exprList.add(expr);
        }
    }

    /**
     * @param keyValueDataHolderList List<KeyValueDataHolder>
     * @param n                      number of expression to be added the given list
     */
    private void addKeyValueToList(List<KeyValueExpression> keyValueDataHolderList, int n) {

        if (keyValueStack.isEmpty()) {
            throw new IllegalStateException("KeyValue stack cannot be empty in processing a KeyValueList");
        }

        if (n == 1) {
            KeyValueExpression keyValue = keyValueStack.pop();
            keyValueDataHolderList.add(keyValue);
        } else {
            KeyValueExpression keyValue = keyValueStack.pop();
            addKeyValueToList(keyValueDataHolderList, n - 1);
            keyValueDataHolderList.add(keyValue);
        }
    }

    private String getPkgName() {
        if (pkgNameStack.isEmpty()) {
            throw new IllegalStateException("Package name stack is empty");
        }

        return pkgNameStack.pop();
    }

    /**
     * return value within double quotes.
     *
     * @param inputString string with double quotes
     * @return value
     */
    private static String getValueWithinBackquote(String inputString) {
        Pattern p = Pattern.compile("`([^`]*)`");
        Matcher m = p.matcher(inputString);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /**
     * Start a struct builder.
     */
    public void startStruct() {
        structBuilder = new Struct.StructBuilder();
    }

    /**
     * Creates a {@link Struct}.
     *
     * @param name     Name of the {@link Struct}
     * @param isPublic Flag indicating whether the {@link Struct} is public
     * @param location Location of this {@link Struct} in the source file
     */
    public void createStructDefinition(String name, boolean isPublic, NodeLocation location) {
        structBuilder.setStructName(new SymbolName(name, pkgName));
        structBuilder.setNodeLocation(location);
        structBuilder.setPublic(isPublic);
        Struct struct = structBuilder.build();
        bFileBuilder.addStruct(struct);
        structBuilder = null;
        registerStructType(name);
    }

    /**
     * Add an field of the {@link Struct}.
     *
     * @param fieldName Name of the field in the {@link Struct}
     * @param location  Location of the field in the source file
     */
    public void createStructField(String fieldName, NodeLocation location) {
        // Create a struct field declaration
        SymbolName localVarId = new SymbolName(fieldName);
        BType localVarType = typeQueue.remove();

        VariableDcl variableDcl = new VariableDcl(location, localVarType, localVarId);
        structBuilder.addField(variableDcl);
    }

    /**
     * Register the user defined struct type as a data type
     *
     * @param typeName Name of the Struct
     */
    private void registerStructType(String typeName) {
        BTypes.addStructType(typeName);
    }

    /**
     * Create a struct initializing expression
     *
     * @param location   Location of the initialization in the source bal file
     * @param structName Name of the struct type
     */
    public void createStructInitExpr(NodeLocation location, String structName) {
        // Create the Struct declaration
        SymbolName structSymName = new SymbolName(structName);
        StructDcl structDcl = new StructDcl(location, structSymName);

        // Create the RHS of the expression
        StructInitExpr structInitExpr = new StructInitExpr(location, structDcl);
        structInitExpr.setType(new BStructType(structName));
        exprStack.push(structInitExpr);
    }

    /**
     * Create an expression for accessing fields of user defined struct types.
     *
     * @param location Source location of the ballerina file
     */
    public void createStructFieldRefExpr(NodeLocation location) {
        if (exprStack.size() < 2) {
            return;
        }
        ReferenceExpr field = (ReferenceExpr) exprStack.pop();
        StructFieldAccessExpr fieldExpr;
        if (field instanceof StructFieldAccessExpr) {
            fieldExpr = (StructFieldAccessExpr) field;
        } else {
            fieldExpr = new StructFieldAccessExpr(location, field.getSymbolName(), field);
        }

        ReferenceExpr parent = (ReferenceExpr) exprStack.pop();
        StructFieldAccessExpr parentExpr = new StructFieldAccessExpr(location, parent.getSymbolName(), parent);

        parentExpr.setFieldExpr(fieldExpr);
        fieldExpr.setParent(parentExpr);
        exprStack.push(parentExpr);
    }

}
