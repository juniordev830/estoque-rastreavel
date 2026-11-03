package br.junior.estoque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class EstoqueServiceTest {
    private final EstoqueService estoque = new EstoqueService(
            Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void registraEntradasESaidasComHistorico() {
        estoque.registrarEntrada("Teclado", 10);
        estoque.registrarSaida("Teclado", 3);

        assertEquals(7, estoque.saldo("Teclado"));
        assertEquals(2, estoque.historico("Teclado").size());
    }

    @Test
    void impedeSaldoNegativo() {
        assertThrows(IllegalStateException.class, () -> estoque.registrarSaida("Mouse", 1));
    }

    @Test
    void rejeitaOverflowSemAlterarSaldoOuHistorico() {
        estoque.registrarEntrada("Monitor", Integer.MAX_VALUE);
        var antes = estoque.historico("Monitor");

        assertThrows(ArithmeticException.class, () -> estoque.registrarEntrada("Monitor", 1));

        assertEquals(Integer.MAX_VALUE, estoque.saldo("Monitor"));
        assertEquals(antes, estoque.historico("Monitor"));
        estoque.registrarSaida("Monitor", 1);
        assertEquals(Integer.MAX_VALUE - 1, estoque.saldo("Monitor"));
    }

    @Test
    void aceitaEntradaAteOLimiteExato() {
        estoque.registrarEntrada("Monitor", Integer.MAX_VALUE - 1);
        estoque.registrarEntrada("Monitor", 1);
        assertEquals(Integer.MAX_VALUE, estoque.saldo("Monitor"));
        assertEquals(2, estoque.historico("Monitor").size());
    }

    @Test
    void rejeitaQuantidadesInvalidasSemMovimentar() {
        estoque.registrarEntrada("Mouse", 3);
        for (int quantidade : new int[]{0, -1, Integer.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> estoque.registrarEntrada("Mouse", quantidade));
            assertThrows(IllegalArgumentException.class, () -> estoque.registrarSaida("Mouse", quantidade));
        }
        assertThrows(IllegalStateException.class, () -> estoque.registrarSaida("Mouse", 4));
        assertEquals(3, estoque.saldo("Mouse"));
        assertEquals(1, estoque.historico("Mouse").size());
    }

    @Test
    void preservaHistoricoImutavelNormalizaProdutoEUsaRelogioInjetado() {
        estoque.registrarEntrada("  Teclado  ", 2);
        var snapshot = estoque.historico("Teclado");
        assertEquals(java.time.LocalDateTime.of(2026, 11, 3, 12, 0), snapshot.get(0).quando());
        assertEquals("Teclado", snapshot.get(0).produto());
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        estoque.registrarSaida("Teclado", 1);
        assertEquals(1, snapshot.size());
        assertEquals(2, estoque.historico("Teclado").size());
    }

    @Test
    void rejeitaRelogioNuloNaConstrucao() {
        assertThrows(NullPointerException.class, () -> new EstoqueService(null));
    }
}
