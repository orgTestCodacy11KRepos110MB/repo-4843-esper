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
package com.espertech.esper.common.internal.epl.enummethod.eval.plain.reverse;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.common.client.type.EPTypePremade;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenClassScope;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenMethod;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenMethodScope;
import com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpression;
import com.espertech.esper.common.internal.epl.enummethod.codegen.EnumForgeCodegenNames;
import com.espertech.esper.common.internal.epl.enummethod.codegen.EnumForgeCodegenParams;
import com.espertech.esper.common.internal.epl.enummethod.eval.EnumEval;
import com.espertech.esper.common.internal.epl.enummethod.eval.EnumForge;
import com.espertech.esper.common.internal.epl.expression.core.ExprEvaluatorContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionBuilder.*;

public class EnumReverseForge implements EnumEval, EnumForge {

    private int numStreams;

    public EnumReverseForge(int numStreams) {
        this.numStreams = numStreams;
    }

    public EnumEval getEnumEvaluator() {
        return this;
    }

    public int getStreamNumSize() {
        return numStreams;
    }

    public Object evaluateEnumMethod(EventBean[] eventsLambda, Collection enumcoll, boolean isNewData, ExprEvaluatorContext context) {
        if (enumcoll.isEmpty()) {
            return enumcoll;
        }

        ArrayList<Object> result = new ArrayList<Object>(enumcoll);
        Collections.reverse(result);
        return result;
    }

    public CodegenExpression codegen(EnumForgeCodegenParams args, CodegenMethodScope codegenMethodScope, CodegenClassScope codegenClassScope) {
        CodegenMethod method = codegenMethodScope.makeChild(EPTypePremade.COLLECTION.getEPType(), EnumReverseForge.class, codegenClassScope).addParam(EnumForgeCodegenNames.PARAMSCOLLOBJ).getBlock()
                .ifCondition(exprDotMethod(EnumForgeCodegenNames.REF_ENUMCOLL, "isEmpty"))
                .blockReturn(EnumForgeCodegenNames.REF_ENUMCOLL)
                .declareVar(EPTypePremade.ARRAYLIST.getEPType(), "result", newInstance(EPTypePremade.ARRAYLIST.getEPType(), EnumForgeCodegenNames.REF_ENUMCOLL))
                .staticMethod(Collections.class, "reverse", ref("result"))
                .methodReturn(ref("result"));
        return localMethod(method, args.getExpressions());
    }
}
