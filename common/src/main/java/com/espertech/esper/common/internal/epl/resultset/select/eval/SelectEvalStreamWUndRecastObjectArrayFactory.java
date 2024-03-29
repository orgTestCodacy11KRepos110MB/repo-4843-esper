/*
 ***************************************************************************************
 *  Copyright (C) 2006 EsperTech, Inc. All rights reserved.                            *
 *  http://www.espertech.com/esper                                                     *
 *  http://www.espertech.com                                                           *
 *  ---------------------------------------------------------------------------------- *
 *  The software in this package is published under the terms of the GPL license       *
 *  a copy of which has been included with this distribution in the license.txt file.  *
 ***************************************************************************************
 */
package com.espertech.esper.common.internal.epl.resultset.select.eval;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.common.client.EventType;
import com.espertech.esper.common.client.type.EPTypeClass;
import com.espertech.esper.common.client.type.EPTypePremade;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenBlock;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenClassScope;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenMethod;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenMethodScope;
import com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpression;
import com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionField;
import com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionRef;
import com.espertech.esper.common.internal.epl.expression.codegen.ExprForgeCodegenSymbol;
import com.espertech.esper.common.internal.epl.expression.core.*;
import com.espertech.esper.common.internal.epl.resultset.select.core.SelectExprForgeContext;
import com.espertech.esper.common.internal.epl.resultset.select.core.SelectExprProcessorCodegenSymbol;
import com.espertech.esper.common.internal.epl.resultset.select.core.SelectExprProcessorForge;
import com.espertech.esper.common.internal.event.arr.ObjectArrayEventType;
import com.espertech.esper.common.internal.event.core.*;
import com.espertech.esper.common.internal.settings.ClasspathImportServiceCompileTime;
import com.espertech.esper.common.internal.util.TypeWidenerException;
import com.espertech.esper.common.internal.util.TypeWidenerFactory;
import com.espertech.esper.common.internal.util.TypeWidenerSPI;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionBuilder.*;

public class SelectEvalStreamWUndRecastObjectArrayFactory {

    public static SelectExprProcessorForge make(EventType[] eventTypes, SelectExprForgeContext selectExprForgeContext, int streamNumber, EventType targetType, ExprNode[] exprNodes, ClasspathImportServiceCompileTime classpathImportService, String statementName)
            throws ExprValidationException {
        ObjectArrayEventType oaResultType = (ObjectArrayEventType) targetType;
        ObjectArrayEventType oaStreamType = (ObjectArrayEventType) eventTypes[streamNumber];

        // (A) fully assignment-compatible: same number, name and type of fields, no additional expressions: Straight repackage
        if (oaResultType.isDeepEqualsConsiderOrder(oaStreamType) && selectExprForgeContext.getExprForges().length == 0) {
            return new OAInsertProcessorSimpleRepackage(selectExprForgeContext, streamNumber, targetType);
        }

        // (B) not completely assignable: find matching properties
        Set<WriteablePropertyDescriptor> writables = EventTypeUtility.getWriteableProperties(oaResultType, true, false);
        List<Item> items = new ArrayList<Item>();
        List<WriteablePropertyDescriptor> written = new ArrayList<WriteablePropertyDescriptor>();

        // find the properties coming from the providing source stream
        for (WriteablePropertyDescriptor writeable : writables) {
            String propertyName = writeable.getPropertyName();

            Integer indexSource = oaStreamType.getPropertiesIndexes().get(propertyName);
            Integer indexTarget = oaResultType.getPropertiesIndexes().get(propertyName);

            if (indexSource != null) {
                Object setOneType = oaResultType.getTypes().get(propertyName);
                boolean setOneTypeFound = oaResultType.getTypes().containsKey(propertyName);
                Object setTwoType = oaStreamType.getTypes().get(propertyName);
                ExprValidationException message = BaseNestableEventUtil.comparePropType(propertyName, setOneType, setOneTypeFound, setTwoType, oaResultType.getName());
                if (message != null) {
                    throw new ExprValidationException(message.getMessage(), message);
                }
                items.add(new Item(indexTarget, indexSource, null, null));
                written.add(writeable);
            }
        }

        // find the properties coming from the expressions of the select clause
        int count = written.size();
        for (int i = 0; i < selectExprForgeContext.getExprForges().length; i++) {
            String columnName = selectExprForgeContext.getColumnNames()[i];
            ExprForge forge = selectExprForgeContext.getExprForges()[i];
            ExprNode exprNode = exprNodes[i];

            WriteablePropertyDescriptor writable = findWritable(columnName, writables);
            if (writable == null) {
                throw new ExprValidationException("Failed to find column '" + columnName + "' in target type '" + oaResultType.getName() + "'");
            }

            TypeWidenerSPI widener;
            try {
                widener = TypeWidenerFactory.getCheckPropertyAssignType(ExprNodeUtilityPrint.toExpressionStringMinPrecedenceSafe(exprNode), exprNode.getForge().getEvaluationType(),
                        writable.getType(), columnName, false, null, statementName);
            } catch (TypeWidenerException ex) {
                throw new ExprValidationException(ex.getMessage(), ex);
            }

            items.add(new Item(count, -1, forge, widener));
            written.add(writable);
            count++;
        }

        // make manufacturer
        Item[] itemsArr = items.toArray(new Item[items.size()]);
        EventBeanManufacturerForge manufacturer;
        try {
            manufacturer = EventTypeUtility.getManufacturer(oaResultType,
                    written.toArray(new WriteablePropertyDescriptor[written.size()]), classpathImportService, true, selectExprForgeContext.getEventTypeAvroHandler());
        } catch (EventBeanManufactureException e) {
            throw new ExprValidationException("Failed to write to type: " + e.getMessage(), e);
        }

        return new OAInsertProcessorAllocate(streamNumber, itemsArr, manufacturer, targetType);
    }

    private static WriteablePropertyDescriptor findWritable(String columnName, Set<WriteablePropertyDescriptor> writables) {
        for (WriteablePropertyDescriptor writable : writables) {
            if (writable.getPropertyName().equals(columnName)) {
                return writable;
            }
        }
        return null;
    }

    private static class OAInsertProcessorSimpleRepackage implements SelectExprProcessorForge {
        private final SelectExprForgeContext selectExprForgeContext;
        private final int underlyingStreamNumber;
        private final EventType resultType;

        private OAInsertProcessorSimpleRepackage(SelectExprForgeContext selectExprForgeContext, int underlyingStreamNumber, EventType resultType) {
            this.selectExprForgeContext = selectExprForgeContext;
            this.underlyingStreamNumber = underlyingStreamNumber;
            this.resultType = resultType;
        }

        public EventType getResultEventType() {
            return resultType;
        }

        public CodegenMethod processCodegen(CodegenExpression resultEventType, CodegenExpression eventBeanFactory, CodegenMethodScope codegenMethodScope, SelectExprProcessorCodegenSymbol selectSymbol, ExprForgeCodegenSymbol exprSymbol, CodegenClassScope codegenClassScope) {
            CodegenMethod methodNode = codegenMethodScope.makeChild(EventBean.EPTYPE, this.getClass(), codegenClassScope);
            CodegenExpressionRef refEPS = exprSymbol.getAddEPS(methodNode);
            CodegenExpression value = exprDotMethod(cast(ObjectArrayBackedEventBean.EPTYPE, arrayAtIndex(refEPS, constant(underlyingStreamNumber))), "getProperties");
            methodNode.getBlock().methodReturn(exprDotMethod(eventBeanFactory, "adapterForTypedObjectArray", value, resultEventType));
            return methodNode;
        }
    }

    private static class OAInsertProcessorAllocate implements SelectExprProcessorForge {
        private final int underlyingStreamNumber;
        private final Item[] items;
        private final EventBeanManufacturerForge manufacturer;
        private final EventType resultType;

        private OAInsertProcessorAllocate(int underlyingStreamNumber, Item[] items, EventBeanManufacturerForge manufacturer, EventType resultType) {
            this.underlyingStreamNumber = underlyingStreamNumber;
            this.items = items;
            this.manufacturer = manufacturer;
            this.resultType = resultType;
        }

        public EventType getResultEventType() {
            return resultType;
        }

        public CodegenMethod processCodegen(CodegenExpression resultEventType, CodegenExpression eventBeanFactory, CodegenMethodScope codegenMethodScope, SelectExprProcessorCodegenSymbol selectSymbol, ExprForgeCodegenSymbol exprSymbol, CodegenClassScope codegenClassScope) {
            CodegenExpressionField manufacturerField = codegenClassScope.addFieldUnshared(true, EventBeanManufacturer.EPTYPE, manufacturer.make(codegenMethodScope, codegenClassScope));
            CodegenMethod methodNode = codegenMethodScope.makeChild(EventBean.EPTYPE, this.getClass(), codegenClassScope);
            CodegenExpressionRef refEPS = exprSymbol.getAddEPS(methodNode);
            CodegenBlock block = methodNode.getBlock()
                    .declareVar(ObjectArrayBackedEventBean.EPTYPE, "theEvent", cast(ObjectArrayBackedEventBean.EPTYPE, arrayAtIndex(refEPS, constant(underlyingStreamNumber))))
                    .declareVar(EPTypePremade.OBJECTARRAY.getEPType(), "props", newArrayByLength(EPTypePremade.OBJECT.getEPType(), constant(items.length)));
            for (Item item : items) {
                if (item.getOptionalFromIndex() != -1) {
                    block.assignArrayElement("props", constant(item.getToIndex()), arrayAtIndex(exprDotMethod(ref("theEvent"), "getProperties"), constant(item.getOptionalFromIndex())));
                } else {
                    CodegenExpression value;
                    if (item.getOptionalWidener() != null) {
                        value = item.forge.evaluateCodegen((EPTypeClass) item.forge.getEvaluationType(), methodNode, exprSymbol, codegenClassScope);
                        value = item.getOptionalWidener().widenCodegen(value, methodNode, codegenClassScope);
                    } else {
                        value = item.forge.evaluateCodegen(EPTypePremade.OBJECT.getEPType(), methodNode, exprSymbol, codegenClassScope);
                    }
                    block.assignArrayElement("props", constant(item.getToIndex()), value);
                }
            }
            block.methodReturn(exprDotMethod(manufacturerField, "make", ref("props")));
            return methodNode;
        }
    }

    private static class Item {
        private final int toIndex;
        private final int optionalFromIndex;
        private final ExprForge forge;
        private final TypeWidenerSPI optionalWidener;

        private ExprEvaluator evaluatorAssigned;

        private Item(int toIndex, int optionalFromIndex, ExprForge forge, TypeWidenerSPI optionalWidener) {
            this.toIndex = toIndex;
            this.optionalFromIndex = optionalFromIndex;
            this.forge = forge;
            this.optionalWidener = optionalWidener;
        }

        public int getToIndex() {
            return toIndex;
        }

        public int getOptionalFromIndex() {
            return optionalFromIndex;
        }

        public ExprForge getForge() {
            return forge;
        }

        public TypeWidenerSPI getOptionalWidener() {
            return optionalWidener;
        }

        public ExprEvaluator getEvaluatorAssigned() {
            return evaluatorAssigned;
        }

        public void setEvaluatorAssigned(ExprEvaluator evaluatorAssigned) {
            this.evaluatorAssigned = evaluatorAssigned;
        }
    }
}
