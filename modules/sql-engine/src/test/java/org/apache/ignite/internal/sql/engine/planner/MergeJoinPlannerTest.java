/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.sql.engine.planner;

import static org.apache.calcite.rel.RelFieldCollation.Direction.ASCENDING;
import static org.apache.calcite.rel.RelFieldCollation.Direction.DESCENDING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.function.Consumer;
import org.apache.calcite.rel.RelCollation;
import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.RelFieldCollation;
import org.apache.calcite.rel.core.Join;
import org.apache.ignite.internal.sql.engine.framework.TestBuilders;
import org.apache.ignite.internal.sql.engine.framework.TestBuilders.TableBuilder;
import org.apache.ignite.internal.sql.engine.framework.TestTable;
import org.apache.ignite.internal.sql.engine.rel.IgniteRel;
import org.apache.ignite.internal.sql.engine.rel.IgniteSort;
import org.apache.ignite.internal.sql.engine.rel.IgniteTableScan;
import org.apache.ignite.internal.sql.engine.schema.IgniteIndex.Collation;
import org.apache.ignite.internal.sql.engine.schema.IgniteSchema;
import org.apache.ignite.internal.sql.engine.trait.IgniteDistributions;
import org.apache.ignite.internal.type.NativeTypes;
import org.junit.jupiter.api.Test;

/** MergeJoin planner test. */
public class MergeJoinPlannerTest extends AbstractPlannerTest {
    /** Only MergeJoin encourage. */
    private static final String[] DISABLED_RULES = {
            "NestedLoopJoinConverter",
            "CorrelatedNestedLoopJoin",
            "FilterSpoolMergeRule",
            "JoinCommuteRule"
    };

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, collation consisted of join keys of left table should be propagated as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerPassThroughOrderByLeft1() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + " order by LEFT_T.c1, LEFT_T.c2";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfJoin(rel));

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        RelCollation expected = RelCollations.of(
                new RelFieldCollation(0, ASCENDING),
                new RelFieldCollation(1, ASCENDING)
        );

        assertEquals(expected, sortNodes.get(0).collation());
        assertEquals(expected, sortNodes.get(1).collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, collation consisted of join keys of left table should be propagated as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerPassThroughOrderByLeft2() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + " order by LEFT_T.c1 DESC, LEFT_T.c2 DESC";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfJoin(rel));

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        RelCollation expected = RelCollations.of(
                new RelFieldCollation(0, DESCENDING),
                new RelFieldCollation(1, DESCENDING)
        );

        assertEquals(expected, sortNodes.get(0).collation());
        assertEquals(expected, sortNodes.get(1).collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, collation consisted of join keys of left table should be propagated as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerPassThroughOrderByLeft3() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + " order by LEFT_T.c1 DESC, LEFT_T.c2 ASC";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfJoin(rel));

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        RelCollation expected = RelCollations.of(
                new RelFieldCollation(0, DESCENDING),
                new RelFieldCollation(1, ASCENDING)
        );

        assertEquals(expected, sortNodes.get(0).collation());
        assertEquals(expected, sortNodes.get(1).collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, collation consisted of join keys of left table should be propagated as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerPassThroughOrderByLeft4() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + " order by LEFT_T.c1 ASC NULLS LAST, LEFT_T.c2 ASC NULLS LAST";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfJoin(rel));

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        RelCollation expected = RelCollations.of(
                new RelFieldCollation(0, ASCENDING),
                new RelFieldCollation(1, ASCENDING)
        );

        assertEquals(expected, sortNodes.get(0).collation());
        assertEquals(expected, sortNodes.get(1).collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, collation consisted of join keys of left table should be propagated as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerPassThroughOrderByLeft5() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + " order by LEFT_T.c1 ASC NULLS FIRST, LEFT_T.c2 ASC NULLS LAST";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfJoin(rel));

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        RelCollation expected = RelCollations.of(
                new RelFieldCollation(0, ASCENDING, RelFieldCollation.NullDirection.FIRST),
                new RelFieldCollation(1)
        );

        assertEquals(expected, sortNodes.get(0).collation());
        assertEquals(expected, sortNodes.get(1).collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, collation that is superset of join keys should be propagated as is, if it doesn't include fields from
     * right table.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerPassThroughOrderByLeft6() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + " order by LEFT_T.c1, LEFT_T.c2, LEFT_T.c3";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfJoin(rel));

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, ASCENDING),
                        new RelFieldCollation(1, ASCENDING),
                        new RelFieldCollation(2, ASCENDING)
                ),
                sortNodes.get(0).collation()
        );
        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, ASCENDING),
                        new RelFieldCollation(1, ASCENDING)
                ),
                sortNodes.get(1).collation()
        );
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, collation that is superset of join keys should be propagated as is, if it doesn't include fields from
     * right table.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerPassThroughOrderByLeft7() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + " order by LEFT_T.c1 DESC, LEFT_T.c2 DESC, LEFT_T.c3 DESC";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfJoin(rel));

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, DESCENDING),
                        new RelFieldCollation(1, DESCENDING),
                        new RelFieldCollation(2, DESCENDING)
                ),
                sortNodes.get(0).collation()
        );
        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, DESCENDING),
                        new RelFieldCollation(1, DESCENDING)
                ),
                sortNodes.get(1).collation()
        );
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, collation that is superset of join keys should be propagated as is, if it doesn't include fields from
     * right table.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerPassThroughOrderByLeft8() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + " order by LEFT_T.c1 DESC, LEFT_T.c2 ASC, LEFT_T.c3 DESC";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfJoin(rel));

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, DESCENDING),
                        new RelFieldCollation(1, ASCENDING),
                        new RelFieldCollation(2, DESCENDING)
                ),
                sortNodes.get(0).collation()
        );
        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, DESCENDING),
                        new RelFieldCollation(1, ASCENDING)
                ),
                sortNodes.get(1).collation()
        );
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, collation that is superset of join keys should be propagated as is, if it doesn't include fields from
     * right table.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerPassThroughOrderByLeft9() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + " order by LEFT_T.c1 ASC NULLS LAST, LEFT_T.c2 ASC NULLS LAST, LEFT_T.c3 ASC NULLS LAST";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfJoin(rel));

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, ASCENDING),
                        new RelFieldCollation(1, ASCENDING),
                        new RelFieldCollation(2, ASCENDING)
                ),
                sortNodes.get(0).collation()
        );
        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, ASCENDING),
                        new RelFieldCollation(1, ASCENDING)
                ),
                sortNodes.get(1).collation()
        );
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, collation that is superset of join keys should be propagated as is, if it doesn't include fields from
     * right table.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerPassThroughOrderByLeft10() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + " order by LEFT_T.c1 ASC NULLS FIRST, LEFT_T.c2 ASC NULLS LAST, LEFT_T.c3 ASC NULLS FIRST";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfJoin(rel));

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, ASCENDING, RelFieldCollation.NullDirection.FIRST),
                        new RelFieldCollation(1, ASCENDING),
                        new RelFieldCollation(2, ASCENDING, RelFieldCollation.NullDirection.FIRST)
                ),
                sortNodes.get(0).collation()
        );
        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, ASCENDING, RelFieldCollation.NullDirection.FIRST),
                        new RelFieldCollation(1, ASCENDING)
                ),
                sortNodes.get(1).collation()
        );
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, collation that is superset of join keys, but its prefix contains columns outside of join keys, can't be
     * propagated.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerPassThroughOrderByLeft11() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + " order by LEFT_T.c3, LEFT_T.c2, LEFT_T.c1";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        IgniteSort topSortNode = sortOnTopOfJoin(rel);

        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(2, ASCENDING),
                        new RelFieldCollation(1, ASCENDING),
                        new RelFieldCollation(0, ASCENDING)
                ),
                topSortNode.collation()
        );

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, ASCENDING),
                        new RelFieldCollation(1, ASCENDING)
                ),
                sortNodes.get(0).collation()
        );
        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, ASCENDING),
                        new RelFieldCollation(1, ASCENDING)
                ),
                sortNodes.get(1).collation()
        );
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, collation that is subset of join keys should be propagated as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerPassThroughOrderByLeft12() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + "   and LEFT_T.c3 = RIGHT_T.c3 "
                + " order by LEFT_T.c1, LEFT_T.c2";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfJoin(rel));

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        RelCollation expected = RelCollations.of(
                new RelFieldCollation(0, ASCENDING),
                new RelFieldCollation(1, ASCENDING),
                new RelFieldCollation(2, ASCENDING)
        );

        assertEquals(expected, sortNodes.get(0).collation());
        assertEquals(expected, sortNodes.get(1).collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, collation that is subset of join keys should be propagated as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerPassThroughOrderByLeft13() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + "   and LEFT_T.c3 = RIGHT_T.c3 "
                + " order by LEFT_T.c1 DESC, LEFT_T.c2 DESC";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfJoin(rel));

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        RelCollation expected = RelCollations.of(
                new RelFieldCollation(0, DESCENDING),
                new RelFieldCollation(1, DESCENDING),
                new RelFieldCollation(2, ASCENDING)
        );

        assertEquals(expected, sortNodes.get(0).collation());
        assertEquals(expected, sortNodes.get(1).collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, collation that is subset of join keys should be propagated as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerPassThroughOrderByLeft14() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + "   and LEFT_T.c3 = RIGHT_T.c3 "
                + " order by LEFT_T.c1 DESC, LEFT_T.c2 ASC";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfJoin(rel));

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        RelCollation expected = RelCollations.of(
                new RelFieldCollation(0, DESCENDING),
                new RelFieldCollation(1, ASCENDING),
                new RelFieldCollation(2, ASCENDING)
        );

        assertEquals(expected, sortNodes.get(0).collation());
        assertEquals(expected, sortNodes.get(1).collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, collation that is subset of join keys should be propagated as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerPassThroughOrderByLeft15() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + "   and LEFT_T.c3 = RIGHT_T.c3 "
                + " order by LEFT_T.c1 ASC NULLS LAST, LEFT_T.c2 ASC NULLS LAST";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfJoin(rel));

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        RelCollation expected = RelCollations.of(
                new RelFieldCollation(0, ASCENDING),
                new RelFieldCollation(1, ASCENDING),
                new RelFieldCollation(2, ASCENDING)
        );

        assertEquals(expected, sortNodes.get(0).collation());
        assertEquals(expected, sortNodes.get(1).collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, collation that is subset of join keys should be propagated as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerPassThroughOrderByLeft16() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + "   and LEFT_T.c3 = RIGHT_T.c3 "
                + " order by LEFT_T.c1 ASC NULLS FIRST, LEFT_T.c2 ASC NULLS LAST";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfJoin(rel));

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        RelCollation expected = RelCollations.of(
                new RelFieldCollation(0, ASCENDING, RelFieldCollation.NullDirection.FIRST),
                new RelFieldCollation(1, ASCENDING),
                new RelFieldCollation(2, ASCENDING)
        );

        assertEquals(expected, sortNodes.get(0).collation());
        assertEquals(expected, sortNodes.get(1).collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, collation consisted of join keys only should be propagated as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerPassThroughOrderByRight1() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + " order by RIGHT_T.c1, RIGHT_T.c2";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfJoin(rel));

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        RelCollation expected = RelCollations.of(
                new RelFieldCollation(0, ASCENDING),
                new RelFieldCollation(1, ASCENDING)
        );

        assertEquals(expected, sortNodes.get(0).collation());
        assertEquals(expected, sortNodes.get(1).collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, collation consisted of join keys only should be propagated as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerPassThroughOrderByRight2() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + " order by RIGHT_T.c1 DESC, RIGHT_T.c2 DESC";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfJoin(rel));

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        RelCollation expected = RelCollations.of(
                new RelFieldCollation(0, DESCENDING),
                new RelFieldCollation(1, DESCENDING)
        );

        assertEquals(expected, sortNodes.get(0).collation());
        assertEquals(expected, sortNodes.get(1).collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, collation consisted of join keys only should be propagated as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerPassThroughOrderByRight3() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + " order by RIGHT_T.c1 DESC, RIGHT_T.c2 ASC";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfJoin(rel));

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        RelCollation expected = RelCollations.of(
                new RelFieldCollation(0, DESCENDING),
                new RelFieldCollation(1, ASCENDING)
        );

        assertEquals(expected, sortNodes.get(0).collation());
        assertEquals(expected, sortNodes.get(1).collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, collation consisted of join keys only should be propagated as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerPassThroughOrderByRight4() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + " order by RIGHT_T.c1 ASC NULLS LAST, RIGHT_T.c2 ASC NULLS LAST";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfJoin(rel));

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        RelCollation expected = RelCollations.of(
                new RelFieldCollation(0, ASCENDING),
                new RelFieldCollation(1, ASCENDING)
        );

        assertEquals(expected, sortNodes.get(0).collation());
        assertEquals(expected, sortNodes.get(1).collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, collation consisted of join keys only should be propagated as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerPassThroughOrderByRight5() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + " order by RIGHT_T.c1 ASC NULLS FIRST, RIGHT_T.c2 ASC NULLS LAST";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfJoin(rel));

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        RelCollation expected = RelCollations.of(
                new RelFieldCollation(0, ASCENDING, RelFieldCollation.NullDirection.FIRST),
                new RelFieldCollation(1, ASCENDING)
        );

        assertEquals(expected, sortNodes.get(0).collation());
        assertEquals(expected, sortNodes.get(1).collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>Any collation that contains column from right table which is not part of join keys can't be propagated.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerPassThroughOrderByRight6() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + " order by RIGHT_T.c1, RIGHT_T.c2, RIGHT_T.c3";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        IgniteSort topSortNode = sortOnTopOfJoin(rel);

        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(3, ASCENDING),
                        new RelFieldCollation(4, ASCENDING),
                        new RelFieldCollation(5, ASCENDING)
                ),
                topSortNode.collation()
        );

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        RelCollation expectedBottomCollation = RelCollations.of(
                new RelFieldCollation(0, ASCENDING),
                new RelFieldCollation(1, ASCENDING)
        );

        assertEquals(expectedBottomCollation, sortNodes.get(0).collation());
        assertEquals(expectedBottomCollation, sortNodes.get(1).collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, collation that is subset of join keys should be propagated as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerPassThroughOrderByRight7() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + "   and LEFT_T.c3 = RIGHT_T.c3 "
                + " order by RIGHT_T.c1, RIGHT_T.c2";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfJoin(rel));

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        RelCollation expected = RelCollations.of(
                new RelFieldCollation(0, ASCENDING),
                new RelFieldCollation(1, ASCENDING),
                new RelFieldCollation(2)
        );

        assertEquals(expected, sortNodes.get(0).collation());
        assertEquals(expected, sortNodes.get(1).collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, collation that is subset of join keys should be propagated as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerPassThroughOrderByRight8() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + "   and LEFT_T.c3 = RIGHT_T.c3 "
                + " order by RIGHT_T.c1 DESC, RIGHT_T.c2 DESC";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfJoin(rel));

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        RelCollation expected = RelCollations.of(
                new RelFieldCollation(0, DESCENDING),
                new RelFieldCollation(1, DESCENDING),
                new RelFieldCollation(2, ASCENDING)
        );

        assertEquals(expected, sortNodes.get(0).collation());
        assertEquals(expected, sortNodes.get(1).collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, collation that is subset of join keys should be propagated as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerPassThroughOrderByRight9() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + "   and LEFT_T.c3 = RIGHT_T.c3 "
                + " order by RIGHT_T.c1 DESC, RIGHT_T.c2 ASC";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfJoin(rel));

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        RelCollation expected = RelCollations.of(
                new RelFieldCollation(0, DESCENDING),
                new RelFieldCollation(1, ASCENDING),
                new RelFieldCollation(2, ASCENDING)
        );

        assertEquals(expected, sortNodes.get(0).collation());
        assertEquals(expected, sortNodes.get(1).collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, collation that is subset of join keys should be propagated as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerPassThroughOrderByRight10() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + "   and LEFT_T.c3 = RIGHT_T.c3 "
                + " order by RIGHT_T.c1 ASC NULLS LAST, RIGHT_T.c2 ASC NULLS LAST";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfJoin(rel));

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        RelCollation expected = RelCollations.of(
                new RelFieldCollation(0, ASCENDING),
                new RelFieldCollation(1, ASCENDING),
                new RelFieldCollation(2, ASCENDING)
        );

        assertEquals(expected, sortNodes.get(0).collation());
        assertEquals(expected, sortNodes.get(1).collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, collation that is subset of join keys should be propagated as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerPassThroughOrderByRight11() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + "   and LEFT_T.c3 = RIGHT_T.c3 "
                + " order by RIGHT_T.c1 ASC NULLS FIRST, RIGHT_T.c2 ASC NULLS LAST";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfJoin(rel));

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        RelCollation expected = RelCollations.of(
                new RelFieldCollation(0, ASCENDING, RelFieldCollation.NullDirection.FIRST),
                new RelFieldCollation(1, ASCENDING),
                new RelFieldCollation(2, ASCENDING)
        );

        assertEquals(expected, sortNodes.get(0).collation());
        assertEquals(expected, sortNodes.get(1).collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of right join, collation consisted of left keys can't be propagated.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testRightPassThroughOrderByLeft1() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + " right join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + " order by LEFT_T.c1, LEFT_T.c2";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        IgniteSort topSortNode = sortOnTopOfJoin(rel);

        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, ASCENDING),
                        new RelFieldCollation(1, ASCENDING)
                ),
                topSortNode.collation()
        );

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        RelCollation expected = RelCollations.of(
                new RelFieldCollation(0, ASCENDING),
                new RelFieldCollation(1, ASCENDING)
        );

        assertEquals(expected, sortNodes.get(0).collation());
        assertEquals(expected, sortNodes.get(1).collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of right join, collation that is superset of join keys can't be propagated.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testRightPassThroughOrderByLeft2() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + " right join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + " order by LEFT_T.c1, LEFT_T.c2, LEFT_T.c3";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        IgniteSort topSortNode = sortOnTopOfJoin(rel);

        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, ASCENDING),
                        new RelFieldCollation(1, ASCENDING),
                        new RelFieldCollation(2, ASCENDING)
                ),
                topSortNode.collation()
        );

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        RelCollation expected = RelCollations.of(
                new RelFieldCollation(0, ASCENDING),
                new RelFieldCollation(1, ASCENDING)
        );

        assertEquals(expected, sortNodes.get(0).collation());
        assertEquals(expected, sortNodes.get(1).collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of right join, collation consisted of join keys only should be propagated as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testRightPassThroughOrderByRight1() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + " right join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + " order by RIGHT_T.c1, RIGHT_T.c2";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfJoin(rel));

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        RelCollation expected = RelCollations.of(
                new RelFieldCollation(0, ASCENDING),
                new RelFieldCollation(1, ASCENDING)
        );

        assertEquals(expected, sortNodes.get(0).collation());
        assertEquals(expected, sortNodes.get(1).collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of right join, collation consisted of join keys only should be propagated as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testRightPassThroughOrderByRight2() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + " right join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + " order by RIGHT_T.c1 DESC, RIGHT_T.c2 DESC";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfJoin(rel));

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        RelCollation expected = RelCollations.of(
                new RelFieldCollation(0, DESCENDING),
                new RelFieldCollation(1, DESCENDING)
        );

        assertEquals(expected, sortNodes.get(0).collation());
        assertEquals(expected, sortNodes.get(1).collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of right join, collation consisted of join keys only should be propagated as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testRightPassThroughOrderByRight3() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + " right join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + " order by RIGHT_T.c1 DESC, RIGHT_T.c2 ASC";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfJoin(rel));

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        RelCollation expected = RelCollations.of(
                new RelFieldCollation(0, DESCENDING),
                new RelFieldCollation(1, ASCENDING)
        );

        assertEquals(expected, sortNodes.get(0).collation());
        assertEquals(expected, sortNodes.get(1).collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of right join, collation consisted of join keys only should be propagated as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testRightPassThroughOrderByRight4() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + " right join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + " order by RIGHT_T.c1 ASC NULLS LAST, RIGHT_T.c2 ASC NULLS LAST";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfJoin(rel));

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        RelCollation expected = RelCollations.of(
                new RelFieldCollation(0, ASCENDING),
                new RelFieldCollation(1, ASCENDING)
        );

        assertEquals(expected, sortNodes.get(0).collation());
        assertEquals(expected, sortNodes.get(1).collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of right join, collation consisted of join keys only should be propagated as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testRightPassThroughOrderByRight5() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + " right join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + " order by RIGHT_T.c1 ASC NULLS FIRST, RIGHT_T.c2 ASC NULLS LAST";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfJoin(rel));

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        RelCollation expected = RelCollations.of(
                new RelFieldCollation(0, ASCENDING, RelFieldCollation.NullDirection.FIRST),
                new RelFieldCollation(1, ASCENDING)
        );

        assertEquals(expected, sortNodes.get(0).collation());
        assertEquals(expected, sortNodes.get(1).collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of left join, collation consisted of join keys of right table can't be propagated.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testLeftPassThroughOrderByRight1() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  left join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + " order by RIGHT_T.c1, RIGHT_T.c2";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(3, ASCENDING),
                        new RelFieldCollation(4, ASCENDING)
                ),
                sortOnTopOfJoin(rel).collation()
        );

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        RelCollation expected = RelCollations.of(
                new RelFieldCollation(0, ASCENDING),
                new RelFieldCollation(1, ASCENDING)
        );

        assertEquals(expected, sortNodes.get(0).collation());
        assertEquals(expected, sortNodes.get(1).collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of left join, collation consisted of join keys of right table can't be propagated.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testLeftPassThroughOrderByRight2() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  left join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + "   and LEFT_T.c3 = RIGHT_T.c3 "
                + " order by RIGHT_T.c1, RIGHT_T.c2";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(3, ASCENDING),
                        new RelFieldCollation(4, ASCENDING)
                ),
                sortOnTopOfJoin(rel).collation()
        );

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        RelCollation expected = RelCollations.of(
                new RelFieldCollation(0, ASCENDING),
                new RelFieldCollation(1, ASCENDING),
                new RelFieldCollation(2, ASCENDING)
        );

        assertEquals(expected, sortNodes.get(0).collation());
        assertEquals(expected, sortNodes.get(1).collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of full join, any collation can't be propagated.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testFullPassThroughOrderByLeft1() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  full join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + " order by LEFT_T.c1, LEFT_T.c2";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, ASCENDING),
                        new RelFieldCollation(1, ASCENDING)
                ),
                sortOnTopOfJoin(rel).collation()
        );

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        RelCollation expected = RelCollations.of(
                new RelFieldCollation(0, ASCENDING),
                new RelFieldCollation(1, ASCENDING)
        );

        assertEquals(expected, sortNodes.get(0).collation());
        assertEquals(expected, sortNodes.get(1).collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of full join, any collation can't be propagated.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testFullPassThroughOrderByRight1() throws Exception {
        IgniteSchema schema = createSchema(
                createTableA("LEFT_T"),
                createTableA("RIGHT_T")
        );

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  full join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + " order by RIGHT_T.c1, RIGHT_T.c2";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(3, ASCENDING),
                        new RelFieldCollation(4, ASCENDING)
                ),
                sortOnTopOfJoin(rel).collation()
        );

        List<IgniteSort> sortNodes = sortOnTopOfScan(rel);

        RelCollation expected = RelCollations.of(
                new RelFieldCollation(0, ASCENDING),
                new RelFieldCollation(1, ASCENDING)
        );

        assertEquals(expected, sortNodes.get(0).collation());
        assertEquals(expected, sortNodes.get(1).collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, left collation that is superset or equal to join keys could be derived as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerDerivePreserveLeft1() throws Exception {
        TestTable left = createTableB("LEFT_T",
                b -> b.sortedIndex()
                        .name("idx")
                        .addColumn("C1", Collation.ASC_NULLS_LAST)
                        .addColumn("C2", Collation.ASC_NULLS_LAST)
                        .end()
        );

        IgniteSchema schema = createSchema(left, createTableB("RIGHT_T"));

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 ";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfScan(rel, "LEFT_T"));
        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, ASCENDING),
                        new RelFieldCollation(1, ASCENDING)
                ),
                sortOnTopOfScan(rel, "RIGHT_T").collation()
        );
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, left collation that is superset or equal to join keys could be derived as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerDerivePreserveLeft2() throws Exception {
        TestTable left = createTableB("LEFT_T",
                b -> b.sortedIndex()
                        .name("idx")
                        .addColumn("C1", Collation.DESC_NULLS_FIRST)
                        .addColumn("C2", Collation.DESC_NULLS_FIRST)
                        .end()
        );

        IgniteSchema schema = createSchema(left, createTableB("RIGHT_T"));

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 ";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfScan(rel, "LEFT_T"));
        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, DESCENDING),
                        new RelFieldCollation(1, DESCENDING)
                ),
                sortOnTopOfScan(rel, "RIGHT_T").collation()
        );
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, left collation that is superset or equal to join keys could be derived as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerDerivePreserveLeft3() throws Exception {
        TestTable left = createTableB("LEFT_T",
                b -> b.sortedIndex()
                        .name("idx")
                        .addColumn("C1", Collation.DESC_NULLS_FIRST)
                        .addColumn("C2", Collation.ASC_NULLS_LAST)
                        .end()
        );

        IgniteSchema schema = createSchema(left, createTableB("RIGHT_T"));

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 ";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfScan(rel, "LEFT_T"));
        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, DESCENDING),
                        new RelFieldCollation(1, ASCENDING)
                ),
                sortOnTopOfScan(rel, "RIGHT_T").collation()
        );
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, left collation that is superset or equal to join keys could be derived as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerDerivePreserveLeft4() throws Exception {
        TestTable left = createTableB("LEFT_T",
                b -> b.sortedIndex()
                        .name("idx")
                        .addColumn("C1", Collation.ASC_NULLS_LAST)
                        .addColumn("C2", Collation.ASC_NULLS_LAST)
                        .end()
        );

        IgniteSchema schema = createSchema(left, createTableB("RIGHT_T"));

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 ";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfScan(rel, "LEFT_T"));
        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, ASCENDING),
                        new RelFieldCollation(1, ASCENDING)
                ),
                sortOnTopOfScan(rel, "RIGHT_T").collation()
        );
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, left collation that is superset or equal to join keys could be derived as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerDerivePreserveLeft5() throws Exception {
        TestTable left = createTableB("LEFT_T",
                b -> b.sortedIndex()
                        .name("idx")
                        .addColumn("C1", Collation.ASC_NULLS_FIRST)
                        .addColumn("C2", Collation.ASC_NULLS_LAST)
                        .end()
        );

        IgniteSchema schema = createSchema(left, createTableB("RIGHT_T"));

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 ";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfScan(rel, "LEFT_T"));
        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, ASCENDING, RelFieldCollation.NullDirection.FIRST),
                        new RelFieldCollation(1, ASCENDING)
                ),
                sortOnTopOfScan(rel, "RIGHT_T").collation()
        );
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, left collation that is superset or equal to join keys could be derived as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerDerivePreserveLeft6() throws Exception {
        TestTable left = createTableB("LEFT_T",
                b -> b.sortedIndex()
                        .name("idx")
                        .addColumn("C1", Collation.ASC_NULLS_LAST)
                        .addColumn("C2", Collation.ASC_NULLS_LAST)
                        .addColumn("C3", Collation.ASC_NULLS_LAST)
                        .end()
        );

        IgniteSchema schema = createSchema(left, createTableB("RIGHT_T"));

        String sql = "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 ";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfScan(rel, "LEFT_T"));
        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, ASCENDING),
                        new RelFieldCollation(1, ASCENDING)
                ),
                sortOnTopOfScan(rel, "RIGHT_T").collation()
        );
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, left collation that is superset or equal to join keys could be derived as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerDerivePreserveLeft7() throws Exception {
        TestTable left = createTableB("LEFT_T",
                b -> b.sortedIndex()
                        .name("idx")
                        .addColumn("C1", Collation.DESC_NULLS_FIRST)
                        .addColumn("C2", Collation.DESC_NULLS_FIRST)
                        .addColumn("C3", Collation.DESC_NULLS_FIRST)
                        .end()
        );

        IgniteSchema schema = createSchema(left, createTableB("RIGHT_T"));

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 ";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfScan(rel, "LEFT_T"));
        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, DESCENDING),
                        new RelFieldCollation(1, DESCENDING)
                ),
                sortOnTopOfScan(rel, "RIGHT_T").collation()
        );
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, left collation that is superset or equal to join keys could be derived as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerDerivePreserveLeft8() throws Exception {
        TestTable left = createTableB("LEFT_T",
                b -> b.sortedIndex()
                        .name("idx")
                        .addColumn("C1", Collation.DESC_NULLS_FIRST)
                        .addColumn("C2", Collation.ASC_NULLS_LAST)
                        .addColumn("C3", Collation.ASC_NULLS_LAST)
                        .end()
        );

        IgniteSchema schema = createSchema(left, createTableB("RIGHT_T"));

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 ";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfScan(rel, "LEFT_T"));
        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, DESCENDING),
                        new RelFieldCollation(1, ASCENDING)
                ),
                sortOnTopOfScan(rel, "RIGHT_T").collation()
        );
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, left collation that is superset or equal, but its prefix contains columns outside of join keys, can't be
     * derived.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerDerivePreserveLeft9() throws Exception {
        TestTable left = createTableB("LEFT_T",
                b -> b.sortedIndex()
                        .name("idx")
                        .addColumn("C3", Collation.ASC_NULLS_LAST)
                        .addColumn("C1", Collation.ASC_NULLS_LAST)
                        .addColumn("C2", Collation.ASC_NULLS_LAST)
                        .end()
        );

        IgniteSchema schema = createSchema(left, createTableB("RIGHT_T"));

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 ";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, ASCENDING),
                        new RelFieldCollation(1, ASCENDING)
                ),
                sortOnTopOfScan(rel, "LEFT_T").collation()
        );
        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, ASCENDING),
                        new RelFieldCollation(1, ASCENDING)
                ),
                sortOnTopOfScan(rel, "RIGHT_T").collation()
        );
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, left collation that is superset or equal to join keys could be derived as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerDerivePreserveLeft10() throws Exception {
        TestTable left = createTableB("LEFT_T",
                b -> b.sortedIndex()
                        .name("idx")
                        .addColumn("C1", Collation.ASC_NULLS_FIRST)
                        .addColumn("C2", Collation.ASC_NULLS_LAST)
                        .addColumn("C3", Collation.ASC_NULLS_LAST)
                        .end()
        );

        IgniteSchema schema = createSchema(left, createTableB("RIGHT_T"));

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 ";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfScan(rel, "LEFT_T"));
        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, ASCENDING, RelFieldCollation.NullDirection.FIRST),
                        new RelFieldCollation(1, ASCENDING)
                ),
                sortOnTopOfScan(rel, "RIGHT_T").collation()
        );
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, left collation that is subset of join keys, is not suitable.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerDerivePreserveLeft11() throws Exception {
        TestTable left = createTableB("LEFT_T",
                b -> b.sortedIndex()
                        .name("idx")
                        .addColumn("C1", Collation.ASC_NULLS_LAST)
                        .addColumn("C2", Collation.ASC_NULLS_LAST)
                        .end()
        );

        IgniteSchema schema = createSchema(left, createTableB("RIGHT_T"));

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + "   and LEFT_T.c3 = RIGHT_T.c3 ";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, ASCENDING),
                        new RelFieldCollation(1, ASCENDING),
                        new RelFieldCollation(2, ASCENDING)
                ),
                sortOnTopOfScan(rel, "LEFT_T").collation()
        );
        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, ASCENDING),
                        new RelFieldCollation(1, ASCENDING),
                        new RelFieldCollation(2, ASCENDING)
                ),
                sortOnTopOfScan(rel, "RIGHT_T").collation()
        );
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, left collation that is superset of join keys and has a common prefix could be derived as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerDerivePreserveLeft12() throws Exception {
        TestTable left = createTableB("LEFT_T",
                b -> b.sortedIndex()
                        .name("idx")
                        .addColumn("C1", Collation.ASC_NULLS_LAST)
                        .addColumn("C2", Collation.ASC_NULLS_LAST)
                        .addColumn("C3", Collation.ASC_NULLS_LAST)
                        .end()
        );

        IgniteSchema schema = createSchema(left, createTableB("RIGHT_T"));

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + " order by LEFT_T.c1 NULLS LAST, LEFT_T.c2 NULLS LAST, LEFT_T.c3 NULLS LAST";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfJoin(rel));

        assertNull(sortOnTopOfScan(rel, "LEFT_T"));
        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, ASCENDING),
                        new RelFieldCollation(1, ASCENDING)
                ),
                sortOnTopOfScan(rel, "RIGHT_T").collation()
        );
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, left collation that is superset of join keys, and there is no a common prefix, could not be derived.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerDerivePreserveLeft13() throws Exception {
        TestTable left = createTableB("LEFT_T",
                b -> b.sortedIndex()
                        .name("idx")
                        .addColumn("C3", Collation.ASC_NULLS_LAST)
                        .addColumn("C1", Collation.ASC_NULLS_LAST)
                        .addColumn("C2", Collation.ASC_NULLS_LAST)
                        .end()
        );

        IgniteSchema schema = createSchema(left, createTableB("RIGHT_T"));

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + " order by LEFT_T.c3 NULLS LAST, LEFT_T.c1 NULLS LAST, LEFT_T.c2 NULLS LAST";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(2, ASCENDING),
                        new RelFieldCollation(0, ASCENDING),
                        new RelFieldCollation(1, ASCENDING)
                ),
                sortOnTopOfJoin(rel).collation()
        );

        RelCollation expected = RelCollations.of(
                new RelFieldCollation(0, ASCENDING),
                new RelFieldCollation(1, ASCENDING)
        );

        assertEquals(expected, sortOnTopOfScan(rel, "LEFT_T").collation());
        assertEquals(expected, sortOnTopOfScan(rel, "RIGHT_T").collation());
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, right collation that consists of join keys only could be derived as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerDerivePreserveRight1() throws Exception {
        TestTable right = createTableB("RIGHT_T",
                b -> b.sortedIndex()
                        .name("idx")
                        .addColumn("C1", Collation.ASC_NULLS_LAST)
                        .addColumn("C2", Collation.ASC_NULLS_LAST)
                        .end()
                );

        IgniteSchema schema = createSchema(createTableB("LEFT_T"), right);

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 ";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfScan(rel, "RIGHT_T"));
        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, ASCENDING),
                        new RelFieldCollation(1, ASCENDING)
                ),
                sortOnTopOfScan(rel, "LEFT_T").collation()
        );
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, right collation that consists of join keys only could be derived as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerDerivePreserveRight2() throws Exception {
        TestTable right = createTableB("RIGHT_T",
                b -> b.sortedIndex()
                        .name("idx")
                        .addColumn("C1", Collation.DESC_NULLS_FIRST)
                        .addColumn("C2", Collation.DESC_NULLS_FIRST)
                        .end()
        );

        IgniteSchema schema = createSchema(createTableB("LEFT_T"), right);

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 ";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfScan(rel, "RIGHT_T"));
        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, DESCENDING),
                        new RelFieldCollation(1, DESCENDING)
                ),
                sortOnTopOfScan(rel, "LEFT_T").collation()
        );
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, right collation that consists of join keys only could be derived as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerDerivePreserveRight3() throws Exception {
        TestTable right = createTableB("RIGHT_T",
                b -> b.sortedIndex()
                        .name("idx")
                        .addColumn("C1", Collation.DESC_NULLS_LAST)
                        .addColumn("C2", Collation.ASC_NULLS_FIRST)
                        .end()
        );

        IgniteSchema schema = createSchema(createTableB("LEFT_T"), right);

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 ";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfScan(rel, "RIGHT_T"));
        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, DESCENDING, RelFieldCollation.NullDirection.LAST),
                        new RelFieldCollation(1, ASCENDING, RelFieldCollation.NullDirection.FIRST)
                ),
                sortOnTopOfScan(rel, "LEFT_T").collation()
        );
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, right collation that consists of join keys only could be derived as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerDerivePreserveRight4() throws Exception {
        TestTable right = createTableB("RIGHT_T",
                b -> b.sortedIndex()
                        .name("idx")
                        .addColumn("C1", Collation.ASC_NULLS_FIRST)
                        .addColumn("C2", Collation.ASC_NULLS_FIRST)
                        .end()
        );

        IgniteSchema schema = createSchema(createTableB("LEFT_T"), right);

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 ";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfScan(rel, "RIGHT_T"));
        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, ASCENDING, RelFieldCollation.NullDirection.FIRST),
                        new RelFieldCollation(1, ASCENDING, RelFieldCollation.NullDirection.FIRST)
                ),
                sortOnTopOfScan(rel, "LEFT_T").collation()
        );
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, right collation that consists of join keys only could be derived as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerDerivePreserveRight5() throws Exception {
        TestTable right = createTableB("RIGHT_T",
                b -> b.sortedIndex()
                        .name("idx")
                        .addColumn("C1", Collation.ASC_NULLS_FIRST)
                        .addColumn("C2", Collation.ASC_NULLS_FIRST)
                        .end()
        );

        IgniteSchema schema = createSchema(createTableB("LEFT_T"), right);

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 ";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfScan(rel, "RIGHT_T"));
        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, ASCENDING, RelFieldCollation.NullDirection.FIRST),
                        new RelFieldCollation(1, ASCENDING, RelFieldCollation.NullDirection.FIRST)
                ),
                sortOnTopOfScan(rel, "LEFT_T").collation()
        );
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, right collation that is superset of join keys could be derived as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerDerivePreserveRight6() throws Exception {
        TestTable right = createTableB("RIGHT_T",
                b -> b.sortedIndex()
                        .name("idx")
                        .addColumn("C1", Collation.ASC_NULLS_FIRST)
                        .addColumn("C2", Collation.ASC_NULLS_FIRST)
                        .addColumn("C3", Collation.ASC_NULLS_FIRST)
                        .end()
        );

        IgniteSchema schema = createSchema(createTableB("LEFT_T"), right);

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 ";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfScan(rel, "RIGHT_T"));
        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, ASCENDING, RelFieldCollation.NullDirection.FIRST),
                        new RelFieldCollation(1, ASCENDING, RelFieldCollation.NullDirection.FIRST)
                ),
                sortOnTopOfScan(rel, "LEFT_T").collation()
        );
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, right collation that is superset of join keys could be derived as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerDerivePreserveRight7() throws Exception {
        TestTable right = createTableB("RIGHT_T",
                b -> b.sortedIndex()
                        .name("idx")
                        .addColumn("C1", Collation.DESC_NULLS_LAST)
                        .addColumn("C2", Collation.DESC_NULLS_LAST)
                        .addColumn("C3", Collation.DESC_NULLS_FIRST)
                        .end()
        );

        IgniteSchema schema = createSchema(createTableB("LEFT_T"), right);

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 ";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfScan(rel, "RIGHT_T"));
        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, DESCENDING, RelFieldCollation.NullDirection.LAST),
                        new RelFieldCollation(1, DESCENDING, RelFieldCollation.NullDirection.LAST)
                ),
                sortOnTopOfScan(rel, "LEFT_T").collation()
        );
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, right collation that is superset of join keys could be derived as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerDerivePreserveRight8() throws Exception {
        TestTable right = createTableB("RIGHT_T",
                b -> b.sortedIndex()
                        .name("idx")
                        .addColumn("C1", Collation.DESC_NULLS_LAST)
                        .addColumn("C2", Collation.ASC_NULLS_FIRST)
                        .addColumn("C3", Collation.ASC_NULLS_FIRST)
                        .end()
        );

        IgniteSchema schema = createSchema(createTableB("LEFT_T"), right);

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 ";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfScan(rel, "RIGHT_T"));
        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, DESCENDING, RelFieldCollation.NullDirection.LAST),
                        new RelFieldCollation(1, ASCENDING, RelFieldCollation.NullDirection.FIRST)
                ),
                sortOnTopOfScan(rel, "LEFT_T").collation()
        );
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, right collation that is superset of join keys could be derived as is.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerDerivePreserveRight9() throws Exception {
        TestTable right = createTableB("RIGHT_T",
                b -> b.sortedIndex()
                        .name("idx")
                        .addColumn("C1", Collation.ASC_NULLS_FIRST)
                        .addColumn("C2", Collation.ASC_NULLS_FIRST)
                        .addColumn("C3", Collation.ASC_NULLS_FIRST)
                        .end()
        );

        IgniteSchema schema = createSchema(createTableB("LEFT_T"), right);

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 ";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfScan(rel, "RIGHT_T"));
        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, ASCENDING, RelFieldCollation.NullDirection.FIRST),
                        new RelFieldCollation(1, ASCENDING, RelFieldCollation.NullDirection.FIRST)
                ),
                sortOnTopOfScan(rel, "LEFT_T").collation()
        );
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, right collation that is subset of join keys can't be derived.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerDerivePreserveRight10() throws Exception {
        TestTable right = createTableB("RIGHT_T",
                b -> b.sortedIndex()
                        .name("idx")
                        .addColumn("C1", Collation.ASC_NULLS_LAST)
                        .addColumn("C2", Collation.ASC_NULLS_LAST)
                        .end()
        );

        IgniteSchema schema = createSchema(createTableB("LEFT_T"), right);

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + "   and LEFT_T.c3 = RIGHT_T.c3 ";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, ASCENDING),
                        new RelFieldCollation(1, ASCENDING),
                        new RelFieldCollation(2, ASCENDING)
                ),
                sortOnTopOfScan(rel, "RIGHT_T").collation()
        );
        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, ASCENDING),
                        new RelFieldCollation(1, ASCENDING),
                        new RelFieldCollation(2, ASCENDING)
                ),
                sortOnTopOfScan(rel, "LEFT_T").collation()
        );
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, left collation should be preferred if it fully covers join keys.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerDeriveMixed1() throws Exception {
        TestTable left = createTableB("LEFT_T",
                b -> b.sortedIndex()
                        .name("idx1")
                        .addColumn("C1", Collation.ASC_NULLS_LAST)
                        .addColumn("C2", Collation.ASC_NULLS_LAST)
                        .end()
        );

        TestTable right = createTableB("RIGHT_T",
                b -> b.sortedIndex()
                        .name("idx2")
                        .addColumn("C2", Collation.ASC_NULLS_LAST)
                        .addColumn("C1", Collation.ASC_NULLS_LAST)
                        .end()
        );

        IgniteSchema schema = createSchema(left, right);

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 ";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertNull(sortOnTopOfScan(rel, "LEFT_T"));
        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(0, ASCENDING),
                        new RelFieldCollation(1, ASCENDING)
                ),
                sortOnTopOfScan(rel, "RIGHT_T").collation()
        );
    }

    /**
     * Test verifies the collation propagation from a parent node.
     *
     * <p>In case of inner join, left collation should be preferred if it fully covers join keys.
     *
     * @throws Exception In case of any unexpected error.
     */
    @Test
    public void testInnerDeriveMixed2() throws Exception {
        TestTable left = createTableB("LEFT_T",
                b -> b.sortedIndex()
                        .name("idx1")
                        .addColumn("C1", Collation.ASC_NULLS_FIRST)
                        .addColumn("C2", Collation.ASC_NULLS_FIRST)
                        .end()
        );

        TestTable right = createTableB("RIGHT_T",
                b -> b.sortedIndex()
                        .name("idx2")
                        .addColumn("C3", Collation.ASC_NULLS_FIRST)
                        .addColumn("C2", Collation.ASC_NULLS_FIRST)
                        .addColumn("C1", Collation.ASC_NULLS_FIRST)
                        .end()
        );

        IgniteSchema schema = createSchema(left, right);

        String sql = ""
                + "select * "
                + "  from LEFT_T "
                + "  join RIGHT_T "
                + "    on LEFT_T.c1 = RIGHT_T.c1 "
                + "   and LEFT_T.c2 = RIGHT_T.c2 "
                + "   and LEFT_T.c3 = RIGHT_T.c3 ";

        IgniteRel rel = physicalPlan(sql, schema, DISABLED_RULES);

        assertEquals(
                RelCollations.of(
                        new RelFieldCollation(2, ASCENDING, RelFieldCollation.NullDirection.FIRST),
                        new RelFieldCollation(1, ASCENDING, RelFieldCollation.NullDirection.FIRST),
                        new RelFieldCollation(0, ASCENDING, RelFieldCollation.NullDirection.FIRST)
                ),
                sortOnTopOfScan(rel, "LEFT_T").collation()
        );
        assertNull(sortOnTopOfScan(rel, "RIGHT_T"));
    }

    /**
     * Search for sort node whose input is arbitrary scan node.
     *
     * @param root The root of the tree to search.
     * @return The list of the sort nodes or an empty list, if there is no such nodes.
     */
    private List<IgniteSort> sortOnTopOfScan(IgniteRel root) {
        return findNodes(root, byClass(IgniteSort.class)
                .and(node -> node.getInputs().size() == 1 && node.getInput(0) instanceof IgniteTableScan));
    }

    /**
     * Search for sort node whose input is scan of the given table.
     *
     * @param root The root of the tree to search.
     * @param tableName Desired table name.
     * @return The sort node or {@code null}, if there is no such node.
     */
    private IgniteSort sortOnTopOfScan(IgniteRel root, String tableName) {
        List<IgniteSort> sortNodes = findNodes(root, byClass(IgniteSort.class)
                .and(node -> node.getInputs().size() == 1 && node.getInput(0) instanceof IgniteTableScan
                        && node.getInput(0).getTable().unwrap(TestTable.class).name().equals(tableName)));

        if (sortNodes.size() > 1) {
            throw new AssertionError("Unexpected count of sort nodes: exp<=1, act=" + sortNodes.size());
        }

        return sortNodes.isEmpty() ? null : sortNodes.get(0);
    }

    /**
     * Search for sort node whose input is join node.
     *
     * @param root The root of the tree to search.
     * @return The sort node or {@code null}, if there is no such node.
     */
    private IgniteSort sortOnTopOfJoin(IgniteRel root) {
        List<IgniteSort> sortNodes = findNodes(root, byClass(IgniteSort.class)
                .and(node -> node.getInputs().size() == 1 && node.getInput(0) instanceof Join));

        if (sortNodes.size() > 1) {
            throw new AssertionError("Unexpected count of sort nodes: exp<=1, act=" + sortNodes.size());
        }

        return sortNodes.isEmpty() ? null : sortNodes.get(0);
    }

    private static TestTable createTableA(String tableName) {
        return TestBuilders.table()
                .name(tableName)
                .addColumn("C1", NativeTypes.INT32)
                .addColumn("C2", NativeTypes.INT32)
                .addColumn("C3", NativeTypes.INT32)
                .distribution(IgniteDistributions.single())
                .build();
    }

    private static TestTable createTableB(String tableName) {
        return createTableB(tableName, ignore -> {
        });
    }

    private static TestTable createTableB(String tableName, Consumer<TableBuilder> changer) {
        TableBuilder tableBuilder = TestBuilders.table()
                .name(tableName)
                .distribution(IgniteDistributions.single())
                .addColumn("C1", NativeTypes.INT32)
                .addColumn("C2", NativeTypes.INT32)
                .addColumn("C3", NativeTypes.INT32);

        changer.accept(tableBuilder);

        return tableBuilder.build();
    }
}
