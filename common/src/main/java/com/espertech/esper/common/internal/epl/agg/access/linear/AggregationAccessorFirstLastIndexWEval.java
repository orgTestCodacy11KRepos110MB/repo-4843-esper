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
package com.espertech.esper.common.internal.epl.agg.access.linear;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.common.client.type.EPTypeClass;
import com.espertech.esper.common.client.type.EPTypePremade;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenClassScope;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenMethod;
import com.espertech.esper.common.internal.bytecodemodel.core.CodegenNamedMethods;
import com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpression;
import com.espertech.esper.common.internal.epl.agg.core.AggregationAccessorForgeGetCodegenContext;
import com.espertech.esper.common.internal.epl.expression.codegen.CodegenLegoMethodExpression;
import com.espertech.esper.common.internal.util.SimpleNumberCoercerFactory;

import java.util.Collections;

import static com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionBuilder.*;
import static com.espertech.esper.common.internal.epl.expression.codegen.ExprForgeCodegenNames.NAME_EXPREVALCONTEXT;

/**
 * Represents the aggregation accessor that provides the result for the "first" and "last" aggregation function with index.
 */
public class AggregationAccessorFirstLastIndexWEval {
    public static void getValueCodegen(AggregationAccessorFirstLastIndexWEvalForge forge, AggregationAccessorForgeGetCodegenContext context) {
        AggregationStateLinearForge stateForge = (AggregationStateLinearForge) context.getAccessStateForge();
        CodegenMethod getBeanFirstLastIndex = getBeanFirstLastIndexCodegen(forge, context.getColumn(), context.getClassScope(), stateForge, context.getMethod(), context.getNamedMethods());
        context.getMethod().getBlock().declareVar(EventBean.EPTYPE, "bean", localMethod(getBeanFirstLastIndex))
                .ifRefNullReturnNull("bean")
                .declareVar(EventBean.EPTYPEARRAY, "eventsPerStreamBuf", newArrayByLength(EventBean.EPTYPE, constant(forge.getStreamNum() + 1)))
                .assignArrayElement("eventsPerStreamBuf", constant(forge.getStreamNum()), ref("bean"))
                .methodReturn(localMethod(CodegenLegoMethodExpression.codegenExpression(forge.getChildNode(), context.getMethod(), context.getClassScope()), ref("eventsPerStreamBuf"), constantTrue(), ref(NAME_EXPREVALCONTEXT)));
    }

    public static void getEnumerableEventsCodegen(AggregationAccessorFirstLastIndexWEvalForge forge, AggregationAccessorForgeGetCodegenContext context) {
        AggregationStateLinearForge stateForge = (AggregationStateLinearForge) context.getAccessStateForge();
        CodegenMethod getBeanFirstLastIndex = getBeanFirstLastIndexCodegen(forge, context.getColumn(), context.getClassScope(), stateForge, context.getMethod(), context.getNamedMethods());
        context.getMethod().getBlock().declareVar(EventBean.EPTYPE, "bean", localMethod(getBeanFirstLastIndex))
                .ifRefNullReturnNull("bean")
                .methodReturn(staticMethod(Collections.class, "singletonList", ref("bean")));
    }

    public static void getEnumerableScalarCodegen(AggregationAccessorFirstLastIndexWEvalForge forge, AggregationAccessorForgeGetCodegenContext context) {
        AggregationStateLinearForge stateForge = (AggregationStateLinearForge) context.getAccessStateForge();
        CodegenMethod getBeanFirstLastIndex = getBeanFirstLastIndexCodegen(forge, context.getColumn(), context.getClassScope(), stateForge, context.getMethod(), context.getNamedMethods());
        context.getMethod().getBlock().declareVar(EventBean.EPTYPE, "bean", localMethod(getBeanFirstLastIndex))
                .ifRefNullReturnNull("bean")
                .declareVar(EventBean.EPTYPEARRAY, "eventsPerStreamBuf", newArrayByLength(EventBean.EPTYPE, constant(forge.getStreamNum() + 1)))
                .assignArrayElement("eventsPerStreamBuf", constant(forge.getStreamNum()), ref("bean"))
                .declareVar(EPTypePremade.OBJECT.getEPType(), "value", localMethod(CodegenLegoMethodExpression.codegenExpression(forge.getChildNode(), context.getMethod(), context.getClassScope()), ref("eventsPerStreamBuf"), constantTrue(), ref(NAME_EXPREVALCONTEXT)))
                .ifRefNullReturnNull("value")
                .methodReturn(staticMethod(Collections.class, "singletonList", ref("value")));
    }

    public static void getEnumerableEventCodegen(AggregationAccessorFirstLastIndexWEvalForge forge, AggregationAccessorForgeGetCodegenContext context) {
        AggregationStateLinearForge stateForge = (AggregationStateLinearForge) context.getAccessStateForge();
        CodegenMethod getBeanFirstLastIndex = getBeanFirstLastIndexCodegen(forge, context.getColumn(), context.getClassScope(), stateForge, context.getMethod(), context.getNamedMethods());
        context.getMethod().getBlock().methodReturn(localMethod(getBeanFirstLastIndex));
    }

    private static CodegenMethod getBeanFirstLastIndexCodegen(AggregationAccessorFirstLastIndexWEvalForge forge, int column, CodegenClassScope classScope, AggregationStateLinearForge stateForge, CodegenMethod parent, CodegenNamedMethods namedMethods) {
        CodegenMethod method = parent.makeChild(EventBean.EPTYPE, AggregationAccessorFirstLastIndexWEval.class, classScope);
        if (forge.getConstant() == -1) {
            EPTypeClass evalType = (EPTypeClass) forge.getIndexNode().getEvaluationType();
            method.getBlock().declareVar(evalType, "indexResult", localMethod(CodegenLegoMethodExpression.codegenExpression(forge.getIndexNode(), method, classScope), constantNull(), constantTrue(), constantNull()));
            if (!evalType.getType().isPrimitive()) {
                method.getBlock().ifRefNullReturnNull("indexResult");
            }
            method.getBlock().declareVar(EPTypePremade.INTEGERPRIMITIVE.getEPType(), "index", SimpleNumberCoercerFactory.SimpleNumberCoercerInt.codegenInt(ref("indexResult"), evalType));
        } else {
            method.getBlock().declareVar(EPTypePremade.INTEGERPRIMITIVE.getEPType(), "index", constant(forge.getConstant()));
        }
        CodegenExpression value = forge.isFirst() ? stateForge.getAggregatorLinear().getFirstNthValueCodegen(ref("index"), method, classScope, namedMethods) : stateForge.getAggregatorLinear().getLastNthValueCodegen(ref("index"), method, classScope, namedMethods);
        method.getBlock().methodReturn(value);
        return method;
    }
}