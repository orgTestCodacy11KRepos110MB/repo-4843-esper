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
package com.espertech.esper.common.internal.compile.stage2;

import com.espertech.esper.common.internal.collection.Pair;
import com.espertech.esper.common.internal.epl.expression.core.ExprFilterSpecLookupableForge;
import com.espertech.esper.common.internal.epl.expression.core.ExprNode;
import com.espertech.esper.common.internal.filterspec.*;
import com.espertech.esper.common.internal.util.JavaClassHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper to compile (validate and optimize) filter expressions as used in pattern and filter-based streams.
 */
public final class FilterSpecCompilerConsolidateUtil {
    protected static void consolidate(FilterSpecParaForgeMap filterParamExprMap, String statementName) {
        // consolidate or place in a boolean expression (by removing filter spec param from the map)
        // any filter parameter that feature the same property name and filter operator,
        // i.e. we are looking for "a!=5 and a!=6"  to transform to "a not in (5,6)" which can match faster
        // considering that "a not in (5,6) and a not in (7,8)" is "a not in (5, 6, 7, 8)" therefore
        // we need to consolidate until there is no more work to do
        Map<Pair<ExprFilterSpecLookupableForge, FilterOperator>, List<FilterSpecPlanPathTripletForge>> mapOfParams = new HashMap<>();

        boolean haveConsolidated;
        do {
            haveConsolidated = false;
            mapOfParams.clear();

            // sort into buckets of propertyName + filterOperator combination
            for (FilterSpecPlanPathTripletForge currenttriplet : filterParamExprMap.getTriplets()) {
                ExprFilterSpecLookupableForge lookupable = currenttriplet.getParam().getLookupable();
                FilterOperator op = currenttriplet.getParam().getFilterOperator();
                Pair<ExprFilterSpecLookupableForge, FilterOperator> key = new Pair<>(lookupable, op);

                List<FilterSpecPlanPathTripletForge> existingParam = mapOfParams.get(key);
                if (existingParam == null) {
                    existingParam = new ArrayList<>();
                    mapOfParams.put(key, existingParam);
                }
                existingParam.add(currenttriplet);
            }

            for (List<FilterSpecPlanPathTripletForge> entry : mapOfParams.values()) {
                if (entry.size() > 1) {
                    haveConsolidated = true;
                    consolidate(entry, filterParamExprMap, statementName);
                }
            }
        }
        while (haveConsolidated);
    }

    // remove duplicate propertyName + filterOperator items making a judgement to optimize or simply remove the optimized form
    private static void consolidate(List<FilterSpecPlanPathTripletForge> items, FilterSpecParaForgeMap filterParamExprMap, String statementName) {
        boolean eligible = false;
        FilterOperator op = items.get(0).getParam().getFilterOperator();
        if (op == FilterOperator.NOT_EQUAL) {
            eligible = handleConsolidateNotEqual(items, filterParamExprMap, statementName);
        }

        // for all others we simple remove the second optimized form (filter param with same prop name and filter op)
        // and thus the boolean expression that started this is included
        if (!eligible) {
            for (int i = 1; i < items.size(); i++) {
                filterParamExprMap.removeValue(items.get(i));
            }
        }
    }

    // consolidate "val != 3 and val != 4 and val != 5"
    // to "val not in (3, 4, 5)"
    private static boolean handleConsolidateNotEqual(List<FilterSpecPlanPathTripletForge> parameters, FilterSpecParaForgeMap filterParamExprMap, String statementName) {
        // determine eligible
        for (FilterSpecPlanPathTripletForge triplet : parameters) {
            FilterSpecParamForge param = triplet.getParam();
            if (param instanceof FilterSpecParamConstantForge ||
                param instanceof FilterSpecParamEventPropForge ||
                param instanceof FilterSpecParamEventPropIndexedForge) {
                continue;
            }
            return false;
        }

        List<FilterSpecParamInValueForge> values = new ArrayList<>();
        ExprNode lastNotEqualsExprNode = null;

        for (FilterSpecPlanPathTripletForge triplet : parameters) {
            FilterSpecParamForge param = triplet.getParam();
            if (param instanceof FilterSpecParamConstantForge) {
                FilterSpecParamConstantForge constantParam = (FilterSpecParamConstantForge) param;
                Object constant = constantParam.getFilterConstant();
                values.add(new FilterForEvalConstantAnyTypeForge(constant));
            } else if (param instanceof FilterSpecParamEventPropForge) {
                FilterSpecParamEventPropForge eventProp = (FilterSpecParamEventPropForge) param;
                values.add(new FilterForEvalEventPropForge(eventProp.getResultEventAsName(), eventProp.getResultEventProperty(),
                        eventProp.getExprIdentNodeEvaluator(), eventProp.isMustCoerce(), JavaClassHelper.getBoxedType(eventProp.getCoercionType())));
            } else if (param instanceof FilterSpecParamEventPropIndexedForge) {
                FilterSpecParamEventPropIndexedForge eventProp = (FilterSpecParamEventPropIndexedForge) param;
                values.add(new FilterForEvalEventPropIndexedForge(eventProp.getResultEventAsName(), eventProp.getResultEventIndex(), eventProp.getResultEventProperty(),
                        eventProp.getEventType(), eventProp.isMustCoerce(), JavaClassHelper.getBoxedType(eventProp.getCoercionType())));
            } else {
                throw new IllegalStateException("Unknown filter parameter:" + param.toString());
            }

            lastNotEqualsExprNode = filterParamExprMap.removeEntry(triplet);
        }

        FilterSpecParamInForge param = new FilterSpecParamInForge(parameters.get(0).getParam().getLookupable(), FilterOperator.NOT_IN_LIST_OF_VALUES, values);
        FilterSpecPlanPathTripletForge triplet = new FilterSpecPlanPathTripletForge(param, null);
        filterParamExprMap.put(lastNotEqualsExprNode, triplet);

        return true;
    }
}
