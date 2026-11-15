package br.junior.estoque;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LotesValidadeTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate HOJE = LocalDate.of(2026, 11, 8);
    private CatalogoProdutos catalogo;
    private LotesValidade lotes;

    @BeforeEach void preparar() {
        catalogo = new CatalogoProdutos(CLOCK);
        catalogo.cadastrarProduto("A", "Leite", "Alimentos",
                CatalogoProdutos.Unidade.UNIDADE, 2, 10, new BigDecimal("4.50"));
        lotes = new LotesValidade(catalogo, CLOCK);
    }

    @Test void cadastraLoteNormalizado() {
        var l = lotes.receber("a", " lote-1 ", HOJE.plusDays(5), 10);
        assertEquals("A", l.sku());
        assertEquals("LOTE-1", l.numero());
        assertEquals(10, l.saldo());
        assertEquals(CLOCK.instant(), l.recebidoEm());
    }

    @Test void rejeitaLoteDuplicado() {
        lotes.receber("A", "L1", HOJE.plusDays(5), 10);
        assertThrows(IllegalStateException.class,
                () -> lotes.receber("a", "l1", HOJE.plusDays(5), 1));
        assertEquals(10, lotes.saldoTotal("A"));
    }

    @Test void rejeitaLoteJaVencido() {
        assertThrows(IllegalArgumentException.class,
                () -> lotes.receber("A", "L1", HOJE.minusDays(1), 10));
        assertTrue(lotes.listar(null, true).isEmpty());
    }

    @Test void aceitaLoteComValidadeHoje() {
        lotes.receber("A", "L1", HOJE, 3);
        assertEquals(3, lotes.disponivel("A", HOJE));
        assertEquals(0, lotes.disponivel("A", HOJE.plusDays(1)));
    }

    @Test void validaQuantidade() {
        assertThrows(IllegalArgumentException.class,
                () -> lotes.receber("A", "L1", HOJE.plusDays(1), 0));
        assertThrows(IllegalArgumentException.class,
                () -> lotes.receber("A", "L1", HOJE.plusDays(1), -1));
        assertThrows(IllegalArgumentException.class,
                () -> lotes.consumir("A", 0, "PED"));
    }

    @Test void naoRecebeProdutoInativo() {
        catalogo.alterarAtivo("A", false);
        assertThrows(IllegalStateException.class,
                () -> lotes.receber("A", "L1", HOJE.plusDays(1), 2));
    }

    @Test void consomePorValidadeMaisProxima() {
        lotes.receber("A", "L2", HOJE.plusDays(10), 10);
        lotes.receber("A", "L1", HOJE.plusDays(2), 4);
        var movimentos = lotes.consumir("A", 6, "PED");
        assertEquals(2, movimentos.size());
        assertEquals("L1", movimentos.get(0).numero());
        assertEquals(4, movimentos.get(0).quantidade());
        assertEquals("L2", movimentos.get(1).numero());
        assertEquals(2, movimentos.get(1).quantidade());
        assertEquals(8, lotes.saldoTotal("A"));
    }

    @Test void consumoParcialMantemSaldo() {
        lotes.receber("A", "L1", HOJE.plusDays(5), 10);
        lotes.consumir("A", 3, "PED");
        assertEquals(7, lotes.obter("A", "L1").saldo());
        assertEquals(10, lotes.obter("A", "L1").recebido());
    }

    @Test void consumoInsuficienteNaoAlteraNada() {
        lotes.receber("A", "L1", HOJE.plusDays(5), 2);
        assertThrows(IllegalStateException.class, () -> lotes.consumir("A", 3, "PED"));
        assertEquals(2, lotes.obter("A", "L1").saldo());
        assertTrue(lotes.historico("A").isEmpty());
    }

    @Test void consumoComReferenciaVaziaFalha() {
        lotes.receber("A", "L1", HOJE.plusDays(5), 2);
        assertThrows(IllegalArgumentException.class, () -> lotes.consumir("A", 1, ""));
        assertEquals(2, lotes.saldoTotal("A"));
    }

    @Test void historicoRegistraConsumos() {
        lotes.receber("A", "L1", HOJE.plusDays(5), 10);
        lotes.consumir("A", 2, "PED-1");
        var evento = lotes.historico("A").get(0);
        assertEquals("PED-1", evento.referencia());
        assertEquals(CLOCK.instant(), evento.quando());
        assertEquals(2, evento.quantidade());
    }

    @Test void lotesZeradosPodemSerFiltrados() {
        lotes.receber("A", "L1", HOJE.plusDays(5), 1);
        lotes.consumir("A", 1, "PED");
        assertEquals(0, lotes.listar("A", false).size());
        assertEquals(1, lotes.listar("A", true).size());
    }

    @Test void consultaVencimentoDentroDoPrazo() {
        lotes.receber("A", "L1", HOJE.plusDays(2), 1);
        lotes.receber("A", "L2", HOJE.plusDays(10), 1);
        assertEquals(1, lotes.vencendoEm(3).size());
        assertEquals("L1", lotes.vencendoEm(3).get(0).numero());
        assertEquals(2, lotes.vencendoEm(20).size());
    }

    @Test void intervaloNegativoFalha() {
        assertThrows(IllegalArgumentException.class, () -> lotes.vencendoEm(-1));
        assertThrows(IllegalArgumentException.class, () -> lotes.vencendoEm(4000));
    }

    @Test void consultaLoteInexistenteFalha() {
        assertThrows(java.util.NoSuchElementException.class, () -> lotes.obter("A", "X"));
    }

    @Test void listaOrdenadaPorValidade() {
        lotes.receber("A", "L2", HOJE.plusDays(10), 1);
        lotes.receber("A", "L1", HOJE.plusDays(2), 1);
        assertEquals("L1", lotes.listar("A", true).get(0).numero());
    }

    @Test void snapshotDeHistoricoNaoMuda() {
        lotes.receber("A", "L1", HOJE.plusDays(5), 5);
        var snapshot = lotes.historico("A");
        lotes.consumir("A", 1, "PED");
        assertEquals(0, snapshot.size());
        assertEquals(1, lotes.historico("A").size());
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
    }
}
