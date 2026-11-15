package br.junior.estoque;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CatalogoProdutosTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC);
    private final CatalogoProdutos catalogo = new CatalogoProdutos(CLOCK);

    private CatalogoProdutos.Produto cadastrar(String sku) {
        return catalogo.cadastrarProduto(sku, "Mouse", "Periféricos",
                CatalogoProdutos.Unidade.UNIDADE, 3, 15, new BigDecimal("25.50"));
    }

    @Test void cadastroNormalizaSkuENome() {
        var p = catalogo.cadastrarProduto(" ab-01 ", " Mouse ", " Periféricos ",
                CatalogoProdutos.Unidade.UNIDADE, 3, 15, new BigDecimal("25.50"));
        assertEquals("AB-01", p.sku());
        assertEquals("Mouse", p.nome());
        assertEquals("Periféricos", p.categoria());
        assertEquals(CLOCK.instant(), p.criadoEm());
        assertTrue(p.ativo());
    }

    @Test void rejeitaSkuInvalido() {
        assertThrows(IllegalArgumentException.class, () -> cadastrar(""));
        assertThrows(IllegalArgumentException.class, () -> cadastrar("a b"));
        assertThrows(IllegalArgumentException.class, () -> cadastrar("á"));
        assertThrows(IllegalArgumentException.class, () -> cadastrar("a/b"));
    }

    @Test void rejeitaDuplicidadeCaseInsensitive() {
        cadastrar("ab-01");
        assertThrows(IllegalStateException.class, () -> cadastrar("AB-01"));
        assertEquals(1, catalogo.listarProdutos(null, null, null).size());
    }

    @Test void validaFaixasDeEstoque() {
        assertThrows(IllegalArgumentException.class, () ->
                catalogo.cadastrarProduto("A", "Mouse", "TI",
                        CatalogoProdutos.Unidade.UNIDADE, -1, 10, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () ->
                catalogo.cadastrarProduto("A", "Mouse", "TI",
                        CatalogoProdutos.Unidade.UNIDADE, 10, 5, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () ->
                catalogo.cadastrarProduto("A", "Mouse", "TI",
                        CatalogoProdutos.Unidade.UNIDADE, 0, 0, BigDecimal.ZERO));
    }

    @Test void validaCustoMonetario() {
        assertThrows(IllegalArgumentException.class, () ->
                catalogo.cadastrarProduto("A", "Mouse", "TI",
                        CatalogoProdutos.Unidade.UNIDADE, 0, 10, new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class, () ->
                catalogo.cadastrarProduto("A", "Mouse", "TI",
                        CatalogoProdutos.Unidade.UNIDADE, 0, 10, new BigDecimal("1.234")));
        assertEquals(new BigDecimal("1.00"),
                catalogo.cadastrarProduto("A", "Mouse", "TI",
                        CatalogoProdutos.Unidade.UNIDADE, 0, 10,
                        BigDecimal.ONE).custoUnitario());
    }

    @Test void calculaReposicao() {
        var p = cadastrar("A");
        assertTrue(p.precisaRepor(2));
        assertFalse(p.precisaRepor(3));
        assertEquals(13, p.quantidadeSugerida(2));
        assertEquals(0, p.quantidadeSugerida(3));
    }

    @Test void atualizaSemPerderCriacao() {
        var antes = cadastrar("A");
        var depois = catalogo.atualizarProduto("a", "Teclado", "TI",
                CatalogoProdutos.Unidade.CAIXA, 5, 20, new BigDecimal("100.00"));
        assertEquals(antes.criadoEm(), depois.criadoEm());
        assertEquals("Teclado", depois.nome());
        assertEquals(5, depois.estoqueMinimo());
        assertEquals(CatalogoProdutos.Unidade.CAIXA, depois.unidade());
    }

    @Test void desativaEReativa() {
        cadastrar("A");
        assertFalse(catalogo.alterarAtivo("A", false).ativo());
        assertTrue(catalogo.listarProdutos(null, null, true).isEmpty());
        assertTrue(catalogo.alterarAtivo("A", true).ativo());
    }

    @Test void filtraProdutosPorNomeCategoriaEAtividade() {
        cadastrar("A");
        catalogo.cadastrarProduto("B", "Monitor", "Vídeo",
                CatalogoProdutos.Unidade.UNIDADE, 1, 10, BigDecimal.TEN);
        assertEquals(1, catalogo.listarProdutos("MOU", null, null).size());
        assertEquals(1, catalogo.listarProdutos(null, "vídeo", null).size());
        catalogo.alterarAtivo("B", false);
        assertEquals(1, catalogo.listarProdutos(null, null, true).size());
    }

    @Test void retornaProdutosOrdenadosPorSku() {
        cadastrar("Z");
        cadastrar("B");
        cadastrar("A");
        assertEquals(java.util.List.of("A", "B", "Z"),
                catalogo.listarProdutos(null, null, null).stream()
                        .map(CatalogoProdutos.Produto::sku).toList());
    }

    @Test void produtoInexistenteGeraErro() {
        assertThrows(java.util.NoSuchElementException.class,
                () -> catalogo.obterProduto("NAO"));
        assertThrows(java.util.NoSuchElementException.class,
                () -> catalogo.atualizarProduto("NAO", "X", "Y",
                        CatalogoProdutos.Unidade.UNIDADE, 0, 1, BigDecimal.ONE));
    }

    @Test void cadastraFornecedorNormalizado() {
        var f = catalogo.cadastrarFornecedor("for-01", "Fornecedor", " VENDAS@EXEMPLO.COM ");
        assertEquals("FOR-01", f.codigo());
        assertEquals("vendas@exemplo.com", f.email());
        assertTrue(f.ativo());
    }

    @Test void fornecedorRejeitaEmailInvalido() {
        assertThrows(IllegalArgumentException.class,
                () -> catalogo.cadastrarFornecedor("F", "Fornecedor", "invalido"));
        assertThrows(IllegalArgumentException.class,
                () -> catalogo.cadastrarFornecedor("F", "Fornecedor", "a@dominio"));
    }

    @Test void fornecedorDuplicadoNaoSobrescreve() {
        catalogo.cadastrarFornecedor("F", "Primeiro", "a@exemplo.com");
        assertThrows(IllegalStateException.class,
                () -> catalogo.cadastrarFornecedor("f", "Segundo", "b@exemplo.com"));
        assertEquals("Primeiro", catalogo.obterFornecedor("F").nome());
    }

    @Test void vinculaEDesvinculaFornecedor() {
        cadastrar("A");
        catalogo.cadastrarFornecedor("F", "Fornecedor", "a@exemplo.com");
        catalogo.vincularFornecedor("A", "F");
        assertEquals(1, catalogo.fornecedoresDe("A").size());
        assertThrows(IllegalStateException.class, () -> catalogo.vincularFornecedor("A", "F"));
        catalogo.desvincularFornecedor("A", "F");
        assertTrue(catalogo.fornecedoresDe("A").isEmpty());
    }

    @Test void naoVinculaInativos() {
        cadastrar("A");
        catalogo.cadastrarFornecedor("F", "Fornecedor", "a@exemplo.com");
        catalogo.atualizarFornecedor("F", "Fornecedor", "a@exemplo.com", false);
        assertThrows(IllegalStateException.class, () -> catalogo.vincularFornecedor("A", "F"));
        catalogo.atualizarFornecedor("F", "Fornecedor", "a@exemplo.com", true);
        catalogo.alterarAtivo("A", false);
        assertThrows(IllegalStateException.class, () -> catalogo.vincularFornecedor("A", "F"));
    }

    @Test void atualizaFornecedorEFiltraAtivos() {
        catalogo.cadastrarFornecedor("F", "Fornecedor", "a@exemplo.com");
        var novo = catalogo.atualizarFornecedor("F", "Novo", "b@exemplo.com", false);
        assertEquals("Novo", novo.nome());
        assertEquals("b@exemplo.com", novo.email());
        assertEquals(0, catalogo.listarFornecedores(true).size());
        assertEquals(1, catalogo.listarFornecedores(false).size());
    }

    @Test void auditoriaPreservaEventos() {
        cadastrar("A");
        catalogo.alterarAtivo("A", false);
        var eventos = catalogo.auditoria();
        assertEquals(2, eventos.size());
        assertEquals("Criado", eventos.get(0).acao());
        assertEquals("Desativado", eventos.get(1).acao());
        assertThrows(UnsupportedOperationException.class, eventos::clear);
    }
}
