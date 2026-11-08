package br.junior.estoque;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EstoqueLocalizadoTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC);
    private CatalogoProdutos catalogo;
    private EstoqueLocalizado estoque;

    @BeforeEach void preparar() {
        catalogo = new CatalogoProdutos(CLOCK);
        catalogo.cadastrarProduto("A", "Teclado", "TI",
                CatalogoProdutos.Unidade.UNIDADE, 5, 20, new BigDecimal("10.00"));
        catalogo.cadastrarProduto("B", "Mouse", "TI",
                CatalogoProdutos.Unidade.UNIDADE, 2, 10, new BigDecimal("5.00"));
        estoque = new EstoqueLocalizado(catalogo, CLOCK);
        estoque.cadastrarLocal("L1", "Depósito");
        estoque.cadastrarLocal("L2", "Loja");
    }

    @Test void localNormalizaCodigo() {
        assertEquals("L3", estoque.cadastrarLocal(" l3 ", " Filial ").codigo());
        assertEquals("Filial", estoque.obterLocal("L3").descricao());
    }

    @Test void localDuplicadoFalha() {
        assertThrows(IllegalStateException.class, () -> estoque.cadastrarLocal("l1", "Outro"));
        assertEquals(2, estoque.listarLocais(false).size());
    }

    @Test void localInexistenteFalha() {
        assertThrows(java.util.NoSuchElementException.class, () -> estoque.obterLocal("L99"));
    }

    @Test void localPodeSerDesativado() {
        estoque.alterarLocal("L2", "Loja", false);
        assertEquals(1, estoque.listarLocais(true).size());
        assertThrows(IllegalStateException.class, () -> estoque.receber("A", "L2", 1, "NF"));
    }

    @Test void naoDesativaLocalComReserva() {
        estoque.receber("A", "L1", 5, "NF");
        var r = estoque.reservar("A", "L1", 2, "PED");
        assertThrows(IllegalStateException.class, () -> estoque.alterarLocal("L1", "Depósito", false));
        estoque.cancelarReserva(r.id());
        assertFalse(estoque.alterarLocal("L1", "Depósito", false).ativo());
    }

    @Test void recebeEConsultaSaldo() {
        var posicao = estoque.receber("A", "L1", 10, "NF-1");
        assertEquals(10, posicao.quantidadeFisica());
        assertEquals(0, posicao.quantidadeReservada());
        assertEquals(10, posicao.disponivel());
        assertEquals(10, estoque.saldoTotal("A"));
    }

    @Test void entradaZeroOuNegativaNaoAlteraSaldo() {
        assertThrows(IllegalArgumentException.class, () -> estoque.receber("A", "L1", 0, "NF"));
        assertThrows(IllegalArgumentException.class, () -> estoque.receber("A", "L1", -1, "NF"));
        assertEquals(0, estoque.consultar("A", "L1").quantidadeFisica());
        assertTrue(estoque.historico(null, null).isEmpty());
    }

    @Test void entradaComReferenciaInvalidaFalha() {
        assertThrows(IllegalArgumentException.class, () -> estoque.receber("A", "L1", 1, ""));
        assertEquals(0, estoque.saldoTotal("A"));
    }

    @Test void entradaOverflowNaoAlteraSaldoNemHistorico() {
        estoque.receber("A", "L1", Integer.MAX_VALUE, "NF");
        assertThrows(ArithmeticException.class, () -> estoque.receber("A", "L1", 1, "NF"));
        assertEquals(Integer.MAX_VALUE, estoque.saldoTotal("A"));
        assertEquals(1, estoque.historico(null, null).size());
    }

    @Test void retiraSaldoDisponivel() {
        estoque.receber("A", "L1", 10, "NF");
        var posicao = estoque.retirar("A", "L1", 3, "PED");
        assertEquals(7, posicao.quantidadeFisica());
        assertEquals(2, estoque.historico("A", "L1").size());
    }

    @Test void retiradaInsuficienteNaoMovimenta() {
        estoque.receber("A", "L1", 2, "NF");
        assertThrows(IllegalStateException.class, () -> estoque.retirar("A", "L1", 3, "PED"));
        assertEquals(2, estoque.saldoTotal("A"));
        assertEquals(1, estoque.historico(null, null).size());
    }

    @Test void transfereEntreLocaisSemAlterarTotal() {
        estoque.receber("A", "L1", 10, "NF");
        estoque.transferir("A", "L1", "L2", 4, "TR-1");
        assertEquals(6, estoque.consultar("A", "L1").quantidadeFisica());
        assertEquals(4, estoque.consultar("A", "L2").quantidadeFisica());
        assertEquals(10, estoque.saldoTotal("A"));
        assertEquals(3, estoque.historico("A", null).size());
    }

    @Test void naoTransfereParaMesmoLocal() {
        estoque.receber("A", "L1", 10, "NF");
        assertThrows(IllegalArgumentException.class,
                () -> estoque.transferir("A", "L1", "l1", 1, "TR"));
        assertEquals(10, estoque.saldoTotal("A"));
    }

    @Test void transferenciaInsuficienteNaoAlteraDestino() {
        estoque.receber("A", "L1", 2, "NF");
        assertThrows(IllegalStateException.class,
                () -> estoque.transferir("A", "L1", "L2", 3, "TR"));
        assertEquals(0, estoque.consultar("A", "L2").quantidadeFisica());
    }

    @Test void transferenciaOverflowNaoAlteraOrigem() {
        estoque.receber("A", "L1", 1, "NF");
        estoque.receber("A", "L2", Integer.MAX_VALUE, "NF");
        assertThrows(ArithmeticException.class,
                () -> estoque.transferir("A", "L1", "L2", 1, "TR"));
        assertEquals(1, estoque.consultar("A", "L1").quantidadeFisica());
        assertEquals(2, estoque.historico(null, null).size());
    }

    @Test void reservaReduzDisponivelSemAlterarFisico() {
        estoque.receber("A", "L1", 10, "NF");
        var r = estoque.reservar("A", "L1", 4, "PED");
        assertEquals(EstoqueLocalizado.EstadoReserva.ATIVA, r.estado());
        assertEquals(10, estoque.consultar("A", "L1").quantidadeFisica());
        assertEquals(4, estoque.consultar("A", "L1").quantidadeReservada());
        assertEquals(6, estoque.consultar("A", "L1").disponivel());
    }

    @Test void reservaImpedeRetiradaDeSaldoComprometido() {
        estoque.receber("A", "L1", 10, "NF");
        estoque.reservar("A", "L1", 8, "PED");
        assertThrows(IllegalStateException.class, () -> estoque.retirar("A", "L1", 3, "VENDA"));
        assertThrows(IllegalStateException.class,
                () -> estoque.transferir("A", "L1", "L2", 3, "TR"));
    }

    @Test void reservaNaoPodeExcederDisponivel() {
        estoque.receber("A", "L1", 5, "NF");
        estoque.reservar("A", "L1", 4, "PED");
        assertThrows(IllegalStateException.class, () -> estoque.reservar("A", "L1", 2, "PED2"));
        assertEquals(1, estoque.listarReservas(null).size());
    }

    @Test void cancelarReservaLiberaDisponivel() {
        estoque.receber("A", "L1", 5, "NF");
        var r = estoque.reservar("A", "L1", 3, "PED");
        assertEquals(EstoqueLocalizado.EstadoReserva.CANCELADA,
                estoque.cancelarReserva(r.id()).estado());
        assertEquals(5, estoque.consultar("A", "L1").disponivel());
        assertThrows(IllegalStateException.class, () -> estoque.cancelarReserva(r.id()));
    }

    @Test void atenderReservaReduzFisico() {
        estoque.receber("A", "L1", 10, "NF");
        var r = estoque.reservar("A", "L1", 4, "PED");
        assertEquals(EstoqueLocalizado.EstadoReserva.ATENDIDA,
                estoque.atenderReserva(r.id()).estado());
        assertEquals(6, estoque.consultar("A", "L1").quantidadeFisica());
        assertEquals(0, estoque.consultar("A", "L1").quantidadeReservada());
        assertThrows(IllegalStateException.class, () -> estoque.atenderReserva(r.id()));
    }

    @Test void ajusteDeInventarioAumentaEDiminui() {
        estoque.receber("A", "L1", 10, "NF");
        estoque.ajustarInventario("A", "L1", 12, "Contagem");
        estoque.ajustarInventario("A", "L1", 7, "Contagem");
        assertEquals(7, estoque.saldoTotal("A"));
        assertEquals(3, estoque.historico("A", "L1").size());
    }

    @Test void ajusteNaoPodeViolarReservas() {
        estoque.receber("A", "L1", 10, "NF");
        estoque.reservar("A", "L1", 7, "PED");
        assertThrows(IllegalStateException.class,
                () -> estoque.ajustarInventario("A", "L1", 6, "Contagem"));
        assertEquals(10, estoque.saldoTotal("A"));
    }

    @Test void ajusteSemDiferencaNaoGeraEvento() {
        estoque.receber("A", "L1", 10, "NF");
        estoque.ajustarInventario("A", "L1", 10, "Contagem");
        assertEquals(1, estoque.historico(null, null).size());
    }

    @Test void posicoesSaoOrdenadas() {
        estoque.receber("B", "L2", 1, "NF");
        estoque.receber("A", "L2", 2, "NF");
        estoque.receber("A", "L1", 3, "NF");
        assertEquals(java.util.List.of("A/L1", "A/L2", "B/L2"),
                estoque.listarPosicoes(null).stream()
                        .map(p -> p.sku() + "/" + p.local()).toList());
    }

    @Test void historicoTemSequenciaERelogio() {
        estoque.receber("A", "L1", 5, "NF");
        estoque.retirar("A", "L1", 2, "VENDA");
        var eventos = estoque.historico("A", "L1");
        assertEquals(1L, eventos.get(0).sequencia());
        assertEquals(2L, eventos.get(1).sequencia());
        assertEquals(CLOCK.instant(), eventos.get(0).quando());
        assertEquals(3, eventos.get(1).saldoApos());
    }

    @Test void historicoESnapshotsNaoSaoMutaveis() {
        estoque.receber("A", "L1", 5, "NF");
        var eventos = estoque.historico(null, null);
        var posicoes = estoque.listarPosicoes(null);
        assertThrows(UnsupportedOperationException.class, eventos::clear);
        assertThrows(UnsupportedOperationException.class, posicoes::clear);
        estoque.receber("A", "L1", 1, "NF2");
        assertEquals(1, eventos.size());
    }

    @Test void produtoInativoNaoRecebeNovasEntradas() {
        catalogo.alterarAtivo("A", false);
        assertThrows(IllegalStateException.class, () -> estoque.receber("A", "L1", 1, "NF"));
    }

    @Test void reservaInexistenteFalha() {
        assertThrows(java.util.NoSuchElementException.class, () -> estoque.obterReserva(99));
        assertThrows(java.util.NoSuchElementException.class, () -> estoque.cancelarReserva(99));
    }
}
