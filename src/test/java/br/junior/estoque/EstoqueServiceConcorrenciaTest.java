package br.junior.estoque;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class EstoqueServiceConcorrenciaTest {
    @Test
    void permiteSomenteUmaSaidaQuandoHaUmaUnidade() throws Exception {
        var estoque = new EstoqueService();
        estoque.registrarEntrada("Mouse", 1);

        assertEquals(1, executarEmParalelo(() -> estoque.registrarSaida("Mouse", 1)));

        assertEquals(0, estoque.saldo("Mouse"));
        assertEquals(2, estoque.historico("Mouse").size());
    }

    private static int executarEmParalelo(Runnable operacao) throws Exception {
        int tentativas = 16;
        var executor = Executors.newFixedThreadPool(tentativas);
        var prontos = new CountDownLatch(tentativas);
        var iniciar = new CountDownLatch(1);
        List<Future<Boolean>> resultados = new ArrayList<>();

        try {
            for (int i = 0; i < tentativas; i++) {
                resultados.add(executor.submit(() -> {
                    prontos.countDown();
                    if (!iniciar.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("As operações não começaram a tempo");
                    }
                    try {
                        operacao.run();
                        return true;
                    } catch (IllegalStateException esperado) {
                        return false;
                    }
                }));
            }
            if (!prontos.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("As operações não ficaram prontas a tempo");
            }
            iniciar.countDown();

            int sucessos = 0;
            for (var resultado : resultados) {
                if (resultado.get(5, TimeUnit.SECONDS)) {
                    sucessos++;
                }
            }
            return sucessos;
        } finally {
            executor.shutdownNow();
        }
    }
}
