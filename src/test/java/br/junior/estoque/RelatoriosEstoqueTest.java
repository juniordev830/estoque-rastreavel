package br.junior.estoque;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RelatoriosEstoqueTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC);
    private CatalogoProdutos catalogo;
    private EstoqueLocalizado estoque;
    private RelatoriosEstoque relatorios;

    @BeforeEach void preparar() {
        catalogo = new CatalogoProdutos(CLOCK);
        catalogo.cadastrarProduto("A", "Teclado", "TI",
                CatalogoProdutos.Unidade.UNIDADE, 5, 20, new BigDecimal("10.50"));
        catalogo.cadastrarProduto("B", "Mouse", "TI",
                CatalogoProdutos.Unidade.UNIDADE, 2, 10, new BigDecimal("5.00"));
        estoque = new EstoqueLocalizado(catalogo, CLOCK);
        estoque.cadastrarLocal("L1", "Depósito");
        estoque.cadastrarLocal("L2", "Loja");
        relatorios = new RelatoriosEstoque(catalogo, estoque, CLOCK);
    }

    @Test void resumoVazioTemZeroUnidades() {
        var r = relatorios.resumo();
        assertEquals(2, r.produtosAtivos());
        assertEquals(2, r.locaisAtivos());
        assertEquals(0, r.unidadesFisicas());
        assertEquals(0, r.unidadesReservadas());
        assertEquals(BigDecimal.ZERO, r.valorEstoque());
        assertEquals(CLOCK.instant(), r.geradoEm());
    }

    @Test void resumoSomaEstoqueDosLocais() {
        estoque.receber("A", "L1", 10, "NF");
        estoque.receber("A", "L2", 5, "NF");
        estoque.receber("B", "L1", 2, "NF");
        var r = relatorios.resumo();
        assertEquals(17, r.unidadesFisicas());
        assertEquals(0, r.unidadesReservadas());
        assertEquals(0, new BigDecimal("167.50").compareTo(r.valorEstoque()));
    }

    @Test void resumoDescontaReservasDoDisponivel() {
        estoque.receber("A", "L1", 10, "NF");
        estoque.reservar("A", "L1", 4, "PED");
        var r = relatorios.resumo();
        assertEquals(10, r.unidadesFisicas());
        assertEquals(4, r.unidadesReservadas());
        assertEquals(6, r.unidadesDisponiveis());
        assertEquals(1, r.reservasAtivas());
    }

    @Test void resumoDesconsideraReservaCancelada() {
        estoque.receber("A", "L1", 10, "NF");
        var reserva = estoque.reservar("A", "L1", 4, "PED");
        estoque.cancelarReserva(reserva.id());
        assertEquals(0, relatorios.resumo().reservasAtivas());
        assertEquals(10, relatorios.resumo().unidadesDisponiveis());
    }

    @Test void sugereReposicaoDeProdutosAbaixoDoMinimo() {
        estoque.receber("A", "L1", 2, "NF");
        estoque.receber("B", "L1", 3, "NF");
        var sugestoes = relatorios.sugestoesReposicao();
        assertEquals(1, sugestoes.size());
        assertEquals("A", sugestoes.get(0).sku());
        assertEquals(18, sugestoes.get(0).sugerido());
        assertEquals(0, new BigDecimal("189.00")
                .compareTo(sugestoes.get(0).custoEstimado()));
    }

    @Test void reservaPodeDispararReposicao() {
        estoque.receber("A", "L1", 6, "NF");
        estoque.receber("B", "L1", 2, "NF");
        estoque.reservar("A", "L1", 3, "PED");
        assertEquals(1, relatorios.sugestoesReposicao().size());
        assertEquals(17, relatorios.sugestoesReposicao().get(0).sugerido());
    }

    @Test void produtoInativoNaoEntraEmReposicao() {
        catalogo.alterarAtivo("A", false);
        assertEquals(1, relatorios.sugestoesReposicao().size());
        assertEquals("B", relatorios.sugestoesReposicao().get(0).sku());
    }

    @Test void posicoesCriticasConsideramDisponibilidade() {
        estoque.receber("A", "L1", 10, "NF");
        estoque.reservar("A", "L1", 8, "PED");
        estoque.receber("B", "L1", 3, "NF");
        assertEquals(1, relatorios.posicoesCriticas().size());
        assertEquals("A", relatorios.posicoesCriticas().get(0).sku());
    }

    @Test void csvDePosicoesTemCabecalhoEDados() {
        estoque.receber("A", "L1", 5, "NF");
        var csv = relatorios.exportarPosicoesCsv();
        assertTrue(csv.startsWith("sku;produto;local;fisico;reservado;disponivel"));
        assertTrue(csv.contains("\"A\";\"Teclado\";\"L1\";5;0;5;10.50"));
    }

    @Test void csvDeEventosContemMovimentacoes() {
        estoque.receber("A", "L1", 5, "NF-1");
        estoque.retirar("A", "L1", 2, "VENDA");
        var csv = relatorios.exportarEventosCsv();
        assertTrue(csv.contains("ENTRADA"));
        assertTrue(csv.contains("SAIDA"));
        assertTrue(csv.contains("\"NF-1\""));
        assertTrue(csv.contains(";3\n"));
    }

    @Test void csvProtegeFormulaNaReferencia() {
        estoque.receber("A", "L1", 5, "=2+2");
        assertTrue(relatorios.exportarEventosCsv().contains("\"'=2+2\""));
    }

    @Test void csvEscapaAspasNaReferencia() {
        estoque.receber("A", "L1", 5, "NF \"123\"");
        assertTrue(relatorios.exportarEventosCsv().contains("\"NF \"\"123\"\"\""));
    }

    @Test void markdownTemIndicadoresEReposicao() {
        estoque.receber("A", "L1", 2, "NF");
        var texto = relatorios.relatorioMarkdown();
        assertTrue(texto.contains("# Relatório de Estoque"));
        assertTrue(texto.contains("Unidades físicas: 2"));
        assertTrue(texto.contains("| A | Teclado | 2 | 18 |"));
    }

    @Test void markdownEscapaSeparadoresDoNome() {
        catalogo.atualizarProduto("A", "Teclado | Especial", "TI",
                CatalogoProdutos.Unidade.UNIDADE, 5, 20, new BigDecimal("10.50"));
        assertTrue(relatorios.relatorioMarkdown().contains("Teclado \\| Especial"));
    }

    @Test void relatorioNaoAlteraEstoque() {
        estoque.receber("A", "L1", 10, "NF");
        relatorios.resumo();
        relatorios.sugestoesReposicao();
        relatorios.exportarPosicoesCsv();
        relatorios.exportarEventosCsv();
        relatorios.relatorioMarkdown();
        assertEquals(10, estoque.saldoTotal("A"));
        assertEquals(1, estoque.historico(null, null).size());
    }

    @Test void reposicaoZeraQuandoDisponivelIgualaMinimo() {
        estoque.receber("A", "L1", 5, "NF");
        estoque.receber("B", "L1", 2, "NF");
        assertTrue(relatorios.sugestoesReposicao().isEmpty());
    }
}
