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
package com.espertech.esper.common.internal.epl.enummethod.eval.plain.exceptintersectunion;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.common.client.type.EPTypePremade;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenBlock;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenClassScope;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenMethod;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenMethodScope;
import com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpression;
import com.espertech.esper.common.internal.epl.enummethod.codegen.EnumForgeCodegenNames;
import com.espertech.esper.common.internal.epl.enummethod.codegen.EnumForgeCodegenParams;
import com.espertech.esper.common.internal.epl.enummethod.eval.EnumEval;
import com.espertech.esper.common.internal.epl.expression.codegen.ExprForgeCodegenSymbol;
import com.espertech.esper.common.internal.epl.expression.core.ExprEnumerationEval;
import com.espertech.esper.common.internal.epl.expression.core.ExprEvaluatorContext;

import java.util.ArrayList;
import java.util.Collection;

import static com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionBuilder.*;

public class EnumExceptForgeEval implements EnumEval {

    private final EnumExceptForge forge;
    private final ExprEnumerationEval evaluator;

    public EnumExceptForgeEval(EnumExceptForge forge, ExprEnumerationEval evaluator) {
        this.forge = forge;
        this.evaluator = evaluator;
    }

    public Object evaluateEnumMethod(EventBean[] eventsLambda, Collection enumcoll, boolean isNewData, ExprEvaluatorContext context) {
        if (enumcoll == null) {
            return null;
        }

        Collection set;
        if (forge.scalar) {
            set = evaluator.evaluateGetROCollectionScalar(eventsLambda, isNewData, context);
        } else {
            set = evaluator.evaluateGetROCollectionEvents(eventsLambda, isNewData, context);
        }

        return enumExceptForgeEvalSet(set, enumcoll, forge.scalar);
    }

    public static CodegenExpression codegen(EnumExceptForge forge, EnumForgeCodegenParams args, CodegenMethodScope codegenMethodScope, CodegenClassScope codegenClassScope) {
        ExprForgeCodegenSymbol scope = new ExprForgeCodegenSymbol(false, null);
        CodegenMethod methodNode = codegenMethodScope.makeChildWithScope(EPTypePremade.COLLECTION.getEPType(), EnumIntersectForgeEval.class, scope, codegenClassScope).addParam(EnumForgeCodegenNames.PARAMSCOLLOBJ);

        CodegenBlock block = methodNode.getBlock();
        if (forge.scalar) {
            block.declareVar(EPTypePremade.COLLECTION.getEPType(), "other", forge.evaluatorForge.evaluateGetROCollectionScalarCodegen(methodNode, scope, codegenClassScope));
        } else {
            block.declareVar(EPTypePremade.COLLECTION.getEPType(), "other", forge.evaluatorForge.evaluateGetROCollectionEventsCodegen(methodNode, scope, codegenClassScope));
        }
        block.methodReturn(staticMethod(EnumExceptForgeEval.class, "enumExceptForgeEvalSet", ref("other"), EnumForgeCodegenNames.REF_ENUMCOLL, constant(forge.scalar)));
        return localMethod(methodNode, args.getEps(), args.getEnumcoll(), args.getIsNewData(), args.getExprCtx());
    }

    /**
     * NOTE: Code-generation-invoked method, method name and parameter order matters
     *
     * @param other    other
     * @param enumcoll coll
     * @param scalar   indicator
     * @return intersection
     */
    public static Collection enumExceptForgeEvalSet(Collection other, Collection enumcoll, boolean scalar) {
        if (other == null || other.isEmpty() || enumcoll.isEmpty()) {
            return enumcoll;
        }

        if (scalar) {
            ArrayList<Object> result = new ArrayList<Object>(enumcoll);
            result.removeAll(other);
            return result;
        }

        Collection<EventBean> targetEvents = (Collection<EventBean>) enumcoll;
        Collection<EventBean> sourceEvents = (Collection<EventBean>) other;
        ArrayList<EventBean> result = new ArrayList<EventBean>();

        // we compare event underlying
        for (EventBean targetEvent : targetEvents) {
            if (targetEvent == null) {
                result.add(null);
                continue;
            }

            boolean found = false;
            for (EventBean sourceEvent : sourceEvents) {
                if (targetEvent == sourceEvent) {
                    found = true;
                    break;
                }
                if (sourceEvent == null) {
                    continue;
                }
                if (targetEvent.getUnderlying().equals(sourceEvent.getUnderlying())) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                result.add(targetEvent);
            }
        }
        return result;
    }
}
