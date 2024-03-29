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
package com.espertech.esper.common.internal.epl.resultset.grouped;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.common.client.type.EPTypePremade;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenBlock;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenClassScope;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenMethod;
import com.espertech.esper.common.internal.bytecodemodel.core.CodegenInstanceAux;
import com.espertech.esper.common.internal.bytecodemodel.core.CodegenNamedParam;
import com.espertech.esper.common.internal.collection.MultiKeyArrayOfKeys;
import com.espertech.esper.common.internal.compile.multikey.MultiKeyClassRef;
import com.espertech.esper.common.internal.compile.multikey.MultiKeyCodegen;
import com.espertech.esper.common.internal.epl.agg.core.AggregationService;
import com.espertech.esper.common.internal.epl.expression.codegen.CodegenLegoMethodExpression;
import com.espertech.esper.common.internal.epl.expression.core.ExprEvaluatorContext;
import com.espertech.esper.common.internal.epl.expression.core.ExprNode;
import com.espertech.esper.common.internal.epl.expression.core.ExprNodeUtilityPrint;
import com.espertech.esper.common.internal.epl.resultset.core.ResultSetProcessorUtil;
import com.espertech.esper.common.internal.epl.resultset.rowperevent.ResultSetProcessorRowPerEventImpl;
import com.espertech.esper.common.internal.epl.resultset.rowpergroup.ResultSetProcessorRowPerGroup;

import java.util.Set;
import java.util.function.Consumer;

import static com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionBuilder.*;
import static com.espertech.esper.common.internal.epl.expression.codegen.ExprForgeCodegenNames.*;

import static com.espertech.esper.common.internal.epl.resultset.codegen.ResultSetProcessorCodegenNames.MEMBER_EXPREVALCONTEXT;
import static com.espertech.esper.common.internal.epl.util.EPTypeCollectionConst.EPTYPE_SET_MULTIKEYARRAYOFKEYS_EVENTBEAN;
import static com.espertech.esper.common.internal.metrics.instrumentation.InstrumentationCode.instblock;

public class ResultSetProcessorGroupedUtil {
    public final static String METHOD_APPLYAGGVIEWRESULTKEYEDVIEW = "applyAggViewResultKeyedView";
    public final static String METHOD_APPLYAGGJOINRESULTKEYEDJOIN = "applyAggJoinResultKeyedJoin";

    /**
     * NOTE: Code-generation-invoked method, method name and parameter order matters
     *
     * @param aggregationService   aggs
     * @param exprEvaluatorContext ctx
     * @param newData              new data
     * @param newDataMultiKey      new data keys
     * @param oldData              old data
     * @param oldDataMultiKey      old data keys
     * @param eventsPerStream      event buffer, transient buffer
     */
    public static void applyAggViewResultKeyedView(AggregationService aggregationService, ExprEvaluatorContext exprEvaluatorContext, EventBean[] newData, Object[] newDataMultiKey, EventBean[] oldData, Object[] oldDataMultiKey, EventBean[] eventsPerStream) {
        // update aggregates
        if (newData != null) {
            // apply new data to aggregates
            for (int i = 0; i < newData.length; i++) {
                eventsPerStream[0] = newData[i];
                aggregationService.applyEnter(eventsPerStream, newDataMultiKey[i], exprEvaluatorContext);
            }
        }
        if (oldData != null) {
            // apply old data to aggregates
            for (int i = 0; i < oldData.length; i++) {
                eventsPerStream[0] = oldData[i];
                aggregationService.applyLeave(eventsPerStream, oldDataMultiKey[i], exprEvaluatorContext);
            }
        }
    }

    /**
     * NOTE: Code-generation-invoked method, method name and parameter order matters
     *
     * @param aggregationService   aggs
     * @param exprEvaluatorContext ctx
     * @param newEvents            new data
     * @param newDataMultiKey      new data keys
     * @param oldEvents            old data
     * @param oldDataMultiKey      old data keys
     */
    public static void applyAggJoinResultKeyedJoin(AggregationService aggregationService, ExprEvaluatorContext exprEvaluatorContext, Set<MultiKeyArrayOfKeys<EventBean>> newEvents, Object[] newDataMultiKey, Set<MultiKeyArrayOfKeys<EventBean>> oldEvents, Object[] oldDataMultiKey) {
        // update aggregates
        if (!newEvents.isEmpty()) {
            // apply old data to aggregates
            int count = 0;
            for (MultiKeyArrayOfKeys<EventBean> eventsPerStream : newEvents) {
                aggregationService.applyEnter(eventsPerStream.getArray(), newDataMultiKey[count], exprEvaluatorContext);
                count++;
            }
        }
        if (oldEvents != null && !oldEvents.isEmpty()) {
            // apply old data to aggregates
            int count = 0;
            for (MultiKeyArrayOfKeys<EventBean> eventsPerStream : oldEvents) {
                aggregationService.applyLeave(eventsPerStream.getArray(), oldDataMultiKey[count], exprEvaluatorContext);
                count++;
            }
        }
    }

    public static CodegenMethod generateGroupKeySingleCodegen(ExprNode[] groupKeyExpressions, MultiKeyClassRef optionalMultiKeyClasses, CodegenClassScope classScope, CodegenInstanceAux instance) {
        Consumer<CodegenMethod> code = methodNode -> {
            String[] expressions = null;
            if (classScope.isInstrumented()) {
                expressions = ExprNodeUtilityPrint.toExpressionStringsMinPrecedence(groupKeyExpressions);
            }
            methodNode.getBlock().apply(instblock(classScope, "qResultSetProcessComputeGroupKeys", REF_ISNEWDATA, constant(expressions), REF_EPS));

            if (optionalMultiKeyClasses != null && optionalMultiKeyClasses.getClassNameMK() != null) {
                CodegenMethod method = MultiKeyCodegen.codegenMethod(groupKeyExpressions, optionalMultiKeyClasses, methodNode, classScope);
                methodNode.getBlock()
                    .declareVar(EPTypePremade.OBJECT.getEPType(), "key", localMethod(method, REF_EPS, REF_ISNEWDATA, MEMBER_EXPREVALCONTEXT))
                    .apply(instblock(classScope, "aResultSetProcessComputeGroupKeys", REF_ISNEWDATA, ref("key")))
                    .methodReturn(ref("key"));
                return;
            }

            if (groupKeyExpressions.length > 1) {
                throw new IllegalStateException("Multiple group-by expression and no multikey");
            }

            CodegenMethod expression = CodegenLegoMethodExpression.codegenExpression(groupKeyExpressions[0].getForge(), methodNode, classScope);
            methodNode.getBlock()
                    .declareVar(EPTypePremade.OBJECT.getEPType(), "key", localMethod(expression, REF_EPS, REF_ISNEWDATA, MEMBER_EXPREVALCONTEXT))
                    .apply(instblock(classScope, "aResultSetProcessComputeGroupKeys", REF_ISNEWDATA, ref("key")))
                    .methodReturn(ref("key"));
        };

        return instance.getMethods().addMethod(EPTypePremade.OBJECT.getEPType(), "generateGroupKeySingle", CodegenNamedParam.from(EventBean.EPTYPEARRAY, NAME_EPS, EPTypePremade.BOOLEANPRIMITIVE.getEPType(), NAME_ISNEWDATA), ResultSetProcessorUtil.class, classScope, code);
    }

    public static CodegenMethod generateGroupKeyArrayViewCodegen(CodegenMethod generateGroupKeySingle, CodegenClassScope classScope, CodegenInstanceAux instance) {
        Consumer<CodegenMethod> code = method -> {
            method.getBlock().ifRefNullReturnNull("events")
                    .declareVar(EventBean.EPTYPEARRAY, "eventsPerStream", newArrayByLength(EventBean.EPTYPE, constant(1)))
                    .declareVar(EPTypePremade.OBJECTARRAY.getEPType(), "keys", newArrayByLength(EPTypePremade.OBJECT.getEPType(), arrayLength(ref("events"))));
            {
                CodegenBlock forLoop = method.getBlock().forLoopIntSimple("i", arrayLength(ref("events")));
                forLoop.assignArrayElement("eventsPerStream", constant(0), arrayAtIndex(ref("events"), ref("i")))
                        .assignArrayElement("keys", ref("i"), localMethod(generateGroupKeySingle, ref("eventsPerStream"), REF_ISNEWDATA));
            }
            method.getBlock().methodReturn(ref("keys"));
        };
        return instance.getMethods().addMethod(EPTypePremade.OBJECTARRAY.getEPType(), "generateGroupKeyArrayView", CodegenNamedParam.from(EventBean.EPTYPEARRAY, "events", EPTypePremade.BOOLEANPRIMITIVE.getEPType(), NAME_ISNEWDATA), ResultSetProcessorRowPerGroup.class, classScope, code);
    }

    public static CodegenMethod generateGroupKeyArrayJoinCodegen(CodegenMethod generateGroupKeySingle, CodegenClassScope classScope, CodegenInstanceAux instance) {
        Consumer<CodegenMethod> code = method -> {
            method.getBlock().ifCondition(exprDotMethod(ref("resultSet"), "isEmpty")).blockReturn(constantNull())
                    .declareVar(EPTypePremade.OBJECTARRAY.getEPType(), "keys", newArrayByLength(EPTypePremade.OBJECT.getEPType(), exprDotMethod(ref("resultSet"), "size")))
                    .declareVar(EPTypePremade.INTEGERPRIMITIVE.getEPType(), "count", constant(0))
                    .forEach(MultiKeyArrayOfKeys.EPTYPE, "eventsPerStream", ref("resultSet"))
                    .assignArrayElement("keys", ref("count"), localMethod(generateGroupKeySingle, cast(EventBean.EPTYPEARRAY, exprDotMethod(ref("eventsPerStream"), "getArray")), REF_ISNEWDATA))
                    .incrementRef("count")
                    .blockEnd()
                    .methodReturn(ref("keys"));
        };
        return instance.getMethods().addMethod(EPTypePremade.OBJECTARRAY.getEPType(), "generateGroupKeyArrayJoin", CodegenNamedParam.from(EPTYPE_SET_MULTIKEYARRAYOFKEYS_EVENTBEAN, "resultSet", EPTypePremade.BOOLEANPRIMITIVE.getEPType(), "isNewData"), ResultSetProcessorRowPerEventImpl.class, classScope, code);
    }
}
