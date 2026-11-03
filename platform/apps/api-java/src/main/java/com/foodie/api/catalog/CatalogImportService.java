package com.foodie.api.catalog;

import com.foodie.api.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Time;
import java.text.Normalizer;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Importação em massa do catálogo por CSV (E23). O arquivo é validado inteiro antes de qualquer
 * escrita; qualquer erro cancela a importação. O cabeçalho é:
 *
 * <pre>categoria,produto,descricao,preco,disponivel,estoque,disponivel_de,disponivel_ate</pre>
 *
 * <p>Uma linha com `produto` vazio cria só a categoria. Produto existente na mesma categoria é
 * atualizado (preço, descrição, disponibilidade, estoque e janela); produto novo é criado. O
 * `dryRun` devolve as ações e os erros sem gravar nada.</p>
 */
@Service
public class CatalogImportService {
    private static final int MAX_ROWS = 500;
    private static final int MAX_CSV_CHARS = 200_000;
    private static final int MAX_PRICE_CENTS = 10_000_000;
    private static final int MAX_STOCK = 100_000;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final JdbcTemplate jdbc;
    private final MenuService menu;

    public CatalogImportService(JdbcTemplate jdbc, MenuService menu) {
        this.jdbc = jdbc;
        this.menu = menu;
    }

    public record Result(boolean dryRun, boolean applied, int categoriesCreated, int productsCreated,
                         int productsUpdated, List<Line> lines, List<Problem> errors) {}

    public record Line(int line, String action, String category, String product, Integer priceCents) {}

    public record Problem(int line, String message) {}

    private record CsvRow(int line, List<String> cells) {}

    private record ProductRow(int line, String category, String product, String description, int priceCents,
                              boolean available, Integer stock, LocalTime from, LocalTime until) {}

    @Transactional
    public Result importCsv(long restaurantId, String csv, boolean dryRun) {
        if (csv == null || csv.isBlank()) throw new ApiException(400, "Envie o CSV do catálogo");
        if (csv.length() > MAX_CSV_CHARS) throw new ApiException(400, "CSV muito grande (máximo de 200 mil caracteres)");
        List<CsvRow> rows = parse(csv, delimiter(csv));
        if (rows.size() < 2) throw new ApiException(400, "O CSV precisa de cabeçalho e pelo menos uma linha");
        Header header = header(rows.getFirst());
        List<Problem> errors = new ArrayList<>();
        List<String> categories = new ArrayList<>();
        List<ProductRow> products = new ArrayList<>();
        Set<String> seenCategories = new LinkedHashSet<>();
        Set<String> seenProducts = new LinkedHashSet<>();
        int dataRows = 0;
        for (int i = 1; i < rows.size(); i++) {
            CsvRow row = rows.get(i);
            if (row.cells().stream().allMatch(String::isBlank)) continue;
            dataRows++;
            if (dataRows > MAX_ROWS) throw new ApiException(400, "CSV com mais de " + MAX_ROWS + " linhas");
            parseRow(row, header, categories, products, seenCategories, seenProducts, errors);
        }
        if (dataRows == 0) throw new ApiException(400, "O CSV não tem linhas de dados");
        if (!errors.isEmpty()) return new Result(dryRun, false, 0, 0, 0, List.of(), errors);

        Map<String, Long> categoryIds = new LinkedHashMap<>();
        List<Line> lines = new ArrayList<>();
        int categoriesCreated = 0;
        for (String category : categories) {
            Long existing = findCategory(restaurantId, category);
            if (existing != null) {
                categoryIds.put(key(category), existing);
                lines.add(new Line(0, "category-existing", category, "", null));
            } else {
                categoriesCreated++;
                lines.add(new Line(0, "category-create", category, "", null));
                if (!dryRun) {
                    Map<String, Object> created = menu.createCategory(restaurantId, category);
                    categoryIds.put(key(category), ((Number) created.get("id")).longValue());
                }
            }
        }

        int productsCreated = 0;
        int productsUpdated = 0;
        for (ProductRow product : products) {
            Long categoryId = categoryIds.get(key(product.category()));
            Long productId = categoryId == null ? null : findProduct(restaurantId, categoryId, product.product());
            boolean update = productId != null;
            if (update) productsUpdated++;
            else productsCreated++;
            lines.add(new Line(product.line(), update ? "update" : "create", product.category(), product.product(), product.priceCents()));
            if (dryRun) continue;
            if (categoryId == null) throw new ApiException(400, "Categoria não encontrada: " + product.category());
            if (update) {
                updateProduct(restaurantId, productId, product);
            } else {
                Map<String, Object> created = menu.createProduct(restaurantId, categoryId, product.product(), product.description(), product.priceCents());
                long id = ((Number) created.get("id")).longValue();
                updateProduct(restaurantId, id, product);
            }
        }
        return new Result(dryRun, !dryRun, categoriesCreated, productsCreated, productsUpdated, lines, List.of());
    }

    private void parseRow(CsvRow row, Header header, List<String> categories, List<ProductRow> products,
                          Set<String> seenCategories, Set<String> seenProducts, List<Problem> errors) {
        String category = cell(row, header.category()).strip();
        if (category.length() < 2) {
            errors.add(new Problem(row.line(), "categoria deve ter entre 2 e 120 caracteres"));
            return;
        }
        if (category.length() > 120) {
            errors.add(new Problem(row.line(), "categoria deve ter no máximo 120 caracteres"));
            return;
        }
        if (seenCategories.add(key(category))) categories.add(category);
        String product = header.product() < 0 ? "" : cell(row, header.product()).strip();
        if (product.isEmpty()) return;
        if (product.length() < 2 || product.length() > 160) {
            errors.add(new Problem(row.line(), "produto deve ter entre 2 e 160 caracteres"));
            return;
        }
        String description = header.description() < 0 ? "" : cell(row, header.description()).strip();
        if (description.length() > 500) {
            errors.add(new Problem(row.line(), "descrição deve ter no máximo 500 caracteres"));
            return;
        }
        Integer price;
        try {
            price = price(cell(row, header.price()));
        } catch (RuntimeException error) {
            errors.add(new Problem(row.line(), error.getMessage()));
            return;
        }
        if (price == null) {
            errors.add(new Problem(row.line(), "preço é obrigatório para produto"));
            return;
        }
        Boolean available;
        try {
            available = available(header.available() < 0 ? "" : cell(row, header.available()));
        } catch (RuntimeException error) {
            errors.add(new Problem(row.line(), error.getMessage()));
            return;
        }
        Integer stock;
        try {
            stock = stock(header.stock() < 0 ? "" : cell(row, header.stock()));
        } catch (RuntimeException error) {
            errors.add(new Problem(row.line(), error.getMessage()));
            return;
        }
        LocalTime from = null;
        LocalTime until = null;
        try {
            from = time(header.from() < 0 ? "" : cell(row, header.from()));
            until = time(header.until() < 0 ? "" : cell(row, header.until()));
        } catch (RuntimeException error) {
            errors.add(new Problem(row.line(), error.getMessage()));
            return;
        }
        if ((from == null) != (until == null)) {
            errors.add(new Problem(row.line(), "informe disponivel_de e disponivel_ate juntos"));
            return;
        }
        if (from != null && !from.isBefore(until)) {
            errors.add(new Problem(row.line(), "disponivel_de deve ser antes de disponivel_ate"));
            return;
        }
        String productKey = key(category) + "\u0000" + key(product);
        if (!seenProducts.add(productKey)) {
            errors.add(new Problem(row.line(), "produto repetido na mesma categoria: " + product));
            return;
        }
        products.add(new ProductRow(row.line(), category, product, description, price, available, stock, from, until));
    }

    private static Header header(CsvRow row) {
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < row.cells().size(); i++) index.putIfAbsent(normalize(row.cells().get(i)), i);
        int category = first(index, "categoria", "category");
        int product = first(index, "produto", "product");
        if (category < 0 || product < 0) throw new ApiException(400, "Cabeçalho precisa de categoria e produto");
        return new Header(category, product, first(index, "descricao", "description"),
            first(index, "preco", "price"), first(index, "disponivel", "available"),
            first(index, "estoque", "stock"), first(index, "disponivel_de", "available_from", "inicio"),
            first(index, "disponivel_ate", "available_until", "fim"));
    }

    private static int first(Map<String, Integer> index, String... names) {
        for (String name : names) {
            Integer found = index.get(normalize(name));
            if (found != null) return found;
        }
        return -1;
    }

    private static String cell(CsvRow row, int index) {
        return index >= 0 && index < row.cells().size() ? row.cells().get(index).strip() : "";
    }

    private Long findCategory(long restaurantId, String name) {
        List<Long> rows = jdbc.query("SELECT id FROM categories WHERE restaurant_id = ? AND LOWER(name) = LOWER(?) ORDER BY id LIMIT 1",
            (rs, row) -> rs.getLong(1), restaurantId, name);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private Long findProduct(long restaurantId, long categoryId, String name) {
        List<Long> rows = jdbc.query("SELECT p.id FROM products p WHERE p.restaurant_id = ? AND p.category_id = ? AND LOWER(p.name) = LOWER(?) ORDER BY p.id LIMIT 1",
            (rs, row) -> rs.getLong(1), restaurantId, categoryId, name);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private void updateProduct(long restaurantId, long productId, ProductRow product) {
        int changed = jdbc.update("UPDATE products SET description = ?, price_cents = ?, available = ?, stock = ?, available_from = ?, available_until = ? "
                + "WHERE id = ? AND restaurant_id = ?",
            product.description(), product.priceCents(), product.available(), product.stock(),
            product.from() == null ? null : Time.valueOf(product.from()),
            product.until() == null ? null : Time.valueOf(product.until()), productId, restaurantId);
        if (changed == 0) throw new ApiException(404, "Produto não encontrado");
    }

    private static Integer price(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.replace("R$", "").replace("\u00A0", "").replace(" ", "");
        if (value.contains(",")) value = value.replace(".", "").replace(',', '.');
        BigDecimal amount;
        try {
            amount = new BigDecimal(value);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("preço inválido: " + raw);
        }
        BigDecimal cents = amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP);
        if (cents.signum() <= 0 || cents.compareTo(BigDecimal.valueOf(MAX_PRICE_CENTS)) > 0) {
            throw new IllegalArgumentException("preço deve estar entre 0,01 e 100.000,00");
        }
        return cents.intValueExact();
    }

    private static Boolean available(String raw) {
        if (raw == null || raw.isBlank()) return true;
        String value = normalize(raw);
        if (value.equals("sim") || value.equals("s") || value.equals("true") || value.equals("1")) return true;
        if (value.equals("nao") || value.equals("n") || value.equals("false") || value.equals("0")) return false;
        throw new IllegalArgumentException("disponivel deve ser sim/nao");
    }

    private static Integer stock(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            int value = Integer.parseInt(raw);
            if (value < 0 || value > MAX_STOCK) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("estoque deve ser um inteiro entre 0 e " + MAX_STOCK);
        }
    }

    private static LocalTime time(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalTime.parse(raw, TIME);
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("horário deve estar no formato HH:MM");
        }
    }

    private static String key(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .strip().toLowerCase(Locale.ROOT);
    }

    private static char delimiter(String csv) {
        String first = csv.startsWith("\uFEFF") ? csv.substring(1) : csv;
        int end = first.indexOf('\n');
        if (end >= 0) first = first.substring(0, end);
        long semicolons = first.chars().filter(ch -> ch == ';').count();
        long commas = first.chars().filter(ch -> ch == ',').count();
        if (semicolons == 0 && commas == 0) throw new ApiException(400, "CSV sem separador (use vírgula ou ponto e vírgula)");
        return semicolons > commas ? ';' : ',';
    }

    private static List<CsvRow> parse(String csv, char delimiter) {
        String text = csv.startsWith("\uFEFF") ? csv.substring(1) : csv;
        List<CsvRow> rows = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        int line = 1;
        int start = 1;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    if (ch == '\n') line++;
                    cell.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == delimiter) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else if (ch == '\n') {
                cells.add(cell.toString());
                cell.setLength(0);
                rows.add(new CsvRow(start, cells));
                cells = new ArrayList<>();
                line++;
                start = line;
            } else if (ch != '\r') {
                cell.append(ch);
            }
        }
        if (quoted) throw new ApiException(400, "CSV com aspas não fechadas");
        if (cell.length() > 0 || !cells.isEmpty()) {
            cells.add(cell.toString());
            rows.add(new CsvRow(start, cells));
        }
        return rows;
    }

    private record Header(int category, int product, int description, int price, int available, int stock,
                          int from, int until) {}
}
