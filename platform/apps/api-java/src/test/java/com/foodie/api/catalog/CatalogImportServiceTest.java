package com.foodie.api.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/** CSV da loja: valida tudo antes de gravar, cria/atualiza e devolve o preview do dry-run. */
class CatalogImportServiceTest {
    private final JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final MenuService menu = org.mockito.Mockito.mock(MenuService.class);
    private final CatalogImportService service = new CatalogImportService(jdbc, menu);

    @Test
    void dryRunShowsWhatWouldBeCreatedWithoutWriting() {
        noCategories();
        noProducts();

        CatalogImportService.Result result = service.importCsv(7L,
            "categoria,produto,descricao,preco,disponivel,estoque,disponivel_de,disponivel_ate\n"
                + "Bebidas,Suco,Natural,9.90,sim,10,11:00,15:00\n"
                + "Sobremesas,,,\n", true);

        assertTrue(result.dryRun());
        assertFalse(result.applied());
        assertEquals(2, result.categoriesCreated());
        assertEquals(1, result.productsCreated());
        assertEquals(0, result.productsUpdated());
        assertEquals(List.of(), result.errors());
        assertTrue(result.lines().stream().anyMatch(line -> "category-create".equals(line.action()) && "Sobremesas".equals(line.category())));
        assertTrue(result.lines().stream().anyMatch(line -> "create".equals(line.action()) && "Suco".equals(line.product()) && line.priceCents() == 990));
        verifyNoInteractions(menu);
    }

    @Test
    void actualImportCreatesCategoryAndProductWithAllFields() {
        noCategories();
        noProducts();
        when(menu.createCategory(7L, "Bebidas")).thenReturn(Map.of("id", 10L));
        when(menu.createProduct(eq(7L), eq(10L), eq("Suco"), eq("Natural"), eq(990))).thenReturn(Map.of("id", 20L));
        when(jdbc.update(any(String.class), any(Object[].class))).thenReturn(1);

        CatalogImportService.Result result = service.importCsv(7L,
            "categoria,produto,descricao,preco,disponivel,estoque,disponivel_de,disponivel_ate\n"
                + "Bebidas,Suco,Natural,9.90,sim,10,11:00,15:00\n", false);

        assertTrue(result.applied());
        assertEquals(1, result.categoriesCreated());
        assertEquals(1, result.productsCreated());
        verify(menu).createCategory(7L, "Bebidas");
        verify(menu).createProduct(7L, 10L, "Suco", "Natural", 990);
        verify(jdbc).update(argThat(sql -> sql != null && sql.contains("UPDATE products")), any(Object[].class));
    }

    @Test
    void existingProductIsUpdatedInPlace() {
        when(jdbc.query(argThat(sql -> sql != null && sql.contains("FROM categories")), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of(10L));
        when(jdbc.query(argThat(sql -> sql != null && sql.contains("FROM products")), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of(20L));
        when(jdbc.update(any(String.class), any(Object[].class))).thenReturn(1);

        CatalogImportService.Result result = service.importCsv(7L,
            "categoria,produto,descricao,preco\nBebidas,Suco,Natural,9.90\n", false);

        assertEquals(0, result.categoriesCreated());
        assertEquals(0, result.productsCreated());
        assertEquals(1, result.productsUpdated());
        verify(menu, never()).createProduct(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
        verify(jdbc).update(argThat(sql -> sql != null && sql.contains("UPDATE products")), any(Object[].class));
    }

    @Test
    void semicolonAndQuotedDescriptionAreAccepted() {
        noCategories();
        noProducts();

        CatalogImportService.Result result = service.importCsv(7L,
            "categoria;produto;descricao;preco\nBebidas;Suco;\"Natural, sem açúcar\";9,90\n", true);

        assertEquals(0, result.errors().size());
        assertEquals(1, result.productsCreated());
    }

    @Test
    void invalidPriceCancelsTheWholeImport() {
        CatalogImportService.Result result = service.importCsv(7L,
            "categoria,produto,descricao,preco\nBebidas,Suco,Natural,0\n", true);

        assertFalse(result.applied());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().getFirst().message().contains("preço"));
        verifyNoInteractions(menu);
    }

    @Test
    void duplicateProductInTheSameCategoryIsAnError() {
        CatalogImportService.Result result = service.importCsv(7L,
            "categoria,produto,descricao,preco\nBebidas,Suco,Natural,9.90\nBebidas,Suco,Outro,8.00\n", true);

        assertFalse(result.applied());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().getFirst().message().contains("repetido"));
    }

    @Test
    void invalidWindowIsAnError() {
        CatalogImportService.Result result = service.importCsv(7L,
            "categoria,produto,descricao,preco,disponivel_de,disponivel_ate\nBebidas,Suco,Natural,9.90,15:00,11:00\n", true);

        assertFalse(result.applied());
        assertTrue(result.errors().getFirst().message().contains("antes"));
    }

    @Test
    void missingHeaderIsRejected() {
        ApiException error = assertThrows(ApiException.class,
            () -> service.importCsv(7L, "nome,preco\nSuco,9,90\n", true));

        assertEquals(400, error.status());
        assertTrue(error.getMessage().contains("cabeçalho") || error.getMessage().contains("Cabeçalho"));
    }

    private void noCategories() {
        when(jdbc.query(argThat(sql -> sql != null && sql.contains("FROM categories")), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of());
    }

    private void noProducts() {
        when(jdbc.query(argThat(sql -> sql != null && sql.contains("FROM products")), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of());
    }
}
