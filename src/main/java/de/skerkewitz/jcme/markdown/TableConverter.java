package de.skerkewitz.jcme.markdown;

import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Convert HTML {@code <table>} elements to GitHub-flavored Markdown pipe tables.
 *
 * <p>Mirrors the Python {@code TableConverter}: handles {@code rowspan}/{@code colspan} by
 * padding the grid, escapes pipe characters in cell text, and renders newlines as
 * {@code <br/>} so cell content remains on one line.
 */
public class TableConverter {

    public String convertTable(Element table, String childText, Set<String> parents, MarkdownConverter ctx) {
        // Collect all rows (works whether or not <thead>/<tbody> wraps them).
        List<List<Element>> rows = new ArrayList<>();
        for (Element tr : table.select("tr")) {
            List<Element> cells = new ArrayList<>();
            for (Element cell : tr.children()) {
                String tag = cell.tagName().toLowerCase();
                if (tag.equals("td") || tag.equals("th")) cells.add(cell);
            }
            if (!cells.isEmpty()) rows.add(cells);
        }
        if (rows.isEmpty()) return "";

        List<List<CellRender>> grid = pad(rows, ctx);
        boolean hasHeader = !rows.isEmpty() && rows.get(0).stream()
                .allMatch(c -> "th".equalsIgnoreCase(c.tagName()));

        return "\n\n" + render(grid, hasHeader) + "\n\n";
    }

    /** Convert a single <td> or <th> cell to its inline-only markdown text. */
    public String convertCell(Element cell, String childText, Set<String> parents, MarkdownConverter ctx) {
        return normalizeCellText(childText);
    }

    private static String normalizeCellText(String text) {
        String normalized = text.replace("|", "\\|").replace("\n", "<br/>");
        if (normalized.endsWith("<br/>")) normalized = normalized.substring(0, normalized.length() - 5);
        if (normalized.startsWith("<br/>")) normalized = normalized.substring(5);
        return normalized.trim();
    }

    private List<List<CellRender>> pad(List<List<Element>> rows, MarkdownConverter ctx) {
        List<List<CellRender>> padded = new ArrayList<>();
        Map<Long, CellRender> occupied = new HashMap<>();
        for (int r = 0; r < rows.size(); r++) {
            List<Element> row = rows.get(r);
            List<CellRender> cur = new ArrayList<>();
            int c = 0;
            for (Element cell : row) {
                while (occupied.containsKey(key(r, c))) {
                    cur.add(occupied.remove(key(r, c)));
                    c++;
                }
                int rs = parseSpan(cell.attr("rowspan"));
                int cs = parseSpan(cell.attr("colspan"));
                String cellText = ctx.processChildren(cell, MarkdownConverter.markInline(Set.of(cell.tagName().toLowerCase())));
                CellRender main = new CellRender(cell.tagName(), normalizeCellText(cellText));
                cur.add(main);
                for (int i = 1; i < cs; i++) cur.add(new CellRender(cell.tagName(), ""));
                for (int i = 0; i < rs; i++) {
                    for (int j = 0; j < cs; j++) {
                        if (i == 0 && j == 0) continue;
                        occupied.put(key(r + i, c + j), new CellRender(cell.tagName(), ""));
                    }
                }
                c += cs;
            }
            while (occupied.containsKey(key(r, c))) {
                cur.add(occupied.remove(key(r, c)));
                c++;
            }
            padded.add(cur);
        }
        return padded;
    }

    private static String render(List<List<CellRender>> grid, boolean hasHeader) {
        int width = grid.stream().mapToInt(List::size).max().orElse(0);
        // Pad ragged rows to the same column count
        for (List<CellRender> row : grid) {
            while (row.size() < width) row.add(new CellRender("td", ""));
        }
        StringBuilder out = new StringBuilder();
        if (hasHeader) {
            renderRow(out, grid.get(0));
            renderSeparator(out, width);
            for (int i = 1; i < grid.size(); i++) renderRow(out, grid.get(i));
        } else {
            // Empty header row when no <th>; matches markdownify behavior
            out.append('|');
            for (int i = 0; i < width; i++) out.append("   |");
            out.append('\n');
            renderSeparator(out, width);
            for (List<CellRender> row : grid) renderRow(out, row);
        }
        return out.toString().stripTrailing();
    }

    private static void renderRow(StringBuilder out, List<CellRender> row) {
        out.append('|');
        for (CellRender cell : row) out.append(' ').append(cell.text).append(" |");
        out.append('\n');
    }

    private static void renderSeparator(StringBuilder out, int columns) {
        out.append('|');
        for (int i = 0; i < columns; i++) out.append(" --- |");
        out.append('\n');
    }

    private static int parseSpan(String value) {
        if (value == null || value.isEmpty()) return 1;
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed < 1 ? 1 : parsed;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static long key(int row, int col) {
        return ((long) row << 32) | (col & 0xffffffffL);
    }

    private record CellRender(String tagName, String text) {}
}
