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
package com.espertech.esper.common.internal.epl.enummethod.eval.singlelambdaopt3form.orderby;

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
import com.espertech.esper.common.internal.epl.enummethod.eval.EnumForgeBasePlain;
import com.espertech.esper.common.internal.epl.expression.core.ExprEvaluatorContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionBuilder.*;

public class EnumOrderByScalarNoParams extends EnumForgeBasePlain implements EnumEval {

    private final boolean descending;

    public EnumOrderByScalarNoParams(int streamCountIncoming, boolean descending) {
        super(streamCountIncoming);
        this.descending = descending;
    }

    public EnumEval getEnumEvaluator() {
        return this;
    }

    public Object evaluateEnumMethod(EventBean[] eventsLambda, Collection enumcoll, boolean isNewData, ExprEvaluatorContext context) {

        if (enumcoll == null || enumcoll.isEmpty()) {
            return enumcoll;
        }

        List list = new ArrayList(enumcoll);
        if (descending) {
            Collections.sort(list, Collections.reverseOrder());
        } else {
            Collections.sort(list);
        }
        return list;
    }

    public CodegenExpression codegen(EnumForgeCodegenParams args, CodegenMethodScope codegenMethodScope, CodegenClassScope codegenClassScope) {
        CodegenBlock block = codegenMethodScope.makeChild(EPTypePremade.COLLECTION.getEPType(), EnumOrderByScalarNoParams.class, codegenClassScope).addParam(EnumForgeCodegenNames.PARAMSCOLLOBJ).getBlock()
                .ifCondition(or(equalsNull(EnumForgeCodegenNames.REF_ENUMCOLL), exprDotMethod(EnumForgeCodegenNames.REF_ENUMCOLL, "isEmpty")))
                .blockReturn(EnumForgeCodegenNames.REF_ENUMCOLL)
                .declareVar(EPTypePremade.LIST.getEPType(), "list", newInstance(EPTypePremade.ARRAYLIST.getEPType(), EnumForgeCodegenNames.REF_ENUMCOLL));
        if (descending) {
            block.staticMethod(Collections.class, "sort", ref("list"), staticMethod(Collections.class, "reverseOrder"));
        } else {
            block.staticMethod(Collections.class, "sort", ref("list"));
        }
        CodegenMethod method = block.methodReturn(ref("list"));
        return localMethod(method, args.getExpressions());
    }
}
