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
package com.espertech.esper.common.internal.compile.stage1.specmapper;

import com.espertech.esper.common.client.EPException;
import com.espertech.esper.common.client.soda.*;
import com.espertech.esper.common.client.type.EPTypeNull;
import com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionUtil;
import com.espertech.esper.common.internal.collection.Pair;
import com.espertech.esper.common.internal.compile.stage1.spec.CreateTableColumn;
import com.espertech.esper.common.internal.compile.stage1.spec.*;
import com.espertech.esper.common.internal.compile.stage2.StatementSpecCompileException;
import com.espertech.esper.common.internal.context.compile.ContextCompileTimeDescriptor;
import com.espertech.esper.common.internal.context.compile.ContextMetaData;
import com.espertech.esper.common.internal.context.util.ContextPropertyRegistry;
import com.espertech.esper.common.internal.epl.agg.access.linear.AggregationAccessorLinearType;
import com.espertech.esper.common.internal.epl.enummethod.dot.ExprLambdaGoesNode;
import com.espertech.esper.common.internal.epl.expression.agg.accessagg.ExprAggMultiFunctionCountMinSketchNode;
import com.espertech.esper.common.internal.epl.expression.agg.accessagg.ExprAggMultiFunctionLinearAccessNode;
import com.espertech.esper.common.internal.epl.expression.agg.accessagg.ExprAggMultiFunctionSortedMinMaxByNode;
import com.espertech.esper.common.internal.epl.expression.agg.accessagg.ExprPlugInMultiFunctionAggNode;
import com.espertech.esper.common.internal.epl.expression.agg.method.*;
import com.espertech.esper.common.internal.epl.expression.chain.Chainable;
import com.espertech.esper.common.internal.epl.expression.chain.ChainableArray;
import com.espertech.esper.common.internal.epl.expression.chain.ChainableCall;
import com.espertech.esper.common.internal.epl.expression.chain.ChainableName;
import com.espertech.esper.common.internal.epl.expression.core.*;
import com.espertech.esper.common.internal.epl.expression.declared.compiletime.ExprDeclaredNode;
import com.espertech.esper.common.internal.epl.expression.dot.core.ExprDotNode;
import com.espertech.esper.common.internal.epl.expression.dot.core.ExprDotNodeImpl;
import com.espertech.esper.common.internal.epl.expression.dot.walk.ChainableWalkHelper;
import com.espertech.esper.common.internal.epl.expression.funcs.*;
import com.espertech.esper.common.internal.epl.expression.ops.*;
import com.espertech.esper.common.internal.epl.expression.prev.ExprPreviousNode;
import com.espertech.esper.common.internal.epl.expression.prev.ExprPreviousNodePreviousType;
import com.espertech.esper.common.internal.epl.expression.prior.ExprPriorNode;
import com.espertech.esper.common.internal.epl.expression.subquery.ExprSubselectAllSomeAnyNode;
import com.espertech.esper.common.internal.epl.expression.subquery.ExprSubselectExistsNode;
import com.espertech.esper.common.internal.epl.expression.subquery.ExprSubselectInNode;
import com.espertech.esper.common.internal.epl.expression.subquery.ExprSubselectRowNode;
import com.espertech.esper.common.internal.epl.expression.table.ExprTableAccessNode;
import com.espertech.esper.common.internal.epl.expression.table.ExprTableAccessNodeKeys;
import com.espertech.esper.common.internal.epl.expression.table.ExprTableAccessNodeSubprop;
import com.espertech.esper.common.internal.epl.expression.table.ExprTableAccessNodeTopLevel;
import com.espertech.esper.common.internal.epl.expression.time.node.ExprTimePeriod;
import com.espertech.esper.common.internal.epl.expression.time.node.ExprTimePeriodImpl;
import com.espertech.esper.common.internal.epl.expression.time.node.ExprTimestampNode;
import com.espertech.esper.common.internal.epl.expression.variable.ExprVariableNode;
import com.espertech.esper.common.internal.epl.expression.variable.ExprVariableNodeImpl;
import com.espertech.esper.common.internal.epl.pattern.and.EvalAndForgeNode;
import com.espertech.esper.common.internal.epl.pattern.core.EvalForgeNode;
import com.espertech.esper.common.internal.epl.pattern.every.EvalEveryForgeNode;
import com.espertech.esper.common.internal.epl.pattern.everydistinct.EvalEveryDistinctForgeNode;
import com.espertech.esper.common.internal.epl.pattern.filter.EvalFilterForgeNode;
import com.espertech.esper.common.internal.epl.pattern.followedby.EvalFollowedByForgeNode;
import com.espertech.esper.common.internal.epl.pattern.guard.EvalGuardForgeNode;
import com.espertech.esper.common.internal.epl.pattern.matchuntil.EvalMatchUntilForgeNode;
import com.espertech.esper.common.internal.epl.pattern.not.EvalNotForgeNode;
import com.espertech.esper.common.internal.epl.pattern.observer.EvalObserverForgeNode;
import com.espertech.esper.common.internal.epl.pattern.or.EvalOrForgeNode;
import com.espertech.esper.common.internal.epl.rowrecog.core.RowRecogNFATypeEnum;
import com.espertech.esper.common.internal.epl.rowrecog.expr.*;
import com.espertech.esper.common.internal.epl.script.core.ExprNodeScript;
import com.espertech.esper.common.internal.epl.table.compiletime.TableCompileTimeUtil;
import com.espertech.esper.common.internal.epl.table.compiletime.TableMetaData;
import com.espertech.esper.common.internal.epl.variable.compiletime.VariableMetaData;
import com.espertech.esper.common.internal.epl.variable.core.VariableUtil;
import com.espertech.esper.common.internal.settings.ClasspathImportSingleRowDesc;
import com.espertech.esper.common.internal.type.ClassDescriptor;
import com.espertech.esper.common.internal.type.CronOperatorEnum;
import com.espertech.esper.common.internal.type.MathArithTypeEnum;
import com.espertech.esper.common.internal.type.RelationalOpEnum;
import com.espertech.esper.common.internal.util.*;

import java.util.*;

import static com.espertech.esper.common.internal.epl.historical.database.core.HistoricalEventViewableDatabaseForgeFactory.SAMPLE_WHERECLAUSE_PLACEHOLDER;

/**
 * Helper for mapping internal representations of a statement to the SODA object model for statements.
 */
public class StatementSpecMapper {

    /**
     * Maps the SODA-selector to the internal representation
     *
     * @param selector is the SODA-selector to map
     * @return internal stream selector
     */
    public static SelectClauseStreamSelectorEnum mapFromSODA(StreamSelector selector) {
        if (selector == StreamSelector.ISTREAM_ONLY) {
            return SelectClauseStreamSelectorEnum.ISTREAM_ONLY;
        } else if (selector == StreamSelector.RSTREAM_ONLY) {
            return SelectClauseStreamSelectorEnum.RSTREAM_ONLY;
        } else if (selector == StreamSelector.RSTREAM_ISTREAM_BOTH) {
            return SelectClauseStreamSelectorEnum.RSTREAM_ISTREAM_BOTH;
        } else {
            throw new IllegalArgumentException("Invalid selector '" + selector + "' encountered");
        }
    }

    /**
     * Maps the internal stream selector to the SODA-representation
     *
     * @param selector is the internal selector to map
     * @return SODA stream selector
     */
    public static StreamSelector mapFromSODA(SelectClauseStreamSelectorEnum selector) {
        if (selector == SelectClauseStreamSelectorEnum.ISTREAM_ONLY) {
            return StreamSelector.ISTREAM_ONLY;
        } else if (selector == SelectClauseStreamSelectorEnum.RSTREAM_ONLY) {
            return StreamSelector.RSTREAM_ONLY;
        } else if (selector == SelectClauseStreamSelectorEnum.RSTREAM_ISTREAM_BOTH) {
            return StreamSelector.RSTREAM_ISTREAM_BOTH;
        } else {
            throw new IllegalArgumentException("Invalid selector '" + selector + "' encountered");
        }
    }

    /**
     * Unmap expresission.
     *
     * @param expression to unmap
     * @return expression
     */
    public static Expression unmap(ExprNode expression) {
        return unmapExpressionDeep(expression, new StatementSpecUnMapContext());
    }

    public static StatementSpecRaw map(EPStatementObjectModel sodaStatement, StatementSpecMapEnv mapEnv) {
        ContextCompileTimeDescriptor contextCompileTimeDescriptor = null;
        if (sodaStatement.getContextName() != null) {
            ContextMetaData contextDetail = mapEnv.getContextCompileTimeResolver().getContextInfo(sodaStatement.getContextName());
            if (contextDetail != null) {
                contextCompileTimeDescriptor = new ContextCompileTimeDescriptor(sodaStatement.getContextName(), contextDetail.getContextModuleName(), contextDetail.getContextVisibility(), new ContextPropertyRegistry(contextDetail), contextDetail.getValidationInfos());
            }
        }

        StatementSpecMapContext mapContext = new StatementSpecMapContext(contextCompileTimeDescriptor, mapEnv);

        StatementSpecRaw raw = map(sodaStatement, mapContext);
        raw.setHasPriorExpressions(mapContext.isHasPriorExpression());
        raw.setReferencedVariables(mapContext.getVariableNames());
        raw.setTableExpressions(mapContext.getTableExpressions());
        raw.setSubstitutionParameters(mapContext.getSubstitutionNodes());
        return raw;
    }

    private static StatementSpecRaw map(EPStatementObjectModel sodaStatement, StatementSpecMapContext mapContext) {
        SelectClauseStreamSelectorEnum defaultStreamSelector = StatementSpecMapper.mapFromSODA(mapContext.getConfiguration().getCompiler().getStreamSelection().getDefaultStreamSelector());
        StatementSpecRaw raw = new StatementSpecRaw(defaultStreamSelector);

        List<AnnotationDesc> annotations = mapAnnotations(sodaStatement.getAnnotations());
        raw.setAnnotations(annotations);
        mapFireAndForget(sodaStatement.getFireAndForgetClause(), raw, mapContext);
        mapExpressionDeclaration(sodaStatement.getExpressionDeclarations(), raw, mapContext);
        mapScriptExpressions(sodaStatement.getScriptExpressions(), raw, mapContext);
        mapClassProvidedExpressions(sodaStatement.getClassProvidedExpressions(), raw, mapContext);
        mapContextName(sodaStatement.getContextName(), raw, mapContext);
        mapUpdateClause(sodaStatement.getUpdateClause(), raw, mapContext);
        mapCreateContext(sodaStatement.getCreateContext(), raw, mapContext);
        mapCreateWindow(sodaStatement.getCreateWindow(), raw, mapContext);
        mapCreateIndex(sodaStatement.getCreateIndex(), raw, mapContext);
        mapCreateVariable(sodaStatement.getCreateVariable(), raw, mapContext);
        mapCreateTable(sodaStatement.getCreateTable(), raw, mapContext);
        mapCreateSchema(sodaStatement.getCreateSchema(), raw, mapContext);
        mapCreateExpression(sodaStatement.getCreateExpression(), raw, mapContext);
        mapCreateClass(sodaStatement.getCreateClass(), raw, mapContext);
        mapCreateGraph(sodaStatement.getCreateDataFlow(), raw, mapContext);
        mapOnTrigger(sodaStatement.getOnExpr(), raw, mapContext);
        InsertIntoDesc desc = mapInsertInto(sodaStatement.getInsertInto(), mapContext);
        raw.setInsertIntoDesc(desc);
        mapSelect(sodaStatement.getSelectClause(), raw, mapContext);
        mapFrom(sodaStatement.getFromClause(), raw, mapContext);
        mapWhere(sodaStatement.getWhereClause(), raw, mapContext);
        mapGroupBy(sodaStatement.getGroupByClause(), raw, mapContext);
        mapHaving(sodaStatement.getHavingClause(), raw, mapContext);
        mapOutputLimit(sodaStatement.getOutputLimitClause(), raw, mapContext);
        mapOrderBy(sodaStatement.getOrderByClause(), raw, mapContext);
        mapRowLimit(sodaStatement.getRowLimitClause(), raw, mapContext);
        mapMatchRecognize(sodaStatement.getMatchRecognizeClause(), raw, mapContext);
        mapForClause(sodaStatement.getForClause(), raw, mapContext);
        mapSQLParameters(sodaStatement.getFromClause(), raw, mapContext);
        mapIntoVariableClause(sodaStatement.getIntoTableClause(), raw);
        return raw;
    }

    private static void mapIntoVariableClause(IntoTableClause intoClause, StatementSpecRaw raw) {
        if (intoClause != null) {
            raw.setIntoTableSpec(new IntoTableSpec(intoClause.getTableName()));
        }
    }

    private static void mapFireAndForget(FireAndForgetClause fireAndForgetClause, StatementSpecRaw raw, StatementSpecMapContext mapContext) {
        if (fireAndForgetClause instanceof FireAndForgetDelete) {
            raw.setFireAndForgetSpec(new FireAndForgetSpecDelete());
        } else if (fireAndForgetClause instanceof FireAndForgetInsert) {
            FireAndForgetInsert insert = (FireAndForgetInsert) fireAndForgetClause;
            List<List<ExprNode>> rows = new ArrayList<>(insert.getRows().size());
            for (List<Expression> row : insert.getRows()) {
                List<ExprNode> nodes = new ArrayList<>(row.size());
                for (Expression expr : row) {
                    nodes.add(mapExpressionDeep(expr, mapContext));
                }
                rows.add(nodes);
            }
            raw.setFireAndForgetSpec(new FireAndForgetSpecInsert(insert.isUseValuesKeyword(), rows));
        } else if (fireAndForgetClause instanceof FireAndForgetUpdate) {
            FireAndForgetUpdate upd = (FireAndForgetUpdate) fireAndForgetClause;
            List<OnTriggerSetAssignment> assignments = new ArrayList<>();
            for (Assignment pair : upd.getAssignments()) {
                ExprNode expr = mapExpressionDeep(pair.getValue(), mapContext);
                assignments.add(new OnTriggerSetAssignment(expr));
            }
            FireAndForgetSpecUpdate updspec = new FireAndForgetSpecUpdate(assignments);
            raw.setFireAndForgetSpec(updspec);
        } else {
            if (fireAndForgetClause == null) {
                return;
            }
            throw new IllegalStateException("Unrecognized fire-and-forget clause " + fireAndForgetClause);
        }
    }

    /**
     * Maps the internal representation of a statement to the SODA object model.
     *
     * @param statementSpec is the internal representation
     * @return object model of statement
     */
    public static StatementSpecUnMapResult unmap(StatementSpecRaw statementSpec) {
        StatementSpecUnMapContext unmapContext = new StatementSpecUnMapContext();
        EPStatementObjectModel model = unmapInternal(statementSpec, unmapContext);
        return new StatementSpecUnMapResult(model);
    }

    private static EPStatementObjectModel unmapInternal(StatementSpecRaw statementSpec, StatementSpecUnMapContext unmapContext) {
        EPStatementObjectModel model = new EPStatementObjectModel();
        model.setAnnotations(unmapAnnotations(statementSpec.getAnnotations()));
        unmapFireAndForget(statementSpec.getFireAndForgetSpec(), model, unmapContext);
        model.setExpressionDeclarations(unmapExpressionDeclarations(statementSpec.getExpressionDeclDesc(), unmapContext));
        model.setScriptExpressions(unmapScriptExpressions(statementSpec.getScriptExpressions(), unmapContext));
        model.setClassProvidedExpressions(unmapClassProvideds(statementSpec.getClassProvideds(), unmapContext));
        unmapContextName(statementSpec.getOptionalContextName(), model);
        unmapCreateContext(statementSpec.getCreateContextDesc(), model, unmapContext);
        unmapCreateWindow(statementSpec.getCreateWindowDesc(), model, unmapContext);
        unmapCreateIndex(statementSpec.getCreateIndexDesc(), model, unmapContext);
        unmapCreateVariable(statementSpec.getCreateVariableDesc(), model, unmapContext);
        unmapCreateTable(statementSpec.getCreateTableDesc(), model, unmapContext);
        unmapCreateSchema(statementSpec.getCreateSchemaDesc(), model);
        unmapCreateExpression(statementSpec.getCreateExpressionDesc(), model, unmapContext);
        unmapCreateClass(statementSpec.getCreateClassProvided(), model);
        unmapCreateGraph(statementSpec.getCreateDataFlowDesc(), model, unmapContext);
        unmapUpdateClause(statementSpec.getStreamSpecs(), statementSpec.getUpdateDesc(), model, unmapContext);
        unmapOnClause(statementSpec.getOnTriggerDesc(), model, unmapContext);
        InsertIntoClause insertIntoClause = unmapInsertInto(statementSpec.getInsertIntoDesc(), unmapContext);
        model.setInsertInto(insertIntoClause);
        SelectClause selectClause = unmapSelect(statementSpec.getSelectClauseSpec(), statementSpec.getSelectStreamSelectorEnum(), unmapContext);
        model.setSelectClause(selectClause);
        unmapFrom(statementSpec.getStreamSpecs(), statementSpec.getOuterJoinDescList(), model, unmapContext);
        unmapWhere(statementSpec.getWhereClause(), model, unmapContext);
        unmapGroupBy(statementSpec.getGroupByExpressions(), model, unmapContext);
        unmapHaving(statementSpec.getHavingClause(), model, unmapContext);
        unmapOutputLimit(statementSpec.getOutputLimitSpec(), model, unmapContext);
        unmapOrderBy(statementSpec.getOrderByList(), model, unmapContext);
        unmapRowLimit(statementSpec.getRowLimitSpec(), model);
        unmapMatchRecognize(statementSpec.getMatchRecognizeSpec(), model, unmapContext);
        unmapForClause(statementSpec.getForClauseSpec(), model, unmapContext);
        unmapSQLParameters(statementSpec.getSqlParameters(), unmapContext);
        unmapIntoVariableClause(statementSpec.getIntoTableSpec(), model);
        return model;
    }

    private static void unmapIntoVariableClause(IntoTableSpec intoTableSpec, EPStatementObjectModel model) {
        if (intoTableSpec == null) {
            return;
        }
        model.setIntoTableClause(new IntoTableClause(intoTableSpec.getName()));
    }

    private static void unmapCreateTable(CreateTableDesc desc, EPStatementObjectModel model, StatementSpecUnMapContext unmapContext) {
        if (desc == null) {
            return;
        }
        CreateTableClause clause = new CreateTableClause(desc.getTableName());
        List<com.espertech.esper.common.client.soda.CreateTableColumn> cols = new ArrayList<>();
        for (CreateTableColumn col : desc.getColumns()) {
            Expression optExpr = col.getOptExpression() != null ? unmapExpressionDeep(col.getOptExpression(), unmapContext) : null;
            List<AnnotationPart> annots = unmapAnnotations(col.getAnnotations());
            String optType = col.getOptType() == null ? null : col.getOptType().toEPL();
            com.espertech.esper.common.client.soda.CreateTableColumn coldesc = new com.espertech.esper.common.client.soda.CreateTableColumn(col.getColumnName(), optExpr, optType, annots, col.getPrimaryKey());
            cols.add(coldesc);
        }
        clause.setColumns(cols);
        model.setCreateTable(clause);
    }

    private static void unmapFireAndForget(FireAndForgetSpec fireAndForgetSpec, EPStatementObjectModel model, StatementSpecUnMapContext unmapContext) {
        if (fireAndForgetSpec instanceof FireAndForgetSpecDelete) {
            model.setFireAndForgetClause(new FireAndForgetDelete());
        } else if (fireAndForgetSpec instanceof FireAndForgetSpecInsert) {
            FireAndForgetSpecInsert insert = (FireAndForgetSpecInsert) fireAndForgetSpec;
            List<List<Expression>> rows = new ArrayList<>(insert.getMultirow().size());
            for (List<ExprNode> exprRow : insert.getMultirow()) {
                List<Expression> row = new ArrayList<>(exprRow.size());
                for (ExprNode expr : exprRow) {
                    row.add(unmapExpressionDeep(expr, unmapContext));
                }
                rows.add(row);
            }
            FireAndForgetInsert spec = new FireAndForgetInsert(insert.isUseValuesKeyword());
            spec.setRows(rows);
            model.setFireAndForgetClause(spec);
        } else if (fireAndForgetSpec instanceof FireAndForgetSpecUpdate) {
            FireAndForgetSpecUpdate upd = (FireAndForgetSpecUpdate) fireAndForgetSpec;
            FireAndForgetUpdate faf = new FireAndForgetUpdate();
            for (OnTriggerSetAssignment assignment : upd.getAssignments()) {
                Expression expr = unmapExpressionDeep(assignment.getExpression(), unmapContext);
                faf.addAssignment(new Assignment(expr));
            }
            model.setFireAndForgetClause(faf);
        } else {
            if (fireAndForgetSpec != null) {
                throw new IllegalStateException("Unrecognized type of fire-and-forget: " + fireAndForgetSpec);
            }
        }
    }

    // Collect substitution parameters
    private static void unmapSQLParameters(Map<Integer, List<ExprNode>> sqlParameters, StatementSpecUnMapContext unmapContext) {
        if (sqlParameters == null) {
            return;
        }
        for (Map.Entry<Integer, List<ExprNode>> pair : sqlParameters.entrySet()) {
            for (ExprNode node : pair.getValue()) {
                unmapExpressionDeep(node, unmapContext);
            }
        }
    }

    private static void unmapOnClause(OnTriggerDesc onTriggerDesc, EPStatementObjectModel model, StatementSpecUnMapContext unmapContext) {
        if (onTriggerDesc == null) {
            return;
        }
        if (onTriggerDesc.getOnTriggerType() == OnTriggerType.ON_DELETE) {
            OnTriggerWindowDesc window = (OnTriggerWindowDesc) onTriggerDesc;
            model.setOnExpr(new OnDeleteClause(window.getWindowName(), window.getOptionalAsName()));
        } else if (onTriggerDesc.getOnTriggerType() == OnTriggerType.ON_UPDATE) {
            OnTriggerWindowUpdateDesc window = (OnTriggerWindowUpdateDesc) onTriggerDesc;
            OnUpdateClause clause = new OnUpdateClause(window.getWindowName(), window.getOptionalAsName());
            for (OnTriggerSetAssignment assignment : window.getAssignments()) {
                Expression expr = unmapExpressionDeep(assignment.getExpression(), unmapContext);
                clause.addAssignment(expr);
            }
            model.setOnExpr(clause);
        } else if (onTriggerDesc.getOnTriggerType() == OnTriggerType.ON_SELECT) {
            OnTriggerWindowDesc window = (OnTriggerWindowDesc) onTriggerDesc;
            OnSelectClause onSelect = new OnSelectClause(window.getWindowName(), window.getOptionalAsName());
            onSelect.setDeleteAndSelect(window.isDeleteAndSelect());
            model.setOnExpr(onSelect);
        } else if (onTriggerDesc.getOnTriggerType() == OnTriggerType.ON_SET) {
            OnTriggerSetDesc trigger = (OnTriggerSetDesc) onTriggerDesc;
            OnSetClause clause = new OnSetClause();
            for (OnTriggerSetAssignment assignment : trigger.getAssignments()) {
                Expression expr = unmapExpressionDeep(assignment.getExpression(), unmapContext);
                clause.addAssignment(expr);
            }
            model.setOnExpr(clause);
        } else if (onTriggerDesc.getOnTriggerType() == OnTriggerType.ON_SPLITSTREAM) {
            OnTriggerSplitStreamDesc trigger = (OnTriggerSplitStreamDesc) onTriggerDesc;
            OnInsertSplitStreamClause clause = OnInsertSplitStreamClause.create();
            for (OnTriggerSplitStream stream : trigger.getSplitStreams()) {
                Expression whereClause = null;
                if (stream.getWhereClause() != null) {
                    whereClause = unmapExpressionDeep(stream.getWhereClause(), unmapContext);
                }
                List<ContainedEventSelect> propertySelects = null;
                String propertySelectStreamName = null;
                if (stream.getFromClause() != null) {
                    propertySelects = unmapPropertySelects(stream.getFromClause().getPropertyEvalSpec(), unmapContext);
                    propertySelectStreamName = stream.getFromClause().getOptionalStreamName();
                }
                InsertIntoClause insertIntoClause = unmapInsertInto(stream.getInsertInto(), unmapContext);
                SelectClause selectClause = unmapSelect(stream.getSelectClause(), SelectClauseStreamSelectorEnum.ISTREAM_ONLY, unmapContext);
                clause.addItem(OnInsertSplitStreamItem.create(insertIntoClause, selectClause, propertySelects, propertySelectStreamName, whereClause));
            }
            model.setOnExpr(clause);
            clause.setFirst(trigger.isFirst());
        } else if (onTriggerDesc.getOnTriggerType() == OnTriggerType.ON_MERGE) {
            OnTriggerMergeDesc trigger = (OnTriggerMergeDesc) onTriggerDesc;
            List<OnMergeMatchItem> matchItems = new ArrayList<>();
            for (OnTriggerMergeMatched matched : trigger.getItems()) {

                List<OnMergeMatchedAction> actions = new ArrayList<>();
                Expression matchCond = matched.getOptionalMatchCond() != null ? unmapExpressionDeep(matched.getOptionalMatchCond(), unmapContext) : null;
                OnMergeMatchItem matchItem = new OnMergeMatchItem(matched.isMatchedUnmatched(), matchCond, actions);
                for (OnTriggerMergeAction actionitem : matched.getActions()) {
                    OnMergeMatchedAction action;
                    if (actionitem instanceof OnTriggerMergeActionDelete) {
                        OnTriggerMergeActionDelete delete = (OnTriggerMergeActionDelete) actionitem;
                        Expression optionalCondition = delete.getOptionalWhereClause() == null ? null : unmapExpressionDeep(delete.getOptionalWhereClause(), unmapContext);
                        action = new OnMergeMatchedDeleteAction(optionalCondition);
                    } else if (actionitem instanceof OnTriggerMergeActionUpdate) {
                        OnTriggerMergeActionUpdate merge = (OnTriggerMergeActionUpdate) actionitem;
                        List<Assignment> assignments = new ArrayList<>();
                        for (OnTriggerSetAssignment pair : merge.getAssignments()) {
                            Expression expr = unmapExpressionDeep(pair.getExpression(), unmapContext);
                            assignments.add(new Assignment(expr));
                        }
                        Expression optionalCondition = merge.getOptionalWhereClause() == null ? null : unmapExpressionDeep(merge.getOptionalWhereClause(), unmapContext);
                        action = new OnMergeMatchedUpdateAction(assignments, optionalCondition);
                    } else if (actionitem instanceof OnTriggerMergeActionInsert) {
                        action = unmapMergeInsert((OnTriggerMergeActionInsert) actionitem, unmapContext);
                    } else {
                        throw new IllegalArgumentException("Unrecognized merged action type '" + actionitem.getClass() + "'");
                    }
                    actions.add(action);
                }
                matchItems.add(matchItem);
            }
            OnMergeClause onMerge = OnMergeClause.create(trigger.getWindowName(), trigger.getOptionalAsName(), matchItems);
            if (trigger.getOptionalInsertNoMatch() != null) {
                onMerge.setInsertNoMatch(unmapMergeInsert(trigger.getOptionalInsertNoMatch(), unmapContext));
            }
            model.setOnExpr(onMerge);
        } else {
            throw new IllegalArgumentException("Type of on-clause not handled: " + onTriggerDesc.getOnTriggerType());
        }
    }

    private static OnMergeMatchedInsertAction unmapMergeInsert(OnTriggerMergeActionInsert insert, StatementSpecUnMapContext unmapContext) {
        List<String> columnNames = new ArrayList<>(insert.getColumns());
        List<SelectClauseElement> select = unmapSelectClauseElements(insert.getSelectClause(), unmapContext);
        Expression optionalCondition = insert.getOptionalWhereClause() == null ? null : unmapExpressionDeep(insert.getOptionalWhereClause(), unmapContext);
        Expression optionalPrecedence = insert.getEventPrecedence() == null ? null : unmapExpressionDeep(insert.getEventPrecedence(), unmapContext);
        return new OnMergeMatchedInsertAction(columnNames, optionalPrecedence, select, optionalCondition, insert.getOptionalStreamName());
    }

    private static void unmapUpdateClause(List<StreamSpecRaw> desc, UpdateDesc updateDesc, EPStatementObjectModel model, StatementSpecUnMapContext unmapContext) {
        if (updateDesc == null) {
            return;
        }
        String type = ((FilterStreamSpecRaw) desc.get(0)).getRawFilterSpec().getEventTypeName();
        UpdateClause clause = new UpdateClause(type, updateDesc.getOptionalStreamName());
        for (OnTriggerSetAssignment assignment : updateDesc.getAssignments()) {
            Expression expr = unmapExpressionDeep(assignment.getExpression(), unmapContext);
            clause.addAssignment(expr);
        }
        model.setUpdateClause(clause);

        if (updateDesc.getOptionalWhereClause() != null) {
            Expression expr = unmapExpressionDeep(updateDesc.getOptionalWhereClause(), unmapContext);
            model.getUpdateClause().setOptionalWhereClause(expr);
        }
    }

    private static void unmapContextName(String contextName, EPStatementObjectModel model) {
        model.setContextName(contextName);
    }

    private static void unmapCreateContext(CreateContextDesc createContextDesc, EPStatementObjectModel model, StatementSpecUnMapContext unmapContext) {
        if (createContextDesc == null) {
            return;
        }

        ContextDescriptor desc = unmapCreateContextDetail(createContextDesc.getContextDetail(), unmapContext);
        CreateContextClause clause = new CreateContextClause(createContextDesc.getContextName(), desc);
        model.setCreateContext(clause);
    }

    private static ContextDescriptor unmapCreateContextDetail(ContextSpec contextDetail, StatementSpecUnMapContext unmapContext) {
        ContextDescriptor desc;
        if (contextDetail instanceof ContextSpecInitiatedTerminated) {
            ContextSpecInitiatedTerminated spec = (ContextSpecInitiatedTerminated) contextDetail;
            ContextDescriptorCondition startCondition = unmapCreateContextRangeCondition(spec.getStartCondition(), unmapContext);
            ContextDescriptorCondition endCondition = unmapCreateContextRangeCondition(spec.getEndCondition(), unmapContext);
            List<Expression> distinctExpressions = null;
            if (spec.getDistinctExpressions() != null && spec.getDistinctExpressions().length > 0) {
                distinctExpressions = unmapExpressionDeep(Arrays.asList(spec.getDistinctExpressions()), unmapContext);
            }
            desc = new ContextDescriptorInitiatedTerminated(startCondition, endCondition, spec.isOverlapping(), distinctExpressions);
        } else if (contextDetail instanceof ContextSpecKeyed) {
            ContextSpecKeyed seg = (ContextSpecKeyed) contextDetail;
            List<ContextDescriptorKeyedSegmentedItem> segmentedItems = new ArrayList<>();
            for (ContextSpecKeyedItem item : seg.getItems()) {
                Filter filter = unmapFilter(item.getFilterSpecRaw(), unmapContext);
                segmentedItems.add(new ContextDescriptorKeyedSegmentedItem(item.getPropertyNames(), filter, item.getAliasName()));
            }
            List<ContextDescriptorConditionFilter> initCondition = null;
            if (seg.getOptionalInit() != null) {
                initCondition = new ArrayList<>();
                for (ContextSpecCondition filter : seg.getOptionalInit()) {
                    initCondition.add((ContextDescriptorConditionFilter) unmapCreateContextRangeCondition(filter, unmapContext));
                }
            }
            ContextDescriptorCondition terminationCondition = null;
            if (seg.getOptionalTermination() != null) {
                terminationCondition = unmapCreateContextRangeCondition(seg.getOptionalTermination(), unmapContext);
            }
            desc = new ContextDescriptorKeyedSegmented(segmentedItems, initCondition, terminationCondition);
        } else if (contextDetail instanceof ContextSpecCategory) {
            ContextSpecCategory category = (ContextSpecCategory) contextDetail;
            List<ContextDescriptorCategoryItem> categoryItems = new ArrayList<>();
            Filter filter = unmapFilter(category.getFilterSpecRaw(), unmapContext);
            for (ContextSpecCategoryItem item : category.getItems()) {
                Expression expr = unmapExpressionDeep(item.getExpression(), unmapContext);
                categoryItems.add(new ContextDescriptorCategoryItem(expr, item.getName()));
            }
            desc = new ContextDescriptorCategory(categoryItems, filter);
        } else if (contextDetail instanceof ContextSpecHash) {
            ContextSpecHash init = (ContextSpecHash) contextDetail;
            List<ContextDescriptorHashSegmentedItem> hashes = new ArrayList<>();
            for (ContextSpecHashItem item : init.getItems()) {
                DotExpressionItem dot = unmapChains(new ArrayList<>(Collections.singletonList(item.getFunction())), unmapContext).get(0);
                SingleRowMethodExpression dotExpression = new SingleRowMethodExpression(new ArrayList<>(Collections.singletonList(dot)));
                Filter filter = unmapFilter(item.getFilterSpecRaw(), unmapContext);
                hashes.add(new ContextDescriptorHashSegmentedItem(dotExpression, filter));
            }
            desc = new ContextDescriptorHashSegmented(hashes, init.getGranularity(), init.isPreallocate());
        } else {
            ContextNested nested = (ContextNested) contextDetail;
            List<CreateContextClause> contexts = new ArrayList<>();
            for (CreateContextDesc item : nested.getContexts()) {
                ContextDescriptor detail = unmapCreateContextDetail(item.getContextDetail(), unmapContext);
                contexts.add(new CreateContextClause(item.getContextName(), detail));
            }
            desc = new ContextDescriptorNested(contexts);
        }
        return desc;
    }

    private static ContextDescriptorCondition unmapCreateContextRangeCondition(ContextSpecCondition endpoint, StatementSpecUnMapContext unmapContext) {
        if (endpoint instanceof ContextSpecConditionCrontab) {
            ContextSpecConditionCrontab crontab = (ContextSpecConditionCrontab) endpoint;
            List<List<Expression>> crontabs = new ArrayList<>();
            for (List<ExprNode> crontabItem : crontab.getCrontabs()) {
                crontabs.add(unmapExpressionDeep(crontabItem, unmapContext));
            }
            return new ContextDescriptorConditionCrontab(crontabs, crontab.isImmediate());
        } else if (endpoint instanceof ContextSpecConditionPattern) {
            ContextSpecConditionPattern pattern = (ContextSpecConditionPattern) endpoint;
            PatternExpr patternExpr = unmapPatternEvalDeep(pattern.getPatternRaw(), unmapContext);
            return new ContextDescriptorConditionPattern(patternExpr, pattern.isInclusive(), pattern.isImmediate(), pattern.getAsName());
        } else if (endpoint instanceof ContextSpecConditionFilter) {
            ContextSpecConditionFilter filter = (ContextSpecConditionFilter) endpoint;
            Filter filterExpr = unmapFilter(filter.getFilterSpecRaw(), unmapContext);
            return new ContextDescriptorConditionFilter(filterExpr, filter.getOptionalFilterAsName());
        } else if (endpoint instanceof ContextSpecConditionTimePeriod) {
            ContextSpecConditionTimePeriod period = (ContextSpecConditionTimePeriod) endpoint;
            TimePeriodExpression expression = (TimePeriodExpression) unmapExpressionDeep(period.getTimePeriod(), unmapContext);
            return new ContextDescriptorConditionTimePeriod(expression, period.isImmediate());
        } else if (endpoint instanceof ContextSpecConditionImmediate) {
            return new ContextDescriptorConditionImmediate();
        } else if (endpoint instanceof ContextSpecConditionNever) {
            return new ContextDescriptorConditionNever();
        }
        throw new IllegalStateException("Unrecognized endpoint " + endpoint);
    }

    private static void unmapCreateWindow(CreateWindowDesc createWindowDesc, EPStatementObjectModel model, StatementSpecUnMapContext unmapContext) {
        if (createWindowDesc == null) {
            return;
        }
        Expression filter = null;
        if (createWindowDesc.getInsertFilter() != null) {
            filter = unmapExpressionDeep(createWindowDesc.getInsertFilter(), unmapContext);
        }

        CreateWindowClause clause = new CreateWindowClause(createWindowDesc.getWindowName(), unmapViews(createWindowDesc.getViewSpecs(), unmapContext));
        clause.setInsert(createWindowDesc.isInsert());
        clause.setInsertWhereClause(filter);
        clause.setColumns(unmapColumns(createWindowDesc.getColumns()));
        clause.setAsEventTypeName(createWindowDesc.getAsEventTypeName());
        clause.setRetainUnion(createWindowDesc.getStreamSpecOptions().isRetainUnion());
        model.setCreateWindow(clause);
    }

    private static void unmapCreateIndex(CreateIndexDesc createIndexDesc, EPStatementObjectModel model, StatementSpecUnMapContext unmapContext) {
        if (createIndexDesc == null) {
            return;
        }
        List<CreateIndexColumn> cols = new ArrayList<>();
        for (CreateIndexItem item : createIndexDesc.getColumns()) {
            CreateIndexColumn col = unmapCreateIndexColumn(item, unmapContext);
            cols.add(col);
        }
        model.setCreateIndex(new CreateIndexClause(createIndexDesc.getIndexName(), createIndexDesc.getWindowName(), cols, createIndexDesc.isUnique()));
    }

    private static CreateIndexColumn unmapCreateIndexColumn(CreateIndexItem item, StatementSpecUnMapContext unmapContext) {
        List<Expression> columns = unmapExpressionDeep(item.getExpressions(), unmapContext);
        List<Expression> parameters = unmapExpressionDeep(item.getParameters(), unmapContext);
        return new CreateIndexColumn(columns, item.getType(), parameters);
    }

    private static void unmapCreateVariable(CreateVariableDesc createVariableDesc, EPStatementObjectModel model, StatementSpecUnMapContext unmapContext) {
        if (createVariableDesc == null) {
            return;
        }
        Expression assignment = null;
        if (createVariableDesc.getAssignment() != null) {
            assignment = unmapExpressionDeep(createVariableDesc.getAssignment(), unmapContext);
        }
        CreateVariableClause clause = new CreateVariableClause(createVariableDesc.getVariableType().toEPL(), createVariableDesc.getVariableName(), assignment, createVariableDesc.isConstant());
        model.setCreateVariable(clause);
    }

    private static void unmapCreateSchema(CreateSchemaDesc desc, EPStatementObjectModel model) {
        if (desc == null) {
            return;
        }
        model.setCreateSchema(unmapCreateSchemaInternal(desc));
    }

    private static void unmapCreateExpression(CreateExpressionDesc desc, EPStatementObjectModel model, StatementSpecUnMapContext unmapContext) {
        if (desc == null) {
            return;
        }
        CreateExpressionClause clause;
        if (desc.getExpression() != null) {
            clause = new CreateExpressionClause(unmapExpressionDeclItem(desc.getExpression()));
        } else {
            clause = new CreateExpressionClause(unmapScriptExpression(desc.getScript()));
        }
        model.setCreateExpression(clause);
    }

    private static void unmapCreateClass(String createClassClassText, EPStatementObjectModel model) {
        if (createClassClassText == null) {
            return;
        }
        model.setCreateClass(new CreateClassClause(createClassClassText));
    }

    private static CreateSchemaClause unmapCreateSchemaInternal(CreateSchemaDesc desc) {
        List<SchemaColumnDesc> columns = unmapColumns(desc.getColumns());
        CreateSchemaClause clause = new CreateSchemaClause(desc.getSchemaName(), desc.getTypes(), columns, desc.getInherits(), desc.getAssignedType().mapToSoda());
        clause.setStartTimestampPropertyName(desc.getStartTimestampProperty());
        clause.setEndTimestampPropertyName(desc.getEndTimestampProperty());
        clause.setCopyFrom(desc.getCopyFrom());
        return clause;
    }

    private static void unmapCreateGraph(CreateDataFlowDesc desc, EPStatementObjectModel model, StatementSpecUnMapContext unmapContext) {
        if (desc == null) {
            return;
        }

        List<CreateSchemaClause> schemas = new ArrayList<>();
        for (CreateSchemaDesc schema : desc.getSchemas()) {
            schemas.add(unmapCreateSchemaInternal(schema));
        }

        List<DataFlowOperator> operators = new ArrayList<>();
        for (GraphOperatorSpec spec : desc.getOperators()) {
            operators.add(unmapGraphOperator(spec, unmapContext));
        }

        CreateDataFlowClause clause = new CreateDataFlowClause(desc.getGraphName(), schemas, operators);
        model.setCreateDataFlow(clause);
    }

    private static DataFlowOperator unmapGraphOperator(GraphOperatorSpec spec, StatementSpecUnMapContext unmapContext) {
        DataFlowOperator op = new DataFlowOperator();
        op.setOperatorName(spec.getOperatorName());
        op.setAnnotations(unmapAnnotations(spec.getAnnotations()));

        List<DataFlowOperatorInput> inputs = new ArrayList<>();
        for (GraphOperatorInputNamesAlias in : spec.getInput().getStreamNamesAndAliases()) {
            inputs.add(new DataFlowOperatorInput(Arrays.asList(in.getInputStreamNames()), in.getOptionalAsName()));
        }
        op.setInput(inputs);

        List<DataFlowOperatorOutput> outputs = new ArrayList<>();
        for (GraphOperatorOutputItem out : spec.getOutput().getItems()) {
            List<DataFlowOperatorOutputType> types = out.getTypeInfo().isEmpty() ? null : new ArrayList<>(Collections.singletonList(unmapTypeInfo(out.getTypeInfo().get(0))));
            outputs.add(new DataFlowOperatorOutput(out.getStreamName(), types));
        }
        op.setOutput(outputs);

        if (spec.getDetail() != null) {
            List<DataFlowOperatorParameter> parameters = new ArrayList<>();
            for (Map.Entry<String, Object> param : spec.getDetail().getConfigs().entrySet()) {
                Object value = param.getValue();
                if (value instanceof StatementSpecRaw) {
                    value = unmapInternal((StatementSpecRaw) value, unmapContext);
                }
                if (value instanceof ExprNode) {
                    value = unmapExpressionDeep((ExprNode) value, unmapContext);
                }
                parameters.add(new DataFlowOperatorParameter(param.getKey(), value));
            }
            op.setParameters(parameters);
        } else {
            op.setParameters(Collections.emptyList());
        }

        return op;
    }

    private static DataFlowOperatorOutputType unmapTypeInfo(GraphOperatorOutputItemType typeInfo) {
        List<DataFlowOperatorOutputType> types = Collections.emptyList();
        if (typeInfo.getTypeParameters() != null && !typeInfo.getTypeParameters().isEmpty()) {
            types = new ArrayList<>();
            for (GraphOperatorOutputItemType type : typeInfo.getTypeParameters()) {
                types.add(unmapTypeInfo(type));
            }
        }
        return new DataFlowOperatorOutputType(typeInfo.isWildcard(), typeInfo.getTypeOrClassname(), types);
    }

    private static void unmapOrderBy(List<OrderByItem> orderByList, EPStatementObjectModel model, StatementSpecUnMapContext unmapContext) {
        if ((orderByList == null) || (orderByList.size() == 0)) {
            return;
        }

        OrderByClause clause = new OrderByClause();
        for (OrderByItem item : orderByList) {
            Expression expr = unmapExpressionDeep(item.getExprNode(), unmapContext);
            clause.add(expr, item.isDescending());
        }
        model.setOrderByClause(clause);
    }

    private static void unmapOutputLimit(OutputLimitSpec outputLimitSpec, EPStatementObjectModel model, StatementSpecUnMapContext unmapContext) {
        if (outputLimitSpec == null) {
            return;
        }

        OutputLimitSelector selector = OutputLimitSelector.DEFAULT;
        if (outputLimitSpec.getDisplayLimit() == OutputLimitLimitType.FIRST) {
            selector = OutputLimitSelector.FIRST;
        }
        if (outputLimitSpec.getDisplayLimit() == OutputLimitLimitType.LAST) {
            selector = OutputLimitSelector.LAST;
        }
        if (outputLimitSpec.getDisplayLimit() == OutputLimitLimitType.SNAPSHOT) {
            selector = OutputLimitSelector.SNAPSHOT;
        }
        if (outputLimitSpec.getDisplayLimit() == OutputLimitLimitType.ALL) {
            selector = OutputLimitSelector.ALL;
        }

        OutputLimitClause clause;
        if (outputLimitSpec.getRateType() == OutputLimitRateType.TIME_PERIOD) {
            TimePeriodExpression timePeriod = (TimePeriodExpression) unmapExpressionDeep(outputLimitSpec.getTimePeriodExpr(), unmapContext);
            clause = new OutputLimitClause(selector, timePeriod);
        } else if (outputLimitSpec.getRateType() == OutputLimitRateType.AFTER) {
            if (outputLimitSpec.getAfterTimePeriodExpr() != null) {
                TimePeriodExpression after = (TimePeriodExpression) unmapExpressionDeep(outputLimitSpec.getAfterTimePeriodExpr(), unmapContext);
                clause = new OutputLimitClause(OutputLimitSelector.DEFAULT, OutputLimitUnit.AFTER, after, null);
            } else {
                clause = new OutputLimitClause(OutputLimitSelector.DEFAULT, OutputLimitUnit.AFTER, null, outputLimitSpec.getAfterNumberOfEvents());
            }
        } else if (outputLimitSpec.getRateType() == OutputLimitRateType.WHEN_EXPRESSION) {
            Expression whenExpression = unmapExpressionDeep(outputLimitSpec.getWhenExpressionNode(), unmapContext);
            List<Assignment> thenAssignments = new ArrayList<>();
            clause = new OutputLimitClause(selector, whenExpression, thenAssignments);
            if (outputLimitSpec.getThenExpressions() != null) {
                for (OnTriggerSetAssignment assignment : outputLimitSpec.getThenExpressions()) {
                    Expression expr = unmapExpressionDeep(assignment.getExpression(), unmapContext);
                    clause.addThenAssignment(expr);
                }
            }
        } else if (outputLimitSpec.getRateType() == OutputLimitRateType.CRONTAB) {
            List<ExprNode> timerAtExpressions = outputLimitSpec.getCrontabAtSchedule();
            List<Expression> mappedExpr = unmapExpressionDeep(timerAtExpressions, unmapContext);
            clause = new OutputLimitClause(selector, mappedExpr.toArray(new Expression[0]));
        } else if (outputLimitSpec.getRateType() == OutputLimitRateType.TERM) {
            clause = new OutputLimitClause(selector, OutputLimitUnit.CONTEXT_PARTITION_TERM);
        } else {
            clause = new OutputLimitClause(selector, outputLimitSpec.getRate(), outputLimitSpec.getVariableName(), OutputLimitUnit.EVENTS);
        }

        clause.setAfterNumberOfEvents(outputLimitSpec.getAfterNumberOfEvents());
        if (outputLimitSpec.getAfterTimePeriodExpr() != null) {
            clause.setAfterTimePeriodExpression(unmapExpressionDeep(outputLimitSpec.getAfterTimePeriodExpr(), unmapContext));
        }
        clause.setAndAfterTerminate(outputLimitSpec.isAndAfterTerminate());
        if (outputLimitSpec.getAndAfterTerminateExpr() != null) {
            clause.setAndAfterTerminateAndExpr(unmapExpressionDeep(outputLimitSpec.getAndAfterTerminateExpr(), unmapContext));
        }
        if (outputLimitSpec.getAndAfterTerminateThenExpressions() != null) {
            List<Assignment> thenAssignments = new ArrayList<>();
            for (OnTriggerSetAssignment assignment : outputLimitSpec.getAndAfterTerminateThenExpressions()) {
                Expression expr = unmapExpressionDeep(assignment.getExpression(), unmapContext);
                thenAssignments.add(new Assignment(expr));
            }
            clause.setAndAfterTerminateThenAssignments(thenAssignments);
        }
        model.setOutputLimitClause(clause);
    }

    private static void unmapRowLimit(RowLimitSpec rowLimitSpec, EPStatementObjectModel model) {
        if (rowLimitSpec == null) {
            return;
        }
        RowLimitClause spec = new RowLimitClause(rowLimitSpec.getNumRows(), rowLimitSpec.getOptionalOffset(),
            rowLimitSpec.getNumRowsVariable(), rowLimitSpec.getOptionalOffsetVariable());
        model.setRowLimitClause(spec);
    }

    private static void unmapForClause(ForClauseSpec spec, EPStatementObjectModel model, StatementSpecUnMapContext unmapContext) {
        if ((spec == null) || (spec.getClauses() == null) || (spec.getClauses().size() == 0)) {
            return;
        }
        ForClause clause = new ForClause();
        for (ForClauseItemSpec itemSpec : spec.getClauses()) {
            ForClauseItem item = new ForClauseItem(ForClauseKeyword.valueOf(itemSpec.getKeyword().toUpperCase(Locale.ENGLISH)));
            item.setExpressions(unmapExpressionDeep(itemSpec.getExpressions(), unmapContext));
            clause.getItems().add(item);
        }
        model.setForClause(clause);
    }

    private static void unmapMatchRecognize(MatchRecognizeSpec spec, EPStatementObjectModel model, StatementSpecUnMapContext unmapContext) {
        if (spec == null) {
            return;
        }
        MatchRecognizeClause clause = new MatchRecognizeClause();
        clause.setPartitionExpressions(unmapExpressionDeep(spec.getPartitionByExpressions(), unmapContext));

        List<SelectClauseExpression> measures = new ArrayList<>();
        for (MatchRecognizeMeasureItem item : spec.getMeasures()) {
            measures.add(new SelectClauseExpression(unmapExpressionDeep(item.getExpr(), unmapContext), item.getName()));
        }
        clause.setMeasures(measures);
        clause.setAll(spec.isAllMatches());
        clause.setSkipClause(MatchRecognizeSkipClause.values()[spec.getSkip().getSkip().ordinal()]);

        List<MatchRecognizeDefine> defines = new ArrayList<>();
        for (MatchRecognizeDefineItem define : spec.getDefines()) {

            defines.add(new MatchRecognizeDefine(define.getIdentifier(), unmapExpressionDeep(define.getExpression(), unmapContext)));
        }
        clause.setDefines(defines);

        if (spec.getInterval() != null) {
            clause.setIntervalClause(new MatchRecognizeIntervalClause((TimePeriodExpression) unmapExpressionDeep(spec.getInterval().getTimePeriodExpr(), unmapContext), spec.getInterval().isOrTerminated()));
        }
        clause.setPattern(unmapExpressionDeepRowRecog(spec.getPattern(), unmapContext));
        model.setMatchRecognizeClause(clause);
    }

    private static void mapOrderBy(OrderByClause orderByClause, StatementSpecRaw raw, StatementSpecMapContext mapContext) {
        if (orderByClause == null) {
            return;
        }
        for (OrderByElement element : orderByClause.getOrderByExpressions()) {
            ExprNode orderExpr = mapExpressionDeep(element.getExpression(), mapContext);
            OrderByItem item = new OrderByItem(orderExpr, element.isDescending());
            raw.getOrderByList().add(item);
        }
    }

    private static void mapOutputLimit(OutputLimitClause outputLimitClause, StatementSpecRaw raw, StatementSpecMapContext mapContext) {
        if (outputLimitClause == null) {
            return;
        }

        OutputLimitLimitType displayLimit = OutputLimitLimitType.valueOf(outputLimitClause.getSelector().toString().toUpperCase(Locale.ENGLISH));

        OutputLimitRateType rateType;
        if (outputLimitClause.getUnit() == OutputLimitUnit.EVENTS) {
            rateType = OutputLimitRateType.EVENTS;
        } else if (outputLimitClause.getUnit() == OutputLimitUnit.TIME_PERIOD) {
            rateType = OutputLimitRateType.TIME_PERIOD;
        } else if (outputLimitClause.getUnit() == OutputLimitUnit.CRONTAB_EXPRESSION) {
            rateType = OutputLimitRateType.CRONTAB;
        } else if (outputLimitClause.getUnit() == OutputLimitUnit.WHEN_EXPRESSION) {
            rateType = OutputLimitRateType.WHEN_EXPRESSION;
        } else if (outputLimitClause.getUnit() == OutputLimitUnit.AFTER) {
            rateType = OutputLimitRateType.AFTER;
        } else if (outputLimitClause.getUnit() == OutputLimitUnit.CONTEXT_PARTITION_TERM) {
            rateType = OutputLimitRateType.TERM;
        } else {
            throw new IllegalArgumentException("Unknown output limit unit " + outputLimitClause.getUnit());
        }

        Double frequency = outputLimitClause.getFrequency();
        String frequencyVariable = outputLimitClause.getFrequencyVariable();

        if (frequencyVariable != null) {
            mapContext.getVariableNames().add(frequencyVariable);
        }

        ExprNode whenExpression = null;
        List<OnTriggerSetAssignment> assignments = null;
        if (outputLimitClause.getWhenExpression() != null) {
            whenExpression = mapExpressionDeep(outputLimitClause.getWhenExpression(), mapContext);

            assignments = new ArrayList<>();
            for (Assignment pair : outputLimitClause.getThenAssignments()) {
                ExprNode expr = mapExpressionDeep(pair.getValue(), mapContext);
                assignments.add(new OnTriggerSetAssignment(expr));
            }
        }

        List<ExprNode> timerAtExprList = null;
        if (outputLimitClause.getCrontabAtParameters() != null) {
            timerAtExprList = mapExpressionDeep(Arrays.asList(outputLimitClause.getCrontabAtParameters()), mapContext);
        }

        ExprTimePeriod timePeriod = null;
        if (outputLimitClause.getTimePeriodExpression() != null) {
            timePeriod = (ExprTimePeriod) mapExpressionDeep(outputLimitClause.getTimePeriodExpression(), mapContext);
        }

        ExprTimePeriod afterTimePeriod = null;
        if (outputLimitClause.getAfterTimePeriodExpression() != null) {
            afterTimePeriod = (ExprTimePeriod) mapExpressionDeep(outputLimitClause.getAfterTimePeriodExpression(), mapContext);
        }

        ExprNode andAfterTerminateAndExpr = null;
        if (outputLimitClause.getAndAfterTerminateAndExpr() != null) {
            andAfterTerminateAndExpr = mapExpressionDeep(outputLimitClause.getAndAfterTerminateAndExpr(), mapContext);
        }

        List<OnTriggerSetAssignment> afterTerminateAssignments = null;
        if (outputLimitClause.getAndAfterTerminateThenAssignments() != null) {
            afterTerminateAssignments = new ArrayList<>();
            for (Assignment pair : outputLimitClause.getAndAfterTerminateThenAssignments()) {
                ExprNode expr = mapExpressionDeep(pair.getValue(), mapContext);
                afterTerminateAssignments.add(new OnTriggerSetAssignment(expr));
            }
        }

        OutputLimitSpec spec = new OutputLimitSpec(frequency, frequencyVariable, rateType, displayLimit, whenExpression, assignments, timerAtExprList, timePeriod, afterTimePeriod, outputLimitClause.getAfterNumberOfEvents(), outputLimitClause.isAndAfterTerminate(), andAfterTerminateAndExpr, afterTerminateAssignments);
        raw.setOutputLimitSpec(spec);
    }

    private static void mapOnTrigger(OnClause onExpr, StatementSpecRaw raw, StatementSpecMapContext mapContext) {
        if (onExpr == null) {
            return;
        }

        if (onExpr instanceof OnDeleteClause) {
            OnDeleteClause onDeleteClause = (OnDeleteClause) onExpr;
            raw.setOnTriggerDesc(new OnTriggerWindowDesc(onDeleteClause.getWindowName(), onDeleteClause.getOptionalAsName(), OnTriggerType.ON_DELETE, false));
        } else if (onExpr instanceof OnSelectClause) {
            OnSelectClause onSelectClause = (OnSelectClause) onExpr;
            raw.setOnTriggerDesc(new OnTriggerWindowDesc(onSelectClause.getWindowName(), onSelectClause.getOptionalAsName(), OnTriggerType.ON_SELECT, onSelectClause.isDeleteAndSelect()));
        } else if (onExpr instanceof OnSetClause) {
            OnSetClause setClause = (OnSetClause) onExpr;
            List<OnTriggerSetAssignment> assignments = new ArrayList<>();
            for (Assignment pair : setClause.getAssignments()) {
                ExprNode expr = mapExpressionDeep(pair.getValue(), mapContext);
                assignments.add(new OnTriggerSetAssignment(expr));
            }
            OnTriggerSetDesc desc = new OnTriggerSetDesc(assignments);
            raw.setOnTriggerDesc(desc);
        } else if (onExpr instanceof OnUpdateClause) {
            OnUpdateClause updateClause = (OnUpdateClause) onExpr;
            List<OnTriggerSetAssignment> assignments = new ArrayList<>();
            for (Assignment pair : updateClause.getAssignments()) {
                ExprNode expr = mapExpressionDeep(pair.getValue(), mapContext);
                assignments.add(new OnTriggerSetAssignment(expr));
            }
            OnTriggerWindowUpdateDesc desc = new OnTriggerWindowUpdateDesc(updateClause.getWindowName(), updateClause.getOptionalAsName(), assignments);
            raw.setOnTriggerDesc(desc);
        } else if (onExpr instanceof OnInsertSplitStreamClause) {
            OnInsertSplitStreamClause splitClause = (OnInsertSplitStreamClause) onExpr;
            List<OnTriggerSplitStream> streams = new ArrayList<>();
            for (OnInsertSplitStreamItem item : splitClause.getItems()) {
                OnTriggerSplitStreamFromClause fromClause = null;
                if (item.getPropertySelects() != null) {
                    PropertyEvalSpec propertyEvalSpec = mapPropertySelects(item.getPropertySelects(), mapContext);
                    fromClause = new OnTriggerSplitStreamFromClause(propertyEvalSpec, item.getPropertySelectsStreamName());
                }

                ExprNode whereClause = null;
                if (item.getWhereClause() != null) {
                    whereClause = mapExpressionDeep(item.getWhereClause(), mapContext);
                }

                InsertIntoDesc insertDesc = mapInsertInto(item.getInsertInto(), mapContext);
                SelectClauseSpecRaw selectDesc = mapSelectRaw(item.getSelectClause(), mapContext);

                streams.add(new OnTriggerSplitStream(insertDesc, selectDesc, fromClause, whereClause));
            }
            OnTriggerSplitStreamDesc desc = new OnTriggerSplitStreamDesc(OnTriggerType.ON_SPLITSTREAM, splitClause.isFirst(), streams);
            raw.setOnTriggerDesc(desc);
        } else if (onExpr instanceof OnMergeClause) {
            OnMergeClause merge = (OnMergeClause) onExpr;
            List<OnTriggerMergeMatched> matcheds = new ArrayList<>();
            for (OnMergeMatchItem matchItem : merge.getMatchItems()) {
                List<OnTriggerMergeAction> actions = new ArrayList<>();
                for (OnMergeMatchedAction action : matchItem.getActions()) {
                    OnTriggerMergeAction actionItem;
                    if (action instanceof OnMergeMatchedDeleteAction) {
                        OnMergeMatchedDeleteAction delete = (OnMergeMatchedDeleteAction) action;
                        ExprNode optionalCondition = delete.getWhereClause() == null ? null : mapExpressionDeep(delete.getWhereClause(), mapContext);
                        actionItem = new OnTriggerMergeActionDelete(optionalCondition);
                    } else if (action instanceof OnMergeMatchedUpdateAction) {
                        OnMergeMatchedUpdateAction update = (OnMergeMatchedUpdateAction) action;
                        List<OnTriggerSetAssignment> assignments = new ArrayList<>();
                        for (Assignment pair : update.getAssignments()) {
                            ExprNode expr = mapExpressionDeep(pair.getValue(), mapContext);
                            assignments.add(new OnTriggerSetAssignment(expr));
                        }
                        ExprNode optionalCondition = update.getWhereClause() == null ? null : mapExpressionDeep(update.getWhereClause(), mapContext);
                        actionItem = new OnTriggerMergeActionUpdate(optionalCondition, assignments);
                    } else if (action instanceof OnMergeMatchedInsertAction) {
                        actionItem = mapOnTriggerMergeActionInsert((OnMergeMatchedInsertAction) action, mapContext);
                    } else {
                        throw new IllegalArgumentException("Unrecognized merged action type '" + action.getClass() + "'");
                    }
                    actions.add(actionItem);
                }
                ExprNode optionalCondition = matchItem.getOptionalCondition() == null ? null : mapExpressionDeep(matchItem.getOptionalCondition(), mapContext);
                matcheds.add(new OnTriggerMergeMatched(matchItem.isMatched(), optionalCondition, actions));
            }
            OnTriggerMergeActionInsert optionalInsertNoMatch = merge.getInsertNoMatch() == null ? null : mapOnTriggerMergeActionInsert(merge.getInsertNoMatch(), mapContext);
            OnTriggerMergeDesc mergeDesc = new OnTriggerMergeDesc(merge.getWindowName(), merge.getOptionalAsName(), optionalInsertNoMatch, matcheds);
            raw.setOnTriggerDesc(mergeDesc);
        } else {
            throw new IllegalArgumentException("Cannot map on-clause expression type : " + onExpr);
        }
    }

    private static OnTriggerMergeActionInsert mapOnTriggerMergeActionInsert(OnMergeMatchedInsertAction insert, StatementSpecMapContext mapContext) {
        List<String> columnNames = new ArrayList<>(insert.getColumnNames());
        List<SelectClauseElementRaw> select = mapSelectClauseElements(insert.getSelectList(), mapContext);
        ExprNode optionalCondition = insert.getWhereClause() == null ? null : mapExpressionDeep(insert.getWhereClause(), mapContext);
        ExprNode optionalPrecedence = insert.getEventPrecedence() == null ? null : mapExpressionDeep(insert.getEventPrecedence(), mapContext);
        return new OnTriggerMergeActionInsert(optionalCondition, insert.getOptionalStreamName(), columnNames, select, optionalPrecedence);
    }

    private static void mapRowLimit(RowLimitClause rowLimitClause, StatementSpecRaw raw, StatementSpecMapContext mapContext) {
        if (rowLimitClause == null) {
            return;
        }
        if (rowLimitClause.getNumRowsVariable() != null) {
            mapContext.getVariableNames().add(rowLimitClause.getNumRowsVariable());
        }
        if (rowLimitClause.getOptionalOffsetRowsVariable() != null) {
            mapContext.getVariableNames().add(rowLimitClause.getOptionalOffsetRowsVariable());
        }
        raw.setRowLimitSpec(new RowLimitSpec(rowLimitClause.getNumRows(), rowLimitClause.getOptionalOffsetRows(),
            rowLimitClause.getNumRowsVariable(), rowLimitClause.getOptionalOffsetRowsVariable()));
    }

    private static void mapForClause(ForClause clause, StatementSpecRaw raw, StatementSpecMapContext mapContext) {
        if ((clause == null) || (clause.getItems().size() == 0)) {
            return;
        }
        raw.setForClauseSpec(new ForClauseSpec());
        for (ForClauseItem item : clause.getItems()) {
            ForClauseItemSpec specItem = new ForClauseItemSpec(item.getKeyword().getName(), mapExpressionDeep(item.getExpressions(), mapContext));
            raw.getForClauseSpec().getClauses().add(specItem);
        }
    }

    private static void mapMatchRecognize(MatchRecognizeClause clause, StatementSpecRaw raw, StatementSpecMapContext mapContext) {
        if (clause == null) {
            return;
        }
        MatchRecognizeSpec spec = new MatchRecognizeSpec();
        spec.setPartitionByExpressions(mapExpressionDeep(clause.getPartitionExpressions(), mapContext));

        List<MatchRecognizeMeasureItem> measures = new ArrayList<>();
        for (SelectClauseExpression item : clause.getMeasures()) {
            measures.add(new MatchRecognizeMeasureItem(mapExpressionDeep(item.getExpression(), mapContext), item.getAsName()));
        }
        spec.setMeasures(measures);
        spec.setAllMatches(clause.isAll());
        spec.setSkip(new MatchRecognizeSkip(MatchRecognizeSkipEnum.values()[clause.getSkipClause().ordinal()]));

        List<MatchRecognizeDefineItem> defines = new ArrayList<>();
        for (MatchRecognizeDefine define : clause.getDefines()) {

            defines.add(new MatchRecognizeDefineItem(define.getName(), mapExpressionDeep(define.getExpression(), mapContext)));
        }
        spec.setDefines(defines);

        if (clause.getIntervalClause() != null) {
            ExprTimePeriod timePeriod = (ExprTimePeriod) mapExpressionDeep(clause.getIntervalClause().getExpression(), mapContext);
            spec.setInterval(new MatchRecognizeInterval(timePeriod, clause.getIntervalClause().isOrTerminated()));
        }
        spec.setPattern(mapExpressionDeepRowRecog(clause.getPattern(), mapContext));
        raw.setMatchRecognizeSpec(spec);
    }

    private static void mapHaving(Expression havingClause, StatementSpecRaw raw, StatementSpecMapContext mapContext) {
        if (havingClause == null) {
            return;
        }
        ExprNode node = mapExpressionDeep(havingClause, mapContext);
        raw.setHavingClause(node);
    }

    private static void unmapHaving(ExprNode havingExprRootNode, EPStatementObjectModel model, StatementSpecUnMapContext unmapContext) {
        if (havingExprRootNode == null) {
            return;
        }
        Expression expr = unmapExpressionDeep(havingExprRootNode, unmapContext);
        model.setHavingClause(expr);
    }

    private static void mapGroupBy(GroupByClause groupByClause, StatementSpecRaw raw, StatementSpecMapContext mapContext) {
        if (groupByClause == null) {
            return;
        }
        for (GroupByClauseExpression expr : groupByClause.getGroupByExpressions()) {
            GroupByClauseElement element = mapGroupByExpr(expr, mapContext);
            raw.getGroupByExpressions().add(element);
        }
    }

    private static GroupByClauseElement mapGroupByExpr(GroupByClauseExpression expr, StatementSpecMapContext mapContext) {
        if (expr instanceof GroupByClauseExpressionSingle) {
            ExprNode node = mapExpressionDeep(((GroupByClauseExpressionSingle) expr).getExpression(), mapContext);
            return new GroupByClauseElementExpr(node);
        }
        if (expr instanceof GroupByClauseExpressionCombination) {
            List<ExprNode> nodes = mapExpressionDeep(((GroupByClauseExpressionCombination) expr).getExpressions(), mapContext);
            return new GroupByClauseElementCombinedExpr(nodes);
        }
        if (expr instanceof GroupByClauseExpressionGroupingSet) {
            GroupByClauseExpressionGroupingSet set = (GroupByClauseExpressionGroupingSet) expr;
            return new GroupByClauseElementGroupingSet(mapGroupByElements(set.getExpressions(), mapContext));
        }
        if (expr instanceof GroupByClauseExpressionRollupOrCube) {
            GroupByClauseExpressionRollupOrCube rollup = (GroupByClauseExpressionRollupOrCube) expr;
            return new GroupByClauseElementRollupOrCube(rollup.isCube(), mapGroupByElements(rollup.getExpressions(), mapContext));
        }
        throw new IllegalStateException("Group by expression not recognized: " + expr);
    }

    private static List<GroupByClauseElement> mapGroupByElements(List<GroupByClauseExpression> elements, StatementSpecMapContext mapContext) {
        List<GroupByClauseElement> out = new ArrayList<>();
        for (GroupByClauseExpression element : elements) {
            out.add(mapGroupByExpr(element, mapContext));
        }
        return out;
    }

    private static void unmapGroupBy(List<GroupByClauseElement> groupByExpressions, EPStatementObjectModel model, StatementSpecUnMapContext unmapContext) {
        if (groupByExpressions.size() == 0) {
            return;
        }
        List<GroupByClauseExpression> expressions = new ArrayList<>();
        for (GroupByClauseElement element : groupByExpressions) {
            expressions.add(unmapGroupByExpression(element, unmapContext));
        }
        model.setGroupByClause(new GroupByClause(expressions));
    }

    private static GroupByClauseExpression unmapGroupByExpression(GroupByClauseElement element, StatementSpecUnMapContext unmapContext) {
        if (element instanceof GroupByClauseElementExpr) {
            GroupByClauseElementExpr expr = (GroupByClauseElementExpr) element;
            Expression unmapped = unmapExpressionDeep(expr.getExpr(), unmapContext);
            return new GroupByClauseExpressionSingle(unmapped);
        }
        if (element instanceof GroupByClauseElementCombinedExpr) {
            GroupByClauseElementCombinedExpr expr = (GroupByClauseElementCombinedExpr) element;
            List<Expression> unmapped = unmapExpressionDeep(expr.getExpressions(), unmapContext);
            return new GroupByClauseExpressionCombination(unmapped);
        } else if (element instanceof GroupByClauseElementRollupOrCube) {
            GroupByClauseElementRollupOrCube rollup = (GroupByClauseElementRollupOrCube) element;
            List<GroupByClauseExpression> elements = unmapGroupByExpressions(rollup.getRollupExpressions(), unmapContext);
            return new GroupByClauseExpressionRollupOrCube(rollup.isCube(), elements);
        } else if (element instanceof GroupByClauseElementGroupingSet) {
            GroupByClauseElementGroupingSet set = (GroupByClauseElementGroupingSet) element;
            List<GroupByClauseExpression> elements = unmapGroupByExpressions(set.getElements(), unmapContext);
            return new GroupByClauseExpressionGroupingSet(elements);
        } else {
            throw new IllegalStateException("Unrecognized group-by element " + element);
        }
    }

    private static List<GroupByClauseExpression> unmapGroupByExpressions(List<GroupByClauseElement> rollupExpressions, StatementSpecUnMapContext unmapContext) {
        List<GroupByClauseExpression> out = new ArrayList<>();
        for (GroupByClauseElement e : rollupExpressions) {
            out.add(unmapGroupByExpression(e, unmapContext));
        }
        return out;
    }


    private static void mapWhere(Expression whereClause, StatementSpecRaw raw, StatementSpecMapContext mapContext) {
        if (whereClause == null) {
            return;
        }
        ExprNode node = mapExpressionDeep(whereClause, mapContext);
        raw.setWhereClause(node);
    }

    private static void unmapWhere(ExprNode filterRootNode, EPStatementObjectModel model, StatementSpecUnMapContext unmapContext) {
        if (filterRootNode == null) {
            return;
        }
        Expression expr = unmapExpressionDeep(filterRootNode, unmapContext);
        model.setWhereClause(expr);
    }

    private static void unmapFrom(List<StreamSpecRaw> streamSpecs, List<OuterJoinDesc> outerJoinDescList, EPStatementObjectModel model, StatementSpecUnMapContext unmapContext) {
        FromClause from = new FromClause();
        model.setFromClause(from);

        for (StreamSpecRaw stream : streamSpecs) {
            Stream targetStream;
            if (stream instanceof FilterStreamSpecRaw) {
                FilterStreamSpecRaw filterStreamSpec = (FilterStreamSpecRaw) stream;
                Filter filter = unmapFilter(filterStreamSpec.getRawFilterSpec(), unmapContext);
                FilterStream filterStream = new FilterStream(filter, filterStreamSpec.getOptionalStreamName());
                unmapStreamOpts(stream.getOptions(), filterStream);
                targetStream = filterStream;
            } else if (stream instanceof DBStatementStreamSpec) {
                DBStatementStreamSpec db = (DBStatementStreamSpec) stream;
                targetStream = new SQLStream(db.getDatabaseName(), db.getSqlWithSubsParams(), db.getOptionalStreamName(), db.getMetadataSQL());
            } else if (stream instanceof PatternStreamSpecRaw) {
                PatternStreamSpecRaw pattern = (PatternStreamSpecRaw) stream;
                PatternExpr patternExpr = unmapPatternEvalDeep(pattern.getEvalForgeNode(), unmapContext);
                AnnotationPart[] annotationParts = PatternLevelAnnotationUtil.annotationsFromSpec(pattern);
                PatternStream patternStream = new PatternStream(patternExpr, pattern.getOptionalStreamName(), annotationParts);
                unmapStreamOpts(stream.getOptions(), patternStream);
                targetStream = patternStream;
            } else if (stream instanceof MethodStreamSpec) {
                MethodStreamSpec method = (MethodStreamSpec) stream;
                MethodInvocationStream methodStream = new MethodInvocationStream(method.getClassName(), method.getMethodName(), method.getOptionalStreamName());
                for (ExprNode exprNode : method.getExpressions()) {
                    Expression expr = unmapExpressionDeep(exprNode, unmapContext);
                    methodStream.addParameter(expr);
                }
                methodStream.setOptionalEventTypeName(method.getEventTypeName());
                targetStream = methodStream;
            } else {
                throw new IllegalArgumentException("Stream modelled by " + stream.getClass() + " cannot be unmapped");
            }

            if (targetStream instanceof ProjectedStream) {
                ProjectedStream projStream = (ProjectedStream) targetStream;
                for (ViewSpec viewSpec : stream.getViewSpecs()) {
                    List<Expression> viewExpressions = unmapExpressionDeep(viewSpec.getObjectParameters(), unmapContext);
                    projStream.addView(View.create(viewSpec.getObjectNamespace(), viewSpec.getObjectName(), viewExpressions));
                }
            }
            from.add(targetStream);
        }

        for (OuterJoinDesc desc : outerJoinDescList) {
            PropertyValueExpression left = null;
            PropertyValueExpression right = null;
            ArrayList<PropertyValueExpressionPair> additionalProperties = new ArrayList<>();

            if (desc.getOptLeftNode() != null) {
                left = (PropertyValueExpression) unmapExpressionFlat(desc.getOptLeftNode(), unmapContext);
                right = (PropertyValueExpression) unmapExpressionFlat(desc.getOptRightNode(), unmapContext);

                if (desc.getAdditionalLeftNodes() != null) {
                    for (int i = 0; i < desc.getAdditionalLeftNodes().length; i++) {
                        ExprIdentNode leftNode = desc.getAdditionalLeftNodes()[i];
                        ExprIdentNode rightNode = desc.getAdditionalRightNodes()[i];
                        PropertyValueExpression propLeft = (PropertyValueExpression) unmapExpressionFlat(leftNode, unmapContext);
                        PropertyValueExpression propRight = (PropertyValueExpression) unmapExpressionFlat(rightNode, unmapContext);
                        additionalProperties.add(new PropertyValueExpressionPair(propLeft, propRight));
                    }
                }
            }
            from.add(new OuterJoinQualifier(desc.getOuterJoinType(), left, right, additionalProperties));
        }
    }

    private static void unmapStreamOpts(StreamSpecOptions options, ProjectedStream stream) {
        stream.setUnidirectional(options.isUnidirectional());
        stream.setRetainUnion(options.isRetainUnion());
        stream.setRetainIntersection(options.isRetainIntersection());
    }

    private static StreamSpecOptions mapStreamOpts(ProjectedStream stream) {
        return new StreamSpecOptions(stream.isUnidirectional(), stream.isRetainUnion(), stream.isRetainIntersection());
    }

    private static SelectClause unmapSelect(SelectClauseSpecRaw selectClauseSpec, SelectClauseStreamSelectorEnum selectStreamSelectorEnum, StatementSpecUnMapContext unmapContext) {
        SelectClause clause = SelectClause.create();
        clause.setStreamSelector(StatementSpecMapper.mapFromSODA(selectStreamSelectorEnum));
        clause.addElements(unmapSelectClauseElements(selectClauseSpec.getSelectExprList(), unmapContext));
        clause.setDistinct(selectClauseSpec.isDistinct());
        return clause;
    }

    private static List<SelectClauseElement> unmapSelectClauseElements(List<SelectClauseElementRaw> selectExprList, StatementSpecUnMapContext unmapContext) {
        List<SelectClauseElement> elements = new ArrayList<>();
        for (SelectClauseElementRaw raw : selectExprList) {
            if (raw instanceof SelectClauseStreamRawSpec) {
                SelectClauseStreamRawSpec streamSpec = (SelectClauseStreamRawSpec) raw;
                elements.add(new SelectClauseStreamWildcard(streamSpec.getStreamName(), streamSpec.getOptionalAsName()));
            } else if (raw instanceof SelectClauseElementWildcard) {
                elements.add(new SelectClauseWildcard());
            } else if (raw instanceof SelectClauseExprRawSpec) {
                SelectClauseExprRawSpec rawSpec = (SelectClauseExprRawSpec) raw;
                Expression expression = unmapExpressionDeep(rawSpec.getSelectExpression(), unmapContext);
                SelectClauseExpression selectExpr = new SelectClauseExpression(expression, rawSpec.getOptionalAsName());
                selectExpr.setAnnotatedByEventFlag(rawSpec.isEvents());
                elements.add(selectExpr);
            } else {
                throw new IllegalStateException("Unexpected select clause element typed " + raw.getClass().getName());
            }
        }
        return elements;
    }

    private static InsertIntoClause unmapInsertInto(InsertIntoDesc insertIntoDesc, StatementSpecUnMapContext unmapContext) {
        if (insertIntoDesc == null) {
            return null;
        }
        StreamSelector selector = mapFromSODA(insertIntoDesc.getStreamSelector());
        Expression precedence = unmapExpressionDeep(insertIntoDesc.getEventPrecedence(), unmapContext);
        return InsertIntoClause.create(insertIntoDesc.getEventTypeName(),
            insertIntoDesc.getColumnNames().toArray(new String[0]), selector, precedence);
    }

    private static void mapCreateContext(CreateContextClause createContext, StatementSpecRaw raw, StatementSpecMapContext mapContext) {
        if (createContext == null) {
            return;
        }

        ContextSpec detail = mapCreateContextDetail(createContext.getDescriptor(), mapContext);

        CreateContextDesc desc = new CreateContextDesc(createContext.getContextName(), detail);
        raw.setCreateContextDesc(desc);
    }

    private static ContextSpec mapCreateContextDetail(ContextDescriptor descriptor, StatementSpecMapContext mapContext) {
        ContextSpec detail;
        if (descriptor instanceof ContextDescriptorInitiatedTerminated) {
            ContextDescriptorInitiatedTerminated desc = (ContextDescriptorInitiatedTerminated) descriptor;
            ContextSpecCondition init = mapCreateContextRangeCondition(desc.getInitCondition(), mapContext);
            ContextSpecCondition term = mapCreateContextRangeCondition(desc.getTermCondition(), mapContext);
            ExprNode[] distinctExpressions = null;
            if (desc.getOptionalDistinctExpressions() != null && desc.getOptionalDistinctExpressions().size() > 0) {
                distinctExpressions = ExprNodeUtilityQuery.toArray(mapExpressionDeep(desc.getOptionalDistinctExpressions(), mapContext));
            }
            detail = new ContextSpecInitiatedTerminated(init, term, desc.isOverlapping(), distinctExpressions);
        } else if (descriptor instanceof ContextDescriptorKeyedSegmented) {
            ContextDescriptorKeyedSegmented seg = (ContextDescriptorKeyedSegmented) descriptor;
            List<ContextSpecKeyedItem> itemsdesc = new ArrayList<>();
            for (ContextDescriptorKeyedSegmentedItem item : seg.getItems()) {
                FilterSpecRaw rawSpec = mapFilter(item.getFilter(), mapContext);
                itemsdesc.add(new ContextSpecKeyedItem(rawSpec, item.getPropertyNames(), item.getStreamName()));
            }
            List<ContextSpecConditionFilter> optionalInit = null;
            if (seg.getInitiationConditions() != null && !seg.getInitiationConditions().isEmpty()) {
                optionalInit = new ArrayList<>();
                for (ContextDescriptorConditionFilter filter : seg.getInitiationConditions()) {
                    optionalInit.add((ContextSpecConditionFilter) mapCreateContextRangeCondition(filter, mapContext));
                }
            }
            ContextSpecCondition optionalTermination = null;
            if (seg.getTerminationCondition() != null) {
                optionalTermination = mapCreateContextRangeCondition(seg.getTerminationCondition(), mapContext);
            }
            detail = new ContextSpecKeyed(itemsdesc, optionalInit, optionalTermination);
        } else if (descriptor instanceof ContextDescriptorCategory) {
            ContextDescriptorCategory cat = (ContextDescriptorCategory) descriptor;
            FilterSpecRaw rawSpec = mapFilter(cat.getFilter(), mapContext);
            List<ContextSpecCategoryItem> itemsdesc = new ArrayList<>();
            for (ContextDescriptorCategoryItem item : cat.getItems()) {
                ExprNode expr = mapExpressionDeep(item.getExpression(), mapContext);
                itemsdesc.add(new ContextSpecCategoryItem(expr, item.getLabel()));
            }
            detail = new ContextSpecCategory(itemsdesc, rawSpec);
        } else if (descriptor instanceof ContextDescriptorHashSegmented) {
            ContextDescriptorHashSegmented hash = (ContextDescriptorHashSegmented) descriptor;
            List<ContextSpecHashItem> itemsdesc = new ArrayList<>();
            for (ContextDescriptorHashSegmentedItem item : hash.getItems()) {
                FilterSpecRaw rawSpec = mapFilter(item.getFilter(), mapContext);
                SingleRowMethodExpression singleRowMethodExpression = (SingleRowMethodExpression) item.getHashFunction();
                Chainable func = mapChains(Collections.singletonList(singleRowMethodExpression.getChain().get(0)), mapContext).get(0);
                itemsdesc.add(new ContextSpecHashItem(func, rawSpec));
            }
            detail = new ContextSpecHash(itemsdesc, hash.getGranularity(), hash.isPreallocate());
        } else {
            ContextDescriptorNested nested = (ContextDescriptorNested) descriptor;
            List<CreateContextDesc> itemsdesc = new ArrayList<>();
            for (CreateContextClause item : nested.getContexts()) {
                itemsdesc.add(new CreateContextDesc(item.getContextName(), mapCreateContextDetail(item.getDescriptor(), mapContext)));
            }
            detail = new ContextNested(itemsdesc);
        }
        return detail;
    }

    private static ContextSpecCondition mapCreateContextRangeCondition(ContextDescriptorCondition condition, StatementSpecMapContext mapContext) {
        if (condition instanceof ContextDescriptorConditionCrontab) {
            ContextDescriptorConditionCrontab crontab = (ContextDescriptorConditionCrontab) condition;
            List<List<ExprNode>> expr = new ArrayList<>(crontab.getCrontabExpressions().size());
            for (List<Expression> crontabItem : crontab.getCrontabExpressions()) {
                expr.add(mapExpressionDeep(crontabItem, mapContext));
            }
            return new ContextSpecConditionCrontab(expr, crontab.isNow());
        } else if (condition instanceof ContextDescriptorConditionFilter) {
            ContextDescriptorConditionFilter filter = (ContextDescriptorConditionFilter) condition;
            FilterSpecRaw filterExpr = mapFilter(filter.getFilter(), mapContext);
            return new ContextSpecConditionFilter(filterExpr, filter.getOptionalAsName());
        }
        if (condition instanceof ContextDescriptorConditionPattern) {
            ContextDescriptorConditionPattern pattern = (ContextDescriptorConditionPattern) condition;
            EvalForgeNode patternExpr = mapPatternEvalDeep(pattern.getPattern(), mapContext);
            return new ContextSpecConditionPattern(patternExpr, pattern.isInclusive(), pattern.isNow(), pattern.getAsName());
        }
        if (condition instanceof ContextDescriptorConditionTimePeriod) {
            ContextDescriptorConditionTimePeriod timePeriod = (ContextDescriptorConditionTimePeriod) condition;
            ExprNode expr = mapExpressionDeep(timePeriod.getTimePeriod(), mapContext);
            return new ContextSpecConditionTimePeriod((ExprTimePeriod) expr, timePeriod.isNow());
        }
        if (condition instanceof ContextDescriptorConditionImmediate) {
            return ContextSpecConditionImmediate.INSTANCE;
        }
        if (condition instanceof ContextDescriptorConditionNever) {
            return ContextSpecConditionNever.INSTANCE;
        }
        throw new IllegalStateException("Unrecognized condition " + condition);
    }

    private static void mapCreateWindow(CreateWindowClause createWindow, StatementSpecRaw raw, StatementSpecMapContext mapContext) {
        if (createWindow == null) {
            return;
        }

        ExprNode insertFromWhereExpr = null;
        if (createWindow.getInsertWhereClause() != null) {
            insertFromWhereExpr = mapExpressionDeep(createWindow.getInsertWhereClause(), mapContext);
        }
        List<ColumnDesc> columns = mapColumns(createWindow.getColumns());
        raw.setCreateWindowDesc(new CreateWindowDesc(createWindow.getWindowName(), mapViews(createWindow.getViews(), mapContext), new StreamSpecOptions(false, createWindow.isRetainUnion(), false), createWindow.isInsert(), insertFromWhereExpr, columns, createWindow.getAsEventTypeName()));
    }

    private static void mapCreateIndex(CreateIndexClause clause, StatementSpecRaw raw, StatementSpecMapContext mapContext) {
        if (clause == null) {
            return;
        }

        List<CreateIndexItem> cols = new ArrayList<>();
        for (CreateIndexColumn col : clause.getColumns()) {
            CreateIndexItem item = mapCreateIndexCol(col, mapContext);
            cols.add(item);
        }

        CreateIndexDesc desc = new CreateIndexDesc(clause.isUnique(), clause.getIndexName(), clause.getWindowName(), cols);
        raw.setCreateIndexDesc(desc);
    }

    private static CreateIndexItem mapCreateIndexCol(CreateIndexColumn col, StatementSpecMapContext mapContext) {
        List<ExprNode> columns = mapExpressionDeep(col.getColumns(), mapContext);
        List<ExprNode> parameters = mapExpressionDeep(col.getParameters(), mapContext);
        return new CreateIndexItem(columns, col.getType() == null ? CreateIndexType.HASH.getNameLower() : col.getType(), parameters);
    }

    private static void mapUpdateClause(UpdateClause updateClause, StatementSpecRaw raw, StatementSpecMapContext mapContext) {
        if (updateClause == null) {
            return;
        }
        List<OnTriggerSetAssignment> assignments = new ArrayList<>();
        for (Assignment pair : updateClause.getAssignments()) {
            ExprNode expr = mapExpressionDeep(pair.getValue(), mapContext);
            assignments.add(new OnTriggerSetAssignment(expr));
        }
        ExprNode whereClause = null;
        if (updateClause.getOptionalWhereClause() != null) {
            whereClause = mapExpressionDeep(updateClause.getOptionalWhereClause(), mapContext);
        }
        UpdateDesc desc = new UpdateDesc(updateClause.getOptionalAsClauseStreamName(), assignments, whereClause);
        raw.setUpdateDesc(desc);
        FilterSpecRaw filterSpecRaw = new FilterSpecRaw(updateClause.getEventType(), Collections.emptyList(), null);
        raw.getStreamSpecs().add(new FilterStreamSpecRaw(filterSpecRaw, ViewSpec.EMPTY_VIEWSPEC_ARRAY, null, StreamSpecOptions.DEFAULT));
    }

    private static void mapCreateVariable(CreateVariableClause createVariable, StatementSpecRaw raw, StatementSpecMapContext mapContext) {
        if (createVariable == null) {
            return;
        }

        ExprNode assignment = null;
        if (createVariable.getOptionalAssignment() != null) {
            assignment = mapExpressionDeep(createVariable.getOptionalAssignment(), mapContext);
        }
        ClassDescriptor type = ClassDescriptor.parseTypeText(createVariable.getVariableType());
        raw.setCreateVariableDesc(new CreateVariableDesc(type, createVariable.getVariableName(), assignment, createVariable.isConstant()));
    }

    private static void mapCreateTable(CreateTableClause createTable, StatementSpecRaw raw, StatementSpecMapContext mapContext) {
        if (createTable == null) {
            return;
        }

        List<CreateTableColumn> cols = new ArrayList<>();
        for (com.espertech.esper.common.client.soda.CreateTableColumn desc : createTable.getColumns()) {
            ExprNode optNode = desc.getOptionalExpression() != null ? mapExpressionDeep(desc.getOptionalExpression(), mapContext) : null;
            List<AnnotationDesc> annotations = mapAnnotations(desc.getAnnotations());
            ClassDescriptor ident = desc.getOptionalTypeName() == null ? null : ClassDescriptor.parseTypeText(desc.getOptionalTypeName());
            cols.add(new CreateTableColumn(desc.getColumnName(), optNode, ident, annotations, desc.getPrimaryKey()));
        }

        CreateTableDesc agg = new CreateTableDesc(createTable.getTableName(), cols);
        raw.setCreateTableDesc(agg);
    }

    private static void mapCreateSchema(CreateSchemaClause clause, StatementSpecRaw raw, StatementSpecMapContext mapContext) {
        if (clause == null) {
            return;
        }
        CreateSchemaDesc desc = mapCreateSchemaInternal(clause);
        raw.setCreateSchemaDesc(desc);
    }

    private static void mapCreateExpression(CreateExpressionClause clause, StatementSpecRaw raw, StatementSpecMapContext mapContext) {
        if (clause == null) {
            return;
        }

        CreateExpressionDesc desc;
        if (clause.getExpressionDeclaration() != null) {
            ExpressionDeclItem item = mapExpressionDeclItem(clause.getExpressionDeclaration(), mapContext);
            desc = new CreateExpressionDesc(item);
        } else {
            ExpressionScriptProvided item = mapScriptExpression(clause.getScriptExpression(), mapContext);
            desc = new CreateExpressionDesc(item);
        }
        raw.setCreateExpressionDesc(desc);
    }

    private static void mapCreateClass(CreateClassClause clause, StatementSpecRaw raw, StatementSpecMapContext mapContext) {
        if (clause == null) {
            return;
        }
        raw.setCreateClassProvided(clause.getClassProvidedExpression().getClassText());
    }

    private static CreateSchemaDesc mapCreateSchemaInternal(CreateSchemaClause clause) {
        List<ColumnDesc> columns = mapColumns(clause.getColumns());
        return new CreateSchemaDesc(clause.getSchemaName(), clause.getTypes(), columns, clause.getInherits(), CreateSchemaDesc.AssignedType.mapFrom(clause.getTypeDefinition()), clause.getStartTimestampPropertyName(), clause.getEndTimestampPropertyName(), clause.getCopyFrom());
    }

    private static void mapCreateGraph(CreateDataFlowClause clause, StatementSpecRaw raw, StatementSpecMapContext mapContext) {
        if (clause == null) {
            return;
        }

        List<CreateSchemaDesc> schemas = new ArrayList<>();
        for (CreateSchemaClause schema : clause.getSchemas()) {
            schemas.add(mapCreateSchemaInternal(schema));
        }

        List<GraphOperatorSpec> ops = new ArrayList<>();
        for (DataFlowOperator op : clause.getOperators()) {
            ops.add(mapGraphOperator(op, mapContext));
        }

        CreateDataFlowDesc desc = new CreateDataFlowDesc(clause.getDataFlowName(), ops, schemas);
        raw.setCreateDataFlowDesc(desc);
    }

    private static GraphOperatorSpec mapGraphOperator(DataFlowOperator op, StatementSpecMapContext mapContext) {
        List<AnnotationDesc> annotations = mapAnnotations(op.getAnnotations());

        GraphOperatorInput input = new GraphOperatorInput();
        for (DataFlowOperatorInput in : op.getInput()) {
            input.getStreamNamesAndAliases().add(new GraphOperatorInputNamesAlias(in.getInputStreamNames().toArray(new String[0]), in.getOptionalAsName()));
        }

        GraphOperatorOutput output = new GraphOperatorOutput();
        for (DataFlowOperatorOutput out : op.getOutput()) {
            output.getItems().add(new GraphOperatorOutputItem(out.getStreamName(), mapGraphOpType(out.getTypeInfo())));
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        for (DataFlowOperatorParameter entry : op.getParameters()) {
            Object value = entry.getParameterValue();
            if (value instanceof EPStatementObjectModel) {
                value = map((EPStatementObjectModel) value, mapContext);
            } else if (value instanceof Expression) {
                value = mapExpressionDeep((Expression) value, mapContext);
            }
            detail.put(entry.getParameterName(), value);
        }

        return new GraphOperatorSpec(op.getOperatorName(), input, output, new GraphOperatorDetail(detail), annotations);
    }

    private static List<GraphOperatorOutputItemType> mapGraphOpType(List<DataFlowOperatorOutputType> typeInfos) {
        if (typeInfos == null) {
            return Collections.emptyList();
        }
        List<GraphOperatorOutputItemType> types = new ArrayList<>();
        for (DataFlowOperatorOutputType info : typeInfos) {
            GraphOperatorOutputItemType type = new GraphOperatorOutputItemType(info.isWildcard(), info.getTypeOrClassname(), mapGraphOpType(info.getTypeParameters()));
            types.add(type);
        }
        return types;
    }

    private static List<ColumnDesc> mapColumns(List<SchemaColumnDesc> columns) {
        if (columns == null) {
            return null;
        }
        List<ColumnDesc> result = new ArrayList<>();
        for (SchemaColumnDesc col : columns) {
            result.add(new ColumnDesc(col.getName(), col.getType()));
        }
        return result;
    }

    private static List<SchemaColumnDesc> unmapColumns(List<ColumnDesc> columns) {
        if (columns == null) {
            return null;
        }
        List<SchemaColumnDesc> result = new ArrayList<>();
        for (ColumnDesc col : columns) {
            result.add(new SchemaColumnDesc(col.getName(), col.getType()));
        }
        return result;
    }

    private static InsertIntoDesc mapInsertInto(InsertIntoClause insertInto, StatementSpecMapContext mapContext) {
        if (insertInto == null) {
            return null;
        }

        String eventTypeName = insertInto.getStreamName();
        InsertIntoDesc desc = new InsertIntoDesc(mapFromSODA(insertInto.getStreamSelector()), eventTypeName);

        for (String name : insertInto.getColumnNames()) {
            desc.add(name);
        }

        desc.setEventPrecedence(mapExpressionDeep(insertInto.getEventPrecedence(), mapContext));
        return desc;
    }

    private static void mapSelect(SelectClause selectClause, StatementSpecRaw raw, StatementSpecMapContext mapContext) {
        if (selectClause == null) {
            return;
        }
        SelectClauseSpecRaw spec = mapSelectRaw(selectClause, mapContext);
        raw.setSelectStreamDirEnum(mapFromSODA(selectClause.getStreamSelector()));
        raw.setSelectClauseSpec(spec);
    }

    private static List<SelectClauseElementRaw> mapSelectClauseElements(List<SelectClauseElement> elements, StatementSpecMapContext mapContext) {
        List<SelectClauseElementRaw> result = new ArrayList<>();
        for (SelectClauseElement element : elements) {
            if (element instanceof SelectClauseWildcard) {
                result.add(new SelectClauseElementWildcard());
            } else if (element instanceof SelectClauseExpression) {
                SelectClauseExpression selectExpr = (SelectClauseExpression) element;
                Expression expr = selectExpr.getExpression();
                ExprNode exprNode = mapExpressionDeep(expr, mapContext);
                SelectClauseExprRawSpec rawElement = new SelectClauseExprRawSpec(exprNode, selectExpr.getAsName(), selectExpr.isAnnotatedByEventFlag());
                result.add(rawElement);
            } else if (element instanceof SelectClauseStreamWildcard) {
                SelectClauseStreamWildcard streamWild = (SelectClauseStreamWildcard) element;
                SelectClauseStreamRawSpec rawElement = new SelectClauseStreamRawSpec(streamWild.getStreamName(), streamWild.getOptionalColumnName());
                result.add(rawElement);
            }
        }
        return result;
    }

    private static SelectClauseSpecRaw mapSelectRaw(SelectClause selectClause, StatementSpecMapContext mapContext) {
        SelectClauseSpecRaw spec = new SelectClauseSpecRaw();
        spec.addAll(mapSelectClauseElements(selectClause.getSelectList(), mapContext));
        spec.setDistinct(selectClause.isDistinct());
        return spec;
    }

    private static Expression unmapExpressionDeep(ExprNode exprNode, StatementSpecUnMapContext unmapContext) {
        if (exprNode == null) {
            return null;
        }
        Expression parent = unmapExpressionFlat(exprNode, unmapContext);
        unmapExpressionRecursive(parent, exprNode, unmapContext);
        return parent;
    }

    private static List<ExprNode> mapExpressionDeep(List<Expression> expressions, StatementSpecMapContext mapContext) {
        List<ExprNode> result = new ArrayList<>();
        if (expressions == null) {
            return result;
        }
        for (Expression expr : expressions) {
            if (expr == null) {
                result.add(null);
                continue;
            }
            result.add(mapExpressionDeep(expr, mapContext));
        }
        return result;
    }

    private static MatchRecognizeRegEx unmapExpressionDeepRowRecog(RowRecogExprNode exprNode, StatementSpecUnMapContext unmapContext) {
        MatchRecognizeRegEx parent = unmapExpressionFlatRowRecog(exprNode, unmapContext);
        unmapExpressionRecursiveRowRecog(parent, exprNode, unmapContext);
        return parent;
    }

    private static ExprNode mapExpressionDeep(Expression expr, StatementSpecMapContext mapContext) {
        if (expr == null) {
            return null;
        }
        ExprNode parent = mapExpressionFlat(expr, mapContext);
        mapExpressionRecursive(parent, expr, mapContext);
        return parent;
    }

    private static RowRecogExprNode mapExpressionDeepRowRecog(MatchRecognizeRegEx expr, StatementSpecMapContext mapContext) {
        RowRecogExprNode parent = mapExpressionFlatRowRecog(expr, mapContext);
        mapExpressionRecursiveRowRecog(parent, expr, mapContext);
        return parent;
    }

    private static ExprNode mapExpressionFlat(Expression expr, StatementSpecMapContext mapContext) {
        if (expr == null) {
            throw new IllegalArgumentException("Null expression parameter");
        }
        if (expr instanceof ArithmaticExpression) {
            ArithmaticExpression arith = (ArithmaticExpression) expr;
            return new ExprMathNode(MathArithTypeEnum.parseOperator(arith.getOperator()),
                mapContext.getConfiguration().getCompiler().getExpression().isIntegerDivision(),
                mapContext.getConfiguration().getCompiler().getExpression().isDivisionByZeroReturnsNull());
        } else if (expr instanceof PropertyValueExpression) {
            PropertyValueExpression prop = (PropertyValueExpression) expr;
            int indexDot = StringValue.unescapedIndexOfDot(prop.getPropertyName());

            // handle without nesting
            if (indexDot == -1) {

                // properties and also indexed properties can be either just a property of may reference a table
                ExprTableAccessNode tableAccessNode = TableCompileTimeUtil.mapPropertyToTableUnnested(prop.getPropertyName(), mapContext.getTableCompileTimeResolver());
                if (tableAccessNode != null) {
                    mapContext.getTableExpressions().add(tableAccessNode);
                    return tableAccessNode;
                }

                // maybe variable
                VariableMetaData variableMetaData = mapContext.getVariableCompileTimeResolver().resolve(prop.getPropertyName());
                if (variableMetaData != null) {
                    ExprVariableNodeImpl node = new ExprVariableNodeImpl(variableMetaData, null);
                    mapContext.getVariableNames().add(variableMetaData.getVariableName());
                    String message = VariableUtil.checkVariableContextName(mapContext.getContextName(), variableMetaData);
                    if (message != null) {
                        throw new EPException(message);
                    }
                    return node;
                }

                return new ExprIdentNodeImpl(prop.getPropertyName());
            }

            String stream = prop.getPropertyName().substring(0, indexDot);
            String property = prop.getPropertyName().substring(indexDot + 1);

            Pair<ExprTableAccessNode, ExprDotNode> tableNode = TableCompileTimeUtil.mapPropertyToTableNested(mapContext.getTableCompileTimeResolver(), stream, property);
            if (tableNode != null) {
                mapContext.getTableExpressions().add(tableNode.getFirst());
                return tableNode.getFirst();
            }

            VariableMetaData variableMetaData = mapContext.getVariableCompileTimeResolver().resolve(stream);
            if (variableMetaData != null) {
                ExprVariableNodeImpl node = new ExprVariableNodeImpl(variableMetaData, property);
                mapContext.getVariableNames().add(variableMetaData.getVariableName());
                String message = VariableUtil.checkVariableContextName(mapContext.getContextName(), variableMetaData);
                if (message != null) {
                    throw new EPException(message);
                }
                return node;
            }

            if (mapContext.getContextName() != null) {
                ContextCompileTimeDescriptor contextDescriptor = mapContext.getContextCompileTimeDescriptor();
                if (contextDescriptor != null && contextDescriptor.getContextPropertyRegistry().isContextPropertyPrefix(stream)) {
                    return new ExprContextPropertyNodeImpl(property);
                }
            }

            return new ExprIdentNodeImpl(property, stream);
        } else if (expr instanceof Conjunction) {
            return new ExprAndNodeImpl();
        } else if (expr instanceof Disjunction) {
            return new ExprOrNode();
        } else if (expr instanceof RelationalOpExpression) {
            RelationalOpExpression op = (RelationalOpExpression) expr;
            if (op.getOperator().equals("=")) {
                return new ExprEqualsNodeImpl(false, false);
            } else if (op.getOperator().equals("!=")) {
                return new ExprEqualsNodeImpl(true, false);
            } else if (op.getOperator().toUpperCase(Locale.ENGLISH).trim().equals("IS")) {
                return new ExprEqualsNodeImpl(false, true);
            } else if (op.getOperator().toUpperCase(Locale.ENGLISH).trim().equals("IS NOT")) {
                return new ExprEqualsNodeImpl(true, true);
            } else {
                return new ExprRelationalOpNodeImpl(RelationalOpEnum.parse(op.getOperator()));
            }
        } else if (expr instanceof ConstantExpression) {
            ConstantExpression op = (ConstantExpression) expr;
            Class<?> constantType = null;
            if (op.getConstantType() != null) {
                try {
                    constantType = mapContext.getClasspathImportService().getClassForNameProvider().classForName(op.getConstantType());
                } catch (ClassNotFoundException e) {
                    constantType = JavaClassHelper.getPrimitiveClassForName(op.getConstantType());
                    if (constantType == null) {
                        throw new EPException("Error looking up class name '" + op.getConstantType() + "' to resolve as constant type");
                    }
                }
            }
            if (!CodegenExpressionUtil.canRenderConstant(op.getConstant())) {
                throw new EPException("Invalid constant of type '" + ClassHelperPrint.getClassNameFullyQualPretty(op.getConstant().getClass()) + "' encountered as the class has no compiler representation, please use substitution parameters instead");
            }
            return new ExprConstantNodeImpl(op.getConstant(), constantType);
        } else if (expr instanceof ConcatExpression) {
            return new ExprConcatNode();
        } else if (expr instanceof SubqueryExpression) {
            SubqueryExpression sub = (SubqueryExpression) expr;
            StatementSpecRaw rawSubselect = map(sub.getModel(), mapContext);
            return new ExprSubselectRowNode(rawSubselect);
        } else if (expr instanceof SubqueryInExpression) {
            SubqueryInExpression sub = (SubqueryInExpression) expr;
            StatementSpecRaw rawSubselect = map(sub.getModel(), mapContext);
            return new ExprSubselectInNode(rawSubselect, sub.isNotIn());
        } else if (expr instanceof SubqueryExistsExpression) {
            SubqueryExistsExpression sub = (SubqueryExistsExpression) expr;
            StatementSpecRaw rawSubselect = map(sub.getModel(), mapContext);
            return new ExprSubselectExistsNode(rawSubselect);
        } else if (expr instanceof SubqueryQualifiedExpression) {
            SubqueryQualifiedExpression sub = (SubqueryQualifiedExpression) expr;
            StatementSpecRaw rawSubselect = map(sub.getModel(), mapContext);
            boolean isNot = false;
            RelationalOpEnum relop = null;
            if (sub.getOperator().equals("!=")) {
                isNot = true;
            }
            if (!sub.getOperator().equals("=")) {
                relop = RelationalOpEnum.parse(sub.getOperator());
            }
            return new ExprSubselectAllSomeAnyNode(rawSubselect, isNot, sub.isAll(), relop);
        } else if (expr instanceof CountStarProjectionExpression) {
            return new ExprCountNode(false);
        } else if (expr instanceof CountProjectionExpression) {
            CountProjectionExpression count = (CountProjectionExpression) expr;
            return new ExprCountNode(count.isDistinct());
        } else if (expr instanceof AvgProjectionExpression) {
            AvgProjectionExpression avg = (AvgProjectionExpression) expr;
            return new ExprAvgNode(avg.isDistinct());
        } else if (expr instanceof SumProjectionExpression) {
            SumProjectionExpression avg = (SumProjectionExpression) expr;
            return new ExprSumNode(avg.isDistinct());
        } else if (expr instanceof BetweenExpression) {
            BetweenExpression between = (BetweenExpression) expr;
            return new ExprBetweenNodeImpl(between.isLowEndpointIncluded(), between.isHighEndpointIncluded(), between.isNotBetween());
        } else if (expr instanceof PriorExpression) {
            mapContext.setHasPriorExpression();
            return new ExprPriorNode();
        } else if (expr instanceof PreviousExpression) {
            PreviousExpression prev = (PreviousExpression) expr;
            return new ExprPreviousNode(ExprPreviousNodePreviousType.valueOf(prev.getType().toString()));
        } else if (expr instanceof StaticMethodExpression) {
            StaticMethodExpression method = (StaticMethodExpression) expr;
            List<Chainable> chained = mapChains(method.getChain(), mapContext);
            chained.add(0, new ChainableCall(method.getClassName(), Collections.emptyList()));
            return new ExprDotNodeImpl(chained,
                mapContext.getConfiguration().getCompiler().getExpression().isDuckTyping(),
                mapContext.getConfiguration().getCompiler().getExpression().isUdfCache());
        } else if (expr instanceof MinProjectionExpression) {
            MinProjectionExpression method = (MinProjectionExpression) expr;
            return new ExprMinMaxAggrNode(method.isDistinct(), MinMaxTypeEnum.MIN, expr.getChildren().size() > 1, method.isEver());
        } else if (expr instanceof MaxProjectionExpression) {
            MaxProjectionExpression method = (MaxProjectionExpression) expr;
            return new ExprMinMaxAggrNode(method.isDistinct(), MinMaxTypeEnum.MAX, expr.getChildren().size() > 1, method.isEver());
        } else if (expr instanceof NotExpression) {
            return new ExprNotNode();
        } else if (expr instanceof InExpression) {
            InExpression inExpr = (InExpression) expr;
            return new ExprInNodeImpl(inExpr.isNotIn());
        } else if (expr instanceof CoalesceExpression) {
            return new ExprCoalesceNode();
        } else if (expr instanceof CaseWhenThenExpression) {
            return new ExprCaseNode(false);
        } else if (expr instanceof CaseSwitchExpression) {
            return new ExprCaseNode(true);
        } else if (expr instanceof MaxRowExpression) {
            return new ExprMinMaxRowNode(MinMaxTypeEnum.MAX);
        } else if (expr instanceof MinRowExpression) {
            return new ExprMinMaxRowNode(MinMaxTypeEnum.MIN);
        } else if (expr instanceof BitwiseOpExpression) {
            BitwiseOpExpression bit = (BitwiseOpExpression) expr;
            return new ExprBitWiseNode(bit.getBinaryOp());
        } else if (expr instanceof ArrayExpression) {
            return new ExprArrayNode();
        } else if (expr instanceof LikeExpression) {
            LikeExpression like = (LikeExpression) expr;
            return new ExprLikeNode(like.isNot());
        } else if (expr instanceof RegExpExpression) {
            RegExpExpression regexp = (RegExpExpression) expr;
            return new ExprRegexpNode(regexp.isNot());
        } else if (expr instanceof MedianProjectionExpression) {
            MedianProjectionExpression median = (MedianProjectionExpression) expr;
            return new ExprMedianNode(median.isDistinct());
        } else if (expr instanceof AvedevProjectionExpression) {
            AvedevProjectionExpression node = (AvedevProjectionExpression) expr;
            return new ExprAvedevNode(node.isDistinct());
        } else if (expr instanceof StddevProjectionExpression) {
            StddevProjectionExpression node = (StddevProjectionExpression) expr;
            return new ExprStddevNode(node.isDistinct());
        } else if (expr instanceof LastEverProjectionExpression) {
            LastEverProjectionExpression node = (LastEverProjectionExpression) expr;
            return new ExprFirstLastEverNode(node.isDistinct(), false);
        } else if (expr instanceof FirstEverProjectionExpression) {
            FirstEverProjectionExpression node = (FirstEverProjectionExpression) expr;
            return new ExprFirstLastEverNode(node.isDistinct(), true);
        } else if (expr instanceof CountEverProjectionExpression) {
            CountEverProjectionExpression node = (CountEverProjectionExpression) expr;
            return new ExprCountEverNode(node.isDistinct());
        } else if (expr instanceof InstanceOfExpression) {
            InstanceOfExpression node = (InstanceOfExpression) expr;
            return new ExprInstanceofNode(node.getTypeNames());
        } else if (expr instanceof TypeOfExpression) {
            return new ExprTypeofNode();
        } else if (expr instanceof CastExpression) {
            CastExpression node = (CastExpression) expr;
            return new ExprCastNode(ClassDescriptor.parseTypeText(node.getTypeName()));
        } else if (expr instanceof PropertyExistsExpression) {
            return new ExprPropertyExistsNode();
        } else if (expr instanceof CurrentTimestampExpression) {
            return new ExprTimestampNode();
        } else if (expr instanceof CurrentEvaluationContextExpression) {
            return new ExprCurrentEvaluationContextNode();
        } else if (expr instanceof IStreamBuiltinExpression) {
            return new ExprIStreamNode();
        } else if (expr instanceof TimePeriodExpression) {
            TimePeriodExpression tpe = (TimePeriodExpression) expr;
            return new ExprTimePeriodImpl(tpe.isHasYears(), tpe.isHasMonths(), tpe.isHasWeeks(), tpe.isHasDays(), tpe.isHasHours(), tpe.isHasMinutes(), tpe.isHasSeconds(), tpe.isHasMilliseconds(), tpe.isHasMicroseconds(),
                mapContext.getClasspathImportService().getTimeAbacus());
        } else if (expr instanceof NewOperatorExpression) {
            NewOperatorExpression noe = (NewOperatorExpression) expr;
            return new ExprNewStructNode(noe.getColumnNames().toArray(new String[0]));
        } else if (expr instanceof NewInstanceOperatorExpression) {
            NewInstanceOperatorExpression noe = (NewInstanceOperatorExpression) expr;
            ClassDescriptor type = ClassDescriptor.parseTypeText(noe.getClassName());
            return new ExprNewInstanceNode(type, noe.getNumArrayDimensions());
        } else if (expr instanceof CompareListExpression) {
            CompareListExpression exp = (CompareListExpression) expr;
            if ((exp.getOperator().equals("=")) || (exp.getOperator().equals("!="))) {
                return new ExprEqualsAllAnyNode(exp.getOperator().equals("!="), exp.isAll());
            } else {
                return new ExprRelationalOpAllAnyNode(RelationalOpEnum.parse(exp.getOperator()), exp.isAll());
            }
        } else if (expr instanceof SubstitutionParameterExpression) {
            SubstitutionParameterExpression node = (SubstitutionParameterExpression) expr;
            ClassDescriptor ident = node.getOptionalType() == null ? null : ClassDescriptor.parseTypeText(node.getOptionalType());
            ExprSubstitutionNode substitutionNode = new ExprSubstitutionNode(node.getOptionalName(), ident);
            mapContext.getSubstitutionNodes().add(substitutionNode);
            return substitutionNode;
        } else if (expr instanceof SingleRowMethodExpression) {
            SingleRowMethodExpression single = (SingleRowMethodExpression) expr;
            if ((single.getChain() == null) || (single.getChain().size() == 0)) {
                throw new IllegalArgumentException("Single row method expression requires one or more method calls");
            }
            List<Chainable> chain = mapChains(single.getChain(), mapContext);
            ChainableCall call = (ChainableCall) chain.get(0);
            String functionName = call.getName();

            Pair<Class, ClasspathImportSingleRowDesc> pair;
            try {
                pair = mapContext.getClasspathImportService().resolveSingleRow(functionName, mapContext.getClassProvidedClasspathExtension());
            } catch (Exception e) {
                throw new IllegalArgumentException("Function name '" + functionName + "' cannot be resolved to a single-row function: " + e.getMessage(), e);
            }
            call.setName(pair.getSecond().getMethodName());
            return new ExprPlugInSingleRowNode(functionName, pair.getFirst(), chain, pair.getSecond());
        } else if (expr instanceof PlugInProjectionExpression) {
            PlugInProjectionExpression node = (PlugInProjectionExpression) expr;
            ExprNode exprNode = ASTAggregationHelper.tryResolveAsAggregation(mapContext.getClasspathImportService(), node.isDistinct(), node.getFunctionName(), mapContext.getPlugInAggregations(), mapContext.getClassProvidedClasspathExtension());
            if (exprNode == null) {
                throw new EPException("Error resolving aggregation function named '" + node.getFunctionName() + "'");
            }
            return exprNode;
        } else if (expr instanceof OrderedObjectParamExpression) {
            OrderedObjectParamExpression order = (OrderedObjectParamExpression) expr;
            return new ExprOrderedExpr(order.isDescending());
        } else if (expr instanceof CrontabFrequencyExpression) {
            return new ExprNumberSetFrequency();
        } else if (expr instanceof CrontabRangeExpression) {
            return new ExprNumberSetRange();
        } else if (expr instanceof CrontabParameterSetExpression) {
            return new ExprNumberSetList();
        } else if (expr instanceof CrontabParameterExpression) {
            CrontabParameterExpression cronParam = (CrontabParameterExpression) expr;
            if (cronParam.getType() == ScheduleItemType.WILDCARD) {
                return new ExprWildcardImpl();
            }
            CronOperatorEnum operator;
            if (cronParam.getType() == ScheduleItemType.LASTDAY) {
                operator = CronOperatorEnum.LASTDAY;
            } else if (cronParam.getType() == ScheduleItemType.WEEKDAY) {
                operator = CronOperatorEnum.WEEKDAY;
            } else if (cronParam.getType() == ScheduleItemType.LASTWEEKDAY) {
                operator = CronOperatorEnum.LASTWEEKDAY;
            } else {
                throw new IllegalArgumentException("Cron parameter not recognized: " + cronParam.getType());
            }
            return new ExprNumberSetCronParam(operator);
        } else if (expr instanceof AccessProjectionExpressionBase) {
            AggregationAccessorLinearType type;
            if (expr instanceof FirstProjectionExpression) {
                type = AggregationAccessorLinearType.FIRST;
            } else if (expr instanceof LastProjectionExpression) {
                type = AggregationAccessorLinearType.LAST;
            } else {
                type = AggregationAccessorLinearType.WINDOW;
            }
            return new ExprAggMultiFunctionLinearAccessNode(type);
        } else if (expr instanceof DotExpression) {
            DotExpression theBase = (DotExpression) expr;
            // the first chain element may itself be nested:
            //   chain.get(0)="table.a" (looks like a class name)
            //   chain.get(1)="doit()"
            List<Chainable> chain = mapChains(theBase.getChain(), mapContext);
            if (!chain.isEmpty() && Chainable.isPlainPropertyChain(chain.get(0))) {
                List<Chainable> elementized = Chainable.chainForDot(chain.get(0));
                elementized.addAll(chain.subList(1, chain.size()));
                chain = elementized;
            }
            return ChainableWalkHelper.processDot(true, expr.getChildren().isEmpty(), chain, mapContext);
        } else if (expr instanceof LambdaExpression) {
            LambdaExpression theBase = (LambdaExpression) expr;
            return new ExprLambdaGoesNode(new ArrayList<>(theBase.getParameters()));
        } else if (expr instanceof StreamWildcardExpression) {
            StreamWildcardExpression sw = (StreamWildcardExpression) expr;
            return new ExprStreamUnderlyingNodeImpl(sw.getStreamName(), true);
        } else if (expr instanceof GroupingExpression) {
            return new ExprGroupingNode();
        } else if (expr instanceof GroupingIdExpression) {
            return new ExprGroupingIdNode();
        } else if (expr instanceof TableAccessExpression) {
            TableAccessExpression b = (TableAccessExpression) expr;
            TableMetaData table = mapContext.getTableCompileTimeResolver().resolve(b.getTableName());
            if (table == null) {
                throw new IllegalArgumentException("Failed to find table by name '" + b.getTableName() + "'");
            }
            ExprTableAccessNode tableNode;
            String tableName = table.getTableName();
            if (b.getOptionalColumn() != null) {
                tableNode = new ExprTableAccessNodeSubprop(tableName, b.getOptionalColumn());
            } else {
                tableNode = new ExprTableAccessNodeTopLevel(tableName);
            }
            mapContext.getTableExpressions().add(tableNode);
            return tableNode;
        } else if (expr instanceof WildcardExpression) {
            return new ExprWildcardImpl();
        } else if (expr instanceof NamedParameterExpression) {
            NamedParameterExpression named = (NamedParameterExpression) expr;
            return new ExprNamedParameterNodeImpl(named.getName());
        }
        throw new IllegalArgumentException("Could not map expression node of type " + expr.getClass().getSimpleName());
    }

    private static List<Expression> unmapExpressionDeep(ExprNode[] expressions, StatementSpecUnMapContext unmapContext) {
        return unmapExpressionDeep(Arrays.asList(expressions), unmapContext);
    }

    private static List<Expression> unmapExpressionDeep(List<ExprNode> expressions, StatementSpecUnMapContext unmapContext) {
        List<Expression> result = new ArrayList<>();
        if (expressions == null) {
            return result;
        }
        for (ExprNode expr : expressions) {
            if (expr == null) {
                result.add(null);
                continue;
            }
            result.add(unmapExpressionDeep(expr, unmapContext));
        }
        return result;
    }

    private static MatchRecognizeRegEx unmapExpressionFlatRowRecog(RowRecogExprNode expr, StatementSpecUnMapContext unmapContext) {
        if (expr instanceof RowRecogExprNodeAlteration) {
            return new MatchRecognizeRegExAlteration();
        } else if (expr instanceof RowRecogExprNodeAtom) {
            RowRecogExprNodeAtom atom = (RowRecogExprNodeAtom) expr;
            MatchRecognizeRegExRepeat repeat = unmapRowRecogRepeat(atom.getOptionalRepeat(), unmapContext);
            return new MatchRecognizeRegExAtom(atom.getTag(), MatchRecogizePatternElementType.values()[atom.getType().ordinal()], repeat);
        } else if (expr instanceof RowRecogExprNodeConcatenation) {
            return new MatchRecognizeRegExConcatenation();
        } else if (expr instanceof RowRecogExprNodePermute) {
            return new MatchRecognizeRegExPermutation();
        } else {
            RowRecogExprNodeNested nested = (RowRecogExprNodeNested) expr;
            MatchRecognizeRegExRepeat repeat = unmapRowRecogRepeat(nested.getOptionalRepeat(), unmapContext);
            return new MatchRecognizeRegExNested(MatchRecogizePatternElementType.values()[nested.getType().ordinal()], repeat);
        }
    }

    private static MatchRecognizeRegExRepeat unmapRowRecogRepeat(RowRecogExprRepeatDesc optionalRepeat, StatementSpecUnMapContext unmapContext) {
        if (optionalRepeat == null) {
            return null;
        }
        return new MatchRecognizeRegExRepeat(
            unmapExpressionDeep(optionalRepeat.getLower(), unmapContext),
            unmapExpressionDeep(optionalRepeat.getUpper(), unmapContext),
            unmapExpressionDeep(optionalRepeat.getSingle(), unmapContext)
        );
    }

    private static RowRecogExprNode mapExpressionFlatRowRecog(MatchRecognizeRegEx expr, StatementSpecMapContext mapContext) {
        if (expr instanceof MatchRecognizeRegExAlteration) {
            return new RowRecogExprNodeAlteration();
        } else if (expr instanceof MatchRecognizeRegExAtom) {
            MatchRecognizeRegExAtom atom = (MatchRecognizeRegExAtom) expr;
            RowRecogExprRepeatDesc repeat = mapRowRecogRepeat(atom.getOptionalRepeat(), mapContext);
            return new RowRecogExprNodeAtom(atom.getName(), RowRecogNFATypeEnum.values()[atom.getType().ordinal()], repeat);
        } else if (expr instanceof MatchRecognizeRegExConcatenation) {
            return new RowRecogExprNodeConcatenation();
        } else if (expr instanceof MatchRecognizeRegExPermutation) {
            return new RowRecogExprNodePermute();
        } else {
            MatchRecognizeRegExNested nested = (MatchRecognizeRegExNested) expr;
            RowRecogExprRepeatDesc repeat = mapRowRecogRepeat(nested.getOptionalRepeat(), mapContext);
            return new RowRecogExprNodeNested(RowRecogNFATypeEnum.values()[nested.getType().ordinal()], repeat);
        }
    }

    private static RowRecogExprRepeatDesc mapRowRecogRepeat(MatchRecognizeRegExRepeat optionalRepeat, StatementSpecMapContext mapContext) {
        if (optionalRepeat == null) {
            return null;
        }
        return new RowRecogExprRepeatDesc(
            mapExpressionDeep(optionalRepeat.getLow(), mapContext),
            mapExpressionDeep(optionalRepeat.getHigh(), mapContext),
            mapExpressionDeep(optionalRepeat.getSingle(), mapContext)
        );
    }

    private static Expression unmapExpressionFlat(ExprNode expr, StatementSpecUnMapContext unmapContext) {
        if (expr instanceof ExprIdentNode) {
            ExprIdentNode prop = (ExprIdentNode) expr;
            String propertyName = prop.getUnresolvedPropertyName();
            if (prop.getStreamOrPropertyName() != null) {
                propertyName = prop.getStreamOrPropertyName() + "." + prop.getUnresolvedPropertyName();
            }
            return new PropertyValueExpression(propertyName);
        } else if (expr instanceof ExprMathNode) {
            ExprMathNode math = (ExprMathNode) expr;
            return new ArithmaticExpression(math.getMathArithTypeEnum().getExpressionText());
        } else if (expr instanceof ExprVariableNode) {
            ExprVariableNode prop = (ExprVariableNode) expr;
            String propertyName = prop.getVariableNameWithSubProp();
            return new PropertyValueExpression(propertyName);
        } else if (expr instanceof ExprContextPropertyNodeImpl) {
            ExprContextPropertyNodeImpl prop = (ExprContextPropertyNodeImpl) expr;
            return new PropertyValueExpression(ContextPropertyRegistry.CONTEXT_PREFIX + "." + prop.getPropertyName());
        } else if (expr instanceof ExprEqualsNode) {
            ExprEqualsNode equals = (ExprEqualsNode) expr;
            String operator;
            if (!equals.isIs()) {
                operator = "=";
                if (equals.isNotEquals()) {
                    operator = "!=";
                }
            } else {
                operator = "is";
                if (equals.isNotEquals()) {
                    operator = "is not";
                }
            }
            return new RelationalOpExpression(operator);
        } else if (expr instanceof ExprRelationalOpNode) {
            ExprRelationalOpNode rel = (ExprRelationalOpNode) expr;
            return new RelationalOpExpression(rel.getRelationalOpEnum().getExpressionText());
        } else if (expr instanceof ExprAndNode) {
            return new Conjunction();
        } else if (expr instanceof ExprOrNode) {
            return new Disjunction();
        } else if (expr instanceof ExprConstantNodeImpl) {
            ExprConstantNodeImpl constNode = (ExprConstantNodeImpl) expr;
            String constantType = null;
            if (constNode.getConstantType() != null && constNode.getConstantType() != EPTypeNull.INSTANCE) {
                constantType = constNode.getConstantType().getTypeName();
            }
            return new ConstantExpression(constNode.getConstantValue(), constantType);
        } else if (expr instanceof ExprConcatNode) {
            return new ConcatExpression();
        } else if (expr instanceof ExprSubselectRowNode) {
            ExprSubselectRowNode sub = (ExprSubselectRowNode) expr;
            StatementSpecUnMapResult unmapped = unmap(sub.getStatementSpecRaw());
            return new SubqueryExpression(unmapped.getObjectModel());
        } else if (expr instanceof ExprSubselectInNode) {
            ExprSubselectInNode sub = (ExprSubselectInNode) expr;
            StatementSpecUnMapResult unmapped = unmap(sub.getStatementSpecRaw());
            return new SubqueryInExpression(unmapped.getObjectModel(), sub.isNotIn());
        } else if (expr instanceof ExprSubselectExistsNode) {
            ExprSubselectExistsNode sub = (ExprSubselectExistsNode) expr;
            StatementSpecUnMapResult unmapped = unmap(sub.getStatementSpecRaw());
            return new SubqueryExistsExpression(unmapped.getObjectModel());
        } else if (expr instanceof ExprSubselectAllSomeAnyNode) {
            ExprSubselectAllSomeAnyNode sub = (ExprSubselectAllSomeAnyNode) expr;
            StatementSpecUnMapResult unmapped = unmap(sub.getStatementSpecRaw());
            String operator = "=";
            if (sub.isNot()) {
                operator = "!=";
            }
            if (sub.getRelationalOp() != null) {
                operator = sub.getRelationalOp().getExpressionText();
            }
            return new SubqueryQualifiedExpression(unmapped.getObjectModel(), operator, sub.isAll());
        } else if (expr instanceof ExprCountNode) {
            ExprCountNode sub = (ExprCountNode) expr;
            if (sub.getChildNodes().length == 0 || (sub.getChildNodes().length == 1 && sub.isHasFilter())) {
                return new CountStarProjectionExpression();
            } else {
                return new CountProjectionExpression(sub.isDistinct());
            }
        } else if (expr instanceof ExprPriorNode) {
            return new PriorExpression();
        } else if (expr instanceof ExprPreviousNode) {
            ExprPreviousNode prev = (ExprPreviousNode) expr;
            PreviousExpression result = new PreviousExpression();
            result.setType(PreviousExpressionType.valueOf(prev.getPreviousType().toString()));
            return result;
        } else if (expr instanceof ExprSumNode) {
            ExprSumNode sub = (ExprSumNode) expr;
            return new SumProjectionExpression(sub.isDistinct());
        } else if (expr instanceof ExprLeavingAggNode) {
            return new PlugInProjectionExpression("leaving", false);
        } else if (expr instanceof ExprRateAggNode) {
            return new PlugInProjectionExpression("rate", false);
        } else if (expr instanceof ExprAvgNode) {
            ExprAvgNode sub = (ExprAvgNode) expr;
            return new AvgProjectionExpression(sub.isDistinct());
        } else if (expr instanceof ExprNthAggNode) {
            return new PlugInProjectionExpression("nth", false);
        } else if (expr instanceof ExprBetweenNode) {
            ExprBetweenNode between = (ExprBetweenNode) expr;
            return new BetweenExpression(between.isLowEndpointIncluded(), between.isHighEndpointIncluded(), between.isNotBetween());
        } else if (expr instanceof ExprAggMultiFunctionCountMinSketchNode) {
            ExprAggMultiFunctionCountMinSketchNode cmsNode = (ExprAggMultiFunctionCountMinSketchNode) expr;
            return new PlugInProjectionExpression(cmsNode.getAggregationFunctionName(), false);
        } else if (expr instanceof ExprBitWiseNode) {
            ExprBitWiseNode node = (ExprBitWiseNode) expr;
            return new BitwiseOpExpression(node.getBitWiseOpEnum());
        } else if (expr instanceof ExprAggMultiFunctionSortedMinMaxByNode) {
            ExprAggMultiFunctionSortedMinMaxByNode node = (ExprAggMultiFunctionSortedMinMaxByNode) expr;
            return new PlugInProjectionExpression(node.getAggregationFunctionName(), false);
        } else if (expr instanceof ExprArrayNode) {
            return new ArrayExpression();
        } else if (expr instanceof ExprMinMaxAggrNode) {
            ExprMinMaxAggrNode node = (ExprMinMaxAggrNode) expr;
            if (node.getMinMaxTypeEnum() == MinMaxTypeEnum.MIN) {
                return new MinProjectionExpression(node.isDistinct(), node.isEver());
            } else {
                return new MaxProjectionExpression(node.isDistinct(), node.isEver());
            }
        } else if (expr instanceof ExprMinMaxRowNode) {
            ExprMinMaxRowNode node = (ExprMinMaxRowNode) expr;
            if (node.getMinMaxTypeEnum() == MinMaxTypeEnum.MAX) {
                return new MaxRowExpression();
            }
            return new MinRowExpression();
        } else if (expr instanceof ExprCaseNode) {
            ExprCaseNode mycase = (ExprCaseNode) expr;
            if (mycase.isCase2()) {
                return new CaseSwitchExpression();
            } else {
                return new CaseWhenThenExpression();
            }
        } else if (expr instanceof ExprCoalesceNode) {
            return new CoalesceExpression();
        } else if (expr instanceof ExprLikeNode) {
            ExprLikeNode exprLikeNode = (ExprLikeNode) expr;
            return new LikeExpression(exprLikeNode.isNot());
        } else if (expr instanceof ExprNotNode) {
            return new NotExpression();
        } else if (expr instanceof ExprInNode) {
            ExprInNode inExpr = (ExprInNode) expr;
            return new InExpression(inExpr.isNotIn());
        } else if (expr instanceof ExprRegexpNode) {
            ExprRegexpNode exprRegexNode = (ExprRegexpNode) expr;
            return new RegExpExpression(exprRegexNode.isNot());
        } else if (expr instanceof ExprTimestampNode) {
            return new CurrentTimestampExpression();
        } else if (expr instanceof ExprCastNode) {
            ExprCastNode node = (ExprCastNode) expr;
            return new CastExpression(node.getClassIdentifierWArray().toEPL());
        } else if (expr instanceof ExprInstanceofNode) {
            ExprInstanceofNode node = (ExprInstanceofNode) expr;
            return new InstanceOfExpression(node.getClassIdentifiers());
        } else if (expr instanceof ExprPropertyExistsNode) {
            return new PropertyExistsExpression();
        } else if (expr instanceof ExprCurrentEvaluationContextNode) {
            return new CurrentEvaluationContextExpression();
        } else if (expr instanceof ExprAvedevNode) {
            ExprAvedevNode node = (ExprAvedevNode) expr;
            return new AvedevProjectionExpression(node.isDistinct());
        } else if (expr instanceof ExprStddevNode) {
            ExprStddevNode node = (ExprStddevNode) expr;
            return new StddevProjectionExpression(node.isDistinct());
        } else if (expr instanceof ExprMedianNode) {
            ExprMedianNode median = (ExprMedianNode) expr;
            return new MedianProjectionExpression(median.isDistinct());
        } else if (expr instanceof ExprFirstLastEverNode) {
            ExprFirstLastEverNode firstlast = (ExprFirstLastEverNode) expr;
            return firstlast.isFirst() ? new FirstEverProjectionExpression(firstlast.isDistinct()) : new LastEverProjectionExpression(firstlast.isDistinct());
        } else if (expr instanceof ExprCountEverNode) {
            ExprCountEverNode countEver = (ExprCountEverNode) expr;
            return new CountEverProjectionExpression(countEver.isDistinct());
        } else if (expr instanceof ExprTypeofNode) {
            return new TypeOfExpression();
        } else if (expr instanceof ExprPlugInSingleRowNode) {
            ExprPlugInSingleRowNode node = (ExprPlugInSingleRowNode) expr;
            List<DotExpressionItem> chain = unmapChains(node.getChainSpec(), unmapContext);
            if (chain.get(0) instanceof DotExpressionItemCall) {
                ((DotExpressionItemCall) chain.get(0)).setName(node.getFunctionName()); // we use the actual function name
            }
            return new SingleRowMethodExpression(chain);
        } else if (expr instanceof ExprPlugInAggNode) {
            ExprPlugInAggNode node = (ExprPlugInAggNode) expr;
            return new PlugInProjectionExpression(node.getAggregationFunctionName(), node.isDistinct());
        } else if (expr instanceof ExprPlugInMultiFunctionAggNode) {
            ExprPlugInMultiFunctionAggNode node = (ExprPlugInMultiFunctionAggNode) expr;
            return new PlugInProjectionExpression(node.getAggregationFunctionName(), node.isDistinct());
        } else if (expr instanceof ExprIStreamNode) {
            return new IStreamBuiltinExpression();
        } else if (expr instanceof ExprSubstitutionNode) {
            ExprSubstitutionNode node = (ExprSubstitutionNode) expr;
            return new SubstitutionParameterExpression(node.getOptionalName(), node.getOptionalType() == null ? null : node.getOptionalType().toEPL());
        } else if (expr instanceof ExprTimePeriod) {
            ExprTimePeriod node = (ExprTimePeriod) expr;
            return new TimePeriodExpression(node.isHasYear(), node.isHasMonth(), node.isHasWeek(), node.isHasDay(), node.isHasHour(), node.isHasMinute(), node.isHasSecond(), node.isHasMillisecond(), node.isHasMicrosecond());
        } else if (expr instanceof ExprWildcard) {
            return new CrontabParameterExpression(ScheduleItemType.WILDCARD);
        } else if (expr instanceof ExprNewInstanceNode) {
            ExprNewInstanceNode newNode = (ExprNewInstanceNode) expr;
            return new NewInstanceOperatorExpression(newNode.getClassIdentNoDimensions().toEPL(), newNode.getNumArrayDimensions());
        } else if (expr instanceof ExprNewStructNode) {
            ExprNewStructNode newNode = (ExprNewStructNode) expr;
            return new NewOperatorExpression(new ArrayList<>(Arrays.asList(newNode.getColumnNames())));
        } else if (expr instanceof ExprNumberSetFrequency) {
            return new CrontabFrequencyExpression();
        } else if (expr instanceof ExprNumberSetRange) {
            return new CrontabRangeExpression();
        } else if (expr instanceof ExprNumberSetList) {
            return new CrontabParameterSetExpression();
        } else if (expr instanceof ExprOrderedExpr) {
            ExprOrderedExpr order = (ExprOrderedExpr) expr;
            return new OrderedObjectParamExpression(order.isDescending());
        } else if (expr instanceof ExprEqualsAllAnyNode) {
            ExprEqualsAllAnyNode node = (ExprEqualsAllAnyNode) expr;
            String operator = node.isNot() ? "!=" : "=";
            return new CompareListExpression(node.isAll(), operator);
        } else if (expr instanceof ExprRelationalOpAllAnyNode) {
            ExprRelationalOpAllAnyNode node = (ExprRelationalOpAllAnyNode) expr;
            return new CompareListExpression(node.isAll(), node.getRelationalOpEnum().getExpressionText());
        } else if (expr instanceof ExprNumberSetCronParam) {
            ExprNumberSetCronParam cronParam = (ExprNumberSetCronParam) expr;
            ScheduleItemType type;
            if (cronParam.getCronOperator() == CronOperatorEnum.LASTDAY) {
                type = ScheduleItemType.LASTDAY;
            } else if (cronParam.getCronOperator() == CronOperatorEnum.LASTWEEKDAY) {
                type = ScheduleItemType.LASTWEEKDAY;
            } else if (cronParam.getCronOperator() == CronOperatorEnum.WEEKDAY) {
                type = ScheduleItemType.WEEKDAY;
            } else {
                throw new IllegalArgumentException("Cron parameter not recognized: " + cronParam.getCronOperator());
            }
            return new CrontabParameterExpression(type);
        } else if (expr instanceof ExprAggMultiFunctionLinearAccessNode) {
            ExprAggMultiFunctionLinearAccessNode accessNode = (ExprAggMultiFunctionLinearAccessNode) expr;
            AccessProjectionExpressionBase ape;
            if (accessNode.getStateType() == AggregationAccessorLinearType.FIRST) {
                ape = new FirstProjectionExpression();
            } else if (accessNode.getStateType() == AggregationAccessorLinearType.WINDOW) {
                ape = new WindowProjectionExpression();
            } else {
                ape = new LastProjectionExpression();
            }
            return ape;
        } else if (expr instanceof ExprDotNode) {
            ExprDotNode dotNode = (ExprDotNode) expr;
            DotExpression dotExpr = new DotExpression();
            for (Chainable chain : dotNode.getChainSpec()) {
                dotExpr.add(unmapChainItem(chain, unmapContext));
            }
            return dotExpr;
        } else if (expr instanceof ExprStreamUnderlyingNodeImpl) {
            ExprStreamUnderlyingNodeImpl streamNode = (ExprStreamUnderlyingNodeImpl) expr;
            return new StreamWildcardExpression(streamNode.getStreamName());
        } else if (expr instanceof ExprLambdaGoesNode) {
            ExprLambdaGoesNode lambdaNode = (ExprLambdaGoesNode) expr;
            return new LambdaExpression(new ArrayList<>(lambdaNode.getGoesToNames()));
        } else if (expr instanceof ExprDeclaredNode) {
            ExprDeclaredNode declNode = (ExprDeclaredNode) expr;
            DotExpression dotExpr = new DotExpression();
            dotExpr.add(declNode.getPrototype().getName(),
                unmapExpressionDeep(declNode.getChainParameters(), unmapContext));
            return dotExpr;
        } else if (expr instanceof ExprNodeScript) {
            ExprNodeScript scriptNode = (ExprNodeScript) expr;
            DotExpression dotExpr = new DotExpression();
            dotExpr.add(scriptNode.getScript().getName(), unmapExpressionDeep(scriptNode.getParameters(), unmapContext));
            return dotExpr;
        } else if (expr instanceof ExprGroupingNode) {
            return new GroupingExpression();
        } else if (expr instanceof ExprGroupingIdNode) {
            return new GroupingIdExpression();
        } else if (expr instanceof ExprNamedParameterNode) {
            ExprNamedParameterNode named = (ExprNamedParameterNode) expr;
            return new NamedParameterExpression(named.getParameterName());
        } else if (expr instanceof ExprTableAccessNode) {
            ExprTableAccessNode table = (ExprTableAccessNode) expr;
            if (table instanceof ExprTableAccessNodeTopLevel) {
                ExprTableAccessNodeTopLevel topLevel = (ExprTableAccessNodeTopLevel) table;
                return new TableAccessExpression(topLevel.getTableName(), unmapExpressionDeep(topLevel.getChildNodes(), unmapContext), null);
            }
            if (table instanceof ExprTableAccessNodeSubprop) {
                ExprTableAccessNodeSubprop sub = (ExprTableAccessNodeSubprop) table;
                if (sub.getChildNodes().length == 0) {
                    return new PropertyValueExpression(table.getTableName() + "." + sub.getSubpropName());
                } else {
                    return new TableAccessExpression(sub.getTableName(), unmapExpressionDeep(sub.getChildNodes(), unmapContext), sub.getSubpropName());
                }
            }
            if (table instanceof ExprTableAccessNodeKeys) {
                DotExpression dotExpression = new DotExpression();
                dotExpression.add(table.getTableName(), Collections.EMPTY_LIST, true);
                dotExpression.add("keys", Collections.<Expression>emptyList());
                return dotExpression;
            }
        }
        throw new IllegalArgumentException("Could not map expression node of type " + expr.getClass().getSimpleName());
    }

    private static void unmapExpressionRecursive(Expression parent, ExprNode expr, StatementSpecUnMapContext unmapContext) {
        for (ExprNode child : expr.getChildNodes()) {
            Expression result = unmapExpressionFlat(child, unmapContext);
            parent.getChildren().add(result);
            unmapExpressionRecursive(result, child, unmapContext);
        }
    }

    private static void unmapExpressionRecursiveRowRecog(MatchRecognizeRegEx parent, RowRecogExprNode expr, StatementSpecUnMapContext unmapContext) {
        for (RowRecogExprNode child : expr.getChildNodes()) {
            MatchRecognizeRegEx result = unmapExpressionFlatRowRecog(child, unmapContext);
            parent.getChildren().add(result);
            unmapExpressionRecursiveRowRecog(result, child, unmapContext);
        }
    }

    private static void mapExpressionRecursive(ExprNode parent, Expression expr, StatementSpecMapContext mapContext) {
        for (Expression child : expr.getChildren()) {
            ExprNode result = mapExpressionFlat(child, mapContext);
            parent.addChildNode(result);
            mapExpressionRecursive(result, child, mapContext);
        }
    }

    private static void mapExpressionRecursiveRowRecog(RowRecogExprNode parent, MatchRecognizeRegEx expr, StatementSpecMapContext mapContext) {
        for (MatchRecognizeRegEx child : expr.getChildren()) {
            RowRecogExprNode result = mapExpressionFlatRowRecog(child, mapContext);
            parent.addChildNode(result);
            mapExpressionRecursiveRowRecog(result, child, mapContext);
        }
    }

    private static void mapFrom(FromClause fromClause, StatementSpecRaw raw, StatementSpecMapContext mapContext) {
        if (fromClause == null) {
            return;
        }

        for (Stream stream : fromClause.getStreams()) {
            StreamSpecRaw spec;

            ViewSpec[] views = ViewSpec.EMPTY_VIEWSPEC_ARRAY;
            if (stream instanceof ProjectedStream) {
                ProjectedStream projectedStream = (ProjectedStream) stream;
                views = ViewSpec.toArray(mapViews(projectedStream.getViews(), mapContext));
            }

            if (stream instanceof FilterStream) {
                FilterStream filterStream = (FilterStream) stream;
                FilterSpecRaw filterSpecRaw = mapFilter(filterStream.getFilter(), mapContext);
                StreamSpecOptions options = mapStreamOpts(filterStream);
                spec = new FilterStreamSpecRaw(filterSpecRaw, views, filterStream.getStreamName(), options);
            } else if (stream instanceof SQLStream) {
                SQLStream sqlStream = (SQLStream) stream;
                spec = new DBStatementStreamSpec(sqlStream.getStreamName(), views,
                    sqlStream.getDatabaseName(), sqlStream.getSqlWithSubsParams(), sqlStream.getOptionalMetadataSQL());
            } else if (stream instanceof PatternStream) {
                PatternStream patternStream = (PatternStream) stream;
                EvalForgeNode child = mapPatternEvalDeep(patternStream.getExpression(), mapContext);
                StreamSpecOptions options = mapStreamOpts(patternStream);
                PatternLevelAnnotationFlags flags = PatternLevelAnnotationUtil.annotationsToSpec(patternStream.getAnnotations());
                spec = new PatternStreamSpecRaw(child, views, patternStream.getStreamName(), options, flags.isSuppressSameEventMatches(), flags.isDiscardPartialsOnMatch());
            } else if (stream instanceof MethodInvocationStream) {
                MethodInvocationStream methodStream = (MethodInvocationStream) stream;
                List<ExprNode> expressions = new ArrayList<>();
                for (Expression expr : methodStream.getParameterExpressions()) {
                    ExprNode exprNode = mapExpressionDeep(expr, mapContext);
                    expressions.add(exprNode);
                }

                if (mapContext.getVariableCompileTimeResolver().resolve(methodStream.getClassName()) != null) {
                    mapContext.getVariableNames().add(methodStream.getClassName());
                }

                spec = new MethodStreamSpec(methodStream.getStreamName(), views, "method",
                    methodStream.getClassName(), methodStream.getMethodName(), expressions, methodStream.getOptionalEventTypeName());
            } else {
                throw new IllegalArgumentException("Could not map from stream " + stream + " to an internal representation");
            }

            raw.getStreamSpecs().add(spec);
        }

        for (OuterJoinQualifier qualifier : fromClause.getOuterJoinQualifiers()) {
            ExprIdentNode left = null;
            ExprIdentNode right = null;
            ExprIdentNode[] additionalLeft = null;
            ExprIdentNode[] additionalRight = null;

            if (qualifier.getLeft() != null) {

                left = (ExprIdentNode) mapExpressionFlat(qualifier.getLeft(), mapContext);
                right = (ExprIdentNode) mapExpressionFlat(qualifier.getRight(), mapContext);

                if (qualifier.getAdditionalProperties().size() != 0) {
                    additionalLeft = new ExprIdentNode[qualifier.getAdditionalProperties().size()];
                    additionalRight = new ExprIdentNode[qualifier.getAdditionalProperties().size()];
                    int count = 0;
                    for (PropertyValueExpressionPair pair : qualifier.getAdditionalProperties()) {
                        additionalLeft[count] = (ExprIdentNode) mapExpressionFlat(pair.getLeft(), mapContext);
                        additionalRight[count] = (ExprIdentNode) mapExpressionFlat(pair.getRight(), mapContext);
                        count++;
                    }
                }
            }

            raw.getOuterJoinDescList().add(new OuterJoinDesc(qualifier.getType(), left, right, additionalLeft, additionalRight));
        }
    }

    private static List<ViewSpec> mapViews(List<View> views, StatementSpecMapContext mapContext) {
        List<ViewSpec> viewSpecs = new ArrayList<>();
        for (View view : views) {
            List<ExprNode> viewExpressions = mapExpressionDeep(view.getParameters(), mapContext);
            viewSpecs.add(new ViewSpec(view.getNamespace(), view.getName(), viewExpressions));
        }
        return viewSpecs;
    }

    private static List<View> unmapViews(List<ViewSpec> viewSpecs, StatementSpecUnMapContext unmapContext) {
        List<View> views = new ArrayList<>();
        for (ViewSpec viewSpec : viewSpecs) {
            List<Expression> viewExpressions = unmapExpressionDeep(viewSpec.getObjectParameters(), unmapContext);
            views.add(View.create(viewSpec.getObjectNamespace(), viewSpec.getObjectName(), viewExpressions));
        }
        return views;
    }

    private static EvalForgeNode mapPatternEvalFlat(PatternExpr eval, StatementSpecMapContext mapContext) {
        if (eval == null) {
            throw new IllegalArgumentException("Null expression parameter");
        }
        boolean attachPatternText = mapContext.isAttachPatternText();
        if (eval instanceof PatternAndExpr) {
            return new EvalAndForgeNode(attachPatternText);
        } else if (eval instanceof PatternFilterExpr) {
            PatternFilterExpr filterExpr = (PatternFilterExpr) eval;
            FilterSpecRaw filterSpec = mapFilter(filterExpr.getFilter(), mapContext);
            return new EvalFilterForgeNode(attachPatternText, filterSpec, filterExpr.getTagName(), filterExpr.getOptionalConsumptionLevel());
        } else if (eval instanceof PatternEveryExpr) {
            return new EvalEveryForgeNode(attachPatternText);
        } else if (eval instanceof PatternOrExpr) {
            return new EvalOrForgeNode(attachPatternText);
        } else if (eval instanceof PatternNotExpr) {
            return new EvalNotForgeNode(attachPatternText);
        } else if (eval instanceof PatternFollowedByExpr) {
            PatternFollowedByExpr fb = (PatternFollowedByExpr) eval;
            List<ExprNode> maxExpr = mapExpressionDeep(fb.getOptionalMaxPerSubexpression(), mapContext);
            return new EvalFollowedByForgeNode(attachPatternText, maxExpr);
        }
        if (eval instanceof PatternObserverExpr) {
            PatternObserverExpr observer = (PatternObserverExpr) eval;
            List<ExprNode> expressions = mapExpressionDeep(observer.getParameters(), mapContext);
            return new EvalObserverForgeNode(attachPatternText, new PatternObserverSpec(observer.getNamespace(), observer.getName(), expressions));
        } else if (eval instanceof PatternGuardExpr) {
            PatternGuardExpr guard = (PatternGuardExpr) eval;
            List<ExprNode> expressions = mapExpressionDeep(guard.getParameters(), mapContext);
            return new EvalGuardForgeNode(attachPatternText, new PatternGuardSpec(guard.getNamespace(), guard.getName(), expressions));
        } else if (eval instanceof PatternMatchUntilExpr) {
            PatternMatchUntilExpr until = (PatternMatchUntilExpr) eval;
            ExprNode low = until.getLow() != null ? mapExpressionDeep(until.getLow(), mapContext) : null;
            ExprNode high = until.getHigh() != null ? mapExpressionDeep(until.getHigh(), mapContext) : null;
            ExprNode single = until.getSingle() != null ? mapExpressionDeep(until.getSingle(), mapContext) : null;
            return new EvalMatchUntilForgeNode(attachPatternText, low, high, single);
        } else if (eval instanceof PatternEveryDistinctExpr) {
            PatternEveryDistinctExpr everyDist = (PatternEveryDistinctExpr) eval;
            List<ExprNode> expressions = mapExpressionDeep(everyDist.getExpressions(), mapContext);
            return new EvalEveryDistinctForgeNode(attachPatternText, expressions);
        }
        throw new IllegalArgumentException("Could not map pattern expression node of type " + eval.getClass().getSimpleName());
    }

    private static PatternExpr unmapPatternEvalFlat(EvalForgeNode eval, StatementSpecUnMapContext unmapContext) {
        if (eval instanceof EvalFilterForgeNode) {
            EvalFilterForgeNode filterNode = (EvalFilterForgeNode) eval;
            Filter filter = unmapFilter(filterNode.getRawFilterSpec(), unmapContext);
            PatternFilterExpr expr = new PatternFilterExpr(filter, filterNode.getEventAsName());
            expr.setOptionalConsumptionLevel(filterNode.getConsumptionLevel());
            return expr;
        } else if (eval instanceof EvalAndForgeNode) {
            return new PatternAndExpr();
        } else if (eval instanceof EvalEveryForgeNode) {
            return new PatternEveryExpr();
        } else if (eval instanceof EvalOrForgeNode) {
            return new PatternOrExpr();
        } else if (eval instanceof EvalNotForgeNode) {
            return new PatternNotExpr();
        } else if (eval instanceof EvalFollowedByForgeNode) {
            EvalFollowedByForgeNode fb = (EvalFollowedByForgeNode) eval;
            List<Expression> expressions = unmapExpressionDeep(fb.getOptionalMaxExpressions(), unmapContext);
            return new PatternFollowedByExpr(expressions);
        } else if (eval instanceof EvalObserverForgeNode) {
            EvalObserverForgeNode observerNode = (EvalObserverForgeNode) eval;
            List<Expression> expressions = unmapExpressionDeep(observerNode.getPatternObserverSpec().getObjectParameters(), unmapContext);
            return new PatternObserverExpr(observerNode.getPatternObserverSpec().getObjectNamespace(),
                observerNode.getPatternObserverSpec().getObjectName(), expressions);
        } else if (eval instanceof EvalGuardForgeNode) {
            EvalGuardForgeNode guardNode = (EvalGuardForgeNode) eval;
            List<Expression> expressions = unmapExpressionDeep(guardNode.getPatternGuardSpec().getObjectParameters(), unmapContext);
            return new PatternGuardExpr(guardNode.getPatternGuardSpec().getObjectNamespace(),
                guardNode.getPatternGuardSpec().getObjectName(), expressions);
        } else if (eval instanceof EvalMatchUntilForgeNode) {
            EvalMatchUntilForgeNode matchUntilNode = (EvalMatchUntilForgeNode) eval;
            Expression low = matchUntilNode.getLowerBounds() != null ? unmapExpressionDeep(matchUntilNode.getLowerBounds(), unmapContext) : null;
            Expression high = matchUntilNode.getUpperBounds() != null ? unmapExpressionDeep(matchUntilNode.getUpperBounds(), unmapContext) : null;
            Expression single = matchUntilNode.getSingleBound() != null ? unmapExpressionDeep(matchUntilNode.getSingleBound(), unmapContext) : null;
            return new PatternMatchUntilExpr(low, high, single);
        } else if (eval instanceof EvalEveryDistinctForgeNode) {
            EvalEveryDistinctForgeNode everyDistinctNode = (EvalEveryDistinctForgeNode) eval;
            List<Expression> expressions = unmapExpressionDeep(everyDistinctNode.getExpressions(), unmapContext);
            return new PatternEveryDistinctExpr(expressions);
        }
        throw new IllegalArgumentException("Could not map pattern expression node of type " + eval.getClass().getSimpleName());
    }

    private static void unmapPatternEvalRecursive(PatternExpr parent, EvalForgeNode eval, StatementSpecUnMapContext unmapContext) {
        for (EvalForgeNode child : eval.getChildNodes()) {
            PatternExpr result = unmapPatternEvalFlat(child, unmapContext);
            parent.getChildren().add(result);
            unmapPatternEvalRecursive(result, child, unmapContext);
        }
    }

    private static void mapPatternEvalRecursive(EvalForgeNode parent, PatternExpr expr, StatementSpecMapContext mapContext) {
        for (PatternExpr child : expr.getChildren()) {
            EvalForgeNode result = mapPatternEvalFlat(child, mapContext);
            parent.addChildNode(result);
            mapPatternEvalRecursive(result, child, mapContext);
        }
    }

    private static PatternExpr unmapPatternEvalDeep(EvalForgeNode exprNode, StatementSpecUnMapContext unmapContext) {
        PatternExpr parent = unmapPatternEvalFlat(exprNode, unmapContext);
        unmapPatternEvalRecursive(parent, exprNode, unmapContext);
        return parent;
    }

    private static EvalForgeNode mapPatternEvalDeep(PatternExpr expr, StatementSpecMapContext mapContext) {
        EvalForgeNode parent = mapPatternEvalFlat(expr, mapContext);
        mapPatternEvalRecursive(parent, expr, mapContext);
        return parent;
    }

    private static FilterSpecRaw mapFilter(Filter filter, StatementSpecMapContext mapContext) {
        List<ExprNode> expr = new ArrayList<>();
        if (filter.getFilter() != null) {
            ExprNode exprNode = mapExpressionDeep(filter.getFilter(), mapContext);
            expr.add(exprNode);
        }

        PropertyEvalSpec evalSpec = null;
        if (filter.getOptionalPropertySelects() != null) {
            evalSpec = mapPropertySelects(filter.getOptionalPropertySelects(), mapContext);
        }

        return new FilterSpecRaw(filter.getEventTypeName(), expr, evalSpec);
    }

    private static PropertyEvalSpec mapPropertySelects(List<ContainedEventSelect> propertySelects, StatementSpecMapContext mapContext) {
        PropertyEvalSpec evalSpec = new PropertyEvalSpec();
        for (ContainedEventSelect propertySelect : propertySelects) {
            SelectClauseSpecRaw selectSpec = null;
            if (propertySelect.getSelectClause() != null) {
                selectSpec = mapSelectRaw(propertySelect.getSelectClause(), mapContext);
            }

            ExprNode exprNodeWhere = null;
            if (propertySelect.getWhereClause() != null) {
                exprNodeWhere = mapExpressionDeep(propertySelect.getWhereClause(), mapContext);
            }

            ExprNode splitterExpr = null;
            if (propertySelect.getSplitExpression() != null) {
                splitterExpr = mapExpressionDeep(propertySelect.getSplitExpression(), mapContext);
            }

            evalSpec.add(new PropertyEvalAtom(splitterExpr, propertySelect.getOptionalSplitExpressionTypeName(), propertySelect.getOptionalAsName(), selectSpec, exprNodeWhere));
        }
        return evalSpec;
    }

    private static Filter unmapFilter(FilterSpecRaw filter, StatementSpecUnMapContext unmapContext) {
        Expression expr = null;
        if (filter.getFilterExpressions().size() > 1) {
            expr = new Conjunction();
            for (ExprNode exprNode : filter.getFilterExpressions()) {
                Expression expression = unmapExpressionDeep(exprNode, unmapContext);
                expr.getChildren().add(expression);
            }
        } else if (filter.getFilterExpressions().size() == 1) {
            expr = unmapExpressionDeep(filter.getFilterExpressions().get(0), unmapContext);
        }

        Filter filterDef = new Filter(filter.getEventTypeName(), expr);

        if (filter.getOptionalPropertyEvalSpec() != null) {
            List<ContainedEventSelect> propertySelects = unmapPropertySelects(filter.getOptionalPropertyEvalSpec(), unmapContext);
            filterDef.setOptionalPropertySelects(propertySelects);
        }
        return filterDef;
    }

    private static List<ContainedEventSelect> unmapPropertySelects(PropertyEvalSpec propertyEvalSpec, StatementSpecUnMapContext unmapContext) {
        List<ContainedEventSelect> propertySelects = new ArrayList<>();
        for (PropertyEvalAtom atom : propertyEvalSpec.getAtoms()) {
            SelectClause selectClause = null;
            if (atom.getOptionalSelectClause() != null && !atom.getOptionalSelectClause().getSelectExprList().isEmpty()) {
                selectClause = unmapSelect(atom.getOptionalSelectClause(), SelectClauseStreamSelectorEnum.ISTREAM_ONLY, unmapContext);
            }

            Expression filterExpression = null;
            if (atom.getOptionalWhereClause() != null) {
                filterExpression = unmapExpressionDeep(atom.getOptionalWhereClause(), unmapContext);
            }

            Expression splitExpression = unmapExpressionDeep(atom.getSplitterExpression(), unmapContext);

            ContainedEventSelect contained = new ContainedEventSelect(splitExpression);
            contained.setOptionalSplitExpressionTypeName(atom.getOptionalResultEventType());
            contained.setSelectClause(selectClause);
            contained.setWhereClause(filterExpression);
            contained.setOptionalAsName(atom.getOptionalAsName());

            if (atom.getSplitterExpression() != null) {
                contained.setSplitExpression(unmapExpressionDeep(atom.getSplitterExpression(), unmapContext));
            }
            propertySelects.add(contained);
        }
        return propertySelects;
    }

    private static List<AnnotationPart> unmapAnnotations(List<AnnotationDesc> annotations) {
        List<AnnotationPart> result = new ArrayList<>();
        for (AnnotationDesc desc : annotations) {
            result.add(unmapAnnotation(desc));
        }
        return result;
    }

    private static List<ExpressionDeclaration> unmapExpressionDeclarations(ExpressionDeclDesc expr, StatementSpecUnMapContext unmapContext) {
        if (expr == null || expr.getExpressions().isEmpty()) {
            return Collections.emptyList();
        }
        List<ExpressionDeclaration> result = new ArrayList<>();
        for (ExpressionDeclItem desc : expr.getExpressions()) {
            result.add(unmapExpressionDeclItem(desc));
        }
        return result;
    }

    private static ExpressionDeclaration unmapExpressionDeclItem(ExpressionDeclItem desc) {
        return new ExpressionDeclaration(desc.getName(), new ArrayList<>(Arrays.asList(desc.getParametersNames())), desc.getOptionalSoda(), desc.isAlias());
    }

    private static List<ScriptExpression> unmapScriptExpressions(List<ExpressionScriptProvided> scripts, StatementSpecUnMapContext unmapContext) {
        if (scripts == null || scripts.isEmpty()) {
            return Collections.emptyList();
        }
        List<ScriptExpression> result = new ArrayList<>();
        for (ExpressionScriptProvided script : scripts) {
            ScriptExpression e = unmapScriptExpression(script);
            result.add(e);
        }
        return result;
    }

    private static List<ClassProvidedExpression> unmapClassProvideds(List<String> classProvideds, StatementSpecUnMapContext unmapContext) {
        if (classProvideds == null || classProvideds.isEmpty()) {
            return Collections.emptyList();
        }
        List<ClassProvidedExpression> result = new ArrayList<>(classProvideds.size());
        for (String text : classProvideds) {
            ClassProvidedExpression e = new ClassProvidedExpression(text);
            result.add(e);
        }
        return result;
    }

    private static ScriptExpression unmapScriptExpression(ExpressionScriptProvided script) {
        String returnType = script.getOptionalReturnTypeName();
        List<String> params = new ArrayList<>(Arrays.asList(script.getParameterNames()));
        return new ScriptExpression(script.getName(), params, script.getExpression(), returnType, script.getOptionalDialect(), script.getOptionalEventTypeName());
    }

    private static AnnotationPart unmapAnnotation(AnnotationDesc desc) {
        if ((desc.getAttributes() == null) || (desc.getAttributes().isEmpty())) {
            return new AnnotationPart(desc.getName());
        }

        List<AnnotationAttribute> attributes = new ArrayList<>();
        for (Pair<String, Object> pair : desc.getAttributes()) {
            if (pair.getSecond() instanceof AnnotationDesc) {
                attributes.add(new AnnotationAttribute(pair.getFirst(), unmapAnnotation((AnnotationDesc) pair.getSecond())));
            } else {
                attributes.add(new AnnotationAttribute(pair.getFirst(), pair.getSecond()));
            }
        }
        return new AnnotationPart(desc.getName(), attributes);
    }

    public static List<AnnotationDesc> mapAnnotations(List<AnnotationPart> annotations) {
        List<AnnotationDesc> result;
        if (annotations != null) {
            result = new ArrayList<>();
            for (AnnotationPart part : annotations) {
                result.add(mapAnnotation(part));
            }
        } else {
            result = Collections.emptyList();
        }
        return result;
    }

    private static void mapContextName(String contextName, StatementSpecRaw raw, StatementSpecMapContext mapContext) {
        raw.setOptionalContextName(contextName);
    }

    private static void mapExpressionDeclaration(List<ExpressionDeclaration> expressionDeclarations, StatementSpecRaw raw, StatementSpecMapContext mapContext) {
        if (expressionDeclarations == null || expressionDeclarations.isEmpty()) {
            return;
        }

        ExpressionDeclDesc desc = new ExpressionDeclDesc();
        raw.setExpressionDeclDesc(desc);

        for (ExpressionDeclaration decl : expressionDeclarations) {
            ExpressionDeclItem item = mapExpressionDeclItem(decl, mapContext);
            desc.getExpressions().add(item);
            mapContext.addExpressionDeclaration(item);
        }
    }

    private static ExpressionDeclItem mapExpressionDeclItem(ExpressionDeclaration decl, StatementSpecMapContext mapContext) {
        ExpressionDeclItem item = new ExpressionDeclItem(decl.getName(),
            decl.isAlias() ? new String[0] : decl.getParameterNames().toArray(new String[0]), decl.isAlias());
        item.setOptionalSoda(decl.getExpression());
        return item;
    }

    private static void mapScriptExpressions(List<ScriptExpression> scriptExpressions, StatementSpecRaw raw, StatementSpecMapContext mapContext) {
        if (scriptExpressions == null || scriptExpressions.isEmpty()) {
            return;
        }

        List<ExpressionScriptProvided> scripts = new ArrayList<>();
        raw.setScriptExpressions(scripts);

        for (ScriptExpression decl : scriptExpressions) {
            ExpressionScriptProvided scriptProvided = mapScriptExpression(decl, mapContext);
            scripts.add(scriptProvided);
            mapContext.addScript(scriptProvided);
        }
    }

    private static void mapClassProvidedExpressions(List<ClassProvidedExpression> classProvidedExpressions, StatementSpecRaw raw, StatementSpecMapContext mapContext) {
        if (classProvidedExpressions == null || classProvidedExpressions.isEmpty()) {
            return;
        }

        List<String> classes = new ArrayList<>(classProvidedExpressions.size());
        raw.setClassProvideds(classes);

        for (ClassProvidedExpression decl : classProvidedExpressions) {
            classes.add(decl.getClassText());
        }
    }

    private static ExpressionScriptProvided mapScriptExpression(ScriptExpression decl, StatementSpecMapContext mapContext) {
        String[] params = decl.getParameterNames() == null ? new String[0] : decl.getParameterNames().toArray(new String[0]);
        return new ExpressionScriptProvided(decl.getName(), decl.getExpressionText(), params, decl.getOptionalReturnType(), decl.getOptionalEventTypeName(), decl.getOptionalDialect());
    }

    private static AnnotationDesc mapAnnotation(AnnotationPart part) {
        if ((part.getAttributes() == null) || (part.getAttributes().isEmpty())) {
            return new AnnotationDesc(part.getName(), Collections.emptyList());
        }

        List<Pair<String, Object>> attributes = new ArrayList<>();
        for (AnnotationAttribute pair : part.getAttributes()) {
            if (pair.getValue() instanceof AnnotationPart) {
                attributes.add(new Pair<>(pair.getName(), mapAnnotation((AnnotationPart) pair.getValue())));
            } else {
                attributes.add(new Pair<>(pair.getName(), pair.getValue()));
            }
        }
        return new AnnotationDesc(part.getName(), attributes);
    }

    private static void mapSQLParameters(FromClause fromClause, StatementSpecRaw raw, StatementSpecMapContext mapContext) {
        if ((fromClause == null) || (fromClause.getStreams() == null)) {
            return;
        }
        int streamNum = -1;
        for (Stream stream : fromClause.getStreams()) {
            streamNum++;
            if (!(stream instanceof SQLStream)) {
                continue;
            }
            SQLStream sqlStream = (SQLStream) stream;

            List<PlaceholderParser.Fragment> sqlFragments = null;
            try {
                sqlFragments = PlaceholderParser.parsePlaceholder(sqlStream.getSqlWithSubsParams());
            } catch (PlaceholderParseException e) {
                throw new RuntimeException("Error parsing SQL placeholder expression '" + sqlStream.getSqlWithSubsParams() + "': ");
            }

            for (PlaceholderParser.Fragment fragment : sqlFragments) {
                if (!(fragment instanceof PlaceholderParser.ParameterFragment)) {
                    continue;
                }

                // Parse expression, store for substitution parameters
                String expression = fragment.getValue();
                if (expression.toUpperCase(Locale.ENGLISH).equals(SAMPLE_WHERECLAUSE_PLACEHOLDER)) {
                    continue;
                }

                if (expression.trim().length() == 0) {
                    throw new EPException("Missing expression within ${...} in SQL statement");
                }
                String toCompile = "select * from java.lang.Object where " + expression;
                StatementSpecRaw rawSqlExpr;
                try {
                    rawSqlExpr = mapContext.getMapEnv().getCompilerServices().parseWalk(toCompile, mapContext.getMapEnv());
                } catch (StatementSpecCompileException e) {
                    throw new EPException("Failed to compile SQL parameter '" + expression + "': " + e.getExpression(), e);
                }

                mapContext.getSubstitutionNodes().addAll(rawSqlExpr.getSubstitutionParameters());
                mapContext.getVariableNames().addAll(rawSqlExpr.getReferencedVariables());
                mapContext.getTableExpressions().addAll(rawSqlExpr.getTableExpressions());

                // add expression
                if (raw.getSqlParameters() == null) {
                    raw.setSqlParameters(new HashMap<>());
                }
                List<ExprNode> listExp = raw.getSqlParameters().get(streamNum);
                if (listExp == null) {
                    listExp = new ArrayList<>();
                    raw.getSqlParameters().put(streamNum, listExp);
                }
                listExp.add(rawSqlExpr.getWhereClause());
            }
        }
    }

    private static List<Chainable> mapChains(List<DotExpressionItem> pairs, StatementSpecMapContext mapContext) {
        List<Chainable> chains = new ArrayList<>();
        for (DotExpressionItem item : pairs) {
            chains.add(mapChainItem(item, mapContext));
        }
        return chains;
    }

    private static List<DotExpressionItem> unmapChains(List<Chainable> pairs, StatementSpecUnMapContext unmapContext) {
        List<DotExpressionItem> result = new ArrayList<>();
        for (Chainable chain : pairs) {
            result.add(unmapChainItem(chain, unmapContext));
        }
        return result;
    }

    private static DotExpressionItem unmapChainItem(Chainable chain, StatementSpecUnMapContext unmapContext) {
        if (chain instanceof ChainableName) {
            return new DotExpressionItemName(((ChainableName) chain).getName());
        } else if (chain instanceof ChainableArray) {
            List<ExprNode> indexes = ((ChainableArray) chain).getIndexes();
            return new DotExpressionItemArray(unmapExpressionDeep(indexes, unmapContext));
        } else if (chain instanceof ChainableCall) {
            ChainableCall call = (ChainableCall) chain;
            return new DotExpressionItemCall(call.getName(), unmapExpressionDeep(call.getParameters(), unmapContext));
        } else {
            throw new IllegalStateException("Unrecognized chainable " + chain);
        }
    }

    private static Chainable mapChainItem(DotExpressionItem item, StatementSpecMapContext mapContext) {
        if (item instanceof DotExpressionItemName) {
            return new ChainableName(((DotExpressionItemName) item).getName());
        } else if (item instanceof DotExpressionItemArray) {
            List<Expression> indexes = ((DotExpressionItemArray) item).getIndexes();
            return new ChainableArray(mapExpressionDeep(indexes, mapContext));
        } else if (item instanceof DotExpressionItemCall) {
            DotExpressionItemCall call = (DotExpressionItemCall) item;
            return new ChainableCall(call.getName(), mapExpressionDeep(call.getParameters(), mapContext));
        } else {
            throw new IllegalStateException("Unrecognized item " + item);
        }
    }

    public static ExprNode mapExpression(Expression expression, StatementSpecMapContext env) {
        return mapExpressionDeep(expression, env);
    }
}
