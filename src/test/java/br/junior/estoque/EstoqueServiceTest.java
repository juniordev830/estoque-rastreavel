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
}
