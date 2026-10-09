package com.toolbox.tools.lineageviewer;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LineageAcceptanceTest {
    @Test void invalidReplacementKeepsPreviousGraph() {
        LineageViewerService service = new LineageViewerService();
        service.rebuildFromJson("[{\"name\":\"job\",\"inputTables\":[\"a\"],\"outputTables\":[\"b\"]}]");
        LineageGraph before = service.graph();
        for (String invalid : Arrays.asList("{}", "null", "42", "{\"records\":{}}", "[42]", "", " ")) {
            assertFalse(new LineageViewerController(service).rebuild(Collections.<String,Object>singletonMap("json", invalid)).isSuccess(), invalid);
            assertSame(before, service.graph(), "Invalid data must not erase existing graph");
        }
    }

    @Test void duplicateSqlNamesCannotSilentlyOverwriteTraceback() {
        assertThrows(IllegalArgumentException.class, () -> new LineageDataLoader().parse(
            "[{\"name\":\"same\",\"inputTables\":[\"a\"],\"outputTables\":[\"b\"]},{\"name\":\"same\",\"inputTables\":[\"c\"],\"outputTables\":[\"d\"]}]"));
    }

    @Test void repeatedOutputNamesCountOneProducerPerRecord() {
        LineageGraph g = LineageGraph.build(new LineageDataLoader().parse(
            "[{\"name\":\"job\",\"inputTables\":[\"a\",\"a\"],\"outputTables\":[\"b\",\"b\"]}]"));
        assertEquals(1, g.producersOf("b").size());
        assertEquals(1, g.node("b").getProducingSqlCount());
        assertEquals(1, g.consumersOf("a").size());
    }

    @Test void tableCardsDoNotRescanEverySqlForEachTable() {
        final int[] reads = {0};
        List<LineageSqlRecord> rows = new ArrayList<>();
        for (int i=0; i<200; i++) {
            rows.add(new LineageSqlRecord("test", "j"+i, "", "select 1", Arrays.asList("a"+i), Arrays.asList("b"+i)) {
                @Override public List<String> getInputTables() { reads[0]++; return super.getInputTables(); }
            });
        }
        LineageGraph g = LineageGraph.build(rows);
        reads[0]=0;
        for (LineageTableNode node : g.allNodes()) g.consumersOf(node.getName());
        assertEquals(0, reads[0], "Query must use the built consumer index instead of tables x SQL scans");
    }
}
